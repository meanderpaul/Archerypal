# Archerypal Incus isolation on RPIserver

Production host is **RPIserver only**. z800 is a migration source / rollback copy.

## Port map

| Service | Listen |
|---|---|
| Archerypal inside guest | `127.0.0.1:8080` |
| Host Incus proxy | `127.0.0.1:18081` → guest `:8080` |
| Host nginx / DuckDNS TLS | public → `127.0.0.1:18081` |
| Existing Tailscale Funnel on Pi | stays on `127.0.0.1:8080` (do not collide) |

## Order

```sh
# On build machine (Windows/Linux with Go):
cd server
GOOS=linux GOARCH=arm64 go build -o deploy/archerypal-server-linux-arm64 ./cmd/archerypal-server

# On RPIserver:
scp deploy/incus/*.sh deploy/archerypal-server-linux-arm64 RPi5-Tailscale:/tmp/archerypal-incus/
ssh RPi5-Tailscale
sudo cp /tmp/archerypal-incus/archerypal-server-linux-arm64 /tmp/
cd /tmp/archerypal-incus
sudo bash 01-create-archerypal-guest.sh
PROXY_LISTEN=127.0.0.1:18081 sudo bash 02-migrate-from-z800.sh
sudo bash 03-host-edge-nginx.sh
sudo bash 05-wire-usage-agent.sh
# After public health OK:
bash 04-retire-z800.sh
```

## Verify

```sh
curl -fsS http://127.0.0.1:18081/health
curl -fsS https://universeconflict.duckdns.org/health
sudo bash /opt/usage-monitor/deploy/incus/05-chaos-check.sh   # GUEST_NAME=archerypal if adapted
```

Confirm **Archerypal** cost unit in Usage Monitor under host `archerypal-guest`.
