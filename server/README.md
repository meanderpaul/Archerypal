Standalone Go server for remote Friend-ID matches.

## Production (current target)

**Host:** `RPIserver` only — Incus guest `archerypal` (`archerypal-guest`).
**Public URL:** `https://universeconflict.duckdns.org`
**Edge:** RPIserver nginx + DuckDNS TLS → Incus proxy `127.0.0.1:18081` → guest `:8080`
**Data:** guest volume `/var/lib/archerypal` (SQLite + uploads)

See [deploy/incus/README.md](deploy/incus/README.md) for create/migrate/retire scripts.

**Note:** Public TLS for `universeconflict.duckdns.org` must be valid on RPIserver
(`certbot renew`). Guest health via `http://127.0.0.1:18081/health` is the local
cutover check; external clients need a non-expired cert + DNS pointing at the Pi.

The previous **z800** bare-metal install is **retired** after cutover (rollback copy only). Do not run Critter or Archerypal on z800.
On z800 (requires sudo password):

```bash
sudo systemctl disable --now archerypal.service archerypal-shoot-sync.timer
```


## Legacy notes

Older docs mentioning z800 as the live host, or Raspberry Pi Tailscale Funnel as the match relay path, are historical. Funnel on RPIserver (if present) is unrelated to Archerypal and must not share port `8080` with the Archerypal proxy (hence `18081`).

## Deploy / update on RPIserver guest

Cross-compile on the dev machine:

```bash
cd server
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o deploy/archerypal-server-linux-arm64 ./cmd/archerypal-server
```

Then follow [deploy/incus/README.md](deploy/incus/README.md).

## API overview

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /health` | — | Liveness |
| `POST /v1/players/register` | — | Silent Friend ID registration |
| `PATCH /v1/players/me` | HMAC | Update name / FCM token |
| `GET /v1/friends` | HMAC | List friends |
| `POST /v1/friends/add` | HMAC | **Gone (410)** — use request/accept |
| `POST /v1/friends/request` | HMAC + Plus | Send friend request |
| `POST /v1/friends/accept` | HMAC + Plus | Accept friend request |
| `POST /v1/entitlements/sync` | HMAC | Sync Plus (`purchaseToken` + Play verify) |
| `POST /v1/matches/challenge` | HMAC + Plus | Create room + WS URL |
| `POST /v1/matches/{id}/complete` | HMAC + Plus | Persist final scores (rate-limited) |
| `GET /v1/leaderboards/global` | — | Top 50 (ETag) |
| `GET /v1/leaderboards/friends` | HMAC | Friends board (ETag) |
| `GET /v1/shoots` | HMAC + Plus | Upcoming shoots (curated + friend-visible) |
| `POST /v1/shoots` | HMAC + Plus | Create community shoot |
| `POST /v1/shoots/{id}/rsvp` | HMAC + Plus | RSVP going |
| `DELETE /v1/shoots/{id}/rsvp` | HMAC + Plus | Cancel RSVP |
| `GET /v1/shoots/{id}/attendees` | HMAC + Plus | Who is going |
| `POST /v1/admin/shoots` | `X-Admin-Key` | Seed curated regional shoots |
| `POST /v1/admin/shoots/cancel-stale` | `X-Admin-Key` | Cancel curated shoots not in keep list |
| `POST /v1/friends/request` | HMAC + Plus | Send friend request |
| `POST /v1/friends/accept` | HMAC + Plus | Accept request |
| `POST /v1/friends/decline` | HMAC + Plus | Decline request |
| `GET /v1/friends/requests` | HMAC + Plus | Pending incoming requests |
| `POST /v1/auth/google/link` | HMAC + ID token | Link Google account for recovery |
| `POST /v1/auth/google/restore` | ID token | Restore Friend ID on new device |
| `GET /v1/auth/google/status` | HMAC | Google link status |
| `POST /v1/admin/beta-invites` | `X-Admin-Key` | Create closed-beta invite code |
| `GET /v1/room/{id}?token=…` | JWT | WebSocket match relay |

### Curated regional shoots

Set `ADMIN_API_KEY` in config, then:

```bash
curl -s -X POST https://universeconflict.duckdns.org/v1/admin/shoots \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: YOUR_ADMIN_KEY" \
  -d '{"id":"tri-county-2026-spring","title":"Tri-County 3D Shoot","locationName":"Tri-County Archery Club","region":"Tri-County","startsAt":1751328000000,"eventType":"3d","sourceUrl":"https://example.org/events"}'
