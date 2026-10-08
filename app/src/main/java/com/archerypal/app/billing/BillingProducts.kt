package com.archerypal.app.billing

object BillingProducts {
    /** One-time in-app product — removes home-screen banner ads. Play Console: `1.adfree`. */
    const val REMOVE_ADS = "1.adfree"

    /**
     * Subscription — Archerypal Plus (remote friend matches, social, server leaderboards).
     * Play Console / RevenueCat product ID: `1plus`.
     */
    const val PLUS_MONTHLY = "1plus"

    /**
     * Preferred subscription base-plan / offer id for in-app purchase (auto-renewing monthly).
     * Falls back to the first available offer if this id is missing.
     */
    const val PLUS_BASE_PLAN_SUBSCRIBE = "plus-subscribe"

    /**
     * Lifetime Archerypal Plus — **gift-only** for testers via RevenueCat promotional
     * entitlements (dashboard). Never offered in the in-app store; do not sell with
     * Google Play promo codes.
     */
    const val PLUS_LIFETIME = "lifetime"

    fun isPlusSku(sku: String): Boolean =
        sku == PLUS_MONTHLY || sku == PLUS_LIFETIME
}
