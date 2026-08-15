package com.banksphere.kyc.entity;

/**
 * A deliberately small, fixed demo set — NOT a claim that these four
 * represent every legally accepted Indian banking KYC document. See
 * CLAUDE.md's fictional-project rule and ADR-008.
 */
public enum DocumentType {
    PAN,
    IDENTITY_PROOF,
    ADDRESS_PROOF,
    BANK_STATEMENT
}
