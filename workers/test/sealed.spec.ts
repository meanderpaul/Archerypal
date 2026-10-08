import { describe, expect, it } from "vitest";
import { sha256 } from "../src/util/crypto";
import { sealBytes, unsealBytes, appKeyMatches } from "../src/util/sealed";

describe("sealed envelope", () => {
	it("round-trips AES-GCM payload", async () => {
		const secret = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899";
		const hash = await sha256(secret);
		const friendCode = "AP-TEST01";
		const ts = 1_700_000_000;
		const plain = new TextEncoder().encode(JSON.stringify({ hello: "world", n: 42 }));
		const envelope = await sealBytes(hash, friendCode, ts, plain);
		expect(envelope.v).toBe(1);
		expect(envelope.iv.length).toBeGreaterThan(0);
		const out = await unsealBytes(hash, friendCode, ts, envelope);
		expect(new TextDecoder().decode(out)).toBe(new TextDecoder().decode(plain));
	});

	it("fails with wrong timestamp salt", async () => {
		const hash = await sha256("deadbeef");
		const envelope = await sealBytes(hash, "AP-AAAAAA", 100, new TextEncoder().encode("{}"));
		await expect(unsealBytes(hash, "AP-AAAAAA", 101, envelope)).rejects.toThrow();
	});
});

describe("appKeyMatches", () => {
	it("accepts equal keys", () => {
		expect(appKeyMatches("dev-app-client-key-change-me", "dev-app-client-key-change-me")).toBe(
			true,
		);
	});
	it("rejects mismatches", () => {
		expect(appKeyMatches("nope", "dev-app-client-key-change-me")).toBe(false);
		expect(appKeyMatches("", "dev-app-client-key-change-me")).toBe(false);
	});
});
