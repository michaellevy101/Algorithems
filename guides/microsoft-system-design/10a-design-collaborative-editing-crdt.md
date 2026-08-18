# 10a. Design Collaborative Document Editing — CRDT Approach

> 📘 **Two approaches exist for this problem.** This guide covers the **CRDT (decentralized)** approach. See also: [10b — OT/Server-Authority Approach](10b-design-collaborative-editing-ot.md) for a centralized, server-authoritative design using Operational Transformation.

**Key Terms:**

| Term | Full Name | What it does |
|------|-----------|--------------|
| **OT** | Operational Transformation | Algorithm transforming concurrent edits to maintain consistency |
| **CRDT** | Conflict-free Replicated Data Type | Data structure that auto-merges without conflicts |
| **WebSocket** | Full-duplex protocol | Real-time bidirectional communication |
| **Tombstone** | Deleted marker | Marks deleted items instead of removing (for sync) |
| **Pub/Sub** | Publish/Subscribe | Messaging pattern where senders broadcast to subscribers |
| **Chunking** | Document partitioning | Splitting large documents into independently editable sections |
| **Vector Clock** | Logical timestamp | Tracks causal ordering of events across distributed nodes |

## Problem Statement
Design a real-time collaborative document editor like Google Docs or Microsoft Word Online where multiple users can edit simultaneously. The system must support arbitrarily large documents, handle 100+ concurrent editors, and deliver sub-100ms edit propagation.

## Clarifying Questions to Ask

1. **Concurrency**: How many simultaneous editors per document? (50? 100? 500?)
2. **Document size**: Is there a max document size, or should it be unbounded? (100KB? 10MB? unlimited?)
3. **Latency**: Max acceptable delay for seeing others' edits? (<100ms?)
4. **Offline support**: Allow offline editing and sync later?
5. **Features**: Comments, suggestions, version history, undo/redo?
6. **Conflict resolution**: How to handle simultaneous edits to the same paragraph?
7. **Rich content**: Text only or images, tables, embedded media, formatting?
8. **Retention**: How long to keep version history? (30 days? forever?)

## Design Reasoning

> **Core philosophy**: Real-time collaboration demands conflict-free convergence across all clients, making eventual consistency and low-latency change propagation the primary drivers of every architectural choice. Documents of any size must remain editable without loading the entire content into memory.

> **Why CRDT over OT?** OT requires a central server to sequence and transform every operation — this creates a single point of failure and a scalability bottleneck. CRDTs are mathematically guaranteed to converge regardless of operation order, enabling offline editing, P2P sync, and horizontal scaling without a coordinator. The trade-off is higher per-character storage (unique IDs + tombstones), but this is manageable with periodic garbage collection. For a **server-authoritative OT approach** that gives the server full structural control, see [Guide 10b](10b-design-collaborative-editing-ot.md).

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Conflict Resolution | CRDT | Auto-merges without central coordination; supports offline editing and P2P sync | OT — requires a central server to transform operations, harder to scale horizontally |
| Real-time Transport | WebSocket (SignalR) | Full-duplex, sub-100ms latency needed for keystroke-level collaborative edits | HTTP polling — too high latency for real-time multi-user editing |
| Document Store | Cosmos DB | Flexible schema for CRDT tree structures; low-latency global reads with multi-region | PostgreSQL — rigid relational schema poorly fits nested CRDT state |
| Session & Presence Store | Redis Pub/Sub | In-memory speed for cursor tracking and active editor lists across servers | Database polling — too slow for real-time cursor position updates |
| Version History | Event Store (append-only log) | Natural fit for operation history; enables replay, undo, and time-travel | Full-document snapshots only — expensive storage, loses granular edit history |
| Sync Strategy | Batched operations (50ms) | Reduces network overhead vs. per-keystroke while still feeling real-time | Per-keystroke sync — excessive bandwidth and server load for large sessions |
| Large Document Strategy | Chunked by section | Only load and sync sections near the viewport; scales to unlimited document size | Monolithic CRDT — entire document state in memory, collapses at ~10MB |

