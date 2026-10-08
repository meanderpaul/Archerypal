#!/usr/bin/env bash
# Pull Archerypal state from z800 and install into RPIserver Incus guest.
# Run on RPIserver. Requires SSH access to z800 as the current user (or Z800_HOST).
set -euo pipefail

GUEST_NAME="${GUEST_NAME:-archerypal}"
Z800_HOST="${Z800_HOST:-z800}"
STAGE="${STAGE:-/var/tmp/archerypal-migrate}"
PROXY_LISTEN="${PROXY_LISTEN:-127.0.0.1:18081}"

if [[ "$(id -u)" -ne 0 ]]; then exec sudo -E bash "$0" "$@"; fi
command -v incus >/dev/null || { echo "Incus missing" >&2; exit 1; }
incus info "$GUEST_NAME" >/dev/null || { echo "Create guest first" >&2; exit 1; }

# Prefer pre-staged migrate tarball (includes DB/uploads/opt). Secrets from /etc
# must be provided separately if the packer lacked root on z800:
#   sudo tar -C /etc -czf /tmp/archerypal-etc.tgz archerypal
STAGE_TGZ="${STAGE_TGZ:-}"
ETC_TGZ="${ETC_TGZ:-}"
if [[ -z "$STAGE_TGZ" && -f /tmp/archerypal-migrate.tgz ]]; then STAGE_TGZ=/tmp/archerypal-migrate.tgz; fi
if [[ -z "$ETC_TGZ" && -f /tmp/archerypal-etc.tgz ]]; then ETC_TGZ=/tmp/archerypal-etc.tgz; fi

echo "==> Staging backup"
rm -rf "$STAGE"
mkdir -p "$STAGE"/{opt,etc,var,bin}

if [[ -n "$STAGE_TGZ" && -f "$STAGE_TGZ" ]]; then
  echo "Using staged $STAGE_TGZ"
  tar -C "$STAGE" -xzf "$STAGE_TGZ"
else
  echo "==> Pulling live archive from ${Z800_HOST}"
  ssh "$Z800_HOST" 'bash -s' <<'REMOTE'
set -euo pipefail
sudo systemctl stop archerypal.service || true
sudo -u archerypal bash -c 'sqlite3 /var/lib/archerypal/archerypal.db "PRAGMA wal_checkpoint(TRUNCATE);"' || true
TMP=$(mktemp -d /tmp/archerypal-bak.XXXXXX)
sudo cp -a /opt/archerypal/. "$TMP/opt/"
sudo cp -a /etc/archerypal/. "$TMP/etc/"
sudo cp -a /var/lib/archerypal/. "$TMP/var/"
sudo tar -C "$TMP" -czf /tmp/archerypal-migrate.tgz .
sudo chown "$(whoami)" /tmp/archerypal-migrate.tgz
echo "PACKED"
REMOTE
  scp "$Z800_HOST:/tmp/archerypal-migrate.tgz" "$STAGE/archerypal-migrate.tgz"
  tar -C "$STAGE" -xzf "$STAGE/archerypal-migrate.tgz"
fi

if [[ -n "$ETC_TGZ" && -f "$ETC_TGZ" ]]; then
  echo "Merging secrets from $ETC_TGZ"
  mkdir -p "$STAGE/etc"
  tar -C "$STAGE/etc" -xzf "$ETC_TGZ" --strip-components=1 2>/dev/null \
    || tar -C "$STAGE" -xzf "$ETC_TGZ"
fi

# Normalize layout to STAGE/etc/config.env
if [[ -f "$STAGE/etc/archerypal/config.env" && ! -f "$STAGE/etc/config.env" ]]; then
  mv "$STAGE/etc/archerypal/"* "$STAGE/etc/" 2>/dev/null || true
fi

if [[ ! -f "$STAGE/etc/config.env" ]]; then
  if [[ "${ALLOW_BOOTSTRAP_CONFIG:-0}" == "1" ]]; then
    echo "WARN: generating bootstrap /etc/archerypal/config.env (z800 secrets unavailable)"
    mkdir -p "$STAGE/etc"
    JWT_SECRET="$(openssl rand -hex 32)"
    ADMIN_API_KEY="$(openssl rand -hex 24)"
    cat >"$STAGE/etc/config.env" <<EOF
LISTEN_ADDR=127.0.0.1:8080
ENV=production
JWT_SECRET=${JWT_SECRET}
ADMIN_API_KEY=${ADMIN_API_KEY}
PUBLIC_WS_BASE=wss://universeconflict.duckdns.org
DB_PATH=/var/lib/archerypal/archerypal.db
MIGRATIONS_DIR=/opt/archerypal/migrations
EOF
    if [[ ! -f "$STAGE/etc/shoot-sources.yaml" ]]; then
      cat >"$STAGE/etc/shoot-sources.yaml" <<'EOF'
# Minimal shoot sync sources — replace with production list after cutover
sources: []
EOF
    fi
  else
    echo "ERROR: /etc/archerypal/config.env missing in stage." >&2
    echo "On z800 run: sudo tar -C /etc -czf /tmp/archerypal-etc.tgz archerypal" >&2
    echo "Then scp to RPIserver:/tmp/archerypal-etc.tgz and re-run." >&2
    echo "Or set ALLOW_BOOTSTRAP_CONFIG=1 to generate new JWT/ADMIN secrets." >&2
    exit 1
  fi
fi


