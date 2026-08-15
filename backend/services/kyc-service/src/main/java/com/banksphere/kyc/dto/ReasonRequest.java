package com.banksphere.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Shared request shape for every employee action that must carry a
 * customer-visible reason: reject-document, request-information,
 * reject-application. Always customer-visible text — never internal-only
 * notes (see {@code KycApplication.reviewReason}'s javadoc).
 */
public record ReasonRequest(
        @NotBlank @Size(max = 1000)
        String reason
) {
}
