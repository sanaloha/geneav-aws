package com.geneav.scan.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for per-client rate limiting and scan concurrency. All values are
 * overridable via environment variables (see application.yml) so limits can be
 * adjusted in production without a rebuild.
 */
@ConfigurationProperties(prefix = "geneav.ratelimit")
public class RateLimitProperties {

    /** Master switch. When false the filter passes every request through. */
    private boolean enabled = true;

    /** Limit for POST /api/v1/scan (protects the ClamAV engine). */
    private Limit scan = new Limit(10, 10);

    /** Limit for POST /api/v1/chat (protects the paid OpenAI budget). */
    private Limit chat = new Limit(15, 15);

    /** Fallback limit for any other /api/v1/** endpoint. */
    private Limit other = new Limit(60, 60);

    /** Max scans processed simultaneously; excess callers wait then get 429. */
    private int scanMaxConcurrent = 4;

    /** How long a scan request waits for a concurrency slot before giving up. */
    private long scanAcquireTimeoutMs = 2_000;

    /** Idle buckets older than this are evicted to bound memory. */
    private long idleTtlMs = 900_000;

    /** How often the idle-bucket sweep runs. */
    private long evictionIntervalMs = 300_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Limit getScan() {
        return scan;
    }

    public void setScan(Limit scan) {
        this.scan = scan;
    }

    public Limit getChat() {
        return chat;
    }

    public void setChat(Limit chat) {
        this.chat = chat;
    }

    public Limit getOther() {
        return other;
    }

    public void setOther(Limit other) {
        this.other = other;
    }

    public int getScanMaxConcurrent() {
        return scanMaxConcurrent;
    }

    public void setScanMaxConcurrent(int scanMaxConcurrent) {
        this.scanMaxConcurrent = scanMaxConcurrent;
    }

    public long getScanAcquireTimeoutMs() {
        return scanAcquireTimeoutMs;
    }

    public void setScanAcquireTimeoutMs(long scanAcquireTimeoutMs) {
        this.scanAcquireTimeoutMs = scanAcquireTimeoutMs;
    }

    public long getIdleTtlMs() {
        return idleTtlMs;
    }

    public void setIdleTtlMs(long idleTtlMs) {
        this.idleTtlMs = idleTtlMs;
    }

    public long getEvictionIntervalMs() {
        return evictionIntervalMs;
    }

    public void setEvictionIntervalMs(long evictionIntervalMs) {
        this.evictionIntervalMs = evictionIntervalMs;
    }

    /** A single bucket's shape: burst {@code capacity} and sustained {@code refillPerMinute}. */
    public static class Limit {
        private long capacity;
        private double refillPerMinute;

        public Limit() {
        }

        public Limit(long capacity, double refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public double getRefillPerMinute() {
            return refillPerMinute;
        }

        public void setRefillPerMinute(double refillPerMinute) {
            this.refillPerMinute = refillPerMinute;
        }
    }
}
