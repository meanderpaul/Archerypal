package api

import (
	"crypto/subtle"
	"net/http"
)

const maxAdminBodyBytes = 1 << 20 // 1 MiB

func (s *Server) requireAdmin(w http.ResponseWriter, r *http.Request) bool {
	if s.cfg.AdminAPIKey == "" {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return false
	}
	got := r.Header.Get("X-Admin-Key")
	if subtle.ConstantTimeCompare([]byte(got), []byte(s.cfg.AdminAPIKey)) != 1 {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return false
	}
	if s.adminLimiter != nil && !s.adminLimiter.allow(s.clientIP(r)) {
		http.Error(w, "too many requests", http.StatusTooManyRequests)
		return false
	}
	return true
}

// limitAdminBody caps request bodies on admin routes (they bypass device-auth limits).
func limitAdminBody(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxAdminBodyBytes)
}
