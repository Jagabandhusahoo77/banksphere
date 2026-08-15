CREATE TABLE kyc_status_history (
    id                     UUID          PRIMARY KEY,
    kyc_application_id     UUID          NOT NULL REFERENCES kyc_applications (id),
    from_status            VARCHAR(40),
    to_status               VARCHAR(40)   NOT NULL,
    changed_by_employee_id  UUID,
    reason                  VARCHAR(1000),
    changed_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_kyc_status_history_from CHECK (from_status IS NULL OR from_status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_INFORMATION_REQUIRED',
        'RESUBMITTED', 'APPROVED', 'REJECTED'
    )),
    CONSTRAINT chk_kyc_status_history_to CHECK (to_status IN (
        'DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'ADDITIONAL_INFORMATION_REQUIRED',
        'RESUBMITTED', 'APPROVED', 'REJECTED'
    ))
);

-- The review screen's "review history" panel (part of GET
-- /api/v1/kyc/employee/applications/{id}) reads this ordered by
-- changed_at for one application.
CREATE INDEX idx_kyc_status_history_application_id ON kyc_status_history (kyc_application_id);
