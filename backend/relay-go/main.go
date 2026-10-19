package main

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strings"
	"sync"
	"time"
)

// HostSession represents an active world hosted through Campfire.
type HostSession struct {
	Code       string
	Control    net.Conn
	Writer     *bufio.Writer
	GuestPipes chan net.Conn
	Mu         sync.Mutex
	Closed     bool
}

var (
	sessions   = make(map[string]*HostSession)
	sessionsMu sync.RWMutex

	waitingGuests   = make(map[string]chan net.Conn)
	waitingGuestsMu sync.Mutex
)

const codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Ambiguity-free Base32 (no 0, 1, I, O)

// generateCodeLocked generates a 4-character alphanumeric room code.
// MUST be called while holding sessionsMu write-lock.
func generateCodeLocked() string {
	for {
		b := make([]byte, 4)
		rand.Read(b)
		var sb strings.Builder
		for i := 0; i < 4; i++ {
			sb.WriteByte(codeChars[int(b[i])%len(codeChars)])
		}
		code := sb.String()
		if _, exists := sessions[code]; !exists {
			return code
		}
	}
}

func main() {
	port := os.Getenv("SERVER_PORT")
	if port == "" {
		port = os.Getenv("PORT")
	}
	if port == "" {
		port = "25631"
	}

	addr := ":" + port
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		log.Fatalf("[FATAL] Failed to bind to %s: %v", addr, err)
	}
	defer listener.Close()

	log.Printf("==================================================")
	log.Printf("🔥 Campfire High-Performance Go Relay Server 🔥")
	log.Printf("Listening on: %s", addr)
	log.Printf("Minecraft Virtual-Host & Tunnel Multiplexer Active")
	log.Printf("==================================================")

	for {
		conn, err := listener.Accept()
		if err != nil {
			log.Printf("[WARN] Accept error: %v", err)
			continue
		}
		go handleIncoming(conn)
	}
}

func handleIncoming(conn net.Conn) {
	// Read initial peek buffer to identify connection protocol:
	// 1. CAMPFIRE_HOST <code?>
	// 2. CAMPFIRE_PIPE <guest_id>
	// 3. CAMPFIRE_JOIN <code?>
	// 4. Vanilla Minecraft handshake packet
	bufReader := bufio.NewReader(conn)
	peek, err := bufReader.Peek(13)
	if err != nil {
		conn.Close()
		return
	}

	peekStr := string(peek)

	if strings.HasPrefix(peekStr, "CAMPFIRE_HOST") {
		line, err := bufReader.ReadString('\n')
		if err != nil {
			conn.Close()
			return
		}
		handleHostRegistration(conn, bufReader, strings.TrimSpace(line))
		return
	}

	if strings.HasPrefix(peekStr, "CAMPFIRE_PIPE") {
		line, err := bufReader.ReadString('\n')
		if err != nil {
			conn.Close()
			return
		}
		handleHostPipe(conn, strings.TrimSpace(line))
		return
	}

	if strings.HasPrefix(peekStr, "CAMPFIRE_JOIN") {
		line, err := bufReader.ReadString('\n')
		if err != nil {
			conn.Close()
			return
		}
		handleCampfireModGuest(conn, bufReader, strings.TrimSpace(line))
		return
	}

	// Otherwise, vanilla Minecraft client direct handshake
	handleMinecraftGuest(conn, bufReader)
}