```

Or edit seed rows in `migrations/002_shoots.sql` before deploy.

### Auto-sync shoots (scraped + manual)

Build the sync tool:

```bash
cd server
GOOS=linux GOARCH=amd64 go build -o deploy/shoot-sync-linux-amd64 ./cmd/shoot-sync
```

On z800:

1. Copy binary to `/opt/archerypal/bin/shoot-sync`
2. Copy `deploy/shoot-sources.yaml.example` → `/etc/archerypal/shoot-sources.yaml`
3. Copy `deploy/shoots-manual.example.json` → `/etc/archerypal/shoots-manual.json` (edit events as needed)
4. Install timer:

```bash
sudo cp deploy/archerypal-shoot-sync.service deploy/archerypal-shoot-sync.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now archerypal-shoot-sync.timer
```

Manual run:

```bash
sudo -u archerypal env $(grep -v '^#' /etc/archerypal/config.env | xargs) \
  /opt/archerypal/bin/shoot-sync -config /etc/archerypal/shoot-sources.yaml
```

Dry run locally:

```bash
ADMIN_API_KEY=... MATCH_SERVER_URL=https://universeconflict.duckdns.org \
  go run ./cmd/shoot-sync -config deploy/shoot-sources.yaml.example -dry-run
```

The sync tool upserts curated shoots via `POST /v1/admin/shoots` and cancels stale curated IDs via `POST /v1/admin/shoots/cancel-stale`. Configure **iCal** sources when clubs publish `.ics` URLs; use **html** for simple event pages.

## Security hardening

Production deploy should set:

| Variable | Purpose |
|----------|---------|
| `ENV=production` | Fail startup if `JWT_SECRET` is default; also blocks Plus bypass flags |
| `JWT_SECRET` | Random 32+ byte hex (auto-generated by `setup-z800.sh`) |
| `ADMIN_API_KEY` | Protects admin shoot sync + beta invite routes |
| `TRUST_FORWARDED_FOR=true` | Trust `X-Forwarded-For` from nginx only (spoofable if exposed directly) |

**Do not** set `BYPASS_PLUS_ENTITLEMENT` or `ALLOW_UNVERIFIED_ENTITLEMENT_SYNC` on production — the process will refuse to start.

**Posts / uploads:** Images are magic-sniffed, decoded, dimension-capped, and re-encoded to JPEG. Media keys are allowlisted (`32 hex + .jpg/.png`). Daily per-user image quotas and a tighter `POST /v1/posts` rate limit apply. Post body is parameterized SQL + Compose plaintext (no HTML execution).

**nginx:** `deploy/nginx-archerypal-http.conf` (rate limit zones + JWT log redaction) and `deploy/nginx-archerypal-location.conf` (per-route limits) are installed by `setup-z800.sh`. Prefer admin calls over Tailscale/localhost, not the public hostname.

**App auth headers:** `X-Friend-Code`, `X-Timestamp`, `X-Device-Signature`, and `X-Nonce` (required on POST/PATCH/PUT/DELETE). Mutating requests sign `{timestamp}{nonce}{method}{pathWithQuery}{sha256(body)}`.

**Tests:** `go test ./...` from the `server/` directory.

**Plus billing:** Server verifies Play purchase tokens when `GOOGLE_PLAY_SA_PATH` is set. See [../docs/GOOGLE_PLAY_SERVER_VERIFY.md](../docs/GOOGLE_PLAY_SERVER_VERIFY.md).

## Testing with the Android app

See [../docs/SERVER_TESTING.md](../docs/SERVER_TESTING.md) for the full checklist (public HTTPS, remote match flow).

Example config: [deploy/config.env.example](deploy/config.env.example).
