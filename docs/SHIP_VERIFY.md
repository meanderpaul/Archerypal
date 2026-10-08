# Ship verification matrix

Use this before uploading a Play build.

## Release gates

- [ ] `PlusFeatures.PLUS_PAYWALL_DISABLED_FOR_TESTING` is `false`
- [ ] `MATCH_SERVER_URL` is `https://archerypal-api.pauljreedii.workers.dev`
- [ ] `curl https://archerypal-api.pauljreedii.workers.dev/health` returns ok
- [ ] Curated shoots seeded (TAC + Tri-County appear in Social)
- [ ] Secrets/artifacts ignored: `key`, `app/release/`, `bugreport-*.zip`, `.tmp-libp2p/`, `.tools/`, `app/google-services.json`
- [ ] Play Console products active: `1.adfree`, `1plus`, `lifetime`
- [ ] Lifetime promo codes created (do **not** price at $0)

## Billing cohorts

| Cohort | Setup | Expect |
|--------|-------|--------|
| Free | no purchase | banners show; no Social / remote Plus |
| Monthly | buy / license-test `1plus` | Plus active; **no banners** |
| Lifetime | redeem promo for `lifetime` | Plus active after reopen; **no banners**; no in-app lifetime button |

## Match flows

- [ ] Nearby host → join via QR → score → finish → history
- [ ] Remote challenge → invite **link** join still works
- [ ] Remote challenge → FCM notification join (requires `google-services.json` + FCM on server when enabled)

## Social

- [ ] Feed loads curated shoots (TAC Killington, NY Tri County, MA/RI Tri-County)
- [ ] Shoot RSVP toggles
- [ ] Text posts appear in Feed; photo posts still **501** until R2
- [ ] Guest lobby copy looks polished (no emoji checkmarks)

## Automated

```bash
cd workers && npm test
.\gradlew testDebugUnitTest assembleDebug
```
