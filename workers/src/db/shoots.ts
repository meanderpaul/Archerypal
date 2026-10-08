import { listFriends } from "./friends";
import { nowSec } from "../util/http";

export type Shoot = {
	id: string;
	title: string;
	description?: string;
	locationName: string;
	region?: string;
	startsAt: number;
	endsAt?: number;
	hostFriendCode?: string;
	hostDisplayName?: string;
	isCurated: boolean;
	isPublic: boolean;
	sourceUrl?: string;
	registrationUrl?: string;
	eventType?: string;
	status: string;
	friendsGoingCount: number;
	viewerIsGoing: boolean;
	latitude?: number;
	longitude?: number;
	distanceKm?: number;
};

export type ShootAttendee = {
	friendCode: string;
	displayName: string;
	isFriend: boolean;
};

export type CreateShootInput = {
	title: string;
	description?: string;
	locationName: string;
	region?: string;
	startsAt: number;
	endsAt?: number;
	hostCode: string;
	isPublic?: boolean;
	sourceUrl?: string;
	registrationUrl?: string;
	eventType?: string;
	latitude?: number;
	longitude?: number;
};

export type CuratedShootInput = {
	id: string;
	title: string;
	description?: string;
	locationName: string;
	region?: string;
	startsAt: number;
	endsAt?: number;
	sourceUrl?: string;
	registrationUrl?: string;
	eventType?: string;
	latitude?: number;
	longitude?: number;
};

type ShootRow = {
	id: string;
	title: string;
	description: string | null;
	location_name: string;
	region: string | null;
	starts_at: number;
	ends_at: number | null;
	host_friend_code: string | null;
	host_display_name: string | null;
	is_curated: number;
	is_public: number;
	status: string;
	source_url: string | null;
	registration_url: string | null;
	event_type: string | null;
	latitude: number | null;
	longitude: number | null;
};

function emptyToNull(s: string | undefined): string | null {
	const t = (s ?? "").trim();
	return t === "" ? null : t;
}

function mapRow(row: ShootRow): Shoot {
	const sh: Shoot = {
		id: row.id,
		title: row.title,
		locationName: row.location_name,
		startsAt: row.starts_at,
		isCurated: row.is_curated === 1,
		isPublic: row.is_public === 1,
		status: row.status,
		friendsGoingCount: 0,
		viewerIsGoing: false,
	};
	if (row.description) sh.description = row.description;
	if (row.region) sh.region = row.region;
	if (row.ends_at != null) sh.endsAt = row.ends_at;
	if (row.host_friend_code) {
		sh.hostFriendCode = row.host_friend_code;
		if (row.host_display_name) sh.hostDisplayName = row.host_display_name;
	}
	if (row.source_url) sh.sourceUrl = row.source_url;
	if (row.registration_url) sh.registrationUrl = row.registration_url;
	if (row.event_type) sh.eventType = row.event_type;
	if (row.latitude != null) sh.latitude = row.latitude;
	if (row.longitude != null) sh.longitude = row.longitude;
	return sh;
}

const SELECT_COLS = `
  s.id, s.title, s.description, s.location_name, s.region,
  s.starts_at, s.ends_at, s.host_friend_code, s.is_curated, s.is_public, s.status,
  p.display_name AS host_display_name,
  s.source_url, s.registration_url, s.event_type,
  s.latitude, s.longitude`;

