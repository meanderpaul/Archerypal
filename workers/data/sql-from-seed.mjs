import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const dir = dirname(fileURLToPath(import.meta.url));
const shoots = JSON.parse(readFileSync(join(dir, "shoots-ne.json"), "utf8"));
const now = Math.floor(Date.now() / 1000);

function esc(s) {
	if (s == null || s === "") return "NULL";
	return "'" + String(s).replace(/'/g, "''") + "'";
}

const stmts = [];
for (const s of shoots) {
	stmts.push(`INSERT INTO shoots (
  id, title, description, location_name, region, starts_at, ends_at,
  host_friend_code, is_curated, is_public, status, created_at,
  source_url, registration_url, event_type, latitude, longitude
) VALUES (
  ${esc(s.id)}, ${esc(s.title)}, ${esc(s.description)}, ${esc(s.locationName)},
  ${esc(s.region)}, ${s.startsAt}, ${s.endsAt ?? "NULL"}, NULL, 1, 0, 'scheduled', ${now},
  ${esc(s.sourceUrl)}, ${esc(s.registrationUrl || null)}, ${esc(s.eventType)},
  ${s.latitude ?? "NULL"}, ${s.longitude ?? "NULL"}
)
ON CONFLICT(id) DO UPDATE SET
  title=excluded.title,
  description=excluded.description,
  location_name=excluded.location_name,
  region=excluded.region,
  starts_at=excluded.starts_at,
  ends_at=excluded.ends_at,
  status='scheduled',
  source_url=excluded.source_url,
  registration_url=excluded.registration_url,
  event_type=excluded.event_type,
  latitude=excluded.latitude,
  longitude=excluded.longitude;`);
}

writeFileSync(join(dir, "seed-shoots.sql"), stmts.join("\n"));
console.log("wrote", stmts.length, "statements");
