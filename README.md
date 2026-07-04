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

```bash
# clean file
curl -F "file=@README.md;type=text/plain" http://localhost:8080/api/v1/scan

# EICAR test virus (harmless, triggers a detection)
printf 'X5O!P%%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*' > eicar.txt
curl -F "file=@eicar.txt;type=text/plain" http://localhost:8080/api/v1/scan
```

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

## Status vs. GN-1

Implemented: scan + health endpoints, ClamAV integration, type/size guards
(400/413/415), OpenAPI docs, responsive marketing site with a live "try a scan" page.

Out of scope (per ticket): user accounts / API keys, billing, rate limiting.
