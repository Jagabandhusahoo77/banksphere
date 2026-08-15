package com.banksphere.employee.exception;

import java.util.UUID;

public class BranchNotFoundException extends RuntimeException {

    public BranchNotFoundException(UUID id) {
        super("Branch not found: " + id);
    }
}
