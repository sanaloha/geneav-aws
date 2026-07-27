#!/usr/bin/env bash
#
# Measures how many scans one box actually sustains.
#
# docs/business-case.md §5.6 has carried an open question since it was written:
# "the number of Pro customers one box supports is unknown". This answers it with
# a number instead of an estimate.
#
# WHAT IS BEING MEASURED
#
# The binding constraint is not CPU, it is `scan-max-concurrent` (default 4) in
# application.yml — a global semaphore that exists to protect ClamAV's memory
# footprint. With N concurrent slots and a mean scan time of T seconds, sustained
# throughput cannot exceed N/T scans per second no matter how many clients ask.
# On top of that, `scan-acquire-timeout-ms` (default 2000) means a request that
# cannot get a slot within 2s is REJECTED rather than queued indefinitely. So the
# useful output is: throughput, latency at the tail, and the point at which
# requests start being turned away.
#
# BEFORE YOU RUN IT
#
# The per-IP token bucket (10 requests/minute for anonymous scans) will throttle
# this long before the semaphore does, and you would end up benchmarking the rate
# limiter. Relax it for the run:
#
#   GENEAV_RATELIMIT_ENABLED=false
#
# The script counts 429s separately and warns loudly if it sees them, so a
# forgotten limiter shows up as an obviously wrong result rather than a quietly
# wrong one.
#
# Run against localhost on the box itself, not through Caddy — the proxy applies
# its own 20/minute per-IP limit.
#
# USAGE
#   scripts/benchmark-scan.sh [-u URL] [-c CONCURRENCY] [-n REQUESTS] [-s SIZE_KB] [-k API_KEY]
#
#   -u  base URL              (default http://localhost:8080)
#   -c  concurrent clients    (default 8 — deliberately above the semaphore)
#   -n  total requests        (default 100)
#   -s  payload size in KB    (default 256)
#   -k  API key, if you want to test the authenticated path
#
set -euo pipefail

URL="http://localhost:8080"
CONCURRENCY=8
REQUESTS=100
SIZE_KB=256
API_KEY=""

while getopts "u:c:n:s:k:h" opt; do
  case "$opt" in
    u) URL="$OPTARG" ;;
    c) CONCURRENCY="$OPTARG" ;;
    n) REQUESTS="$OPTARG" ;;
    s) SIZE_KB="$OPTARG" ;;
    k) API_KEY="$OPTARG" ;;
    h) sed -n '2,48p' "$0"; exit 0 ;;
    *) echo "see -h" >&2; exit 2 ;;
  esac
done

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Random bytes rather than a real document: incompressible, matches no signature,
# and so exercises the full scan path without a short-circuit on an early hit.
payload="$work/payload.bin"
dd if=/dev/urandom of="$payload" bs=1024 count="$SIZE_KB" 2>/dev/null

auth=()
AUTH_HEADER=""
if [ -n "$API_KEY" ]; then
  auth=(-H "Authorization: Bearer $API_KEY")
  AUTH_HEADER="Authorization: Bearer $API_KEY"
fi

echo "geneav scan benchmark"
echo "  target       $URL"
echo "  payload      ${SIZE_KB} KB of random bytes"
echo "  requests     $REQUESTS at concurrency $CONCURRENCY"
echo "  auth         ${API_KEY:+API key}${API_KEY:-anonymous}"
echo

# Warm-up: the first scan after a restart pays JIT and connection setup costs
# that would otherwise land in the p99 and be mistaken for a capacity limit.
echo "warming up..."
for _ in 1 2 3; do
  curl -s -o /dev/null --max-time 60 -X POST "$URL/api/v1/scan" \
    -F "file=@$payload;filename=warmup.bin" "${auth[@]}" || true
done

echo "running..."
start=$(date +%s.%N)

seq "$REQUESTS" | xargs -P "$CONCURRENCY" -I{} \
  curl -s -o /dev/null -w '%{http_code} %{time_total}\n' \
    --max-time 60 \
    ${AUTH_HEADER:+-H "$AUTH_HEADER"} \
    -X POST "$URL/api/v1/scan" \
    -F "file=@$payload;filename=benchmark.bin" \
  > "$work/results.txt" 2>/dev/null || true

end=$(date +%s.%N)
elapsed=$(awk "BEGIN{printf \"%.3f\", $end - $start}")

ok=$(awk '$1==200' "$work/results.txt" | wc -l | tr -d ' ')
throttled=$(awk '$1==429' "$work/results.txt" | wc -l | tr -d ' ')
rejected=$(awk '$1==503' "$work/results.txt" | wc -l | tr -d ' ')
other=$(awk '$1!=200 && $1!=429 && $1!=503' "$work/results.txt" | wc -l | tr -d ' ')

awk '$1==200 {print $2}' "$work/results.txt" | sort -n > "$work/times.txt"

pct() { # pct <fraction>
  local n; n=$(wc -l < "$work/times.txt" | tr -d ' ')
  [ "$n" -eq 0 ] && { echo "n/a"; return; }
  local i; i=$(awk "BEGIN{i=int($1*$n); print (i<1)?1:i}")
  awk "NR==$i{printf \"%.3f\", \$1}" "$work/times.txt"
}

echo
echo "results"
echo "  elapsed          ${elapsed}s"
echo "  succeeded        $ok"
echo "  rate-limited     $throttled  (429)"
echo "  capacity-refused $rejected  (503 — could not acquire a scan slot in time)"
echo "  other failures   $other"
[ "$ok" -gt 0 ] && {
  echo "  throughput       $(awk "BEGIN{printf \"%.2f\", $ok/$elapsed}") scans/sec sustained"
  echo "  mean latency     $(awk '{t+=$1} END{printf "%.3f", t/NR}' "$work/times.txt")s"
  echo "  p50 / p95 / p99  $(pct 0.50)s / $(pct 0.95)s / $(pct 0.99)s"
  echo
  echo "  extrapolated     $(awk "BEGIN{printf \"%.0f\", ($ok/$elapsed)*86400*30}") scans/month at this rate"
}

[ "$throttled" -gt 0 ] && {
  echo
  echo "WARNING: $throttled requests were rate-limited (429). You are measuring the"
  echo "         token bucket, not the scanner. Re-run with GENEAV_RATELIMIT_ENABLED=false."
}
[ "$rejected" -gt 0 ] && {
  echo
  echo "NOTE: $rejected requests could not acquire a scan slot within"
  echo "      scan-acquire-timeout-ms and were refused. That is the semaphore doing"
  echo "      its job — it is the real capacity ceiling, and this is what saturation"
  echo "      looks like to a client."
}
exit 0
