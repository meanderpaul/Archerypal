package main

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"

	"gopkg.in/yaml.v3"
)

type config struct {
	ManualFile string         `yaml:"manual_file"`
	Sources    []sourceConfig `yaml:"sources"`
}

type sourceConfig struct {
	ID     string `yaml:"id"`
	Type   string `yaml:"type"`
	URL    string `yaml:"url"`
	Region string `yaml:"region"`
}

func loadConfig(path string) (*config, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var cfg config
	if err := yaml.Unmarshal(b, &cfg); err != nil {
		return nil, err
	}
	return &cfg, nil
}

func fetchSource(client *http.Client, src sourceConfig) ([]adminShootPayload, error) {
	switch strings.ToLower(strings.TrimSpace(src.Type)) {
	case "ical", "ics":
		return fetchICalSource(client, src)
	case "manual-inline":
		return nil, nil
	default:
		return fetchHTMLSource(client, src)
	}
}

var slugRe = regexp.MustCompile(`[^a-z0-9]+`)

func stableID(sourceID, title string, startsAt time.Time) string {
	slug := slugRe.ReplaceAllString(strings.ToLower(title), "-")
	slug = strings.Trim(slug, "-")
	if len(slug) > 40 {
		slug = slug[:40]
	}
	return fmt.Sprintf("%s-%s-%s", sourceID, slug, startsAt.Format("2006-01-02"))
}

func fetchHTMLSource(client *http.Client, src sourceConfig) ([]adminShootPayload, error) {
	if src.URL == "" {
		return nil, fmt.Errorf("url required")
	}
	resp, err := client.Get(src.URL)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("http %d", resp.StatusCode)
	}
	body, err := readLimited(resp.Body, 2<<20)
	if err != nil {
		return nil, err
	}
	html := string(body)
	events := parseGenericHTMLEvents(html, src)
	return events, nil
}

// parseGenericHTMLEvents extracts events from common patterns: h2/h3 headings with dates nearby.
func parseGenericHTMLEvents(html string, src sourceConfig) []adminShootPayload {
	titleRe := regexp.MustCompile(`(?i)<h[23][^>]*>([^<]+)</h[23]>`)
	dateRe := regexp.MustCompile(`(?i)(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{1,2},?\s+\d{4}`)
	titles := titleRe.FindAllStringSubmatch(html, -1)
	dates := dateRe.FindAllString(html, -1)
	if len(titles) == 0 || len(dates) == 0 {
		return nil
	}
	n := len(titles)
	if len(dates) < n {
		n = len(dates)
	}
	var out []adminShootPayload
	for i := 0; i < n; i++ {
		title := strings.TrimSpace(stripTags(titles[i][1]))
		if title == "" || isNoiseTitle(title) {
			continue
		}
		startsAt, ok := parseFlexibleDate(dates[i])
		if !ok || startsAt.Before(time.Now().Add(-48*time.Hour)) {
			continue
		}
		id := stableID(src.ID, title, startsAt)
		desc := fmt.Sprintf("Imported from %s — check the club site for registration details.", src.URL)
		out = append(out, adminShootPayload{
			ID:           id,
			Title:        title,
			Description:  desc,
			LocationName: src.Region,
			Region:       src.Region,
			StartsAt:     startsAt.UnixMilli(),
			SourceURL:    src.URL,
			EventType:    guessEventType(title),
		})
	}
	return dedupePayloads(out)
}

func isNoiseTitle(title string) bool {
	lower := strings.ToLower(title)
	noise := []string{"menu", "navigation", "footer", "header", "contact", "about us", "home"}
	for _, n := range noise {
		if lower == n {
			return true
		}
	}
	return len(title) < 4
}

func guessEventType(title string) string {
	lower := strings.ToLower(title)
	switch {
	case strings.Contains(lower, "3d"):
		return "3d"
	case strings.Contains(lower, "indoor"):
		return "indoor"
	case strings.Contains(lower, "target"):
		return "target"
	case strings.Contains(lower, "field"):
		return "field"
	default:
		return "club"
	}
}

func stripTags(s string) string {
	re := regexp.MustCompile(`<[^>]+>`)
	return re.ReplaceAllString(s, "")
}

func dedupePayloads(in []adminShootPayload) []adminShootPayload {
	seen := map[string]struct{}{}
	var out []adminShootPayload
	for _, p := range in {
		if _, ok := seen[p.ID]; ok {
			continue
		}
		seen[p.ID] = struct{}{}
		out = append(out, p)
	}
	return out
}

func readLimited(r interface{ Read([]byte) (int, error) }, max int64) ([]byte, error) {
	buf := make([]byte, 0, 4096)
	tmp := make([]byte, 4096)
	var total int64
	for {
		n, err := r.Read(tmp)
		if n > 0 {
			total += int64(n)
			if total > max {
				return nil, fmt.Errorf("response too large")
			}
			buf = append(buf, tmp[:n]...)
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return buf, err
		}
	}
	return buf, nil
}

func parseFlexibleDate(s string) (time.Time, bool) {
	s = strings.TrimSpace(s)
	formats := []string{
		"January 2, 2006",
		"Jan 2, 2006",
		"January 2 2006",
		"Jan 2 2006",
	}
	for _, f := range formats {
		if t, err := time.Parse(f, s); err == nil {
			return t.UTC(), true
		}
	}
	return time.Time{}, false
}
