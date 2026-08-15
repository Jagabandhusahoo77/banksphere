package com.banksphere.kyc.controller;

import com.banksphere.kyc.dto.CreateKycApplicationRequest;
import com.banksphere.kyc.dto.KycApplicationResponse;
import com.banksphere.kyc.dto.KycDocumentResponse;
import com.banksphere.kyc.entity.DocumentType;
import com.banksphere.kyc.security.CurrentUser;
import com.banksphere.kyc.service.KycApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

/**
 * PUBLIC: none — every endpoint requires a customer-authenticated
 * request (see SecurityConfig). Customer identity is always derived from
 * the JWT via {@link CurrentUser#id} — never from a request body/path
 * variable — and {@link CurrentUser#id} itself rejects an
 * employee-authenticated request with 403 before it ever reaches a
 * service method. The KYC application id in the URL is never trusted on
 * its own; every lookup/mutation additionally enforces ownership in
 * {@code KycApplicationServiceImpl}.
 */
@RestController
@RequestMapping("/api/v1/kyc/applications")
@RequiredArgsConstructor
public class KycApplicationController {

    private final KycApplicationService kycApplicationService;

    @PostMapping
    public ResponseEntity<KycApplicationResponse> createApplication(
            @Valid @RequestBody CreateKycApplicationRequest request, Authentication authentication) {
        KycApplicationResponse response = kycApplicationService.createApplication(CurrentUser.id(authentication), request);
        return ResponseEntity.created(URI.create("/api/v1/kyc/applications/" + response.id())).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<KycApplicationResponse> getMyApplication(Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.getMyApplication(CurrentUser.id(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KycApplicationResponse> getApplication(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.getApplication(CurrentUser.id(authentication), id));
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<KycDocumentResponse> uploadDocument(
            @PathVariable UUID id,
            @RequestParam DocumentType documentType,
            @RequestPart MultipartFile file,
            Authentication authentication) {
        KycDocumentResponse response = kycApplicationService.uploadDocument(
                CurrentUser.id(authentication), id, documentType, file);
        return ResponseEntity.created(URI.create("/api/v1/kyc/applications/" + id + "/documents/" + response.id()))
                .body(response);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<KycApplicationResponse> submit(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.submit(CurrentUser.id(authentication), id));
    }

    @PostMapping("/{id}/resubmit")
    public ResponseEntity<KycApplicationResponse> resubmit(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(kycApplicationService.resubmit(CurrentUser.id(authentication), id));
    }
}
