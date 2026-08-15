package com.banksphere.employee.exception;

public class DuplicateEmployeeNumberException extends RuntimeException {

    public DuplicateEmployeeNumberException(String employeeNumber) {
        super("An employee with employee number '" + employeeNumber + "' already exists");
    }
}
