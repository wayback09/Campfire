package main

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"encoding/binary"
	"errors"
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
	SendChan   chan string
	CloseOnce  sync.Once
	Closed     chan struct{}
	GuestPipes chan net.Conn
}

func newHostSession(code string, conn net.Conn) *HostSession {
	return &HostSession{
		Code:       code,
		Control:    conn,
		SendChan:   make(chan string, 64),
		Closed:     make(chan struct{}),
		GuestPipes: make(chan net.Conn, 16),
	}
}

func (s *HostSession) Close() {
	s.CloseOnce.Do(func() {
		close(s.Closed)
		s.Control.Close()
	})
}

// Send queues a message asynchronously to the host control channel.
// Returns false if the session is closed or if the buffer is full.
func (s *HostSession) Send(msg string) bool {
	select {
	case s.SendChan <- msg:
		return true
	case <-s.Closed:
		return false
	default:
		log.Printf("[HOST] Send queue full for CAMP-%s, closing unresponsive session", s.Code)
		s.Close()
		return false
	}
}

var (
	sessions   = make(map[string]*HostSession)
	sessionsMu sync.RWMutex

	waitingGuests   = make(map[string]chan net.Conn)
	waitingGuestsMu sync.Mutex
)

const codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

// generateCodeLocked generates a unique 4-character room code with a strict iteration limit.
// MUST be called while holding sessionsMu write-lock.
func generateCodeLocked() (string, error) {
	for attempts := 0; attempts < 100; attempts++ {
		b := make([]byte, 4)
		if _, err := rand.Read(b); err != nil {
			return "", fmt.Errorf("crypto/rand read failed: %w", err)
		}
		var sb strings.Builder
		for i := 0; i < 4; i++ {
			sb.WriteByte(codeChars[int(b[i])%len(codeChars)])
		}
		code := sb.String()
		if _, exists := sessions[code]; !exists {
			return code, nil
		}
	}
	return "", errors.New("server room capacity reached (no open codes available)")
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

	// HTTP health check support for uptime pingers
	if strings.HasPrefix(peekStr, "GET ") || strings.HasPrefix(peekStr, "HEAD ") {
		conn.Write([]byte("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nConnection: close\r\nContent-Length: 15\r\n\r\nCampfire Active\n"))
		conn.Close()
		return
	}

	handleMinecraftGuest(conn, bufReader)
}

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
		generated, err := generateCodeLocked()
		if err != nil {
			sessionsMu.Unlock()
			log.Printf("[HOST REJECTED] %v", err)
			conn.Write([]byte("ERROR " + err.Error() + "\n"))
			conn.Close()
			return
		}
		code = generated
	}

	session := newHostSession(code, conn)
	sessions[code] = session
	sessionsMu.Unlock()

	log.Printf("[HOST CONNECTED] CAMP-%s from %s", code, conn.RemoteAddr())

	// Dedicated asynchronous writer goroutine for this session
	go func() {
		writer := bufio.NewWriter(session.Control)
		for {
			select {
			case msg, ok := <-session.SendChan:
				if !ok {
					return
				}
				session.Control.SetWriteDeadline(time.Now().Add(5 * time.Second))
				if _, err := writer.WriteString(msg); err != nil {
					session.Close()
					return
				}
				if err := writer.Flush(); err != nil {
					session.Close()
					return
				}
			case <-session.Closed:
				return
			}
		}
	}()

	// Send acknowledgment to host mod
	if !session.Send(fmt.Sprintf("OK %s\n", code)) {
		sessionsMu.Lock()
		delete(sessions, code)
		sessionsMu.Unlock()
		session.Close()
		return
	}

	// Reader loop for control messages & heartbeats
	go func() {
		defer func() {
			sessionsMu.Lock()
			delete(sessions, code)
			sessionsMu.Unlock()
			session.Close()
			log.Printf("[HOST DISCONNECTED] CAMP-%s", code)
		}()

		for {
			line, err := r.ReadString('\n')
			if err != nil {
				break
			}
			line = strings.TrimSpace(line)
			if line == "PING" {
				session.Send("PONG\n")
			}
		}
	}()
}

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

