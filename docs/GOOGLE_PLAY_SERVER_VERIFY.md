# Google Play / Plus entitlement verification

## Current build (Workers + RevenueCat) — use this

Public debug API is Cloudflare Workers. Plus on the server is driven primarily by
**RevenueCat webhooks**, not by the Go Android Publisher client.

| Step | Where | Notes |
|------|--------|------|
| Validate Play receipts | RevenueCat + Play SA | Upload SA from RPI backup — [PLAY_SERVICE_ACCOUNT.md](PLAY_SERVICE_ACCOUNT.md) |
| Grant/revoke Plus rows | `POST /v1/webhooks/revenuecat` | Workers; set `REVENUECAT_WEBHOOK_SECRET` |
| Client mirror | `POST /v1/entitlements/sync` | **Disabled** on Workers unless `ALLOW_UNVERIFIED_ENTITLEMENT_SYNC` (local `.dev.vars` only). Production grants come from RC webhook. |

Do **not** copy `google-play-sa.json` into the Workers project or the Android app module.

## Legacy Go server (optional)

Only if you run `archerypal-server` again with Play token verification:

`POST /v1/entitlements/sync`:

1. When `GOOGLE_PLAY_SA_PATH` + `GOOGLE_PLAY_PACKAGE_NAME` are set, the client sends
   `purchaseToken` + `sku`. The server verifies with the Google Play Android Publisher API.
2. Testing only: if Play verification is **not** configured and
   `ALLOW_UNVERIFIED_ENTITLEMENT_SYNC=true`, the old client hash path is allowed.
3. Otherwise sync returns **403**.

| Flag | Purpose | Production |
|------|---------|------------|
| `BYPASS_PLUS_ENTITLEMENT` | Skip Plus checks (local/dev) | **Forbidden** on public Go server |
| `ALLOW_UNVERIFIED_ENTITLEMENT_SYNC` | Allow client-hash sync without Play | **Forbidden** on public Go server |

```bash
GOOGLE_PLAY_PACKAGE_NAME=com.archerypal.app
GOOGLE_PLAY_SA_PATH=/etc/archerypal/google-play-sa.json
```

Use the **same** SA file as RevenueCat (`archerypal-play-verify@…`), from the RPI backup path
documented in [PLAY_SERVICE_ACCOUNT.md](PLAY_SERVICE_ACCOUNT.md).

## Local Plus without billing

```bash
ENV=development
LISTEN_ADDR=:8080
BYPASS_PLUS_ENTITLEMENT=true
```

Workers: do **not** set `BYPASS_PLUS_ENTITLEMENT` or `ALLOW_UNVERIFIED_ENTITLEMENT_SYNC`
in `wrangler.jsonc` / production secrets. Use `workers/.dev.vars` for local Plus testing.
