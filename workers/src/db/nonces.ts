export async function consumeNonce(
	db: D1Database,
	friendCode: string,
	nonce: string,
	expiresAt: number,
): Promise<"ok" | "replay"> {
	try {
		await db
			.prepare(
				`INSERT INTO used_nonces (friend_code, nonce, expires_at) VALUES (?, ?, ?)`,
			)
			.bind(friendCode, nonce, expiresAt)
			.run();
		return "ok";
	} catch (e) {
		const msg = String(e);
		if (msg.includes("UNIQUE") || msg.includes("constraint")) return "replay";
		throw e;
	}
}

export async function purgeExpiredNonces(db: D1Database, now: number): Promise<void> {
	await db.prepare(`DELETE FROM used_nonces WHERE expires_at < ?`).bind(now).run();
}
