package security

import (
	"crypto/rand"
	"encoding/base64"
	"net/http"
	"sync"
	"time"
)

const CookieName = "seal_session"

type Session struct {
	ID      string
	CSRF    string
	Expires time.Time
}

type Sessions struct {
	mu       sync.Mutex
	sessions map[string]Session
	ttl      time.Duration
}

func NewSessions(ttl time.Duration) *Sessions {
	return &Sessions{sessions: make(map[string]Session), ttl: ttl}
}

func (s *Sessions) Create(w http.ResponseWriter, r *http.Request) Session {
	now := time.Now()
	sess := Session{ID: token(32), CSRF: token(32), Expires: now.Add(s.ttl)}
	s.mu.Lock()
	s.gc(now)
	s.sessions[sess.ID] = sess
	s.mu.Unlock()
	http.SetCookie(w, &http.Cookie{Name: CookieName, Value: sess.ID, Path: "/", HttpOnly: true,
		Secure: r.TLS != nil, SameSite: http.SameSiteStrictMode, MaxAge: int(s.ttl.Seconds())})
	return sess
}

func (s *Sessions) Get(r *http.Request) (Session, bool) {
	c, err := r.Cookie(CookieName)
	if err != nil {
		return Session{}, false
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	sess, ok := s.sessions[c.Value]
	if !ok || time.Now().After(sess.Expires) {
		delete(s.sessions, c.Value)
		return Session{}, false
	}
	return sess, true
}

func (s *Sessions) Delete(w http.ResponseWriter, r *http.Request) {
	if c, err := r.Cookie(CookieName); err == nil {
		s.mu.Lock()
		delete(s.sessions, c.Value)
		s.mu.Unlock()
	}
	http.SetCookie(w, &http.Cookie{Name: CookieName, Path: "/", MaxAge: -1, HttpOnly: true,
		Secure: r.TLS != nil, SameSite: http.SameSiteStrictMode})
}

func (s *Sessions) gc(now time.Time) {
	for id, sess := range s.sessions {
		if now.After(sess.Expires) {
			delete(s.sessions, id)
		}
	}
}

func token(n int) string {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return base64.RawURLEncoding.EncodeToString(b)
}
