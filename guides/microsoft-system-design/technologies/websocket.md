# WebSocket

## What It Is
WebSocket is a communication protocol that provides full-duplex (bidirectional) communication channels over a single TCP connection. Unlike HTTP request-response, WebSocket keeps the connection open for real-time data exchange.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HTTP vs WEBSOCKET                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

HTTP (Request-Response):
┌────────┐                              ┌────────┐
│ Client │ ──── Request ───────────────▶│ Server │
│        │ ◀─── Response ───────────────│        │
│        │ ──── Request ───────────────▶│        │
│        │ ◀─── Response ───────────────│        │
│        │     (new connection each)    │        │
└────────┘                              └────────┘

WebSocket (Full Duplex):
┌────────┐                              ┌────────┐
│ Client │ ══════════════════════════════│ Server │
│        │ ◀───── messages ─────────────▶│        │
│        │ ◀───── messages ─────────────▶│        │
│        │    (single connection,        │        │
│        │     both can send anytime)    │        │
└────────┘                              └────────┘
```

## Connection Handshake

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WEBSOCKET HANDSHAKE (Upgrade from HTTP)                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. CLIENT REQUEST (HTTP Upgrade):
   GET /chat HTTP/1.1
   Host: server.example.com
   Upgrade: websocket
   Connection: Upgrade
   Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
   Sec-WebSocket-Version: 13

2. SERVER RESPONSE (101 Switching Protocols):
   HTTP/1.1 101 Switching Protocols
   Upgrade: websocket
   Connection: Upgrade
   Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

3. CONNECTION ESTABLISHED
   → Now speaking WebSocket protocol (binary frames)
```

## Frame Structure

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WEBSOCKET FRAME                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+---------------------------------------------------------------+

OPCODES:
  0x0: Continuation frame
  0x1: Text frame (UTF-8)
  0x2: Binary frame
  0x8: Close
  0x9: Ping
  0xA: Pong
```

## Client Implementation (JavaScript)

```javascript
// Connect
const ws = new WebSocket('wss://server.example.com/chat');

// Connection opened
ws.onopen = (event) => {
    console.log('Connected');
    ws.send(JSON.stringify({ type: 'join', room: 'general' }));
};

// Receive message
ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    console.log('Received:', data);
};

// Handle errors
ws.onerror = (error) => {
    console.error('WebSocket error:', error);
};

// Connection closed
ws.onclose = (event) => {
    console.log('Disconnected:', event.code, event.reason);
    // Implement reconnection logic
    setTimeout(connect, 1000);
};

// Send message
function sendMessage(text) {
    if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'message', text }));
    }
}
```

## Server Implementation (Node.js)

```javascript
const WebSocket = require('ws');

const wss = new WebSocket.Server({ port: 8080 });

// Track connected clients
const clients = new Map();

wss.on('connection', (ws, req) => {
    const userId = req.headers['x-user-id'];
    clients.set(userId, ws);
    
    ws.on('message', (data) => {
        const message = JSON.parse(data);
        
        if (message.type === 'broadcast') {
            // Send to all clients
            clients.forEach((client) => {
                if (client.readyState === WebSocket.OPEN) {
                    client.send(JSON.stringify(message));
                }
            });
        }
    });
    
    ws.on('close', () => {
        clients.delete(userId);
    });
    
    // Heartbeat to detect dead connections
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });
});

// Ping clients every 30 seconds
setInterval(() => {
    wss.clients.forEach((ws) => {
        if (!ws.isAlive) return ws.terminate();
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);
```

## Scaling WebSocket Servers

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SCALING WEBSOCKET                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

PROBLEM: User A connected to Server 1, User B connected to Server 2
         How does A send message to B?

SOLUTION: Pub/Sub backplane (Redis, Kafka)

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│   User A ──▶ Server 1 ──┐                                                               │
│                         │                                                                │
│                         ▼                                                                │
│                   ┌──────────┐                                                          │
│                   │  Redis   │  PUBLISH channel:room123 "Hello"                         │
│                   │  Pub/Sub │                                                          │
│                   └──────────┘                                                          │
│                         │                                                                │
│                         │ SUBSCRIBE channel:room123                                     │
│                         ▼                                                                │
│   User B ◀── Server 2 ◀─┘                                                               │
│                                                                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

STICKY SESSIONS (Alternative):
  Load balancer routes user to same server based on session ID
  Simpler but less fault-tolerant (server failure = lost connections)
```

## Connection Management

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CONNECTION LIFECYCLE                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. HEARTBEAT (Ping/Pong)
   ┌────────┐                              ┌────────┐
   │ Client │ ──── Ping (every 30s) ──────▶│ Server │
   │        │ ◀─── Pong ───────────────────│        │
   └────────┘                              └────────┘
   
   Purpose: Detect dead connections (network dropped, client crashed)

2. RECONNECTION
   ┌─────────────────────────────────────────────────────────────────────────────────────┐
   │  function connect() {                                                                │
   │      const ws = new WebSocket(url);                                                 │
   │      ws.onclose = () => {                                                           │
   │          // Exponential backoff: 1s, 2s, 4s, 8s, max 30s                           │
   │          const delay = Math.min(30000, 1000 * Math.pow(2, retryCount));            │
   │          setTimeout(connect, delay);                                                │
   │      };                                                                             │
   │  }                                                                                  │
   └─────────────────────────────────────────────────────────────────────────────────────┘

3. GRACEFUL SHUTDOWN
   Server: Send close frame with code 1001 (going away)
   Client: Reconnect to different server
```

## WebSocket vs Alternatives

| Feature | WebSocket | SSE | Long Polling | HTTP/2 Push |
|---------|-----------|-----|--------------|-------------|
| Direction | Bidirectional | Server→Client | Request-Response | Server→Client |
| Connection | Persistent | Persistent | New each time | Persistent |
| Binary data | Yes | No (text only) | Yes | Yes |
| Browser support | All modern | All modern | All | All modern |
| Complexity | Medium | Low | Low | Low |
| Use case | Chat, games | Notifications | Fallback | Assets |

## When to Use

✅ **Good for:**
- Real-time chat/messaging
- Live notifications
- Multiplayer games
- Collaborative editing
- Live dashboards/charts
- Financial tickers
- IoT device communication

❌ **Not ideal for:**
- One-way server updates (use SSE)
- Infrequent updates (use polling)
- Request-response API (use HTTP)
- Unreliable networks without offline support

## Trade-offs

| Pros | Cons |
|------|------|
| Low latency | Stateful (harder to scale) |
| Bidirectional | Needs sticky sessions or pub/sub |
| Efficient (no HTTP overhead) | Connection management complexity |
| Real-time | Firewall/proxy issues possible |
| Binary support | More server resources per connection |

## Azure SignalR Service

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Azure SignalR = Managed WebSocket service                                              │
│                                                                                          │
│  Benefits:                                                                              │
│    • Handles 100K+ connections per unit                                                │
│    • Auto-scaling                                                                       │
│    • Built-in pub/sub (no Redis needed)                                                │
│    • Fallback to SSE/long-polling                                                      │
│                                                                                          │
│  Your server only handles business logic, SignalR handles connections                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## Used In (This Guide)

- **Teams (Q1)**: Real-time messaging, presence
- **Chat Messaging (Q8)**: Message delivery
- **Collaborative Editing (Q10)**: Cursor sync, changes
- **Notification Service (Q11)**: Push notifications
- **Ticket Booking (Q42)**: Queue position updates
