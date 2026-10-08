package store

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"
)

type Shoot struct {
	ID                string   `json:"id"`
	Title             string   `json:"title"`
	Description       string   `json:"description,omitempty"`
	LocationName      string   `json:"locationName"`
	Region            string   `json:"region,omitempty"`
	StartsAt          int64    `json:"startsAt"`
	EndsAt            *int64   `json:"endsAt,omitempty"`
	HostFriendCode    *string  `json:"hostFriendCode,omitempty"`
	HostDisplayName   *string  `json:"hostDisplayName,omitempty"`
	IsCurated         bool     `json:"isCurated"`
	IsPublic          bool     `json:"isPublic"`
	SourceURL         string   `json:"sourceUrl,omitempty"`
	RegistrationURL   string   `json:"registrationUrl,omitempty"`
	EventType         string   `json:"eventType,omitempty"`
	Status            string   `json:"status"`
	FriendsGoingCount int      `json:"friendsGoingCount"`
	ViewerIsGoing     bool     `json:"viewerIsGoing"`
	Latitude          *float64 `json:"latitude,omitempty"`
	Longitude         *float64 `json:"longitude,omitempty"`
	DistanceKm        *float64 `json:"distanceKm,omitempty"`
}

type ShootAttendee struct {
	FriendCode  string `json:"friendCode"`
	DisplayName string `json:"displayName"`
	IsFriend    bool   `json:"isFriend"`
}

type CreateShootInput struct {
	Title           string
	Description     string
	LocationName    string
	Region          string
	StartsAt        time.Time
	EndsAt          *time.Time
	HostCode        string
	IsPublic        bool
	SourceURL       string
	RegistrationURL string
	EventType       string
	Latitude        *float64
	Longitude       *float64
}

type CuratedShootInput struct {
	ID              string
	Title           string
	Description     string
	LocationName    string
	Region          string
	StartsAt        time.Time
	EndsAt          *time.Time
	SourceURL       string
	RegistrationURL string
	EventType       string
	Latitude        *float64
	Longitude       *float64
}

func newShootID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}

func scanShootRow(
	id, title, description, locationName, region string,
	startsAt int64, ends sql.NullInt64,
	hostCode sql.NullString, hostName string,
	isCurated, isPublic int, status string,
	sourceURL, registrationURL, eventType sql.NullString,
	lat, lng sql.NullFloat64,
) Shoot {
	var sh Shoot
	sh.ID = id
	sh.Title = title
	sh.Description = description
	sh.LocationName = locationName
	sh.Region = region
	sh.StartsAt = startsAt
	if ends.Valid {
		v := ends.Int64
		sh.EndsAt = &v
	}
	if hostCode.Valid {
		c := hostCode.String
		sh.HostFriendCode = &c
		if hostName != "" {
			sh.HostDisplayName = &hostName
		}
	}
	sh.IsCurated = isCurated == 1
	sh.IsPublic = isPublic == 1
	sh.Status = status
	if sourceURL.Valid {
		sh.SourceURL = sourceURL.String
	}
	if registrationURL.Valid {
		sh.RegistrationURL = registrationURL.String
	}
	if eventType.Valid {
		sh.EventType = eventType.String
	}
	if lat.Valid {
		v := lat.Float64
		sh.Latitude = &v
	}
	if lng.Valid {
		v := lng.Float64
		sh.Longitude = &v
	}
	return sh
}

const shootSelectCols = `
		s.id, s.title, COALESCE(s.description, ''), s.location_name, COALESCE(s.region, ''),
		s.starts_at, s.ends_at, s.host_friend_code, s.is_curated, s.is_public, s.status,
		COALESCE(p.display_name, ''),
		s.source_url, s.registration_url, s.event_type,
		s.latitude, s.longitude`

func (s *Store) CreateShoot(ctx context.Context, in CreateShootInput) (*Shoot, error) {
	id, err := newShootID()
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC()
	var ends any
	if in.EndsAt != nil {
		ends = in.EndsAt.UnixMilli()
	}
	isPublic := 0
	if in.IsPublic {
		isPublic = 1
	}
	_, err = s.db.ExecContext(ctx, `
		INSERT INTO shoots (id, title, description, location_name, region, starts_at, ends_at,
			host_friend_code, is_curated, is_public, status, created_at,
			source_url, registration_url, event_type, latitude, longitude)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'scheduled', ?, ?, ?, ?, ?, ?)`,
		id, in.Title, nullEmpty(in.Description), in.LocationName, nullEmpty(in.Region),
		in.StartsAt.UnixMilli(), ends, in.HostCode, isPublic, now.Unix(),
		nullEmpty(in.SourceURL), nullEmpty(in.RegistrationURL), nullEmpty(in.EventType),
		nullFloat(in.Latitude), nullFloat(in.Longitude))
	if err != nil {
		return nil, err
	}
	return s.GetShootForViewer(ctx, id, in.HostCode)
}

