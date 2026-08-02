# geneav

Antivirus for documents. A marketing website plus a REST API that scans uploaded
documents for malware, backed by [ClamAV](https://www.clamav.net/).

> Tracked in Jira: **GN-1**. See [`plan.md`](./plan.md) for the project plan and roadmap.

## Architecture

```
frontend/  Next.js (App Router, TypeScript)  — marketing site + "try a scan"  :3000
backend/   Spring Boot (Java 17, Maven)       — scan + health REST API          :8080
clamav     ClamAV daemon (clamd)              — the detection engine            :3310
```

The backend talks to `clamd` over ClamAV's native INSTREAM protocol (no third-party
client library) and exposes:

| Method | Path              | Description                                   |
|--------|-------------------|-----------------------------------------------|
| POST   | `/api/v1/scan`    | Upload a document (`multipart` `file`), get a JSON verdict |
| GET    | `/api/v1/health`  | Reports API + engine readiness                |
| POST   | `/api/v1/signup`  | Create an account, receive your first API key (programmatic) |
| POST   | `/api/v1/auth/signup` · `/auth/login` · `/auth/logout` | Dashboard password auth (session cookie) |
| GET    | `/api/v1/auth/me` | The signed-in account, or 401                 |
| GET    | `/api/v1/usage`   | Current-month scan usage vs. plan quota (session or key) |
| GET/POST/DELETE | `/api/v1/keys` | List / create / revoke API keys (session or key) |
| POST   | `/api/v1/marketplace/resolve` · `/activate` | Azure Marketplace purchase → plan (session) |
| GET    | `/api/v1/marketplace/subscription` | The account's live marketplace subscription (session) |
| POST   | `/api/v1/marketplace/webhook` | Microsoft subscription lifecycle events (Entra JWT) |
| GET    | `/docs`           | Swagger UI                                     |
| GET    | `/api-docs`       | OpenAPI JSON                                   |

## Quick start (Docker — recommended)

Runs ClamAV, the API, and the website together:

```bash
docker compose up --build
```

Then open:
- Website → http://localhost:3000
- API docs → http://localhost:8080/docs

> The first `clamav` start downloads the signature database (a few minutes). The
> backend waits for the container to become healthy before starting.

## Running the pieces individually

### Prerequisites
- **Node.js 20+** (frontend)
- **Java 17+ and Maven** (backend)
- **A running ClamAV `clamd`** on `localhost:3310` (Docker is easiest):
  ```bash
  docker run -d --name clamav -p 3310:3310 clamav/clamav:1.3
  ```

### Backend
```bash
cd backend
mvn spring-boot:run
# override the engine location if needed:
#   CLAMAV_HOST=localhost CLAMAV_PORT=3310 mvn spring-boot:run
```

### Frontend
```bash
cd frontend
cp .env.local.example .env.local   # optional; defaults to http://localhost:8080
npm install
npm run dev                        # http://localhost:3000
```

## Try it

### Scan a document — `POST http://localhost:8080/api/v1/scan`

Send a single file as a `multipart/form-data` field named `file`:

```bash
# clean file
curl -F "file=@README.md;type=text/plain" http://localhost:8080/api/v1/scan
```

```json
{
  "scanId": "442125fb-e4fb-4ed2-8ab8-48ce2cbdb50d",
  "status": "clean",
  "threat": null,
  "fileName": "README.md",
  "fileSize": 3445,
  "contentType": "text/plain",
  "scannedAt": "2026-07-14T05:18:16.689Z"
}
```

```bash
# EICAR test virus (harmless, triggers a detection)
printf 'X5O!P%%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*' > eicar.txt
curl -F "file=@eicar.txt;type=text/plain" http://localhost:8080/api/v1/scan
```

```json
{
  "scanId": "c6153bab-2ce9-4f66-b2ac-247b64d9cf68",
  "status": "infected",
  "threat": "Eicar-Test-Signature",
  "fileName": "eicar.txt",
  "fileSize": 68,
  "contentType": "text/plain",
  "scannedAt": "2026-07-14T05:18:16.891Z"
}
```

