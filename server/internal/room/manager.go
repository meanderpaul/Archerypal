package room

import (
	"errors"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

const maxMessageSize = 4096

var (
	ErrRoomFull     = errors.New("room full")
	ErrRoomNotFound = errors.New("room not found")
	ErrTooManyRooms = errors.New("too many active rooms")
)

type Manager struct {
	mu              sync.Mutex
	rooms           map[string]*liveRoom
	maxRooms        int
	roomTTL         time.Duration
	allowedOrigins  map[string]struct{}
	upgrader        websocket.Upgrader
}

type liveRoom struct {
	id        string
	hostCode  string
	expiresAt time.Time
	clients   map[string]*client
	mu        sync.Mutex
}

type client struct {
	friendCode string
	conn       *websocket.Conn
	send       chan []byte
}

func ParseAllowedOrigins(publicWSBase string, extra ...string) map[string]struct{} {
	out := make(map[string]struct{})
	add := func(host string) {
		host = strings.TrimSpace(strings.ToLower(host))
		if host != "" {
			out[host] = struct{}{}
		}
	}
	for _, raw := range append([]string{publicWSBase}, extra...) {
		raw = strings.TrimSpace(raw)
		if raw == "" {
			continue
		}
		if !strings.Contains(raw, "://") {
			raw = "wss://" + raw
		}
		u, err := url.Parse(raw)
		if err != nil {
			continue
		}
		add(u.Host)
	}
	return out
}

func NewManager(maxRooms int, roomTTL time.Duration, allowedOrigins map[string]struct{}) *Manager {
	if allowedOrigins == nil {
		allowedOrigins = map[string]struct{}{}
	}
	m := &Manager{
		rooms:          make(map[string]*liveRoom),
		maxRooms:       maxRooms,
		roomTTL:        roomTTL,
		allowedOrigins: allowedOrigins,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
			CheckOrigin:     func(r *http.Request) bool { return originAllowed(r, allowedOrigins) },
		},
	}
	go m.reapLoop()
	return m
}

func originAllowed(r *http.Request, allowed map[string]struct{}) bool {
	if len(allowed) == 0 {
		return true
	}
	origin := strings.TrimSpace(r.Header.Get("Origin"))
	if origin == "" {
		// Native mobile clients typically omit Origin.
		return true
	}
	u, err := url.Parse(origin)
	if err != nil {
		return false
	}
	_, ok := allowed[strings.ToLower(u.Host)]
	return ok
}

func (m *Manager) ActiveCount() int {
	m.mu.Lock()
	defer m.mu.Unlock()
	return len(m.rooms)
}

func (m *Manager) Create(roomID, hostFriendCode string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if len(m.rooms) >= m.maxRooms {
		return ErrTooManyRooms
	}
	if _, ok := m.rooms[roomID]; ok {
		return errors.New("room exists")
	}
	m.rooms[roomID] = &liveRoom{
		id:        roomID,
		hostCode:  hostFriendCode,
		expiresAt: time.Now().Add(m.roomTTL),
		clients:   make(map[string]*client),
	}
	return nil
}

func (m *Manager) Close(roomID string) {
	m.mu.Lock()
	r, ok := m.rooms[roomID]
	if ok {
		// Re-check expiry-friendly close: only remove if still present.
		delete(m.rooms, roomID)
	}
	m.mu.Unlock()
	if !ok {
		return
	}
	r.mu.Lock()
	for _, c := range r.clients {
		close(c.send)
		_ = c.conn.Close()
	}
	r.clients = nil
	r.mu.Unlock()
}

func (m *Manager) reapLoop() {
	tick := time.NewTicker(time.Minute)
	defer tick.Stop()
	for range tick.C {
		now := time.Now()
		var expired []string
		m.mu.Lock()
		for id, r := range m.rooms {
			if now.After(r.expiresAt) {
				expired = append(expired, id)
			}
		}
		m.mu.Unlock()
		for _, id := range expired {
			m.closeIfExpired(id)
		}
	}
}

func (m *Manager) closeIfExpired(roomID string) {
	m.mu.Lock()
	r, ok := m.rooms[roomID]
	if !ok || !time.Now().After(r.expiresAt) {
		m.mu.Unlock()
		return
	}
	delete(m.rooms, roomID)
	m.mu.Unlock()
	r.mu.Lock()
	for _, c := range r.clients {
		close(c.send)
		_ = c.conn.Close()
	}
	r.clients = nil
	r.mu.Unlock()
}

func (m *Manager) HostFriendCode(roomID string) (string, bool) {
	m.mu.Lock()
	r, ok := m.rooms[roomID]
	m.mu.Unlock()
	if !ok {
		return "", false
	}
	return r.hostCode, true
}

func (m *Manager) Upgrade(w http.ResponseWriter, r *http.Request) (*websocket.Conn, error) {
	return m.upgrader.Upgrade(w, r, nil)
}

func (m *Manager) Join(roomID, friendCode string, conn *websocket.Conn) error {
	m.mu.Lock()
	r, ok := m.rooms[roomID]
	m.mu.Unlock()
	if !ok {
		return ErrRoomNotFound
	}
	r.mu.Lock()
	if len(r.clients) >= 8 {
		r.mu.Unlock()
		return ErrRoomFull
	}
	if _, exists := r.clients[friendCode]; exists {
		r.mu.Unlock()
		return errors.New("already joined")
	}
	cl := &client{friendCode: friendCode, conn: conn, send: make(chan []byte, 16)}
	r.clients[friendCode] = cl
	peers := make([]*client, 0, len(r.clients))
	for code, c := range r.clients {
		if code != friendCode {
			peers = append(peers, c)
		}
	}
	r.mu.Unlock()

	go cl.writePump()
	go m.readPump(r, cl, peers)
	return nil
}

func (m *Manager) readPump(r *liveRoom, cl *client, initialPeers []*client) {
	defer func() {
		r.mu.Lock()
		delete(r.clients, cl.friendCode)
		r.mu.Unlock()
		_ = cl.conn.Close()
	}()

	cl.conn.SetReadLimit(maxMessageSize)
	for {
		_, msg, err := cl.conn.ReadMessage()
		if err != nil {
			return
		}
		r.mu.Lock()
		for code, peer := range r.clients {
			if code == cl.friendCode {
				continue
			}
			select {
			case peer.send <- msg:
			default:
			}
		}
		r.mu.Unlock()
	}
}

func (cl *client) writePump() {
	for msg := range cl.send {
		if err := cl.conn.WriteMessage(websocket.TextMessage, msg); err != nil {
			return
		}
	}
}

func (m *Manager) Touch(roomID string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	r, ok := m.rooms[roomID]
	if !ok {
		return
	}
	r.expiresAt = time.Now().Add(m.roomTTL)
}
