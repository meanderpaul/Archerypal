package com.archerypal.app.billing

/** Banner ads hide when the user bought remove-ads or has an active Plus subscription. */
object BillingEntitlements {
    fun shouldShowBannerAds(isAdFree: Boolean, isPlusActive: Boolean): Boolean =
        !isAdFree && !isPlusActive

    fun hasFullStoreAccess(isAdFree: Boolean, isPlusActive: Boolean): Boolean =
        isAdFree && isPlusActive
}
