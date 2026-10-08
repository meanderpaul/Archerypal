#!/usr/bin/env bash
set -euo pipefail

# Wait for tailscaled to finish connecting before enabling Funnel.
# Without this, boot can hit "unexpected state: NoState" and leave the public URL down.
max_attempts=60
for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if tailscale status --self >/dev/null 2>&1; then
        exec tailscale funnel --bg http://127.0.0.1:8080
    fi
    sleep 2
done

echo "tailscale did not become ready within $((max_attempts * 2))s" >&2
exit 1
