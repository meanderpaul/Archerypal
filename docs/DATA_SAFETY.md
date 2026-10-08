# Google Play — Data safety form (Archerypal)

**Last updated:** October 8, 2026

Use this guide when completing **Play Console → App content → Data safety**. It reflects the current app: local/P2P scorekeeping, optional online API (Friend ID, friends, feed, shoots, stats — free with Google Sign-In) plus **Plus** extras (posting, creating shoots, remote relay, global standings), **Google Sign-In** for account recovery, **Google AdMob** (free users), and Google Play Billing.

**Privacy policy URL (store listing):**  
`https://github.com/meanderpaul/Archerypal/blob/main/PRIVACY_POLICY.md`

---

## Overview answers

| Question | Answer |
|----------|--------|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS/TLS to API and third-party SDKs) |
| Do you provide a way for users to request that their data is deleted? | **Partial** — users can clear local data; server deletion during beta via support contact (self-service planned) |
| Is this data required for your app, or can users choose whether it's collected? | **Users can choose** — offline play needs no server account; online features (Social, Plus) require registration and Google Sign-In |
| Independent security review? | **No** (unless you opt in separately) |

---

## Data collected by **Archerypal API** (online features)

Declare **Yes** for collection when users register for online features:

| Data type | Notes |
|-----------|--------|
| Name | Display name stored with Friend ID (social / leaderboards) |
| User IDs | Friend ID (`AP-XXXXXX`); Google account **subject** if linked for recovery |
| Email | **Not retained** on the server. Verified via Google at sign-in; app may store a **masked** email on-device only |
| Precise location | **Yes** when users use nearby feed filtering or create shoots with geocoded coordinates |
| App activity | Match stats submitted to leaderboards after remote matches; text posts, shoots, RSVPs, friend requests |
| Device or other IDs | FCM push tokens while registered (cleared on credential rotation / session clear) |
| Other (device auth) | HMAC device credential hash (not the raw secret) |

Local-only match scores for offline/Nearby play remain on-device unless submitted via remote match completion.

---

## Data Archerypal does **not** collect to its own servers

| Data type | Notes |
|-----------|--------|
| Photos / videos | Camera used for QR scan only; photo posts are off (`PHOTO_POSTS_ENABLED`). Update this row and declare Photos when they ship |
| Full email address | Not stored in D1 after PII hardening; Google `sub` is the recovery key |
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
| Purchase history / app permissions | Processed by **Google Play** for Remove ads / Plus. Archerypal stores entitlement flags locally and Plus metadata on the API. Do **not** declare payment card data — Google handles that. |

If Play Console asks about **Purchase history**: typically **No** for direct collection by the developer; entitlement is verified through Play Billing / RevenueCat.

### RevenueCat

The RevenueCat SDK receives the **Friend ID** (as the app user ID) and the **display name** (`$displayName` attribute, used to find customers in the RevenueCat dashboard for support). Email is **not** sent.

| Data type | Collected? | Shared? | Purposes |
|-----------|-----------|---------|----------|
| Name | **Yes** | **Yes** (RevenueCat, service provider) | **Account management**, **App functionality** |
| User IDs | **Yes** | **Yes** (RevenueCat, service provider) | **Account management**, **App functionality** |
| Purchase history | **Yes** | **Yes** (RevenueCat, service provider) | **App functionality** |

---

## Ads declaration (App content)

Under **Ads**:

- **Does your app contain ads?** → **Yes**
- Ad format: **Banner** (home screen)
- **Are ads used to promote your own products?** → **No** (third-party AdMob ads)
- Users can remove ads via in-app purchase → note in store listing / privacy policy

---

## Security practices (short answers)

- Data encrypted in transit: **Yes** (HTTPS / WSS)
- Users can request data deletion from Archerypal: **Yes (support contact)** during beta
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
- [ ] Declare **precise location** if nearby feed / shoots are in the shipping build
- [ ] Do **not** declare email collection for Archerypal servers (masked on-device only)
- [ ] Declare **Name** and **User IDs** shared with RevenueCat (display name + Friend ID)
- [ ] In-app products for ad-free / Plus are active in Play Console
- [ ] Store description mentions free version includes ads and optional ad removal / Plus

---

## References

- [AdMob — Data disclosure for EU consent](https://support.google.com/admob/answer/10113207)
- [Play Console — Data safety form](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Play Billing — Getting ready](https://developer.android.com/google/play/billing/getting-ready)
