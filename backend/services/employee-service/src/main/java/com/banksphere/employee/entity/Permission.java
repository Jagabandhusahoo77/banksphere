package com.banksphere.employee.entity;

/**
 * Fine-grained permissions, defined separately from {@link Role} on purpose
 * (CLAUDE.md / this phase's own instruction: "Define permissions separately
 * from roles"). Every server-side authorization check in this codebase
 * should check for a permission, never a role name directly — a role is
 * just a named bundle of permissions (see
 * {@link com.banksphere.employee.security.RolePermissions}), and an
 * employee may hold more than one role.
 */
public enum Permission {
    CUSTOMER_VIEW,
    ACCOUNT_VIEW,
    TRANSACTION_VIEW,

    CASH_DEPOSIT,
    CASH_WITHDRAWAL,

    KYC_VIEW,
    KYC_REVIEW,
    KYC_APPROVE,
    /** Phase 9C — added alongside the pre-existing KYC_APPROVE: a final REJECT decision on a whole application is just as significant an authority as a final APPROVE, so it gets its own permission rather than being folded into KYC_REVIEW (which gates in-progress review actions — document verify/reject, request-information — not the terminal decision). See ADR-008. */
    KYC_REJECT,

    LOAN_VIEW,
    LOAN_REVIEW,
    LOAN_APPROVE,

    CARD_VIEW,
    CARD_REVIEW,
    CARD_APPROVE,

    SERVICE_REQUEST_VIEW,
    SERVICE_REQUEST_PROCESS,

    TRANSACTION_INVESTIGATE,

    AUDIT_VIEW,

    EMPLOYEE_MANAGE,
    ROLE_MANAGE
}
