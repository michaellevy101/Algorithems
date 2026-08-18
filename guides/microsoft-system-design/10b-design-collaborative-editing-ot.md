# 10b. Design Collaborative Document Editing — OT / Server-Authority Approach

> 📘 **Two approaches exist for this problem.** This guide covers the **OT (server-authoritative)** approach where the server is the single source of truth. See also: [10a — CRDT Approach](10a-design-collaborative-editing-crdt.md) for a decentralized, offline-first design using CRDTs.

**Key Terms:**

| Term | Full Name | What it does |
|------|-----------|--------------|
| **OT** | Operational Transformation | Algorithm that transforms concurrent operations to preserve user intent |
| **Block** | Paragraph unit | The atomic structural unit of a document — typically one paragraph |
| **Segment** | Block group | Groups of blocks owned by a single Collaboration Service instance for OT ordering |
| **Intent** | Client edit payload | Raw text change sent by the thin client; the server interprets it |
| **Fractional Index** | Lexicographic order key | String-based position key (e.g., "a", "an", "b") enabling O(1) insertion |
| **Operations Log** | Server-side op history | Ordered sequence of all accepted operations; used for OT transforms |
| **Revision** | Operation sequence number | Monotonically increasing version after each accepted server operation |
| **clientOpId** | Client operation ID | Unique ID per operation enabling idempotent retries on reconnection |
| **Thin Client** | Minimal client logic | Client only sends intents; server makes all structural decisions |
| **Hot Path** | Real-time pipeline | WS → Redis Stream → OT → Broadcast Svc → Pub/Sub → WS (~7-12ms end-to-end) |
| **Cold Path** | Persistence pipeline | EH → Cosmos DB → Compaction → Blob (async, never blocks hot path) |

## Problem Statement
Design a real-time collaborative document editor like Google Docs or Microsoft Word Online where multiple users can edit simultaneously. The system must support documents of arbitrary size, handle 100+ concurrent editors, and deliver sub-100ms edit propagation — with the server as the authoritative brain that manages document structure.

## Clarifying Questions to Ask

1. **Concurrency**: How many simultaneous editors per document? (50? 100? 500?)
2. **Document size**: Is there a max document size, or should it be unbounded?
3. **Latency**: Max acceptable delay for seeing others' edits? (<100ms? <200ms?)
4. **Offline support**: Allow offline editing and sync later?
5. **Rich content**: Text only, or images, tables, formatting?
6. **Version history**: How long to retain? Granularity? (per-keystroke? per-save?)
7. **Structural operations**: Should the system auto-split paragraphs on Enter, auto-merge on Backspace at boundaries?
8. **Idempotency**: Must operations be safe to retry on network failure?

## Design Reasoning

> **Core philosophy**: The server is the **single source of truth** and the **brain** of the system. Clients are thin — they send raw "intents" (text changes), and the server decides all structural mutations (splitting blocks, merging blocks, deleting empty blocks). This ensures consistency is centrally enforced and simplifies client logic dramatically.

> **Why OT over CRDT?** CRDTs guarantee convergence without coordination, but they push complexity to every client (each must maintain full CRDT state with unique character IDs and tombstones). In a server-authoritative model, OT is a natural fit: the server defines a single global operation order, transforms stale operations against its log, and broadcasts the canonical result. Clients stay simple. The trade-off is that OT requires server connectivity for conflict resolution (no true offline merge), but the sync flow below handles reconnection gracefully. For a **decentralized CRDT approach** that works fully offline, see [Guide 10a](10a-design-collaborative-editing-crdt.md).

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Authority model | Server-authoritative (thin client) | Server decides all structural changes (split/merge/delete blocks); clients stay simple | Client-authoritative — every client needs full structural logic, harder to keep consistent |
| Conflict resolution | OT with server operations log | Server defines global order; transforms stale ops against log to preserve intent | CRDT — no central coordinator, but pushes merge complexity to every client |
| Document model | Block-based (paragraphs) | Natural editing units; enables lazy loading, partial updates, and targeted OT | Character-level — too granular for structure; section-level — too coarse for concurrent editing |
| Document hierarchy | Segments group blocks for OT ownership | Per-segment leader ensures total order; distributes OT load for large docs | Flat blocks — single leader bottleneck at 1000+ blocks |
| Block ordering | Fractional indexing | O(1) insertion anywhere without re-indexing other rows in the database | Integer positions — requires reindexing all subsequent blocks on insert |
| Hot-path transport | Redis Streams (fixed N partitions, hash(segmentId) % N) | ~2-5ms, decouples gateway from Collab Svc instances. Gateway has no routing logic — just writes to the partition. On failover, new owner picks up the same partitions with zero gateway reconfiguration | Direct gRPC to instance — leaks service discovery into gateway, tight coupling |
| Cold-path persistence | Event Hub + Cosmos DB (async) | Decouples persistence from real-time path; EH enables replay on failover | Direct DB writes — blocks the hot path; no replay capability |
| Broadcast channels | Per-WS-server Redis Pub/Sub | ~100 stable channels vs ~200K per-doc channels; targeted via Health Redis lookup | Per-document channels — too much metadata churn at scale |
| Control plane | REST/HTTP | Document CRUD, metadata, and structure (block ID list) are infrequent and cacheable | WebSocket for everything — unnecessary persistent connection for metadata reads |
| Data plane | WebSocket | Full-duplex, low-latency for streaming edit operations and receiving broadcasts | HTTP long-polling — too much overhead for per-keystroke collaboration |
| Client batching | Idle 500ms / 100 chars / max latency | Reduces server load without perceptible delay; three triggers cover all typing patterns | Per-keystroke — 10x more messages; fixed interval — misses burst/idle patterns |
| Retry safety | clientOpId (idempotency key) | On reconnect, client retries with same ID; server deduplicates | No idempotency — duplicate operations corrupt document state |
| Event model | Asymmetric (intent → structural events) | Client sends generic `edit`; server broadcasts specific `block.created`, `block.deleted` | Symmetric — clients must understand structural logic, violates thin-client principle |

## High-Level Design

