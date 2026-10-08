package api

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/playbilling"
	"github.com/meanderpaul/archerypal-server/internal/room"
	"github.com/meanderpaul/archerypal-server/internal/store"
)

const testDeviceSecret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

func securityTestServer(t *testing.T, cfg Config) (*Server, *store.Store) {
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
	if cfg.UploadsDir == "" {
		cfg.UploadsDir = t.TempDir()
	}
	if cfg.AdminAPIKey == "" {
		cfg.AdminAPIKey = "test-admin-key"
	}
	au := auth.NewAuthenticator(st, "test-jwt-secret")
	rm := room.NewManager(8, time.Minute, nil)
	return NewServer(st, au, rm, cfg), st
}

func signDevice(t *testing.T, secret, method, path string, body []byte, nonce string) (ts, sig string) {
	t.Helper()
	ts = strconv.FormatInt(time.Now().Unix(), 10)
	key := sha256.Sum256([]byte(secret))
	bodyHash := sha256.Sum256(body)
	canonical := ts + nonce + method + path + hex.EncodeToString(bodyHash[:])
	mac := hmac.New(sha256.New, key[:])
	mac.Write([]byte(canonical))
	return ts, hex.EncodeToString(mac.Sum(nil))
}

func registerPlayer(t *testing.T, h http.Handler, code string) {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"friendCode":   code,
		"displayName":  "Tester",
		"deviceSecret": testDeviceSecret,
	})
	req := httptest.NewRequest(http.MethodPost, "/v1/players/register", bytes.NewReader(body))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("register %s: %d %s", code, rec.Code, rec.Body.String())
	}
}

func deviceRequest(t *testing.T, method, path, friendCode string, body []byte) *http.Request {
	t.Helper()
	nonce := ""
	if method != http.MethodGet && method != http.MethodHead {
		var b [16]byte
		_, _ = rand.Read(b[:])
		nonce = hex.EncodeToString(b[:])
	}
	ts, sig := signDevice(t, testDeviceSecret, method, path, body, nonce)
	req := httptest.NewRequest(method, "http://example.com"+path, bytes.NewReader(body))
	req.Header.Set(auth.HeaderFriendCode, friendCode)
	req.Header.Set(auth.HeaderTimestamp, ts)
	req.Header.Set(auth.HeaderSignature, sig)
	if nonce != "" {
		req.Header.Set(auth.HeaderNonce, nonce)
	}
	return req
}

func TestValidMediaKey(t *testing.T) {
	good := "0123456789abcdef0123456789abcdef.jpg"
	if !validMediaKey(good) {
		t.Fatal("expected valid key")
	}
	for _, bad := range []string{
		"",
		"../etc/passwd",
		`..\windows\system32`,
		"0123456789abcdef0123456789abcdef.jpg/../x",
		"nothex.jpg",
		"0123456789abcdef0123456789abcdef.gif",
		"0123456789abcdef0123456789abcdef.jpg\x00",
		"/tmp/0123456789abcdef0123456789abcdef.jpg",
	} {
		if validMediaKey(bad) {
			t.Fatalf("expected invalid: %q", bad)
		}
	}
}

func TestResolveUploadPath_RejectsTraversal(t *testing.T) {
	srv, _ := securityTestServer(t, Config{BypassPlusEntitlement: true})
	if _, err := srv.resolveUploadPath("../secret.jpg"); err == nil {
		t.Fatal("expected error")
	}
	key := "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.jpg"
	path, err := srv.resolveUploadPath(key)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasSuffix(path, key) {
		t.Fatalf("path=%s", path)
	}
}

