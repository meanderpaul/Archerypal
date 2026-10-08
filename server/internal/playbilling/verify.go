package playbilling

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"

	"golang.org/x/oauth2/google"
	"google.golang.org/api/androidpublisher/v3"
	"google.golang.org/api/option"
)

// Android Publisher OAuth scope for purchase verification.
const scope = androidpublisher.AndroidpublisherScope

var (
	ErrNotConfigured  = errors.New("play billing verification not configured")
	ErrInactive       = errors.New("purchase not active")
	ErrUnsupportedSKU = errors.New("unsupported sku")
)

// Result is a Play-verified entitlement.
type Result struct {
	SKU       string
	ExpiresAt *int64 // unix seconds; nil means non-expiring (lifetime)
}

// Verifier checks Google Play purchase tokens.
type Verifier interface {
	Enabled() bool
	Verify(ctx context.Context, sku, purchaseToken string) (*Result, error)
}

// Client talks to the Android Publisher API.
type Client struct {
	packageName string
	service     *androidpublisher.Service
}

// NewFromEnv builds a Client when GOOGLE_PLAY_SA_PATH and GOOGLE_PLAY_PACKAGE_NAME are set.
// Missing config returns a nil client and nil error (verification disabled).
func NewFromEnv() (*Client, error) {
	path := strings.TrimSpace(os.Getenv("GOOGLE_PLAY_SA_PATH"))
	pkg := strings.TrimSpace(os.Getenv("GOOGLE_PLAY_PACKAGE_NAME"))
	if path == "" || pkg == "" {
		return nil, nil
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read play credentials: %w", err)
	}
	creds, err := google.CredentialsFromJSON(context.Background(), raw, scope)
	if err != nil {
		return nil, fmt.Errorf("play oauth: %w", err)
	}
	svc, err := androidpublisher.NewService(
		context.Background(),
		option.WithCredentials(creds),
	)
	if err != nil {
		return nil, fmt.Errorf("androidpublisher service: %w", err)
	}
	return &Client{packageName: pkg, service: svc}, nil
}

func (c *Client) Enabled() bool {
	return c != nil && c.service != nil && c.packageName != ""
}

// Verify checks a purchase token with Google Play for sku ("1plus" or "lifetime").
func (c *Client) Verify(ctx context.Context, sku, purchaseToken string) (*Result, error) {
	if !c.Enabled() {
		return nil, ErrNotConfigured
	}
	sku = strings.TrimSpace(sku)
	purchaseToken = strings.TrimSpace(purchaseToken)
	if sku == "" || purchaseToken == "" {
		return nil, fmt.Errorf("sku and purchaseToken required")
	}
	switch sku {
	case "1plus":
		return c.verifySubscription(ctx, sku, purchaseToken)
	case "lifetime":
		return c.verifyProduct(ctx, sku, purchaseToken)
	default:
		return nil, ErrUnsupportedSKU
	}
}

func (c *Client) verifySubscription(ctx context.Context, sku, token string) (*Result, error) {
	sub, err := c.service.Purchases.Subscriptions.Get(c.packageName, sku, token).Context(ctx).Do()
	if err != nil {
		return nil, fmt.Errorf("subscriptions.get: %w", err)
	}
	if sub.ExpiryTimeMillis <= 0 {
		return nil, ErrInactive
	}
	expiryUnix := sub.ExpiryTimeMillis / 1000
	if time.Now().Unix() >= expiryUnix {
		return nil, ErrInactive
	}
	// Payment pending is not entitled yet (paymentState 0).
	if sub.PaymentState != nil && *sub.PaymentState == 0 {
		return nil, ErrInactive
	}
	return &Result{SKU: sku, ExpiresAt: &expiryUnix}, nil
}

func (c *Client) verifyProduct(ctx context.Context, sku, token string) (*Result, error) {
	prod, err := c.service.Purchases.Products.Get(c.packageName, sku, token).Context(ctx).Do()
	if err != nil {
		return nil, fmt.Errorf("products.get: %w", err)
	}
	// 0 = Purchased, 1 = Canceled, 2 = Pending
	if prod.PurchaseState != 0 {
		return nil, ErrInactive
	}
	return &Result{SKU: sku, ExpiresAt: nil}, nil
}

// Optional wraps a possibly-nil Verifier.
func Optional(v Verifier) Verifier {
	if v == nil {
		return disabled{}
	}
	return v
}

type disabled struct{}

func (disabled) Enabled() bool { return false }

func (disabled) Verify(context.Context, string, string) (*Result, error) {
	return nil, ErrNotConfigured
}
