package com.geneav.scan.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Holds the per-client token buckets and the global scan-concurrency permit set.
 *
 * <p>Buckets are created lazily per {@code key} (usually "type:clientIp") and
 * swept periodically once idle so a flood of distinct IPs cannot grow the map
 * without bound.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties props;
    private final Semaphore scanPermits;

    public RateLimiterService(RateLimitProperties props) {
        this.props = props;
        this.scanPermits = new Semaphore(Math.max(1, props.getScanMaxConcurrent()), true);
    }

    /**
     * Consumes one token from the bucket identified by {@code key}, creating it
     * (full) on first use with the given shape. Returns true if allowed.
     */
    public boolean tryAcquire(String key, RateLimitProperties.Limit limit) {
        return bucket(key, limit).tryConsume();
    }

    /** Whole seconds the client should wait before retrying {@code key}. */
    public long retryAfterSeconds(String key, RateLimitProperties.Limit limit) {
        return bucket(key, limit).retryAfterSeconds();
    }

    /** Tries to reserve a scan slot, waiting up to the configured timeout. */
    public boolean tryAcquireScanSlot() throws InterruptedException {
        return scanPermits.tryAcquire(props.getScanAcquireTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    /** Releases a slot previously reserved via {@link #tryAcquireScanSlot()}. */
    public void releaseScanSlot() {
        scanPermits.release();
    }

    private TokenBucket bucket(String key, RateLimitProperties.Limit limit) {
        return buckets.computeIfAbsent(key,
                k -> new TokenBucket(limit.getCapacity(), limit.getRefillPerMinute()));
    }

    @Scheduled(fixedDelayString = "${geneav.ratelimit.eviction-interval-ms:300000}")
    void evictIdleBuckets() {
        long cutoff = System.currentTimeMillis() - props.getIdleTtlMs();
        int before = buckets.size();
        buckets.values().removeIf(b -> b.lastAccessMs() < cutoff);
        int removed = before - buckets.size();
        if (removed > 0) {
            log.debug("rate-limit: evicted {} idle bucket(s), {} remaining", removed, buckets.size());
        }
    }
}
