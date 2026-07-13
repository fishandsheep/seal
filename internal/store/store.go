package store

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"time"

	"github.com/fishandsheep/seal/internal/model"
	bolt "go.etcd.io/bbolt"
)

var (
	bMeta        = []byte("meta")
	bConnections = []byte("connections")
	bAggregates  = []byte("aggregates")
	bJobs        = []byte("analysis_jobs")
	bExplains    = []byte("explain_cache")
	kAdmin       = []byte("admin_hash")
)

type Store struct {
	db   *bolt.DB
	aead cipher.AEAD
}

type storedConnection struct {
	model.Connection
	Password []byte `json:"password"`
}

func Open(dir string) (*Store, error) {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, err
	}
	key, err := loadOrCreateKey(filepath.Join(dir, "seal.key"))
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	aead, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	db, err := bolt.Open(filepath.Join(dir, "seal.db"), 0o600, &bolt.Options{Timeout: 2 * time.Second})
	if err != nil {
		return nil, err
	}
	s := &Store{db: db, aead: aead}
	err = db.Update(func(tx *bolt.Tx) error {
		for _, name := range [][]byte{bMeta, bConnections, bAggregates, bJobs, bExplains} {
			if _, err := tx.CreateBucketIfNotExists(name); err != nil {
				return err
			}
		}
		return nil
	})
	if err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *Store) Close() error { return s.db.Close() }

func (s *Store) HasAdmin() (bool, error) {
	var ok bool
	err := s.db.View(func(tx *bolt.Tx) error { ok = len(tx.Bucket(bMeta).Get(kAdmin)) > 0; return nil })
	return ok, err
}

func (s *Store) AdminHash() (string, error) {
	var hash string
	err := s.db.View(func(tx *bolt.Tx) error { hash = string(tx.Bucket(bMeta).Get(kAdmin)); return nil })
	return hash, err
}

func (s *Store) SetAdminHash(hash string) error {
	if hash == "" {
		return errors.New("empty administrator hash")
	}
	return s.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(bMeta)
		if len(b.Get(kAdmin)) > 0 {
			return errors.New("administrator already configured")
		}
		return b.Put(kAdmin, []byte(hash))
	})
}

func (s *Store) PutConnection(c model.ConnectionSecret) error {
	if c.ID == "" || c.Name == "" || c.Host == "" || c.User == "" {
		return errors.New("connection id, name, host, and user are required")
	}
	sealed, err := s.seal([]byte(c.Password), []byte(c.ID))
	if err != nil {
		return err
	}
	data, err := json.Marshal(storedConnection{Connection: c.Connection, Password: sealed})
	if err != nil {
		return err
	}
	return s.db.Update(func(tx *bolt.Tx) error { return tx.Bucket(bConnections).Put([]byte(c.ID), data) })
}

func (s *Store) Connection(id string) (model.ConnectionSecret, error) {
	var stored storedConnection
	err := s.db.View(func(tx *bolt.Tx) error {
		v := tx.Bucket(bConnections).Get([]byte(id))
		if v == nil {
			return os.ErrNotExist
		}
		return json.Unmarshal(v, &stored)
	})
	if err != nil {
		return model.ConnectionSecret{}, err
	}
	plain, err := s.open(stored.Password, []byte(id))
	if err != nil {
		return model.ConnectionSecret{}, err
	}
	return model.ConnectionSecret{Connection: stored.Connection, Password: string(plain)}, nil
}

func (s *Store) Connections() ([]model.Connection, error) {
	var out []model.Connection
	err := s.db.View(func(tx *bolt.Tx) error {
		return tx.Bucket(bConnections).ForEach(func(_, value []byte) error {
			var c storedConnection
			if err := json.Unmarshal(value, &c); err != nil {
				return err
			}
			out = append(out, c.Connection)
			return nil
		})
	})
	sort.Slice(out, func(i, j int) bool { return out[i].CreatedAt.After(out[j].CreatedAt) })
	return out, err
}

func (s *Store) DeleteConnection(id string) error {
	return s.db.Update(func(tx *bolt.Tx) error { return tx.Bucket(bConnections).Delete([]byte(id)) })
}

func (s *Store) MergeAggregate(event model.QueryEvent) error {
	if event.Fingerprint == "" || event.BlindReason != "" {
		return nil
	}
	return s.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(bAggregates)
		var a model.Aggregate
		if v := b.Get([]byte(event.Fingerprint)); v != nil {
			if err := json.Unmarshal(v, &a); err != nil {
				return err
			}
		} else {
			a = model.Aggregate{Fingerprint: event.Fingerprint, Sample: event.SQL, Command: event.Command, FirstSeen: event.Time}
		}
		a.Count++
		a.Total += event.Latency
		if event.Latency > a.Max {
			a.Max = event.Latency
		}
		if event.RiskScore > a.RiskScore {
			a.RiskScore = event.RiskScore
		}
		a.LastSeen = event.Time
		data, err := json.Marshal(a)
		if err != nil {
			return err
		}
		return b.Put([]byte(event.Fingerprint), data)
	})
}

