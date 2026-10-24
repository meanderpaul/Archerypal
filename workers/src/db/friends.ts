export type Friend = { friendCode: string; displayName: string };

export type FriendRequest = {
	fromCode: string;
	fromDisplayName: string;
	toCode: string;
	status: string;
	createdAt: number;
};

export type OutgoingFriendRequest = FriendRequest & { toDisplayName: string };

export type FriendSuggestion = {
	friendCode: string;
	displayName: string;
	mutualCount: number;
};

function pair(a: string, b: string): [string, string] {
	return a < b ? [a, b] : [b, a];
}

export async function listFriends(db: D1Database, self: string): Promise<Friend[]> {
	const { results } = await db
		.prepare(
			`SELECT p.friend_code AS friendCode, p.display_name AS displayName
       FROM friendships f
       JOIN players p ON p.friend_code = CASE WHEN f.player_a = ? THEN f.player_b ELSE f.player_a END
       WHERE f.player_a = ? OR f.player_b = ?
       ORDER BY p.display_name COLLATE NOCASE`,
		)
		.bind(self, self, self)
		.all<{ friendCode: string; displayName: string }>();
	return results ?? [];
}

export async function listFriendPushTokens(db: D1Database, self: string): Promise<string[]> {
	const { results } = await db
		.prepare(
			`SELECT p.fcm_token AS token
       FROM friendships f
       JOIN players p ON p.friend_code = CASE WHEN f.player_a = ? THEN f.player_b ELSE f.player_a END
       WHERE (f.player_a = ? OR f.player_b = ?) AND p.fcm_token IS NOT NULL AND p.fcm_token != ''`,
		)
		.bind(self, self, self)
		.all<{ token: string }>();
	return (results ?? []).map((r) => r.token);
}

export async function addFriendship(
	db: D1Database,
	a: string,
	b: string,
	now: number,
): Promise<void> {
	const [pa, pb] = pair(a, b);
	await db
		.prepare(
			`INSERT OR IGNORE INTO friendships (player_a, player_b, created_at) VALUES (?, ?, ?)`,
		)
		.bind(pa, pb, now)
		.run();
}

export async function areFriends(db: D1Database, a: string, b: string): Promise<boolean> {
	if (a === b) return true;
	const [pa, pb] = pair(a, b);
	const row = await db
		.prepare(`SELECT 1 AS ok FROM friendships WHERE player_a = ? AND player_b = ?`)
		.bind(pa, pb)
		.first<{ ok: number }>();
	return Boolean(row);
}

export async function createFriendRequest(
	db: D1Database,
	from: string,
	to: string,
	now: number,
): Promise<"ok" | "conflict"> {
	try {
		await db
			.prepare(
				`INSERT INTO friend_requests (from_code, to_code, status, created_at, updated_at)
         VALUES (?, ?, 'pending', ?, ?)`,
			)
			.bind(from, to, now, now)
			.run();
		return "ok";
	} catch (e) {
		const msg = String(e);
		if (msg.includes("UNIQUE") || msg.includes("constraint")) return "conflict";
		throw e;
	}
}

export async function listIncomingRequests(db: D1Database, self: string): Promise<FriendRequest[]> {
	const { results } = await db
		.prepare(
			`SELECT fr.from_code AS fromCode, p.display_name AS fromDisplayName,
              fr.to_code AS toCode, fr.status AS status, fr.created_at AS createdAt
       FROM friend_requests fr
       JOIN players p ON p.friend_code = fr.from_code
       WHERE fr.to_code = ? AND fr.status = 'pending'
       ORDER BY fr.created_at DESC`,
		)
		.bind(self)
		.all<FriendRequest>();
	return results ?? [];
}

export async function listOutgoingRequests(
	db: D1Database,
	self: string,
): Promise<OutgoingFriendRequest[]> {
	const { results } = await db
		.prepare(
			`SELECT fr.from_code AS fromCode, me.display_name AS fromDisplayName,
              fr.to_code AS toCode, p.display_name AS toDisplayName,
              fr.status AS status, fr.created_at AS createdAt
       FROM friend_requests fr
       JOIN players p ON p.friend_code = fr.to_code
       JOIN players me ON me.friend_code = fr.from_code
       WHERE fr.from_code = ? AND fr.status = 'pending'
       ORDER BY fr.created_at DESC`,
		)
		.bind(self)
		.all<OutgoingFriendRequest>();
	return results ?? [];
}

/** Friends of friends, excluding existing friends, pending requests either way, and opted-out players. */
export async function listFriendSuggestions(
	db: D1Database,
	self: string,
	limit = 15,
): Promise<FriendSuggestion[]> {
	const { results } = await db
		.prepare(
			`WITH my_friends AS (
         SELECT CASE WHEN player_a = ?1 THEN player_b ELSE player_a END AS code
         FROM friendships WHERE player_a = ?1 OR player_b = ?1
       ),
       fof AS (
         SELECT CASE WHEN f.player_a = mf.code THEN f.player_b ELSE f.player_a END AS code
         FROM friendships f
         JOIN my_friends mf ON f.player_a = mf.code OR f.player_b = mf.code
       )
       SELECT p.friend_code AS friendCode, p.display_name AS displayName,
              COUNT(*) AS mutualCount
       FROM fof
       JOIN players p ON p.friend_code = fof.code
       WHERE fof.code != ?1
         AND p.discoverable = 1
         AND fof.code NOT IN (SELECT code FROM my_friends)
         AND NOT EXISTS (
           SELECT 1 FROM friend_requests fr
           WHERE fr.status = 'pending'
             AND ((fr.from_code = ?1 AND fr.to_code = fof.code)
               OR (fr.from_code = fof.code AND fr.to_code = ?1))
         )
       GROUP BY p.friend_code, p.display_name
       ORDER BY mutualCount DESC, p.display_name COLLATE NOCASE
       LIMIT ?2`,
		)
		.bind(self, limit)
		.all<FriendSuggestion>();
	return results ?? [];
}

export async function updateFriendRequestStatus(
	db: D1Database,
	from: string,
	to: string,
	status: "accepted" | "declined",
	now: number,
): Promise<boolean> {
	const res = await db
		.prepare(
			`UPDATE friend_requests SET status = ?, updated_at = ?
       WHERE from_code = ? AND to_code = ? AND status = 'pending'`,
		)
		.bind(status, now, from, to)
		.run();
	return (res.meta.changes ?? 0) > 0;
}
