package com.banksphere.kyc.service;

import com.banksphere.kyc.dto.CreateKycApplicationRequest;
import com.banksphere.kyc.dto.KycApplicationDetailResponse;
import com.banksphere.kyc.dto.KycApplicationResponse;
import com.banksphere.kyc.dto.KycDocumentResponse;
import com.banksphere.kyc.dto.KycQueueItemResponse;
import com.banksphere.kyc.entity.DocumentStatus;
import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.entity.KycApplication;
import com.banksphere.kyc.entity.KycDocument;
import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.exception.ActiveApplicationExistsException;
import com.banksphere.kyc.exception.DocumentUploadNotAllowedException;
import com.banksphere.kyc.exception.DuplicateDocumentException;
import com.banksphere.kyc.exception.InvalidDocumentException;
import com.banksphere.kyc.exception.InvalidStateTransitionException;
import com.banksphere.kyc.exception.KycAccessDeniedException;
import com.banksphere.kyc.exception.KycApplicationNotFoundException;
import com.banksphere.kyc.exception.MissingDocumentsException;
import com.banksphere.kyc.repository.KycApplicationRepository;
import com.banksphere.kyc.repository.KycDocumentRepository;
import com.banksphere.kyc.repository.KycStatusHistoryRepository;
import com.banksphere.kyc.security.EmployeePrincipal;
import com.banksphere.kyc.storage.DocumentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycApplicationServiceImplTest {

    @Mock
    private KycApplicationRepository applicationRepository;
    @Mock
    private KycDocumentRepository documentRepository;
    @Mock
    private KycStatusHistoryRepository statusHistoryRepository;
    @Mock
    private DocumentStorage documentStorage;
    @Mock
    private KycAuditLog auditLog;

    private KycApplicationServiceImpl service;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        service = new KycApplicationServiceImpl(applicationRepository, documentRepository, statusHistoryRepository, documentStorage, auditLog);
        customerId = UUID.randomUUID();
    }

    private KycApplication draftApplication(UUID owner) {
        return KycApplication.builder()
                .id(UUID.randomUUID())
                .customerId(owner)
                .status(KycStatus.DRAFT)
                .panNumber("ABCDE1234F")
                .occupation("Engineer")
                .annualIncomeRange("5-10L")
                .version(0L)
                .build();
    }

    private CreateKycApplicationRequest createRequest() {
        return new CreateKycApplicationRequest("ABCDE1234F", "Engineer", "5-10L");
    }

    private EmployeePrincipal employee() {
        return new EmployeePrincipal(UUID.randomUUID(), "EMP001", UUID.randomUUID(), "BANK0HQ0001",
                List.of("KYC_OFFICER"), List.of("KYC_VIEW", "KYC_REVIEW", "KYC_APPROVE", "KYC_REJECT"));
    }

    private void stubEmptyDocuments(UUID applicationId) {
        when(documentRepository.findByKycApplicationIdOrderBySubmittedAtAsc(applicationId)).thenReturn(List.of());
    }

    private void stubAllDocumentTypesPresent(UUID applicationId) {
        when(documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(
                eq(applicationId), any(), eq(DocumentStatus.REJECTED))).thenReturn(true);
    }

    // ---------------------------------------------------------------
    // createApplication
    // ---------------------------------------------------------------

    @Test
    void createApplication_persistsDraftApplication_whenNoActiveApplicationExists() {
        when(applicationRepository.findFirstByCustomerIdAndStatusNotInOrderByCreatedAtDesc(eq(customerId), any()))
                .thenReturn(Optional.empty());
        when(applicationRepository.save(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.findByKycApplicationIdOrderBySubmittedAtAsc(any())).thenReturn(List.of());
        when(documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(any(), any(), any())).thenReturn(false);

        KycApplicationResponse response = service.createApplication(customerId, createRequest());

        assertThat(response.status()).isEqualTo(KycStatus.DRAFT);
        assertThat(response.panNumber()).isEqualTo("ABCDE1234F");

        ArgumentCaptor<KycApplication> captor = ArgumentCaptor.forClass(KycApplication.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
    }

    @Test
    void createApplication_throwsActiveApplicationExists_whenNonTerminalApplicationAlreadyExists() {
        when(applicationRepository.findFirstByCustomerIdAndStatusNotInOrderByCreatedAtDesc(eq(customerId), any()))
                .thenReturn(Optional.of(draftApplication(customerId)));

        assertThatThrownBy(() -> service.createApplication(customerId, createRequest()))
                .isInstanceOf(ActiveApplicationExistsException.class);
        verify(applicationRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // getApplication
    // ---------------------------------------------------------------

    @Test
    void getApplication_returnsApplication_whenCallerOwnsIt() {
        KycApplication application = draftApplication(customerId);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        stubEmptyDocuments(application.getId());
        stubAllDocumentTypesPresent(application.getId());

        KycApplicationResponse response = service.getApplication(customerId, application.getId());

        assertThat(response.id()).isEqualTo(application.getId());
    }

    @Test
    void getApplication_throwsAccessDenied_whenCallerDoesNotOwnApplication() {
        KycApplication application = draftApplication(UUID.randomUUID());
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.getApplication(customerId, application.getId()))
                .isInstanceOf(KycAccessDeniedException.class);
    }

    @Test
    void getApplication_throwsNotFound_whenApplicationDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(applicationRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getApplication(customerId, missingId))
                .isInstanceOf(KycApplicationNotFoundException.class);
    }

    // ---------------------------------------------------------------
    // uploadDocument
    // ---------------------------------------------------------------

    @Test
    void uploadDocument_storesDocument_whenApplicationIsDraftAndFileIsValid() {
        KycApplication application = draftApplication(customerId);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(
                eq(application.getId()), eq(DocumentType.PAN), eq(DocumentStatus.REJECTED))).thenReturn(false);
        when(documentStorage.store(eq(application.getId()), anyString(), any())).thenReturn("stored/ref.pdf");
        when(documentRepository.save(any(KycDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", "content".getBytes());
        KycDocumentResponse response = service.uploadDocument(customerId, application.getId(), DocumentType.PAN, file);

        assertThat(response.documentType()).isEqualTo(DocumentType.PAN);
        assertThat(response.documentStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void uploadDocument_throwsAccessDenied_whenCallerDoesNotOwnApplication() {
        KycApplication application = draftApplication(UUID.randomUUID());
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", "content".getBytes());
        assertThatThrownBy(() -> service.uploadDocument(customerId, application.getId(), DocumentType.PAN, file))
                .isInstanceOf(KycAccessDeniedException.class);
    }

    @Test
    void uploadDocument_throwsInvalidDocument_whenFileTypeUnsupported() {
        KycApplication application = draftApplication(customerId);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/x-msdownload", "content".getBytes());
        assertThatThrownBy(() -> service.uploadDocument(customerId, application.getId(), DocumentType.PAN, file))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void uploadDocument_throwsDuplicateDocument_whenNonRejectedDocumentOfSameTypeAlreadyExists() {
        KycApplication application = draftApplication(customerId);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(
                eq(application.getId()), eq(DocumentType.PAN), eq(DocumentStatus.REJECTED))).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", "content".getBytes());
        assertThatThrownBy(() -> service.uploadDocument(customerId, application.getId(), DocumentType.PAN, file))
                .isInstanceOf(DuplicateDocumentException.class);
    }

    @Test
    void uploadDocument_throwsUploadNotAllowed_whenApplicationNotInDraftOrInfoRequested() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.UNDER_REVIEW);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        MockMultipartFile file = new MockMultipartFile("file", "pan.pdf", "application/pdf", "content".getBytes());
        assertThatThrownBy(() -> service.uploadDocument(customerId, application.getId(), DocumentType.PAN, file))
                .isInstanceOf(DocumentUploadNotAllowedException.class);
    }

    // ---------------------------------------------------------------
    // submit / resubmit
    // ---------------------------------------------------------------

    @Test
    void submit_transitionsToSubmitted_whenAllRequiredDocumentTypesPresent() {
        KycApplication application = draftApplication(customerId);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        stubAllDocumentTypesPresent(application.getId());
        when(applicationRepository.saveAndFlush(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyDocuments(application.getId());

        KycApplicationResponse response = service.submit(customerId, application.getId());

        assertThat(response.status()).isEqualTo(KycStatus.SUBMITTED);
        verify(statusHistoryRepository).save(any());
        verify(auditLog).applicationSubmitted(customerId, application.getId());
    }

    @Test
    void submit_throwsMissingDocuments_whenARequiredDocumentTypeIsAbsent() {
        KycApplication application = draftApplication(customerId);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(any(), any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.submit(customerId, application.getId()))
                .isInstanceOf(MissingDocumentsException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void submit_throwsInvalidStateTransition_whenApplicationNotInDraft() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.UNDER_REVIEW);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.submit(customerId, application.getId()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void resubmit_transitionsToResubmitted_whenApplicationAwaitingAdditionalInformation() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.ADDITIONAL_INFORMATION_REQUIRED);
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        stubAllDocumentTypesPresent(application.getId());
        when(applicationRepository.saveAndFlush(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyDocuments(application.getId());

        KycApplicationResponse response = service.resubmit(customerId, application.getId());

        assertThat(response.status()).isEqualTo(KycStatus.RESUBMITTED);
    }

    // ---------------------------------------------------------------
    // Employee queue / detail
    // ---------------------------------------------------------------

    @Test
    void getQueue_reportsDocumentCompleteness_forEachApplication() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.SUBMITTED);
        when(applicationRepository.findQueue(KycStatus.SUBMITTED)).thenReturn(List.of(application));
        when(documentRepository.existsByKycApplicationIdAndDocumentTypeAndDocumentStatusNot(any(), any(), any()))
                .thenReturn(true, true, false, true);

        List<KycQueueItemResponse> queue = service.getQueue(KycStatus.SUBMITTED);

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).documentsRequired()).isEqualTo(4);
        assertThat(queue.get(0).documentsSubmitted()).isEqualTo(3);
    }

    // ---------------------------------------------------------------
    // Employee review actions
    // ---------------------------------------------------------------

    @Test
    void startReview_movesSubmittedToUnderReview_andSetsCurrentReviewer() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.SUBMITTED);
        EmployeePrincipal reviewer = employee();
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.saveAndFlush(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyDocuments(application.getId());

        KycApplicationDetailResponse response = service.startReview(reviewer, application.getId());

        assertThat(response.status()).isEqualTo(KycStatus.UNDER_REVIEW);
        assertThat(response.currentReviewerId()).isEqualTo(reviewer.employeeId());
        verify(auditLog).reviewStarted(reviewer, customerId, application.getId());
    }

    @Test
    void verifyDocument_marksDocumentVerified_andClearsAnyRejectionReason() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.UNDER_REVIEW);
        KycDocument document = KycDocument.builder()
                .id(UUID.randomUUID())
                .kycApplicationId(application.getId())
                .documentType(DocumentType.PAN)
                .documentStatus(DocumentStatus.PENDING)
                .storageReference("ref")
                .originalFileName("pan.pdf")
                .contentType("application/pdf")
                .fileSize(100)
                .build();
        EmployeePrincipal reviewer = employee();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentRepository.save(any(KycDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        KycDocumentResponse response = service.verifyDocument(reviewer, document.getId());

        assertThat(response.documentStatus()).isEqualTo(DocumentStatus.VERIFIED);
        verify(auditLog).documentVerified(reviewer, customerId, application.getId(), document.getId());
    }

    @Test
    void rejectDocument_marksDocumentRejected_withReason() {
        KycApplication application = draftApplication(customerId);
        KycDocument document = KycDocument.builder()
                .id(UUID.randomUUID())
                .kycApplicationId(application.getId())
                .documentType(DocumentType.PAN)
                .documentStatus(DocumentStatus.PENDING)
                .storageReference("ref")
                .originalFileName("pan.pdf")
                .contentType("application/pdf")
                .fileSize(100)
                .build();
        EmployeePrincipal reviewer = employee();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentRepository.save(any(KycDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        KycDocumentResponse response = service.rejectDocument(reviewer, document.getId(), "blurry image");

        assertThat(response.documentStatus()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("blurry image");
    }

    @Test
    void requestInformation_movesUnderReviewToAdditionalInformationRequired_withCustomerVisibleReason() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.UNDER_REVIEW);
        EmployeePrincipal reviewer = employee();
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.saveAndFlush(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyDocuments(application.getId());

        KycApplicationDetailResponse response = service.requestInformation(reviewer, application.getId(), "Please upload a clearer address proof");

        assertThat(response.status()).isEqualTo(KycStatus.ADDITIONAL_INFORMATION_REQUIRED);
        assertThat(response.reviewReason()).isEqualTo("Please upload a clearer address proof");
    }

    @Test
    void approve_movesUnderReviewToApproved_andRecordsReviewer() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.UNDER_REVIEW);
        EmployeePrincipal reviewer = employee();
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.saveAndFlush(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyDocuments(application.getId());

        KycApplicationDetailResponse response = service.approve(reviewer, application.getId());

        assertThat(response.status()).isEqualTo(KycStatus.APPROVED);
        assertThat(response.reviewedBy()).isEqualTo(reviewer.employeeId());
    }

    @Test
    void approve_throwsInvalidStateTransition_whenApplicationNotUnderReview() {
        KycApplication application = draftApplication(customerId);
        EmployeePrincipal reviewer = employee();
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.approve(reviewer, application.getId()))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void getApplicationForCustomer_returnsMostRecentApplication_whenOneExists() {
        KycApplication application = draftApplication(customerId);
        when(applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(List.of(application));
        stubEmptyDocuments(application.getId());

        Optional<KycApplicationDetailResponse> response = service.getApplicationForCustomer(customerId);

        assertThat(response).isPresent();
        assertThat(response.get().customerId()).isEqualTo(customerId);
    }

    @Test
    void getApplicationForCustomer_returnsEmpty_whenCustomerHasNeverStartedKyc() {
        when(applicationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)).thenReturn(List.of());

        Optional<KycApplicationDetailResponse> response = service.getApplicationForCustomer(customerId);

        assertThat(response).isEmpty();
    }

    @Test
    void reject_movesUnderReviewToRejected_withCustomerVisibleReason() {
        KycApplication application = draftApplication(customerId);
        application.setStatus(KycStatus.UNDER_REVIEW);
        EmployeePrincipal reviewer = employee();
        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.saveAndFlush(any(KycApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        stubEmptyDocuments(application.getId());

        KycApplicationDetailResponse response = service.reject(reviewer, application.getId(), "PAN mismatch");

        assertThat(response.status()).isEqualTo(KycStatus.REJECTED);
        assertThat(response.reviewReason()).isEqualTo("PAN mismatch");
    }
}
