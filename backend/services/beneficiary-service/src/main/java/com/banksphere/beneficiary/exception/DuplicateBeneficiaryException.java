package com.banksphere.beneficiary.exception;

/**
 * Thrown by the proactive application-layer check in
 * BeneficiaryServiceImpl. The database's partial unique index
 * (uq_beneficiaries_customer_account_ifsc_active — see the V1 migration)
 * is the actual source of truth for this rule and is what protects
 * against the concurrent-request race this check alone can't close; a
 * race that slips past this check still surfaces as a 409, just via
 * GlobalExceptionHandler's DataIntegrityViolationException handler
 * instead of this exception.
 */
public class DuplicateBeneficiaryException extends RuntimeException {

    public DuplicateBeneficiaryException(String accountNumber, String ifsc) {
        super("An active beneficiary with account number " + accountNumber + " and IFSC " + ifsc + " already exists");
    }
}
