# Payment gateway integration — provider choice and Razorpay plan

> ## ⚠️ Superseded — 27 July 2026
>
> **Billing ships through the Microsoft Azure Marketplace, not Razorpay.** See
> [`marketplace-plan.md`](marketplace-plan.md) for what was built.
>
> **Why the decision changed.** §1 below chose Razorpay largely because the
> account was already KYC'd, while conceding that its "weakest corner" is
> exactly geneav's shape: international customers, USD, auto-renewing, sold from
> an Indian entity — and that EU/UK VAT and US sales tax would be "your problem".
> Microsoft as **merchant of record** absorbs all of it: it collects payment,
> applies tax in every jurisdiction, and pays out. The table in §1 already
> scored a merchant of record as "the least total work"; Marketplace is that
> option, and it arrives with a distribution channel attached.
>
> **What is still worth reading.** §4.1 and §4.2 are provider-agnostic and
> transferred to the Marketplace implementation almost verbatim — take the raw
> request body for signature checks, make retries free via a unique event id,
> always return 2xx once stored, and exempt the webhook from both
> `RateLimitFilter` and the Caddy per-IP limit. That last trap was real: it
> would have silently broken subscription lifecycle events in production.
>
> **What did not survive.** §2's `V5__billing.sql` — V5 was taken by signup
> attribution, so the marketplace schema is `V6__marketplace.sql`. The
> `PaymentProvider` seam in §4 was not built either: with one rail there was
> nothing to abstract over. It remains the right shape if a direct card rail is
> ever added back (see `marketplace-plan.md` §5).

**Status:** Superseded by [`marketplace-plan.md`](marketplace-plan.md)
**Date:** 26 July 2026 · superseded 27 July 2026
**Audience:** §1 is the commercial decision; §2 onward is the implementation design.
**Relates to:** [`business-case.md`](business-case.md) finding #1 — "the unit economics work, but
only once billing exists… revenue today is $0."

---

## 0. Why this document exists

geneav has the entire commercial machinery except the money. Accounts, API keys, plan tiers,
monthly quotas and per-plan rate limits are live and enforced — `QuotaMeteringFilter` reads
`account.plan` and returns 402 when the quota is spent. What is missing is any way to *become*
a paying account: every paid CTA on the pricing page is a `mailto:admin@geneav.com`.

This plan turns the existing quota machinery into revenue: a signed-in user picks a paid tier,
pays through a gateway, a verified webhook flips `account.plan`, and the metering path that
already exists starts enforcing the new quota — **with no change to the scan path at all.**

**Assumptions this plan is written against** (change these and §1 changes with them):

- Customers are mostly **international**, paying in **USD** — matching the live pricing page
  ($19 Starter / $39 Pro / $149 Scale).
- Paid plans **auto-renew** monthly.
- The first slice is **full self-serve upgrade**, not a manual invoice flow.

---

## 1. Which gateway

That combination — global, USD, auto-recurring, sold from an Indian entity — is Razorpay's
weakest corner. Stated plainly before committing to it:

| | Razorpay | Stripe | Merchant of Record (Paddle / Lemon Squeezy / Polar) |
|---|---|---|---|
| India domestic INR (UPI, netbanking, RuPay) | **Best in class** | Workable | Not the point |
| International cards in USD | Needs separate activation; higher MDR (~3% + 18% GST vs ~2% domestic); settles to your bank in INR | Native | Native |
| Recurring / subscriptions | Built around Indian mandates — card e-mandate, UPI Autopay, NACH. International recurring is the restricted path | Native, mature | Native |
| EU/UK VAT + US sales tax on SaaS | Your problem | Your problem (Stripe Tax calculates; you still register and file) | **Handled — they are the seller of record** |
| Onboarding status | **Already KYC'd** | Fresh onboarding | Fresh onboarding |

For selling $19–$149/month SaaS to a worldwide developer audience, an MoR is the least total
work, because it absorbs VAT registration entirely; Stripe is the best pure gateway. Razorpay's
honest advantage is that **the account already exists and is KYC'd**, which is worth real weeks —
and if a meaningful share of customers turns out to be Indian, it becomes the outright best
choice.

**Decision: build Razorpay, behind a provider seam.** One `PaymentProvider` interface sits
between geneav and the gateway. Swapping to Stripe, or adding it alongside, is then a new
implementation class plus a webhook handler — not a rewrite of the account, plan or quota model,
which stay provider-agnostic.

### 1.1 Two questions to settle with Razorpay before writing code

Both are account-level facts only Razorpay can confirm, and the second can invalidate the
recurring design:

1. Are **international payments activated** on the account?
2. Can **Razorpay Subscriptions auto-charge international cards in USD** for this account?

If (2) is no, the fallback that still ships is one-time Orders / Payment Links with a 30-day
grant and renewal reminders — same DB model, same webhook, only `RazorpayPaymentProvider` and
the expiry job change. Everything below still holds.

---

## 2. Database — `V5__billing.sql`

