CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    customer_id    UUID          NOT NULL,
    account_number VARCHAR(20)   NOT NULL,
    account_type   VARCHAR(20)   NOT NULL,
    balance        NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency       VARCHAR(3)    NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT chk_accounts_account_type CHECK (account_type IN ('SAVINGS', 'CURRENT')),
    CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED'))
);

CREATE INDEX idx_accounts_customer_id ON accounts (customer_id);
