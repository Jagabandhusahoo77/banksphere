package com.banksphere.kyc.exception;

import com.banksphere.kyc.entity.DocumentType;

import java.util.List;

/** Submit/resubmit attempted without at least one non-REJECTED document of every required type. Mapped to {@code 422}. */
public class MissingDocumentsException extends RuntimeException {

    public MissingDocumentsException(List<DocumentType> missing) {
        super("Cannot submit: missing required documents: " + missing);
    }
}