func (s *Store) CreateCuratedShoot(ctx context.Context, in CuratedShootInput) error {
	now := time.Now().UTC()
	var ends any
	if in.EndsAt != nil {
		ends = in.EndsAt.UnixMilli()
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT OR REPLACE INTO shoots (id, title, description, location_name, region, starts_at, ends_at,
			host_friend_code, is_curated, is_public, status, created_at,
			source_url, registration_url, event_type, latitude, longitude)
		VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 1, 0, 'scheduled', ?, ?, ?, ?, ?, ?)`,
		in.ID, in.Title, nullEmpty(in.Description), in.LocationName, nullEmpty(in.Region),
		in.StartsAt.UnixMilli(), ends, now.Unix(),
		nullEmpty(in.SourceURL), nullEmpty(in.RegistrationURL), nullEmpty(in.EventType),
		nullFloat(in.Latitude), nullFloat(in.Longitude))
	return err
}

func (s *Store) CancelShootsNotIn(ctx context.Context, ids []string) (int, error) {
	if len(ids) == 0 {
		res, err := s.db.ExecContext(ctx, `
			UPDATE shoots SET status = 'cancelled'
			WHERE is_curated = 1 AND status = 'scheduled'`)
		if err != nil {
			return 0, err
		}
		n, _ := res.RowsAffected()
		return int(n), nil
	}
	placeholders := strings.Repeat("?,", len(ids))
	placeholders = strings.TrimSuffix(placeholders, ",")
	args := make([]any, len(ids))
	for i, id := range ids {
		args[i] = id
	}
	q := fmt.Sprintf(`
		UPDATE shoots SET status = 'cancelled'
		WHERE is_curated = 1 AND status = 'scheduled' AND id NOT IN (%s)`, placeholders)
	res, err := s.db.ExecContext(ctx, q, args...)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return int(n), nil
}

func (s *Store) ListVisibleShoots(ctx context.Context, viewerCode string, fromMs, toMs int64) ([]Shoot, error) {
	friendCodes, err := s.ListFriendCodes(ctx, viewerCode)
	if err != nil {
		return nil, err
	}
	friendSet := make(map[string]struct{}, len(friendCodes))
	for _, c := range friendCodes {
		friendSet[c] = struct{}{}
	}

	rows, err := s.db.QueryContext(ctx, `
		SELECT`+shootSelectCols+`
		FROM shoots s
		LEFT JOIN players p ON p.friend_code = s.host_friend_code
		WHERE s.status = 'scheduled'
		  AND s.starts_at >= ?
		  AND (? = 0 OR s.starts_at <= ?)
		ORDER BY s.starts_at ASC`, fromMs, toMs, toMs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var candidates []Shoot
	for rows.Next() {
		var ends sql.NullInt64
		var hostCode sql.NullString
		var hostName string
		var isCurated, isPublic int
		var sourceURL, registrationURL, eventType sql.NullString
		var lat, lng sql.NullFloat64
		var sh Shoot
		if err := rows.Scan(&sh.ID, &sh.Title, &sh.Description, &sh.LocationName, &sh.Region,
			&sh.StartsAt, &ends, &hostCode, &isCurated, &isPublic, &sh.Status, &hostName,
			&sourceURL, &registrationURL, &eventType, &lat, &lng); err != nil {
			return nil, err
		}
		sh = scanShootRow(sh.ID, sh.Title, sh.Description, sh.LocationName, sh.Region,
			sh.StartsAt, ends, hostCode, hostName, isCurated, isPublic, sh.Status,
			sourceURL, registrationURL, eventType, lat, lng)
		candidates = append(candidates, sh)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	var visible []Shoot
	for _, sh := range candidates {
		if sh.IsCurated || sh.IsPublic {
			visible = append(visible, sh)
			continue
		}
		if sh.HostFriendCode != nil {
			if *sh.HostFriendCode == viewerCode {
				visible = append(visible, sh)
				continue
			}
			if _, ok := friendSet[*sh.HostFriendCode]; ok {
				visible = append(visible, sh)
				continue
			}
		}
		attendees, err := s.listShootAttendeeCodes(ctx, sh.ID)
		if err != nil {
			return nil, err
		}
		for _, code := range attendees {
			if _, ok := friendSet[code]; ok {
				visible = append(visible, sh)
				break
			}
		}
	}

	for i := range visible {
		count, going, err := s.shootFriendStats(ctx, visible[i].ID, viewerCode, friendSet)
		if err != nil {
			return nil, err
		}
		visible[i].FriendsGoingCount = count
		visible[i].ViewerIsGoing = going
	}
	return visible, nil
}

func (s *Store) GetShootForViewer(ctx context.Context, shootID, viewerCode string) (*Shoot, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT`+shootSelectCols+`
		FROM shoots s
		LEFT JOIN players p ON p.friend_code = s.host_friend_code
		WHERE s.id = ?`, shootID)
	var ends sql.NullInt64
	var hostCode sql.NullString
	var hostName string
	var isCurated, isPublic int
	var sourceURL, registrationURL, eventType sql.NullString
	var lat, lng sql.NullFloat64
	var sh Shoot
	if err := row.Scan(&sh.ID, &sh.Title, &sh.Description, &sh.LocationName, &sh.Region,
		&sh.StartsAt, &ends, &hostCode, &isCurated, &isPublic, &sh.Status, &hostName,
		&sourceURL, &registrationURL, &eventType, &lat, &lng); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	sh = scanShootRow(sh.ID, sh.Title, sh.Description, sh.LocationName, sh.Region,
		sh.StartsAt, ends, hostCode, hostName, isCurated, isPublic, sh.Status,
		sourceURL, registrationURL, eventType, lat, lng)

	friendCodes, err := s.ListFriendCodes(ctx, viewerCode)
	if err != nil {
		return nil, err
	}
	friendSet := make(map[string]struct{}, len(friendCodes))
	for _, c := range friendCodes {
		friendSet[c] = struct{}{}
	}

	if !sh.IsCurated && !sh.IsPublic {
		visible := false
		if sh.HostFriendCode != nil {
			if *sh.HostFriendCode == viewerCode {
				visible = true
			} else if _, ok := friendSet[*sh.HostFriendCode]; ok {
				visible = true
			}
		}
		if !visible {
			attendees, err := s.listShootAttendeeCodes(ctx, sh.ID)
			if err != nil {
				return nil, err
			}
			for _, code := range attendees {
				if _, ok := friendSet[code]; ok {
					visible = true
					break
				}
			}
		}
		if !visible {
			return nil, ErrNotFound
		}
	}

	count, going, err := s.shootFriendStats(ctx, sh.ID, viewerCode, friendSet)
	if err != nil {
		return nil, err
	}
	sh.FriendsGoingCount = count
	sh.ViewerIsGoing = going
	return &sh, nil
}

