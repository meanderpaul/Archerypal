import type { Context } from "hono";

export function json(c: Context, status: number, body: unknown): Response {
	return c.json(body as Record<string, unknown>, status as 200);
}

export function textError(c: Context, status: number, message: string): Response {
	return c.text(message, status as 400);
}

export async function readJson<T>(c: Context): Promise<T | null> {
	try {
		return (await c.req.json()) as T;
	} catch {
		return null;
	}
}

export function nowSec(): number {
	return Math.floor(Date.now() / 1000);
}