## High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                COLLABORATIVE EDITING ARCHITECTURE                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   User A    │  │   User B    │  │   User C    │
│  (Editing)  │  │  (Editing)  │  │  (Viewing)  │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       │ Operations     │ Operations     │ Subscribe
       ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    WEBSOCKET GATEWAY (SignalR)                    │
│              (Maintains persistent connections per doc)           │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    COLLABORATION SERVICE                         │
│                                                                  │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐       │
│  │  CRDT Engine  │  │   Chunk Mgr   │  │  Presence Mgr │       │
│  │               │  │               │  │               │       │
│  │ Merge ops per │  │ Load/unload   │  │ Cursor sync   │       │
│  │ chunk, resolve│  │ sections on   │  │ via Redis     │       │
│  │ conflicts     │  │ demand        │  │ Pub/Sub       │       │
│  └───────────────┘  └───────────────┘  └───────────────┘       │
└─────────────────────────┬───────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │ Document │    │ Session  │    │ Version  │
    │   Store  │    │  Store   │    │ History  │
    │(Cosmos DB)│   │ (Redis)  │    │(Event Log)│
    └──────────┘    └──────────┘    └──────────┘
```

## API Design

### Document APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/documents` | Create a new document |
| `GET` | `/api/v1/documents/{docId}` | Get document metadata and table of contents |
| `GET` | `/api/v1/documents/{docId}/chunks?range=0-5` | Get specific chunks (lazy load) |
| `GET` | `/api/v1/documents/{docId}/history` | Get revision history |
| `GET` | `/api/v1/documents/{docId}/history/{version}` | Get specific version |
| `DELETE` | `/api/v1/documents/{docId}` | Delete a document |

### Collaboration APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/documents/{docId}/share` | Share document with users |
| `GET` | `/api/v1/documents/{docId}/collaborators` | List active collaborators |
| `GET` | `/api/v1/documents/{docId}/cursors` | Get collaborator cursor positions |
| `POST` | `/api/v1/documents/{docId}/comments` | Add a comment or suggestion |

### WebSocket Events (Real-Time Editing)
| Event | Direction | Payload |
|-------|-----------|---------|
| `op.apply` | Client → Server | `{ docId, chunkId, operations[], baseVersion }` |
| `op.broadcast` | Server → Clients | `{ docId, chunkId, operations[], version, userId }` |
| `cursor.move` | Client → Server | `{ docId, chunkId, position, selection }` |
| `cursor.update` | Server → Clients | `{ userId, chunkId, position, selection, color }` |
| `user.joined` | Server → Clients | `{ userId, name, avatarUrl }` |
| `user.left` | Server → Clients | `{ userId }` |
| `chunk.subscribe` | Client → Server | `{ docId, chunkIds[] }` |
| `chunk.unsubscribe` | Client → Server | `{ docId, chunkIds[] }` |

### Apply Operations Request
```json
// Client sends a batch of operations every 50ms
{
  "doc_id": "doc123",
  "chunk_id": "chunk_3",
  "base_version": 1541,
  "client_seq": 47,
  "operations": [
    { "type": "insert", "position": 42, "text": "hello", "crdt_id": "A:47" },
    { "type": "delete", "position": 45, "length": 2, "crdt_ids": ["B:12", "B:13"] }
  ]
}
```

## Key Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| WebSocket Gateway | Persistent full-duplex connections | SignalR / Socket.io |
| CRDT Engine | Conflict-free merge of concurrent edits | Yjs / Automerge / Custom |
| Chunk Manager | Lazy-load and partition large documents | Custom |
| Document Store | Persist CRDT state and chunks | Cosmos DB |
| Session Store | Active editors, connection routing | Redis |
| Version History | Append-only operation log | Event Store / Kafka |
| Presence Service | Cursor positions, active user list | Redis Pub/Sub |
| Media Service | Handle embedded images and files | Blob Storage + CDN |

## Why Not OT? (Brief Comparison)

