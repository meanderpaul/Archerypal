-- Phase 1 core schema (ported from server/migrations 001,003,006,008,009)

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS players (
    friend_code TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    device_secret_hash BLOB NOT NULL,
    fcm_token TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS friendships (
    player_a TEXT NOT NULL,
    player_b TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (player_a, player_b),
    CHECK (player_a < player_b),
    FOREIGN KEY (player_a) REFERENCES players(friend_code),
    FOREIGN KEY (player_b) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_friendships_a ON friendships(player_a);
CREATE INDEX IF NOT EXISTS idx_friendships_b ON friendships(player_b);

CREATE TABLE IF NOT EXISTS entitlements (
    friend_code TEXT NOT NULL,
    sku TEXT NOT NULL,
    expires_at INTEGER,
    play_token_hash BLOB,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (friend_code, sku),
    FOREIGN KEY (friend_code) REFERENCES players(friend_code)
);

CREATE TABLE IF NOT EXISTS rooms (
    id TEXT PRIMARY KEY,
    host_friend_code TEXT NOT NULL,
    joiner_friend_code TEXT,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    FOREIGN KEY (host_friend_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_rooms_host ON rooms(host_friend_code);
CREATE INDEX IF NOT EXISTS idx_rooms_status ON rooms(status);

CREATE TABLE IF NOT EXISTS player_stats (
    friend_code TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    matches_played INTEGER NOT NULL DEFAULT 0,
    matches_won INTEGER NOT NULL DEFAULT 0,
    total_points INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (friend_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_player_stats_wins ON player_stats(matches_won DESC, total_points DESC);

CREATE TABLE IF NOT EXISTS completed_matches (
    match_id TEXT PRIMARY KEY,
    completed_at INTEGER NOT NULL,
    scoring_type TEXT NOT NULL,
    target_count INTEGER NOT NULL,
    winner_friend_code TEXT NOT NULL,
    scores_json TEXT NOT NULL,
    room_id TEXT
);

CREATE TABLE IF NOT EXISTS friend_requests (
    from_code TEXT NOT NULL,
    to_code TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (from_code, to_code),
    CHECK (from_code != to_code),
    CHECK (status IN ('pending', 'accepted', 'declined')),
    FOREIGN KEY (from_code) REFERENCES players(friend_code),
    FOREIGN KEY (to_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_friend_requests_to ON friend_requests(to_code, status);

CREATE TABLE IF NOT EXISTS google_account_links (
    google_sub TEXT PRIMARY KEY,
    friend_code TEXT NOT NULL UNIQUE REFERENCES players(friend_code),
    email TEXT,
    linked_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_google_links_friend ON google_account_links(friend_code);

CREATE TABLE IF NOT EXISTS used_nonces (
    friend_code TEXT NOT NULL,
    nonce TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    PRIMARY KEY (friend_code, nonce)
);

CREATE INDEX IF NOT EXISTS idx_used_nonces_expires ON used_nonces(expires_at);
