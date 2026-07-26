# geneav — Production Readiness & Azure VM Deployment Plan

A plan to take geneav from a working local scaffold to a hardened deployment on a
single Azure virtual machine. Companion to [`plan.md`](./plan.md) (product roadmap).

> Target: one Azure Linux VM running the existing three-container stack
> (frontend + backend + ClamAV) behind a TLS reverse proxy.

## Decisions

| Decision        | Choice                                              | Status |
|-----------------|-----------------------------------------------------|--------|
| **Scope**       | **Public production**                               | ✅ Decided |
| **Domain/TLS**  | **Free `nip.io` wildcard hostname** (`<vm-ip>.nip.io`) | ✅ Decided |

**Scope is Public production**, so the full Phase 1 hardening applies — including the
items marked _(prod-only)_ below (app-level rate limiting, optional API keys).

**Hostname is a free `nip.io` wildcard.** `nip.io` resolves `<vm-ip>.nip.io` (and any
label like `geneav.<vm-ip>.nip.io`) straight to the embedded IP with no DNS setup. Once
the VM has its static public IP, the hostname is `<that-ip>.nip.io` and Caddy can obtain
a real Let's Encrypt cert for it via the HTTP-01 challenge. **Caveat:** `nip.io` is a
single shared registered domain, so it counts against Let's Encrypt's shared
per-domain rate limits — fine for one cert, but if issuance fails with a rate-limit
error, switch to `sslip.io` (equivalent) or fall back to a self-signed cert. See Risks.

## Where the code stands today

- **Everything is hardwired to `localhost`.** CORS (`GENEAV_ALLOWED_ORIGINS`), the
  frontend's `NEXT_PUBLIC_API_BASE_URL` (baked at **build** time in `next.config.mjs`),
  and the `docker-compose.yml` port mappings all assume local runs.
- **No TLS, no auth, no rate limiting.** `POST /api/v1/scan` is fully open. Rate
  limiting and API keys are explicitly out of scope per GN-1.
- **Containers run as root**, with no resource limits and no `restart:` policy.
- **ClamAV needs real memory** — `clamd` holds the signature database resident.
  Measured 26 July 2026: **VmRSS 974 MB, peak 987 MB** (~1 GB). Earlier revisions of
  this document said ~1.5–2 GB; that was an estimate and it was roughly 2x high.
  Still the largest single consumer, and still the main driver of VM sizing.
- **End-to-end EICAR detection is still unverified** (`plan.md` marks it pending).

---

## Phase 0 — Verify locally before touching servers

Nothing deploys until the stack is green locally.

- [ ] `docker compose up --build`; wait for `clamav` to report healthy (first run
      downloads the signature DB).
- [ ] Stream an EICAR test string via stdin (never write it to disk on Windows —
      Defender quarantines it) and confirm an `infected` verdict from `/api/v1/scan`.
- [ ] Confirm documented error paths: oversized upload → **413**, unsupported type → **415**,
      engine down → **503**.

Closes the last two open GN-1 acceptance criteria.

## Phase 1 — Application hardening (code changes)

1. **Frontend runtime config.** `NEXT_PUBLIC_API_BASE_URL` is inlined at build time.
   Preferred fix: route browser calls through a same-origin `/api` path (the reverse
   proxy forwards `/api` → backend), so the frontend never needs the backend's absolute
   URL **and CORS becomes unnecessary**. Alternative: pass the prod URL as a Docker
   build arg.
2. **CORS + config via env** for the real host
   (`GENEAV_ALLOWED_ORIGINS=https://<domain>`). Already parameterized — needs prod values.
   _(Can drop entirely if using the same-origin `/api` approach.)_
3. **Rate limiting** on `/api/v1/scan`. A public, unauthenticated, CPU/RAM-heavy scan
   endpoint is a DoS magnet. Minimum: per-IP limiting at the reverse proxy. Better _(prod-only)_:
   app-level token bucket (Bucket4j).
4. **API keys** _(prod-only, optional)_ — a simple header check to gate programmatic use.
5. **Container hardening:** non-root `USER` in both Dockerfiles; JVM heap cap
   (`-XX:MaxRAMPercentage=75`); pin base images by digest; `restart: unless-stopped`;
   `mem_limit` / `cpus` per service.
