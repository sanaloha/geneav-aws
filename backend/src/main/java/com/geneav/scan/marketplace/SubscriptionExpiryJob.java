package com.geneav.scan.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Safety-net sweep for marketplace entitlements, in the style of
 * {@link com.geneav.scan.account.PasswordResetCleanupJob}: downgrades accounts
 * whose subscription lapsed (suspended past term end, or an Unsubscribe whose
 * webhook never landed). The webhook is the primary signal; this job only
 * repairs what it missed.
 */
@Component
public class SubscriptionExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryJob.class);

    private final MarketplaceService marketplace;
    private final MarketplaceProperties props;

    public SubscriptionExpiryJob(MarketplaceService marketplace, MarketplaceProperties props) {
        this.marketplace = marketplace;
        this.props = props;
    }

    /** {@code fixedDelay} so a slow sweep never overlaps itself. */
    @Scheduled(fixedDelayString = "${geneav.marketplace.expiry-sweep-interval-ms:3600000}",
            initialDelayString = "${geneav.marketplace.expiry-sweep-initial-delay-ms:120000}")
    public void sweep() {
        if (!props.isEnabled()) {
            return;
        }
        int downgraded = marketplace.expireLapsed(Instant.now());
        if (downgraded > 0) {
            log.info("Downgraded {} account(s) with lapsed marketplace subscriptions", downgraded);
        }
    }
}
