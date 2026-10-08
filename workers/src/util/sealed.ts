import { bytesToHex, hexToBytes, timingSafeEqual, utf8 } from "./crypto";

export const SEALED_CONTENT_TYPE = "application/vnd.archerypal.sealed+json";
export const SEAL_INFO = "archerypal-v1-enc";

export type SealedEnvelope = {
	v: 1;
	iv: string;
	ct: string;
	tag: string;
};

function b64Encode(bytes: Uint8Array): string {
	let s = "";
	for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]!);
	return btoa(s);
}

function b64Decode(s: string): Uint8Array {
	const bin = atob(s);
	const out = new Uint8Array(bin.length);
	for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
	return out;
}

/** HKDF-SHA256 extract+expand → 32-byte AES key. */
export async function deriveSealKey(
	deviceSecretHash: Uint8Array,
	friendCode: string,
	timestampSeconds: number | string,
): Promise<CryptoKey> {
	const salt = utf8(`${friendCode}:${timestampSeconds}`);
	const info = utf8(SEAL_INFO);
	const baseKey = await crypto.subtle.importKey(
		"raw",
		deviceSecretHash as BufferSource,
		"HKDF",
		false,
		["deriveKey"],
	);
	return crypto.subtle.deriveKey(
		{ name: "HKDF", hash: "SHA-256", salt, info },
		baseKey,
		{ name: "AES-GCM", length: 256 },
		false,
		["encrypt", "decrypt"],
	);
}

export async function sealBytes(
	deviceSecretHash: Uint8Array,
	friendCode: string,
	timestampSeconds: number | string,
	plaintext: Uint8Array,
): Promise<SealedEnvelope> {
	const key = await deriveSealKey(deviceSecretHash, friendCode, timestampSeconds);
	const iv = new Uint8Array(12);
	crypto.getRandomValues(iv);
	const sealed = new Uint8Array(
		await crypto.subtle.encrypt(
			{ name: "AES-GCM", iv, tagLength: 128 },
			key,
			plaintext as BufferSource,
		),
	);
	const tag = sealed.slice(sealed.length - 16);
	const ct = sealed.slice(0, sealed.length - 16);
	return {
		v: 1,
		iv: b64Encode(iv),
		ct: b64Encode(ct),
		tag: b64Encode(tag),
	};
}

export async function unsealBytes(
	deviceSecretHash: Uint8Array,
	friendCode: string,
	timestampSeconds: number | string,
	envelope: SealedEnvelope,
): Promise<Uint8Array> {
	if (envelope.v !== 1) throw new Error("unsupported seal version");
	const key = await deriveSealKey(deviceSecretHash, friendCode, timestampSeconds);
	const iv = b64Decode(envelope.iv);
	const ct = b64Decode(envelope.ct);
	const tag = b64Decode(envelope.tag);
	if (iv.length !== 12 || tag.length !== 16) throw new Error("invalid seal");
	const combined = new Uint8Array(ct.length + tag.length);
	combined.set(ct, 0);
	combined.set(tag, ct.length);
	const plain = await crypto.subtle.decrypt(
		{ name: "AES-GCM", iv, tagLength: 128 },
		key,
		combined,
	);
	return new Uint8Array(plain);
}

export function parseSealedEnvelope(raw: Uint8Array): SealedEnvelope | null {
	try {
		const obj = JSON.parse(new TextDecoder().decode(raw)) as Partial<SealedEnvelope>;
		if (obj.v !== 1 || typeof obj.iv !== "string" || typeof obj.ct !== "string" || typeof obj.tag !== "string") {
			return null;
		}
		return { v: 1, iv: obj.iv, ct: obj.ct, tag: obj.tag };
	} catch {
		return null;
	}
}

export async function sealJson(
	deviceSecretHash: Uint8Array,
	friendCode: string,
	timestampSeconds: number | string,
	body: unknown,
): Promise<Uint8Array> {
	const plaintext = utf8(JSON.stringify(body));
	const envelope = await sealBytes(deviceSecretHash, friendCode, timestampSeconds, plaintext);
	return utf8(JSON.stringify(envelope));
}

export function isSealedContentType(value: string | null | undefined): boolean {
	return (value ?? "").toLowerCase().includes("application/vnd.archerypal.sealed+json");
}

/** Constant-time compare for app client key. */
export function appKeyMatches(provided: string, expected: string): boolean {
	if (!expected) return false;
	const a = utf8(provided);
	const b = utf8(expected);
	if (a.length !== b.length) return false;
	return timingSafeEqual(a, b);
}

export { bytesToHex, hexToBytes };
