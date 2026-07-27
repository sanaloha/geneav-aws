# Microsoft Marketplace — setup and operations runbook

**Status:** Code shipped; offer not yet published
**Date:** 27 July 2026
**Supersedes:** [`payment-gateway-plan.md`](payment-gateway-plan.md) (Razorpay)

> The integration is **built and disabled**. Everything below is account-level
> work in Partner Center and Entra that no code can do. Until it is done,
> `GENEAV_MARKETPLACE_ENABLED=false` and the site behaves exactly as it did
> before billing existed.

---

## 0. Why Marketplace rather than a card gateway

[`business-case.md`](business-case.md) finding #1 was "the unit economics work,
but only once billing exists… revenue today is $0". The blocker was never the
metering — `QuotaMeteringFilter` has enforced plan quotas for months — it was
that no one could *become* a paying account.

Microsoft as **merchant of record** removes the problem
[`payment-gateway-plan.md`](payment-gateway-plan.md) §1 spent its length
worrying about: it collects payment, applies VAT and sales tax in every
jurisdiction, and pays out. There is no tax registration to maintain.

Two things beyond revenue justify it:

- **Distribution.** [`compliance-roadmap.md`](compliance-roadmap.md) §5 concludes
  that distribution, not certification, is the bottleneck. A Marketplace listing
  is a channel that compounds — [`marketing-plan.md`](marketing-plan.md)'s own
  criterion for what to build.
- **Budget access.** An Azure-benefit-eligible offer draws down a customer's
  Microsoft Azure Consumption Commitment, so a committed enterprise can buy
  geneav with money it has already spent. No card checkout can do that.

**The cost:** a 3% marketplace service fee, and buyers must have a Microsoft
account. Against ~$28/month of fixed infrastructure ([`business-case.md`](business-case.md)
§5.1, rehosted to AWS 27 July 2026) the fee does not move break-even at all: a
single Pro customer clears it with or without the 3%. The Microsoft-account requirement is
the real constraint — see Risks.

---

## 1. Partner Center

1. **Enrol.** Partner Center account under the Microsoft AI Cloud Partner
   Program, then the *Marketplace* program. Complete the publisher profile.
2. **Payout and tax profiles.** Bank details plus tax forms. **Start this
   first** — it is verification-gated and nothing can transact until it clears.
   It is the schedule risk in this whole document; the code is not.
3. **Create the SaaS offer.** The offer ID is permanent once published.
   Listing assets required: logos at **216×216, 90×90, 48×48, 255×115**, at
   least one screenshot, a description ≤3000 characters, a privacy policy URL,
   Terms of Use, and support contacts. A **lead management destination is
   mandatory** for a transactable offer.
4. **Plans.** Plan IDs are permanent and **must match**
   `geneav.marketplace.plan-map` in `application.yml`:

   | Plan ID | Display | Price | Term | Trial |
   |---|---|---|---|---|
   | `geneav-starter` | Starter | $19 | P1M | no |
   | `geneav-pro` | Pro | $39 | P1M | **30-day** |
   | `geneav-scale` | Scale | $149 | P1M | no |

   Set **`isPricePerSeat: false`** on all three — geneav has no seat model.
   Leave **auto-activation off** so activation flows through our landing page.
   The free tier is **not** listed; it stays self-serve at geneav.com.
5. **Technical configuration.**

   | Field | Value |
   |---|---|
   | Landing page URL | `https://geneav.com/marketplace/landing` |
   | Connection webhook | `https://geneav.com/api/v1/marketplace/webhook` |
   | Microsoft Entra tenant ID | App B's tenant |
   | Microsoft Entra application ID | App B's client id |

   No `#` in the landing page URL — it silently breaks the redirect.
6. **Publish to a preview audience**, run §4, then submit for certification.

---

## 2. The two Entra app registrations

Microsoft's recommended split, and the reason the config has two sets of
credentials. Getting these backwards is the most likely setup mistake.

