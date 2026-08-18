# Chat & Messaging

[← Back to Overview](./README.md) | [Video →](./video.md) | [Technologies](./technologies.md)

> 📚 **Key technologies:** [SignalR](../technologies/signalr.md) for real-time delivery, [WebSocket](../technologies/websocket.md) for persistent connections.

---
### 1. Chat Message Flow

**Two Paths: Write (Send) vs Read (Sync)**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CHAT ARCHITECTURE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                              ┌─────────────────┐                            │
│                              │  Azure Front    │                            │
│                              │     Door        │                            │
│                              └────────┬────────┘                            │
│                                       │                                      │
│          ┌────────────────────────────┼────────────────────────────┐        │
│          ▼                            ▼                            ▼         │
│  ┌───────────────┐        ┌─────────────────────┐      ┌─────────────────┐  │
│  │ AUTH SERVICE  │        │   CHAT SERVICE      │      │  SYNC SERVICE   │  │
│  │ (Azure AD)    │        │   (Write Path)      │      │  (Read Path)    │  │
│  │               │◀───────│                     │      │                 │  │
│  │ • JWT valid   │ 2.Auth │ POST /messages      │      │ GET /messages   │  │
│  │ • Permissions │        │ • Validate          │      │ • Query Cosmos  │  │
│  └───────────────┘        │ • Write Cosmos DB   │      │ • Paginate      │  │
│                           │ • Publish Event Hubs│      │                 │  │
│                           └──────────┬──────────┘      └────────┬────────┘  │
│                                      │                          │            │
│                                      ▼                          ▼            │
│                    ┌───────────────────────────────────────────────────┐    │
│                    │                    COSMOS DB                       │    │
│                    │                 (Messages Store)                   │    │
│                    └───────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Write Path: Sending a Message (Real-time)**

```
Client               Auth             Chat Service            Cosmos DB        Event Hubs
  │                   │                    │                      │                │
  │── 1. Send ───────▶│                    │                      │                │
  │   (JWT + msg)     │                    │                      │                │
  │                   │── 2. Validate ────▶│                      │                │
  │                   │◀── 2b. OK ─────────│                      │                │
  │                   │                    │                      │                │
  │                   │                    │── 3. Persist ───────▶│                │
  │                   │                    │◀── 3b. Ack ──────────│                │
  │                   │                    │                      │                │
  │                   │                    │── 4. Publish ────────│───────────────▶│
  │                   │                    │                      │                │
  │◀── 5. Ack ────────│────────────────────│                      │                │
  │   (sent ✓)        │                    │                      │                │
```

| Step | From | To | Action | Sync/Async |
|------|------|-----|--------|------------|
| 1 | Client | Front Door | Send message with JWT | SYNC |
| 2 | Chat Service | Auth Service | Validate JWT, check channel permissions | SYNC |
| 3 | Chat Service | Cosmos DB | Persist message | SYNC |
| 4 | Chat Service | Event Hubs | Publish for fanout | ASYNC |
| 5 | Chat Service | Client | Acknowledge message sent | SYNC |

**Message Delivery (ASYNC - after ack):**

```
Event Hubs          Notification Worker         Redis              SignalR           User B
     │                      │                     │                   │                 │
     │── 6. Consume ───────▶│                     │                   │                 │
     │   (new message)      │                     │                   │                 │
     │                      │── 7. Check ────────▶│                   │                 │
     │                      │    presence         │                   │                 │
     │                      │◀── 7b. Online ──────│                   │                 │
     │                      │                     │                   │                 │
     │                      │── 8. Push ──────────│──────────────────▶│                 │
     │                      │                     │                   │── 9. Deliver ──▶│
```

| Step | From | To | Action | Sync/Async |
|------|------|-----|--------|------------|
| 6 | Event Hubs | Notification Worker | Consume message event | ASYNC |
| 7 | Worker | Redis | Check recipient presence | SYNC |
| 8 | Worker | SignalR | Push if online | ASYNC |
| 9 | SignalR | Client | WebSocket delivery | REAL-TIME |

> **If offline**: No action needed. Message is in Cosmos DB; user syncs on reconnect.

**Design Decisions Explained:**

| Decision | Why This Choice | Alternative Considered |
|----------|-----------------|----------------------|
| Event Hubs for fanout | High throughput (millions/sec), partitioned by channelId, Kafka-compatible | Service Bus - better for task queues but lower throughput |
| Cosmos DB first, then queue | Durability - message is safe even if fanout fails; can replay from DB | Queue first risks message loss if DB write fails |
| Azure Functions as worker | Serverless, auto-scale with Event Hubs partitions, cost-efficient | AKS pods - more control but manual scaling |
| Redis TTL for presence | Auto-cleanup on crash/disconnect; no explicit "offline" event needed | Explicit disconnect - unreliable |

> 💡 **Enhancement:** For mobile push alerts when app is closed, see [Push Notifications](./notifications.md).

---

## Offline → Online: Message Sync (Sync Service)

When a user reconnects after being offline, they need to fetch missed messages. This is handled by a **separate Sync Service**, not the Chat Service.

