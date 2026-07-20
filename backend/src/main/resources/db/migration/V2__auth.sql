-- Password + federated login. Columns are nullable so existing (Phase 1) accounts,
-- which were provisioned by email only, keep working.

ALTER TABLE account ADD COLUMN password_hash    VARCHAR(100);
ALTER TABLE account ADD COLUMN auth_provider    VARCHAR(32) NOT NULL DEFAULT 'password';
ALTER TABLE account ADD COLUMN provider_subject VARCHAR(255);

-- A federated identity (provider + subject) maps to at most one account.
CREATE UNIQUE INDEX uq_account_provider_subject
    ON account (auth_provider, provider_subject)
    WHERE provider_subject IS NOT NULL;
