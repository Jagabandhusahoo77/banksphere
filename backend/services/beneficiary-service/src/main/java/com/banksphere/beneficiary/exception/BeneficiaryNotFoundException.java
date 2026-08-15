package com.banksphere.beneficiary.exception;

import java.util.UUID;

public class BeneficiaryNotFoundException extends RuntimeException {

    public BeneficiaryNotFoundException(UUID id) {
        super("Beneficiary not found with id: " + id);
    }
}
