package com.banksphere.employee.exception;

public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String username) {
        super("An employee with username '" + username + "' already exists");
    }
}
