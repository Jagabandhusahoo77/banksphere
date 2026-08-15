package com.banksphere.kyc.dto;

import com.banksphere.kyc.entity.DocumentStatus;
import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycDocument;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately excludes {@code storageReference} — see
 * {@code DocumentStorage}'s javadoc: it is never serialized to a customer
 * or employee response, only used server-side by the document-content
 * endpoint.
 */
public record KycDocumentResponse(
        UUID id,
        DocumentType documentType,
        DocumentStatus documentStatus,
        String originalFileName,
        String contentType,
        long fileSize,
        Instant submittedAt,
        Instant verifiedAt,
        String rejectionReason
) {
    public static KycDocumentResponse from(KycDocument document) {
        return new KycDocumentResponse(
                document.getId(),
                document.getDocumentType(),
                document.getDocumentStatus(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getFileSize(),
                document.getSubmittedAt(),
                document.getVerifiedAt(),
                document.getRejectionReason());
    }
}
