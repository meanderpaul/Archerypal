import seedShoots from "../../data/shoots-ne.json";
import { cancelCuratedNotIn, upsertCuratedShoot, type CuratedShootInput } from "../db/shoots";
import { scrapeAllRemoteShoots, type ParsedShoot } from "./shootSources";

type SeedShoot = {
	id: string;
	title: string;
	description?: string;
	locationName: string;
	region?: string;
	startsAt: number;
	endsAt?: number;
	sourceUrl?: string;
	registrationUrl?: string;
	eventType?: string;
	latitude?: number;
	longitude?: number;
};

function toCurated(s: SeedShoot | ParsedShoot): CuratedShootInput {
	return {
		id: s.id,
		title: s.title,
		description: s.description,
		locationName: s.locationName,
		region: s.region,
		startsAt: s.startsAt,
		endsAt: s.endsAt,
		sourceUrl: s.sourceUrl,
		registrationUrl: s.registrationUrl,
		eventType: s.eventType ?? "3d",
		latitude: s.latitude,
		longitude: s.longitude,
	};
}

function promoteDescription(seed: SeedShoot, remote: ParsedShoot): string {
	const base = (remote.description ?? seed.description ?? "").replace(
		/^\[Provisional[^\]]*\]\s*/i,
		"",
	);
	return base.trim();
}

/**
 * Merge curated seed with live scrapes. Remote TAC (same stable id) overwrites
 * provisional seed rows with confirmed dates/URLs.
 */
export function mergeShoots(
	seed: SeedShoot[],
	remote: ParsedShoot[],
): CuratedShootInput[] {
	const byId = new Map<string, CuratedShootInput>();
	for (const s of seed) byId.set(s.id, toCurated(s));

	for (const r of remote) {
		const existing = byId.get(r.id);
		if (existing) {
			byId.set(r.id, {
				...existing,
				...toCurated(r),
				description: promoteDescription(existing as SeedShoot, r),
				registrationUrl: r.registrationUrl || existing.registrationUrl,
				sourceUrl: r.sourceUrl || existing.sourceUrl,
			});
		} else {
			byId.set(r.id, toCurated(r));
		}
	}
	return [...byId.values()];
}

export type ShootSyncResult = {
	upserted: number;
	cancelled: number;
	ids: string[];
};

export async function runShootSync(db: D1Database): Promise<ShootSyncResult> {
	const remote = await scrapeAllRemoteShoots();
	const merged = mergeShoots(seedShoots as SeedShoot[], remote);
	for (const sh of merged) {
		await upsertCuratedShoot(db, sh);
	}
	const ids = merged.map((s) => s.id);
	// Only cancel curated NE sync ids that disappeared — keepIds = all we just wrote.
	const cancelled = await cancelCuratedNotIn(db, ids);
	return { upserted: merged.length, cancelled, ids };
}
