# Seal repository guide

Seal is a Linux-only Go 1.26 monolith for observing plaintext MySQL 8 traffic. Read `PRODUCT.md` and `DESIGN.md` before changing workflows or UI.

## Build

```bash
go generate ./...
go test ./...
go build -trimpath -o seal ./cmd/seal
```

`go generate` runs templ, installs pinned npm build dependencies, compiles Tailwind CSS, and copies pinned htmx assets. Runtime output is one embedded Go binary; only `tcpdump` is external.

## Architecture

- `cmd/seal`: config, lifecycle, graceful shutdown.
- `internal/capture`: tcpdump manager, PCAP/TCP/MySQL parser, bounded event broker, async aggregator.
- `internal/risk`: SQL redaction, fingerprinting, static findings and score.
- `internal/store`: bbolt persistence and AES-GCM connection secrets.
- `internal/security`: Argon2id and in-memory authenticated sessions.
- `internal/database`: connection tests and bounded on-demand EXPLAIN.
- `web`: standard-library handlers, templ views, templUI source, embedded assets.

## Invariants

- Never persist raw SQL literals or prepared parameter values.
- Never build tcpdump commands through a shell.
- Preserve legacy `storage/`; do not read, migrate, or delete it.
- Keep channels, rings, uploads, parser buffers, and browser rows bounded.
- Normal requests render full templ pages; htmx requests may render partials.
- Risk score direction is always 0 safe → 100 severe.
