package com.geneav.scan.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One received Marketplace webhook notification. The unique {@code event_id}
 * constraint is the idempotency guard: Microsoft retries webhook delivery up to
 * 500 times over eight hours, and every retry after the first insert must be a
 * free no-op.
 */
@Entity
@Table(name = "marketplace_event")
public class MarketplaceEvent {

    @Id
    private UUID id;

    /** The payload {@code id}; for actionable events it is also the operationId to ACK. */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    /** Microsoft's subscription id (not our row id); null if the payload lacked one. */
    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected MarketplaceEvent() {
        // for JPA
    }

    public MarketplaceEvent(UUID id, UUID eventId, UUID subscriptionId, String action,
                            String status, String payload, Instant receivedAt) {
        this.id = id;
        this.eventId = eventId;
        this.subscriptionId = subscriptionId;
        this.action = action;
        this.status = status;
        this.payload = payload;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getAction() {
        return action;
    }

    public String getStatus() {
        return status;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
