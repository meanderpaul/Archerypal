import { Hono } from "hono";
import type { AppVariables } from "./middleware/deviceAuth";
import { healthRoutes } from "./routes/health";
import { playerRoutes } from "./routes/players";
import { friendRoutes } from "./routes/friends";
import { authRoutes } from "./routes/auth";
import { matchRoutes } from "./routes/matches";
import { leaderboardRoutes } from "./routes/leaderboards";
import { entitlementRoutes } from "./routes/entitlements";
import { webhookRoutes } from "./routes/webhooks";
import { shootRoutes } from "./routes/shoots";
import { feedRoutes } from "./routes/feed";
import { postRoutes } from "./routes/posts";
import { stubRoutes } from "./routes/stubs";
import { linkRoutes } from "./routes/links";
import { purgeExpiredNonces } from "./db/nonces";
import { nowSec } from "./util/http";

export const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

app.onError((err, c) => {
	console.error("worker error", err);
	return c.text("server error", 500);
});

/** Best-effort nonce purge on traffic (Phase 1; Cron Trigger later). */
app.use("*", async (c, next) => {
	c.executionCtx.waitUntil(
		purgeExpiredNonces(c.env.DB, nowSec()).catch(() => undefined),
	);
	await next();
});

app.route("/", healthRoutes);
app.route("/", playerRoutes);
app.route("/", friendRoutes);
app.route("/", authRoutes);
app.route("/", matchRoutes);
app.route("/", leaderboardRoutes);
app.route("/", entitlementRoutes);
app.route("/", webhookRoutes);
app.route("/", shootRoutes);
app.route("/", feedRoutes);
app.route("/", postRoutes);
app.route("/", linkRoutes);
app.route("/", stubRoutes);
