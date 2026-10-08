package store

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

var (
	ErrNotFound      = errors.New("not found")
	ErrConflict      = errors.New("conflict")
	ErrFriendOrder   = errors.New("invalid friend pair order")
)

type Player struct {
	FriendCode       string
	DisplayName      string
	DeviceSecretHash []byte
	FCMToken         sql.NullString
	CreatedAt        time.Time
	UpdatedAt        time.Time
}

type PlayerStats struct {
	FriendCode    string `json:"friendCode"`
	DisplayName   string `json:"displayName"`
	MatchesPlayed int    `json:"matchesPlayed"`
	MatchesWon    int    `json:"matchesWon"`
	TotalPoints   int    `json:"totalPoints"`
	UpdatedAt     int64  `json:"updatedAt"`
}

type Room struct {
	ID               string
	HostFriendCode   string
	JoinerFriendCode string
	Status           string
	CreatedAt        time.Time
	ExpiresAt        time.Time
}

type CompleteMatchInput struct {
	MatchID          string
	RoomID           string
	CompletedAt      time.Time
	ScoringType      string
	TargetCount      int
	WinnerFriendCode string
	Players          []CompletePlayer
}

type CompletePlayer struct {
	FriendCode  string `json:"friendCode"`
	DisplayName string `json:"displayName"`
	Total       int    `json:"total"`
}

type Store struct {
	db *sql.DB
}

func Open(dbPath string) (*Store, error) {
	if err := os.MkdirAll(filepath.Dir(dbPath), 0o755); err != nil && !os.IsExist(err) {
		return nil, err
	}
	dsn := fmt.Sprintf("file:%s?_pragma=foreign_keys(1)&_pragma=journal_mode(WAL)", dbPath)
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)
	return &Store{db: db}, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) Migrate(migrationsDir string) error {
	if _, err := s.db.Exec(`CREATE TABLE IF NOT EXISTS schema_migrations (
		name TEXT PRIMARY KEY,
		applied_at INTEGER NOT NULL
	)`); err != nil {
		return fmt.Errorf("ensure schema_migrations: %w", err)
	}
	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("read migrations dir: %w", err)
	}
	var names []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			names = append(names, e.Name())
		}
	}
	sort.Strings(names)
	for _, name := range names {
		var applied int
		if err := s.db.QueryRow(`SELECT COUNT(*) FROM schema_migrations WHERE name = ?`, name).Scan(&applied); err != nil {
			return fmt.Errorf("check migration %s: %w", name, err)
		}
		if applied > 0 {
			continue
		}
		b, err := os.ReadFile(filepath.Join(migrationsDir, name))
		if err != nil {
			return fmt.Errorf("read migration %s: %w", name, err)
		}
		tx, err := s.db.Begin()
		if err != nil {
			return err
		}
		if _, err := tx.Exec(string(b)); err != nil {
			tx.Rollback()
			return fmt.Errorf("apply migration %s: %w", name, err)
		}
		if _, err := tx.Exec(`INSERT INTO schema_migrations (name, applied_at) VALUES (?, ?)`,
			name, time.Now().UTC().Unix()); err != nil {
			tx.Rollback()
			return fmt.Errorf("record migration %s: %w", name, err)
		}
		if err := tx.Commit(); err != nil {
			return err
		}
	}
	return nil
}

func HashDeviceSecret(secret string) []byte {
	sum := sha256.Sum256([]byte(secret))
	return sum[:]
}

