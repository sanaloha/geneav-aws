package com.geneav.scan.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.geneav.scan.account.Account;
import com.geneav.scan.account.AccountRepository;
import com.geneav.scan.plan.PlanCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the Microsoft Marketplace subscription lifecycle, and is the
 * <b>only</b> writer of {@code account.plan} for billing reasons. Webhook
 * events are applied transactionally: the event is recorded first (its unique
 * constraint is the idempotency guard), then the subscription and plan are
 * updated. Nothing in the scan/quota path learns a marketplace exists.
 */
@Service
@EnableConfigurationProperties(MarketplaceProperties.class)
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    public static final String BILLING_SOURCE_MARKETPLACE = "marketplace";
    public static final String BILLING_SOURCE_NONE = "none";

    private final MarketplaceProperties props;
    private final FulfillmentClient fulfillment;
    private final MarketplaceSubscriptionRepository subscriptions;
    private final MarketplaceEventRepository events;
    private final AccountRepository accounts;
    private final PlanCatalog plans;

    public MarketplaceService(MarketplaceProperties props, FulfillmentClient fulfillment,
                              MarketplaceSubscriptionRepository subscriptions, MarketplaceEventRepository events,
                              AccountRepository accounts, PlanCatalog plans) {
        this.props = props;
        this.fulfillment = fulfillment;
        this.subscriptions = subscriptions;
        this.events = events;
        this.accounts = accounts;
        this.plans = plans;
    }

    /**
     * Guards every marketplace endpoint, matching the mail/openai convention:
     * unconfigured means 503 and the rest of the product is unaffected.
     */
    public void requireConfigured() {
        if (!props.isConfigured()) {
            throw new MarketplaceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Marketplace integration is not configured on this server.");
        }
    }

    /**
     * Resolves a landing-page purchase token against Microsoft and records the
     * purchase for {@code account} as {@code PendingFulfillmentStart}. Safe to
     * repeat: re-resolving the same purchase refreshes the stored details.
     */
    @Transactional
    public MarketplaceSubscription resolvePurchase(Account account, String marketplaceToken) {
        requireConfigured();
        if (marketplaceToken == null || marketplaceToken.isBlank()) {
            throw new MarketplaceException(HttpStatus.BAD_REQUEST, "A marketplace purchase token is required.");
        }

        FulfillmentClient.ResolvedPurchase resolved = fulfillment.resolve(marketplaceToken.trim());
        FulfillmentClient.SubscriptionDetails details = resolved.subscription();
        UUID marketplaceSubId = details != null && details.id() != null ? details.id() : resolved.id();
        String marketplacePlanId = resolved.planId();
        String planKey = requireMappedPlan(marketplacePlanId);

        MarketplaceSubscription sub = subscriptions.findByMarketplaceSubId(marketplaceSubId).orElse(null);
        if (sub != null && !sub.getAccountId().equals(account.getId())) {
            // The purchase is already claimed by someone else's geneav account.
            throw new MarketplaceException(HttpStatus.CONFLICT,
                    "This marketplace subscription is already linked to a different geneav account.");
        }
        if (sub == null) {
            Optional<MarketplaceSubscription> live = subscriptions.findLiveByAccountId(account.getId());
            if (live.isPresent()) {
                throw new MarketplaceException(HttpStatus.CONFLICT,
                        "This account already has a marketplace subscription. Cancel it in the Azure portal "
                                + "before linking another.");
            }
            sub = new MarketplaceSubscription(UUID.randomUUID(), account.getId(), marketplaceSubId,
                    resolved.offerId(), marketplacePlanId, planKey,
                    MarketplaceSubscription.STATUS_PENDING, Instant.now());
        } else {
            sub.setMarketplacePlanId(marketplacePlanId);
            sub.setPlanKey(planKey);
        }
        applyDetails(sub, details, resolved.quantity());
        return subscriptions.save(sub);
    }

    /**
     * Activates a resolved purchase: tells Microsoft to start billing, then
     * grants the entitlement. If the commit after Microsoft's 200 fails, the
     * {@code Subscribe} webhook re-applies the grant.
     */
    @Transactional
    public MarketplaceSubscription activate(Account account, UUID marketplaceSubId) {
        requireConfigured();
        MarketplaceSubscription sub = subscriptions.findByMarketplaceSubId(marketplaceSubId)
                .filter(s -> s.getAccountId().equals(account.getId()))
                .orElseThrow(() -> new MarketplaceException(HttpStatus.NOT_FOUND,
                        "No resolved marketplace purchase with that id. Resolve it first."));

        fulfillment.activate(sub.getMarketplaceSubId(), sub.getMarketplacePlanId());

        sub.setStatus(MarketplaceSubscription.STATUS_SUBSCRIBED);
        sub.touch(Instant.now());
        grantPlan(account.getId(), sub.getPlanKey());
        log.info("Activated marketplace subscription {} -> account {} on plan {}",
                sub.getMarketplaceSubId(), account.getId(), sub.getPlanKey());
        return subscriptions.save(sub);
    }

    @Transactional(readOnly = true)
    public Optional<MarketplaceSubscription> currentSubscription(UUID accountId) {
        return subscriptions.findLiveByAccountId(accountId);
    }

    /**
     * Applies one webhook notification. Duplicate deliveries (same payload id)
     * are 200 no-ops. Returns whether an operation acknowledgement (PATCH) is
     * still owed for this event — {@code ChangePlan}/{@code ChangeQuantity}
     * auto-accept after 10 seconds, so the caller must act on this promptly.
     */
    @Transactional
    public AckDecision applyWebhook(WebhookNotification payload) {
        requireConfigured();
        if (payload == null || payload.id() == null || payload.action() == null) {
            throw new MarketplaceException(HttpStatus.BAD_REQUEST, "Malformed webhook payload.");
        }
        if (events.existsByEventId(payload.id())) {
            log.debug("Duplicate marketplace event {} ({}); ignoring", payload.id(), payload.action());
            return AckDecision.none();
        }
        events.save(new MarketplaceEvent(UUID.randomUUID(), payload.id(), payload.subscriptionId(),
                payload.action(), payload.status() == null ? "unknown" : payload.status(),
                payload.rawJson() == null ? "{}" : payload.rawJson(), Instant.now()));

        MarketplaceSubscription sub = payload.subscriptionId() == null ? null
                : subscriptions.findByMarketplaceSubId(payload.subscriptionId()).orElse(null);
        if (sub == null) {
            // A purchase that was never resolved on our side (or a test event).
            // Stored above for the audit trail; nothing to change.
            log.warn("Marketplace event {} ({}) for unknown subscription {}; stored, no action",
                    payload.id(), payload.action(), payload.subscriptionId());
            return AckDecision.none();
        }

        Instant now = Instant.now();
        switch (payload.action()) {
            case "Subscribe" -> {
                sub.setStatus(MarketplaceSubscription.STATUS_SUBSCRIBED);
                applyDetails(sub, payload.subscription(), payload.quantity());
                grantPlan(sub.getAccountId(), sub.getPlanKey());
            }
            case "ChangePlan" -> {
                String newPlanKey = props.getPlanMap().get(payload.planId());
                if (newPlanKey == null) {
                    log.error("Marketplace ChangePlan {} names unmapped plan id '{}'; refusing",
                            payload.id(), payload.planId());
                    return AckDecision.failure(sub.getMarketplaceSubId(), payload.id());
                }
                sub.setMarketplacePlanId(payload.planId());
                sub.setPlanKey(newPlanKey);
                applyDetails(sub, payload.subscription(), payload.quantity());
                if (MarketplaceSubscription.STATUS_SUBSCRIBED.equals(sub.getStatus())) {
                    grantPlan(sub.getAccountId(), newPlanKey);
                }
                sub.touch(now);
                subscriptions.save(sub);
                return AckDecision.success(sub.getMarketplaceSubId(), payload.id());
            }
            case "ChangeQuantity" -> {
                // geneav plans are not per-seat; record and accept.
                sub.setQuantity(payload.quantity());
                sub.touch(now);
                subscriptions.save(sub);
                return AckDecision.success(sub.getMarketplaceSubId(), payload.id());
            }
            case "Renew" -> applyDetails(sub, payload.subscription(), payload.quantity());
            case "Suspend" -> {
                // Payment failed. Drop to free — the customer keeps working at
                // the free quota while they fix a card — but keep the planKey so
                // Reinstate can restore it.
                sub.setStatus(MarketplaceSubscription.STATUS_SUSPENDED);
                withAccount(sub.getAccountId(), account -> account.setPlan(plans.defaultPlanKey()));
            }
            case "Reinstate" -> {
                sub.setStatus(MarketplaceSubscription.STATUS_SUBSCRIBED);
                applyDetails(sub, payload.subscription(), payload.quantity());
                grantPlan(sub.getAccountId(), sub.getPlanKey());
            }
            case "Unsubscribe" -> {
                sub.setStatus(MarketplaceSubscription.STATUS_UNSUBSCRIBED);
                revokePlan(sub.getAccountId());
            }
            default ->
                // Microsoft reserves the right to add actions: store and accept.
                log.info("Marketplace event {} has unrecognised action '{}'; stored, no action",
                        payload.id(), payload.action());
        }
        sub.touch(now);
        subscriptions.save(sub);
        return AckDecision.none();
    }

    /**
     * Safety-net sweep: closes out suspended subscriptions whose paid term
     * ended without reinstatement, and repairs accounts that missed a
     * downgrade (e.g. a webhook that exhausted its retries).
     */
    @Transactional
    public int expireLapsed(Instant now) {
        int changed = 0;
        for (MarketplaceSubscription sub : subscriptions.findLapsed(now)) {
            if (MarketplaceSubscription.STATUS_SUSPENDED.equals(sub.getStatus())) {
                sub.setStatus(MarketplaceSubscription.STATUS_UNSUBSCRIBED);
                sub.touch(now);
                subscriptions.save(sub);
            }
            Account account = accounts.findById(sub.getAccountId()).orElse(null);
            if (account != null && BILLING_SOURCE_MARKETPLACE.equals(account.getBillingSource())) {
                account.setPlan(plans.defaultPlanKey());
                account.setBillingSource(BILLING_SOURCE_NONE);
                accounts.save(account);
                changed++;
                log.info("Marketplace subscription {} lapsed; account {} downgraded to {}",
                        sub.getMarketplaceSubId(), account.getId(), plans.defaultPlanKey());
            }
        }
        return changed;
    }

    /** The public Azure Marketplace listing URL for the configured offer. */
    public String listingUrl() {
        return "https://azuremarketplace.microsoft.com/marketplace/apps/"
                + props.getPublisherId() + "." + props.getOfferId();
    }

    // ------------------------------------------------------------------ helpers

    private String requireMappedPlan(String marketplacePlanId) {
        String planKey = props.getPlanMap().get(marketplacePlanId);
        if (planKey == null) {
            // Never guess: an unmapped id must not default to a bigger tier
            // than was paid for.
            log.error("Resolved marketplace purchase names unmapped plan id '{}'", marketplacePlanId);
            throw new MarketplaceException(HttpStatus.BAD_GATEWAY,
                    "This purchase names a plan this server does not recognise. Please contact support.");
        }
        return planKey;
    }

    private void applyDetails(MarketplaceSubscription sub, FulfillmentClient.SubscriptionDetails details,
                              Integer quantity) {
        if (quantity != null) {
            sub.setQuantity(quantity);
        }
        if (details == null) {
            return;
        }
        if (details.term() != null) {
            sub.setTermStart(details.term().startDate());
            sub.setTermEnd(details.term().endDate());
        }
        if (details.autoRenew() != null) {
            sub.setAutoRenew(details.autoRenew());
        }
        if (details.isFreeTrial() != null) {
            sub.setFreeTrial(details.isFreeTrial());
        }
        if (details.isTest() != null) {
            sub.setTest(details.isTest());
        }
        if (details.beneficiary() != null) {
            sub.setBeneficiaryEmail(details.beneficiary().emailId());
            sub.setBeneficiaryTenantId(details.beneficiary().tenantId());
        }
        if (details.purchaser() != null) {
            sub.setPurchaserEmail(details.purchaser().emailId());
        }
    }

    private void grantPlan(UUID accountId, String planKey) {
        withAccount(accountId, account -> {
            account.setPlan(planKey);
            account.setBillingSource(BILLING_SOURCE_MARKETPLACE);
        });
    }

    private void revokePlan(UUID accountId) {
        withAccount(accountId, account -> {
            account.setPlan(plans.defaultPlanKey());
            account.setBillingSource(BILLING_SOURCE_NONE);
        });
    }

    private void withAccount(UUID accountId, java.util.function.Consumer<Account> mutation) {
        Account account = accounts.findById(accountId).orElse(null);
        if (account == null) {
            log.error("Marketplace subscription references missing account {}", accountId);
            return;
        }
        mutation.accept(account);
        accounts.save(account);
    }

    /**
     * Whether (and how) the webhook handler still owes Microsoft an operation
     * status PATCH for this event.
     */
    public record AckDecision(UUID subscriptionId, UUID operationId, String status) {
        public static AckDecision none() {
            return new AckDecision(null, null, null);
        }

        public static AckDecision success(UUID subscriptionId, UUID operationId) {
            return new AckDecision(subscriptionId, operationId, "Success");
        }

        public static AckDecision failure(UUID subscriptionId, UUID operationId) {
            return new AckDecision(subscriptionId, operationId, "Failure");
        }

        public boolean required() {
            return status != null;
        }
    }

    /**
     * The webhook notification shape. Deliberately loose: Microsoft reserves
     * the right to extend the schema, so unknown fields are ignored and
     * everything is nullable. {@code rawJson} is attached by the controller for
     * the audit trail; it is not a wire field.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookNotification(UUID id, UUID subscriptionId, String publisherId, String offerId,
                                      String planId, Integer quantity, String action, String status,
                                      FulfillmentClient.SubscriptionDetails subscription, String rawJson) {

        public WebhookNotification withRawJson(String json) {
            return new WebhookNotification(id, subscriptionId, publisherId, offerId, planId, quantity,
                    action, status, subscription, json);
        }
    }
}
