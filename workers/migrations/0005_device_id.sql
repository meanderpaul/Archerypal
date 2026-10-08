-- Opaque device IDs for authenticated REST (hide friend codes on the wire).

PRAGMA foreign_keys = ON;

ALTER TABLE players ADD COLUMN device_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_players_device_id
  ON players(device_id)
  WHERE device_id IS NOT NULL;