// handleHostRegistration registers a new world hosting tunnel from the Campfire mod.
func handleHostRegistration(conn net.Conn, r *bufio.Reader, command string) {
	parts := strings.Fields(command)
	var requestedCode string
	if len(parts) >= 2 {
		requestedCode = strings.ToUpper(strings.TrimSpace(parts[1]))
	}

	sessionsMu.Lock()
	var code string
	if len(requestedCode) >= 4 && sessions[requestedCode] == nil {
		code = requestedCode
	} else {
		code = generateCodeLocked()
	}

	session := &HostSession{
		Code:       code,
		Control:    conn,
		Writer:     bufio.NewWriter(conn),
		GuestPipes: make(chan net.Conn, 16),
	}
	sessions[code] = session
	sessionsMu.Unlock()

	log.Printf("[HOST CONNECTED] CAMP-%s from %s", code, conn.RemoteAddr())

	// Acknowledge back to host mod with the assigned room code
	ackMsg := fmt.Sprintf("OK %s\n", code)
	if _, err := session.Control.Write([]byte(ackMsg)); err != nil {
		log.Printf("[HOST ERROR] Failed to send ack to %s: %v", conn.RemoteAddr(), err)
		conn.Close()
		return
	}

	// Keepalive & ping-pong loop
	go func() {
		for {
			line, err := r.ReadString('\n')
			if err != nil {
				break
			}
			line = strings.TrimSpace(line)
			if line == "PING" {
				session.Mu.Lock()
				session.Writer.WriteString("PONG\n")
				session.Writer.Flush()
				session.Mu.Unlock()
			}
		}

		// Cleanup on disconnect
		sessionsMu.Lock()
		delete(sessions, code)
		sessionsMu.Unlock()
		session.Closed = true
		conn.Close()
		log.Printf("[HOST DISCONNECTED] CAMP-%s", code)
	}()
}

// handleHostPipe matches a host reverse connection to a waiting guest.
func handleHostPipe(hostPipe net.Conn, command string) {
	parts := strings.Fields(command)
	if len(parts) < 2 {
		hostPipe.Close()
		return
	}
	guestID := parts[1]

	waitingGuestsMu.Lock()
	ch, ok := waitingGuests[guestID]
	if ok {
		delete(waitingGuests, guestID)
	}
	waitingGuestsMu.Unlock()

	if !ok {
		hostPipe.Close()
		return
	}

	ch <- hostPipe
}

// handleMinecraftGuest handles vanilla Minecraft clients entering via custom domain / SRV record.
func handleMinecraftGuest(guestConn net.Conn, r *bufio.Reader) {
	packetLength, lenBytes, err := readVarInt(r)
	if err != nil || packetLength <= 0 {
		guestConn.Close()
		return
	}

	packetData := make([]byte, packetLength)
	if _, err := io.ReadFull(r, packetData); err != nil {
		guestConn.Close()
		return
	}

	dataBuf := bytes.NewReader(packetData)
	packetID, _, err := readVarInt(dataBuf)
	if err != nil || packetID != 0x00 { // Handshake packet ID is 0x00
		guestConn.Close()
		return
	}

	// Protocol version (VarInt)
	_, _, err = readVarInt(dataBuf)
	if err != nil {
		guestConn.Close()
		return
	}

	// Server address
	addrLen, _, err := readVarInt(dataBuf)
	if err != nil || addrLen <= 0 || addrLen > 255 {
		guestConn.Close()
		return
	}
	addrBytes := make([]byte, addrLen)
	if _, err := io.ReadFull(dataBuf, addrBytes); err != nil {
		guestConn.Close()
		return
	}
	serverAddress := string(addrBytes)

	targetCode := extractCode(serverAddress)
	log.Printf("[GUEST] %s connecting for world CAMP-%s (addr: %q)", guestConn.RemoteAddr(), targetCode, serverAddress)

	sessionsMu.RLock()
	session, exists := sessions[targetCode]
	sessionsMu.RUnlock()

	if !exists || session == nil || session.Closed {
		log.Printf("[GUEST REJECTED] World CAMP-%s not found or offline", targetCode)
		guestConn.Close()
		return
	}

	guestID := fmt.Sprintf("%d_%d", time.Now().UnixNano(), randInt())
	pipeChan := make(chan net.Conn, 1)

	waitingGuestsMu.Lock()
	waitingGuests[guestID] = pipeChan
	waitingGuestsMu.Unlock()

	session.Mu.Lock()
	_, writeErr := session.Writer.WriteString(fmt.Sprintf("NEW_GUEST %s\n", guestID))
	if writeErr == nil {
		session.Writer.Flush()
	}
	session.Mu.Unlock()

	if writeErr != nil {
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()
		guestConn.Close()
		return
	}

	select {
	case hostPipe := <-pipeChan:
		defer hostPipe.Close()
		defer guestConn.Close()

		fullHandshake := append(lenBytes, packetData...)
		if _, err := hostPipe.Write(fullHandshake); err != nil {
			return
		}

		log.Printf("[BRIDGE ESTABLISHED] Guest %s <---> World CAMP-%s", guestConn.RemoteAddr(), targetCode)
		splice(guestConn, hostPipe)
		log.Printf("[BRIDGE CLOSED] Guest %s disconnected from CAMP-%s", guestConn.RemoteAddr(), targetCode)

	case <-time.After(5 * time.Second):
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()
		log.Printf("[GUEST TIMEOUT] World CAMP-%s took too long to pipe for %s", targetCode, guestConn.RemoteAddr())
		guestConn.Close()
	}
}

