package com.archerypal.app.billing

/**
 * Feature flags for Plus and remote play.
 */
object PlusFeatures {
    const val REMOTE_PLAY_ENABLED = true

    /**
     * Must stay false for release / A/B testing.
     * Testers get Plus via `1plus` purchase or a RevenueCat promotional grant of
     * `com_archerypal_app_pro` (lifetime) — not this flag.
     */
    const val PLUS_PAYWALL_DISABLED_FOR_TESTING = false

    /**
     * Photo posts stay off until media storage ships at full rollout. Keep in sync with the
     * Worker `PHOTO_POSTS_ENABLED` var, and don't add photos to [VALUE_POINTS] until both are on.
     */
    const val PHOTO_POSTS_ENABLED = false

    val VALUE_POINTS: List<String> = listOf(
        "Post to the feed",
        "Create and host shoots",
        "Remote matches with friends anywhere",
        "Global standings",
        "No ads"
    )

    fun hasPlusAccess(subscribed: Boolean): Boolean =
        PLUS_PAYWALL_DISABLED_FOR_TESTING || subscribed
}
