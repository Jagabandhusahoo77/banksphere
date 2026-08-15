package com.banksphere.kyc.exception;

import java.util.UUID;

public class KycDocumentNotFoundException extends RuntimeException {

    public KycDocumentNotFoundException(UUID id) {
        super("KYC document not found: " + id);
    }
}
