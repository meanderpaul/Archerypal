package store

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

type FriendRequest struct {
	FromCode        string `json:"fromCode"`
	FromDisplayName string `json:"fromDisplayName"`
	ToCode          string `json:"toCode"`
	ToDisplayName   string `json:"toDisplayName,omitempty"`
	Status          string `json:"status"`
	CreatedAt       int64  `json:"createdAt"`
}

func (s *Store) SendFriendRequest(ctx context.Context, fromCode, toCode string) error {
	if fromCode == toCode {
		return errors.New("cannot request self")
	}
	if _, err := s.GetPlayer(ctx, toCode); err != nil {
		return err
	}
	a, b, err := canonicalPair(fromCode, toCode)
	if err != nil {
		return err
	}
	var exists int
	err = s.db.QueryRowContext(ctx, `
		SELECT COUNT(*) FROM friendships WHERE player_a = ? AND player_b = ?`, a, b).Scan(&exists)
	if err != nil {
		return err
	}
	if exists > 0 {
		return ErrConflict
	}
	now := time.Now().UTC()
	_, err = s.db.ExecContext(ctx, `
		INSERT INTO friend_requests (from_code, to_code, status, created_at, updated_at)
		VALUES (?, ?, 'pending', ?, ?)
		ON CONFLICT(from_code, to_code) DO UPDATE SET
			status = 'pending',
			updated_at = excluded.updated_at`,
		fromCode, toCode, now.Unix(), now.Unix())
	if err != nil {
		return err
	}
	return nil
}

func (s *Store) ListPendingFriendRequests(ctx context.Context, toCode string) ([]FriendRequest, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT fr.from_code, pf.display_name, fr.to_code, fr.status, fr.created_at
		FROM friend_requests fr
		JOIN players pf ON pf.friend_code = fr.from_code
		WHERE fr.to_code = ? AND fr.status = 'pending'
		ORDER BY fr.created_at DESC`, toCode)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []FriendRequest
	for rows.Next() {
		var r FriendRequest
		if err := rows.Scan(&r.FromCode, &r.FromDisplayName, &r.ToCode, &r.Status, &r.CreatedAt); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

func (s *Store) AcceptFriendRequest(ctx context.Context, toCode, fromCode string) (*Player, error) {
	res, err := s.db.ExecContext(ctx, `
		UPDATE friend_requests SET status = 'accepted', updated_at = ?
		WHERE from_code = ? AND to_code = ? AND status = 'pending'`,
		time.Now().UTC().Unix(), fromCode, toCode)
	if err != nil {
		return nil, err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return nil, ErrNotFound
	}
	return s.AddFriendship(ctx, toCode, fromCode)
}

func (s *Store) DeclineFriendRequest(ctx context.Context, toCode, fromCode string) error {
	res, err := s.db.ExecContext(ctx, `
		UPDATE friend_requests SET status = 'declined', updated_at = ?
		WHERE from_code = ? AND to_code = ? AND status = 'pending'`,
		time.Now().UTC().Unix(), fromCode, toCode)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) AreFriends(ctx context.Context, a, b string) (bool, error) {
	pa, pb, err := canonicalPair(a, b)
	if err != nil {
		return false, err
	}
	var n int
	err = s.db.QueryRowContext(ctx, `
		SELECT COUNT(*) FROM friendships WHERE player_a = ? AND player_b = ?`, pa, pb).Scan(&n)
	if err != nil {
		return false, err
	}
	return n > 0, nil
}

func (s *Store) HasPendingRequest(ctx context.Context, fromCode, toCode string) (bool, error) {
	var status string
	err := s.db.QueryRowContext(ctx, `
		SELECT status FROM friend_requests WHERE from_code = ? AND to_code = ?`,
		fromCode, toCode).Scan(&status)
	if errors.Is(err, sql.ErrNoRows) {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return status == "pending", nil
}
