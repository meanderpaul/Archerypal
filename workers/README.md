# Archerypal Workers API

Public URL (all Android builds): `https://archerypal-api.pauljreedii.workers.dev`

Set via `MATCH_SERVER_URL` in `app/build.gradle.kts` (debug + release).

After pulling PII hardening migrations:

```powershell
npx wrangler d1 migrations apply archerypal --remote
```

That applies `0004_clear_google_emails.sql` (nulls retained Google emails in D1)
and `0005_device_id.sql` (opaque `device_id` for authenticated REST).

## Commands

```powershell
fnm use 22
cd workers
npx wrangler d1 migrations apply archerypal --remote
npx wrangler secret put JWT_SECRET
npx wrangler secret put GOOGLE_OAUTH_CLIENT_ID
npx wrangler secret put REVENUECAT_WEBHOOK_SECRET
npx wrangler secret put APP_CLIENT_KEY
npx wrangler secret put ADMIN_API_KEY
npm run deploy
node scripts/smoke.mjs
```

`APP_CLIENT_KEY` must match Android `archerypal.app.client.key` in `local.properties`
(debug fallback: `dev-app-client-key-change-me`). Bootstrap routes
(`/v1/players/register`, `/v1/auth/google/restore`, `/v1/auth/google/sign-in`) require
header `X-App-Key`. Authenticated REST uses `X-Device-Id` + HMAC and sealed JSON bodies.
### Seed / sync New England shoots

```powershell
$env:ADMIN_API_KEY="..."
$env:MATCH_SERVER_URL="https://archerypal-api.pauljreedii.workers.dev"
node scripts/seed-shoots.mjs
```

Curated calendar: [`data/shoots-ne.json`](data/shoots-ne.json) (TAC Killington/Pico, NY Tri County, MA/RI Tri-County — 2026 confirmed + 2027 provisional).

Weekly Cron (`0 12 * * 1`) runs `runShootSync`: re-upserts seed and scrapes TAC / Tri-County pages to promote provisional dates when text schedules publish.

Regenerate seed timestamps after editing the generator:

```powershell
node data/generate-shoots-ne.mjs
```

## Notes

- **Text posts** — `POST /v1/posts` stores body in D1 (no R2). Photo uploads return 501 until media lands.
  Bodies are opaque text (1–2000 chars, no C0 control chars except tab/newline). Android renders with
  Compose `Text` (no HTML). Any future web client must HTML-escape — never `innerHTML`.
- **Photo posts (when R2 ships)** — port the Go sanitize pipeline: magic-byte sniff JPEG/PNG only,
  full decode → scale → re-encode JPEG (strips EXIF/polyglots/PHP/PHTML), store random
  `^[a-f0-9]{32}\.jpe?g$` keys, fixed `image/jpeg` Content-Type, ACL = public / author / friends.
  Never trust client filename or Content-Type.
- **Photo posts full-rollout checklist** — photos stay off (and out of all Plus marketing) until every item is done:
  1. R2 bucket + `MEDIA` binding in `wrangler.jsonc`; upload + authenticated `GET /v1/media/:key`
     (Android `AuthenticatedMediaImage.kt` already exists).
  2. Per-user upload quotas (daily count + bytes) and Plus-only uploads.
  3. Moderation: report post, admin remove, auto-delete media on post delete.
  4. Set Worker var `PHOTO_POSTS_ENABLED=true` and app `PlusFeatures.PHOTO_POSTS_ENABLED = true`
     (adds the image picker to the composer).
  5. Add "Photo posts" to `PlusFeatures.VALUE_POINTS`, the RevenueCat paywall, Play listing,
     `PRIVACY_POLICY.md` and `docs/DATA_SAFETY.md` (declare Photos).
- **Share links (App Links)** — `GET /f/:code` (friend) and `GET /r/:roomId?token=` (match invite)
  serve a landing page with "Open in Archerypal" + Play Store buttons. Android verifies the host via
  `GET /.well-known/assetlinks.json`, built from `ANDROID_RELEASE_CERT_SHA256` (Play app-signing +
  upload key fingerprints, comma-separated) and optional `ANDROID_DEBUG_CERT_SHA256`. Set them with
  `wrangler secret put` (Play Console → App integrity → App signing shows the SHA-256 values).
  The host lives in `LinkConfig.PUBLIC_HOST` and the manifest; moving to a custom domain means
  changing both and serving `assetlinks.json` there.
- **Share links (App Links)** — `GET /f/:code` (friend) and `GET /r/:roomId?token=` (match invite)
  serve a landing page with "Open in Archerypal" + Play Store buttons. Android verifies the host via
  `GET /.well-known/assetlinks.json`, built from `ANDROID_RELEASE_CERT_SHA256` (Play app-signing +
  upload key fingerprints, comma-separated) and optional `ANDROID_DEBUG_CERT_SHA256`. Set them with
  `wrangler secret put` (Play Console → App integrity → App signing shows the SHA-256 values).
  The host lives in `LinkConfig.PUBLIC_HOST` and the manifest; moving to a custom domain means
  changing both and serving `assetlinks.json` there.
- **Free vs Plus routes** — free (device auth): friends request/accept/decline/list, `GET /v1/feed`,
  `GET /v1/shoots`, RSVP, attendees, `/v1/leaderboards/friends`. Plus: `POST /v1/posts`,
  `POST /v1/shoots`, `/v1/matches/*`, `/v1/leaderboards/global`. Friend requests are rate-limited
  to 30/day per Friend ID (isolate-local soft limit).
- Plus source of truth: RevenueCat webhook (`/v1/webhooks/revenuecat`). Play SA is uploaded
  to RevenueCat only — not to this Worker (see `docs/PLAY_SERVICE_ACCOUNT.md`).
- `POST /v1/entitlements/sync` is disabled in production unless
  `ALLOW_UNVERIFIED_ENTITLEMENT_SYNC=true` (local `.dev.vars` only).
- `BYPASS_PLUS_ENTITLEMENT` and `ALLOW_UNVERIFIED_ENTITLEMENT_SYNC` are **forbidden** on
  production. Use [`/.dev.vars.example`](.dev.vars.example) for local `wrangler dev` only.
- Match rooms use Durable Object `MatchRoom` (hibernatable WebSockets).
- Friends: use `request` → `accept` / `decline`. Instant `POST /v1/friends/add` returns 410.
