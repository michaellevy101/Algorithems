# Presence & Status

[← Back to Overview](./README.md) | [Chat](./chat.md) | [Video →](./video.md) | [Technologies](./technologies.md)

> 📚 **Key technologies:** [SignalR](../technologies/signalr.md) for presence broadcast, [Redis](../technologies/redis.md) for TTL-based tracking.

## What is Presence?

Presence answers two questions:
1. **Is this user online?** → Used for message routing (SignalR vs skip)
2. **What's their status?** → Shown as green/yellow/red dot in UI

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PRESENCE OVERVIEW                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   PURPOSE 1: Message Routing          PURPOSE 2: Status UI                   │
│   ─────────────────────────           ────────────────────                   │
│   Is user connected via WebSocket?    What status dot to show?               │
│                                                                              │
│   Online  → Push via SignalR          🟢 Available                           │
│   Offline → Skip (user syncs later)   🟡 Away / Idle                         │
│                                       🔴 Busy / Do Not Disturb               │
│                                       ⚫ Offline / Appear Offline            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

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
         │   AUTH SERVICE   │    │ PRESENCE SERVICE │    │  OTHER SERVICES  │
         │  (Azure AD B2C)  │    │      (AKS)       │    │  (Chat, Video)   │
         │                  │    │                  │    │                  │
         │ • JWT validation │◀───│ • Heartbeat API  │───▶│ • Query presence │
         │ • Token issuance │ 2. │ • Status updates │    │   before push    │
         └──────────────────┘    │ • Broadcast      │    └──────────────────┘
                                 └────────┬─────────┘
                                          │
                         ┌────────────────┴────────────────┐
                         │                                 │
                         ▼ 3.                              ▼ 4.
              ┌──────────────────┐              ┌──────────────────┐
              │      REDIS       │              │  AZURE SIGNALR   │
              │  (Presence TTL)  │              │  (Broadcast)     │
              └──────────────────┘              └──────────────────┘
