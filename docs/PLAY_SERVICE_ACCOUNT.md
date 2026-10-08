# Play service account (current build)

Reuse the **existing** Play-verify service account from the RPI backup. Do **not** create a
second SA or drop a raw copy into the Android tree — that would fork credentials and risk
breaking Play Console / RevenueCat.

## Identity (from RPI backup)

| Field | Value |
|-------|--------|
| GCP project | `archerypal` |
| Client email | `archerypal-play-verify@archerypal.iam.gserviceaccount.com` |
| Backup path (identical copies) | `D:\RPIserver-backup-20260818\guest-archerypal\etc\archerypal\google-play-sa.json` |
| Also at | `D:\RPIserver-backup-20260818\archerypal\etc\archerypal\google-play-sa.json` |
| Working copy (gitignored) | `secrets/google-play-sa.json` in this repo |

Guest `config.env` on that backup already had:

```
GOOGLE_PLAY_PACKAGE_NAME=com.archerypal.app
GOOGLE_PLAY_SA_PATH=/etc/archerypal/google-play-sa.json
```

That was for the **Go** `archerypal-server` Android Publisher verify path.

## What changed in this build

| Layer | Old (RPI Go) | Current (non-breaking) |
|-------|--------------|-------------------------|
| Receipt authority | Server calls Play with SA | **RevenueCat** validates Play with the same SA |
| Server Plus rows | `POST /v1/entitlements/sync` + Play token | **RC webhook** → Workers; client sync is best-effort mirror |
| Debug billing | Play license testers / local | **Test Store** SDK key (`test_…`) — no SA required |
| Release billing | Play Billing + Go verify | Play via RC (`goog_…`) once SA is on the RC Play app |
| Package | `com.archerypal.app` | Same for Play/RC; debug uses `.debug` suffix + Test Store |

Workers intentionally **do not** load the SA JSON (see `workers/src/routes/entitlements.ts`).
Putting the SA on Workers would be a different product and is not required for this cutover.

## Amend for RevenueCat (required for live Play)

1. Confirm Play Console → Users and permissions still lists  
   `archerypal-play-verify@archerypal.iam.gserviceaccount.com` with at least:
   - View app information
   - View financial data
   - Manage orders and subscriptions
2. In Google Cloud project linked to Play (`917453199570` / `archerypal`), enable:
   - **Google Play Android Developer API**  
     https://console.developers.google.com/apis/api/androidpublisher.googleapis.com/overview?project=917453199570  
   Then grant the SA (for RC / RTDN):
   - Pub/Sub Editor (or Admin)
   - Monitoring Viewer
3. RevenueCat → Project `com.archerypal.app` → App **Archerypal Android** (`app0f6ba317ba`) →  
   Service credentials → upload `secrets/google-play-sa.json` (or the backup path).
4. Wait a few minutes, then re-check credentials in RC (or ask the agent to re-validate).
   Upload alone is not enough until the Android Publisher API is enabled.
5. Keep products as already mapped (`1plus:plus-subscribe`, `1.adfree`) — do not re-import
   under new IDs.

## Optional: Go server again

Only if you restore `archerypal-server` with Play token verify: place the same file at
`/etc/archerypal/google-play-sa.json` and set the env vars from
[GOOGLE_PLAY_SERVER_VERIFY.md](GOOGLE_PLAY_SERVER_VERIFY.md). Prefer RC webhooks while
Workers are the public API.

## Validate backup file locally

```powershell
.\scripts\check-play-sa.ps1
```

Expects the backup path and checks `type` / `project_id` / `client_email` only (no key dump).
