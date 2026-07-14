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

## Status vs. GN-1

Implemented: scan + health endpoints, ClamAV integration, type/size guards
(400/413/415), OpenAPI docs, responsive marketing site with a live "try a scan" page.

Out of scope (per ticket): user accounts / API keys, billing, rate limiting.
