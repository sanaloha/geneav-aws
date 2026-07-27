package com.geneav.scan.marketplace;

import com.geneav.scan.account.Account;
import com.geneav.scan.account.AccountRepository;
import com.geneav.scan.plan.PlanCatalog;
import com.geneav.scan.plan.PlanProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceServiceTest {

    private final FulfillmentClient fulfillment = mock(FulfillmentClient.class);
    private final MarketplaceSubscriptionRepository subscriptions = mock(MarketplaceSubscriptionRepository.class);
    private final MarketplaceEventRepository events = mock(MarketplaceEventRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);

    private final MarketplaceProperties props = configuredProps();
    private final MarketplaceService service = new MarketplaceService(
            props, fulfillment, subscriptions, events, accounts, catalog());

    private static MarketplaceProperties configuredProps() {
        MarketplaceProperties p = new MarketplaceProperties();
        p.setEnabled(true);
        p.setTenantId("tenant-1");
        p.setClientId("client-1");
        p.setClientSecret("secret-1");
        p.setPlanMap(Map.of("geneav-starter", "starter", "geneav-pro", "pro", "geneav-scale", "scale"));
        return p;
    }

    private static PlanCatalog catalog() {
        PlanProperties props = new PlanProperties();
        props.setDefaultPlan("free");
        props.getDefinitions().put("free", new PlanProperties.Plan());
        props.getDefinitions().put("pro", new PlanProperties.Plan());
        return new PlanCatalog(props);
    }

    private Account account(String plan, String billingSource) {
        Account account = new Account(UUID.randomUUID(), "a@b.com", plan, "active", Instant.now());
        account.setBillingSource(billingSource);
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        return account;
    }

    private MarketplaceSubscription subscription(Account account, String status, String planKey) {
        MarketplaceSubscription sub = new MarketplaceSubscription(UUID.randomUUID(), account.getId(),
                UUID.randomUUID(), "geneav-scan-api", "geneav-" + planKey, planKey, status, Instant.now());
        when(subscriptions.findByMarketplaceSubId(sub.getMarketplaceSubId())).thenReturn(Optional.of(sub));
        when(subscriptions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        return sub;
    }

    private MarketplaceService.WebhookNotification event(UUID subId, String action, String planId,
                                                         Integer quantity) {
        return new MarketplaceService.WebhookNotification(UUID.randomUUID(), subId, "pub", "offer",
                planId, quantity, action, "InProgress", null, "{}");
    }

    // ------------------------------------------------------------- guard rails

    @Test
    void unconfiguredMarketplaceReturns503() {
        MarketplaceService disabled = new MarketplaceService(new MarketplaceProperties(), fulfillment,
                subscriptions, events, accounts, catalog());

        assertThatThrownBy(() -> disabled.resolvePurchase(account("free", "none"), "tok"))
                .isInstanceOf(MarketplaceException.class)
                .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void duplicateEventIsANoOp() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Subscribed", "pro");
        MarketplaceService.WebhookNotification payload =
                event(sub.getMarketplaceSubId(), "Unsubscribe", "geneav-pro", null);
        when(events.existsByEventId(payload.id())).thenReturn(true);

        MarketplaceService.AckDecision ack = service.applyWebhook(payload);

        assertThat(ack.required()).isFalse();
        verify(events, never()).save(any());
        assertThat(account.getPlan()).isEqualTo("pro"); // no second application
    }

    @Test
    void eventForUnknownSubscriptionIsStoredButChangesNothing() {
        when(subscriptions.findByMarketplaceSubId(any())).thenReturn(Optional.empty());

        MarketplaceService.AckDecision ack =
                service.applyWebhook(event(UUID.randomUUID(), "Subscribe", "geneav-pro", null));

        assertThat(ack.required()).isFalse();
        verify(events).save(any());
        verify(accounts, never()).save(any());
    }

    // --------------------------------------------------------- lifecycle moves

    @Test
    void subscribeGrantsThePlan() {
        Account account = account("free", "none");
        MarketplaceSubscription sub = subscription(account, "PendingFulfillmentStart", "pro");

        service.applyWebhook(event(sub.getMarketplaceSubId(), "Subscribe", "geneav-pro", null));

        assertThat(sub.getStatus()).isEqualTo("Subscribed");
        assertThat(account.getPlan()).isEqualTo("pro");
        assertThat(account.getBillingSource()).isEqualTo("marketplace");
    }

    @Test
    void changePlanToMappedPlanAppliesAndAcksSuccess() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Subscribed", "pro");

        MarketplaceService.AckDecision ack =
                service.applyWebhook(event(sub.getMarketplaceSubId(), "ChangePlan", "geneav-scale", null));

        assertThat(ack.required()).isTrue();
        assertThat(ack.status()).isEqualTo("Success");
        assertThat(sub.getPlanKey()).isEqualTo("scale");
        assertThat(account.getPlan()).isEqualTo("scale");
    }

    @Test
    void changePlanToUnmappedPlanRefusesAndAcksFailure() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Subscribed", "pro");

        MarketplaceService.AckDecision ack =
                service.applyWebhook(event(sub.getMarketplaceSubId(), "ChangePlan", "geneav-mega", null));

        assertThat(ack.status()).isEqualTo("Failure");
        assertThat(sub.getPlanKey()).isEqualTo("pro"); // unchanged
        assertThat(account.getPlan()).isEqualTo("pro");
    }

    @Test
    void suspendDropsToFreeButKeepsThePlanKeyForReinstate() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Subscribed", "pro");

        service.applyWebhook(event(sub.getMarketplaceSubId(), "Suspend", "geneav-pro", null));

        assertThat(sub.getStatus()).isEqualTo("Suspended");
        assertThat(sub.getPlanKey()).isEqualTo("pro"); // remembered
        assertThat(account.getPlan()).isEqualTo("free");
        // Still a marketplace customer — only Unsubscribe severs the link.
        assertThat(account.getBillingSource()).isEqualTo("marketplace");
    }

    @Test
    void reinstateRestoresTheRememberedPlan() {
        Account account = account("free", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Suspended", "pro");

        service.applyWebhook(event(sub.getMarketplaceSubId(), "Reinstate", "geneav-pro", null));

        assertThat(sub.getStatus()).isEqualTo("Subscribed");
        assertThat(account.getPlan()).isEqualTo("pro");
    }

    @Test
    void unsubscribeRevokesEntitlementEntirely() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Subscribed", "pro");

        service.applyWebhook(event(sub.getMarketplaceSubId(), "Unsubscribe", "geneav-pro", null));

        assertThat(sub.getStatus()).isEqualTo("Unsubscribed");
        assertThat(account.getPlan()).isEqualTo("free");
        assertThat(account.getBillingSource()).isEqualTo("none");
    }

    @Test
    void unrecognisedActionIsStoredAndAccepted() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Subscribed", "pro");

        MarketplaceService.AckDecision ack =
                service.applyWebhook(event(sub.getMarketplaceSubId(), "SomethingNew", "geneav-pro", null));

        assertThat(ack.required()).isFalse();
        verify(events).save(any());
        assertThat(account.getPlan()).isEqualTo("pro"); // untouched
    }

    // ------------------------------------------------------- resolve / activate

    @Test
    void resolveWithUnmappedPlanIdNeverGuessesATier() {
        Account account = account("free", "none");
        when(fulfillment.resolve("tok")).thenReturn(new FulfillmentClient.ResolvedPurchase(
                UUID.randomUUID(), "sub", "geneav-scan-api", "geneav-mega", null, null));

        assertThatThrownBy(() -> service.resolvePurchase(account, "tok"))
                .isInstanceOf(MarketplaceException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void resolveRefusesASecondLiveSubscription() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription existing = subscription(account, "Subscribed", "pro");
        when(subscriptions.findLiveByAccountId(account.getId())).thenReturn(Optional.of(existing));
        when(subscriptions.findByMarketplaceSubId(any())).thenReturn(Optional.empty());
        when(fulfillment.resolve("tok")).thenReturn(new FulfillmentClient.ResolvedPurchase(
                UUID.randomUUID(), "sub", "geneav-scan-api", "geneav-starter", null, null));

        assertThatThrownBy(() -> service.resolvePurchase(account, "tok"))
                .isInstanceOf(MarketplaceException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void resolveRefusesAPurchaseClaimedByAnotherAccount() {
        Account owner = account("pro", "marketplace");
        MarketplaceSubscription theirs = subscription(owner, "Subscribed", "pro");
        Account intruder = account("free", "none");
        when(fulfillment.resolve("tok")).thenReturn(new FulfillmentClient.ResolvedPurchase(
                theirs.getMarketplaceSubId(), "sub", "geneav-scan-api", "geneav-pro", null, null));

        assertThatThrownBy(() -> service.resolvePurchase(intruder, "tok"))
                .isInstanceOf(MarketplaceException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void activateTellsMicrosoftThenGrantsThePlan() {
        Account account = account("free", "none");
        MarketplaceSubscription sub = subscription(account, "PendingFulfillmentStart", "pro");

        service.activate(account, sub.getMarketplaceSubId());

        verify(fulfillment).activate(sub.getMarketplaceSubId(), "geneav-pro");
        assertThat(sub.getStatus()).isEqualTo("Subscribed");
        assertThat(account.getPlan()).isEqualTo("pro");
        assertThat(account.getBillingSource()).isEqualTo("marketplace");
    }

    // ------------------------------------------------------------- expiry sweep

    @Test
    void expirySweepDowngradesLapsedMarketplaceAccounts() {
        Account account = account("pro", "marketplace");
        MarketplaceSubscription sub = subscription(account, "Suspended", "pro");
        sub.setTermEnd(Instant.now().minusSeconds(3600));
        when(subscriptions.findLapsed(any())).thenReturn(java.util.List.of(sub));

        int downgraded = service.expireLapsed(Instant.now());

        assertThat(downgraded).isEqualTo(1);
        assertThat(sub.getStatus()).isEqualTo("Unsubscribed");
        assertThat(account.getPlan()).isEqualTo("free");
        assertThat(account.getBillingSource()).isEqualTo("none");
    }

    @Test
    void expirySweepLeavesAlreadyDowngradedAccountsAlone() {
        Account account = account("free", "none"); // webhook already handled it
        MarketplaceSubscription sub = subscription(account, "Unsubscribed", "pro");
        sub.setTermEnd(Instant.now().minusSeconds(3600));
        when(subscriptions.findLapsed(any())).thenReturn(java.util.List.of(sub));

        assertThat(service.expireLapsed(Instant.now())).isZero();
        assertThat(account.getPlan()).isEqualTo("free");
    }
}
