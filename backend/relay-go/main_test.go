package main

import (
	"bufio"
	"bytes"
	"net"
	"strings"
	"testing"
	"time"
)

func TestExtractCode(t *testing.T) {
	cases := []struct {
		in   string
		want string
	}{
		{"CAMP-ABCD", "ABCD"},
		{"camp-abcd", "ABCD"},
		{"ABCD", "ABCD"},
		{"ABCD:25565", "ABCD"},
		{"CAMP-ABCD.example.com", "ABCD"},
		{"CAMPABCD", "ABCD"},
		{"  camp-abcd  ", "ABCD"},
		{"", ""},
	}
	for _, c := range cases {
		if got := extractCode(c.in); got != c.want {
			t.Errorf("extractCode(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestGenerateCodeIsUniqueAndWellFormed(t *testing.T) {
	sessionsMu.Lock()
	defer sessionsMu.Unlock()

	seen := map[string]bool{}
	for i := 0; i < 200; i++ {
		code, err := generateCodeLocked()
		if err != nil {
			t.Fatalf("generateCodeLocked() error = %v", err)
		}
		if len(code) != 4 {
			t.Fatalf("code %q has length %d, want 4", code, len(code))
		}
		for _, r := range code {
			if !strings.ContainsRune(codeChars, r) {
				t.Fatalf("code %q contains disallowed rune %q", code, r)
			}
		}
		if seen[code] {
			t.Fatalf("duplicate code generated: %q", code)
		}
		seen[code] = true
	}
}

// writeVarInt encodes an unsigned VarInt the way Minecraft does.
func writeVarInt(buf *bytes.Buffer, value int) {
	for {
		b := byte(value & 0x7F)
		value >>= 7
		if value != 0 {
			b |= 0x80
		}
		buf.WriteByte(b)
		if value == 0 {
			return
		}
	}
}

func TestReadVarInt(t *testing.T) {
	cases := []struct {
		in   []byte
		want int
	}{
		{[]byte{0x00}, 0},
		{[]byte{0x01}, 1},
		{[]byte{0x7F}, 127},
		{[]byte{0x80, 0x01}, 128},
		{[]byte{0xFF, 0xFF, 0xFF, 0x7F}, 268435455},
	}
	for _, c := range cases {
		got, _, err := readVarInt(bytes.NewReader(c.in))
		if err != nil {
			t.Fatalf("readVarInt(%v) error = %v", c.in, err)
		}
		if got != c.want {
			t.Errorf("readVarInt(%v) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestReadVarIntRoundTrip(t *testing.T) {
	for _, v := range []int{0, 1, 127, 128, 25565, 268435455} {
		var buf bytes.Buffer
		writeVarInt(&buf, v)

		got, raw, err := readVarInt(bytes.NewReader(buf.Bytes()))
		if err != nil {
			t.Fatalf("readVarInt round trip failed for %d: %v", v, err)
		}
		if got != v {
			t.Errorf("round trip = %d, want %d", got, v)
		}
		if !bytes.Equal(raw, buf.Bytes()) {
			t.Errorf("raw bytes = %v, want %v", raw, buf.Bytes())
		}
	}
}

func startTestRelay(t *testing.T) (string, func()) {
	t.Helper()

	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}

	done := make(chan struct{})
	go func() {
		defer close(done)
		serve(listener)
	}()

	cleanup := func() {
		listener.Close()
		select {
		case <-done:
		case <-time.After(2 * time.Second):
			t.Log("relay did not stop in time")
		}
	}
	return listener.Addr().String(), cleanup
}

func TestHostRegistrationAssignsCode(t *testing.T) {
	addr, cleanup := startTestRelay(t)
	defer cleanup()

	conn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()

	conn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := conn.Write([]byte("CAMPFIRE_HOST\n")); err != nil {
		t.Fatalf("write: %v", err)
	}

	reader := bufio.NewReader(conn)
	line, err := reader.ReadString('\n')
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if !strings.HasPrefix(line, "OK ") {
		t.Fatalf("expected OK prefix, got %q", line)
	}
	code := strings.TrimSpace(strings.TrimPrefix(line, "OK "))
	if len(code) != 4 {
		t.Fatalf("expected 4 character code, got %q", code)
	}

	// The session must be registered under that code.
	sessionsMu.RLock()
	_, ok := sessions[code]
	sessionsMu.RUnlock()
	if !ok {
		t.Fatalf("session %q was not registered", code)
	}
}

func TestHostRegistrationWithRequestedCode(t *testing.T) {
	addr, cleanup := startTestRelay(t)
	defer cleanup()

	conn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()

	conn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := conn.Write([]byte("CAMPFIRE_HOST WXYZ\n")); err != nil {
		t.Fatalf("write: %v", err)
	}

	reader := bufio.NewReader(conn)
	line, err := reader.ReadString('\n')
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if got := strings.TrimSpace(strings.TrimPrefix(line, "OK ")); got != "WXYZ" {
		t.Fatalf("expected requested code WXYZ, got %q", got)
	}
}

func TestJoinToUnknownWorldIsRejected(t *testing.T) {
	addr, cleanup := startTestRelay(t)
	defer cleanup()

	conn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()

	conn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := conn.Write([]byte("CAMPFIRE_JOIN ZZZZ\n")); err != nil {
		t.Fatalf("write: %v", err)
	}

	reader := bufio.NewReader(conn)
	line, err := reader.ReadString('\n')
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if !strings.HasPrefix(line, "ERROR") {
		t.Fatalf("expected ERROR for unknown world, got %q", line)
	}
}

func TestHealthCheckResponds(t *testing.T) {
	addr, cleanup := startTestRelay(t)
	defer cleanup()

	conn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer conn.Close()

	conn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := conn.Write([]byte("GET / HTTP/1.1\r\nHost: x\r\n\r\n")); err != nil {
		t.Fatalf("write: %v", err)
	}

	buf := make([]byte, 256)
	n, _ := conn.Read(buf)
	if !strings.Contains(string(buf[:n]), "200 OK") {
		t.Fatalf("expected 200 OK, got %q", string(buf[:n]))
	}
}

// TestGuestBridgesThroughHost verifies the full tunnel path: the host
// registers, a guest joins, the relay asks the host for a pipe, the host opens
// one, and bytes flow guest -> host.
func TestGuestBridgesThroughHost(t *testing.T) {
	addr, cleanup := startTestRelay(t)
	defer cleanup()

	// --- host ---
	hostConn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("dial host: %v", err)
	}
	defer hostConn.Close()
	hostConn.SetDeadline(time.Now().Add(10 * time.Second))

	if _, err := hostConn.Write([]byte("CAMPFIRE_HOST\n")); err != nil {
		t.Fatalf("host write: %v", err)
	}
	hostReader := bufio.NewReader(hostConn)
	line, err := hostReader.ReadString('\n')
	if err != nil {
		t.Fatalf("host read: %v", err)
	}
	code := strings.TrimSpace(strings.TrimPrefix(line, "OK "))
	if len(code) != 4 {
		t.Fatalf("bad code %q", code)
	}

	// --- guest ---
	guestConn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("dial guest: %v", err)
	}
	defer guestConn.Close()
	guestConn.SetDeadline(time.Now().Add(10 * time.Second))

	if _, err := guestConn.Write([]byte("CAMPFIRE_JOIN " + code + "\n")); err != nil {
		t.Fatalf("guest write: %v", err)
	}

	// --- host reacts to NEW_GUEST by opening a pipe ---
	newGuestCh := make(chan string, 1)
	go func() {
		for {
			l, err := hostReader.ReadString('\n')
			if err != nil {
				return
			}
			l = strings.TrimSpace(l)
			if strings.HasPrefix(l, "NEW_GUEST ") {
				newGuestCh <- strings.TrimSpace(strings.TrimPrefix(l, "NEW_GUEST "))
				return
			}
		}
	}()

	var guestID string
	select {
	case guestID = <-newGuestCh:
	case <-time.After(5 * time.Second):
		t.Fatal("host never received NEW_GUEST")
	}

	pipeConn, err := net.Dial("tcp", addr)
	if err != nil {
		t.Fatalf("dial pipe: %v", err)
	}
	defer pipeConn.Close()
	pipeConn.SetDeadline(time.Now().Add(10 * time.Second))

	if _, err := pipeConn.Write([]byte("CAMPFIRE_PIPE " + guestID + "\n")); err != nil {
		t.Fatalf("pipe write: %v", err)
	}

	// --- guest should be acknowledged ---
	guestReader := bufio.NewReader(guestConn)
	ack, err := guestReader.ReadString('\n')
	if err != nil {
		t.Fatalf("guest ack read: %v", err)
	}
	if !strings.HasPrefix(ack, "OK CONNECTED") {
		t.Fatalf("expected OK CONNECTED, got %q", ack)
	}

	// --- bytes flow guest -> host pipe ---
	payload := []byte("hello-from-guest")
	if _, err := guestConn.Write(payload); err != nil {
		t.Fatalf("guest payload write: %v", err)
	}

	buf := make([]byte, len(payload))
	if _, err := readFull(pipeConn, buf); err != nil {
		t.Fatalf("host pipe read: %v", err)
	}
	if string(buf) != string(payload) {
		t.Fatalf("host pipe got %q, want %q", buf, payload)
	}
}

func readFull(conn net.Conn, buf []byte) (int, error) {
	total := 0
	for total < len(buf) {
		n, err := conn.Read(buf[total:])
		total += n
		if err != nil {
			return total, err
		}
	}
	return total, nil
}

func TestRandIntIsNonNegative(t *testing.T) {
	for i := 0; i < 1000; i++ {
		if v := randInt(); v < 0 {
			t.Fatalf("randInt returned a negative value: %d", v)
		}
	}
}
