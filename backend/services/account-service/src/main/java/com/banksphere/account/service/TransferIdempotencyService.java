package com.banksphere.account.service;

import com.banksphere.account.entity.TransferIdempotencyRecord;
import com.banksphere.account.entity.TransferIdempotencyStatus;
import com.banksphere.account.exception.IdempotencyConflictException;
import com.banksphere.account.repository.TransferIdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Every method here runs in its OWN transaction ({@code REQUIRES_NEW}),
 * deliberately independent of {@code AccountServiceImpl.transfer}'s own
 * {@code @Transactional} boundary — see ADR-009's idempotency section.
 * This is what makes the mechanism actually work: if the transfer itself
 * fails and its transaction rolls back, the {@code IN_PROGRESS} →
 * {@code FAILED} bookkeeping this class already committed must survive
 * that rollback, not be undone by it. Must be called through the Spring
 * proxy (i.e. as a separate injected bean, never a same-class private
 * method) for {@code REQUIRES_NEW} to actually take effect.
 */
@Service
@RequiredArgsConstructor
public class TransferIdempotencyService {

    private final TransferIdempotencyRecordRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferIdempotencyResult checkOrCreate(UUID customerId, String idempotencyKey) {
        Optional<TransferIdempotencyRecord> existing = repository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            TransferIdempotencyRecord record = existing.get();
            if (record.getStatus() == TransferIdempotencyStatus.COMPLETED) {
                return TransferIdempotencyResult.completed(record.getResponseSnapshot());
            }
            // IN_PROGRESS (a genuine concurrent race) or FAILED (the
            // previous attempt with this exact key didn't succeed) are
            // both treated as a conflict this phase — a client that wants
            // to retry after a genuine failure should generate a fresh
            // idempotency key, a deliberate simplification documented in
            // ADR-009 rather than silently allowing same-key retry-after-
            // failure without a test proving it's safe.
            throw new IdempotencyConflictException();
        }

        try {
            TransferIdempotencyRecord record = TransferIdempotencyRecord.builder()
                    .customerId(customerId)
                    .idempotencyKey(idempotencyKey)
                    .build();
            record = repository.saveAndFlush(record);
            return TransferIdempotencyResult.proceed(record.getId());
        } catch (DataIntegrityViolationException ex) {
            // Lost the race to a concurrent request with the same
            // (customerId, idempotencyKey) — the UNIQUE index (see the
            // V3 migration) is what actually enforces exactly-once, not
            // the findBy... check above, which a concurrent second
            // request can still race past before either commits.
            throw new IdempotencyConflictException();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID recordId, UUID transferId, String responseSnapshotJson) {
        repository.findById(recordId).ifPresent(record -> {
            record.setStatus(TransferIdempotencyStatus.COMPLETED);
            record.setTransferId(transferId);
            record.setResponseSnapshot(responseSnapshotJson);
            record.setCompletedAt(Instant.now());
            repository.save(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID recordId) {
        repository.findById(recordId).ifPresent(record -> {
            record.setStatus(TransferIdempotencyStatus.FAILED);
            record.setCompletedAt(Instant.now());
            repository.save(record);
        });
    }
}
