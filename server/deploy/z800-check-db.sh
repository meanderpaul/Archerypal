#!/bin/sh
docker run --rm -v /var/lib/archerypal:/db alpine sh -c '
  apk add --no-cache sqlite >/dev/null
  echo "=== migrations ==="
  sqlite3 /db/archerypal.db "SELECT name FROM schema_migrations ORDER BY name;"
  echo "=== shoots ==="
  sqlite3 /db/archerypal.db "SELECT id, title, status FROM shoots;"
'
