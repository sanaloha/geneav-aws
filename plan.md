# geneav — Project Plan

Project plan for **geneav**, a document-scanning antivirus product: a marketing
website plus a REST API that scans uploaded documents for malware using ClamAV.

> Tracked in Jira: **[GN-1](https://santoshsscet.atlassian.net/browse/GN-1)** ·
> Scaffold PR: **[#1](https://github.com/sanaloha/geneav/pull/1)** ·
> This document: **[#2](https://github.com/sanaloha/geneav/issues/2)**

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

### 4. Post-v1 (out of scope for GN-1) — ⏳ Backlog
- User accounts, authentication, and API keys.
- Billing / subscription management.
- Rate limiting and abuse protection.
- Additional scan engines behind the `ScanEngine` abstraction.

## Current status

| Item                                             | Status |
|--------------------------------------------------|--------|
| Frontend build + all routes serving              | ✅ Verified |
| Backend build + tests                            | ✅ 5/5 passing |
| Backend + ClamAV end-to-end (EICAR)              | 🔜 Pending Docker |
| Website deployed / live                          | ⏳ Not started |

### Acceptance criteria (GN-1)
- [x] Home page explains the product + CTA
- [x] Developer page documents the API with a runnable example
- [x] `POST /api/v1/scan` returns a JSON verdict; `GET /api/v1/health` returns status
- [x] Oversized/unsupported files return documented errors
- [x] API documented via OpenAPI/Swagger
- [ ] Website live (runs locally; not yet deployed)
- [ ] End-to-end scan verified against a real ClamAV (EICAR)

## Open items / next actions

1. Install and start Docker, then `docker compose up --build` to run the full stack.
2. Verify EICAR detection end-to-end via `POST /api/v1/scan`.
3. Choose hosting and deploy frontend + backend + ClamAV.
