import { createRemoteJWKSet, jwtVerify } from "jose";

const googleJwks = createRemoteJWKSet(
	new URL("https://www.googleapis.com/oauth2/v3/certs"),
);

export type GoogleIdentity = {
	sub: string;
	email: string | null;
};

export async function verifyGoogleIdToken(
	idToken: string,
	clientId: string,
): Promise<GoogleIdentity | null> {
	if (!clientId) return null;
	try {
		const { payload } = await jwtVerify(idToken, googleJwks, {
			audience: clientId,
			issuer: ["https://accounts.google.com", "accounts.google.com"],
		});
		const sub = String(payload.sub ?? "");
		if (!sub) return null;
		return {
			sub,
			email: typeof payload.email === "string" ? payload.email : null,
		};
	} catch {
		return null;
	}
}
