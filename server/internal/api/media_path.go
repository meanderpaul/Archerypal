package api

import (
	"errors"
	"path/filepath"
	"regexp"
	"strings"
)

var (
	errInvalidMediaKey = errors.New("invalid media key")
	mediaKeyRe         = regexp.MustCompile(`^[a-f0-9]{32}\.(jpe?g|png)$`)
)

// validMediaKey allowlists server-generated upload keys only.
func validMediaKey(key string) bool {
	if key == "" || strings.ContainsRune(key, 0) {
		return false
	}
	if key != filepath.Base(key) {
		return false
	}
	return mediaKeyRe.MatchString(key)
}

// resolveUploadPath joins key under UploadsDir and rejects any escape.
func (s *Server) resolveUploadPath(key string) (string, error) {
	if !validMediaKey(key) {
		return "", errInvalidMediaKey
	}
	base := filepath.Clean(s.cfg.UploadsDir)
	full := filepath.Join(base, filepath.Base(key))
	rel, err := filepath.Rel(base, full)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(filepath.Separator)) {
		return "", errInvalidMediaKey
	}
	return full, nil
}