### Health — `GET http://localhost:8080/api/v1/health`

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","engine":"UP","checks":[{"name":"clamav","status":"UP"}]}
```

### Error responses

| Situation                          | HTTP |
|------------------------------------|------|
| Empty / missing `file` in the form | 400  |
| File exceeds the 25 MB limit       | 413  |
| Unsupported content type           | 415  |
| ClamAV engine unreachable          | 503  |

### Chat assistant — `POST http://localhost:8080/api/v1/chat`

A small chatbot (floating 💬 widget on every page) answers questions about geneav,
the scans it performs, and the safety it provides — and politely declines anything
off-topic. It is backed by the **OpenAI API**; the API key stays server-side.

Send the conversation so far as JSON; the last message is the user's new question:

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"What file types can geneav scan?"}]}'
# {"reply":"geneav scans documents such as PDFs, Office files, text, CSV, RTF, and ZIPs …","model":"gpt-4o-mini"}
```

Chat is **disabled until `OPENAI_API_KEY` is set** on the server; until then
`/chat` returns `503` and the widget shows that it is unconfigured. Check
availability with `GET /api/v1/chat/health`.

## Commercial API — accounts, keys & quotas

The scan and chat endpoints are usable **anonymously** (free tier, throttled per
IP). For higher, metered access, callers authenticate with an API key.

```bash
# 1. Sign up — returns your first key ONCE (store it; it is not recoverable)
curl -X POST http://localhost:8080/api/v1/signup \
  -H "Content-Type: application/json" -d '{"email":"you@example.com"}'
# {"accountId":"...","email":"you@example.com","plan":"free","apiKey":"gav_live_...","keyPrefix":"gav_live_...."}

# 2. Call the API with the key — usage is metered against your plan quota
curl -X POST http://localhost:8080/api/v1/scan \
  -H "Authorization: Bearer gav_live_..." -F "file=@invoice.pdf"

# 3. Check usage / manage keys
curl http://localhost:8080/api/v1/usage  -H "Authorization: Bearer gav_live_..."
curl http://localhost:8080/api/v1/keys   -H "Authorization: Bearer gav_live_..."
curl -X POST   http://localhost:8080/api/v1/keys      -H "Authorization: Bearer gav_live_..." -d '{"name":"ci"}'
curl -X DELETE http://localhost:8080/api/v1/keys/{id} -H "Authorization: Bearer gav_live_..."
```

Prefer a UI? The **dashboard** at `/login` lets you sign up / sign in with an
email + password — or with **Microsoft (Entra ID)** once
`GENEAV_MICROSOFT_LOGIN_ENABLED` is set — and manage keys, usage, and your
marketplace subscription from a browser session (secure HttpOnly cookie), with
no need to handle a raw key yourself.

Passwords must be at least 12 characters (and at most 72 — BCrypt ignores anything
beyond that), mix at least three of lowercase/uppercase/digits/symbols, and may not
be a common password or contain your email address. Signup sends an acknowledgement
email; it is **disabled by default** and only logged, until you set
`GENEAV_MAIL_ENABLED=true` plus `SMTP_HOST` / `SMTP_USERNAME` / `SMTP_PASSWORD`.
Mail is sent off the request thread and never fails a signup.

Keys are stored only as SHA-256 hashes — the plaintext is shown once, at creation.
Response codes: **401** missing/invalid key · **402** monthly quota exhausted ·
**429** rate limited. Plans (`free`, `pro`, …) and their quotas/rates are defined
in `application.yml` under `geneav.plans` and are env-overridable. Accounts, keys,
and usage live in **PostgreSQL** (schema managed by Flyway).

## Billing — Microsoft Marketplace

Paid plans are sold through the **Microsoft Azure Marketplace**. Microsoft is the
merchant of record: it collects payment, handles global VAT/sales tax, and bills
the customer's Azure account. A purchase raises the quota on an ordinary geneav
account — the scan path is untouched.

```
buy on Azure Marketplace
  → land on /marketplace/landing?token=…      (Partner Center "landing page URL")
  → sign in with Microsoft (Entra SSO)
  → POST /api/v1/marketplace/resolve          → exchange token for the purchase
  → POST /api/v1/marketplace/activate         → Microsoft starts billing
  → account.plan := the mapped tier, billing_source := 'marketplace'
