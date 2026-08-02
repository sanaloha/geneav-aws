#!/usr/bin/env bash
# Deploy a commit to the Lightsail box by pulling pre-built images from ECR.
#
# Everything goes through the AWS control plane (SSM Run Command), so this needs
# no inbound SSH: the SSM agent on the box polls outbound, and the Lightsail
# firewall has no port 22 rule at all. GitHub-hosted runners have dynamic egress
# IPs, so an allow-list was never going to work.
#
# HISTORY — why this does not ship source. Until July 2026 the whole git tree
# was base64'd into the deploy payload and the VM compiled both apps. That died
# when the payload hit 199 KB and the control plane silently truncated it, and
# it was a bad idea anyway: builds contended with live traffic. Images are now
# built in CI, pushed to ECR, and pulled here — the payload is a few KB
# regardless of repo size.
#
# Only three files are sent: the two compose files and the Caddyfile. They are
# configuration the box must have on disk, and they are small. Keep it that way
# — SSM's parameter ceiling is ~100 KB, TIGHTER than the Azure one that caused
# the original problem, so the guard below is not decorative.
#
# caddy/Dockerfile is no longer among them: the reverse proxy image is built in
# CI and pulled like the other two, so the box has no reason to hold a build
# context it will never use.
#
# Requires: aws CLI v2, already authenticated (configure-aws-credentials in CI,
# `aws configure`/SSO locally), and python3 for JSON encoding. Runs on Linux or
# WSL — a Windows-native aws CLI will not resolve the file:// paths used here.
#
# Usage: scripts/deploy-lightsail.sh [--dry-run] [<sha>]
set -euo pipefail

# Local values (account id, ECR host, mi- node id) live in .env.aws at the repo
# root. Gitignored, and absent in CI — which sets the same variables from
# repository secrets and variables, so the -f guard is what keeps both paths
# working from one set of names.
ENV_FILE="${GENEAV_ENV_FILE:-$(cd "$(dirname "$0")/.." && pwd)/.env.aws}"
if [ -f "$ENV_FILE" ]; then
  set -a; . "$ENV_FILE"; set +a
fi

REGION="${AWS_REGION:-us-east-1}"
NODE="${GENEAV_SSM_NODE:?set GENEAV_SSM_NODE (the mi-... managed node id)}"
REMOTE_DIR="${GENEAV_REMOTE_DIR:-/home/ubuntu/geneav}"
REGISTRY="${GENEAV_REGISTRY:?set GENEAV_REGISTRY (e.g. 123456789012.dkr.ecr.us-east-1.amazonaws.com)}"

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && { DRY_RUN=1; shift; }

cd "$(git rev-parse --show-toplevel)"
SHA="${1:-$(git rev-parse HEAD)}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "==> deploying $SHA from $REGISTRY to $NODE"

# Config the box needs on disk. Kept deliberately small — this is the only thing
# still travelling inline, and it must never grow into a size problem again.
CONFIG_B64="$(tar -czf - \
  docker-compose.yml docker-compose.prod.yml caddy/Caddyfile \
  | base64 -w0)"

