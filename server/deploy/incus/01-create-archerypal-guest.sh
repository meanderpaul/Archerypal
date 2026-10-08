#!/usr/bin/env bash
# Create Archerypal Incus guest on RPIserver (arm64). Does not migrate data yet.
set -euo pipefail

GUEST_NAME="${GUEST_NAME:-archerypal}"
GUEST_HOSTNAME="${GUEST_HOSTNAME:-archerypal-guest}"
IMAGE="${IMAGE:-local:debian13-cloud}"
PROFILE="${PROFILE:-usage-guest}"
IPV4="${IPV4:-10.89.0.20}"

if [[ "$(id -u)" -ne 0 ]]; then exec sudo -E bash "$0" "$@"; fi
command -v incus >/dev/null || { echo "Incus missing" >&2; exit 1; }

if ! incus image list local: --format csv | grep -q debian13-cloud; then
  echo "==> Caching debian/13/cloud arm64 image"
  incus image copy images:debian/13/cloud local: --alias debian13-cloud --auto-update=false
fi

if incus info "$GUEST_NAME" >/dev/null 2>&1; then
  echo "Guest $GUEST_NAME already exists"
else
  echo "==> Launching $GUEST_NAME"
  incus launch "$IMAGE" "$GUEST_NAME" --profile "$PROFILE"
fi

incus config set "$GUEST_NAME" boot.autostart=true
incus config set "$GUEST_NAME" boot.autostart.priority=40
incus config set "$GUEST_NAME" boot.autostart.delay=20
incus config set "$GUEST_NAME" limits.memory=2GiB || true
incus config set "$GUEST_NAME" limits.cpu=2 || true
incus config device override "$GUEST_NAME" eth0 ipv4.address="$IPV4" 2>/dev/null \
  || incus config device set "$GUEST_NAME" eth0 ipv4.address="$IPV4" 2>/dev/null || true

# Dedicated data volume for SQLite + uploads
if ! incus storage volume show default archerypal-data >/dev/null 2>&1; then
  incus storage volume create default archerypal-data
fi
incus config device remove "$GUEST_NAME" data 2>/dev/null || true
incus config device add "$GUEST_NAME" data disk pool=default source=archerypal-data path=/var/lib/archerypal

for _ in $(seq 1 60); do
  incus exec "$GUEST_NAME" -- true 2>/dev/null && break
  sleep 2
done

incus exec "$GUEST_NAME" -- hostnamectl set-hostname "$GUEST_HOSTNAME" || \
  incus exec "$GUEST_NAME" -- bash -c "echo $GUEST_HOSTNAME >/etc/hostname; hostname $GUEST_HOSTNAME"
incus exec "$GUEST_NAME" -- bash -c "grep -q '$GUEST_HOSTNAME' /etc/hosts || echo '127.0.1.1 $GUEST_HOSTNAME' >>/etc/hosts"

incus exec "$GUEST_NAME" -- bash -c '
  set -euo pipefail
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y --no-install-recommends ca-certificates curl rsync sqlite3
  id archerypal >/dev/null 2>&1 || useradd --system --home /var/lib/archerypal --shell /usr/sbin/nologin archerypal
  install -d -o archerypal -g archerypal /opt/archerypal/bin /opt/archerypal/migrations /var/lib/archerypal /etc/archerypal
'

incus list "$GUEST_NAME"
echo "Next: 02-migrate-from-z800.sh then 03-host-edge-nginx.sh"
