import { createMiddleware } from "hono/factory";
import { appKeyMatches } from "../util/sealed";
import { textError } from "../util/http";

/** Gate open bootstrap endpoints so casual curl cannot register / restore. */
export const requireAppKey = createMiddleware<{ Bindings: Env }>(async (c, next) => {
	const expected = (c.env.APP_CLIENT_KEY ?? "").trim();
	const provided = (c.req.header("X-App-Key") ?? "").trim();
	if (!expected || !appKeyMatches(provided, expected)) {
		return textError(c, 401, "unauthorized");
	}
	await next();
});
