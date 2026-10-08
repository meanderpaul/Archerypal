package playbilling

import (
	"errors"
	"testing"
)

func TestOptionalDisabled(t *testing.T) {
	v := Optional(nil)
	if v.Enabled() {
		t.Fatal("expected disabled")
	}
	_, err := v.Verify(t.Context(), "1plus", "tok")
	if !errors.Is(err, ErrNotConfigured) {
		t.Fatalf("got %v", err)
	}
}

func TestNewFromEnv_MissingConfig(t *testing.T) {
	t.Setenv("GOOGLE_PLAY_SA_PATH", "")
	t.Setenv("GOOGLE_PLAY_PACKAGE_NAME", "")
	c, err := NewFromEnv()
	if err != nil {
		t.Fatal(err)
	}
	if c != nil {
		t.Fatal("expected nil client when env unset")
	}
}
