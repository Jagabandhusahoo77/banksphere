package com.banksphere.employee.dto;

import java.util.List;
import java.util.UUID;

/**
 * The "find customer" step's response — resolved via either an account
 * number or a customer id (see {@code OperationsController}), always
 * returning the customer's name plus every one of their accounts so the
 * employee can pick which one to credit. Deliberately does not include
 * anything beyond first/last name — see {@code CustomerLookupResponse} on
 * the customer-service side for why.
 */
public record CustomerSearchResponse(UUID customerId, String customerName, List<AccountSummary> accounts) {
}
