CREATE TABLE kyc_documents (
    id                  UUID          PRIMARY KEY,
    kyc_application_id  UUID          NOT NULL REFERENCES kyc_applications (id),
    document_type       VARCHAR(30)   NOT NULL,
    document_status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    storage_reference    VARCHAR(255)  NOT NULL,
    original_file_name   VARCHAR(255)  NOT NULL,
    content_type         VARCHAR(100)  NOT NULL,
    file_size            BIGINT        NOT NULL,
    submitted_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    verified_at          TIMESTAMPTZ,
    verified_by          UUID,
    rejection_reason     VARCHAR(500),
    CONSTRAINT chk_kyc_documents_type CHECK (document_type IN (
        'PAN', 'IDENTITY_PROOF', 'ADDRESS_PROOF', 'BANK_STATEMENT'
    )),
    CONSTRAINT chk_kyc_documents_status CHECK (document_status IN (
        'PENDING', 'VERIFIED', 'REJECTED'
    ))
);

-- The review screen's document list (GET /api/v1/kyc/employee/applications/{id})
-- and the "does this application have all four required document types
-- with at least one non-REJECTED document" submit/resubmit check both
-- filter by kyc_application_id — see KycApplicationService.computeMissingDocumentTypes.
CREATE INDEX idx_kyc_documents_application_id ON kyc_documents (kyc_application_id);

-- Duplicate-document rejection (a customer re-uploading the same document
-- type while a PENDING or VERIFIED document of that type already exists)
-- is enforced in the service layer, not by a database constraint: unlike
-- beneficiary's active-uniqueness rule, a REJECTED document must remain
-- re-uploadable under the same type without first deleting the rejected
-- row, and "PENDING or VERIFIED" is not expressible as a single static
-- partial-index predicate the way beneficiary's single-status ACTIVE
-- predicate is. See ADR-008.
