package capture

import (
	"github.com/fishandsheep/seal/internal/model"
	"testing"
	"time"
)

func TestBrokerReplayAndSlowSubscriberDrop(t *testing.T) {
	b := NewBroker(3)
	for i := 0; i < 5; i++ {
		b.Publish(model.QueryEvent{Time: time.Now(), SQL: "select ?"}, "")
	}
	ch, replay, cancel := b.Subscribe(3, 1)
	defer cancel()
	if len(replay) != 2 || replay[0].ID != 4 {
		t.Fatalf("replay=%v", replay)
	}
	b.Publish(model.QueryEvent{SQL: "one"}, "")
	b.Publish(model.QueryEvent{SQL: "two"}, "")
	if b.Metrics().SubscriberDropped == 0 {
		t.Fatal("slow subscriber did not drop")
	}
	<-ch
}
