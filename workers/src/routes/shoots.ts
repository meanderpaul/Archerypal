import { Hono } from "hono";
import { deviceAuth, type AppVariables } from "../middleware/deviceAuth";
import { requirePlus } from "../middleware/plus";
import {
	cancelCuratedNotIn,
	clearShootRsvp,
	createShoot,
	listShootAttendees,
	listVisibleShoots,
	setShootRsvp,
	upsertCuratedShoot,
} from "../db/shoots";
import { httpsUrlOk, latitudeOk, longitudeOk } from "../util/validate";
import { textError } from "../util/http";

export const shootRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

function parseMs(raw: string | undefined, fallback: number): number {
	if (!raw) return fallback;
	const n = Number.parseInt(raw, 10);
	return Number.isFinite(n) ? n : fallback;
}

type CreateBody = {
	title?: string;
	description?: string;
	locationName?: string;
	region?: string;
	startsAt?: number;
	endsAt?: number;
	isPublic?: boolean;
	sourceUrl?: string;
	registrationUrl?: string;
	eventType?: string;
	latitude?: number;
	longitude?: number;
};

type AdminBody = CreateBody & { id?: string };

type CancelBody = { keepIds?: string[] };

shootRoutes.get("/v1/shoots", deviceAuth, async (c) => {
	const fromMs = parseMs(c.req.query("from"), Date.now() - 24 * 60 * 60 * 1000);
	const toMs = parseMs(c.req.query("to"), 0);
	const shoots = await listVisibleShoots(c.env.DB, c.get("friendCode"), fromMs, toMs);
	return c.json({ shoots });
});

shootRoutes.post("/v1/shoots", deviceAuth, async (c) => {
	const denied = await requirePlus(c);
	if (denied) return denied;
	let body: CreateBody;
	try {
		body = JSON.parse(new TextDecoder().decode(c.get("rawBody"))) as CreateBody;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const title = (body.title ?? "").trim();
	const locationName = (body.locationName ?? "").trim();
	const startsAt = body.startsAt ?? 0;
	if (!title || !locationName || !startsAt) {
		return textError(c, 400, "title, locationName, and startsAt required");
	}
	if (!httpsUrlOk(body.sourceUrl) || !httpsUrlOk(body.registrationUrl)) {
		return textError(c, 400, "invalid url");
	}
	if (!latitudeOk(body.latitude) || !longitudeOk(body.longitude)) {
		return textError(c, 400, "invalid coordinates");
	}
	const shoot = await createShoot(c.env.DB, {
		title,
		description: (body.description ?? "").trim(),
		locationName,
		region: (body.region ?? "").trim(),
		startsAt,
		endsAt: body.endsAt && body.endsAt > 0 ? body.endsAt : undefined,
		hostCode: c.get("friendCode"),
		isPublic: !!body.isPublic,
		sourceUrl: (body.sourceUrl ?? "").trim(),
		registrationUrl: (body.registrationUrl ?? "").trim(),
		eventType: (body.eventType ?? "").trim(),
		latitude: body.latitude,
		longitude: body.longitude,
	});
	if (!shoot) return textError(c, 500, "create shoot failed");
	return c.json({ shoot });
});

shootRoutes.post("/v1/shoots/:id/rsvp", deviceAuth, async (c) => {
	const result = await setShootRsvp(c.env.DB, c.req.param("id"), c.get("friendCode"));
	if (result === "not_found") return textError(c, 404, "not found");
	return c.json({ ok: true });
});

shootRoutes.delete("/v1/shoots/:id/rsvp", deviceAuth, async (c) => {
	const result = await clearShootRsvp(c.env.DB, c.req.param("id"), c.get("friendCode"));
	if (result === "not_found") return textError(c, 404, "not found");
	return c.json({ ok: true });
});

shootRoutes.get("/v1/shoots/:id/attendees", deviceAuth, async (c) => {
	const attendees = await listShootAttendees(c.env.DB, c.req.param("id"), c.get("friendCode"));
	if (!attendees) return textError(c, 404, "not found");
	return c.json({ attendees });
});

function requireAdmin(c: {
	req: { header: (name: string) => string | undefined };
	env: Env;
}): Response | null {
	const key = (c.env.ADMIN_API_KEY ?? "").trim();
	if (!key) return new Response("admin not configured", { status: 503 });
	const provided = (c.req.header("X-Admin-Key") ?? "").trim();
	if (!provided || provided !== key) return new Response("unauthorized", { status: 401 });
	return null;
}

shootRoutes.post("/v1/admin/shoots", async (c) => {
	const denied = requireAdmin(c);
	if (denied) return denied;
	let body: AdminBody;
	try {
		body = (await c.req.json()) as AdminBody;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const id = (body.id ?? "").trim();
	const title = (body.title ?? "").trim();
	const locationName = (body.locationName ?? "").trim();
	const startsAt = body.startsAt ?? 0;
	if (!id || !title || !locationName || !startsAt) {
		return textError(c, 400, "id, title, locationName, and startsAt required");
	}
	await upsertCuratedShoot(c.env.DB, {
		id,
		title,
		description: (body.description ?? "").trim(),
		locationName,
		region: (body.region ?? "").trim(),
		startsAt,
		endsAt: body.endsAt && body.endsAt > 0 ? body.endsAt : undefined,
		sourceUrl: (body.sourceUrl ?? "").trim(),
		registrationUrl: (body.registrationUrl ?? "").trim(),
		eventType: (body.eventType ?? "").trim(),
		latitude: body.latitude,
		longitude: body.longitude,
	});
	return c.json({ ok: true });
});

shootRoutes.post("/v1/admin/shoots/cancel-stale", async (c) => {
	const denied = requireAdmin(c);
	if (denied) return denied;
	let body: CancelBody;
	try {
		body = (await c.req.json()) as CancelBody;
	} catch {
		return textError(c, 400, "invalid json");
	}
	const cancelled = await cancelCuratedNotIn(c.env.DB, body.keepIds ?? []);
	return c.json({ cancelled });
});
