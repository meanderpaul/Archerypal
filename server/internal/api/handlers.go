package api

import (
	"bytes"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/fcm"
	"github.com/meanderpaul/archerypal-server/internal/googleauth"
	"github.com/meanderpaul/archerypal-server/internal/middleware"
	"github.com/meanderpaul/archerypal-server/internal/playbilling"
	"github.com/meanderpaul/archerypal-server/internal/room"
	"github.com/meanderpaul/archerypal-server/internal/store"
	"github.com/meanderpaul/archerypal-server/internal/validate"
)

// Must cover multipart post uploads (5 MB image + form overhead).
const maxDeviceAuthBodyBytes = 6 << 20

type Config struct {
	PublicWSBase                   string
	RoomTokenTTL                   time.Duration
	BypassPlusEntitlement          bool
	AllowUnverifiedEntitlementSync bool // testing only; rejected in production
	TrustForwardedFor              bool // only when behind a trusted reverse proxy
	AdminAPIKey                    string
	UploadsDir                     string
	GoogleOAuthClientID            string
	RequireBetaInvite              bool
	FCM                            *fcm.Sender
	PlayVerifier                   playbilling.Verifier
}

type Server struct {
	store                    *store.Store
	auth                     *auth.Authenticator
	rooms                    *room.Manager
	cfg                      Config
	fcm                      *fcm.Sender
	googleVerifier           *googleauth.Verifier
	playVerifier             playbilling.Verifier
	registerLimiter          *rateLimiter
	restoreLimiter           *rateLimiter
	globalLeaderboardLimiter *rateLimiter
	deviceAuthLimiter        *rateLimiter
	postLimiter              *rateLimiter
	adminLimiter             *rateLimiter
	entitlementLimiter       *rateLimiter
	completeLimiter          *rateLimiter
	uploadQuota              *uploadQuotaTracker
}

func NewServer(st *store.Store, au *auth.Authenticator, rm *room.Manager, cfg Config) *Server {
	if cfg.RoomTokenTTL == 0 {
		cfg.RoomTokenTTL = 15 * time.Minute
	}
	var verifier *googleauth.Verifier
	if cfg.GoogleOAuthClientID != "" {
		verifier = googleauth.NewVerifier(cfg.GoogleOAuthClientID)
	}
	return &Server{
		store:                    st,
		auth:                     au,
		rooms:                    rm,
		cfg:                      cfg,
		fcm:                      cfg.FCM,
		googleVerifier:           verifier,
		playVerifier:             playbilling.Optional(cfg.PlayVerifier),
		registerLimiter:          newRateLimiter(5, time.Minute),
		restoreLimiter:           newRateLimiter(10, time.Minute),
		globalLeaderboardLimiter: newRateLimiter(30, time.Minute),
		deviceAuthLimiter:        newRateLimiter(120, time.Minute),
		postLimiter:              newRateLimiter(10, time.Minute),
		adminLimiter:             newRateLimiter(10, time.Minute),
		entitlementLimiter:       newRateLimiter(30, time.Hour),
		completeLimiter:          newRateLimiter(20, time.Hour),
		uploadQuota:              newUploadQuotaTracker(),
	}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", s.handleHealth)
	mux.HandleFunc("POST /v1/players/register", s.rateLimitRegister(s.handleRegister))
	mux.Handle("PATCH /v1/players/me", s.withDeviceAuth(http.HandlerFunc(s.handlePatchMe)))
	mux.Handle("GET /v1/friends", s.withDeviceAuth(http.HandlerFunc(s.handleListFriends)))
	mux.Handle("POST /v1/friends/add", s.withDeviceAuth(http.HandlerFunc(s.handleAddFriend)))
	mux.Handle("POST /v1/matches/challenge", s.withDeviceAuth(http.HandlerFunc(s.handleChallenge)))
	mux.Handle("POST /v1/entitlements/sync", s.withDeviceAuth(http.HandlerFunc(s.handleSyncEntitlement)))
	mux.Handle("POST /v1/matches/{roomId}/complete", s.withDeviceAuth(http.HandlerFunc(s.handleCompleteMatch)))
	mux.HandleFunc("GET /v1/leaderboards/global", s.rateLimitGlobalLeaderboard(s.handleGlobalLeaderboard))
	mux.Handle("GET /v1/leaderboards/friends", s.withDeviceAuth(http.HandlerFunc(s.handleFriendsLeaderboard)))
	mux.HandleFunc("GET /v1/room/{roomId}", s.handleRoomWS)
	s.registerShootRoutes(mux)
	s.registerFriendRequestRoutes(mux)
	s.registerProfileRoutes(mux)
	s.registerPostRoutes(mux)
	s.registerFeedRoutes(mux)
	s.registerMediaRoutes(mux)
	s.registerAuthRoutes(mux)
	s.registerBetaAdminRoutes(mux)
	return middleware.Chain(mux)
}

