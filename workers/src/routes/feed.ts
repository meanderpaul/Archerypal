import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { listFriends } from "../db/friends";
import { listVisiblePosts, type Post } from "../db/posts";
import { listVisibleShoots, type Shoot } from "../db/shoots";

export const feedRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const KM_PER_MILE = 1.609344;
const DEFAULT_NEARBY_KM = 50 * KM_PER_MILE;
/** "Regional" posts and events: roughly a day-trip drive from the viewer. */
const REGIONAL_KM = 150 * KM_PER_MILE;

type FeedAuthor = {
	friendCode: string;
	displayName: string;
};

type FeedItem = {
	type: string;
	id: string;
	createdAt: number;
	category?: string;
	author?: FeedAuthor;
	body?: string;
	imageKey?: string;
	commentCount?: number;
	shoot?: Shoot;
};

function haversineKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
	const earth = 6371;
	const rad = Math.PI / 180;
	const dLat = (lat2 - lat1) * rad;
	const dLng = (lng2 - lng1) * rad;
	const a =
		Math.sin(dLat / 2) ** 2 +
		Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) ** 2;
	return earth * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function shootCategory(sh: Shoot, viewer: string, friends: Set<string>): string {
	if (sh.isCurated || sh.isPublic) return "global";
	if (sh.hostFriendCode) {
		if (sh.hostFriendCode === viewer) return "friend";
		if (friends.has(sh.hostFriendCode)) return "friend";
	}
	return "friend";
}

function postToFeedItem(post: Post): FeedItem {
	return {
		type: "post",
		id: `post-${post.id}`,
		createdAt: post.createdAt,
		category: "post",
		author: {
			friendCode: post.authorCode,
			displayName: post.authorName,
		},
		body: post.body,
		imageKey: post.imageKey,
		commentCount: post.commentCount,
	};
}

feedRoutes.get("/v1/feed", deviceAuth, async (c) => {
	const filter = (c.req.query("filter") ?? "all").toLowerCase();
	const sinceMs = Number.parseInt(c.req.query("since") ?? "0", 10) || 0;
	// Prefer headers so precise coords are not written into access-log URL paths.
	const latRaw = c.req.header("X-Client-Lat") ?? c.req.query("lat");
	const lngRaw = c.req.header("X-Client-Lng") ?? c.req.query("lng");
	const lat = latRaw != null && latRaw !== "" ? Number.parseFloat(latRaw) : undefined;
	const lng = lngRaw != null && lngRaw !== "" ? Number.parseFloat(lngRaw) : undefined;
	const radiusKm =
		Number.parseFloat(c.req.query("radiusKm") ?? "") || DEFAULT_NEARBY_KM;

	const viewer = c.get("friendCode");
	const friendsList = await listFriends(c.env.DB, viewer);
	const friends = new Set(friendsList.map((f) => f.friendCode));

	const items: FeedItem[] = [];
	const hasViewerLocation = lat != null && lng != null && Number.isFinite(lat) && Number.isFinite(lng);

	if (filter === "posts_friends") {
		const posts = await listVisiblePosts(c.env.DB, viewer, sinceMs, 200, "friends");
		return c.json({ items: posts.map(postToFeedItem) });
	}

	if (filter === "posts_regional") {
		if (!hasViewerLocation) return c.json({ items: [] });
		const posts = await listVisiblePosts(c.env.DB, viewer, sinceMs, 200, "public");
		const nearby = posts.filter(
			(p) =>
				p.latitude != null &&
				p.longitude != null &&
				haversineKm(lat, lng, p.latitude, p.longitude) <= REGIONAL_KM,
		);
		return c.json({ items: nearby.map(postToFeedItem) });
	}

	// Legacy clients: "posts" and "all" mix public and friends' posts.
	const includePosts = filter === "all" || filter === "posts";
	if (includePosts) {
		const posts = await listVisiblePosts(c.env.DB, viewer, sinceMs);
		for (const post of posts) {
			items.push(postToFeedItem(post));
		}
	}

	if (filter === "posts") {
		items.sort((a, b) => b.createdAt - a.createdAt);
		return c.json({ items });
	}

	const fromMs = Math.min(
		Date.now() - 30 * 24 * 60 * 60 * 1000,
		sinceMs > 0 ? sinceMs : Date.now(),
	);
	const listFrom = Math.min(fromMs, Date.now() - 30 * 24 * 60 * 60 * 1000);
	const shoots = await listVisibleShoots(c.env.DB, viewer, listFrom, 0);

	for (const sh of shoots) {
		if (sh.latitude != null && sh.longitude != null && lat != null && lng != null) {
			sh.distanceKm = haversineKm(lat, lng, sh.latitude, sh.longitude);
		}
		const category = shootCategory(sh, viewer, friends);
		let include = false;
		switch (filter) {
			case "nearby":
				include = sh.distanceKm != null && sh.distanceKm <= radiusKm;
				break;
			case "friends":
				include = !sh.isCurated && !sh.isPublic;
				break;
			case "global":
				include =
					(sh.isCurated || sh.isPublic) &&
					sh.distanceKm != null &&
					sh.distanceKm <= REGIONAL_KM;
				break;
			case "all":
			default:
				include = true;
				break;
		}
		if (!include) continue;
		items.push({
			type: "shoot",
			id: `shoot-${sh.id}`,
			// Milliseconds so feed badges / relative time match posts.
			createdAt: sh.startsAt,
			category,
			shoot: sh,
		});
	}

	items.sort((a, b) => {
		const aKey = a.shoot?.startsAt ?? a.createdAt;
		const bKey = b.shoot?.startsAt ?? b.createdAt;
		return bKey - aKey;
	});
	return c.json({ items });
});
