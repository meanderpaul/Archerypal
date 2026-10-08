#!/usr/bin/env bash
set -euo pipefail
STAGE=/tmp/archerypal-rollback
rm -rf "$STAGE"
mkdir -p "$STAGE/opt" "$STAGE/etc" "$STAGE/var"
cp -a /opt/archerypal/. "$STAGE/opt/" || true
if [[ -d /etc/archerypal ]]; then
  cp -a /etc/archerypal/. "$STAGE/etc/" 2>/dev/null || true
fi
cp -a /var/lib/archerypal/. "$STAGE/var/"
if command -v sqlite3 >/dev/null 2>&1; then
  sqlite3 /var/lib/archerypal/archerypal.db 'PRAGMA wal_checkpoint(TRUNCATE);' || true
fi
tar -C "$STAGE" -czf /tmp/archerypal-migrate.tgz .
ls -lh /tmp/archerypal-migrate.tgz
tar -tzf /tmp/archerypal-migrate.tgz | head -n 40
