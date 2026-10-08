import { randomHex } from "../util/crypto";

export type Player = {
	friendCode: string;
	displayName: string;
	deviceSecretHash: ArrayBuffer;
	fcmToken: string | null;
	deviceId: string | null;
};

function mapPlayer(row: {
	friend_code: string;
	display_name: string;
	device_secret_hash: ArrayBuffer;
	fcm_token: string | null;
	device_id: string | null;
}): Player {
	return {
		friendCode: row.friend_code,
		displayName: row.display_name,
		deviceSecretHash: row.device_secret_hash,
		fcmToken: row.fcm_token,
		deviceId: row.device_id,
	};
}

export async function getPlayer(db: D1Database, friendCode: string): Promise<Player | null> {
	const row = await db
		.prepare(
			`SELECT friend_code, display_name, device_secret_hash, fcm_token, device_id
       FROM players WHERE friend_code = ?`,
		)
		.bind(friendCode)
		.first<{
			friend_code: string;
			display_name: string;
			device_secret_hash: ArrayBuffer;
			fcm_token: string | null;
			device_id: string | null;
		}>();
	if (!row) return null;
	return mapPlayer(row);
}

export async function getPlayerByDeviceId(db: D1Database, deviceId: string): Promise<Player | null> {
	const row = await db
		.prepare(
			`SELECT friend_code, display_name, device_secret_hash, fcm_token, device_id
       FROM players WHERE device_id = ?`,
		)
		.bind(deviceId)
		.first<{
			friend_code: string;
			display_name: string;
			device_secret_hash: ArrayBuffer;
			fcm_token: string | null;
			device_id: string | null;
		}>();
	if (!row) return null;
	return mapPlayer(row);
}

/** Ensure player has an opaque device_id; returns the id. */
export async function ensureDeviceId(db: D1Database, friendCode: string): Promise<string> {
	const existing = await getPlayer(db, friendCode);
	if (!existing) throw new Error("player missing");
	if (existing.deviceId) return existing.deviceId;

	for (let i = 0; i < 8; i++) {
		const deviceId = randomHex(16);
		try {
			await db
				.prepare(`UPDATE players SET device_id = ? WHERE friend_code = ? AND device_id IS NULL`)
				.bind(deviceId, friendCode)
				.run();
			const again = await getPlayer(db, friendCode);
			if (again?.deviceId) return again.deviceId;
		} catch (e) {
			const msg = String(e);
			if (msg.includes("UNIQUE") || msg.includes("constraint")) continue;
			throw e;
		}
	}
	throw new Error("could not allocate device id");
}

export async function insertPlayer(
	db: D1Database,
	friendCode: string,
	displayName: string,
	deviceSecretHash: Uint8Array,
	fcmToken: string | null,
	now: number,
	deviceId?: string,
): Promise<{ status: "ok"; deviceId: string } | { status: "conflict" }> {
	const id = deviceId ?? randomHex(16);
	try {
		await db.batch([
			db
				.prepare(
					`INSERT INTO players (friend_code, display_name, device_secret_hash, fcm_token, created_at, updated_at, device_id)
           VALUES (?, ?, ?, ?, ?, ?, ?)`,
				)
				.bind(friendCode, displayName, deviceSecretHash, fcmToken, now, now, id),
			db
				.prepare(
					`INSERT INTO player_stats (friend_code, display_name, matches_played, matches_won, total_points, updated_at)
           VALUES (?, ?, 0, 0, 0, ?)`,
				)
				.bind(friendCode, displayName, now),
		]);
		return { status: "ok", deviceId: id };
	} catch (e) {
		const msg = String(e);
		if (msg.includes("UNIQUE") || msg.includes("constraint")) return { status: "conflict" };
		throw e;
	}
}

