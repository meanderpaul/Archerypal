# Privacy Policy — Archerypal

**Last updated:** October 8, 2026

Archerypal is an archery scorekeeping app for Android with local/P2P matches and optional online features (friends, feed, shoots, leaderboards, and Plus remote play).

## Summary

- **Local match data** (names, scores) stays on your devices for offline play and syncs directly between archers via Nearby Connections when possible.
- **Online features** (free with Google Sign-In, plus Archerypal Plus extras) use our Cloudflare-hosted API and match relay: Friend ID, display name, stats, friend links, and optional **Google account link** (for device recovery) are stored when you register.
- **Device credentials** (`deviceSecret`) are stored encrypted on your phone and are rotated when you restore via Google Sign-In on a new device.
- **Google email** is used during Sign-In to verify your account and is shared with **RevenueCat** (our purchase provider) so we can find your account when you contact support. We **do not retain your full email** on our own servers. The app may keep a **masked** copy (e.g. `pa***@gmail.com`) in encrypted on-device storage for display.
- **Ads** are shown on the home screen unless you buy Remove ads or subscribe to Plus. Ads are served by **Google AdMob**.
- **Purchases** are processed by **Google Play**; we do not receive your payment card details.

## Data we store on our server (online features)

When you connect to the online service, we store:

- Friend ID and display name
- Match statistics submitted after remote matches (leaderboards)
- Friend relationships and pending friend requests
- Text posts you publish (posts are text-only; photo posts are not available)
- For **public** posts only: an approximate location (rounded to about 7 miles) so the post can appear in other archers' regional feed. Friends-only posts store no location.
- Shoot/event details you create or RSVP to, which may include **location name and coordinates** you provide
- Precise device location **only when** you use feed features that filter nearby or regional posts and shoots (sent as request metadata, not stored as a standing profile field)
- Push notification tokens (FCM) while signed in; cleared when you restore on a new device or clear the local session
- Plus entitlement metadata (purchase token hash; server verification via RevenueCat / Play)
- If you link Google: Google account **subject ID** only (not your full email) for account recovery

We do not sell this data. You can request deletion during beta by contacting support (self-service deletion planned).

**Display names and Friend IDs** are treated as social identifiers (visible on leaderboards, invites, and friend features).

## Data processed on your device

- **Player names** you type for a match session
- **Scores and match history** stored locally (encrypted device credentials for server access)
- **Ad-free / Plus status** — cached locally and synced with Google Play / server
- **Match state** shared via Nearby Connections or the online WebSocket relay when using Plus
- **Camera** — used only to scan QR codes; images are not saved or uploaded by Archerypal
- **Masked Google email** (if linked) in encrypted on-device storage

## Google Sign-In (account recovery)

If you link a Google account, we store the link between your Friend ID and Google account **subject ID** so you can restore on a new phone. Full email is verified with Google at sign-in time and is **not retained** in our database. Google processes sign-in according to [Google's Privacy Policy](https://policies.google.com/privacy).

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

Optional **Remove ads** and **Archerypal Plus** purchases are handled by Google Play / RevenueCat. Google processes payment and provides entitlement information needed to unlock features. We do not receive or store your payment card or bank details.

RevenueCat identifies your purchases by your **Friend ID**. The app also sends your **display name** and, when you sign in with Google, your **Google email address** to RevenueCat so we can find your account when you contact support about a purchase or gifted Plus.

## Permissions

The app requests Bluetooth, Wi‑Fi, location, camera, and internet permissions:

- **Internet** — online API, AdMob, and Google Play Billing
- **Nearby Connections** requires Bluetooth, Wi‑Fi, and location APIs for local peer discovery on Android
- **Location** — used for Nearby discovery and to show nearby shoots/events in the social feed
- **Camera** is used only for QR scanning
- **Advertising ID** (`AD_ID`) — used by AdMob when ads are shown; not used by Archerypal for its own tracking

## Third parties

- **Google Play Services** — Nearby Connections transport on device. Subject to [Google's Privacy Policy](https://policies.google.com/privacy).
- **Google AdMob** — advertising in the free version. Subject to [Google's Privacy Policy](https://policies.google.com/privacy) and [AdMob & AdSense program policies](https://support.google.com/admob/answer/6128543).
- **Google Play Billing / RevenueCat** — in-app purchase and subscription processing. RevenueCat receives your Friend ID, display name, and Google email address (if you sign in with Google). Subject to [RevenueCat's Privacy Policy](https://www.revenuecat.com/privacy).
- **Cloudflare** — hosts our API and live match WebSocket relay. Live match frames are forwarded between participants in a room; room access uses short-lived tokens. Match completion scores you submit for leaderboards are stored by us.

## Children's privacy

The app is not directed at children under 13 and does not knowingly collect information from children.

## Contact

For privacy questions, open an issue at [github.com/meanderpaul/Archerypal](https://github.com/meanderpaul/Archerypal/issues).

## Changes

We may update this policy. The latest version is published in this repository and linked from the Google Play store listing.
