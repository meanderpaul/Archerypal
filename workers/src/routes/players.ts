import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { requireAppKey } from "../middleware/appKey";
import { insertPlayer, searchPlayers, setDiscoverable, updatePlayer } from "../db/players";
import { sha256 } from "../util/crypto";
import { deviceSecretOk, displayNameOk, isFriendCode } from "../util/validate";
import { nowSec, readJson, textError } from "../util/http";
import { allowRate, clientIp } from "../util/rateLimit";

export const playerRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const REGISTER_LIMIT = 5;
const REGISTER_WINDOW_MS = 60_000;

playerRoutes.post("/v1/players/register", requireAppKey, async (c) => {
	const ip = clientIp(c);
	if (!allowRate(`register:${ip}`, REGISTER_LIMIT, REGISTER_WINDOW_MS)) {
		return textError(c, 429, "too many requests");
	}

	const body = await readJson<{
		friendCode?: string;
		displayName?: string;
		deviceSecret?: string;
		fcmToken?: string;
	}>(c);
	if (!body) return textError(c, 400, "invalid json");

	const friendCode = (body.friendCode ?? "").trim();
	const displayName = (body.displayName ?? "").trim();
	const deviceSecret = (body.deviceSecret ?? "").trim();
	const fcmToken = body.fcmToken?.trim() || null;

	if (!isFriendCode(friendCode)) return textError(c, 400, "invalid friendCode");
	if (!displayNameOk(displayName)) return textError(c, 400, "invalid displayName");
	if (!deviceSecretOk(deviceSecret)) return textError(c, 400, "invalid deviceSecret");

	const hash = await sha256(deviceSecret);
	const result = await insertPlayer(c.env.DB, friendCode, displayName, hash, fcmToken, nowSec());
	if (result.status === "conflict") return textError(c, 409, "friend code taken");
	return c.json({ friendCode, deviceId: result.deviceId });
});

playerRoutes.patch("/v1/players/me", deviceAuth, async (c) => {
	const raw = c.get("rawBody");
	let body: { displayName?: string; fcmToken?: string | null; discoverable?: boolean | null };
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const displayName = (body.displayName ?? "").trim();
	if (!displayNameOk(displayName)) return textError(c, 400, "invalid displayName");
	const fcm =
		body.fcmToken === undefined ? undefined : body.fcmToken === null ? null : body.fcmToken.trim();
	const now = nowSec();
	await updatePlayer(c.env.DB, c.get("friendCode"), displayName, fcm, now);
	if (typeof body.discoverable === "boolean") {
		await setDiscoverable(c.env.DB, c.get("friendCode"), body.discoverable, now);
	}
	return c.json({ friendCode: c.get("friendCode") });
});

const SEARCH_MIN_CHARS = 2;
const SEARCH_MAX_CHARS = 32;
const SEARCH_PER_MINUTE = 30;

playerRoutes.get("/v1/players/search", deviceAuth, async (c) => {
	const self = c.get("friendCode");
	const q = (c.req.query("q") ?? "").trim();
	if (q.length < SEARCH_MIN_CHARS || q.length > SEARCH_MAX_CHARS) {
		return textError(c, 400, "query must be 2-32 characters");
	}
	if (!allowRate(`player-search:${self}`, SEARCH_PER_MINUTE, 60_000)) {
		return textError(c, 429, "too many searches");
	}
	const results = await searchPlayers(c.env.DB, self, q);
	return c.json({ results });
});
