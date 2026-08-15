package com.banksphere.customer.otp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Scenario 9 ("rate limiting works") at the sliding-window primitive level — see OtpServiceImpl's IP-keyed usage. */
class OtpRateLimiterTest {

    private final OtpRateLimiter rateLimiter = new OtpRateLimiter();

    @Test
    void tryAcquire_allowsUpToTheConfiguredMax() {
        String key = "otp-request:203.0.113.1";

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryAcquire(key, 5, 900)).isTrue();
        }
    }

    @Test
    void tryAcquire_rejectsOnceMaxIsExceeded() {
        String key = "otp-request:203.0.113.2";
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(key, 5, 900);
        }

        assertThat(rateLimiter.tryAcquire(key, 5, 900)).isFalse();
    }

    @Test
    void tryAcquire_keysAreIndependent() {
        String keyA = "otp-request:203.0.113.3";
        String keyB = "otp-request:203.0.113.4";
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(keyA, 5, 900);
        }

        // Exhausting keyA's window must not affect a completely different key.
        assertThat(rateLimiter.tryAcquire(keyB, 5, 900)).isTrue();
    }

    @Test
    void tryAcquire_allowsAgain_onceTheWindowHasElapsed() throws InterruptedException {
        String key = "otp-request:203.0.113.5";
        assertThat(rateLimiter.tryAcquire(key, 1, 1)).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 1, 1)).isFalse();

        Thread.sleep(1100);

        assertThat(rateLimiter.tryAcquire(key, 1, 1)).isTrue();
    }
}
