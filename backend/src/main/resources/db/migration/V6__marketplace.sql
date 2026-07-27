-- Microsoft Marketplace billing.
--
-- account.plan remains the single source of truth for entitlement; these tables
-- record where that entitlement came from and keep Microsoft's subscription
-- lifecycle auditable. Nothing in the scan/quota path reads them.

-- Where this account's entitlement comes from. 'none' = self-serve free tier;
-- 'marketplace' = a live Microsoft Marketplace subscription set the plan.
ALTER TABLE account ADD COLUMN billing_source VARCHAR(32) NOT NULL DEFAULT 'none';

CREATE TABLE marketplace_subscription (
    id                    UUID PRIMARY KEY,
    account_id            UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    marketplace_sub_id    UUID NOT NULL UNIQUE,   -- Microsoft's SaaS subscription id
    offer_id              VARCHAR(128) NOT NULL,
    marketplace_plan_id   VARCHAR(128) NOT NULL,  -- e.g. geneav-pro (Partner Center plan id)
    plan_key              VARCHAR(64)  NOT NULL,  -- e.g. pro (geneav.plans key, via plan-map)
    quantity              INTEGER,                -- null: geneav plans are not per-seat
    status                VARCHAR(32)  NOT NULL,  -- PendingFulfillmentStart|Subscribed|Suspended|Unsubscribed
    is_free_trial         BOOLEAN NOT NULL DEFAULT false,
    is_test               BOOLEAN NOT NULL DEFAULT false,
    auto_renew            BOOLEAN,
    term_start            TIMESTAMPTZ,
    term_end              TIMESTAMPTZ,
    beneficiary_email     VARCHAR(320),
    beneficiary_tenant_id UUID,
    purchaser_email       VARCHAR(320),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mp_sub_account ON marketplace_subscription (account_id);
-- At most one live subscription per account; a second purchase must replace,
-- not stack.
CREATE UNIQUE INDEX uq_mp_sub_live ON marketplace_subscription (account_id)
    WHERE status IN ('PendingFulfillmentStart', 'Subscribed', 'Suspended');

-- Webhook idempotency + audit trail. Microsoft retries the webhook up to 500
-- times over eight hours; the unique constraint makes every retry a free no-op.
CREATE TABLE marketplace_event (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,   -- payload "id"; doubles as the operationId to ACK
    subscription_id UUID,                   -- Microsoft's subscription id (not our row id)
    action          VARCHAR(64) NOT NULL,   -- Subscribe|ChangePlan|Renew|Suspend|Reinstate|Unsubscribe|...
    status          VARCHAR(32) NOT NULL,   -- payload "status" at receipt
    payload         TEXT NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