```

| Step | From | To | Action |
|------|------|-----|--------|
| 1 | Client | Front Door | Heartbeat or status change |
| 2 | Presence Service | Auth Service | Validate JWT |
| 3 | Presence Service | Redis | Store presence with TTL |
| 4 | Presence Service | SignalR | Broadcast status changes |

## Redis Data Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PRESENCE IN REDIS                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  KEY STRUCTURE:                                                              │
│  ──────────────                                                              │
│  presence:{userId}:{deviceId}                                                │
│                                                                              │
│  Example keys:                                                               │
│    presence:user123:desktop-win-abc                                          │
│    presence:user123:mobile-ios-xyz                                           │
│    presence:user123:web-chrome-def                                           │
│                                                                              │
│  VALUE (JSON):                                                               │
│  ─────────────                                                               │
│  {                                                                           │
│    "status": "available",       // available, busy, dnd, away, offline       │
│    "activity": "InACall",       // InACall, InAMeeting, Presenting, etc.     │
│    "lastSeen": 1706295600000,   // Unix timestamp (ms)                       │
│    "device": "desktop",         // desktop, mobile, web                      │
│    "client": "Teams/1.6.0"      // Client version                            │
│  }                                                                           │
│                                                                              │
│  TTL: 5 minutes (auto-expire if no heartbeat)                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## How Presence is Maintained

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         HEARTBEAT MECHANISM                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  CLIENT SENDS HEARTBEAT EVERY 30 SECONDS:                                    │
│                                                                              │
│  ┌──────────┐     POST /presence/heartbeat      ┌─────────────────────────┐ │
│  │  Client  │ ─────────────────────────────────►│  Presence Service       │ │
│  │ (Teams)  │    { status: "available" }        │  (AKS)                  │ │
│  └──────────┘                                   └───────────┬─────────────┘ │
│                                                             │               │
│                                                             ▼               │
│                                                  ┌─────────────────────────┐│
│                                                  │  Redis                  ││
│                                                  │  SETEX presence:user123 ││
│                                                  │         300 {json}      ││
│                                                  │  (TTL = 5 min = 300s)   ││
│                                                  └─────────────────────────┘│
│                                                                              │
│  WHAT HAPPENS:                                                               │
│  ─────────────                                                               │
│  • Each heartbeat resets TTL to 5 minutes                                    │
│  • If client crashes/disconnects → no heartbeat → TTL expires → "offline"   │
│  • No explicit "disconnect" event needed (unreliable anyway)                 │
│                                                                              │
│  TIMELINE EXAMPLE:                                                           │
│  ─────────────────                                                           │
│  0:00  - User opens Teams → first heartbeat → TTL = 5 min                   │
│  0:30  - Heartbeat → TTL reset to 5 min                                      │
│  1:00  - Heartbeat → TTL reset to 5 min                                      │
│  1:30  - User closes laptop (no more heartbeats)                             │
│  6:30  - TTL expires → Redis deletes key → user is "offline"                 │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Multi-Device Presence

Users can be on multiple devices simultaneously. How do we show ONE status?

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MULTI-DEVICE AGGREGATION                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SCENARIO: User has 3 devices                                                │
│                                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐                 │
│  │ Desktop        │  │ Mobile         │  │ Web            │                 │
│  │ Status: Busy   │  │ Status: Away   │  │ Status: Avail  │                 │
│  │ (in a call)    │  │ (idle 10 min)  │  │ (active tab)   │                 │
│  └────────────────┘  └────────────────┘  └────────────────┘                 │
│                                                                              │
│  AGGREGATION RULES (priority order):                                         │
│  ────────────────────────────────────                                        │
│  1. In a call/meeting on ANY device    → 🔴 Busy                            │
│  2. Do Not Disturb set on ANY device   → 🔴 Do Not Disturb                  │
│  3. Available on ANY device            → 🟢 Available                        │
│  4. Away on ALL devices                → 🟡 Away                             │
│  5. No devices online                  → ⚫ Offline                          │
│                                                                              │
│  RESULT: User shows as 🔴 Busy (desktop is in a call)                        │
│                                                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  IMPLEMENTATION:                                                             │
│                                                                              │
│  GET presence:user123:* → Returns all device keys                            │
│                                                                              │
│  Presence Service aggregates:                                                │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  function getAggregatedStatus(userId) {                          │        │
│  │    const devices = redis.keys(`presence:${userId}:*`);           │        │
│  │    const statuses = devices.map(d => redis.get(d));              │        │
│  │                                                                  │        │
│  │    if (statuses.some(s => s.activity === 'InACall')) return 'busy';│       │
│  │    if (statuses.some(s => s.status === 'dnd')) return 'dnd';     │        │
│  │    if (statuses.some(s => s.status === 'available')) return 'available';│ │
│  │    if (statuses.length > 0) return 'away';                       │        │
│  │    return 'offline';                                             │        │
│  │  }                                                               │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Status UI Display

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         STATUS IN TEAMS UI                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  When User A views a channel or chat:                                        │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │  #general                                                        │        │
│  │  ─────────────────────────────────────────────────────────────── │        │
│  │                                                                  │        │
│  │  👤 John Smith  🟢    Hey team, the build is ready               │        │
│  │                       10:30 AM                                   │        │
│  │                                                                  │        │
│  │  👤 Jane Doe    🟡    Great! I'll review after lunch             │        │
│  │                       10:32 AM                                   │        │
│  │                                                                  │        │
│  │  👤 Bob Wilson  🔴    @Jane I'm in a call, ping me after         │        │
│  │                       10:35 AM                                   │        │
│  │                                                                  │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│                                                                              │
│  STATUS MEANINGS:                                                            │
│  ────────────────                                                            │
│  🟢 Available    - User is active and can respond                            │
│  🟢 Available    - "Be right back" (user set, auto-reverts)                  │
│  🟡 Away         - Idle for 5+ minutes (auto-detected)                       │
│  🔴 Busy         - In a call, meeting, or presenting                         │
│  🔴 Do Not Disturb - User manually set; notifications silenced               │
│  ⚫ Offline      - No active sessions                                        │
│  ⚫ Appear Offline - User manually hiding presence                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Message Routing (Step 7 of Chat Flow)

Presence determines **how** to deliver messages:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      MESSAGE ROUTING DECISION                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Notification Worker receives message for User B:                            │
│                                                                              │
│     GET presence:user_B:* ───► Redis                                        │
│                                  │                                           │
│                  ┌───────────────┴───────────────┐                          │
│                  ▼                               ▼                           │
│         ANY key EXISTS                    NO keys found                      │
│         (User has active session)         (TTL expired = offline)            │
│                  │                               │                           │
│                  ▼                               ▼                           │
│       ┌─────────────────┐              ┌─────────────────────┐              │
│       │ Azure SignalR   │              │   No action needed  │              │
│       │ Push via        │              │   Message is in     │              │
│       │ WebSocket       │              │   Cosmos DB; user   │              │
│       └─────────────────┘              │   syncs on reconnect│              │
│                                        └─────────────────────┘              │
│                                                                              │
│  💡 Enhancement: Add Notification Hubs for offline branch to send push      │
│     alerts to mobile devices (APNs/FCM). See Chat page for details.         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Presence Broadcast (Status Changes)

When a user's status changes, notify their contacts:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      PRESENCE CHANGE BROADCAST                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  SCENARIO: User A changes status from Available → Busy (joins a call)        │
│                                                                              │
│  ┌──────────┐     Status change        ┌─────────────────────────┐          │
│  │  User A  │ ────────────────────────►│  Presence Service       │          │
│  │ (Client) │    { status: "busy",     │                         │          │
│  └──────────┘      activity: "InACall"}└───────────┬─────────────┘          │
│                                                    │                         │
│                    ┌───────────────────────────────┼───────────────┐        │
│                    ▼                               ▼               ▼         │
│         ┌─────────────────┐          ┌─────────────────┐  ┌──────────────┐  │
│         │  Update Redis   │          │  Publish Event  │  │  Get Contacts│  │
│         │  presence:userA │          │  to Event Hubs  │  │  who care    │  │
│         └─────────────────┘          └────────┬────────┘  └──────┬───────┘  │
│                                               │                   │          │
│                                               ▼                   │          │
│                                    ┌─────────────────────┐       │          │
│                                    │  Azure SignalR      │◄──────┘          │
│                                    │  Push to online     │                   │
│                                    │  contacts of User A │                   │
│                                    └─────────────────────┘                   │
│                                                                              │
│  WHO GETS NOTIFIED?                                                          │
│  ──────────────────                                                          │
│  • Users in same channel (currently viewing)                                 │
│  • Users with 1:1 chat open with User A                                      │
│  • NOT everyone in the organization (doesn't scale)                          │
│                                                                              │
│  OPTIMIZATION: "Subscription model"                                          │
│  ─────────────────────────────────                                           │
│  • Client subscribes to presence of users visible on screen                  │
│  • Unsubscribes when user scrolls away or closes chat                        │
│  • Reduces broadcast scope dramatically                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Why Redis? (Design Decision)

| Requirement | Why Redis Works |
|-------------|-----------------|
| Low latency | Sub-millisecond reads; presence checked on every message |
| TTL support | Native EXPIRE command; auto-cleanup without cron jobs |
| Pub/Sub | Broadcast presence changes to SignalR hubs |
| High throughput | Handles millions of heartbeats/sec |
| Simple data model | Key-value fits presence perfectly |

**Alternative Considered: Cosmos DB**
- ❌ Higher latency (~5-10ms vs <1ms)
- ❌ More expensive for high-frequency writes (heartbeats)
- ❌ No native TTL with automatic deletion (has TTL but requires indexing)

**Why NOT in-memory in the service?**
- ❌ Multiple service instances wouldn't share state
- ❌ Service restart = lose all presence data
- ✅ Redis is shared across all Presence Service pods

## Interview Tips

**Common Questions:**

| Question | Answer |
|----------|--------|
| "Why TTL instead of explicit disconnect?" | Crashes and network failures don't trigger disconnect events. TTL auto-expires. |
| "Why 30s heartbeat, 5min TTL?" | Heartbeat frequent enough to detect idle; TTL long enough to survive brief network hiccups |
| "How to handle 100M users?" | Redis Cluster; partition by userId hash; each shard handles subset |
| "What if Redis goes down?" | Fail open: treat everyone as "offline", messages still delivered via Cosmos DB sync |

**Scale Numbers to Know:**
- Redis can handle ~100K ops/sec per shard
- 1M concurrent users × heartbeat every 30s = ~33K writes/sec (easily handled)
- Presence data per user: ~200 bytes × 3 devices = ~600 bytes
- 1M users = ~600MB RAM (tiny for Redis)

---

[← Back to Overview](./README.md)
