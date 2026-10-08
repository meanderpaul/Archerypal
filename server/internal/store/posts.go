package store

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"errors"
	"time"
)

type Post struct {
	ID          string `json:"id"`
	AuthorCode  string `json:"authorCode"`
	AuthorName  string `json:"authorName"`
	Body        string `json:"body"`
	ImageKey    string `json:"imageKey,omitempty"`
	IsPublic    bool   `json:"isPublic"`
	CreatedAt   int64  `json:"createdAt"`
}

type CreatePostInput struct {
	AuthorCode string
	Body       string
	ImageKey   string
	IsPublic   bool
}

func newPostID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}

func (s *Store) CreatePost(ctx context.Context, in CreatePostInput) (*Post, error) {
	id, err := newPostID()
	if err != nil {
		return nil, err
	}
	now := time.Now().UTC().UnixMilli()
	isPublic := 0
	if in.IsPublic {
		isPublic = 1
	}
	_, err = s.db.ExecContext(ctx, `
		INSERT INTO posts (id, author_code, body, image_key, is_public, created_at)
		VALUES (?, ?, ?, ?, ?, ?)`,
		id, in.AuthorCode, in.Body, nullEmpty(in.ImageKey), isPublic, now)
	if err != nil {
		return nil, err
	}
	return s.GetPost(ctx, id)
}

func (s *Store) GetPost(ctx context.Context, id string) (*Post, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT p.id, p.author_code, pl.display_name, p.body,
			COALESCE(p.image_key, ''), p.is_public, p.created_at
		FROM posts p
		JOIN players pl ON pl.friend_code = p.author_code
		WHERE p.id = ?`, id)
	var post Post
	var imageKey string
	var isPublic int
	if err := row.Scan(&post.ID, &post.AuthorCode, &post.AuthorName, &post.Body,
		&imageKey, &isPublic, &post.CreatedAt); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	post.ImageKey = imageKey
	post.IsPublic = isPublic == 1
	return &post, nil
}

func (s *Store) ListVisiblePosts(ctx context.Context, viewerCode string, sinceMs int64) ([]Post, error) {
	friendCodes, err := s.ListFriendCodes(ctx, viewerCode)
	if err != nil {
		return nil, err
	}
	friendSet := make(map[string]struct{}, len(friendCodes)+1)
	friendSet[viewerCode] = struct{}{}
	for _, c := range friendCodes {
		friendSet[c] = struct{}{}
	}

	rows, err := s.db.QueryContext(ctx, `
		SELECT p.id, p.author_code, pl.display_name, p.body,
			COALESCE(p.image_key, ''), p.is_public, p.created_at
		FROM posts p
		JOIN players pl ON pl.friend_code = p.author_code
		WHERE (? = 0 OR p.created_at >= ?)
		ORDER BY p.created_at DESC
		LIMIT 200`, sinceMs, sinceMs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []Post
	for rows.Next() {
		var post Post
		var imageKey string
		var isPublic int
		if err := rows.Scan(&post.ID, &post.AuthorCode, &post.AuthorName, &post.Body,
			&imageKey, &isPublic, &post.CreatedAt); err != nil {
			return nil, err
		}
		post.ImageKey = imageKey
		post.IsPublic = isPublic == 1
		if post.IsPublic {
			out = append(out, post)
			continue
		}
		if _, ok := friendSet[post.AuthorCode]; ok {
			out = append(out, post)
		}
	}
	return out, rows.Err()
}

func (s *Store) CanViewMediaByKey(ctx context.Context, viewerCode, imageKey string) (bool, error) {
	row := s.db.QueryRowContext(ctx, `
		SELECT author_code, is_public FROM posts WHERE image_key = ?`, imageKey)
	var author string
	var isPublic int
	if err := row.Scan(&author, &isPublic); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, ErrNotFound
		}
		return false, err
	}
	if isPublic == 1 || author == viewerCode {
		return true, nil
	}
	return s.AreFriends(ctx, viewerCode, author)
}

func (s *Store) CanViewPostImage(ctx context.Context, postID, viewerCode string) (bool, string, error) {
	post, err := s.GetPost(ctx, postID)
	if err != nil {
		return false, "", err
	}
	if post.ImageKey == "" {
		return false, "", ErrNotFound
	}
	if post.IsPublic || post.AuthorCode == viewerCode {
		return true, post.ImageKey, nil
	}
	friends, err := s.AreFriends(ctx, viewerCode, post.AuthorCode)
	if err != nil {
		return false, "", err
	}
	return friends, post.ImageKey, nil
}
