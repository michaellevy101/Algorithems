# Database Configurations

[← Back to Overview](./README.md) | [Files](./files.md) | [Interview Tips →](./interview-tips.md) | [Technologies](./technologies.md)

---
## Database Configurations

### 1. Azure Cosmos DB (Messages & Threads)

| Setting | Value |
|---------|-------|
| **Container** | `messages` |
| **Partition Key** | `/channelId` |
| **Consistency** | Session (user sees own writes) |
| **Throughput** | Autoscale 400 - 100,000 RU/s |
| **TTL** | 7 years (compliance) or per-tenant |
| **Multi-region** | Enabled (US East, EU West, Asia East) |

**Document Structure:**
```json
{
  "id": "msg_uuid",
  "channelId": "channel_123",      // Partition Key
  "senderId": "user_456",
  "content": "Hello team!",
  "timestamp": "2026-01-26T17:00:00Z",
  "threadId": "thread_789",        // For replies
  "type": "text",
  "reactions": [],
  "mentions": ["user_111", "user_222"]
}
```

**Why `/channelId` as partition key?**
- All messages in a channel queried together
- Even distribution across partitions
- Efficient range queries by timestamp within channel

**Partition Key Strategy Deep Dive:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PARTITION KEY CONSIDERATIONS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  OPTION 1: /channelId (CHOSEN ✓)                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Partition "channel_123"    │ Partition "channel_456"               │    │
│  │ ┌─────────────────────┐    │ ┌─────────────────────┐               │    │
│  │ │ msg1: "Hello"       │    │ │ msg5: "Hi team"     │               │    │
│  │ │ msg2: "Hi"          │    │ │ msg6: "Update"      │               │    │
│  │ │ msg3: "Meeting?"    │    │ │ msg7: "Done"        │               │    │
│  │ │ msg4: "Sure, 3pm"   │    │ └─────────────────────┘               │    │
│  │ └─────────────────────┘    │                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ✓ Query "get messages in channel" hits ONE partition                       │
│  ✓ Messages in same channel are co-located (fast range queries)            │
│  ✓ Good distribution if channels are active                                 │
│  ⚠ Hot partition risk: Very active channel (e.g., company-wide)            │
│                                                                              │
│  OPTION 2: /channelId + /timeBucket (for very high-volume channels)        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Partition Key = channelId + "_" + YYYYMMDD                          │    │
│  │ "channel_123_20260126" │ "channel_123_20260127"                     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ✓ Spreads load for high-volume channels                                   │
│  ✗ Query for "last 100 messages" may cross partitions                      │
│  Use only if: Single channel > 50GB or > 10K RU/s                          │
│                                                                              │
│  OPTION 3: /userId (NOT RECOMMENDED)                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ "user_456" has all messages SENT by that user                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│  ✗ "Get messages in channel" requires CROSS-PARTITION query (slow!)        │
│  ✗ Only good for "my sent messages" - rare query pattern                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Consistency Level Deep Dive:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    WHY SESSION CONSISTENCY?                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SCENARIO: User sends message, then refreshes page                          │
│                                                                              │
│  STRONG CONSISTENCY:                                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ User writes to US East → Wait for EU West + Asia to confirm         │    │
│  │ Latency: 200-300ms (cross-region round trip)                        │    │
│  │ ✗ Too slow for chat!                                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  EVENTUAL CONSISTENCY:                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ User writes to US East → Returns immediately                        │    │
│  │ User refreshes → Might read from stale replica                      │    │
│  │ ✗ User doesn't see their own message! Confusing UX                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  SESSION CONSISTENCY (CHOSEN ✓):                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ User writes to US East → Returns immediately                        │    │
│  │ Same user refreshes → Guaranteed to see own write (session token)   │    │
│  │ Other users → May see slight delay (eventual)                       │    │
│  │ ✓ Fast writes + user sees own messages + acceptable for chat        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  HOW IT WORKS:                                                               │
│  • Client receives session token with each response                         │
│  • Client sends token with next request                                     │
│  • Cosmos DB ensures read includes all writes up to that token             │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Why Cosmos DB for messages?**
- Write-heavy workload (millions of messages/min)
- Flexible schema (text, files, reactions, edits)
- Multi-region writes for global low latency
- Session consistency = user sees own writes immediately

---

### 2. Azure SQL Database (Users, Teams, Channels)

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `Users` | User profiles | `userId (PK)`, `tenantId`, `displayName`, `email` |
| `Teams` | Team definitions | `teamId (PK)`, `tenantId`, `name`, `createdAt` |
| `Channels` | Channels in teams | `channelId (PK)`, `teamId (FK)`, `name`, `type` |
| `TeamMembers` | Team membership | `teamId`, `userId`, `role`, `joinedAt` |
| `ChannelMembers` | Channel access | `channelId`, `userId`, `role` |
| `Files` | File metadata | `fileId (PK)`, `channelId`, `blobUrl`, `size`, `uploadedBy` |

**Indexes:**
- `tenantId` on all tables (tenant isolation)
- `teamId` on Channels, TeamMembers
- `channelId` on Files, ChannelMembers

**Why SQL for metadata?**
- Relational data with joins (user → teams → channels)
- ACID transactions (adding user to team = atomic)
- Complex queries (find all teams user belongs to)
- Read-heavy, infrequent writes

---

### 3. Azure Cache for Redis (Presence, Sessions, Cache)

