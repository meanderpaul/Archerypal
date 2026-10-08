package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/store"
	"github.com/meanderpaul/archerypal-server/internal/validate"
)

type createShootReq struct {
	Title           string   `json:"title"`
	Description     string   `json:"description"`
	LocationName    string   `json:"locationName"`
	Region          string   `json:"region"`
	StartsAt        int64    `json:"startsAt"`
	EndsAt          *int64   `json:"endsAt"`
	IsPublic        bool     `json:"isPublic"`
	SourceURL       string   `json:"sourceUrl"`
	RegistrationURL string   `json:"registrationUrl"`
	EventType       string   `json:"eventType"`
	Latitude        *float64 `json:"latitude"`
	Longitude       *float64 `json:"longitude"`
}

type adminCreateShootReq struct {
	ID              string   `json:"id"`
	Title           string   `json:"title"`
	Description     string   `json:"description"`
	LocationName    string   `json:"locationName"`
	Region          string   `json:"region"`
	StartsAt        int64    `json:"startsAt"`
	EndsAt          *int64   `json:"endsAt"`
	SourceURL       string   `json:"sourceUrl"`
	RegistrationURL string   `json:"registrationUrl"`
	EventType       string   `json:"eventType"`
	Latitude        *float64 `json:"latitude"`
	Longitude       *float64 `json:"longitude"`
}

type adminCancelShootsReq struct {
	KeepIDs []string `json:"keepIds"`
}

func (s *Server) registerShootRoutes(mux *http.ServeMux) {
	mux.Handle("GET /v1/shoots", s.withDeviceAuth(http.HandlerFunc(s.handleListShoots)))
	mux.Handle("POST /v1/shoots", s.withDeviceAuth(http.HandlerFunc(s.handleCreateShoot)))
	mux.Handle("GET /v1/shoots/{id}", s.withDeviceAuth(http.HandlerFunc(s.handleGetShoot)))
	mux.Handle("POST /v1/shoots/{id}/rsvp", s.withDeviceAuth(http.HandlerFunc(s.handleSetShootRsvp)))
	mux.Handle("DELETE /v1/shoots/{id}/rsvp", s.withDeviceAuth(http.HandlerFunc(s.handleClearShootRsvp)))
	mux.Handle("GET /v1/shoots/{id}/attendees", s.withDeviceAuth(http.HandlerFunc(s.handleListShootAttendees)))
	mux.HandleFunc("POST /v1/admin/shoots", s.handleAdminCreateShoot)
	mux.HandleFunc("POST /v1/admin/shoots/cancel-stale", s.handleAdminCancelStaleShoots)
}

func (s *Server) handleListShoots(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	fromMs := parseQueryInt64(r, "from", time.Now().Add(-24*time.Hour).UnixMilli())
	toMs := parseQueryInt64(r, "to", 0)
	shoots, err := s.store.ListVisibleShoots(r.Context(), code, fromMs, toMs)
	if err != nil {
		http.Error(w, "list shoots failed", http.StatusInternalServerError)
		return
	}
	if shoots == nil {
		shoots = []store.Shoot{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"shoots": shoots})
}