```
OT (Operational Transformation) is the main alternative to CRDTs.
It transforms concurrent position-based operations against each other
so both sides converge. Used by Google Docs.

KEY DIFFERENCE:
  ┌────────────────────────────────┬─────────────────────────────────┐
  │  OT (Server-Authoritative)     │  CRDT (Decentralized) ✓        │
  ├────────────────────────────────┼─────────────────────────────────┤
  │  Central server sequences ops  │  No coordinator needed          │
  │  Server = single point of fail │  Any replica can accept writes  │
  │  Requires connectivity         │  Works fully offline            │
  │  Complex transform functions   │  Merge is automatic by design   │
  │  Compact (no per-char IDs)     │  Storage overhead (unique IDs)  │
  └────────────────────────────────┴─────────────────────────────────┘

  We choose CRDT because offline editing and horizontal scaling
  without a coordinator are primary requirements.

  For a full OT-based design, see Guide 10b.
```

## CRDT Engine (Primary Approach)

```
CRDT (Conflict-free Replicated Data Type):

Each character has a unique, ordered ID based on (siteId, counter):
  "HELLO" → [(A:1,'H'), (A:2,'E'), (A:3,'L'), (A:4,'L'), (A:5,'O')]

CRDT STATE TREE:

  Position:    A:1     A:2     A:3     A:4     A:5
  Character:    H       E       L       L       O
                        │       │
                        ▼       ▼
  User A inserts 'X'  A:6    User B deletes
  after A:2            'X'    A:3 → tombstone
                        │
                        ▼

  Final State:
  ┌─────┬─────┬─────┬─────────┬─────┬─────┐
  │ A:1 │ A:2 │ A:6 │   A:3   │ A:4 │ A:5 │
  │  H  │  E  │  X  │ ░░░L░░░ │  L  │  O  │
  │     │     │ NEW │TOMBSTONE │     │     │
  └─────┴─────┴─────┴─────────┴─────┴─────┘
  Visible: "HEXLO"

WHY IT WORKS: IDs are globally unique and totally ordered.
  - Each user has a unique siteId (e.g., "A", "B")
  - IDs are tuples: (siteId, counter) → "A:6", "B:3"
  - Total order: sort by position first, then by siteId to break ties
  - Insertions always place between existing IDs (no position shift)
  - Deletions mark as tombstone (never physically removed during editing)
  - Any two replicas that have seen the same set of ops converge
  - No central server needed — works peer-to-peer and offline

CONCURRENT INSERT AT SAME POSITION (tie-breaking):
  User A inserts 'X' after A:2 → creates A:6
  User B inserts 'Y' after A:2 → creates B:3
  Both go between A:2 and A:3 — who wins?

  TIE-BREAK RULE: Compare siteId lexicographically.
    "A:6" < "B:3" (because "A" < "B")
    → A:6 comes first, then B:3
    → Result: "HE-X-Y-LLO" (deterministic on all replicas)

  This works because:
  - Every replica applies the same tie-breaking rule
  - siteIds are globally unique (assigned on session join)
  - The result is deterministic regardless of arrival order

PROS vs OT:
  ✓ No central coordination      ✗ OT needs a server
  ✓ Works offline natively       ✗ OT requires connectivity
  ✓ Simpler merge logic          ✗ OT transform functions are complex
  ✗ Storage overhead (IDs)       ✓ OT is more compact
  ✗ Tombstone garbage collection ✓ OT deletes immediately
```

## Large Document Handling

A collaborative editor must handle documents of any size — from a short memo to a 500-page report. Loading the entire CRDT state into memory is not viable beyond ~10MB.

```
CHUNKED DOCUMENT ARCHITECTURE:

┌──────────────────────────────────────────────────────┐
│  Document: "Project Plan" (doc123)                    │
│                                                       │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐     │
│  │  Chunk 0   │  │  Chunk 1   │  │  Chunk 2   │ ... │
│  │ "Title &   │  │ "Section 1 │  │ "Section 2 │     │
│  │  Intro"    │  │  Budget"   │  │  Timeline" │     │
│  │            │  │            │  │            │     │
│  │ ~50KB CRDT │  │ ~120KB CRDT│  │ ~80KB CRDT │     │
│  │ state      │  │ state      │  │ state      │     │
│  │ version: 47│  │ version: 92│  │ version: 31│     │
│  └────────────┘  └────────────┘  └────────────┘     │
│                                                       │
│  Metadata (always loaded):                            │
│  { chunks: 47, total_size: "4.2MB", editors: 12 }    │
└──────────────────────────────────────────────────────┘

CLIENT VIEWPORT LOADING:

  User scrolls to Section 2:
  ┌─────────────────────────────────────────────┐
  │ Chunk 0  │  Chunk 1  │ ▓▓Chunk 2▓▓│Chunk 3 │
  │ skeleton │  loaded   │  VIEWPORT  │ loading │
  │ (height  │  (cached) │  (active   │ (prefetch│
  │  only)   │           │   CRDT)    │  ahead) │
  └─────────────────────────────────────────────┘

  Only chunks near the viewport have active CRDT state.
  Distant chunks show rendered HTML from last snapshot.
```

