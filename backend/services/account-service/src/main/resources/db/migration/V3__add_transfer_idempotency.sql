-- Transfer idempotency — Phase 9D. Owned by account-service (not
-- customer-service/OTP infrastructure): idempotency is a property of
-- *executing an operation exactly once*, not an authentication concern —
-- see ADR-009's scope note distinguishing the two.
--
-- A client-generated idempotency_key, scoped per customer (not globally
-- unique) so two different customers can coincidentally generate the same
-- key without colliding. The UNIQUE constraint on (customer_id,
-- idempotency_key) is what actually enforces "exactly once" — the
-- INSERT-then-execute-then-UPDATE application flow (see
-- AccountServiceImpl.transfer) only works because a concurrent second
-- INSERT with the same key fails at the database, not because the
-- application checked-then-acted non-atomically.
CREATE TABLE transfer_idempotency_records (
    id                 UUID          PRIMARY KEY,
    customer_id        UUID          NOT NULL,
    idempotency_key    VARCHAR(100)  NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',
    transfer_id        UUID,
    response_snapshot  TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    CONSTRAINT chk_transfer_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_transfer_idempotency_customer_key ON transfer_idempotency_records (customer_id, idempotency_key);
