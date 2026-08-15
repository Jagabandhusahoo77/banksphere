CREATE TABLE transactions (
    id                     UUID          PRIMARY KEY,
    transaction_reference  VARCHAR(40)   NOT NULL,
    account_id             UUID          NOT NULL,
    transaction_type       VARCHAR(20)   NOT NULL,
    amount                 NUMERIC(19,4) NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    description            VARCHAR(500),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_transactions_reference UNIQUE (transaction_reference),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_account_id_created_at ON transactions (account_id, created_at DESC);
