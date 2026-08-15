package com.banksphere.customer.otp;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Binds a step-up challenge to the exact operation it authorizes — the
 * mechanism behind "request OTP for ₹1,000, then submit ₹100,000 must
 * fail" (see ADR-009). The caller supplies the operation's fields as an
 * already-ordered list of canonical strings (e.g. {@code [sourceAccountId,
 * destinationAccountNumber, destinationIfsc, amount.toPlainString(),
 * currency]} for a transfer) — deliberately not a generic JSON
 * serialization of an arbitrary object, since JSON key ordering/number
 * formatting are not guaranteed stable across serializer versions and
 * this hash must be recomputed identically, twice, in two different
 * services (customer-service at request/verify time, account-service's
 * confirm call at execution time).
 */
@Component
public class OtpContextHasher {

    /**
     * ASCII unit separator (0x1F) between fields — vanishingly unlikely
     * to appear in any real business field (account numbers, IFSC codes,
     * amounts), unlike a printable delimiter such as "|" or "," which a
     * free-text description field could plausibly contain.
     */
    private static final char DELIMITER = 0x1F;

    public String hash(List<String> canonicalParts) {
        String canonical = String.join(String.valueOf(DELIMITER), canonicalParts);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed available on every JVM — this is unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
