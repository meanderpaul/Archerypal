import { utf8 } from "../util/crypto";

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

type ServiceAccount = {
	project_id: string;
	client_email: string;
	private_key: string;
	token_uri?: string;
};

export type PushMessage = {
	title: string;
	body: string;
	channelId: string;
	data: Record<string, string>;
};

let cachedToken: { value: string; expiresAt: number; email: string } | null = null;

function parseServiceAccount(raw: string | undefined): ServiceAccount | null {
	if (!raw?.trim()) return null;
	try {
		const sa = JSON.parse(raw) as Partial<ServiceAccount>;
		if (!sa.project_id || !sa.client_email || !sa.private_key) return null;
		return sa as ServiceAccount;
	} catch {
		return null;
	}
}

function base64Url(bytes: Uint8Array): string {
	let bin = "";
	for (const b of bytes) bin += String.fromCharCode(b);
	return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToPkcs8(pem: string): ArrayBuffer {
	const body = pem
		.replace(/-----BEGIN PRIVATE KEY-----/, "")
		.replace(/-----END PRIVATE KEY-----/, "")
		.replace(/\s+/g, "");
	const bin = atob(body);
	const out = new Uint8Array(bin.length);
	for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
	return out.buffer;
}

async function accessToken(sa: ServiceAccount): Promise<string> {
	const now = Math.floor(Date.now() / 1000);
	if (cachedToken && cachedToken.email === sa.client_email && cachedToken.expiresAt - 60 > now) {
		return cachedToken.value;
	}
	const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
	const header = base64Url(utf8(JSON.stringify({ alg: "RS256", typ: "JWT" })));
	const claims = base64Url(
		utf8(
			JSON.stringify({
				iss: sa.client_email,
				scope: FCM_SCOPE,
				aud: tokenUri,
				iat: now,
				exp: now + 3600,
			}),
		),
	);
	const key = await crypto.subtle.importKey(
		"pkcs8",
		pemToPkcs8(sa.private_key),
		{ name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
		false,
		["sign"],
	);
	const signature = await crypto.subtle.sign(
		"RSASSA-PKCS1-v1_5",
		key,
		utf8(`${header}.${claims}`) as BufferSource,
	);
	const assertion = `${header}.${claims}.${base64Url(new Uint8Array(signature))}`;
	const res = await fetch(tokenUri, {
		method: "POST",
		headers: { "content-type": "application/x-www-form-urlencoded" },
		body: new URLSearchParams({
			grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
			assertion,
		}),
	});
	if (!res.ok) throw new Error(`fcm oauth ${res.status}`);
	const json = (await res.json()) as { access_token: string; expires_in: number };
	cachedToken = { value: json.access_token, expiresAt: now + json.expires_in, email: sa.client_email };
	return json.access_token;
}

/** No-op when FCM credentials aren't configured or the recipient has no push token. */
export async function sendPush(env: Env, deviceToken: string | null | undefined, msg: PushMessage): Promise<void> {
	const sa = parseServiceAccount(env.FCM_SERVICE_ACCOUNT_JSON);
	if (!sa || !deviceToken?.trim()) return;
	const token = await accessToken(sa);
	const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
		method: "POST",
		headers: {
			authorization: `Bearer ${token}`,
			"content-type": "application/json",
		},
		body: JSON.stringify({
			message: {
				token: deviceToken,
				notification: { title: msg.title, body: msg.body },
				data: msg.data,
				android: {
					priority: "HIGH",
					notification: { channel_id: msg.channelId },
				},
			},
		}),
	});
	if (!res.ok) {
		const detail = (await res.text()).slice(0, 300);
		throw new Error(`fcm send ${res.status}: ${detail}`);
	}
}
