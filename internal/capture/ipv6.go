package capture

import (
	"fmt"
	"sort"
	"time"

	"github.com/google/gopacket"
	"github.com/google/gopacket/layers"
)

const (
	maxIPv6FragmentSets = 1024
	maxIPv6Datagram     = 16 << 20
)

type ipv6FragmentKey struct {
	src, dst string
	id       uint32
	next     layers.IPProtocol
}
type ipv6FragmentSet struct {
	header      layers.IPv6
	chunks      map[int][]byte
	total, size int
	seen        time.Time
}
type ipv6Defragmenter struct {
	sets map[ipv6FragmentKey]*ipv6FragmentSet
}

func newIPv6Defragmenter() *ipv6Defragmenter {
	return &ipv6Defragmenter{sets: make(map[ipv6FragmentKey]*ipv6FragmentSet)}
}

func (d *ipv6Defragmenter) add(ip *layers.IPv6, fragment *layers.IPv6Fragment, seen time.Time) ([]byte, bool, error) {
	for key, set := range d.sets {
		if seen.Sub(set.seen) > 30*time.Second {
			delete(d.sets, key)
		}
	}
	key := ipv6FragmentKey{src: ip.SrcIP.String(), dst: ip.DstIP.String(), id: fragment.Identification, next: fragment.NextHeader}
	set := d.sets[key]
	if set == nil {
		if len(d.sets) >= maxIPv6FragmentSets {
			var oldestKey ipv6FragmentKey
			var oldest time.Time
			for k, v := range d.sets {
				if oldest.IsZero() || v.seen.Before(oldest) {
					oldestKey, oldest = k, v.seen
				}
			}
			delete(d.sets, oldestKey)
		}
		set = &ipv6FragmentSet{header: *ip, chunks: make(map[int][]byte)}
		d.sets[key] = set
	}
	set.seen = seen
	offset := int(fragment.FragmentOffset) * 8
	if offset+len(fragment.Payload) > maxIPv6Datagram {
		delete(d.sets, key)
		return nil, false, fmt.Errorf("IPv6 fragmented datagram exceeds %d bytes", maxIPv6Datagram)
	}
	if _, exists := set.chunks[offset]; !exists {
		set.chunks[offset] = append([]byte(nil), fragment.Payload...)
		set.size += len(fragment.Payload)
	}
	if !fragment.MoreFragments {
		set.total = offset + len(fragment.Payload)
	}
	if set.total == 0 || set.size < set.total {
		return nil, false, nil
	}
	offsets := make([]int, 0, len(set.chunks))
	for offset := range set.chunks {
		offsets = append(offsets, offset)
	}
	sort.Ints(offsets)
	payload := make([]byte, 0, set.total)
	position := 0
	for _, offset := range offsets {
		if offset != position {
			return nil, false, nil
		}
		payload = append(payload, set.chunks[offset]...)
		position += len(set.chunks[offset])
		if position >= set.total {
			break
		}
	}
	if position != set.total {
		return nil, false, nil
	}
	delete(d.sets, key)
	header := set.header
	header.NextHeader = fragment.NextHeader
	header.BaseLayer = layers.BaseLayer{}
	buf := gopacket.NewSerializeBuffer()
	if err := gopacket.SerializeLayers(buf, gopacket.SerializeOptions{FixLengths: true}, &header, gopacket.Payload(payload)); err != nil {
		return nil, false, err
	}
	return buf.Bytes(), true, nil
}
