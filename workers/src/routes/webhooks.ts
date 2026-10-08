import { Hono } from "hono";
import type { AppVariables } from "../middleware/deviceAuth";
import {
	clearEntitlement,
	mapRevenueCatProduct,
	upsertEntitlement,
} from "../db/entitlements";
import { nowSec, textError } from "../util/http";

/**
 * RevenueCat webhook (Play-aligned products).
 * App user id = Friend Code.
 * Entitlement `com_archerypal_app_pro` ← Play `1plus` + promotional lifetime gifts.
 * Entitlement `ad_free` ← Play `1.adfree`.
 */
export const webhookRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

type RcEvent = {
	type?: string;
	app_user_id?: string;
	product_id?: string;
	entitlement_ids?: string[] | null;
	expiration_at_ms?: number | null;
	expiration_at?: number | null;
	store?: string;
	period_type?: string;
};

webhookRoutes.post("/v1/webhooks/revenuecat", async (c) => {
	const expected = c.env.REVENUECAT_WEBHOOK_SECRET;
	if (!expected) return textError(c, 503, "webhook not configured");

	const auth = c.req.header("Authorization") ?? "";
	const bearer = auth.startsWith("Bearer ") ? auth.slice(7) : auth;
	if (bearer !== expected) return textError(c, 401, "unauthorized");

	let payload: { event?: RcEvent };
	try {
		payload = (await c.req.json()) as typeof payload;
	} catch {
		return textError(c, 400, "invalid json");
	}

	const event = payload.event ?? (payload as RcEvent);
	const friendCode = (event.app_user_id ?? "").trim();
	const productId = (event.product_id ?? "").trim();
	const type = (event.type ?? "").toUpperCase();
	const store = (event.store ?? "").toUpperCase();
	const period = (event.period_type ?? "").toUpperCase();
	const entitlementIds = (event.entitlement_ids ?? []).map((e) => e.toLowerCase());
	if (!friendCode) return c.json({ ok: true });

	const promotional =
		store === "PROMOTIONAL" ||
		period === "PROMOTIONAL" ||
		productId.startsWith("rc_promo");

	let sku = mapRevenueCatProduct(productId);
	if (
		!sku &&
		(entitlementIds.includes("com_archerypal_app_pro") ||
			entitlementIds.includes("plus") ||
			promotional)
	) {
		sku = promotional ? "lifetime" : "1plus";
	}
	if (!sku && entitlementIds.includes("ad_free")) {
		sku = "1.adfree";
	}
	if (!sku) return c.json({ ok: true });

	const now = nowSec();
	const grantTypes = new Set([
		"INITIAL_PURCHASE",
		"RENEWAL",
		"PRODUCT_CHANGE",
		"UNCANCELLATION",
		"NON_RENEWING_PURCHASE",
		"TEMPORARY_ENTITLEMENT_GRANT",
		"TRANSFER",
	]);
	const revokeTypes = new Set(["EXPIRATION", "REFUND"]);

	if (grantTypes.has(type)) {
		let expiresAt: number | null = null;
		if (sku === "lifetime" || promotional) {
			expiresAt = null;
			sku = sku === "1.adfree" ? sku : "lifetime";
		} else if (sku === "1plus") {
			const ms =
				event.expiration_at_ms ??
				(event.expiration_at ? event.expiration_at * 1000 : null);
			expiresAt = ms ? Math.floor(ms / 1000) : now + 35 * 24 * 3600;
		}
		await upsertEntitlement(c.env.DB, friendCode, sku, expiresAt, null, now);
	} else if (revokeTypes.has(type)) {
		await clearEntitlement(c.env.DB, friendCode, sku);
	} else if (type === "CANCELLATION" && promotional) {
		await clearEntitlement(c.env.DB, friendCode, sku);
	}

	return c.json({ ok: true });
});
