#!/usr/bin/env bash
# Wire Usage agent inside archerypal guest + optional chaos/comms checks.
set -euo pipefail

GUEST_NAME="${GUEST_NAME:-archerypal}"
DASHBOARD_URL="${DASHBOARD_URL:-http://10.89.0.1:8787}"
MONITOR_ROOT="${MONITOR_ROOT:-/opt/usage-monitor}"
HOST_ENV="${HOST_ENV:-/etc/usage-monitor-agent.env}"
PROXY_LISTEN="${PROXY_LISTEN:-127.0.0.1:18081}"

if [[ "$(id -u)" -ne 0 ]]; then exec sudo -E bash "$0" "$@"; fi
AGENT_TOKEN=$(grep -E '^AGENT_TOKEN=' "$HOST_ENV" | head -n1 | cut -d= -f2-)
[[ -n "$AGENT_TOKEN" ]] || { echo "AGENT_TOKEN missing" >&2; exit 1; }

# Ensure proxy on 18081 (avoid Funnel :8080)
incus config device remove "$GUEST_NAME" api-proxy 2>/dev/null || true
incus config device add "$GUEST_NAME" api-proxy proxy \
  listen=tcp:"$PROXY_LISTEN" connect=tcp:127.0.0.1:8080 bind=host

incus exec "$GUEST_NAME" -- mkdir -p /opt/usage-monitor/agent
incus file push "$MONITOR_ROOT/package.json" "$GUEST_NAME/opt/usage-monitor/package.json"
incus file push --recursive "$MONITOR_ROOT/agent" "$GUEST_NAME/opt/usage-monitor/"

incus exec "$GUEST_NAME" -- bash -c "
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
if ! command -v node >/dev/null 2>&1 || ! node -e 'process.exit(Number(process.versions.node.split(\".\")[0])>=20?0:1)'; then
  apt-get update -y
  apt-get install -y --no-install-recommends ca-certificates curl
  curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
  apt-get install -y --no-install-recommends nodejs
fi
cat >/etc/usage-monitor-agent.env <<EOF
USAGE_API_URL=${DASHBOARD_URL}
AGENT_TOKEN=${AGENT_TOKEN}
COLLECT_INTERVAL_SECONDS=60
HOST_KIND=guest
HOST_ID=archerypal-guest
MONITOR_GROUP_NAME=Archerypal
MONITOR_SERVICES=archerypal.service
EOF
chmod 600 /etc/usage-monitor-agent.env
cat >/etc/systemd/system/usage-agent.service <<EOF
[Unit]
Description=Usage Monitor Linux Agent (Archerypal guest)
After=network-online.target archerypal.service
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=/opt/usage-monitor
ExecStart=/usr/bin/node /opt/usage-monitor/agent/index.js
EnvironmentFile=/etc/usage-monitor-agent.env
Restart=always
RestartSec=10
StartLimitIntervalSec=0
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now usage-agent.service
"

# Apply unattended upgrades inside guest if script present
if [[ -f /opt/usage-monitor/deploy/incus/11-enable-unattended-upgrades.sh ]]; then
  bash /opt/usage-monitor/deploy/incus/11-enable-unattended-upgrades.sh "$GUEST_NAME" || true
fi

echo "==> Health"
curl -fsS "http://${PROXY_LISTEN}/health" || true
incus exec "$GUEST_NAME" -- systemctl --no-pager --full status usage-agent | head -n 15 || true
echo "Wire complete for archerypal-guest"
