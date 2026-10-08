package main

import (
	"bufio"
	"fmt"
	"net/http"
	"strings"
	"time"
)

func fetchICalSource(client *http.Client, src sourceConfig) ([]adminShootPayload, error) {
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
	body, err := readLimited(resp.Body, 4<<20)
	if err != nil {
		return nil, err
	}
	events, err := parseICal(string(body), src)
	if err != nil {
		return nil, err
	}
	return dedupePayloads(events), nil
}

type icalEvent struct {
	UID         string
	Summary     string
	Description string
	Location    string
	DTStart     time.Time
	DTEnd       *time.Time
	URL         string
}

func parseICal(raw string, src sourceConfig) ([]adminShootPayload, error) {
	events := unfoldICal(raw)
	var out []adminShootPayload
	for _, ev := range events {
		if ev.Summary == "" || ev.DTStart.IsZero() {
			continue
		}
		if ev.DTStart.Before(time.Now().Add(-48 * time.Hour)) {
			continue
		}
		id := ev.UID
		if id == "" {
			id = stableID(src.ID, ev.Summary, ev.DTStart)
		} else {
			id = fmt.Sprintf("%s-%s", src.ID, sanitizeID(id))
		}
		loc := ev.Location
		if loc == "" {
			loc = src.Region
		}
		desc := strings.TrimSpace(ev.Description)
		if desc == "" {
			desc = fmt.Sprintf("Imported from %s.", src.URL)
		}
		p := adminShootPayload{
			ID:           id,
			Title:        ev.Summary,
			Description:  desc,
			LocationName: loc,
			Region:       src.Region,
			StartsAt:     ev.DTStart.UnixMilli(),
			SourceURL:    firstNonEmpty(ev.URL, src.URL),
			EventType:    guessEventType(ev.Summary),
		}
		if ev.DTEnd != nil {
			ms := ev.DTEnd.UnixMilli()
			p.EndsAt = &ms
		}
		if ev.URL != "" {
			p.RegistrationURL = ev.URL
		}
		out = append(out, p)
	}
	return out, nil
}

func sanitizeID(s string) string {
	s = strings.ToLower(s)
	repl := strings.NewReplacer("/", "-", ":", "-", "@", "-", " ", "-")
	return repl.Replace(s)
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

func unfoldICal(raw string) []icalEvent {
	sc := bufio.NewScanner(strings.NewReader(raw))
	sc.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	var events []icalEvent
	var cur *icalEvent
	var inEvent bool

	flush := func() {
		if cur != nil && inEvent {
			events = append(events, *cur)
		}
		cur = nil
		inEvent = false
	}

	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" {
			continue
		}
		if strings.HasPrefix(line, " ") || strings.HasPrefix(line, "\t") {
			continue
		}
		if strings.Contains(line, ":") && !strings.HasPrefix(line, "BEGIN:") && !strings.HasPrefix(line, "END:") {
			// unfolded continuation handled below via simple join not needed for v1
		}
		key, val := splitICalLine(line)
		switch key {
		case "BEGIN":
			if val == "VEVENT" {
				inEvent = true
				cur = &icalEvent{}
			}
		case "END":
			if val == "VEVENT" {
				flush()
			}
		case "UID":
			if cur != nil {
				cur.UID = val
			}
		case "SUMMARY":
			if cur != nil {
				cur.Summary = val
			}
		case "DESCRIPTION":
			if cur != nil {
				cur.Description = val
			}
		case "LOCATION":
			if cur != nil {
				cur.Location = val
			}
		case "URL":
			if cur != nil {
				cur.URL = val
			}
		case "DTSTART", "DTSTART;VALUE=DATE":
			if cur != nil {
				if t, ok := parseICalTime(val); ok {
					cur.DTStart = t
				}
			}
		case "DTEND", "DTEND;VALUE=DATE":
			if cur != nil {
				if t, ok := parseICalTime(val); ok {
					cur.DTEnd = &t
				}
			}
		}
	}
	flush()
	return events
}

func splitICalLine(line string) (string, string) {
	// KEY;PARAM=value or KEY:value
	colon := strings.Index(line, ":")
	if colon < 0 {
		return line, ""
	}
	keyPart := line[:colon]
	val := line[colon+1:]
	if semi := strings.Index(keyPart, ";"); semi >= 0 {
		keyPart = keyPart[:semi]
	}
	return strings.ToUpper(strings.TrimSpace(keyPart)), strings.TrimSpace(val)
}

func parseICalTime(s string) (time.Time, bool) {
	s = strings.TrimSpace(s)
	if len(s) == 8 {
		t, err := time.Parse("20060102", s)
		return t.UTC(), err == nil
	}
	if strings.HasSuffix(s, "Z") {
		t, err := time.Parse("20060102T150405Z", s)
		return t.UTC(), err == nil
	}
	t, err := time.Parse("20060102T150405", s)
	return t.UTC(), err == nil
}
