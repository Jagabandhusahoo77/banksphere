package com.banksphere.kyc.service;

import com.banksphere.kyc.security.EmployeePrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Structured, single-line audit events for the KYC domain — NOT the full
 * Audit Service (future work, see ADR-006 Decision 7 and ADR-008). A
 * dedicated logger, one line per event, fixed key=value fields, so a
 * future log shipper or a real Audit Service consuming these lines needs
 * no reparsing of free-text messages; structured today so it can later be
 * published through Outbox → Kafka → Audit Service without changing the
 * event shape. Mirrors employee-service's {@code EmployeeAuditLog}
 * exactly, extended with {@code customerId}/{@code applicationId}/
 * {@code documentId} fields this domain needs.
 *
 * <p>Every line carries {@code employeeId}/{@code employeeNumber}/
 * {@code branchId} filled ONLY for an employee-initiated event
 * (review/verify/reject/request-information/approve/reject) — a
 * customer-initiated event (submit, document upload) leaves them
 * {@code null}, never fabricated. See {@code KycStatusHistory}'s same
 * convention for {@code changedByEmployeeId}.
 */
@Component
public class KycAuditLog {

    private static final Logger AUDIT = LoggerFactory.getLogger("com.banksphere.kyc.AUDIT");

    public void applicationSubmitted(UUID customerId, UUID applicationId) {
        logCustomerEvent("KYC_APPLICATION_SUBMITTED", customerId, applicationId, null, "SUCCESS");
    }

    public void documentUploaded(UUID customerId, UUID applicationId, UUID documentId, String documentType) {
        logCustomerEvent("KYC_DOCUMENT_UPLOADED", customerId, applicationId, documentId,
                "SUCCESS(documentType=" + documentType + ")");
    }

    public void reviewStarted(EmployeePrincipal employee, UUID customerId, UUID applicationId) {
        logEmployeeEvent("KYC_REVIEW_STARTED", employee, customerId, applicationId, null, "SUCCESS");
    }

    public void documentVerified(EmployeePrincipal employee, UUID customerId, UUID applicationId, UUID documentId) {
        logEmployeeEvent("KYC_DOCUMENT_VERIFIED", employee, customerId, applicationId, documentId, "SUCCESS");
    }

    public void documentRejected(EmployeePrincipal employee, UUID customerId, UUID applicationId, UUID documentId, String reason) {
        logEmployeeEvent("KYC_DOCUMENT_REJECTED", employee, customerId, applicationId, documentId,
                "SUCCESS(reason=" + reason + ")");
    }

    public void additionalInformationRequested(EmployeePrincipal employee, UUID customerId, UUID applicationId, String reason) {
        logEmployeeEvent("KYC_ADDITIONAL_INFORMATION_REQUESTED", employee, customerId, applicationId, null,
                "SUCCESS(reason=" + reason + ")");
    }

    public void applicationApproved(EmployeePrincipal employee, UUID customerId, UUID applicationId) {
        logEmployeeEvent("KYC_APPROVED", employee, customerId, applicationId, null, "SUCCESS");
    }

    public void applicationRejected(EmployeePrincipal employee, UUID customerId, UUID applicationId, String reason) {
        logEmployeeEvent("KYC_REJECTED", employee, customerId, applicationId, null,
                "SUCCESS(reason=" + reason + ")");
    }

    public void reviewConflict(EmployeePrincipal employee, UUID customerId, UUID applicationId, String action) {
        logEmployeeEvent(action, employee, customerId, applicationId, null,
                "FAILURE(reason=concurrent modification)");
    }

    private void logCustomerEvent(String action, UUID customerId, UUID applicationId, UUID documentId, String result) {
        String correlationId = MDC.get("correlationId");
        AUDIT.info("action={} employeeId={} employeeNumber={} branchId={} customerId={} applicationId={} documentId={} "
                        + "result={} timestamp={} correlationId={}",
                action, null, null, null, customerId, applicationId, documentId, result, Instant.now(), correlationId);
    }

    private void logEmployeeEvent(String action, EmployeePrincipal employee, UUID customerId, UUID applicationId,
                                   UUID documentId, String result) {
        String correlationId = MDC.get("correlationId");
        AUDIT.info("action={} employeeId={} employeeNumber={} branchId={} customerId={} applicationId={} documentId={} "
                        + "result={} timestamp={} correlationId={}",
                action, employee.employeeId(), employee.employeeNumber(), employee.branchId(), customerId,
                applicationId, documentId, result, Instant.now(), correlationId);
    }
}
