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
}
