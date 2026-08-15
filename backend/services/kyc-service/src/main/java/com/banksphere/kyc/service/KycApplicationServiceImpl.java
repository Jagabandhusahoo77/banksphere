package com.banksphere.kyc.service;

import com.banksphere.kyc.dto.CreateKycApplicationRequest;
import com.banksphere.kyc.dto.KycApplicationDetailResponse;
import com.banksphere.kyc.dto.KycApplicationResponse;
import com.banksphere.kyc.dto.KycDocumentResponse;
import com.banksphere.kyc.dto.KycQueueItemResponse;
import com.banksphere.kyc.dto.KycStatusHistoryResponse;
import com.banksphere.kyc.entity.DocumentStatus;
import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycApplication;
import com.banksphere.kyc.entity.KycDocument;
import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.entity.KycStatusHistory;
import com.banksphere.kyc.exception.ActiveApplicationExistsException;
import com.banksphere.kyc.exception.DocumentUploadNotAllowedException;
import com.banksphere.kyc.exception.DuplicateDocumentException;
import com.banksphere.kyc.exception.InvalidDocumentException;
import com.banksphere.kyc.exception.KycAccessDeniedException;
import com.banksphere.kyc.exception.KycApplicationNotFoundException;
import com.banksphere.kyc.exception.KycDocumentNotFoundException;
import com.banksphere.kyc.exception.MissingDocumentsException;
import com.banksphere.kyc.repository.KycApplicationRepository;
import com.banksphere.kyc.repository.KycDocumentRepository;
import com.banksphere.kyc.repository.KycStatusHistoryRepository;
import com.banksphere.kyc.security.EmployeePrincipal;
import com.banksphere.kyc.storage.DocumentStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycApplicationServiceImpl implements KycApplicationService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final List<DocumentType> REQUIRED_DOCUMENT_TYPES = List.of(DocumentType.values());
    private static final List<KycStatus> TERMINAL_STATUSES = List.of(KycStatus.APPROVED, KycStatus.REJECTED);

    private final KycApplicationRepository applicationRepository;
    private final KycDocumentRepository documentRepository;
    private final KycStatusHistoryRepository statusHistoryRepository;
    private final DocumentStorage documentStorage;
    private final KycAuditLog auditLog;

    // ---------------------------------------------------------------
    // Customer-facing
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public KycApplicationResponse createApplication(UUID customerId, CreateKycApplicationRequest request) {
        applicationRepository.findFirstByCustomerIdAndStatusNotInOrderByCreatedAtDesc(customerId, TERMINAL_STATUSES)
                .ifPresent(existing -> {
                    throw new ActiveApplicationExistsException(
                            "A KYC application is already in progress (status " + existing.getStatus() + ")");
                });

        KycApplication application = KycApplication.builder()
                .customerId(customerId)
                .panNumber(request.panNumber())
                .occupation(request.occupation())
                .annualIncomeRange(request.annualIncomeRange())
                .build();
        application = applicationRepository.save(application);
        return toResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public KycApplicationResponse getMyApplication(UUID customerId) {
        List<KycApplication> applications = applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        if (applications.isEmpty()) {
            throw new KycApplicationNotFoundException(customerId);
        }
        return toResponse(applications.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public KycApplicationResponse getApplication(UUID customerId, UUID applicationId) {
        return toResponse(getOwnedApplication(customerId, applicationId));
    }

    @Override
    @Transactional
    public KycDocumentResponse uploadDocument(UUID customerId, UUID applicationId, DocumentType documentType, MultipartFile file) {
        KycApplication application = getOwnedApplication(customerId, applicationId);

        if (application.getStatus() != KycStatus.DRAFT && application.getStatus() != KycStatus.ADDITIONAL_INFORMATION_REQUIRED) {
            throw new DocumentUploadNotAllowedException(application.getStatus());
        }
        validateFile(file);

        boolean alreadySatisfied = documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(
                applicationId, documentType, DocumentStatus.REJECTED);
        if (alreadySatisfied) {
            throw new DuplicateDocumentException(
                    "A " + documentType + " document has already been submitted for this application");
        }

        byte[] content = readBytes(file);
        String storageReference = documentStorage.store(applicationId, file.getOriginalFilename(), content);

        KycDocument document = KycDocument.builder()
                .kycApplicationId(applicationId)
                .documentType(documentType)
                .storageReference(storageReference)
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .build();
        document = documentRepository.save(document);

        auditLog.documentUploaded(customerId, applicationId, document.getId(), documentType.name());
        return KycDocumentResponse.from(document);
    }

    @Override
    @Transactional
    public KycApplicationResponse submit(UUID customerId, UUID applicationId) {
        KycApplication application = getOwnedApplication(customerId, applicationId);
        transition(application, KycStatus.SUBMITTED, null);
        application.setSubmittedAt(Instant.now());
        applicationRepository.saveAndFlush(application);
        auditLog.applicationSubmitted(customerId, applicationId);
        return toResponse(application);
    }

    @Override
    @Transactional
    public KycApplicationResponse resubmit(UUID customerId, UUID applicationId) {
        KycApplication application = getOwnedApplication(customerId, applicationId);
        transition(application, KycStatus.RESUBMITTED, null);
        application.setSubmittedAt(Instant.now());
        applicationRepository.saveAndFlush(application);
        auditLog.applicationSubmitted(customerId, applicationId);
        return toResponse(application);
    }

    // ---------------------------------------------------------------
    // Employee-facing
    // ---------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<KycQueueItemResponse> getQueue(KycStatus statusFilter) {
        return applicationRepository.findQueue(statusFilter).stream()
                .map(application -> {
                    List<DocumentType> missing = computeMissingDocumentTypes(application.getId());
                    int required = REQUIRED_DOCUMENT_TYPES.size();
                    return new KycQueueItemResponse(
                            application.getId(),
                            application.getCustomerId(),
                            application.getSubmittedAt(),
                            application.getStatus(),
                            required - missing.size(),
                            required,
                            application.getCurrentReviewerId());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KycApplicationDetailResponse getApplicationDetail(UUID applicationId) {
        return toDetailResponse(getApplicationOrThrow(applicationId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KycApplicationDetailResponse> getApplicationForCustomer(UUID customerId) {
        return applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .findFirst()
                .map(this::toDetailResponse);
    }

    @Override
    @Transactional
    public KycApplicationDetailResponse startReview(EmployeePrincipal employee, UUID applicationId) {
        KycApplication application = getApplicationOrThrow(applicationId);
        transition(application, KycStatus.UNDER_REVIEW, employee.employeeId());
        application.setCurrentReviewerId(employee.employeeId());
        applicationRepository.saveAndFlush(application);
        auditLog.reviewStarted(employee, application.getCustomerId(), applicationId);
        return toDetailResponse(application);
    }

    @Override
    @Transactional
    public KycDocumentResponse verifyDocument(EmployeePrincipal employee, UUID documentId) {
        KycDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new KycDocumentNotFoundException(documentId));
        document.setDocumentStatus(DocumentStatus.VERIFIED);
        document.setVerifiedAt(Instant.now());
        document.setVerifiedBy(employee.employeeId());
        document.setRejectionReason(null);
        document = documentRepository.save(document);

        KycApplication application = getApplicationOrThrow(document.getKycApplicationId());
        auditLog.documentVerified(employee, application.getCustomerId(), application.getId(), documentId);
        return KycDocumentResponse.from(document);
    }

    @Override
    @Transactional
    public KycDocumentResponse rejectDocument(EmployeePrincipal employee, UUID documentId, String reason) {
        KycDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new KycDocumentNotFoundException(documentId));
        document.setDocumentStatus(DocumentStatus.REJECTED);
        document.setVerifiedAt(Instant.now());
        document.setVerifiedBy(employee.employeeId());
        document.setRejectionReason(reason);
        document = documentRepository.save(document);

        KycApplication application = getApplicationOrThrow(document.getKycApplicationId());
        auditLog.documentRejected(employee, application.getCustomerId(), application.getId(), documentId, reason);
        return KycDocumentResponse.from(document);
    }

    @Override
    @Transactional
    public KycApplicationDetailResponse requestInformation(EmployeePrincipal employee, UUID applicationId, String reason) {
        KycApplication application = getApplicationOrThrow(applicationId);
        transition(application, KycStatus.ADDITIONAL_INFORMATION_REQUIRED, employee.employeeId());
        application.setReviewReason(reason);
        applicationRepository.saveAndFlush(application);
        auditLog.additionalInformationRequested(employee, application.getCustomerId(), applicationId, reason);
        return toDetailResponse(application);
    }

    @Override
    @Transactional
    public KycApplicationDetailResponse approve(EmployeePrincipal employee, UUID applicationId) {
        KycApplication application = getApplicationOrThrow(applicationId);
        transition(application, KycStatus.APPROVED, employee.employeeId());
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(employee.employeeId());
        applicationRepository.saveAndFlush(application);
        auditLog.applicationApproved(employee, application.getCustomerId(), applicationId);
        return toDetailResponse(application);
    }

    @Override
    @Transactional
    public KycApplicationDetailResponse reject(EmployeePrincipal employee, UUID applicationId, String reason) {
        KycApplication application = getApplicationOrThrow(applicationId);
        transition(application, KycStatus.REJECTED, employee.employeeId());
        application.setReviewedAt(Instant.now());
        application.setReviewedBy(employee.employeeId());
        application.setReviewReason(reason);
        applicationRepository.saveAndFlush(application);
        auditLog.applicationRejected(employee, application.getCustomerId(), applicationId, reason);
        return toDetailResponse(application);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentContent getDocumentContent(UUID documentId) {
        KycDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new KycDocumentNotFoundException(documentId));
        byte[] content = documentStorage.load(document.getStorageReference());
        return new DocumentContent(content, document.getContentType(), document.getOriginalFileName());
    }

    // ---------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------

    private KycApplication getOwnedApplication(UUID customerId, UUID applicationId) {
        KycApplication application = getApplicationOrThrow(applicationId);
        if (!application.getCustomerId().equals(customerId)) {
            throw new KycAccessDeniedException("This KYC application does not belong to the authenticated customer");
        }
        return application;
    }

    private KycApplication getApplicationOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new KycApplicationNotFoundException(applicationId));
    }

    /**
     * Every mutation that changes {@code status} funnels through here so
     * {@code KycStateMachine} is the single authority — see its own
     * javadoc. {@code changedByEmployeeId} is null for a customer-initiated
     * transition (submit/resubmit), matching {@code KycStatusHistory}'s
     * documented convention.
     */
    private void transition(KycApplication application, KycStatus to, UUID changedByEmployeeId) {
        KycStatus from = application.getStatus();
        KycStateMachine.requireValidTransition(from, to);

        if (to == KycStatus.SUBMITTED || to == KycStatus.RESUBMITTED) {
            List<DocumentType> missing = computeMissingDocumentTypes(application.getId());
            if (!missing.isEmpty()) {
                throw new MissingDocumentsException(missing);
            }
        }

        application.setStatus(to);
        KycStatusHistory history = KycStatusHistory.builder()
                .kycApplicationId(application.getId())
                .fromStatus(from)
                .toStatus(to)
                .changedByEmployeeId(changedByEmployeeId)
                .reason(application.getReviewReason())
                .build();
        statusHistoryRepository.save(history);
    }

    private List<DocumentType> computeMissingDocumentTypes(UUID applicationId) {
        return REQUIRED_DOCUMENT_TYPES.stream()
                .filter(type -> !documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(
                        applicationId, type, DocumentStatus.REJECTED))
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("A non-empty file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidDocumentException("File exceeds the maximum allowed size of 5MB");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidDocumentException("Unsupported file type: " + file.getContentType());
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new InvalidDocumentException("Could not read uploaded file");
        }
    }

    private KycApplicationResponse toResponse(KycApplication application) {
        List<KycDocumentResponse> documents = documentRepository.findByKycApplicationIdOrderBySubmittedAtAsc(application.getId())
                .stream().map(KycDocumentResponse::from).toList();
        List<DocumentType> missing = computeMissingDocumentTypes(application.getId());
        return new KycApplicationResponse(
                application.getId(),
                application.getStatus(),
                application.getPanNumber(),
                application.getOccupation(),
                application.getAnnualIncomeRange(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getReviewReason(),
                missing,
                documents,
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private KycApplicationDetailResponse toDetailResponse(KycApplication application) {
        List<KycDocumentResponse> documents = documentRepository.findByKycApplicationIdOrderBySubmittedAtAsc(application.getId())
                .stream().map(KycDocumentResponse::from).toList();
        List<KycStatusHistoryResponse> history = statusHistoryRepository.findByKycApplicationIdOrderByChangedAtAsc(application.getId())
                .stream().map(KycStatusHistoryResponse::from).toList();
        List<DocumentType> missing = computeMissingDocumentTypes(application.getId());
        return new KycApplicationDetailResponse(
                application.getId(),
                application.getCustomerId(),
                application.getStatus(),
                application.getPanNumber(),
                application.getOccupation(),
                application.getAnnualIncomeRange(),
                application.getCurrentReviewerId(),
                application.getSubmittedAt(),
                application.getReviewedAt(),
                application.getReviewedBy(),
                application.getReviewReason(),
                missing,
                documents,
                history,
                application.getVersion(),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }
}
