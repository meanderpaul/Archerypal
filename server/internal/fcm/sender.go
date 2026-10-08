package fcm

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"sync"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

// Sender delivers data+notification messages via FCM HTTP v1.
// When credentials are missing, Send is a no-op success so link-share still works.
type Sender struct {
	projectID string
	client    *http.Client
	mu        sync.Mutex
}

type serviceAccountFile struct {
	ProjectID string `json:"project_id"`
}

func NewFromEnv() (*Sender, error) {
	path := strings.TrimSpace(os.Getenv("FCM_CREDENTIALS_PATH"))
	if path == "" {
		path = strings.TrimSpace(os.Getenv("GOOGLE_APPLICATION_CREDENTIALS"))
	}
	if path == "" {
		return &Sender{}, nil
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read FCM credentials: %w", err)
	}
	var meta serviceAccountFile
	if err := json.Unmarshal(raw, &meta); err != nil {
		return nil, fmt.Errorf("parse FCM credentials: %w", err)
	}
	if meta.ProjectID == "" {
		return nil, fmt.Errorf("FCM credentials missing project_id")
	}
	creds, err := google.CredentialsFromJSON(context.Background(), raw,
		"https://www.googleapis.com/auth/firebase.messaging")
	if err != nil {
		return nil, fmt.Errorf("FCM oauth: %w", err)
	}
	return &Sender{
		projectID: meta.ProjectID,
		client:    oauth2.NewClient(context.Background(), creds.TokenSource),
	}, nil
}

func (s *Sender) Enabled() bool {
	return s != nil && s.client != nil && s.projectID != ""
}

type InvitePayload struct {
	Title     string
	Body      string
	InviteURI string
	RoomID    string
	HostName  string
}

func (s *Sender) SendInvite(ctx context.Context, deviceToken string, invite InvitePayload) error {
	if !s.Enabled() || strings.TrimSpace(deviceToken) == "" {
		return nil
	}
	if invite.Title == "" {
		invite.Title = "Archerypal invite"
	}
	if invite.Body == "" {
		invite.Body = "Tap to join the match"
	}
	body := map[string]any{
		"message": map[string]any{
			"token": deviceToken,
			"notification": map[string]string{
				"title": invite.Title,
				"body":  invite.Body,
			},
			"data": map[string]string{
				"type":      "remote_invite",
				"inviteUri": invite.InviteURI,
				"roomId":    invite.RoomID,
				"hostName":  invite.HostName,
			},
			"android": map[string]any{
				"priority": "HIGH",
				"notification": map[string]string{
					"channel_id":   "remote_invites",
					"click_action": "OPEN_REMOTE_INVITE",
				},
			},
		},
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return err
	}
	url := fmt.Sprintf("https://fcm.googleapis.com/v1/projects/%s/messages:send", s.projectID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := s.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if resp.StatusCode >= 300 {
		return fmt.Errorf("fcm send %d: %s", resp.StatusCode, strings.TrimSpace(string(respBody)))
	}
	return nil
}
