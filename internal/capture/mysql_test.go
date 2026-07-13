package capture

import (
	"encoding/binary"
	"github.com/fishandsheep/seal/internal/model"
	"testing"
	"time"
)

func mysqlPacket(seq byte, payload []byte) []byte {
	b := make([]byte, 4+len(payload))
	b[0] = byte(len(payload))
	b[1] = byte(len(payload) >> 8)
	b[2] = byte(len(payload) >> 16)
	b[3] = seq
	copy(b[4:], payload)
	return b
}

func TestCOMQueryAcrossTCPChunksAndLatency(t *testing.T) {
	var events []model.QueryEvent
	s := newMySQLSession("test", "c", "s", func(e model.QueryEvent, _ string) { events = append(events, e) })
	start := time.Now()
	packet := mysqlPacket(0, append([]byte{0x03}, []byte("SELECT * FROM users WHERE id=42")...))
	s.feed(true, packet[:6], start, 0)
	s.feed(true, packet[6:], start, 0)
	s.feed(false, mysqlPacket(1, []byte{0x01}), start.Add(12*time.Millisecond), 0)
	if len(events) != 1 || events[0].Latency != 12*time.Millisecond || events[0].Fingerprint == "" {
		t.Fatalf("events=%+v", events)
	}
}

func TestPreparedLifecycleAndRedactedParameters(t *testing.T) {
	var events []model.QueryEvent
	s := newMySQLSession("test", "c", "s", func(e model.QueryEvent, _ string) { events = append(events, e) })
	now := time.Now()
	s.feed(true, mysqlPacket(0, append([]byte{0x16}, []byte("SELECT name FROM users WHERE id=?")...)), now, 0)
	ok := make([]byte, 12)
	ok[0] = 0
	binary.LittleEndian.PutUint32(ok[1:5], 7)
	binary.LittleEndian.PutUint16(ok[7:9], 1)
	s.feed(false, mysqlPacket(1, ok), now, 0)
	exec := make([]byte, 10+1+1+2+8)
	exec[0] = 0x17
	binary.LittleEndian.PutUint32(exec[1:5], 7)
	exec[5] = 0
	binary.LittleEndian.PutUint32(exec[6:10], 1)
	exec[11] = 1
	binary.LittleEndian.PutUint16(exec[12:14], 8)
	binary.LittleEndian.PutUint64(exec[14:], 123456)
	s.feed(true, mysqlPacket(0, exec), now, 0)
	s.feed(false, mysqlPacket(1, []byte{0}), now.Add(time.Millisecond), 0)
	if len(events) != 1 || events[0].Command != "COM_STMT_EXECUTE" || events[0].ParameterMeta == "" {
		t.Fatalf("events=%+v", events)
	}
	if events[0].SQL != "select name from users where id = ?" {
		t.Fatalf("sql=%q", events[0].SQL)
	}
	s.feed(true, mysqlPacket(0, []byte{0x19, 7, 0, 0, 0}), now, 0)
}

func TestTLSAndCompressionDetection(t *testing.T) {
	for name, payload := range map[string][]byte{"tls": {0x16, 0x03, 0x03, 0, 1}, "compression": {0x20, 0, 0, 0, 1, 2, 3, 4}} {
		t.Run(name, func(t *testing.T) {
			var got model.QueryEvent
			s := newMySQLSession("test", "c", "s", func(e model.QueryEvent, _ string) { got = e })
			s.feed(true, mysqlPacket(1, payload), time.Now(), 0)
			if got.BlindReason == "" {
				t.Fatalf("no blind event for %s", name)
			}
		})
	}
}

func TestRawTLSRecordAndCompressedFrameDetection(t *testing.T) {
	for name, data := range map[string][]byte{
		"raw-tls":    {0x16, 0x03, 0x03, 0, 5, 1, 2, 3, 4, 5},
		"compressed": mysqlPacket(0, []byte{1, 0, 0, 0x78, 0x9c, 1, 2}),
	} {
		t.Run(name, func(t *testing.T) {
			var got model.QueryEvent
			s := newMySQLSession("test", "c", "s", func(e model.QueryEvent, _ string) { got = e })
			s.feed(true, data, time.Now(), 0)
			if got.BlindReason == "" {
				t.Fatalf("no blind event for %s", name)
			}
		})
	}
}
