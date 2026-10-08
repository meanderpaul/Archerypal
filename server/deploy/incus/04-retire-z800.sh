#!/usr/bin/env bash
# Retire Archerypal (and confirm no Critter) on z800 after RPIserver cutover.
set -euo pipefail
Z800_HOST="${Z800_HOST:-z800}"

ssh "$Z800_HOST" 'bash -s' <<'REMOTE'
set -euo pipefail
echo "==> Stopping Archerypal on z800"
sudo systemctl disable --now archerypal.service 2>/dev/null || true
sudo systemctl disable --now archerypal-shoot-sync.timer 2>/dev/null || true
sudo systemctl disable --now archerypal-shoot-sync.service 2>/dev/null || true
sudo systemctl disable --now tailscale-funnel-archerypal.service 2>/dev/null || true
sudo systemctl disable --now cloudflared-archerypal.service 2>/dev/null || true

echo "==> Confirm no Critter / Archerypal processes"
ps aux | grep -Ei 'archerypal|critter|tracker' | grep -v grep || echo "(none)"
ss -tlnp | grep -E ':8080|:18080' || echo "(no 8080/18080 listeners)"

echo "==> Leaving /var/lib/archerypal and /opt/archerypal in place as rollback copy"
ls -ld /var/lib/archerypal /opt/archerypal 2>/dev/null || true
echo "DONE"
REMOTE
