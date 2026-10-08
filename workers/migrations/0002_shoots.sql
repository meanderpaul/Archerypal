-- Shoots calendar + RSVP (ported from server migrations 002/004/005)

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS shoots (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    location_name TEXT NOT NULL,
    region TEXT,
    starts_at INTEGER NOT NULL,
    ends_at INTEGER,
    host_friend_code TEXT,
    is_curated INTEGER NOT NULL DEFAULT 0,
    is_public INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'scheduled',
    source_url TEXT,
    registration_url TEXT,
    event_type TEXT,
    latitude REAL,
    longitude REAL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (host_friend_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_shoots_starts_at ON shoots(starts_at);
CREATE INDEX IF NOT EXISTS idx_shoots_curated ON shoots(is_curated);
CREATE INDEX IF NOT EXISTS idx_shoots_public ON shoots(is_public);
CREATE INDEX IF NOT EXISTS idx_shoots_coords ON shoots(latitude, longitude);

CREATE TABLE IF NOT EXISTS shoot_rsvps (
    shoot_id TEXT NOT NULL,
    friend_code TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (shoot_id, friend_code),
    FOREIGN KEY (shoot_id) REFERENCES shoots(id),
    FOREIGN KEY (friend_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_shoot_rsvps_friend ON shoot_rsvps(friend_code);
