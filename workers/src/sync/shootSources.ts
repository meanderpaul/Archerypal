export type ParsedShoot = {
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

const MONTHS: Record<string, number> = {
	january: 0,
	jan: 0,
	february: 1,
	feb: 1,
	march: 2,
	mar: 2,
	april: 3,
	apr: 3,
	may: 4,
	june: 5,
	jun: 5,
	july: 6,
	jul: 6,
	august: 7,
	aug: 7,
	september: 8,
	sep: 8,
	sept: 8,
	october: 9,
	oct: 9,
	november: 10,
	nov: 10,
	december: 11,
	dec: 11,
};

/** Parse ranges like "May 22-24, 2026" or "May 22–24, 2026". */
export function parseMonthDayRange(
	text: string,
): { start: number; end: number; year: number } | null {
	const re =
		/\b(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\s+(\d{1,2})\s*[-–]\s*(\d{1,2}),?\s*(20\d{2})\b/i;
	const m = text.match(re);
	if (!m) return null;
	const month = MONTHS[m[1].toLowerCase()];
	if (month == null) return null;
	const d1 = Number(m[2]);
	const d2 = Number(m[3]);
	const year = Number(m[4]);
	return {
		year,
		start: Date.UTC(year, month, d1),
		end: Date.UTC(year, month, d2, 23, 59, 59),
	};
}

async function fetchText(url: string): Promise<string | null> {
	try {
		const res = await fetch(url, {
			headers: { "user-agent": "ArcherypalShootSync/1.0" },
			redirect: "follow",
		});
		if (!res.ok) return null;
		return await res.text();
	} catch {
		return null;
	}
}

function stripProvisional(desc: string | undefined): string {
	return (desc ?? "").replace(/^\[Provisional[^\]]*\]\s*/i, "").trim();
}

/**
 * Scrape TAC Killington page + events list. When 2027 (or later) dates appear,
 * return a confirmed shoot that overwrites the provisional curated id.
 */
export async function scrapeTacKillington(): Promise<ParsedShoot[]> {
	const out: ParsedShoot[] = [];
	const killingtonHtml = await fetchText(
		"https://totalarcherychallenge.com/pages/killington-pico-vt",
	);
	const eventsHtml = await fetchText("https://totalarcherychallenge.com/pages/events");
	const blob = `${killingtonHtml ?? ""}\n${eventsHtml ?? ""}`;

	// Prefer Killington/Pico section dates.
	const killingtonSection =
		blob.match(/Killington[\s\S]{0,800}?May[\s\S]{0,80}?20\d{2}/i)?.[0] ?? blob;

	const range = parseMonthDayRange(killingtonSection) ?? parseMonthDayRange(blob);
	if (!range) return out;

	const confirmed = !/provisional|coming soon|stay tuned|dates TBD/i.test(
		killingtonSection.slice(0, 400),
	);
	// Only promote years we care about when page has a concrete range.
	if (range.year >= 2026 && confirmed) {
		const id = `curated-tac-killington-${range.year}`;
		out.push({
			id,
			title: "Total Archery Challenge — Killington/Pico",
			description: stripProvisional(
				"Jack Links Total Archery Challenge powered by PSE at Pico Mountain. 100+ 3D targets across mountain courses.",
			),
			locationName: "Pico Mountain Resort, 73 Alpine Dr, Mendon, VT 05701",
			region: "Vermont",
			startsAt: range.start,
			endsAt: range.end,
			eventType: "3d",
			sourceUrl: "https://totalarcherychallenge.com/pages/killington-pico-vt",
			registrationUrl:
				range.year === 2026
					? "https://totalarcherychallenge.strideevents.com/total-archery-challenge-killington-vt/event-information"
					: "https://totalarcherychallenge.com/pages/killington-pico-vt",
			latitude: 43.6556,
			longitude: -72.8414,
		});
	}
	return out;
}

/** Best-effort date scrape from Tri County / MA-RI pages (when text dates appear). */
export async function scrapeTriCountyPages(): Promise<ParsedShoot[]> {
	const out: ParsedShoot[] = [];
	const pages = [
		{
			url: "https://www.tricountyarchers.com/",
			region: "NY Tri County",
			idPrefix: "curated-ny-tricounty-scraped",
		},
		{
			url: "http://archery.bethlehemrodandgun.com/?page_id=2",
			region: "NY Tri County",
			idPrefix: "curated-ny-tricounty-scraped",
		},
		{
			url: "https://www.oldbow.com/tricounty-3d-archery-league.htm",
			region: "MA/RI Tri-County",
			idPrefix: "curated-mari-tricounty-scraped",
		},
		{
			url: "https://www.hansonrodandgunclub.com/tricounty-3d-archery-league",
			region: "MA/RI Tri-County",
			idPrefix: "curated-mari-tricounty-scraped",
		},
	];

	const dateRe =
		/\b(?:Sunday,?\s+)?((?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},?\s+20\d{2}|\d{1,2}\/\d{1,2}\/20\d{2})\b/gi;

	for (const page of pages) {
		const html = await fetchText(page.url);
		if (!html) continue;
		const plain = html.replace(/<[^>]+>/g, " ");
		const matches = [...plain.matchAll(dateRe)].map((m) => m[1]);
		const seen = new Set<string>();
		for (const raw of matches) {
			const t = parseLooseDate(raw);
			if (!t) continue;
			const key = t.toISOString().slice(0, 10);
			if (seen.has(key)) continue;
			seen.add(key);
			// Only future-ish or current-year dates
			if (t.getUTCFullYear() < 2026) continue;
			out.push({
				id: `${page.idPrefix}-${key}`,
				title: `${page.region} 3D Shoot`,
				description: "Auto-imported from league page when dates were published as text.",
				locationName: `${page.region} host club — see source`,
				region: page.region,
				startsAt: Date.UTC(t.getUTCFullYear(), t.getUTCMonth(), t.getUTCDate(), 11),
				endsAt: Date.UTC(t.getUTCFullYear(), t.getUTCMonth(), t.getUTCDate(), 18),
				eventType: "3d",
				sourceUrl: page.url.startsWith("http://")
					? page.url.replace("http://", "https://")
					: page.url,
			});
		}
	}
	return out;
}

function parseLooseDate(raw: string): Date | null {
	const mdy = raw.match(/^(\d{1,2})\/(\d{1,2})\/(20\d{2})$/);
	if (mdy) {
		return new Date(Date.UTC(Number(mdy[3]), Number(mdy[1]) - 1, Number(mdy[2])));
	}
	const t = Date.parse(raw);
	if (!Number.isFinite(t)) return null;
	return new Date(t);
}

export async function scrapeAllRemoteShoots(): Promise<ParsedShoot[]> {
	const [tac, tri] = await Promise.all([scrapeTacKillington(), scrapeTriCountyPages()]);
	const byId = new Map<string, ParsedShoot>();
	for (const s of [...tac, ...tri]) byId.set(s.id, s);
	return [...byId.values()];
}
