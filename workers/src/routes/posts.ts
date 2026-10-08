import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { requirePlus } from "../middleware/plus";
import { createPost } from "../db/posts";
import { textError } from "../util/http";
import { latitudeOk, longitudeOk, postBodyOk, sanitizePostBody } from "../util/validate";

export const postRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

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
	return c.json({ post });
});