func TestHandleGetMedia_InvalidKey(t *testing.T) {
	srv, _ := securityTestServer(t, Config{BypassPlusEntitlement: true})
	h := srv.Handler()
	registerPlayer(t, h, "AP-MED001")

	cases := []string{
		"/v1/media/not-a-valid-key.jpg",
		"/v1/media/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.gif",
		"/v1/media/zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz.jpg",
	}
	for _, path := range cases {
		body := []byte("")
		req := deviceRequest(t, http.MethodGet, path, "AP-MED001", body)
		rec := httptest.NewRecorder()
		h.ServeHTTP(rec, req)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("%s: expected 400, got %d body=%s", path, rec.Code, rec.Body.String())
		}
	}
}

func TestCreatePost_SQLiBodyStoredSafely(t *testing.T) {
	srv, st := securityTestServer(t, Config{BypassPlusEntitlement: true})
	h := srv.Handler()
	registerPlayer(t, h, "AP-SQL001")

	evil := `'; DROP TABLE posts;--`
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	_ = w.WriteField("body", evil)
	_ = w.WriteField("isPublic", "true")
	_ = w.Close()

	req := deviceRequest(t, http.MethodPost, "/v1/posts", "AP-SQL001", buf.Bytes())
	req.Header.Set("Content-Type", w.FormDataContentType())
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d %s", rec.Code, rec.Body.String())
	}

	posts, err := st.ListVisiblePosts(context.Background(), "AP-SQL001", 0)
	if err != nil {
		t.Fatal(err)
	}
	if len(posts) != 1 || posts[0].Body != evil {
		t.Fatalf("body not stored as plaintext: %+v", posts)
	}
	// Table still queryable — injection did not execute.
	if _, err := st.ListVisiblePosts(context.Background(), "AP-SQL001", 0); err != nil {
		t.Fatalf("posts table broken: %v", err)
	}
}

func tinyJPEG(t *testing.T) []byte {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, 8, 8))
	for y := 0; y < 8; y++ {
		for x := 0; x < 8; x++ {
			img.Set(x, y, color.RGBA{R: 200, G: 100, B: 50, A: 255})
		}
	}
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, img, &jpeg.Options{Quality: 90}); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func TestCreatePost_ImageSanitized(t *testing.T) {
	srv, _ := securityTestServer(t, Config{BypassPlusEntitlement: true})
	h := srv.Handler()
	registerPlayer(t, h, "AP-IMG001")

	jpegBytes := tinyJPEG(t)
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	_ = mw.WriteField("body", "range day")
	_ = mw.WriteField("isPublic", "true")
	part, err := mw.CreateFormFile("image", "shot.png")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := part.Write(jpegBytes); err != nil {
		t.Fatal(err)
	}
	_ = mw.Close()

	req := deviceRequest(t, http.MethodPost, "/v1/posts", "AP-IMG001", buf.Bytes())
	req.Header.Set("Content-Type", mw.FormDataContentType())
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d %s", rec.Code, rec.Body.String())
	}

	entries, err := os.ReadDir(srv.cfg.UploadsDir)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || !strings.HasSuffix(entries[0].Name(), ".jpg") {
		t.Fatalf("expected one .jpg upload, got %+v", entries)
	}
}

func TestCreatePost_RejectsNonImage(t *testing.T) {
	srv, _ := securityTestServer(t, Config{BypassPlusEntitlement: true})
	h := srv.Handler()
	registerPlayer(t, h, "AP-IMG002")

	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	_ = mw.WriteField("body", "bad file")
	part, _ := mw.CreateFormFile("image", "x.jpg")
	_, _ = part.Write([]byte("not an image at all"))
	_ = mw.Close()

	req := deviceRequest(t, http.MethodPost, "/v1/posts", "AP-IMG002", buf.Bytes())
	req.Header.Set("Content-Type", mw.FormDataContentType())
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d %s", rec.Code, rec.Body.String())
	}
}