| | **App A — sign-in** | **App B — fulfillment** |
|---|---|---|
| Name | `geneav-marketplace-signin` | `geneav-marketplace-fulfillment` |
| Tenancy | **Multi-tenant** (any buyer's org) | **Single-tenant** |
| Purpose | Signs the buyer in on the landing page | Calls the SaaS Fulfillment API |
| Secret | Yes | Yes |
| Env vars | `MICROSOFT_LOGIN_CLIENT_ID` / `_SECRET` | `MARKETPLACE_CLIENT_ID` / `_SECRET` |
| In Partner Center? | No | **Yes** — tenant + app id |
| Redirect URI | `https://geneav.com/login/oauth2/code/microsoft` | — |
| API permissions | Delegated `User.Read` only | — |

Do **not** request any permission marked *needs admin consent* on App A — it
blocks every non-administrator buyer from reaching the landing page.
Do **not** enable *Allow public client flows* on App B.

Register the marketplace API service principal in the tenant:

```bash
az ad sp create --id 20e940b3-4c77-4b0b-9a53-9e16a1b010a7
```

Skipping this is the usual cause of a 403 on the first `resolve` call.

---

## 3. How the code is wired

| Concern | Where |
|---|---|
| Config (disabled by default) | `geneav.marketplace.*` in `application.yml` |
| Publisher token (cached, 1h) | `MarketplaceTokenProvider` |
| The only class that calls Microsoft | `FulfillmentClient` |
| Webhook authentication | `WebhookJwtValidator` (`aud` / `tid` / `appid`\|`azp`) |
| The only writer of `account.plan` for billing | `MarketplaceService` |
| Schema | `V6__marketplace.sql` |

`account.plan` remains the single source of truth for entitlement. Status
mapping:

| Marketplace event | `account.plan` |
|---|---|
| `Subscribe` / `Reinstate` | the mapped tier |
| `ChangePlan` | the new mapped tier |
| `Suspend` (payment failed) | → `free`, `plan_key` remembered for reinstate |
| `Unsubscribe` | → `free`, `billing_source` cleared |

Dropping to `free` on suspend rather than to no access is deliberate: the
customer keeps working at 500 scans/month while they fix a card, and nothing
starts failing in a way they cannot diagnose.

**Three exemptions the webhook depends on.** Microsoft retries up to 500 times
over eight hours from a small set of IPs, so the webhook is exempt from
`ApiKeyAuthFilter` (its Entra JWT would be read as an invalid API key),
`RateLimitFilter` (the `other` bucket is 60/min per IP), and the Caddy
`handle /api/*` per-IP limit. The JWT, not throttling, is the guard. All three
are covered by tests or an explicit `Caddyfile` block ordered ahead of `/api/*`.

---

## 4. Verification before going live

1. **Locally**, with `./geneav.sh start`:
   - marketplace disabled → every endpoint 503, scan and signup unchanged
   - enabled with dummy credentials → webhook returns 401 for a missing,
     malformed, or API-key-shaped token
   - `scripts/replay-marketplace-webhook.sh` replays a saved payload
     (needs `MARKETPLACE_RESOURCE_ID` overridden to the client id — test only)
2. **Preview offer**, plan prices at **$0**: purchase → landing → Entra sign-in
   → resolve → activate. Confirm `GET /api/v1/usage` reports `pro` with quota
   100,000, and that 402 now arrives at 100,000 rather than 500. Then change
   plan and unsubscribe from the Microsoft 365 Admin Center and confirm the
   webhook applies each.
3. **Production**: deploy with `GENEAV_MARKETPLACE_ENABLED=false` first and
   confirm the site is unchanged; then enable and run one real low-value
   transaction end to end before announcing.

---

## 5. Risks

- **The payout/tax profile is the schedule risk**, not the code. Start it first.
- **Offer and plan IDs are permanent.** `geneav-starter` / `geneav-pro` /
  `geneav-scale` must match `plan-map` exactly and cannot be renamed later.
- **24/7 availability becomes contractual.** Microsoft requires the landing page
  and webhook to be reachable at all times. A single VM with no failover was an
  accepted risk for a free product; a missed webhook now means a customer paying
  for a tier they do not have. This directly conflicts with the nightly
  auto-shutdown cost lever in [`../deploy-plan.md`](../deploy-plan.md) Phase 4,
  and makes uptime monitoring ([`compliance-roadmap.md`](compliance-roadmap.md)
  §6 item 7, still not configured) a prerequisite rather than good practice.
- **Marketplace-only means Azure-customer-only.** A buyer without a Microsoft
  account cannot purchase at all. That is the accepted cost of dropping the
  direct card rail; the `PaymentProvider` seam described in
  [`payment-gateway-plan.md`](payment-gateway-plan.md) §4 remains the way back
  if it proves too narrow.
- **The frontend flag is build-time.** `NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED` is
  baked into the bundle, so it must be flipped and *redeployed* together with
  the backend's `GENEAV_MICROSOFT_LOGIN_ENABLED`. Setting one without the other
  gives a button that either never appears or leads nowhere.