```

Afterwards Microsoft POSTs lifecycle events (`Subscribe`, `ChangePlan`, `Renew`,
`Suspend`, `Reinstate`, `Unsubscribe`) to `/api/v1/marketplace/webhook`. That
endpoint is public but every call must carry a valid **Entra JWT** — validated
against `aud` / `tid` / `appid`|`azp` before the body is parsed. It is
deliberately exempt from `ApiKeyAuthFilter`, `RateLimitFilter`, and the Caddy
per-IP limit, because Microsoft retries up to 500 times over eight hours from a
small set of IPs; the JWT, not throttling, is the guard.

Partner Center plan ids map to geneav tiers under `geneav.marketplace.plan-map`
(`geneav-starter` → `starter`, and so on). An unmapped id changes no plan and
logs loudly — it must never default to a bigger tier than was paid for.

**Disabled by default.** With no credentials configured every marketplace
endpoint returns 503 and the site behaves exactly as it does without billing,
matching the `geneav.mail` / `geneav.openai` convention. Setup steps for Partner
Center and the two Entra app registrations are in
[`docs/marketplace-plan.md`](docs/marketplace-plan.md).

## Testing with Postman

1. **New request** → set method to **POST** and URL to
   `http://localhost:8080/api/v1/scan`.
2. Open the **Body** tab → select **form-data**.
3. Add a key named exactly **`file`**. Hover the key's value cell and switch its
   type dropdown from *Text* to **File**, then choose a file to upload.
   - Do **not** set the `Content-Type` header yourself — Postman adds the
     `multipart/form-data` boundary automatically when you use form-data.
4. Click **Send**. You'll get the JSON verdict shown above.

To reproduce a detection, save the EICAR string to a `.txt` file and upload it:

```
X5O!P%@AP[4\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*
```

**Health check in Postman:** new request, method **GET**, URL
`http://localhost:8080/api/v1/health`, then **Send**.

**Import the whole API instead:** in Postman click **Import** → **Link** and paste
`http://localhost:8080/api-docs` (the OpenAPI JSON). Postman generates a
collection with both endpoints pre-filled. You can also explore them
interactively in Swagger UI at `http://localhost:8080/docs`.

## Tests

```bash
cd backend && mvn test
```

## Configuration

| Setting              | Env var                  | Default                  |
|----------------------|--------------------------|--------------------------|
| ClamAV host          | `CLAMAV_HOST`            | `localhost`              |
| ClamAV port          | `CLAMAV_PORT`            | `3310`                   |
| Max upload size      | (application.yml)        | `25MB`                   |
| Allowed CORS origins | `GENEAV_ALLOWED_ORIGINS` | `http://localhost:3000`  |
| Frontend → API URL   | `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` |
| OpenAI API key (chat)| `OPENAI_API_KEY`         | _(empty → chat disabled)_ |
| OpenAI model         | `OPENAI_MODEL`          | `gpt-4o-mini`            |
| Rate limiting on/off | `GENEAV_RATELIMIT_ENABLED` | `true`                |
| Scan burst / per-min | `GENEAV_RATELIMIT_SCAN_CAPACITY` / `GENEAV_RATELIMIT_SCAN_RPM` | `10` / `10` |
| Chat burst / per-min | `GENEAV_RATELIMIT_CHAT_CAPACITY` / `GENEAV_RATELIMIT_CHAT_RPM` | `15` / `15` |
| Max concurrent scans | `GENEAV_RATELIMIT_SCAN_CONCURRENCY` | `4`             |
| Postgres JDBC URL    | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/geneav` |
| Postgres user / pass | `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `geneav` / `geneav` |
| Free plan quota / rpm| `GENEAV_PLAN_FREE_QUOTA` / `GENEAV_PLAN_FREE_RPM` | `500` / `10` |
| Starter plan quota / rpm | `GENEAV_PLAN_STARTER_QUOTA` / `GENEAV_PLAN_STARTER_RPM` | `10000` / `30` |
| Pro plan quota / rpm | `GENEAV_PLAN_PRO_QUOTA` / `GENEAV_PLAN_PRO_RPM` | `100000` / `120` |
| Scale plan quota / rpm | `GENEAV_PLAN_SCALE_QUOTA` / `GENEAV_PLAN_SCALE_RPM` | `500000` / `300` |
| Marketplace billing on/off | `GENEAV_MARKETPLACE_ENABLED` | `false` _(→ 503)_ |
| Marketplace fulfillment app | `MARKETPLACE_TENANT_ID` / `MARKETPLACE_CLIENT_ID` / `MARKETPLACE_CLIENT_SECRET` | _(empty)_ |
| Marketplace offer identity | `MARKETPLACE_PUBLISHER_ID` / `MARKETPLACE_OFFER_ID` | _(empty)_ / `geneav-scan-api` |
| Microsoft sign-in on/off | `GENEAV_MICROSOFT_LOGIN_ENABLED` | `false` |
| Microsoft sign-in app | `MICROSOFT_LOGIN_CLIENT_ID` / `MICROSOFT_LOGIN_CLIENT_SECRET` | _(empty)_ |
| Microsoft button in the UI | `NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED` | _(empty)_ — **build-time** |

