package com.archerypal.app.data

object LinkConfig {
    /**
     * Host for shareable https links. Must match the App Links intent filter in
     * AndroidManifest.xml and serve `/.well-known/assetlinks.json` from the Worker.
     */
    const val PUBLIC_HOST = "archerypal-api.pauljreedii.workers.dev"
    const val FRIEND_PATH = "/f/"
    const val INVITE_PATH = "/r/"
}
