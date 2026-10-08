export type GoogleLink = {
	googleSub: string;
	friendCode: string;
};

export async function getLinkBySub(db: D1Database, sub: string): Promise<GoogleLink | null> {
	const row = await db
		.prepare(`SELECT google_sub, friend_code FROM google_account_links WHERE google_sub = ?`)
		.bind(sub)
		.first<{ google_sub: string; friend_code: string }>();
	if (!row) return null;
	return { googleSub: row.google_sub, friendCode: row.friend_code };
}

export async function getLinkByFriend(
	db: D1Database,
	friendCode: string,
): Promise<GoogleLink | null> {
	const row = await db
		.prepare(`SELECT google_sub, friend_code FROM google_account_links WHERE friend_code = ?`)
		.bind(friendCode)
		.first<{ google_sub: string; friend_code: string }>();
	if (!row) return null;
	return { googleSub: row.google_sub, friendCode: row.friend_code };
}

/** Link Google sub → friend code. Email is intentionally not stored. */
export async function linkGoogle(
	db: D1Database,
	sub: string,
	friendCode: string,
	now: number,
): Promise<"ok" | "conflict"> {
	try {
		await db
			.prepare(
				`INSERT INTO google_account_links (google_sub, friend_code, email, linked_at)
         VALUES (?, ?, NULL, ?)`,
			)
			.bind(sub, friendCode, now)
			.run();
		return "ok";
	} catch (e) {
		const msg = String(e);
		if (msg.includes("UNIQUE") || msg.includes("constraint")) return "conflict";
		throw e;
	}
}
