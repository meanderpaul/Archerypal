/** Secrets set via `wrangler secret put` (merged into Env). */
interface Env {
	JWT_SECRET: string;
	GOOGLE_OAUTH_CLIENT_ID: string;
	REVENUECAT_WEBHOOK_SECRET: string;
	/** Shared with the Android app (`X-App-Key`) for bootstrap endpoints. */
	APP_CLIENT_KEY: string;
	ADMIN_API_KEY?: string;
	/** Local/dev only via `.dev.vars` — never set on production. */
	BYPASS_PLUS_ENTITLEMENT?: string;
	/** Local/dev only — allow client `/v1/entitlements/sync` without RC verify. */
	ALLOW_UNVERIFIED_ENTITLEMENT_SYNC?: string;
	PUBLIC_WS_BASE?: string;
	/** Unset (off) until photo posts ship at full rollout; mirrors `PlusFeatures.PHOTO_POSTS_ENABLED`. */
	PHOTO_POSTS_ENABLED?: string;
	/** Firebase service-account JSON (secret). Push is a no-op when unset. */
	FCM_SERVICE_ACCOUNT_JSON?: string;
	/** Comma-separated SHA-256 fingerprints (Play app signing + upload key) for App Links. */
	ANDROID_RELEASE_CERT_SHA256?: string;
	/** Comma-separated SHA-256 fingerprints for the `.debug` build. */
	ANDROID_DEBUG_CERT_SHA256?: string;
}
