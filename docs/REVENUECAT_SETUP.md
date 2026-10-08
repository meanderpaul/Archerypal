# RevenueCat setup (Play-aligned)

Configure **RevenueCat to match existing Google Play products** — do not create new Play SKUs for this integration.

## Project (MCP-connected)

| Field | Value |
|-------|-------|
| Project | `com.archerypal.app` (`proj6d70a60a`) |
| Test Store app | `appfcd047c3dc` — sandbox SDK key `test_…` |
| Play Store app | `app0f6ba317ba` — package `com.archerypal.app` — SDK key `goog_…` |
| Webhook | `Archerypal Workers` → `https://archerypal-api.pauljreedii.workers.dev/v1/webhooks/revenuecat` |

Cursor agents authenticate the **RevenueCat MCP** plugin to list/edit apps, products, entitlements, offerings, and webhooks for this project.

## Play service account (amended for this build)

Reuse the RPI backup SA — do not mint a new one. Full steps:
[PLAY_SERVICE_ACCOUNT.md](PLAY_SERVICE_ACCOUNT.md).

| Field | Value |
|-------|--------|
| SA email | `archerypal-play-verify@archerypal.iam.gserviceaccount.com` |
| Upload to | RC → Archerypal Android (`app0f6ba317ba`) service credentials |
| Source file | `D:\RPIserver-backup-20260818\guest-archerypal\etc\archerypal\google-play-sa.json` |
| Not used by | Android app module, Workers (webhook is enough for server Plus) |

Validate: `.\scripts\check-play-sa.ps1`

## Play ↔ RevenueCat map

| Play product ID | Type | RC entitlement | In Paywall offering |
|-----------------|------|----------------|---------------------|
| `1plus` | Subscription (`plus-subscribe`, optional yearly base plans) | `com_archerypal_app_pro` | Yes (`$rc_monthly`) |
| `1.adfree` | One-time IAP | `ad_free` | Yes (`$rc_lifetime` package, optional) |
| `lifetime` | One-time | `com_archerypal_app_pro` | **No** — RC promotional gift only |

RC store identifiers:

- Test Store: `1plus`, `1.adfree`
- Play Store: `1plus:plus-subscribe`, `1.adfree`

- Pro active → Plus features + ads hidden.
- `ad_free` alone → ads hidden only.
- App user id = **Friend Code** (`Purchases.logIn`).

## Android

- SDK: `purchases` + `purchases-ui` **10.20.0** (Kotlin helpers are in `purchases`; no separate `purchases-ktx` artifact)
- Keys in `local.properties` (split so debug stays non-breaking):
  - `revenuecat.api.key.test=test_…` → debug / Test Store
  - `revenuecat.api.key.play=goog_…` → release / Play
  - `revenuecat.api.key=…` → fallback if a split key is omitted
- UI: **Paywall** when not Pro; **Customer Center** when Pro (More → Manage subscription)

## Dashboard checklist

1. ~~Products / entitlements / default offering~~ — aligned via MCP (see map above).
2. Upload Play **service account JSON** from the backup path (see above) — MCP cannot attach it.
3. Design / publish a **Paywall** on the `default` offering (none published yet).
4. Enable **Customer Center** in the dashboard.
5. Webhook URL is registered; set **Authorization** bearer secret on the webhook to match Workers:

```powershell
cd D:\code\Archerypal\workers
fnm use 22
npx wrangler secret put REVENUECAT_WEBHOOK_SECRET
npx wrangler deploy
```

Then update the RevenueCat webhook `authorization_header` to `Bearer <same secret>` (dashboard or MCP `update-webhook-integration`).

## Gift lifetime to a tester

1. Tester opens debug app and creates / restores account (Friend Code exists).
2. RC → Customers → Friend Code → Grant promotional entitlement `com_archerypal_app_pro` → **lifetime**.
3. Tester taps **Restore / refresh purchases** (or reopen app).

No Google Play promo codes required.
