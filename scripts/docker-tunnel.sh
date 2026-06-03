#!/usr/bin/env bash
# Forwards myserver's Docker socket and container ports to localhost via SSH.
# Run once per session before starting the app.
set -euo pipefail

if lsof -i :2375 -sTCP:LISTEN -t >/dev/null 2>&1; then
  echo "Docker tunnel already active on port 2375"
  exit 0
fi

echo "Establishing Docker tunnel to myserver..."
ssh -NfL "localhost:2375:/var/run/docker.sock" \
    -L 4566:localhost:4566 \
    -L 5433:localhost:5433 \
    mehdi@myserver
echo "Tunnel active — Docker on tcp://localhost:2375, ports 4566 and 5433 forwarded"
