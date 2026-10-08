import { Hono, type Context } from "hono";
import { deviceAuth, legacyFriendCodeAuth, type AppVariables } from "../middleware/deviceAuth";
import { requireAppKey } from "../middleware/appKey";
import { verifyGoogleIdToken } from "../auth/google";
import { getLinkByFriend, getLinkBySub, linkGoogle } from "../db/google";
import {
	ensureDeviceId,
	getPlayer,
	insertPlayer,
	rotateDeviceSecret,
	clearFcmToken,
} from "../db/players";
import { generateFriendCode, maskEmail, randomHex, sha256 } from "../util/crypto";
import { displayNameOk } from "../util/validate";
import { nowSec, readJson, textError } from "../util/http";
import { allowRate, clientIp } from "../util/rateLimit";

export const authRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

type AuthCtx = Context<{ Bindings: Env; Variables: AppVariables }>;

const RESTORE_LIMIT = 10;
const RESTORE_WINDOW_MS = 60_000;

async function requireGoogleConfigured(c: { env: Env }): Promise<Response | null> {
	if (!c.env.GOOGLE_OAUTH_CLIENT_ID) {
		return new Response("google auth not configured", { status: 503 });
	}
	return null;
}

function rateLimitAuth(c: AuthCtx): Response | null {
	const ip = clientIp(c);
	if (!allowRate(`google-auth:${ip}`, RESTORE_LIMIT, RESTORE_WINDOW_MS)) {
		return textError(c, 429, "too many requests");
	}
	return null;
}

authRoutes.post("/v1/auth/google/link", deviceAuth, async (c) => {
	const missing = await requireGoogleConfigured(c);
	if (missing) return missing;

	const raw = c.get("rawBody");
	let body: { idToken?: string };
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const verified = await verifyGoogleIdToken(
		(body.idToken ?? "").trim(),
		c.env.GOOGLE_OAUTH_CLIENT_ID,
	);
	if (!verified) return textError(c, 401, "unauthorized");

	const self = c.get("friendCode");
	const bySub = await getLinkBySub(c.env.DB, verified.sub);
	if (bySub && bySub.friendCode !== self) {
		return textError(c, 409, "google account already linked to another friend id");
	}
	if (bySub && bySub.friendCode === self) {
		return c.json({ linked: true, email: maskEmail(verified.email) });
	}

	const byFriend = await getLinkByFriend(c.env.DB, self);
	if (byFriend) {
		if (byFriend.googleSub !== verified.sub) {
			return textError(c, 409, "friend id already linked to another google account");
		}
		return c.json({ linked: true, email: maskEmail(verified.email) });
	}

	const result = await linkGoogle(c.env.DB, verified.sub, self, nowSec());
	if (result === "conflict") return textError(c, 409, "google account already linked");
	return c.json({ linked: true, email: maskEmail(verified.email) });
});

authRoutes.get("/v1/auth/google/status", deviceAuth, async (c) => {
	const link = await getLinkByFriend(c.env.DB, c.get("friendCode"));
	if (!link) return c.json({ linked: false });
	return c.json({ linked: true, email: null });
});

authRoutes.post("/v1/auth/google/restore", requireAppKey, async (c) => {
	const limited = rateLimitAuth(c);
	if (limited) return limited;
	const missing = await requireGoogleConfigured(c);
	if (missing) return missing;
	const body = await readJson<{ idToken?: string }>(c);
	if (!body) return textError(c, 400, "invalid json");
	const verified = await verifyGoogleIdToken(
		(body.idToken ?? "").trim(),
		c.env.GOOGLE_OAUTH_CLIENT_ID,
	);
	if (!verified) return textError(c, 401, "unauthorized");

	const link = await getLinkBySub(c.env.DB, verified.sub);
	if (!link) return textError(c, 404, "not found");
	const player = await getPlayer(c.env.DB, link.friendCode);
	if (!player) return textError(c, 404, "not found");

	const deviceSecret = randomHex(32);
	const now = nowSec();
	await rotateDeviceSecret(c.env.DB, link.friendCode, await sha256(deviceSecret), now);
	await clearFcmToken(c.env.DB, link.friendCode, now);
	const deviceId = await ensureDeviceId(c.env.DB, link.friendCode);
	return c.json({
		friendCode: link.friendCode,
		deviceSecret,
		deviceId,
		displayName: player.displayName,
		email: maskEmail(verified.email),
		created: false,
	});
});

authRoutes.post("/v1/auth/google/sign-in", requireAppKey, async (c) => {
	const limited = rateLimitAuth(c);
	if (limited) return limited;
	const missing = await requireGoogleConfigured(c);
	if (missing) return missing;
	const body = await readJson<{
		idToken?: string;
		displayName?: string;
		inviteCode?: string;
	}>(c);
	if (!body) return textError(c, 400, "invalid json");
	const verified = await verifyGoogleIdToken(
		(body.idToken ?? "").trim(),
		c.env.GOOGLE_OAUTH_CLIENT_ID,
	);
	if (!verified) return textError(c, 401, "unauthorized");

	const existing = await getLinkBySub(c.env.DB, verified.sub);
	if (existing) {
		const player = await getPlayer(c.env.DB, existing.friendCode);
		if (!player) return textError(c, 500, "server error");
		const deviceSecret = randomHex(32);
		const now = nowSec();
		await rotateDeviceSecret(
			c.env.DB,
			existing.friendCode,
			await sha256(deviceSecret),
			now,
		);
		await clearFcmToken(c.env.DB, existing.friendCode, now);
		const deviceId = await ensureDeviceId(c.env.DB, existing.friendCode);
		return c.json({
			friendCode: existing.friendCode,
			deviceSecret,
			deviceId,
			displayName: player.displayName,
			email: maskEmail(verified.email),
			created: false,
		});
	}

	const displayName = (body.displayName ?? "").trim() || "Archer";
	if (!displayNameOk(displayName)) return textError(c, 400, "invalid displayName");

	let friendCode = generateFriendCode();
	const deviceSecret = randomHex(32);
	const hash = await sha256(deviceSecret);
	let deviceId = "";
	for (let i = 0; i < 8; i++) {
		const inserted = await insertPlayer(
			c.env.DB,
			friendCode,
			displayName,
			hash,
			null,
			nowSec(),
		);
		if (inserted.status === "ok") {
			deviceId = inserted.deviceId;
			break;
		}
		friendCode = generateFriendCode();
		if (i === 7) return textError(c, 500, "server error");
	}

	const linked = await linkGoogle(c.env.DB, verified.sub, friendCode, nowSec());
	if (linked === "conflict") return textError(c, 409, "google account already linked");

	return c.json({
		friendCode,
		deviceSecret,
		deviceId,
		displayName,
		email: maskEmail(verified.email),
		created: true,
	});
});

/** Legacy installs: mint/fetch opaque deviceId using cleartext X-Friend-Code + HMAC. */
authRoutes.post("/v1/auth/device-bind", legacyFriendCodeAuth, async (c) => {
	const deviceId = await ensureDeviceId(c.env.DB, c.get("friendCode"));
	return c.json({ deviceId });
});