// handleCampfireModGuest handles direct guest connections initiated by the Campfire client mod.
func handleCampfireModGuest(guestConn net.Conn, r *bufio.Reader, command string) {
	parts := strings.Fields(command)
	if len(parts) < 2 {
		guestConn.Close()
		return
	}
	targetCode := extractCode(parts[1])
	log.Printf("[MOD GUEST] %s connecting for world CAMP-%s", guestConn.RemoteAddr(), targetCode)

	sessionsMu.RLock()
	session, exists := sessions[targetCode]
	sessionsMu.RUnlock()

	if !exists || session == nil || session.Closed {
		log.Printf("[MOD GUEST REJECTED] World CAMP-%s not found or offline", targetCode)
		guestConn.Write([]byte("ERROR World not found or offline\n"))
		guestConn.Close()
		return
	}

	guestID := fmt.Sprintf("%d_%d", time.Now().UnixNano(), randInt())
	pipeChan := make(chan net.Conn, 1)

	waitingGuestsMu.Lock()
	waitingGuests[guestID] = pipeChan
	waitingGuestsMu.Unlock()

	session.Mu.Lock()
	_, writeErr := session.Writer.WriteString(fmt.Sprintf("NEW_GUEST %s\n", guestID))
	if writeErr == nil {
		session.Writer.Flush()
	}
	session.Mu.Unlock()

	if writeErr != nil {
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()
		guestConn.Close()
		return
	}

	select {
	case hostPipe := <-pipeChan:
		defer hostPipe.Close()
		defer guestConn.Close()

		if _, err := guestConn.Write([]byte("OK CONNECTED\n")); err != nil {
			return
		}

		if r.Buffered() > 0 {
			buf := make([]byte, r.Buffered())
			r.Read(buf)
			hostPipe.Write(buf)
		}

		log.Printf("[BRIDGE ESTABLISHED] Guest %s <---> World CAMP-%s", guestConn.RemoteAddr(), targetCode)
		splice(guestConn, hostPipe)
		log.Printf("[BRIDGE CLOSED] Guest %s disconnected from CAMP-%s", guestConn.RemoteAddr(), targetCode)

	case <-time.After(5 * time.Second):
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()
		log.Printf("[GUEST TIMEOUT] World CAMP-%s took too long to pipe for %s", targetCode, guestConn.RemoteAddr())
		guestConn.Close()
	}
}

func extractCode(addr string) string {
	raw := strings.ToUpper(strings.TrimSpace(addr))
	if idx := strings.Index(raw, ":"); idx != -1 {
		raw = raw[:idx]
	}
	if idx := strings.Index(raw, "."); idx != -1 {
		raw = raw[:idx]
	}
	raw = strings.ReplaceAll(raw, "CAMP-", "")
	raw = strings.ReplaceAll(raw, "CAMP", "")
	raw = strings.ReplaceAll(raw, "-", "")
	return raw
}

func splice(c1, c2 net.Conn) {
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		io.Copy(c1, c2)
		if tc, ok := c1.(*net.TCPConn); ok {
			tc.CloseWrite()
		}
	}()

	go func() {
		defer wg.Done()
		io.Copy(c2, c1)
		if tc, ok := c2.(*net.TCPConn); ok {
			tc.CloseWrite()
		}
	}()

	wg.Wait()
}

func readVarInt(r io.Reader) (int, []byte, error) {
	var val int
	var raw []byte
	for i := 0; i < 5; i++ {
		b := make([]byte, 1)
		if _, err := io.ReadFull(r, b); err != nil {
			return 0, nil, err
		}
		raw = append(raw, b[0])
		val |= int(b[0]&0x7F) << (7 * i)
		if (b[0] & 0x80) == 0 {
			break
		}
	}
	return val, raw, nil
}

func randInt() int64 {
	b := make([]byte, 8)
	rand.Read(b)
	return int64(binary.BigEndian.Uint64(b))
}
