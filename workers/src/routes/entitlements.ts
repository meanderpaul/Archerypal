import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { upsertEntitlement } from "../db/entitlements";
import { sha256 } from "../util/crypto";
import { nowSec, textError } from "../util/http";

export const entitlementRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

function unverifiedSyncAllowed(env: Env): boolean {
	const v = env.ALLOW_UNVERIFIED_ENTITLEMENT_SYNC;
	return v === "true" || v === "1";
}

entitlementRoutes.post("/v1/entitlements/sync", deviceAuth, async (c) => {
	// Production source of truth is RevenueCat webhook (`/v1/webhooks/revenuecat`).
	// Client sync is testing-only behind an explicit flag (never set on production).
	if (!unverifiedSyncAllowed(c.env)) {
		return textError(
			c,
			403,
			"server-side entitlement verification required; unverified entitlement sync disabled",
		);
	}

	const raw = c.get("rawBody");
	let body: {
		sku?: string;
		purchaseToken?: string;
		purchaseTokenHash?: string;
		expiresAt?: number;
	};
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}

	const sku = (body.sku ?? "").trim();
	if (sku !== "1plus" && sku !== "lifetime" && sku !== "1.adfree") {
		return textError(c, 400, "invalid sku");
	}

	let tokenHash: Uint8Array | null = null;
	if (body.purchaseToken) {
		tokenHash = await sha256(body.purchaseToken);
	} else if (body.purchaseTokenHash && /^[0-9a-fA-F]{64}$/.test(body.purchaseTokenHash)) {
		const hex = body.purchaseTokenHash.toLowerCase();
		tokenHash = new Uint8Array(32);
		for (let i = 0; i < 32; i++) tokenHash[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
	} else {
		return textError(c, 400, "sku and purchaseToken or purchaseTokenHash required");
	}

	// Subscription SKUs need a positive expiresAt; only lifetime may be permanent (null).
	let expiresAt: number | null;
	if (sku === "lifetime") {
		expiresAt = null;
	} else if (body.expiresAt && body.expiresAt > 0) {
		expiresAt = body.expiresAt;
	} else {
		return textError(c, 400, "expiresAt required for non-lifetime SKUs");
	}

	await upsertEntitlement(c.env.DB, c.get("friendCode"), sku, expiresAt, tokenHash, nowSec());
	return c.json({ ok: true });
});
