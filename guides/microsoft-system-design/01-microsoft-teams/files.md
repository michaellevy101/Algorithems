# Microsoft Teams: File Sharing - High-Level Design

[← Back to Overview](./README.md) | [Chat](./chat.md) | [Video](./video.md) | [Databases](./databases.md)

---

> **Note**: Microsoft Teams delegates file storage to **SharePoint** (channel files) and **OneDrive** (chat files). This page covers the generic file sharing pattern applicable to any chat application.

---

## High-Level Architecture

```
                           ┌─────────────────────────────────────┐
                           │          AZURE FRONT DOOR           │
                           │         (1. All requests enter)     │
                           └──────────────────┬──────────────────┘
                                              │
                    ┌─────────────────────────┼─────────────────────────┐
                    │                         │                         │
                    ▼                         ▼                         ▼
         ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
         │   AUTH SERVICE   │    │   FILE SERVICE   │    │  OTHER SERVICES  │
         │  (Azure AD B2C)  │    │      (AKS)       │    │  (Chat, Video)   │
         │                  │    │                  │    │                  │
         │ • JWT validation │    │ • Upload URLs    │    │                  │
         │ • Token issuance │◀───│ • Metadata CRUD  │    │                  │
         │ • RBAC policies  │2.  │ • Trigger events │    │                  │
         └──────────────────┘    └────────┬─────────┘    └──────────────────┘
                                          │
                         ┌────────────────┴────────────────┐
                         │                                 │
                         ▼ 3.                              ▼ 4.
              ┌──────────────────┐              ┌──────────────────┐
              │   METADATA DB    │              │  BLOB STORAGE    │
              │   (Cosmos DB)    │              │  (Files + Thumbs)│
              └──────────────────┘              └────────┬─────────┘
                                                        │
                                                        ▼ 5. (on blob create)
                                               ┌──────────────────┐
                                               │   EVENT GRID     │
                                               │  (Blob triggers) │
                                               └────────┬─────────┘
                                                        │
                                                        ▼ 6.
                                               ┌──────────────────┐
                                               │ AZURE FUNCTIONS  │
                                               │ (Virus scan,     │
                                               │  Thumbnails)     │
                                               └──────────────────┘
```

**Flow Numbers (Write Path):**
| Step | What Happens |
|------|--------------|
| 1 | All requests enter via Front Door (SSL, routing) |
| 2 | File Service validates token with Auth Service |
| 3 | File metadata stored in Cosmos DB |
| 4 | Files stored in Blob Storage |
| 5 | Blob upload triggers Event Grid |
| 6 | Azure Functions process files (ASYNC) |

