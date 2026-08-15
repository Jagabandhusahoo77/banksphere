package com.banksphere.customer.otp;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory, single-JVM-instance sliding-window rate limiter, keyed by an
 * arbitrary string (an IP address or an identifier — see
 * {@code OtpServiceImpl}'s two call sites). <b>This is a local/demo
 * implementation only.</b> It has no shared state across instances, so it
 * provides no real protection the moment this service runs behind a
 * load balancer with more than one replica — an attacker could simply
 * spread requests across instances. Production deployment should use
 * distributed rate limiting (e.g. Redis with a sliding-window or
 * token-bucket algorithm) — see ADR-009 and CLAUDE.md's honesty
 * requirement: this is explicitly documented as a demo-only limitation,
 * not claimed as production-ready.
 */
@Component
public class OtpRateLimiter {

    private final ConcurrentMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /**
     * Records one hit for {@code key} and returns {@code true} if the
     * number of hits within {@code windowSeconds} (including this one)
     * is at or under {@code maxHits} — i.e. {@code true} means "allowed."
     */
    public boolean tryAcquire(String key, int maxHits, long windowSeconds) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);
        Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxHits) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}
