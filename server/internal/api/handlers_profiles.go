package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/store"
	"github.com/meanderpaul/archerypal-server/internal/validate"
)

type profileReq struct {
	Bio           string `json:"bio"`
	Club          string `json:"club"`
	BowType       string `json:"bowType"`
	HomeRange     string `json:"homeRange"`
	LocationLabel string `json:"locationLabel"`
}

func (s *Server) registerProfileRoutes(mux *http.ServeMux) {
	mux.Handle("GET /v1/profiles/me", s.withDeviceAuth(http.HandlerFunc(s.handleGetMyProfile)))
	mux.Handle("PUT /v1/profiles/me", s.withDeviceAuth(http.HandlerFunc(s.handleUpsertMyProfile)))
	mux.Handle("DELETE /v1/profiles/me", s.withDeviceAuth(http.HandlerFunc(s.handleClearMyProfile)))
	mux.Handle("GET /v1/profiles/{code}", s.withDeviceAuth(http.HandlerFunc(s.handleGetProfile)))
}

func (s *Server) handleGetMyProfile(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	prof, err := s.store.GetProfile(r.Context(), code)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "profile error", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"profile": prof})
}

func (s *Server) handleUpsertMyProfile(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	var req profileReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	bio, ok := profileTextField(req.Bio)
	if !ok {
		http.Error(w, "bio too long", http.StatusBadRequest)
		return
	}
	club, ok := profileTextField(req.Club)
	if !ok {
		http.Error(w, "club too long", http.StatusBadRequest)
		return
	}
	bowType, ok := profileTextField(req.BowType)
	if !ok {
		http.Error(w, "bowType too long", http.StatusBadRequest)
		return
	}
	homeRange, ok := profileTextField(req.HomeRange)
	if !ok {
		http.Error(w, "homeRange too long", http.StatusBadRequest)
		return
	}
	locationLabel, ok := profileTextField(req.LocationLabel)
	if !ok {
		http.Error(w, "locationLabel too long", http.StatusBadRequest)
		return
	}
	prof, err := s.store.UpsertProfile(r.Context(), code, store.ProfileInput{
		Bio:           bio,
		Club:          club,
		BowType:       bowType,
		HomeRange:     homeRange,
		LocationLabel: locationLabel,
	})
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "update profile failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"profile": prof})
}

func (s *Server) handleClearMyProfile(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	code := auth.FriendCodeFromContext(r.Context())
	if err := s.store.ClearProfile(r.Context(), code); err != nil {
		http.Error(w, "clear profile failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleGetProfile(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	viewer := auth.FriendCodeFromContext(r.Context())
	target := strings.TrimSpace(r.PathValue("code"))
	if target == "" || !validate.FriendCode(target) {
		http.Error(w, "code required", http.StatusBadRequest)
		return
	}
	prof, err := s.store.GetProfileForViewer(r.Context(), target, viewer)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "profile error", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"profile": prof})
}

func profileTextField(raw string) (string, bool) {
	return validate.TextField(raw, validate.MaxBio)
}