| Key Pattern | Value | TTL |
|-------------|-------|-----|
| `presence:{userId}` | `{status, lastSeen, device}` | 5 min |
| `session:{connectionId}` | `{userId, deviceType, region}` | 30 min |
| `typing:{channelId}` | `Set of userIds` | 10 sec |
| `unread:{userId}:{channelId}` | `count` | None |
| `user:{userId}` | `{profile JSON}` | 1 hour |

**Configuration:**
- Cluster: 3 shards with replicas for HA
- Eviction: `allkeys-lru` for cache keys
- Pub/Sub: Used for presence broadcast across instances

**Redis Data Structures Used:**

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    REDIS DATA STRUCTURES IN TEAMS                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. STRING with TTL (Presence)                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ SET presence:user_123 '{"status":"online","device":"desktop"}'      │    │
│  │     EX 300                                                           │    │
│  │                                                                      │    │
│  │ • TTL auto-expires if client doesn't heartbeat                      │    │
│  │ • No explicit "offline" event needed (handles crashes gracefully)   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  2. SORTED SET (Typing Indicators)                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ ZADD typing:channel_123 <timestamp> user_456                        │    │
│  │ ZRANGEBYSCORE typing:channel_123 <now-10sec> +inf                   │    │
│  │                                                                      │    │
│  │ • Score = timestamp when user started typing                        │    │
│  │ • Query: "who typed in last 10 seconds"                             │    │
│  │ • Auto-cleanup: ZREMRANGEBYSCORE removes old entries               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  3. HASH (Unread Counts)                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ HINCRBY unread:user_123 channel_456 1   # Increment unread         │    │
│  │ HSET unread:user_123 channel_456 0      # Mark as read             │    │
│  │ HGETALL unread:user_123                 # Get all unread counts    │    │
│  │                                                                      │    │
│  │ • Atomic increments                                                 │    │
│  │ • Single round-trip for all channels                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  4. PUB/SUB (Presence Broadcast)                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ PUBLISH presence_updates '{"userId":"123","status":"offline"}'      │    │
│  │                                                                      │    │
│  │ • All server instances subscribe to channel                         │    │
│  │ • When user status changes, all instances notified                  │    │
│  │ • Each instance updates connected clients via WebSocket             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Why Redis?**
- Sub-millisecond latency for presence checks
- TTL for automatic offline detection
- Pub/Sub for real-time broadcast to all server instances
- Ephemeral data that doesn't need durability

> 📚 **Learn more:** [Redis Guide](../technologies/redis.md)

---

### 4. Azure Blob Storage (Files, Media, Recordings)

| Container | Purpose | Access |
|-----------|---------|--------|
| `attachments` | Chat file attachments | Private (SAS tokens) |
| `profile-images` | User avatars | Public read via CDN |
| `recordings` | Meeting recordings | Private (SAS tokens) |
| `thumbnails` | File/video previews | Public read via CDN |
| `transcripts` | Meeting transcripts | Private (SAS tokens) |

**Configuration:**
- Lifecycle Policy: Hot → Cool after 30 days, Archive after 1 year
- Soft delete: 14 days retention
- Versioning: Enabled for compliance

**Why Blob Storage?**
- Cheapest storage for large files (pennies per GB)
- Tiered storage (hot/cool/archive) for cost optimization
- Direct upload via SAS tokens (offloads application servers)
- Integrates with CDN for global delivery

---

### 5. Azure AI Search (Message & File Search)

| Index | Source | Searchable Fields |
|-------|--------|-------------------|
| `messages-index` | Cosmos DB change feed | `content`, `senderId`, `channelId`, `timestamp` |
| `files-index` | Azure SQL + Blob metadata | `fileName`, `content` (extracted), `uploadedBy` |
| `people-index` | Azure AD sync | `displayName`, `email`, `department` |

**Configuration:**
- Indexer: Runs every 5 minutes from Cosmos DB change feed
- Security: Filter by `tenantId` in queries
- Features: Fuzzy search, highlighting, autocomplete

**Why dedicated Search (not SQL LIKE)?**
- Full-text search with ranking and relevance
- Fuzzy matching (typos, synonyms)
- Fast even with millions of messages
- Faceted search (filter by date, sender, channel)

---

## Scalability Considerations

- **Horizontal scaling**: Stateless services in AKS with HPA (Horizontal Pod Autoscaler)
- **Database sharding**: Cosmos DB partitioned by channel_id (messages stay together)
- **Message fanout**: Event Hubs partitioned by team_id, SignalR groups per channel
- **CDN**: Azure CDN for profile pictures, file downloads, static assets
- **Regional deployment**: Active-active in 3+ regions, Cosmos DB multi-master
- **Connection limits**: SignalR service scales to millions of concurrent connections

---

## Technologies Deep Dive

📚 **Learn more about the database technologies used:**

| Technology | Role | Reference |
|------------|------|-----------|
| Cosmos DB | Message storage, global distribution | [Cosmos DB Guide](../technologies/cosmos-db.md) |
| Redis | Presence, caching, pub/sub | [Redis Guide](../technologies/redis.md) |
| PostgreSQL/SQL | Relational metadata | [PostgreSQL Guide](../technologies/postgresql.md) |
| Elasticsearch | Full-text search | [Elasticsearch Guide](../technologies/elasticsearch.md) |

---

## Related Pages

- [Chat & Messaging](./chat.md) - How Cosmos DB and Redis are used
- [Video & Calls](./video.md) - Blob Storage for recordings
- [File Sharing](./files.md) - Blob Storage for attachments
- [Interview Tips](./interview-tips.md) - Trade-offs, how to explain

[← Back to Overview](./README.md)
