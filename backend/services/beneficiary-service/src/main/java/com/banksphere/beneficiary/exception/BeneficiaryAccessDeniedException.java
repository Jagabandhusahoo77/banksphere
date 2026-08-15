package com.banksphere.beneficiary.exception;

import java.util.UUID;

/** Thrown when the authenticated customer tries to access another customer's beneficiary. */
public class BeneficiaryAccessDeniedException extends RuntimeException {

    public BeneficiaryAccessDeniedException(UUID requestedResourceId) {
        super("Not authorized to access this resource: " + requestedResourceId);
    }
}
