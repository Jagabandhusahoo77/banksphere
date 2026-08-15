package com.banksphere.beneficiary.controller;

import com.banksphere.beneficiary.dto.BeneficiaryResponse;
import com.banksphere.beneficiary.dto.CreateBeneficiaryRequest;
import com.banksphere.beneficiary.dto.UpdateBeneficiaryRequest;
import com.banksphere.beneficiary.security.CurrentUser;
import com.banksphere.beneficiary.service.BeneficiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * PUBLIC: none — every endpoint requires authentication (see
 * SecurityConfig). The owning customer is always derived from the JWT
 * (see CurrentUser), never a client-supplied id — there is deliberately
 * no {@code customerId} path/query parameter or request-body field
 * anywhere in this controller. Every lookup/mutation additionally
 * enforces that the authenticated caller owns the beneficiary (see
 * BeneficiaryServiceImpl) — the beneficiary id in the URL is never
 * trusted on its own.
 */
@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @PostMapping
    public ResponseEntity<BeneficiaryResponse> createBeneficiary(
            @Valid @RequestBody CreateBeneficiaryRequest request, Authentication authentication) {
        BeneficiaryResponse response = beneficiaryService.createBeneficiary(request, CurrentUser.id(authentication));
        return ResponseEntity.created(URI.create("/api/v1/beneficiaries/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<BeneficiaryResponse>> getBeneficiaries(Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.getBeneficiaries(CurrentUser.id(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> getBeneficiary(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.getBeneficiary(id, CurrentUser.id(authentication)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BeneficiaryResponse> updateBeneficiary(
            @PathVariable UUID id, @Valid @RequestBody UpdateBeneficiaryRequest request, Authentication authentication) {
        return ResponseEntity.ok(beneficiaryService.updateBeneficiary(id, request, CurrentUser.id(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateBeneficiary(@PathVariable UUID id, Authentication authentication) {
        beneficiaryService.deactivateBeneficiary(id, CurrentUser.id(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * Phase 9C — Customer 360's beneficiaries section. Reachable only by
     * an employee token holding {@code CUSTOMER_VIEW} (reused rather than
     * introducing a new {@code BENEFICIARY_VIEW} permission — see
     * ADR-008); a customer token can never carry that authority.
     */
    @GetMapping("/employee/customer/{customerId}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<List<BeneficiaryResponse>> getBeneficiariesForEmployee(@PathVariable UUID customerId) {
        return ResponseEntity.ok(beneficiaryService.getBeneficiariesForEmployee(customerId));
    }
}
