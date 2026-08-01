# geneav — Production Readiness & AWS Lightsail Deployment Plan

A plan to take geneav from a working local scaffold to a hardened deployment on a
single Linux box. Companion to [`plan.md`](./plan.md) (product roadmap).

> Target: one AWS Lightsail instance running the container stack behind a TLS
> reverse proxy.

> **Rehosted from Azure, 27 July 2026.** Phases 0, 1, 3 and 4 below were written
> for an Azure VM and are cloud-neutral, so they stand as-is. Phase 2 has been
> rewritten for Lightsail. The move was driven by cost: a `Standard_D2s_v3` at
> ~$87/month for a stack measuring ~2.0 GB, with eastus refusing to offer a
> smaller SKU to shrink into. See [`docs/business-case.md`](docs/business-case.md).

## Decisions

| Decision        | Choice                                              | Status |
|-----------------|-----------------------------------------------------|--------|
| **Scope**       | **Public production**                               | ✅ Decided |
| **Domain/TLS**  | Registered domain (`geneav.com`); `nip.io` for staging cutover | ✅ Decided |

**Scope is Public production**, so the full Phase 1 hardening applies — including the
items marked _(prod-only)_ below (app-level rate limiting, optional API keys).

**Production runs on the registered domain `geneav.com`**, with `www` redirected to the
apex and `analytics` on its own subdomain, all pointed at the Lightsail static IP.

**`nip.io` remains the staging lever.** `nip.io` resolves `<ip>.nip.io` (and any label like
`geneav.<ip-with-dashes>.nip.io`) straight to the embedded IP with no DNS setup, and Caddy
can obtain a real Let's Encrypt cert for it over HTTP-01. That is how a freshly provisioned
box gets verified end to end *before* production DNS is touched. **Caveat:** `nip.io` is a
single shared registered domain, so it counts against Let's Encrypt's shared per-domain
rate limits — fine for one cert, but if issuance fails with a rate-limit error, switch to
`sslip.io` (equivalent). See Risks.

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
  Still the largest single consumer, and still the main driver of host sizing.
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

## Phase 2 — AWS Lightsail provisioning

All of this is scripted in [`aws-provision.sh`](./aws-provision.sh).

- **Instance:** Ubuntu 22.04, **Lightsail `medium_3_0`** — 4 GB / 2 vCPU / 80 GB SSD /
  4 TB transfer, $24/month, **x86**. The 2 GB bundle is not viable: `clamd` alone holds
  ~974 MB resident and a freshclam reload needs headroom above that. Staying on x86 keeps
  CI's plain `docker build` valid; Graviton would work (the backend is pure JVM and the
  arm64 SWC binaries are already in the lockfile) but would need buildx.
  > **Why not EC2?** `t4g.medium` + 40 GB gp3 + an IPv4 address is ~$31/month before
  > anything else. Lightsail bundles compute, disk, static IP and transfer into one price.
- **Firewall:** **80 + 443 only**, and the default port-22 rule is *removed*.
  `put-instance-public-ports` replaces the whole rule set rather than appending, which is
  what makes that deletion possible. Break-glass SSH is `--allow-ssh-from <cidr>`.
- **Static IP:** allocated and attached. Free while attached to a running instance, billed
  if left dangling. DNS depends on it, so it must not change.
- **DNS:** A records for `geneav.com`, `www` and `analytics` at the static IP. For staging
  a fresh box before cutover, use `geneav.<ip-with-dashes>.nip.io` — no records needed.
- **Registry:** three ECR repositories — backend, frontend and caddy — with **immutable
  tags** and a lifecycle policy keeping the newest 5 images (~$0.30/month, versus $5 for
  ACR Basic). Caddy is in the registry rather than built on the box because the deploy is
  `up --no-build`: a `build:` for it would only ever have worked on a box that had already
  built it once, which a freshly provisioned instance has not.
- **Identity:** GitHub OIDC → `geneav-ci` role (ECR push + SSM SendCommand, pinned to
  `repo:sanaloha/geneav-aws:ref:refs/heads/main`); an SSM hybrid activation binding the box
  to `geneav-ssm-instance` (SSM core + **pull-only** ECR).
- **Disk:** the bundled 80 GB is ample; the signature DB lives in a Docker volume.
- **Backups:** an S3 bucket with a **14-day** lifecycle rule — matching what the privacy
  policy promises — plus a `s3:PutObject`-only IAM user for the backup sidecar.

## Phase 3 — Deploy

- Install Docker Engine + the compose plugin on the box (done by the instance's
  user-data script on first boot).
- Add a **reverse proxy with automatic HTTPS** in front. **Caddy** is the least-effort
  choice: one `Caddyfile`, automatic Let's Encrypt certs + renewal, built-in per-IP rate
  limiting. It terminates TLS and proxies `/` → frontend, `/api` → backend.
- **One exception to the per-IP rate limit** (added 27 July 2026): the Microsoft Marketplace
  webhook must be declared in a `handle /api/v1/marketplace/webhook` block placed **ahead** of
  the `/api/*` block, with no `rate_limit`. Microsoft retries delivery up to 500 times over eight
  hours from a small set of source IPs, so the 20-events/minute limit would silently break
  subscription lifecycle events. The backend validates an Entra JWT on every call — that, not
  throttling, is the guard. The same exemption exists in `RateLimitFilter` and `ApiKeyAuthFilter`.
- Add a **`docker-compose.prod.yml` override**: prod env vars, restart policies, resource
  limits, and the Caddy service. Host port publishing lives in `docker-compose.override.yml`,
  which Compose auto-loads for local `docker compose up` but *not* when prod passes explicit
  `-f` files — so prod publishes only Caddy's 80/443 plus a loopback-bound 8080 for the deploy
  health check. This is done, and is what makes "no public app ports" true rather than
  firewall-dependent.
