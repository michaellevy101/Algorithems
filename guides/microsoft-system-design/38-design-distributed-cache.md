# 38. Design Distributed Cache

**Key Terms:**

| Term | Full Name | What it does |
|------|-----------|--------------|
| **LRU** | Least Recently Used | Eviction policy removing oldest accessed item |
| **LFU** | Least Frequently Used | Eviction policy removing least accessed item |
| **TTL** | Time To Live | Auto-expiration after set duration |
| **Consistent Hashing** | Distribution algorithm | Minimizes data movement when scaling |
| **Virtual Node** | Hash ring technique | Multiple ring positions per physical node |
| **QPS** | Queries Per Second | Throughput measure for cache |
| **AOF** | Append-Only File | Redis persistence by logging writes |
| **RDB** | Redis Database | Redis point-in-time snapshot persistence |

## Problem Statement
Design a distributed caching system like Redis or Memcached that provides low-latency data access across multiple nodes.

## Clarifying Questions to Ask

1. **Scale**: Data size? QPS? (1TB, 1M QPS?)
2. **Consistency**: Strong or eventual?
3. **Eviction**: LRU, LFU, TTL?
4. **Persistence**: In-memory only or persist to disk?
5. **Data types**: Key-value only or complex types (lists, sets)?
6. **Replication**: Primary-replica or peer-to-peer?
7. **Partition tolerance**: How to handle network splits?

## Design Reasoning

> **Core philosophy**: Minimize data movement during scaling and maximize throughput by distributing keys via consistent hashing, keeping routing logic in a smart client library to avoid proxy overhead.

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Key distribution | Consistent hashing with virtual nodes | Adding/removing a node moves only 1/N of keys; virtual nodes ensure even load | Modulo hashing — adding a node remaps nearly all keys, causing cache stampede |
| Routing | Client library (no proxy) | Eliminates an extra network hop; client directly connects to correct node | Proxy-based routing — adds latency and a single point of failure |
| Eviction policy | LRU | O(1) access and eviction with doubly-linked list + hash map; matches most workloads | LFU — better for stable hot-sets but more complex and scan-resistant workloads are rare |
| Consistency | Eventual | Caches tolerate staleness; strong consistency would add write latency that defeats the purpose | Strong consistency — requires synchronous replication, negating cache speed benefits |
| Replication | Async primary-replica | Low write latency; replica handles failover without blocking the write path | Synchronous replication — higher durability but doubles write latency |
| Persistence | Optional (AOF/RDB) | Pure cache needs no persistence; warm restart benefits from RDB snapshots | Always persist — unnecessary write overhead for ephemeral cache data |

## High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                 DISTRIBUTED CACHE ARCHITECTURE                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        CLIENTS                                   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                   CLIENT LIBRARY                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Consistent Hashing Ring                                  │   │
│  │                                                           │   │
│  │        ┌───────────────────┐                             │   │
│  │       ╱                     ╲                            │   │
│  │      │    ●Node A           │                            │   │
│  │      │         ○ key1       │                            │   │
│  │      │              ●Node B │  hash(key) → node          │   │
│  │      │    ○ key2            │                            │   │
│  │       ╲         ●Node C    ╱                             │   │
│  │        └───────────────────┘                             │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
    ┌─────────────────────┼─────────────────────┐
    ▼                     ▼                     ▼
┌─────────────┐    ┌─────────────┐      ┌─────────────┐
│   NODE A    │    │   NODE B    │      │   NODE C    │
│             │    │             │      │             │
│ ┌─────────┐ │    │ ┌─────────┐ │      │ ┌─────────┐ │
│ │Hash Table│ │    │ │Hash Table│ │      │ │Hash Table│ │
│ │         │ │    │ │         │ │      │ │         │ │
│ │key1→val1│ │    │ │key2→val2│ │      │ │key3→val3│ │
│ └─────────┘ │    │ └─────────┘ │      │ └─────────┘ │
│             │    │             │      │             │
│ ┌─────────┐ │    │ ┌─────────┐ │      │ ┌─────────┐ │
│ │ Replica │ │◄───│ │ Primary │ │────► │ │ Replica │ │
│ │(Node B) │ │    │ │         │ │      │ │(Node B) │ │
│ └─────────┘ │    │ └─────────┘ │      │ └─────────┘ │
└─────────────┘    └─────────────┘      └─────────────┘
```


## API Design

### Cache Operations APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/cache/{key}` | Get cached value |
| `PUT` | `/api/v1/cache/{key}` | Set cache entry with TTL |
| `DELETE` | `/api/v1/cache/{key}` | Delete cache entry |
| `POST` | `/api/v1/cache/batch/get` | Batch get multiple keys |
| `POST` | `/api/v1/cache/batch/set` | Batch set multiple keys |

