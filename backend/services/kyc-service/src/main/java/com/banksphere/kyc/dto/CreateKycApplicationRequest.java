package com.banksphere.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Illustrative demo fields only — see DocumentType's javadoc and ADR-008; not a claim of regulatory completeness. */
public record CreateKycApplicationRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "must be a well-formed PAN (e.g. ABCDE1234F)")
        String panNumber,

        @NotBlank @Size(max = 100)
        String occupation,

        @NotBlank @Size(max = 30)
        String annualIncomeRange
) {
}
