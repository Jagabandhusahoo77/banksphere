package com.banksphere.account.service;

import com.banksphere.account.entity.TransferIdempotencyRecord;
import com.banksphere.account.entity.TransferIdempotencyStatus;
import com.banksphere.account.exception.IdempotencyConflictException;
import com.banksphere.account.repository.TransferIdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scenarios 27, 29-31 ("transfer executes exactly once," "failed/expired
 * OTP doesn't mutate balance twice") at the idempotency-bookkeeping
 * level. The real exactly-once enforcement is the database's own unique
 * index (see the V3 migration) — this class's own javadoc explains why —
 * so this test focuses on the decision logic {@code checkOrCreate} makes
 * for each of a record's possible prior states, not on proving the DB
 * constraint itself (that needs a real Postgres, see the *IT tests).
 */
@ExtendWith(MockitoExtension.class)
class TransferIdempotencyServiceTest {

    @Mock
    private TransferIdempotencyRecordRepository repository;

    private TransferIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new TransferIdempotencyService(repository);
    }

    @Test
    void checkOrCreate_returnsCachedSnapshot_whenARecordAlreadyCompleted() {
        UUID customerId = UUID.randomUUID();
        TransferIdempotencyRecord completed = TransferIdempotencyRecord.builder()
                .customerId(customerId).idempotencyKey("key-1")
                .status(TransferIdempotencyStatus.COMPLETED).responseSnapshot("{\"transferId\":\"abc\"}").build();
        when(repository.findByCustomerIdAndIdempotencyKey(customerId, "key-1")).thenReturn(Optional.of(completed));

        TransferIdempotencyResult result = idempotencyService.checkOrCreate(customerId, "key-1");

        assertThat(result.alreadyCompleted()).isTrue();
        assertThat(result.responseSnapshot()).isEqualTo("{\"transferId\":\"abc\"}");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void checkOrCreate_rejectsAConcurrentInProgressRequestWithTheSameKey() {
        UUID customerId = UUID.randomUUID();
        TransferIdempotencyRecord inProgress = TransferIdempotencyRecord.builder()
                .customerId(customerId).idempotencyKey("key-2").status(TransferIdempotencyStatus.IN_PROGRESS).build();
        when(repository.findByCustomerIdAndIdempotencyKey(customerId, "key-2")).thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> idempotencyService.checkOrCreate(customerId, "key-2"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void checkOrCreate_rejectsARetryOfAPreviouslyFailedKey_deliberateSimplification() {
        UUID customerId = UUID.randomUUID();
        TransferIdempotencyRecord failed = TransferIdempotencyRecord.builder()
                .customerId(customerId).idempotencyKey("key-3").status(TransferIdempotencyStatus.FAILED).build();
        when(repository.findByCustomerIdAndIdempotencyKey(customerId, "key-3")).thenReturn(Optional.of(failed));

        // A client that wants to retry after a genuine failure should
        // generate a fresh idempotency key — see this class's own javadoc.
        assertThatThrownBy(() -> idempotencyService.checkOrCreate(customerId, "key-3"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void checkOrCreate_createsANewInProgressRecord_forAFreshKey() {
        UUID customerId = UUID.randomUUID();
        when(repository.findByCustomerIdAndIdempotencyKey(customerId, "key-4")).thenReturn(Optional.empty());
        UUID recordId = UUID.randomUUID();
        when(repository.saveAndFlush(any(TransferIdempotencyRecord.class)))
                .thenAnswer(inv -> {
                    TransferIdempotencyRecord record = inv.getArgument(0);
                    record.setId(recordId);
                    return record;
                });

        TransferIdempotencyResult result = idempotencyService.checkOrCreate(customerId, "key-4");

        assertThat(result.alreadyCompleted()).isFalse();
        assertThat(result.recordId()).isEqualTo(recordId);
    }

    @Test
    void checkOrCreate_treatsAUniqueConstraintRace_asAConflict() {
        // The findBy... check above can be raced by a second concurrent
        // request with the identical (customerId, idempotencyKey) pair —
        // the real backstop is the DB's own unique index (V3 migration),
        // surfaced here as a DataIntegrityViolationException from saveAndFlush.
        UUID customerId = UUID.randomUUID();
        when(repository.findByCustomerIdAndIdempotencyKey(customerId, "key-5")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(TransferIdempotencyRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> idempotencyService.checkOrCreate(customerId, "key-5"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void markCompleted_setsStatusTransferIdAndSnapshot() {
        UUID recordId = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        TransferIdempotencyRecord record = TransferIdempotencyRecord.builder()
                .id(recordId).status(TransferIdempotencyStatus.IN_PROGRESS).build();
        when(repository.findById(recordId)).thenReturn(Optional.of(record));

        idempotencyService.markCompleted(recordId, transferId, "{\"transferId\":\"" + transferId + "\"}");

        assertThat(record.getStatus()).isEqualTo(TransferIdempotencyStatus.COMPLETED);
        assertThat(record.getTransferId()).isEqualTo(transferId);
        assertThat(record.getResponseSnapshot()).contains(transferId.toString());
        assertThat(record.getCompletedAt()).isNotNull();
    }

    @Test
    void markFailed_setsStatusFailed() {
        UUID recordId = UUID.randomUUID();
        TransferIdempotencyRecord record = TransferIdempotencyRecord.builder()
                .id(recordId).status(TransferIdempotencyStatus.IN_PROGRESS).build();
        when(repository.findById(recordId)).thenReturn(Optional.of(record));

        idempotencyService.markFailed(recordId);

        assertThat(record.getStatus()).isEqualTo(TransferIdempotencyStatus.FAILED);
        assertThat(record.getCompletedAt()).isNotNull();
    }

    @Test
    void markCompleted_isANoOp_ifTheRecordSomehowDoesNotExist() {
        UUID recordId = UUID.randomUUID();
        when(repository.findById(recordId)).thenReturn(Optional.empty());

        idempotencyService.markCompleted(recordId, UUID.randomUUID(), "{}");

        verify(repository, never()).save(any());
    }
}