### Cache Set Request
```json
PUT /api/v1/cache/user:123:profile
{
  "value": { "name": "John", "email": "john@example.com" },
  "ttl_seconds": 3600,
  "policy": "write-through"   // write-through, write-behind, write-around
}
```

### Cache Management APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/cache/invalidate` | Invalidate by pattern (e.g., `user:123:*`) |
| `GET` | `/api/v1/cache/stats` | Get hit rate, memory usage, eviction count |
| `GET` | `/api/v1/cache/nodes` | List cache nodes and distribution |
| `POST` | `/api/v1/cache/flush` | Flush entire cache (admin only) |


## Key Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| Client Library | Routing, connection pooling | Custom SDK |
| Cache Node | Store data in memory | Custom or Redis |
| Consistent Hashing | Distribute keys | Client-side |
| Replication | Data redundancy | Async replication |
| Cluster Manager | Node membership | Gossip protocol |

## Consistent Hashing

```
CONSISTENT HASHING:
┌─────────────────────────────────────────────────────────────────┐
│  Hash ring: 0 to 2^32-1                                        │
│                                                                  │
│  Nodes placed on ring by hash(node_id):                        │
│    Node A at position 1000                                     │
│    Node B at position 3000                                     │
│    Node C at position 5000                                     │
│                                                                  │
│  Key placement: Walk clockwise to find node                    │
│    hash("user:123") = 2500 → Node B (first node after 2500)   │
│                                                                  │
│  Node addition (Node D at 4000):                               │
│    Only keys between 3000-4000 move from C to D               │
│    Minimal data movement!                                       │
└─────────────────────────────────────────────────────────────────┘

VIRTUAL NODES (for balance):
┌─────────────────────────────────────────────────────────────────┐
│  Problem: 3 nodes might not divide ring evenly                 │
│  Solution: Each physical node → 100+ virtual nodes             │
│                                                                  │
│  Node A → A1, A2, A3, ... A100 (scattered on ring)            │
│  Node B → B1, B2, B3, ... B100                                 │
│  Node C → C1, C2, C3, ... C100                                 │
│                                                                  │
│  Result: Even distribution regardless of node count            │
└─────────────────────────────────────────────────────────────────┘
```

## Eviction Policies

```
LRU (Least Recently Used):
┌─────────────────────────────────────────────────────────────────┐
│  Doubly-linked list + hash map                                 │
│                                                                  │
│  Access: Move to head (O(1))                                   │
│  Evict: Remove from tail (O(1))                               │
│                                                                  │
│  HEAD ←→ [recent] ←→ [older] ←→ [oldest] ←→ TAIL              │
└─────────────────────────────────────────────────────────────────┘

LFU (Least Frequently Used):
┌─────────────────────────────────────────────────────────────────┐
│  Track access count per key                                     │
│  Evict key with lowest count                                   │
│                                                                  │
│  Better for: Workloads with stable hot set                     │
│  Worse for: Scan patterns (one-time access pollutes cache)    │
└─────────────────────────────────────────────────────────────────┘

TTL (Time To Live):
┌─────────────────────────────────────────────────────────────────┐
│  Each key has expiration timestamp                             │
│  Background thread cleans expired keys                         │
│  Lazy expiration: Check on access                              │
└─────────────────────────────────────────────────────────────────┘
```

## Scalability Considerations

- **Horizontal scaling**: Add nodes, consistent hashing redistributes keys
- **Hot keys**: Replicate hot keys to multiple nodes
- **Memory limits**: Set max memory, configure eviction policy
- **Connection pooling**: Reuse connections in client library
- **Pipelining**: Batch multiple commands in single round trip

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| Consistency | Strong | Eventual | **Eventual** - for cache |
| Eviction | LRU | LFU | **LRU** - simpler, good enough |
| Replication | Sync | Async | **Async** - lower latency |
| Persistence | None | AOF/RDB | **Optional** - depends on use case |

## Technologies Deep Dive

📚 **Learn more about the key technologies used in this design:**

| Technology | Role in This Design | Reference |
|------------|---------------------|-----------|
| Redis | In-memory cache with persistence options | [Tech Guide](technologies/redis.md) |
| Cosmos DB | Session store with global distribution | [Tech Guide](technologies/cosmos-db.md) |
| Consistent Hashing | Key distribution across cache nodes | [Tech Guide](technologies/consistent-hashing.md) |

---

## How to Explain It Well

> **Opening**: "Distributed cache needs consistent hashing for even distribution and minimal data movement when scaling."

> **Key talking points**:
> - "Consistent hashing: add node = move only 1/N of keys"
> - "Virtual nodes ensure even distribution across physical nodes"
> - "LRU eviction with doubly-linked list for O(1) operations"
> - "Async replication to replicas for fault tolerance"
> - "Client library handles routing - no proxy needed"

---