func (s *Store) RegisterPlayer(ctx context.Context, friendCode, displayName, deviceSecret string, fcmToken *string) error {
	now := time.Now().UTC()
	hash := HashDeviceSecret(deviceSecret)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	res, err := tx.ExecContext(ctx, `
		INSERT INTO players (friend_code, display_name, device_secret_hash, fcm_token, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?)`,
		friendCode, displayName, hash, nullString(fcmToken), now.Unix(), now.Unix())
	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE") {
			return ErrConflict
		}
		return err
	}
	if n, _ := res.RowsAffected(); n != 1 {
		return ErrConflict
	}
	_, err = tx.ExecContext(ctx, `
		INSERT INTO player_stats (friend_code, display_name, matches_played, matches_won, total_points, updated_at)
		VALUES (?, ?, 0, 0, 0, ?)`, friendCode, displayName, now.Unix())
	if err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) GetPlayer(ctx context.Context, friendCode string) (*Player, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT friend_code, display_name, device_secret_hash, fcm_token, created_at, updated_at
		FROM players WHERE friend_code = ?`, friendCode)
	var p Player
	var created, updated int64
	var fcm sql.NullString
	if err := row.Scan(&p.FriendCode, &p.DisplayName, &p.DeviceSecretHash, &fcm, &created, &updated); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	p.FCMToken = fcm
	p.CreatedAt = time.Unix(created, 0).UTC()
	p.UpdatedAt = time.Unix(updated, 0).UTC()
	return &p, nil
}

func (s *Store) UpdatePlayer(ctx context.Context, friendCode, displayName string, fcmToken *string) error {
	now := time.Now().UTC()
	var res sql.Result
	var err error
	if fcmToken == nil {
		// Preserve existing FCM token when the client only updates the display name.
		res, err = s.db.ExecContext(ctx, `
			UPDATE players SET display_name = ?, updated_at = ?
			WHERE friend_code = ?`, displayName, now.Unix(), friendCode)
	} else {
		res, err = s.db.ExecContext(ctx, `
			UPDATE players SET display_name = ?, fcm_token = ?, updated_at = ?
			WHERE friend_code = ?`, displayName, nullString(fcmToken), now.Unix(), friendCode)
	}
	if err != nil {
		return err
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return ErrNotFound
	}
	_, _ = s.db.ExecContext(ctx, `
		UPDATE player_stats SET display_name = ?, updated_at = ? WHERE friend_code = ?`,
		displayName, now.Unix(), friendCode)
	return nil
}

func canonicalPair(a, b string) (string, string, error) {
	if a == b {
		return "", "", errors.New("cannot friend self")
	}
	if a < b {
		return a, b, nil
	}
	return b, a, nil
}

func (s *Store) AddFriendship(ctx context.Context, selfCode, theirCode string) (*Player, error) {
	a, b, err := canonicalPair(selfCode, theirCode)
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC()
	_, err = s.db.ExecContext(ctx, `
		INSERT OR IGNORE INTO friendships (player_a, player_b, created_at) VALUES (?, ?, ?)`,
		a, b, now.Unix())
	if err != nil {
		return nil, err
	}
	return s.GetPlayer(ctx, theirCode)
}

const PlusSKU = "1plus"
const PlusLifetimeSKU = "lifetime"

func IsPlusSKU(sku string) bool {
	return sku == PlusSKU || sku == PlusLifetimeSKU
}

type FriendSummary struct {
	FriendCode  string `json:"friendCode"`
	DisplayName string `json:"displayName"`
}

func (s *Store) ListFriends(ctx context.Context, friendCode string) ([]FriendSummary, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT p.friend_code, p.display_name
		FROM friendships f
		JOIN players p ON p.friend_code = CASE WHEN f.player_a = ? THEN f.player_b ELSE f.player_a END
		WHERE f.player_a = ? OR f.player_b = ?
		ORDER BY p.display_name COLLATE NOCASE`,
		friendCode, friendCode, friendCode)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []FriendSummary
	for rows.Next() {
		var f FriendSummary
		if err := rows.Scan(&f.FriendCode, &f.DisplayName); err != nil {
			return nil, err
		}
		out = append(out, f)
	}
	return out, rows.Err()
}

func (s *Store) UpsertEntitlement(ctx context.Context, friendCode, sku string, tokenHash []byte, expiresAt *int64) error {
	now := time.Now().UTC()
	var exp any
	if expiresAt != nil {
		exp = *expiresAt
	}
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO entitlements (friend_code, sku, expires_at, play_token_hash, updated_at)
		VALUES (?, ?, ?, ?, ?)
		ON CONFLICT(friend_code, sku) DO UPDATE SET
			expires_at = excluded.expires_at,
			play_token_hash = excluded.play_token_hash,
			updated_at = excluded.updated_at`,
		friendCode, sku, exp, tokenHash, now.Unix())
	return err
}

func (s *Store) HasActiveEntitlement(ctx context.Context, friendCode, sku string) (bool, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT expires_at FROM entitlements WHERE friend_code = ? AND sku = ?`,
		friendCode, sku)
	var expires sql.NullInt64
	if err := row.Scan(&expires); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, nil
		}
		return false, err
	}
	if !expires.Valid || expires.Int64 == 0 {
		return true, nil
	}
	return time.Now().Unix() < expires.Int64, nil
}

// HasActivePlus is true when monthly or lifetime Plus entitlement is active.
func (s *Store) HasActivePlus(ctx context.Context, friendCode string) (bool, error) {
	ok, err := s.HasActiveEntitlement(ctx, friendCode, PlusSKU)
	if err != nil || ok {
		return ok, err
	}
	return s.HasActiveEntitlement(ctx, friendCode, PlusLifetimeSKU)
}

func (s *Store) ListFriendCodes(ctx context.Context, friendCode string) ([]string, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT CASE WHEN player_a = ? THEN player_b ELSE player_a END AS other
		FROM friendships WHERE player_a = ? OR player_b = ?`,
		friendCode, friendCode, friendCode)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []string
	for rows.Next() {
		var other string
		if err := rows.Scan(&other); err != nil {
			return nil, err
		}
		out = append(out, other)
	}
	return out, rows.Err()
}

func (s *Store) CreateRoom(ctx context.Context, roomID, hostFriendCode, joinerFriendCode string, expiresAt time.Time) error {
	now := time.Now().UTC()
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO rooms (id, host_friend_code, joiner_friend_code, status, created_at, expires_at)
		VALUES (?, ?, ?, 'open', ?, ?)`,
		roomID, hostFriendCode, nullEmpty(joinerFriendCode), now.Unix(), expiresAt.Unix())
	return err
}