### How Chunking Works

| Aspect | Strategy | Detail |
|--------|----------|--------|
| **Chunk boundary** | Section headings (H1, H2) | Natural document structure; users rarely edit across headings |
| **Chunk size target** | ~50–200KB of CRDT state | Keeps merge operations fast; one chunk fits in a single WebSocket frame |
| **Loading** | On-demand + prefetch ±1 | Client subscribes to visible chunks and prefetches neighbors |
| **Unloading** | LRU eviction | Chunks far from viewport are flushed; re-fetched on scroll |
| **Cross-chunk edits** | Two-phase commit | Rare (e.g., deleting across a heading); lock both chunks briefly |
| **Splitting** | Auto-split at 300KB | When a chunk exceeds 300KB, split at nearest heading boundary |
| **Merging** | Auto-merge below 20KB | When adjacent chunks both drop below 20KB, merge them |

### Memory & Bandwidth Estimates

```
Per-chunk overhead:
  CRDT state:        50–200 KB (text + character IDs)
  Tombstones:        ~20% overhead (cleaned up periodically)
  Operation buffer:  ~5 KB (50ms batch window)

Per-client memory:
  Active chunks:     3–5 loaded  → 150 KB – 1 MB
  Prefetched:        2 chunks    → ~200 KB
  Skeleton metadata: ~1 KB per unloaded chunk
  Total for 500-page doc: ~2 MB client memory (not 50 MB)

Network per editor:
  Operation batches: ~500 bytes every 50ms = ~10 KB/s while typing
  Chunk load:        ~100 KB one-time per scroll into new section
  Cursor updates:    ~50 bytes every 100ms = ~500 B/s per collaborator
```

## Presence & Cursor Sync

```
PRESENCE FLOW:

  User A moves cursor in Chunk 2
       │
       ▼
  ┌─────────────────────────────┐
  │ WebSocket Gateway (Server 1)│
  └──────────────┬──────────────┘
                 │ PUBLISH "doc123:chunk2:cursors"
                 ▼
  ┌─────────────────────────────┐
  │        Redis Pub/Sub        │
  │                             │
  │  Channel: doc123:presence   │
  │  Payload: {                 │
  │    userId: "A",             │
  │    chunkId: "chunk_2",      │
  │    position: 142,           │
  │    selection: [142, 158],   │
  │    color: "#3B82F6"         │
  │  }                          │
  └──────────┬──────────────────┘
             │ Fan out to all subscribed servers
             ▼
  ┌─────────────────────────────┐
  │ WebSocket Gateway (Server 2)│ → pushes to User B, C
  └─────────────────────────────┘

  Each user gets a unique color. Cursors are shown as
  colored carets with name labels that fade after 3 seconds.

  PRESENCE LIST (Redis Hash):
  ┌────────────────────────────────────────────────┐
  │ doc123:editors                                  │
  │   "userA" → { name, color, chunkId, lastSeen } │
  │   "userB" → { name, color, chunkId, lastSeen } │
  │   "userC" → { name, color, chunkId, lastSeen } │
  │                                                 │
  │  TTL: 30s — heartbeat every 10s refreshes it    │
  │  If heartbeat missed → user removed from list   │
  └────────────────────────────────────────────────┘
```

## Offline Sync Strategy

