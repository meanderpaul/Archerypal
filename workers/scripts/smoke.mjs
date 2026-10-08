/**
 * End-to-end smoke against the deployed Worker (Node 22+).
 * Usage: node scripts/smoke.mjs
 *
 * Plus-gated paths need either a real entitlement (RC webhook) or local
 * ALLOW_UNVERIFIED_ENTITLEMENT_SYNC / BYPASS_PLUS_ENTITLEMENT in `.dev.vars`.
 */
import { createHash, createHmac, randomBytes } from "node:crypto";

const BASE = process.env.MATCH_SERVER_URL ?? "https://archerypal-api.pauljreedii.workers.dev";

function hex(buf) {
	return Buffer.from(buf).toString("hex");
}

function sha256(data) {
	return createHash("sha256").update(data).digest();
}

function sign(deviceSecret, timestamp, method, path, body, nonce = "") {
	const key = sha256(deviceSecret);
	const bodyHash = hex(sha256(body));
	const canonical = `${timestamp}${nonce}${method}${path}${bodyHash}`;
	return createHmac("sha256", key).update(canonical).digest("hex");
}

async function req(method, path, { body, friendCode, deviceSecret, allowStatuses = [] } = {}) {
	const bodyBuf = body ? Buffer.from(JSON.stringify(body)) : Buffer.alloc(0);
	const headers = { "Content-Type": "application/json" };
	if (friendCode && deviceSecret) {
		const ts = Math.floor(Date.now() / 1000);
		const mutating = ["POST", "PUT", "PATCH", "DELETE"].includes(method);
		const nonce = mutating ? crypto.randomUUID() : "";
		headers["X-Friend-Code"] = friendCode;
		headers["X-Timestamp"] = String(ts);
		if (nonce) headers["X-Nonce"] = nonce;
		headers["X-Device-Signature"] = sign(deviceSecret, ts, method, path, bodyBuf, nonce);
	}
	const res = await fetch(`${BASE}${path}`, {
		method,
		headers,
		body: bodyBuf.length ? bodyBuf : undefined,
	});
	const text = await res.text();
	let json;
	try {
		json = JSON.parse(text);
	} catch {
		json = text;
	}
	if (!res.ok && !allowStatuses.includes(res.status)) {
		throw new Error(`${method} ${path} -> ${res.status}: ${text}`);
	}
	return { status: res.status, json };
}

function friendCode() {
	const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	let s = "AP-";
	for (let i = 0; i < 6; i++) s += alphabet[randomBytes(1)[0] % alphabet.length];
	return s;
}

async function main() {
	console.log("BASE", BASE);
	const health = await req("GET", "/health");
	console.log("health", health.json);

	const a = { code: friendCode(), secret: hex(randomBytes(32)), name: "SmokeA" };
	const b = { code: friendCode(), secret: hex(randomBytes(32)), name: "SmokeB" };

	await req("POST", "/v1/players/register", {
		body: { friendCode: a.code, displayName: a.name, deviceSecret: a.secret },
	});
	await req("POST", "/v1/players/register", {
		body: { friendCode: b.code, displayName: b.name, deviceSecret: b.secret },
	});
	console.log("registered", a.code, b.code);

	const gone = await req("POST", "/v1/friends/add", {
		friendCode: a.code,
		deviceSecret: a.secret,
		body: { theirFriendCode: b.code },
		allowStatuses: [410],
	});
	if (gone.status !== 410) {
		throw new Error(`expected /friends/add to return 410, got ${gone.status}`);
	}
	console.log("friends/add correctly disabled (410)");

	const sync = await req("POST", "/v1/entitlements/sync", {
		friendCode: a.code,
		deviceSecret: a.secret,
		body: {
			sku: "1plus",
			purchaseTokenHash: hex(sha256(`smoke:${a.code}`)),
			expiresAt: Math.floor(Date.now() / 1000) + 86400,
		},
		allowStatuses: [403],
	});
	await req("POST", "/v1/entitlements/sync", {
		friendCode: b.code,
		deviceSecret: b.secret,
		body: {
			sku: "1plus",
			purchaseTokenHash: hex(sha256(`smoke:${b.code}`)),
			expiresAt: Math.floor(Date.now() / 1000) + 86400,
		},
		allowStatuses: [403],
	});

	if (sync.status === 403) {
		console.log("entitlement sync locked (expected in production); skipping Plus-gated paths");
		console.log("SMOKE OK");
		return;
	}

	await req("POST", "/v1/friends/request", {
		friendCode: a.code,
		deviceSecret: a.secret,
		body: { theirFriendCode: b.code },
	});
	await req("POST", "/v1/friends/accept", {
		friendCode: b.code,
		deviceSecret: b.secret,
		body: { fromFriendCode: a.code },
	});
	const friends = await req("GET", "/v1/friends", {
		friendCode: a.code,
		deviceSecret: a.secret,
	});
	console.log("friends", friends.json);

	const challenge = await req("POST", "/v1/matches/challenge", {
		friendCode: a.code,
		deviceSecret: a.secret,
		body: { targetFriendCode: b.code },
	});
	console.log("challenge", {
		roomId: challenge.json.roomId,
		wsUrl: challenge.json.wsUrl?.slice(0, 80),
		hasHostToken: Boolean(challenge.json.hostToken),
		hasJoinToken: Boolean(challenge.json.joinToken),
	});

	const feed = await req("GET", "/v1/feed?filter=all", {
		friendCode: a.code,
		deviceSecret: a.secret,
	});
	console.log("feed", { itemCount: feed.json?.items?.length ?? feed.json });

	console.log("SMOKE OK");
}

main().catch((err) => {
	console.error("SMOKE FAIL", err);
	process.exit(1);
});