`account.plan` remains the **single source of truth for entitlement**; billing writes to it.
Nothing in the scan/quota path learns that a gateway exists.

```sql
-- Maps a geneav account to its identity at the payment provider.
CREATE TABLE billing_customer (
    account_id           UUID        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    provider             VARCHAR(32) NOT NULL,          -- 'razorpay'
    provider_customer_id VARCHAR(64) NOT NULL,          -- cust_XXXXXXXX
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, provider)
);

CREATE TABLE billing_subscription (
    id                   UUID        PRIMARY KEY,
    account_id           UUID        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    provider             VARCHAR(32) NOT NULL,
    provider_ref         VARCHAR(64) NOT NULL,          -- sub_XXXXXXXX
    plan_key             VARCHAR(64) NOT NULL,          -- starter | pro | scale
    provider_plan_id     VARCHAR(64) NOT NULL,          -- plan_XXXXXXXX
    status               VARCHAR(32) NOT NULL,          -- created|authenticated|active|halted|cancelled|completed|expired
    current_period_end   TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_billing_sub_ref ON billing_subscription (provider, provider_ref);
-- At most one live subscription per account; a second upgrade must replace, not stack.
CREATE UNIQUE INDEX uq_billing_sub_live ON billing_subscription (account_id)
    WHERE status IN ('created', 'authenticated', 'active', 'halted');

-- Webhook idempotency + audit trail. Razorpay retries, and retries must be free.
CREATE TABLE billing_event (
    id          UUID         PRIMARY KEY,
    provider    VARCHAR(32)  NOT NULL,
    event_id    VARCHAR(128) NOT NULL,   -- X-Razorpay-Event-Id
    event_type  VARCHAR(64)  NOT NULL,
    payload     TEXT         NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, event_id)
);
```

---

## 3. Configuration — `application.yml`

Mirrors the existing disabled-by-default pattern used by `geneav.mail` and `geneav.openai`: with
no keys configured, billing endpoints return 503 and the site behaves exactly as it does today.

```yaml
geneav:
  billing:
    enabled: ${GENEAV_BILLING_ENABLED:false}
    provider: ${GENEAV_BILLING_PROVIDER:razorpay}
    currency: ${GENEAV_BILLING_CURRENCY:USD}
    razorpay:
      key-id: ${RAZORPAY_KEY_ID:}
      key-secret: ${RAZORPAY_KEY_SECRET:}
      webhook-secret: ${RAZORPAY_WEBHOOK_SECRET:}
    # geneav plan key -> provider plan id. Plans are created once in the Razorpay
    # dashboard (USD, monthly) and their ids pasted here; prices stay out of code,
    # exactly as PlanProperties keeps limits out of code.
    plan-ids:
      starter: ${RAZORPAY_PLAN_STARTER:}
      pro:     ${RAZORPAY_PLAN_PRO:}
      scale:   ${RAZORPAY_PLAN_SCALE:}
```

Add the same keys to `.env.prod.example`. **Secrets live in `.env.prod` on the VM only** — never
committed, and the webhook secret is distinct from the API key secret.

---

## 4. Backend — new package `com.geneav.scan.billing`

Dependency: `com.razorpay:razorpay-java` in `backend/pom.xml`, version pinned explicitly (as
`springdoc` is) since it is not managed by the Spring Boot BOM.

| Class | Responsibility |
|---|---|
| `BillingProperties` | `@ConfigurationProperties("geneav.billing")`, modelled on `PlanProperties`. Holds the plan-id mapping — **not** `PlanProperties`, which stays about limits. |
| `PaymentProvider` (interface) | The swap seam: `CheckoutHandle startSubscription(Account, String planKey)`, `void cancelAtPeriodEnd(String providerRef)`, `ProviderEvent verify(String rawBody, String signature, String eventId)`. Returns geneav types, never SDK types. |
| `RazorpayPaymentProvider` | The only class that imports the Razorpay SDK. |
| `BillingService` | Orchestration, and the only writer of `account.plan` for billing reasons. Applies events transactionally: record `billing_event` first (unique constraint = idempotency), then update subscription + plan. |
| `BillingController` | The four endpoints below. |
| Entities + repositories | `Subscription`, `BillingCustomer`, `BillingEvent`, in the style of `Account` / `ApiKey`. |
| `SubscriptionExpiryJob` | `@Scheduled` sweep; downgrades to `free` once `current_period_end` passes and status is no longer live. Follow `PasswordResetCleanupJob` for the env-overridable cadence. |

**Endpoints** under `/api/v1/billing/`, session-authenticated via the existing
`CurrentAccount.resolve(request)` pattern:

- `POST /subscription` — body `{planKey}`; validates against `PlanCatalog`, creates the Razorpay
  subscription, returns `{razorpaySubscriptionId, keyId, planKey}`
- `GET /subscription` — current plan, status, renewal date, `cancelAtPeriodEnd`
- `POST /subscription/cancel` — cancels **at period end**, which is what the Terms already
  promise ("you may cancel at any time and will retain access until the end of the paid period")
