package com.archerypal.app.push

import android.content.Context
import com.archerypal.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object PushTokenRegistrar {
    fun refresh(context: Context) {
        if (!BuildConfig.HAS_GOOGLE_SERVICES) return

        runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener(PushTokenStore::update)
        }
    }
}
