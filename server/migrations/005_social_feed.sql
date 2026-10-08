-- Social feed: shoot coordinates, optional profiles, posts

ALTER TABLE shoots ADD COLUMN latitude REAL;
ALTER TABLE shoots ADD COLUMN longitude REAL;
CREATE INDEX IF NOT EXISTS idx_shoots_coords ON shoots(latitude, longitude);

CREATE TABLE IF NOT EXISTS player_profiles (
    friend_code TEXT PRIMARY KEY,
    bio TEXT,
    club TEXT,
    bow_type TEXT,
    home_range TEXT,
    location_label TEXT,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY (friend_code) REFERENCES players(friend_code)
);

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
