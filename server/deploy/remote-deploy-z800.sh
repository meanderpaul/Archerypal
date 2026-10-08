#!/usr/bin/env bash
set -euo pipefail

SRC=/tmp/archerypal-deploy-src
DEP=/tmp/archerypal-deploy
export PATH="$HOME/.local/go/bin:$PATH"

mkdir -p "$DEP/migrations"
cd "$SRC"

echo "==> Building binaries..."
go mod download
go build -o "$DEP/archerypal-server-linux-amd64" ./cmd/archerypal-server
go build -o "$DEP/shoot-sync-linux-amd64" ./cmd/shoot-sync
cp migrations/*.sql "$DEP/migrations/"
cp deploy/archerypal-shoot-sync.service deploy/archerypal-shoot-sync.timer "$DEP/"
cp deploy/shoot-sources.yaml.example "$DEP/shoot-sources.yaml.example"
cp deploy/shoots-manual.example.json "$DEP/shoots-manual.example.json"

install_root() {
  docker run --rm --privileged --pid=host \
    -v "$DEP":/dep:ro \
    -v /opt/archerypal/bin:/opt/archerypal/bin \
    -v /opt/archerypal/migrations:/opt/archerypal/migrations \
    -v /etc/systemd/system:/etc/systemd/system \
    -v /etc/archerypal:/etc/archerypal \
    alpine sh -s <<'EOF'
set -e
cp /dep/archerypal-server-linux-amd64 /opt/archerypal/bin/archerypal-server
cp /dep/shoot-sync-linux-amd64 /opt/archerypal/bin/shoot-sync
chmod 755 /opt/archerypal/bin/archerypal-server /opt/archerypal/bin/shoot-sync
cp /dep/migrations/*.sql /opt/archerypal/migrations/
cp /dep/archerypal-shoot-sync.service /etc/systemd/system/
cp /dep/archerypal-shoot-sync.timer /etc/systemd/system/
mkdir -p /etc/archerypal
if [ ! -f /etc/archerypal/shoot-sources.yaml ]; then
  cp /dep/shoot-sources.yaml.example /etc/archerypal/shoot-sources.yaml
fi
if [ ! -f /etc/archerypal/shoots-manual.json ]; then
  cp /dep/shoots-manual.example.json /etc/archerypal/shoots-manual.json
fi
chown archerypal:archerypal /opt/archerypal/bin/* /opt/archerypal/migrations/*
chmod 640 /etc/archerypal/shoot-sources.yaml /etc/archerypal/shoots-manual.json 2>/dev/null || true
chown root:archerypal /etc/archerypal/shoot-sources.yaml /etc/archerypal/shoots-manual.json 2>/dev/null || true
EOF
}

echo "==> Installing to /opt/archerypal and /etc/archerypal..."
install_root

echo "==> Restarting services..."
docker run --rm --privileged --pid=host alpine \
  nsenter -t 1 -m -u -i -n -p sh -c \
  "systemctl daemon-reload && systemctl restart archerypal && systemctl enable archerypal-shoot-sync.timer && systemctl start archerypal-shoot-sync.timer"

sleep 2
echo "==> Health check..."
curl -sf http://127.0.0.1:8080/health || curl -sf https://universeconflict.duckdns.org/health
echo ""
echo "==> archerypal status:"
docker run --rm --privileged --pid=host alpine \
  nsenter -t 1 -m -u -i -n -p systemctl --no-pager status archerypal --lines=5

echo "==> shoot-sync timer:"
docker run --rm --privileged --pid=host alpine \
  nsenter -t 1 -m -u -i -n -p systemctl --no-pager status archerypal-shoot-sync.timer --lines=5

echo "==> Deploy complete."
