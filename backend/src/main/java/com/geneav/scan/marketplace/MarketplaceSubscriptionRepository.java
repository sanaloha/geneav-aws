package com.geneav.scan.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketplaceSubscriptionRepository extends JpaRepository<MarketplaceSubscription, UUID> {

    Optional<MarketplaceSubscription> findByMarketplaceSubId(UUID marketplaceSubId);

    /** The account's live subscription, if any (unique by partial index). */
    @Query("""
            select s from MarketplaceSubscription s
            where s.accountId = :accountId
              and s.status in ('PendingFulfillmentStart', 'Subscribed', 'Suspended')
            """)
    Optional<MarketplaceSubscription> findLiveByAccountId(UUID accountId);

    /**
     * Subscriptions whose paid term has ended without a renewal: suspended ones
     * that were never reinstated, and unsubscribed ones whose account may have
     * missed the downgrade (e.g. a webhook that exhausted its retries).
     */
    @Query("""
            select s from MarketplaceSubscription s
            where s.status in ('Suspended', 'Unsubscribed')
              and s.termEnd is not null and s.termEnd < :now
            """)
    List<MarketplaceSubscription> findLapsed(Instant now);
}