> `NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED` is baked into the frontend bundle at
> **build** time and must match the backend's `GENEAV_MICROSOFT_LOGIN_ENABLED`.
> Set one without the other and the button either never appears or leads to an
> OAuth2 route the backend has not registered.

> In production, set a strong `POSTGRES_PASSWORD` in the box's `.env.prod` (the prod
> compose refuses to start without it). The Postgres data lives in the
> `postgres-data` Docker volume, which survives redeploys.

> The chat assistant needs `OPENAI_API_KEY`. Locally, export it before starting
> the stack (e.g. `OPENAI_API_KEY=sk-... docker compose up`); in production put it
> in the box's gitignored `.env.prod`. Never commit a real key — `.env.prod.example`
> ships only a placeholder.

## Deployment

Production runs on a single **AWS Lightsail** instance (4 GB / 2 vCPU, `us-east-1`)
behind Caddy. Pushing to `main` deploys automatically once CI passes.

> **Moved from Azure, July 2026.** The stack was on a `Standard_D2s_v3` VM at
> ~$87/month while measuring ~2.0 GB of the 7.8 GB it was paying for, and eastus
> would not offer a smaller SKU (`SkuNotAvailable`) to shrink into. Lightsail's
> 4 GB bundle is $24/month with the static IP, 80 GB SSD and 4 TB of transfer
> included — about **$25/month all-in against $87**. Nothing in the application
> changed; only the host, the registry and the deploy transport.

Deploys go through **AWS Systems Manager Run Command**, not SSH. The instance has
**no port 22 rule at all** — the SSM agent polls outbound — which also sidesteps
the problem that killed the SSH approach: GitHub-hosted runners have dynamic
egress IPs and cannot be allow-listed.

**Images are built in CI and pulled from ECR** — the box compiles nothing. CI
builds `geneav-backend`, `geneav-frontend` and `geneav-caddy` tagged with the
commit SHA, pushes them to `<acct>.dkr.ecr.us-east-1.amazonaws.com`, and
`scripts/deploy-lightsail.sh` tells the box to pull that tag and restart. The ECR
repositories use **immutable tags**, so a SHA cannot later be repointed at
different bytes, and a lifecycle policy keeps only the newest 5 images.

### Changing the hostname requires a rebuild, not a redeploy

`aws.geneav.com` is the staging host on the Lightsail box; `geneav.com` is
served separately. Three A records point at the static IP — the apex plus
`www.` and `analytics.`, because `docker-compose.prod.yml` derives those two
from `GENEAV_HOST` and Caddy requests a certificate for each.

Moving to a new hostname is **two** changes, and doing only the first breaks the
site in a way that looks like it worked:

1. Repository variable `NEXT_PUBLIC_API_BASE_URL`, then **rebuild**. Next.js
   inlines `NEXT_PUBLIC_*` into the client bundle at image build time, and
   `API_BASE` is what Nav, Dashboard, ScanForm, ResetPassword, ChatWidget and
   `/developers` call. A container restart cannot pick it up.
