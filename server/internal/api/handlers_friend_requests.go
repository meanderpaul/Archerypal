package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/store"
)

type friendRequestReq struct {
	TheirFriendCode string `json:"theirFriendCode"`
}

type friendRequestActionReq struct {
	FromFriendCode string `json:"fromFriendCode"`
}

func (s *Server) registerFriendRequestRoutes(mux *http.ServeMux) {
	mux.Handle("POST /v1/friends/request", s.withDeviceAuth(http.HandlerFunc(s.handleFriendRequest)))
	mux.Handle("POST /v1/friends/accept", s.withDeviceAuth(http.HandlerFunc(s.handleAcceptFriendRequest)))
	mux.Handle("POST /v1/friends/decline", s.withDeviceAuth(http.HandlerFunc(s.handleDeclineFriendRequest)))
	mux.Handle("GET /v1/friends/requests", s.withDeviceAuth(http.HandlerFunc(s.handleListFriendRequests)))
}

func (s *Server) handleFriendRequest(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	self := auth.FriendCodeFromContext(r.Context())
	var req friendRequestReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	their := strings.TrimSpace(req.TheirFriendCode)
	if their == "" {
		http.Error(w, "theirFriendCode required", http.StatusBadRequest)
		return
	}
	if their == self {
		http.Error(w, "cannot request self", http.StatusBadRequest)
		return
	}
	if err := s.store.SendFriendRequest(r.Context(), self, their); err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "player not found", http.StatusNotFound)
			return
		}
		if errors.Is(err, store.ErrConflict) {
			http.Error(w, "already friends", http.StatusConflict)
			return
		}
		http.Error(w, "request failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleAcceptFriendRequest(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	self := auth.FriendCodeFromContext(r.Context())
	var req friendRequestActionReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	from := strings.TrimSpace(req.FromFriendCode)
	if from == "" {
		http.Error(w, "fromFriendCode required", http.StatusBadRequest)
		return
	}
	friend, err := s.store.AcceptFriendRequest(r.Context(), self, from)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "request not found", http.StatusNotFound)
			return
		}
		http.Error(w, "accept failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"friend": map[string]string{
			"friendCode":  friend.FriendCode,
			"displayName": friend.DisplayName,
		},
	})
}

func (s *Server) handleDeclineFriendRequest(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	self := auth.FriendCodeFromContext(r.Context())
	var req friendRequestActionReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	from := strings.TrimSpace(req.FromFriendCode)
	if from == "" {
		http.Error(w, "fromFriendCode required", http.StatusBadRequest)
		return
	}
	if err := s.store.DeclineFriendRequest(r.Context(), self, from); err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "request not found", http.StatusNotFound)
			return
		}
		http.Error(w, "decline failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleListFriendRequests(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	self := auth.FriendCodeFromContext(r.Context())
	requests, err := s.store.ListPendingFriendRequests(r.Context(), self)
	if err != nil {
		http.Error(w, "list requests failed", http.StatusInternalServerError)
		return
	}
	if requests == nil {
		requests = []store.FriendRequest{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"requests": requests})
}
