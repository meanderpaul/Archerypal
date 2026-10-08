-- Friend requests for standings/suggestions adds

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