```
OFFLINE EDITING FLOW:

  1. User goes offline
     ┌──────────────────────────────────┐
     │ Local CRDT state is self-contained│
     │ User continues editing normally   │
     │ Operations queue in IndexedDB     │
     └──────────────────────────────────┘

  2. Edits accumulate locally
     ┌──────────────────────────────────┐
     │ Local ops:  [insert 'X' at A:6]  │
     │             [delete A:3]          │
     │             [insert 'Y' at A:7]  │
     │ Queued: 3 ops, 0 acknowledged    │
     └──────────────────────────────────┘

  3. User reconnects
     ┌─────────────┐                ┌──────────────┐
     │   Client    │───sync req───▶│    Server     │
     │             │  (my version: │               │
     │             │   47, ops: 3) │               │
     │             │               │  server ver:  │
     │             │◀──sync resp───│   52 (5 new   │
     │             │  (missed ops) │   remote ops) │
     └─────────────┘                └──────────────┘

  4. CRDT merge (automatic — no conflicts!)
     Local ops + remote ops → merged CRDT state
     Both client and server converge to version 55

  KEY: CRDTs guarantee convergence regardless of
  the order operations are received. No conflict
  resolution UI needed — it just works.
```

## Version History & Snapshots

```
HYBRID STORAGE: Snapshots + Operation Log

Timeline:
  ──────────────────────────────────────────────────────▶
  │         │         │         │         │
  v0       v100      v200      v300      v342 (current)
  SNAP      SNAP      SNAP      SNAP
  (full     (full     (full     (full
  state)    state)    state)    state)
             ├─op101──op102──...──op200─┤
                    operations between snapshots

  To reconstruct version 250:
  1. Load snapshot at v200     (~100 KB, instant)
  2. Replay 50 operations      (~5 KB, <10ms)
  Total: Much faster than replaying all 250 ops

SNAPSHOT SCHEDULE:
  - Every 100 operations OR every 5 minutes (whichever first)
  - Per chunk, not per document (chunks have independent versions)
  - Old snapshots retained per retention policy (e.g., 90 days)

UNDO/REDO:
  - Each user maintains a local undo stack of their own operations
  - Undo generates an inverse operation (insert ↔ delete)
  - Inverse op is applied through the normal CRDT merge path
  - Other users see the undo as a regular edit (no special handling)
```

## Data Model

```json
// Document Metadata (always loaded)
{
  "doc_id": "doc123",
  "title": "Project Plan",
  "owner_id": "user456",
  "collaborators": [
    { "user_id": "user789", "role": "editor", "added_at": "2024-01-15T10:00:00Z" },
    { "user_id": "user012", "role": "viewer", "added_at": "2024-01-16T09:00:00Z" }
  ],
  "chunk_count": 47,
  "total_size_bytes": 4200000,
  "created_at": "2024-01-15T10:00:00Z",
  "updated_at": "2024-01-15T14:30:00Z"
}

// Chunk (loaded on demand)
{
  "chunk_id": "chunk_3",
  "doc_id": "doc123",
  "heading": "Section 2: Timeline",
  "version": 31,
  "crdt_state": [
    { "id": "A:1", "char": "S", "deleted": false },
    { "id": "A:2", "char": "e", "deleted": false },
    { "id": "B:5", "char": "X", "deleted": false },
    { "id": "A:3", "char": "c", "deleted": true },
    ...
  ],
  "snapshot_version": 20,
  "pending_ops_since_snapshot": 11,
  "size_bytes": 85000
}

// Operation (append-only log)
{
  "op_id": "op789",
  "doc_id": "doc123",
  "chunk_id": "chunk_3",
  "user_id": "user456",
  "base_version": 30,
  "operations": [
    { "type": "insert", "crdt_id": "A:47", "after": "A:2", "char": "x" },
    { "type": "delete", "crdt_id": "B:5" }
  ],
  "timestamp": "2024-01-15T14:30:00Z"
}
```

## Scalability Considerations