- Deploy: push to `main`. CI builds images, pushes them to ECR, and
  `scripts/deploy-lightsail.sh` tells the box (via SSM) to pull the SHA-tagged images and
  restart, rolling back if the pull, the start or the health check fails.

### The AWS box starts from an empty database — decided 1 August 2026

**The Azure Postgres is not migrated.** Cutover is a DNS change, not a dump-and-restore,
and there is no freeze window.

What that costs, stated plainly so it is not rediscovered at cutover: **every account, API
key and usage counter on the Azure box is gone.** Anyone holding a live API key gets a 401
the moment DNS moves, and users must sign up again. Umami analytics history does not come
across either, and Umami issues a **new website id** that has to go into the
`NEXT_PUBLIC_UMAMI_WEBSITE_ID` repository variable — it is baked into the frontend bundle
at build time, so picking it up needs a rebuild, not a restart (`.env.prod.example`).

What makes it survivable: the Marketplace offer is **not published**, so there are no paid
entitlements and no subscription state to lose. Doing this after the offer goes live would
be a different decision entirely — it would strand paying customers.

Nothing in the app needs seeding. Flyway owns the schema (`V1__init` … `V6__marketplace`,
with `ddl-auto: validate`), so an empty database provisions itself on first boot, and there
is no admin account or seed data to recreate — signup is entirely self-service.

## Phase 4 — Operations

- **CI/CD:** GitHub Actions — build + test on PR; on merge to `main`, build images, push to
  ECR, deploy via SSM Run Command. Done.
- **Certs & signatures:** Caddy auto-renews TLS; freshclam auto-updates signatures inside
  the ClamAV container (persisted via the `clamav-db` volume).
- **Backups:** nightly `pg_dump -Fc` of both databases, local volume **and** S3, 14-day
  retention in both. Done.
- **Monitoring:** CloudWatch or a Lightsail metric alarm, plus an uptime check against
  `GET /api/v1/health`. **Now a prerequisite, not a nice-to-have** — see the availability
  note below. Still unconfigured.
- ~~**Cost control:** a nightly stop/start schedule if this is a demo/dev box.~~
  **Incompatible with Marketplace billing.** Microsoft requires the landing page and the
  webhook to be reachable **24/7**; a nightly shutdown window drops webhook deliveries and
  leaves customers on a plan they are not paying for, or paying for one they do not have.
  It is a dev/demo lever only — do not enable it on the box serving the offer. (Lightsail
  bills the bundle by the month whether the instance runs or not, so on this host it would
  not have saved anything anyway.)

### Availability became contractual (27 July 2026)

Publishing a transactable Marketplace offer changes the operational bar. Previously a single
box with no failover was an accepted risk for a free product; a missed webhook now has a
billing consequence. Two items move from "planned" to "required before the offer goes live":

- **Uptime monitoring and alerting** on `/api/v1/health` and the webhook path
  ([`docs/compliance-roadmap.md`](docs/compliance-roadmap.md) §6 item 7, still unconfigured).
- **A deliberate answer on redundancy.** The rebuild-on-the-production-box window is gone —
  images are built in CI and the deploy is a pull — but it is still one instance in one
  availability zone, and a failed deploy still costs a container restart. Microsoft's webhook
  retries over eight hours absorb a short window, but not an outage.

The mitigating detail worth knowing: the expiry sweep (`SubscriptionExpiryJob`) is a safety net
that repairs entitlements a dropped webhook would otherwise leave stale, so a brief outage
degrades rather than corrupts. It is not a substitute for being up.

---

## Deliverables this plan produces

| Artifact                     | Purpose                                             |
|------------------------------|-----------------------------------------------------|
| `docker-compose.prod.yml`    | Prod override: restart + limits, Caddy, S3 backups   |
| `docker-compose.override.yml`| Dev-only host port publishing, so prod publishes none |
| `Caddyfile`                  | TLS termination + reverse proxy + per-IP rate limit |
| Hardened `Dockerfile`s       | Non-root user, heap caps, pinned bases              |
| `.env.prod.example`          | Documented prod environment variables               |
| `.github/workflows/ci-cd.yml`| CI (build/test) + CD (ECR push, SSM deploy)        |
| `aws-provision.sh`           | Lightsail, ECR ×3, IAM/OIDC, SSM activation, S3 bucket |
| `scripts/deploy-lightsail.sh`| Pull-and-restart deploy with health-check rollback  |

## Risks / watch-items

- **ClamAV memory:** under-sizing the host makes `clamd` OOM-kill mid-scan. The 4 GB bundle
  is chosen against a measured ~2.0 GB; the 2 GB bundle is not viable.
- **First-boot delay:** initial signature DB download takes minutes; health checks must
  tolerate the `start_period`.
- **Open scan endpoint:** without rate limiting, one client can saturate CPU/RAM. Treat
  Phase 1 item 3 as non-optional for anything internet-facing.
- **Build-time frontend URL:** forgetting to set the API base URL at build produces a
  site that silently calls `localhost`. The same-origin `/api` approach avoids this.
- **SSM payload ceiling (~100 KB):** *tighter* than the control-plane limit that broke
  deploys in July 2026. `deploy-lightsail.sh` refuses to send over 90 KB. If that guard ever
  fires, something that is not configuration has crept into the tarball.
- **Single AZ, single instance:** a Lightsail bundle is one machine in one availability
  zone. Backups go to S3, but there is no failover.
- **`nip.io` (staging only):** `nip.io` is one shared registered domain under Let's
  Encrypt's per-domain limits. Fine for one cert during cutover; if issuance is rate-limited,
  use `sslip.io`. Test against Let's Encrypt **staging** first to avoid burning the quota.
