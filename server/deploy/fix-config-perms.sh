#!/usr/bin/env bash
# Fix /etc/archerypal ownership so archerypal user can read config.env.
# Run on z800: sudo bash fix-config-perms.sh
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

if ! id archerypal &>/dev/null; then
  echo "archerypal system user missing — run setup-z800.sh first" >&2
  exit 1
fi

if [[ ! -f /etc/archerypal/config.env ]]; then
  echo "Missing /etc/archerypal/config.env — run setup-z800.sh first" >&2
  exit 1
fi

install -d -m 750 -o root -g archerypal /etc/archerypal
chmod 640 /etc/archerypal/config.env
chown root:archerypal /etc/archerypal/config.env

systemctl restart archerypal

echo "Permissions fixed. Verifying as archerypal user:"
sudo -u archerypal test -r /etc/archerypal/config.env && echo "  config.env readable: yes"

echo ""
echo "Recent service log (should not show permission denied):"
journalctl -u archerypal -n 5 --no-pager
