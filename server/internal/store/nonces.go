package store

import (
	"context"
	"strings"
	"time"
)

func (s *Store) ConsumeNonce(ctx context.Context, friendCode, nonce string, expiresAt time.Time) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO used_nonces (friend_code, nonce, expires_at)
		VALUES (?, ?, ?)`,
		friendCode, nonce, expiresAt.Unix())
	if err != nil && strings.Contains(err.Error(), "UNIQUE") {
		return ErrConflict
	}
	return err
}

func (s *Store) PurgeExpiredNonces(ctx context.Context) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM used_nonces WHERE expires_at < ?`, time.Now().Unix())
	return err
}
