package com.banksphere.customer.service;

import com.banksphere.customer.dto.CustomerCreateRequest;
import com.banksphere.customer.dto.CustomerLookupResponse;
import com.banksphere.customer.dto.CustomerResponse;
import com.banksphere.customer.dto.CustomerUpdateRequest;

import java.util.UUID;

public interface CustomerService {

    CustomerResponse createCustomer(CustomerCreateRequest request);

    /** Throws CustomerAccessDeniedException if {@code requestingCustomerId} != {@code id}. */
    CustomerResponse getCustomer(UUID id, UUID requestingCustomerId);

    /** Throws CustomerAccessDeniedException if {@code requestingCustomerId} != {@code id}. */
    CustomerResponse updateCustomer(UUID id, CustomerUpdateRequest request, UUID requestingCustomerId);

    /**
     * Phase 9B — an employee holding {@code CUSTOMER_VIEW} looking up any
     * customer by id, e.g. to confirm a name before a cash deposit. No
     * self-ownership check (there is no "self" for an employee) — the
     * permission check happens at the controller layer via
     * {@code @PreAuthorize}; this deliberately returns
     * {@link CustomerLookupResponse}, not the fuller {@link CustomerResponse}
     * self-service uses, so no more than a name/status ever crosses this
     * boundary. See ADR-007.
     */
    CustomerLookupResponse employeeLookup(UUID id);

    /**
     * Phase 9C — the Customer 360 aggregation in employee-service needs
     * the fuller profile (contact details, created date) a name/status
     * pair can't provide. A deliberately separate method/endpoint from
     * {@link #employeeLookup}, not a widening of it, so the original
     * cash-deposit lookup's minimal-exposure contract is untouched — see
     * ADR-008. Still never exposes a password/credential (this service's
     * {@link CustomerResponse} never carries one — see CLAUDE.md's
     * "structurally absent" rule).
     */
    CustomerResponse employeeLookupFullProfile(UUID id);
}
