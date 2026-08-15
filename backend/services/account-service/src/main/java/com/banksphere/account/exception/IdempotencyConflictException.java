package com.banksphere.account.exception;

/**
 * Thrown when a second request with the same {@code idempotencyKey}
 * arrives while the first is still {@code IN_PROGRESS} — a genuine race
 * (e.g. a network retry firing before the original request's response
 * even came back), not a "this already succeeded, here's the cached
 * result" case (see {@code AccountServiceImpl.transfer}, which handles
 * {@code COMPLETED} by returning the snapshot directly, never throwing).
 * Mapped to {@code 409}.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("A transfer with this idempotency key is already in progress");
    }
}