**Why Separate Services? (CQRS Pattern)**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CQRS: COMMAND QUERY RESPONSIBILITY SEGREGATION            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CHAT SERVICE (Write/Command)          SYNC SERVICE (Read/Query)            │
│  ┌─────────────────────────────┐      ┌─────────────────────────────┐       │
│  │ POST /api/messages          │      │ GET /api/messages?since=... │       │
│  │                             │      │ GET /api/messages/search    │       │
│  │ • Validate message          │      │                             │       │
│  │ • Write to Cosmos DB        │      │ • Query Cosmos DB           │       │
│  │ • Publish to Event Hubs     │      │ • Paginate large results    │       │
│  │ • Return ack to sender      │      │ • Return to client          │       │
│  └─────────────────────────────┘      └─────────────────────────────┘       │
│              │                                    │                          │
│              │                                    │                          │
│              ▼                                    ▼                          │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                           COSMOS DB                                  │    │
│  │                      (shared data store)                             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

| Aspect | Chat Service (Write) | Sync Service (Read) |
|--------|---------------------|---------------------|
| **Pattern** | Async, fire-and-forget | Sync, request-response |
| **Operation** | Write + publish event | Query + return data |
| **Payload** | Small (one message) | Large (hundreds of messages) |
| **Latency** | <100ms ack required | Seconds acceptable |
| **Scaling trigger** | Messages/second | Concurrent reconnects + data size |
| **Resource needs** | CPU (validation, publish) | Memory + IO (large queries) |
| **Failure impact** | Message not sent (critical) | User retries (less critical) |

**Why NOT combine them?**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PROBLEM: Combined service during "thundering herd"                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Scenario: Network outage restored, 10,000 users reconnect simultaneously   │
│                                                                              │
│  If combined:                                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  10,000 sync requests (heavy queries, large responses)              │    │
│  │  + normal message send traffic                                       │    │
│  │  = Service overwhelmed                                               │    │
│  │  = NEW messages can't be sent (write path blocked by read load)     │    │
│  │  = Users can't chat during recovery ❌                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  If separated:                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Chat Service: Handles writes normally (unaffected)                  │    │
│  │  Sync Service: Scales up to handle reconnect storm                   │    │
│  │  = Users can still send messages while others sync ✓                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Message Sync Flow:**

```
USER RECONNECTS AFTER OFFLINE:

┌──────────┐                                              ┌─────────────────┐
│  Client  │                                              │  Sync Service   │
│  (User B)│                                              │     (AKS)       │
└────┬─────┘                                              └────────┬────────┘
     │                                                             │
     │  STEP 1: Get user's chats from METADATA (Azure SQL)         │
     │                                                             │
     │ 1. GET /api/users/{userId}/chats                            │
     │ ───────────────────────────────────────────────────────────►│
     │                                                             │
     │                                         2. Query Azure SQL: │
     │                                            SELECT channelId, name, type
     │                                            FROM UserChannels
     │                                            WHERE userId = {userId}
     │                                                             │
     │ 3. Return { chats: [{id, name, type, lastActivity}, ...] }  │
     │ ◄───────────────────────────────────────────────────────────│
     │                                                             │
     │  STEP 2: Get messages for each chat from COSMOS DB          │
     │                                                             │
     │ 4. GET /api/channels/{id}/messages?since=2026-01-26T10:00   │
     │ ───────────────────────────────────────────────────────────►│
     │                                                             │
     │                                         5. Query Cosmos DB: │
     │                                            WHERE channelId = {id}
     │                                            AND timestamp > since
     │                                            ORDER BY timestamp
     │                                            LIMIT 50
     │                                                             │
     │ 6. Return { messages: [...], nextCursor: "..." }            │
     │ ◄───────────────────────────────────────────────────────────│
     │                                                             │
     │ 7. Repeat step 4-6 for each chat (parallel or prioritized)  │
     │                                                             │
     │ 8. After sync complete, connect to SignalR for real-time    │
     │ ══════════════════════════════════════════════════════════►│ SignalR
     │                                                             │
```

**Why Two Databases?**

| Data | Database | Reason |
|------|----------|--------|
| User's chat list | Azure SQL | Relational (user ↔ channel membership), rarely changes |
| Chat messages | Cosmos DB | High volume writes, partitioned by channelId for fast reads |

**Why Pagination (not WebSocket Streaming)?**

