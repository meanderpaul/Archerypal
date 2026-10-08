-- Closed beta invite codes (Phase 3 prep)

CREATE TABLE IF NOT EXISTS beta_invites (
    code TEXT PRIMARY KEY,
    note TEXT,
    redeemed_by TEXT REFERENCES players(friend_code),
    redeemed_at INTEGER,
    expires_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_beta_invites_redeemed ON beta_invites(redeemed_by);
