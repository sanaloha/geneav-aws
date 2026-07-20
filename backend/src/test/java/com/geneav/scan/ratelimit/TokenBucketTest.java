package com.geneav.scan.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    @Test
    void allowsBurstUpToCapacityThenBlocks() {
        TokenBucket bucket = new TokenBucket(3, 1); // 3 burst, ~1/min refill

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        // 6000/min == 100/sec, so one token returns in ~10ms.
        TokenBucket bucket = new TokenBucket(1, 6000);

        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.tryConsume()).isFalse();

        Thread.sleep(40);
        assertThat(bucket.tryConsume()).isTrue();
    }

    @Test
    void retryAfterIsAtLeastOneSecondWhenEmpty() {
        TokenBucket bucket = new TokenBucket(1, 1);
        assertThat(bucket.tryConsume()).isTrue();
        assertThat(bucket.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }
}
