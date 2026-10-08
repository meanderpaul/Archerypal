import type { Context } from "hono";
import { hasActivePlus } from "../db/entitlements";
import { textError } from "../util/http";
import type { AppVariables } from "./deviceAuth";

export async function requirePlus(
	c: Context<{ Bindings: Env; Variables: AppVariables }>,
): Promise<Response | null> {
	if (c.env.BYPASS_PLUS_ENTITLEMENT === "true" || c.env.BYPASS_PLUS_ENTITLEMENT === "1") {
		return null;
	}
	const code = c.get("friendCode");
	const now = Math.floor(Date.now() / 1000);
	const ok = await hasActivePlus(c.env.DB, code, now);
	if (!ok) return textError(c, 402, "Archerypal Plus required");
	return null;
}
