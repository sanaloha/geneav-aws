package com.geneav.scan.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A customer of the API. Owns one or more {@link ApiKey}s and a plan. */
@Entity
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Plan key, resolved against the configured plan catalog (e.g. "free", "pro"). */
    @Column(nullable = false)
    private String plan;

    @Column(nullable = false)
    private String status;

    /** BCrypt hash of the password, or null for accounts without a password (e.g. OAuth). */
    @Column(name = "password_hash")
    private String passwordHash;

    /** How this account authenticates: "password" or a federated provider like "google". */
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
