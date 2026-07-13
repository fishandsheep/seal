package web

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"io"
	"log/slog"
	"mime/multipart"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/a-h/templ"
	"github.com/fishandsheep/seal/internal/capture"
	"github.com/fishandsheep/seal/internal/config"
	"github.com/fishandsheep/seal/internal/database"
	"github.com/fishandsheep/seal/internal/model"
	"github.com/fishandsheep/seal/internal/risk"
	"github.com/fishandsheep/seal/internal/security"
	"github.com/fishandsheep/seal/internal/store"
	assetsfs "github.com/fishandsheep/seal/web/assets"
)

type App struct {
	ctx      context.Context
	cfg      config.Config
	store    *store.Store
	broker   *capture.Broker
	capture  *capture.Manager
	parser   *capture.Parser
	database *database.Service
	sessions *security.Sessions
	limiter  *loginLimiter
	analysis sync.WaitGroup
}

type contextKey int

const sessionKey contextKey = 1

func NewApp(ctx context.Context, cfg config.Config, db *store.Store, broker *capture.Broker, manager *capture.Manager, parser *capture.Parser) *App {
	return &App{ctx: ctx, cfg: cfg, store: db, broker: broker, capture: manager, parser: parser, database: database.New(db), sessions: security.NewSessions(12 * time.Hour), limiter: newLoginLimiter()}
}

func (a *App) Handler() http.Handler {
	root := http.NewServeMux()
	base := a.cfg.BasePath
	root.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain; charset=utf-8")
		_, _ = io.WriteString(w, "ok\n")
	})
	root.HandleFunc("GET /readyz", a.ready)
	assets := http.FileServer(http.FS(assetsfs.Files))
	root.Handle("GET "+base+"/assets/", http.StripPrefix(base+"/assets/", assets))
	root.HandleFunc("GET "+base+"/login", a.loginGet)
	root.HandleFunc("POST "+base+"/login", a.loginPost)
	root.HandleFunc("GET "+base+"/setup", a.setupGet)
	root.HandleFunc("POST "+base+"/setup", a.setupPost)

	protected := http.NewServeMux()
	protected.HandleFunc("GET "+base+"/", a.home)
	protected.HandleFunc("POST "+base+"/logout", a.logout)
	protected.HandleFunc("GET "+base+"/live", a.live)
	protected.HandleFunc("POST "+base+"/live/start", a.liveStart)
	protected.HandleFunc("POST "+base+"/live/stop", a.liveStop)
	protected.HandleFunc("GET "+base+"/live/events", a.liveEvents)
	protected.HandleFunc("GET "+base+"/connections", a.connections)
	protected.HandleFunc("POST "+base+"/connections", a.connectionAdd)
	protected.HandleFunc("POST "+base+"/connections/test", a.connectionTest)
	protected.HandleFunc("POST "+base+"/connections/{id}/delete", a.connectionDelete)
	protected.HandleFunc("GET "+base+"/analyses", a.analyses)
	protected.HandleFunc("POST "+base+"/analyses", a.analysisUpload)
	protected.HandleFunc("GET "+base+"/queries/{fingerprint}/risk", a.queryRisk)
	protected.HandleFunc("POST "+base+"/queries/{fingerprint}/explain", a.queryExplain)
	protected.HandleFunc("GET "+base+"/settings", a.settings)
	root.Handle(base+"/", a.requireAuth(a.requireCSRF(protected)))
	return a.securityHeaders(a.recoverer(root))
}

func (a *App) page(r *http.Request, title, active string) PageData {
	s, _ := r.Context().Value(sessionKey).(security.Session)
	return PageData{Base: a.cfg.BasePath, CSRF: s.CSRF, Title: title, Active: active, Config: a.cfg, Capture: a.capture.Status(), Metrics: a.broker.Metrics()}
}

func (a *App) home(w http.ResponseWriter, r *http.Request) {
	http.Redirect(w, r, a.cfg.BasePath+"/live", http.StatusSeeOther)
}

