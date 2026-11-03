#!/usr/bin/env bash
# Logs in to the BlitzPay MCP server and (re)opens an mcpc session.
# Usage: scripts/mcp-login.sh [staging|local|<url>]   (default: staging)
# Then:  mcpc @blitzpay tools-list
set -euo pipefail

case "${1:-staging}" in
  staging) URL="https://api-blitzpay-staging.zackpay.de/mcp" ;;
  local)   URL="http://localhost:8080/mcp" ;;
  *)       URL="$1" ;;
esac
SESSION="@blitzpay"

mcpc login "$URL"
mcpc close "$SESSION" >/dev/null 2>&1 || true   # drop expired session if any
mcpc connect "$URL" "$SESSION"
mcpc "$SESSION"
