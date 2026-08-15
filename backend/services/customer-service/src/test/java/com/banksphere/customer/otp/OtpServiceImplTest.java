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
import com.banksphere.customer.otp.dto.TransferStepUpContext;
import com.banksphere.customer.repository.CustomerCredentialsRepository;
import com.banksphere.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers Phase 9D's OTP-generation, expiration, security, login, and
 * step-up testing scenarios (see ADR-009 and the phase's own test
 * checklist) at the unit level, against a fully mocked persistence layer.
 * {@code passwordEncoder} and {@code contextHasher} are real instances
 * (not mocks) — both are pure, fast, and their actual behavior (BCrypt
 * matching, SHA-256 context binding) is exactly what several tests here
 * need to assert against.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    private static final String CLIENT_IP = "203.0.113.5";

    @Mock
    private OtpChallengeRepository challengeRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCredentialsRepository credentialsRepository;

    @Mock
    private OtpGenerator otpGenerator;

    @Mock
    private OtpDeliveryProvider deliveryProvider;

    @Mock
    private OtpRateLimiter rateLimiter;

    @Mock
    private DevOtpInbox devOtpInbox;

    @Mock
    private OtpAuditLog auditLog;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final OtpContextHasher contextHasher = new OtpContextHasher();
    private final OtpProperties otpProperties = new OtpProperties(6, 300, 3, 30, 300);
    private final DevOtpInboxProperties devOtpInboxProperties = new DevOtpInboxProperties(false);

    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(challengeRepository, customerRepository, credentialsRepository,
                passwordEncoder, otpGenerator, contextHasher, deliveryProvider, rateLimiter,
                otpProperties, devOtpInboxProperties, devOtpInbox, auditLog);

        // Shared default: every OtpService method rate-limits first. An
        // unstubbed boolean mock returns false, which would make every
        // test fail at the first line unless overridden — lenient() so
        // the one test that deliberately exercises the rate-limit
        // rejection path (which re-stubs this to false) doesn't trigger
        // an UnnecessaryStubbingException for this default.
        lenient().when(rateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(true);
        lenient().when(challengeRepository.save(any(OtpChallenge.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(otpGenerator.generate(anyInt())).thenReturn("123456");
    }

    private Customer eligibleCustomer(UUID customerId, String email) {
        return Customer.builder().id(customerId).email(email).phone("+91-9999900000").build();
    }

    private CustomerCredentials enabledCredentials(UUID customerId) {
        return CustomerCredentials.builder().customerId(customerId).passwordHash("irrelevant").enabled(true).build();
    }

    // ---------------------------------------------------------------
    // OTP generation (scenarios 1-4)
    // ---------------------------------------------------------------

    @Test
    void requestLoginOtp_usesSecureRandomGenerator_neverRawIdentifierOrTimestamp() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(eligibleCustomer(customerId, "jane@example.com")));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(enabledCredentials(customerId)));

        otpService.requestLoginOtp("jane@example.com", CLIENT_IP);

        // The service asks OtpGenerator (SecureRandom-backed, see its own
        // unit-level guarantee) for the code — it never derives one from
        // the identifier, customerId, or a timestamp itself.
        verify(otpGenerator).generate(otpProperties.length());
    }

    @Test
    void requestLoginOtp_persistsOnlyAHash_neverThePlaintextOtp() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(eligibleCustomer(customerId, "jane@example.com")));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(enabledCredentials(customerId)));

        otpService.requestLoginOtp("jane@example.com", CLIENT_IP);

        ArgumentCaptor<OtpChallenge> captor = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challengeRepository).save(captor.capture());
        OtpChallenge saved = captor.getValue();

        assertThat(saved.getOtpHash()).isNotEqualTo("123456");
        assertThat(passwordEncoder.matches("123456", saved.getOtpHash())).isTrue();
    }

    @Test
    void requestLoginOtp_generatesConfiguredLength() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(eligibleCustomer(customerId, "jane@example.com")));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(enabledCredentials(customerId)));

        otpService.requestLoginOtp("jane@example.com", CLIENT_IP);

        verify(otpGenerator).generate(6);
    }

    // ---------------------------------------------------------------
    // Expiration (scenarios 5-7)
    // ---------------------------------------------------------------

    @Test
    void verifyLoginOtp_rejectsExpiredChallenge_andMarksItExpired() {
        UUID challengeId = UUID.randomUUID();
        OtpChallenge expired = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(UUID.randomUUID()).otpHash(passwordEncoder.encode("123456"))
                .maxAttempts(3).expiresAt(Instant.now().minusSeconds(5)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(expired.getStatus()).isEqualTo(OtpChallengeStatus.EXPIRED);
        verify(auditLog).otpExpired(any(), any(), any(), any());
    }

    @Test
    void verifyLoginOtp_rejectsAlreadyConsumedChallenge_noReuse() {
        UUID challengeId = UUID.randomUUID();
        OtpChallenge consumed = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(UUID.randomUUID()).otpHash(passwordEncoder.encode("123456"))
                .status(OtpChallengeStatus.CONSUMED).maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyLoginOtp_locksChallenge_whenMaxAttemptsAlreadyExhausted() {
        UUID challengeId = UUID.randomUUID();
        OtpChallenge exhausted = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(UUID.randomUUID()).otpHash(passwordEncoder.encode("123456"))
                .attemptCount(3).maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(exhausted));

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(exhausted.getStatus()).isEqualTo(OtpChallengeStatus.LOCKED);
        verify(auditLog).otpLocked(any(), any(), any(), any());
    }

    @Test
    void verifyLoginOtp_alreadyLockedChallenge_rejectsWithoutFurtherAttemptIncrement() {
        UUID challengeId = UUID.randomUUID();
        OtpChallenge locked = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(UUID.randomUUID()).otpHash(passwordEncoder.encode("123456"))
                .status(OtpChallengeStatus.LOCKED).attemptCount(3).maxAttempts(3)
                .expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(locked.getAttemptCount()).isEqualTo(3);
    }

    // ---------------------------------------------------------------
    // Security (scenarios 8-11)
    // ---------------------------------------------------------------

    @Test
    void requestLoginOtp_returnsIdenticalGenericResponseShape_regardlessOfWhetherIdentifierExists() {
        when(customerRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByEmailIgnoreCase("real@example.com")).thenReturn(Optional.of(eligibleCustomer(customerId, "real@example.com")));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(enabledCredentials(customerId)));

        OtpRequestResponse forGhost = otpService.requestLoginOtp("ghost@example.com", CLIENT_IP);
        OtpRequestResponse forReal = otpService.requestLoginOtp("real@example.com", CLIENT_IP);

        assertThat(forGhost.message()).isEqualTo(forReal.message());
        assertThat(forGhost.challengeId()).isNotNull();
        assertThat(forReal.challengeId()).isNotNull();
    }

    @Test
    void requestLoginOtp_neverCallsDeliveryProvider_whenIdentifierDoesNotMatchAnEligibleCustomer() {
        when(customerRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        otpService.requestLoginOtp("ghost@example.com", CLIENT_IP);

        verify(deliveryProvider, never()).deliver(anyString(), anyString(), any());
    }

    @Test
    void requestLoginOtp_stillPersistsARealChallenge_forANonExistentIdentifier_soTimingIsConsistent() {
        when(customerRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        otpService.requestLoginOtp("ghost@example.com", CLIENT_IP);

        ArgumentCaptor<OtpChallenge> captor = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challengeRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isNull();
    }

    @Test
    void verifyLoginOtp_neverSucceeds_forAChallengeWithNoMatchedCustomer_evenWithTheCorrectHash() {
        UUID challengeId = UUID.randomUUID();
        OtpChallenge ghostChallenge = OtpChallenge.builder()
                .id(challengeId).identifier("ghost@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(null).otpHash(passwordEncoder.encode("123456"))
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(ghostChallenge));

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void requestLoginOtp_rejectsWhenIpRateLimitExceeded() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> otpService.requestLoginOtp("jane@example.com", CLIENT_IP))
                .isInstanceOf(OtpRateLimitExceededException.class);

        verify(challengeRepository, never()).save(any());
    }

    @Test
    void requestLoginOtp_rejectsSecondRequestWithinResendCooldown() {
        when(challengeRepository.findByIdentifierAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq("jane@example.com"), org.mockito.ArgumentMatchers.eq(OtpPurpose.LOGIN), any()))
                .thenReturn(List.of(OtpChallenge.builder().id(UUID.randomUUID()).status(OtpChallengeStatus.PENDING).build()));

        assertThatThrownBy(() -> otpService.requestLoginOtp("jane@example.com", CLIENT_IP))
                .isInstanceOf(OtpRateLimitExceededException.class);
    }

    // ---------------------------------------------------------------
    // Login (scenarios 12-16, refresh/logout covered separately)
    // ---------------------------------------------------------------

    @Test
    void verifyLoginOtp_authenticatesAndConsumesChallenge_whenOtpIsCorrect() {
        UUID challengeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OtpChallenge challenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456"))
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        OtpVerificationResult result = otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP);

        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.purpose()).isEqualTo(OtpPurpose.LOGIN);
        assertThat(challenge.getStatus()).isEqualTo(OtpChallengeStatus.CONSUMED);
        assertThat(challenge.getConsumedAt()).isNotNull();
        verify(auditLog).otpVerificationSucceeded(any(), any(), any(), any());
    }

    @Test
    void verifyLoginOtp_rejectsIncorrectOtp_andIncrementsAttemptCount() {
        UUID challengeId = UUID.randomUUID();
        OtpChallenge challenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(UUID.randomUUID()).otpHash(passwordEncoder.encode("123456"))
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "000000", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);

        assertThat(challenge.getAttemptCount()).isEqualTo(1);
        assertThat(challenge.getStatus()).isEqualTo(OtpChallengeStatus.PENDING);
    }

    @Test
    void verifyLoginOtp_rejectsUnknownChallengeId() {
        UUID challengeId = UUID.randomUUID();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP))
                .isInstanceOf(OtpChallengeNotFoundException.class);
    }

    @Test
    void verifyLoginOtp_rejectsAStepUpChallengeId_wrongPurpose() {
        UUID challengeId = UUID.randomUUID();
        OtpChallenge stepUpChallenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(UUID.randomUUID()).otpHash(passwordEncoder.encode("123456"))
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(stepUpChallenge));

        assertThatThrownBy(() -> otpService.verifyLoginOtp(challengeId, "123456", CLIENT_IP))
                .isInstanceOf(OtpChallengeNotFoundException.class);
    }

    @Test
    void supersedingAnOldChallenge_neverResetsItsAttemptCount_itExpiresPermanentlyInstead() {
        UUID customerId = UUID.randomUUID();
        OtpChallenge stale = OtpChallenge.builder()
                .id(UUID.randomUUID()).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(customerId).attemptCount(2).status(OtpChallengeStatus.PENDING)
                .otpHash(passwordEncoder.encode("999999")).maxAttempts(3).expiresAt(Instant.now().plusSeconds(200)).build();
        when(customerRepository.findByEmailIgnoreCase("jane@example.com")).thenReturn(Optional.of(eligibleCustomer(customerId, "jane@example.com")));
        when(credentialsRepository.findById(customerId)).thenReturn(Optional.of(enabledCredentials(customerId)));
        // First lookup (cooldown check, small window) finds nothing; the
        // superseding lookup (10x window) finds the stale PENDING challenge.
        when(challengeRepository.findByIdentifierAndPurposeAndCreatedAtAfterOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq("jane@example.com"), org.mockito.ArgumentMatchers.eq(OtpPurpose.LOGIN), any()))
                .thenReturn(List.of(), List.of(stale));

        otpService.requestLoginOtp("jane@example.com", CLIENT_IP);

        assertThat(stale.getStatus()).isEqualTo(OtpChallengeStatus.EXPIRED);
        assertThat(stale.getAttemptCount()).isEqualTo(2); // never reset — the row is just retired
    }

    // ---------------------------------------------------------------
    // Step-up (scenarios 17-26)
    // ---------------------------------------------------------------

    private TransferStepUpContext transferContext(UUID sourceAccountId, BigDecimal amount) {
        return new TransferStepUpContext(sourceAccountId, "222222222222", "BANK0000001", amount, "INR");
    }

    @Test
    void requestStepUp_createsChallengeBoundToOperationContext() {
        UUID customerId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(eligibleCustomer(customerId, "jane@example.com")));
        StepUpRequestRequest request = new StepUpRequestRequest(OtpPurpose.STEP_UP_TRANSFER, transferContext(sourceAccountId, new BigDecimal("100000.00")));

        StepUpRequestResponse response = otpService.requestStepUp(customerId, request, CLIENT_IP);

        assertThat(response.challengeId()).isNotNull();
        ArgumentCaptor<OtpChallenge> captor = ArgumentCaptor.forClass(OtpChallenge.class);
        verify(challengeRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getContextHash())
                .isEqualTo(contextHasher.hash(transferContext(sourceAccountId, new BigDecimal("100000.00")).canonicalParts()));
    }

    @Test
    void requestStepUp_rejectsAnyPurposeOtherThanStepUpTransfer() {
        UUID customerId = UUID.randomUUID();
        StepUpRequestRequest request = new StepUpRequestRequest(OtpPurpose.LOGIN, null);

        assertThatThrownBy(() -> otpService.requestStepUp(customerId, request, CLIENT_IP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyStepUp_marksChallengeVerified_whenOtpIsCorrect() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        OtpChallenge challenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456")).contextHash("some-hash")
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        StepUpVerifyResponse response = otpService.verifyStepUp(customerId, challengeId, "123456", CLIENT_IP);

        assertThat(response.verified()).isTrue();
        assertThat(challenge.getStatus()).isEqualTo(OtpChallengeStatus.VERIFIED);
    }

    @Test
    void verifyStepUp_rejectsIncorrectOtp() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        OtpChallenge challenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456")).contextHash("some-hash")
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> otpService.verifyStepUp(customerId, challengeId, "000000", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);
        assertThat(challenge.getStatus()).isEqualTo(OtpChallengeStatus.PENDING);
    }

    @Test
    void verifyStepUp_rejectsExpiredChallenge() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        OtpChallenge challenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456")).contextHash("some-hash")
                .maxAttempts(3).expiresAt(Instant.now().minusSeconds(5)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> otpService.verifyStepUp(customerId, challengeId, "123456", CLIENT_IP))
                .isInstanceOf(InvalidOtpException.class);
    }

    @Test
    void verifyStepUp_rejectsChallengeBelongingToAnotherCustomer() {
        UUID owningCustomerId = UUID.randomUUID();
        UUID attackerCustomerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        OtpChallenge challenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(owningCustomerId).otpHash(passwordEncoder.encode("123456")).contextHash("some-hash")
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> otpService.verifyStepUp(attackerCustomerId, challengeId, "123456", CLIENT_IP))
                .isInstanceOf(StepUpAccessDeniedException.class);
    }

    @Test
    void verifyStepUp_rejectsALoginChallengeId_wrongPurpose() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        OtpChallenge loginChallenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.LOGIN)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456"))
                .maxAttempts(3).expiresAt(Instant.now().plusSeconds(60)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(loginChallenge));

        assertThatThrownBy(() -> otpService.verifyStepUp(customerId, challengeId, "123456", CLIENT_IP))
                .isInstanceOf(OtpChallengeNotFoundException.class);
    }

    private OtpChallenge verifiedTransferChallenge(UUID customerId, UUID challengeId, String contextHash) {
        return OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456")).contextHash(contextHash)
                .status(OtpChallengeStatus.VERIFIED).maxAttempts(3).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    @Test
    void confirmStepUpExecution_succeeds_whenContextMatchesExactly() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        TransferStepUpContext context = transferContext(sourceAccountId, new BigDecimal("100000.00"));
        String contextHash = contextHasher.hash(context.canonicalParts());
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(verifiedTransferChallenge(customerId, challengeId, contextHash)));

        StepUpConfirmResponse response = otpService.confirmStepUpExecution(customerId,
                new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, context));

        assertThat(response.confirmed()).isTrue();
    }

    @Test
    void confirmStepUpExecution_rejects_whenAmountChangedAfterChallengeCreation() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        String originalHash = contextHasher.hash(transferContext(sourceAccountId, new BigDecimal("1000.00")).canonicalParts());
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(verifiedTransferChallenge(customerId, challengeId, originalHash)));

        TransferStepUpContext tamperedContext = transferContext(sourceAccountId, new BigDecimal("100000.00"));

        assertThatThrownBy(() -> otpService.confirmStepUpExecution(customerId,
                new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, tamperedContext)))
                .isInstanceOf(StepUpContextMismatchException.class);
    }

    @Test
    void confirmStepUpExecution_rejects_whenRecipientChangedAfterChallengeCreation() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        String originalHash = contextHasher.hash(transferContext(sourceAccountId, new BigDecimal("1000.00")).canonicalParts());
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(verifiedTransferChallenge(customerId, challengeId, originalHash)));

        TransferStepUpContext tamperedContext = new TransferStepUpContext(
                sourceAccountId, "999999999999", "BANK0000001", new BigDecimal("1000.00"), "INR");

        assertThatThrownBy(() -> otpService.confirmStepUpExecution(customerId,
                new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, tamperedContext)))
                .isInstanceOf(StepUpContextMismatchException.class);
    }

    @Test
    void confirmStepUpExecution_rejects_whenChallengeNotYetVerified() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        TransferStepUpContext context = transferContext(sourceAccountId, new BigDecimal("100000.00"));
        String contextHash = contextHasher.hash(context.canonicalParts());
        OtpChallenge pendingChallenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456")).contextHash(contextHash)
                .status(OtpChallengeStatus.PENDING).maxAttempts(3).expiresAt(Instant.now().plusSeconds(300)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(pendingChallenge));

        assertThatThrownBy(() -> otpService.confirmStepUpExecution(customerId,
                new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, context)))
                .isInstanceOf(StepUpNotReadyException.class);
    }

    @Test
    void confirmStepUpExecution_rejects_whenChallengeAlreadyExecuted_noReplay() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        TransferStepUpContext context = transferContext(sourceAccountId, new BigDecimal("100000.00"));
        String contextHash = contextHasher.hash(context.canonicalParts());
        OtpChallenge executedChallenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456")).contextHash(contextHash)
                .status(OtpChallengeStatus.EXECUTED).maxAttempts(3).expiresAt(Instant.now().plusSeconds(300)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(executedChallenge));

        assertThatThrownBy(() -> otpService.confirmStepUpExecution(customerId,
                new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, context)))
                .isInstanceOf(StepUpNotReadyException.class);
    }

    @Test
    void confirmStepUpExecution_rejects_whenExpired() {
        UUID customerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        TransferStepUpContext context = transferContext(sourceAccountId, new BigDecimal("100000.00"));
        String contextHash = contextHasher.hash(context.canonicalParts());
        OtpChallenge expiredChallenge = OtpChallenge.builder()
                .id(challengeId).identifier("jane@example.com").purpose(OtpPurpose.STEP_UP_TRANSFER)
                .customerId(customerId).otpHash(passwordEncoder.encode("123456")).contextHash(contextHash)
                .status(OtpChallengeStatus.VERIFIED).maxAttempts(3).expiresAt(Instant.now().minusSeconds(5)).build();
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(expiredChallenge));

        assertThatThrownBy(() -> otpService.confirmStepUpExecution(customerId,
                new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, context)))
                .isInstanceOf(StepUpNotReadyException.class);
    }

    @Test
    void confirmStepUpExecution_rejects_whenChallengeBelongsToAnotherCustomer() {
        UUID owningCustomerId = UUID.randomUUID();
        UUID attackerCustomerId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        TransferStepUpContext context = transferContext(sourceAccountId, new BigDecimal("100000.00"));
        String contextHash = contextHasher.hash(context.canonicalParts());
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(verifiedTransferChallenge(owningCustomerId, challengeId, contextHash)));

        assertThatThrownBy(() -> otpService.confirmStepUpExecution(attackerCustomerId,
                new StepUpConfirmRequest(challengeId, OtpPurpose.STEP_UP_TRANSFER, context)))
                .isInstanceOf(StepUpAccessDeniedException.class);
    }
}
