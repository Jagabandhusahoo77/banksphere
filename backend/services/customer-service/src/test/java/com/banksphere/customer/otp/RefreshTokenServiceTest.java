package com.banksphere.customer.otp;

import com.banksphere.customer.exception.RefreshTokenInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers Phase 9D's refresh-token rotation and reuse-detection algorithm
 * (ADR-009) — scenario 15 ("refresh token issued if implemented") and the
 * broader session-security requirement that a stolen, already-rotated
 * token can never be replayed successfully.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private OtpAuditLog auditLog;

    @Mock
    private RefreshTokenRevocationService revocationService;

    private final RefreshTokenProperties properties = new RefreshTokenProperties(1_209_600, false);
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(repository, properties, auditLog, revocationService);
        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static String sha256(String plaintext) throws Exception {
        byte[] hashBytes = MessageDigest.getInstance("SHA-256").digest(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    void issue_persistsOnlyAHash_neverThePlaintextToken() {
        UUID customerId = UUID.randomUUID();

        IssuedRefreshToken issued = refreshTokenService.issue(customerId);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(issued.plaintext());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(captor.getValue().getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
    }

    @Test
    void issue_generatesADifferentTokenEveryTime() {
        UUID customerId = UUID.randomUUID();

        IssuedRefreshToken first = refreshTokenService.issue(customerId);
        IssuedRefreshToken second = refreshTokenService.issue(customerId);

        assertThat(first.plaintext()).isNotEqualTo(second.plaintext());
    }

    @Test
    void rotate_revokesThePresentedTokenAndIssuesAFreshOne() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID presentedId = UUID.randomUUID();
        String plaintext = "presented-refresh-token-value";
        RefreshToken presented = RefreshToken.builder()
                .id(presentedId).customerId(customerId).tokenHash(sha256(plaintext))
                .status(RefreshTokenStatus.ACTIVE).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByTokenHash(sha256(plaintext))).thenReturn(Optional.of(presented));

        IssuedRefreshToken next = refreshTokenService.rotate(plaintext);

        assertThat(next.plaintext()).isNotEqualTo(plaintext);
        assertThat(presented.getStatus()).isEqualTo(RefreshTokenStatus.ROTATED);
        assertThat(presented.getReplacedByTokenId()).isEqualTo(next.tokenId());
    }

    @Test
    void rotate_rejectsAnUnknownToken() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("no-such-token"))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void rotate_rejectsAnExpiredToken() throws Exception {
        String plaintext = "expired-token";
        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID()).customerId(UUID.randomUUID()).tokenHash(sha256(plaintext))
                .status(RefreshTokenStatus.ACTIVE).expiresAt(Instant.now().minusSeconds(5)).build();
        when(repository.findByTokenHash(sha256(plaintext))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate(plaintext))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void rotate_rejectsAnAlreadyRevokedToken() throws Exception {
        String plaintext = "revoked-token";
        RefreshToken revoked = RefreshToken.builder()
                .id(UUID.randomUUID()).customerId(UUID.randomUUID()).tokenHash(sha256(plaintext))
                .status(RefreshTokenStatus.REVOKED).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByTokenHash(sha256(plaintext))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate(plaintext))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    /**
     * The core reuse-detection algorithm: presenting a token that was
     * already rotated out (its successor already exists) must revoke the
     * ENTIRE token family for that customer, not just reject the one
     * presented token — so a legitimate, currently-active token issued
     * from the same family also stops working. This is what makes token
     * theft detectable/containable rather than merely "one dead token."
     */
    /**
     * The revocation itself (finding every ACTIVE token for the customer,
     * flipping each to REVOKED) is verified in
     * RefreshTokenRevocationServiceTest — that class exists specifically
     * because a REQUIRES_NEW bean must be a real separate Spring bean,
     * not a same-class private method (see its own javadoc for why: this
     * exact bug was caught via live Docker verification, where
     * rotate()'s own transaction rolling back on the
     * RefreshTokenInvalidException it throws was silently undoing the
     * revocation). This test only proves rotate() actually DELEGATES to
     * it — an interaction test, not a state test.
     */
    @Test
    void rotate_detectsReuseOfAnAlreadyRotatedToken_andDelegatesFamilyRevocation() throws Exception {
        UUID customerId = UUID.randomUUID();
        String stolenPlaintext = "already-rotated-token";
        UUID stolenId = UUID.randomUUID();
        RefreshToken stolen = RefreshToken.builder()
                .id(stolenId).customerId(customerId).tokenHash(sha256(stolenPlaintext))
                .status(RefreshTokenStatus.ROTATED).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByTokenHash(sha256(stolenPlaintext))).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> refreshTokenService.rotate(stolenPlaintext))
                .isInstanceOf(RefreshTokenInvalidException.class);

        verify(auditLog).refreshTokenReuseDetected(customerId, stolenId);
        verify(revocationService).revokeAllForCustomer(customerId);
    }

    @Test
    void rotate_reuseDetection_neverLeaksTheReuseReasonToTheCaller() throws Exception {
        // Same exception type/message as every other invalid-refresh-token
        // case (unknown/expired/revoked) — see RefreshTokenInvalidException's
        // own javadoc: a caller must never be able to distinguish "this was
        // theft" from "this token simply expired."
        String stolenPlaintext = "already-rotated-token-2";
        RefreshToken stolen = RefreshToken.builder()
                .id(UUID.randomUUID()).customerId(UUID.randomUUID()).tokenHash(sha256(stolenPlaintext))
                .status(RefreshTokenStatus.ROTATED).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByTokenHash(sha256(stolenPlaintext))).thenReturn(Optional.of(stolen));

        assertThatThrownBy(() -> refreshTokenService.rotate(stolenPlaintext))
                .isInstanceOf(RefreshTokenInvalidException.class)
                .hasMessage("Invalid or expired refresh token");
    }

    @Test
    void revoke_marksAnActiveTokenRevoked() throws Exception {
        String plaintext = "logout-token";
        RefreshToken active = RefreshToken.builder()
                .id(UUID.randomUUID()).customerId(UUID.randomUUID()).tokenHash(sha256(plaintext))
                .status(RefreshTokenStatus.ACTIVE).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByTokenHash(sha256(plaintext))).thenReturn(Optional.of(active));

        refreshTokenService.revoke(plaintext);

        assertThat(active.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
        assertThat(active.getRevokedAt()).isNotNull();
    }

    @Test
    void revoke_isANoOp_forAnUnknownToken_neverThrows() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        refreshTokenService.revoke("no-such-token");

        verify(repository, never()).save(any());
    }
}
