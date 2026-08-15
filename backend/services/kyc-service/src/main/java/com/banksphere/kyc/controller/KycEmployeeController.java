package com.banksphere.kyc.controller;

import com.banksphere.kyc.dto.KycApplicationDetailResponse;
import com.banksphere.kyc.dto.KycDocumentResponse;
import com.banksphere.kyc.dto.KycQueueItemResponse;
import com.banksphere.kyc.dto.ReasonRequest;
import com.banksphere.kyc.entity.KycStatus;
import com.banksphere.kyc.security.EmployeeCurrentUser;
import com.banksphere.kyc.service.DocumentContent;
import com.banksphere.kyc.service.KycApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * PUBLIC: none. Gated at two layers: {@code SecurityConfig} requires at
 * least {@code KYC_VIEW} on every {@code /api/v1/kyc/employee/**} path
 * (so a customer token — which never carries any {@code KYC_*} authority
 * — is rejected with 403 before reaching any method here), and each
 * method below additionally requires the specific permission its action
 * needs via {@code @PreAuthorize}. Employee identity for every decision
 * comes from {@link EmployeeCurrentUser#identity}, derived from the
 * verified employee JWT — never from the request body (see ADR-007's
 * precedent, extended here to a new domain). KYC review is NOT
 * branch-scoped — see ADR-008's explicit branch-scope decision.
 */
@RestController
@RequestMapping("/api/v1/kyc/employee")
@RequiredArgsConstructor
public class KycEmployeeController {

    private final KycApplicationService kycApplicationService;

    @GetMapping("/queue")
    @PreAuthorize("hasAuthority('KYC_VIEW')")
    public ResponseEntity<List<KycQueueItemResponse>> getQueue(@RequestParam(required = false) KycStatus status) {
        return ResponseEntity.ok(kycApplicationService.getQueue(status));
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAuthority('KYC_VIEW')")
    public ResponseEntity<KycApplicationDetailResponse> getApplication(@PathVariable UUID id) {
        return ResponseEntity.ok(kycApplicationService.getApplicationDetail(id));
    }

    /**
     * The Customer 360 aggregation's KYC section (see employee-service's
     * Customer360ServiceImpl). {@code 204 No Content} — not {@code 404}
     * — when the customer has never started KYC: that is a normal,
     * common state for this lookup, not an error condition.
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAuthority('KYC_VIEW')")
    public ResponseEntity<KycApplicationDetailResponse> getApplicationForCustomer(@PathVariable UUID customerId) {
        return kycApplicationService.getApplicationForCustomer(customerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/applications/{id}/start-review")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<KycApplicationDetailResponse> startReview(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.startReview(EmployeeCurrentUser.identity(authentication), id));
    }

    @PostMapping("/documents/{id}/verify")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<KycDocumentResponse> verifyDocument(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.verifyDocument(EmployeeCurrentUser.identity(authentication), id));
    }

    @PostMapping("/documents/{id}/reject")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<KycDocumentResponse> rejectDocument(
            @PathVariable UUID id, @Valid @RequestBody ReasonRequest request, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.rejectDocument(
                EmployeeCurrentUser.identity(authentication), id, request.reason()));
    }

    @GetMapping("/documents/{id}/content")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<byte[]> getDocumentContent(@PathVariable UUID id) {
        DocumentContent content = kycApplicationService.getDocumentContent(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.fileName()).build().toString())
                .body(content.content());
    }

    @PostMapping("/applications/{id}/request-information")
    @PreAuthorize("hasAuthority('KYC_REVIEW')")
    public ResponseEntity<KycApplicationDetailResponse> requestInformation(
            @PathVariable UUID id, @Valid @RequestBody ReasonRequest request, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.requestInformation(
                EmployeeCurrentUser.identity(authentication), id, request.reason()));
    }

    @PostMapping("/applications/{id}/approve")
    @PreAuthorize("hasAuthority('KYC_APPROVE')")
    public ResponseEntity<KycApplicationDetailResponse> approve(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.approve(EmployeeCurrentUser.identity(authentication), id));
    }

    @PostMapping("/applications/{id}/reject")
    @PreAuthorize("hasAuthority('KYC_REJECT')")
    public ResponseEntity<KycApplicationDetailResponse> reject(
            @PathVariable UUID id, @Valid @RequestBody ReasonRequest request, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.reject(
                EmployeeCurrentUser.identity(authentication), id, request.reason()));
    }
}
