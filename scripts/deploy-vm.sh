#!/usr/bin/env bash
# Deploy a commit to the Azure VM by pulling pre-built images from ACR.
#
# Everything goes through the Azure control plane (az vm run-command), so this
# needs no inbound SSH: GitHub-hosted runners have dynamic egress IPs and the
# VM's NSG only allows SSH from a couple of fixed addresses.
#
# HISTORY — why this no longer ships source. Until July 2026 the whole git tree
# was base64'd into the run-command script. That died when the Marketplace work
# pushed the payload to 199 KB: Azure returned "Enable succeeded" with EMPTY
# stdout and silently did nothing. The effective ceiling turned out to be well
# under the documented 256 KB, and trimming could not get back under it because
# the payload was application source. Images are now built in CI, pushed to
# ACR, and pulled here — the script is a few KB regardless of repo size, and
# the VM no longer compiles anything while serving live traffic.
#
# Only three files are sent now: the two compose files and the Caddyfile. They
# are configuration the VM must have on disk, and they are small.
#
# Requires: az, already authenticated (azure/login in CI, `az login` locally).
#
# Usage: scripts/deploy-vm.sh [--dry-run] [<sha>]
set -euo pipefail

RG="${GENEAV_RG:-GENEAV-RG}"
VM="${GENEAV_VM:-geneav-vm}"
REMOTE_DIR="${GENEAV_REMOTE_DIR:-/home/azureuser/geneav}"
REGISTRY="${GENEAV_REGISTRY:-geneavacr.azurecr.io}"

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && { DRY_RUN=1; shift; }

cd "$(git rev-parse --show-toplevel)"
SHA="${1:-$(git rev-parse HEAD)}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "==> deploying $SHA from $REGISTRY"

# Config the VM needs on disk. Kept deliberately small — this is the only thing
# still travelling inline, and it must never grow into a size problem again.
CONFIG_B64="$(tar -czf - \
  docker-compose.yml docker-compose.prod.yml caddy/Caddyfile caddy/Dockerfile \
  | base64 -w0)"

# The remote script runs under dash (RunShellScript uses /bin/sh), NOT bash.
# No pipefail, no [[ ]], no <<<. Keep it POSIX.
{
  cat <<REMOTE_HEAD
set -eu
D=$REMOTE_DIR
SHA=$SHA
REGISTRY=$REGISTRY
CONFIG_B64='$CONFIG_B64'
REMOTE_HEAD
  cat <<'REMOTE_BODY'
cd "$D"

echo "=== preflight ==="
if [ ! -f "$D/.env.prod" ]; then echo "FATAL: $D/.env.prod missing"; exit 1; fi

# Registry credentials live in .env.prod alongside the other secrets. The token
# is PULL-ONLY and scoped to these two repositories, so a VM compromise cannot
# push a poisoned image.
. "$D/.env.prod"
if [ -z "${ACR_TOKEN_USER:-}" ] || [ -z "${ACR_TOKEN_PASSWORD:-}" ]; then
  echo "FATAL: ACR_TOKEN_USER / ACR_TOKEN_PASSWORD missing from .env.prod"; exit 1
fi

PREV="$(cat "$D/.deployed-sha" 2>/dev/null || echo none)"
echo "current: $PREV -> target: $SHA"

echo "=== refresh config ==="
# Config is replaced in place; there is no tree to swap now that the source is
# gone. Keep a copy so a failed deploy can put the old config back.
rm -rf "$D/.config-prev" && mkdir -p "$D/.config-prev"
for f in docker-compose.yml docker-compose.prod.yml caddy/Caddyfile caddy/Dockerfile; do
  if [ -f "$D/$f" ]; then
    mkdir -p "$D/.config-prev/$(dirname "$f")"
    cp "$D/$f" "$D/.config-prev/$f"
  fi
done
printf '%s' "$CONFIG_B64" | base64 -d | tar -xzf - -C "$D"

echo "=== login + pull ==="
echo "$ACR_TOKEN_PASSWORD" | docker login "$REGISTRY" -u "$ACR_TOKEN_USER" --password-stdin >/dev/null

export GENEAV_IMAGE_TAG="$SHA"
export GENEAV_REGISTRY="$REGISTRY"
COMPOSE="docker compose --env-file $D/.env.prod -f docker-compose.yml -f docker-compose.prod.yml"

if ! $COMPOSE pull backend frontend; then
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
ok=0
i=1
while [ "$i" -le 30 ]; do
  if curl -fsS --max-time 5 http://localhost:8080/api/v1/health >/dev/null 2>&1; then ok=1; break; fi
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

size=$(wc -c < "$work/remote.sh")
echo "==> remote script: $size bytes"

if [ "$DRY_RUN" = "1" ]; then
  echo "==> --dry-run: not invoking. Script written to stdout below."
  cat "$work/remote.sh"
  exit 0
fi

out="$(az vm run-command invoke -g "$RG" -n "$VM" \
  --command-id RunShellScript --scripts "@$work/remote.sh" \
  --query 'value[0].message' -o tsv)"

echo "$out"

# `az vm run-command invoke` exits 0 as long as the *invocation* succeeded — it
# does not care whether the script itself failed. Check for the marker instead.
if printf '%s' "$out" | grep -q "DEPLOY_COMPLETE $SHA"; then
  echo "==> deployed $SHA"
else
  echo "==> DEPLOY FAILED (no completion marker for $SHA)" >&2
  exit 1
fi
