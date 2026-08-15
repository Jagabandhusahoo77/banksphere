package com.banksphere.account.exception;

import java.util.UUID;

/**
 * Thrown when a TELLER attempts a branch-scoped operation (cash deposit)
 * against an account whose IFSC doesn't match the employee's own branch
 * IFSC. Not thrown for BRANCH_MANAGER/ADMIN — see
 * {@code EmployeePrincipal#hasBroadBranchScope} and ADR-007 for the
 * documented broader-scope decision.
 */
public class BranchScopeViolationException extends RuntimeException {

    public BranchScopeViolationException(UUID accountId, String accountIfsc, String employeeBranchIfsc) {
        super("Account " + accountId + " belongs to branch " + accountIfsc
                + ", which is outside the caller's own branch (" + employeeBranchIfsc + ")");
    }
}
