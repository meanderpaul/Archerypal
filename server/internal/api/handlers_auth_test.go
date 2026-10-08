package api

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/room"
	"github.com/meanderpaul/archerypal-server/internal/store"
)

func testServer(t *testing.T) (*Server, *store.Store) {
	t.Helper()
	dir := t.TempDir()
	st, err := store.Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	migrations := filepath.Join("..", "..", "migrations")
	if err := st.Migrate(migrations); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	t.Cleanup(func() { _ = st.Close() })
	au := auth.NewAuthenticator(st, "test-jwt-secret")
	rm := room.NewManager(8, time.Minute, nil)
	srv := NewServer(st, au, rm, Config{
		AdminAPIKey: "test-admin-key",
		UploadsDir:  t.TempDir(),
	})
	return srv, st
}

func TestHandleRegister_Validation(t *testing.T) {
	srv, _ := testServer(t)
	handler := srv.Handler()

	body := `{"friendCode":"bad","displayName":"x","deviceSecret":"short"}`
	req := httptest.NewRequest(http.MethodPost, "/v1/players/register", bytes.NewReader([]byte(body)))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d body=%s", rec.Code, rec.Body.String())
	}
}

func TestHandleRegister_Success(t *testing.T) {
	srv, st := testServer(t)
	handler := srv.Handler()

	secret := "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	body, _ := json.Marshal(map[string]string{
		"friendCode":   "AP-REG001",
		"displayName":  "Archer",
		"deviceSecret": secret,
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/players/register", bytes.NewReader(body))
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", rec.Code, rec.Body.String())
	}
	if _, err := st.GetPlayer(context.Background(), "AP-REG001"); err != nil {
		t.Fatalf("player not registered: %v", err)
	}
}

func TestRequireAdmin_ConstantTime(t *testing.T) {
	srv, _ := testServer(t)
	handler := srv.Handler()

	req := httptest.NewRequest(http.MethodPost, "/v1/admin/shoots", bytes.NewReader([]byte(`{}`)))
	req.Header.Set("X-Admin-Key", "wrong-key")
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", rec.Code)
	}
}

func TestRateLimitRegister(t *testing.T) {
	srv, _ := testServer(t)
	srv.registerLimiter = newRateLimiter(1, time.Minute)
	handler := srv.Handler()

	secret := "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	payload, _ := json.Marshal(map[string]string{
		"friendCode":   "AP-RL001",
		"displayName":  "One",
		"deviceSecret": secret,
	})
	req1 := httptest.NewRequest(http.MethodPost, "/v1/players/register", bytes.NewReader(payload))
	rec1 := httptest.NewRecorder()
	handler.ServeHTTP(rec1, req1)

	payload2, _ := json.Marshal(map[string]string{
		"friendCode":   "AP-RL002",
		"displayName":  "Two",
		"deviceSecret": secret,
	})
	req2 := httptest.NewRequest(http.MethodPost, "/v1/players/register", bytes.NewReader(payload2))
	rec2 := httptest.NewRecorder()
	handler.ServeHTTP(rec2, req2)

	if rec2.Code != http.StatusTooManyRequests {
		t.Fatalf("expected 429 on second register, got %d", rec2.Code)
	}
}
