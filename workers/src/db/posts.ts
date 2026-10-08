export type Post = {
	id: string;
	authorCode: string;
	authorName: string;
	body: string;
	imageKey: string;
	isPublic: boolean;
	createdAt: number;
	latitude?: number;
	longitude?: number;
};

export type CreatePostInput = {
	authorCode: string;
	body: string;
	isPublic: boolean;
	imageKey?: string;
	latitude?: number;
	longitude?: number;
};

/**
 * all: public posts plus own/friends' posts (legacy "posts" filter).
 * friends: own and friends' posts only, any visibility.
 * public: public posts only (caller narrows to the viewer's region).
 */
export type PostScope = "all" | "friends" | "public";

type PostRow = {
	id: string;
	author_code: string;
	display_name: string;
	body: string;
	image_key: string | null;
	is_public: number;
	created_at: number;
	latitude: number | null;
	longitude: number | null;
};

const POST_COLS = `p.id, p.author_code, pl.display_name, p.body,
        p.image_key, p.is_public, p.created_at, p.latitude, p.longitude`;

const FRIEND_OR_SELF = `(
           p.author_code = ?
           OR EXISTS (
             SELECT 1 FROM friendships f
             WHERE (f.player_a = ? AND f.player_b = p.author_code)
                OR (f.player_b = ? AND f.player_a = p.author_code)
           )
         )`;

function newPostId(): string {
	const bytes = crypto.getRandomValues(new Uint8Array(16));
	return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function rowToPost(row: PostRow): Post {
	const post: Post = {
		id: row.id,
		authorCode: row.author_code,
		authorName: row.display_name,
		body: row.body,
		imageKey: row.image_key ?? "",
		isPublic: row.is_public === 1,
		createdAt: row.created_at,
	};
	if (row.latitude != null) post.latitude = row.latitude;
	if (row.longitude != null) post.longitude = row.longitude;
	return post;
}

/** One decimal (~7 miles): enough for a regional radius without pinpointing the author. */
function coarseDegrees(deg: number | undefined): number | null {
	if (deg == null || !Number.isFinite(deg)) return null;
	return Math.round(deg * 10) / 10;
}

export async function createPost(db: D1Database, in_: CreatePostInput): Promise<Post | null> {
	const id = newPostId();
	const createdAt = Date.now();
	// Only public posts surface regionally; private posts never keep a location.
	const located = in_.isPublic && in_.latitude != null && in_.longitude != null;
	await db
		.prepare(
			`INSERT INTO posts (id, author_code, body, image_key, is_public, created_at, latitude, longitude)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		)
		.bind(
			id,
			in_.authorCode,
			in_.body,
			in_.imageKey?.trim() ? in_.imageKey.trim() : null,
			in_.isPublic ? 1 : 0,
			createdAt,
			located ? coarseDegrees(in_.latitude) : null,
			located ? coarseDegrees(in_.longitude) : null,
		)
		.run();
	return getPost(db, id);
}

export async function getPost(db: D1Database, id: string): Promise<Post | null> {
	const row = await db
		.prepare(
			`SELECT ${POST_COLS}
       FROM posts p
       JOIN players pl ON pl.friend_code = p.author_code
       WHERE p.id = ?`,
		)
		.bind(id)
		.first<PostRow>();
	return row ? rowToPost(row) : null;
}

/**
 * Visible posts: public to all Plus viewers; private to author + friends.
 * created_at is Unix milliseconds (matches Android feed relative time).
 */
export async function listVisiblePosts(
	db: D1Database,
	viewerCode: string,
	sinceMs = 0,
	limit = 200,
	scope: PostScope = "all",
): Promise<Post[]> {
	let visibility: string;
	let binds: unknown[];
	switch (scope) {
		case "friends":
			visibility = FRIEND_OR_SELF;
			binds = [viewerCode, viewerCode, viewerCode];
			break;
		case "public":
			visibility = "p.is_public = 1";
			binds = [];
			break;
		default:
			visibility = `(p.is_public = 1 OR ${FRIEND_OR_SELF})`;
			binds = [viewerCode, viewerCode, viewerCode];
	}
	const rows = await db
		.prepare(
			`SELECT ${POST_COLS}
       FROM posts p
       JOIN players pl ON pl.friend_code = p.author_code
       WHERE (? = 0 OR p.created_at >= ?)
         AND ${visibility}
       ORDER BY p.created_at DESC
       LIMIT ?`,
		)
		.bind(sinceMs, sinceMs, ...binds, limit)
		.all<PostRow>();
	return (rows.results ?? []).map(rowToPost);
}