```
 ═══════════════════════════════════════════════════════════════════════════
                   COLLABORATIVE EDITING — SERVER-AUTHORITY MODEL
 ═══════════════════════════════════════════════════════════════════════════

 ┌──────────┐   ┌──────────┐   ┌──────────┐
 │ Client A │   │ Client B │   │ Client C │       Thin clients: batch
 │  (thin)  │   │  (thin)  │   │  (thin)  │       keystrokes (500ms idle /
 └────┬─────┘   └────┬─────┘   └────┬─────┘       100 chars / 2s max),
      │              │              │              send raw intents
      │   WebSocket  │   (batched intents + broadcasts)
      ▼              ▼              ▼
 ┌──────────────────────────────────────────────────────────┐
 │                WS GATEWAY  (SignalR)                      │  Presence
 │   WS-1, WS-2 … WS-N  (auto-scaled by connections)       │◄── Service
 │   Each subscribes to Pub/Sub channel "ws:<serverId>"     │    (Redis)
 │   On doc open → registers in Health Redis                │
 │   On edit → appends to Redis Stream partition for the segment  │
 │   (hash(segmentId) % N — no knowledge of Collab Svc instances)│
 └──────────────────┬───────────────────────────────────────┘
                    │
                    ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │         REDIS STREAMS  (fixed N partitions, e.g. 32)                 │
 │                                                                      │
 │   hash(segmentId) % N → partition. Messages carry segmentId field.   │
 │   Fixed partition count — scales with infra, not with data.          │
 │                                                                      │
 │   Decouples WS Gateway from Collaboration Service:                   │
 │     • Gateway writes to partition hash(segmentId) % N — no need      │
 │       to know which Collab Svc instance owns the segment             │
 │     • Collab Svc consumer group: each instance claims partitions     │
 │       and filters messages by owned segmentIds                       │
 │     • On failover, rebalanced partitions are picked up by new        │
 │       instance — zero gateway reconfiguration                        │
 │   Also provides: durability + ordering within a partition            │
 └──────────────────┬───────────────────────────────────────────────────┘
                    │
                    ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │                                                                      │
 │                  COLLABORATION SERVICE  (OT Engine)                   │
 │                                                                      │
 │  Segment ownership: consistent hashing (ZooKeeper / etcd)            │
 │  segmentId → hash → exactly one Collab Svc instance owns it         │
 │                                                                      │
 │  On consuming from Redis Stream:                                     │
 │    1. OT transform against in-memory ops log                         │
 │    2. Handle structural changes (split/merge/delete blocks,          │
 │       assign fractional indices)                                     │
 │    3. Write transformed op to ──┬── Broadcast Service (hot path)     │
 │                                 └── Event Hub (cold path)            │
 │    4. ACK the Redis Stream message                                   │
 │                                                                      │
 └────────────────────┬─────────────────────┬───────────────────────────┘
                      │                     │
               hot path                cold path
                      │                     │
                      ▼                     ▼
 ┌───────────────────────────────┐  ┌──────────────────────────────────┐
 │     BROADCAST SERVICE         │  │        EVENT HUB                  │
 │                               │  │   Partitioned by docId, durable  │
 │  Look up Health Redis:        │  │   Replay source on failover      │
 │    doc:X → { WS-1, WS-3 }    │  └──────────────┬───────────────────┘
 │  Publish to Pub/Sub channels: │                 │
 │    ws:WS-1, ws:WS-3, ws:WS-7 │       ┌─────────┴─────────┐
 │  (~100 stable per-WS-server   │       ▼                   ▼
 │   channels, not ~200K per-doc)│  ┌────────────────┐ ┌─────────────────────┐
 └───────────────────────────────┘  │ PERSISTENCE SVC│ │  COMPACTION SERVICE  │
              │                     │                │ │                     │
              ▼                     │ Writes ops to  │ │ Every 500 ops →     │
 ┌─────────────────────────┐       │ Cosmos DB      │ │  snapshot to Blob   │
 │      Health Redis        │       │ (block content │ │ Prune old deltas    │
 │  doc → { WS-1, WS-3 }  │       │  + metadata)   │ │ Archive after 90d   │
 │  Broadcast routing      │       └───────┬────────┘ └──────────┬──────────┘
 └─────────────────────────┘               ▼                     ▼
                                ┌──────────────────┐ ┌──────────────────────┐
                                │  Document Store  │ │ BLOB STORAGE         │
                                │  (Cosmos DB)     │ │ (Snapshots)          │
                                │  Block content   │ │ Full state / 500 ops │
                                └──────────────────┘ │ Reconnection+history │
                                                     └──────────────────────┘

 SERVICE RESPONSIBILITIES (single-responsibility per service):
   WS Gateway      = Connections + write to Redis Stream (no routing logic)
   Collab Svc      = OT only (consumes from Redis Stream, in-memory ops log)
   Broadcast Svc   = Fanout only (Health Redis lookup → Pub/Sub publish)
   Persistence Svc = DB writes only (EH consumer → Cosmos DB)
   Compaction Svc  = Snapshots only (EH consumer → Blob Storage)

 FAILOVER: When a Collab Svc instance crashes, ZooKeeper reassigns
   segments. New owner starts consuming from the same Redis Stream —
   zero reconfiguration at the WS Gateway. Replays EH from last
   checkpoint to rebuild in-memory ops log. Unacked Redis Stream
   messages are reprocessed. Clients retry with clientOpId → dedup.
```

### Control Plane vs Data Plane

```
CONTROL PLANE (REST/HTTP) — Infrequent, cacheable:
  ┌──────────────────────────────────────────────────────────┐
  │  POST /documents                → Create document         │
  │  GET  /documents/{id}           → Metadata (+ ETag)       │
  │  GET  /documents/{id}/blocks/structure                    │
  │                                 → Ordered block ID list   │
  │  GET  /documents/{id}/blocks?after={blockId}&limit=10     │
  │                                 → Block content (paginate)│
  │  GET  /documents/{id}/blocks?aroundPercent=30&limit=10    │
  │                                 → Scroll-to-position      │
  │  POST /documents/{id}/sessions  → WebSocket handshake     │
  └──────────────────────────────────────────────────────────┘

DATA PLANE (WebSocket) — Continuous, real-time:
  ┌──────────────────────────────────────────────────────────┐
  │  Client → Server:  document.edit (batched text changes)   │
  │                    document.liveness (cursor + heartbeat) │
  │  Server → Client:  document.doc.changed (structural +     │
  │                      content events in one ordered stream)│
  │                    document.edit.ack / document.edit.error│
  └──────────────────────────────────────────────────────────┘

KEY DESIGN DECISION: ETag vs Revision
  ┌──────────────────────────────────────────────────────────┐
  │  ETag:     HTTP-level cache header. Used for conditional  │
  │            GET (If-None-Match → 304 Not Modified).        │
  │            Avoids re-downloading unchanged metadata.      │
  │                                                          │
  │  Revision: App-level version number. Used for OT conflict │
  │            resolution (baseRevision in edits) and sync    │
  │            handshake (lastSeenRevision on reconnect).     │
  │                                                          │
  │  They serve different layers and BOTH are needed.        │
  └──────────────────────────────────────────────────────────┘
```

## API Design

### Document APIs (Control Plane — REST)
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/documents` | Create a new document. Include `Idempotency-Key` header for safe retries |
| `GET` | `/documents/{id}` | Get metadata only. Returns ETag for conditional caching |
| `DELETE` | `/documents/{id}` | Delete a document. Returns 204 / 404 |
| `GET` | `/documents/{id}/blocks/structure?after={blockId}&limit={n}` | Paginated block structure (IDs + fracIndex, no content) |
| `GET` | `/documents/{id}/blocks?after={blockId}&limit={n}` | Paginated block content. `after` is cursor-based pagination |
| `GET` | `/documents/{id}/blocks?aroundPercent={pct}&limit={n}` | Scroll-to-position: returns blocks around the Nth percentile of total blocks. Mutually exclusive with `after` |
| `GET` | `/documents/{id}/blocks/{blockId}` | Get single block metadata and text |
| `POST` | `/documents/{id}/sessions` | Create a WebSocket session. Returns `wsUrl` + `revision` + initial structure |
| `GET` | `/documents/{id}/history?limit={n}&before={revision}` | Paginated version history |
| `POST` | `/documents/{id}/share` | Share document with a user (editor/viewer) |
| `GET` | `/documents/{id}/collaborators` | List collaborators with online status and cursor location |

### Request / Response Examples

```json
// POST /documents
// Header: Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
{
  "name": "Project Plan",
  "description": "Q2 planning document",
  "metadata": { "team": "backend" }
}
// Response 201:
{
  "id": "doc123",
  "revision": 0,
  "etag": "W/\"0\""
}

