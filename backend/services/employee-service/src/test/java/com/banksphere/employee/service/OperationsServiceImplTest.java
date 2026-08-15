package com.banksphere.employee.service;

import com.banksphere.employee.dto.AccountLookupResult;
import com.banksphere.employee.dto.CashDepositHistoryEntry;
import com.banksphere.employee.dto.CashDepositRequest;
import com.banksphere.employee.dto.CashDepositResponse;
import com.banksphere.employee.dto.CustomerLookupResult;
import com.banksphere.employee.dto.CustomerSearchResponse;
import com.banksphere.employee.dto.EmployeeDepositResult;
import com.banksphere.employee.entity.Branch;
import com.banksphere.employee.entity.BranchStatus;
import com.banksphere.employee.entity.CashDepositOperation;
import com.banksphere.employee.entity.Employee;
import com.banksphere.employee.entity.EmployeeStatus;
import com.banksphere.employee.entity.Role;
import com.banksphere.employee.exception.DownstreamOperationException;
import com.banksphere.employee.repository.CashDepositOperationRepository;
import com.banksphere.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private CashDepositOperationRepository cashDepositOperationRepository;

    @Mock
    private AccountOperationsClient accountOperationsClient;

    @Mock
    private CustomerLookupClient customerLookupClient;

    @Mock
    private EmployeeAuditLog auditLog;

    private OperationsServiceImpl operationsService;
    private Employee teller;
    private Branch branch;

    @BeforeEach
    void setUp() {
        operationsService = new OperationsServiceImpl(
                employeeRepository, cashDepositOperationRepository, accountOperationsClient, customerLookupClient, auditLog);
        branch = Branch.builder().id(UUID.randomUUID()).branchCode("HQ001").branchName("Head Office")
                .ifsc("BANK0000001").status(BranchStatus.ACTIVE).build();
        teller = Employee.builder().id(UUID.randomUUID()).employeeNumber("EMP000010").username("jane.teller")
                .passwordHash("hash").firstName("Jane").lastName("Teller").email("jane.teller@banksphere.example")
                .branch(branch).status(EmployeeStatus.ACTIVE).roles(Set.of(Role.TELLER)).build();
    }

    private AccountLookupResult sampleAccount(UUID customerId) {
        return new AccountLookupResult(UUID.randomUUID(), customerId, "617242043877", "BANK0000001",
                "SAVINGS", new BigDecimal("20000.00"), "INR", "ACTIVE");
    }

    @Test
    void customerSearch_byAccountNumber_resolvesCustomerAndReturnsAllAccounts() {
        UUID customerId = UUID.randomUUID();
        AccountLookupResult matched = sampleAccount(customerId);
        when(accountOperationsClient.lookupByAccountNumber("617242043877", "token")).thenReturn(matched);
        when(accountOperationsClient.lookupByCustomerId(customerId, "token")).thenReturn(List.of(matched));
        when(customerLookupClient.lookup(customerId, "token"))
                .thenReturn(new CustomerLookupResult(customerId, "John", "Smith", "ACTIVE"));

        CustomerSearchResponse response = operationsService.customerSearch("617242043877", null, "token");

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.customerName()).isEqualTo("John Smith");
        assertThat(response.accounts()).hasSize(1);
        assertThat(response.accounts().get(0).accountNumber()).isEqualTo("617242043877");
    }

    @Test
    void customerSearch_byCustomerId_doesNotCallAccountNumberLookup() {
        UUID customerId = UUID.randomUUID();
        when(accountOperationsClient.lookupByCustomerId(customerId, "token")).thenReturn(List.of(sampleAccount(customerId)));
        when(customerLookupClient.lookup(customerId, "token"))
                .thenReturn(new CustomerLookupResult(customerId, "John", "Smith", "ACTIVE"));

        CustomerSearchResponse response = operationsService.customerSearch(null, customerId, "token");

        assertThat(response.customerId()).isEqualTo(customerId);
        verify(accountOperationsClient, never()).lookupByAccountNumber(anyString(), anyString());
    }

    @Test
    void customerSearch_throwsIllegalArgumentException_whenBothIdentifiersProvided() {
        assertThatThrownBy(() -> operationsService.customerSearch("617242043877", UUID.randomUUID(), "token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customerSearch_throwsIllegalArgumentException_whenNeitherIdentifierProvided() {
        assertThatThrownBy(() -> operationsService.customerSearch(null, null, "token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cashDeposit_succeeds_persistsOperationAndReturnsRealTransactionReference() {
        UUID accountId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(employeeRepository.findById(teller.getId())).thenReturn(Optional.of(teller));
        AccountLookupResult updatedAccount = new AccountLookupResult(accountId, customerId, "617242043877",
                "BANK0000001", "SAVINGS", new BigDecimal("30000.00"), "INR", "ACTIVE");
        when(accountOperationsClient.deposit(eq(accountId), eq(new BigDecimal("10000.00")), anyString(), eq("token")))
                .thenReturn(new EmployeeDepositResult(updatedAccount, "TXN-ABC123"));
        when(cashDepositOperationRepository.existsByOperationReference(anyString())).thenReturn(false);

        CashDepositRequest request = new CashDepositRequest(accountId, new BigDecimal("10000.00"), null);
        CashDepositResponse response = operationsService.cashDeposit(request, teller.getId(), "token");

        assertThat(response.transactionReference()).isEqualTo("TXN-ABC123");
        assertThat(response.newBalance()).isEqualByComparingTo("30000.00");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.performedBy()).isEqualTo("EMP000010");
        assertThat(response.branchCode()).isEqualTo("HQ001");
        assertThat(response.operationReference()).startsWith("CD-");

        ArgumentCaptor<CashDepositOperation> captor = ArgumentCaptor.forClass(CashDepositOperation.class);
        verify(cashDepositOperationRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo(customerId);
        assertThat(captor.getValue().getAccountNumber()).isEqualTo("617242043877");
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");

        verify(auditLog).cashDepositStarted(eq(teller.getId().toString()), eq("EMP000010"), anyString(), eq(accountId.toString()), eq(new BigDecimal("10000.00")));
        verify(auditLog).cashDepositSucceeded(eq(teller.getId().toString()), eq("EMP000010"), anyString(), eq(accountId.toString()), eq(new BigDecimal("10000.00")), anyString());
    }

    @Test
    void cashDeposit_buildsBranchDescription_includingOptionalEmployeeNote() {
        UUID accountId = UUID.randomUUID();
        when(employeeRepository.findById(teller.getId())).thenReturn(Optional.of(teller));
        when(accountOperationsClient.deposit(any(), any(), anyString(), anyString()))
                .thenReturn(new EmployeeDepositResult(sampleAccount(UUID.randomUUID()), "TXN-ABC123"));
        when(cashDepositOperationRepository.existsByOperationReference(anyString())).thenReturn(false);

        operationsService.cashDeposit(new CashDepositRequest(accountId, BigDecimal.TEN, "customer requested"), teller.getId(), "token");

        verify(accountOperationsClient).deposit(eq(accountId), eq(BigDecimal.TEN),
                eq("CASH DEPOSIT - Branch HQ001 (customer requested)"), eq("token"));
    }

    @Test
    void cashDeposit_whenDownstreamRejects_doesNotPersistOperation_andAuditsFailure() {
        UUID accountId = UUID.randomUUID();
        when(employeeRepository.findById(teller.getId())).thenReturn(Optional.of(teller));
        when(accountOperationsClient.deposit(any(), any(), anyString(), anyString()))
                .thenThrow(new DownstreamOperationException(403, "Outside the caller's own branch"));

        CashDepositRequest request = new CashDepositRequest(accountId, BigDecimal.TEN, null);

        assertThatThrownBy(() -> operationsService.cashDeposit(request, teller.getId(), "token"))
                .isInstanceOf(DownstreamOperationException.class);

        verify(cashDepositOperationRepository, never()).save(any());
        verify(auditLog).cashDepositFailed(eq(teller.getId().toString()), eq("EMP000010"), anyString(),
                eq(accountId.toString()), eq(BigDecimal.TEN), eq("Outside the caller's own branch"));
    }

    @Test
    void cashDepositHistory_resolvesCustomerNamesLiveAndNeverStoresThem() {
        UUID customerId = UUID.randomUUID();
        when(employeeRepository.findById(teller.getId())).thenReturn(Optional.of(teller));
        CashDepositOperation operation = CashDepositOperation.builder()
                .id(UUID.randomUUID()).operationReference("CD-0000000001").employeeId(teller.getId())
                .employeeNumber("EMP000010").branchId(branch.getId()).branchCode("HQ001")
                .customerId(customerId).accountId(UUID.randomUUID()).accountNumber("617242043877")
                .amount(BigDecimal.TEN).currency("INR").status("COMPLETED").transactionReference("TXN-ABC123")
                .build();
        when(cashDepositOperationRepository.findByBranchIdOrderByCreatedAtDesc(eq(branch.getId()), any()))
                .thenReturn(List.of(operation));
        when(customerLookupClient.lookup(customerId, "token"))
                .thenReturn(new CustomerLookupResult(customerId, "John", "Smith", "ACTIVE"));

        List<CashDepositHistoryEntry> history = operationsService.cashDepositHistory(teller.getId(), "token");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).customerName()).isEqualTo("John Smith");
        assertThat(history.get(0).operationReference()).isEqualTo("CD-0000000001");
    }

    @Test
    void cashDepositHistory_toleratesAFailedCustomerLookup_ratherThanFailingTheWholeList() {
        UUID customerId = UUID.randomUUID();
        when(employeeRepository.findById(teller.getId())).thenReturn(Optional.of(teller));
        CashDepositOperation operation = CashDepositOperation.builder()
                .id(UUID.randomUUID()).operationReference("CD-0000000002").employeeId(teller.getId())
                .employeeNumber("EMP000010").branchId(branch.getId()).branchCode("HQ001")
                .customerId(customerId).accountId(UUID.randomUUID()).accountNumber("617242043877")
                .amount(BigDecimal.TEN).currency("INR").status("COMPLETED").build();
        when(cashDepositOperationRepository.findByBranchIdOrderByCreatedAtDesc(eq(branch.getId()), any()))
                .thenReturn(List.of(operation));
        when(customerLookupClient.lookup(customerId, "token")).thenThrow(new DownstreamOperationException(404, "not found"));

        List<CashDepositHistoryEntry> history = operationsService.cashDepositHistory(teller.getId(), "token");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).customerName()).isNull();
    }
}
