package com.archerypal.app

import android.app.Application
import com.archerypal.app.billing.RevenueCatConfig

class ArcherypalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RevenueCatConfig.configure(this)
    }
}
