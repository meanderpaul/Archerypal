package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

type manualShoot struct {
	ID              string `json:"id"`
	Title           string `json:"title"`
	Description     string `json:"description"`
	LocationName    string `json:"locationName"`
	Region          string `json:"region"`
	StartsAt        int64  `json:"startsAt"`
	EndsAt          *int64 `json:"endsAt"`
	SourceURL       string `json:"sourceUrl"`
	RegistrationURL string `json:"registrationUrl"`
	EventType       string `json:"eventType"`
}

type adminShootPayload struct {
	ID              string `json:"id"`
	Title           string `json:"title"`
	Description     string `json:"description"`
	LocationName    string `json:"locationName"`
	Region          string `json:"region"`
	StartsAt        int64  `json:"startsAt"`
	EndsAt          *int64 `json:"endsAt,omitempty"`
	SourceURL       string `json:"sourceUrl,omitempty"`
	RegistrationURL string `json:"registrationUrl,omitempty"`
	EventType       string `json:"eventType,omitempty"`
}

func main() {
	configPath := flag.String("config", "deploy/shoot-sources.yaml", "path to shoot sources config")
	dryRun := flag.Bool("dry-run", false, "print actions without calling admin API")
	flag.Parse()

	cfg, err := loadConfig(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	serverURL := envOr("MATCH_SERVER_URL", "https://archerypal-api.pauljreedii.workers.dev")
	adminKey := os.Getenv("ADMIN_API_KEY")
	if adminKey == "" && !*dryRun {
		log.Fatal("ADMIN_API_KEY required (set in env or /etc/archerypal/config.env)")
	}

	client := &http.Client{Timeout: 45 * time.Second}

	var all []adminShootPayload
	seen := map[string]struct{}{}

	if cfg.ManualFile != "" {
		manual, err := loadManualFile(cfg.ManualFile)
		if err != nil {
			log.Printf("manual file: %v", err)
		} else {
			for _, m := range manual {
				p := manualToPayload(m)
				if p.ID == "" {
					continue
				}
				all = append(all, p)
				seen[p.ID] = struct{}{}
			}
			log.Printf("loaded %d manual shoot(s)", len(manual))
		}
	}

	for _, src := range cfg.Sources {
		events, err := fetchSource(client, src)
		if err != nil {
			log.Printf("source %q: %v", src.ID, err)
			continue
		}
		for _, ev := range events {
			if ev.ID == "" {
				continue
			}
			if _, ok := seen[ev.ID]; ok {
				continue
			}
			all = append(all, ev)
			seen[ev.ID] = struct{}{}
		}
		log.Printf("source %q: %d event(s)", src.ID, len(events))
	}

	if len(all) == 0 {
		log.Println("no shoots to sync")
		return
	}

	keepIDs := make([]string, 0, len(all))
	upserted := 0
	for _, sh := range all {
		keepIDs = append(keepIDs, sh.ID)
		if *dryRun {
			log.Printf("would upsert: %s — %s", sh.ID, sh.Title)
			continue
		}
		if err := postAdminShoot(client, serverURL, adminKey, sh); err != nil {
			log.Printf("upsert %q: %v", sh.ID, err)
			continue
		}
		upserted++
	}

	if *dryRun {
		log.Printf("dry-run complete: %d shoot(s)", len(all))
		return
	}

	cancelled, err := cancelStale(client, serverURL, adminKey, keepIDs)
	if err != nil {
		log.Printf("cancel stale: %v", err)
	} else if cancelled > 0 {
		log.Printf("cancelled %d stale curated shoot(s)", cancelled)
	}
	log.Printf("sync complete: upserted %d, total %d", upserted, len(all))
}

func loadManualFile(path string) ([]manualShoot, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var out []manualShoot
	if err := json.Unmarshal(b, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func manualToPayload(m manualShoot) adminShootPayload {
	return adminShootPayload{
		ID:              strings.TrimSpace(m.ID),
		Title:           strings.TrimSpace(m.Title),
		Description:     strings.TrimSpace(m.Description),
		LocationName:    strings.TrimSpace(m.LocationName),
		Region:          strings.TrimSpace(m.Region),
		StartsAt:        m.StartsAt,
		EndsAt:          m.EndsAt,
		SourceURL:       strings.TrimSpace(m.SourceURL),
		RegistrationURL: strings.TrimSpace(m.RegistrationURL),
		EventType:       strings.TrimSpace(m.EventType),
	}
}

func postAdminShoot(client *http.Client, baseURL, adminKey string, sh adminShootPayload) error {
	body, err := json.Marshal(sh)
	if err != nil {
		return err
	}
	req, err := http.NewRequest(http.MethodPost, strings.TrimRight(baseURL, "/")+"/v1/admin/shoots", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Admin-Key", adminKey)
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("status %d: %s", resp.StatusCode, strings.TrimSpace(string(b)))
	}
	return nil
}

func cancelStale(client *http.Client, baseURL, adminKey string, keepIDs []string) (int, error) {
	body, err := json.Marshal(map[string]any{"keepIds": keepIDs})
	if err != nil {
		return 0, err
	}
	req, err := http.NewRequest(http.MethodPost, strings.TrimRight(baseURL, "/")+"/v1/admin/shoots/cancel-stale", bytes.NewReader(body))
	if err != nil {
		return 0, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Admin-Key", adminKey)
	resp, err := client.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		return 0, fmt.Errorf("status %d: %s", resp.StatusCode, strings.TrimSpace(string(b)))
	}
	var out struct {
		Cancelled int `json:"cancelled"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		return 0, err
	}
	return out.Cancelled, nil
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
