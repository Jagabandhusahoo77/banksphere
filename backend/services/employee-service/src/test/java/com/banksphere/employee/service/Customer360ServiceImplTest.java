package com.banksphere.employee.service;

import com.banksphere.employee.dto.AccountLookupResult;
import com.banksphere.employee.dto.BeneficiaryLookupResult;
import com.banksphere.employee.dto.Customer360Response;
import com.banksphere.employee.dto.CustomerProfileLookupResult;
import com.banksphere.employee.dto.KycApplicationLookupResult;
import com.banksphere.employee.dto.TransactionLookupResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Customer360ServiceImplTest {

    @Mock
    private CustomerLookupClient customerLookupClient;
    @Mock
    private AccountOperationsClient accountOperationsClient;
    @Mock
    private TransactionLookupClient transactionLookupClient;
    @Mock
    private BeneficiaryLookupClient beneficiaryLookupClient;
    @Mock
    private KycLookupClient kycLookupClient;

    private Customer360ServiceImpl customer360Service;

    private UUID customerId;
    private static final String TOKEN = "test-bearer-token";
    private static final Set<String> ALL_PERMISSIONS =
            Set.of("CUSTOMER_VIEW", "ACCOUNT_VIEW", "TRANSACTION_VIEW", "KYC_VIEW");

    @BeforeEach
    void setUp() {
        customer360Service = new Customer360ServiceImpl(
                customerLookupClient, accountOperationsClient, transactionLookupClient, beneficiaryLookupClient, kycLookupClient);
        customerId = UUID.randomUUID();
    }

    private AccountLookupResult sampleAccount(UUID id) {
        return new AccountLookupResult(id, customerId, "123456789012", "BANK0001234", "SAVINGS",
                new BigDecimal("500.00"), "INR", "ACTIVE");
    }

    @Test
    void getCustomer360_populatesAllSections_whenCallerHoldsAllPermissions() {
        UUID accountId = UUID.randomUUID();
        when(customerLookupClient.lookupFullProfile(customerId, TOKEN))
                .thenReturn(new CustomerProfileLookupResult(customerId, "John", "Smith", "john@example.com", "+1-555", "ACTIVE", Instant.now()));
        when(accountOperationsClient.lookupByCustomerId(customerId, TOKEN)).thenReturn(List.of(sampleAccount(accountId)));
        when(transactionLookupClient.recentTransactionsForAccount(accountId, TOKEN)).thenReturn(List.of(
                new TransactionLookupResult(UUID.randomUUID(), "TXN-1", accountId, "DEPOSIT", new BigDecimal("100.00"),
                        "INR", "COMPLETED", "desc", Instant.now())));
        when(beneficiaryLookupClient.lookupByCustomerId(customerId, TOKEN)).thenReturn(List.of(
                new BeneficiaryLookupResult(UUID.randomUUID(), "Jane Doe", "999999999999", "BANK0009999", "Other Bank", "Jane", "ACTIVE")));
        when(kycLookupClient.lookupByCustomerId(customerId, TOKEN)).thenReturn(Optional.of(
                new KycApplicationLookupResult(UUID.randomUUID(), "SUBMITTED", Instant.now(), null, null, List.of(), List.of())));

        Customer360Response response = customer360Service.getCustomer360(customerId, ALL_PERMISSIONS, TOKEN);

        assertThat(response.customer().available()).isTrue();
        assertThat(response.customer().data().firstName()).isEqualTo("John");
        assertThat(response.accounts().available()).isTrue();
        assertThat(response.accounts().data()).hasSize(1);
        assertThat(response.transactions().available()).isTrue();
        assertThat(response.transactions().data()).hasSize(1);
        assertThat(response.beneficiaries().available()).isTrue();
        assertThat(response.beneficiaries().data()).hasSize(1);
        assertThat(response.kyc().available()).isTrue();
        assertThat(response.kyc().data().status()).isEqualTo("SUBMITTED");
        assertThat(response.unavailableCapabilities()).contains("LOANS", "CARDS", "FOREX", "SERVICE_REQUESTS");
    }

    @Test
    void getCustomer360_marksEverySectionUnavailable_whenCallerHoldsNoPermissions() {
        Customer360Response response = customer360Service.getCustomer360(customerId, Set.of(), TOKEN);

        assertThat(response.customer().available()).isFalse();
        assertThat(response.customer().data()).isNull();
        assertThat(response.accounts().available()).isFalse();
        assertThat(response.transactions().available()).isFalse();
        assertThat(response.beneficiaries().available()).isFalse();
        assertThat(response.kyc().available()).isFalse();

        verify(customerLookupClient, never()).lookupFullProfile(any(), any());
        verify(accountOperationsClient, never()).lookupByCustomerId(any(), any());
        verify(beneficiaryLookupClient, never()).lookupByCustomerId(any(), any());
        verify(kycLookupClient, never()).lookupByCustomerId(any(), any());
    }

    @Test
    void getCustomer360_marksKycSectionAvailableWithNullData_whenCustomerNeverStartedKyc() {
        when(kycLookupClient.lookupByCustomerId(customerId, TOKEN)).thenReturn(Optional.empty());

        Customer360Response response = customer360Service.getCustomer360(customerId, Set.of("KYC_VIEW"), TOKEN);

        assertThat(response.kyc().available()).isTrue();
        assertThat(response.kyc().data()).isNull();
    }

    @Test
    void getCustomer360_marksTransactionsUnavailable_whenCallerHasTransactionViewButNotAccountView() {
        Customer360Response response = customer360Service.getCustomer360(customerId, Set.of("TRANSACTION_VIEW"), TOKEN);

        assertThat(response.transactions().available()).isFalse();
        assertThat(response.transactions().unavailableReason()).contains("ACCOUNT_VIEW");
        verify(transactionLookupClient, never()).recentTransactionsForAccount(any(), any());
    }

    @Test
    void getCustomer360_doesNotFetchTransactions_whenCallerHasAccountViewButNotTransactionView() {
        UUID accountId = UUID.randomUUID();
        when(accountOperationsClient.lookupByCustomerId(customerId, TOKEN)).thenReturn(List.of(sampleAccount(accountId)));

        Customer360Response response = customer360Service.getCustomer360(customerId, Set.of("ACCOUNT_VIEW"), TOKEN);

        assertThat(response.accounts().available()).isTrue();
        assertThat(response.transactions().available()).isFalse();
        verify(transactionLookupClient, never()).recentTransactionsForAccount(any(), any());
    }
}