| Aspect | Strategy | Numbers |
|--------|----------|---------|
| **Concurrent editors** | One collab session per server, sticky routing | Up to ~500 editors per doc session server |
| **Document size** | Chunked by section; only viewport chunks loaded | Unlimited — 500-page docs use ~2MB client RAM |
| **Operation batching** | Collect and send every 50ms | ~10 KB/s per active typist |
| **Snapshots** | Every 100 ops or 5 min per chunk | Keeps replay under 100 ops (~10ms) |
| **Tombstone GC** | Background sweep when no editors online | Reclaims ~20% CRDT overhead after cleanup |
| **Connection limits** | Each gateway handles ~100K WebSocket connections | Horizontal scale with Azure SignalR Service |
| **Global latency** | Multi-region Cosmos DB + regional gateways | <100ms for same-region, <300ms cross-region |
| **Storage** | CRDT state + op log + snapshots per chunk | ~500 KB/chunk avg; 500-page doc ≈ 25 MB total |

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| Algorithm | OT (central server) | CRDT (decentralized) | **CRDT** — offline-friendly, no coordinator bottleneck |
| Sync frequency | Per-keystroke | Batched (50ms) | **Batched** — 20x less network overhead, still feels instant |
| Document storage | Monolithic state | Chunked by section | **Chunked** — scales to unlimited size, reduces memory |
| Version storage | Full snapshots only | Ops only | **Hybrid** — snapshots every 100 ops + op log between |
| Cursor sync | Sync all positions | Only visible chunks | **Visible chunks** — reduces noise in large documents |
| Tombstone cleanup | Eager (on delete) | Lazy (background GC) | **Lazy** — avoids coordination; GC when session is idle (timeout: 5 min no edits) |

## Consistency & Failure Handling

```
CONSISTENCY GUARANTEE: Strong Eventual Consistency (SEC).
  Any two replicas that have received the same set of operations
  are guaranteed to be in the same state — regardless of order.

  This is stronger than plain eventual consistency because
  convergence is guaranteed by the CRDT math, not just
  "eventually maybe."

IDEMPOTENCY:
  CRDT operations are inherently idempotent:
  - Inserting (A:6, 'X') twice → only one 'X' (same ID)
  - Deleting A:3 twice → already a tombstone (no-op)
  No clientOpId needed — the CRDT structure handles dedup.

SERVER CRASH RECOVERY:
  ┌──────────────────────────────────────────────────────────┐
  │  CRDT state is persisted per-chunk in Cosmos DB.         │
  │  If the collaboration server crashes:                    │
  │                                                          │
  │  1. Clients detect lost WebSocket → reconnect            │
  │  2. New server loads CRDT state from Cosmos DB           │
  │  3. Clients push any unACK'd operations                  │
  │  4. CRDT merge is idempotent → duplicates are harmless   │
  │                                                          │
  │  No operations are lost because:                         │
  │  - Persisted ops survive the crash (Cosmos DB)           │
  │  - Unpersisted ops are retried by clients (idempotent)   │
  │  - CRDT guarantees convergence after all ops are merged  │
  └──────────────────────────────────────────────────────────┘

LATENCY DEFINITIONS:
  "Sub-100ms" means: time from one client sending an operation
  to another client in the SAME REGION rendering it on screen.
  Cross-region: <300ms (includes Cosmos DB replication lag).
  Local echo: instant (<5ms) — client applies its own ops
  optimistically before server confirms.
```

## Technologies Deep Dive

📚 **Learn more about the key technologies used in this design:**

| Technology | Role in This Design | Reference |
|------------|---------------------|-----------|
| WebSocket | Real-time operation sync between clients | [Tech Guide](technologies/websocket.md) |
| Redis | Presence tracking and cursor pub/sub | [Tech Guide](technologies/redis.md) |
| Raft | Consensus for operation ordering | [Tech Guide](technologies/raft-consensus.md) |
| SignalR | Managed real-time connection service | [Tech Guide](technologies/signalr.md) |

---

## How to Explain It Well

> **Opening**: "Collaborative editing requires real-time sync with conflict resolution across arbitrarily large documents. I'll use CRDTs for automatic merge and chunk the document by section so we never load the whole thing into memory."

> **Key talking points**:
> - "CRDTs give each character a unique ID — merges are automatic with no central coordinator"
> - "Documents are chunked by section heading; clients only load chunks near the viewport"
> - "This means a 500-page doc uses ~2 MB of client memory, not 50 MB"
> - "WebSocket for real-time sync, batch operations every 50ms for efficiency"
> - "Presence shows other users' cursors via Redis pub/sub with colored carets"
> - "Snapshots every 100 ops keep version history replay fast (<10ms)"
> - "Offline: CRDT state is self-contained — queue ops locally, merge on reconnect with zero conflicts"

---