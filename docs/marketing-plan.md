# geneav — Marketing Plan

**Status:** Draft v1
**Date:** 26 July 2026
**Related:** [`business-case.md`](business-case.md) · [`payment-gateway-plan.md`](payment-gateway-plan.md) · [`compliance-roadmap.md`](compliance-roadmap.md)

> This plan covers distribution only. It assumes the product as shipped today and does not
> propose product changes beyond the five bounded tasks in §2.

---

## 1. Context

geneav is live at geneav.com, technically complete for its core job, and has **zero customers, zero
revenue, and zero measurement**. [`business-case.md`](business-case.md) is unambiguous about the
reason: *"The defensible position is execution speed, price and developer experience — not
technology"* (§6), and [`compliance-roadmap.md`](compliance-roadmap.md) §5 puts it plainly —
*"neither certificate is the current bottleneck — **distribution is**."*

This plan addresses distribution, under three constraints fixed before it was written:

1. **There is no checkout.** Every paid CTA is a `mailto:admin@geneav.com`
   ([`page.tsx:99-134`](../frontend/app/page.tsx)). Phase 1 therefore optimises for **free-tier
   signups and audience**, not MRR, and paid demand generation is held until billing ships.
2. **~5 hours/week, near-zero spend.** No paid acquisition. Content, SEO, developer communities and
   directories — channels that compound rather than channels you rent.
3. **Global developers, USD.** Matches current pricing, site copy, and the stated assumption in
   [`payment-gateway-plan.md`](payment-gateway-plan.md).

One house rule governs everything below, inherited from [`page.tsx:21-24`](../frontend/app/page.tsx):

> *"Every claim on this page must be true of the current build — no invented customers, testimonials,
> user counts, or uptime figures."*

**This plan extends that rule to every blog post, forum comment, and directory listing.** For a
product whose entire pitch is "we tell you exactly what we are," one inflated claim is
disproportionately expensive.

---

## 2. Positioning — the message house

[`business-case.md`](business-case.md) §7 already wrote the positioning guidance. This plan adopts
it verbatim as non-negotiable:

> - Lead with integration and operations, **never** with detection capability.
> - State the ClamAV dependency openly and early.
> - Anchor the cost argument on **self-hosting** (18–68x in year one; 6–17x ongoing — always say
>   which), not on undercutting competitors.
> - Name the "no sales call required" advantage over VirusTotal and OPSWAT explicitly.

**The one-line pitch:**

> Your upload endpoint has no antivirus. geneav is one HTTPS call that fixes that — without you
> operating a ClamAV daemon.

**The wedge**, stated as a problem the buyer already has ([`business-case.md`](business-case.md) §3):

> A Lambda handling a file upload cannot run GravityZone. Serverless and containerised upload
> pipelines are an AV coverage gap *by construction*.

This is the most commercially valuable sentence in the repository. It names a gap the buyer can
verify in their own architecture in thirty seconds, and endpoint-AV vendors have no answer for it.

### 2.1 Three proof pillars

Each maps to something independently verifiable.

| Pillar | The claim | Why it survives scrutiny |
|---|---|---|
| **Cheaper than building it** | Year one: ~$8,640–32,040 self-hosted vs $468 Pro — **18–68x**. Ongoing: ~$220–670/mo vs $39 — **6–17x** | The saving is the 2–4 weeks of integration engineering that never happens (§5.4). Infrastructure is a wash, and conceding that *increases* credibility. **Never quote 20–70x against the monthly figure** — that ratio was computed when Pro was $10 and overstates by ~4x today ([`business-case.md`](business-case.md) §5.5 correction) |
| **No sales call, no card** | Scan a file in the next five minutes | VirusTotal and OPSWAT publish no pricing at all (§5.3). Free tier is 500 scans/month, no card |
| **Honest by construction** | Files never stored · no tracking cookies · a public [`/security`](../frontend/app/security/page.tsx) page with a "what we have not built yet" section | All verifiable from the code. The `/security` page is a conversion asset, not a liability |

### 2.2 What we never say

That detection is better than ClamAV's. That anyone is using it. That uptime is any particular
number. That the content-type allow-list is a security control — it checks a *client-declared* type
and `application/octet-stream` is on the list ([`business-case.md`](business-case.md) §6). Or that
geneav replaces endpoint AV.

### 2.3 The honest-gaps play

[`/security`](../frontend/app/security/page.tsx) publicly lists what does *not* exist: no SOC 2, no
ISO 27001, no third-party pentest, single region. Counter-intuitively this is an asset for the
developer-led SMB buyer, who per [`compliance-roadmap.md`](compliance-roadmap.md) §5 *"wants a
security page, a DPA, and a fast, honest questionnaire response"* — not badges.