func (s *Server) withDeviceAuth(next http.Handler) http.Handler {
	return s.rateLimitDeviceAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(io.LimitReader(r.Body, maxDeviceAuthBodyBytes+1))
		if err != nil {
			http.Error(w, "bad body", http.StatusBadRequest)
			return
		}
		if len(body) > maxDeviceAuthBodyBytes {
			http.Error(w, "body too large", http.StatusRequestEntityTooLarge)
			return
		}
		r.Body = io.NopCloser(bytes.NewReader(body))
		ctx := auth.WithBodyBytes(r.Context(), body)
		s.auth.DeviceAuth(next).ServeHTTP(w, r.WithContext(ctx))
	}))
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

type registerReq struct {
	FriendCode   string  `json:"friendCode"`
	DisplayName  string  `json:"displayName"`
	DeviceSecret string  `json:"deviceSecret"`
	FCMToken     *string `json:"fcmToken"`
	InviteCode   string  `json:"inviteCode"`
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	var req registerReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	req.FriendCode = strings.TrimSpace(req.FriendCode)
	req.DisplayName = strings.TrimSpace(req.DisplayName)
	req.InviteCode = strings.TrimSpace(req.InviteCode)
	if !validate.FriendCode(req.FriendCode) {
		http.Error(w, "invalid friend code format", http.StatusBadRequest)
		return
	}
	displayName, ok := validate.DisplayName(req.DisplayName)
	if !ok {
		http.Error(w, "invalid display name", http.StatusBadRequest)
		return
	}
	req.DisplayName = displayName
	if !validate.DeviceSecret(req.DeviceSecret) {
		http.Error(w, "invalid device secret", http.StatusBadRequest)
		return
	}
	if req.FriendCode == "" || req.DisplayName == "" || req.DeviceSecret == "" {
		http.Error(w, "missing fields", http.StatusBadRequest)
		return
	}
	if s.cfg.RequireBetaInvite {
		if req.InviteCode == "" {
			http.Error(w, "invite code required", http.StatusForbidden)
			return
		}
		if err := s.store.RedeemBetaInvite(r.Context(), req.InviteCode, req.FriendCode); err != nil {
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
	err := s.store.RegisterPlayer(r.Context(), req.FriendCode, req.DisplayName, req.DeviceSecret, req.FCMToken)
	if errors.Is(err, store.ErrConflict) {
		http.Error(w, "friend code taken", http.StatusConflict)
		return
	}
	if err != nil {
		http.Error(w, "register failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"friendCode": req.FriendCode})
}

type patchMeReq struct {
	DisplayName string  `json:"displayName"`
	FCMToken    *string `json:"fcmToken"`
}

func (s *Server) handlePatchMe(w http.ResponseWriter, r *http.Request) {
	code := auth.FriendCodeFromContext(r.Context())
	var req patchMeReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	req.DisplayName = strings.TrimSpace(req.DisplayName)
	displayName, ok := validate.DisplayName(req.DisplayName)
	if !ok {
		http.Error(w, "invalid display name", http.StatusBadRequest)
		return
	}
	req.DisplayName = displayName
	if req.DisplayName == "" {
		http.Error(w, "displayName required", http.StatusBadRequest)
		return
	}
	if err := s.store.UpdatePlayer(r.Context(), code, req.DisplayName, req.FCMToken); err != nil {
		if errors.Is(err, store.ErrNotFound) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.Error(w, "update failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"friendCode": code})
}

func (s *Server) requirePlus(w http.ResponseWriter, r *http.Request) bool {
	if s.cfg.BypassPlusEntitlement {
		return true
	}
	code := auth.FriendCodeFromContext(r.Context())
	active, err := s.store.HasActivePlus(r.Context(), code)
	if err != nil {
		http.Error(w, "entitlement check failed", http.StatusInternalServerError)
		return false
	}
	if !active {
		http.Error(w, "Archerypal Plus required", http.StatusPaymentRequired)
		return false
	}
	return true
}

func (s *Server) handleListFriends(w http.ResponseWriter, r *http.Request) {
	code := auth.FriendCodeFromContext(r.Context())
	friends, err := s.store.ListFriends(r.Context(), code)
	if err != nil {
		http.Error(w, "list friends failed", http.StatusInternalServerError)
		return
	}
	if friends == nil {
		friends = []store.FriendSummary{}
	}
	writeJSON(w, http.StatusOK, map[string]any{"friends": friends})
}

type syncEntitlementReq struct {
	SKU               string `json:"sku"`
	PurchaseToken     string `json:"purchaseToken"`
	PurchaseTokenHash string `json:"purchaseTokenHash"` // testing-only unverified path
	ExpiresAt         *int64 `json:"expiresAt"`         // testing-only unverified path
}

const maxPurchaseTokenRunes = 4096

func (s *Server) handleSyncEntitlement(w http.ResponseWriter, r *http.Request) {
	code := auth.FriendCodeFromContext(r.Context())
	if s.entitlementLimiter != nil && !s.entitlementLimiter.allow(code) {
		http.Error(w, "too many requests", http.StatusTooManyRequests)
		return
	}
	var req syncEntitlementReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	req.SKU = strings.TrimSpace(req.SKU)
	req.PurchaseToken = strings.TrimSpace(req.PurchaseToken)
	req.PurchaseTokenHash = strings.TrimSpace(req.PurchaseTokenHash)
	if req.SKU == "" {
		http.Error(w, "sku required", http.StatusBadRequest)
		return
	}
	if !store.IsPlusSKU(req.SKU) {
		http.Error(w, "unsupported sku", http.StatusBadRequest)
		return
	}

	// Preferred path: verify the raw purchase token with Google Play.
	if s.playVerifier.Enabled() {
		if req.PurchaseToken == "" {
			http.Error(w, "purchaseToken required", http.StatusBadRequest)
			return
		}
		if len([]rune(req.PurchaseToken)) > maxPurchaseTokenRunes {
			http.Error(w, "purchaseToken too long", http.StatusBadRequest)
			return
		}
		verified, err := s.playVerifier.Verify(r.Context(), req.SKU, req.PurchaseToken)
		if err != nil {
			if errors.Is(err, playbilling.ErrInactive) {
				http.Error(w, "purchase not active", http.StatusForbidden)
				return
			}
			if errors.Is(err, playbilling.ErrUnsupportedSKU) {
				http.Error(w, "unsupported sku", http.StatusBadRequest)
				return
			}
			log.Printf("entitlement sync play verify failed: friend=%s sku=%s err=%v", code, req.SKU, err)
			http.Error(w, "purchase verification failed", http.StatusBadGateway)
			return
		}
		sum := sha256.Sum256([]byte(req.PurchaseToken))
		if err := s.store.UpsertEntitlement(r.Context(), code, verified.SKU, sum[:], verified.ExpiresAt); err != nil {
			http.Error(w, "sync failed", http.StatusInternalServerError)
			return
		}
		log.Printf("entitlement sync (play verified): friend=%s sku=%s", code, verified.SKU)
		writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
		return
	}

	// Testing-only fallback: client-supplied token hash (never trust in production).
	if !s.cfg.AllowUnverifiedEntitlementSync {
		http.Error(w, "server-side Play verification required; unverified entitlement sync disabled", http.StatusForbidden)
		return
	}
	if req.PurchaseTokenHash == "" {
		http.Error(w, "sku and purchaseTokenHash required", http.StatusBadRequest)
		return
	}
	if req.SKU == store.PlusSKU && (req.ExpiresAt == nil || *req.ExpiresAt <= 0) {
		http.Error(w, "expiresAt required for monthly Plus until server-side Play verification is enabled", http.StatusBadRequest)
		return
	}
	hash, err := hex.DecodeString(req.PurchaseTokenHash)
	if err != nil || len(hash) != sha256.Size {
		http.Error(w, "invalid purchaseTokenHash", http.StatusBadRequest)
		return
	}
	log.Printf("entitlement sync (unverified testing): friend=%s sku=%s", code, req.SKU)
	if err := s.store.UpsertEntitlement(r.Context(), code, req.SKU, hash, req.ExpiresAt); err != nil {
		http.Error(w, "sync failed", http.StatusInternalServerError)
		return
	}
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleAddFriend(w http.ResponseWriter, r *http.Request) {
	// Instant mutual friendship removed — it allowed forcing private-feed visibility.
	// Clients must use /v1/friends/request then /v1/friends/accept.
	http.Error(w, "use /v1/friends/request then /v1/friends/accept", http.StatusGone)
}

type challengeReq struct {
	TargetFriendCode string `json:"targetFriendCode"`
}

func (s *Server) handleChallenge(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	host := auth.FriendCodeFromContext(r.Context())
	var req challengeReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	target := strings.TrimSpace(req.TargetFriendCode)
	if target == "" {
		http.Error(w, "targetFriendCode required", http.StatusBadRequest)
		return
	}
	if _, err := s.store.GetPlayer(r.Context(), target); err != nil {
		http.Error(w, "target not found", http.StatusNotFound)
		return
	}
	roomID, err := newRoomID()
	if err != nil {
		http.Error(w, "server error", http.StatusInternalServerError)
		return
	}
	expires := time.Now().Add(s.cfg.RoomTokenTTL * 2)
	if err := s.store.CreateRoom(r.Context(), roomID, host, target, expires); err != nil {
		http.Error(w, "create room failed", http.StatusInternalServerError)
		return
	}
	if err := s.rooms.Create(roomID, host); err != nil {
		_ = s.store.CloseRoom(r.Context(), roomID)
		if errors.Is(err, room.ErrTooManyRooms) {
			http.Error(w, "too many active rooms", http.StatusServiceUnavailable)
			return
		}
		http.Error(w, "create room failed", http.StatusInternalServerError)
		return
	}
	token, err := s.auth.IssueRoomToken(roomID, host, true, s.cfg.RoomTokenTTL)
	if err != nil {
		http.Error(w, "token error", http.StatusInternalServerError)
		return
	}
	joinToken, err := s.auth.IssueRoomToken(roomID, target, false, s.cfg.RoomTokenTTL)
	if err != nil {
		http.Error(w, "token error", http.StatusInternalServerError)
		return
	}
	hostPlayer, _ := s.store.GetPlayer(r.Context(), host)
	hostName := ""
	if hostPlayer != nil {
		hostName = hostPlayer.DisplayName
	}
	inviteURI := "archerypal://remote?roomId=" + roomID + "&token=" + joinToken
	if hostName != "" {
		inviteURI += "&host=" + strings.ReplaceAll(hostName, " ", "%20")
	}
	targetFcm := ""
	if token, err := s.store.FCMTokenFor(r.Context(), target); err == nil && token != "" {
		targetFcm = "present"
		if s.fcm != nil && s.fcm.Enabled() {
			body := "Open Archerypal to join"
			if hostName != "" {
				body = hostName + " challenged you — tap to join"
			}
			if err := s.fcm.SendInvite(r.Context(), token, fcm.InvitePayload{
				Title:     "Match invite",
				Body:      body,
				InviteURI: inviteURI,
				RoomID:    roomID,
				HostName:  hostName,
			}); err != nil {
				log.Printf("fcm invite failed room=%s target=%s: %v", roomID, target, err)
				targetFcm = "send_failed"
			} else {
				targetFcm = "sent"
			}
		}
	}
	base := s.publicWSBase(r)
	wsURL := base + "/v1/room/" + roomID + "?token=" + token
	writeJSON(w, http.StatusOK, map[string]string{
		"roomId":      roomID,
		"wsUrl":       wsURL,
		"joinToken":   joinToken,
		"inviteUri":   inviteURI,
		"targetFcm":   targetFcm,
	})
}

func newRoomID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}

type completeReq struct {
	MatchID          string                 `json:"matchId"`
	TargetCount      int                    `json:"targetCount"`
	ScoringType      string                 `json:"scoringType"`
	CompletedAt      int64                  `json:"completedAt"`
	Players          []store.CompletePlayer `json:"players"`
	WinnerFriendCode string                 `json:"winnerFriendCode"`
}

func (s *Server) handleCompleteMatch(w http.ResponseWriter, r *http.Request) {
	if !s.requirePlus(w, r) {
		return
	}
	roomID := r.PathValue("roomId")
	host := auth.FriendCodeFromContext(r.Context())
	if s.completeLimiter != nil && !s.completeLimiter.allow(host) {
		http.Error(w, "too many requests", http.StatusTooManyRequests)
		return
	}
	dbRoom, err := s.store.GetRoom(r.Context(), roomID)
	if err != nil {
		http.Error(w, "room not found", http.StatusNotFound)
		return
	}
	if dbRoom.HostFriendCode != host {
		http.Error(w, "host only", http.StatusForbidden)
		return
	}
	var req completeReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	if req.MatchID == "" || req.WinnerFriendCode == "" || len(req.Players) == 0 {
		http.Error(w, "missing fields", http.StatusBadRequest)
		return
	}
	if err := validateCompleteMatch(dbRoom, req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	completedAt := time.UnixMilli(req.CompletedAt)
	if req.CompletedAt == 0 {
		completedAt = time.Now().UTC()
	}
	in := store.CompleteMatchInput{
		MatchID:          req.MatchID,
		RoomID:           roomID,
		CompletedAt:      completedAt,
		ScoringType:      req.ScoringType,
		TargetCount:      req.TargetCount,
		WinnerFriendCode: req.WinnerFriendCode,
		Players:          req.Players,
	}
	if err := s.store.CompleteMatch(r.Context(), in); err != nil {
		if errors.Is(err, store.ErrConflict) {
			http.Error(w, "already completed", http.StatusConflict)
			return
		}
		http.Error(w, "complete failed", http.StatusInternalServerError)
		return
	}
	s.rooms.Close(roomID)
	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (s *Server) handleGlobalLeaderboard(w http.ResponseWriter, r *http.Request) {
	limit := 50
	if q := r.URL.Query().Get("limit"); q != "" {
		if n, err := strconv.Atoi(q); err == nil {
			limit = validate.Limit(n)
		}
	}
	rows, err := s.store.GlobalLeaderboard(r.Context(), limit)
	if err != nil {
		http.Error(w, "leaderboard error", http.StatusInternalServerError)
		return
	}
	etag := `"` + store.LeaderboardETag(rows) + `"`
	if r.Header.Get("If-None-Match") == etag {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	w.Header().Set("ETag", etag)
	writeJSON(w, http.StatusOK, map[string]any{"entries": rows})
}

func (s *Server) handleFriendsLeaderboard(w http.ResponseWriter, r *http.Request) {
	code := auth.FriendCodeFromContext(r.Context())
	rows, err := s.store.FriendsLeaderboard(r.Context(), code)
	if err != nil {
		http.Error(w, "leaderboard error", http.StatusInternalServerError)
		return
	}
	etag := `"` + store.LeaderboardETag(rows) + `"`
	if r.Header.Get("If-None-Match") == etag {
		w.WriteHeader(http.StatusNotModified)
		return
	}
	w.Header().Set("ETag", etag)
	writeJSON(w, http.StatusOK, map[string]any{"entries": rows})
}

func (s *Server) handleRoomWS(w http.ResponseWriter, r *http.Request) {
	roomID := r.PathValue("roomId")
	token := r.URL.Query().Get("token")
	if token == "" {
		http.Error(w, "missing token", http.StatusUnauthorized)
		return
	}
	claims, err := s.auth.ParseRoomToken(token)
	if err != nil || claims.RoomID != roomID {
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}
	if _, err := s.store.GetRoom(r.Context(), roomID); err != nil {
		http.Error(w, "room not found", http.StatusNotFound)
		return
	}
	conn, err := s.rooms.Upgrade(w, r)
	if err != nil {
		return
	}
	s.rooms.Touch(roomID)
	if err := s.rooms.Join(roomID, claims.FriendCode, conn); err != nil {
		_ = conn.Close()
		return
	}
}

func (s *Server) publicWSBase(r *http.Request) string {
	if s.cfg.PublicWSBase != "" {
		return strings.TrimSuffix(s.cfg.PublicWSBase, "/")
	}
	scheme := "wss"
	if r.TLS == nil {
		if xf := r.Header.Get("X-Forwarded-Proto"); xf != "" {
			scheme = "wss"
			if xf == "http" {
				scheme = "ws"
			}
		} else {
			scheme = "ws"
		}
	}
	host := r.Host
	if xh := r.Header.Get("X-Forwarded-Host"); xh != "" {
		host = xh
	}
	return scheme + "://" + host
}

