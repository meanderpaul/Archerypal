package store

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"
)

type BetaInvite struct {
	Code       string
	Note       string
	RedeemedBy sql.NullString
	RedeemedAt sql.NullInt64
	ExpiresAt  sql.NullInt64
}

func (s *Store) RedeemBetaInvite(ctx context.Context, code, friendCode string) error {
	code = strings.TrimSpace(strings.ToUpper(code))
	if code == "" {
		return ErrNotFound
	}
	now := time.Now().UTC().Unix()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	var redeemedBy sql.NullString
	var expiresAt sql.NullInt64
	err = tx.QueryRowContext(ctx, `
		SELECT redeemed_by, expires_at FROM beta_invites WHERE code = ?`, code).
		Scan(&redeemedBy, &expiresAt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrNotFound
		}
		return err
	}
	if redeemedBy.Valid && redeemedBy.String != "" {
		return ErrConflict
	}
	if expiresAt.Valid && expiresAt.Int64 > 0 && now >= expiresAt.Int64 {
		return ErrNotFound
	}
	res, err := tx.ExecContext(ctx, `
		UPDATE beta_invites SET redeemed_by = ?, redeemed_at = ?
		WHERE code = ? AND (redeemed_by IS NULL OR redeemed_by = '')`,
		friendCode, now, code)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return ErrConflict
	}
	return tx.Commit()
}

func (s *Store) CreateBetaInvite(ctx context.Context, code, note string, expiresAt *time.Time) error {
	code = strings.TrimSpace(strings.ToUpper(code))
	if code == "" {
		return errors.New("code required")
	}
	var exp sql.NullInt64
	if expiresAt != nil {
		exp = sql.NullInt64{Int64: expiresAt.Unix(), Valid: true}
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO beta_invites (code, note, expires_at) VALUES (?, ?, ?)`,
		code, strings.TrimSpace(note), exp)
	if err != nil && strings.Contains(err.Error(), "UNIQUE") {
		return ErrConflict
	}
	return err
}
