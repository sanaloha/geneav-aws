# geneav — Project Plan

Project plan for **geneav**, a document-scanning antivirus product: a marketing
website plus a REST API that scans uploaded documents for malware using ClamAV.

> Tracked in Jira: **[GN-1](https://santoshsscet.atlassian.net/browse/GN-1)** ·
> Scaffold PR: **[#1](https://github.com/sanaloha/geneav-az/pull/1)** ·
> This document: **[#2](https://github.com/sanaloha/geneav-az/issues/2)**

## Overview

geneav makes malware scanning a one-call building block. Users can scan a document
from the website or straight from their code via a simple HTTP API. Detection is
handled by [ClamAV](https://www.clamav.net/) and its signature database.

The product has two deliverables:

1. **Marketing / product site** — explains what geneav is and lets visitors try a scan.
2. **Document-scanning REST API** — accepts a document and returns a clean/infected verdict.

## Architecture

```
frontend/  Next.js 14 (App Router, TypeScript)  — marketing site + "try a scan"  :3000
backend/   Spring Boot 3.3 (Java 17, Maven)      — scan + health REST API          :8080
clamav     ClamAV daemon (clamd)                 — the detection engine            :3310
```

- The backend talks to `clamd` over ClamAV's native **INSTREAM** protocol — no
  third-party client library, nothing written to disk on the API.
- `docker-compose.yml` wires all three services together for local runs.

### API surface

| Method | Path              | Description                                      |
|--------|-------------------|--------------------------------------------------|
| POST   | `/api/v1/scan`    | Upload a document (`multipart` `file`) → JSON verdict |
| GET    | `/api/v1/health`  | Reports API + engine readiness                   |
| GET    | `/docs`           | Swagger UI                                        |
| GET    | `/api-docs`       | OpenAPI JSON                                      |

## Tech decisions (from GN-1)

| Area          | Decision                                    |
|---------------|---------------------------------------------|
| Scan engine   | Real **ClamAV** (not a stub)                |
| Frontend      | **Next.js**                                 |
| Backend       | **Spring Boot**                             |
| Rate limiting | None for v1 (deferred)                      |
| Next.js version | Pinned to patched **14.2.35** (security advisory) |

## Milestones / roadmap

### 1. Scaffold — ✅ Done (PR #1)
- Next.js site: home, features, developers (live "try a scan"), about.
- Spring Boot API: `/api/v1/scan`, `/api/v1/health`, type/size guards (400/413/415/503),
  OpenAPI docs, controller tests (5/5 passing).
- `docker-compose.yml` for clamav + backend + frontend.

### 2. End-to-end verification — 🔜 In progress
- Stand up Java 17 + Maven (done locally) and Docker + ClamAV.
- Run the full stack and confirm a real **EICAR** test file is detected end-to-end.
- Confirm oversized/unsupported files return the documented error codes against the live API.

### 3. Deployment — ⏳ Planned
- Host the frontend and backend; run ClamAV as a managed/containerized service.
- Wire `NEXT_PUBLIC_API_BASE_URL` and CORS origins to the deployed hosts.
- Tick the "website is live" acceptance criterion.

### 4. Post-v1 (out of scope for GN-1) — mostly shipped
- ✅ User accounts, authentication, and API keys.
- ✅ Rate limiting and abuse protection.
- 🚫 **Billing / subscription management — dropped 2 Aug 2026.** The SaaS-fulfillment
  code shipped 27 July 2026 and still exists in the backend, disabled by default, but the
  channel is no longer offered: the `/marketplace` pages are deleted and no page advertises
  a purchase flow. Paid plans are arranged by email until a channel is chosen. Entra ID
  sign-in stays — it is independent of billing and still the only federated login.
- ⏳ A purchase channel to replace it. Undecided; the fulfillment contract differs per
  marketplace, so the existing code is not a head start on most options.
- ⏳ Additional scan engines behind the `ScanEngine` abstraction.

## Current status

| Item                                             | Status |
|--------------------------------------------------|--------|
| Frontend build + all routes serving              | ✅ Verified |
| Backend build + tests                            | ✅ 145/145 passing |
| Backend + ClamAV end-to-end (EICAR)              | ✅ Verified locally |
| Website deployed / live                          | ✅ Live at geneav.com |
| Paid-plan purchase channel                       | 🚫 None — dropped 2 Aug 2026; arranged by email |

### Acceptance criteria (GN-1)
- [x] Home page explains the product + CTA
- [x] Developer page documents the API with a runnable example
- [x] `POST /api/v1/scan` returns a JSON verdict; `GET /api/v1/health` returns status
- [x] Oversized/unsupported files return documented errors
- [x] API documented via OpenAPI/Swagger
- [x] Website live (`geneav.com`; moving to AWS Lightsail)
- [x] End-to-end scan verified against a real ClamAV (EICAR)

## Open items / next actions

The three items that used to sit here (run the stack, verify EICAR, choose hosting) are
done — see the status table above. What remains is finishing the host cutover and picking
a purchase channel for paid plans.

1. **Merge the first-deploy fixes** ([#2](https://github.com/sanaloha/geneav-aws/pull/2)) —
   Caddy is pulled from ECR rather than built on a box that has never built it, and its
   hostnames derive from `GENEAV_HOST` so a staging run does not drag `geneav.com` in.
2. **Provision AWS** — `./aws-provision.sh`, then set the GitHub secrets/variables and put
   `.env.prod` on the box through SSM Session Manager (port 22 is closed; there is no SSH).
3. **Stage on `nip.io`**, verify end to end, then cut `geneav.com` DNS to the Lightsail
   static IP. **The AWS box starts from an empty database** — accounts, API keys and
   analytics history do not come across (`deploy-plan.md`).
4. **Configure uptime monitoring** on `/api/v1/health` and the webhook path. This is a
   prerequisite for publishing the Marketplace offer, not a nice-to-have — availability
   became contractual once billing was involved.
5. **Finish the Partner Center work** — payout/tax profiles, app registrations, listing
   assets, certification ([`docs/marketplace-plan.md`](docs/marketplace-plan.md)).
