package com.banksphere.customer.otp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    /** The resend-cooldown check — see OtpServiceImpl#requestOtp. */
    List<OtpChallenge> findByIdentifierAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
            String identifier, OtpPurpose purpose, Instant since);

    /** The dev-only OTP inbox — never used by any production code path. */
    List<OtpChallenge> findTop20ByOrderByCreatedAtDesc();
}
