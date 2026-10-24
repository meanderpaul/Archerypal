const FRIEND_CODE_RE = /^AP-[A-Z0-9]{6}$/;

export function isFriendCode(code: string): boolean {
	return FRIEND_CODE_RE.test(code);
}

export function clampLimit(raw: string | null | undefined, def = 50, min = 1, max = 100): number {
	const n = Number.parseInt(raw ?? "", 10);
	if (!Number.isFinite(n)) return def;
	return Math.min(max, Math.max(min, n));
}

export function displayNameOk(name: string): boolean {
	const t = name.trim();
	if (t.length < 1 || t.length > 64) return false;
	return [...t].length <= 64;
}

export function deviceSecretOk(secret: string): boolean {
	return /^[0-9a-fA-F]{32,}$/.test(secret.trim());
}

/** Empty string is allowed; non-empty must be https:// */
export function httpsUrlOk(url: string | undefined | null): boolean {
	const t = (url ?? "").trim();
	if (t === "") return true;
	try {
		const u = new URL(t);
		return u.protocol === "https:";
	} catch {
		return false;
	}
}

export function latitudeOk(lat: number | undefined | null): boolean {
	if (lat == null) return true;
	return Number.isFinite(lat) && lat >= -90 && lat <= 90;
}

export function longitudeOk(lng: number | undefined | null): boolean {
	if (lng == null) return true;
	return Number.isFinite(lng) && lng >= -180 && lng <= 180;
}

/** Post body: 1–2000 Unicode chars after trim; no C0 controls except tab/newline. */
export function sanitizePostBody(body: string): string {
	return body
		.replace(/\r\n/g, "\n")
		.replace(/\r/g, "\n")
		.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, "");
}

export function postBodyOk(body: string): boolean {
	const t = sanitizePostBody(body).trim();
	if (t.length < 1) return false;
	return [...t].length <= 2000;
}

export const MAX_COMMENT_CHARS = 500;

export function commentBodyOk(body: string): boolean {
	const t = sanitizePostBody(body).trim();
	if (t.length < 1) return false;
	return [...t].length <= MAX_COMMENT_CHARS;
}