func (a *App) ready(w http.ResponseWriter, _ *http.Request) {
	if _, err := a.store.HasAdmin(); err != nil {
		http.Error(w, "store unavailable", http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	_, _ = io.WriteString(w, "ready\n")
}

func (a *App) setupGet(w http.ResponseWriter, r *http.Request) {
	if !requestLoopback(r) {
		http.Error(w, "administrator setup is only available from loopback", http.StatusForbidden)
		return
	}
	if ok, _ := a.store.HasAdmin(); ok {
		http.Redirect(w, r, a.cfg.BasePath+"/login", http.StatusSeeOther)
		return
	}
	a.render(w, r, http.StatusOK, SetupPage(PageData{Base: a.cfg.BasePath, Title: "初始化"}))
}

func (a *App) setupPost(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	if !requestLoopback(r) {
		http.Error(w, "administrator setup is only available from loopback", http.StatusForbidden)
		return
	}
	if err := r.ParseForm(); err != nil {
		a.render(w, r, http.StatusBadRequest, SetupPage(PageData{Base: a.cfg.BasePath, Title: "初始化", Error: "表单无效"}))
		return
	}
	password := r.FormValue("password")
	if password != r.FormValue("confirm") {
		a.render(w, r, http.StatusUnprocessableEntity, SetupPage(PageData{Base: a.cfg.BasePath, Title: "初始化", Error: "两次口令不一致"}))
		return
	}
	hash, err := security.HashPassword(password)
	if err == nil {
		err = a.store.SetAdminHash(hash)
	}
	if err != nil {
		a.render(w, r, http.StatusUnprocessableEntity, SetupPage(PageData{Base: a.cfg.BasePath, Title: "初始化", Error: err.Error()}))
		return
	}
	a.sessions.Create(w, r)
	http.Redirect(w, r, a.cfg.BasePath+"/live", http.StatusSeeOther)
}

func (a *App) loginGet(w http.ResponseWriter, r *http.Request) {
	if ok, _ := a.store.HasAdmin(); !ok {
		http.Redirect(w, r, a.cfg.BasePath+"/setup", http.StatusSeeOther)
		return
	}
	if _, ok := a.sessions.Get(r); ok {
		http.Redirect(w, r, a.cfg.BasePath+"/live", http.StatusSeeOther)
		return
	}
	a.render(w, r, http.StatusOK, LoginPage(PageData{Base: a.cfg.BasePath, Title: "登录"}))
}

func (a *App) loginPost(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
	ip := clientIP(r)
	if !a.limiter.Allow(ip) {
		a.render(w, r, http.StatusTooManyRequests, LoginPage(PageData{Base: a.cfg.BasePath, Title: "登录", Error: "尝试过多，请稍后再试"}))
		return
	}
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", http.StatusBadRequest)
		return
	}
	hash, _ := a.store.AdminHash()
	if !security.VerifyPassword(hash, r.FormValue("password")) {
		a.limiter.Fail(ip)
		time.Sleep(150 * time.Millisecond)
		a.render(w, r, http.StatusUnauthorized, LoginPage(PageData{Base: a.cfg.BasePath, Title: "登录", Error: "口令错误"}))
		return
	}
	a.limiter.Success(ip)
	a.sessions.Create(w, r)
	http.Redirect(w, r, a.cfg.BasePath+"/live", http.StatusSeeOther)
}

func (a *App) logout(w http.ResponseWriter, r *http.Request) {
	a.sessions.Delete(w, r)
	http.Redirect(w, r, a.cfg.BasePath+"/login", http.StatusSeeOther)
}

func (a *App) live(w http.ResponseWriter, r *http.Request) {
	a.capture.Start()
	d := a.page(r, "实时监控", "live")
	d.Capture = a.capture.Status()
	d.Events = a.broker.Recent(100)
	a.render(w, r, http.StatusOK, LivePage(d))
}
func (a *App) liveStart(w http.ResponseWriter, r *http.Request) {
	a.capture.Start()
	d := a.page(r, "实时监控", "live")
	d.Capture = a.capture.Status()
	a.render(w, r, http.StatusOK, CaptureStatus(d))
}
func (a *App) liveStop(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()
	_ = a.capture.Stop(ctx)
	d := a.page(r, "实时监控", "live")
	d.Capture = a.capture.Status()
	a.render(w, r, http.StatusOK, CaptureStatus(d))
}

func (a *App) liveEvents(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", http.StatusInternalServerError)
		return
	}
	after, _ := strconv.ParseUint(r.Header.Get("Last-Event-ID"), 10, 64)
	ch, replay, cancel := a.broker.Subscribe(after, 256)
	defer cancel()
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache, no-store")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	flusher.Flush()
	queue := replay
	ticker := time.NewTicker(250 * time.Millisecond)
	keepalive := time.NewTicker(15 * time.Second)
	session, _ := r.Context().Value(sessionKey).(security.Session)
	expires := time.NewTimer(time.Until(session.Expires))
	defer ticker.Stop()
	defer keepalive.Stop()
	defer expires.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case <-expires.C:
			return
		case e, ok := <-ch:
			if !ok {
				return
			}
			queue = append(queue, e)
		case <-ticker.C:
			if len(queue) > 0 {
				n := len(queue)
				if n > 100 {
					n = 100
				}
				batch := append([]model.QueryEvent(nil), queue[:n]...)
				queue = queue[n:]
				var buf bytes.Buffer
				if err := LiveRows(a.cfg.BasePath, batch).Render(r.Context(), &buf); err != nil {
					return
				}
				if _, err := fmt.Fprintf(w, "id: %d\nevent: queries\n", batch[len(batch)-1].ID); err != nil {
					return
				}
				for _, line := range strings.Split(buf.String(), "\n") {
					if _, err := fmt.Fprintf(w, "data: %s\n", line); err != nil {
						return
					}
				}
				_, _ = io.WriteString(w, "\n")
				flusher.Flush()
			}
		case <-keepalive.C:
			if _, err := io.WriteString(w, ": keepalive\n\n"); err != nil {
				return
			}
			flusher.Flush()
		}
	}
}

