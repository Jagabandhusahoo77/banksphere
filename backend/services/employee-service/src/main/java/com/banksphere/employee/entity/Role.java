package com.banksphere.employee.entity;

/**
 * The fixed set of employee roles for this phase. Roles are code-defined,
 * not administrable via the database yet — see
 * {@link com.banksphere.employee.security.RolePermissions} for the
 * role-to-permission mapping and ADR-006 for why a dynamic, DB-backed
 * role/permission system is deferred rather than built now.
 */
public enum Role {
    TELLER,
    KYC_OFFICER,
    LOAN_OFFICER,
    CARD_OFFICER,
    OPERATIONS,
    BRANCH_MANAGER,
    ADMIN
}
