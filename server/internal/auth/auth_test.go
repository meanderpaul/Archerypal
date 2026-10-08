package auth

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	"github.com/meanderpaul/archerypal-server/internal/store"
)

func testStore(t *testing.T) *store.Store {
	t.Helper()
	dir := t.TempDir()
	st, err := store.Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	if err := st.Migrate(filepath.Join("..", "..", "migrations")); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	t.Cleanup(func() { _ = st.Close() })
	return st
}

func signRequestAt(tsRaw, secret, friendCode, method, path string, body []byte, nonce string) (sig string) {
	key := sha256.Sum256([]byte(secret))
	bodyHash := sha256.Sum256(body)
	canonical := tsRaw + nonce + method + path + hex.EncodeToString(bodyHash[:])
	mac := hmac.New(sha256.New, key[:])
	mac.Write([]byte(canonical))
	return hex.EncodeToString(mac.Sum(nil))
}

func TestVerifyDeviceRequest_QueryStringInSignature(t *testing.T) {
	st := testStore(t)
	secret := "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	if err := st.RegisterPlayer(context.Background(), "AP-TEST01", "Tester", secret, nil); err != nil {
		t.Fatalf("register: %v", err)
	}
	au := NewAuthenticator(st, "jwt-test-secret")

	body := []byte("")
	path := "/v1/shoots?from=1&to=2"
	tsRaw := strconvFormatInt(time.Now().Unix())
	sig := signRequestAt(tsRaw, secret, "AP-TEST01", http.MethodGet, path, body, "")

	req := httptest.NewRequest(http.MethodGet, "http://example.com"+path, bytes.NewReader(body))
	req.Header.Set(HeaderFriendCode, "AP-TEST01")
	req.Header.Set(HeaderTimestamp, tsRaw)
	req.Header.Set(HeaderSignature, sig)
	ctx := WithBodyBytes(req.Context(), body)
	req = req.WithContext(ctx)

	rec := httptest.NewRecorder()
	called := false
	au.DeviceAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
	})).ServeHTTP(rec, req)

	if !called || rec.Code != http.StatusOK {
		t.Fatalf("expected auth ok, code=%d called=%v", rec.Code, called)
	}
}

func TestVerifyDeviceRequest_RequiresNonceOnPost(t *testing.T) {
	st := testStore(t)
	secret := "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	if err := st.RegisterPlayer(context.Background(), "AP-TEST02", "Tester", secret, nil); err != nil {
		t.Fatalf("register: %v", err)
	}
	au := NewAuthenticator(st, "jwt-test-secret")

	body := []byte(`{"theirFriendCode":"AP-OTHER1"}`)
	path := "/v1/friends/add"
	tsRaw := strconvFormatInt(time.Now().Unix())
	sig := signRequestAt(tsRaw, secret, "AP-TEST02", http.MethodPost, path, body, "")

	req := httptest.NewRequest(http.MethodPost, "http://example.com"+path, bytes.NewReader(body))
	req.Header.Set(HeaderFriendCode, "AP-TEST02")
	req.Header.Set(HeaderTimestamp, tsRaw)
	req.Header.Set(HeaderSignature, sig)
	ctx := WithBodyBytes(req.Context(), body)
	req = req.WithContext(ctx)

	rec := httptest.NewRecorder()
	au.DeviceAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {})).ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 without nonce, got %d", rec.Code)
	}
}

func TestVerifyDeviceRequest_NonceReplayRejected(t *testing.T) {
	st := testStore(t)
	secret := "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	if err := st.RegisterPlayer(context.Background(), "AP-TEST03", "Tester", secret, nil); err != nil {
		t.Fatalf("register: %v", err)
	}
	au := NewAuthenticator(st, "jwt-test-secret")

	body := []byte(`{}`)
	path := "/v1/players/me"
	nonce := "11111111-2222-3333-4444-555555555555"
	tsRaw := strconvFormatInt(time.Now().Unix())
	sig := signRequestAt(tsRaw, secret, "AP-TEST03", http.MethodPatch, path, body, nonce)

	doReq := func() *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPatch, "http://example.com"+path, bytes.NewReader(body))
		req.Header.Set(HeaderFriendCode, "AP-TEST03")
		req.Header.Set(HeaderTimestamp, tsRaw)
		req.Header.Set(HeaderSignature, sig)
		req.Header.Set(HeaderNonce, nonce)
		ctx := WithBodyBytes(req.Context(), body)
		req = req.WithContext(ctx)
		rec := httptest.NewRecorder()
		au.DeviceAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusOK)
		})).ServeHTTP(rec, req)
		return rec
	}

	first := doReq()
	if first.Code != http.StatusOK {
		t.Fatalf("first request expected 200, got %d", first.Code)
	}
	second := doReq()
	if second.Code != http.StatusUnauthorized {
		t.Fatalf("replay expected 401, got %d", second.Code)
	}
}

func TestVerifyDeviceRequest_TimestampSkew(t *testing.T) {
	st := testStore(t)
	secret := "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	if err := st.RegisterPlayer(context.Background(), "AP-TEST04", "Tester", secret, nil); err != nil {
		t.Fatalf("register: %v", err)
	}
	au := NewAuthenticator(st, "jwt-test-secret")

	body := []byte("")
	path := "/v1/friends"
	staleTS := time.Now().Add(-10 * time.Minute).Unix()
	tsRaw := strconvFormatInt(staleTS)
	key := sha256.Sum256([]byte(secret))
	bodyHash := sha256.Sum256(body)
	canonical := tsRaw + http.MethodGet + path + hex.EncodeToString(bodyHash[:])
	mac := hmac.New(sha256.New, key[:])
	mac.Write([]byte(canonical))
	sig := hex.EncodeToString(mac.Sum(nil))

	req := httptest.NewRequest(http.MethodGet, "http://example.com"+path, bytes.NewReader(body))
	req.Header.Set(HeaderFriendCode, "AP-TEST04")
	req.Header.Set(HeaderTimestamp, tsRaw)
	req.Header.Set(HeaderSignature, sig)
	ctx := WithBodyBytes(req.Context(), body)
	req = req.WithContext(ctx)

	rec := httptest.NewRecorder()
	au.DeviceAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {})).ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 for stale timestamp, got %d", rec.Code)
	}
}

func strconvFormatInt(v int64) string {
	return strconv.FormatInt(v, 10)
}