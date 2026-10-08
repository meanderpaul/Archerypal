package store

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"
)

type GoogleAccountLink struct {
	GoogleSub  string
	FriendCode string
	Email      string
	LinkedAt   time.Time
}

func (s *Store) LinkGoogleAccount(ctx context.Context, sub, friendCode, email string) error {
	now := time.Now().UTC()
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO google_account_links (google_sub, friend_code, email, linked_at)
		VALUES (?, ?, ?, ?)`,
		sub, friendCode, strings.TrimSpace(email), now.Unix())
	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE") {
			return ErrConflict
		}
		return err
	}
	return nil
}

func (s *Store) GetGoogleLinkBySub(ctx context.Context, sub string) (*GoogleAccountLink, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT google_sub, friend_code, COALESCE(email, ''), linked_at
		FROM google_account_links WHERE google_sub = ?`, sub)
	var link GoogleAccountLink
	var linked int64
	if err := row.Scan(&link.GoogleSub, &link.FriendCode, &link.Email, &linked); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	link.LinkedAt = time.Unix(linked, 0).UTC()
	return &link, nil
}

func (s *Store) GetGoogleLinkByFriendCode(ctx context.Context, friendCode string) (*GoogleAccountLink, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT google_sub, friend_code, COALESCE(email, ''), linked_at
		FROM google_account_links WHERE friend_code = ?`, friendCode)
	var link GoogleAccountLink
	var linked int64
	if err := row.Scan(&link.GoogleSub, &link.FriendCode, &link.Email, &linked); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	link.LinkedAt = time.Unix(linked, 0).UTC()
	return &link, nil
}

func (s *Store) RotateDeviceSecret(ctx context.Context, friendCode, newSecret string) error {
	now := time.Now().UTC()
	hash := HashDeviceSecret(newSecret)
	res, err := s.db.ExecContext(ctx, `
		UPDATE players SET device_secret_hash = ?, updated_at = ?
		WHERE friend_code = ?`, hash, now.Unix(), friendCode)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return ErrNotFound
	}
	return nil
}
