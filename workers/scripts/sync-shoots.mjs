#!/usr/bin/env node
/**
 * Trigger a remote shoot sync by re-seeding from shoots-ne.json plus optional
 * local scrape. Prefer Worker cron in production; this is for manual ops.
 *
 * Usage:
 *   $env:ADMIN_API_KEY="..."; node scripts/sync-shoots.mjs
 */
import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const dir = dirname(fileURLToPath(import.meta.url));
const r = spawnSync(process.execPath, [join(dir, "seed-shoots.mjs")], {
	stdio: "inherit",
	env: process.env,
});
process.exit(r.status ?? 1);