func (s *Store) GetRoom(ctx context.Context, roomID string) (*Room, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT id, host_friend_code, COALESCE(joiner_friend_code, ''), status, created_at, expires_at
		FROM rooms WHERE id = ?`, roomID)
	var r Room
	var created, expires int64
	if err := row.Scan(&r.ID, &r.HostFriendCode, &r.JoinerFriendCode, &r.Status, &created, &expires); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	r.CreatedAt = time.Unix(created, 0).UTC()
	r.ExpiresAt = time.Unix(expires, 0).UTC()
	return &r, nil
}

func (s *Store) CloseRoom(ctx context.Context, roomID string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE rooms SET status = 'closed' WHERE id = ?`, roomID)
	return err
}

func (s *Store) CountOpenRooms(ctx context.Context) (int, error) {
	var n int
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM rooms WHERE status = 'open'`).Scan(&n)
	return n, err
}

func (s *Store) CompleteMatch(ctx context.Context, in CompleteMatchInput) error {
	scoresJSON, err := json.Marshal(in.Players)
	if err != nil {
		return err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	_, err = tx.ExecContext(ctx, `
		INSERT INTO completed_matches (match_id, completed_at, scoring_type, target_count, winner_friend_code, scores_json, room_id)
		VALUES (?, ?, ?, ?, ?, ?, ?)`,
		in.MatchID, in.CompletedAt.Unix(), in.ScoringType, in.TargetCount, in.WinnerFriendCode, string(scoresJSON), in.RoomID)
	if err != nil {
		if strings.Contains(err.Error(), "UNIQUE") {
			return ErrConflict
		}
		return err
	}

	now := time.Now().UTC()
	for _, pl := range in.Players {
		won := 0
		if pl.FriendCode == in.WinnerFriendCode {
			won = 1
		}
		_, err = tx.ExecContext(ctx, `
			INSERT INTO player_stats (friend_code, display_name, matches_played, matches_won, total_points, updated_at)
			VALUES (?, ?, 1, ?, ?, ?)
			ON CONFLICT(friend_code) DO UPDATE SET
				display_name = excluded.display_name,
				matches_played = player_stats.matches_played + 1,
				matches_won = player_stats.matches_won + excluded.matches_won,
				total_points = player_stats.total_points + excluded.total_points,
				updated_at = excluded.updated_at`,
			pl.FriendCode, pl.DisplayName, won, pl.Total, now.Unix())
		if err != nil {
			return err
		}
	}

	_, err = tx.ExecContext(ctx, `UPDATE rooms SET status = 'closed' WHERE id = ?`, in.RoomID)
	if err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) GlobalLeaderboard(ctx context.Context, limit int) ([]PlayerStats, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT friend_code, display_name, matches_played, matches_won, total_points, updated_at
		FROM player_stats
		ORDER BY matches_won DESC, total_points DESC, updated_at DESC
		LIMIT ?`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanStatsRows(rows)
}

func (s *Store) FriendsLeaderboard(ctx context.Context, friendCode string) ([]PlayerStats, error) {
	codes, err := s.ListFriendCodes(ctx, friendCode)
	if err != nil {
		return nil, err
	}
	codes = append(codes, friendCode)
	sort.Strings(codes)
	placeholders := strings.Repeat("?,", len(codes))
	placeholders = strings.TrimSuffix(placeholders, ",")
	args := make([]any, len(codes))
	for i, c := range codes {
		args[i] = c
	}
	q := fmt.Sprintf(`
		SELECT friend_code, display_name, matches_played, matches_won, total_points, updated_at
		FROM player_stats WHERE friend_code IN (%s)
		ORDER BY matches_won DESC, total_points DESC`, placeholders)
	rows, err := s.db.QueryContext(ctx, q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanStatsRows(rows)
}

func scanStatsRows(rows *sql.Rows) ([]PlayerStats, error) {
	var out []PlayerStats
	for rows.Next() {
		var st PlayerStats
		var updated int64
		if err := rows.Scan(&st.FriendCode, &st.DisplayName, &st.MatchesPlayed, &st.MatchesWon, &st.TotalPoints, &updated); err != nil {
			return nil, err
		}
		st.UpdatedAt = updated
		out = append(out, st)
	}
	return out, rows.Err()
}

func LeaderboardETag(rows []PlayerStats) string {
	h := sha256.New()
	for _, r := range rows {
		fmt.Fprintf(h, "%s:%d:%d:%d:%d|", r.FriendCode, r.MatchesPlayed, r.MatchesWon, r.TotalPoints, r.UpdatedAt)
	}
	return hex.EncodeToString(h.Sum(nil))
}

func (s *Store) FCMTokenFor(ctx context.Context, friendCode string) (string, error) {
	row := s.db.QueryRowContext(ctx, `SELECT fcm_token FROM players WHERE friend_code = ?`, friendCode)
	var fcm sql.NullString
	if err := row.Scan(&fcm); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", ErrNotFound
		}
		return "", err
	}
	if !fcm.Valid || fcm.String == "" {
		return "", ErrNotFound
	}
	return fcm.String, nil
}

func nullString(p *string) sql.NullString {
	if p == nil || *p == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: *p, Valid: true}
}