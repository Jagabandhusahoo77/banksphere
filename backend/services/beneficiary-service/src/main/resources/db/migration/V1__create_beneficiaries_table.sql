CREATE TABLE beneficiaries (
    id               UUID          PRIMARY KEY,
    customer_id      UUID          NOT NULL,
    beneficiary_name VARCHAR(100)  NOT NULL,
    account_number   VARCHAR(20)   NOT NULL,
    ifsc             VARCHAR(11)   NOT NULL,
    bank_name        VARCHAR(150)  NOT NULL,
    nickname         VARCHAR(50),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_beneficiaries_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- "List my beneficiaries" (GET /api/v1/beneficiaries) is the service's
-- single most common query — every request is scoped to the caller's own
-- customer_id (see docs/security/authorization.md).
CREATE INDEX idx_beneficiaries_customer_id ON beneficiaries (customer_id);

-- Enforces "same customer + same account number + same IFSC must not have
-- two ACTIVE beneficiaries" at the database level, not just in application
-- code — a concurrent duplicate POST /api/v1/beneficiaries request can
-- still race past an application-layer existence check, but not past this
-- constraint (see BeneficiaryServiceImpl.createBeneficiary and
-- GlobalExceptionHandler's DataIntegrityViolationException handler, which
-- turns the resulting DB error into a clean 409). A PARTIAL unique index
-- (WHERE status = 'ACTIVE') rather than a plain unique constraint is
-- deliberate: it lets a customer deactivate a beneficiary and later
-- re-add the same account/IFSC combination, which a whole-table unique
-- constraint would permanently block. This same composite also serves as
-- the customer_id+account_number index called out in the design brief —
-- a separate bare index on either column alone would be redundant with
-- it, so none was added (see docs/database/README.md).
CREATE UNIQUE INDEX uq_beneficiaries_customer_account_ifsc_active
    ON beneficiaries (customer_id, account_number, ifsc)
    WHERE status = 'ACTIVE';
