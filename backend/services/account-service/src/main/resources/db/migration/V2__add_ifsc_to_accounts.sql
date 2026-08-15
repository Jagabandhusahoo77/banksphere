-- BankSphere is a single fictional bank with no branch model yet, so every
-- account shares one demo IFSC rather than a per-account/per-branch value —
-- see Account.java and AccountServiceImpl#BANKSPHERE_IFSC.
--
-- Added as a nullable column, backfilled, then locked to NOT NULL in three
-- steps so this migration is safe to run against a table that may already
-- have rows (a bare `ADD COLUMN ... NOT NULL` with no default would fail
-- outright against any existing account).
ALTER TABLE accounts ADD COLUMN ifsc VARCHAR(11);

UPDATE accounts SET ifsc = 'BANK0000001' WHERE ifsc IS NULL;

ALTER TABLE accounts ALTER COLUMN ifsc SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN ifsc SET DEFAULT 'BANK0000001';

-- Same 11-character Indian IFSC format beneficiary-service already
-- validates (4-letter bank code + literal '0' + 6 alphanumeric branch code).
ALTER TABLE accounts ADD CONSTRAINT chk_accounts_ifsc_format CHECK (ifsc ~ '^[A-Z]{4}0[A-Z0-9]{6}$');
