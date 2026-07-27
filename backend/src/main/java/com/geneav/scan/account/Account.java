package com.geneav.scan.account;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A customer of the API. Owns one or more {@link ApiKey}s and a plan. */
@Entity
@Table(name = "account")
public class Account {

    /**
     * Providers whose credentials geneav itself holds. For anything else the
     * password lives at the identity provider, so we must not mint one here.
     */
    private static final Set<String> LOCAL_AUTH_PROVIDERS = Set.of("password", "apikey");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Plan key, resolved against the configured plan catalog (e.g. "free", "pro"). */
    @Column(nullable = false)
    private String plan;

    /**
     * Where the current plan came from: "none" for the self-serve free tier, or
     * "marketplace" when a live Microsoft Marketplace subscription set it. Only
     * billing code writes this; the scan/quota path never reads it.
     */
    @Column(name = "billing_source", nullable = false)
    private String billingSource = "none";

    @Column(nullable = false)
    private String status;

    /**
     * BCrypt hash of the password, or null for an account that has never set one —
     * a federated (e.g. Google) account, or one provisioned for API-key use only.
     * A null hash on its own says nothing about whether a password may be set;
     * {@link #isFederated()} is the signal for that.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * How this account authenticates: "password", "apikey", or a federated
     * provider like "google".
     */
    @Column(name = "auth_provider", nullable = false)
    private String authProvider = "password";

    /** Stable subject id from the federated provider, or null for password accounts. */
    @Column(name = "provider_subject")
    private String providerSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When the password last changed. Sessions authenticated before this are
     * rejected, which signs the account out everywhere else. Null means never
     * changed, so pre-existing sessions are unaffected.
     */
    @Column(name = "credentials_changed_at")
    private Instant credentialsChangedAt;

    /**
     * Which channel produced this signup, or null if it arrived untagged or
     * predates attribution capture. Set once at signup and never revised — the
     * question it answers is "what earned this account", not "where has this
     * customer been since".
     */
    @Embedded
    private SignupAttribution attribution;

    protected Account() {
        // for JPA
    }

    public Account(UUID id, String email, String plan, String status, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.plan = plan;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getBillingSource() {
        return billingSource;
    }

    public void setBillingSource(String billingSource) {
        this.billingSource = billingSource;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    /**
     * Whether sign-in is delegated to an external identity provider, in which
     * case geneav must never set a local password for the account.
     *
     * <p>An unrecognised provider counts as federated: a provider added later
     * is then refused until it is deliberately listed as local, rather than
     * silently becoming password-resettable.
     */
    public boolean isFederated() {
        return !LOCAL_AUTH_PROVIDERS.contains(authProvider);
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = providerSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    public void setCredentialsChangedAt(Instant credentialsChangedAt) {
        this.credentialsChangedAt = credentialsChangedAt;
    }

    public SignupAttribution getAttribution() {
        return attribution;
    }

    public void setAttribution(SignupAttribution attribution) {
        this.attribution = attribution;
    }

    /**
     * Whether a session authenticated at {@code sessionAuthenticatedAt} is still
     * valid. A session with no recorded time is only trusted if the password has
     * never changed.
     */
    public boolean acceptsSessionFrom(Instant sessionAuthenticatedAt) {
        if (credentialsChangedAt == null) {
            return true;
        }
        return sessionAuthenticatedAt != null && !sessionAuthenticatedAt.isBefore(credentialsChangedAt);
    }
}