// GET /documents/doc123
// Response 200 (or 304 if ETag matches):
{
  "id": "doc123",
  "name": "Project Plan",
  "description": "Q2 planning document",
  "blockCount": 247,
  "revision": 342,
  "etag": "W/\"342\"",
  "lastUpdateTime": "2024-01-15T14:30:00Z"
}

// GET /documents/doc123/blocks/structure?limit=5
// Response:
{
  "revision": 342,
  "blocks": [
    { "blockId": "b1", "blockRevision": 12, "fracIndex": "a" },
    { "blockId": "b2", "blockRevision": 45, "fracIndex": "b" },
    { "blockId": "b3", "blockRevision": 89, "fracIndex": "n" },
    { "blockId": "b4", "blockRevision": 23, "fracIndex": "t" },
    { "blockId": "b5", "blockRevision": 67, "fracIndex": "u" }
  ]
}

// POST /documents/doc123/sessions
// Response:
{
  "wsUrl": "wss://collab.example.com/ws/doc123?token=...",
  "revision": 342,
  "blocks": [
    { "blockId": "b1", "blockRevision": 12 },
    { "blockId": "b2", "blockRevision": 45 },
    ...
  ]
}
```

### WebSocket Events (Data Plane)

| Event | Direction | Payload |
|-------|-----------|---------|
| `document.liveness` | Client → Server | `{ docId, docRevision, blockId, cursorPosition, selection }` |
| `document.edit` | Client → Server | `{ docId, clientOpId, baseRevision, changes[] }` |
| `document.edit.ack` | Server → Client | `{ docId, clientOpId, docRevision, blockRevisions[] }` |
| `document.edit.error` | Server → Client | `{ docId, clientOpId, code, message }` |
| `document.doc.changed` | Server → Clients | `{ docRevision, changes[] }` |

### WebSocket Event Payloads

```json
// Client → Server: document.edit (batched changes)
{
  "docId": "doc123",
  "clientOpId": "a1b2c3d4",
  "baseRevision": 247,
  "changes": [
    {
      "blockId": "b5",
      "blockRevision": 67,
      "position": 12,
      "type": "insert",
      "text": "Hello\nWorld"
    }
  ]
}

// Server → Client: document.edit.ack
{
  "docId": "doc123",
  "clientOpId": "a1b2c3d4",
  "docRevision": 342,
  "blockRevisions": [
    { "blockId": "b5", "blockRevision": 68 },
    { "blockId": "b8", "blockRevision": 1 }
  ]
}

// Server → Clients: document.doc.changed
// (structural + content events in one ordered stream)
{
  "docRevision": 342,
  "changes": [
    {
      "blockId": "b5", "blockRevision": 68,
      "position": 12,
      "op": { "type": "delete", "len": 15 }
    },
    {
      "blockId": "b8", "blockRevision": 1,
      "op": { "type": "blockCreated", "fracIndex": "un",
              "text": "World" }
    }
  ]
}

// Server → Client: document.edit.error
// Codes: STALE_REVISION, PERMISSION_DENIED, INVALID_OP
// NOTE: No BLOCK_GONE — server translates edits on deleted
//       blocks to the surviving block (see Edge Cases).
{
  "docId": "doc123",
  "clientOpId": "a1b2c3d4",
  "code": "STALE_REVISION",
  "message": "baseRevision 100 is too old; ops log compacted past it. Full resync required."
}
```

### Server Processes the Intent (Example)

```
User types "Hello\nWorld" at position 12 in block_5.
Client just sends the raw text change (thin client).

  Server receives the edit and detects \n:
  1. Truncates block_5 content at position 12     → block.updated
  2. Creates block_8 with remaining text "World"  → block.created
  3. Assigns fracIndex "un" to block_8 (between b5="u" and b6="v")
  4. Broadcasts document.doc.changed with both events in order
  5. Sends document.edit.ack to the originating client

  Client never needs split/merge logic — it just applies the events.
```

## Key Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| WebSocket Gateway | Persistent full-duplex connections; subscribes to per-server Pub/Sub channel; resolves segment owner from hash ring | Azure SignalR Service |
| Collaboration Service | OT engine. Consumes from Redis Streams for owned segments, transforms, writes to Broadcast Svc + Event Hub. Per-segment leader via consistent hashing | Custom service |
| Broadcast Service | Fanout only. Looks up Health Redis (doc → WS servers), publishes transformed ops to per-WS-server Pub/Sub channels | Custom service |
| Event Hub | Durable ordered history of all accepted ops (cold path). Partitioned by docId. Replay source on Collab Svc failover | Azure Event Hub |
| Persistence Service | EH consumer: writes accepted ops to Document Store (block content + metadata) | Custom service |
| Compaction Service | EH consumer: counts ops, snapshots every 500, prunes old deltas, archives after 90d | Custom service |
| Document Store | Block content, metadata, and structure | Cosmos DB |
| Health Redis | Maps docId → set of WS server IDs. Used by Broadcast Svc for targeted fanout | Redis |
| Broadcast Pub/Sub | Per-WS-server channels (~100 stable) for pushing transformed ops to gateway instances | Redis Pub/Sub |
| Redis Streams | Fixed N partitions (e.g. 32). WS Gateway appends with segmentId field; Collab Svc consumer group claims partitions, filters by owned segments | Redis Streams |
| Session / Presence Store | Active editors, cursor positions, heartbeats, connection routing | Redis |
| Coordination Service | Consistent hash ring for segment → Collab Svc instance mapping | ZooKeeper / etcd |
| Blob Storage | Full block-state snapshots for reconnection and version history | Azure Blob / S3 |

## Block-Based Document Model

```
DOCUMENT AS AN ORDERED LIST OF BLOCKS:

  ┌─────────────────────────────────────────────────┐
  │  Document: "Project Plan" (doc123)               │
  │  headRevision: 342                               │
  │                                                  │
  │  Structure (ordered by fractional index):        │
  │                                                  │
  │  fracIndex │ blockId  │ type      │ content      │
  │  ──────────┼──────────┼───────────┼────────────  │
  │  "a"       │ block_1  │ heading   │ "Project P.."│
  │  "b"       │ block_2  │ paragraph │ "This doc.." │
  │  "n"       │ block_3  │ paragraph │ "The budge.."│
  │  "t"       │ block_4  │ heading   │ "Timeline"   │
  │  "u"       │ block_5  │ paragraph │ "Phase 1:.." │
  │  "v"       │ block_6  │ paragraph │ "Phase 2:.." │
  └─────────────────────────────────────────────────┘

  Each block ≈ one paragraph. Blocks are the unit of:
  • Editing (OT operates within a single block)
  • Loading (fetch blocks on demand by viewport)
  • Locking (at most, lock one block during transform)
```

### Why Blocks, Not Characters or Sections?

| Granularity | Pros | Cons |
|-------------|------|------|
| **Character-level** | Maximum concurrency | Massive overhead; every char needs metadata |
| **Block (paragraph)** ✓ | Natural editing unit; good lazy-load boundary; OT scope is small | Cross-block edits need server coordination (split/merge) |
| **Section-level** | Fewer units to manage | Too coarse — locks out concurrent editors in same section |

### Scaling Beyond Blocks: The Segment Layer

```
For small documents (<100 blocks), a single Collaboration Service
instance handles the entire document. But for large documents
(1000+ blocks), OT on a single instance becomes a bottleneck.

