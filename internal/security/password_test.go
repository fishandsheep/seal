package security

import (
	"net/http/httptest"
	"testing"
	"time"
)

func TestPasswordRoundTrip(t *testing.T) {
	hash, err := HashPassword("correct horse battery staple")
	if err != nil {
		t.Fatal(err)
	}
	if !VerifyPassword(hash, "correct horse battery staple") || VerifyPassword(hash, "wrong password") {
		t.Fatal("password verification mismatch")
	}
}

func TestSessionExpires(t *testing.T) {
	sessions := NewSessions(time.Millisecond)
	w := httptest.NewRecorder()
	r := httptest.NewRequest("GET", "http://localhost/", nil)
	sessions.Create(w, r)
	cookie := w.Result().Cookies()[0]
	time.Sleep(3 * time.Millisecond)
	r.AddCookie(cookie)
	if _, ok := sessions.Get(r); ok {
		t.Fatal("expired session accepted")
	}
}
