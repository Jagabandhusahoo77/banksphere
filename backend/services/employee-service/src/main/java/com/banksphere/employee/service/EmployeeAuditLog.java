package com.banksphere.employee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Structured, single-line audit events for every employee authentication
 * and employee-management action — NOT the full Audit Service (that's
 * future work, see ADR-006, Decision 7). This is deliberately just
 * structured logging today: a dedicated logger, one line per event, fixed
 * key=value fields, so a future log-shipper (Fluent Bit / OpenSearch, per
 * the project roadmap) or a real Audit Service consuming these lines needs
 * no reparsing of free-text messages.
 *
 * <p>Every line carries at minimum: {@code employeeId}, {@code
 * employeeNumber}, {@code action}, {@code timestamp}, {@code
 * correlationId} (from {@link com.banksphere.employee.security.CorrelationIdFilter}'s
 * MDC entry — already present on the log line via the logging pattern, but
 * also included explicitly here so the audit line is self-contained even
 * if it's later shipped somewhere that strips MDC context), and {@code
 * result}.
 */
@Component
public class EmployeeAuditLog {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.banksphere.employee.AUDIT");

    public void loginSucceeded(String employeeId, String employeeNumber) {
        log("LOGIN", employeeId, employeeNumber, "SUCCESS");
    }

    public void loginFailed(String usernameAttempted) {
        // No employeeId/employeeNumber captured on failure — deliberately:
        // logging them for an unknown/wrong-password attempt would let the
        // audit log itself become an account-number-enumeration oracle. The
        // attempted username is retained since it's already caller-supplied
        // input, not derived secret state.
        log("LOGIN", null, null, "FAILURE(username=" + usernameAttempted + ")");
    }

    public void employeeCreated(String actingEmployeeId, String createdEmployeeId, String createdEmployeeNumber) {
        log("EMPLOYEE_CREATE", actingEmployeeId, null, "SUCCESS(createdEmployeeId=" + createdEmployeeId
                + ",createdEmployeeNumber=" + createdEmployeeNumber + ")");
    }

    public void employeeStatusChanged(String actingEmployeeId, String targetEmployeeId, String newStatus) {
        log("EMPLOYEE_STATUS_CHANGE", actingEmployeeId, null, "SUCCESS(targetEmployeeId=" + targetEmployeeId
                + ",newStatus=" + newStatus + ")");
    }

    /**
     * Phase 9B — the three-event shape (STARTED/SUCCEEDED/FAILED) this
     * phase's own instructions asked for, so a cash deposit that fails
     * partway through (e.g. account-service rejects it) is still fully
     * traceable — a STARTED line with no matching SUCCEEDED line is
     * itself a meaningful signal, not a silent gap. Carries the account
     * reference (accountId — not the raw account number, keeping this
     * log line consistent with the "opaque ids, not customer data" shape
     * the rest of this class already uses) and the amount, in addition to
     * the standard employeeId/employeeNumber/branchId/timestamp/
     * correlationId/result fields.
     */
    public void cashDepositStarted(String employeeId, String employeeNumber, String branchId, String accountId, BigDecimal amount) {
        logDetailed("CASH_DEPOSIT_STARTED", employeeId, employeeNumber, branchId, accountId, amount, "IN_PROGRESS");
    }

    public void cashDepositSucceeded(String employeeId, String employeeNumber, String branchId, String accountId,
                                      BigDecimal amount, String operationReference) {
        logDetailed("CASH_DEPOSIT_SUCCEEDED", employeeId, employeeNumber, branchId, accountId, amount,
                "SUCCESS(operationReference=" + operationReference + ")");
    }

    public void cashDepositFailed(String employeeId, String employeeNumber, String branchId, String accountId,
                                   BigDecimal amount, String reason) {
        logDetailed("CASH_DEPOSIT_FAILED", employeeId, employeeNumber, branchId, accountId, amount,
                "FAILURE(reason=" + reason + ")");
    }

    private void log(String action, String employeeId, String employeeNumber, String result) {
        String correlationId = MDC.get("correlationId");
        AUDIT.info("action={} employeeId={} employeeNumber={} result={} timestamp={} correlationId={}",
                action, employeeId, employeeNumber, result, Instant.now(), correlationId);
    }

    private void logDetailed(String action, String employeeId, String employeeNumber, String branchId,
                              String accountId, BigDecimal amount, String result) {
        String correlationId = MDC.get("correlationId");
        AUDIT.info("action={} employeeId={} employeeNumber={} branchId={} accountId={} amount={} result={} timestamp={} correlationId={}",
                action, employeeId, employeeNumber, branchId, accountId, amount, result, Instant.now(), correlationId);
    }
}
