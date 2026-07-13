package capture

import (
	"context"
	"github.com/fishandsheep/seal/internal/store"
	"log/slog"
	"time"
)

func RunAggregator(ctx context.Context, broker *Broker, db *store.Store, retention time.Duration, queue int) {
	ch, replay, cancel := broker.Subscribe(0, queue)
	defer cancel()
	for _, event := range replay {
		if err := db.MergeAggregate(event); err != nil {
			slog.Error("aggregate query", "error", err)
		}
	}
	cleanup := time.NewTicker(time.Hour)
	defer cleanup.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case event, ok := <-ch:
			if !ok {
				return
			}
			if err := db.MergeAggregate(event); err != nil {
				slog.Error("aggregate query", "error", err)
			}
		case <-cleanup.C:
			if _, err := db.Cleanup(time.Now().Add(-retention)); err != nil {
				slog.Error("cleanup aggregates", "error", err)
			}
		}
	}
}
