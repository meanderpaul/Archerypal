package api

import (
	"sync"
	"time"
)

const (
	maxImagePostsPerDay = 20
	maxImageBytesPerDay = 50 << 20 // 50 MiB
)

type dayQuota struct {
	day   string // UTC YYYY-MM-DD
	count int
	bytes int64
}

type uploadQuotaTracker struct {
	mu   sync.Mutex
	byID map[string]*dayQuota
}

func newUploadQuotaTracker() *uploadQuotaTracker {
	return &uploadQuotaTracker{byID: make(map[string]*dayQuota)}
}

func utcDay(t time.Time) string {
	return t.UTC().Format("2006-01-02")
}

// allow reports whether friendCode may upload nbytes more today, and records it if allowed.
func (q *uploadQuotaTracker) allow(friendCode string, nbytes int64) bool {
	if friendCode == "" || nbytes < 0 {
		return false
	}
	now := time.Now()
	day := utcDay(now)
	q.mu.Lock()
	defer q.mu.Unlock()
	cur := q.byID[friendCode]
	if cur == nil || cur.day != day {
		cur = &dayQuota{day: day}
		q.byID[friendCode] = cur
	}
	if cur.count >= maxImagePostsPerDay {
		return false
	}
	if cur.bytes+nbytes > maxImageBytesPerDay {
		return false
	}
	cur.count++
	cur.bytes += nbytes
	return true
}
