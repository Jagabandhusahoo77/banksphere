package com.banksphere.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code customerId} is deliberately NOT a field here — same reasoning as
 * account-service's {@code AccountCreateRequest}: the owner is always the
 * authenticated caller, taken from the JWT in BeneficiaryController, never
 * a client-supplied value. See docs/security/authorization.md.
 */
public record CreateBeneficiaryRequest(

        @NotBlank(message = "beneficiaryName is required")
        @Size(max = 100, message = "beneficiaryName must be at most 100 characters")
        String beneficiaryName,

        @NotBlank(message = "accountNumber is required")
        @Pattern(regexp = "^[0-9]{9,18}$", message = "accountNumber must be 9 to 18 digits")
        String accountNumber,

        @NotBlank(message = "ifsc is required")
        @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "ifsc must be a valid 11-character IFSC code, e.g. BANK0001234")
        String ifsc,

        @NotBlank(message = "bankName is required")
        @Size(max = 150, message = "bankName must be at most 150 characters")
        String bankName,

        @Size(max = 50, message = "nickname must be at most 50 characters")
        String nickname
) {
}
