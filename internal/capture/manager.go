package capture

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os/exec"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

type CaptureStatus struct {
	State     string    `json:"state"`
	PID       int       `json:"pid"`
	StartedAt time.Time `json:"started_at"`
	Restarts  int       `json:"restarts"`
	LastError string    `json:"last_error"`
	Interface string    `json:"interface"`
	Filter    string    `json:"filter"`
}

type Manager struct {
	mu     sync.Mutex
	root   context.Context
	path   string
	iface  string
	ports  []uint16
	parser *Parser
	status CaptureStatus
	cancel context.CancelFunc
	done   chan struct{}
}

func NewManager(root context.Context, path, iface string, ports []uint16, parser *Parser) *Manager {
	filter := BPFFilter(ports)
	return &Manager{root: root, path: path, iface: iface, ports: append([]uint16(nil), ports...), parser: parser,
		status: CaptureStatus{State: "stopped", Interface: iface, Filter: filter}}
}

func BPFFilter(ports []uint16) string {
	parts := make([]string, len(ports))
	for i, p := range ports {
		parts[i] = "port " + strconv.Itoa(int(p))
	}
	return "tcp and (" + strings.Join(parts, " or ") + ")"
}

func TCPDumpArgs(iface string, ports []uint16) []string {
	return []string{"--immediate-mode", "-U", "-n", "-s", "0", "-i", iface, "-w", "-", BPFFilter(ports)}
}

func (m *Manager) Start() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.cancel != nil {
		return false
	}
	ctx, cancel := context.WithCancel(m.root)
	m.cancel = cancel
	m.done = make(chan struct{})
	m.status.State = "starting"
	m.status.LastError = ""
	m.status.Restarts = 0
	go m.run(ctx, m.done)
	return true
}

func (m *Manager) Stop(ctx context.Context) error {
	m.mu.Lock()
	cancel, done := m.cancel, m.done
	m.mu.Unlock()
	if cancel == nil {
		return nil
	}
	cancel()
	select {
	case <-done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (m *Manager) Status() CaptureStatus { m.mu.Lock(); defer m.mu.Unlock(); return m.status }

func (m *Manager) run(ctx context.Context, done chan struct{}) {
	defer func() {
		m.mu.Lock()
		m.cancel = nil
		m.done = nil
		m.status.PID = 0
		if ctx.Err() != nil || m.status.State != "degraded" {
			m.status.State = "stopped"
		}
		m.mu.Unlock()
		close(done)
	}()
	for attempt := 0; attempt < 5; attempt++ {
		if ctx.Err() != nil {
			return
		}
		err := m.captureOnce(ctx)
		if ctx.Err() != nil {
			return
		}
		m.mu.Lock()
		m.status.State = "degraded"
		m.status.LastError = err.Error()
		m.status.Restarts++
		m.mu.Unlock()
		delay := time.Second << attempt
		if delay > 15*time.Second {
			delay = 15 * time.Second
		}
		timer := time.NewTimer(delay)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}

func (m *Manager) captureOnce(ctx context.Context) error {
	cmd := exec.CommandContext(ctx, m.path, TCPDumpArgs(m.iface, m.ports)...)
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return err
	}
	var errBuf boundedBuffer
	stderrDone := make(chan struct{})
	go func() { _, _ = io.Copy(&errBuf, stderr); close(stderrDone) }()
	cmd.Cancel = func() error {
		if cmd.Process == nil {
			return nil
		}
		err := syscall.Kill(-cmd.Process.Pid, syscall.SIGTERM)
		if errors.Is(err, syscall.ESRCH) {
			return nil
		}
		return err
	}
	cmd.WaitDelay = 3 * time.Second
	if err := cmd.Start(); err != nil {
		<-stderrDone
		return fmt.Errorf("start tcpdump: %w", err)
	}
	m.mu.Lock()
	m.status.State = "running"
	m.status.PID = cmd.Process.Pid
	m.status.StartedAt = time.Now()
	m.mu.Unlock()
	_, parseErr := m.parser.Parse(stdout, "live")
	waitErr := cmd.Wait()
	<-stderrDone
	if ctx.Err() != nil {
		return ctx.Err()
	}
	if parseErr != nil {
		return parseErr
	}
	if waitErr != nil {
		return fmt.Errorf("tcpdump exited: %w: %s", waitErr, strings.TrimSpace(errBuf.String()))
	}
	return errors.New("tcpdump exited unexpectedly")
}

type boundedBuffer struct{ b bytes.Buffer }

func (b *boundedBuffer) Write(p []byte) (int, error) {
	n := len(p)
	const max = 8192
	if len(p) > max {
		p = p[len(p)-max:]
	}
	if b.b.Len()+len(p) > max {
		old := b.b.Bytes()
		keep := max - len(p)
		copyOld := append([]byte(nil), old[len(old)-keep:]...)
		b.b.Reset()
		_, _ = b.b.Write(copyOld)
	}
	_, _ = b.b.Write(p)
	return n, nil
}
func (b *boundedBuffer) String() string { return b.b.String() }