func (a *App) connections(w http.ResponseWriter, r *http.Request) {
	d := a.page(r, "数据库连接", "connections")
	d.Connections, _ = a.store.Connections()
	d.Message = r.URL.Query().Get("message")
	d.Error = r.URL.Query().Get("error")
	a.render(w, r, http.StatusOK, ConnectionsPage(d))
}

func (a *App) connectionAdd(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		a.fragmentError(w, r, http.StatusBadRequest, "表单无效")
		return
	}
	port64, err := strconv.ParseUint(r.FormValue("port"), 10, 16)
	if err != nil || port64 == 0 {
		a.fragmentError(w, r, http.StatusUnprocessableEntity, "端口无效")
		return
	}
	c := model.ConnectionSecret{Connection: model.Connection{ID: newID(), Name: strings.TrimSpace(r.FormValue("name")), Host: strings.TrimSpace(r.FormValue("host")), Port: uint16(port64), User: strings.TrimSpace(r.FormValue("user")), Database: strings.TrimSpace(r.FormValue("database")), Endpoint: strings.TrimSpace(r.FormValue("endpoint")), CreatedAt: time.Now()}, Password: r.FormValue("password")}
	if err := a.store.PutConnection(c); err != nil {
		a.fragmentError(w, r, http.StatusUnprocessableEntity, err.Error())
		return
	}
	http.Redirect(w, r, a.cfg.BasePath+"/connections?message="+"连接已保存", http.StatusSeeOther)
}

func (a *App) connectionTest(w http.ResponseWriter, r *http.Request) {
	if err := r.ParseForm(); err != nil {
		http.Error(w, "invalid form", 400)
		return
	}
	c, err := a.store.Connection(r.FormValue("id"))
	if err == nil {
		err = a.database.Test(r.Context(), c)
	}
	target := a.cfg.BasePath + "/connections?message=连接测试成功"
	if err != nil {
		target = a.cfg.BasePath + "/connections?error=" + urlQuery(err.Error())
	}
	http.Redirect(w, r, target, http.StatusSeeOther)
}
func (a *App) connectionDelete(w http.ResponseWriter, r *http.Request) {
	err := a.store.DeleteConnection(r.PathValue("id"))
	if err != nil {
		a.fragmentError(w, r, http.StatusInternalServerError, err.Error())
		return
	}
	http.Redirect(w, r, a.cfg.BasePath+"/connections?message=连接已删除", http.StatusSeeOther)
}

func (a *App) analyses(w http.ResponseWriter, r *http.Request) {
	d := a.page(r, "PCAP 分析", "analyses")
	d.Jobs, _ = a.store.Jobs()
	d.Aggregates, d.Total, _ = a.store.Aggregates(0, 100)
	d.Error = r.URL.Query().Get("error")
	a.render(w, r, http.StatusOK, AnalysesPage(d))
}

