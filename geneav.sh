#!/usr/bin/env bash
# Start/stop the local geneav stack (clamav + backend + frontend).
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

API_BASE="http://localhost:8080"
FRONTEND_URL="http://localhost:3000"
# ClamAV downloads its signature DB on first boot, and the backend only starts
# once clamd reports healthy, so a cold start is much slower than a warm one.
HEALTH_TIMEOUT="${GENEAV_HEALTH_TIMEOUT:-240}"

usage() {
  cat <<EOF
Usage: ./geneav.sh <command>

Commands:
  start        Build if needed, start the stack, wait until the API is healthy
  stop         Stop and remove containers (keeps the ClamAV signature DB)
  restart      stop, then start
  status       Show container status and backend health
  logs [svc]   Follow logs, all services or one of: clamav backend frontend
  reset        Stop and delete the ClamAV signature DB volume (forces re-download)
  scan <file>  Scan a file through the running API (needs GENEAV_API_KEY)
  help         Show this message

Env:
  GENEAV_HEALTH_TIMEOUT  Seconds to wait for health on start (default 240)
  GENEAV_API_KEY         gav_live_... key used by 'scan'. The API has no
                         anonymous tier; mint one with:
                           curl -sX POST $API_BASE/api/v1/signup \\
                             -H 'Content-Type: application/json' \\
                             -d '{"email":"you@example.com"}'
EOF
}

compose() { docker compose "$@"; }

require_docker() {
  if ! docker version >/dev/null 2>&1; then
    echo "error: the Docker daemon is not reachable. Is Docker Desktop running?" >&2
    exit 1
  fi
}

health_json() { curl -fsS --max-time 5 "$API_BASE/api/v1/health" 2>/dev/null; }

wait_for_health() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while ((SECONDS < deadline)); do
    if health_json >/dev/null; then return 0; fi
    printf '.' >&2
    sleep 2
  done
  return 1
}

cmd_start() {
  require_docker
  compose up --build -d
  printf 'waiting for the API to come up ' >&2
  if wait_for_health; then
    printf ' up\n\n' >&2
    compose ps --format '{{.Name}}\t{{.Status}}'
    echo
    echo "health:   $(health_json)"
    echo
    echo "Frontend: $FRONTEND_URL"
    echo "API:      $API_BASE/api/v1/scan"
    echo "Docs:     $API_BASE/docs"
  else
    printf ' timed out after %ss\n\n' "$HEALTH_TIMEOUT" >&2
    echo "Recent backend logs:" >&2
    compose logs --tail 40 backend >&2
    exit 1
  fi
}

cmd_stop() {
  require_docker
  compose down
}

cmd_status() {
  require_docker
  compose ps --format '{{.Name}}\t{{.Status}}\t{{.Ports}}'
  echo
  local h
  if h="$(health_json)"; then
    echo "health: $h"
  else
    echo "health: unreachable at $API_BASE/api/v1/health"
  fi
}

cmd_logs() {
  require_docker
  if [ "$#" -gt 0 ]; then compose logs -f --tail 100 "$@"; else compose logs -f --tail 100; fi
}

cmd_reset() {
  require_docker
  # -v drops the clamav-db volume; the next start re-downloads the signature DB.
  compose down -v
}

cmd_scan() {
  local file="${1:-}"
  if [ -z "$file" ]; then
    echo "usage: ./geneav.sh scan <file>" >&2
    exit 1
  fi
  if [ ! -f "$file" ]; then
    echo "error: no such file: $file" >&2
    exit 1
  fi
  if [ -z "${GENEAV_API_KEY:-}" ]; then
    cat >&2 <<EOF
error: set GENEAV_API_KEY first — /api/v1/scan requires an API key.

  export GENEAV_API_KEY=\$(curl -sX POST $API_BASE/api/v1/signup \\
    -H 'Content-Type: application/json' -d '{"email":"you@example.com"}' \\
    | sed -n 's/.*"apiKey":"\([^"]*\)".*/\1/p')
EOF
    exit 1
  fi
  curl -fsS -H "Authorization: Bearer ${GENEAV_API_KEY}" \
    -F "file=@${file}" "$API_BASE/api/v1/scan"
  echo
}

case "${1:-help}" in
  start)   shift; cmd_start "$@" ;;
  stop)    shift; cmd_stop "$@" ;;
  restart) shift; cmd_stop; cmd_start ;;
  status)  shift; cmd_status "$@" ;;
  logs)    shift; cmd_logs "$@" ;;
  reset)   shift; cmd_reset "$@" ;;
  scan)    shift; cmd_scan "$@" ;;
  help|-h|--help) usage ;;
  *) echo "unknown command: $1" >&2; echo >&2; usage >&2; exit 1 ;;
esac
