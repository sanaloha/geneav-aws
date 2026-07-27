# Inspecting the production database

How to reach the Postgres behind https://geneav.com to answer a support question or
diagnose a bug. Read-only by default — see [Safety](#safety) before running anything
that writes.

## What you are connecting to

| | |
|---|---|
| Host | AWS Lightsail instance `geneav` (us-east-1), reached as SSM managed node `mi-…` |
| Container | `geneav-postgres` (postgres:16-alpine) |
| Database / user | `geneav` / `geneav` |
| Password | `POSTGRES_PASSWORD` in `/home/ubuntu/geneav/.env.prod` (root-owned, `0600`) |
| Data | the `geneav_postgres-data` Docker volume — persists across redeploys |

Tables: `account`, `api_key`, `password_reset_token`, `usage_counter`,
`marketplace_subscription`, `flyway_schema_history`.

The database name and user are the Compose defaults; `.env.prod` overrides neither.

> **There is no SSH.** The instance firewall has no port 22 rule — deploys go through
> AWS Systems Manager, and so does interactive access. Find the node id with:
> ```bash
> aws ssm describe-instance-information --region us-east-1 \
>   --query 'InstanceInformationList[].[InstanceId,ComputerName,PingStatus]' --output table
> ```

## Route A — psql in the container

The normal route. Needs no password: the connection is over a unix socket inside the
container, which Postgres trusts.

```bash
aws ssm start-session --target <mi-...> --region us-east-1
sudo docker exec -it geneav-postgres psql -U geneav -d geneav
```

Session Manager lands you as `ssm-user`, which is **not** in the `docker` group — hence
the `sudo`. (`ubuntu` is in the group, if you switch to it.)

Useful meta-commands:

| Command | Does |
|---|---|
| `\dt` | List tables |
| `\d account` | Describe a table: columns, indexes, foreign keys |
| `\x` | Toggle expanded output — much easier on wide rows |
| `\q` | Quit |

For a single query without an interactive session, straight from your laptop:

```bash
aws ssm send-command --region us-east-1 \
  --instance-ids <mi-...> --document-name AWS-RunShellScript \
  --parameters 'commands=["docker exec geneav-postgres psql -U geneav -d geneav -c \"\\dt\""]' \
  --query 'Command.CommandId' --output text
# then read the output back:
aws ssm get-command-invocation --region us-east-1 \
  --command-id <id> --instance-id <mi-...> --query StandardOutputContent --output text
```

## Route B — port forward for a GUI client

Postgres is **not** published on the host at all any more (only Caddy's 80/443 and a
loopback-bound 8080 are), so there is nothing on the host to forward to directly.
Publish it temporarily inside a session, or forward through Session Manager:

```bash
# expose 5432 on the box's loopback for the duration of your session
sudo docker exec -d geneav-postgres true   # (container is already running)
# then, from your laptop:
aws ssm start-session --region us-east-1 --target <mi-...> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["5432"],"localPortNumber":["15432"]}'
```

> The port-forwarding document forwards to a port **on the instance**, so this only works
> while something is listening there. If nothing is, add a temporary
> `-p 127.0.0.1:5432:5432` publish to the postgres container, or just use Route A —
> which is the reason Route A is the normal route.

Point pgAdmin / DBeaver / DataGrip at `localhost:15432`, database `geneav`, user `geneav`.
This route **does** need the password, since it is a TCP rather than socket connection:

```bash
sudo grep POSTGRES_PASSWORD /home/ubuntu/geneav/.env.prod
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
  sudo docker exec geneav-postgres pg_dump -U geneav geneav | gzip > ~/geneav-$(date +%F).sql.gz
  ```
  There is also a nightly automatic dump (`geneav-postgres-backup`) to a local volume and to
  S3, retained 14 days in both — but take your own before touching anything, rather than
  hoping last night's is recent enough.
- **Schema changes belong in a Flyway migration** under
  `backend/src/main/resources/db/migration/`, never in a live psql session. A manual
  `alter table` drifts from the repo, is not reproduced by the next deploy, and can fail
  startup — the backend runs with `ddl-auto: validate`.
