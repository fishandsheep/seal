package store

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/fishandsheep/seal/internal/model"
)

func TestConnectionPasswordEncrypted(t *testing.T) {
	dir := t.TempDir()
	s, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	c := model.ConnectionSecret{Connection: model.Connection{ID: "c1", Name: "prod", Host: "db", Port: 3306, User: "seal", CreatedAt: time.Now()}, Password: "top-secret-password"}
	if err := s.PutConnection(c); err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile(filepath.Join(dir, "seal.db"))
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(raw, []byte(c.Password)) {
		t.Fatal("database contains plaintext password")
	}
	got, err := s.Connection("c1")
	if err != nil {
		t.Fatal(err)
	}
	if got.Password != c.Password {
		t.Fatalf("password = %q", got.Password)
	}
	info, _ := os.Stat(filepath.Join(dir, "seal.key"))
	if info.Mode().Perm() != 0o600 {
		t.Fatalf("key mode = %o", info.Mode().Perm())
	}
}

func TestAggregateCleanup(t *testing.T) {
	s, err := Open(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	old := model.QueryEvent{Fingerprint: "old", SQL: "select ?", Time: time.Now().Add(-31 * 24 * time.Hour)}
	newer := model.QueryEvent{Fingerprint: "new", SQL: "select ?", Time: time.Now()}
	_ = s.MergeAggregate(old)
	_ = s.MergeAggregate(newer)
	n, err := s.Cleanup(time.Now().Add(-30 * 24 * time.Hour))
	if err != nil || n != 1 {
		t.Fatalf("removed=%d err=%v", n, err)
	}
}

func TestPreparedParameterMetadataIsNotPersisted(t *testing.T) {
	dir := t.TempDir()
	s, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer s.Close()
	event := model.QueryEvent{Fingerprint: "fp", SQL: "select name from users where id = ?", ParameterMeta: "secret-parameter-value", Time: time.Now()}
	if err := s.MergeAggregate(event); err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile(filepath.Join(dir, "seal.db"))
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(raw, []byte("secret-parameter-value")) {
		t.Fatal("prepared parameter metadata persisted")
	}
}
