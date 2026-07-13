package database

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"net"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/fishandsheep/seal/internal/model"
	"github.com/fishandsheep/seal/internal/risk"
	"github.com/fishandsheep/seal/internal/store"
	"github.com/go-sql-driver/mysql"
)

type Service struct {
	store *store.Store
	mu    sync.Mutex
	locks map[string]chan struct{}
}

func New(s *store.Store) *Service { return &Service{store: s, locks: make(map[string]chan struct{})} }

func (s *Service) Test(ctx context.Context, c model.ConnectionSecret) error {
	db, err := sql.Open("mysql", dsn(c))
	if err != nil {
		return err
	}
	defer db.Close()
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(0)
	ctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		return fmt.Errorf("connect to MySQL: %w", err)
	}
	return nil
}

func (s *Service) Explain(ctx context.Context, connectionID, fingerprint, query string, refresh bool) (model.ExplainResult, error) {
	c, err := s.store.Connection(connectionID)
	if err != nil {
		return model.ExplainResult{}, err
	}
	key := connectionID + "\x00" + c.Database + "\x00" + fingerprint
	if !refresh {
		if cached, ok, err := s.store.Explain(key); err != nil {
			return model.ExplainResult{}, err
		} else if ok {
			return cached, nil
		}
	}
	sem := s.semaphore(connectionID)
	select {
	case sem <- struct{}{}:
		defer func() { <-sem }()
	case <-ctx.Done():
		return model.ExplainResult{}, ctx.Err()
	}
	db, err := sql.Open("mysql", dsn(c))
	if err != nil {
		return model.ExplainResult{}, err
	}
	defer db.Close()
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(0)
	ctx, cancel := context.WithTimeout(ctx, 4*time.Second)
	defer cancel()
	var raw string
	if err := db.QueryRowContext(ctx, "EXPLAIN FORMAT=JSON "+query).Scan(&raw); err != nil {
		return model.ExplainResult{}, fmt.Errorf("EXPLAIN failed: %w", err)
	}
	summary, err := summarize(raw)
	if err != nil {
		return model.ExplainResult{}, err
	}
	sanitized, err := sanitizeExplain(raw)
	if err != nil {
		return model.ExplainResult{}, err
	}
	result := model.ExplainResult{Fingerprint: fingerprint, Connection: connectionID, Schema: c.Database, Summary: summary, Raw: sanitized, ExpiresAt: time.Now().Add(24 * time.Hour)}
	if err := s.store.PutExplain(key, result); err != nil {
		return model.ExplainResult{}, err
	}
	return result, nil
}

func (s *Service) semaphore(id string) chan struct{} {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.locks[id] == nil {
		s.locks[id] = make(chan struct{}, 1)
	}
	return s.locks[id]
}

func dsn(c model.ConnectionSecret) string {
	cfg := mysql.NewConfig()
	cfg.User = c.User
	cfg.Passwd = c.Password
	cfg.Net = "tcp"
	cfg.Addr = net.JoinHostPort(c.Host, fmt.Sprint(c.Port))
	cfg.DBName = c.Database
	cfg.Timeout = 3 * time.Second
	cfg.ReadTimeout = 4 * time.Second
	cfg.WriteTimeout = 4 * time.Second
	cfg.InterpolateParams = false
	cfg.MultiStatements = false
	return cfg.FormatDSN()
}

func summarize(raw string) (string, error) {
	var root any
	if err := json.Unmarshal([]byte(raw), &root); err != nil {
		return "", fmt.Errorf("parse EXPLAIN JSON: %w", err)
	}
	var scans, temp, filesort bool
	var rows float64
	keys := map[string]bool{}
	candidates := map[string]bool{}
	var walk func(any)
	walk = func(v any) {
		switch x := v.(type) {
		case map[string]any:
			if a, ok := x["access_type"].(string); ok && strings.EqualFold(a, "ALL") {
				scans = true
			}
			if n, ok := x["rows_examined_per_scan"].(float64); ok && n > rows {
				rows = n
			}
			if b, ok := x["using_temporary_table"].(bool); ok && b {
				temp = true
			}
			if b, ok := x["using_filesort"].(bool); ok && b {
				filesort = true
			}
			if k, ok := x["key"].(string); ok && k != "" {
				keys[k] = true
			}
			if ps, ok := x["possible_keys"].([]any); ok {
				for _, p := range ps {
					if k, ok := p.(string); ok {
						candidates[k] = true
					}
				}
			}
			for _, child := range x {
				walk(child)
			}
		case []any:
			for _, child := range x {
				walk(child)
			}
		}
	}
	walk(root)
	parts := []string{}
	if scans {
		parts = append(parts, "full table scan detected")
	} else {
		parts = append(parts, "no full table scan reported")
	}
	if rows > 0 {
		parts = append(parts, fmt.Sprintf("maximum estimated rows %.0f", rows))
	}
	if temp {
		parts = append(parts, "temporary table used")
	}
	if filesort {
		parts = append(parts, "filesort used")
	}
	parts = append(parts, "indexes used: "+joinSet(keys), "candidate indexes: "+joinSet(candidates))
	return strings.Join(parts, "; "), nil
}

func sanitizeExplain(raw string) (string, error) {
	var root any
	if err := json.Unmarshal([]byte(raw), &root); err != nil {
		return "", fmt.Errorf("parse EXPLAIN JSON: %w", err)
	}
	var walk func(any)
	walk = func(value any) {
		switch x := value.(type) {
		case map[string]any:
			for key, child := range x {
				if text, ok := child.(string); ok && strings.Contains(strings.ToLower(key), "condition") {
					x[key] = risk.Analyze(text).Redacted
				} else {
					walk(child)
				}
			}
		case []any:
			for _, child := range x {
				walk(child)
			}
		}
	}
	walk(root)
	data, err := json.MarshalIndent(root, "", "  ")
	if err != nil {
		return "", err
	}
	return string(data), nil
}

func joinSet(set map[string]bool) string {
	if len(set) == 0 {
		return "none"
	}
	values := make([]string, 0, len(set))
	for v := range set {
		values = append(values, v)
	}
	sort.Strings(values)
	return strings.Join(values, ", ")
}
