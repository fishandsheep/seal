package config

import (
	"errors"
	"flag"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	Listen        string
	BasePath      string
	DataDir       string
	TCPDumpPath   string
	Interface     string
	MySQLPorts    []uint16
	Retention     time.Duration
	MaxUpload     int64
	RingCapacity  int
	QueueCapacity int
}

func Parse(args []string) (Config, error) {
	c := Config{
		Listen:        env("SEAL_LISTEN", "127.0.0.1:7070"),
		BasePath:      env("SEAL_BASE_PATH", "/seal"),
		DataDir:       env("SEAL_DATA_DIR", "data"),
		TCPDumpPath:   env("SEAL_TCPDUMP_PATH", "tcpdump"),
		Interface:     env("SEAL_INTERFACE", "any"),
		Retention:     30 * 24 * time.Hour,
		MaxUpload:     256 << 20,
		RingCapacity:  10_000,
		QueueCapacity: 2_048,
	}
	ports := env("SEAL_MYSQL_PORTS", "3306")
	retention := env("SEAL_RETENTION", "720h")
	maxUpload := env("SEAL_MAX_UPLOAD", "268435456")
	ring := env("SEAL_RING_CAPACITY", "10000")
	queue := env("SEAL_QUEUE_CAPACITY", "2048")

	fs := flag.NewFlagSet("seal", flag.ContinueOnError)
	fs.StringVar(&c.Listen, "listen", c.Listen, "HTTP listen address")
	fs.StringVar(&c.BasePath, "base-path", c.BasePath, "URL base path")
	fs.StringVar(&c.DataDir, "data-dir", c.DataDir, "data directory")
	fs.StringVar(&c.TCPDumpPath, "tcpdump", c.TCPDumpPath, "tcpdump executable")
	fs.StringVar(&c.Interface, "interface", c.Interface, "capture interface")
	fs.StringVar(&ports, "mysql-ports", ports, "comma-separated MySQL ports")
	fs.StringVar(&retention, "retention", retention, "aggregate retention duration")
	fs.StringVar(&maxUpload, "max-upload", maxUpload, "maximum PCAP upload bytes")
	fs.StringVar(&ring, "ring-capacity", ring, "recent event ring size")
	fs.StringVar(&queue, "queue-capacity", queue, "capture queue size")
	if err := fs.Parse(args); err != nil {
		return Config{}, err
	}

	var err error
	if c.MySQLPorts, err = parsePorts(ports); err != nil {
		return Config{}, err
	}
	if c.Retention, err = time.ParseDuration(retention); err != nil || c.Retention < time.Hour {
		return Config{}, fmt.Errorf("invalid retention %q", retention)
	}
	if c.MaxUpload, err = strconv.ParseInt(maxUpload, 10, 64); err != nil || c.MaxUpload < 1<<20 {
		return Config{}, fmt.Errorf("invalid max-upload %q", maxUpload)
	}
	if c.RingCapacity, err = strconv.Atoi(ring); err != nil || c.RingCapacity < 100 || c.RingCapacity > 100_000 {
		return Config{}, fmt.Errorf("invalid ring-capacity %q", ring)
	}
	if c.QueueCapacity, err = strconv.Atoi(queue); err != nil || c.QueueCapacity < 64 || c.QueueCapacity > 65_536 {
		return Config{}, fmt.Errorf("invalid queue-capacity %q", queue)
	}
	if err := validate(c); err != nil {
		return Config{}, err
	}
	c.DataDir, err = filepath.Abs(c.DataDir)
	return c, err
}

func validate(c Config) error {
	if !strings.HasPrefix(c.BasePath, "/") || (len(c.BasePath) > 1 && strings.HasSuffix(c.BasePath, "/")) || strings.ContainsAny(c.BasePath, "?#") {
		return errors.New("base-path must start with '/' and have no trailing slash, '?' or '#'")
	}
	if strings.ContainsAny(c.Interface, "\x00\r\n") || c.Interface == "" || len(c.Interface) > 64 {
		return errors.New("invalid capture interface")
	}
	if strings.ContainsAny(c.TCPDumpPath, "\x00\r\n") || c.TCPDumpPath == "" {
		return errors.New("invalid tcpdump path")
	}
	_, _, err := net.SplitHostPort(c.Listen)
	if err != nil {
		return fmt.Errorf("invalid listen address: %w", err)
	}
	return nil
}

func IsLoopbackListen(addr string) bool {
	host, _, err := net.SplitHostPort(addr)
	if err != nil {
		return false
	}
	if host == "localhost" {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

func parsePorts(value string) ([]uint16, error) {
	seen := make(map[uint16]bool)
	var ports []uint16
	for _, part := range strings.Split(value, ",") {
		n, err := strconv.ParseUint(strings.TrimSpace(part), 10, 16)
		if err != nil || n == 0 {
			return nil, fmt.Errorf("invalid MySQL port %q", part)
		}
		p := uint16(n)
		if !seen[p] {
			seen[p] = true
			ports = append(ports, p)
		}
	}
	if len(ports) == 0 {
		return nil, errors.New("at least one MySQL port is required")
	}
	return ports, nil
}

func env(name, fallback string) string {
	if v, ok := os.LookupEnv(name); ok {
		return v
	}
	return fallback
}
