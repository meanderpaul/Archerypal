package api

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strings"
	"time"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/googleauth"
	"github.com/meanderpaul/archerypal-server/internal/store"
	"github.com/meanderpaul/archerypal-server/internal/validate"
)

type googleAuthReq struct {
	IDToken string `json:"idToken"`
}

type googleSignInReq struct {
	IDToken     string `json:"idToken"`
	DisplayName string `json:"displayName"`
	InviteCode  string `json:"inviteCode"`
}

func (s *Server) registerAuthRoutes(mux *http.ServeMux) {
	mux.Handle("POST /v1/auth/google/link", s.withDeviceAuth(http.HandlerFunc(s.handleGoogleLink)))
	mux.HandleFunc("POST /v1/auth/google/restore", s.rateLimitRestore(http.HandlerFunc(s.handleGoogleRestore)))
	mux.HandleFunc("POST /v1/auth/google/sign-in", s.rateLimitRestore(http.HandlerFunc(s.handleGoogleSignIn)))
	mux.Handle("GET /v1/auth/google/status", s.withDeviceAuth(http.HandlerFunc(s.handleGoogleStatus)))
}

func (s *Server) handleGoogleLink(w http.ResponseWriter, r *http.Request) {
	if s.googleVerifier == nil {
		http.Error(w, "google auth not configured", http.StatusServiceUnavailable)
		return
	}
	self := auth.FriendCodeFromContext(r.Context())
	var req googleAuthReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	verified, err := s.googleVerifier.Verify(r.Context(), req.IDToken)
	if err != nil {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}
	existing, err := s.store.GetGoogleLinkBySub(r.Context(), verified.Sub)
	if err == nil && existing.FriendCode != self {
		http.Error(w, "google account already linked to another friend id", http.StatusConflict)
		return
	}
	if err != nil && !errors.Is(err, store.ErrNotFound) {
		http.Error(w, "link check failed", http.StatusInternalServerError)
		return
	}
	if existing != nil && existing.FriendCode == self {
		writeJSON(w, http.StatusOK, map[string]any{
			"linked": true,
			"email":  verified.Email,
		})
		return
	}
	linked, err := s.store.GetGoogleLinkByFriendCode(r.Context(), self)
	if err == nil {
		if linked.GoogleSub != verified.Sub {
			http.Error(w, "friend id already linked to another google account", http.StatusConflict)
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{
			"linked": true,
			"email":  linked.Email,
		})
		return
	}
	if err != nil && !errors.Is(err, store.ErrNotFound) {
		http.Error(w, "link check failed", http.StatusInternalServerError)
		return
	}
	if err := s.store.LinkGoogleAccount(r.Context(), verified.Sub, self, verified.Email); err != nil {
		if errors.Is(err, store.ErrConflict) {
			http.Error(w, "google account already linked", http.StatusConflict)
			return
		}
		http.Error(w, "link failed", http.StatusInternalServerError)
		return
	}
	log.Printf("google link: friend=%s sub=%s", self, verified.Sub)
	writeJSON(w, http.StatusOK, map[string]any{
		"linked": true,
		"email":  verified.Email,
	})
}

func (s *Server) handleGoogleRestore(w http.ResponseWriter, r *http.Request) {
	if s.googleVerifier == nil {
		http.Error(w, "google auth not configured", http.StatusServiceUnavailable)
		return
	}
	var req googleAuthReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	verified, err := s.googleVerifier.Verify(r.Context(), req.IDToken)
	if err != nil {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}
	s.completeGoogleRestore(w, r, verified)
}

