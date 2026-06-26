package com.archerypal.app.ads

import com.archerypal.app.BuildConfig

object AdIds {
    const val APP_ID = "ca-app-pub-9822446624840365~8399062865"
    const val BANNER = "ca-app-pub-9822446624840365/7974154511"

    // Google sample banner — used in debug builds so you don't risk invalid traffic.
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"

    val bannerUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER else BANNER
}
