#!/usr/bin/env bash
# Deploy the current git commit to the Azure VM.
#
# Everything goes through the Azure control plane (az vm run-command), so this
# needs no inbound SSH. That matters for two reasons:
#   * GitHub-hosted runners have dynamic egress IPs, and the VM's NSG only
#     allows SSH from a couple of fixed addresses — port 22 would time out.
#   * The VM has no credentials for this private repo, so it cannot pull. We
#     ship a git archive of the commit instead.
#
# Requires: az, already authenticated (azure/login in CI, `az login` locally).
#
# Usage: scripts/deploy-vm.sh [--dry-run]
set -euo pipefail

RG="${GENEAV_RG:-GENEAV-RG}"
VM="${GENEAV_VM:-geneav-vm}"
REMOTE_DIR="${GENEAV_REMOTE_DIR:-/home/azureuser/geneav}"
# RunShellScript rejects oversized scripts; stay well under the documented cap.
MAX_SCRIPT_BYTES="${GENEAV_MAX_SCRIPT_BYTES:-200000}"

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

cd "$(git rev-parse --show-toplevel)"
SHA="$(git rev-parse HEAD)"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "==> packaging $SHA"
# git archive ships tracked files only, which is the point: it is exactly the
# committed tree, with no local cruft and no gitignored secrets (.env.prod
# stays on the VM and is copied across below).
git archive --format=tar HEAD | gzip -9 > "$work/tree.tar.gz"

# Guard the mistake that broke a deploy once already: the Dockerfile copies
# frontend/public, but git does not track empty directories.
if ! tar -tzf "$work/tree.tar.gz" | grep -q '^frontend/public/'; then
  echo "FATAL: frontend/public/ is not in the archive; the frontend image will" >&2
  echo "       fail on 'COPY --from=build /app/public ./public'. Ensure the" >&2
  echo "       directory is tracked (frontend/public/.gitkeep)." >&2
  exit 1
fi

# The remote script runs under dash (RunShellScript uses /bin/sh), NOT bash.
# No pipefail, no [[ ]], no <<<. Keep it POSIX.
{
  cat <<REMOTE_HEAD
set -eu
D=$REMOTE_DIR
SHA=$SHA
REMOTE_HEAD
  cat <<'REMOTE_BODY'
TS=$(date +%Y%m%d-%H%M%S)
NEW="$D-new-$TS"
OLD="$D-old-$TS"

echo "=== preflight ==="
if [ ! -f "$D/.env.prod" ]; then echo "FATAL: $D/.env.prod missing"; exit 1; fi

echo "=== unpack $SHA ==="
mkdir -p "$NEW"
base64 -d <<'B64_EOF' | tar -xzf - -C "$NEW"
REMOTE_BODY

  base64 -w0 < "$work/tree.tar.gz"
  echo

  cat <<'REMOTE_TAIL'
B64_EOF

if [ ! -f "$NEW/docker-compose.prod.yml" ]; then echo "FATAL: unpack incomplete"; rm -rf "$NEW"; exit 1; fi
if [ ! -d "$NEW/frontend/public" ]; then echo "FATAL: frontend/public missing"; rm -rf "$NEW"; exit 1; fi
echo "unpacked $(find "$NEW" -type f | wc -l) files"

# .env.prod is gitignored, so it is not in the archive — carry it across.
cp "$D/.env.prod" "$NEW/.env.prod"
echo "$SHA" > "$NEW/.deployed-sha"

echo "=== swap (previous tree kept at $OLD) ==="
mv "$D" "$OLD"
mv "$NEW" "$D"
cd "$D"

echo "=== build + up ==="
if docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --build; then
  echo "BUILD_OK"
else
  echo "BUILD_FAILED - rolling back"
  cd "$(dirname "$D")"
  rm -rf "$D"
  mv "$OLD" "$D"
  cd "$D"
  # --build matters: a bare `up -d` would keep any image that rebuilt
  # successfully before the failure, leaving old and new code mixed.
  docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.prod.yml up -d --build || true
  echo "ROLLED_BACK"
  exit 1
fi

docker image prune -f >/dev/null 2>&1 || true

# Keep the two most recent rollback copies; drop the rest.
ls -1dt "$D"-old-* 2>/dev/null | tail -n +3 | xargs -r rm -rf

echo "=== containers ==="
docker compose ps --format "{{.Name}}\t{{.Status}}"
echo "=== DEPLOY_COMPLETE $SHA ==="
REMOTE_TAIL
} > "$work/remote.sh"

size=$(wc -c < "$work/remote.sh")
echo "==> remote script: $size bytes"
if [ "$size" -gt "$MAX_SCRIPT_BYTES" ]; then
  echo "FATAL: remote script is $size bytes (limit $MAX_SCRIPT_BYTES)." >&2
  echo "       The repo has outgrown inline delivery; push images to a registry instead." >&2
  exit 1
fi

if [ "$DRY_RUN" = "1" ]; then
  echo "==> --dry-run: not invoking. Script written to stdout below."
  cat "$work/remote.sh"
  exit 0
fi

echo "==> deploying to $VM ($RG)"
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
