import { DurableObject } from "cloudflare:workers";

const MAX_CLIENTS = 8;
const MAX_MESSAGE_BYTES = 4096;
const ROOM_TTL_MS = 30 * 60 * 1000;

/**
 * Opaque text fan-out for live match sync (parity with Go room.Manager).
 */
export class MatchRoom extends DurableObject<Env> {
	async fetch(request: Request): Promise<Response> {
		const url = new URL(request.url);
		if (url.pathname === "/join" && request.headers.get("Upgrade") === "websocket") {
			const friendCode = url.searchParams.get("friendCode") ?? "";
			if (!friendCode) {
				return new Response("missing friendCode", { status: 400 });
			}
			const sockets = this.ctx.getWebSockets();
			if (sockets.length >= MAX_CLIENTS) {
				return new Response("room full", { status: 503 });
			}

			const pair = new WebSocketPair();
			const [client, server] = Object.values(pair);
			this.ctx.acceptWebSocket(server);
			server.serializeAttachment({ friendCode });
			await this.ctx.storage.setAlarm(Date.now() + ROOM_TTL_MS);
			return new Response(null, { status: 101, webSocket: client });
		}

		if (url.pathname === "/close") {
			for (const ws of this.ctx.getWebSockets()) {
				try {
					ws.close(1000, "room closed");
				} catch {
					/* ignore */
				}
			}
			await this.ctx.storage.deleteAlarm();
			return new Response("ok");
		}

		return new Response("not found", { status: 404 });
	}

	async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer) {
		const text =
			typeof message === "string" ? message : new TextDecoder().decode(message);
		if (new TextEncoder().encode(text).byteLength > MAX_MESSAGE_BYTES) {
			ws.close(1009, "message too large");
			return;
		}
		for (const peer of this.ctx.getWebSockets()) {
			if (peer === ws) continue;
			try {
				peer.send(text);
			} catch {
				/* ignore broken peer */
			}
		}
		await this.ctx.storage.setAlarm(Date.now() + ROOM_TTL_MS);
	}

	async webSocketClose(ws: WebSocket, code: number, reason: string) {
		try {
			ws.close(code, reason);
		} catch {
			/* ignore */
		}
	}

	async alarm() {
		for (const ws of this.ctx.getWebSockets()) {
			try {
				ws.close(1000, "room expired");
			} catch {
				/* ignore */
			}
		}
	}
}
