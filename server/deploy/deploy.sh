#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN="${1:-./archerypal-server}"

install -d -o archerypal -g archerypal /opt/archerypal/bin
install -m 755 "$BIN" /opt/archerypal/bin/archerypal-server
install -m 644 "$SCRIPT_DIR/archerypal.service" /etc/systemd/system/archerypal.service
systemctl daemon-reload
systemctl enable archerypal
systemctl restart archerypal
systemctl --no-pager status archerypal
