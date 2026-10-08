-- Google account links for device recovery

CREATE TABLE IF NOT EXISTS google_account_links (
    google_sub TEXT PRIMARY KEY,
    friend_code TEXT NOT NULL UNIQUE REFERENCES players(friend_code),
    email TEXT,
    linked_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_google_links_friend ON google_account_links(friend_code);