> **Note**: CDN is only used for the **read path** (downloads). See [Download Flow](#download-flow) below.

---

## Upload Flow

```
Client                 Auth              File Service           Blob Storage        Functions
  │                     │                     │                      │                  │
  │── 1. Upload req ───▶│                     │                      │                  │
  │   (JWT token)       │                     │                      │                  │
  │                     │── 2. Validate ─────▶│                      │                  │
  │                     │      token          │                      │                  │
  │                     │◀── 2b. OK ──────────│                      │                  │
  │                     │                     │                      │                  │
  │◀── 3. Pre-signed ───│─────────────────────│                      │                  │
  │    URL (15 min TTL) │                     │                      │                  │
  │                     │                     │                      │                  │
  │════ 4. Direct PUT (bypasses servers) ════════════════════════════▶│                  │
  │                     │                     │                      │                  │
  │                     │                     │◀── 5. Event Grid ────│                  │
  │                     │                     │    (blob created)    │                  │
  │                     │                     │                      │                  │
  │                     │                     │── 6. Trigger ────────│─────────────────▶│
  │                     │                     │                      │   (virus scan)   │
  │                     │                     │                      │                  │
  │◀── 7. WebSocket notify (file ready) ──────│                      │                  │
```

| Step | From | To | Action | Sync/Async |
|------|------|-----|--------|------------|
| 1 | Client | File Service | Request upload URL with JWT | SYNC |
| 2 | File Service | Auth Service | Validate JWT token | SYNC |
| 3 | File Service | Client | Return pre-signed Blob URL | SYNC |
| 4 | Client | Blob Storage | Direct file upload (PUT) | SYNC |
| 5 | Blob Storage | Event Grid | Blob created event | ASYNC |
| 6 | Event Grid | Azure Functions | Trigger virus scan | ASYNC |
| 7 | File Service | Client | Notify file ready via WebSocket | ASYNC |

---

## Download Flow (Read Path)

```
Client                 Auth              File Service         Blob Storage           CDN
  │                     │                     │                    │                   │
  │── 1. Download req ─▶│                     │                    │                   │
  │   (fileId + JWT)    │                     │                    │                   │
  │                     │── 2. Validate ─────▶│                    │                   │
  │                     │    + check perms    │                    │                   │
  │                     │◀── 2b. OK ──────────│                    │                   │
  │                     │                     │                    │                   │
  │◀── 3. CDN URL ──────│─────────────────────│                    │                   │
  │   (signed, 1hr TTL) │                     │                    │                   │
  │                     │                     │                    │                   │
  │═══════════════════ 4. GET from CDN ═══════════════════════════════════════════════▶│
  │                     │                     │                    │         cache hit?│
  │                     │                     │                    │◀── 4b. Origin ────│
  │                     │                     │                    │     (if miss)     │
  │◀══════════════════════════════════ 5. File bytes ═════════════════════════════════│
```

| Step | From | To | Action | Sync/Async |
|------|------|-----|--------|------------|
| 1 | Client | File Service | Request download with fileId + JWT | SYNC |
| 2 | File Service | Auth Service | Validate JWT + check file permissions | SYNC |
| 3 | File Service | Client | Return signed CDN URL | SYNC |
| 4 | Client | CDN | Request file (CDN checks cache) | SYNC |
| 4b | CDN | Blob Storage | Fetch from origin if cache miss | SYNC |
| 5 | CDN | Client | Return file bytes | SYNC |

**CDN Benefits:**
- Edge caching → fast global downloads
- Offloads Blob Storage
- Popular files served from cache (no origin hit)

---

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Auth | Centralized service | Single source of truth, all services use it |
| Upload | Pre-signed URL | Server doesn't handle bytes; scales |
| Events | Event Grid + Blob trigger | Azure-native, no Kafka needed |
| Post-processing | ASYNC | Don't block upload on virus scan |
| Download | CDN | Fast for popular files globally |

---

## Data Model

```
Files Table (Cosmos DB)
├── id (UUID)
├── name (string)
├── size_bytes (int)
├── mime_type (string)
├── blob_path (string)
├── thumbnail_path (string, nullable)
├── uploaded_by (user_id)
├── channel_id (nullable)    ← Channel file
├── chat_id (nullable)       ← Direct message file
├── status (pending | scanning | ready | failed)
├── created_at (timestamp)
└── deleted_at (timestamp, nullable)  ← Soft delete
```

**Partition Key**: `channel_id` or `chat_id` (co-locate files with conversations)

---

## Common Interview Questions

| Question | Quick Answer |
|----------|--------------|
| "How handle large files?" | Chunked upload with resumable sessions |
| "How prevent malicious files?" | ASYNC virus scan, file marked 'ready' only after pass |
| "How handle permissions?" | Auth Service checks channel/chat membership |
| "How generate thumbnails?" | Azure Functions triggered by Event Grid (ASYNC) |
| "What if virus scan fails?" | Status = 'failed', notify user, don't serve file |

---

## Related Pages

- [Chat & Messaging](./chat.md) - File links in messages
- [Video & Calls](./video.md) - Screen sharing (different flow)
- [Databases](./databases.md) - Cosmos DB, Blob Storage patterns
- [Back to Overview](./README.md)
