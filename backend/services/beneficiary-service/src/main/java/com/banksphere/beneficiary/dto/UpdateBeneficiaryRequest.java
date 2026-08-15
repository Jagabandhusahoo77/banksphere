package com.banksphere.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deliberately does NOT include {@code accountNumber} or {@code ifsc}:
 * those identify *which* real-world account this beneficiary points to,
 * so changing them is conceptually "this is a different beneficiary," not
 * an edit of this one — the same convention most bank "manage payee"
 * screens follow (delete and re-add, rather than repoint in place). It
 * also keeps the duplicate-prevention constraint meaningful: allowing an
 * update to freely change account_number/ifsc would let a customer route
 * around the active-duplicate check that create-time enforces. Only
 * display fields are editable here.
 */
public record UpdateBeneficiaryRequest(

        @NotBlank(message = "beneficiaryName is required")
        @Size(max = 100, message = "beneficiaryName must be at most 100 characters")
        String beneficiaryName,

        @NotBlank(message = "bankName is required")
        @Size(max = 150, message = "bankName must be at most 150 characters")
        String bankName,

        @Size(max = 50, message = "nickname must be at most 50 characters")
        String nickname
) {
}
