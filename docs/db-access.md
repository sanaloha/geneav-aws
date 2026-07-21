# Inspecting the production database

How to reach the Postgres behind https://geneav.com to answer a support question or
diagnose a bug. Read-only by default — see [Safety](#safety) before running anything
that writes.

## What you are connecting to

| | |
|---|---|
| VM | `geneav-vm` in resource group `GENEAV-RG`, `172.190.148.55` |
| Container | `geneav-postgres` (postgres:16-alpine) |
| Database / user | `geneav` / `geneav` |
| Password | `POSTGRES_PASSWORD` in `~/geneav/.env.prod` on the VM (root-owned, `0600`) |
| Data | the `geneav_postgres-data` Docker volume — persists across redeploys |

Tables: `account`, `api_key`, `password_reset_token`, `usage_counter`,
`flyway_schema_history`.

The database name and user are the Compose defaults; `.env.prod` overrides neither.

## Route A — psql in the container

The normal route. Needs no password: the connection is over a unix socket inside the
container, which Postgres trusts.

```bash
az vm start -g GENEAV-RG -n geneav-vm     # only if the nightly shutdown deallocated it
ssh azureuser@172.190.148.55
docker exec -it geneav-postgres psql -U geneav -d geneav
```

Docker needs no `sudo` here — `azureuser` is in the `docker` group.

Useful meta-commands:

| Command | Does |
|---|---|
| `\dt` | List tables |
| `\d account` | Describe a table: columns, indexes, foreign keys |
| `\x` | Toggle expanded output — much easier on wide rows |
| `\q` | Quit |

For a single query without an interactive session, straight from your laptop:

```bash
ssh azureuser@172.190.148.55 \
  "docker exec geneav-postgres psql -U geneav -d geneav -c '\dt'"
```

## Route B — SSH tunnel for a GUI client

Port 5432 is bound on the VM host, but the NSG only admits inbound 22/80/443, so it is
not reachable directly. Tunnel it instead:

```bash
ssh -L 15432:localhost:5432 azureuser@172.190.148.55
```

Leave that session open and point pgAdmin / DBeaver / DataGrip at `localhost:15432`,
database `geneav`, user `geneav`. This route **does** need the password, since it is a
TCP rather than socket connection:

```bash
sudo grep POSTGRES_PASSWORD ~/geneav/.env.prod
```

## Queries that have earned their keep

Accounts, and whether each can sign in with a password:

```sql
select id, email, auth_provider, status,
       (password_hash is null) as passwordless, created_at
from account
order by created_at desc;
```

Outstanding and recent password resets — an empty result while a user insists they
requested one means no token was ever issued, which points at the application rather
than at mail delivery:

```sql
select a.email, t.created_at, t.expires_at, t.used_at
from password_reset_token t
join account a on a.id = t.account_id
order by t.created_at desc;
```

Pair these with the application's own account of events:

```bash
docker logs geneav-backend 2>&1 | grep -iE 'mail|reset' | tail -20
```

## Safety

This is live customer data.

- Wrap exploratory work in `begin;` … `rollback;` so a stray statement cannot land.
- Take a backup before any write:
  ```bash
  docker exec geneav-postgres pg_dump -U geneav geneav | gzip > ~/geneav-$(date +%F).sql.gz
  ```
- **Schema changes belong in a Flyway migration** under
  `backend/src/main/resources/db/migration/`, never in a live psql session. A manual
  `alter table` drifts from the repo, is not reproduced by the next deploy, and can fail
  startup — the backend runs with `ddl-auto: validate`.
