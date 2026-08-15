package com.banksphere.beneficiary.service;

import com.banksphere.beneficiary.dto.BeneficiaryResponse;
import com.banksphere.beneficiary.dto.CreateBeneficiaryRequest;
import com.banksphere.beneficiary.dto.UpdateBeneficiaryRequest;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryService {

    /** The new beneficiary is always owned by {@code ownerCustomerId} (the authenticated caller) — never client-supplied. */
    BeneficiaryResponse createBeneficiary(CreateBeneficiaryRequest request, UUID ownerCustomerId);

    /** Only this customer's own ACTIVE beneficiaries — the ones usable for a future transfer. */
    List<BeneficiaryResponse> getBeneficiaries(UUID requestingCustomerId);

    /** Throws BeneficiaryAccessDeniedException if the beneficiary isn't owned by {@code requestingCustomerId}. Returns regardless of status. */
    BeneficiaryResponse getBeneficiary(UUID id, UUID requestingCustomerId);

    /** Throws BeneficiaryAccessDeniedException if the beneficiary isn't owned by {@code requestingCustomerId}. */
    BeneficiaryResponse updateBeneficiary(UUID id, UpdateBeneficiaryRequest request, UUID requestingCustomerId);

    /** Soft-deletes: sets status to INACTIVE rather than removing the row — see docs/09-engineering-journal for why. */
    void deactivateBeneficiary(UUID id, UUID requestingCustomerId);

    /**
     * Phase 9C — the Customer 360 aggregation's beneficiaries section.
     * Reuses the existing {@code findByCustomerIdAndStatus} query — same
     * data {@link #getBeneficiaries} returns for self-service, just for
     * an employee-authorized {@code customerId} instead of the caller's
     * own. See ADR-008.
     */
    List<BeneficiaryResponse> getBeneficiariesForEmployee(UUID customerId);
}
