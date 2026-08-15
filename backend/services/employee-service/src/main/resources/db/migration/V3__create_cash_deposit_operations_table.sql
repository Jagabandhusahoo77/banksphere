-- Phase 9B — the employee-side "did I do this" operation log for cash
-- deposits. Every column is either an opaque id or an immutable
-- point-in-time fact (account number, amounts, who/when) — never a
-- mutable value like balance or customer name that would require staying
-- in sync with account-service/customer-service. See ADR-007, Decision 9.
CREATE TABLE cash_deposit_operations (
    id                    UUID          PRIMARY KEY,
    operation_reference   VARCHAR(20)   NOT NULL,
    employee_id           UUID          NOT NULL REFERENCES employees (id),
    employee_number       VARCHAR(20)   NOT NULL,
    branch_id             UUID          NOT NULL REFERENCES branches (id),
    branch_code           VARCHAR(20)   NOT NULL,
    customer_id           UUID          NOT NULL,
    account_id            UUID          NOT NULL,
    account_number        VARCHAR(20)   NOT NULL,
    amount                NUMERIC(19,4) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    transaction_reference VARCHAR(30),
    failure_reason        VARCHAR(500),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_cash_deposit_operations_reference UNIQUE (operation_reference),
    CONSTRAINT chk_cash_deposit_operations_status CHECK (status IN ('COMPLETED', 'FAILED'))
);

-- "My/my branch's recent cash deposits" (the employee operations history
-- screen) is this table's only query so far — most recent first, scoped
-- to the acting employee's own branch.
CREATE INDEX idx_cash_deposit_operations_branch_created_at ON cash_deposit_operations (branch_id, created_at DESC);
