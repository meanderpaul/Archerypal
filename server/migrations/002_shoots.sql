-- Shoots calendar and RSVP

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
    status TEXT NOT NULL DEFAULT 'scheduled',
    created_at INTEGER NOT NULL,
    FOREIGN KEY (host_friend_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_shoots_starts_at ON shoots(starts_at);
CREATE INDEX IF NOT EXISTS idx_shoots_curated ON shoots(is_curated);

CREATE TABLE IF NOT EXISTS shoot_rsvps (
    shoot_id TEXT NOT NULL,
    friend_code TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (shoot_id, friend_code),
    FOREIGN KEY (shoot_id) REFERENCES shoots(id),
    FOREIGN KEY (friend_code) REFERENCES players(friend_code)
);

CREATE INDEX IF NOT EXISTS idx_shoot_rsvps_friend ON shoot_rsvps(friend_code);

-- Example regional shoot (edit dates/locations on deploy)
INSERT OR IGNORE INTO shoots (
    id, title, description, location_name, region, starts_at, ends_at,
    host_friend_code, is_curated, status, created_at
) VALUES (
    'curated-tri-county-3d-2026',
    'Tri-County 3D Shoot',
    'Regional 3D archery shoot — check local club for registration details.',
    'Tri-County Archery Club',
    'Tri-County',
    1751328000000,
    1751414400000,
    NULL,
    1,
    'scheduled',
    1750000000
);
