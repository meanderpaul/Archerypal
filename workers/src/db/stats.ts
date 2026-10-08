export type LeaderboardEntry = {
	friendCode: string;
	displayName: string;
	matchesPlayed: number;
	matchesWon: number;
	totalPoints: number;
	updatedAt?: number;
};

export async function globalLeaderboard(
	db: D1Database,
	limit: number,
): Promise<LeaderboardEntry[]> {
	const { results } = await db
		.prepare(
			`SELECT friend_code AS friendCode, display_name AS displayName,
              matches_played AS matchesPlayed, matches_won AS matchesWon,
              total_points AS totalPoints, updated_at AS updatedAt
       FROM player_stats
       WHERE matches_played > 0
       ORDER BY matches_won DESC, total_points DESC, updated_at DESC
       LIMIT ?`,
		)
		.bind(limit)
		.all<LeaderboardEntry>();
	return results ?? [];
}

export async function friendsLeaderboard(
	db: D1Database,
	self: string,
): Promise<LeaderboardEntry[]> {
	const { results } = await db
		.prepare(
			`SELECT ps.friend_code AS friendCode, ps.display_name AS displayName,
              ps.matches_played AS matchesPlayed, ps.matches_won AS matchesWon,
              ps.total_points AS totalPoints, ps.updated_at AS updatedAt
       FROM player_stats ps
       WHERE ps.friend_code = ?
          OR ps.friend_code IN (
            SELECT CASE WHEN player_a = ? THEN player_b ELSE player_a END
            FROM friendships WHERE player_a = ? OR player_b = ?
          )
       ORDER BY matches_won DESC, total_points DESC`,
		)
		.bind(self, self, self, self)
		.all<LeaderboardEntry>();
	return results ?? [];
}

export async function leaderboardEtag(entries: LeaderboardEntry[]): Promise<string> {
	const payload = entries
		.map(
			(e) =>
				`${e.friendCode}|${e.displayName}|${e.matchesPlayed}|${e.matchesWon}|${e.totalPoints}`,
		)
		.join("\n");
	const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(payload));
	const hex = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
	return `"${hex}"`;
}
