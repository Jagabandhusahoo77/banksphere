package com.banksphere.kyc.exception;

/** A non-REJECTED document of this type already exists for the application. Mapped to {@code 409}. */
public class DuplicateDocumentException extends RuntimeException {

    public DuplicateDocumentException(String message) {
        super(message);
    }
}
