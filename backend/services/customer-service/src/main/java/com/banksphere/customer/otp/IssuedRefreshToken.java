package com.banksphere.customer.otp;

import java.time.Instant;
import java.util.UUID;

/** {@code plaintext} is the only time the real token value exists outside a cookie — never logged, never persisted. */
public record IssuedRefreshToken(String plaintext, UUID tokenId, UUID customerId, Instant expiresAt) {
}
