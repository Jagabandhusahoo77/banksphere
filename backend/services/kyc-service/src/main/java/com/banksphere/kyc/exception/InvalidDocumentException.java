package com.banksphere.kyc.exception;

/** Malformed upload: unsupported file type, oversized file, or missing/invalid document type. Mapped to {@code 400}. */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