function newShootId(): string {
	const bytes = crypto.getRandomValues(new Uint8Array(16));
	return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function friendSet(db: D1Database, viewer: string): Promise<Set<string>> {
	const friends = await listFriends(db, viewer);
	return new Set(friends.map((f) => f.friendCode));
}

async function listAttendeeCodes(db: D1Database, shootId: string): Promise<string[]> {
	const { results } = await db
		.prepare(`SELECT friend_code FROM shoot_rsvps WHERE shoot_id = ?`)
		.bind(shootId)
		.all<{ friend_code: string }>();
	return (results ?? []).map((r) => r.friend_code);
}

async function shootFriendStats(
	db: D1Database,
	shootId: string,
	viewer: string,
	friends: Set<string>,
): Promise<{ count: number; going: boolean }> {
	const codes = await listAttendeeCodes(db, shootId);
	let count = 0;
	let going = false;
	for (const code of codes) {
		if (code === viewer) going = true;
		if (friends.has(code)) count++;
	}
	return { count, going };
}

async function isVisibleTo(
	db: D1Database,
	sh: Shoot,
	viewer: string,
	friends: Set<string>,
): Promise<boolean> {
	if (sh.isCurated || sh.isPublic) return true;
	if (sh.hostFriendCode) {
		if (sh.hostFriendCode === viewer) return true;
		if (friends.has(sh.hostFriendCode)) return true;
	}
	const attendees = await listAttendeeCodes(db, sh.id);
	return attendees.some((c) => friends.has(c));
}

export async function createShoot(db: D1Database, in_: CreateShootInput): Promise<Shoot | null> {
	const id = newShootId();
	const created = nowSec();
	await db
		.prepare(
			`INSERT INTO shoots (
         id, title, description, location_name, region, starts_at, ends_at,
         host_friend_code, is_curated, is_public, status, created_at,
         source_url, registration_url, event_type, latitude, longitude
       ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'scheduled', ?, ?, ?, ?, ?, ?)`,
		)
		.bind(
			id,
			in_.title,
			emptyToNull(in_.description),
			in_.locationName,
			emptyToNull(in_.region),
			in_.startsAt,
			in_.endsAt ?? null,
			in_.hostCode,
			in_.isPublic ? 1 : 0,
			created,
			emptyToNull(in_.sourceUrl),
			emptyToNull(in_.registrationUrl),
			emptyToNull(in_.eventType),
			in_.latitude ?? null,
			in_.longitude ?? null,
		)
		.run();
	return getShootForViewer(db, id, in_.hostCode);
}

export async function upsertCuratedShoot(db: D1Database, in_: CuratedShootInput): Promise<void> {
	const created = nowSec();
	await db
		.prepare(
			`INSERT INTO shoots (
         id, title, description, location_name, region, starts_at, ends_at,
         host_friend_code, is_curated, is_public, status, created_at,
         source_url, registration_url, event_type, latitude, longitude
       ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 1, 0, 'scheduled', ?, ?, ?, ?, ?, ?)
       ON CONFLICT(id) DO UPDATE SET
         title = excluded.title,
         description = excluded.description,
         location_name = excluded.location_name,
         region = excluded.region,
         starts_at = excluded.starts_at,
         ends_at = excluded.ends_at,
         status = 'scheduled',
         source_url = excluded.source_url,
         registration_url = excluded.registration_url,
         event_type = excluded.event_type,
         latitude = excluded.latitude,
         longitude = excluded.longitude`,
		)
		.bind(
			in_.id,
			in_.title,
			emptyToNull(in_.description),
			in_.locationName,
			emptyToNull(in_.region),
			in_.startsAt,
			in_.endsAt ?? null,
			created,
			emptyToNull(in_.sourceUrl),
			emptyToNull(in_.registrationUrl),
			emptyToNull(in_.eventType),
			in_.latitude ?? null,
			in_.longitude ?? null,
		)
		.run();
}

export async function cancelCuratedNotIn(db: D1Database, keepIds: string[]): Promise<number> {
	if (keepIds.length === 0) {
		const res = await db
			.prepare(
				`UPDATE shoots SET status = 'cancelled'
         WHERE is_curated = 1 AND status = 'scheduled'`,
			)
			.run();
		return res.meta.changes ?? 0;
	}
	const placeholders = keepIds.map(() => "?").join(",");
	const res = await db
		.prepare(
			`UPDATE shoots SET status = 'cancelled'
       WHERE is_curated = 1 AND status = 'scheduled' AND id NOT IN (${placeholders})`,
		)
		.bind(...keepIds)
		.run();
	return res.meta.changes ?? 0;
}

export async function listVisibleShoots(
	db: D1Database,
	viewer: string,
	fromMs: number,
	toMs: number,
): Promise<Shoot[]> {
	const friends = await friendSet(db, viewer);
	// Include upcoming starts and still-running multi-day / season events.
	const { results } = await db
		.prepare(
			`SELECT ${SELECT_COLS}
       FROM shoots s
       LEFT JOIN players p ON p.friend_code = s.host_friend_code
       WHERE s.status = 'scheduled'
         AND (
           s.starts_at >= ?
           OR (s.ends_at IS NOT NULL AND s.ends_at >= ?)
         )
         AND (? = 0 OR s.starts_at <= ?)
       ORDER BY s.starts_at ASC`,
		)
		.bind(fromMs, fromMs, toMs, toMs)
		.all<ShootRow>();

	const visible: Shoot[] = [];
	for (const row of results ?? []) {
		const sh = mapRow(row);
		if (!(await isVisibleTo(db, sh, viewer, friends))) continue;
		const stats = await shootFriendStats(db, sh.id, viewer, friends);
		sh.friendsGoingCount = stats.count;
		sh.viewerIsGoing = stats.going;
		visible.push(sh);
	}
	return visible;
}

export async function getShootForViewer(
	db: D1Database,
	shootId: string,
	viewer: string,
): Promise<Shoot | null> {
	const row = await db
		.prepare(
			`SELECT ${SELECT_COLS}
       FROM shoots s
       LEFT JOIN players p ON p.friend_code = s.host_friend_code
       WHERE s.id = ?`,
		)
		.bind(shootId)
		.first<ShootRow>();
	if (!row) return null;
	const sh = mapRow(row);
	const friends = await friendSet(db, viewer);
	if (!(await isVisibleTo(db, sh, viewer, friends))) return null;
	const stats = await shootFriendStats(db, sh.id, viewer, friends);
	sh.friendsGoingCount = stats.count;
	sh.viewerIsGoing = stats.going;
	return sh;
}

export async function setShootRsvp(
	db: D1Database,
	shootId: string,
	friendCode: string,
): Promise<"ok" | "not_found"> {
	const sh = await getShootForViewer(db, shootId, friendCode);
	if (!sh) return "not_found";
	await db
		.prepare(
			`INSERT INTO shoot_rsvps (shoot_id, friend_code, created_at)
       VALUES (?, ?, ?)
       ON CONFLICT(shoot_id, friend_code) DO UPDATE SET created_at = excluded.created_at`,
		)
		.bind(shootId, friendCode, nowSec())
		.run();
	return "ok";
}

export async function clearShootRsvp(
	db: D1Database,
	shootId: string,
	friendCode: string,
): Promise<"ok" | "not_found"> {
	const res = await db
		.prepare(`DELETE FROM shoot_rsvps WHERE shoot_id = ? AND friend_code = ?`)
		.bind(shootId, friendCode)
		.run();
	if ((res.meta.changes ?? 0) === 0) return "not_found";
	return "ok";
}

export async function listShootAttendees(
	db: D1Database,
	shootId: string,
	viewer: string,
): Promise<ShootAttendee[] | null> {
	const sh = await getShootForViewer(db, shootId, viewer);
	if (!sh) return null;
	const friends = await friendSet(db, viewer);
	const { results } = await db
		.prepare(
			`SELECT r.friend_code AS friendCode, p.display_name AS displayName
       FROM shoot_rsvps r
       JOIN players p ON p.friend_code = r.friend_code
       WHERE r.shoot_id = ?
       ORDER BY p.display_name COLLATE NOCASE`,
		)
		.bind(shootId)
		.all<{ friendCode: string; displayName: string }>();
	return (results ?? []).map((r) => ({
		friendCode: r.friendCode,
		displayName: r.displayName,
		isFriend: friends.has(r.friendCode) || r.friendCode === viewer,
	}));
}
