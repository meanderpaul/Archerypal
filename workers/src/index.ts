import { app } from "./app";
import { MatchRoom } from "./room/MatchRoom";
import { runShootSync } from "./sync/runShootSync";

export { MatchRoom };

export default {
	fetch: app.fetch,

	async scheduled(
		_controller: ScheduledController,
		env: Env,
		ctx: ExecutionContext,
	): Promise<void> {
		ctx.waitUntil(
			runShootSync(env.DB)
				.then((r) => {
					console.log(
						`shoot-sync upserted=${r.upserted} cancelled=${r.cancelled}`,
					);
				})
				.catch((err) => console.error("shoot-sync failed", err)),
		);
	},
};
