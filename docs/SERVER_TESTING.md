# Test the app against Cloudflare Workers

Use this checklist to go from “Worker deployed” to “two phones can play a remote match.”

## Prerequisites

| Requirement | Why |
|-------------|-----|
| Worker `archerypal-api` deployed | Serves REST + WSS |
| D1 migrations applied (`0001`, `0002_shoots`) | Schema for players + shoots |
| `PUBLIC_WS_BASE=wss://archerypal-api.pauljreedii.workers.dev` | Challenge API returns joinable WSS URLs |
| App `MATCH_SERVER_URL` = same host | Set in `app/build.gradle.kts` (all builds) |
| `ADMIN_API_KEY` secret (for seed/sync) | Upsert curated shoots |

## 1. Worker health

```bash
curl -s https://archerypal-api.pauljreedii.workers.dev/health
# {"status":"ok"}
```

Deploy / migrate (from `workers/`):

```powershell
fnm use 22
cd workers
npx wrangler d1 migrations apply archerypal --remote
npm run deploy
```

Seed New England shoots (once, or after schedule edits):

```powershell
$env:ADMIN_API_KEY="..."
$env:MATCH_SERVER_URL="https://archerypal-api.pauljreedii.workers.dev"
node scripts/seed-shoots.mjs
```

Weekly Cron (`0 12 * * 1`) re-runs shoot sync and promotes provisional TAC / Tri-County dates when source pages publish text dates.

## 2. Phone setup

1. Install debug or release build (`com.archerypal.app` or `.debug`).
2. No Tailscale required.
3. Rebuild after any `MATCH_SERVER_URL` change in `build.gradle.kts`.

## 3. App smoke test (one phone)

1. Open app → enter your name.
2. **More** or **Contest** (Plus):
   - **Friend ID** appears (e.g. `AP-XXXXXX`)
   - Server shows connected when `/health` succeeds
3. If “Server unreachable”:
   - `curl https://archerypal-api.pauljreedii.workers.dev/health` from a PC
   - Check Workers logs in the Cloudflare dashboard

## 4. Remote match test (two phones)

**Plus** required on the **host**. For local Workers testing only, set
`BYPASS_PLUS_ENTITLEMENT=true` in `workers/.dev.vars` (never in production `wrangler.jsonc`).

1. Both devices register Friend IDs.
2. Host challenges friend / shares invite link.
3. Guest joins → WebSocket scoring → finish.

## 5. Shoots / Social

1. Social feed shows curated TAC Killington + Tri-County entries.
2. RSVP toggles; attendees list loads.
3. Create text post: expect it in Feed. Photo attach: expect 501 until R2.

## Legacy Go host

The Go relay under `server/` remains for reference / local tools. App production traffic does **not** use DuckDNS. Optional Go `shoot-sync` default URL now points at Workers.
