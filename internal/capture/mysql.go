package capture

import (
	"encoding/binary"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/fishandsheep/seal/internal/model"
	"github.com/fishandsheep/seal/internal/risk"
)

const (
	maxMySQLPacket = 16 << 20
	maxSQLBytes    = 1 << 20
)

type mysqlSession struct {
	mu        sync.Mutex
	clientBuf []byte
	serverBuf []byte
	clientMsg []byte
	serverMsg []byte
	prepared  map[uint32]preparedStatement
	prepare   string
	pending   *pendingQuery
	blind     bool
	client    string
	server    string
	source    string
	publish   func(model.QueryEvent, string)
}

type preparedStatement struct {
	SQL        string
	ParamTypes []uint16
}
type pendingQuery struct {
	Command, SQL, Meta string
	Started            time.Time
}

func newMySQLSession(source, client, server string, publish func(model.QueryEvent, string)) *mysqlSession {
	return &mysqlSession{prepared: make(map[uint32]preparedStatement), source: source, client: client, server: server, publish: publish}
}

func (s *mysqlSession) feed(client bool, data []byte, seen time.Time, skipped int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.blind {
		return
	}
	if skipped != 0 {
		s.emitBlind("TCP stream contains a capture gap", seen)
		s.clientBuf = nil
		s.serverBuf = nil
		return
	}
	buf := &s.serverBuf
	message := &s.serverMsg
	if client {
		buf = &s.clientBuf
		message = &s.clientMsg
	}
	if len(*buf) == 0 && len(*message) == 0 && looksTLS(data) {
		s.emitBlind("TLS-encrypted MySQL flow", seen)
		s.blind = true
		return
	}
	*buf = append(*buf, data...)
	for len(*buf) >= 4 {
		length := int((*buf)[0]) | int((*buf)[1])<<8 | int((*buf)[2])<<16
		if length > maxMySQLPacket {
			s.emitBlind("invalid or compressed MySQL packet framing", seen)
			s.blind = true
			return
		}
		if len(*buf) < length+4 {
			return
		}
		seq := (*buf)[3]
		payload := append([]byte(nil), (*buf)[4:4+length]...)
		*buf = (*buf)[4+length:]
		*message = append(*message, payload...)
		if length == 0xffffff {
			continue
		}
		full := *message
		*message = nil
		if client {
			s.clientPacket(full, seq, seen)
		} else {
			s.serverPacket(full, seq, seen)
		}
	}
}

func (s *mysqlSession) clientPacket(payload []byte, seq byte, seen time.Time) {
	if len(payload) == 0 {
		return
	}
	if looksCompressed(payload) {
		s.emitBlind("MySQL compressed protocol framing", seen)
		s.blind = true
		return
	}
	if looksTLS(payload) {
		s.emitBlind("TLS-encrypted MySQL flow", seen)
		s.blind = true
		return
	}
	if seq == 1 && len(payload) >= 4 && !isCommand(payload[0]) {
		caps := binary.LittleEndian.Uint32(payload[:4])
		if caps&0x00000800 != 0 {
			s.emitBlind("MySQL CLIENT_SSL negotiation", seen)
			s.blind = true
			return
		}
		if caps&0x00000020 != 0 {
			s.emitBlind("MySQL compression enabled", seen)
			s.blind = true
			return
		}
	}
	switch payload[0] {
	case 0x03: // COM_QUERY
		if len(payload)-1 > maxSQLBytes {
			s.emitBlind("SQL exceeds 1 MiB parser limit", seen)
			return
		}
		s.pending = &pendingQuery{Command: "COM_QUERY", SQL: string(payload[1:]), Started: seen}
	case 0x16: // COM_STMT_PREPARE
		if len(payload)-1 > maxSQLBytes {
			s.emitBlind("prepared SQL exceeds 1 MiB parser limit", seen)
			return
		}
		s.prepare = string(payload[1:])
		s.pending = nil
	case 0x17: // COM_STMT_EXECUTE
		if len(payload) < 10 {
			return
		}
		id := binary.LittleEndian.Uint32(payload[1:5])
		stmt, ok := s.prepared[id]
		if !ok {
			s.emitBlind(fmt.Sprintf("unknown prepared statement id %d", id), seen)
			return
		}
		meta := parseExecuteMeta(payload[10:], stmt.ParamTypes)
		if meta.types != nil {
			stmt.ParamTypes = meta.types
			s.prepared[id] = stmt
		}
		s.pending = &pendingQuery{Command: "COM_STMT_EXECUTE", SQL: stmt.SQL, Meta: meta.label, Started: seen}
	case 0x19: // COM_STMT_CLOSE
		if len(payload) >= 5 {
			delete(s.prepared, binary.LittleEndian.Uint32(payload[1:5]))
		}
	}
}

