package com.banksphere.employee.dto;

import java.util.UUID;

/** Deserialization target for beneficiary-service's {@code BeneficiaryResponse}. See ADR-008. */
public record BeneficiaryLookupResult(
        UUID id,
        String beneficiaryName,
        String accountNumber,
        String ifsc,
        String bankName,
        String nickname,
        String status
) {
}