- `POST /webhook/razorpay` — public, signature-verified; everything else is authenticated

### 4.1 Webhook details that bite

- Take the body as `@RequestBody String rawBody` and HMAC **the exact bytes**. Re-serialising
  parsed JSON changes the signature and every event fails. No filter ahead of it consumes the
  body (`ApiKeyAuthFilter` and `RateLimitFilter` read headers only), so this is safe.
- Verify `X-Razorpay-Signature` = HMAC-SHA256(rawBody, **webhook secret**). Compare in constant
  time; reject with 400 before parsing.
- Idempotency on `X-Razorpay-Event-Id` via the `billing_event` unique constraint — a duplicate
  delivery returns 200 and does nothing.
- **Always return 2xx once stored.** Non-2xx triggers Razorpay's retry schedule.
- The browser-side checkout callback is a **UX signal only, never the entitlement grant** — it
  comes from the client. Only the webhook may change `account.plan`. Confirm the exact signature
  concatenation against current Razorpay docs; it differs between the Subscriptions and Orders
  flows.
- Handle: `subscription.activated` → set plan; `subscription.charged` → extend
  `current_period_end`; `subscription.pending` / `.halted` → keep plan, flag for email;
  `subscription.cancelled` / `.completed` → let the expiry job downgrade at period end. Anything
  unrecognised: store and 200.

### 4.2 Two rate limits will silently break the webhook

Both must be exempted, or Razorpay's retries get 429s — most likely on the 1st of the month when
`subscription.charged` events arrive in a burst from a small set of source IPs:

- `RateLimitFilter.isExempt()` — add the webhook path alongside the existing health and
  `auth/me` exemptions. It sits under `/api/v1/*` and currently falls into the `other` bucket at
  60/min **per IP**.
- `caddy/Caddyfile` — the `handle /api/*` block applies `events 20 / window 1m` per remote host.
  Add a `handle /api/v1/billing/webhook/*` block **ahead** of it that proxies without
  `rate_limit`. Signature verification, not throttling, is the guard here.

---

## 5. Frontend

- `app/lib/billing.ts` — load `https://checkout.razorpay.com/v1/checkout.js` once (idempotent
  promise), then open Checkout with the `subscription_id` from the backend. No CSP is set today
  in `next.config.mjs` or the Caddyfile, so nothing blocks the script — but if security headers
  are added later, that domain must be allowlisted in `script-src` / `frame-src`.
- `app/page.tsx` — replace the three `mailto:` CTAs with an upgrade handler: signed out →
  `/login`; signed in → create subscription → open Checkout. Remove the stale "no checkout yet"
  comment above the `plans` array.
- `app/components/Dashboard.tsx` — a Billing card beside the existing usage meter: current plan,
  renewal date, Change plan / Cancel. After checkout, re-fetch `/api/v1/usage` — but show
  "activating…" rather than the new plan until the webhook lands, since the client callback is
  not the grant.

---

## 6. Compliance follow-ups

- Add Razorpay to the public subprocessor list on `/security` and to the privacy page.
- Razorpay's merchant requirements expect reachable pricing, contact, and refund/cancellation
  policies. §6 of the Terms already covers billing, non-refundability and cancellation, so this
  is a link check, not new drafting.
- Update `business-case.md` finding #1 and §8 ("No billing. No Stripe, no checkout") once shipped.

---

## 7. Verification

1. **Unit tests** in `backend/src/test/java/com/geneav/scan/billing/`, in the style of
   `QuotaMeteringFilterTest`:
   - valid signature accepted; tampered body and wrong secret rejected with 400
   - duplicate `X-Razorpay-Event-Id` is a 200 no-op (no second plan change)
   - `subscription.activated` sets `account.plan`; `charged` extends the period
   - unknown plan key on `POST /subscription` → 400, never a Razorpay call
   - `SubscriptionExpiryJob` downgrades a lapsed account, leaves a live one alone
   - billing disabled (no keys) → 503, matching the mail/openai convention
2. **Local end-to-end** with test-mode keys (`rzp_test_…`) and test cards:
   - bring the stack up with `./geneav.sh start`
   - webhooks cannot reach localhost — either tunnel (ngrok/cloudflared) and register that URL in
     the Razorpay dashboard, or add `scripts/replay-webhook.sh` that POSTs a saved payload with a
     freshly computed HMAC. Keep the replay script regardless; it is how production events get
     debugged.
   - sign up → upgrade to Pro → confirm `GET /api/v1/usage` reports `pro` with quota 100000, and
     that the 402 now arrives at 100,000 rather than 500
3. **Downgrade path:** cancel, force `current_period_end` into the past, run the job, confirm the
   account is back to `free`.
4. **Production smoke:** deploy with `GENEAV_BILLING_ENABLED=false` first and confirm the site is
   unchanged; then enable with live keys and run one real low-value transaction end to end before
   announcing pricing.