func (s *Store) shootFriendStats(ctx context.Context, shootID, viewerCode string, friendSet map[string]struct{}) (int, bool, error) {
	codes, err := s.listShootAttendeeCodes(ctx, shootID)
	if err != nil {
		return 0, false, err
	}
	count := 0
	going := false
	for _, code := range codes {
		if code == viewerCode {
			going = true
		}
		if _, ok := friendSet[code]; ok {
			count++
		}
	}
	return count, going, nil
}

func (s *Store) listShootAttendeeCodes(ctx context.Context, shootID string) ([]string, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT friend_code FROM shoot_rsvps WHERE shoot_id = ?`, shootID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []string
	for rows.Next() {
		var code string
		if err := rows.Scan(&code); err != nil {
			return nil, err
		}
		out = append(out, code)
	}
	return out, rows.Err()
}

func (s *Store) SetShootRsvp(ctx context.Context, shootID, friendCode string) error {
	if _, err := s.GetShootForViewer(ctx, shootID, friendCode); err != nil {
		return err
	}
	now := time.Now().UTC()
	_, err := s.db.ExecContext(ctx, `
		INSERT OR REPLACE INTO shoot_rsvps (shoot_id, friend_code, created_at)
		VALUES (?, ?, ?)`, shootID, friendCode, now.Unix())
	return err
}

func (s *Store) ClearShootRsvp(ctx context.Context, shootID, friendCode string) error {
	res, err := s.db.ExecContext(ctx, `
		DELETE FROM shoot_rsvps WHERE shoot_id = ? AND friend_code = ?`, shootID, friendCode)
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *Store) ListShootAttendees(ctx context.Context, shootID, viewerCode string) ([]ShootAttendee, error) {
	if _, err := s.GetShootForViewer(ctx, shootID, viewerCode); err != nil {
		return nil, err
	}
	friendCodes, err := s.ListFriendCodes(ctx, viewerCode)
	if err != nil {
		return nil, err
	}
	friendSet := make(map[string]struct{}, len(friendCodes))
	for _, c := range friendCodes {
		friendSet[c] = struct{}{}
	}

	rows, err := s.db.QueryContext(ctx, `
		SELECT r.friend_code, p.display_name
		FROM shoot_rsvps r
		JOIN players p ON p.friend_code = r.friend_code
		WHERE r.shoot_id = ?
		ORDER BY p.display_name COLLATE NOCASE`, shootID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []ShootAttendee
	for rows.Next() {
		var a ShootAttendee
		if err := rows.Scan(&a.FriendCode, &a.DisplayName); err != nil {
			return nil, err
		}
		_, a.IsFriend = friendSet[a.FriendCode]
		if a.FriendCode == viewerCode {
			a.IsFriend = true
		}
		out = append(out, a)
	}
	return out, rows.Err()
}

func nullEmpty(s string) any {
	if strings.TrimSpace(s) == "" {
		return nil
	}
	return s
}

func nullFloat(v *float64) any {
	if v == nil {
		return nil
	}
	return *v
}
