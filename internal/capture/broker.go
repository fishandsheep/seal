package capture

import (
	"sync"
	"time"

	"github.com/fishandsheep/seal/internal/model"
)

type Metrics struct {
	Packets           uint64
	Queries           uint64
	BlindFlows        uint64
	ParserDropped     uint64
	SubscriberDropped uint64
}

type Broker struct {
	mu       sync.RWMutex
	ring     []model.QueryEvent
	start    int
	count    int
	nextID   uint64
	subs     map[uint64]chan model.QueryEvent
	nextSub  uint64
	original map[string]originalValue
	metrics  Metrics
}

type originalValue struct {
	SQL     string
	Expires time.Time
}

func NewBroker(capacity int) *Broker {
	return &Broker{ring: make([]model.QueryEvent, capacity), subs: make(map[uint64]chan model.QueryEvent), original: make(map[string]originalValue)}
}

func (b *Broker) Publish(event model.QueryEvent, original string) {
	b.mu.Lock()
	b.nextID++
	event.ID = b.nextID
	if event.Time.IsZero() {
		event.Time = time.Now()
	}
	if b.count < len(b.ring) {
		b.ring[(b.start+b.count)%len(b.ring)] = event
		b.count++
	} else {
		b.ring[b.start] = event
		b.start = (b.start + 1) % len(b.ring)
	}
	if original != "" && event.Fingerprint != "" {
		b.gcOriginal(time.Now())
		if len(b.original) >= len(b.ring) {
			var oldestKey string
			var oldest time.Time
			for key, value := range b.original {
				if oldestKey == "" || value.Expires.Before(oldest) {
					oldestKey, oldest = key, value.Expires
				}
			}
			delete(b.original, oldestKey)
		}
		b.original[event.Fingerprint] = originalValue{SQL: original, Expires: time.Now().Add(10 * time.Minute)}
	}
	if event.BlindReason != "" {
		b.metrics.BlindFlows++
	} else {
		b.metrics.Queries++
	}
	for _, ch := range b.subs {
		select {
		case ch <- event:
		default:
			b.metrics.SubscriberDropped++
		}
	}
	b.mu.Unlock()
}

func (b *Broker) Subscribe(after uint64, capacity int) (<-chan model.QueryEvent, []model.QueryEvent, func()) {
	b.mu.Lock()
	b.nextSub++
	id := b.nextSub
	ch := make(chan model.QueryEvent, capacity)
	b.subs[id] = ch
	replay := make([]model.QueryEvent, 0)
	for i := 0; i < b.count; i++ {
		e := b.ring[(b.start+i)%len(b.ring)]
		if e.ID > after {
			replay = append(replay, e)
		}
	}
	b.mu.Unlock()
	return ch, replay, func() {
		b.mu.Lock()
		if c, ok := b.subs[id]; ok {
			delete(b.subs, id)
			close(c)
		}
		b.mu.Unlock()
	}
}

func (b *Broker) Recent(limit int) []model.QueryEvent {
	b.mu.RLock()
	defer b.mu.RUnlock()
	if limit <= 0 || limit > b.count {
		limit = b.count
	}
	out := make([]model.QueryEvent, 0, limit)
	for i := b.count - limit; i < b.count; i++ {
		out = append(out, b.ring[(b.start+i)%len(b.ring)])
	}
	return out
}

func (b *Broker) Original(fingerprint string) (string, bool) {
	b.mu.Lock()
	defer b.mu.Unlock()
	v, ok := b.original[fingerprint]
	if !ok || time.Now().After(v.Expires) {
		delete(b.original, fingerprint)
		return "", false
	}
	return v.SQL, true
}

func (b *Broker) AddPackets(n uint64) { b.mu.Lock(); b.metrics.Packets += n; b.mu.Unlock() }
func (b *Broker) AddParserDrop()      { b.mu.Lock(); b.metrics.ParserDropped++; b.mu.Unlock() }
func (b *Broker) Metrics() Metrics    { b.mu.RLock(); defer b.mu.RUnlock(); return b.metrics }
func (b *Broker) gcOriginal(now time.Time) {
	for k, v := range b.original {
		if now.After(v.Expires) {
			delete(b.original, k)
		}
	}
}
