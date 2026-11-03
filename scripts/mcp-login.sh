#!/usr/bin/env bash
# Logs in to the BlitzPay MCP server and opens a persistent mcpc session.
# Usage: scripts/mcp-login.sh [staging|local|<url>]   (default: staging)
# Asks for the OAuth client id/secret (or reads MCP_CLIENT_ID / MCP_CLIENT_SECRET).
# The session survives this script; continue any time with: mcpc @blitzpay tools-list
set -euo pipefail

case "${1:-staging}" in
  staging) URL="https://api-blitzpay-staging.zackpay.de/mcp" ;;
  local)   URL="http://localhost:8080/mcp" ;;
  *)       URL="$1" ;;
esac
SESSION="@blitzpay"

# Session still alive -> nothing to do, just continue.
if mcpc "$SESSION" >/dev/null 2>&1; then
  echo "Session $SESSION is active. Continue with: mcpc $SESSION tools-list"
  exit 0
fi

CLIENT_ID="${MCP_CLIENT_ID:-}"
CLIENT_SECRET="${MCP_CLIENT_SECRET:-}"
[[ -n "$CLIENT_ID" ]]     || read -rp  "Client ID: " CLIENT_ID
[[ -n "$CLIENT_SECRET" ]] || { read -rsp "Client secret: " CLIENT_SECRET; echo; }

# ponytail: secret passes as a CLI arg (visible in `ps` while login runs); fine on a dev laptop.
mcpc login "$URL" --client-id "$CLIENT_ID" --client-secret "$CLIENT_SECRET" ${MCP_GRANT:+--grant "$MCP_GRANT"}
mcpc close "$SESSION" >/dev/null 2>&1 || true   # drop expired session if any
mcpc connect "$URL" "$SESSION"
mcpc "$SESSION"
echo "Continue later with: mcpc $SESSION <command>"
