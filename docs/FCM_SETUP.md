# Firebase Cloud Messaging setup

Android builds work without Firebase configuration. To enable push invites and
Crashlytics, download `google-services.json` from the Firebase console and place
it at:

```text
app/google-services.json
```

The Firebase Android app must use the package name `com.archerypal.app`.
If push notifications are needed in debug builds too, add
`com.archerypal.app.debug` as another Android app in the same Firebase project
and download a configuration containing both clients. Do not commit
`google-services.json`; it is ignored because it contains project-specific
configuration.

## Server credentials (Cloudflare Worker)

The Worker sends friend-request pushes via FCM HTTP v1. Store the Firebase
service-account JSON as a secret:

```powershell
cd workers
Get-Content path\to\firebase-service-account.json -Raw | npx wrangler secret put FCM_SERVICE_ACCOUNT_JSON
```

When the secret is unset, sends are skipped and friend requests still work
(they appear the next time the recipient opens Social). Android shows them on
the `friend_requests` channel and tapping opens Social → Friends.

## Server credentials (legacy Go server)

FCM sending happens on the Archerypal server. Create a Firebase service account
with permission to send Cloud Messaging messages, store its JSON credentials
outside the repository, and set:

```text
FCM_CREDENTIALS_PATH=/absolute/path/to/firebase-service-account.json
```

Restart the server after changing the environment. The server also accepts
`GOOGLE_APPLICATION_CREDENTIALS` as a fallback. Keep service-account JSON files
private and never package them in the Android app.

## Notification permission

Android 13 and newer asks the user for notification permission. Push tokens may
still be registered when permission is denied, but invite notifications will
not be shown until permission is granted.

Google Play promo codes and promotional offers are unrelated to FCM. They
affect Play Billing entitlements only and do not configure push notifications.
