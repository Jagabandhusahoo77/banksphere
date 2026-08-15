-- idx_transactions_account_id is redundant: idx_transactions_account_id_created_at
-- (account_id, created_at DESC) already serves equality lookups on account_id
-- alone as a leading-column prefix, and additionally supports the
-- account_id + created_at DESC ordering used by GET /api/v1/transactions/account/{id}.
DROP INDEX IF EXISTS idx_transactions_account_id;
