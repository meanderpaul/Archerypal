package store

import (
	"context"
	"database/sql"
	"errors"
	"time"
)

type PlayerProfile struct {
	FriendCode    string `json:"friendCode"`
	DisplayName   string `json:"displayName"`
	Bio           string `json:"bio,omitempty"`
	Club          string `json:"club,omitempty"`
	BowType       string `json:"bowType,omitempty"`
	HomeRange     string `json:"homeRange,omitempty"`
	LocationLabel string `json:"locationLabel,omitempty"`
	UpdatedAt     int64  `json:"updatedAt"`
}

type ProfileInput struct {
	Bio           string
	Club          string
	BowType       string
	HomeRange     string
	LocationLabel string
}

func (s *Store) GetProfile(ctx context.Context, friendCode string) (*PlayerProfile, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT p.friend_code, p.display_name,
			COALESCE(pr.bio, ''), COALESCE(pr.club, ''), COALESCE(pr.bow_type, ''),
			COALESCE(pr.home_range, ''), COALESCE(pr.location_label, ''),
			COALESCE(pr.updated_at, 0)
		FROM players p
		LEFT JOIN player_profiles pr ON pr.friend_code = p.friend_code
		WHERE p.friend_code = ?`, friendCode)
	var prof PlayerProfile
	if err := row.Scan(&prof.FriendCode, &prof.DisplayName,
		&prof.Bio, &prof.Club, &prof.BowType, &prof.HomeRange, &prof.LocationLabel, &prof.UpdatedAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	return &prof, nil
}

func (s *Store) GetProfileForViewer(ctx context.Context, targetCode, viewerCode string) (*PlayerProfile, error) {
	if targetCode != viewerCode {
		friends, err := s.AreFriends(ctx, viewerCode, targetCode)
		if err != nil {
			return nil, err
		}
		if !friends {
			return nil, ErrNotFound
		}
	}
	return s.GetProfile(ctx, targetCode)
}

func (s *Store) UpsertProfile(ctx context.Context, friendCode string, in ProfileInput) (*PlayerProfile, error) {
	if _, err := s.GetPlayer(ctx, friendCode); err != nil {
		return nil, err
	}
	now := time.Now().UTC().Unix()
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO player_profiles (friend_code, bio, club, bow_type, home_range, location_label, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(friend_code) DO UPDATE SET
			bio = excluded.bio,
			club = excluded.club,
			bow_type = excluded.bow_type,
			home_range = excluded.home_range,
			location_label = excluded.location_label,
			updated_at = excluded.updated_at`,
		friendCode,
		nullEmpty(in.Bio), nullEmpty(in.Club), nullEmpty(in.BowType),
		nullEmpty(in.HomeRange), nullEmpty(in.LocationLabel), now)
	if err != nil {
		return nil, err
	}
	return s.GetProfile(ctx, friendCode)
}

func (s *Store) ClearProfile(ctx context.Context, friendCode string) error {
	res, err := s.db.ExecContext(ctx, `DELETE FROM player_profiles WHERE friend_code = ?`, friendCode)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return nil
	}
	return nil
}
