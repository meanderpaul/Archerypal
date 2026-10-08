#!/usr/bin/env bash
# Ensure Let's Encrypt auto-renew works on RPIserver (nginx + DuckDNS).
# Idempotent. Run on the Pi as root (or via sudo).
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then exec sudo bash "$0" "$@"; fi

SITE="${SITE:-/etc/nginx/sites-enabled/universe}"
WEBROOT="${WEBROOT:-/var/www/certbot}"
HOOK="/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh"

mkdir -p "$WEBROOT" /etc/letsencrypt/renewal-hooks/deploy

cat > "$HOOK" <<'EOF'
#!/bin/bash
nginx -t && systemctl reload nginx
EOF
chmod +x "$HOOK"

if [[ ! -f "$SITE" ]]; then
  echo "Missing nginx site: $SITE" >&2
  exit 1
fi

# Keep backups out of sites-enabled â€” nginx loads every file there.
mkdir -p /etc/nginx/bak
cp -a "$SITE" "/etc/nginx/bak/$(basename "$SITE").bak.$(date +%Y%m%d%H%M%S)"

python3 - "$SITE" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()
if "acme-challenge" in text and "listen 192.168.1.205:80" in text:
    # Already hardened (or partially). Only rewrite the HTTP server block if it still has a bare server-level return.
    pass

old = """server {
    listen 192.168.1.205:80;
    server_name universeconflict.duckdns.org;
    include /etc/nginx/snippets/archerypal-location.conf;
    
    # Redirect all HTTP to HTTPS
    return 301 https://$host$request_uri;
}"""

new = """server {
    listen 192.168.1.205:80;
    server_name universeconflict.duckdns.org;
    # Keep HTTP challenges on :80 so renewals do not depend on temporary nginx edits.
    location ^~ /.well-known/acme-challenge/ {
        root /var/www/certbot;
        default_type text/plain;
    }
    include /etc/nginx/snippets/archerypal-location.conf;
    location / {
        return 301 https://$host$request_uri;
    }
}"""

if "location ^~ /.well-known/acme-challenge/" in text:
    print("ACME challenge location already present")
elif old in text:
    path.write_text(text.replace(old, new, 1))
    print(f"Updated HTTP ACME path in {path}")
else:
    print("WARNING: expected HTTP server block not found; leaving site unchanged", file=sys.stderr)
    print("Ensure port 80 is forwarded to this host and ACME can answer on /.well-known/acme-challenge/", file=sys.stderr)
PY

nginx -t
systemctl reload nginx
systemctl enable --now certbot.timer

echo "certbot.timer: $(systemctl is-enabled certbot.timer) / $(systemctl is-active certbot.timer)"
systemctl list-timers certbot.timer --no-pager || true

# Wait if another certbot is mid-run
for _ in $(seq 1 30); do
  if ! pgrep -f '/usr/bin/certbot' >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

certbot renew --dry-run --no-random-sleep-on-renew --non-interactive
echo "Auto-renew OK (dry-run passed)."
echo "Keep router port 80 (and 443) forwarded to this Pi's LAN IP or renewals will fail again."