It is also the cheapest differentiator available, because competitors structurally cannot copy it
without alarming their own enterprise pipeline.

---

## 3. Phase 0 — Fix the leaks (Week 1, ~4 hours)

Do not send a single visitor anywhere until these are done.

| # | Task | Where | Why |
|---|---|---|---|
| 1 | **Fix the free-tier copy bug** | [`page.tsx:189`](../frontend/app/page.tsx) reads *"Free tier — 100 scans a month"*; the pricing card at [`page.tsx:97`](../frontend/app/page.tsx) and [`application.yml:134`](../backend/src/main/resources/application.yml) both say **500** | The strongest acquisition lever on the page is understated by 5x, directly under the primary CTA |
| 2 | **Install cookieless analytics** | New container in [`docker-compose.prod.yml`](../docker-compose.prod.yml); route via [`caddy/Caddyfile`](../caddy/Caddyfile) | There is **no measurement of any kind** today. Every recommendation below is unfalsifiable without it |
| 3 | **Capture signup attribution** | `POST /api/v1/auth/signup` — persist referrer + UTM onto the account row (nullable columns, new Flyway migration) | Otherwise you will know traffic rose and never know which channel caused a signup |
| 4 | **Verify geneav.com in Google Search Console** | External, no code | The SEO groundwork in [`layout.tsx`](../frontend/app/layout.tsx) and [`sitemap.ts`](../frontend/app/sitemap.ts) is already strong; Search Console tells you which keywords actually rank |
| 5 | **Add a `/blog` route** | New `frontend/app/blog/` segment reusing the existing page shell and [`theme.ts`](../frontend/app/theme.ts) tokens; register in [`sitemap.ts`](../frontend/app/sitemap.ts) | Phase 1 is entirely content, and there is nowhere to put it |

### 3.1 Analytics recommendation

**Self-hosted Umami**, as another container on the existing VM, sharing the running PostgreSQL 16
instance.

- **It preserves the no-consent-banner claim.** Umami is cookieless.
  [`compliance-roadmap.md`](compliance-roadmap.md) §2 calls out *"No tracking cookies… No consent
  banner is required. Most competitors cannot say this."* A Google Analytics tag would destroy a
  stated differentiator to gain nothing.
- **Zero marginal cost.** Postgres is already running.
- **Caveat — largely resolved 26 July 2026.** This originally warned that `clamd` holds ~1.5–2 GB
  resident on an 8 GiB box that had never been benchmarked. Measurement since: `clamd` is **974 MB**,
  and the whole stack **with Umami running** is **~2.0 GB of 7.8 GB**. Umami itself is ~250 MB. The
  memory risk was roughly half what this section assumed. Throughput is still the open question
  ([`business-case.md`](business-case.md) §5.6).

**Measure from day one:** unique visitors by source, `/developers` visits (highest-intent page),
anonymous scans run through [`ScanForm`](../frontend/app/components/ScanForm.tsx), signups, and
signups that go on to create an API key. That last one is the real activation event.

---

## 4. Phase 1 — Build the audience (Weeks 2–12, ~5 hrs/week)

**Goal: 100 free-tier accounts that have created at least one API key, plus one piece of content
that ranks.** Not revenue — there is nothing to charge with yet.

### 4.1 The content engine (~3 of the 5 weekly hours)

Content is the only channel that compounds at zero spend, and geneav has an unusual advantage: this
repository **already contains original research** — a competitive pricing table, a self-hosting cost
model, and hard-won ClamAV operational knowledge. Most solo founders have to invent content. Here it
only has to be *extracted*.

Ranked by expected return. Roughly one substantial piece per fortnight, with faster integration
guides in between.