func (a *App) analysisUpload(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, a.cfg.MaxUpload+(1<<20))
	if err := r.ParseMultipartForm(a.cfg.MaxUpload); err != nil {
		a.fragmentError(w, r, http.StatusRequestEntityTooLarge, "PCAP 超过上传上限或表单无效")
		return
	}
	defer r.MultipartForm.RemoveAll()
	file, header, err := r.FormFile("pcap")
	if err != nil {
		a.fragmentError(w, r, http.StatusBadRequest, "请选择 PCAP 文件")
		return
	}
	defer file.Close()
	dir := filepath.Join(a.cfg.DataDir, "tmp")
	if err = os.MkdirAll(dir, 0o700); err != nil {
		a.fragmentError(w, r, 500, err.Error())
		return
	}
	tmp, err := os.CreateTemp(dir, "analysis-*.pcap")
	if err != nil {
		a.fragmentError(w, r, 500, err.Error())
		return
	}
	path := tmp.Name()
	n, copyErr := io.Copy(tmp, io.LimitReader(file, a.cfg.MaxUpload+1))
	closeErr := tmp.Close()
	if copyErr != nil || closeErr != nil || n > a.cfg.MaxUpload {
		_ = os.Remove(path)
		a.fragmentError(w, r, http.StatusRequestEntityTooLarge, "PCAP 超过上传上限")
		return
	}
	job := model.AnalysisJob{ID: newID(), Filename: safeFilename(header), Status: "queued", CreatedAt: time.Now(), UpdatedAt: time.Now()}
	if err := a.store.PutJob(job); err != nil {
		_ = os.Remove(path)
		a.fragmentError(w, r, 500, err.Error())
		return
	}
	a.analysis.Add(1)
	go func() { defer a.analysis.Done(); a.runAnalysis(job, path) }()
	http.Redirect(w, r, a.cfg.BasePath+"/analyses", http.StatusSeeOther)
}

func (a *App) Shutdown(ctx context.Context) error {
	done := make(chan struct{})
	go func() { a.analysis.Wait(); close(done) }()
	select {
	case <-done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (a *App) runAnalysis(job model.AnalysisJob, path string) {
	defer os.Remove(path)
	job.Status = "running"
	job.UpdatedAt = time.Now()
	_ = a.store.PutJob(job)
	before := a.broker.Metrics().Queries
	f, err := os.Open(path)
	if err == nil {
		var stats capture.ParseStats
		stats, err = a.parser.Parse(f, "pcap:"+job.ID)
		job.Packets = stats.Packets
		_ = f.Close()
	}
	job.Queries = a.broker.Metrics().Queries - before
	job.UpdatedAt = time.Now()
	if err != nil {
		job.Status = "failed"
		job.Error = err.Error()
	} else {
		job.Status = "complete"
	}
	_ = a.store.PutJob(job)
}

func (a *App) queryRisk(w http.ResponseWriter, r *http.Request) {
	fingerprint := r.PathValue("fingerprint")
	agg, err := a.store.Aggregate(fingerprint)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	d := a.page(r, "风险详情", "analyses")
	d.Aggregate = agg
	d.Report = risk.Analyze(agg.Sample)
	d.Connections, _ = a.store.Connections()
	original, ok := a.broker.Original(fingerprint)
	if !ok {
		d.ExplainNote = "原始 SQL 已过期或来自历史聚合；Seal 不会从脱敏值重建查询。"
	} else if safe, reason := risk.SafeToExplain(original); !safe {
		d.ExplainNote = reason
	} else if len(d.Connections) == 0 {
		d.ExplainNote = "尚未配置数据库连接。"
	}
	a.render(w, r, http.StatusOK, RiskPage(d))
}

func (a *App) queryExplain(w http.ResponseWriter, r *http.Request) {
	fingerprint := r.PathValue("fingerprint")
	agg, err := a.store.Aggregate(fingerprint)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	_ = r.ParseForm()
	d := a.page(r, "风险详情", "analyses")
	d.Aggregate = agg
	d.Report = risk.Analyze(agg.Sample)
	d.Connections, _ = a.store.Connections()
	original, ok := a.broker.Original(fingerprint)
	if !ok {
		d.ExplainNote = "原始 SQL 已过期；无法安全执行 EXPLAIN。"
	} else if safe, reason := risk.SafeToExplain(original); !safe {
		d.ExplainNote = reason
	} else {
		result, e := a.database.Explain(r.Context(), r.FormValue("connection_id"), fingerprint, original, r.FormValue("refresh") == "1")
		if e != nil {
			d.ExplainNote = e.Error()
		} else {
			d.Explain = &result
		}
	}
	status := http.StatusOK
	if d.ExplainNote != "" && d.Explain == nil {
		status = http.StatusUnprocessableEntity
	}
	a.render(w, r, status, RiskPage(d))
}

func (a *App) settings(w http.ResponseWriter, r *http.Request) {
	d := a.page(r, "设置与诊断", "settings")
	a.render(w, r, http.StatusOK, SettingsPage(d))
}

func (a *App) render(w http.ResponseWriter, r *http.Request, status int, component templ.Component) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := component.Render(r.Context(), w); err != nil {
		slog.Error("render", "error", err)
	}
}
func (a *App) fragmentError(w http.ResponseWriter, r *http.Request, status int, message string) {
	if r.Header.Get("HX-Request") == "true" {
		a.render(w, r, status, ErrorFragment(message))
		return
	}
	a.render(w, r, status, ErrorPage(a.page(r, "请求错误", ""), message))
}

func (a *App) requireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if ok, _ := a.store.HasAdmin(); !ok {
			redirect(w, r, a.cfg.BasePath+"/setup")
			return
		}
		session, ok := a.sessions.Get(r)
		if !ok {
			redirect(w, r, a.cfg.BasePath+"/login")
			return
		}
		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), sessionKey, session)))
	})
}
func (a *App) requireCSRF(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet || r.Method == http.MethodHead || r.Method == http.MethodOptions {
			next.ServeHTTP(w, r)
			return
		}
		session, _ := r.Context().Value(sessionKey).(security.Session)
		token := r.Header.Get("X-CSRF-Token")
		if token == "" {
			if strings.HasPrefix(r.Header.Get("Content-Type"), "multipart/form-data") {
				r.Body = http.MaxBytesReader(w, r.Body, a.cfg.MaxUpload+(1<<20))
				_ = r.ParseMultipartForm(1 << 20)
			} else {
				r.Body = http.MaxBytesReader(w, r.Body, 1<<20)
				_ = r.ParseForm()
			}
			token = r.FormValue("_csrf")
		}
		if subtle.ConstantTimeCompare([]byte(token), []byte(session.CSRF)) != 1 {
			a.fragmentError(w, r, http.StatusForbidden, "CSRF 校验失败，请刷新页面后重试")
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (a *App) securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("Referrer-Policy", "no-referrer")
		h.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
		h.Set("Vary", "HX-Request")
		if strings.HasPrefix(r.URL.Path, a.cfg.BasePath) {
			h.Set("Cache-Control", "no-store")
		}
		next.ServeHTTP(w, r)
	})
}
func (a *App) recoverer(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if v := recover(); v != nil {
				slog.Error("panic", "value", v, "path", r.URL.Path)
				http.Error(w, "internal server error", 500)
			}
		}()
		next.ServeHTTP(w, r)
	})
}

