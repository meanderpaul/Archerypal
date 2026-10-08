# Google Play billing setup



Product IDs in the app must match Play Console exactly.



| Product ID | Type | Price | Purpose |

|------------|------|-------|---------|

| `1.adfree` | In-app (one-time) | Your choice | Removes banner ads |

| `1plus` | Subscription | e.g. monthly auto-renew | Plus — feed posting, creating shoots, remote play, global standings, **no banner ads** (friends, feed reading and RSVPs are free; do not list photo posts until they ship) |

| `lifetime` | In-app (one-time) | Real list price (not $0) | Lifetime Plus — **hidden in-app**; restore + promo codes only |



Base plans on `1plus`: `plus-subscribe` (preferred in-app), `plus-month` (prepaid), `plus-yearly`.



## 1. Remove ads (one-time)



1. Play Console → **Monetize** → **Products** → **One-time products**.

2. Product ID: `1.adfree` (exact).

3. Activate.



## 2. Archerypal Plus (subscription)



1. **Subscriptions** → product `1plus`.

2. Prefer base plan `plus-subscribe` (monthly auto-renewing); activate.

3. Subscribers are **ad-free** while the subscription is active (no separate Remove ads purchase required).



## 3. Lifetime Plus (hidden one-time)



1. One-time product `lifetime` with a **non-zero** list price.

2. Do **not** discount to $0 — Play forbids free IAPs.

3. Create **Monetize → Promo codes → In-app product** one-time-use codes for this SKU.

4. Hand codes to paid A/B testers (or yourself). The app has **no purchase button** for lifetime; ownership restores on launch after Play redemption.

5. Lifetime owners get Plus + ad-free permanently.



## 4. A/B cohorts (same binary)



| Cohort | Entitlement |

|--------|-------------|

| Free | none → ads, no Plus surfaces |

| Paid monthly | `1plus` → Plus + ad-free |

| Paid lifetime | `lifetime` (promo) → Plus + ad-free |



Keep `PLUS_PAYWALL_DISABLED_FOR_TESTING = false` in release builds.



## 5. Test purchases



1. **Setup → License testing** — add tester Gmail accounts.

2. Install from an internal testing track.

3. License testers get free test charges for monthly / remove-ads; lifetime free grants should use **promo codes**.



## 6. Verify in the app



- More → Subscribe to Plus shows monthly price.

- After monthly or lifetime entitlement: **Plus is active**, banners hidden.

- Reinstall → Play restore unlocks again.



## 7. Data safety



Declare **in-app purchases** and **subscriptions**. Payment is processed by Google; the app stores local entitlement flags only.

