# Redis

## What It Is
Redis is an in-memory data structure store used as a cache, message broker, and database. It supports strings, hashes, lists, sets, sorted sets, streams, and more with sub-millisecond latency.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              REDIS ARCHITECTURE                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

CLIENT REQUEST                    REDIS SERVER                         PERSISTENCE
     │                                 │                                    │
     │  GET user:123                   │                                    │
     │────────────────────────────────▶│                                    │
     │                                 │                                    │
     │                    ┌────────────┴────────────┐                       │
     │                    │      IN-MEMORY          │                       │
     │                    │     DATA STORE          │                       │
     │                    │                         │                       │
     │                    │  Hash Tables            │                       │
     │                    │  Skip Lists (sorted)    │                       │
     │                    │  Linked Lists           │                       │
     │                    └────────────┬────────────┘                       │
     │                                 │                                    │
     │  {"name": "John", ...}          │          ┌─────────────────────────┤
     │◀────────────────────────────────│          │  RDB Snapshots          │
     │                                 │          │  AOF (Append Only File) │
     │                                 │          └─────────────────────────┘

SINGLE-THREADED EVENT LOOP:
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Redis uses single-threaded event loop for commands (no locks needed!)                  │
│  All operations are ATOMIC by default                                                   │
│  I/O handled by separate threads (Redis 6+)                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Data Structures

### 1. Strings
```redis
SET user:123:name "John"
GET user:123:name              → "John"
INCR counter                   → 1 (atomic increment)
SETEX session:abc 3600 "data"  → Set with 1 hour TTL
```

### 2. Hashes (Objects)
```redis
HSET user:123 name "John" age 30 city "Seattle"
HGET user:123 name             → "John"
HGETALL user:123               → {"name": "John", "age": "30", "city": "Seattle"}
HINCRBY user:123 age 1         → 31
```

### 3. Lists (Queues)
```redis
LPUSH queue:tasks "task1"      → Push to left (head)
RPUSH queue:tasks "task2"      → Push to right (tail)
LPOP queue:tasks               → Pop from left
BRPOP queue:tasks 30           → Blocking pop (wait up to 30s)
```

### 4. Sets (Unique items)
```redis
SADD tags:post:1 "redis" "database" "cache"
SMEMBERS tags:post:1           → {"redis", "database", "cache"}
SISMEMBER tags:post:1 "redis"  → 1 (true)
SINTER tags:post:1 tags:post:2 → Intersection
```

### 5. Sorted Sets (Leaderboards, Priority Queues)
```redis
ZADD leaderboard 100 "player1" 85 "player2" 92 "player3"
ZREVRANGE leaderboard 0 2 WITHSCORES  → Top 3 players
ZRANK leaderboard "player2"            → 2 (0-indexed rank)
ZINCRBY leaderboard 10 "player2"       → Add 10 points

Time Complexity:
  ZADD:     O(log N)
  ZRANK:    O(log N)
  ZRANGE:   O(log N + M) where M = result size
```

### 6. Geo (Location-based)
```redis
GEOADD places -122.33 47.60 "starbucks"
GEOADD places -122.34 47.61 "mcdonalds"
GEORADIUS places -122.33 47.60 5 km WITHDIST
  → Returns places within 5km with distances
```

## Common Patterns

### Cache-Aside Pattern
```
READ:
  1. Check Redis: GET key
  2. If miss → Query database
  3. Store in Redis: SET key value EX 3600

WRITE:
  1. Update database
  2. Invalidate cache: DEL key
  (Don't update cache - may cause inconsistency)
```

### Distributed Lock
```redis
# Acquire lock (atomic)
SET lock:resource "owner123" NX EX 30
  NX = Only if not exists
  EX = Expires in 30 seconds

# Release lock (Lua script for atomicity)
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end
```

### Rate Limiting (Sliding Window)
```redis
# Allow 100 requests per minute
local current = redis.call("INCR", KEYS[1])
if current == 1 then
    redis.call("EXPIRE", KEYS[1], 60)
end
if current > 100 then
    return 0  -- Rate limited
end
return 1  -- Allowed
```

### Pub/Sub
```redis
# Subscriber
SUBSCRIBE channel:notifications

# Publisher
PUBLISH channel:notifications "New message!"

# All subscribers receive the message instantly
```

## Clustering & High Availability

```
REDIS CLUSTER (Horizontal Scaling):
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│   Data sharded across nodes using hash slots (16384 total)                              │
│                                                                                          │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                                │
│   │   Node 1    │    │   Node 2    │    │   Node 3    │                                │
│   │ Slots 0-5460│    │Slots 5461-  │    │Slots 10923- │                                │
│   │             │    │    10922    │    │    16383    │                                │
│   │  + Replica  │    │  + Replica  │    │  + Replica  │                                │
│   └─────────────┘    └─────────────┘    └─────────────┘                                │
│                                                                                          │
│   Key "user:123" → CRC16("user:123") % 16384 = slot 7842 → Node 2                      │
│                                                                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

REDIS SENTINEL (High Availability):
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│   Sentinel monitors master, promotes replica on failure                                 │
│                                                                                          │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐                                       │
│   │ Sentinel │     │ Sentinel │     │ Sentinel │   (Quorum = 2)                        │
│   └────┬─────┘     └────┬─────┘     └────┬─────┘                                       │
│        │                │                │                                              │
│        └────────────────┼────────────────┘                                              │
│                         │ monitors                                                      │
│                         ▼                                                               │
│                  ┌────────────┐                                                         │
│                  │   Master   │                                                         │
│                  └─────┬──────┘                                                         │
│                        │ replicates                                                     │
│              ┌─────────┴─────────┐                                                      │
│              ▼                   ▼                                                      │
│        ┌──────────┐        ┌──────────┐                                                │
│        │ Replica 1│        │ Replica 2│                                                │
│        └──────────┘        └──────────┘                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## When to Use

✅ **Good for:**
- Caching (session, API responses, database queries)
- Real-time leaderboards (sorted sets)
- Rate limiting (atomic counters)
- Pub/Sub messaging (chat, notifications)
- Distributed locks
- Geospatial queries
- Session storage

❌ **Not ideal for:**
- Data larger than memory
- Complex queries (joins, aggregations)
- Strong durability requirements (use database)
- Large objects (>1MB values)

## Trade-offs

| Pros | Cons |
|------|------|
| Sub-millisecond latency | Limited to RAM size |
| Rich data structures | Single-threaded for commands |
| Atomic operations | Persistence can impact performance |
| Built-in replication | No built-in encryption (use TLS) |
| Cluster mode for scaling | Memory fragmentation possible |

## Configuration Tips

```
# Memory management
maxmemory 4gb
maxmemory-policy allkeys-lru  # Evict least recently used

# Persistence
save 900 1      # RDB snapshot if 1 key changed in 900 sec
appendonly yes  # AOF for durability
appendfsync everysec  # Fsync every second (balance)

# Timeouts
timeout 300     # Close idle connections after 5 min
tcp-keepalive 60
```

## Used In (This Guide)

- **Teams (Q1)**: Presence, session cache
- **URL Shortener (Q4)**: URL mapping cache
- **Rate Limiter (Q16)**: Token bucket, sliding window
- **Leaderboard (Q32)**: Sorted sets for rankings
- **Ticket Booking (Q42)**: Inventory counters, queue
- **E-commerce (Q46)**: Shopping cart, inventory cache
