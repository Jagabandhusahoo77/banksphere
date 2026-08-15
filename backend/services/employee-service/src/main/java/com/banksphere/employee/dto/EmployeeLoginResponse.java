package com.banksphere.employee.dto;

import java.util.List;

public record EmployeeLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        EmployeeSummary employee,
        List<String> roles,
        List<String> permissions,
        BranchSummary branch
) {
    public static EmployeeLoginResponse of(
            String accessToken, long expiresIn, EmployeeSummary employee,
            List<String> roles, List<String> permissions, BranchSummary branch) {
        return new EmployeeLoginResponse(accessToken, "Bearer", expiresIn, employee, roles, permissions, branch);
    }
}
