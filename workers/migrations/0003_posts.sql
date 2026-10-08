-- Text posts (image_key reserved for future R2 media)

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS posts (
    id TEXT PRIMARY KEY,
    author_code TEXT NOT NULL,
    body TEXT NOT NULL,
    image_key TEXT,
    is_public INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (author_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_posts_created ON posts(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_author ON posts(author_code);
