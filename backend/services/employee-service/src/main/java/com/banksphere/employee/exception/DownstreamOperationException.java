package com.banksphere.employee.exception;

/**
 * Wraps a real error response from a downstream service call
 * (account-service/customer-service) so the SAME HTTP status and message
 * that service returned is reported back to the employee-portal, rather
 * than a generic 500 or a fabricated status. See
 * {@code GlobalExceptionHandler} and ADR-007.
 */
public class DownstreamOperationException extends RuntimeException {

    private final int status;

    public DownstreamOperationException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
