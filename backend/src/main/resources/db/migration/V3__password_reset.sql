-- Forgot-password flow. Like api_key, only the SHA-256 hash of the token is
-- stored: a database leak must not yield a usable reset link.

CREATE TABLE password_reset_token (
    id         UUID        PRIMARY KEY,
    account_id UUID        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex; the token itself is never stored
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE      -- set on redemption; tokens are single-use
);

-- Requesting a reset invalidates the account's outstanding tokens, so this is
-- the hot path for both issuing and cleanup.
CREATE INDEX idx_password_reset_account ON password_reset_token (account_id);
