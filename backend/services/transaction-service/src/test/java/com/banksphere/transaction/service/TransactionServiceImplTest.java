package com.banksphere.transaction.service;

import com.banksphere.transaction.dto.PageResponse;
import com.banksphere.transaction.dto.TransactionCreateRequest;
import com.banksphere.transaction.dto.TransactionResponse;
import com.banksphere.transaction.entity.Transaction;
import com.banksphere.transaction.entity.TransactionStatus;
import com.banksphere.transaction.entity.TransactionType;
import com.banksphere.transaction.exception.TransactionAccessDeniedException;
import com.banksphere.transaction.exception.TransactionNotFoundException;
import com.banksphere.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    private static final String TOKEN = "test-bearer-token";

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountOwnershipClient accountOwnershipClient;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UUID accountId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
    }

    @Test
    void createTransaction_persistsTransactionWithGeneratedReference() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionCreateRequest request = new TransactionCreateRequest(
                accountId, TransactionType.DEPOSIT, new BigDecimal("100.00"), "USD", "top-up");

        TransactionResponse response = transactionService.createTransaction(request);

        assertThat(response.transactionReference()).startsWith("TXN-");
        assertThat(response.status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(response.amount()).isEqualByComparingTo("100.00");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(accountId);

        // createTransaction never calls the ownership client — see
        // TransactionService's javadoc for why this path is intentionally
        // different from the two read paths below.
        verify(accountOwnershipClient, never()).isOwnedByCaller(any(), any());
    }

    @Test
    void getTransaction_returnsTransaction_whenFoundAndAccountOwnedByCaller() {
        UUID id = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(id)
                .transactionReference("TXN-ABC")
                .accountId(accountId)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.findById(id)).thenReturn(Optional.of(transaction));
        when(accountOwnershipClient.isOwnedByCaller(accountId, TOKEN)).thenReturn(true);

        TransactionResponse response = transactionService.getTransaction(id, TOKEN);

        assertThat(response.id()).isEqualTo(id);
    }

    @Test
    void getTransaction_throwsNotFoundException_whenMissing() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(id, TOKEN))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void getTransaction_throwsAccessDeniedException_whenAccountNotOwnedByCaller() {
        UUID id = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(id)
                .transactionReference("TXN-ABC")
                .accountId(accountId)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(TransactionStatus.COMPLETED)
                .build();
        when(transactionRepository.findById(id)).thenReturn(Optional.of(transaction));
        when(accountOwnershipClient.isOwnedByCaller(accountId, TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getTransaction(id, TOKEN))
                .isInstanceOf(TransactionAccessDeniedException.class);
    }

    @Test
    void getTransactionsByAccount_returnsPagedResults_whenAccountOwnedByCaller() {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionReference("TXN-XYZ")
                .accountId(accountId)
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(TransactionStatus.COMPLETED)
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(accountOwnershipClient.isOwnedByCaller(accountId, TOKEN)).thenReturn(true);
        when(transactionRepository.findByAccountId(accountId, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));

        PageResponse<TransactionResponse> response = transactionService.getTransactionsByAccount(accountId, pageable, TOKEN);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.page()).isEqualTo(0);
    }

    @Test
    void getTransactionsByAccount_throwsAccessDeniedException_whenAccountNotOwnedByCaller() {
        Pageable pageable = PageRequest.of(0, 20);
        when(accountOwnershipClient.isOwnedByCaller(accountId, TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getTransactionsByAccount(accountId, pageable, TOKEN))
                .isInstanceOf(TransactionAccessDeniedException.class);

        // Ownership is checked before the repository is ever queried.
        verify(transactionRepository, never()).findByAccountId(any(), any());
    }

    // ---- Phase 9C: getTransactionsByAccountForEmployee (Customer 360) --

    @Test
    void getTransactionsByAccountForEmployee_returnsPagedResults_withNoOwnershipCheck() {
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionReference("TXN-XYZ")
                .accountId(accountId)
                .transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(TransactionStatus.COMPLETED)
                .build();
        Pageable pageable = PageRequest.of(0, 20);
        when(transactionRepository.findByAccountId(accountId, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction), pageable, 1));

        PageResponse<TransactionResponse> response = transactionService.getTransactionsByAccountForEmployee(accountId, pageable);

        assertThat(response.content()).hasSize(1);
        verify(accountOwnershipClient, never()).isOwnedByCaller(any(), any());
    }
}
