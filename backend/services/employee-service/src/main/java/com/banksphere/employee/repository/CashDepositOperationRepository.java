package com.banksphere.employee.repository;

import com.banksphere.employee.entity.CashDepositOperation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashDepositOperationRepository extends JpaRepository<CashDepositOperation, UUID> {

    List<CashDepositOperation> findByBranchIdOrderByCreatedAtDesc(UUID branchId, Pageable pageable);

    boolean existsByOperationReference(String operationReference);
}
