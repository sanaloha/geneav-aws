package com.geneav.scan.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One Microsoft Marketplace SaaS subscription, mirroring Microsoft's lifecycle
 * for an {@code account}. The account's entitlement lives on
 * {@code account.plan}; this row records where it came from and what Microsoft
 * currently believes about it.
 */
@Entity
@Table(name = "marketplace_subscription")
public class MarketplaceSubscription {

    /** Statuses that count as "live" for the one-live-subscription rule. */
    public static final String STATUS_PENDING = "PendingFulfillmentStart";
    public static final String STATUS_SUBSCRIBED = "Subscribed";
    public static final String STATUS_SUSPENDED = "Suspended";
    public static final String STATUS_UNSUBSCRIBED = "Unsubscribed";

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Microsoft's SaaS subscription id — the key every fulfillment call uses. */
    @Column(name = "marketplace_sub_id", nullable = false, unique = true)
    private UUID marketplaceSubId;

    @Column(name = "offer_id", nullable = false)
    private String offerId;

    /** Partner Center plan id, e.g. {@code geneav-pro}. */
    @Column(name = "marketplace_plan_id", nullable = false)
    private String marketplacePlanId;

    /** geneav plan key the marketplace plan maps to, e.g. {@code pro}. */
    @Column(name = "plan_key", nullable = false)
    private String planKey;

    /** Purchased seat count; null — geneav plans are not per-seat. */
    @Column(name = "quantity")
    private Integer quantity;

    @Column(nullable = false)
    private String status;

    @Column(name = "is_free_trial", nullable = false)
    private boolean freeTrial;

    @Column(name = "is_test", nullable = false)
    private boolean test;

    @Column(name = "auto_renew")
    private Boolean autoRenew;

    @Column(name = "term_start")
    private Instant termStart;

    @Column(name = "term_end")
    private Instant termEnd;

    @Column(name = "beneficiary_email")
    private String beneficiaryEmail;

    @Column(name = "beneficiary_tenant_id")
    private UUID beneficiaryTenantId;

    @Column(name = "purchaser_email")
    private String purchaserEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MarketplaceSubscription() {
        // for JPA
    }

    public MarketplaceSubscription(UUID id, UUID accountId, UUID marketplaceSubId, String offerId,
                                   String marketplacePlanId, String planKey, String status, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.marketplaceSubId = marketplaceSubId;
        this.offerId = offerId;
        this.marketplacePlanId = marketplacePlanId;
        this.planKey = planKey;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getMarketplaceSubId() {
        return marketplaceSubId;
    }

    public String getOfferId() {
        return offerId;
    }

    public String getMarketplacePlanId() {
        return marketplacePlanId;
    }

    public void setMarketplacePlanId(String marketplacePlanId) {
        this.marketplacePlanId = marketplacePlanId;
    }

    public String getPlanKey() {
        return planKey;
    }

    public void setPlanKey(String planKey) {
        this.planKey = planKey;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isLive() {
        return STATUS_PENDING.equals(status) || STATUS_SUBSCRIBED.equals(status)
                || STATUS_SUSPENDED.equals(status);
    }

    public boolean isFreeTrial() {
        return freeTrial;
    }

    public void setFreeTrial(boolean freeTrial) {
        this.freeTrial = freeTrial;
    }

    public boolean isTest() {
        return test;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public Boolean getAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(Boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public Instant getTermStart() {
        return termStart;
    }

    public void setTermStart(Instant termStart) {
        this.termStart = termStart;
    }

    public Instant getTermEnd() {
        return termEnd;
    }

    public void setTermEnd(Instant termEnd) {
        this.termEnd = termEnd;
    }

    public String getBeneficiaryEmail() {
        return beneficiaryEmail;
    }

    public void setBeneficiaryEmail(String beneficiaryEmail) {
        this.beneficiaryEmail = beneficiaryEmail;
    }

    public UUID getBeneficiaryTenantId() {
        return beneficiaryTenantId;
    }

    public void setBeneficiaryTenantId(UUID beneficiaryTenantId) {
        this.beneficiaryTenantId = beneficiaryTenantId;
    }

    public String getPurchaserEmail() {
        return purchaserEmail;
    }

    public void setPurchaserEmail(String purchaserEmail) {
        this.purchaserEmail = purchaserEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(Instant when) {
        this.updatedAt = when;
    }
}