| # | Piece | Why it wins | Source |
|---|---|---|---|
| 1 | **"What it actually costs to self-host ClamAV"** | The flagship. Original research, high commercial intent, and the strongest argument available. Publish the full model — infra, the 2–4 week integration build, the 2–4 h/month ops — and leave the $39 comparison as a footnote, not a pitch | §5.1, §5.4 |
| 2 | **"Your file upload endpoint has no antivirus"** | The wedge as a standalone argument. Reaches the buyer *before* they know the category exists, which is where the cheapest customers are | §3 |
| 3 | **Integration guides, one per stack** | Highest-converting format for a developer API. Real, runnable code. Start with Node/Express, Python/FastAPI, a Next.js route handler, S3 pre-signed upload, Django | `/developers`, Postman collection |
| 4 | **"Running ClamAV in production: the parts nobody warns you about"** | Signature DB memory footprint, freshclam reload spikes, `clamd` having **no rate limiting whatsoever**, and why the 4-concurrent semaphore exists | §4.1 |
| 5 | **"Talking to clamd over INSTREAM"** | Deep technical credibility — a raw-socket INSTREAM client written without a library ([`ClamAvScanEngine.java`](../backend/src/main/java/com/geneav/scan/service/ClamAvScanEngine.java)). Almost nobody blogs this | §2.1 |
| 6 | **"Malware scanning APIs compared, honestly"** | Publish the normalised $/1,000-scan table *including* the line conceding that multi-engine competitors genuinely buy more detection. The concession is what makes it trustworthy, and it will outrank the sanitised comparison pages competitors write | §5.3 |
| 7 | **"The GPL question when you build on ClamAV"** | Narrow, almost zero competition, and it reaches exactly the technical-lead persona who has to answer it | [`NOTICE`](../NOTICE), §4.2 |

**Format rule.** Each post closes with one honest, low-pressure line linking to `/developers` — not
a hard CTA. The audience for #1, #4 and #5 is people currently self-hosting ClamAV; being the most
useful page they found that day converts better than selling to them.

**SEO note.** The keyword array in [`layout.tsx`](../frontend/app/layout.tsx) (`scan PDF for
malware`, `ClamAV REST API`, `self-hosted antivirus`, …) is well chosen. Pieces #1 and #4 target the
`self-hosted antivirus` intent, where the searcher has a live problem and no vendor is bidding.

### 4.2 Distribution (~1.5 hrs/week)

Publishing without distributing is the classic solo-founder failure. Budget for it explicitly.

| Channel | Play | Cadence |
|---|---|---|
| **Hacker News** | One "Show HN: geneav — a document malware scanning API (ClamAV behind a REST endpoint)". **After Phase 0, not before.** Lead the first comment with the ClamAV dependency stated openly — that framing is what HN rewards, and an omission is what it punishes | Once; pieces #1 and #5 can be posted separately later |
| **Reddit** | r/webdev, r/devops, r/programming, r/selfhosted. Post the *content*, not the product. r/selfhosted in particular will reward an honest self-hosting cost model and punish a pitch | 1 thread per published piece |
| **dev.to / Hashnode / Lobsters** | Cross-post integration guides with a canonical link back to `/blog` | With each guide |
| **API directories** | RapidAPI, APIs.guru, the `public-apis` GitHub list, ProductHunt, StackShare, AlternativeTo, relevant `awesome-*` lists. Confirm each still accepts submissions before spending the time | One batch, ~2 hrs total |
| **GitHub** | Publish the Postman collection plus a small open-source client (Node + Python). Free, permanent, discoverable, and it turns integration into copy-paste | One-off, then maintain |
| **Targeted answers** | Stack Overflow and GitHub issues where someone asks how to scan uploads. Answer the question properly first; mention geneav only where it genuinely fits | ~20 min/week |

### 4.3 Weekly cadence (5 hours)

| Block | Hours | Activity |
|---|---|---|
| Write | 3.0 | Draft or ship the current piece |
| Distribute | 1.0 | Post, cross-post, reply to every comment |
| Measure & respond | 0.5 | Analytics review; answer inbound `mailto:` properly |
| Opportunistic | 0.5 | Community answers, directory submissions |

**Treat every `mailto:admin@geneav.com` reply as research, not just a sale.** Until billing ships
those emails are the only qualitative signal available.
[`business-case.md`](business-case.md) §7 predicts the first feature request will be **scan history /
audit trail**; a handful of emails either confirms that or redirects the roadmap, which is worth more
than the deal.

---

## 5. Phase 2 — Convert to revenue (gated on checkout)

Trigger: the `PaymentProvider` work in [`payment-gateway-plan.md`](payment-gateway-plan.md) lands and
the three `mailto:` CTAs become real buttons.

| # | Action | Note |
|---|---|---|
| 1 | Replace the `mailto:` CTAs | Already scoped in the payment gateway plan |
| 2 | Email every Phase 1 free-tier account | The warmest possible list, assembled precisely while you could not charge. This is the entire reason Phase 1 optimises for signups |
| 3 | In-dashboard upgrade prompt at 80% quota | The usage meter in [`Dashboard.tsx`](../frontend/app/components/Dashboard.tsx) already exists. An account approaching 500 scans is the best-qualified buying signal you will get |
| 4 | Publish a customer-facing DPA | [`compliance-roadmap.md`](compliance-roadmap.md) §3.4. Mandatory under Art. 28 to serve any business EU customer. **⚖️ counsel** |
| 5 | *Then* consider paid search | Only once cost-per-paid-conversion is measurable |

