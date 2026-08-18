# 8. Design WhatsApp/Slack Chat Messaging

**Key Terms:**

| Term | Full Name | What it does |
|------|-----------|--------------|
| **WebSocket** | Full-duplex protocol | Persistent connection for real-time bidirectional messaging |
| **ACK** | Acknowledgment | Confirmation that message was received |
| **Fanout** | Message distribution | Sending one message to many recipients |
| **TIMEUUID** | Time-based UUID | UUID that's sortable by creation time |
| **APNs** | Apple Push Notification service | Delivers notifications to iOS devices |
| **FCM** | Firebase Cloud Messaging | Delivers notifications to Android/iOS |
| **E2EE** | End-to-End Encryption | Only sender and recipient can read messages |

## Problem Statement
Design a real-time messaging system supporting billions of messages daily with delivery guarantees, read receipts, and offline support.

## Clarifying Questions to Ask

1. **Scale**: How many users? Messages per day? (2B users, 100B messages?)
2. **Message types**: Text only or media too?
3. **Group size**: Max participants per group? (256? 1000?)
4. **Delivery guarantees**: At-least-once or exactly-once?
5. **Message retention**: How long to store messages?
6. **End-to-end encryption**: Required?
7. **Features**: Read receipts, typing indicators, presence?

## Design Reasoning

> **Core philosophy**: Deliver messages in real-time with at-least-once guarantees using persistent WebSocket connections, while optimizing for the write-heavy nature of chat (100B messages/day) with a storage engine designed for sequential writes and time-ordered reads.

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Client protocol | WebSocket (persistent) | Full-duplex connection enables instant push delivery and typing indicators without polling overhead | HTTP long-polling — higher latency, more server connections, wastes bandwidth at 2B users |
| Message queue | Kafka (partitioned by chat_id) | Guarantees message ordering within each chat; durable replay for crash recovery | Redis Pub/Sub — no persistence, messages lost if subscriber is offline |
| Message store | Cassandra | Optimized for write-heavy workloads; time-series clustering by TIMEUUID gives ordered reads for free | PostgreSQL — write throughput bottleneck at 100B messages/day, sharding is complex |
| Session/presence store | Redis | Sub-ms lookups for online status and WebSocket connection routing across gateway servers | Database polling — too slow for real-time presence with millions of concurrent users |
| Group delivery | Fanout-on-write (small groups) | Pre-writes to each member's inbox for fast reads; acceptable write amplification for groups under 256 | Fanout-on-read — slower reads requiring joins across group membership, poor UX for active chats |
| Offline delivery | APNs/FCM push notifications | Platform-native push reaches offline devices without maintaining idle WebSocket connections | SMS fallback — expensive at scale, not suitable for rich message content |
| Message ordering | TIMEUUID | Globally unique and time-sortable; no coordination needed across distributed writers | Sequence counters — require centralized counter service, single point of failure |

## High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                    CHAT SYSTEM ARCHITECTURE                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────┐                               ┌─────────────┐
│   SENDER    │                               │  RECEIVER   │
│   (Alice)   │                               │   (Bob)     │
└──────┬──────┘                               └──────▲──────┘
       │                                             │
       │ 1. Send Message                  5. Push Message
       │                                             │
       ▼                                             │
┌─────────────────────────────────────────────────────────────────┐
│                    WEBSOCKET GATEWAY                             │
│              (Maintains persistent connections)                  │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          │ 2. Publish to queue
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MESSAGE QUEUE (Kafka)                         │
│                    Partitioned by chat_id                        │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          │ 3. Process
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    CHAT SERVICE                                  │
│  - Validate message                                              │
│  - Store in database                                             │
│  - Fanout to recipients                                          │
└─────────────────────────┬───────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │ Message  │    │ Session  │    │   Push   │
    │    DB    │    │  Store   │    │ Service  │
    │(Cassandra)│    │ (Redis)  │    │          │
    └──────────┘    └──────────┘    └──────────┘
                          │               │
                          │               │ 4. If offline
                          │               ▼
                          │         ┌──────────┐
                          │         │   APNs/  │
                          │         │   FCM    │
                          └─────────┴──────────┘
```


## API Design

### Message APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/messages` | Send a message |
| `GET` | `/api/v1/messages?chatId={id}&limit=50&before={cursor}` | Fetch message history |
| `PUT` | `/api/v1/messages/{messageId}` | Edit a message |
| `DELETE` | `/api/v1/messages/{messageId}` | Delete a message |
| `POST` | `/api/v1/messages/{messageId}/read` | Mark message as read |

### Chat/Group APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/chats` | Create a 1:1 or group chat |
| `GET` | `/api/v1/chats` | List user's chats |
| `POST` | `/api/v1/chats/{chatId}/members` | Add member to group |
| `DELETE` | `/api/v1/chats/{chatId}/members/{userId}` | Remove member |

