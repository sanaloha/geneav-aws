# geneav — GDPR and ISO 27001 Roadmap

**Status:** Draft v1
**Date:** 25 July 2026
**Related:** [`business-case.md`](business-case.md) · [GN-12](https://santoshsscet.atlassian.net/browse/GN-12)

> This is an engineering and planning document, not legal advice. The items marked
> **⚖️ counsel** need a qualified data-protection lawyer before they go live — in particular the
> DPA, the standard contractual clauses, and the Article 27 determination.

---

## 1. These are two different problems

They get bundled together in sales conversations, but they behave nothing alike:

| | GDPR | ISO 27001 |
|---|---|---|
| Nature | **Law.** Obligation, not a choice | **Voluntary certification** |
| Trigger | Any EU/EEA data subject uses geneav | A customer contractually demands it |
| Cost | Mostly time; ~€600–2,400/yr for an EU rep | **$15,000–50,000** in year one |
| Timeline | Weeks | 4–9 months, and it cannot be meaningfully rushed |
| What it proves | You handle personal data lawfully | You operate a management system |
| Consequence of skipping | Regulatory exposure, fines | You lose deals that require it |

The practical implication: **GDPR is already overdue the moment an EU user signs up. ISO 27001 should
wait until a deal pays for it.** Section 6 sets out the sequence.

---

## 2. Where geneav already stands well

Worth stating first, because it is genuinely unusual and it is a sales asset:

- **Scanned files are never persisted.** They are streamed to the engine and discarded
  ([`ClamAvScanEngine.java:44-68`](../backend/src/main/java/com/geneav/scan/service/ClamAvScanEngine.java),
  [`frontend/app/privacy/page.tsx:51-61`](../frontend/app/privacy/page.tsx)). This removes the single
  largest GDPR surface a scanning service normally carries — no retention schedule for customer
  content, no deletion pipeline, no backup exposure for it.
- **No tracking cookies.** One HttpOnly, `SameSite=Lax` session cookie and nothing else
  ([`application.yml:3-8`](../backend/src/main/resources/application.yml)). **No consent banner is
  required.** Most competitors cannot say this.
- **Credentials are handled properly already.** BCrypt passwords, API keys stored only as SHA-256
  hashes, single-use hashed reset tokens with a 10-minute TTL.
- **A subprocessor list already exists** and is public at [`/security`](../frontend/app/security/page.tsx).
- **No advertising, profiling, or automated decision-making**, so no DPIA is likely required — though
  the reasoning should be written down rather than assumed (§3.4).

---

## 3. GDPR

### 3.1 What role geneav plays

geneav is **both**, and the distinction drives every obligation below:

| Data | Role | Basis |
|---|---|---|
| Account email, password hash, plan, usage counters | **Controller** | Contract (Art. 6(1)(b)) |
| Server logs, rate-limit and abuse records | **Controller** | Legitimate interests (Art. 6(1)(f)) |
| Files submitted for scanning | **Processor** — the customer is the controller | Customer's own basis |
| Marketplace subscription + Entra sign-in identity | **Controller** | Contract (Art. 6(1)(b)) |

**Marketplace billing does not change geneav's role for scanned content.** Added 27 July 2026:
Microsoft is merchant of record for the *transaction* only — it takes payment details, applies
tax, and pays out, so geneav never sees a card number. For everything this table already covered,
including the files customers submit, geneav remains exactly what it was. **The Article 28 DPA
obligation is therefore unchanged** — do not let "Microsoft handles billing" be mistaken for
"Microsoft handles our GDPR position". What is genuinely new is a small amount of additional
controller data: subscription id, plan, purchaser and beneficiary email, and Entra tenant id
(`V6__marketplace.sql`), plus name and email from Entra when a user signs in with Microsoft.

Being a processor for the scanned content is what makes a DPA mandatory: Article 28 requires a written
contract between controller and processor. You cannot lawfully serve a business EU customer without one.

### 3.2 Three real gaps

**Gap 1 — filenames in logs were personal data. ✅ Fixed.**

Every scan used to log the original filename, e.g. `file='John_Smith_medical_report_2026.pdf'` — personal
data, sometimes special-category data under Article 9, written to a log with no configured retention
(there is still no logback configuration anywhere in the backend).

Scan logs now record only the file **extension** and **size**
([`ScanController.logExtension`](../backend/src/main/java/com/geneav/scan/controller/ScanController.java)),
which preserves the diagnostic value ("are PDFs failing?") while removing the personal data. Anything
that does not look like a plain extension collapses to a constant, so a crafted filename cannot write
arbitrary text into a log line either. The privacy policy and `/security` were updated to match — they
had described the old behaviour.

The response still echoes the caller their own filename; that is their data coming back to them and does
not persist.

**Gap 2 — no self-serve account deletion.** The API exposes `DELETE /keys/{id}` but no account
deletion endpoint (see the API surface in [`business-case.md`](business-case.md) §8.2). Article 17
erasure currently depends on emailing `admin@geneav.com` and a manual database operation. That is
defensible at current scale but does not survive a data subject who insists, and it will not pass a
questionnaire.

**Gap 3 — the backup claim is unverifiable.** The privacy policy says records "may persist briefly in
backups before they age out" ([`privacy/page.tsx:102-111`](../frontend/app/privacy/page.tsx)), but
backups are a manual, ad-hoc `pg_dump` with no schedule and no expiry
([`docs/db-access.md:101-104`](db-access.md)). Either automate backups with a defined retention window
and make the statement true, or remove the sentence. Today the policy describes a process that does not
exist. *(Flagged previously in [`business-case.md`](business-case.md) §6.)*

### 3.3 International transfers

Processing happens in **Azure East US** ([`azure-provision.sh:10-15`](../azure-provision.sh)). An EU
customer sending files to geneav is exporting personal data to the United States, so a transfer
mechanism is required. The practical route is to annex **standard contractual clauses** to your DPA and
document that Microsoft Azure is certified under the EU–US Data Privacy Framework. **⚖️ counsel.**

Note the commercial angle: a **West Europe VM (~$73/month, the same as today's)** would let you offer EU
data residency and sidestep the transfer question for EU customers entirely. That was already flagged as
a market-access gap in [`business-case.md`](business-case.md) §5 — GDPR strengthens the case.

### 3.4 Documentation to produce

| # | Item | Notes |
|---|---|---|
| 1 | **DPA template** with SCCs annexed | Mandatory under Art. 28 for business customers. **⚖️ counsel** |
| 2 | **Article 30 records of processing** | Internal register, both as controller and processor. A single table is fine at this scale |
| 3 | **Article 27 EU representative** | Likely required: established outside the EU, offering services to EU data subjects. Confirm the "occasional processing" exemption does not apply. **⚖️ counsel** |
| 4 | **Breach notification runbook** | 72 hours to the supervisory authority. Must exist *before* it is needed |
| 5 | **Retention schedule** | Accounts, logs, usage counters, backups. Currently undefined for logs |
| 6 | **DPIA screening note** | Short written justification for why a full DPIA is not required |
| 7 | **Data subject rights procedure** | Access, rectification, erasure, portability — who does what, within what window |
| 8 | **Add Microsoft to the subprocessor list** — ✅ done | Marketplace (billing) and Entra ID (sign-in) are published on [`/security`](../frontend/app/security/page.tsx) and `/privacy`. Note for questionnaires: Microsoft is merchant of record for the transaction, **not** a processor of scanned files |
| 9 | **Erasure now spans a second system** | Art. 17 deletion must consider the marketplace subscription too. Cancelling in Azure is the *customer's* action and does not delete their geneav account; deleting the geneav account cascades `marketplace_subscription` but does not cancel their Microsoft billing. Say which is which in the rights procedure, or a user who asks for erasure keeps getting invoiced. **⚖️ counsel** |

### 3.5 Cost and timeline

Weeks, not months. Roughly **€600–2,400/year** for an Article 27 representative (providers range from
about €19 to €200/month), plus counsel time for the DPA and SCCs. Everything else is your own effort.

---

## 4. ISO 27001

### 4.1 What it actually certifies

ISO/IEC 27001:2022 certifies an **Information Security Management System** — a documented, operating
process for managing security risk. It does not certify that the product is secure. An organisation
with weak controls can certify if it has honestly identified and accepted those risks.

Required: a defined scope, a risk assessment methodology, a risk treatment plan, a **Statement of
Applicability** justifying inclusion or exclusion of each of Annex A's 93 controls, a policy set, an
internal audit, and a management review. Then a **Stage 1** documentation audit and a **Stage 2**
implementation audit by an accredited certification body.

The constraint people underestimate: auditors want **evidence the ISMS has been operating**, typically
three months of records minimum. You cannot compress that by spending more.

### 4.2 Cost and timeline

| Component | Range (year one) |
|---|---|
| Certification body (Stage 1 + Stage 2) | $5,000–17,000 for a small scope |
| Compliance automation platform | $4,000–30,000/yr (Sprinto lowest, Vanta/Drata higher) |
| Consultant (optional but usual) | $5,000–15,000 |
| **Total, small organisation** | **$15,000–50,000** |

Then **$3,000–10,000/year** for surveillance audits in years two and three, and recertification every
three years. A cloud-native startup using a platform and templates can land near **$10,000–25,000**
all-in. Timeline is **4–9 months** realistically, 3–4 aggressively.

### 4.3 Annex A gaps in geneav today

Everything here is already documented in [`business-case.md`](business-case.md) §6 — ISO simply forces
them onto a schedule:

| Annex A control | Current state |
|---|---|
| 5.9 Asset inventory | Does not exist |
| 5.19–5.22 Supplier security | Subprocessor list exists; no review process |
| 5.29–5.30 Continuity & ICT readiness | Single VM, single region, no plan, no test |
| 8.8 Vulnerability management | No documented process; no dependency scanning in CI |
| 8.9 Configuration management | Containers run as root, no digest-pinned images ([`deploy-plan.md:68-70`](../deploy-plan.md)) |
| 8.13 Backup | Manual `pg_dump`, never restore-tested ([`db-access.md:101-104`](db-access.md)) |
| 8.15–8.16 Logging & monitoring | No monitoring or alerting configured; Azure Monitor is a plan item ([`deploy-plan.md:108`](../deploy-plan.md)) |
| 8.25–8.28 Secure development | CI runs tests and build only; no documented SDLC or security review gate |

None of these is hard individually. Collectively they are the four-to-nine months.

### 4.4 Certifying as a one-person company

It is legitimate — scope can be a single person and a single product — but several Annex A controls
assume an organisation with staff. Segregation of duties (5.3), HR security (6.1–6.6), and management
review are handled by documenting them **proportionately**: stating that the sole officer performs all
roles, and describing the compensating controls, is an acceptable answer. Auditors object to pretending,
not to being small.

---

## 5. Are you buying the right badge?

Certification demand splits by market:

- **US buyers ask for SOC 2 Type II.** Type II requires an observation window, usually 3–12 months.
- **EU, UK and Asian enterprises ask for ISO 27001.**
- **SMB and developer-led buyers ask for neither** — they want a security page, a DPA, and a fast,
  honest questionnaire response. geneav now has the first two of those three.

Because geneav sells a developer API rather than an enterprise platform, and has no customers yet, the
honest answer is that **neither certificate is the current bottleneck** — distribution is
([`business-case.md`](business-case.md) §7). Pick the badge only when a named deal is blocked on it, and
pick the one that deal's buyer actually asked for.

Until then, the `/security` page states plainly that neither exists
([`frontend/app/security/page.tsx`](../frontend/app/security/page.tsx)), which is the right posture:
verifiable, and it converts the gap into a signal about what to build.

---

## 6. Recommended sequence

**Now — GDPR (weeks, near-zero cost).** It is a legal obligation and the cheapest item on this page.

| # | Action | Type |
|---|---|---|
| 1 | Stop logging raw filenames | Code — small |
| 2 | Appoint an Article 27 EU representative | Vendor · **⚖️ counsel** |
| 3 | Publish a DPA with SCCs | Document · **⚖️ counsel** |
| 4 | Write the Article 30 record, retention schedule, and breach runbook | Document |
| 5 | Reconcile the privacy policy's backup claim | Document or automation |

**Next — foundations that are good engineering anyway (ongoing).** Do these as normal roadmap work,
not as a compliance project. They are already on the backlog, and doing them now turns a later
certification from nine months into three:

| # | Action | Also satisfies |
|---|---|---|
| 6 | Automated backups **with restore tests** | A.8.13 · fixes gap 3 |
| 7 | Monitoring and alerting (Azure Monitor + uptime check) | A.8.15–8.16 |
| 8 | Self-serve account deletion | Art. 17 · fixes gap 2 |
| 9 | Container hardening: non-root, heap cap, digest pinning | A.8.9 |
| 10 | Dependency scanning in CI | A.8.8 |
| 11 | Asset inventory and quarterly access review | A.5.9 |

**Later — certification, only when a deal requires it.** Choose SOC 2 or ISO 27001 based on who is
asking. Budget $15,000–50,000 and two to three quarters. Revisit once there is revenue to justify it;
certification converts deals, it does not create them.

**Parallel, commercially motivated:** a **West Europe** deployment. It removes the EU transfer question,
opens a market currently closed to you, and costs about one more VM.

---

## Sources

Retrieved 25 July 2026:

- [ISO 27001 Certification Cost Breakdown in 2026 — StrongDM](https://www.strongdm.com/blog/iso-27001-certification-cost) — audit and total cost ranges
- [ISO 27001 Certification Cost — Sprinto](https://sprinto.com/iso-27001/certification-cost/) — small-organisation first-year and surveillance costs
- [ISO 27001 Costs for Startups — ISMS Copilot](https://www.ismscopilot.com/blog/iso-27001-costs-startups-what-to-expect) — startup first-year range
- [Vanta Pricing in 2026 — Sprinto](https://sprinto.com/blog/vanta-pricing/) and [Drata Pricing in 2026 — Sprinto](https://sprinto.com/blog/drata-pricing/) — compliance platform pricing
- [EU Representative Providers Compared (2026) — Engage Compliance](https://www.engagecompliance.co/eu-representative-providers-compared) — Article 27 representative pricing
- [Article 27 Representative Pricing — EU Business Partners](https://article27representative.eu/en-us/pricing/) — Article 27 representative pricing

Regulatory references: [GDPR Articles 6, 9, 17, 27, 28, 30, 35](https://gdpr-info.eu/) ·
ISO/IEC 27001:2022 Annex A.
