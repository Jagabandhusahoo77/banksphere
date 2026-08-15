package com.banksphere.kyc.exception;

import com.banksphere.kyc.entity.KycStatus;

/** Document upload attempted while the application is not in DRAFT or ADDITIONAL_INFORMATION_REQUIRED. Mapped to {@code 422}. */
public class DocumentUploadNotAllowedException extends RuntimeException {

    public DocumentUploadNotAllowedException(KycStatus status) {
        super("Cannot upload documents while the application is in status " + status);
    }
}
