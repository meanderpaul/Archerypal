const encoder = new TextEncoder();

export function utf8(s: string): Uint8Array {
	return encoder.encode(s);
}

export function bytesToHex(bytes: ArrayBuffer | Uint8Array): string {
	const arr = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
	return [...arr].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function hexToBytes(hex: string): Uint8Array {
	const clean = hex.trim().toLowerCase();
	if (clean.length % 2 !== 0) throw new Error("invalid hex");
	const out = new Uint8Array(clean.length / 2);
	for (let i = 0; i < out.length; i++) {
		out[i] = parseInt(clean.slice(i * 2, i * 2 + 2), 16);
	}
	return out;
}

export async function sha256(data: ArrayBuffer | Uint8Array | string): Promise<Uint8Array> {
	const buf = typeof data === "string" ? utf8(data) : data;
	const digest = await crypto.subtle.digest("SHA-256", buf as BufferSource);
	return new Uint8Array(digest);
}

export async function hmacSha256(keyBytes: Uint8Array, message: string): Promise<Uint8Array> {
	const key = await crypto.subtle.importKey(
		"raw",
		keyBytes as BufferSource,
		{ name: "HMAC", hash: "SHA-256" },
		false,
		["sign"],
	);
	const sig = await crypto.subtle.sign("HMAC", key, utf8(message));
	return new Uint8Array(sig);
}

export function timingSafeEqual(a: Uint8Array, b: Uint8Array): boolean {
	if (a.length !== b.length) return false;
	let diff = 0;
	for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
	return diff === 0;
}

export function randomHex(byteLen: number): string {
	const b = new Uint8Array(byteLen);
	crypto.getRandomValues(b);
	return bytesToHex(b);
}

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function randomCode(length: number): string {
	const b = new Uint8Array(length);
	crypto.getRandomValues(b);
	let code = "";
	for (let i = 0; i < length; i++) code += CODE_ALPHABET[b[i]! % CODE_ALPHABET.length];
	return code;
}

export function generateFriendCode(): string {
	return "AP-" + randomCode(6);
}

/** Short shareable room id (40 bits). Joining still requires the invitee's device auth. */
export function generateRoomCode(): string {
	return randomCode(8);
}

/** Mask for API responses — never return full emails to clients or logs. */
export function maskEmail(email: string | null | undefined): string | null {
	if (!email || !email.trim()) return null;
	const trimmed = email.trim();
	const at = trimmed.indexOf("@");
	if (at <= 1) return "***";
	const local = trimmed.slice(0, at);
	const domain = trimmed.slice(at);
	return `${local.slice(0, 2)}***${domain}`;
}
