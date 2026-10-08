# Archerypal (Android)

Kotlin + Jetpack Compose app for archery scoring on the field and Plus community features online.

## Features

- **Host / Join (Nearby)** — offline field matches via Google Nearby Connections + QR
- **Remote matches (Plus)** — WebSocket relay on the Archerypal server; invite links + push invites
- **Scoring** — ASA, IBO, Universal, and freeform; host-authoritative sync
- **History & standings** — local history, online friends board (free), online global board (Plus)
- **Social** — friends, feed, shoots & RSVPs, profiles are free with Google Sign-In; posting and creating shoots are Plus
- **Photo posts** — off until full rollout (`PlusFeatures.PHOTO_POSTS_ENABLED` + Worker `PHOTO_POSTS_ENABLED`)
- **Monetization** — AdMob banners (free), Remove ads IAP, Archerypal Plus monthly subscription
  - Lifetime Plus exists as a **hidden** one-time Play product for promo-code tester grants

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Google Nearby Connections (local)
- Cloudflare Workers + D1 + Durable Objects (`workers/`) for remote play / social
- CameraX + ML Kit barcode scanning, ZXing QR
- Google Sign-In, Play Billing, AdMob
- Firebase Cloud Messaging (optional — see [docs/FCM_SETUP.md](docs/FCM_SETUP.md))
- `minSdk 26`, `targetSdk 36`

## Open in Android Studio

1. **File → Open** this folder (`Archerypal`).
2. Let Gradle sync finish.
3. Prefer a **physical Android device** for Nearby / camera.
4. Run the `app` configuration.

## Production API

Public URL (debug + release): **`https://archerypal-api.pauljreedii.workers.dev`**

Cloudflare Workers. See [workers/README.md](workers/README.md) and [docs/SERVER_CONNECTION.md](docs/SERVER_CONNECTION.md).

## Build release AAB

```bash
# 1. Create a release keystore (one time)
keytool -genkey -v -keystore app/release/archerypal-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias archerypal

# 2. Copy and fill in signing config
copy keystore.properties.example keystore.properties

# 3. Build the App Bundle
.\gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## Google Play checklist

| Requirement | Status |
|-------------|--------|
| `targetSdk 36` | Configured |
| AAB | `bundleRelease` |
| R8 minify + shrink | Release enabled |
| Privacy policy | [PRIVACY_POLICY.md](PRIVACY_POLICY.md) |
| Data safety | [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md) |
| Billing products | [docs/PLAY_BILLING_SETUP.md](docs/PLAY_BILLING_SETUP.md) |
| FCM (push invites) | [docs/FCM_SETUP.md](docs/FCM_SETUP.md) |

**Contains ads?** Yes (banner; remediable via Remove ads or Plus).  
**Backend?** Yes — Friend IDs, remote matches, social, standings. Declare accordingly in Play Console.

## Server

See [workers/README.md](workers/README.md). Quick health check:

```bash
curl -s https://archerypal-api.pauljreedii.workers.dev/health
```
