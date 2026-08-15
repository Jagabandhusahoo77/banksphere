-- OTP authentication infrastructure — Phase 9D. Lives in customer-service
-- because customer-service already owns customer authentication (JWT
-- issuance, credentials, register/login); OTP is a new authentication
-- factor within that same bounded context, not a separate domain — see
-- ADR-009.
--
-- customer_id is nullable and deliberately NOT a foreign key to
-- `customers`: an OTP request for an identifier that does not match any
-- real customer still creates a challenge row (see OtpServiceImpl), so the
-- response shape/timing is identical whether or not the identifier is
-- registered — the account-enumeration defense this table exists to
-- support would be defeated by a NOT NULL/FK constraint that could only
-- be satisfied by a real customer.
CREATE TABLE otp_challenges (
    id                UUID          PRIMARY KEY,
    identifier        VARCHAR(255)  NOT NULL,
    purpose           VARCHAR(40)   NOT NULL,
    customer_id       UUID,
    otp_hash          VARCHAR(255)  NOT NULL,
    context_hash      VARCHAR(64),
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempt_count     INT           NOT NULL DEFAULT 0,
    max_attempts      INT           NOT NULL DEFAULT 5,
    expires_at        TIMESTAMPTZ   NOT NULL,
    consumed_at       TIMESTAMPTZ,
    requested_ip      VARCHAR(64),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_otp_challenges_purpose CHECK (purpose IN (
        'LOGIN', 'STEP_UP_TRANSFER', 'STEP_UP_WITHDRAWAL', 'STEP_UP_BENEFICIARY', 'STEP_UP_PROFILE_CHANGE'
    )),
    CONSTRAINT chk_otp_challenges_status CHECK (status IN (
        'PENDING', 'VERIFIED', 'EXECUTED', 'EXPIRED', 'LOCKED', 'CONSUMED'
    ))
);

-- The 30-second resend-cooldown check (OtpServiceImpl.requestOtp) looks
-- up "does this identifier+purpose already have a recent PENDING
-- challenge" — this index is that query's access path.
CREATE INDEX idx_otp_challenges_identifier_purpose ON otp_challenges (identifier, purpose, created_at DESC);

-- Customer 360-style lookups ("this customer's recent OTP activity", used
-- by the dev-only OTP inbox) filter by customer_id.
CREATE INDEX idx_otp_challenges_customer_id ON otp_challenges (customer_id, created_at DESC);

-- Refresh tokens — deliberately opaque, high-entropy random values, never
-- JWTs (see ADR-009: a refresh token needs no self-describing claims, and
-- keeping it opaque means nothing about the customer is readable if the
-- stored hash were ever exposed). Only the SHA-256 hash is stored, the
-- same "never store the verifiable secret in plaintext" principle as
-- otp_hash above — SHA-256 (not BCrypt) is deliberate here: the token
-- itself is already a long, high-entropy random value (unlike a
-- human-chosen password), so it needs no slow/salted hashing to resist
-- brute force, only fast, collision-resistant lookup-by-hash.
CREATE TABLE refresh_tokens (
    id                   UUID          PRIMARY KEY,
    customer_id          UUID          NOT NULL REFERENCES customers(id),
    token_hash           VARCHAR(64)   NOT NULL UNIQUE,
    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    replaced_by_token_id UUID,
    expires_at           TIMESTAMPTZ   NOT NULL,
    revoked_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_refresh_tokens_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED'))
);

-- POST /api/v1/auth/token/refresh's entire job is "look this hash up" — the UNIQUE constraint above already gives this an index, no separate one needed.
CREATE INDEX idx_refresh_tokens_customer_id ON refresh_tokens (customer_id, status);
