#!/bin/sh
set -e
cat > /etc/archerypal/shoot-sources.yaml <<'EOF'
manual_file: /etc/archerypal/shoots-manual.json
sources: []
EOF
cat > /etc/archerypal/shoots-manual.json <<'EOF'
[]
EOF
chown root:archerypal /etc/archerypal/shoot-sources.yaml /etc/archerypal/shoots-manual.json
chmod 640 /etc/archerypal/shoot-sources.yaml /etc/archerypal/shoots-manual.json
chgrp archerypal /etc/archerypal
chmod 750 /etc/archerypal
if ! grep -q '^ADMIN_API_KEY=' /etc/archerypal/config.env 2>/dev/null; then
  KEY=$(openssl rand -hex 24)
  echo "ADMIN_API_KEY=${KEY}" >> /etc/archerypal/config.env
  echo "Added ADMIN_API_KEY to config.env"
fi
ls -la /etc/archerypal/
