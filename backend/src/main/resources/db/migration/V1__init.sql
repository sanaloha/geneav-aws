-- Phase 1 foundation: accounts, API keys, and monthly usage metering.

CREATE TABLE account (
    id         UUID         PRIMARY KEY,
    email      VARCHAR(320) NOT NULL UNIQUE,
    plan       VARCHAR(64)  NOT NULL DEFAULT 'free',
    status     VARCHAR(32)  NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE api_key (
    id           UUID        PRIMARY KEY,
    account_id   UUID        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    name         VARCHAR(120),
    key_hash     VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hex of the secret; the secret itself is never stored
    key_prefix   VARCHAR(24) NOT NULL,         -- e.g. "gav_live_ab12" for display in listings
    last_four    VARCHAR(4)  NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_api_key_account ON api_key (account_id);

-- One row per account x calendar-month (UTC) x endpoint. Incremented atomically
-- as requests are metered; read for quota enforcement and the usage endpoint.
CREATE TABLE usage_counter (
    account_id   UUID        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    period_month VARCHAR(7)  NOT NULL,   -- YYYY-MM
    endpoint     VARCHAR(32) NOT NULL,   -- scan | chat
    count        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, period_month, endpoint)
);
