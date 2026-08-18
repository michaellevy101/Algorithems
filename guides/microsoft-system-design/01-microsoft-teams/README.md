# Design Microsoft Teams

## Problem Statement
Design a real-time collaboration platform like Microsoft Teams that supports messaging, video calls, file sharing, and integrations for millions of concurrent users.

## Guide Structure

| Page | Description |
|------|-------------|
| [Chat Feature](./chat.md) | Messaging, sync, offline delivery |
| [Presence & Status](./presence.md) | Online/offline detection, status UI (green dots) |
| [Push Notifications](./notifications.md) | Optional: APNs, FCM, WNS, Web Push (enhancement) |
| [Video Feature](./video.md) | WebRTC, SFU, recording, screen sharing |
| [Files Feature](./files.md) | File sharing, upload, CDN delivery |
| [Databases](./databases.md) | Cosmos DB, SQL, Redis, Blob, Search configs |
| [Technologies](./technologies.md) | Deep dive: WebRTC, SFU, STUN/TURN, SignalR, Service Workers |
| [Interview Tips](./interview-tips.md) | Trade-offs, how to explain, follow-up questions |

## Quick Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│         Desktop / Web / Mobile / Teams Rooms / Bots                         │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         AZURE FRONT DOOR                                     │
│  • Routes to nearest healthy region (latency-based routing)                 │
│  • SSL termination (offloads TLS from backend services)                     │
│  • WAF: blocks SQL injection, XSS, bot attacks                              │
│  • DDoS protection: absorbs volumetric attacks at edge                      │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
        ┌─────────────────────────┼─────────────────────────┐
        ▼                         ▼                         ▼
┌───────────────┐         ┌───────────────┐         ┌───────────────┐
│   US EAST     │         │   EU WEST     │         │  ASIA EAST    │
└───────┬───────┘         └───────┬───────┘         └───────┬───────┘
        │                         │                         │
        ▼                         ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PER-REGION SERVICES                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │   Chat   │ │  Sync    │ │  Video   │ │   File   │ │  Notif*  │          │
│  │ Service  │ │ Service  │ │ Service  │ │ Service  │ │ (opt.)   │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│                                              * Optional: adds mobile push   │
└─────────────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            DATA LAYER                                        │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐     │
│  │ Cosmos DB │ │ Azure SQL │ │   Redis   │ │   Blob    │ │ AI Search │     │
│  │ (Messages)│ │ (Metadata)│ │ (Presence)│ │  (Files)  │ │  (Search) │     │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Component Purposes

| Layer | Component | What It Does |
|-------|-----------|--------------|
| **Clients** | Desktop/Web/Mobile | User-facing apps; maintain WebSocket for real-time updates |
| **Clients** | Teams Rooms | Conference room devices; join calls with dedicated hardware |
| **Clients** | Bots | Automated agents; respond to messages, integrate 3rd-party apps |
| **Edge** | Azure Front Door | Routes users to nearest region; blocks attacks (WAF/DDoS) |
| **Services** | Chat Service | Accepts new messages; writes to Event Hubs → ACKs back to client |
| **Services** | Sync Service | Handles "get my messages" requests; reads from Cosmos DB |
| **Services** | Video Service | Manages call setup, SFU routing; uses Azure Communication Services |
| **Services** | File Service | Generates upload URLs; triggers virus scan after upload |
| **Services** | Notification Service *(optional)* | Push alerts to offline mobile devices; add for WhatsApp-like UX |
| **Data** | Cosmos DB | Stores chat messages; partitioned by channel for fast reads |
| **Data** | Azure SQL | Stores user profiles, team membership; relational joins |
| **Data** | Redis | Tracks who's online (TTL-based); caches hot data |
| **Data** | Blob Storage | Stores files & recordings; served via CDN for downloads |
| **Data** | AI Search | Indexes messages for "search in chat" feature |

## Key Azure Services

| Component | Azure Service | Why This Service |
|-----------|---------------|------------------|
| Load Balancer | Azure Front Door | Only Azure service with global anycast + integrated WAF |
| Compute | AKS (Kubernetes) | Auto-scale pods per service; industry standard orchestration |
| Real-time | Azure SignalR Service | Manages 1M+ WebSockets without custom infra |
| Message Queue | Azure Event Hubs | 1M events/sec throughput; Kafka-compatible if needed |
| Chat Storage | Cosmos DB | <10ms reads globally; auto-replicates across regions |
| Metadata | Azure SQL Database | ACID transactions for user/team changes; familiar SQL |
| Cache | Azure Cache for Redis | Sub-ms latency; TTL auto-expires stale presence |
| Files | Azure Blob Storage | Unlimited scale; integrates with CDN and Defender |
| Search | Azure AI Search | Full-text + semantic search; handles 100M+ documents |
| Video | Azure Communication Services | Managed WebRTC/SFU; no SRTP/STUN/TURN complexity |
| Push *(optional)* | Azure Notification Hubs | Single API for APNs/FCM/WNS; add if mobile push alerts needed |

## Clarifying Questions to Ask

1. **Scale**: How many daily active users? (e.g., 300M DAU)
2. **Features**: Which features to prioritize? (chat, video, file sharing)
3. **Message retention**: How long to store messages? (compliance)
4. **Availability**: What's the target uptime? (99.99%?)
5. **Latency**: Max acceptable latency for messages? (<100ms?)
6. **Offline support**: Should users see messages when back online?
7. **Call size**: Max participants in a call? (1:1, group up to 1000?)
8. **Recording**: Do calls need to be recorded and stored?

## Start Reading

👉 **[Start with Chat & Messaging →](./chat.md)** (core feature)

Or jump to a specific feature:
- [Video & Calls](./video.md)
- [File Sharing](./files.md)
- [Database Configurations](./databases.md)
- [Interview Tips](./interview-tips.md)

---

## Technologies Deep Dive

📚 **Learn more about the key technologies used in this design:**

| Technology | Role in Teams | Reference |
|------------|---------------|-----------|
| WebSocket | Real-time message delivery | [WebSocket Guide](../technologies/websocket.md) |
| WebRTC | Video/audio peer-to-peer | [WebRTC Guide](../technologies/webrtc.md) |
| SignalR | Managed WebSocket service | [SignalR Guide](../technologies/signalr.md) |
| Redis | Presence tracking, caching | [Redis Guide](../technologies/redis.md) |
| Cosmos DB | Message storage, global distribution | [Cosmos DB Guide](../technologies/cosmos-db.md) |
| Kafka/Event Hubs | Message fanout, event streaming | [Kafka Guide](../technologies/kafka.md) |
| CDN | File delivery, large meeting streaming | [CDN Guide](../technologies/cdn.md) |
