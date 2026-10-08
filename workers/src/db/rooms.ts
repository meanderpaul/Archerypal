export type RoomRow = {
	id: string;
	hostFriendCode: string;
	joinerFriendCode: string | null;
	status: string;
	createdAt: number;
};

export async function createRoom(
	db: D1Database,
	id: string,
	host: string,
	joiner: string,
	now: number,
	expiresAt: number,
): Promise<void> {
	await db
		.prepare(
			`INSERT INTO rooms (id, host_friend_code, joiner_friend_code, status, created_at, expires_at)
       VALUES (?, ?, ?, 'open', ?, ?)`,
		)
		.bind(id, host, joiner, now, expiresAt)
		.run();
}

export async function getRoom(db: D1Database, id: string): Promise<RoomRow | null> {
	const row = await db
		.prepare(
			`SELECT id, host_friend_code, joiner_friend_code, status, created_at FROM rooms WHERE id = ?`,
		)
		.bind(id)
		.first<{
			id: string;
			host_friend_code: string;
			joiner_friend_code: string | null;
			status: string;
			created_at: number;
		}>();
	if (!row) return null;
	return {
		id: row.id,
		hostFriendCode: row.host_friend_code,
		joinerFriendCode: row.joiner_friend_code,
		status: row.status,
		createdAt: row.created_at,
	};
}

export async function closeRoom(db: D1Database, id: string): Promise<void> {
	await db.prepare(`UPDATE rooms SET status = 'closed' WHERE id = ?`).bind(id).run();
}

export type CompletePlayer = {
	friendCode: string;
	displayName: string;
	total: number;
};

export async function insertCompletedMatch(
	db: D1Database,
	matchId: string,
	completedAt: number,
	scoringType: string,
	targetCount: number,
	winnerFriendCode: string,
	scoresJson: string,
	roomId: string,
): Promise<"ok" | "conflict"> {
	try {
		await db
			.prepare(
				`INSERT INTO completed_matches
         (match_id, completed_at, scoring_type, target_count, winner_friend_code, scores_json, room_id)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
			)
			.bind(matchId, completedAt, scoringType, targetCount, winnerFriendCode, scoresJson, roomId)
			.run();
		return "ok";
	} catch (e) {
		const msg = String(e);
		if (msg.includes("UNIQUE") || msg.includes("constraint")) return "conflict";
		throw e;
	}
}

export async function applyMatchStats(
	db: D1Database,
	players: CompletePlayer[],
	winnerFriendCode: string,
	now: number,
): Promise<void> {
	for (const p of players) {
		const won = p.friendCode === winnerFriendCode ? 1 : 0;
		await db
			.prepare(
				`INSERT INTO player_stats (friend_code, display_name, matches_played, matches_won, total_points, updated_at)
         VALUES (?, ?, 1, ?, ?, ?)
         ON CONFLICT(friend_code) DO UPDATE SET
           display_name = excluded.display_name,
           matches_played = player_stats.matches_played + 1,
           matches_won = player_stats.matches_won + excluded.matches_won,
           total_points = player_stats.total_points + excluded.total_points,
           updated_at = excluded.updated_at`,
			)
			.bind(p.friendCode, p.displayName, won, p.total, now)
			.run();
	}
}