func (s *Store) Aggregates(offset, limit int) ([]model.Aggregate, int, error) {
	var all []model.Aggregate
	err := s.db.View(func(tx *bolt.Tx) error {
		return tx.Bucket(bAggregates).ForEach(func(_, value []byte) error {
			var a model.Aggregate
			if err := json.Unmarshal(value, &a); err != nil {
				return err
			}
			all = append(all, a)
			return nil
		})
	})
	sort.Slice(all, func(i, j int) bool { return all[i].LastSeen.After(all[j].LastSeen) })
	total := len(all)
	if offset > total {
		offset = total
	}
	end := offset + limit
	if limit <= 0 || end > total {
		end = total
	}
	return all[offset:end], total, err
}

func (s *Store) Aggregate(fingerprint string) (model.Aggregate, error) {
	var a model.Aggregate
	err := s.db.View(func(tx *bolt.Tx) error {
		v := tx.Bucket(bAggregates).Get([]byte(fingerprint))
		if v == nil {
			return os.ErrNotExist
		}
		return json.Unmarshal(v, &a)
	})
	return a, err
}

func (s *Store) Cleanup(before time.Time) (int, error) {
	removed := 0
	err := s.db.Update(func(tx *bolt.Tx) error {
		b := tx.Bucket(bAggregates)
		var keys [][]byte
		if err := b.ForEach(func(k, v []byte) error {
			var a model.Aggregate
			if err := json.Unmarshal(v, &a); err != nil {
				return err
			}
			if a.LastSeen.Before(before) {
				keys = append(keys, append([]byte(nil), k...))
			}
			return nil
		}); err != nil {
			return err
		}
		for _, k := range keys {
			if err := b.Delete(k); err != nil {
				return err
			}
			removed++
		}
		return nil
	})
	return removed, err
}

func (s *Store) PutJob(job model.AnalysisJob) error { return putJSON(s.db, bJobs, job.ID, job) }

func (s *Store) Jobs() ([]model.AnalysisJob, error) {
	var jobs []model.AnalysisJob
	err := s.db.View(func(tx *bolt.Tx) error {
		return tx.Bucket(bJobs).ForEach(func(_, v []byte) error {
			var j model.AnalysisJob
			if err := json.Unmarshal(v, &j); err != nil {
				return err
			}
			jobs = append(jobs, j)
			return nil
		})
	})
	sort.Slice(jobs, func(i, j int) bool { return jobs[i].CreatedAt.After(jobs[j].CreatedAt) })
	return jobs, err
}

func (s *Store) PutExplain(key string, result model.ExplainResult) error {
	return putJSON(s.db, bExplains, key, result)
}

func (s *Store) Explain(key string) (model.ExplainResult, bool, error) {
	var result model.ExplainResult
	var ok bool
	err := s.db.View(func(tx *bolt.Tx) error {
		v := tx.Bucket(bExplains).Get([]byte(key))
		if v == nil {
			return nil
		}
		if err := json.Unmarshal(v, &result); err != nil {
			return err
		}
		ok = time.Now().Before(result.ExpiresAt)
		return nil
	})
	return result, ok, err
}

func putJSON(db *bolt.DB, bucket []byte, key string, value any) error {
	data, err := json.Marshal(value)
	if err != nil {
		return err
	}
	return db.Update(func(tx *bolt.Tx) error { return tx.Bucket(bucket).Put([]byte(key), data) })
}

func (s *Store) seal(plain, additional []byte) ([]byte, error) {
	nonce := make([]byte, s.aead.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return nil, err
	}
	return s.aead.Seal(nonce, nonce, plain, additional), nil
}

func (s *Store) open(sealed, additional []byte) ([]byte, error) {
	n := s.aead.NonceSize()
	if len(sealed) < n {
		return nil, errors.New("encrypted value is truncated")
	}
	return s.aead.Open(nil, sealed[:n], sealed[n:], additional)
}

func loadOrCreateKey(path string) ([]byte, error) {
	key, err := os.ReadFile(path)
	if err == nil {
		if len(key) != 32 {
			return nil, fmt.Errorf("invalid key length in %s", path)
		}
		if info, statErr := os.Stat(path); statErr == nil && info.Mode().Perm()&0o077 != 0 {
			return nil, fmt.Errorf("key file %s must have mode 0600", path)
		}
		return key, nil
	}
	if !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	key = make([]byte, 32)
	if _, err := rand.Read(key); err != nil {
		return nil, err
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0o600)
	if err != nil {
		if errors.Is(err, os.ErrExist) {
			return loadOrCreateKey(path)
		}
		return nil, err
	}
	if _, err = f.Write(key); err == nil {
		err = f.Sync()
	}
	closeErr := f.Close()
	if err != nil {
		return nil, err
	}
	return key, closeErr
}
