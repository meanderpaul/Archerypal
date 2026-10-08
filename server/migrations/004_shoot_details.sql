-- Rich shoot metadata and public community shoots

ALTER TABLE shoots ADD COLUMN source_url TEXT;
ALTER TABLE shoots ADD COLUMN registration_url TEXT;
ALTER TABLE shoots ADD COLUMN event_type TEXT;
ALTER TABLE shoots ADD COLUMN is_public INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_shoots_public ON shoots(is_public);
