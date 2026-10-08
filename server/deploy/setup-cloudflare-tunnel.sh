#!/usr/bin/env bash
# Install Cloudflare Tunnel on z800 (or any Linux host) for api.archerypal.app → archerypal (localhost:8080).
#
# Recommended: Cloudflare Zero Trust dashboard (token install)
#   1. https://one.dash.cloudflare.com → Networks → Tunnels → Create
#   2. Name: archerypal
#   3. Public hostname: api.archerypal.app → http://127.0.0.1:8080
#   4. Copy the Linux install token, then on z800:
#        sudo ./setup-cloudflare-tunnel.sh --token 'eyJ...'
#
# Alternative: config file after `cloudflared tunnel login` + `cloudflared tunnel create`
#        sudo ./setup-cloudflare-tunnel.sh --config /etc/cloudflared/config.yml
#
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_HOST="${API_HOST:-api.archerypal.app}"
ORIGIN_URL="${ORIGIN_URL:-http://127.0.0.1:8080}"
WS_BASE="${WS_BASE:-wss://${API_HOST}}"

usage() {
  sed -n '2,12p' "$0"
  exit 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then usage; fi

install_cloudflared() {
  if command -v cloudflared >/dev/null 2>&1; then
    echo "cloudflared already installed: $(cloudflared --version)"
    return
  fi
  arch="$(uname -m)"
  case "$arch" in
    aarch64|arm64) asset=cloudflared-linux-arm64 ;;
    armv7l|armv6l) asset=cloudflared-linux-arm ;;
    x86_64) asset=cloudflared-linux-amd64 ;;
    *) echo "unsupported arch: $arch"; exit 1 ;;
  esac
  curl -fsSL -o /tmp/cloudflared "https://github.com/cloudflare/cloudflared/releases/latest/download/${asset}"
  chmod +x /tmp/cloudflared
  install -m 755 /tmp/cloudflared /usr/local/bin/cloudflared
  echo "installed $(cloudflared --version)"
}

configure_archerypal_origin() {
  local cfg=/etc/archerypal/config.env
  if [[ ! -f "$cfg" ]]; then
    echo "missing $cfg — install archerypal.service first"
    exit 1
  fi
  # Tunnel connects locally; no need to expose 8080 on Tailscale/LAN.
  if grep -q '^LISTEN_ADDR=' "$cfg"; then
    sed -i 's|^LISTEN_ADDR=.*|LISTEN_ADDR=127.0.0.1:8080|' "$cfg"
  else
    echo 'LISTEN_ADDR=127.0.0.1:8080' >>"$cfg"
  fi
  if grep -q '^PUBLIC_WS_BASE=' "$cfg"; then
    sed -i "s|^PUBLIC_WS_BASE=.*|PUBLIC_WS_BASE=${WS_BASE}|" "$cfg"
  else
    echo "PUBLIC_WS_BASE=${WS_BASE}" >>"$cfg"
  fi
  systemctl restart archerypal
  echo "archerypal: LISTEN_ADDR=127.0.0.1:8080 PUBLIC_WS_BASE=${WS_BASE}"
}

install_token_service() {
  local token="$1"
  install -d -m 755 /etc/cloudflared
  umask 077
  printf 'CLOUDFLARE_TUNNEL_TOKEN=%s\n' "$token" >/etc/cloudflared/tunnel.env
  chmod 600 /etc/cloudflared/tunnel.env
  install -m 644 "$SCRIPT_DIR/cloudflared-archerypal.service" /etc/systemd/system/cloudflared-archerypal.service
  systemctl daemon-reload
  systemctl enable cloudflared-archerypal
  systemctl restart cloudflared-archerypal
  sleep 2
  systemctl --no-pager status cloudflared-archerypal
}

install_config_service() {
  local config_path="$1"
  install -d -m 755 /etc/cloudflared
  if [[ "$config_path" != /etc/cloudflared/config.yml ]]; then
    install -m 644 "$config_path" /etc/cloudflared/config.yml
  fi
  cat >/etc/systemd/system/cloudflared-archerypal.service <<EOF
[Unit]
Description=Cloudflare Tunnel for Archerypal API
After=network-online.target archerypal.service
Wants=network-online.target
Requires=archerypal.service

[Service]
Type=notify
ExecStart=/usr/local/bin/cloudflared --no-autoupdate tunnel --config /etc/cloudflared/config.yml run
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable cloudflared-archerypal
  systemctl restart cloudflared-archerypal
  sleep 2
  systemctl --no-pager status cloudflared-archerypal
}

install_cloudflared

case "${1:-}" in
  --token)
    [[ -n "${2:-}" ]] || { echo "usage: $0 --token '<token>'"; exit 1; }
    configure_archerypal_origin
    install_token_service "$2"
    ;;
  --config)
    [[ -n "${2:-}" ]] || { echo "usage: $0 --config /path/to/config.yml"; exit 1; }
    configure_archerypal_origin
    install_config_service "$2"
    ;;
  *)
    echo "cloudflared installed ($(cloudflared --version))."
    echo "archerypal left unchanged until tunnel is connected."
    echo "Next: create tunnel in Cloudflare dashboard, then run:"
    echo "  sudo $0 --token '<paste-token-here>'"
    exit 0
    ;;
esac

echo ""
echo "Verify (from any network):"
echo "  curl -s https://${API_HOST}/health"
