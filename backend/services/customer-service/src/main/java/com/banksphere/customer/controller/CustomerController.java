package com.banksphere.customer.controller;

import com.banksphere.customer.dto.CustomerCreateRequest;
import com.banksphere.customer.dto.CustomerLookupResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.CustomerUpdateRequest;
import com.banksphere.customer.security.CurrentUser;
import com.banksphere.customer.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * PUBLIC: none — every endpoint here requires authentication (see
 * SecurityConfig). {@code POST /} is kept for completeness/future
 * admin tooling but is superseded by {@code POST /api/v1/auth/register}
 * for real signups (that endpoint creates both the profile and
 * credentials atomically; this one only creates a profile, with no way
 * to ever log in as that customer). GET/PUT by id additionally enforce
 * that the authenticated customer matches the requested id — see
 * CustomerService.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        CustomerResponse response = customerService.createCustomer(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(customerService.getCustomer(id, CurrentUser.id(authentication)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable UUID id,
                                                             @Valid @RequestBody CustomerUpdateRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request, CurrentUser.id(authentication)));
    }

    /**
     * Phase 9B — reachable only by an employee token holding {@code
     * CUSTOMER_VIEW} (a customer token can never carry that authority, so
     * this is unreachable by any customer regardless of whose id they
     * pass). A deliberately separate path from {@code GET /{id}} above
     * rather than branching that method's existing self-only logic — zero
     * risk of changing customer-self-service behavior. See ADR-007.
     */
    @GetMapping("/employee-lookup/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerLookupResponse> employeeLookup(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.employeeLookup(id));
    }

    /**
     * Phase 9C — additive, not a replacement for {@code employee-lookup}
     * above: the Customer 360 aggregation in employee-service needs the
     * fuller profile this returns. Same {@code CUSTOMER_VIEW} gate. See
     * ADR-008.
     */
    @GetMapping("/employee-lookup/{id}/profile")
    @PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ResponseEntity<CustomerResponse> employeeLookupFullProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.employeeLookupFullProfile(id));
    }
}
