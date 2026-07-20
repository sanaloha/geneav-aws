package com.geneav.scan.ratelimit;

/**
 * A thread-safe token bucket. Starts full at {@code capacity} and refills
 * continuously at {@code refillPerSecond}; each accepted request consumes one
 * token. Bursts up to the capacity are allowed, then callers are throttled to
 * the sustained refill rate.
 *
 * <p>Kept dependency-free on purpose — in-memory is sufficient for the single-VM
 * deployment. If geneav ever scales horizontally, move this to a shared store
 * (e.g. Redis) so limits are enforced across instances.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillPerMs;

    private double tokens;
    private long lastRefillMs;
    private volatile long lastAccessMs;

    TokenBucket(long capacity, double refillPerMinute) {
        this.capacity = capacity;
        this.refillPerMs = refillPerMinute / 60_000.0;
        this.tokens = capacity;
        long now = System.currentTimeMillis();
        this.lastRefillMs = now;
        this.lastAccessMs = now;
    }

    /** Attempts to consume one token. Returns true if a token was available. */
    synchronized boolean tryConsume() {
        long now = System.currentTimeMillis();
        lastAccessMs = now;
        refill(now);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Whole seconds until the next token is available (>= 1 once throttled). */
    synchronized long retryAfterSeconds() {
        refill(System.currentTimeMillis());
        if (tokens >= 1.0 || refillPerMs <= 0.0) {
            return 1;
        }
        double neededMs = (1.0 - tokens) / refillPerMs;
        return Math.max(1, (long) Math.ceil(neededMs / 1000.0));
    }

    long lastAccessMs() {
        return lastAccessMs;
    }

    private void refill(long now) {
        long elapsed = now - lastRefillMs;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerMs);
            lastRefillMs = now;
        }
    }
}
