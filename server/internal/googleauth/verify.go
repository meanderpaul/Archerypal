package googleauth

import (
	"context"
	"errors"
	"fmt"

	"google.golang.org/api/idtoken"
)

var ErrNotConfigured = errors.New("google oauth client id not configured")

type VerifiedToken struct {
	Sub   string
	Email string
}

type Verifier struct {
	clientID string
}

func NewVerifier(clientID string) *Verifier {
	return &Verifier{clientID: clientID}
}

func (v *Verifier) Verify(ctx context.Context, idToken string) (*VerifiedToken, error) {
	if v.clientID == "" {
		return nil, ErrNotConfigured
	}
	idToken = trim(idToken)
	if idToken == "" {
		return nil, errors.New("missing id token")
	}
	payload, err := idtoken.Validate(ctx, idToken, v.clientID)
	if err != nil {
		return nil, fmt.Errorf("invalid id token: %w", err)
	}
	email, _ := payload.Claims["email"].(string)
	return &VerifiedToken{
		Sub:   payload.Subject,
		Email: email,
	}, nil
}

func trim(s string) string {
	for len(s) > 0 && (s[0] == ' ' || s[0] == '\t' || s[0] == '\n') {
		s = s[1:]
	}
	for len(s) > 0 {
		last := s[len(s)-1]
		if last != ' ' && last != '\t' && last != '\n' {
			break
		}
		s = s[:len(s)-1]
	}
	return s
}
