package com.banksphere.account.repository;

import com.banksphere.account.entity.TransferIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferIdempotencyRecordRepository extends JpaRepository<TransferIdempotencyRecord, UUID> {

    Optional<TransferIdempotencyRecord> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
}
