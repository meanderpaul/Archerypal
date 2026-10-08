-- Replay protection for device HMAC auth

CREATE TABLE IF NOT EXISTS used_nonces (
    friend_code TEXT NOT NULL,
    nonce TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    PRIMARY KEY (friend_code, nonce)
);

CREATE INDEX IF NOT EXISTS idx_used_nonces_expires ON used_nonces(expires_at);
