package com.banksphere.customer.otp;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * An in-memory, single-instance, most-recent-50 record of delivered OTPs
 * — exists purely so local development/testing/E2E scripts can retrieve
 * the "delivered" OTP without a real SMS/email/WhatsApp provider. Never
 * persisted, never shared across instances. Reachable only through
 * {@link DevOtpInboxController}, which does not exist at all in the
 * application context unless {@link DevOtpInboxProperties#enabled()} is
 * true (see its own javadoc) — this class has no gate of its own, since
 * that one gate at the controller/bean level is the single source of
 * truth for whether this feature is exposed.
 */
@Component
public class DevOtpInbox {

    private static final int MAX_ENTRIES = 50;

    public record Entry(String challengeId, String identifier, OtpPurpose purpose, String otp, Instant createdAt, Instant expiresAt) {
    }

    private final Deque<Entry> entries = new ConcurrentLinkedDeque<>();

    public void record(String challengeId, String identifier, OtpPurpose purpose, String otp, Instant createdAt, Instant expiresAt) {
        entries.addFirst(new Entry(challengeId, identifier, purpose, otp, createdAt, expiresAt));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    public List<Entry> recent() {
        return List.copyOf(entries);
    }
}
