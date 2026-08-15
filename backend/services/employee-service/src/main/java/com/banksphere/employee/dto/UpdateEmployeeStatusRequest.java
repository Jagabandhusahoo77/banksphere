package com.banksphere.employee.dto;

import com.banksphere.employee.entity.EmployeeStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateEmployeeStatusRequest(
        @NotNull(message = "status is required") EmployeeStatus status
) {
}
