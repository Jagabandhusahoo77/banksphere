package com.banksphere.kyc.exception;

import java.util.UUID;

public class KycApplicationNotFoundException extends RuntimeException {

    public KycApplicationNotFoundException(UUID id) {
        super("KYC application not found: " + id);
    }
}