SOLUTION: Add a "segment" layer between documents and blocks.

  DOCUMENT HIERARCHY:
  ┌───────────────────────────────────────────────────────┐
  │  Document                                             │
  │  ├── Segment 0  (blocks 1-50)    → Collab Svc A      │
  │  ├── Segment 1  (blocks 51-100)  → Collab Svc B      │
  │  ├── Segment 2  (blocks 101-150) → Collab Svc A      │
  │  └── Segment 3  (blocks 151-200) → Collab Svc C      │
  └───────────────────────────────────────────────────────┘

  Each segment is owned by exactly ONE Collab Svc instance
  (assigned via consistent hashing on segmentId).
  OT ordering is guaranteed within a segment because all
  edits flow through a single leader.

WHEN TO USE SEGMENTS:
  • Small doc (< 100 blocks): 1 segment = entire document
  • Medium doc (100-500 blocks): split into ~5-10 segments
  • Large doc (1000+ blocks): split into ~20+ segments

  Segments are transparent to clients — they only see blocks.
  The server maps blockId → segmentId internally.
```

| Aspect | Single-segment (simple) | Multi-segment (large docs) |
|--------|------------------------|---------------------------|
| **OT scope** | One instance handles all blocks | Each instance handles ~50 blocks |
| **Throughput** | ~5K ops/sec per doc | ~5K ops/sec per segment (linear scale) |
| **Cross-segment edits** | N/A | Rare — block split/merge near boundaries requires coordination |
| **Complexity** | Minimal | Need consistent hash ring + segment rebalancing |

## Fractional Indexing

```
PROBLEM: How to insert a block between two others without
         re-indexing every block after the insertion point?

SOLUTION: Lexicographic string keys that allow O(1) insertion.

  Initial:
    "a" → Block 1
    "b" → Block 2
    "c" → Block 3

  Insert between Block 1 and Block 2:
    "a"  → Block 1
    "an" → Block NEW   ← lexicographically between "a" and "b"
    "b"  → Block 2
    "c"  → Block 3

  Insert between Block 1 and Block NEW:
    "a"   → Block 1
    "ag"  → Block NEW2  ← between "a" and "an"
    "an"  → Block NEW
    "b"   → Block 2

HOW THE MATH WORKS:
  ┌──────────────────────────────────────────────────────────┐
  │  midpoint("a", "b") = "an"   (midpoint of 'a'..'z')     │
  │  midpoint("a", "an") = "ag"  (midpoint of 'a'..'n')     │
  │  midpoint("ag", "an") = "aj" (midpoint of 'g'..'n')     │
  │                                                          │
  │  Worst case: after many insertions at the same spot,     │
  │  keys grow longer (e.g., "annnn"). Periodic rebalancing  │
  │  reassigns short keys when document is idle.             │
  └──────────────────────────────────────────────────────────┘

  DATABASE QUERY:
    SELECT * FROM blocks
    WHERE doc_id = 'doc123'
    ORDER BY frac_index ASC;

  → Returns blocks in correct document order.
  → Inserting a new block is a single INSERT — no UPDATEs.
```

## Server-Side Authority (Thin Client Model)

```
THE SERVER IS THE "BRAIN":

  Client (Thin)                    Server (Authority)
  ┌────────────────┐               ┌──────────────────────┐
  │ User types:    │               │                      │
  │ "Hello\nWorld" │──── intent ──▶│ 1. Receive intent    │
  │                │               │ 2. Detect \n         │
  │                │               │ 3. DECIDE: split     │
  │                │               │    block_5 into two  │
  │                │               │ 4. Assign fracIndex  │
  │                │               │    for new block     │
  │                │               │ 5. Transform against │
  │                │               │    ops log if stale  │
  │                │               │ 6. Write to DB       │
  │                │               │ 7. Append to ops log │
  │                │◀── events ────│ 8. Broadcast:        │
  │ Apply events:  │               │    block.updated     │
  │ • update blk 5 │               │    block.created     │
  │ • create blk 6 │               │                      │
  └────────────────┘               └──────────────────────┘

STRUCTURAL OPERATIONS (server decides):
  ┌────────────────────────────────────────────────────────┐
  │ User action        │ Server decision                   │
  │────────────────────┼───────────────────────────────────│
  │ Types \n (Enter)   │ Split block → block.created       │
  │ Backspace at start │ Merge with prev → block.deleted   │
  │ Deletes all text   │ Delete empty → block.deleted      │
  │ Pastes multi-line  │ Multiple splits → N block.created │
  │ Types normally     │ Update content → block.updated    │
  └────────────────────────────────────────────────────────┘

WHY THIN CLIENT?
  • Client logic is trivial: capture keystrokes, send intent, apply events
  • All structural complexity lives on the server (one place to fix bugs)
  • Clients in different platforms (web, iOS, Android) stay simple
  • Server can enforce business rules (max block size, formatting rules)
```

## Buffering & Batching

```
PROBLEM: Sending every keystroke is wasteful.
         Waiting too long feels laggy.

SOLUTION: Three-trigger batching system.

  ┌──────────────────────────────────────────────────────────┐
  │  Client Input Buffer                                      │
  │                                                           │
  │  Trigger 1: IDLE TIME (500ms)                             │
  │    User stops typing for 500ms → flush buffer             │
  │    Covers: normal typing with natural pauses              │
  │                                                           │
  │  Trigger 2: BUFFER SIZE (100 characters)                  │
  │    Buffer reaches 100 chars → flush immediately           │
  │    Covers: fast continuous typing or paste                 │
  │                                                           │
  │  Trigger 3: MAX LATENCY (2 seconds)                       │
  │    Hard cap — flush no matter what after 2s               │
  │    Covers: edge case of slow, continuous typing            │
  │                                                           │
  │  Whichever trigger fires first → send the batch.          │
  └──────────────────────────────────────────────────────────┘

  Timeline example (fast typist):
  ──────────────────────────────────────────────▶ time
  │ k │ k │ k │ k │ ... │ k │     │ k │ k │
  0ms                 100 chars  500ms idle
                       ▲ FLUSH    ▲ FLUSH
                       (trigger 2) (trigger 1)
```

## Idempotency

```
PROBLEM: WebSocket disconnects. Client retries.
         Server may receive the same operation twice.

SOLUTION: Every operation carries a unique clientOpId.

  ┌───────────┐                          ┌───────────┐
  │  Client   │─── edit { clientOpId:    │  Server   │
  │           │    "abc-123", ... } ────▶│           │
  │           │                          │ Process ✓ │
  │           │    (connection drops)     │ Store in  │
  │           │                          │ ops log   │
  │           │─── edit { clientOpId:    │           │
  │           │    "abc-123", ... } ────▶│ Dup check │
  │           │                          │ → SKIP ✓  │
  │           │◀── ack { clientOpId:     │           │
  │           │    "abc-123", rev: 248 } │           │
  └───────────┘                          └───────────┘

  Server dedup check:
    if ops_log.contains(clientOpId):
      return existing ack (idempotent)
    else:
      process operation normally
