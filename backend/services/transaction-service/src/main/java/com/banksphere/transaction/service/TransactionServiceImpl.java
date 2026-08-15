package com.banksphere.transaction.service;

import com.banksphere.transaction.dto.PageResponse;
import com.banksphere.transaction.dto.TransactionCreateRequest;
import com.banksphere.transaction.dto.TransactionResponse;
import com.banksphere.transaction.entity.Transaction;
import com.banksphere.transaction.entity.TransactionStatus;
import com.banksphere.transaction.exception.TransactionAccessDeniedException;
import com.banksphere.transaction.exception.TransactionNotFoundException;
import com.banksphere.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountOwnershipClient accountOwnershipClient;

    @Override
    @Transactional
    public TransactionResponse createTransaction(TransactionCreateRequest request) {
        Transaction transaction = Transaction.builder()
                .transactionReference(generateReference())
                .accountId(request.accountId())
                .transactionType(request.transactionType())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .status(TransactionStatus.COMPLETED)
                .build();

        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID id, String bearerToken) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        requireOwnership(transaction.getAccountId(), bearerToken);
        return TransactionResponse.from(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactionsByAccount(UUID accountId, Pageable pageable, String bearerToken) {
        requireOwnership(accountId, bearerToken);

        Page<TransactionResponse> page = transactionRepository.findByAccountId(accountId, pageable)
                .map(TransactionResponse::from);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactionsByAccountForEmployee(UUID accountId, Pageable pageable) {
        Page<TransactionResponse> page = transactionRepository.findByAccountId(accountId, pageable)
                .map(TransactionResponse::from);
        return PageResponse.from(page);
    }

    private void requireOwnership(UUID accountId, String bearerToken) {
        if (!accountOwnershipClient.isOwnedByCaller(accountId, bearerToken)) {
            throw new TransactionAccessDeniedException(accountId);
        }
    }

    private String generateReference() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}
