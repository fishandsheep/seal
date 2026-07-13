package capture

import (
	"encoding/binary"
	"fmt"
	"io"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/google/gopacket"
	"github.com/google/gopacket/ip4defrag"
	"github.com/google/gopacket/layers"
	"github.com/google/gopacket/pcapgo"
	"github.com/google/gopacket/tcpassembly"
)

type ParseStats struct{ Packets, TCPPackets uint64 }

type Parser struct {
	ports  map[uint16]bool
	broker *Broker
}

func NewParser(ports []uint16, broker *Broker) *Parser {
	m := make(map[uint16]bool, len(ports))
	for _, p := range ports {
		m[p] = true
	}
	return &Parser{ports: m, broker: broker}
}

func (p *Parser) Parse(reader io.Reader, source string) (ParseStats, error) {
	pcap, err := pcapgo.NewReader(reader)
	if err != nil {
		return ParseStats{}, fmt.Errorf("read PCAP header: %w", err)
	}
	factory := &streamFactory{source: source, ports: p.ports, broker: p.broker, sessions: make(map[string]*mysqlSession), refs: make(map[string]int)}
	pool := tcpassembly.NewStreamPool(factory)
	assembler := tcpassembly.NewAssembler(pool)
	assembler.MaxBufferedPagesPerConnection = 128
	assembler.MaxBufferedPagesTotal = 4096
	defragger := ip4defrag.NewIPv4Defragmenter()
	v6defragger := newIPv6Defragmenter()
	var stats ParseStats
	for {
		data, ci, err := pcap.ReadPacketData()
		if err == io.EOF {
			break
		}
		if err != nil {
			return stats, fmt.Errorf("read PCAP packet: %w", err)
		}
		stats.Packets++
		p.broker.AddPackets(1)
		packet := gopacket.NewPacket(data, pcap.LinkType(), gopacket.DecodeOptions{Lazy: true, NoCopy: true})
		if errLayer := packet.ErrorLayer(); errLayer != nil {
			p.broker.AddParserDrop()
			continue
		}
		if layer := packet.Layer(layers.LayerTypeIPv4); layer != nil {
			ip := layer.(*layers.IPv4)
			if ip.Flags&layers.IPv4MoreFragments != 0 || ip.FragOffset != 0 {
				ip, err = defragger.DefragIPv4WithTimestamp(ip, ci.Timestamp)
				if err != nil || ip == nil {
					continue
				}
				assembled := make([]byte, 0, len(ip.Contents)+len(ip.Payload))
				assembled = append(assembled, ip.Contents...)
				assembled = append(assembled, ip.Payload...)
				packet = gopacket.NewPacket(assembled, layers.LayerTypeIPv4, gopacket.DecodeOptions{Lazy: true, NoCopy: true})
			}
		}
		if fragmentLayer := packet.Layer(layers.LayerTypeIPv6Fragment); fragmentLayer != nil {
			ipLayer := packet.Layer(layers.LayerTypeIPv6)
			if ipLayer == nil {
				p.broker.AddParserDrop()
				continue
			}
			ip := ipLayer.(*layers.IPv6)
			fragment := fragmentLayer.(*layers.IPv6Fragment)
			if ip.NextHeader != layers.IPProtocolIPv6Fragment {
				p.broker.AddParserDrop()
				continue
			}
			assembled, complete, defragErr := v6defragger.add(ip, fragment, ci.Timestamp)
			if defragErr != nil {
				p.broker.AddParserDrop()
				continue
			}
			if !complete {
				continue
			}
			packet = gopacket.NewPacket(assembled, layers.LayerTypeIPv6, gopacket.DecodeOptions{Lazy: true, NoCopy: true})
		}
		tcpLayer := packet.Layer(layers.LayerTypeTCP)
		if tcpLayer == nil {
			continue
		}
		netLayer := packet.NetworkLayer()
		if netLayer == nil {
			continue
		}
		tcp := tcpLayer.(*layers.TCP)
		if !p.ports[uint16(tcp.SrcPort)] && !p.ports[uint16(tcp.DstPort)] {
			continue
		}
		stats.TCPPackets++
		assembler.AssembleWithTimestamp(netLayer.NetworkFlow(), tcp, ci.Timestamp)
		if stats.Packets%4096 == 0 {
			assembler.FlushOlderThan(ci.Timestamp.Add(-2 * time.Minute))
		}
	}
	assembler.FlushAll()
	return stats, nil
}

type streamFactory struct {
	mu       sync.Mutex
	source   string
	ports    map[uint16]bool
	broker   *Broker
	sessions map[string]*mysqlSession
	refs     map[string]int
}

type tcpStream struct {
	session *mysqlSession
	client  bool
	factory *streamFactory
	key     string
}

func (f *streamFactory) New(netFlow, tcpFlow gopacket.Flow) tcpassembly.Stream {
	srcPort := endpointPort(tcpFlow.Src().Raw())
	dstPort := endpointPort(tcpFlow.Dst().Raw())
	clientDirection := f.ports[dstPort]
	src := netFlow.Src().String() + ":" + fmt.Sprint(srcPort)
	dst := netFlow.Dst().String() + ":" + fmt.Sprint(dstPort)
	client, server := src, dst
	if !clientDirection {
		client, server = dst, src
	}
	parts := []string{src, dst}
	sort.Strings(parts)
	key := strings.Join(parts, "|")
	f.mu.Lock()
	session := f.sessions[key]
	if session == nil {
		session = newMySQLSession(f.source, client, server, f.broker.Publish)
		f.sessions[key] = session
	}
	f.refs[key]++
	f.mu.Unlock()
	return &tcpStream{session: session, client: clientDirection, factory: f, key: key}
}

func (s *tcpStream) Reassembled(chunks []tcpassembly.Reassembly) {
	for _, chunk := range chunks {
		if len(chunk.Bytes) > 0 || chunk.Skip != 0 {
			s.session.feed(s.client, chunk.Bytes, chunk.Seen, chunk.Skip)
		}
	}
}
func (s *tcpStream) ReassemblyComplete() {
	s.factory.mu.Lock()
	s.factory.refs[s.key]--
	if s.factory.refs[s.key] <= 0 {
		delete(s.factory.refs, s.key)
		delete(s.factory.sessions, s.key)
	}
	s.factory.mu.Unlock()
}

func endpointPort(raw []byte) uint16 {
	if len(raw) < 2 {
		return 0
	}
	return binary.BigEndian.Uint16(raw[len(raw)-2:])
}
