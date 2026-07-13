package capture

import (
	"context"
	"reflect"
	"testing"
	"time"
)

func TestTCPDumpArgsAreSeparateAndValidatedUpstream(t *testing.T) {
	want := []string{"--immediate-mode", "-U", "-n", "-s", "0", "-i", "any", "-w", "-", "tcp and (port 3306 or port 3307)"}
	if got := TCPDumpArgs("any", []uint16{3306, 3307}); !reflect.DeepEqual(got, want) {
		t.Fatalf("args=%q", got)
	}
}

func TestManagerStartIsIdempotentAndStops(t *testing.T) {
	root, cancel := context.WithCancel(context.Background())
	defer cancel()
	m := NewManager(root, "/definitely/missing/tcpdump", "any", []uint16{3306}, NewParser([]uint16{3306}, NewBroker(100)))
	if !m.Start() || m.Start() {
		t.Fatal("start not idempotent")
	}
	ctx, c := context.WithTimeout(context.Background(), time.Second)
	defer c()
	if err := m.Stop(ctx); err != nil {
		t.Fatal(err)
	}
}