func TestCreatePost_RejectsPNGPolyglotWithoutDecode(t *testing.T) {
	// Valid PNG magic but truncated / invalid for image.Decode after sniff.
	srv, _ := securityTestServer(t, Config{BypassPlusEntitlement: true})
	h := srv.Handler()
	registerPlayer(t, h, "AP-IMG003")

	poly := append([]byte{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, bytes.Repeat([]byte{0x00}, 64)...)
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	_ = mw.WriteField("body", "polyglot")
	part, _ := mw.CreateFormFile("image", "x.png")
	_, _ = io.Copy(part, bytes.NewReader(poly))
	_ = mw.Close()

	req := deviceRequest(t, http.MethodPost, "/v1/posts", "AP-IMG003", buf.Bytes())
	req.Header.Set("Content-Type", mw.FormDataContentType())
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for undecodable png, got %d %s", rec.Code, rec.Body.String())
	}
}

func TestSanitizePostImage_ScalesLargePNG(t *testing.T) {
	img := image.NewRGBA(image.Rect(0, 0, 4000, 2000))
	var pngBuf bytes.Buffer
	if err := png.Encode(&pngBuf, img); err != nil {
		t.Fatal(err)
	}
	out, err := sanitizePostImage(bytes.NewReader(pngBuf.Bytes()))
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := jpeg.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatal(err)
	}
	b := decoded.Bounds()
	if b.Dx() > maxImageEdgePx || b.Dy() > maxImageEdgePx {
		t.Fatalf("still too large: %dx%d", b.Dx(), b.Dy())
	}
}

func TestEntitlementSync_RejectedByDefault(t *testing.T) {
	srv, _ := securityTestServer(t, Config{BypassPlusEntitlement: false})
	h := srv.Handler()
	registerPlayer(t, h, "AP-ENT001")

	hash := sha256.Sum256([]byte("fake-token"))
	exp := time.Now().Add(24 * time.Hour).Unix()
	body, _ := json.Marshal(map[string]any{
		"sku":               store.PlusSKU,
		"purchaseTokenHash": hex.EncodeToString(hash[:]),
		"expiresAt":         exp,
	})
	req := deviceRequest(t, http.MethodPost, "/v1/entitlements/sync", "AP-ENT001", body)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403, got %d %s", rec.Code, rec.Body.String())
	}
}

type fakePlayVerifier struct {
	enabled bool
	result  *playbilling.Result
	err     error
	lastSKU string
	lastTok string
}

func (f *fakePlayVerifier) Enabled() bool { return f.enabled }

func (f *fakePlayVerifier) Verify(_ context.Context, sku, token string) (*playbilling.Result, error) {
	f.lastSKU = sku
	f.lastTok = token
	return f.result, f.err
}

func TestEntitlementSync_PlayVerified(t *testing.T) {
	exp := time.Now().Add(24 * time.Hour).Unix()
	fake := &fakePlayVerifier{
		enabled: true,
		result:  &playbilling.Result{SKU: store.PlusSKU, ExpiresAt: &exp},
	}
	srv, st := securityTestServer(t, Config{PlayVerifier: fake})
	h := srv.Handler()
	registerPlayer(t, h, "AP-ENTPL1")

	body, _ := json.Marshal(map[string]any{
		"sku":           store.PlusSKU,
		"purchaseToken": "play-token-abc",
	})
	req := deviceRequest(t, http.MethodPost, "/v1/entitlements/sync", "AP-ENTPL1", body)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d %s", rec.Code, rec.Body.String())
	}
	if fake.lastSKU != store.PlusSKU || fake.lastTok != "play-token-abc" {
		t.Fatalf("verify args: sku=%q token=%q", fake.lastSKU, fake.lastTok)
	}
	ok, err := st.HasActivePlus(context.Background(), "AP-ENTPL1")
	if err != nil || !ok {
		t.Fatalf("expected plus active: ok=%v err=%v", ok, err)
	}
}

