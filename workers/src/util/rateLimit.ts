/** Simple isolate-local sliding-window rate limiter (soft protection). */

const buckets = new Map<string, number[]>();

export function allowRate(key: string, limit: number, windowMs: number): boolean {
	if (!key) return true;
	const now = Date.now();
	const cutoff = now - windowMs;
	const prev = buckets.get(key) ?? [];
	const kept = prev.filter((t) => t > cutoff);
	if (kept.length >= limit) {
		buckets.set(key, kept);
		return false;
	}
	kept.push(now);
	buckets.set(key, kept);
	return true;
}

export function clientIp(c: { req: { header: (name: string) => string | undefined } }): string {
	const cf = c.req.header("cf-connecting-ip");
	if (cf) return cf.trim();
	const xff = c.req.header("x-forwarded-for");
	if (xff) return xff.split(",")[0]?.trim() || "unknown";
	return "unknown";
}
