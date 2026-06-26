# Archerypal (Android)

Pure Kotlin Android app for offline, peer-to-peer archery score tracking on the field.

## Features

- **Host / Join** — Hybrid sync: libp2p circuit relay over cell/Wi‑Fi when online, Nearby Connections fallback offline
- **Shoot setup** — Host sets target count before scoring
- **Turn-based logging** — Large outdoor-readable number pad (0–10)
- **Host sync** — Host is source of truth; peers queue scores when disconnected
- **Live leaderboard** — Totals update as the host merges submissions

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Google Play Services Nearby Connections (local fallback)
- [jvm-libp2p](https://github.com/libp2p/jvm-libp2p) (device-hosted WAN — no backend server)
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
| Permission declarations | Camera, Bluetooth, location (Nearby API), foreground service (match sync) |
| Privacy policy URL | [PRIVACY_POLICY.md](PRIVACY_POLICY.md) |
| Data safety guide | [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md) |

### Play Console — Data safety

Archerypal does **not** operate a backend that collects match scores or player names. Match data stays on devices and syncs peer-to-peer (libp2p or Nearby Connections).

The **free version** shows **Google AdMob** banner ads on the home screen. AdMob may collect and share device/advertising data for ad delivery — declare those types in Play Console per [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md) and [Google's AdMob disclosure guide](https://support.google.com/admob/answer/10113207).

A one-time **Remove ads** in-app purchase (Google Play Billing) stops home-screen ads. Payment is processed by Google; Archerypal only stores a local ad-free entitlement.

**Quick answers for the form:**

- **Contains ads?** Yes (banner, home screen; removable via IAP)
- **Collect data to your own server?** No
- **Third-party data collection (AdMob)?** Yes — see [docs/DATA_SAFETY.md](docs/DATA_SAFETY.md) for per-type declarations
- **Privacy policy URL:** `https://github.com/meanderpaul/Archerypal/blob/main/PRIVACY_POLICY.md`

### libp2p WAN via public circuit relay (no backend you build)

When archers have mobile data or Wi‑Fi, the host runs a **libp2p node on the device**. The host reserves a slot on **public libp2p relay nodes** (community infrastructure such as libp2p/IPFS bootstrap nodes — not servers we operate). Joiners scan the host QR code, which encodes:

- `libp2pPeerId` — host identity
- `libp2pCircuitMultiaddrs` — **primary** dial path through a public relay (`/p2p-circuit/…`)
- `libp2pMultiaddrs` — direct addresses (secondary, when routable)

| Tier | When | Path |
|------|------|------|
| 1 | Internet + circuit addrs in QR | libp2p via public relay |
| 2 | Internet + direct routable addr | libp2p direct TCP |
| 3 | No internet / relay fail | Nearby Connections (~tens of meters) |

- **Online:** libp2p over circuit relay + Noise encryption (host-centric star topology)
- **Offline / no route:** falls back to **Nearby Connections** on the field
- **QR required for remote join:** the QR carries peer ID and dial addresses
- **Foreground service:** keeps relay reservation and libp2p connections alive during active matches

**Honest limits:** Mile-long cellular sync is **best-effort**. Carrier NAT, relay availability, battery, and OS background limits affect reliability. Public relays may be slow or rate-limited; the app tries multiple fallbacks. Hole punching is not available in jvm-libp2p today.

### Store listing assets still needed

- 512×512 hi-res icon (replace vector placeholder if desired)
- Feature graphic 1024×500
- Phone screenshots (host, join, scoring, leaderboard)
- Short + full description
- Privacy policy URL: `https://github.com/meanderpaul/Archerypal/blob/main/PRIVACY_POLICY.md`

## Project structure

```
app/src/main/java/com/archerypal/app/
├── MainActivity.kt          # Navigation + permissions
├── data/Models.kt           # Match state, P2P messages, QR payload
├── p2p/
│   ├── NearbyConnectionsManager.kt   # Local Bluetooth / Wi‑Fi
│   ├── libp2p/MatchProtocol.kt       # App protocol on libp2p streams
│   ├── libp2p/Libp2pMatchNode.kt     # libp2p host, relay reserve, dial
│   ├── libp2p/Libp2pRelayConfig.kt   # public relay multiaddrs
│   ├── Libp2pWanTransport.kt         # WAN transport adapter
│   ├── MatchSyncForegroundService.kt # keep relay + libp2p alive
│   └── HybridMatchTransport.kt       # relay WAN when online, nearby fallback
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
