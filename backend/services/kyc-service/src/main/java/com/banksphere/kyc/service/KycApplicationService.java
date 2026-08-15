package com.banksphere.kyc.service;

import com.banksphere.kyc.dto.CreateKycApplicationRequest;
import com.banksphere.kyc.dto.KycApplicationDetailResponse;
import com.banksphere.kyc.dto.KycApplicationResponse;
import com.banksphere.kyc.dto.KycDocumentResponse;
import com.banksphere.kyc.dto.KycQueueItemResponse;
import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.security.EmployeePrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycApplicationService {

    // Customer-facing
    KycApplicationResponse createApplication(UUID customerId, CreateKycApplicationRequest request);

    KycApplicationResponse getMyApplication(UUID customerId);

    KycApplicationResponse getApplication(UUID customerId, UUID applicationId);

    KycDocumentResponse uploadDocument(UUID customerId, UUID applicationId, DocumentType documentType, MultipartFile file);

    KycApplicationResponse submit(UUID customerId, UUID applicationId);

    KycApplicationResponse resubmit(UUID customerId, UUID applicationId);

    // Employee-facing
    List<KycQueueItemResponse> getQueue(KycStatus statusFilter);

    KycApplicationDetailResponse getApplicationDetail(UUID applicationId);

    /**
     * The Customer 360 aggregation's KYC section — the most recent
     * application for a given customer, or empty if that customer has
     * never started KYC (a normal, common state, not an error). See
     * ADR-008.
     */
    Optional<KycApplicationDetailResponse> getApplicationForCustomer(UUID customerId);

    KycApplicationDetailResponse startReview(EmployeePrincipal employee, UUID applicationId);

    KycDocumentResponse verifyDocument(EmployeePrincipal employee, UUID documentId);

    KycDocumentResponse rejectDocument(EmployeePrincipal employee, UUID documentId, String reason);

    KycApplicationDetailResponse requestInformation(EmployeePrincipal employee, UUID applicationId, String reason);

    KycApplicationDetailResponse approve(EmployeePrincipal employee, UUID applicationId);

    KycApplicationDetailResponse reject(EmployeePrincipal employee, UUID applicationId, String reason);

    DocumentContent getDocumentContent(UUID documentId);
}
