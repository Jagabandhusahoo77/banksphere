package com.banksphere.customer.otp;

import com.banksphere.customer.entity.Customer;
import com.banksphere.customer.entity.CustomerCredentials;
import com.banksphere.customer.exception.InvalidOtpException;
import com.banksphere.customer.exception.OtpChallengeNotFoundException;
import com.banksphere.customer.exception.OtpRateLimitExceededException;
import com.banksphere.customer.exception.StepUpAccessDeniedException;
import com.banksphere.customer.exception.StepUpContextMismatchException;
import com.banksphere.customer.exception.StepUpNotReadyException;
import com.banksphere.customer.otp.dto.OtpRequestResponse;
import com.banksphere.customer.otp.dto.StepUpConfirmRequest;
import com.banksphere.customer.otp.dto.StepUpConfirmResponse;
import com.banksphere.customer.otp.dto.StepUpRequestRequest;
import com.banksphere.customer.otp.dto.StepUpRequestResponse;
import com.banksphere.customer.otp.dto.StepUpVerifyResponse;
import com.banksphere.customer.repository.CustomerCredentialsRepository;
import com.banksphere.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final int REQUEST_RATE_LIMIT_MAX = 10;
    private static final long REQUEST_RATE_LIMIT_WINDOW_SECONDS = 900; // 15 minutes, per IP
    private static final int VERIFY_RATE_LIMIT_MAX = 20;
    private static final long VERIFY_RATE_LIMIT_WINDOW_SECONDS = 900; // 15 minutes, per IP

    private final OtpChallengeRepository challengeRepository;
    private final CustomerRepository customerRepository;
    private final CustomerCredentialsRepository credentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpGenerator otpGenerator;
    private final OtpContextHasher contextHasher;
    private final OtpDeliveryProvider deliveryProvider;
    private final OtpRateLimiter rateLimiter;
    private final OtpProperties otpProperties;
    private final DevOtpInboxProperties devOtpInboxProperties;
    private final DevOtpInbox devOtpInbox;
    private final OtpAuditLog auditLog;

    @Override
    @Transactional
    public OtpRequestResponse requestLoginOtp(String identifier, String clientIp) {
        enforceIpRateLimit(clientIp, "otp-request");
        enforceResendCooldown(identifier, OtpPurpose.LOGIN);

        Optional<Customer> customer = findEligibleCustomer(identifier);

        GeneratedChallenge generated = createChallenge(identifier, OtpPurpose.LOGIN, customer.map(Customer::getId).orElse(null), null, clientIp);

        auditLog.otpRequested(identifier, OtpPurpose.LOGIN, generated.challenge().getId(), generated.challenge().getCustomerId());
        if (customer.isPresent()) {
            deliverOtp(generated.challenge(), generated.plaintextOtp());
        }

        return OtpRequestResponse.of(generated.challenge().getId());
    }

    @Override
    @Transactional
    public OtpVerificationResult verifyLoginOtp(UUID challengeId, String otp, String clientIp) {
        enforceIpRateLimit(clientIp, "otp-verify");

        OtpChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(OtpChallengeNotFoundException::new);

        requirePurpose(challenge, OtpPurpose.LOGIN);
        UUID customerId = validateAndConsume(challenge, otp);

        challenge.setStatus(OtpChallengeStatus.CONSUMED);
        challenge.setConsumedAt(Instant.now());
        challengeRepository.save(challenge);

        auditLog.otpVerificationSucceeded(challenge.getIdentifier(), OtpPurpose.LOGIN, challengeId, customerId);
        return new OtpVerificationResult(customerId, OtpPurpose.LOGIN);
    }

    @Override
    @Transactional
    public StepUpRequestResponse requestStepUp(UUID customerId, StepUpRequestRequest request, String clientIp) {
        enforceIpRateLimit(clientIp, "step-up-request");

        if (request.purpose() != OtpPurpose.STEP_UP_TRANSFER) {
            throw new IllegalArgumentException("Only STEP_UP_TRANSFER is supported in this phase — see OtpPurpose's javadoc");
        }
        if (request.transferContext() == null) {
            throw new IllegalArgumentException("transferContext is required for STEP_UP_TRANSFER");
        }

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Authenticated customer not found: " + customerId));

        String contextHash = contextHasher.hash(request.transferContext().canonicalParts());

        // Superseding an existing PENDING step-up challenge for this exact
        // customer+purpose mirrors requestLoginOtp's own supersession
        // logic — see its comment.
        supersedeExisting(customer.getEmail(), request.purpose());

        GeneratedChallenge generated = createChallenge(customer.getEmail(), request.purpose(), customerId, contextHash, clientIp);
        auditLog.stepUpRequested(customerId, request.purpose(), generated.challenge().getId());
        deliverOtp(generated.challenge(), generated.plaintextOtp());

        return new StepUpRequestResponse(generated.challenge().getId(), generated.challenge().getExpiresAt());
    }

    @Override
    @Transactional
    public StepUpVerifyResponse verifyStepUp(UUID customerId, UUID challengeId, String otp, String clientIp) {
        enforceIpRateLimit(clientIp, "step-up-verify");

        OtpChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(OtpChallengeNotFoundException::new);

        if (!customerId.equals(challenge.getCustomerId())) {
            throw new StepUpAccessDeniedException();
        }
        requireStepUpPurpose(challenge);

        validateAndConsume(challenge, otp);

        // A step-up OTP verification does NOT terminate the challenge —
        // it only proves "the customer entered the code correctly." The
        // challenge still needs to authorize exactly one downstream
        // operation (see confirmStepUpExecution) before it's truly done.
        // The step-up window (stepUpExpirySeconds) starts now, separate
        // from — and typically longer than — the original OTP-entry
        // expiry, since the customer still needs time to complete the
        // actual transfer after verifying.
        Instant stepUpExpiry = Instant.now().plusSeconds(otpProperties.stepUpExpirySeconds());
        challenge.setStatus(OtpChallengeStatus.VERIFIED);
        challenge.setExpiresAt(stepUpExpiry);
        challengeRepository.save(challenge);

        auditLog.stepUpVerified(customerId, challenge.getPurpose(), challengeId);
        return new StepUpVerifyResponse(true, challengeId, stepUpExpiry);
    }

    @Override
    @Transactional
    public StepUpConfirmResponse confirmStepUpExecution(UUID customerId, StepUpConfirmRequest request) {
        OtpChallenge challenge = challengeRepository.findById(request.challengeId())
                .orElseThrow(OtpChallengeNotFoundException::new);

        if (!customerId.equals(challenge.getCustomerId())) {
            auditLog.stepUpFailed(customerId, request.purpose(), request.challengeId(), "not owned by caller");
            throw new StepUpAccessDeniedException();
        }
        if (challenge.getPurpose() != request.purpose()) {
            auditLog.stepUpFailed(customerId, request.purpose(), request.challengeId(), "purpose mismatch");
            throw new StepUpContextMismatchException();
        }

        String recomputedHash = contextHasher.hash(request.transferContext().canonicalParts());
        if (!recomputedHash.equals(challenge.getContextHash())) {
            auditLog.stepUpFailed(customerId, request.purpose(), request.challengeId(), "operation context mismatch");
            throw new StepUpContextMismatchException();
        }

        if (challenge.getStatus() == OtpChallengeStatus.EXECUTED) {
            auditLog.stepUpFailed(customerId, request.purpose(), request.challengeId(), "already executed (replay)");
            throw new StepUpNotReadyException("This step-up authorization has already been used");
        }
        if (challenge.getStatus() != OtpChallengeStatus.VERIFIED) {
            auditLog.stepUpFailed(customerId, request.purpose(), request.challengeId(), "not verified");
            throw new StepUpNotReadyException("This step-up authorization has not been verified");
        }
        if (challenge.isExpired()) {
            auditLog.stepUpExpired(customerId, request.purpose(), request.challengeId());
            throw new StepUpNotReadyException("This step-up authorization has expired");
        }

        // Atomic-in-effect: this method runs inside one @Transactional
        // call, and the entity was loaded (and will be flushed) within
        // this same transaction, so a concurrent second confirm call for
        // the same challengeId either serializes behind this one at the
        // database row level or sees the already-EXECUTED status set
        // above once this transaction commits — either way, exactly one
        // caller can ever observe status == VERIFIED and win the race.
        challenge.setStatus(OtpChallengeStatus.EXECUTED);
        challengeRepository.save(challenge);

        auditLog.stepUpExecuted(customerId, request.purpose(), request.challengeId());
        return new StepUpConfirmResponse(true, request.challengeId());
    }

    // ---------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------

    private Optional<Customer> findEligibleCustomer(String identifier) {
        Optional<Customer> customer = customerRepository.findByEmailIgnoreCase(identifier);
        if (customer.isEmpty()) {
            customer = customerRepository.findByPhone(identifier);
        }
        if (customer.isEmpty()) {
            return Optional.empty();
        }
        Optional<CustomerCredentials> credentials = credentialsRepository.findById(customer.get().getId());
        boolean eligible = credentials.isPresent() && credentials.get().isEnabled();
        return eligible ? customer : Optional.empty();
    }

    private void enforceResendCooldown(String identifier, OtpPurpose purpose) {
        Instant since = Instant.now().minusSeconds(otpProperties.resendCooldownSeconds());
        List<OtpChallenge> recent = challengeRepository
                .findByIdentifierAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(identifier, purpose, since);
        if (!recent.isEmpty()) {
            throw new OtpRateLimitExceededException();
        }
        supersedeExisting(identifier, purpose);
    }

    /**
     * Never let a stale PENDING challenge from an earlier request remain
     * usable once a new one is issued — see the task's own "never reset
     * attempt count by requesting another OTP without invalidating the
     * previous challenge" instruction. Each new challenge starts with a
     * fresh {@code attemptCount = 0} legitimately, because the OLD
     * challenge (whatever attempts it had) is marked {@code EXPIRED} and
     * can never be verified again — nothing is "reset," a new challenge
     * simply supersedes a dead one.
     */
    private void supersedeExisting(String identifier, OtpPurpose purpose) {
        challengeRepository.findByIdentifierAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
                        identifier, purpose, Instant.now().minusSeconds(otpProperties.expirySeconds() * 10L))
                .stream()
                .filter(c -> c.getStatus() == OtpChallengeStatus.PENDING || c.getStatus() == OtpChallengeStatus.VERIFIED)
                .forEach(c -> {
                    c.setStatus(OtpChallengeStatus.EXPIRED);
                    challengeRepository.save(c);
                });
    }

    /** Pairs the persisted challenge with the plaintext OTP that was hashed into it — the plaintext is never itself persisted, only ever held long enough to hand to {@link OtpDeliveryProvider}/{@link DevOtpInbox}. */
    private record GeneratedChallenge(OtpChallenge challenge, String plaintextOtp) {
    }

    private GeneratedChallenge createChallenge(String identifier, OtpPurpose purpose, UUID customerId, String contextHash, String clientIp) {
        String otp = otpGenerator.generate(otpProperties.length());
        OtpChallenge challenge = OtpChallenge.builder()
                .identifier(identifier)
                .purpose(purpose)
                .customerId(customerId)
                .otpHash(passwordEncoder.encode(otp))
                .contextHash(contextHash)
                .maxAttempts(otpProperties.maxAttempts())
                .expiresAt(Instant.now().plusSeconds(otpProperties.expirySeconds()))
                .requestedIp(clientIp)
                .build();
        challenge = challengeRepository.save(challenge);

        if (devOtpInboxProperties.enabled()) {
            devOtpInbox.record(challenge.getId().toString(), identifier, purpose, otp, challenge.getCreatedAt(), challenge.getExpiresAt());
        }
        return new GeneratedChallenge(challenge, otp);
    }

    private void deliverOtp(OtpChallenge challenge, String otp) {
        deliveryProvider.deliver(challenge.getIdentifier(), otp, challenge.getPurpose());
        auditLog.otpDelivered(challenge.getIdentifier(), challenge.getPurpose(), challenge.getId(), challenge.getCustomerId());
    }

    private void requirePurpose(OtpChallenge challenge, OtpPurpose expected) {
        if (challenge.getPurpose() != expected) {
            throw new OtpChallengeNotFoundException();
        }
    }

    private void requireStepUpPurpose(OtpChallenge challenge) {
        if (challenge.getPurpose() == OtpPurpose.LOGIN) {
            throw new OtpChallengeNotFoundException();
        }
    }

    /**
     * Shared verify logic for both LOGIN and step-up OTP entry — checks
     * expiry, consumed/locked state, attempts-remaining, then a
     * constant-time hash comparison, incrementing {@code attemptCount} on
     * a miss and locking the challenge once {@code maxAttempts} is
     * reached. Returns the challenge's {@code customerId} on success.
     * Never returns/throws anything that would let a caller distinguish
     * *why* verification failed.
     */
    private UUID validateAndConsume(OtpChallenge challenge, String otp) {
        if (challenge.getStatus() == OtpChallengeStatus.CONSUMED || challenge.getStatus() == OtpChallengeStatus.EXECUTED) {
            auditLog.otpVerificationFailed(challenge.getIdentifier(), challenge.getPurpose(), challenge.getId(), challenge.getCustomerId(), "already consumed");
            throw new InvalidOtpException();
        }
        if (challenge.getStatus() == OtpChallengeStatus.LOCKED) {
            auditLog.otpVerificationFailed(challenge.getIdentifier(), challenge.getPurpose(), challenge.getId(), challenge.getCustomerId(), "locked");
            throw new InvalidOtpException();
        }
        if (challenge.isExpired()) {
            challenge.setStatus(OtpChallengeStatus.EXPIRED);
            challengeRepository.save(challenge);
            auditLog.otpExpired(challenge.getIdentifier(), challenge.getPurpose(), challenge.getId(), challenge.getCustomerId());
            throw new InvalidOtpException();
        }
        if (!challenge.hasAttemptsRemaining()) {
            challenge.setStatus(OtpChallengeStatus.LOCKED);
            challengeRepository.save(challenge);
            auditLog.otpLocked(challenge.getIdentifier(), challenge.getPurpose(), challenge.getId(), challenge.getCustomerId());
            throw new InvalidOtpException();
        }

        boolean matches = passwordEncoder.matches(otp, challenge.getOtpHash());
        // customerId == null means this identifier never matched a real,
        // eligible customer — even a byte-for-byte correct-looking OTP
        // (impossible in practice, since none was ever really generated
        // for delivery, but defense-in-depth) can never succeed, so
        // enumeration can't be probed via "does verify ever succeed."
        if (!matches || challenge.getCustomerId() == null) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            challengeRepository.save(challenge);
            auditLog.otpVerificationFailed(challenge.getIdentifier(), challenge.getPurpose(), challenge.getId(), challenge.getCustomerId(), "incorrect code");
            throw new InvalidOtpException();
        }

        return challenge.getCustomerId();
    }

    private void enforceIpRateLimit(String clientIp, String bucket) {
        String key = bucket + ":" + (clientIp == null ? "unknown" : clientIp);
        int max = bucket.endsWith("verify") ? VERIFY_RATE_LIMIT_MAX : REQUEST_RATE_LIMIT_MAX;
        long window = bucket.endsWith("verify") ? VERIFY_RATE_LIMIT_WINDOW_SECONDS : REQUEST_RATE_LIMIT_WINDOW_SECONDS;
        if (!rateLimiter.tryAcquire(key, max, window)) {
            throw new OtpRateLimitExceededException();
        }
    }
}
