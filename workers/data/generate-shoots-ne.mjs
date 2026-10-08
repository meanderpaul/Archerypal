import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const dir = dirname(fileURLToPath(import.meta.url));

/**
 * Calendar-day timestamps for multi-day / season events.
 * Noon UTC keeps the intended civil date in US timezones (midnight UTC
 * rolls back to the previous evening in Eastern, e.g. Jan 1 → Dec 31).
 * @param {number} y
 * @param {number} monthIndex zero-based
 * @param {number} d
 */
function calendarDayStart(y, monthIndex, d) {
	return Date.UTC(y, monthIndex, d, 12);
}

/** @param {number} y @param {number} monthIndex @param {number} d */
function calendarDayEnd(y, monthIndex, d) {
	return Date.UTC(y, monthIndex, d, 23, 59, 59);
}

/** @type {Array<Record<string, unknown>>} */
const shoots = [
	{
		id: "curated-tac-killington-2026",
		title: "Total Archery Challenge — Killington/Pico",
		description:
			"Jack Links Total Archery Challenge powered by PSE at Pico Mountain. 100+ 3D targets across mountain courses. Check-in from 6:15 AM; Nock Time courses open 7:00 AM each day.",
		locationName: "Pico Mountain Resort, 73 Alpine Dr, Mendon, VT 05701",
		region: "Vermont",
		startsAt: calendarDayStart(2026, 4, 22),
		endsAt: calendarDayEnd(2026, 4, 24),
		eventType: "3d",
		sourceUrl: "https://totalarcherychallenge.com/pages/killington-pico-vt",
		registrationUrl:
			"https://totalarcherychallenge.strideevents.com/total-archery-challenge-killington-vt/event-information",
		latitude: 43.6556,
		longitude: -72.8414,
	},
];

// Only list shoots with a published date and a named host venue. Guessed future
// dates, season-long ranges, and "rotating host" stubs show up as joinable events
// in the app; future TAC dates arrive via scrapeTacKillington once confirmed.

const ny = [
	["2026-04-12", "Bethlehem Rod & Gun", "Bethlehem Rod & Gun Club, 372 New Unionville Rd, Plattekill, NY", 41.6565, -74.0868],
	["2026-05-03", "Black Rock Fish & Game", "Black Rock Fish & Game Club, Cornwall, NY", 41.4015, -74.0501],
	["2026-05-17", "Monroe-Chester Sportsmen", "Monroe-Chester Sportsmen Club, 224 Gibson Hill Rd, Chester, NY", 41.3126, -74.2715],
	["2026-06-07", "Walden Sportsmen", "Walden Sportsmen's Club, 1823 Route 52, Walden, NY 12586", 41.5618, -74.1885],
	["2026-07-12", "Silver Birch / Airport Park", "Airport Park, 701 Avenue Q, Matamoras, PA 18336", 41.3659, -74.6982],
	["2026-08-09", "Bethlehem Rod & Gun", "Bethlehem Rod & Gun Club, 372 New Unionville Rd, Plattekill, NY", 41.6565, -74.0868],
	["2026-09-13", "Monroe-Chester Sportsmen", "Monroe-Chester Sportsmen Club, 224 Gibson Hill Rd, Chester, NY", 41.3126, -74.2715],
];

for (const [date, short, loc, lat, lng] of ny) {
	const [y, m, d] = date.split("-").map(Number);
	shoots.push({
		id: `curated-ny-tricounty-${date}`,
		title: `NY Tri County 3D — ${short}`,
		description:
			"[Provisional — confirm on league schedule] Open-to-public Tri County Archers 3D. Registration typically 7:00–11:00 AM. Adults $15 / Juniors $5 / Youth free.",
		locationName: loc,
		region: "NY Tri County",
		startsAt: Date.UTC(y, m - 1, d, 11),
		endsAt: Date.UTC(y, m - 1, d, 18),
		eventType: "3d",
		sourceUrl: "https://www.tricountyarchers.com/",
		registrationUrl: "",
		latitude: lat,
		longitude: lng,
	});
}

const mari = [
	["2026-03-15", "Hanson Rod & Gun", "Hanson Rod and Gun Club, 126 Davis Road, Hanson, MA", 42.0615, -70.8803, "https://www.hansonrodandgunclub.com/tricounty-3d-archery-league"],
	["2026-04-26", "Wrentham Sportsman's", "Wrentham Sportsman's Club / WSA, Plainville–Wrentham, MA", 42.0326, -71.3331, "http://www.wsaclub.com/Archery.html"],
	["2026-06-14", "Standish Sportsman's", "Standish Sportsman's Association (south shore MA)", 42.05, -70.95, "https://standishsportsman.com/programs/"],
];

for (const [date, short, loc, lat, lng, src] of mari) {
	const [y, m, d] = date.split("-").map(Number);
	shoots.push({
		id: `curated-mari-tricounty-${date}`,
		title: `MA/RI Tri-County 3D — ${short}`,
		description:
			"[Provisional — confirm flyer] League Sunday shoot. Registration typically sunrise–noon. Adults ~$10 / under 18 free at many hosts.",
		locationName: loc,
		region: "MA/RI Tri-County",
		startsAt: Date.UTC(y, m - 1, d, 11),
		endsAt: Date.UTC(y, m - 1, d, 18),
		eventType: "3d",
		sourceUrl: src,
		registrationUrl: "",
		latitude: lat,
		longitude: lng,
	});
}

writeFileSync(join(dir, "shoots-ne.json"), JSON.stringify(shoots, null, 2) + "\n");
console.log("wrote", shoots.length, "shoots");