# The remote script runs under dash (AWS-RunShellScript uses /bin/sh), NOT bash.
# No pipefail, no [[ ]], no <<<. Keep it POSIX.
{
  cat <<REMOTE_HEAD
set -eu
D=$REMOTE_DIR
SHA=$SHA
REGISTRY=$REGISTRY
REGION=$REGION
CONFIG_B64='$CONFIG_B64'
REMOTE_HEAD
  cat <<'REMOTE_BODY'
cd "$D"

echo "=== preflight ==="
if [ ! -f "$D/.env.prod" ]; then echo "FATAL: $D/.env.prod missing"; exit 1; fi
. "$D/.env.prod"

PREV="$(cat "$D/.deployed-sha" 2>/dev/null || echo none)"
echo "current: $PREV -> target: $SHA"

echo "=== refresh config ==="
# Config is replaced in place; there is no source tree to swap. Keep a copy so a
# failed deploy can put the old config back.
rm -rf "$D/.config-prev" && mkdir -p "$D/.config-prev"
for f in docker-compose.yml docker-compose.prod.yml caddy/Caddyfile; do
  if [ -f "$D/$f" ]; then
    mkdir -p "$D/.config-prev/$(dirname "$f")"
    cp "$D/$f" "$D/.config-prev/$f"
  fi
done
printf '%s' "$CONFIG_B64" | base64 -d | tar -xzf - -C "$D"

echo "=== login + pull ==="
# Preferred path: this box is an SSM hybrid managed node, so the agent keeps
# refreshed credentials for its activation role on disk and no registry key is
# stored here at all. The role is scoped to pull-only on the three repositories,
# so even a full compromise of the box cannot push a poisoned image.
#
# Fallback: an explicit pull-only IAM user key in .env.prod, for the case where
# the agent's credential refresh is not available.
if aws ecr get-login-password --region "$REGION" 2>/dev/null \
     | docker login "$REGISTRY" -u AWS --password-stdin >/dev/null 2>&1; then
  echo "registry login via SSM node role"
elif [ -n "${ECR_AWS_ACCESS_KEY_ID:-}" ] && [ -n "${ECR_AWS_SECRET_ACCESS_KEY:-}" ]; then
  AWS_ACCESS_KEY_ID="$ECR_AWS_ACCESS_KEY_ID" \
  AWS_SECRET_ACCESS_KEY="$ECR_AWS_SECRET_ACCESS_KEY" \
  aws ecr get-login-password --region "$REGION" \
    | docker login "$REGISTRY" -u AWS --password-stdin >/dev/null
  echo "registry login via ECR_AWS_* fallback keys"
else
  echo "FATAL: no ECR credentials — node role failed and ECR_AWS_* not set in .env.prod"
  exit 1
fi

export GENEAV_IMAGE_TAG="$SHA"
export GENEAV_REGISTRY="$REGISTRY"
COMPOSE="docker compose --env-file $D/.env.prod -f docker-compose.yml -f docker-compose.prod.yml"

if ! $COMPOSE pull backend frontend caddy; then
  echo "PULL_FAILED - images for $SHA are not in the registry; nothing changed"
  cp -r "$D/.config-prev/." "$D/" 2>/dev/null || true
  exit 1
fi

echo "=== up ==="
# --no-build is load-bearing: the base compose file still declares `build:` for
# local development, and compose merges it with the prod `image:`. Without this
# a missing image would silently trigger a source build that cannot work here.
if $COMPOSE up -d --no-build; then
  echo "UP_OK"
else
  echo "UP_FAILED - rolling back to $PREV"
  cp -r "$D/.config-prev/." "$D/" 2>/dev/null || true
  if [ "$PREV" != "none" ]; then
    GENEAV_IMAGE_TAG="$PREV" $COMPOSE up -d --no-build || true
  fi
  echo "ROLLED_BACK"
  exit 1
fi

echo "=== health ==="
# 8080 is published on 127.0.0.1 ONLY (see docker-compose.prod.yml) — bound to
# the loopback so it is unreachable from off-box regardless of firewall state,
# but still curl-able from here without going through Caddy and TLS.
#
# The budget depends on whether this is a first deploy. backend waits on
# `clamav: service_healthy`, and on a COLD box clamd must download the whole
# signature database before it answers — the compose healthcheck allows a 120s
# start period plus 5x30s of retries for exactly that reason. A flat 150s poll
# (inherited from the Azure script, where the box was always warm) times out
# while the stack is still legitimately starting.
#
# A first deploy has no previous SHA to roll back to, so patience costs nothing.
# A redeploy keeps the tighter budget, because there the whole point is to revert
# a bad release quickly.
if [ "$PREV" = "none" ]; then
  ATTEMPTS=120   # 10 min — cold start, includes the signature download
  echo "first deploy on this box: allowing 10 min for the ClamAV database download"
else
  ATTEMPTS=60    # 5 min — warm redeploy, images and signatures already local
fi
ok=0
i=1
while [ "$i" -le "$ATTEMPTS" ]; do
  if curl -fsS --max-time 5 http://127.0.0.1:8080/api/v1/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 5
  i=$((i + 1))
done
if [ "$ok" != "1" ]; then
  echo "HEALTH_FAILED - rolling back to $PREV"
  cp -r "$D/.config-prev/." "$D/" 2>/dev/null || true
  if [ "$PREV" != "none" ]; then
    GENEAV_IMAGE_TAG="$PREV" $COMPOSE up -d --no-build || true
  fi
  echo "ROLLED_BACK"
  exit 1
fi

echo "$SHA" > "$D/.deployed-sha"
docker image prune -f >/dev/null 2>&1 || true

echo "=== containers ==="
docker compose ps --format "{{.Name}}\t{{.Status}}"
echo "=== DEPLOY_COMPLETE $SHA ==="
REMOTE_BODY
} > "$work/remote.sh"