// handleGoogleSignIn verifies the Google ID token once, then either restores an
// existing linked account or creates + links a new Friend ID in a single round trip.
func (s *Server) handleGoogleSignIn(w http.ResponseWriter, r *http.Request) {
	if s.googleVerifier == nil {
		http.Error(w, "google auth not configured", http.StatusServiceUnavailable)
		return
	}
	var req googleSignInReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	verified, err := s.googleVerifier.Verify(r.Context(), req.IDToken)
	if err != nil {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}
	_, err = s.store.GetGoogleLinkBySub(r.Context(), verified.Sub)
	if err == nil {
		s.completeGoogleRestore(w, r, verified)
		return
	}
	if !errors.Is(err, store.ErrNotFound) {
		http.Error(w, "sign-in lookup failed", http.StatusInternalServerError)
		return
	}
	displayName, ok := validate.DisplayName(req.DisplayName)
	if !ok {
		http.Error(w, "invalid display name", http.StatusBadRequest)
		return
	}
	inviteCode := strings.TrimSpace(req.InviteCode)
	if s.cfg.RequireBetaInvite {
		if inviteCode == "" {
			http.Error(w, "invite code required", http.StatusForbidden)
			return
		}
	}

	var friendCode, deviceSecret string
	for attempt := 0; attempt < 8; attempt++ {
		friendCode, err = newFriendCode()
		if err != nil {
			http.Error(w, "server error", http.StatusInternalServerError)
			return
		}
		deviceSecret, err = newDeviceSecret()
		if err != nil {
			http.Error(w, "server error", http.StatusInternalServerError)
			return
		}
		if s.cfg.RequireBetaInvite {
			if err := s.store.RedeemBetaInvite(r.Context(), inviteCode, friendCode); err != nil {
				if errors.Is(err, store.ErrNotFound) {
					http.Error(w, "invalid invite code", http.StatusForbidden)
					return
				}
				if errors.Is(err, store.ErrConflict) {
					http.Error(w, "invite code already used", http.StatusForbidden)
					return
				}
				http.Error(w, "invite check failed", http.StatusInternalServerError)
				return
			}
		}
		err = s.store.RegisterPlayer(r.Context(), friendCode, displayName, deviceSecret, nil)
		if errors.Is(err, store.ErrConflict) {
			continue
		}
		if err != nil {
			http.Error(w, "register failed", http.StatusInternalServerError)
			return
		}
		break
	}
	if err != nil {
		http.Error(w, "register failed", http.StatusInternalServerError)
		return
	}
	if err := s.store.LinkGoogleAccount(r.Context(), verified.Sub, friendCode, verified.Email); err != nil {
		if errors.Is(err, store.ErrConflict) {
			// Another request linked this Google account first — restore that account.
			s.completeGoogleRestore(w, r, verified)
			return
		}
		http.Error(w, "link failed", http.StatusInternalServerError)
		return
	}
	log.Printf("google sign-in create: friend=%s sub=%s", friendCode, verified.Sub)
	writeJSON(w, http.StatusOK, map[string]any{
		"friendCode":  friendCode,
		"deviceSecret": deviceSecret,
		"displayName": displayName,
		"email":       strings.TrimSpace(verified.Email),
		"created":     true,
	})
}

func (s *Server) completeGoogleRestore(w http.ResponseWriter, r *http.Request, verified *googleauth.VerifiedToken) {
	link, err := s.store.GetGoogleLinkBySub(r.Context(), verified.Sub)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "no account linked to this google sign-in", http.StatusNotFound)
			return
		}
		http.Error(w, "restore lookup failed", http.StatusInternalServerError)
		return
	}
	player, err := s.store.GetPlayer(r.Context(), link.FriendCode)
	if err != nil {
		http.Error(w, "player not found", http.StatusNotFound)
		return
	}
	newSecret, err := newDeviceSecret()
	if err != nil {
		http.Error(w, "server error", http.StatusInternalServerError)
		return
	}
	if err := s.store.RotateDeviceSecret(r.Context(), link.FriendCode, newSecret); err != nil {
		http.Error(w, "rotate secret failed", http.StatusInternalServerError)
		return
	}
	email := strings.TrimSpace(link.Email)
	if email == "" {
		email = strings.TrimSpace(verified.Email)
	}
	log.Printf("google restore: friend=%s sub=%s", link.FriendCode, verified.Sub)
	writeJSON(w, http.StatusOK, map[string]any{
		"friendCode":  link.FriendCode,
		"deviceSecret": newSecret,
		"displayName": player.DisplayName,
		"email":       email,
		"created":     false,
	})
}

func newFriendCode() (string, error) {
	const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	var b [6]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	out := make([]byte, 6)
	for i := range b {
		out[i] = alphabet[int(b[i])%len(alphabet)]
	}
	return "AP-" + string(out), nil
}

func (s *Server) handleGoogleStatus(w http.ResponseWriter, r *http.Request) {
	self := auth.FriendCodeFromContext(r.Context())
	link, err := s.store.GetGoogleLinkByFriendCode(r.Context(), self)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeJSON(w, http.StatusOK, map[string]any{"linked": false})
			return
		}
		http.Error(w, "status failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"linked": true,
		"email":  link.Email,
	})
}

func newDeviceSecret() (string, error) {
	var b [32]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}

// Ensure compile-time interface usage for googleauth package.
var _ = googleauth.ErrNotConfigured

func (s *Server) registerBetaAdminRoutes(mux *http.ServeMux) {
	mux.HandleFunc("POST /v1/admin/beta-invites", s.handleAdminCreateBetaInvite)
}

type adminBetaInviteReq struct {
	Code      string `json:"code"`
	Note      string `json:"note"`
	ExpiresAt *int64 `json:"expiresAt"`
}

func (s *Server) handleAdminCreateBetaInvite(w http.ResponseWriter, r *http.Request) {
	if !s.requireAdmin(w, r) {
		return
	}
	limitAdminBody(w, r)
	var req adminBetaInviteReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	var expires *time.Time
	if req.ExpiresAt != nil && *req.ExpiresAt > 0 {
		t := time.UnixMilli(*req.ExpiresAt).UTC()
		expires = &t
	}
	if err := s.store.CreateBetaInvite(r.Context(), req.Code, req.Note, expires); err != nil {
		if errors.Is(err, store.ErrConflict) {
			http.Error(w, "invite code exists", http.StatusConflict)
			return
		}
		http.Error(w, "create invite failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}