2. `GENEAV_HOST` in `.env.prod` on the box, then redeploy. This drives Caddy's
   site blocks, the CORS allow-list and the password-reset links.

Do only (2) and the page loads while every API call goes to the old hostname —
which Caddy is no longer serving. Do them in either order, but **land both in
the same deploy**.

Because tags are immutable and keyed on the commit SHA, a rebuild needs a **new
commit**: re-running a workflow for an already-built SHA fails on the push. Do
not delete the images to force it — a SHA that no longer means the same bytes
makes the box's `.deployed-sha` misleading.

> Until July 2026 the deploy base64'd the whole source tree into the remote
> script and the box rebuilt both images. That ended when the payload hit
> ~199 KB and the control plane silently truncated it. Building in CI also stops
> deploys competing with live traffic for CPU. SSM's parameter ceiling (~100 KB)
> is *tighter* than the one that caused the original failure, so
> `deploy-lightsail.sh` refuses to send a payload over 90 KB rather than let it
> be truncated.

**Credentials are split by direction.** CI assumes an IAM role that can push; the
box uses its SSM node role, which is scoped **pull-only** to the three
repositories — so a compromise of the box cannot push a poisoned image. Neither
is an admin credential, and both are revocable independently.

### Local configuration: `.env.aws`

Both scripts source a gitignored `.env.aws` at the repo root, so a local run
needs no exported variables. Create it before provisioning:

```bash
cat > .env.aws <<'EOF'
AWS_ACCOUNT_ID=<your 12-digit account id>
AWS_REGION=us-east-1
EOF
```

Everything else — the ECR registry host, the CI role ARN, the backup bucket, the
static IP and the two one-shot secrets — is **written back into this file by the
provisioning run**, so they cannot be lost to a closed terminal. The only value
you add by hand afterwards is `GENEAV_SSM_NODE`, which does not exist until the
box registers itself a few minutes later.

CI never reads this file. GitHub Actions authenticates through the OIDC role and
takes the same values from repository secrets and variables, so the two paths
share one set of variable names.

If `AWS_ACCOUNT_ID` is set and your credentials resolve to a *different*
account, `aws-provision.sh` aborts rather than building the stack somewhere
nobody is looking for it.

### Provision from scratch

```bash
aws sso login                    # or `aws configure sso` the first time
./aws-provision.sh --dry-run     # show what would be created
./aws-provision.sh               # Lightsail + ECR + IAM + SSM activation + S3 backups
```

It prints the static IP, the ECR registry, the GitHub secrets/variables to set,
and the **one-shot** SSM activation code. Follow the "Next" steps it prints.

### Deploy manually

With `.env.aws` populated this needs no exports at all:

```bash
scripts/deploy-lightsail.sh              # deploy the current commit (must be in ECR)
scripts/deploy-lightsail.sh --dry-run    # print the remote script, change nothing
scripts/deploy-lightsail.sh <sha>        # deploy/roll back to a specific tag
```

The script pulls the SHA-tagged images, restarts, waits for
`GET /api/v1/health`, and **rolls back to the previously deployed SHA** if the
pull, the start, or the health check fails. The box's gitignored `.env.prod` is
never touched.

To roll back by hand, deploy the previous SHA — the images are still in the
registry:

```bash
scripts/deploy-lightsail.sh <previous-sha>
```

### Getting a shell on the box

There is no SSH. Use a Session Manager session:

```bash
aws ssm start-session --target <mi-...> --region us-east-1
aws ssm describe-instance-information --region us-east-1 \
  --query 'InstanceInformationList[].[InstanceId,ComputerName,PingStatus]' --output table
```

If you genuinely need SSH for break-glass, re-run the provisioner with
`--allow-ssh-from <your-ip>/32`, and remove the rule afterwards.

### One-time CI setup

`aws-provision.sh` does all of this; the commands below are the record of what
it creates and how to verify it.

The workflow authenticates with **GitHub OIDC → IAM role**, so no long-lived AWS
key is stored in GitHub. The role's trust policy pins both the repository and the
ref:

```
"token.actions.githubusercontent.com:sub": "repo:sanaloha/geneav-aws:ref:refs/heads/main"
```

