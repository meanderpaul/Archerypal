#!/usr/bin/env node
/**
 * Upsert curated NE shoots via admin API.
 *
 * Usage (PowerShell):
 *   $env:ADMIN_API_KEY="..."; $env:MATCH_SERVER_URL="https://archerypal-api.pauljreedii.workers.dev"; node scripts/seed-shoots.mjs
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const base = (process.env.MATCH_SERVER_URL ?? "https://archerypal-api.pauljreedii.workers.dev").replace(
	/\/$/,
	"",
);
const adminKey = process.env.ADMIN_API_KEY ?? "";
if (!adminKey) {
	console.error("ADMIN_API_KEY required");
	process.exit(1);
}

const dir = dirname(fileURLToPath(import.meta.url));
const shoots = JSON.parse(
	readFileSync(join(dir, "..", "data", "shoots-ne.json"), "utf8"),
);

const keepIds = [];
for (const s of shoots) {
	const res = await fetch(`${base}/v1/admin/shoots`, {
		method: "POST",
		headers: {
			"content-type": "application/json",
			"X-Admin-Key": adminKey,
		},
		body: JSON.stringify(s),
	});
	if (!res.ok) {
		console.error("upsert failed", s.id, res.status, await res.text());
		process.exit(1);
	}
	keepIds.push(s.id);
	console.log("ok", s.id);
}

const cancelRes = await fetch(`${base}/v1/admin/shoots/cancel-stale`, {
	method: "POST",
	headers: {
		"content-type": "application/json",
		"X-Admin-Key": adminKey,
	},
	body: JSON.stringify({ keepIds }),
});
if (!cancelRes.ok) {
	console.error("cancel-stale failed", cancelRes.status, await cancelRes.text());
	process.exit(1);
}
const body = await cancelRes.json();
console.log("seeded", keepIds.length, "cancelled", body.cancelled);