6. **Reduce attack surface:** remove the `3310` / `8080` / `3000` host port mappings —
   only the reverse proxy faces the internet; services talk over the compose network.
7. **Observability:** expose actuator `metrics`/`prometheus`, structured JSON logging,
   and keep the `clamav-db` volume so freshclam signature updates survive restarts.

## Phase 2 — Azure VM provisioning

- **VM:** Ubuntu 22.04 LTS, **Standard B2ms (2 vCPU / 8 GiB)** recommended. B2s
  (2 vCPU / 4 GiB) is the bare floor given `clamd`'s memory footprint.
- **Networking (NSG):** allow **443** (and **80** for the ACME HTTP-01 challenge/redirect)
  from the internet; restrict **SSH (22)** to your IP or use Azure Bastion.
- **Public IP:** static — the `nip.io` hostname is derived from it, so it must not change
  on VM restart.
- **DNS:** none to configure. The hostname is simply `<static-public-ip>.nip.io` (e.g.
  `geneav.20-1-2-3.nip.io`), which resolves to the IP automatically.
- **Disk:** default OS disk is sufficient; the signature DB lives in a Docker volume.

## Phase 3 — Deploy

- Install Docker Engine + the compose plugin on the VM.
- Add a **reverse proxy with automatic HTTPS** in front. **Caddy** is the least-effort
  choice: one `Caddyfile`, automatic Let's Encrypt certs + renewal, built-in per-IP rate
  limiting. It terminates TLS and proxies `/` → frontend, `/api` → backend.
- Add a **`docker-compose.prod.yml` override**: prod env vars, no public app ports,
  restart policies, resource limits, and the Caddy service.
- Deploy:
  ```bash
  git clone <repo> && cd geneav
  docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
  ```

## Phase 4 — Operations

- **CI/CD:** GitHub Actions — build + test (`mvn test`) on PR; optional SSH-deploy on
  merge to `main`.
- **Certs & signatures:** Caddy auto-renews TLS; freshclam auto-updates signatures inside
  the ClamAV container (persisted via the `clamav-db` volume).
- **Monitoring:** Azure Monitor + an uptime check against `GET /api/v1/health`.
- **Cost control:** Azure auto-shutdown schedule if this is a demo/dev box.

---

## Deliverables this plan produces

| Artifact                     | Purpose                                             |
|------------------------------|-----------------------------------------------------|
| `docker-compose.prod.yml`    | Prod override: no public ports, restart + limits, Caddy |
| `Caddyfile`                  | TLS termination + reverse proxy + per-IP rate limit |
| Hardened `Dockerfile`s       | Non-root user, heap caps, pinned bases              |
| `.env.prod.example`          | Documented prod environment variables               |
| `.github/workflows/*.yml`    | CI (build/test), optional CD (deploy)               |
| Azure provisioning notes     | VM size, NSG rules, DNS steps (this doc + runbook)  |

## Risks / watch-items

- **ClamAV memory:** under-sizing the VM makes `clamd` OOM-kill mid-scan. 8 GiB recommended.
- **First-boot delay:** initial signature DB download takes minutes; health checks must
  tolerate the `start_period`.
- **Open scan endpoint:** without rate limiting, one client can saturate CPU/RAM. Treat
  Phase 1 item 3 as non-optional for anything internet-facing.
- **Build-time frontend URL:** forgetting to set the API base URL at build produces a
  site that silently calls `localhost`. The same-origin `/api` approach avoids this.
- **`nip.io` TLS rate limits:** `nip.io` is one shared registered domain under Let's
  Encrypt's per-domain limits (50 certs/week, duplicate-cert caps). One cert is fine;
  if issuance is rate-limited, use `sslip.io` instead or fall back to a self-signed cert.
  Test against Let's Encrypt **staging** first to avoid burning the shared quota.
- **`nip.io` dependency:** the hostname relies on the public `nip.io` resolver staying up.
  Acceptable for a public demo/production-on-a-budget; swap in a registered domain later
  with only a Caddyfile hostname change.
