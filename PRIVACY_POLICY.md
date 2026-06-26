# Privacy Policy — Archerypal

**Last updated:** June 26, 2026

Archerypal is an offline archery scorekeeping app for Android. We do not operate our own backend server and we do not sell user data.

## Summary

- **Match data** (names, scores) stays on your devices and syncs directly between archers — we do not receive it on a server we run.
- **Ads** are shown on the home screen unless you buy the one-time **Remove ads** in-app purchase. Ads are served by **Google AdMob**, which may collect data for advertising as described below.
- **Purchases** are processed by **Google Play**; we do not receive your payment card details.

## Data we collect

**We do not collect, store, or transmit personal data to servers operated by Archerypal.** The app has no user accounts and no first-party analytics.

When you use the free (ad-supported) version, **Google AdMob** may collect and process information for ad delivery and measurement. That processing is performed by Google as a third party, not on servers we operate. See [Google's Privacy Policy](https://policies.google.com/privacy) and [How Google uses data when you use our partners' sites or apps](https://policies.google.com/technologies/partner-sites).

If you purchase **Remove ads**, banner ads are no longer shown and AdMob ad requests from the home screen stop.

## Data processed on your device

- **Player names** you type for a match session
- **Scores and match history** stored locally on your device
- **Ad-free status** — whether you purchased Remove ads, stored locally and verified with Google Play
- **Match state** shared directly between devices using Google Play Services Nearby Connections and/or libp2p peer streams
- **Camera** — used only to scan QR codes when joining a match; images are not saved or uploaded by Archerypal

## Advertising (Google AdMob)

The free version displays banner ads on the home screen via Google AdMob. Depending on your device settings and region, Google may process data such as:

- Advertising ID (and similar device identifiers where permitted)
- IP address and coarse location derived from IP
- Device information (e.g. OS version, device model)
- Ad interactions (e.g. impressions and clicks)
- Diagnostic information related to ad delivery

You can limit ad personalization in your **Google Account ad settings** ([adssettings.google.com](https://adssettings.google.com)). On Android you can also reset or disable the advertising ID in system settings.

Users in the European Economic Area, UK, and Switzerland may be shown a consent message before personalized ads are requested, where required by law.

## In-app purchases (Google Play Billing)

The optional **Remove ads** product is a one-time in-app purchase handled entirely by Google Play. Google processes payment and provides us only with purchase entitlement information needed to unlock ad-free mode. We do not receive or store your payment card or bank details.

## Permissions

The app requests Bluetooth, Wi‑Fi, location, camera, notifications, internet, and foreground-service permissions:

- **Internet** — libp2p match sync over cellular/Wi‑Fi, AdMob ad requests, and Google Play Billing
- **Nearby Connections** requires Bluetooth, Wi‑Fi, and location APIs for local peer discovery on Android
- **Camera** is used only for QR scanning
- **Foreground service** keeps libp2p match sync active while you walk a long course (relay reservation and reconnect)
- **Notifications** show the optional “match active” status while the foreground service runs
- **Advertising ID** (`AD_ID`) — used by AdMob when ads are shown; not used by Archerypal for its own tracking

## Third parties

- **Google Play Services** — Nearby Connections transport on device. Subject to [Google's Privacy Policy](https://policies.google.com/privacy).
- **Google AdMob** — advertising in the free version. Subject to [Google's Privacy Policy](https://policies.google.com/privacy) and [AdMob & AdSense program policies](https://support.google.com/admob/answer/6128543).
- **Google Play Billing** — in-app purchase processing. Subject to [Google Play Terms of Service](https://play.google.com/about/play-terms/).
- **Public libp2p relay nodes** — when archers use cellular or Wi‑Fi data, the app may connect to **public libp2p infrastructure** (community relay/bootstrap nodes on the internet) so phones behind carrier NAT can reach each other. These nodes act as **network helpers only**: they forward encrypted libp2p streams between your devices. They do **not** receive, store, or process your match scores or player names as application data. Relay operators are third parties not controlled by Archerypal.

## Children's privacy

The app is not directed at children under 13 and does not knowingly collect information from children.

## Contact

For privacy questions, open an issue at [github.com/meanderpaul/Archerypal](https://github.com/meanderpaul/Archerypal/issues).

## Changes

We may update this policy. The latest version is published in this repository and linked from the Google Play store listing.