func redirect(w http.ResponseWriter, r *http.Request, target string) {
	if r.Header.Get("HX-Request") == "true" {
		w.Header().Set("HX-Redirect", target)
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	http.Redirect(w, r, target, http.StatusSeeOther)
}
func requestLoopback(r *http.Request) bool {
	ip := net.ParseIP(clientIP(r))
	return ip != nil && ip.IsLoopback()
}
func clientIP(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil {
		return host
	}
	return r.RemoteAddr
}
func newID() string {
	b := make([]byte, 18)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}
func urlQuery(s string) string { return url.QueryEscape(s) }
func safeFilename(h *multipart.FileHeader) string {
	name := filepath.Base(h.Filename)
	if name == "." || name == "/" || name == "" {
		return "capture.pcap"
	}
	if len(name) > 160 {
		name = name[:160]
	}
	return name
}

type loginAttempt struct {
	failures int
	reset    time.Time
	blocked  time.Time
}
type loginLimiter struct {
	mu    sync.Mutex
	items map[string]loginAttempt
}

func newLoginLimiter() *loginLimiter { return &loginLimiter{items: make(map[string]loginAttempt)} }
func (l *loginLimiter) Allow(ip string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	a := l.items[ip]
	now := time.Now()
	if now.After(a.reset) {
		delete(l.items, ip)
		return true
	}
	return now.After(a.blocked)
}
func (l *loginLimiter) Fail(ip string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	a := l.items[ip]
	now := time.Now()
	if now.After(a.reset) {
		a = loginAttempt{reset: now.Add(10 * time.Minute)}
	}
	a.failures++
	if a.failures >= 5 {
		a.blocked = now.Add(5 * time.Minute)
	}
	l.items[ip] = a
}
func (l *loginLimiter) Success(ip string) { l.mu.Lock(); delete(l.items, ip); l.mu.Unlock() }
