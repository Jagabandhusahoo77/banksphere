package com.banksphere.employee.security;

import com.banksphere.employee.entity.Permission;
import com.banksphere.employee.entity.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.banksphere.employee.entity.Permission.ACCOUNT_VIEW;
import static com.banksphere.employee.entity.Permission.AUDIT_VIEW;
import static com.banksphere.employee.entity.Permission.CARD_APPROVE;
import static com.banksphere.employee.entity.Permission.CARD_REVIEW;
import static com.banksphere.employee.entity.Permission.CARD_VIEW;
import static com.banksphere.employee.entity.Permission.CASH_DEPOSIT;
import static com.banksphere.employee.entity.Permission.CASH_WITHDRAWAL;
import static com.banksphere.employee.entity.Permission.CUSTOMER_VIEW;
import static com.banksphere.employee.entity.Permission.EMPLOYEE_MANAGE;
import static com.banksphere.employee.entity.Permission.KYC_APPROVE;
import static com.banksphere.employee.entity.Permission.KYC_REJECT;
import static com.banksphere.employee.entity.Permission.KYC_REVIEW;
import static com.banksphere.employee.entity.Permission.KYC_VIEW;
import static com.banksphere.employee.entity.Permission.LOAN_APPROVE;
import static com.banksphere.employee.entity.Permission.LOAN_REVIEW;
import static com.banksphere.employee.entity.Permission.LOAN_VIEW;
import static com.banksphere.employee.entity.Permission.ROLE_MANAGE;
import static com.banksphere.employee.entity.Permission.SERVICE_REQUEST_PROCESS;
import static com.banksphere.employee.entity.Permission.SERVICE_REQUEST_VIEW;
import static com.banksphere.employee.entity.Permission.TRANSACTION_INVESTIGATE;
import static com.banksphere.employee.entity.Permission.TRANSACTION_VIEW;

/**
 * The authoritative role -> permission mapping, enforced server-side on
 * every request (never by hiding a UI menu item — see ADR-006, Decision 4).
 * This mapping is intentionally code-defined and fixed for this phase, not
 * stored in the database: {@link Role#ROLE_MANAGE} exists as a permission
 * name for a future admin capability to edit this mapping, but building
 * that persistence/admin-UI layer now would be scope beyond "employee
 * identity + RBAC foundation" — see ADR-006, Decision 6.
 *
 * <p>Design notes on the two roles not explicitly spelled out by the task
 * that authorized this table (documented in
 * docs/architecture/employee-platform.md):
 * <ul>
 *   <li>{@code BRANCH_MANAGER} gets broad VIEW/REVIEW oversight across every
 *       domain plus cash operations and audit visibility, but deliberately
 *       NOT the specialist {@code *_APPROVE} permissions (KYC/LOAN/CARD) —
 *       those stay with the trained specialist officer roles, so a branch
 *       manager can supervise without being able to single-handedly approve
 *       every category of decision.</li>
 *   <li>{@code ADMIN} gets employee/role administration and audit
 *       visibility plus minimal read-only customer/account context, but
 *       deliberately NONE of {@code CASH_DEPOSIT}/{@code CASH_WITHDRAWAL}/
 *       any {@code *_APPROVE} permission — per this phase's own explicit
 *       instruction not to give ADMIN unrestricted access to
 *       customer money-moving or approval operations.</li>
 * </ul>
 *
 * <p><b>Phase 9C additions</b> (see ADR-008): {@link Permission#KYC_REJECT}
 * was added alongside the pre-existing {@link Permission#KYC_APPROVE} — a
 * final REJECT decision on a whole KYC application is just as significant
 * an authority as a final APPROVE, so it's a separate, equally-guarded
 * permission rather than being folded into {@link Permission#KYC_REVIEW}
 * (which continues to gate in-progress review actions only — starting a
 * review, verifying/rejecting an individual document, requesting more
 * information — never the terminal application-level decision).
 * {@code KYC_OFFICER} was granted both {@code KYC_REJECT} and {@link
 * Permission#TRANSACTION_VIEW} — the latter because reviewing a customer's
 * transaction pattern is a realistic, legitimate part of KYC/AML risk
 * assessment (needed for the Customer 360 view's transactions section
 * during a review), not an unrelated grant. {@code BRANCH_MANAGER}
 * deliberately does NOT receive {@code KYC_REJECT}, for the identical
 * reason it never received {@code KYC_APPROVE}: final KYC decisions stay
 * with the trained specialist role. No other role's KYC-related access
 * changed — {@code TELLER}/{@code LOAN_OFFICER}/{@code CARD_OFFICER}/
 * {@code ADMIN} get no KYC permissions in this phase either, exactly as
 * before.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> MAPPING = new EnumMap<>(Role.class);

    static {
        MAPPING.put(Role.TELLER, EnumSet.of(
                CUSTOMER_VIEW, ACCOUNT_VIEW, TRANSACTION_VIEW,
                CASH_DEPOSIT, CASH_WITHDRAWAL));

        MAPPING.put(Role.KYC_OFFICER, EnumSet.of(
                CUSTOMER_VIEW, ACCOUNT_VIEW, TRANSACTION_VIEW,
                KYC_VIEW, KYC_REVIEW, KYC_APPROVE, KYC_REJECT));

        MAPPING.put(Role.LOAN_OFFICER, EnumSet.of(
                CUSTOMER_VIEW, ACCOUNT_VIEW,
                LOAN_VIEW, LOAN_REVIEW, LOAN_APPROVE));

        MAPPING.put(Role.CARD_OFFICER, EnumSet.of(
                CUSTOMER_VIEW, ACCOUNT_VIEW,
                CARD_VIEW, CARD_REVIEW, CARD_APPROVE));

        MAPPING.put(Role.OPERATIONS, EnumSet.of(
                CUSTOMER_VIEW, ACCOUNT_VIEW, TRANSACTION_VIEW, TRANSACTION_INVESTIGATE,
                SERVICE_REQUEST_VIEW, SERVICE_REQUEST_PROCESS));

        MAPPING.put(Role.BRANCH_MANAGER, EnumSet.of(
                CUSTOMER_VIEW, ACCOUNT_VIEW, TRANSACTION_VIEW,
                CASH_DEPOSIT, CASH_WITHDRAWAL,
                KYC_VIEW, KYC_REVIEW,
                LOAN_VIEW, LOAN_REVIEW,
                CARD_VIEW, CARD_REVIEW,
                SERVICE_REQUEST_VIEW, SERVICE_REQUEST_PROCESS,
                TRANSACTION_INVESTIGATE, AUDIT_VIEW));

        MAPPING.put(Role.ADMIN, EnumSet.of(
                EMPLOYEE_MANAGE, ROLE_MANAGE, AUDIT_VIEW,
                CUSTOMER_VIEW, ACCOUNT_VIEW));
    }

    private RolePermissions() {
    }

    /** The fixed permission set for a single role. */
    public static Set<Permission> permissionsFor(Role role) {
        return MAPPING.getOrDefault(role, Set.of());
    }

    /** The union of every permission granted by any of the given roles. */
    public static Set<Permission> permissionsFor(Set<Role> roles) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (Role role : roles) {
            permissions.addAll(permissionsFor(role));
        }
        return permissions;
    }
}