# SSM caps the whole parameter payload at ~100 KB and, like the control plane
# this replaced, does not always fail loudly when you exceed it. Refuse to send
# rather than find out.
python3 - "$work/remote.sh" > "$work/params.json" <<'PY'
import json, sys
script = open(sys.argv[1], encoding="utf-8").read()
json.dump({"commands": [script], "executionTimeout": ["1800"]}, sys.stdout)
PY

size=$(wc -c < "$work/params.json")
echo "==> ssm payload: $size bytes"
if [ "$size" -gt 92160 ]; then
  echo "==> ABORT: payload ${size}B exceeds the 90 KB guard (SSM ceiling ~100 KB)." >&2
  echo "    Something that is not configuration has crept into the tarball." >&2
  exit 1
fi

if [ "$DRY_RUN" = "1" ]; then
  echo "==> --dry-run: not invoking. Script written to stdout below."
  cat "$work/remote.sh"
  exit 0
fi

CMD_ID="$(aws ssm send-command \
  --region "$REGION" \
  --instance-ids "$NODE" \
  --document-name AWS-RunShellScript \
  --comment "geneav deploy $SHA" \
  --timeout-seconds 600 \
  --parameters "file://$work/params.json" \
  --query 'Command.CommandId' --output text)"
echo "==> ssm command $CMD_ID"

# send-command is asynchronous. Poll to a terminal state; the invocation can
# briefly not exist at all right after dispatch, which is not an error.
status=Pending
for _ in $(seq 1 240); do
  status="$(aws ssm get-command-invocation --region "$REGION" \
    --command-id "$CMD_ID" --instance-id "$NODE" \
    --query 'Status' --output text 2>/dev/null || echo Pending)"
  case "$status" in
    Success|Failed|Cancelled|TimedOut) break ;;
  esac
  sleep 5
done

out="$(aws ssm get-command-invocation --region "$REGION" \
  --command-id "$CMD_ID" --instance-id "$NODE" \
  --query 'StandardOutputContent' --output text 2>/dev/null || true)"
err="$(aws ssm get-command-invocation --region "$REGION" \
  --command-id "$CMD_ID" --instance-id "$NODE" \
  --query 'StandardErrorContent' --output text 2>/dev/null || true)"

echo "$out"
[ -n "$err" ] && printf '%s\n' "--- stderr ---" "$err" >&2

# Belt and braces: trust the completion marker, not just the status. The remote
# script rolls itself back on failure, and a rollback that succeeds still leaves
# the deploy unfinished.
if [ "$status" = "Success" ] && printf '%s' "$out" | grep -q "DEPLOY_COMPLETE $SHA"; then
  echo "==> deployed $SHA"
else
  echo "==> DEPLOY FAILED (status=$status, no completion marker for $SHA)" >&2
  exit 1
fi
