import {
	bytesToHex,
	hmacSha256,
	hexToBytes,
	sha256,
	timingSafeEqual,
} from "../util/crypto";
import { consumeNonce } from "../db/nonces";
import { getPlayer, getPlayerByDeviceId } from "../db/players";
import { textError } from "../util/http";
import {
	isSealedContentType,
	parseSealedEnvelope,
	SEALED_CONTENT_TYPE,
	sealJson,
	unsealBytes,
} from "../util/sealed";
import { createMiddleware } from "hono/factory";

const MAX_SKEW_SEC = 5 * 60;

export type AppVariables = {
	friendCode: string;
	rawBody: ArrayBuffer;
	deviceSecretHash: Uint8Array;
	authTimestamp: string;
};

function isMutating(method: string): boolean {
	return method === "POST" || method === "PUT" || method === "PATCH" || method === "DELETE";
}

function signPath(url: URL): string {
	let path = url.pathname;
	if (url.search) path += url.search;
	return path;
}

export const deviceAuth = createMiddleware<{
	Bindings: Env;
	Variables: AppVariables;
}>(async (c, next) => {
	const deviceId = (c.req.header("X-Device-Id") ?? "").trim();
	if (!deviceId) return textError(c, 401, "unauthorized");

	const player = await getPlayerByDeviceId(c.env.DB, deviceId);
	if (!player) return textError(c, 401, "unauthorized");

	const rawBody = await c.req.raw.arrayBuffer();

	const tsRaw = (c.req.header("X-Timestamp") ?? "").trim();
	const sigHex = (c.req.header("X-Device-Signature") ?? "").trim();
	if (!tsRaw || !sigHex) return textError(c, 401, "unauthorized");

	const ts = Number.parseInt(tsRaw, 10);
	if (!Number.isFinite(ts)) return textError(c, 401, "unauthorized");
	const now = Math.floor(Date.now() / 1000);
	if (ts > now + MAX_SKEW_SEC || ts < now - MAX_SKEW_SEC) {
		return textError(c, 401, "unauthorized");
	}

	let nonce = "";
	if (isMutating(c.req.method)) {
		nonce = (c.req.header("X-Nonce") ?? "").trim();
		if (!nonce) return textError(c, 401, "unauthorized");
	}

	const bodyHash = bytesToHex(await sha256(new Uint8Array(rawBody)));
	const url = new URL(c.req.url);
	const canonical = `${tsRaw}${nonce}${c.req.method}${signPath(url)}${bodyHash}`;

	const secretHash = new Uint8Array(player.deviceSecretHash);
	const expected = await hmacSha256(secretHash, canonical);
	let got: Uint8Array;
	try {
		got = hexToBytes(sigHex);
	} catch {
		return textError(c, 401, "unauthorized");
	}
	if (!timingSafeEqual(expected, got)) return textError(c, 401, "unauthorized");

	if (nonce) {
		const result = await consumeNonce(c.env.DB, player.friendCode, nonce, ts + MAX_SKEW_SEC);
		if (result === "replay") return textError(c, 401, "unauthorized");
	}

	let plainBody = rawBody;
	const contentType = c.req.header("content-type") ?? "";
	if (rawBody.byteLength > 0 && isSealedContentType(contentType)) {
		const envelope = parseSealedEnvelope(new Uint8Array(rawBody));
		if (!envelope) return textError(c, 401, "unauthorized");
		try {
			const plain = await unsealBytes(secretHash, player.friendCode, tsRaw, envelope);
			plainBody = plain.buffer.slice(
				plain.byteOffset,
				plain.byteOffset + plain.byteLength,
			) as ArrayBuffer;
		} catch {
			return textError(c, 401, "unauthorized");
		}
	} else if (rawBody.byteLength > 0 && isMutating(c.req.method)) {
		// Authenticated mutating JSON must be sealed (multipart media stays HMAC-only).
		if (!contentType.toLowerCase().includes("multipart/form-data")) {
			return textError(c, 401, "unauthorized");
		}
	}

	c.set("friendCode", player.friendCode);
	c.set("rawBody", plainBody);
	c.set("deviceSecretHash", secretHash);
	c.set("authTimestamp", tsRaw);
	await next();

	const res = c.res;
	if (!res) return;
	const resType = res.headers.get("content-type") ?? "";
	if (!resType.toLowerCase().includes("application/json")) return;
	if (res.status === 304) return;

	try {
		const resBuf = new Uint8Array(await res.arrayBuffer());
		const sealed = await sealJson(
			secretHash,
			player.friendCode,
			tsRaw,
			JSON.parse(new TextDecoder().decode(resBuf)),
		);
		const headers = new Headers();
		headers.set("content-type", SEALED_CONTENT_TYPE);
		const etag = res.headers.get("etag");
		if (etag) headers.set("etag", etag);
		c.res = new Response(sealed, {
			status: res.status,
			statusText: res.statusText,
			headers,
		});
	} catch {
		// Leave original response if sealing fails.
	}
});

/**
 * One-time legacy bind: X-Friend-Code + HMAC (cleartext) → returns deviceId.
 * Not sealed; used so existing installs can learn their opaque id.
 */
export const legacyFriendCodeAuth = createMiddleware<{
	Bindings: Env;
	Variables: AppVariables;
}>(async (c, next) => {
	const friendCode = (c.req.header("X-Friend-Code") ?? "").trim();
	if (!friendCode) return textError(c, 401, "unauthorized");

	const player = await getPlayer(c.env.DB, friendCode);
	if (!player) return textError(c, 401, "unauthorized");

	const rawBody = await c.req.raw.arrayBuffer();
	const tsRaw = (c.req.header("X-Timestamp") ?? "").trim();
	const sigHex = (c.req.header("X-Device-Signature") ?? "").trim();
	if (!tsRaw || !sigHex) return textError(c, 401, "unauthorized");

	const ts = Number.parseInt(tsRaw, 10);
	if (!Number.isFinite(ts)) return textError(c, 401, "unauthorized");
	const now = Math.floor(Date.now() / 1000);
	if (ts > now + MAX_SKEW_SEC || ts < now - MAX_SKEW_SEC) {
		return textError(c, 401, "unauthorized");
	}

	const nonce = (c.req.header("X-Nonce") ?? "").trim();
	if (!nonce) return textError(c, 401, "unauthorized");

	const bodyHash = bytesToHex(await sha256(new Uint8Array(rawBody)));
	const url = new URL(c.req.url);
	const canonical = `${tsRaw}${nonce}${c.req.method}${signPath(url)}${bodyHash}`;
	const secretHash = new Uint8Array(player.deviceSecretHash);
	const expected = await hmacSha256(secretHash, canonical);
	let got: Uint8Array;
	try {
		got = hexToBytes(sigHex);
	} catch {
		return textError(c, 401, "unauthorized");
	}
	if (!timingSafeEqual(expected, got)) return textError(c, 401, "unauthorized");

	const result = await consumeNonce(c.env.DB, friendCode, nonce, ts + MAX_SKEW_SEC);
	if (result === "replay") return textError(c, 401, "unauthorized");

	c.set("friendCode", friendCode);
	c.set("rawBody", rawBody);
	c.set("deviceSecretHash", secretHash);
	c.set("authTimestamp", tsRaw);
	await next();
});
