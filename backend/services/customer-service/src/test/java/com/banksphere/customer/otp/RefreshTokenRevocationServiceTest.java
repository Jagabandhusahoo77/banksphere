package com.banksphere.customer.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The actual family-wide revocation mutation — deliberately a standalone
 * unit test, separate from {@code RefreshTokenServiceTest}, since this
 * class only exists to run in its own {@code REQUIRES_NEW} transaction
 * (see its own javadoc, and the live-Docker-verification bug this class
 * was extracted to fix: the revocation was previously being silently
 * rolled back by the very exception {@code RefreshTokenService.rotate}
 * throws right after triggering it).
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRevocationServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenRevocationService revocationService;

    @BeforeEach
    void setUp() {
        revocationService = new RefreshTokenRevocationService(repository);
    }

    @Test
    void revokeAllForCustomer_revokesEveryActiveTokenForThatCustomer() {
        UUID customerId = UUID.randomUUID();
        RefreshToken tokenA = RefreshToken.builder()
                .id(UUID.randomUUID()).customerId(customerId).tokenHash("hash-a")
                .status(RefreshTokenStatus.ACTIVE).expiresAt(Instant.now().plusSeconds(3600)).build();
        RefreshToken tokenB = RefreshToken.builder()
                .id(UUID.randomUUID()).customerId(customerId).tokenHash("hash-b")
                .status(RefreshTokenStatus.ACTIVE).expiresAt(Instant.now().plusSeconds(3600)).build();
        when(repository.findByCustomerIdAndStatus(customerId, RefreshTokenStatus.ACTIVE))
                .thenReturn(List.of(tokenA, tokenB));

        revocationService.revokeAllForCustomer(customerId);

        assertThat(tokenA.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
        assertThat(tokenA.getRevokedAt()).isNotNull();
        assertThat(tokenB.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
        assertThat(tokenB.getRevokedAt()).isNotNull();
        verify(repository).saveAll(List.of(tokenA, tokenB));
    }

    @Test
    void revokeAllForCustomer_isANoOp_whenNoActiveTokensExist() {
        UUID customerId = UUID.randomUUID();
        when(repository.findByCustomerIdAndStatus(customerId, RefreshTokenStatus.ACTIVE)).thenReturn(List.of());

        revocationService.revokeAllForCustomer(customerId);

        verify(repository).saveAll(List.of());
    }
}
