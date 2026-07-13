package web

import (
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"regexp"
	"strings"
	"testing"

	"github.com/fishandsheep/seal/internal/capture"
	"github.com/fishandsheep/seal/internal/config"
	"github.com/fishandsheep/seal/internal/store"
)

func TestSetupAuthCSRFAndEmbeddedAssets(t *testing.T) {
	cfg, err := config.Parse([]string{"--data-dir", t.TempDir(), "--tcpdump", "/missing/tcpdump"})
	if err != nil {
		t.Fatal(err)
	}
	db, err := store.Open(cfg.DataDir)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	broker := capture.NewBroker(100)
	parser := capture.NewParser(cfg.MySQLPorts, broker)
	manager := capture.NewManager(ctx, cfg.TCPDumpPath, cfg.Interface, cfg.MySQLPorts, parser)
	handler := NewApp(ctx, cfg, db, broker, manager, parser).Handler()

	remote := httptest.NewRequest(http.MethodGet, "http://seal.local/seal/setup", nil)
	remote.RemoteAddr = "192.0.2.2:1234"
	rw := httptest.NewRecorder()
	handler.ServeHTTP(rw, remote)
	if rw.Code != http.StatusForbidden {
		t.Fatalf("remote setup status=%d", rw.Code)
	}

	form := url.Values{"password": {"correct horse battery staple"}, "confirm": {"correct horse battery staple"}}
	setup := httptest.NewRequest(http.MethodPost, "http://seal.local/seal/setup", strings.NewReader(form.Encode()))
	setup.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	setup.RemoteAddr = "127.0.0.1:1234"
	rw = httptest.NewRecorder()
	handler.ServeHTTP(rw, setup)
	if rw.Code != http.StatusSeeOther {
		t.Fatalf("setup status=%d body=%s", rw.Code, rw.Body.String())
	}
	cookies := rw.Result().Cookies()
	if len(cookies) != 1 {
		t.Fatalf("cookies=%v", cookies)
	}

	page := httptest.NewRequest(http.MethodGet, "http://seal.local/seal/connections", nil)
	page.AddCookie(cookies[0])
	page.RemoteAddr = "127.0.0.1:1234"
	rw = httptest.NewRecorder()
	handler.ServeHTTP(rw, page)
	if rw.Code != http.StatusOK {
		t.Fatalf("page status=%d", rw.Code)
	}
	if !strings.Contains(rw.Header().Get("Content-Security-Policy"), "object-src 'none'") {
		t.Fatal("CSP missing")
	}
	match := regexp.MustCompile(`name="_csrf" value="([^"]+)"`).FindStringSubmatch(rw.Body.String())
	if len(match) != 2 {
		t.Fatal("CSRF token missing")
	}

	bad := httptest.NewRequest(http.MethodPost, "http://seal.local/seal/logout", nil)
	bad.AddCookie(cookies[0])
	bad.RemoteAddr = "127.0.0.1:1234"
	rw = httptest.NewRecorder()
	handler.ServeHTTP(rw, bad)
	if rw.Code != http.StatusForbidden {
		t.Fatalf("CSRF status=%d", rw.Code)
	}

	asset := httptest.NewRequest(http.MethodGet, "http://seal.local/seal/assets/js/htmx.min.js", nil)
	rw = httptest.NewRecorder()
	handler.ServeHTTP(rw, asset)
	if rw.Code != http.StatusOK {
		t.Fatalf("asset status=%d", rw.Code)
	}
	body, _ := io.ReadAll(rw.Result().Body)
	if len(body) < 1000 {
		t.Fatalf("asset too short: %d", len(body))
	}
}

func TestProtectedRouteRedirectsToLogin(t *testing.T) {
	cfg, _ := config.Parse([]string{"--data-dir", t.TempDir()})
	db, err := store.Open(cfg.DataDir)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	_ = db.SetAdminHash("configured")
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	broker := capture.NewBroker(100)
	parser := capture.NewParser(cfg.MySQLPorts, broker)
	manager := capture.NewManager(ctx, cfg.TCPDumpPath, cfg.Interface, cfg.MySQLPorts, parser)
	handler := NewApp(ctx, cfg, db, broker, manager, parser).Handler()
	r := httptest.NewRequest(http.MethodGet, "http://seal.local/seal/settings", nil)
	w := httptest.NewRecorder()
	handler.ServeHTTP(w, r)
	if w.Code != http.StatusSeeOther || w.Header().Get("Location") != "/seal/login" {
		t.Fatalf("status=%d location=%q", w.Code, w.Header().Get("Location"))
	}
}