```

## Asymmetric Events

```
WHY ASYMMETRIC?

  The client doesn't know (or care) about document structure.
  It just says "I typed this text at this position."
  The server interprets the intent and tells ALL clients what
  structurally happened.

  ┌──────────────────────────────────────────────────────────┐
  │  CLIENT sends:                                           │
  │    ONE event type: "edit"                                │
  │    { blockId, delta, clientOpId, baseRevision }          │
  │                                                          │
  │  SERVER broadcasts:                                      │
  │    MANY event types depending on what happened:          │
  │    • block.updated  — content changed within a block     │
  │    • block.created  — new block split off (user hit \n)  │
  │    • block.deleted  — block removed (emptied or merged)  │
  │    • block.moved    — block reordered (drag-and-drop)    │
  └──────────────────────────────────────────────────────────┘

  Example — User A presses Enter in the middle of a paragraph:

  Client A sends:
    { type: "edit", blockId: "b5", delta: { insert: "\n", pos: 20 } }

  Server broadcasts to ALL clients (including A):
    1. { type: "block.updated", blockId: "b5",
         delta: { truncate: 20 }, revision: 248 }
    2. { type: "block.created", blockId: "b7",
         fracIndex: "un", content: "...remaining text...",
         revision: 249 }

  Client A (and B, C) simply apply these events.
  No client needs split/merge logic.
```

## Operational Transformation (OT) — Server-Side

```
THE OT ENGINE:

  The server maintains an ordered Operations Log.
  When a client's operation arrives with a stale baseRevision,
  the server transforms it against all ops that happened since.

  ┌──────────────────────────────────────────────────────────┐
  │  Operations Log (server):                                │
  │                                                          │
  │  rev 245: User B insert "X" at pos 5 in block_3         │
  │  rev 246: User C delete pos 8-10 in block_3             │
  │  rev 247: User B insert "Y" at pos 12 in block_3        │
  │                                                          │
  │  Incoming from User A (was offline since rev 244):       │
  │  { baseRevision: 244, insert "Z" at pos 7 in block_3 }  │
  │                                                          │
  │  Server transforms A's op against 245, 246, 247:         │
  │                                                          │
  │  T(insert(7), insert(5))  → insert(8)  (shifted +1)     │
  │  T(insert(8), delete(8,10))→ insert(8)  (adjusted)      │
  │  T(insert(8), insert(12)) → insert(8)  (no change)      │
  │                                                          │
  │  Final: insert "Z" at pos 8 → rev 248                   │
  └──────────────────────────────────────────────────────────┘

  KEY INSIGHT: OT only operates WITHIN a single block.
  Cross-block structural changes (split/merge) are handled
  by the Structure Service, not OT. This keeps OT simple.
```

### Transformation Functions

```
  ┌──────────────────────────────────────────────────────────┐
  │  T(insert(pos_a), insert(pos_b)):                        │
  │    pos_a < pos_b  → insert(pos_a)        (no change)    │
  │    pos_a >= pos_b → insert(pos_a + 1)    (shift right)  │
  │                                                          │
  │  T(insert(pos_a), delete(pos_b)):                        │
  │    pos_a <= pos_b → insert(pos_a)        (no change)    │
  │    pos_a > pos_b  → insert(pos_a - 1)    (shift left)   │
  │                                                          │
  │  T(delete(pos_a), insert(pos_b)):                        │
  │    pos_a < pos_b  → delete(pos_a)        (no change)    │
  │    pos_a >= pos_b → delete(pos_a + 1)    (shift right)  │
  │                                                          │
  │  T(delete(pos_a), delete(pos_b)):                        │
  │    pos_a < pos_b  → delete(pos_a)        (no change)    │
  │    pos_a > pos_b  → delete(pos_a - 1)    (shift left)   │
  │    pos_a == pos_b → noop                 (already gone)  │
  │                                                          │
  │  CONVERGENCE PROPERTY:                                   │
  │    apply(A, T(B, A)) == apply(B, T(A, B))               │
  │    Both orderings produce the same final state.          │
  └──────────────────────────────────────────────────────────┘
```

## Local Reconciliation (Client-Side OT)

```
PROBLEM: The user is typing while the server broadcasts
         other users' changes. Naively applying server
         updates would cause cursor jumps and text flicker.

SOLUTION: The client maintains THREE operation states.
         The OT merge runs in a Web Worker to avoid blocking
         the UI thread (no jank during heavy reconciliation).

  NOTE ON OT DIRECTION: T(A, B) means "transform A assuming B
  has already been applied." This is NOT commutative — order matters.

  NOTE ON FLOW CONTROL: The client uses stop-and-wait — only ONE
  batch can be in-flight at a time. PENDING ops wait until the
  current in-flight batch is ACK'd before being promoted and sent.

  ┌─────────────────────────────────────────────────────────┐
  │  CLIENT STATE MODEL:                                     │
  │                                                          │
  │  ┌──────────────┐                                        │
  │  │  CONFIRMED   │  Base state — matches server.          │
  │  │  (rev 245)   │  All ops acknowledged by server.       │
  │  └──────┬───────┘                                        │
  │         │                                                │
  │         ▼                                                │
  │  ┌──────────────┐                                        │
  │  │  IN-FLIGHT   │  Sent to server, waiting for ACK.      │
  │  │  (1 batch)   │  Only ONE batch in-flight at a time.   │
  │  └──────┬───────┘                                        │
  │         │                                                │
  │         ▼                                                │
  │  ┌──────────────┐                                        │
  │  │   PENDING    │  Buffered locally, not yet sent.       │
  │  │  (0-N ops)   │  Sent after in-flight is ACK'd.       │
  │  └──────────────┘                                        │
  │                                                          │
  │  What the user sees = CONFIRMED + IN-FLIGHT + PENDING    │
  └─────────────────────────────────────────────────────────┘

WHEN SERVER BROADCASTS AN UPDATE (from another user):
  ┌──────────────────────────────────────────────────────────┐
  │  Let S = server op, F = in-flight ops, P = pending ops.  │
  │                                                          │
  │  1. S' = T(S, F)   — transform S assuming F was applied  │
  │     F' = T(F, S)   — also update F for when ACK arrives  │
  │                                                          │
  │  2. S'' = T(S', P)  — transform S' assuming P was applied│
  │     P'  = T(P, S')  — also update P so it stays valid    │
  │                                                          │
  │  3. Apply S'' to local view                              │
  │     → No cursor jump! No flicker! User doesn't notice.  │
  └──────────────────────────────────────────────────────────┘

EXAMPLE:
  Confirmed: "HELLO"
  In-flight: insert("X", pos=2) → user sees "HEXLLO"
  Server broadcasts: insert("Y", pos=4) from User B

  Step 1: T(insert("Y",4), insert("X",2)) → insert("Y",5)
          (shifted because our in-flight inserted before pos 4)
  Step 2: No pending ops → insert("Y",5) unchanged
  Step 3: Apply to local: "HEXLLO" → "HEXLYLO"

  When ACK arrives for in-flight:
    Confirmed = "HEXLYLO", In-flight = empty
    Promote PENDING to IN-FLIGHT and send to server.
