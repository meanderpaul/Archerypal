package validate

import (
	"net/url"
	"regexp"
	"strings"
	"unicode/utf8"
)

var friendCodeRe = regexp.MustCompile(`^AP-[A-Z0-9]{6}$`)

const (
	MaxDisplayName = 64
	MaxBio         = 2000
	MaxPostBody    = 2000
	MaxLimit       = 100
	MinDeviceSecretLen = 32
)

func FriendCode(code string) bool {
	return friendCodeRe.MatchString(strings.TrimSpace(code))
}

func DisplayName(name string) (string, bool) {
	name = strings.TrimSpace(name)
	if name == "" || utf8.RuneCountInString(name) > MaxDisplayName {
		return "", false
	}
	return name, true
}

func DeviceSecret(secret string) bool {
	if len(secret) < MinDeviceSecretLen {
		return false
	}
	for _, c := range secret {
		if (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') {
			continue
		}
		return false
	}
	return true
}

func TextField(s string, max int) (string, bool) {
	s = strings.TrimSpace(s)
	if utf8.RuneCountInString(s) > max {
		return "", false
	}
	return s, true
}

// SanitizePostBody strips C0 controls except tab/newline (XSS / binary junk defense).
func SanitizePostBody(s string) string {
	s = strings.ReplaceAll(s, "\r\n", "\n")
	s = strings.ReplaceAll(s, "\r", "\n")
	var b strings.Builder
	b.Grow(len(s))
	for _, r := range s {
		if r == '\t' || r == '\n' {
			b.WriteRune(r)
			continue
		}
		if r < 0x20 || r == 0x7f {
			continue
		}
		b.WriteRune(r)
	}
	return b.String()
}

func PostBody(s string) (string, bool) {
	s = strings.TrimSpace(SanitizePostBody(s))
	if s == "" || utf8.RuneCountInString(s) > MaxPostBody {
		return "", false
	}
	return s, true
}

func HTTPSURL(raw string) bool {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return true
	}
	u, err := url.Parse(raw)
	if err != nil {
		return false
	}
	return u.Scheme == "https" && u.Host != ""
}

func Latitude(v *float64) bool {
	if v == nil {
		return true
	}
	return *v >= -90 && *v <= 90
}

func Longitude(v *float64) bool {
	if v == nil {
		return true
	}
	return *v >= -180 && *v <= 180
}

func Limit(n int) int {
	if n <= 0 {
		return 50
	}
	if n > MaxLimit {
		return MaxLimit
	}
	return n
}
