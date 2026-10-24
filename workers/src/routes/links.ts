import { Hono } from "hono";
import type { AppVariables } from "../middleware/deviceAuth";
import { isFriendCode } from "../util/validate";

export const linkRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const RELEASE_PACKAGE = "com.archerypal.app";
const DEBUG_PACKAGE = "com.archerypal.app.debug";
const ROOM_PARAM = /^[A-Za-z0-9._~-]{1,512}$/;

function fingerprints(raw: string | undefined): string[] {
	return (raw ?? "")
		.split(",")
		.map((f) => f.trim().toUpperCase())
		.filter((f) => /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(f));
}

linkRoutes.get("/.well-known/assetlinks.json", (c) => {
	const statements: unknown[] = [];
	const release = fingerprints(c.env.ANDROID_RELEASE_CERT_SHA256);
	const debug = fingerprints(c.env.ANDROID_DEBUG_CERT_SHA256);
	if (release.length) {
		statements.push({
			relation: ["delegate_permission/common.handle_all_urls"],
			target: {
				namespace: "android_app",
				package_name: RELEASE_PACKAGE,
				sha256_cert_fingerprints: release,
			},
		});
	}
	if (debug.length) {
		statements.push({
			relation: ["delegate_permission/common.handle_all_urls"],
			target: {
				namespace: "android_app",
				package_name: DEBUG_PACKAGE,
				sha256_cert_fingerprints: debug,
			},
		});
	}
	return c.json(statements, 200, { "Cache-Control": "public, max-age=3600" });
});

linkRoutes.get("/f/:code", (c) => {
	const code = c.req.param("code").trim().toUpperCase();
	if (!isFriendCode(code)) return c.text("not found", 404);
	return c.html(
		landingPage({
			title: "Add me on Archerypal",
			body: `Someone shared their Archerypal Friend ID <strong>${escapeHtml(code)}</strong>. Open the app to send them a friend request.`,
			appUri: `archerypal://friend?code=${encodeURIComponent(code)}`,
			referrer: `friend=${code}`,
		}),
	);
});

linkRoutes.get("/r/:roomId", (c) => {
	const roomId = c.req.param("roomId");
	const token = c.req.query("token") ?? "";
	if (!ROOM_PARAM.test(roomId) || (token && !ROOM_PARAM.test(token))) return c.text("not found", 404);
	const tokenParam = token ? `&token=${encodeURIComponent(token)}` : "";
	return c.html(
		landingPage({
			title: "Join an Archerypal match",
			body: `You've been challenged to a remote archery match (room <strong>${escapeHtml(roomId)}</strong>). Open the app to join the lobby.`,
			appUri: `archerypal://remote?roomId=${encodeURIComponent(roomId)}${tokenParam}`,
			referrer: "invite=match",
		}),
	);
});

function landingPage(opts: { title: string; body: string; appUri: string; referrer: string }): string {
	const store = `https://play.google.com/store/apps/details?id=${RELEASE_PACKAGE}&referrer=${encodeURIComponent(opts.referrer)}`;
	const appTarget = new URL(opts.appUri);
	const intentUri =
		`intent://${appTarget.host}${appTarget.search}` +
		`#Intent;scheme=archerypal;package=${RELEASE_PACKAGE};` +
		`S.browser_fallback_url=${encodeURIComponent(store)};end`;
	return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${escapeHtml(opts.title)}</title>
<style>
body{margin:0;font-family:system-ui,sans-serif;background:#13201a;color:#eef3ee;display:flex;min-height:100vh;align-items:center;justify-content:center}
main{max-width:420px;padding:32px 24px;text-align:center}
h1{font-size:1.6rem;margin:0 0 12px}
p{line-height:1.5;color:#c9d6cc}
a.btn{display:block;margin:16px 0 0;padding:14px;border-radius:12px;text-decoration:none;font-weight:600}
a.primary{background:#d9a441;color:#13201a}
a.secondary{border:1px solid #6f8f78;color:#eef3ee}
</style>
</head>
<body>
<main>
<h1>${escapeHtml(opts.title)}</h1>
<p>${opts.body}</p>
<a class="btn primary" href="${escapeHtml(intentUri)}">Open in Archerypal</a>
<a class="btn secondary" href="${escapeHtml(store)}">Get Archerypal on Google Play</a>
</main>
</body>
</html>`;
}

function escapeHtml(value: string): string {
	return value
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/"/g, "&quot;")
		.replace(/'/g, "&#39;");
}