```

## Sync Flow (Offline / Reconnection)

```
RECONNECTION HANDSHAKE:

  ┌─────────────┐                           ┌──────────────┐
  │   Client    │                           │    Server    │
  │             │                           │              │
  │ lastSeen:   │──── handshake ───────────▶│ headRevision │
  │ rev 200     │  { lastSeenRevision: 200 }│ rev 342      │
  │             │                           │              │
  │             │◀── response ──────────────│ { head: 342, │
  │             │  { status: "stale",       │   delta: 142 │
  │             │    headRevision: 342 }    │   ops behind}│
  └──────┬──────┘                           └──────────────┘
         │
         │  Client knows it's 142 revisions behind.
         │  Executes 4-step sync:
         ▼

  STEP 1: PUSH — Send offline deltas to server
  ┌──────────────────────────────────────────────────────────┐
  │  Client pushes all accumulated offline operations.       │
  │  Each has a clientOpId → server deduplicates.            │
  │  Server transforms against ops log (OT) to resolve      │
  │  conflicts with the 142 ops that happened while offline. │
  └──────────────────────────────────────────────────────────┘
         │
         ▼
  STEP 2: PULL STRUCTURE — Fetch updated block list (HTTP)
  ┌──────────────────────────────────────────────────────────┐
  │  GET /api/v1/documents/{docId}/structure                 │
  │  → Returns: ordered list of block IDs + fractional       │
  │    indices. Client now knows which blocks exist,         │
  │    which were created/deleted while offline.             │
  └──────────────────────────────────────────────────────────┘
         │
         ▼
  STEP 3: LAZY LOAD VIEWPORT — Fetch visible blocks first
  ┌──────────────────────────────────────────────────────────┐
  │  GET /api/v1/documents/{docId}/blocks?ids=b3,b4,b5      │
  │  → Fetch content for blocks currently in the viewport.   │
  │  User sees updated content immediately.                  │
  └──────────────────────────────────────────────────────────┘
         │
         ▼
  STEP 4: BACKGROUND FILL — Load remaining blocks
  ┌──────────────────────────────────────────────────────────┐
  │  Progressively fetch remaining blocks in the background. │
  │  Blocks outside viewport show skeleton/placeholder       │
  │  until loaded. User can start editing immediately.       │
  └──────────────────────────────────────────────────────────┘
```

## Data Model

```json
// Document Metadata (Control Plane)
{
  "doc_id": "doc123",
  "title": "Project Plan",
  "owner_id": "user456",
  "head_revision": 342,
  "collaborators": [
    { "user_id": "user789", "role": "editor" },
    { "user_id": "user012", "role": "viewer" }
  ],
  "created_at": "2024-01-15T10:00:00Z",
  "updated_at": "2024-01-15T14:30:00Z"
}

// Block (loaded on demand)
{
  "block_id": "block_5",
  "doc_id": "doc123",
  "type": "paragraph",
  "frac_index": "u",
  "content": "Phase 1: Requirements gathering and initial design.",
  "version": 89,
  "created_at": "2024-01-15T10:05:00Z"
}

// Operation (append-only log entry)
{
  "revision": 248,
  "doc_id": "doc123",
  "block_id": "block_5",
  "user_id": "user456",
  "client_op_id": "a1b2c3d4",
  "base_revision": 244,
  "op_type": "insert",
  "payload": { "position": 8, "text": "Z" },
  "server_timestamp": "2024-01-15T14:30:00.123Z"
}

// Structure (cached, returned by /structure endpoint)
{
  "doc_id": "doc123",
  "head_revision": 342,
  "blocks": [
    { "block_id": "block_1", "frac_index": "a", "type": "heading" },
    { "block_id": "block_2", "frac_index": "b", "type": "paragraph" },
    { "block_id": "block_3", "frac_index": "n", "type": "paragraph" },
    { "block_id": "block_4", "frac_index": "t", "type": "heading" },
    { "block_id": "block_5", "frac_index": "u", "type": "paragraph" },
    { "block_id": "block_6", "frac_index": "v", "type": "paragraph" }
  ]
}
```

## Scalability Considerations

| Aspect | Strategy | Numbers |
|--------|----------|---------|
| **Concurrent editors** | Per-segment leader via consistent hashing; all edits for a segment → one Collab Svc instance | Up to ~500 editors per document session |
| **Document size** | Block-based lazy loading; segments distribute OT across instances | Unlimited — 1000-block doc ≈ 20 segments, ~200KB client RAM |
| **Operations log** | Event Hub partitioned by docId (cold path, async after OT) | ~100 bytes/op; 10K ops/doc ≈ 1 MB |
| **OT transform cost** | OT scoped to single block (small text); log compacted every 500 ops | Transform against ≤500 ops (~5ms); amortized by batching |
| **Block count** | Fractional indexing; no re-index on insert | O(1) insert; periodic rebalance on idle |
| **Connection limits** | Azure SignalR Service manages WebSocket connections | ~100K connections per unit; auto-scale |
| **Broadcast channels** | Per-WS-server Pub/Sub channels (not per-document); targeted via Health Redis | ~100 fixed channels vs ~200K per-doc channels |
| **Hot-path transport** | Redis Streams (fixed partitions): WS Gateway appends to hash(segmentId) % N, Collab Svc consumer group consumes | ~2-5ms; decouples gateway from Collab Svc instances |
| **Batching** | 500ms idle / 100 chars / 2s max | ~2 KB/s per active typist (vs 10 KB/s per-keystroke) |
| **Global latency** | Single-leader per doc (primary region); multi-region Cosmos DB for reads | <100ms same-region, <300ms cross-region |

## Storage & Scaling (Log Compaction)

```
THE STORAGE PROBLEM:
  Keeping every single keystroke-level delta forever
  would lead to infinite database growth.

THE SOLUTION: Checkpointing, Snapshotting, and Pruning.

  ┌──────────────────────────────────────────────────────────┐
  │  THREE STORAGE TIERS:                                    │
  │                                                          │
  │  1. HOT STORAGE (Deltas)                                 │
  │     Where: Cassandra / DynamoDB                          │
  │     What:  Recent operations (deltas) for each block     │
  │     TTL:   Until next snapshot is created                │
  │     Speed: Sub-ms reads for real-time OT transforms      │
  │                                                          │
  │  2. WARM STORAGE (Snapshots)                             │
  │     Where: Blob Storage (S3 / Azure Blob)                │
  │     What:  Full block state at a point-in-time revision  │
  │     When:  Every X revisions (e.g., every 500 ops)       │
  │            or every Y minutes (e.g., every 10 min)       │
  │     Speed: ~50ms reads for reconnection / time-travel    │
  │                                                          │
  │  3. COLD STORAGE (Archive)                               │
  │     Where: S3 Glacier / Azure Archive                    │
  │     What:  Old snapshots + pruned deltas for audit trail │
  │     When:  After retention window (e.g., 90 days)        │
  │     Speed: Minutes to hours (acceptable for compliance)  │
  └──────────────────────────────────────────────────────────┘
