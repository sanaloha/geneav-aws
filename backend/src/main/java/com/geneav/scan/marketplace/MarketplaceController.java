package com.geneav.scan.marketplace;

import com.geneav.scan.account.Account;
import com.geneav.scan.account.CurrentAccount;
import com.geneav.scan.web.ScanException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Microsoft Marketplace purchase flow for the landing page and dashboard.
 * Session-authenticated via {@link CurrentAccount}, like the other management
 * endpoints. Returns 503 whenever the integration is unconfigured, matching
 * the mail/openai convention.
 */
@RestController
@RequestMapping("/api/v1/marketplace")
@Tag(name = "Marketplace", description = "Microsoft Marketplace subscription lifecycle")
public class MarketplaceController {

    private final MarketplaceService marketplace;
    private final CurrentAccount currentAccount;

    public MarketplaceController(MarketplaceService marketplace, CurrentAccount currentAccount) {
        this.marketplace = marketplace;
        this.currentAccount = currentAccount;
    }

    @Operation(summary = "Resolve a marketplace purchase",
            description = "Exchanges the landing-page token for the purchased subscription's details "
                    + "and links the purchase to the signed-in account (not yet billed).")
    @PostMapping(value = "/resolve", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SubscriptionView resolve(@RequestBody ResolveRequest body, HttpServletRequest request) {
        Account account = requireAccount(request);
        return SubscriptionView.of(marketplace.resolvePurchase(account, body == null ? null : body.token()));
    }

    @Operation(summary = "Activate a marketplace subscription",
            description = "Confirms the purchase with Microsoft (billing starts) and applies the plan.")
    @PostMapping(value = "/activate", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SubscriptionView activate(@RequestBody ActivateRequest body, HttpServletRequest request) {
        Account account = requireAccount(request);
        if (body == null || body.subscriptionId() == null) {
            throw new ScanException(HttpStatus.BAD_REQUEST, "subscriptionId is required.");
        }
        return SubscriptionView.of(marketplace.activate(account, body.subscriptionId()));
    }

    @Operation(summary = "Current marketplace subscription",
            description = "The signed-in account's live marketplace subscription, or 404 if none.")
    @GetMapping(value = "/subscription", produces = MediaType.APPLICATION_JSON_VALUE)
    public SubscriptionView subscription(HttpServletRequest request) {
        // Auth first, then configuration — same order as the other two endpoints,
        // so an anonymous caller never learns whether billing is wired up here.
        Account account = requireAccount(request);
        marketplace.requireConfigured();
        return marketplace.currentSubscription(account.getId())
                .map(SubscriptionView::of)
                .orElseThrow(() -> new ScanException(HttpStatus.NOT_FOUND,
                        "No marketplace subscription for this account."));
    }

    private Account requireAccount(HttpServletRequest request) {
        return currentAccount.resolve(request).orElseThrow(() -> new ScanException(HttpStatus.UNAUTHORIZED,
                "Sign in to manage a marketplace subscription."));
    }

    public record ResolveRequest(String token) {
    }

    public record ActivateRequest(UUID subscriptionId) {
    }

    public record SubscriptionView(UUID subscriptionId, String offerId, String marketplacePlanId,
                                   String planKey, String status, boolean freeTrial, Boolean autoRenew,
                                   Instant termStart, Instant termEnd, String beneficiaryEmail) {
        static SubscriptionView of(MarketplaceSubscription s) {
            return new SubscriptionView(s.getMarketplaceSubId(), s.getOfferId(), s.getMarketplacePlanId(),
                    s.getPlanKey(), s.getStatus(), s.isFreeTrial(), s.getAutoRenew(),
                    s.getTermStart(), s.getTermEnd(), s.getBeneficiaryEmail());
        }
    }
}
