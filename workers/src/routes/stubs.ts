import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { getDiscoverable, getPlayer } from "../db/players";
import { areFriends } from "../db/friends";
import { isFriendCode } from "../util/validate";
import { allowRate, clientIp } from "../util/rateLimit";
import { textError } from "../util/http";

/**
 * Remaining Phase-1 stubs: profiles (non-persisted).
 * Posts live in routes/posts.ts; shoots + feed in dedicated modules.
 */
export const stubRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

stubRoutes.get("/v1/profiles/me", deviceAuth, async (c) => {
	const player = await getPlayer(c.env.DB, c.get("friendCode"));
	if (!player) return textError(c, 404, "not found");
	const discoverable = await getDiscoverable(c.env.DB, player.friendCode);
	return c.json({
		profile: {
			friendCode: player.friendCode,
			displayName: player.displayName,
			bio: "",
			club: "",
			bowType: "",
			homeRange: "",
			locationLabel: "",
			updatedAt: 0,
			discoverable,
		},
	});
});

stubRoutes.get("/v1/profiles/:code", deviceAuth, async (c) => {
	const code = c.req.param("code");
	if (!isFriendCode(code)) return textError(c, 400, "invalid friend code");

	const ip = clientIp(c);
	if (!allowRate(`profile-lookup:${ip}`, 60, 60_000)) {
		return textError(c, 429, "too many requests");
	}

	const viewer = c.get("friendCode");
	if (code !== viewer) {
		const friends = await areFriends(c.env.DB, viewer, code);
		if (!friends) return textError(c, 404, "not found");
	}

	const player = await getPlayer(c.env.DB, code);
	if (!player) return textError(c, 404, "not found");
	return c.json({
		profile: {
			friendCode: player.friendCode,
			displayName: player.displayName,
			bio: "",
			club: "",
			bowType: "",
			homeRange: "",
			locationLabel: "",
			updatedAt: 0,
		},
	});
});

stubRoutes.put("/v1/profiles/me", deviceAuth, async (c) => {
	const player = await getPlayer(c.env.DB, c.get("friendCode"));
	if (!player) return textError(c, 404, "not found");
	const raw = c.get("rawBody");
	let body: Record<string, string> = {};
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as Record<string, string>;
	} catch {
		return textError(c, 400, "invalid json");
	}
	return c.json({
		profile: {
			friendCode: player.friendCode,
			displayName: player.displayName,
			bio: body.bio ?? "",
			club: body.club ?? "",
			bowType: body.bowType ?? "",
			homeRange: body.homeRange ?? "",
			locationLabel: body.locationLabel ?? "",
			updatedAt: Math.floor(Date.now() / 1000),
			discoverable: await getDiscoverable(c.env.DB, player.friendCode),
		},
	});
});

stubRoutes.delete("/v1/profiles/me", deviceAuth, (c) => c.json({ ok: true }));