| Approach | Pros | Cons |
|----------|------|------|
| **HTTP Pagination** ✓ | Simple, stateless, cacheable, retry on failure | Multiple round-trips |
| **WebSocket Streaming** | Single connection, progressive loading | Complex error handling, can't cache, connection state |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PAGINATION vs STREAMING TRADE-OFF                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  OPTION A: HTTP Pagination (Recommended for sync)                            │
│  ────────────────────────────────────────────────                            │
│  GET /messages?since=T1&limit=50 → [msg1..msg50], cursor="abc"              │
│  GET /messages?cursor=abc        → [msg51..msg100], cursor="def"            │
│  GET /messages?cursor=def        → [msg101..msg120], cursor=null (done)     │
│                                                                              │
│  ✅ Each request is independent (retry just that page on failure)            │
│  ✅ Can parallelize across channels                                          │
│  ✅ Response can be cached (CDN, client)                                     │
│  ✅ Load balancer can distribute to any Sync Service pod                     │
│  ✅ Progress visible (50/150 messages loaded)                                │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  OPTION B: WebSocket Streaming                                               │
│  ─────────────────────────────────                                           │
│  CONNECT ws://sync/messages?since=T1                                         │
│  ← msg1, msg2, msg3... (streaming)                                           │
│  ← END                                                                       │
│                                                                              │
│  ✅ Single connection (lower overhead if many messages)                      │
│  ✅ Progressive rendering (show messages as they arrive)                     │
│  ❌ If connection drops mid-stream, must restart from beginning              │
│  ❌ Can't cache partial results                                              │
│  ❌ Sticky session needed (same server must handle entire stream)            │
│  ❌ Harder to parallelize (one stream per channel = many connections)        │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  RECOMMENDATION:                                                             │
│  • Use HTTP Pagination for SYNC (historical messages)                        │
│  • Use WebSocket (SignalR) for REAL-TIME (new messages after sync)           │
│                                                                              │
│  This is what Teams/Slack/Discord actually do:                               │
│  1. App opens → HTTP calls to fetch recent messages                          │
│  2. Once loaded → WebSocket for live updates                                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Cursor Design: Why Timestamp Alone is Risky**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CURSOR DESIGN: TIMESTAMP vs COMPOSITE                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  PROBLEM: Multiple messages can have the SAME timestamp                      │
│  ─────────────────────────────────────────────────────                       │
│  Example: Busy channel, 3 messages arrive at same millisecond:               │
│                                                                              │
│    { id: "msg-A", timestamp: 1706295600000, text: "Hello" }                 │
│    { id: "msg-B", timestamp: 1706295600000, text: "Hi" }                    │
│    { id: "msg-C", timestamp: 1706295600000, text: "Hey" }                   │
│                                                                              │
│  ❌ BAD: Query with timestamp only                                           │
│     WHERE timestamp > 1706295600000                                          │
│     → Misses msg-B and msg-C! (same timestamp as last seen)                  │
│                                                                              │
│  ❌ BAD: Query with timestamp >= (try to include same timestamp)             │
│     WHERE timestamp >= 1706295600000                                         │
│     → Returns msg-A again (duplicate!)                                       │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SOLUTION: Composite cursor (timestamp + messageId)                          │
│  ──────────────────────────────────────────────────                          │
│                                                                              │
│  Cursor format: "1706295600000:msg-A" (timestamp:lastMessageId)              │
│                                                                              │
│  ✅ GOOD: Query with composite key                                           │
│     WHERE (timestamp > 1706295600000)                                        │
│        OR (timestamp = 1706295600000 AND id > 'msg-A')                       │
│     ORDER BY timestamp ASC, id ASC                                           │
│                                                                              │
│     → Returns msg-B, msg-C (correct! no duplicates, no misses)               │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  COSMOS DB IMPLEMENTATION:                                                   │
│  ─────────────────────────                                                   │
│  Index: Composite index on (channelId, timestamp, id)                        │
│                                                                              │
│  Query:                                                                      │
│  SELECT * FROM messages m                                                    │
│  WHERE m.channelId = @channelId                                              │
│    AND (m.timestamp > @lastTimestamp                                         │
│         OR (m.timestamp = @lastTimestamp AND m.id > @lastMessageId))         │
│  ORDER BY m.timestamp ASC, m.id ASC                                          │
│  OFFSET 0 LIMIT 50                                                           │
│                                                                              │
│  Response includes next cursor:                                              │
│  {                                                                           │
│    "messages": [...],                                                        │
│    "nextCursor": "1706295700000:msg-Z"  // timestamp:lastId of this page    │
│  }                                                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Sync Strategy:**
1. Client stores `lastCursor` (timestamp:messageId) per channel locally
2. On reconnect, first fetch chat list from Azure SQL (user's channels)
3. For each channel, call Sync Service with `lastCursor` (HTTP)
4. Sync Service queries Cosmos DB with composite key comparison
5. Return in batches (50 msgs) with next cursor for pagination
6. Prioritize: Active chats first, then older chats on scroll
7. After sync complete, subscribe to SignalR for real-time updates (WebSocket)

**Edge Cases:**
- Long offline (days): Paginate, fetch on scroll (don't load all at once)
- Conflicting edits: Server timestamp wins (last-write-wins)
- Deleted messages: Soft-delete flag, client removes from UI
- Multiple devices: Each device tracks own `lastCursor` per channel
- Thundering herd: Sync Service auto-scales independently of Chat Service
- New chats while offline: Appear in chat list from SQL, messages fetched separately
- Same-millisecond messages: Composite cursor ensures no duplicates/misses
---

## Related Pages

- [Video & Calls](./video.md) - WebRTC, SFU, recording
- [File Sharing](./files.md) - Upload flow, CDN
- [Databases](./databases.md) - Cosmos DB, Redis configurations
- [Interview Tips](./interview-tips.md) - Trade-offs, how to explain

[← Back to Overview](./README.md)
