# Archerypal (Android)

Pure Kotlin Android app for offline, peer-to-peer archery score tracking on the field.

## Features

- **Host / Join** — Nearby Connections P2P; join via QR scan or discovered hosts
- **Shoot setup** — Host sets target count before scoring
- **Turn-based logging** — Large outdoor-readable number pad (0–10)
- **Host sync** — Host is source of truth; peers queue scores when disconnected
- **Live leaderboard** — Totals update as the host merges submissions

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Google Play Services Nearby Connections
- CameraX + ML Kit barcode scanning
- ZXing QR generation
- `minSdk 26`, `targetSdk 35` (Google Play requirement)

## Open in Android Studio

1. **File → Open** and select this folder (`Archerypal`).
2. Let Gradle sync finish (Android Studio will download the wrapper if needed).
3. Connect a **physical Android device** (P2P does not work reliably on emulators).
4. Run the `app` configuration.

## Build release AAB for Google Play

```bash
# 1. Create a release keystore (one time)
keytool -genkey -v -keystore app/release/archerypal-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias archerypal

# 2. Copy and fill in signing config
copy keystore.properties.example keystore.properties

# 3. Build the App Bundle (required by Play Store)
.\gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

## Google Play checklist

| Requirement | Status in project |
|-------------|-------------------|
| `targetSdk 35` | Configured in `app/build.gradle.kts` |
| Android App Bundle (AAB) | `bundleRelease` task |
| 64-bit native libs | Default from dependencies |
| Release signing | `keystore.properties` + `signingConfigs.release` |
| R8 minify + shrink | Enabled for `release` |
| Backup disabled | `allowBackup=false`, data extraction rules |
| Permission declarations | Camera, Bluetooth, location (Nearby API) |
| Privacy policy URL | [PRIVACY_POLICY.md](PRIVACY_POLICY.md) |

### Play Console — Data safety

This app:

- Does **not** collect personal data on a server (fully offline P2P)
- Uses camera only for QR scanning
- Uses Bluetooth/Wi‑Fi/location APIs required by [Nearby Connections](https://developers.google.com/nearby/connections/android/get-started)

Declare: **No data collected** (or “data not shared with third parties”) if you do not add analytics.

### Store listing assets still needed

- 512×512 hi-res icon (replace vector placeholder if desired)
- Feature graphic 1024×500
- Phone screenshots (host, join, scoring, leaderboard)
- Short + full description
- Privacy policy URL: `https://github.com/meanderpaul/Archerypal/blob/main/PRIVACY_POLICY.md`

## Project structure

```
app/src/main/java/com/archerypal/
├── MainActivity.kt          # Navigation + permissions
├── data/Models.kt           # Match state, P2P messages, QR payload
├── p2p/NearbyConnectionsManager.kt
├── viewmodel/MatchViewModel.kt
└── ui/
    ├── components/          # Score pad, QR, scanner
    ├── screens/             # Home, Host, Join, Setup, Match
    └── theme/
```

## P2P message protocol

| Type | Direction | Purpose |
|------|-----------|---------|
| `PLAYER_JOIN` | Peer → Host | Announce player name |
| `MATCH_SETUP` | Host → Peers | Target count / phase |
| `SCORE_SUBMIT` | Peer → Host | Submit score |
| `MATCH_STATE` | Host → Peers | Full state snapshot |
| `SCORE_ACK` | Host → Peer | Confirm receipt |
| `REQUEST_SYNC` | Peer → Host | Request latest state |

## Match flow

1. Host enters name → advertises → shows QR
2. Joiners scan QR or pick discovered host → connect P2P
3. Host sets targets → match moves to scoring
4. Archers log scores per target on the number pad
5. Leaderboard updates as host merges peer scores

## Notes

- Requires Google Play Services on device
- All archers must use **Android** (Nearby Connections is platform-specific)
- Replace placeholder signing secrets before publishing
