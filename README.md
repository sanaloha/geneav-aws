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
email + password (Google login coming next) and manage keys + usage from a browser
session (secure HttpOnly cookie) — no need to handle a raw key yourself.

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
and usage live in **PostgreSQL** (schema managed by Flyway); billing/payment
integration is a later slice.

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

> In production, set a strong `POSTGRES_PASSWORD` in the VM's `.env.prod` (the prod
> compose refuses to start without it). The Postgres data lives in the
> `postgres-data` Docker volume, which survives redeploys.

> The chat assistant needs `OPENAI_API_KEY`. Locally, export it before starting
> the stack (e.g. `OPENAI_API_KEY=sk-... docker compose up`); in production put it
> in the VM's gitignored `.env.prod`. Never commit a real key — `.env.prod.example`
> ships only a placeholder.

## Deployment

Production runs on a single Azure VM (`geneav-vm` / `GENEAV-RG`) behind Caddy.
Pushing to `main` deploys automatically once CI passes.

Deploys go through the **Azure control plane** (`az vm run-command`), not SSH.
The VM's NSG only allows port 22 from a couple of fixed addresses, so a
GitHub-hosted runner (dynamic egress IP) could never reach it — and the VM holds
no credentials for this private repo, so it cannot `git pull` either. Instead
`scripts/deploy-vm.sh` packages the commit with `git archive` and hands it to
the VM, which unpacks it and rebuilds.

### Deploy manually

```bash
az login
scripts/deploy-vm.sh              # deploy the current commit
scripts/deploy-vm.sh --dry-run    # print the remote script, change nothing
```

The script keeps the previous tree at `/home/azureuser/geneav-old-<timestamp>`,
rolls back automatically if the build fails, and carries the VM's gitignored
`.env.prod` across untouched.

### One-time CI setup

The `deploy` job authenticates with **OIDC**, so no long-lived secret is stored
in GitHub. Create an Entra app federated to this repo and grant it rights on the
VM:

```bash
# 1. app registration
az ad app create --display-name geneav-deploy
APP_ID=$(az ad app list --display-name geneav-deploy --query '[0].appId' -o tsv)
az ad sp create --id "$APP_ID"

# 2. trust GitHub Actions on main (no secret involved)
az ad app federated-credential create --id "$APP_ID" --parameters '{
  "name": "geneav-main",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:sanaloha/geneav:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'

# 3. least privilege: run commands on the one VM, nothing else
SUB=$(az account show --query id -o tsv)
az role assignment create --assignee "$APP_ID" \
  --role "Virtual Machine Contributor" \
  --scope "/subscriptions/$SUB/resourceGroups/GENEAV-RG/providers/Microsoft.Compute/virtualMachines/geneav-vm"
```

Then add these under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `AZURE_CLIENT_ID` | `$APP_ID` from step 1 |
| `AZURE_TENANT_ID` | `az account show --query tenantId -o tsv` |
| `AZURE_SUBSCRIPTION_ID` | `az account show --query id -o tsv` |

The old `SSH_HOST` / `SSH_USER` / `SSH_KEY` secrets are no longer used and can
be deleted.

## Status vs. GN-1

Implemented: scan + health endpoints, ClamAV integration, type/size guards
(400/413/415), OpenAPI docs, responsive marketing site with a live "try a scan"
page, **per-client rate limiting + scan concurrency caps** (429 on breach), and
the **commercial foundation** — accounts, hashed API keys, plan-based limits, and
monthly usage metering with quota enforcement (Postgres + Flyway).

Out of scope for now: billing / payment processing (Stripe), a self-serve
dashboard UI. These build on the metering foundation above.

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
