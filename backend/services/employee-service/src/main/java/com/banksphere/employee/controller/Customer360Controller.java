package com.banksphere.employee.controller;

import com.banksphere.employee.dto.Customer360Response;
import com.banksphere.employee.security.CurrentUser;
import com.banksphere.employee.service.Customer360Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customer 360 — an authorized aggregation over customer-service,
 * account-service, transaction-service, beneficiary-service, and
 * kyc-service, orchestrated on the caller's behalf (forwarding their own
 * verified bearer token to each, exactly like OperationsController — see
 * ADR-007/ADR-008). employee-service copies none of this data into its
 * own database — see Customer360ServiceImpl.
 *
 * <p>The {@code @PreAuthorize} here is only the FLOOR: a caller needs at
 * least one of the four view permissions to reach this endpoint at all.
 * Which sections actually come back populated is a finer-grained,
 * per-section decision made inside {@code Customer360ServiceImpl} against
 * the caller's full permission set — see {@code Customer360Section}'s
 * javadoc for why this is a deliberate departure from this codebase's
 * usual "one permission gates one whole endpoint" pattern.
 */
@RestController
@RequestMapping("/api/v1/employee/customers")
@RequiredArgsConstructor
public class Customer360Controller {

    private final Customer360Service customer360Service;

    @GetMapping("/{customerId}/360")
    @PreAuthorize("hasAnyAuthority('CUSTOMER_VIEW', 'ACCOUNT_VIEW', 'TRANSACTION_VIEW', 'KYC_VIEW')")
    public ResponseEntity<Customer360Response> getCustomer360(
            @PathVariable UUID customerId,
            Authentication authentication,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        Set<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .collect(Collectors.toSet());
        return ResponseEntity.ok(customer360Service.getCustomer360(
                customerId, permissions, CurrentUser.bearerToken(authorizationHeader)));
    }
}
