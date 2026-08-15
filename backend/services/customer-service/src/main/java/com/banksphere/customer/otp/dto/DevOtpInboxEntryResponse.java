package com.banksphere.customer.otp.dto;

import com.banksphere.customer.otp.DevOtpInbox;
import com.banksphere.customer.otp.OtpPurpose;

import java.time.Instant;
import java.util.UUID;

/** Deliberately carries the real, plaintext OTP — that is this endpoint's entire purpose. See {@code DevOtpInboxController}'s own javadoc for why this is safe only because the whole endpoint is dev-profile-gated. */
public record DevOtpInboxEntryResponse(UUID challengeId, String identifier, OtpPurpose purpose, String otp, Instant createdAt, Instant expiresAt) {

    public static DevOtpInboxEntryResponse from(DevOtpInbox.Entry entry) {
        return new DevOtpInboxEntryResponse(
                UUID.fromString(entry.challengeId()), entry.identifier(), entry.purpose(), entry.otp(), entry.createdAt(), entry.expiresAt());
    }
}
