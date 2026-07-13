package capture

import (
	"bytes"
	"encoding/binary"
	"net"
	"testing"
	"time"

	"github.com/google/gopacket"
	"github.com/google/gopacket/layers"
	"github.com/google/gopacket/pcapgo"
)

func TestPCAPTCPReassemblyAcrossSupportedLinkTypes(t *testing.T) {
	for _, link := range []layers.LinkType{layers.LinkTypeEthernet, layers.LinkTypeLinuxSLL, layers.LinkTypeNull, layers.LinkTypeRaw} {
		t.Run(link.String(), func(t *testing.T) {
			pcap := syntheticPCAP(t, link)
			broker := NewBroker(100)
			parser := NewParser([]uint16{3306}, broker)
			stats, err := parser.Parse(bytes.NewReader(pcap), "test")
			if err != nil {
				t.Fatal(err)
			}
			if stats.TCPPackets < 5 {
				r, _ := pcapgo.NewReader(bytes.NewReader(pcap))
				data, _, _ := r.ReadPacketData()
				packet := gopacket.NewPacket(data, r.LinkType(), gopacket.Default)
				t.Logf("link=%v layers=%v error=%v bytes=%x", r.LinkType(), packet.Layers(), packet.ErrorLayer(), data[:min(len(data), 32)])
				t.Fatalf("tcp packets=%d", stats.TCPPackets)
			}
			events := broker.Recent(10)
			if len(events) != 1 || events[0].Fingerprint == "" || events[0].SQL != "select name from users where id = ?" {
				t.Fatalf("events=%+v", events)
			}
		})
	}
}

func syntheticPCAP(t *testing.T, link layers.LinkType) []byte {
	t.Helper()
	var out bytes.Buffer
	writer := pcapgo.NewWriter(&out)
	if err := writer.WriteFileHeader(65535, link); err != nil {
		t.Fatal(err)
	}
	now := time.Now()
	clientIP := net.IPv4(10, 0, 0, 2)
	serverIP := net.IPv4(10, 0, 0, 3)
	query := mysqlPacket(0, append([]byte{0x03}, []byte("SELECT name FROM users WHERE id=42")...))
	response := mysqlPacket(1, []byte{0x01})
	packets := [][]byte{
		serializeTCP(t, link, clientIP, serverIP, 40000, 3306, 100, 0, true, false, nil),
		serializeTCP(t, link, serverIP, clientIP, 3306, 40000, 200, 101, true, true, nil),
		serializeTCP(t, link, clientIP, serverIP, 40000, 3306, 101, 201, false, true, nil),
		serializeTCP(t, link, clientIP, serverIP, 40000, 3306, 101, 201, false, true, query),
		serializeTCP(t, link, serverIP, clientIP, 3306, 40000, 201, uint32(101+len(query)), false, true, response),
	}
	for i, data := range packets {
		ci := gopacket.CaptureInfo{Timestamp: now.Add(time.Duration(i) * time.Millisecond), CaptureLength: len(data), Length: len(data)}
		if err := writer.WritePacket(ci, data); err != nil {
			t.Fatal(err)
		}
	}
	return out.Bytes()
}

func serializeTCP(t *testing.T, link layers.LinkType, srcIP, dstIP net.IP, srcPort, dstPort uint16, seq, ack uint32, syn, ackFlag bool, payload []byte) []byte {
	t.Helper()
	ip := &layers.IPv4{Version: 4, TTL: 64, SrcIP: srcIP, DstIP: dstIP, Protocol: layers.IPProtocolTCP}
	tcp := &layers.TCP{SrcPort: layers.TCPPort(srcPort), DstPort: layers.TCPPort(dstPort), Seq: seq, Ack: ack, SYN: syn, ACK: ackFlag, PSH: len(payload) > 0, Window: 65535}
	_ = tcp.SetNetworkLayerForChecksum(ip)
	buf := gopacket.NewSerializeBuffer()
	if err := gopacket.SerializeLayers(buf, gopacket.SerializeOptions{FixLengths: true, ComputeChecksums: true}, ip, tcp, gopacket.Payload(payload)); err != nil {
		t.Fatal(err)
	}
	network := buf.Bytes()
	switch link {
	case layers.LinkTypeRaw:
		return append([]byte(nil), network...)
	case layers.LinkTypeNull:
		header := make([]byte, 4)
		binary.LittleEndian.PutUint32(header, 2)
		return append(header, network...)
	case layers.LinkTypeLinuxSLL:
		header := make([]byte, 16)
		binary.BigEndian.PutUint16(header[2:4], 1)
		binary.BigEndian.PutUint16(header[4:6], 6)
		binary.BigEndian.PutUint16(header[14:16], uint16(layers.EthernetTypeIPv4))
		return append(header, network...)
	default:
		eth := &layers.Ethernet{SrcMAC: net.HardwareAddr{2, 0, 0, 0, 0, 2}, DstMAC: net.HardwareAddr{2, 0, 0, 0, 0, 3}, EthernetType: layers.EthernetTypeIPv4}
		full := gopacket.NewSerializeBuffer()
		if err := gopacket.SerializeLayers(full, gopacket.SerializeOptions{FixLengths: true, ComputeChecksums: true}, eth, gopacket.Payload(network)); err != nil {
			t.Fatal(err)
		}
		return full.Bytes()
	}
}
