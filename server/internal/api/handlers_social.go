package api

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/store"
	"github.com/meanderpaul/archerypal-server/internal/validate"
)

const (
	maxPostImageBytes = 5 << 20 // 5 MB
	maxFeedRadiusKm   = 500.0
)

func (s *Server) registerPostRoutes(mux *http.ServeMux) {
	mux.Handle("POST /v1/posts", s.withDeviceAuth(http.HandlerFunc(s.handleCreatePost)))
	mux.Handle("GET /v1/posts", s.withDeviceAuth(http.HandlerFunc(s.handleListPosts)))
}

func (s *Server) handleCreatePost(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	if s.postLimiter != nil && !s.postLimiter.allow(code) {
		http.Error(w, "too many requests", http.StatusTooManyRequests)
		return
	}
	if err := r.ParseMultipartForm(maxPostImageBytes + (1 << 20)); err != nil {
		http.Error(w, "invalid multipart", http.StatusBadRequest)
		return
	}
	body := r.FormValue("body")
	body, ok := validate.PostBody(body)
	if !ok {
		http.Error(w, "body required", http.StatusBadRequest)
		return
	}
	isPublic := strings.EqualFold(r.FormValue("isPublic"), "true") || r.FormValue("isPublic") == "1"

	var imageKey string
	file, header, err := r.FormFile("image")
	if err == nil {
		defer file.Close()
		// header.Size is advisory only; LimitReader + sanitize enforce the real cap.
		if header.Size > maxPostImageBytes {
			http.Error(w, "image too large", http.StatusBadRequest)
			return
		}
		_, reader, ok := sniffImageExt(file)
		if !ok {
			http.Error(w, "unsupported image type", http.StatusBadRequest)
			return
		}
		sanitized, err := sanitizePostImage(reader)
		if err != nil {
			http.Error(w, "invalid image", http.StatusBadRequest)
			return
		}
		if s.uploadQuota != nil && !s.uploadQuota.allow(code, int64(len(sanitized))) {
			http.Error(w, "daily image upload quota exceeded", http.StatusTooManyRequests)
			return
		}
		key, err := s.saveUploadBytes(sanitized, ".jpg")
		if err != nil {
			http.Error(w, "save image failed", http.StatusInternalServerError)
			return
		}
		imageKey = key
	} else if !errors.Is(err, http.ErrMissingFile) {
		http.Error(w, "invalid image", http.StatusBadRequest)
		return
	}

	post, err := s.store.CreatePost(r.Context(), store.CreatePostInput{
		AuthorCode: code,
		Body:       body,
		ImageKey:   imageKey,
		IsPublic:   isPublic,
	})
	if err != nil {
		if imageKey != "" {
			s.deleteUpload(imageKey)
		}
		http.Error(w, "create post failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"post": post})
}

func (s *Server) handleListPosts(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	since := parseQueryInt64(r, "since", 0)
	posts, err := s.store.ListVisiblePosts(r.Context(), code, since)
	if err != nil {
		http.Error(w, "list posts failed", http.StatusInternalServerError)
		return
	}
	if posts == nil {
		posts = []store.Post{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"posts": posts})
}

func (s *Server) registerFeedRoutes(mux *http.ServeMux) {
	mux.Handle("GET /v1/feed", s.withDeviceAuth(http.HandlerFunc(s.handleListFeed)))
}

func (s *Server) handleListFeed(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	filter := store.FeedFilter(strings.TrimSpace(r.URL.Query().Get("filter")))
	if filter == "" {
		filter = store.FeedAll
	}
	since := parseQueryInt64(r, "since", 0)
	radiusKm := 80.0
	if q := r.URL.Query().Get("radiusKm"); q != "" {
		if f, err := strconv.ParseFloat(q, 64); err == nil && f > 0 {
			radiusKm = f
			if radiusKm > maxFeedRadiusKm {
				radiusKm = maxFeedRadiusKm
			}
		}
	}
	var lat, lng *float64
	if la := r.URL.Query().Get("lat"); la != "" {
		if ln := r.URL.Query().Get("lng"); ln != "" {
			if latF, err1 := strconv.ParseFloat(la, 64); err1 == nil {
				if lngF, err2 := strconv.ParseFloat(ln, 64); err2 == nil {
					if !validate.Latitude(&latF) || !validate.Longitude(&lngF) {
						http.Error(w, "invalid coordinates", http.StatusBadRequest)
						return
					}
					lat = &latF
					lng = &lngF
				}
			}
		}
	}
	items, err := s.store.ListFeedItems(r.Context(), store.FeedInput{
		ViewerCode: code,
		Filter:     filter,
		Lat:        lat,
		Lng:        lng,
		RadiusKm:   radiusKm,
		SinceMs:    since,
	})
	if err != nil {
		http.Error(w, "feed error", http.StatusInternalServerError)
		return
	}
	if items == nil {
		items = []store.FeedItem{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

func (s *Server) registerMediaRoutes(mux *http.ServeMux) {
	mux.Handle("GET /v1/media/{key}", s.withDeviceAuth(http.HandlerFunc(s.handleGetMedia)))
}

func (s *Server) handleGetMedia(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	viewer := auth.FriendCodeFromContext(r.Context())
	key := strings.TrimSpace(r.PathValue("key"))
	path, err := s.resolveUploadPath(key)
	if err != nil {
		http.Error(w, "invalid key", http.StatusBadRequest)
		return
	}

	allowed, err := s.store.CanViewMediaByKey(r.Context(), viewer, key)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	if !allowed {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}

	f, err := os.Open(path)
	if err != nil {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	defer f.Close()
	switch strings.ToLower(filepath.Ext(key)) {
	case ".jpg", ".jpeg":
		w.Header().Set("Content-Type", "image/jpeg")
	case ".png":
		w.Header().Set("Content-Type", "image/png")
	default:
		w.Header().Set("Content-Type", "application/octet-stream")
	}
	_, _ = io.Copy(w, f)
}

func (s *Server) saveUploadBytes(data []byte, ext string) (string, error) {
	if err := os.MkdirAll(s.cfg.UploadsDir, 0o755); err != nil {
		return "", err
	}
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	key := hex.EncodeToString(b[:]) + ext
	path, err := s.resolveUploadPath(key)
	if err != nil {
		return "", err
	}
	if err := os.WriteFile(path, data, 0o644); err != nil {
		return "", err
	}
	return key, nil
}

func (s *Server) deleteUpload(key string) {
	path, err := s.resolveUploadPath(key)
	if err != nil {
		return
	}
	_ = os.Remove(path)
}

// saveUpload kept for tests that may still call the raw path; prefer saveUploadBytes.
func (s *Server) saveUpload(r io.Reader, ext string) (string, error) {
	data, err := io.ReadAll(io.LimitReader(r, maxPostImageBytes))
	if err != nil {
		return "", err
	}
	if ext != ".jpg" && ext != ".jpeg" && ext != ".png" {
		ext = ".jpg"
	}
	return s.saveUploadBytes(data, ext)
}
