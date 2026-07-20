package com.geneav.scan.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private RateLimitProperties props(int scanConcurrency, long acquireTimeoutMs) {
        RateLimitProperties p = new RateLimitProperties();
        p.setScanMaxConcurrent(scanConcurrency);
        p.setScanAcquireTimeoutMs(acquireTimeoutMs);
        return p;
    }

    @Test
    void enforcesCapacityPerKey() {
        RateLimiterService service = new RateLimiterService(props(4, 50));
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(2, 1);

        assertThat(service.tryAcquire("scan:1.1.1.1", limit)).isTrue();
        assertThat(service.tryAcquire("scan:1.1.1.1", limit)).isTrue();
        assertThat(service.tryAcquire("scan:1.1.1.1", limit)).isFalse();
    }

    @Test
    void keysAreIndependent() {
        RateLimiterService service = new RateLimiterService(props(4, 50));
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(1, 1);

        assertThat(service.tryAcquire("scan:1.1.1.1", limit)).isTrue();
        assertThat(service.tryAcquire("scan:1.1.1.1", limit)).isFalse();
        // A different client still has its full allowance.
        assertThat(service.tryAcquire("scan:2.2.2.2", limit)).isTrue();
    }

    @Test
    void scanSlotsAreLimitedAndReusable() throws InterruptedException {
        RateLimiterService service = new RateLimiterService(props(1, 50));

        assertThat(service.tryAcquireScanSlot()).isTrue();
        // Only one permit; the next attempt times out.
        assertThat(service.tryAcquireScanSlot()).isFalse();

        service.releaseScanSlot();
        assertThat(service.tryAcquireScanSlot()).isTrue();
    }

    @Test
    void evictionRemovesIdleBuckets() {
        RateLimitProperties p = props(4, 50);
        p.setIdleTtlMs(-1); // everything is "older than" now
        RateLimiterService service = new RateLimiterService(p);
        service.tryAcquire("chat:9.9.9.9", new RateLimitProperties.Limit(5, 5));

        service.evictIdleBuckets();

        // After eviction the key starts fresh with a full bucket again.
        assertThat(service.tryAcquire("chat:9.9.9.9", new RateLimitProperties.Limit(1, 1))).isTrue();
    }
}
