package main

import (
	"bufio"
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/meanderpaul/archerypal-server/internal/api"
	"github.com/meanderpaul/archerypal-server/internal/auth"
	"github.com/meanderpaul/archerypal-server/internal/fcm"
	"github.com/meanderpaul/archerypal-server/internal/playbilling"
	"github.com/meanderpaul/archerypal-server/internal/room"
	"github.com/meanderpaul/archerypal-server/internal/store"
)

type config struct {
	ListenAddr                     string
	DBPath                         string
	JWTSecret                      string
	MaxRooms                       int
	RoomTTLMinutes                 int
	MigrationsDir                  string
	PublicWSBase                   string
	ConfigFile                     string
	BypassPlus                     bool
	AllowUnverifiedEntitlementSync bool
	TrustForwardedFor              bool
	AdminAPIKey                    string
	UploadsDir                     string
	GoogleOAuthClientID            string
	RequireBetaInvite              bool
	Env                            string
}

func main() {
	cfg := loadConfig()
	if err := loadEnvFile(cfg.ConfigFile); err != nil {
		log.Printf("config file: %v", err)
	}
	cfg = mergeEnv(cfg)
	if err := validateProductionConfig(cfg); err != nil {
		log.Fatalf("config: %v", err)
	}

	st, err := store.Open(cfg.DBPath)
	if err != nil {
		log.Fatalf("open db: %v", err)
	}
	defer st.Close()

	if err := st.Migrate(cfg.MigrationsDir); err != nil {
		log.Fatalf("migrate: %v", err)
	}

	go noncePurgeLoop(st)

	au := auth.NewAuthenticator(st, cfg.JWTSecret)
	allowedOrigins := room.ParseAllowedOrigins(cfg.PublicWSBase, "api.archerypal.app")
	rm := room.NewManager(cfg.MaxRooms, time.Duration(cfg.RoomTTLMinutes)*time.Minute, allowedOrigins)
	fcmSender, err := fcm.NewFromEnv()
	if err != nil {
		log.Fatalf("fcm: %v", err)
	}
	if fcmSender.Enabled() {
		log.Printf("FCM push invites enabled")
	} else {
		log.Printf("FCM push invites disabled (set FCM_CREDENTIALS_PATH to enable)")
	}
	playVerifier, err := playbilling.NewFromEnv()
	if err != nil {
		log.Fatalf("play billing: %v", err)
	}
	srv := api.NewServer(st, au, rm, api.Config{
		PublicWSBase:                   cfg.PublicWSBase,
		RoomTokenTTL:                   15 * time.Minute,
		BypassPlusEntitlement:          cfg.BypassPlus,
		AllowUnverifiedEntitlementSync: cfg.AllowUnverifiedEntitlementSync,
		TrustForwardedFor:              cfg.TrustForwardedFor,
		AdminAPIKey:                    cfg.AdminAPIKey,
		UploadsDir:                     cfg.UploadsDir,
		GoogleOAuthClientID:            cfg.GoogleOAuthClientID,
		RequireBetaInvite:              cfg.RequireBetaInvite,
		FCM:                            fcmSender,
		PlayVerifier:                   playVerifier,
	})

	log.Printf("listening on %s env=%s db=%s uploads=%s", cfg.ListenAddr, cfg.Env, cfg.DBPath, cfg.UploadsDir)
	if cfg.AdminAPIKey == "" {
		log.Printf("warning: ADMIN_API_KEY is empty; admin routes will reject all requests")
	}
	if cfg.BypassPlus {
		log.Printf("warning: BYPASS_PLUS_ENTITLEMENT is enabled")
	}
	if cfg.AllowUnverifiedEntitlementSync {
		log.Printf("warning: ALLOW_UNVERIFIED_ENTITLEMENT_SYNC is enabled (client-trusted Plus)")
	}
	if playVerifier != nil && playVerifier.Enabled() {
		log.Printf("Play purchase verification enabled")
	} else {
		log.Printf("Play purchase verification disabled (set GOOGLE_PLAY_SA_PATH and GOOGLE_PLAY_PACKAGE_NAME)")
	}
	if err := http.ListenAndServe(cfg.ListenAddr, srv.Handler()); err != nil {
		log.Fatalf("server: %v", err)
	}
}

func isDevListen(addr string) bool {
	return addr == ":8080" || strings.HasPrefix(addr, "127.0.0.1:")
}

