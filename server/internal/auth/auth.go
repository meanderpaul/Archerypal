package auth

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/meanderpaul/archerypal-server/internal/store"
)

const (
	HeaderFriendCode = "X-Friend-Code"
	HeaderTimestamp  = "X-Timestamp"
	HeaderSignature  = "X-Device-Signature"
	HeaderNonce      = "X-Nonce"
	maxSkew          = 5 * time.Minute
)

type RoomClaims struct {
	RoomID     string `json:"room_id"`
	FriendCode string `json:"friend_code"`
	Host       bool   `json:"host"`
	jwt.RegisteredClaims
}

type Authenticator struct {
	store  *store.Store
	secret []byte
}

func NewAuthenticator(st *store.Store, jwtSecret string) *Authenticator {
	return &Authenticator{store: st, secret: []byte(jwtSecret)}
}

func (a *Authenticator) DeviceAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		friendCode := strings.TrimSpace(r.Header.Get(HeaderFriendCode))
		if friendCode == "" {
			logAuthFailure(friendCode, r, "missing friend code")
			http.Error(w, "missing friend code", http.StatusUnauthorized)
			return
		}
		player, err := a.store.GetPlayer(r.Context(), friendCode)
		if err != nil {
			logAuthFailure(friendCode, r, "unknown player")
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if err := a.verifyDeviceRequest(r, player.DeviceSecretHash); err != nil {
			logAuthFailure(friendCode, r, err.Error())
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		ctx := context.WithValue(r.Context(), friendCodeKey{}, friendCode)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

type friendCodeKey struct{}

func FriendCodeFromContext(ctx context.Context) string {
	v, _ := ctx.Value(friendCodeKey{}).(string)
	return v
}

func isMutatingMethod(method string) bool {
	switch method {
	case http.MethodPost, http.MethodPut, http.MethodPatch, http.MethodDelete:
		return true
	default:
		return false
	}
}

func signPath(r *http.Request) string {
	path := r.URL.EscapedPath()
	if r.URL.RawQuery != "" {
		path += "?" + r.URL.RawQuery
	}
	return path
}

func (a *Authenticator) verifyDeviceRequest(r *http.Request, secretHash []byte) error {
	tsRaw := strings.TrimSpace(r.Header.Get(HeaderTimestamp))
	sigHex := strings.TrimSpace(r.Header.Get(HeaderSignature))
	if tsRaw == "" || sigHex == "" {
		return errors.New("missing auth headers")
	}
	tsInt, err := strconv.ParseInt(tsRaw, 10, 64)
	if err != nil {
		return err
	}
	ts := time.Unix(tsInt, 0)
	now := time.Now()
	if ts.After(now.Add(maxSkew)) || ts.Before(now.Add(-maxSkew)) {
		return errors.New("timestamp out of range")
	}

	nonce := ""
	if isMutatingMethod(r.Method) {
		nonce = strings.TrimSpace(r.Header.Get(HeaderNonce))
		if nonce == "" {
			return errors.New("missing nonce")
		}
	}

	bodyHash := sha256.Sum256(readBodyHashPlaceholder(r))
	canonical := fmt.Sprintf("%s%s%s%s%s", tsRaw, nonce, r.Method, signPath(r), hex.EncodeToString(bodyHash[:]))

	mac := hmac.New(sha256.New, secretHash)
	mac.Write([]byte(canonical))
	expected := mac.Sum(nil)

	got, err := hex.DecodeString(sigHex)
	if err != nil {
		return err
	}
	if !hmac.Equal(expected, got) {
		return errors.New("bad signature")
	}

	if nonce != "" {
		expiresAt := ts.Add(maxSkew)
		if err := a.store.ConsumeNonce(r.Context(), strings.TrimSpace(r.Header.Get(HeaderFriendCode)), nonce, expiresAt); err != nil {
			if errors.Is(err, store.ErrConflict) {
				return errors.New("nonce replay")
			}
			return err
		}
	}
	return nil
}

func logAuthFailure(friendCode string, r *http.Request, reason string) {
	log.Printf("auth failure: friend=%s method=%s path=%s reason=%s", friendCode, r.Method, r.URL.Path, reason)
}

// BodyHashForRequest returns SHA-256 hex of the request body (handlers pass bytes read once).
func BodyHash(body []byte) string {
	sum := sha256.Sum256(body)
	return hex.EncodeToString(sum[:])
}

func readBodyHashPlaceholder(r *http.Request) []byte {
	if b, ok := r.Context().Value(bodyBytesKey{}).([]byte); ok {
		return b
	}
	return nil
}

type bodyBytesKey struct{}

func WithBodyBytes(ctx context.Context, body []byte) context.Context {
	return context.WithValue(ctx, bodyBytesKey{}, body)
}

func (a *Authenticator) IssueRoomToken(roomID, friendCode string, host bool, ttl time.Duration) (string, error) {
	now := time.Now()
	claims := RoomClaims{
		RoomID:     roomID,
		FriendCode: friendCode,
		Host:       host,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(now.Add(ttl)),
			IssuedAt:  jwt.NewNumericDate(now),
			Subject:   friendCode,
		},
	}
	t := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return t.SignedString(a.secret)
}

func (a *Authenticator) ParseRoomToken(tokenStr string) (*RoomClaims, error) {
	t, err := jwt.ParseWithClaims(tokenStr, &RoomClaims{}, func(t *jwt.Token) (any, error) {
		if t.Method != jwt.SigningMethodHS256 {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return a.secret, nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := t.Claims.(*RoomClaims)
	if !ok || !t.Valid {
		return nil, errors.New("invalid token")
	}
	return claims, nil
}
