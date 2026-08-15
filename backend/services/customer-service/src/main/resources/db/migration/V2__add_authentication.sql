-- Authentication credentials, deliberately kept in a separate table from
-- `customers` (profile data) rather than adding password_hash directly to
-- that table — see docs/security/authentication.md. A 1:1 relationship,
-- keyed by customer_id, enforced by both the PK and the FK.
CREATE TABLE customer_credentials (
    customer_id    UUID          PRIMARY KEY REFERENCES customers(id),
    password_hash  VARCHAR(255)  NOT NULL,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
