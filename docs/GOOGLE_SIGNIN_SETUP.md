# Google Sign-In setup

Google Sign-In is required for **Social**, **remote invites/challenges**, and **Friend ID backup/restore**. Nearby Host/Join scoring and local History work without Google (offline / when the server is down).

## 1. Google Cloud / Play Console

1. Open [Google Cloud Console](https://console.cloud.google.com/) for the project linked to your Play app.
2. **APIs & Services → OAuth consent screen** — set to **Testing** during closed beta; add tester Gmail addresses (including yours).
3. **Credentials → Create credentials → OAuth client ID** — you need **both** clients:

| Type | Purpose |
|------|---------|
| **Web application** | Client ID used in the app (`GOOGLE_WEB_CLIENT_ID`) and on the server (`GOOGLE_OAUTH_CLIENT_ID`). This is the ID token audience. |
| **Android** | Links your APK to Google. **Required** — without it you get *"No credentials available"*. |

### Android OAuth client (debug)

Create an **Android** OAuth client with:

- **Package name:** `com.archerypal.app.debug` (debug builds use the `.debug` suffix)
- **SHA-1:** your debug keystore fingerprint

Get debug SHA-1 (Windows, Android Studio JBR):

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -alias androiddebugkey -storepass android -keypass android
```

Copy the **SHA1** line (format `AA:BB:CC:...`).

Also create a second **Android** client for release:

- **Package name:** `com.archerypal.app`
- **SHA-1:** from your release/upload keystore

## 2. Server config

In `/etc/archerypal/config.env` (or local test `config.env`):

```bash
GOOGLE_OAUTH_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Use the **Web** client ID, not the Android one. Restart `archerypal` after changing.

## 3. Android app config

Set the same **Web** client ID in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"YOUR_WEB_CLIENT_ID.apps.googleusercontent.com\"")
```

Rebuild and install the app.

## 4. Physical phone (Run from Android Studio)

Installing via **Run** / the developer window is still a **debug** build:

| Setting | Value |
|---------|--------|
| Package name | `com.archerypal.app.debug` |
| SHA-1 | Run `./gradlew :app:signingReport` and use the **debug** variant SHA-1 |

Your current debug SHA-1 (this machine):

```
48:8A:37:53:25:BB:C1:4A:D2:07:82:49:C5:76:1F:53:76:11:2F:E2
```

Create (or verify) an **Android** OAuth client in Google Cloud with that package + SHA-1.

The phone must be signed into the **same Gmail** you add as a test user below.

## 5. "Not a test user" / access blocked

If Google shows an error that you are **not a test user** (or access is denied), almost always one of these is wrong:

### Add testers in the right place

Test users go in **Google Cloud Console**, not Play Console.

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Select the project that owns client ID `917453199570-...` (check the project dropdown matches your OAuth credentials).
3. **APIs & Services → OAuth consent screen**.
4. Confirm **Publishing status** is **Testing** (not Production, unless the app is verified).
5. Confirm **User type** is **External** (required for personal `@gmail.com` accounts). **Internal** only allows your Google Workspace org.
6. Scroll to **Test users** → **+ Add users**.
7. Add the **exact** Gmail address shown when you pick an account on your phone (e.g. `you@gmail.com`).

**Do not** rely on Play Console → Testing → Testers. That list is for APK downloads only and does **not** authorize Google Sign-In.

### Other common misses

- Signed into a **different** Google account on the phone than the one you added.
- Typo in the test-user email.
- Android OAuth client missing for `com.archerypal.app.debug` + debug SHA-1 (causes different errors, but fix it anyway).
- Changes not propagated yet — wait 5–10 minutes, force-stop the app, try again.
- Web client ID in `build.gradle.kts` is from a **different** Cloud project than where you added test users.

### Quick verification

In Cloud Console → **Credentials**, you should see **three** OAuth clients for local dev:

1. **Web application** — ID matches `GOOGLE_WEB_CLIENT_ID` in `build.gradle.kts`
2. **Android** — package `com.archerypal.app.debug`, SHA-1 from signing report
3. (Later) **Android** — package `com.archerypal.app`, release SHA-1

## 6. Error [16] "Account reauth failed" (after picking Gmail)

You got past the account picker — Google accepted your test user — but **rejected the app signature**. This is different from "not a test user."

### Fix checklist (in order)

1. **Android OAuth client package name** must be exactly:
   ```
   com.archerypal.app.debug
   ```
   Not `com.archerypal.app` (that is for release/Play Store builds only).

2. **Android OAuth client SHA-1** must match the machine that built the APK:
   ```
   48:8A:37:53:25:BB:C1:4A:D2:07:82:49:C5:76:1F:53:76:11:2F:E2
   ```
   Re-run `./gradlew :app:signingReport` if unsure. A typo or extra space in Cloud Console breaks sign-in.

3. **Web client ID** in `build.gradle.kts` must be the **Web application** client ID (`917453199570-0bj3rf1fbcqoor2l2skdus0uv4rvfg2q.apps.googleusercontent.com`). Never put the Android client ID in `GOOGLE_WEB_CLIENT_ID`.

4. **OAuth consent screen → User type** must be **External** (Internal causes obscure failures on personal Gmail).

5. All credentials must live in the **same** Google Cloud project.

6. After editing credentials: **uninstall** Archerypal from the phone, wait **5–10 minutes**, **Run** again from Android Studio.

### Common mistake

Creating only an Android client for `com.archerypal.app` (no `.debug`) because that is the "real" package name in `build.gradle.kts`. Debug installs always use `.debug` suffix.

## 7. Emulator checklist

If you see **"No credentials available"**:

1. Use an emulator image **with Google Play** (not AOSP-only).
2. Open **Settings → Google** (or Passwords & accounts) and **sign in** with a Google account.
3. That Gmail must be listed as a **test user** on the OAuth consent screen.
4. Confirm the **Android** OAuth client exists for `com.archerypal.app.debug` with the correct **debug SHA-1**.
5. Confirm `GOOGLE_WEB_CLIENT_ID` in `build.gradle.kts` matches the **Web** client ID.

## 8. Test flows

| Flow | Steps |
|------|--------|
| New user | Enter name → Continue with Google → Friend ID created and linked |
| Restore | Clear app data → Enter name → Continue with Google → same Friend ID restored |
| More tab | Profile, Google account status, store, invite join |

## 9. Local server testing

Point `MATCH_SERVER_URL` / debug `buildConfigField` at your local relay (e.g. `http://10.0.2.2:8080` for emulator). Google token verification still calls Google; `GOOGLE_OAUTH_CLIENT_ID` must match the Web client used by the app.

## API endpoints

| Method | Path | Auth |
|--------|------|------|
| POST | `/v1/auth/google/link` | Device HMAC + `idToken` body |
| POST | `/v1/auth/google/restore` | `idToken` body only |
| GET | `/v1/auth/google/status` | Device HMAC |
