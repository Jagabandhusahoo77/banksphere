package com.banksphere.beneficiary.service;

import com.banksphere.beneficiary.dto.BeneficiaryResponse;
import com.banksphere.beneficiary.dto.CreateBeneficiaryRequest;
import com.banksphere.beneficiary.dto.UpdateBeneficiaryRequest;
import com.banksphere.beneficiary.entity.Beneficiary;
import com.banksphere.beneficiary.entity.BeneficiaryStatus;
import com.banksphere.beneficiary.exception.BeneficiaryAccessDeniedException;
import com.banksphere.beneficiary.exception.BeneficiaryNotFoundException;
import com.banksphere.beneficiary.exception.DuplicateBeneficiaryException;
import com.banksphere.beneficiary.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BeneficiaryServiceImpl implements BeneficiaryService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryServiceImpl.class);

    private final BeneficiaryRepository beneficiaryRepository;

    @Override
    @Transactional
    public BeneficiaryResponse createBeneficiary(CreateBeneficiaryRequest request, UUID ownerCustomerId) {
        if (beneficiaryRepository.existsByCustomerIdAndAccountNumberAndIfscAndStatus(
                ownerCustomerId, request.accountNumber(), request.ifsc(), BeneficiaryStatus.ACTIVE)) {
            log.info("Duplicate beneficiary attempt for customer {} (account {})", ownerCustomerId, maskAccountNumber(request.accountNumber()));
            throw new DuplicateBeneficiaryException(request.accountNumber(), request.ifsc());
        }

        Beneficiary beneficiary = Beneficiary.builder()
                .customerId(ownerCustomerId)
                .beneficiaryName(request.beneficiaryName())
                .accountNumber(request.accountNumber())
                .ifsc(request.ifsc())
                .bankName(request.bankName())
                .nickname(request.nickname())
                .status(BeneficiaryStatus.ACTIVE)
                .build();

        // The database's partial unique index (see the V1 migration) is what
        // actually closes the race between two concurrent requests both
        // passing the existsBy... check above before either commits — a
        // violation here surfaces as DataIntegrityViolationException, mapped
        // to the same 409 by GlobalExceptionHandler.
        Beneficiary saved = beneficiaryRepository.save(beneficiary);
        log.info("Beneficiary {} created for customer {} (account {})", saved.getId(), ownerCustomerId, maskAccountNumber(saved.getAccountNumber()));
        return BeneficiaryResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries(UUID requestingCustomerId) {
        return beneficiaryRepository.findByCustomerIdAndStatus(requestingCustomerId, BeneficiaryStatus.ACTIVE).stream()
                .map(BeneficiaryResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BeneficiaryResponse getBeneficiary(UUID id, UUID requestingCustomerId) {
        Beneficiary beneficiary = findBeneficiaryOrThrow(id);
        requireOwnership(beneficiary, requestingCustomerId);
        return BeneficiaryResponse.from(beneficiary);
    }

    @Override
    @Transactional
    public BeneficiaryResponse updateBeneficiary(UUID id, UpdateBeneficiaryRequest request, UUID requestingCustomerId) {
        Beneficiary beneficiary = findBeneficiaryOrThrow(id);
        requireOwnership(beneficiary, requestingCustomerId);

        beneficiary.setBeneficiaryName(request.beneficiaryName());
        beneficiary.setBankName(request.bankName());
        beneficiary.setNickname(request.nickname());

        Beneficiary saved = beneficiaryRepository.save(beneficiary);
        log.info("Beneficiary {} updated for customer {}", saved.getId(), requestingCustomerId);
        return BeneficiaryResponse.from(saved);
    }

    @Override
    @Transactional
    public void deactivateBeneficiary(UUID id, UUID requestingCustomerId) {
        Beneficiary beneficiary = findBeneficiaryOrThrow(id);
        requireOwnership(beneficiary, requestingCustomerId);

        beneficiary.setStatus(BeneficiaryStatus.INACTIVE);
        beneficiaryRepository.save(beneficiary);
        log.info("Beneficiary {} deactivated for customer {}", beneficiary.getId(), requestingCustomerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiariesForEmployee(UUID customerId) {
        return beneficiaryRepository.findByCustomerIdAndStatus(customerId, BeneficiaryStatus.ACTIVE).stream()
                .map(BeneficiaryResponse::from)
                .toList();
    }

    private Beneficiary findBeneficiaryOrThrow(UUID id) {
        return beneficiaryRepository.findById(id).orElseThrow(() -> new BeneficiaryNotFoundException(id));
    }

    /**
     * Checked after the existence lookup, not before: beneficiary ids are
     * random, high-entropy UUIDs, not guessable identifiers like an email
     * address, so confirming "this beneficiary id exists" via a 404-vs-403
     * distinction isn't a meaningful information leak the way it would be
     * for a login or customer-profile lookup — same reasoning as
     * account-service's identical-shaped check. See docs/security/authorization.md.
     */
    private void requireOwnership(Beneficiary beneficiary, UUID requestingCustomerId) {
        if (!beneficiary.getCustomerId().equals(requestingCustomerId)) {
            throw new BeneficiaryAccessDeniedException(beneficiary.getId());
        }
    }

    /** Never log a full account number — only the last 4 digits, enough to correlate log lines with a support ticket. */
    private static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }
}