echo "==> Pushing into guest (tar pipe avoids nested-path push quirk)"
incus exec "$GUEST_NAME" -- mkdir -p /opt/archerypal /etc/archerypal /var/lib/archerypal
# Prefer flat layouts from stage; support both STAGE/{opt,etc,var} and nested.
OPT_SRC="$STAGE/opt"; [[ -d "$STAGE/opt/bin" ]] || OPT_SRC="$STAGE/opt"
ETC_SRC="$STAGE/etc"; [[ -f "$STAGE/etc/config.env" ]] || { [[ -f "$STAGE/etc/archerypal/config.env" ]] && ETC_SRC="$STAGE/etc/archerypal"; }
VAR_SRC="$STAGE/var"; [[ -f "$STAGE/var/archerypal.db" ]] || { [[ -f "$STAGE/var/lib/archerypal/archerypal.db" ]] && VAR_SRC="$STAGE/var/lib/archerypal"; }
tar -C "$OPT_SRC" -cf - . | incus exec "$GUEST_NAME" -- tar -C /opt/archerypal -xf -
tar -C "$ETC_SRC" -cf - . | incus exec "$GUEST_NAME" -- tar -C /etc/archerypal -xf -
tar -C "$VAR_SRC" -cf - . | incus exec "$GUEST_NAME" -- tar -C /var/lib/archerypal -xf -

# Prefer ARM64 binary if present on the build machine path; otherwise keep transferred binary
# and warn (amd64 will not run on Pi guest).
if [[ -f /tmp/archerypal-server-linux-arm64 ]]; then
  echo "==> Installing arm64 binary from /tmp/archerypal-server-linux-arm64"
  incus file push /tmp/archerypal-server-linux-arm64 "$GUEST_NAME/opt/archerypal/bin/archerypal-server"
fi
if [[ -f /tmp/shoot-sync-linux-arm64 ]]; then
  echo "==> Installing arm64 shoot-sync from /tmp/shoot-sync-linux-arm64"
  incus file push /tmp/shoot-sync-linux-arm64 "$GUEST_NAME/opt/archerypal/bin/shoot-sync"
fi

incus exec "$GUEST_NAME" -- bash -c '
  set -euo pipefail
  chown -R archerypal:archerypal /opt/archerypal /var/lib/archerypal
  chown -R root:archerypal /etc/archerypal
  chmod 640 /etc/archerypal/config.env 2>/dev/null || true
  chmod 755 /opt/archerypal/bin/archerypal-server /opt/archerypal/bin/shoot-sync 2>/dev/null || true
  # Force listen on guest loopback
  if grep -q "^LISTEN_ADDR=" /etc/archerypal/config.env; then
    sed -i "s|^LISTEN_ADDR=.*|LISTEN_ADDR=127.0.0.1:8080|" /etc/archerypal/config.env
  else
    echo "LISTEN_ADDR=127.0.0.1:8080" >>/etc/archerypal/config.env
  fi
'

echo "==> Installing hardened guest systemd units"
incus file push /opt/usage-monitor/deploy/incus/archerypal.service.guest \
  "$GUEST_NAME/etc/systemd/system/archerypal.service" 2>/dev/null || true

# Inline unit if file not present yet
incus exec "$GUEST_NAME" -- bash -c 'cat >/etc/systemd/system/archerypal.service <<EOF
[Unit]
Description=Archerypal match relay server (Incus guest)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=archerypal
Group=archerypal
EnvironmentFile=/etc/archerypal/config.env
ExecStart=/opt/archerypal/bin/archerypal-server
Restart=always
RestartSec=5
StartLimitIntervalSec=0
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
PrivateDevices=true
ProtectKernelTunables=true
ProtectControlGroups=true
ProtectKernelModules=true
ProtectClock=true
RestrictSUIDSGID=true
ReadWritePaths=/var/lib/archerypal
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6
LockPersonality=true
LimitNOFILE=8192

[Install]
WantedBy=multi-user.target
EOF

cat >/etc/systemd/system/archerypal-shoot-sync.service <<EOF
[Unit]
Description=Archerypal shoot calendar sync
After=network-online.target archerypal.service

[Service]
Type=oneshot
EnvironmentFile=/etc/archerypal/config.env
ExecStart=/opt/archerypal/bin/shoot-sync -config /etc/archerypal/shoot-sources.yaml
User=archerypal
Group=archerypal
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
PrivateDevices=true
ReadWritePaths=/var/lib/archerypal
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6

[Install]
WantedBy=multi-user.target
EOF

if [[ -f /etc/systemd/system/archerypal-shoot-sync.timer ]]; then :; fi
cat >/etc/systemd/system/archerypal-shoot-sync.timer <<EOF
[Unit]
Description=Periodic Archerypal shoot sync

[Timer]
OnBootSec=5m
OnUnitActiveSec=6h
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now archerypal.service
systemctl enable --now archerypal-shoot-sync.timer
systemctl --no-pager --full status archerypal.service | head -n 20
'

echo "==> Publishing host proxy ${PROXY_LISTEN} -> guest:8080"
# Stop anything on host 8080 first (universe/uvicorn conflict risk)
if ss -tln | grep -q ':8080\b'; then
  echo "WARN: something already listens on host :8080 — inspect before cutover" >&2
  ss -tlnp | grep ':8080' || true
fi
incus config device remove "$GUEST_NAME" api-proxy 2>/dev/null || true
incus config device add "$GUEST_NAME" api-proxy proxy \
  listen=tcp:"$PROXY_LISTEN" \
  connect=tcp:127.0.0.1:8080 \
  bind=host

sleep 2
PROXY_URL="http://${PROXY_LISTEN}/health"
curl -fsS "$PROXY_URL" || echo "WARN: health probe failed at $PROXY_URL — check guest logs"
echo
echo "Migration into guest complete. Next: 03-host-edge-nginx.sh then retire z800 services."
