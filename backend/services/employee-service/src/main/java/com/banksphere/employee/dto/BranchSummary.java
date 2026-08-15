package com.banksphere.employee.dto;

import com.banksphere.employee.entity.Branch;

import java.util.UUID;

public record BranchSummary(UUID id, String branchCode, String branchName, String ifsc) {

    public static BranchSummary from(Branch branch) {
        return new BranchSummary(branch.getId(), branch.getBranchCode(), branch.getBranchName(), branch.getIfsc());
    }
}