func (s *Server) handleCreateShoot(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	var req createShootReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	req.Title = strings.TrimSpace(req.Title)
	req.LocationName = strings.TrimSpace(req.LocationName)
	req.SourceURL = strings.TrimSpace(req.SourceURL)
	req.RegistrationURL = strings.TrimSpace(req.RegistrationURL)
	if req.Title == "" || req.LocationName == "" || req.StartsAt == 0 {
		http.Error(w, "title, locationName, and startsAt required", http.StatusBadRequest)
		return
	}
	if !validate.HTTPSURL(req.SourceURL) || !validate.HTTPSURL(req.RegistrationURL) {
		http.Error(w, "invalid url", http.StatusBadRequest)
		return
	}
	if !validate.Latitude(req.Latitude) || !validate.Longitude(req.Longitude) {
		http.Error(w, "invalid coordinates", http.StatusBadRequest)
		return
	}
	var ends *time.Time
	if req.EndsAt != nil && *req.EndsAt > 0 {
		t := time.UnixMilli(*req.EndsAt).UTC()
		ends = &t
	}
	sh, err := s.store.CreateShoot(r.Context(), store.CreateShootInput{
		Title:           req.Title,
		Description:     strings.TrimSpace(req.Description),
		LocationName:    req.LocationName,
		Region:          strings.TrimSpace(req.Region),
		StartsAt:        time.UnixMilli(req.StartsAt).UTC(),
		EndsAt:          ends,
		HostCode:        code,
		IsPublic:        req.IsPublic,
		SourceURL:       strings.TrimSpace(req.SourceURL),
		RegistrationURL: strings.TrimSpace(req.RegistrationURL),
		EventType:       strings.TrimSpace(req.EventType),
		Latitude:        req.Latitude,
		Longitude:       req.Longitude,
	})
	if err != nil {
		http.Error(w, "create shoot failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"shoot": sh})
}

func (s *Server) handleGetShoot(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	id := r.PathValue("id")
	sh, err := s.store.GetShootForViewer(r.Context(), id, code)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "get shoot failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"shoot": sh})
}

func (s *Server) handleSetShootRsvp(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	id := r.PathValue("id")
	if err := s.store.SetShootRsvp(r.Context(), id, code); err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "rsvp failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleClearShootRsvp(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	id := r.PathValue("id")
	if err := s.store.ClearShootRsvp(r.Context(), id, code); err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "clear rsvp failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleListShootAttendees(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	id := r.PathValue("id")
	attendees, err := s.store.ListShootAttendees(r.Context(), id, code)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "list attendees failed", http.StatusInternalServerError)
		return
	}
	if attendees == nil {
		attendees = []store.ShootAttendee{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"attendees": attendees})
}

func (s *Server) handleAdminCreateShoot(w http.ResponseWriter, r *http.Request) {
	if !s.requireAdmin(w, r) {
		return
	}
	limitAdminBody(w, r)
	var req adminCreateShootReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	req.ID = strings.TrimSpace(req.ID)
	req.Title = strings.TrimSpace(req.Title)
	req.LocationName = strings.TrimSpace(req.LocationName)
	if req.ID == "" || req.Title == "" || req.LocationName == "" || req.StartsAt == 0 {
		http.Error(w, "id, title, locationName, and startsAt required", http.StatusBadRequest)
		return
	}
	var ends *time.Time
	if req.EndsAt != nil && *req.EndsAt > 0 {
		t := time.UnixMilli(*req.EndsAt).UTC()
		ends = &t
	}
	err := s.store.CreateCuratedShoot(r.Context(), store.CuratedShootInput{
		ID:              req.ID,
		Title:           req.Title,
		Description:     strings.TrimSpace(req.Description),
		LocationName:    req.LocationName,
		Region:          strings.TrimSpace(req.Region),
		StartsAt:        time.UnixMilli(req.StartsAt).UTC(),
		EndsAt:          ends,
		SourceURL:       strings.TrimSpace(req.SourceURL),
		RegistrationURL: strings.TrimSpace(req.RegistrationURL),
		EventType:       strings.TrimSpace(req.EventType),
		Latitude:        req.Latitude,
		Longitude:       req.Longitude,
	})
	if err != nil {
		http.Error(w, "create curated shoot failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleAdminCancelStaleShoots(w http.ResponseWriter, r *http.Request) {
	if !s.requireAdmin(w, r) {
		return
	}
	limitAdminBody(w, r)
	var req adminCancelShootsReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	n, err := s.store.CancelShootsNotIn(r.Context(), req.KeepIDs)
	if err != nil {
		http.Error(w, "cancel stale shoots failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"cancelled": n})
}

func parseQueryInt64(r *http.Request, key string, defaultVal int64) int64 {
	q := r.URL.Query().Get(key)
	if q == "" {
		return defaultVal
	}
	n, err := strconv.ParseInt(q, 10, 64)
	if err != nil {
		return defaultVal
	}
	return n
}
