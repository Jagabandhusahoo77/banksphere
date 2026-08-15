package com.banksphere.beneficiary.dto;

import com.banksphere.beneficiary.entity.Beneficiary;
import com.banksphere.beneficiary.entity.BeneficiaryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Full account number/IFSC are returned here deliberately unmasked — this
 * is the beneficiary's own owner viewing their own data (the same
 * convention account-service's AccountResponse follows for its own
 * account number), not a case for the masking utilities used at the
 * logging boundary. See BeneficiaryServiceImpl's logging calls for where
 * masking actually applies.
 */
public record BeneficiaryResponse(
        UUID id,
        UUID customerId,
        String beneficiaryName,
        String accountNumber,
        String ifsc,
        String bankName,
        String nickname,
        BeneficiaryStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BeneficiaryResponse from(Beneficiary beneficiary) {
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getCustomerId(),
                beneficiary.getBeneficiaryName(),
                beneficiary.getAccountNumber(),
                beneficiary.getIfsc(),
                beneficiary.getBankName(),
                beneficiary.getNickname(),
                beneficiary.getStatus(),
                beneficiary.getCreatedAt(),
                beneficiary.getUpdatedAt()
        );
    }
}
