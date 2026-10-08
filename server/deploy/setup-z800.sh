#!/usr/bin/env bash
# Install Archerypal relay on z800 behind existing nginx + DuckDNS HTTPS.
# Run on z800: sudo bash setup-z800.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PUBLIC_HOST="${PUBLIC_HOST:-universeconflict.duckdns.org}"
SRC_DIR="${SRC_DIR:-$SCRIPT_DIR}"
BIN="${BIN:-$SCRIPT_DIR/archerypal-server-linux-amd64}"
NGINX_SNIPPET="/etc/nginx/snippets/archerypal-location.conf"
NGINX_SITE="/etc/nginx/sites-enabled/universeconflict.duckdns.org.conf"
WS_MAP="/etc/nginx/conf.d/archerypal-ws-map.conf"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

if [[ ! -f "$BIN" ]]; then
  echo "Missing binary: $BIN" >&2
  exit 1
fi

if ! id archerypal &>/dev/null; then
  useradd --system --home /var/lib/archerypal --shell /usr/sbin/nologin archerypal
fi

install -d -o archerypal -g archerypal /opt/archerypal/bin /opt/archerypal/migrations /var/lib/archerypal
install -m 755 "$BIN" /opt/archerypal/bin/archerypal-server
install -m 644 "$SCRIPT_DIR/migrations/"*.sql /opt/archerypal/migrations/
install -m 644 "$SCRIPT_DIR/archerypal.service" /etc/systemd/system/archerypal.service
install -m 644 "$SCRIPT_DIR/nginx-archerypal-location.conf" "$NGINX_SNIPPET"
install -m 644 "$SCRIPT_DIR/nginx-archerypal-http.conf" /etc/nginx/conf.d/archerypal-http.conf

cat > "$WS_MAP" <<'EOF'
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
EOF

install -d -m 750 -o root -g archerypal /etc/archerypal

if [[ ! -f /etc/archerypal/config.env ]]; then
  JWT_SECRET="$(openssl rand -hex 32)"
  cat > /etc/archerypal/config.env <<EOF
LISTEN_ADDR=127.0.0.1:8080
DB_PATH=/var/lib/archerypal/archerypal.db
JWT_SECRET=${JWT_SECRET}
MAX_ROOMS=32
ROOM_TTL_MINUTES=30
MIGRATIONS_DIR=/opt/archerypal/migrations
PUBLIC_WS_BASE=wss://${PUBLIC_HOST}
ENV=production
EOF
else
  if ! grep -q '^PUBLIC_WS_BASE=' /etc/archerypal/config.env; then
    echo "PUBLIC_WS_BASE=wss://${PUBLIC_HOST}" >> /etc/archerypal/config.env
  else
    sed -i "s|^PUBLIC_WS_BASE=.*|PUBLIC_WS_BASE=wss://${PUBLIC_HOST}|" /etc/archerypal/config.env
  fi
fi

chmod 640 /etc/archerypal/config.env
chown root:archerypal /etc/archerypal/config.env

chown -R archerypal:archerypal /var/lib/archerypal

if ! grep -q 'archerypal-location.conf' "$NGINX_SITE"; then
  sed -i '/strato-api-location.conf/a\    include /etc/nginx/snippets/archerypal-location.conf;' "$NGINX_SITE"
fi

nginx -t
systemctl daemon-reload
systemctl enable archerypal
systemctl restart archerypal
systemctl reload nginx

echo ""
echo "Archerypal installed on z800."
echo "  Health: curl -s https://${PUBLIC_HOST}/health"
echo "  App MATCH_SERVER_URL: https://${PUBLIC_HOST}"
systemctl --no-pager status archerypal --lines=3
