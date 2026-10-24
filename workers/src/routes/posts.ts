import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { requirePlus } from "../middleware/plus";
import {
	createComment,
	createPost,
	deleteComment,
	getVisiblePost,
	listComments,
} from "../db/posts";
import { listFriendPushTokens } from "../db/friends";
import { getPlayer } from "../db/players";
import { sendPush } from "../push/fcm";
import { textError } from "../util/http";
import { allowRate } from "../util/rateLimit";
import {
	commentBodyOk,
	latitudeOk,
	longitudeOk,
	postBodyOk,
	sanitizePostBody,
} from "../util/validate";

export const postRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const COMMENTS_PER_HOUR = 60;
const HOUR_MS = 60 * 60 * 1000;
const POST_COMMENT_CHANNEL = "post_comments";
const FEED_POST_CHANNEL = "feed_posts";

function optionalNumber(raw: unknown): number | undefined {
	if (raw == null || raw === "") return undefined;
	const n = typeof raw === "number" ? raw : Number.parseFloat(String(raw));
	return Number.isFinite(n) ? n : Number.NaN;
}

const MAX_POST_BYTES = 6 << 20; // ~6 MB multipart ceiling (text + optional ignored image)

function photoPostsEnabled(env: Env): boolean {
	return env.PHOTO_POSTS_ENABLED === "true" || env.PHOTO_POSTS_ENABLED === "1";
}

/**
 * Text-only posts. Multipart matches the Android client (body, isPublic, optional image).
 * Images are rejected until R2 media lands — body-only posts work now.
 */
postRoutes.post("/v1/posts", deviceAuth, async (c) => {
	const denied = await requirePlus(c);
	if (denied) return denied;

	const rawBody = c.get("rawBody");
	if (rawBody.byteLength > MAX_POST_BYTES) {
		return textError(c, 400, "request too large");
	}

	const contentType = c.req.header("content-type") ?? "";
	let bodyText = "";
	let isPublic = false;
	let hasImage = false;
	let latitude: number | undefined;
	let longitude: number | undefined;

	if (contentType.toLowerCase().includes("multipart/form-data")) {
		const reconstructed = new Request(c.req.url, {
			method: "POST",
			headers: { "content-type": contentType },
			body: rawBody,
		});
		let form: FormData;
		try {
			form = await reconstructed.formData();
		} catch {
			return textError(c, 400, "invalid multipart");
		}
		bodyText = String(form.get("body") ?? "");
		const publicRaw = String(form.get("isPublic") ?? "");
		isPublic = publicRaw.toLowerCase() === "true" || publicRaw === "1";
		latitude = optionalNumber(form.get("latitude"));
		longitude = optionalNumber(form.get("longitude"));
		const image = form.get("image");
		hasImage = image instanceof File && image.size > 0;
	} else {
		// Allow JSON for tooling / future clients.
		try {
			const json = JSON.parse(new TextDecoder().decode(rawBody)) as {
				body?: string;
				isPublic?: boolean;
				latitude?: number | null;
				longitude?: number | null;
			};
			bodyText = json.body ?? "";
			isPublic = Boolean(json.isPublic);
			latitude = optionalNumber(json.latitude);
			longitude = optionalNumber(json.longitude);
		} catch {
			return textError(c, 400, "invalid body");
		}
	}

	if (hasImage) {
		// Enabling the flag is not enough on its own: R2 storage still has to be wired up.
		return textError(
			c,
			501,
			photoPostsEnabled(c.env)
				? "photo storage not configured"
				: "photo posts not available yet — post text only",
		);
	}

	bodyText = sanitizePostBody(bodyText).trim();
	if (!postBodyOk(bodyText)) {
		return textError(c, 400, "body required");
	}
	if (!latitudeOk(latitude) || !longitudeOk(longitude)) {
		return textError(c, 400, "invalid coordinates");
	}

	const post = await createPost(c.env.DB, {
		authorCode: c.get("friendCode"),
		body: bodyText,
		isPublic,
		latitude,
		longitude,
	});
	if (!post) return textError(c, 500, "create post failed");

	c.executionCtx.waitUntil(
		listFriendPushTokens(c.env.DB, post.authorCode)
			.then((tokens) =>
				Promise.allSettled(
					tokens.map((token) =>
						sendPush(c.env, token, {
							title: `${post.authorName} posted`,
							body: post.body.length > 120 ? `${post.body.slice(0, 117)}...` : post.body,
							channelId: FEED_POST_CHANNEL,
							data: { type: "feed_post", postId: post.id },
						}),
					),
				),
			)
			.then((results) => {
				for (const r of results) {
					if (r.status === "rejected") console.error("feed post push failed", r.reason);
				}
			})
			.catch((err) => console.error("feed post push failed", err)),
	);
	return c.json({ post });
});

postRoutes.get("/v1/posts/:id/comments", deviceAuth, async (c) => {
	const post = await getVisiblePost(c.env.DB, c.req.param("id"), c.get("friendCode"));
	if (!post) return textError(c, 404, "not found");
	const comments = await listComments(c.env.DB, post.id);
	return c.json({ comments });
});

/** Reading comments is free; writing them is Plus, like posting. */
postRoutes.post("/v1/posts/:id/comments", deviceAuth, async (c) => {
	const denied = await requirePlus(c);
	if (denied) return denied;
	const self = c.get("friendCode");
	const post = await getVisiblePost(c.env.DB, c.req.param("id"), self);
	if (!post) return textError(c, 404, "not found");

	let json: { body?: string };
	try {
		json = JSON.parse(new TextDecoder().decode(c.get("rawBody"))) as typeof json;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const body = sanitizePostBody(json.body ?? "").trim();
	if (!commentBodyOk(body)) return textError(c, 400, "comment must be 1-500 characters");
	if (!allowRate(`post-comment:${self}`, COMMENTS_PER_HOUR, HOUR_MS)) {
		return textError(c, 429, "too many comments — try again later");
	}

	const comment = await createComment(c.env.DB, post.id, self, body);
	if (!comment) return textError(c, 500, "create comment failed");

	if (post.authorCode !== self) {
		c.executionCtx.waitUntil(
			getPlayer(c.env.DB, post.authorCode)
				.then((author) =>
					sendPush(c.env, author?.fcmToken, {
						title: `${comment.authorName} commented on your post`,
						body: body.length > 120 ? `${body.slice(0, 117)}...` : body,
						channelId: POST_COMMENT_CHANNEL,
						data: { type: "post_comment", postId: post.id },
					}),
				)
				.catch((err) => console.error("comment push failed", err)),
		);
	}
	return c.json({ comment });
});

postRoutes.delete("/v1/posts/:id/comments/:commentId", deviceAuth, async (c) => {
	const result = await deleteComment(
		c.env.DB,
		c.req.param("id"),
		c.req.param("commentId"),
		c.get("friendCode"),
	);
	if (result === "not_found") return textError(c, 404, "not found");
	if (result === "forbidden") return textError(c, 403, "forbidden");
	return c.json({ ok: true });
});
