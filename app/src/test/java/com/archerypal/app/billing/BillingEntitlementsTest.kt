package com.archerypal.app.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEntitlementsTest {
    @Test
    fun freeUserSeesAds() {
        assertTrue(BillingEntitlements.shouldShowBannerAds(isAdFree = false, isPlusActive = false))
    }

    @Test
    fun removeAdsHidesBanners() {
        assertFalse(BillingEntitlements.shouldShowBannerAds(isAdFree = true, isPlusActive = false))
    }

    @Test
    fun monthlyPlusHidesBannersWithoutRemoveAds() {
        assertFalse(BillingEntitlements.shouldShowBannerAds(isAdFree = false, isPlusActive = true))
    }

    @Test
    fun cancelledPlusShowsAdsUnlessRemoveAdsOwned() {
        assertTrue(BillingEntitlements.shouldShowBannerAds(isAdFree = false, isPlusActive = false))
        assertFalse(BillingEntitlements.shouldShowBannerAds(isAdFree = true, isPlusActive = false))
    }

    @Test
    fun lifetimePlusHidesBanners() {
        assertFalse(BillingEntitlements.shouldShowBannerAds(isAdFree = false, isPlusActive = true))
    }

    @Test
    fun plusSkusRecognized() {
        assertTrue(BillingProducts.isPlusSku(BillingProducts.PLUS_MONTHLY))
        assertTrue(BillingProducts.isPlusSku(BillingProducts.PLUS_LIFETIME))
        assertFalse(BillingProducts.isPlusSku(BillingProducts.REMOVE_ADS))
    }

    @Test
    fun paywallEnabledForRelease() {
        assertFalse(PlusFeatures.PLUS_PAYWALL_DISABLED_FOR_TESTING)
        assertFalse(PlusFeatures.hasPlusAccess(subscribed = false))
        assertTrue(PlusFeatures.hasPlusAccess(subscribed = true))
    }
}
