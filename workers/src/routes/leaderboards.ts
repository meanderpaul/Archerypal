import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { requirePlus } from "../middleware/plus";
import {
	friendsLeaderboard,
	globalLeaderboard,
	leaderboardEtag,
} from "../db/stats";
import { clampLimit } from "../util/validate";

export const leaderboardRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

leaderboardRoutes.get("/v1/leaderboards/global", deviceAuth, async (c) => {
	const denied = await requirePlus(c);
	if (denied) return denied;
	const limit = clampLimit(c.req.query("limit"));
	const entries = await globalLeaderboard(c.env.DB, limit);
	const etag = await leaderboardEtag(entries);
	if (c.req.header("If-None-Match") === etag) {
		return new Response(null, { status: 304, headers: { ETag: etag } });
	}
	return c.json({ entries }, 200, { ETag: etag });
});

leaderboardRoutes.get("/v1/leaderboards/friends", deviceAuth, async (c) => {
	const entries = await friendsLeaderboard(c.env.DB, c.get("friendCode"));
	const etag = await leaderboardEtag(entries);
	if (c.req.header("If-None-Match") === etag) {
		return new Response(null, { status: 304, headers: { ETag: etag } });
	}
	return c.json({ entries }, 200, { ETag: etag });
});
