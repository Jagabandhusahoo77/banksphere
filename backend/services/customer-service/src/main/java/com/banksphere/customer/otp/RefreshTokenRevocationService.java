package com.banksphere.customer.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Runs the reuse-detection family-wide revocation in its OWN transaction
 * ({@code REQUIRES_NEW}), deliberately independent of {@link
 * RefreshTokenService#rotate}'s own {@code @Transactional} boundary — the
 * exact same pattern account-service's {@code TransferIdempotencyService}
 * uses, and for the same reason.
 *
 * <p><b>Why this has to be a separate bean:</b> {@code rotate()} calls
 * this revocation and then immediately throws {@code
 * RefreshTokenInvalidException} to reject the presented (stolen/replayed)
 * token. If the revocation ran inside {@code rotate()}'s own transaction,
 * Spring's default rollback-on-{@code RuntimeException} behavior would
 * roll back the ENTIRE transaction when that exception propagates —
 * including the revocations just written moments earlier — silently
 * reverting every "stolen session" back to {@code ACTIVE} and defeating
 * reuse detection entirely, while still correctly rejecting the one
 * request that triggered it. {@code REQUIRES_NEW} commits the revocation
 * to the database before control ever returns to {@code rotate()}, so it
 * survives regardless of what {@code rotate()} does next. Must be called
 * through the Spring proxy — a same-class private method would not get
 * its own transaction (JDK/CGLIB proxies don't intercept self-invocation).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenRevocationService {

    private final RefreshTokenRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForCustomer(UUID customerId) {
        List<RefreshToken> active = repository.findByCustomerIdAndStatus(customerId, RefreshTokenStatus.ACTIVE);
        Instant now = Instant.now();
        for (RefreshToken token : active) {
            token.setStatus(RefreshTokenStatus.REVOKED);
            token.setRevokedAt(now);
        }
        repository.saveAll(active);
    }
}
