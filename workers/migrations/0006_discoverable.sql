-- Name search opt-out. Players are findable by display name unless they turn this off.

ALTER TABLE players ADD COLUMN discoverable INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_friend_requests_from ON friend_requests(from_code, status);