```

### Snapshot & Pruning Lifecycle

```
  Timeline:
  ──────────────────────────────────────────────────────────▶
  │           │           │           │           │
  rev 0     rev 500     rev 1000    rev 1500    rev 1847
  SNAPSHOT    SNAPSHOT    SNAPSHOT    SNAPSHOT    (current)
  (full       (full       (full       (full
  block       block       block       block
  state)      state)      state)      state)
                           │                     │
                           │  Deltas rev 1001─1847
                           │  (kept in HOT storage)
                           │
                           └── Deltas rev 501─1000
                               PRUNED from hot storage
                               after snapshot at rev 1000

  COMPACTION PROCESS (triggered every X revisions):
  ┌──────────────────────────────────────────────────────────┐
  │  1. Server reaches revision threshold (e.g., 500 new ops)│
  │  2. Merge all deltas since last snapshot into current    │
  │     block state → create new Block Snapshot              │
  │  3. Write snapshot to Blob Storage (warm)                │
  │  4. DELETE old deltas from Hot Storage (Cassandra)       │
  │  5. Move previous snapshot to Cold Storage if past       │
  │     retention window                                     │
  │                                                          │
  │  Result: Hot storage stays small (≤500 deltas per block) │
  │  OT transforms are fast (log never grows unbounded)      │
  └──────────────────────────────────────────────────────────┘

  RECONNECTION WITH SNAPSHOTS:
    Client was at rev 200, server is at rev 1847.
    1. Load snapshot at rev 1500 (blob storage)      ~50ms
    2. Replay deltas rev 1501–1847 (hot storage)     ~5ms
    3. Transform client's offline ops against those  ~1ms
    Total: ~56ms — much faster than replaying 1647 ops
```

## Summary of Responsibilities

| Component | Primary Responsibility |
|-----------|----------------------|
| **Server** | Managing Block structure (split/merge/delete), Global Conflict Resolution (OT against ops log), Snapshotting & Log Compaction, Broadcasting structural events |
| **Client** | Optimistic UI rendering, Local Reconciliation (merging server updates into active text via Web Worker), Lazy Loading (viewport-first block fetching), Buffering & Batching keystrokes |

## Edge Cases & Failure Modes

### Cursor Position After Block Split

```
PROBLEM: User hits Enter → server splits block. Where does the cursor go?

  Before:  block_5 = "Hello|World"   (cursor at position 5)
  User hits Enter at position 5.

  Server broadcasts:
    1. block.updated  { blockId: "block_5", content: "Hello" }
    2. block.created  { blockId: "block_8", fracIndex: "un",
                        content: "World",
                        cursorHint: { fromBlock: "block_5", position: 0 } }
                        ▲
                        The cursorHint tells the originating client
                        to move cursor to position 0 of the new block.

  Non-originating clients: cursor stays in whichever block they were in.
  Originating client: uses cursorHint to jump to start of new block.
```

### In-Flight Operations on a Deleted Block

```
PROBLEM: User A is editing block_5. User B deletes block_5
         (backspaces at start → merges b5 into b4).
         A's in-flight operation arrives at server for a dead block.

  ┌──────────────────────────────────────────────────────────┐
  │  Server receives: edit { blockId: "b5", pos: 3, ... }   │
  │  Server checks: b5 exists? NO → merged into b4.         │
  │                                                          │
  │  Server TRANSLATES (not rejects):                        │
  │    b4 had 50 chars before merge. b5 content was appended.│
  │    A's position 3 in b5 = position 53 in b4 (50 + 3).   │
  │    Server applies: insert at pos 53 in b4.               │
  │                                                          │
  │  Server broadcasts:                                      │
  │    document.doc.changed { blockId: "b4", pos: 53, ... }  │
  │                                                          │
  │  Server sends ACK to A:                                  │
  │    document.edit.ack { clientOpId, docRevision,           │
  │      blockRevisions: [{blockId: "b4", blockRevision: X}] │
  │    }                                                     │
  │    Note: ack references b4, not b5 — A's client updates  │
  │    its state accordingly.                                │
  └──────────────────────────────────────────────────────────┘

  TRANSLATION TABLE:
  ┌────────────────────────────────────────────────────────┐
  │ Merge type            │ Position remapping             │
  │───────────────────────┼────────────────────────────────│
  │ b5 merged INTO b4     │ pos_in_b5 → len(b4) + pos     │
  │ (backward: backspace) │ b5's content appended to b4    │
  │                       │                                │
  │ b5 merged INTO b6     │ pos_in_b5 → pos               │
  │ (forward: delete key) │ b5's content prepended to b6   │
  │                       │                                │
  │ b5 fully deleted      │ Server recreates a block at    │
  │ (all text removed)    │ b5's position and applies edit │
  └────────────────────────────────────────────────────────┘

  WHY TRANSLATE, NOT REJECT?
  The server is the brain. Rejecting an edit because a block
  was deleted means User A loses what they were typing — bad UX.
  The server knows WHERE b5's content went, so it remaps the
  operation transparently. The client stays thin.
```

### Simultaneous Block Splits

```
PROBLEM: User A and B both hit Enter in block_5 at different positions.
         Both operations arrive at the server with the same baseRevision.

  User A: Enter at position 10  (baseRevision: 200)
  User B: Enter at position 20  (baseRevision: 200)

  SERVER RESOLUTION (sequential processing):
  ┌──────────────────────────────────────────────────────────┐
  │  1. Process A's split first (arrived first via Redis     │
  │     ordering within partition):                          │
  │     block_5 = "Hello.....World.....End"                  │
  │                       ▲pos 10                            │
  │     → block_5 = "Hello....." (chars 0-9)                 │
  │     → block_9 = "World.....End" (chars 10+)              │
  │     Revision: 201                                        │
  │                                                          │
  │  2. Process B's split (baseRevision 200, now stale):     │
  │     OT transforms B's position: Enter at pos 20          │
  │     Since A split at pos 10, B's pos 20 is now pos 10   │
  │     in the NEW block_9 (20 - 10 = 10)                   │
  │     → block_9 = "World....." (chars 0-9)                 │
  │     → block_10 = "End" (chars 10+)                       │
  │     Revision: 202                                        │
  │                                                          │
  │  Result: 3 blocks instead of 2. Both intents preserved.  │
  └──────────────────────────────────────────────────────────┘
```

### Fractional Index Exhaustion

```
PROBLEM: After many insertions at the same position,
         fractional index keys grow long: "annnnnnnnnnn..."

  SOLUTION: Periodic rebalancing (during idle periods).

  ┌──────────────────────────────────────────────────────────┐
  │  BEFORE rebalance:                                       │
  │    "a"           → block_1                               │
  │    "annnnng"     → block_7    ← key is 7 chars deep     │
  │    "annnnnm"     → block_8                               │
  │    "annnnn"      → block_5                               │
  │    "b"           → block_2                               │
  │                                                          │
  │  AFTER rebalance (all editors idle for 30s):             │
  │    "a"  → block_1                                        │
  │    "d"  → block_7    ← reassigned short key              │
  │    "g"  → block_8                                        │
  │    "j"  → block_5                                        │
  │    "z"  → block_2                                        │
  │                                                          │
  │  Rebalance is a server-initiated atomic operation:       │
  │    1. Pause accepting new edits (~10ms)                  │
  │    2. Reassign all fracIndex values evenly               │
  │    3. Broadcast structure.rebalanced to all clients      │
  │    4. Resume editing                                     │
  │                                                          │
  │  TRIGGER: When any key exceeds 10 characters.            │
  │  FREQUENCY: Rare — only after hundreds of insertions     │
  │  at the exact same position.                             │
  └──────────────────────────────────────────────────────────┘
