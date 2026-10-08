import { SignJWT, jwtVerify } from "jose";

export type RoomClaims = {
	room_id: string;
	friend_code: string;
	host: boolean;
	sub: string;
};

function secretKey(secret: string): Uint8Array {
	return new TextEncoder().encode(secret);
}

export async function issueRoomToken(
	jwtSecret: string,
	roomId: string,
	friendCode: string,
	host: boolean,
	ttlSeconds: number,
): Promise<string> {
	const now = Math.floor(Date.now() / 1000);
	return new SignJWT({
		room_id: roomId,
		friend_code: friendCode,
		host,
	})
		.setProtectedHeader({ alg: "HS256" })
		.setSubject(friendCode)
		.setIssuedAt(now)
		.setExpirationTime(now + ttlSeconds)
		.sign(secretKey(jwtSecret));
}

export async function parseRoomToken(
	jwtSecret: string,
	token: string,
): Promise<RoomClaims | null> {
	try {
		const { payload } = await jwtVerify(token, secretKey(jwtSecret), {
			algorithms: ["HS256"],
		});
		const room_id = String(payload.room_id ?? "");
		const friend_code = String(payload.friend_code ?? payload.sub ?? "");
		const host = Boolean(payload.host);
		if (!room_id || !friend_code) return null;
		return { room_id, friend_code, host, sub: friend_code };
	} catch {
		return null;
	}
}