func TestEntitlementSync_PlayVerifiedInactive(t *testing.T) {
	fake := &fakePlayVerifier{enabled: true, err: playbilling.ErrInactive}
	srv, _ := securityTestServer(t, Config{PlayVerifier: fake})
	h := srv.Handler()
	registerPlayer(t, h, "AP-ENTINA")

	body, _ := json.Marshal(map[string]any{
		"sku":           store.PlusLifetimeSKU,
		"purchaseToken": "expired-token",
	})
	req := deviceRequest(t, http.MethodPost, "/v1/entitlements/sync", "AP-ENTINA", body)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusForbidden {
		t.Fatalf("expected 403, got %d %s", rec.Code, rec.Body.String())
	}
}

func TestEntitlementSync_PlayVerifiedRequiresToken(t *testing.T) {
	fake := &fakePlayVerifier{enabled: true}
	srv, _ := securityTestServer(t, Config{PlayVerifier: fake})
	h := srv.Handler()
	registerPlayer(t, h, "AP-ENTTOK")

	hash := sha256.Sum256([]byte("fake-token"))
	body, _ := json.Marshal(map[string]any{
		"sku":               store.PlusSKU,
		"purchaseTokenHash": hex.EncodeToString(hash[:]),
	})
	req := deviceRequest(t, http.MethodPost, "/v1/entitlements/sync", "AP-ENTTOK", body)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d %s", rec.Code, rec.Body.String())
	}
}

func TestEntitlementSync_AllowedWhenUnverifiedFlag(t *testing.T) {
	srv, st := securityTestServer(t, Config{AllowUnverifiedEntitlementSync: true})
	h := srv.Handler()
	registerPlayer(t, h, "AP-ENT002")

	hash := sha256.Sum256([]byte("fake-token"))
	exp := time.Now().Add(24 * time.Hour).Unix()
	body, _ := json.Marshal(map[string]any{
		"sku":               store.PlusSKU,
		"purchaseTokenHash": hex.EncodeToString(hash[:]),
		"expiresAt":         exp,
	})
	req := deviceRequest(t, http.MethodPost, "/v1/entitlements/sync", "AP-ENT002", body)
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d %s", rec.Code, rec.Body.String())
	}
	ok, err := st.HasActiveEntitlement(context.Background(), "AP-ENT002", store.PlusSKU)
	if err != nil || !ok {
		t.Fatalf("entitlement not stored: ok=%v err=%v", ok, err)
	}
}

func TestClientIP_IgnoresXFFUnlessTrusted(t *testing.T) {
	srv, _ := securityTestServer(t, Config{})
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	req.RemoteAddr = "10.0.0.5:1234"
	req.Header.Set("X-Forwarded-For", "1.2.3.4")
	if ip := srv.clientIP(req); ip != "10.0.0.5" {
		t.Fatalf("untrusted XFF should be ignored, got %s", ip)
	}
	srv.cfg.TrustForwardedFor = true
	if ip := srv.clientIP(req); ip != "1.2.3.4" {
		t.Fatalf("trusted XFF expected 1.2.3.4, got %s", ip)
	}
}

func TestFeedRadiusKmCapped(t *testing.T) {
	srv, _ := securityTestServer(t, Config{BypassPlusEntitlement: true})
	h := srv.Handler()
	registerPlayer(t, h, "AP-RAD001")
	body := []byte("")
	req := deviceRequest(t, http.MethodGet, "/v1/feed?radiusKm=99999&lat=40&lng=-75", "AP-RAD001", body)
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d %s", rec.Code, rec.Body.String())
	}
}

func TestUploadQuota(t *testing.T) {
	q := newUploadQuotaTracker()
	if !q.allow("AP-Q1", 1000) {
		t.Fatal("first should allow")
	}
	for i := 0; i < maxImagePostsPerDay-1; i++ {
		if !q.allow("AP-Q1", 100) {
			t.Fatalf("allow failed at %d", i)
		}
	}
	if q.allow("AP-Q1", 1) {
		t.Fatal("expected daily count exceeded")
	}
}
