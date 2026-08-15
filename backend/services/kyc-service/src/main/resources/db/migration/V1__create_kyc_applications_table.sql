CREATE TABLE kyc_applications (
    id                   UUID          PRIMARY KEY,
    customer_id          UUID          NOT NULL,
    status               VARCHAR(40)   NOT NULL DEFAULT 'DRAFT',
    pan_number           VARCHAR(20),
    occupation           VARCHAR(100),
    annual_income_range  VARCHAR(30),
    current_reviewer_id  UUID,
    submitted_at         TIMESTAMPTZ,
    reviewed_at          TIMESTAMPTZ,
    reviewed_by          UUID,
    review_reason        VARCHAR(1000),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_kyc_applications_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_INFORMATION_REQUIRED',
        'RESUBMITTED', 'APPROVED', 'REJECTED'
    ))
);

-- "My KYC status" (GET /api/v1/kyc/applications/me) and the customer-scoped
-- create/duplicate-draft check both filter by customer_id — see
-- docs/security/authorization.md; customer_id is never trusted from the
-- request body, only from the customer JWT (see CurrentUser).
CREATE INDEX idx_kyc_applications_customer_id ON kyc_applications (customer_id);

-- A customer may have many REJECTED/APPROVED applications over time (a
-- future re-KYC flow), but only ever one application that is still "in
-- flight" — this partial unique index enforces "at most one non-terminal
-- application per customer" at the database level, mirroring the same
-- partial-unique-index pattern already used in
-- beneficiary-service's uq_beneficiaries_customer_account_ifsc_active.
CREATE UNIQUE INDEX uq_kyc_applications_customer_active
    ON kyc_applications (customer_id)
    WHERE status NOT IN ('APPROVED', 'REJECTED');

-- The employee KYC queue (GET /api/v1/kyc/employee/queue) filters and
-- sorts by status — see KycQueueController.
CREATE INDEX idx_kyc_applications_status ON kyc_applications (status);