export async function updatePlayer(
	db: D1Database,
	friendCode: string,
	displayName: string,
	fcmToken: string | null | undefined,
	now: number,
): Promise<void> {
	if (fcmToken === undefined) {
		await db
			.prepare(`UPDATE players SET display_name = ?, updated_at = ? WHERE friend_code = ?`)
			.bind(displayName, now, friendCode)
			.run();
	} else {
		await db
			.prepare(
				`UPDATE players SET display_name = ?, fcm_token = ?, updated_at = ? WHERE friend_code = ?`,
			)
			.bind(displayName, fcmToken, now, friendCode)
			.run();
	}
	await db
		.prepare(`UPDATE player_stats SET display_name = ?, updated_at = ? WHERE friend_code = ?`)
		.bind(displayName, now, friendCode)
		.run();
}

export async function setDiscoverable(
	db: D1Database,
	friendCode: string,
	discoverable: boolean,
	now: number,
): Promise<void> {
	await db
		.prepare(`UPDATE players SET discoverable = ?, updated_at = ? WHERE friend_code = ?`)
		.bind(discoverable ? 1 : 0, now, friendCode)
		.run();
}

export async function getDiscoverable(db: D1Database, friendCode: string): Promise<boolean> {
	const row = await db
		.prepare(`SELECT discoverable FROM players WHERE friend_code = ?`)
		.bind(friendCode)
		.first<{ discoverable: number }>();
	return row ? row.discoverable !== 0 : true;
}

export type PlayerSearchResult = {
	friendCode: string;
	displayName: string;
	relation: "friend" | "requested" | "incoming" | "none";
};

/** Case-insensitive display-name contains match over discoverable players; prefix matches first. */
export async function searchPlayers(
	db: D1Database,
	self: string,
	query: string,
	limit = 20,
): Promise<PlayerSearchResult[]> {
	const escaped = query.replace(/[\\%_]/g, (ch) => `\\${ch}`);
	const { results } = await db
		.prepare(
			`SELECT p.friend_code AS friendCode, p.display_name AS displayName,
              CASE
                WHEN f.player_a IS NOT NULL THEN 'friend'
                WHEN outgoing.from_code IS NOT NULL THEN 'requested'
                WHEN incoming.from_code IS NOT NULL THEN 'incoming'
                ELSE 'none'
              END AS relation
       FROM players p
       LEFT JOIN friendships f
         ON (f.player_a = ?1 AND f.player_b = p.friend_code)
         OR (f.player_b = ?1 AND f.player_a = p.friend_code)
       LEFT JOIN friend_requests outgoing
         ON outgoing.from_code = ?1 AND outgoing.to_code = p.friend_code AND outgoing.status = 'pending'
       LEFT JOIN friend_requests incoming
         ON incoming.from_code = p.friend_code AND incoming.to_code = ?1 AND incoming.status = 'pending'
       WHERE p.friend_code != ?1
         AND p.discoverable = 1
         AND p.display_name LIKE ?2 ESCAPE '\\'
       ORDER BY CASE WHEN p.display_name LIKE ?3 ESCAPE '\\' THEN 0 ELSE 1 END,
                p.display_name COLLATE NOCASE
       LIMIT ?4`,
		)
		.bind(self, `%${escaped}%`, `${escaped}%`, limit)
		.all<PlayerSearchResult>();
	return results ?? [];
}

export async function rotateDeviceSecret(
	db: D1Database,
	friendCode: string,
	deviceSecretHash: Uint8Array,
	now: number,
): Promise<void> {
	await db
		.prepare(
			`UPDATE players SET device_secret_hash = ?, updated_at = ? WHERE friend_code = ?`,
		)
		.bind(deviceSecretHash, now, friendCode)
		.run();
}

/** Drop push token on logout / credential rotation so stale devices stop receiving. */
export async function clearFcmToken(
	db: D1Database,
	friendCode: string,
	now: number,
): Promise<void> {
	await db
		.prepare(`UPDATE players SET fcm_token = NULL, updated_at = ? WHERE friend_code = ?`)
		.bind(now, friendCode)
		.run();
}
