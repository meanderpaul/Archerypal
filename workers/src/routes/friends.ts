import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { getPlayer } from "../db/players";
import {
	addFriendship,
	createFriendRequest,
	listFriends,
	listFriendSuggestions,
	listIncomingRequests,
	listOutgoingRequests,
	updateFriendRequestStatus,
} from "../db/friends";
import { isFriendCode } from "../util/validate";
import { nowSec, textError } from "../util/http";
import { allowRate } from "../util/rateLimit";
import { sendPush } from "../push/fcm";

export const friendRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const FRIEND_REQUESTS_PER_DAY = 30;
const FRIEND_REQUEST_CHANNEL = "friend_requests";
const DAY_MS = 24 * 60 * 60 * 1000;

friendRoutes.get("/v1/friends", deviceAuth, async (c) => {
	const friends = await listFriends(c.env.DB, c.get("friendCode"));
	return c.json({ friends });
});

friendRoutes.post("/v1/friends/add", deviceAuth, async (c) => {
	// Instant mutual friendship removed — it allowed forcing private-feed visibility (IDOR).
	// Use request → accept / decline instead.
	return textError(c, 410, "use /v1/friends/request then /v1/friends/accept");
});

friendRoutes.post("/v1/friends/request", deviceAuth, async (c) => {
	const raw = c.get("rawBody");
	let body: { theirFriendCode?: string };
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const their = (body.theirFriendCode ?? "").trim();
	const self = c.get("friendCode");
	if (!isFriendCode(their) || their === self) return textError(c, 400, "invalid theirFriendCode");
	if (!allowRate(`friend-request:${self}`, FRIEND_REQUESTS_PER_DAY, DAY_MS)) {
		return textError(c, 429, "too many friend requests today");
	}
	const recipient = await getPlayer(c.env.DB, their);
	if (!recipient) return textError(c, 404, "player not found");
	const result = await createFriendRequest(c.env.DB, self, their, nowSec());
	if (result === "conflict") return textError(c, 409, "request already exists");
	const sender = await getPlayer(c.env.DB, self);
	c.executionCtx.waitUntil(
		sendPush(c.env, recipient.fcmToken, {
			title: "New friend request",
			body: `${sender?.displayName ?? self} wants to be friends on Archerypal`,
			channelId: FRIEND_REQUEST_CHANNEL,
			data: { type: "friend_request", fromFriendCode: self },
		}).catch((err) => console.error("friend request push failed", err)),
	);
	return c.json({ ok: true });
});

friendRoutes.post("/v1/friends/accept", deviceAuth, async (c) => {
	const raw = c.get("rawBody");
	let body: { fromFriendCode?: string };
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const from = (body.fromFriendCode ?? "").trim();
	const self = c.get("friendCode");
	if (!isFriendCode(from)) return textError(c, 400, "invalid fromFriendCode");
	const updated = await updateFriendRequestStatus(c.env.DB, from, self, "accepted", nowSec());
	if (!updated) return textError(c, 404, "request not found");
	await addFriendship(c.env.DB, from, self, nowSec());
	const other = await getPlayer(c.env.DB, from);
	return c.json({
		friend: {
			friendCode: from,
			displayName: other?.displayName ?? from,
		},
	});
});

friendRoutes.post("/v1/friends/decline", deviceAuth, async (c) => {
	const raw = c.get("rawBody");
	let body: { fromFriendCode?: string };
	try {
		body = JSON.parse(new TextDecoder().decode(raw)) as typeof body;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const from = (body.fromFriendCode ?? "").trim();
	const self = c.get("friendCode");
	if (!isFriendCode(from)) return textError(c, 400, "invalid fromFriendCode");
	const updated = await updateFriendRequestStatus(c.env.DB, from, self, "declined", nowSec());
	if (!updated) return textError(c, 404, "request not found");
	return c.json({ ok: true });
});

friendRoutes.get("/v1/friends/requests", deviceAuth, async (c) => {
	const requests = await listIncomingRequests(c.env.DB, c.get("friendCode"));
	return c.json({ requests });
});

friendRoutes.get("/v1/friends/requests/outgoing", deviceAuth, async (c) => {
	const requests = await listOutgoingRequests(c.env.DB, c.get("friendCode"));
	return c.json({ requests });
});

friendRoutes.get("/v1/friends/suggestions", deviceAuth, async (c) => {
	const suggestions = await listFriendSuggestions(c.env.DB, c.get("friendCode"));
	return c.json({ suggestions });
});
