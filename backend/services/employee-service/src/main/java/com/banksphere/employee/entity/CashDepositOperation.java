package com.banksphere.employee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A record of a cash-deposit operation THIS employee/service performed —
 * not a second copy of account-service's or transaction-service's data.
 * Every field here is either (a) an opaque id, or (b) an immutable fact
 * captured at the moment of the operation (an account number never
 * changes once assigned; who performed an operation and when never
 * changes after the fact) — never a mutable value like balance or
 * customer name that could drift out of sync with the owning service. See
 * ADR-007, Decision 9, for the full reasoning on why this table exists
 * and what it deliberately does not store.
 */
@Entity
@Table(name = "cash_deposit_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashDepositOperation {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /** This service's own operational reference — NEVER transaction-service's TXN-... reference. See ADR-007. */
    @Column(name = "operation_reference", nullable = false, unique = true, length = 20)
    private String operationReference;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "employee_number", nullable = false, length = 20)
    private String employeeNumber;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "branch_code", nullable = false, length = 20)
    private String branchCode;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** COMPLETED or FAILED — this row is written once, after the downstream call has already resolved either way; never updated afterward. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** The real transaction-service TXN-... reference, if ledger recording succeeded (best-effort — may be null even on a COMPLETED deposit). */
    @Column(name = "transaction_reference", length = 30)
    private String transactionReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