> This string must match **exactly** what GitHub sends, which is derived from the
> repository's real name. Check it against `git remote get-url origin` — the
> equivalent Azure record was left pointing at the repo's *previous* name and
> would have failed at the token exchange. A mismatch surfaces as a generic
> credentials error, not a name error.

Verify the current state:

```bash
aws iam get-role --role-name geneav-ci \
  --query 'Role.AssumeRolePolicyDocument.Statement[0].Condition' 
aws iam list-role-policies --role-name geneav-ci
aws ecr describe-repositories --query 'repositories[].repositoryName'
```

Then add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::<acct>:role/geneav-ci` |

| Variable | Value | Why a variable, not a secret |
|---|---|---|
| `AWS_REGION` | `us-east-1` | Not sensitive |
| `ECR_REGISTRY` | `<acct>.dkr.ecr.us-east-1.amazonaws.com` | Not sensitive |
| `GENEAV_SSM_NODE` | the `mi-...` managed node id | Not sensitive on its own; useless without IAM |
| `NEXT_PUBLIC_API_BASE_URL` | `https://geneav.com` | Baked into the browser bundle |
| `NEXT_PUBLIC_UMAMI_WEBSITE_ID` | the Umami site id | Baked into the browser bundle |
| `NEXT_PUBLIC_MICROSOFT_LOGIN_ENABLED` | `true` once Entra login is live | Baked into the browser bundle |

> The `NEXT_PUBLIC_*` values used to live in `.env.prod` on the box. They are
> inlined when the **image** is built, so now that CI builds the images they
> must be set here — setting them on the box has no effect. None are secrets;
> they are served to every visitor.

The old `AZURE_CLIENT_ID` / `AZURE_TENANT_ID` / `AZURE_SUBSCRIPTION_ID` /
`ACR_TOKEN_PASSWORD` secrets, the `ACR_REGISTRY` / `ACR_TOKEN_USER` variables,
and the long-unused `SSH_HOST` / `SSH_USER` / `SSH_KEY` secrets are no longer
read by anything and should be deleted.

### Backups

The `postgres-backup` sidecar dumps both databases nightly (`pg_dump -Fc`) to a
local Docker volume **and** copies each dump to S3. The local copy is the fast
restore path; the S3 copy is the one that survives losing the instance, which the
local copy explicitly does not. The IAM user behind the upload can only
`s3:PutObject` — it cannot list, read or delete — so a compromised box cannot
shred the backups it has already written.

Retention is **14 days** in both places, which is what
[the privacy policy](frontend/app/privacy/page.tsx) promises users about deleted
records lingering in backups. Do not raise one without the other.

Restoring is `pg_restore`; verify it against a scratch Postgres occasionally,
because an untested backup is not a backup.

## Status vs. GN-1

Implemented: scan + health endpoints, ClamAV integration, type/size guards
(400/413/415), OpenAPI docs, responsive marketing site with a live "try a scan"
page, **per-client rate limiting + scan concurrency caps** (429 on breach), the
**commercial foundation** — accounts, hashed API keys, plan-based limits, and
monthly usage metering with quota enforcement (Postgres + Flyway) — a self-serve
dashboard, and **Microsoft Marketplace billing** with Entra sign-in (disabled
until credentialed).

Outstanding: the Partner Center offer itself — payout/tax profile, listing
assets, and certification are account-level work no code can do. See
[`docs/marketplace-plan.md`](docs/marketplace-plan.md).

## License

geneav's own code (frontend and backend) is **proprietary — all rights reserved**.
See [`LICENSE`](./LICENSE).

geneav uses [ClamAV](https://www.clamav.net/) as its detection engine. ClamAV is
licensed under the **GNU GPL v2**. geneav does **not** link `libclamav`; the
backend talks to the `clamd` daemon only over a network socket (INSTREAM), which
is the integration model ClamAV documents for commercial and closed-source
software. The GPL therefore applies to ClamAV itself and does not extend to
geneav's application code. Attribution and the full rationale are in
[`NOTICE`](./NOTICE); if you redistribute the ClamAV container, keep its GPL
notices and source pointer intact.
