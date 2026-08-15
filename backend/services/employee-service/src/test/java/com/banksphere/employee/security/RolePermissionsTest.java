package com.banksphere.employee.security;

import com.banksphere.employee.entity.Permission;
import com.banksphere.employee.entity.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the exact role -> permission table documented in docs/architecture/employee-platform.md and ADR-006. */
class RolePermissionsTest {

    @Test
    void teller_getsExactlyCashAndViewPermissions() {
        assertThat(RolePermissions.permissionsFor(Role.TELLER)).containsExactlyInAnyOrder(
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW, Permission.TRANSACTION_VIEW,
                Permission.CASH_DEPOSIT, Permission.CASH_WITHDRAWAL);
    }

    @Test
    void kycOfficer_getsExactlyKycAndViewPermissions() {
        // Phase 9C: TRANSACTION_VIEW and KYC_REJECT were added — see ADR-008.
        assertThat(RolePermissions.permissionsFor(Role.KYC_OFFICER)).containsExactlyInAnyOrder(
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW, Permission.TRANSACTION_VIEW,
                Permission.KYC_VIEW, Permission.KYC_REVIEW, Permission.KYC_APPROVE, Permission.KYC_REJECT);
    }

    @Test
    void loanOfficer_getsExactlyLoanAndViewPermissions() {
        assertThat(RolePermissions.permissionsFor(Role.LOAN_OFFICER)).containsExactlyInAnyOrder(
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW,
                Permission.LOAN_VIEW, Permission.LOAN_REVIEW, Permission.LOAN_APPROVE);
    }

    @Test
    void cardOfficer_getsExactlyCardAndViewPermissions() {
        assertThat(RolePermissions.permissionsFor(Role.CARD_OFFICER)).containsExactlyInAnyOrder(
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW,
                Permission.CARD_VIEW, Permission.CARD_REVIEW, Permission.CARD_APPROVE);
    }

    @Test
    void operations_getsExactlyInvestigateAndServiceRequestPermissions() {
        assertThat(RolePermissions.permissionsFor(Role.OPERATIONS)).containsExactlyInAnyOrder(
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW, Permission.TRANSACTION_VIEW,
                Permission.TRANSACTION_INVESTIGATE, Permission.SERVICE_REQUEST_VIEW, Permission.SERVICE_REQUEST_PROCESS);
    }

    @Test
    void branchManager_getsBroadOversightButNoSpecialistApprovePermissions() {
        Set<Permission> permissions = RolePermissions.permissionsFor(Role.BRANCH_MANAGER);
        assertThat(permissions).contains(
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW, Permission.TRANSACTION_VIEW,
                Permission.CASH_DEPOSIT, Permission.CASH_WITHDRAWAL,
                Permission.KYC_VIEW, Permission.KYC_REVIEW,
                Permission.LOAN_VIEW, Permission.LOAN_REVIEW,
                Permission.CARD_VIEW, Permission.CARD_REVIEW,
                Permission.SERVICE_REQUEST_VIEW, Permission.SERVICE_REQUEST_PROCESS,
                Permission.TRANSACTION_INVESTIGATE, Permission.AUDIT_VIEW);
        // Deliberately NOT delegated to a branch manager — approval authority
        // stays with the trained specialist officer roles (see ADR-006, and
        // ADR-008 for why KYC_REJECT follows the identical reasoning).
        assertThat(permissions).doesNotContain(
                Permission.KYC_APPROVE, Permission.KYC_REJECT, Permission.LOAN_APPROVE, Permission.CARD_APPROVE,
                Permission.EMPLOYEE_MANAGE, Permission.ROLE_MANAGE);
    }

    @Test
    void admin_getsEmployeeAndRoleManagementButNoMoneyMovingOrApprovalPermissions() {
        Set<Permission> permissions = RolePermissions.permissionsFor(Role.ADMIN);
        assertThat(permissions).containsExactlyInAnyOrder(
                Permission.EMPLOYEE_MANAGE, Permission.ROLE_MANAGE, Permission.AUDIT_VIEW,
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW);
        // This phase's own explicit instruction: do not give ADMIN
        // unrestricted access to customer money-moving operations.
        assertThat(permissions).doesNotContain(
                Permission.CASH_DEPOSIT, Permission.CASH_WITHDRAWAL,
                Permission.KYC_APPROVE, Permission.LOAN_APPROVE, Permission.CARD_APPROVE);
    }

    @Test
    void permissionsFor_setOfRoles_unionsEachRolesPermissions() {
        Set<Permission> union = RolePermissions.permissionsFor(Set.of(Role.TELLER, Role.KYC_OFFICER));
        assertThat(union).containsExactlyInAnyOrder(
                Permission.CUSTOMER_VIEW, Permission.ACCOUNT_VIEW, Permission.TRANSACTION_VIEW,
                Permission.CASH_DEPOSIT, Permission.CASH_WITHDRAWAL,
                Permission.KYC_VIEW, Permission.KYC_REVIEW, Permission.KYC_APPROVE, Permission.KYC_REJECT);
    }

    @Test
    void permissionsFor_emptyRoleSet_returnsEmptyPermissionSet() {
        assertThat(RolePermissions.permissionsFor(Set.<Role>of())).isEmpty();
    }
}