func validateProductionConfig(cfg config) error {
	production := strings.EqualFold(cfg.Env, "production") || !isDevListen(cfg.ListenAddr)
	if production && (cfg.JWTSecret == "" || cfg.JWTSecret == "change-me") {
		return fmt.Errorf("JWT_SECRET must be set to a strong random value in production")
	}
	if production && cfg.BypassPlus {
		return fmt.Errorf("BYPASS_PLUS_ENTITLEMENT must not be enabled in production")
	}
	if production && cfg.AllowUnverifiedEntitlementSync {
		return fmt.Errorf("ALLOW_UNVERIFIED_ENTITLEMENT_SYNC must not be enabled in production")
	}
	return nil
}

func noncePurgeLoop(st *store.Store) {
	tick := time.NewTicker(15 * time.Minute)
	defer tick.Stop()
	for range tick.C {
		if err := st.PurgeExpiredNonces(context.Background()); err != nil {
			log.Printf("purge nonces: %v", err)
		}
	}
}

func loadConfig() config {
	exe, _ := os.Executable()
	base := filepath.Dir(exe)
	return config{
		ListenAddr:     ":8080",
		DBPath:         "/var/lib/archerypal/archerypal.db",
		JWTSecret:      "change-me",
		MaxRooms:       32,
		RoomTTLMinutes: 30,
		MigrationsDir:  filepath.Join(base, "migrations"),
		ConfigFile:     "/etc/archerypal/config.env",
		UploadsDir:     "/var/lib/archerypal/uploads",
		Env:            "development",
	}
}

func mergeEnv(cfg config) config {
	if v := os.Getenv("LISTEN_ADDR"); v != "" {
		cfg.ListenAddr = v
	}
	if v := os.Getenv("DB_PATH"); v != "" {
		cfg.DBPath = v
	}
	if v := os.Getenv("JWT_SECRET"); v != "" {
		cfg.JWTSecret = v
	}
	if v := os.Getenv("MAX_ROOMS"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			cfg.MaxRooms = n
		}
	}
	if v := os.Getenv("ROOM_TTL_MINUTES"); v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			cfg.RoomTTLMinutes = n
		}
	}
	if v := os.Getenv("MIGRATIONS_DIR"); v != "" {
		cfg.MigrationsDir = v
	}
	if v := os.Getenv("PUBLIC_WS_BASE"); v != "" {
		cfg.PublicWSBase = v
	}
	if v := os.Getenv("CONFIG_FILE"); v != "" {
		cfg.ConfigFile = v
	}
	if v := os.Getenv("BYPASS_PLUS_ENTITLEMENT"); v != "" {
		cfg.BypassPlus = v == "1" || strings.EqualFold(v, "true") || strings.EqualFold(v, "yes")
	}
	if v := os.Getenv("ALLOW_UNVERIFIED_ENTITLEMENT_SYNC"); v != "" {
		cfg.AllowUnverifiedEntitlementSync = v == "1" || strings.EqualFold(v, "true") || strings.EqualFold(v, "yes")
	}
	if v := os.Getenv("TRUST_FORWARDED_FOR"); v != "" {
		cfg.TrustForwardedFor = v == "1" || strings.EqualFold(v, "true") || strings.EqualFold(v, "yes")
	}
	if v := os.Getenv("ADMIN_API_KEY"); v != "" {
		cfg.AdminAPIKey = v
	}
	if v := os.Getenv("UPLOADS_DIR"); v != "" {
		cfg.UploadsDir = v
	}
	if v := os.Getenv("GOOGLE_OAUTH_CLIENT_ID"); v != "" {
		cfg.GoogleOAuthClientID = v
	}
	if v := os.Getenv("REQUIRE_BETA_INVITE"); v != "" {
		cfg.RequireBetaInvite = v == "1" || strings.EqualFold(v, "true") || strings.EqualFold(v, "yes")
	}
	if v := os.Getenv("ENV"); v != "" {
		cfg.Env = v
	}
	return cfg
}

func loadEnvFile(path string) error {
	f, err := os.Open(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	defer f.Close()

	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, val, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		key = strings.TrimSpace(key)
		val = strings.TrimSpace(val)
		val = strings.Trim(val, `"'`)
		if os.Getenv(key) == "" {
			_ = os.Setenv(key, val)
		}
	}
	return sc.Err()
}
