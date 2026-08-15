package com.banksphere.kyc.exception;

/** The customer already has a non-terminal (not APPROVED/REJECTED) KYC application. Mapped to {@code 409}. */
public class ActiveApplicationExistsException extends RuntimeException {

    public ActiveApplicationExistsException(String message) {
        super(message);
    }
}
