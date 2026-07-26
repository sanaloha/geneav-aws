# geneav — Business Case

**Status:** Draft v1
**Date:** 25 July 2026
**Jira:** [GN-12](https://santoshsscet.atlassian.net/browse/GN-12) — Post Production activities (epic GN-3)
**Audience:** §1–§7 are written for a commercial reader; §8 is a technical appendix.

> **Update, 25 July 2026 — the pricing recommendation has shipped.** Pro is now **$39/month**,
> with **Starter ($19)** and **Scale ($149)** added to close the 1,000x gap between Free and Pro,
> and Free raised from 100 to 500 scans. Break-even is consequently **2 Pro customers, not 8**.
> The analysis below is preserved as originally written: the $10 figures in §1, §5.3 and §5.5
> record the position that *prompted* the change, not current pricing. See the pricing page for
> what is live.

---

## 1. Executive summary

**What it is.** geneav is a hosted REST API that scans an uploaded document for malware and returns a
JSON verdict. One `POST /api/v1/scan` with a multipart file, one clean/infected answer. It is live at
geneav.com, with accounts, API keys, quotas and rate limiting in production.

**Who it is for.** Product and platform teams that accept file uploads — SaaS applications with
document upload, HR/legal portals, claims and onboarding pipelines, customer support attachment
handling. Specifically, teams that need *a file* checked inside *an application flow*, where no
endpoint antivirus agent exists and nobody wants to operate a scanning daemon.

**What it is honestly not.** geneav's detection is ClamAV's detection, exactly — there is no
proprietary engine, no behavioural analysis, no second opinion. This is verifiable from the code
(§4). The product's value is the integration and operations layer around ClamAV, not the detection
itself. Any pitch that implies otherwise will not survive a technical evaluation, and the product's
own public copy already says so ([`frontend/app/page.tsx:116`](../frontend/app/page.tsx)).

**The cost case in one line.** A team self-hosting ClamAV pays roughly the same infrastructure bill
geneav pays (~$70/month) *plus* a one-time integration build and ongoing operational load; geneav's
Pro tier is $10/month. The saving is not on infrastructure — it is on the engineering work that
never happens.

**The three findings that matter:**

| # | Finding | Detail |
|---|---|---|
| 1 | **The unit economics work, but only once billing exists.** | Fixed cost is ~$73/month. Break-even is **8 Pro customers**. Beyond that, margin rises steeply because cost is fixed, not per-scan. But there is no checkout — Pro's CTA is a `mailto:` ([`frontend/app/page.tsx:99-102`](../frontend/app/page.tsx)). Revenue today is $0. |
| 2 | **Pro is priced ~11x below the cheapest directly comparable competitor.** | $10 for 100,000 scans/month vs attachmentAV's €99 (≈$112) for the identical 100,000/month tier. Against ClamAV-based rivals the gap is ~198x. This is not aggressive pricing; it is an outlier that invites "what's the catch?" and leaves substantial revenue unclaimed. |
| 3 | **Capacity per box is unmeasured.** | A global 4-concurrent-scan semaphore ([`application.yml:99`](../backend/src/main/resources/application.yml)) sits on a burstable B2ms VM. Nobody has benchmarked how many scans/month one box actually sustains. Signing a customer to a 100,000-scan entitlement without that number is an unpriced risk. |

**Recommendation.** Raise Pro to $29–$49/month — still 2–4x below the cheapest published competitor,
break-even at 2–3 customers instead of 8 — and do not sell it until billing, a Terms of Service, and
a throughput benchmark exist. See §7.

---

## 2. How geneav works

### 2.1 Request path

A scan request traverses the following, in order:

1. **Caddy** (reverse proxy) — terminates TLS with an auto-renewed Let's Encrypt certificate, caps
   request bodies at 30 MB, and applies a per-IP rate limit of 20 events/minute on `/api/*`
   ([`caddy/Caddyfile:17-29`](../caddy/Caddyfile)).
2. **`ApiKeyAuthFilter`** — an optional `Authorization: Bearer gav_live_…` header. Absent means
   anonymous; invalid means 401 ([`ApiKeyAuthFilter.java:39-62`](../backend/src/main/java/com/geneav/scan/account/ApiKeyAuthFilter.java)).
3. **`RateLimitFilter`** — a token bucket keyed by account (plan limits) or by client IP, plus a
   global semaphore capping simultaneous scans. Deliberately ordered **before body parsing**, so a
   throttled upload is rejected without buffering its payload
   ([`RateLimitFilter.java:23-36`](../backend/src/main/java/com/geneav/scan/ratelimit/RateLimitFilter.java),
   [`RateLimiterService.java:26-54`](../backend/src/main/java/com/geneav/scan/ratelimit/RateLimiterService.java)).
4. **`QuotaMeteringFilter`** — for authenticated scans, checks the monthly quota up front (402 if
   exhausted) and increments the counter **only on a 2xx response**, so failed scans are not billed
   ([`QuotaMeteringFilter.java:57-70`](../backend/src/main/java/com/geneav/scan/usage/QuotaMeteringFilter.java)).
5. **`ScanController`** — validates presence and declared content type, mints a scan UUID
   ([`ScanController.java:73-99`](../backend/src/main/java/com/geneav/scan/controller/ScanController.java)).
6. **`ClamAvScanEngine`** — streams the file to the ClamAV daemon over a raw TCP socket using
   ClamAV's native INSTREAM protocol in 8 KiB length-prefixed chunks, with a 30-second socket
   timeout ([`ClamAvScanEngine.java:44-68`](../backend/src/main/java/com/geneav/scan/service/ClamAvScanEngine.java)).

The API writes nothing to disk; the file is streamed from the request straight to the engine.
(Caveat for a diligence conversation: Spring's multipart handling can still spool large parts to the
servlet temp directory, as no `file-size-threshold` is configured.)

### 2.2 The contract the customer actually buys

A stable, typed response surface — this is the product, more than the scan itself:

| Code | Meaning |
|---|---|
| 200 | Verdict returned (`clean` or `infected`, with threat name) |
| 400 | No file provided |
| 402 | Monthly quota exhausted |
| 413 | File exceeds 25 MB |
| 415 | Unsupported declared content type |
| 429 | Rate limited, with `Retry-After` |
| 503 | Scan engine unreachable |

Documented interactively at `/docs` (Swagger UI) and machine-readably at `/api-docs`
([`application.yml:116-120`](../backend/src/main/resources/application.yml)).

### 2.3 Limits as shipped

- Maximum file size 25 MB, request 26 MB ([`application.yml:14-17`](../backend/src/main/resources/application.yml)).
- Eleven accepted content types: PDF, Word, Excel, PowerPoint, plain text, CSV, RTF, ZIP, and
  `application/octet-stream` ([`ScanController.java:40-53`](../backend/src/main/java/com/geneav/scan/controller/ScanController.java)).
- Archive handling is whatever the stock `clamav/clamav:1.4` image ships; there is no custom
  `clamd.conf` in the repository.

---

## 3. Positioning against traditional antivirus

geneav is **adjacent to** endpoint antivirus, not competing with it. Conflating the two is the
fastest way to lose a security-literate buyer.

| | Traditional endpoint AV | geneav |
|---|---|---|
| Protects | A device | A file, inside an application flow |
| Deployment | Agent installed per endpoint | One HTTPS call, no agent |
| Detection | Signatures + heuristics + behavioural + EDR telemetry | Signatures only |
| Real-time | Yes — filesystem hooks, on-access scanning | No — explicit call only |
| Remediation | Quarantine, rollback, isolation | None; returns a verdict, caller decides |
| Commercial model | Per seat, per year | Per API volume, per month |
| Reference price | Bitdefender GravityZone Business Security ≈ **$74/device/year** | $0–$10/month per account |

**Where geneav wins.** The endpoint AV model has no answer for a file arriving at a cloud API. There
is no device to install an agent on; a Lambda handling an upload cannot run GravityZone. Serverless
and containerised upload pipelines are a coverage gap by construction, and that gap is geneav's
market.

**Where it does not.** geneav offers no real-time protection, no behavioural detection, no EDR
telemetry, no quarantine or remediation, and no device management. It does not reduce the number of
endpoint AV seats an organisation needs by one. Sell it as a control on an ingestion path, not as an
AV replacement.

---

## 4. Positioning against ClamAV — the honest version

**Detection is identical, because it *is* ClamAV.** This was verified directly against the source:
the backend has no YARA, no content disarm and reconstruction, no entropy or macro analysis, no
hash-reputation lookup, and no true-file-type sniffing. `backend/pom.xml` carries no security or
document-parsing dependencies at all. Only `ClamAvScanEngine` implements the `ScanEngine` interface.

Anyone evaluating geneav can determine this in an afternoon. Stating it first is what makes the rest
of the argument credible.

### 4.1 What geneav actually adds

| Layer | Raw ClamAV | geneav |
|---|---|---|
| Interface | `clamd` TCP socket, INSTREAM binary protocol | HTTPS multipart, JSON verdict, OpenAPI spec |
| Memory management | You size, monitor and OOM-debug a ~1 GB resident signature DB (measured 974 MB) | Handled; container limit and `ConcurrentDatabaseReload no` both set ([`docker-compose.prod.yml`](../docker-compose.prod.yml), [`docker-compose.yml`](../docker-compose.yml)) |
| Signature updates | You own the freshclam pipeline | Handled, persisted in a volume |
| Abuse protection | **None whatsoever** | Per-IP and per-account token buckets, plus a global concurrency cap ([`application.yml:80-100`](../backend/src/main/resources/application.yml)) |
| Identity | None | Accounts, BCrypt passwords, session cookies |
| API keys | None | `gav_live_` keys, SHA-256 hashed at rest, plaintext shown once ([`ApiKeyService.java:15-56`](../backend/src/main/java/com/geneav/scan/account/ApiKeyService.java)) |
| Metering | None | Atomic per-calendar-month quota counters ([`UsageService.java:24-45`](../backend/src/main/java/com/geneav/scan/usage/UsageService.java)) |
| TLS | You configure it | Automatic, auto-renewed |
| Deployment | You build it | CI/CD with automatic rollback on build failure ([`scripts/deploy-vm.sh:94-105`](../scripts/deploy-vm.sh)) |
| GPL position | You work it out | Already worked out and documented (§4.2) |

ClamAV's own rate-limiting story deserves emphasis: there isn't one. Expose `clamd` to application
traffic without a governor and a single client can exhaust the box. geneav's two-tier defence — per-IP
proxy limits, per-account token buckets, and a global 4-concurrent semaphore explicitly commented as
protecting ClamAV's memory footprint — is operational knowledge encoded in the product.

### 4.2 The GPL question, pre-answered

This is a real objection for any commercial buyer, and geneav has already resolved it. ClamAV is
GPLv2. geneav does not link `libclamav` and includes no ClamAV source; it speaks to `clamd` over a
socket, which is the integration boundary the ClamAV project itself documents for commercial
software ([`NOTICE:25-54`](../NOTICE), quoting ClamAV's `libclamav` manual;
[`LICENSE:15-21`](../LICENSE)). geneav's own code is proprietary, all rights reserved.

The residual obligation is stated plainly in `NOTICE:45-51`: redistributing a ClamAV binary or
container as part of a deployment keeps that component under GPLv2. Because geneav is delivered as
SaaS and no customer receives a copy of ClamAV, this does not bite today — but it *would* constrain
an on-premises or appliance model, which matters if that becomes a roadmap item.

### 4.3 Where self-hosting is the better choice

State these openly; buyers who need them are not winnable and will respect the candour:

- **Data residency or perimeter rules.** Files leave the customer's environment. A single Azure East
  US region, no data-residency options.
- **Volumes far beyond the Pro tier.** At millions of scans per month, running your own box wins.
- **Air-gapped or on-premises requirements.** Not offered.
- **Need for detection beyond ClamAV.** Self-hosting lets you chain engines; geneav does not.
- **An existing ClamAV deployment.** The integration cost is already sunk.

---

## 5. Cost effectiveness

> All figures retrieved 25 July 2026 unless stated. This is a **model**, and its assumptions are
> listed. Vendors who publish no pricing are marked as such rather than estimated.

### 5.1 What geneav costs to run

Everything runs as containers on one Azure VM: Caddy, Next.js frontend, Spring Boot backend, ClamAV,
and PostgreSQL ([`azure-provision.sh:10-35`](../azure-provision.sh),
[`docker-compose.prod.yml`](../docker-compose.prod.yml)).

| Line item | Spec | Monthly |
|---|---|---|
| Azure VM | Standard_B2ms (2 vCPU / 8 GiB), East US, Linux PAYG — $0.0832/hr | **$60.74** |
| Static public IP | Standard SKU — $0.005/hr | **$3.65** |
| OS disk | 30 GB provisioned → Premium SSD P4 (32 GiB) | **~$5.00** ⚠️ |
| Egress | Well under the 100 GB/month free allowance (§5.2) | **$0.00** |
| Domain | geneav.com registration, amortised | **~$1.20** |
| Transactional email | Hostinger mailbox, SMTP on 587 | **~$2.00** ⚠️ |
| OpenAI (chat widget) | `gpt-4o-mini`, usage-based, **optional** | **~$0–5** |
| TLS, CI/CD, deploy identity | Let's Encrypt + GitHub Actions + Entra ID, all free tier | **$0.00** |
| | **Total** | **≈ $73/month** |

⚠️ = approximate. Azure renders managed-disk prices dynamically and the P4 figure should be confirmed
in the pricing calculator; the Hostinger mailbox cost was not independently verified. Together they
are under 10% of the total and do not affect any conclusion below.

Notes:
- **The 8 GiB VM is larger than it needs to be.** `clamd` holds the full signature database
  resident, and this was previously stated here as ~1.5–2 GB. **Measured 26 July 2026 it is
  974 MB resident, peaking at 987 MB** — about half the figure this section was built on. The
  whole stack (ClamAV, backend, frontend, Postgres, Caddy, Umami) uses **~2.0 GB of 7.8 GB**.
  B2s (4 GiB) now looks like a comfortable fit rather than a floor, and the VM line is the
  largest single cost in the table above. See the sizing note in §5.6.
- **OpenAI is not a fixed cost.** With no API key the chat endpoint returns 503 and the rest of the
  product is unaffected ([`ChatService.java:108-111`](../backend/src/main/java/com/geneav/scan/service/ChatService.java)).
  It is a marketing/support feature, entirely outside the scan path.
- **A nightly deallocation schedule appears to be configured in the Azure portal** (referenced at
  [`docs/db-access.md:28`](db-access.md)) but is absent from `azure-provision.sh`. It is a real
  compute-cost lever — roughly a third off the VM line — but it is a dev/demo lever, not something
  compatible with a paying customer's uptime expectations. The $73 figure assumes 24/7 operation.

### 5.2 Cost per scan

The critical structural point: **geneav's cost is fixed, not per-scan.** Marginal cost of an
additional scan is effectively zero until capacity forces a second VM. Cost per scan is therefore a
pure function of utilisation.

| Scans/month | Cost per 1,000 scans |
|---|---|
| 1,000 | $73.00 |
| 10,000 | $7.30 |
| 100,000 | **$0.73** |
| 500,000 | $0.15 |
| 1,000,000 | $0.07 |

Egress is negligible by construction: scanning is an *upload* (inbound, free on Azure), and the
response is a few hundred bytes of JSON. Only the marketing site generates meaningful outbound
traffic, and it sits far inside the 100 GB free allowance.

### 5.3 Market comparison

Normalised to **cost per 1,000 scans** at each vendor's most favourable published tier:

| Vendor | Plan | Price/month | Scans/month | $/1,000 scans | Engine |
|---|---|---|---|---|---|
| **geneav** | **Pro** | **$10** | **100,000** | **$0.10** | ClamAV |
| geneav | *(cost basis at 100k)* | $73 | 100,000 | $0.73 | ClamAV |
| attachmentAV | Large | €99 (≈$112.56) | 100,000 | $1.13 | Sophos |
| attachmentAV | XXL | €499 (≈$567.36) | 500,000 | $1.13 | Sophos |
| Cloudmersive | Business Advantage | $199.99 | 100,000 | $2.00 | Multi-engine |
| Cloudmersive | Medium Business Advantage | $999.99 | 500,000 | $2.00 | Multi-engine |
| AttachmentScanner | Startup | $99 | 5,000 | $19.80 | "Standard" |
| AttachmentScanner | Corporate 40k | $849 | 40,000 | $21.23 | Commercial |
| Scanii | Plus | $99 | 5,000 | $19.80 | Multi-engine |
| VirusTotal | — | *not published* | — | — | Multi-engine |
| OPSWAT MetaDefender Cloud | — | *not published* | — | — | Multi-engine |

EUR converted at 1.137 USD/EUR (24 July 2026).

**Reading this table honestly.** Part of the gap is a genuine engine-quality difference — attachmentAV
runs Sophos; Cloudmersive, Scanii and VirusTotal are multi-engine. A buyer paying $2.00 per 1,000 for
multi-engine coverage is not overpaying, they are buying more detection. geneav competes on price and
integration simplicity, not detection depth.

The cleanest apples-to-apples comparison is **AttachmentScanner's Startup tier**, which uses their
"standard scanning engine" (i.e. not a commercial one) at $19.80 per 1,000 scans. geneav delivers
comparable detection at $0.10 per 1,000 — roughly **198x cheaper**.

Two vendors — VirusTotal and OPSWAT — publish no pricing at all and gate production access behind a
sales conversation. For a developer who wants to scan a file this afternoon, that is itself a
differentiator worth naming in marketing copy.

### 5.4 Against self-hosting ClamAV — the strongest case

This is where the argument is genuinely compelling, because it is not about infrastructure.

| | Self-host ClamAV | geneav Pro |
|---|---|---|
| Infrastructure | ~$70/month (the same class of VM) | included |
| Integration build | HTTP wrapper, auth, API keys, quotas, rate limiting, TLS, CI/CD | shipped |
| One-time engineering | 2–4 weeks senior engineer ≈ **$6,000–$24,000** † | $0 |
| Ongoing operations | freshclam, OOM tuning, cert renewal, patching ≈ 2–4 h/month ≈ **$150–$600/month** † | $0 |
| GPL diligence | Your counsel's problem | Documented ([`NOTICE`](../NOTICE)) |
| Monthly cost | **~$220–$670** | **$10** |

† Illustrative, not researched: assumes an $75–$150/hour fully-loaded senior engineer rate. Substitute
your own figures — the conclusion is insensitive to a wide range.

Infrastructure is a wash. The saving is entirely the engineering that never happens, and it is
roughly **20–70x** the subscription price for any team whose volume fits under 100,000 scans/month.

### 5.5 Pricing sanity check — the finding

At $10/month for 100,000 scans, Pro is **$0.10 per 1,000 scans** against a **$0.73 per 1,000** cost
basis at that volume. Read naively, Pro sells below cost.

That reading is wrong, because cost is fixed rather than per-scan. The correct model:

| Pro customers | Revenue | Cost | Gross margin |
|---|---|---|---|
| 8 | $80 | $73 | 9% (break-even) |
| 20 | $200 | $73 | 64% |
| 50 | $500 | $73 | 85% |
| 100 | $1,000 | $73 | 93% |

**Break-even is 8 paying Pro customers**, and margin climbs steeply after that because an additional
customer costs nothing until capacity runs out. The model is sound.

The problem is different, and twofold:

1. **The price is an outlier.** At 11x below attachmentAV for the identical 100,000-scan tier and
   198x below the nearest ClamAV-based product, $10 does not read as competitive — it reads as
   unserious, or as a product that will not be around in a year. Underpricing at this magnitude
   costs credibility as well as revenue.
2. **A single heavy customer is unpriced risk.** One account fully consuming its 100,000-scan
   entitlement uses real capacity for $10, and nobody has measured how much capacity that is (§5.6).

**Recommendation: reprice Pro to $29–$49/month.** At $39, geneav remains ~3x cheaper than
attachmentAV's equivalent tier and ~50x cheaper than AttachmentScanner, while break-even drops from
8 customers to 2. Nothing about the value proposition weakens.

> **Correction, 26 July 2026.** This paragraph originally claimed the "20–70x cheaper than building
> it yourself" argument (§5.4) was *unaffected* by the repricing. That was wrong. The ratio is
> self-hosting cost ÷ Pro price, so quadrupling the denominator moves it directly:
>
> | Basis | Self-host | Pro | Ratio |
> |---|---|---|---|
> | Monthly running cost | $220–$670 | $39 | **5.6–17x** |
> | Year one, incl. the $6,000–$24,000 build | $8,640–$32,040 | $468 | **18–68x** |
>
> "20–70x" survives only as the **year-one** figure, where the one-time integration build is counted —
> which is the saving §5.4 says is the real one. As a claim about monthly cost it overstates by ~4x.
> Marketing copy must say which basis it means; see [`marketing-plan.md`](marketing-plan.md) §2.1.

### 5.6 Capacity ceiling

Throughput is bounded by the global semaphore at 4 concurrent scans
([`application.yml:99`](../backend/src/main/resources/application.yml)). Parametrically, with mean
scan latency *L*:

| Mean latency | Scans/sec | Theoretical scans/month |
|---|---|---|
| 0.2 s | 20 | ~52M |
| 1.0 s | 4 | ~10M |
| 3.0 s | 1.33 | ~3.5M |

These are ceilings on the semaphore alone. **The B2ms is a burstable SKU**: sustained CPU above its
baseline drains burst credits, after which the VM is throttled. In practice CPU credits, not the
semaphore, are likely the binding constraint.

#### Measured, 26 July 2026

The latency assumptions above were guesses, and they were **one to two orders of magnitude too
pessimistic**. Measured on the production VM against `localhost:8080`, random-byte payloads,
sequential requests:

| Payload | First request (cold) | Warm, steady state |
|---|---|---|
| 64 KB | 62 ms | **~16 ms** |
| 256 KB | 58 ms | **~17 ms** |
| 1 MB | 131 ms | **~30 ms** |

Host at the time: 2 vCPU, load average 0.06 — effectively idle.

At ~17 ms a scan, the 4-slot semaphore permits roughly **235 scans/second** in theory, against the
20/second the most optimistic row above assumed. Put in commercial terms, a full 100,000-scan Pro
entitlement is about **28 minutes of total scan time per month**. The semaphore is nowhere near
binding at these latencies, and neither is anything else at current volumes.

**What is still not measured.** These are sequential, single-client samples. They establish per-scan
cost, not saturation behaviour. Four concurrent scans on 2 vCPU will contend, so real throughput
under load will be below 4 × the single-stream rate, and the burst-credit question is untouched.
[`scripts/benchmark-scan.sh`](../scripts/benchmark-scan.sh) exists to answer that; running it
properly requires temporarily disabling the per-IP token bucket
(`GENEAV_RATELIMIT_ENABLED=false`), which needs a backend restart and so has not been done against
production.

The honest summary has moved from *"the number of Pro customers one box supports is unknown"* to
*"a single scan costs ~17 ms and the headroom is very large, but the saturation curve is still
unplotted."*

Two further ceilings worth noting:
- **Deployment.** `scripts/deploy-vm.sh` inlines the repository into an `az vm run-command` call and
  fails above 200,000 bytes, with an explicit message that the repo has outgrown inline delivery
  ([`deploy-vm.sh:120-124`](../scripts/deploy-vm.sh)). A container registry becomes necessary before
  long.
- **Redundancy.** Deploys rebuild images on the production VM itself, so builds contend with live
  traffic and there is a rebuild window with no failover.

---

## 6. Risks and honest limitations

Grouped by what a buyer would ask about. Everything here is verifiable from the repository.

### Detection
- **Signature-only.** No zero-day, polymorphic or behavioural detection. Already stated publicly
  ([`frontend/app/page.tsx:116`](../frontend/app/page.tsx)) and even baked into the chat assistant's
  system prompt ([`ChatService.java:56-58`](../backend/src/main/java/com/geneav/scan/service/ChatService.java)).
- **Single engine.** No second opinion. The `ScanEngine` interface exists as a seam for additional
  engines, but only one implementation exists.
- **The content-type allow-list is not a security control.** It checks the *client-declared*
  multipart content type, `application/octet-stream` is on the allow-list, and a `null` content type
  skips the check entirely ([`ScanController.java:80`](../backend/src/main/java/com/geneav/scan/controller/ScanController.java)).
  Any file can be submitted by declaring the right type. This is fine — ClamAV scans the bytes
  regardless — but it must not be described to customers as file-type enforcement.

### Product gaps
- **No billing.** No Stripe, no checkout. Pro is a `mailto:` link. Quota enforcement is real; the
  payment rail is not.
- **No scan history or audit trail.** No table exists — the only record of a scan is an application
  log line ([`ScanController.java:89,93`](../backend/src/main/java/com/geneav/scan/controller/ScanController.java)).
  Any compliance-driven buyer will ask for this, and the answer today is no.
- **No Terms of Service.** A privacy policy exists ([`frontend/app/privacy/page.tsx`](../frontend/app/privacy/page.tsx));
  `/legal` covers licensing attribution only. Charging money without terms is not defensible.
- **No organisations, teams or roles.** Multi-tenancy is per-account row scoping; the only authority
  granted is `ROLE_USER`. Blocks any team-sized deal.
- **Google sign-in is a disabled placeholder.**

### Operational
- **Single VM, single region, no autoscaling, no failover.** One host is one outage.
- **Backups are manual only.** `docs/db-access.md:101-104` documents an ad-hoc `pg_dump`; there is no
  scheduled backup or snapshot policy. The privacy policy's reference to records persisting "briefly
  in backups" is not backed by configured automation — worth reconciling.
- ~~**freshclam reload memory spike is unmitigated.**~~ **Fixed 26 July 2026.**
  `CLAMD_CONF_ConcurrentDatabaseReload=no` is now set in [`docker-compose.yml`](../docker-compose.yml),
  so a signature reload no longer loads the new database alongside the old one. The trade is a brief
  pause in scanning during a reload instead of a transient doubling of memory — the right way round
  on a single box with no failover. Steady-state resident is **974 MB measured**, not the ~1.5–2 GB
  previously quoted here.
- **No monitoring.** Azure Monitor and uptime checks are planned in `deploy-plan.md` but not
  configured. Actuator exposes `health,info` only.
- **Host ports remain published in production.** Compose merges `ports:` lists, so 3000/8080/3310
  stay bound on the VM host — as does Postgres on 5432 ([`docker-compose.yml:25-26`](../docker-compose.yml)).
  The Azure NSG is the only boundary. Known and documented
  ([`docker-compose.prod.yml:65-78`](../docker-compose.prod.yml)), but it is a single-layer defence.
- **Containers run as root**, with no JVM heap cap and no digest-pinned base images — all called for
  in `deploy-plan.md` but not implemented.

### Commercial
- **No customers, no revenue, no reference deployments.**
- **Underpriced by roughly an order of magnitude** (§5.5).
- **Capacity per box unknown** (§5.6).
- **Low moat.** The integration layer is replicable. The defensible position is execution speed,
  price and developer experience — not technology.

---

## 7. Recommendation and next steps

geneav is a real, working, honestly-scoped product with a sound cost structure. It is not yet
sellable. In priority order, as candidate issues under epic GN-3:

**Blockers before charging anyone money**

| # | Item | Why |
|---|---|---|
| 1 | ~~Write a Terms of Service~~ — **done** | Shipped at `/terms`, alongside a `/security` page. Liability cap and indemnity still want a lawyer's review. |
| 2 | ~~Reprice Pro~~ — **done at $39** | §5.5. Shipped with Starter/Scale tiers and a larger free allowance; see the update note at the top. |
| 3 | Wire up Stripe checkout | Converts the existing quota machinery into revenue. Everything else is already built. |
| 4 | Benchmark sustained throughput | §5.6. Determines what a 100,000-scan entitlement actually commits to. |

**Before the first serious customer**

| # | Item | Why |
|---|---|---|
| 5 | Scan history / audit trail | The most likely first feature request; needed for any compliance story. |
| 6 | Scheduled backups + uptime monitoring | Currently manual and absent respectively. Reconcile with the privacy policy's backup claim. |
| 7 | Container hardening | Non-root, JVM heap cap, digest pinning, unpublish host ports. Already specified in `deploy-plan.md`. |

**Growth**

| # | Item | Why |
|---|---|---|
| 8 | Organisations / team accounts | Unblocks deals larger than one developer. |
| 9 | Container registry for deploys | `deploy-vm.sh` inline delivery has a hard ceiling. |
| 10 | Evaluate a second engine behind `ScanEngine` | The one change that would move geneav off "ClamAV with a nice API" — and justify pricing near the market. |

**Positioning guidance for all customer-facing material**

- Lead with integration and operations, never with detection capability.
- State the ClamAV dependency openly and early. It is verifiable in an afternoon; volunteering it
  buys credibility that a discovered omission would destroy.
- Anchor the cost argument on **self-hosting** (20–60x), not on undercutting competitors — the
  competitor gap partly reflects genuinely better engines.
- Name the "no sales call required" advantage over VirusTotal and OPSWAT explicitly.

---

## 8. Technical appendix

### 8.1 Stack

| Layer | Detail |
|---|---|
| Backend | Spring Boot 3.3.2, Java 17, Maven, springdoc-openapi 2.6.0 |
| Frontend | Next.js 14.2.35 (App Router), React 18.3.1, TypeScript 5.5.3, Tailwind 3.4 |
| Database | PostgreSQL 16-alpine, Flyway migrations V1–V4, Hibernate `ddl-auto: validate` |
| Engine | `clamav/clamav:1.4`, healthcheck with 120 s start period |
| Proxy | Caddy 2 (custom build with `caddy-ratelimit`), automatic Let's Encrypt |
| Host | Single Azure VM `geneav-vm` / `GENEAV-RG`, Standard_B2ms, East US |
| CI/CD | GitHub Actions — `mvn test` + `next build`, deploy on push to `main` via Azure OIDC and `az vm run-command` (no inbound SSH), with automatic rollback |

### 8.2 API surface

Base path `/api/v1`:

| Method | Path | Auth |
|---|---|---|
| POST | `/scan` (multipart `file`) | optional key |
| GET | `/health` | none |
| POST | `/chat` | none |
| GET | `/chat/health` | none |
| POST | `/signup` | none |
| GET | `/keys` | session or key |
| POST | `/keys` | session or key |
| DELETE | `/keys/{id}` | session or key |
| GET | `/usage` | session or key |
| POST | `/auth/signup` | none |
| POST | `/auth/login` | none |
| POST | `/auth/forgot-password` | none |
| POST | `/auth/reset-password` | reset token |
| POST | `/auth/logout` | session |
| GET | `/auth/me` | session or key |

Plus `/docs` (Swagger UI), `/api-docs` (OpenAPI JSON), and actuator `health,info`.

### 8.3 Plan limits as configured

[`application.yml:101-114`](../backend/src/main/resources/application.yml):

| Plan | Monthly scan quota | Rate limit | Burst |
|---|---|---|---|
| free | 100 | 10/min | 10 |
| pro | 100,000 | 120/min | 60 |

Anonymous scanning is permitted and unmetered, protected by IP rate limiting only (10/min).

### 8.4 Security posture summary

- BCrypt passwords; policy ≥12 characters, 3 of 4 character classes, common-password and
  email-substring rejection ([`PasswordPolicy.java`](../backend/src/main/java/com/geneav/scan/account/PasswordPolicy.java)).
- API keys stored as SHA-256 hashes with prefix and last-four retained for display.
- Session fixation rotation on login; `credentials_changed_at` invalidates all sessions on password
  change.
- Password reset: single-use hashed token, 10-minute TTL, 60-second cooldown, 24-hour sweep;
  `/forgot-password` always returns 202 to prevent account enumeration.
- CSRF disabled deliberately in favour of `SameSite=Lax` HttpOnly session cookies.
- Files are streamed to the engine and not persisted ([`frontend/app/privacy/page.tsx:51-61`](../frontend/app/privacy/page.tsx)).

### 8.5 Licensing

geneav application code is proprietary, all rights reserved ([`LICENSE`](../LICENSE)). ClamAV is
GPLv2 and is integrated over a socket without linking `libclamav`, which is the boundary ClamAV
documents for commercial integration ([`NOTICE:25-54`](../NOTICE)). Signature databases are
downloaded at runtime by freshclam and are not redistributed.

---

## Sources

Pricing retrieved 24–25 July 2026:

- [Standard_B2ms specs and pricing — CloudPrice](https://cloudprice.net/vm/Standard_B2ms) — $0.0832/hr, $60.74/month, East US Linux PAYG (page updated 22 July 2026)
- [Pricing — Virtual Machine IP Address Options, Microsoft Azure](https://azure.microsoft.com/en-us/pricing/details/ip-addresses/) — Standard static public IP $0.005/hr
- [Pricing — Managed Disks, Microsoft Azure](https://azure.microsoft.com/en-us/pricing/details/managed-disks/) — Premium SSD P4 tier (price rendered dynamically; confirm in the Azure calculator)
- [Azure Bandwidth & Egress Pricing Explained (2026) — EgressCost](https://egresscost.com/azure/) — 100 GB/month free egress allowance; $0.087/GB thereafter in Zone 1
- [Virus and Malware Scan API Pricing — attachmentAV](https://attachmentav.com/pricing/virus-malware-scan-api/) — Large €99/month for 100,000 requests; Sophos engine
- [Plans and Pricing — AttachmentScanner](https://www.attachmentscanner.com/plans-and-pricing) — Startup $99/month for 5,000 scans; Corporate 40k $849/month for 40,000
- [Small Business Pricing — Cloudmersive](https://cloudmersive.com/pricing-small-business) — Business Advantage $199.99/month for 100,000 API calls
- [Pricing — Scanii](https://scanii.com/pricing) — Plus $99/month for 5,000 files
- [Bitdefender GravityZone Pricing 2026 — CostBench](https://costbench.com/software/endpoint-security/bitdefender/) — Business Security $74/device/year up to 100 endpoints
- [VirusTotal API Pricing 2026 — sed.sh](https://sed.sh/compare/virustotal) — no public pricing; enterprise quote required
- [Cloud Threat Intelligence — MetaDefender Cloud, OPSWAT](https://www.opswat.com/products/metadefender/cloud) — no public per-scan pricing
- [Euro to US Dollar exchange rate — Trading Economics](https://tradingeconomics.com/euro-area/currency) — 1.137 USD/EUR, 24 July 2026
