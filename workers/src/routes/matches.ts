import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { requirePlus } from "../middleware/plus";
import { issueRoomToken, parseRoomToken } from "../auth/jwt";
import { getPlayer } from "../db/players";
import {
	applyMatchStats,
	closeRoom,
	createRoom,
	getRoom,
	insertCompletedMatch,
	type CompletePlayer,
} from "../db/rooms";
import { isFriendCode } from "../util/validate";
import { nowSec, textError } from "../util/http";
import { randomHex } from "../util/crypto";
import { allowRate } from "../util/rateLimit";

export const matchRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const ROOM_TOKEN_TTL_SEC = 15 * 60;
/** Reject instant fabricate-and-complete leaderboard spam. */
const MIN_ROOM_AGE_SEC = 30;
/** Soft cap per-player score to limit leaderboard inflation. */
const MAX_PLAYER_TOTAL = 10_000;
const MAX_COMPLETIONS_PER_HOST_PER_HOUR = 20;

function publicWsBase(env: Env, requestUrl: string): string {
	if (env.PUBLIC_WS_BASE) return env.PUBLIC_WS_BASE.replace(/\/$/, "");
	const u = new URL(requestUrl);
	const proto = u.protocol === "https:" ? "wss:" : "ws:";
	return `${proto}//${u.host}`;
}

matchRoutes.post("/v1/matches/challenge", deviceAuth, async (c) => {
	const denied = await requirePlus(c);
	if (denied) return denied;
	if (!c.env.JWT_SECRET) return textError(c, 503, "JWT_SECRET not configured");

	const raw = c.get("rawBody");
	let body: { targetFriendCode?: string };
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const target = (body.targetFriendCode ?? "").trim();
	const host = c.get("friendCode");
	if (!isFriendCode(target) || target === host) {
		return textError(c, 400, "targetFriendCode required");
	}
	if (!(await getPlayer(c.env.DB, target))) return textError(c, 404, "target not found");

	const roomId = randomHex(16);
	const now = nowSec();
	await createRoom(c.env.DB, roomId, host, target, now, now + ROOM_TOKEN_TTL_SEC * 2);

	const hostToken = await issueRoomToken(c.env.JWT_SECRET, roomId, host, true, ROOM_TOKEN_TTL_SEC);
	const joinToken = await issueRoomToken(
		c.env.JWT_SECRET,
		roomId,
		target,
		false,
		ROOM_TOKEN_TTL_SEC,
	);

	// Invite carries join JWT only (short TTL). Host display name is not put in the URI.
	const inviteUri = `archerypal://remote?roomId=${roomId}&token=${joinToken}`;

	const targetPlayer = await getPlayer(c.env.DB, target);
	const targetFcm = targetPlayer?.fcmToken ? "present" : "";
	// FCM send deferred to later phase

	// Token is sent on the WebSocket upgrade via Authorization, not the query string.
	const wsUrl = `${publicWsBase(c.env, c.req.url)}/v1/room/${roomId}`;
	return c.json({
		roomId,
		wsUrl,
		hostToken,
		joinToken,
		inviteUri,
		targetFcm,
	});
});

matchRoutes.post("/v1/matches/:roomId/complete", deviceAuth, async (c) => {
	const denied = await requirePlus(c);
	if (denied) return denied;

	const roomId = c.req.param("roomId");
	const host = c.get("friendCode");

	if (!allowRate(`match-complete:${host}`, MAX_COMPLETIONS_PER_HOST_PER_HOUR, 60 * 60 * 1000)) {
		return textError(c, 429, "too many match completions");
	}

	const room = await getRoom(c.env.DB, roomId);
	if (!room) return textError(c, 404, "room not found");
	if (room.hostFriendCode !== host) return textError(c, 403, "host only");
	if (room.status !== "open") return textError(c, 409, "room not open");

	const now = nowSec();
	if (room.createdAt > 0 && now - room.createdAt < MIN_ROOM_AGE_SEC) {
		return textError(c, 400, "match too short to complete");
	}

	const raw = c.get("rawBody");
	let body: {
		matchId?: string;
		targetCount?: number;
		scoringType?: string;
		completedAt?: number;
		winnerFriendCode?: string;
		players?: CompletePlayer[];
	};
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}

	const matchId = (body.matchId ?? "").trim();
	const winner = (body.winnerFriendCode ?? "").trim();
	const players = body.players ?? [];
	if (!matchId || !winner || players.length === 0) {
		return textError(c, 400, "invalid complete payload");
	}

	const allowed = new Set(
		[room.hostFriendCode, room.joinerFriendCode].filter(Boolean) as string[],
	);
	for (const p of players) {
		if (!allowed.has(p.friendCode)) return textError(c, 400, "player not in room");
		if (!Number.isFinite(p.total) || p.total < 0 || p.total > MAX_PLAYER_TOTAL) {
			return textError(c, 400, "invalid score");
		}
	}
	if (!players.some((p) => p.friendCode === winner)) {
		return textError(c, 400, "winner not in players");
	}

	let completedAt = body.completedAt ?? 0;
	if (!completedAt) completedAt = Date.now();
	const completedAtSec = completedAt > 1e12 ? Math.floor(completedAt / 1000) : completedAt;

	const inserted = await insertCompletedMatch(
		c.env.DB,
		matchId,
		completedAtSec,
		(body.scoringType ?? "").trim() || "unknown",
		body.targetCount ?? 0,
		winner,
		JSON.stringify(players),
		roomId,
	);
	if (inserted === "conflict") return textError(c, 409, "match already completed");

	await applyMatchStats(c.env.DB, players, winner, now);
	await closeRoom(c.env.DB, roomId);

	try {
		const id = c.env.MATCH_ROOM.idFromName(roomId);
		await c.env.MATCH_ROOM.get(id).fetch("https://room/close");
	} catch {
		/* ignore */
	}

	return c.json({ ok: true });
});

function roomTokenFromRequest(c: { req: { header: (n: string) => string | undefined; query: (n: string) => string | undefined } }): string {
	const auth = (c.req.header("Authorization") ?? "").trim();
	const bearer = /^Bearer\s+(.+)$/i.exec(auth);
	if (bearer?.[1]) return bearer[1].trim();
	// Legacy query-string tokens (old clients / invite deep links still embed JWT for share).
	return (c.req.query("token") ?? "").trim();
}

matchRoutes.get("/v1/room/:roomId", async (c) => {
	if (c.req.header("Upgrade")?.toLowerCase() !== "websocket") {
		return textError(c, 426, "upgrade required");
	}
	if (!c.env.JWT_SECRET) return textError(c, 503, "JWT_SECRET not configured");

	const roomId = c.req.param("roomId");
	const token = roomTokenFromRequest(c);
	const claims = await parseRoomToken(c.env.JWT_SECRET, token);
	if (!claims || claims.room_id !== roomId) return textError(c, 401, "invalid token");

	const room = await getRoom(c.env.DB, roomId);
	if (!room || room.status !== "open") return textError(c, 404, "room not found");

	const id = c.env.MATCH_ROOM.idFromName(roomId);
	const stub = c.env.MATCH_ROOM.get(id);
	const joinUrl = new URL("https://room/join");
	joinUrl.searchParams.set("friendCode", claims.friend_code);
	return stub.fetch(joinUrl.toString(), c.req.raw);
});
