package com.banksphere.customer.otp;

import com.banksphere.customer.exception.RefreshTokenInvalidException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens are opaque, high-entropy random values — never JWTs (see
 * ADR-009: a refresh token needs no self-describing claims, and keeping
 * it opaque means the stored hash reveals nothing if it ever leaked).
 * Rotated on every use; reuse of an already-rotated token revokes the
 * entire token family (every active token for that customer) — the
 * standard "refresh token rotation with reuse detection" algorithm. See
 * {@link OtpAuditLog#refreshTokenReuseDetected}.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final RefreshTokenProperties properties;
    private final OtpAuditLog auditLog;
    private final RefreshTokenRevocationService revocationService;

    @Transactional
    public IssuedRefreshToken issue(UUID customerId) {
        String plaintext = generatePlaintext();
        RefreshToken token = RefreshToken.builder()
                .customerId(customerId)
                .tokenHash(hash(plaintext))
                .expiresAt(Instant.now().plusSeconds(properties.expirySeconds()))
                .build();
        token = repository.save(token);
        return new IssuedRefreshToken(plaintext, token.getId(), token.getCustomerId(), token.getExpiresAt());
    }

    /**
     * Validates {@code presentedPlaintext}, rotates it (revokes the
     * presented token, issues a fresh one), and returns the new token —
     * or throws {@link RefreshTokenInvalidException} for any failure,
     * including the reuse-detection case (which additionally revokes
     * every other active token for the same customer as a side effect,
     * logged via {@link OtpAuditLog#refreshTokenReuseDetected}, before
     * throwing the same generic exception a customer would see for any
     * other invalid-token reason).
     */
    @Transactional
    public IssuedRefreshToken rotate(String presentedPlaintext) {
        RefreshToken presented = repository.findByTokenHash(hash(presentedPlaintext))
                .orElseThrow(RefreshTokenInvalidException::new);

        if (presented.getStatus() == RefreshTokenStatus.ROTATED) {
            // This exact token was already used once before to rotate —
            // presenting it again means either a client retried a stale
            // cookie (benign) or the token was stolen and is now racing
            // the legitimate client (not benign). Since the two can't be
            // distinguished from here, treat every case as compromise:
            // revoke the whole family so a stolen token can't be used
            // again either, even if this particular presentation was
            // innocent.
            auditLog.refreshTokenReuseDetected(presented.getCustomerId(), presented.getId());
            // REQUIRES_NEW, via a separate bean — see
            // RefreshTokenRevocationService's own javadoc for why this
            // must NOT run inside this method's own transaction (the
            // RefreshTokenInvalidException thrown two lines down would
            // otherwise roll it back).
            revocationService.revokeAllForCustomer(presented.getCustomerId());
            throw new RefreshTokenInvalidException();
        }
        if (presented.getStatus() == RefreshTokenStatus.REVOKED || presented.isExpired()) {
            throw new RefreshTokenInvalidException();
        }

        IssuedRefreshToken next = issue(presented.getCustomerId());
        presented.setStatus(RefreshTokenStatus.ROTATED);
        presented.setReplacedByTokenId(next.tokenId());
        repository.save(presented);

        return next;
    }

    @Transactional
    public void revoke(String presentedPlaintext) {
        Optional<RefreshToken> token = repository.findByTokenHash(hash(presentedPlaintext));
        token.ifPresent(t -> {
            if (t.getStatus() == RefreshTokenStatus.ACTIVE) {
                t.setStatus(RefreshTokenStatus.REVOKED);
                t.setRevokedAt(Instant.now());
                repository.save(t);
            }
        });
    }

    private String generatePlaintext() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256, not BCrypt — see this class's own javadoc for why a slow, salted hash isn't needed for an already-high-entropy random value. */
    private String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
