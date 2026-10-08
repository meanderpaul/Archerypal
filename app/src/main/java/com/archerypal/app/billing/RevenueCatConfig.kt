package com.archerypal.app.billing

import android.content.Context
import com.archerypal.app.BuildConfig
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

object RevenueCatConfig {
    /**
     * Plus / Pro (remote play, social, leaderboards). Attached to Play `1plus` and
     * promotional lifetime gifts — not to `1.adfree`.
     */
    const val ENTITLEMENT_PRO = "com_archerypal_app_pro"

    /** Ads-only product Play `1.adfree`. */
    const val ENTITLEMENT_AD_FREE = "ad_free"

    fun isConfigured(): Boolean = BuildConfig.REVENUECAT_API_KEY.isNotBlank()

    fun configure(context: Context) {
        val key = BuildConfig.REVENUECAT_API_KEY.trim()
        if (key.isEmpty()) return
        if (Purchases.isConfigured) return
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(
            PurchasesConfiguration.Builder(context.applicationContext, key).build()
        )
    }
}