### WebSocket Events
| Event | Direction | Payload |
|-------|-----------|---------|
| `message.new` | Server → Client | `{ chatId, messageId, content, sender, timestamp }` |
| `message.delivered` | Server → Client | `{ messageId, userId, timestamp }` |
| `message.read` | Server → Client | `{ messageId, userId, timestamp }` |
| `user.typing` | Client → Server | `{ chatId, isTyping }` |
| `user.online` | Server → Client | `{ userId, lastSeen }` |

### Send Message Request
```json
POST /api/v1/messages
{
  "chat_id": "chat_123",
  "content": "Hello!",
  "type": "text",
  "reply_to": "msg_456",       // optional
  "client_msg_id": "uuid-789"  // for idempotency
}
```


## Key Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| WebSocket Gateway | Persistent connections | Custom / SignalR |
| Session Store | Track user connections | Redis |
| Message Queue | Decouple and order | Kafka |
| Chat Service | Business logic | Microservice |
| Message Store | Persist messages | Cassandra |
| Push Service | Offline notifications | APNs, FCM |
| Media Service | Handle attachments | Blob Storage + CDN |

## Message Flow

```
1:1 CHAT:
  Alice → Server → Bob (direct delivery if online)
                 → Store for later (if offline)

GROUP CHAT (Fanout-on-write):
  Alice sends to Group(Alice, Bob, Charlie)
  Server writes to:
    - Bob's inbox
    - Charlie's inbox
  Pros: Fast reads
  Cons: Storage cost, write amplification

GROUP CHAT (Fanout-on-read):
  Alice sends to Group
  Server stores once in Group's messages
  Readers pull from Group
  Pros: Less storage
  Cons: Slower reads, complex queries
```

## Message Delivery States

```
┌────────────────────────────────────────────────────────┐
│  SENT (✓)      → Server received the message           │
│  DELIVERED (✓✓) → Recipient's device received         │
│  READ (✓✓ blue) → Recipient opened the chat           │
└────────────────────────────────────────────────────────┘

Implementation:
  1. Client sends message → Server ACKs → Show single tick
  2. Server delivers to recipient → Recipient ACKs → Double tick
  3. Recipient opens chat → Send read receipt → Blue ticks
```

## Database Schema

```sql
-- Messages (Cassandra - partitioned by chat_id)
CREATE TABLE messages (
    chat_id UUID,
    message_id TIMEUUID,  -- Sortable by time
    sender_id UUID,
    content TEXT,
    content_type VARCHAR,  -- text, image, video
    created_at TIMESTAMP,
    PRIMARY KEY (chat_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

-- User Inbox (for quick access to recent chats)
CREATE TABLE user_inbox (
    user_id UUID,
    chat_id UUID,
    last_message_id TIMEUUID,
    last_message_preview TEXT,
    unread_count INT,
    updated_at TIMESTAMP,
    PRIMARY KEY (user_id, updated_at)
) WITH CLUSTERING ORDER BY (updated_at DESC);
```

## Scalability Considerations

- **Connection management**: Each gateway server handles ~500K connections
- **Partitioning**: Kafka partitioned by chat_id for ordering
- **Presence**: Use heartbeats + Redis pub/sub for online status
- **Group fanout**: Limit group size, use async fanout for large groups
- **Message ordering**: TIMEUUID ensures global ordering within a chat

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| Protocol | HTTP polling | WebSocket | **WebSocket** - real-time, efficient |
| Group fanout | On write | On read | **On write** - for small groups |
| Message store | SQL | Cassandra | **Cassandra** - write-heavy scale |
| Ordering | Timestamps | Sequence IDs | **TIMEUUID** - sortable + unique |

## Technologies Deep Dive

📚 **Learn more about the key technologies used in this design:**

| Technology | Role in This Design | Reference |
|------------|---------------------|-----------|
| Kafka | Message ordering and delivery guarantee | [Tech Guide](technologies/kafka.md) |
| Redis | Online presence and connection state | [Tech Guide](technologies/redis.md) |
| Cassandra | Write-optimized message storage | [Tech Guide](technologies/cassandra.md) |

---

## How to Explain It Well

> **Opening**: "A chat system needs real-time delivery with offline support. I'll design for 2 billion users and 100 billion messages daily."

> **Key talking points**:
> - "WebSocket gateways maintain persistent connections - one per user"
> - "Kafka ensures message ordering per chat via partitioning"
> - "Fanout-on-write for groups under 256 users, on-read for larger"
> - "Cassandra for messages - optimized for write-heavy, time-series data"
> - "Three delivery states: sent, delivered, read - each requires an ACK"

---