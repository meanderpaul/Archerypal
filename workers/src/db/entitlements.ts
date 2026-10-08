const PLUS_SKUS = ["1plus", "lifetime"] as const;

export async function hasActivePlus(db: D1Database, friendCode: string, now: number): Promise<boolean> {
	for (const sku of PLUS_SKUS) {
		const row = await db
			.prepare(
				`SELECT expires_at FROM entitlements WHERE friend_code = ? AND sku = ?`,
			)
			.bind(friendCode, sku)
			.first<{ expires_at: number | null }>();
		if (!row) continue;
		if (row.expires_at == null || row.expires_at === 0 || now < row.expires_at) return true;
	}
	return false;
}

export async function upsertEntitlement(
	db: D1Database,
	friendCode: string,
	sku: string,
	expiresAt: number | null,
	playTokenHash: Uint8Array | null,
	now: number,
): Promise<void> {
	await db
		.prepare(
			`INSERT INTO entitlements (friend_code, sku, expires_at, play_token_hash, updated_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(friend_code, sku) DO UPDATE SET
         expires_at = excluded.expires_at,
         play_token_hash = COALESCE(excluded.play_token_hash, entitlements.play_token_hash),
         updated_at = excluded.updated_at`,
		)
		.bind(friendCode, sku, expiresAt, playTokenHash, now)
		.run();
}

export async function clearEntitlement(
	db: D1Database,
	friendCode: string,
	sku: string,
): Promise<void> {
	await db
		.prepare(`DELETE FROM entitlements WHERE friend_code = ? AND sku = ?`)
		.bind(friendCode, sku)
		.run();
}

/** Map RevenueCat product id → local SKU row. */
export function mapRevenueCatProduct(productId: string): string | null {
	const id = productId.trim();
	if (!id) return null;
	// Dashboard gifts look like rc_promo_<entitlement>_lifetime (entitlement may say "pro", not "plus").
	if (
		id.startsWith("rc_promo") &&
		(id.toLowerCase().includes("plus") ||
			id.toLowerCase().includes("pro") ||
			id.toLowerCase().includes("lifetime"))
	) {
		return "lifetime";
	}
	if (id === "lifetime" || id.toLowerCase().includes("lifetime")) return "lifetime";
	if (id === "1plus" || id.includes("1plus") || id.toLowerCase().includes("plus-subscribe")) {
		return "1plus";
	}
	if (id === "1.adfree" || id.toLowerCase().includes("adfree") || id.toLowerCase().includes("ad_free")) {
		return "1.adfree";
	}
	return null;
}