```

### Server Failover

```
PROBLEM: The collaboration server crashes mid-session.

  DESIGN: Write-ahead to Event Hub BEFORE broadcasting.

  ┌──────────────────────────────────────────────────────────┐
  │  Normal flow:                                            │
  │    1. WS Gateway appends intent to Redis Stream          │
  │    2. Collab Svc consumes from stream                    │
  │    3. OT transform against in-memory ops log             │
  │    4. Write to Event Hub (durable, replicated) ← commit  │
  │    5. Write to Broadcast Svc (hot path)                  │
  │    6. ACK the Redis Stream message                       │
  │                                                          │
  │  If server crashes between step 4 and 5:                 │
  │    → EH has the operation (durable)                      │
  │    → Broadcast missed, but new owner replays EH          │
  │    → Clients reconnect via sync flow                     │
  │                                                          │
  │  If server crashes between step 2 and 4:                 │
  │    → Op is in Redis Stream but not in EH                 │
  │    → New owner consumes unacked messages from the same   │
  │      Redis Stream + replays EH to rebuild state          │
  │                                                          │
  │  If server crashes before step 1:                        │
  │    → Operation was never written anywhere                │
  │    → Client never received ACK → retries with same       │
  │      clientOpId → server processes it fresh               │
  │                                                          │
  │  RESULT: At-least-once delivery with idempotent dedup.   │
  │  No operation is ever lost or applied twice.             │
  └──────────────────────────────────────────────────────────┘

  DELIVERY GUARANTEE: At-least-once with server-side dedup.
    - clientOpId dedup window: 7 days (stored in Cosmos DB)
    - After 7 days, old clientOpIds are purged
    - Clients offline for >7 days must do a full resync
      (pull structure + lazy load, skip push)
```

### Cross-Region Consistency

```
PROBLEM: Users in US-East and EU-West edit the same block.
         Cosmos DB multi-region has replication lag.

  SOLUTION: Single-leader per document.

  ┌──────────────────────────────────────────────────────────┐
  │  Each document is assigned a PRIMARY REGION based on     │
  │  where the first editor connects (e.g., US-East).       │
  │                                                          │
  │  All writes for doc123 route to US-East:                 │
  │    US-East editor: <50ms (local)                         │
  │    EU-West editor: ~150ms (cross-region to leader)       │
  │                                                          │
  │  Reads can use local replica (eventual consistency):     │
  │    EU-West reads: <10ms (local Cosmos DB replica)        │
  │    But may be 50-200ms stale for recently written data   │
  │                                                          │
  │  WHY SINGLE-LEADER?                                      │
  │    OT requires a global operation order.                  │
  │    Multi-leader would create conflicting op logs.         │
  │    Single-leader ensures one canonical ops log per doc.  │
  │                                                          │
  │  OPTIMIZATION: If all editors move to EU-West,           │
  │  the primary region can be migrated (leader handoff).    │
  └──────────────────────────────────────────────────────────┘
```

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| Authority | Server-authoritative | Client-authoritative (CRDT) | **Server** — single source of truth, simpler clients |
| Conflict resolution | OT (central log) | CRDT (decentralized) | **OT** — natural fit for server authority model |
| Document model | Block (paragraph) | Character-level | **Block** — natural editing unit, good lazy-load boundary |
| Document hierarchy | Flat blocks | File → Segment → Block | **Segments** for large docs (1000+ blocks); single segment is fine for small docs |
| Block ordering | Fractional indexing | Integer positions | **Fractional** — O(1) insert without re-indexing |
| Hot-path transport | Redis Streams (~2-5ms) | Direct gRPC to specific instance | **Redis Streams** — decouples gateway from Collab Svc; gateway has no routing logic; failover requires zero gateway reconfiguration |
| Cold-path ops log | Event Hub (durable, replayable) | Direct DB writes | **Event Hub** — decouples persistence from hot path; enables replay on failover |
| Broadcast channels | Per-document Pub/Sub | Per-WS-server Pub/Sub | **Per-WS-server** — ~100 stable channels vs ~200K with churn; targeted via Health Redis |
| Batching | Idle/size/latency triggers | Fixed interval | **3-trigger** — adapts to all typing patterns |
| Event model | Asymmetric (intent → events) | Symmetric | **Asymmetric** — thin client, server decides structure |
| Offline sync | Push-then-pull with OT | Full CRDT state merge | **Push-pull** — leverages server authority for conflict resolution |

## Technologies Deep Dive

📚 **Learn more about the key technologies used in this design:**

| Technology | Role in This Design | Reference |
|------------|---------------------|-----------|
| WebSocket | Real-time data plane for edit intents and broadcasts | [Tech Guide](technologies/websocket.md) |
| Redis | Health tracking (doc→WS servers), presence, session store | [Tech Guide](technologies/redis.md) |
| Redis Streams | Fixed N partitions between WS Gateway and Collab Svc. Decouples routing; provides ordering + durability | [Tech Guide](technologies/redis.md) |
| Redis Pub/Sub | Per-WS-server broadcast channels for pushing transformed ops to gateways | [Tech Guide](technologies/redis.md) |
| Event Hub | Cold-path durable operations log, partitioned by docId. Replay source on failover | — |
| SignalR | Managed WebSocket connection service (Azure) | [Tech Guide](technologies/signalr.md) |
| ZooKeeper / etcd | Consistent hash ring for segment → Collab Svc instance mapping | — |

---

## How to Explain It Well

> **Opening**: "I'll design a server-authoritative collaborative editor where the server is the brain. Clients are thin — they send raw text intents, and the server decides all structural changes like splitting paragraphs. This keeps clients simple across platforms and gives us a single source of truth."

> **Key talking points**:
> - "The document is a list of Blocks (paragraphs). Blocks are the unit of editing, loading, and OT — this keeps the scope of conflict resolution small"
> - "For large documents, blocks are grouped into segments. Each segment is owned by a single Collaboration Service instance via consistent hashing — this gives us a total operation order for OT without distributed locking"
> - "Block ordering uses fractional indexing — inserting a block is O(1) with no re-indexing of other rows"
> - "The architecture separates a hot path from a cold path. The hot path (WS → Redis Stream → OT → Broadcast Svc → Pub/Sub → WS) delivers edits in ~7-12ms. Redis Streams decouple the gateway from the OT engine — the gateway just writes to a stream, no routing logic. The cold path (Event Hub → Cosmos DB → snapshots) handles durable persistence asynchronously — it never blocks the real-time pipeline"
> - "Broadcast uses per-WS-server Pub/Sub channels, not per-document. The Collaboration Service looks up Health Redis to find which WS servers have clients for a document, then publishes only to those — typically 2-5 servers, not hundreds of channels"
> - "Clients batch keystrokes using three triggers: 500ms idle, 100 chars, or 2s max — this cuts network traffic by 10x"
> - "Every operation has a clientOpId for idempotency — safe retries after disconnect"
> - "Events are asymmetric: client sends a generic 'edit', server broadcasts specific 'block.created', 'block.deleted' — clients never need structural logic"
> - "Client maintains three states: Confirmed, In-Flight, Pending — local OT merges server updates without cursor jumps"
> - "Offline reconnection: push local deltas first (server OT-transforms them), then pull the new block structure, then lazy-load the viewport"

---
