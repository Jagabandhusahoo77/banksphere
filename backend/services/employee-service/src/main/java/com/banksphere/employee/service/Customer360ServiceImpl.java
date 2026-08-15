package com.banksphere.employee.service;

import com.banksphere.employee.dto.AccountSummary;
import com.banksphere.employee.dto.BeneficiaryLookupResult;
import com.banksphere.employee.dto.Customer360Response;
import com.banksphere.employee.dto.Customer360Section;
import com.banksphere.employee.dto.CustomerProfileLookupResult;
import com.banksphere.employee.dto.KycApplicationLookupResult;
import com.banksphere.employee.dto.TransactionLookupResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * See Customer360Service's javadoc for the section-level graceful
 * degradation contract. Deliberately does NOT copy any downstream data
 * into employee-service's own database — every field here is fetched
 * live on each request (see ADR-008's "authorized aggregation, not data
 * duplication" decision).
 */
@Service
@RequiredArgsConstructor
public class Customer360ServiceImpl implements Customer360Service {

    /**
     * Domains this codebase has not built yet — see CLAUDE.md's "do not
     * fabricate" rule and this phase's explicit "display future
     * capabilities as unavailable" instruction. A static, always-the-same
     * list, not derived from any permission check.
     */
    private static final List<String> UNAVAILABLE_CAPABILITIES =
            List.of("LOANS", "CARDS", "FOREX", "SERVICE_REQUESTS");

    private static final int MAX_TRANSACTIONS_DISPLAYED = 20;

    private final CustomerLookupClient customerLookupClient;
    private final AccountOperationsClient accountOperationsClient;
    private final TransactionLookupClient transactionLookupClient;
    private final BeneficiaryLookupClient beneficiaryLookupClient;
    private final KycLookupClient kycLookupClient;

    @Override
    public Customer360Response getCustomer360(UUID customerId, Set<String> callerPermissions, String bearerToken) {
        Customer360Section<CustomerProfileLookupResult> customerSection = customerSection(customerId, callerPermissions, bearerToken);
        Customer360Section<List<AccountSummary>> accountsSection = accountsSection(customerId, callerPermissions, bearerToken);
        Customer360Section<List<TransactionLookupResult>> transactionsSection =
                transactionsSection(accountsSection, callerPermissions, bearerToken);
        Customer360Section<List<BeneficiaryLookupResult>> beneficiariesSection = beneficiariesSection(customerId, callerPermissions, bearerToken);
        Customer360Section<KycApplicationLookupResult> kycSection = kycSection(customerId, callerPermissions, bearerToken);

        return new Customer360Response(
                customerId, customerSection, accountsSection, transactionsSection, beneficiariesSection, kycSection,
                UNAVAILABLE_CAPABILITIES);
    }

    private Customer360Section<CustomerProfileLookupResult> customerSection(
            UUID customerId, Set<String> permissions, String bearerToken) {
        if (!permissions.contains("CUSTOMER_VIEW")) {
            return Customer360Section.unavailable("Requires CUSTOMER_VIEW");
        }
        return Customer360Section.of(customerLookupClient.lookupFullProfile(customerId, bearerToken));
    }

    private Customer360Section<List<AccountSummary>> accountsSection(
            UUID customerId, Set<String> permissions, String bearerToken) {
        if (!permissions.contains("ACCOUNT_VIEW")) {
            return Customer360Section.unavailable("Requires ACCOUNT_VIEW");
        }
        List<AccountSummary> accounts = accountOperationsClient.lookupByCustomerId(customerId, bearerToken).stream()
                .map(AccountSummary::from)
                .toList();
        return Customer360Section.of(accounts);
    }

    /**
     * Requires BOTH {@code ACCOUNT_VIEW} (to know which accounts to query
     * — this section has no independent customer-scoped endpoint of its
     * own) and {@code TRANSACTION_VIEW}. Aggregates each account's recent
     * transactions and caps the combined view at {@link
     * #MAX_TRANSACTIONS_DISPLAYED}, most recent first.
     */
    private Customer360Section<List<TransactionLookupResult>> transactionsSection(
            Customer360Section<List<AccountSummary>> accountsSection, Set<String> permissions, String bearerToken) {
        if (!permissions.contains("TRANSACTION_VIEW")) {
            return Customer360Section.unavailable("Requires TRANSACTION_VIEW");
        }
        if (!accountsSection.available()) {
            return Customer360Section.unavailable("Requires ACCOUNT_VIEW (to identify which accounts to show transactions for)");
        }

        List<TransactionLookupResult> transactions = accountsSection.data().stream()
                .flatMap(account -> transactionLookupClient.recentTransactionsForAccount(account.id(), bearerToken).stream())
                .sorted(Comparator.comparing(TransactionLookupResult::createdAt).reversed())
                .limit(MAX_TRANSACTIONS_DISPLAYED)
                .toList();
        return Customer360Section.of(transactions);
    }

    private Customer360Section<List<BeneficiaryLookupResult>> beneficiariesSection(
            UUID customerId, Set<String> permissions, String bearerToken) {
        if (!permissions.contains("CUSTOMER_VIEW")) {
            return Customer360Section.unavailable("Requires CUSTOMER_VIEW");
        }
        return Customer360Section.of(beneficiaryLookupClient.lookupByCustomerId(customerId, bearerToken));
    }

    private Customer360Section<KycApplicationLookupResult> kycSection(
            UUID customerId, Set<String> permissions, String bearerToken) {
        if (!permissions.contains("KYC_VIEW")) {
            return Customer360Section.unavailable("Requires KYC_VIEW");
        }
        return Customer360Section.of(kycLookupClient.lookupByCustomerId(customerId, bearerToken).orElse(null));
    }
}
