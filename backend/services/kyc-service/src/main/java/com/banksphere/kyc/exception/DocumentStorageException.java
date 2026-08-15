package com.banksphere.kyc.exception;

/**
 * Wraps a lower-level storage failure (filesystem I/O today; an S3 SDK
 * exception under a future {@code S3DocumentStorage}) behind one type the
 * rest of the KYC domain can catch without knowing which implementation
 * is active. Mapped to {@code 500} by {@code GlobalExceptionHandler} —
 * never expose the underlying cause/stack trace to the caller.
 */
public class DocumentStorageException extends RuntimeException {

    public DocumentStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
