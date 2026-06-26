# Google Play — Data safety form (Archerypal)

**Last updated:** June 26, 2026

Use this guide when completing **Play Console → App content → Data safety**. It reflects the current app: offline P2P scorekeeping, **Google AdMob** banner ads on the home screen (free users), and a one-time **Remove ads** in-app purchase via Google Play Billing.

**Privacy policy URL (store listing):**  
`https://github.com/meanderpaul/Archerypal/blob/main/PRIVACY_POLICY.md`

---

## Overview answers

| Question | Answer |
|----------|--------|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (for data transmitted off-device by third-party SDKs over HTTPS/TLS) |
| Do you provide a way for users to request that their data is deleted? | **No** — Archerypal does not operate a user account or backend that stores personal data. Users can clear local app data in Android settings. For AdMob/Google data, direct users to [Google's privacy controls](https://myaccount.google.com/data-and-privacy). |
| Is this data required for your app, or can users choose whether it's collected? | **Users can choose** — purchasing Remove ads stops home-screen ad requests; ad-related collection is tied to the free ad-supported experience. Match features do not require ads. |
| Independent security review? | **No** (unless you opt in separately) |

---

## Data Archerypal does **not** collect to its own servers

Declare **No** for collection/sharing by **you** for these types (they stay on-device or sync device-to-device only):

| Data type | Notes |
|-----------|--------|
| Name | Player names typed for matches — local storage + P2P only |
| App activity (match scores, leaderboard) | Local + direct P2P; not sent to Archerypal servers |
| Photos / videos | Camera used for QR scan only; not uploaded |
| Location (precise GPS) | Not collected by Archerypal; Nearby API may use location APIs on-device for Bluetooth/Wi‑Fi discovery |
| Financial info | Payment cards handled by Google Play only |

---

## Data collected via **third-party SDKs** (declare as collected and/or shared)

Google AdMob is integrated in the free version. Use Google's official disclosure as the source of truth: [AdMob data disclosure](https://support.google.com/admob/answer/10113207).

Recommended declarations for **Google AdMob** (when ads are shown):

### Device or other IDs

| Field | Value |
|-------|--------|
| Collected? | **Yes** |
| Shared? | **Yes** |
| Ephemeral? | **No** |
| Required or optional? | **Optional** (users can buy Remove ads; can limit ads via Google/device settings) |
| Purposes | **Advertising or marketing**, **Analytics** (if offered in form for ad measurement) |
| Collected by | Third party (Google) |

*Typically: Advertising ID / device identifiers used for ad delivery and fraud prevention.*

### App interactions

| Field | Value |
|-------|--------|
| Collected? | **Yes** |
| Shared? | **Yes** |
| Purposes | **Advertising or marketing**, **Analytics** |

*Typically: ad impressions, clicks, and related interaction signals.*

### Diagnostics (if listed for AdMob in your SDK scan)

| Field | Value |
|-------|--------|
| Collected? | **Yes** (if Play's questionnaire / SDK list includes it for AdMob) |
| Shared? | **Yes** |
| Purposes | **Analytics**, **Fraud prevention, security, and compliance** |

### Approximate location (derived from IP)

| Field | Value |
|-------|--------|
| Collected? | **Yes** (coarse / IP-derived — common for ad networks) |
| Shared? | **Yes** |
| Purposes | **Advertising or marketing** |

---

## Google Play Billing

| Data type | Declaration |
|-----------|-------------|
| Purchase history / app permissions | Processed by **Google Play** for the Remove ads product. Archerypal only stores a local **ad-free entitlement** flag after Google confirms the purchase. Do **not** declare payment card data — Google handles that. |

If Play Console asks about **Purchase history**: typically **No** for direct collection by the developer; entitlement is verified through Play Billing on-device.

---

## Ads declaration (App content)

Under **Ads**:

- **Does your app contain ads?** → **Yes**
- Ad format: **Banner** (home screen)
- **Are ads used to promote your own products?** → **No** (third-party AdMob ads)
- Users can remove ads via in-app purchase → note in store listing / privacy policy

---

## Security practices (short answers)

- Data encrypted in transit for third-party SDK traffic: **Yes**
- Users can request data deletion from Archerypal: **No** (no Archerypal-operated account/database)
- Committed to Play Families Policy: only if you target children (Archerypal is **not** directed at children under 13)

---

## After Remove ads purchase

When a user owns **Remove ads**:

- Home-screen AdMob banner is not loaded
- AdMob-related collection from that screen stops
- Core match/P2P behavior is unchanged

---

## Checklist before submitting

- [ ] Privacy policy URL in Play Console points to `PRIVACY_POLICY.md` on GitHub `main`
- [ ] **Ads** section marked Yes
- [ ] Data safety form includes AdMob-related types per Google's current SDK guidance
- [ ] In-app product `remove_ads` is active in Play Console
- [ ] Store description mentions free version includes ads and optional ad removal purchase

---

## References

- [AdMob — Data disclosure for EU consent](https://support.google.com/admob/answer/10113207)
- [Play Console — Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Play Billing — Getting ready](https://developer.android.com/google/play/billing/getting-ready)