func (s *mysqlSession) serverPacket(payload []byte, _ byte, seen time.Time) {
	if len(payload) == 0 {
		return
	}
	if looksCompressed(payload) {
		s.emitBlind("MySQL compressed protocol framing", seen)
		s.blind = true
		return
	}
	if looksTLS(payload) {
		s.emitBlind("TLS-encrypted MySQL flow", seen)
		s.blind = true
		return
	}
	if s.prepare != "" {
		if payload[0] == 0 && len(payload) >= 12 {
			id := binary.LittleEndian.Uint32(payload[1:5])
			params := int(binary.LittleEndian.Uint16(payload[7:9]))
			s.prepared[id] = preparedStatement{SQL: s.prepare, ParamTypes: make([]uint16, params)}
		}
		s.prepare = ""
		return
	}
	if s.pending == nil {
		return
	}
	p := s.pending
	s.pending = nil
	report := risk.Analyze(p.SQL)
	s.publish(model.QueryEvent{Time: seen, Source: s.source, Client: s.client, Server: s.server, Command: p.Command,
		SQL: report.Redacted, Fingerprint: report.Fingerprint, ParameterMeta: p.Meta, Latency: seen.Sub(p.Started), RiskScore: report.Score}, p.SQL)
}

func (s *mysqlSession) emitBlind(reason string, seen time.Time) {
	s.publish(model.QueryEvent{Time: seen, Source: s.source, Client: s.client, Server: s.server, Command: "UNPARSEABLE", BlindReason: reason}, "")
}

func isCommand(b byte) bool { return b == 0x03 || b == 0x16 || b == 0x17 || b == 0x19 }
func looksTLS(p []byte) bool {
	return len(p) >= 3 && (p[0] == 0x14 || p[0] == 0x15 || p[0] == 0x16 || p[0] == 0x17) && p[1] == 0x03
}

func looksCompressed(p []byte) bool {
	if len(p) >= 5 && p[3] == 0x78 {
		header := int(p[3])<<8 | int(p[4])
		if header%31 == 0 {
			return true
		}
	}
	if len(p) >= 8 && p[0] == 0 && p[1] == 0 && p[2] == 0 {
		inner := int(p[3]) | int(p[4])<<8 | int(p[5])<<16
		return inner > 0 && inner+4 <= len(p)-3
	}
	return false
}

type executeMeta struct {
	types []uint16
	label string
}

func parseExecuteMeta(payload []byte, previous []uint16) executeMeta {
	if len(previous) == 0 {
		return executeMeta{label: "parameters: none"}
	}
	nullBytes := (len(previous) + 7) / 8
	if len(payload) < nullBytes+1 {
		return executeMeta{label: fmt.Sprintf("parameters: %d (values redacted)", len(previous))}
	}
	pos := nullBytes
	newTypes := payload[pos] == 1
	pos++
	types := previous
	if newTypes && len(payload) >= pos+len(previous)*2 {
		types = make([]uint16, len(previous))
		for i := range types {
			types[i] = binary.LittleEndian.Uint16(payload[pos+i*2:])
		}
	}
	names := make([]string, len(types))
	for i, typ := range types {
		names[i] = mysqlTypeName(byte(typ))
	}
	return executeMeta{types: types, label: fmt.Sprintf("parameters: %d [%s], values redacted", len(types), strings.Join(names, ", "))}
}

func mysqlTypeName(t byte) string {
	switch t {
	case 0:
		return "DECIMAL"
	case 1:
		return "TINYINT"
	case 2:
		return "SMALLINT"
	case 3:
		return "INT"
	case 4:
		return "FLOAT"
	case 5:
		return "DOUBLE"
	case 7:
		return "TIMESTAMP"
	case 8:
		return "BIGINT"
	case 10:
		return "DATE"
	case 11:
		return "TIME"
	case 12:
		return "DATETIME"
	case 15, 253, 254:
		return "STRING"
	case 245:
		return "JSON"
	case 246:
		return "NEWDECIMAL"
	default:
		return fmt.Sprintf("TYPE_%d", t)
	}
}