// handleMinecraftGuest parses the vanilla Minecraft Handshake packet (0x00)
// and bridges the connection by target hostname.
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
	if err != nil || packetID != 0x00 {
		guestConn.Close()
		return
	}

	// Protocol version
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

	if !exists || session == nil {
		log.Printf("[GUEST REJECTED] World CAMP-%s not found or offline", targetCode)
		guestConn.Close()
		return
	}

	guestID := fmt.Sprintf("%d_%d", time.Now().UnixNano(), randInt())
	pipeChan := make(chan net.Conn, 1)

	waitingGuestsMu.Lock()
	waitingGuests[guestID] = pipeChan
	waitingGuestsMu.Unlock()

	if !session.Send(fmt.Sprintf("NEW_GUEST %s\n", guestID)) {
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()
		guestConn.Close()
		return
	}

	select {
	case hostPipe, ok := <-pipeChan:
		if !ok || hostPipe == nil {
			guestConn.Close()
			return
		}
		defer hostPipe.Close()
		defer guestConn.Close()

		fullHandshake := append(lenBytes, packetData...)
		if _, err := hostPipe.Write(fullHandshake); err != nil {
			return
		}

		// Forward any remaining bytes pre-fetched by bufio.Reader
		if r.Buffered() > 0 {
			buf := make([]byte, r.Buffered())
			if _, err := io.ReadFull(r, buf); err != nil {
				return
			}
			if _, err := hostPipe.Write(buf); err != nil {
				return
			}
		}

		log.Printf("[BRIDGE ESTABLISHED] Guest %s <---> World CAMP-%s", guestConn.RemoteAddr(), targetCode)
		splice(guestConn, r, hostPipe)
		log.Printf("[BRIDGE CLOSED] Guest %s disconnected from CAMP-%s", guestConn.RemoteAddr(), targetCode)

	case <-time.After(5 * time.Second):
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()

		// Drain late pipe connection if it arrived concurrently to prevent socket leaks
		select {
		case latePipe := <-pipeChan:
			if latePipe != nil {
				latePipe.Close()
			}
		default:
		}

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

	if !exists || session == nil {
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

	if !session.Send(fmt.Sprintf("NEW_GUEST %s\n", guestID)) {
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()
		guestConn.Close()
		return
	}

	select {
	case hostPipe, ok := <-pipeChan:
		if !ok || hostPipe == nil {
			guestConn.Close()
			return
		}
		defer hostPipe.Close()
		defer guestConn.Close()

		if _, err := guestConn.Write([]byte("OK CONNECTED\n")); err != nil {
			return
		}

		// Safely forward all bytes buffered by bufio.Reader
		if r.Buffered() > 0 {
			buf := make([]byte, r.Buffered())
			if _, err := io.ReadFull(r, buf); err != nil {
				return
			}
			if _, err := hostPipe.Write(buf); err != nil {
				return
			}
		}

		log.Printf("[BRIDGE ESTABLISHED] Guest %s <---> World CAMP-%s", guestConn.RemoteAddr(), targetCode)
		splice(guestConn, r, hostPipe)
		log.Printf("[BRIDGE CLOSED] Guest %s disconnected from CAMP-%s", guestConn.RemoteAddr(), targetCode)

	case <-time.After(5 * time.Second):
		waitingGuestsMu.Lock()
		delete(waitingGuests, guestID)
		waitingGuestsMu.Unlock()

		// Drain late pipe connection if it arrived concurrently to prevent socket leaks
		select {
		case latePipe := <-pipeChan:
			if latePipe != nil {
				latePipe.Close()
			}
		default:
		}

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

func splice(c1 net.Conn, r1 io.Reader, c2 net.Conn) {
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		if r1 != nil {
			io.Copy(c2, r1)
		} else {
			io.Copy(c2, c1)
		}
		if tc, ok := c2.(*net.TCPConn); ok {
			tc.CloseWrite()
		}
	}()

	go func() {
		defer wg.Done()
		io.Copy(c1, c2)
		if tc, ok := c1.(*net.TCPConn); ok {
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
	if _, err := rand.Read(b); err != nil {
		return time.Now().UnixNano()
	}
	return int64(binary.BigEndian.Uint64(b))
}
