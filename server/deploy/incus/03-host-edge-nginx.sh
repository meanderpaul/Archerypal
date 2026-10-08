#!/usr/bin/env bash
# Install Archerypal nginx edge on RPIserver (TLS already present for DuckDNS sites).
# Uses host proxy port 18081 to avoid colliding with existing Funnel on 127.0.0.1:8080.
set -euo pipefail

PUBLIC_HOST="${PUBLIC_HOST:-universeconflict.duckdns.org}"
UPSTREAM="${UPSTREAM:-127.0.0.1:18081}"
SNIPPET="/etc/nginx/snippets/archerypal-location.conf"
HTTP_CONF="/etc/nginx/conf.d/archerypal-http.conf"
WS_MAP="/etc/nginx/conf.d/archerypal-ws-map.conf"
SITE_CANDIDATES=(
  "/etc/nginx/sites-enabled/${PUBLIC_HOST}.conf"
  "/etc/nginx/sites-enabled/universe"
  "/etc/nginx/sites-enabled/default"
)

if [[ "$(id -u)" -ne 0 ]]; then exec sudo -E bash "$0" "$@"; fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Prefer Archerypal repo snippets if present next to this script's parent deploy/
SRC_LOC="${SCRIPT_DIR}/../nginx-archerypal-location.conf"
SRC_HTTP="${SCRIPT_DIR}/../nginx-archerypal-http.conf"

install -d /etc/nginx/snippets /etc/nginx/conf.d

if [[ -f "$SRC_LOC" ]]; then
  sed "s|127.0.0.1:8080|${UPSTREAM}|g" "$SRC_LOC" > "$SNIPPET"
else
  cat > "$SNIPPET" <<EOF
location = /health {
    proxy_pass http://${UPSTREAM}/health;
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
}
location ^~ /v1/ {
    client_max_body_size 8m;
    proxy_pass http://${UPSTREAM}/v1/;
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_set_header Upgrade \$http_upgrade;
    proxy_set_header Connection \$connection_upgrade;
    proxy_read_timeout 3600s;
}
EOF
fi

if [[ -f "$SRC_HTTP" ]]; then
  cp "$SRC_HTTP" "$HTTP_CONF"
else
  cat > "$HTTP_CONF" <<'EOF'
limit_req_zone $binary_remote_addr zone=archerypal_api:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=archerypal_register:10m rate=1r/s;
log_format archerypal_safe '$remote_addr - $request_method $uri $status';
EOF
fi

cat > "$WS_MAP" <<'EOF'
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
EOF

INCLUDED=0
for site in "${SITE_CANDIDATES[@]}"; do
  if [[ -f "$site" ]] && ! grep -q 'archerypal-location.conf' "$site"; then
    # Insert include into each server block is fragile; append a dedicated server if universe site missing.
    echo "Found site $site — add include manually if not auto-inserted"
  fi
  if [[ -f "$site" ]] && ! grep -q 'archerypal-location.conf' "$site"; then
    cp "$site" "${site}.bak.$(date +%Y%m%d%H%M%S)"
    # Prefer inserting after server_name line
    if grep -q "server_name.*${PUBLIC_HOST}" "$site"; then
      sed -i "/server_name.*${PUBLIC_HOST}/a\\    include ${SNIPPET};" "$site"
      INCLUDED=1
    fi
  elif [[ -f "$site" ]] && grep -q 'archerypal-location.conf' "$site"; then
    INCLUDED=1
  fi
done

if [[ "$INCLUDED" -eq 0 ]]; then
  cat > "/etc/nginx/sites-available/archerypal-edge" <<EOF
server {
    listen 192.168.1.205:80;
    listen 100.86.137.49:80;
    server_name ${PUBLIC_HOST};
    location ^~ /.well-known/acme-challenge/ { root /var/www/certbot; }
    location / { return 301 https://\$host\$request_uri; }
}
server {
    listen 192.168.1.205:443 ssl;
    server_name ${PUBLIC_HOST};
    ssl_certificate /etc/letsencrypt/live/${PUBLIC_HOST}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${PUBLIC_HOST}/privkey.pem;
    include ${SNIPPET};
}
EOF
  ln -sfn /etc/nginx/sites-available/archerypal-edge /etc/nginx/sites-enabled/archerypal-edge
  echo "Created dedicated archerypal-edge site (ensure cert exists for ${PUBLIC_HOST} on this host)"
fi

nginx -t
systemctl reload nginx
echo "Nginx edge configured -> ${UPSTREAM}"
echo "Ensure Incus proxy listens on ${UPSTREAM} (see 02-migrate-from-z800.sh PROXY_LISTEN)"
