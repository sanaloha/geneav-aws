#!/usr/bin/env bash
# Replays a Microsoft Marketplace webhook notification against a running
# backend. Microsoft cannot reach localhost, so local end-to-end testing — and
# production debugging of a stored marketplace_event payload — both go through
# this script.
#
# The Authorization header carries a REAL Entra token minted with the
# fulfillment app's own credentials. Such a token has aud=<our client id>,
# tid=<our tenant> and appid=<our client id> — so for the replay to pass the
# webhook's JWT checks, run the backend with MARKETPLACE_RESOURCE_ID (the
# expected appid/azp) overridden to the client id used here:
#
#   GENEAV_MARKETPLACE_ENABLED=true \
#   MARKETPLACE_TENANT_ID=... MARKETPLACE_CLIENT_ID=... MARKETPLACE_CLIENT_SECRET=... \
#   MARKETPLACE_RESOURCE_ID=$MARKETPLACE_CLIENT_ID \      # test override, never in prod
#   ./geneav.sh start
#
# Usage:
#   scripts/replay-marketplace-webhook.sh payload.json [backend-url]
#
# Requires: curl, jq, and MARKETPLACE_TENANT_ID / MARKETPLACE_CLIENT_ID /
# MARKETPLACE_CLIENT_SECRET in the environment.

set -euo pipefail

PAYLOAD_FILE="${1:?usage: replay-marketplace-webhook.sh payload.json [backend-url]}"
BACKEND_URL="${2:-http://localhost:8080}"

: "${MARKETPLACE_TENANT_ID:?set MARKETPLACE_TENANT_ID}"
: "${MARKETPLACE_CLIENT_ID:?set MARKETPLACE_CLIENT_ID}"
: "${MARKETPLACE_CLIENT_SECRET:?set MARKETPLACE_CLIENT_SECRET}"

[ -f "$PAYLOAD_FILE" ] || { echo "payload file not found: $PAYLOAD_FILE" >&2; exit 1; }

echo "Minting an Entra token for tenant $MARKETPLACE_TENANT_ID ..." >&2
TOKEN=$(curl -sS -X POST \
  "https://login.microsoftonline.com/${MARKETPLACE_TENANT_ID}/oauth2/v2.0/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=${MARKETPLACE_CLIENT_ID}" \
  -d "client_secret=${MARKETPLACE_CLIENT_SECRET}" \
  -d "scope=${MARKETPLACE_CLIENT_ID}/.default" \
  | jq -r '.access_token')

[ -n "$TOKEN" ] && [ "$TOKEN" != "null" ] || { echo "token request failed" >&2; exit 1; }

echo "POST ${BACKEND_URL}/api/v1/marketplace/webhook" >&2
curl -sS -w '\nHTTP %{http_code}\n' \
  -X POST "${BACKEND_URL}/api/v1/marketplace/webhook" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data-binary "@${PAYLOAD_FILE}"
