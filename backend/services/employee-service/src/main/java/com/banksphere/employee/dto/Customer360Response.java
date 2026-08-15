package com.banksphere.employee.dto;

import java.util.List;
import java.util.UUID;

/**
 * The Customer 360 aggregation response — composed live from
 * customer-service, account-service, transaction-service,
 * beneficiary-service, and kyc-service on every request (see
 * Customer360ServiceImpl); never persisted or cached in employee-service.
 * See ADR-008.
 *
 * <p>{@code unavailableCapabilities} names domains that do not exist yet
 * in this codebase at all (loans, cards, forex, service requests) — see
 * CLAUDE.md's "do not fabricate" rule. This is NOT the same thing as a
 * {@link Customer360Section} being permission-unavailable; these
 * capabilities are unavailable for every caller, regardless of
 * permissions, because the domain itself has not been built.
 */
public record Customer360Response(
        UUID customerId,
        Customer360Section<CustomerProfileLookupResult> customer,
        Customer360Section<List<AccountSummary>> accounts,
        Customer360Section<List<TransactionLookupResult>> transactions,
        Customer360Section<List<BeneficiaryLookupResult>> beneficiaries,
        Customer360Section<KycApplicationLookupResult> kyc,
        List<String> unavailableCapabilities
) {
}
