package com.geneav.scan.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A credential belonging to an {@link Account}. Only the SHA-256 hash of the
 * secret is stored; the plaintext key is shown to the user exactly once, at
 * creation. {@code keyPrefix} and {@code lastFour} exist purely for display.
 */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    private String name;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "last_four", nullable = false)
    private String lastFour;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
        // for JPA
    }

    public ApiKey(UUID id, UUID accountId, String name, String keyHash, String keyPrefix,
                  String lastFour, String status, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.lastFour = lastFour;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getLastFour() {
        return lastFour;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isActive() {
        return "active".equals(status) && revokedAt == null;
    }

    public void revoke(Instant when) {
        this.status = "revoked";
        this.revokedAt = when;
    }
}
