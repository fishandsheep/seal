package capture

import (
	"bytes"
	"net"
	"testing"
	"time"

	"github.com/google/gopacket"
	"github.com/google/gopacket/layers"
)

func TestIPv6FragmentsReassembleOutOfOrder(t *testing.T) {
	d := newIPv6Defragmenter()
	ip := &layers.IPv6{Version: 6, HopLimit: 64, SrcIP: net.ParseIP("2001:db8::1"), DstIP: net.ParseIP("2001:db8::2"), NextHeader: layers.IPProtocolIPv6Fragment}
	last := &layers.IPv6Fragment{NextHeader: layers.IPProtocolTCP, FragmentOffset: 1, Identification: 9, BaseLayer: layers.BaseLayer{Payload: []byte("tail")}}
	if _, complete, err := d.add(ip, last, time.Now()); err != nil || complete {
		t.Fatalf("last complete=%v err=%v", complete, err)
	}
	first := &layers.IPv6Fragment{NextHeader: layers.IPProtocolTCP, MoreFragments: true, Identification: 9, BaseLayer: layers.BaseLayer{Payload: []byte("12345678")}}
	data, complete, err := d.add(ip, first, time.Now())
	if err != nil || !complete {
		t.Fatalf("complete=%v err=%v", complete, err)
	}
	packet := gopacket.NewPacket(data, layers.LayerTypeIPv6, gopacket.Default)
	layer := packet.Layer(layers.LayerTypeIPv6)
	if layer == nil {
		t.Fatalf("layers=%v error=%v", packet.Layers(), packet.ErrorLayer())
	}
	got := layer.(*layers.IPv6).Payload
	if !bytes.Equal(got, []byte("12345678tail")) {
		t.Fatalf("payload=%q", got)
	}
}