**Two sequencing warnings worth stating plainly.**

- Before marketing pushes volume at a 100,000-scan Pro entitlement, run the throughput benchmark
  ([`business-case.md`](business-case.md) §5.6): *"the number of Pro customers one box supports is
  unknown."* Marketing that succeeds against an unmeasured capacity ceiling is a worse outcome than
  marketing that fails.
- **Do not market the Scale tier at all yet.**
  [`application.yml:129-131`](../backend/src/main/resources/application.yml) carries its own warning:
  Scale's 300 req/min is 5/sec, above the global `scan-max-concurrent` of 4, so *"a single scale
  account at full rate can therefore queue on the global semaphore; raise the concurrency cap (and
  the VM) before selling that tier hard."* It can stay on the pricing page as an anchor; it should
  not be a campaign target.

---

## 6. Metrics and targets

Modest by design — realistic for 5 hrs/week at zero spend, and every one checkable rather than
aspirational.

| Metric | Baseline | Week 12 target |
|---|---|---|
| Monthly unique visitors | Unknown (no analytics) | 1,500 |
| Free-tier signups | 0 known | 100 |
| **Signups that create an API key** (activation) | 0 | 60 |
| Published content pieces | 0 | 8 |
| Keywords ranking on page 1 | Unknown | 3 |
| Inbound `mailto:` enquiries | 0 | 10 |
| Referring domains | Unknown | 25 |

**Activation is the number that matters.** A signup that never creates a key learned nothing about
the product and will not convert when billing ships.

---

## 7. Deliberate non-goals

Stating these stops 5 hrs/week being spread across twelve channels at zero depth each.

- **No paid ads** until cost-per-paid-conversion is measurable.
- **No SOC 2 or ISO 27001.** [`compliance-roadmap.md`](compliance-roadmap.md) §5: *"certification
  converts deals, it does not create them."* Buy the badge when a named deal is blocked on it.
- **No enterprise or outbound sales.** No organisations, teams, roles or SSO exist;
  [`business-case.md`](business-case.md) §6 says this *"blocks any team-sized deal."* Selling into it
  wastes the meeting.
- **No EU-targeted campaigns yet.** Single region (Azure East US), no DPA. A West Europe VM
  (~$73/month) unlocks this cheaply and is the highest-leverage market expansion available — but it
  is a prerequisite, not a campaign.
- **No social-media presence building.** Low return per hour relative to content that ranks.
- **No testimonials, logos or user counts.** There are none. Fabricating them destroys the one asset
  that is genuinely hard to copy.

---

## 8. Honest risks

| Risk | Assessment |
|---|---|
| **"ClamAV with a nice API" is a weak moat** | Real, and [`business-case.md`](business-case.md) §6 says so. Pieces #4 and #5 are the mitigation: they convert operational knowledge into public evidence that the ops layer took real work. That is a delay, not a defence |
| **Demand arrives before checkout does** | The reason Phase 2 is gated. Mitigation: capture emails and accept manual invoicing for the first few |
| **HN/Reddit is a spike, not a channel** | Expect one spike and near-zero residual. SEO compounds; social seeds it. Do not plan around the spike |
| **A single VM meets a traffic spike** | No failover, no monitoring, no benchmark. A successful Show HN is a genuine availability risk. Set up uptime monitoring ([`compliance-roadmap.md`](compliance-roadmap.md) §6, item 7) *before* posting |
| **Content velocity slips** | 5 hrs/week is a real constraint. Two pieces a month is the honest plan; four thin pieces is worse than two good ones |

---

## 9. Verification

How to confirm Phase 0 worked, end to end:

1. **Copy fix** — load geneav.com and confirm the line under the hero CTA reads 500, matching both
   the pricing card and [`application.yml`](../backend/src/main/resources/application.yml).
2. **Analytics** — run the stack locally, visit a page, confirm the hit registers; repeat on
   production from a different device. Then check `document.cookie` in DevTools — **it must still
   show only the session cookie**, or the no-consent-banner claim in §2.1 is broken.
3. **Attribution** — sign up through a URL carrying `?utm_source=test`, then query the accounts table
   (see [`db-access.md`](db-access.md)) and confirm the value persisted.
4. **Blog route** — `curl https://geneav.com/sitemap.xml` and confirm blog URLs appear; paste a post
   URL into Slack to confirm the OG card renders.
5. **Search Console** — confirm the sitemap is submitted and pages are indexing (allow a few days).

**Capture the baseline before publishing anything.** Every target in §6 is measured against week-1
analytics numbers that do not exist yet.
