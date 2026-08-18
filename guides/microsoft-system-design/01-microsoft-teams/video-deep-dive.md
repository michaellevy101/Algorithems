# Video & Calls - Deep Dive

[← Back to Video Overview](./video.md) | [Back to Main](./README.md)

---

> **This page contains detailed architecture and flows for follow-up interview questions.**
> Start with [Video & Calls (High-Level)](./video.md) first!

---

## Table of Contents

| Section | Common Interview Question |
|---------|---------------------------|
| [Complete Azure Architecture](#complete-azure-architecture) | "What Azure services would you use?" |
| [Call Flow Lifecycle](#call-flow-lifecycle) | "Walk me through the call flow" |
| [NAT Traversal Deep Dive](#nat-traversal-deep-dive) | "How does STUN/TURN work?" |
| [Large Meeting Architecture](#large-meeting-architecture) | "How do you scale to 1000 participants?" |
| [Recording Architecture](#recording-architecture) | "How does recording work?" |
| [Screen Sharing](#screen-sharing-architecture) | "How does screen sharing work?" |
| [Quality Adaptation](#quality-adaptation) | "How do you handle bad networks?" |

---

## Complete Azure Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    TEAMS VIDEO CALLING - COMPLETE ARCHITECTURE                                   │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                              CLIENTS                                                             │
│    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐                │
│    │   Desktop    │   │     Web      │   │    Mobile    │   │ Teams Rooms  │   │  Dial-in     │                │
│    │  (Windows/   │   │  (Chrome/    │   │  (iOS/       │   │  (Conference │   │  (PSTN       │                │
│    │    Mac)      │   │   Edge)      │   │   Android)   │   │   Devices)   │   │   Gateway)   │                │
│    └───────┬──────┘   └───────┬──────┘   └───────┬──────┘   └───────┬──────┘   └───────┬──────┘                │
└────────────┼──────────────────┼──────────────────┼──────────────────┼──────────────────┼────────────────────────┘
             │                  │                  │                  │                  │
             └──────────────────┴────────┬─────────┴──────────────────┴──────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         AZURE FRONT DOOR                                                         │
│  ┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐    │
│  │  • Global load balancing (Anycast)  • WAF  • DDoS Protection  • SSL termination  • WebSocket support   │    │
│  └─────────────────────────────────────────────────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────┬────────────────────────────────────────────────────────┘
                                                         │
                         ┌───────────────────────────────┼───────────────────────────────┐
                         ▼                               ▼                               ▼
              ┌─────────────────────┐       ┌─────────────────────┐       ┌─────────────────────┐
              │    REGION: US EAST  │       │   REGION: EU WEST   │       │  REGION: ASIA EAST  │
              └─────────────────────┘       └─────────────────────┘       └─────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    PER-REGION SERVICES (AKS)                                                     │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                                  │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐           │
│  │  CALL CONTROL   │ │   SIGNALING     │ │  MEDIA ROUTING  │ │   RECORDING     │ │   PRESENCE      │           │
│  │    SERVICE      │ │    SERVICE      │ │    SERVICE      │ │    SERVICE      │ │    SERVICE      │           │
│  │                 │ │                 │ │                 │ │                 │ │                 │           │
│  │ • Create call   │ │ • SDP exchange  │ │ • SFU selection │ │ • Start/stop    │ │ • Who's in call │           │
│  │ • Join/leave    │ │ • ICE candidates│ │ • Route media   │ │ • Compliance    │ │ • Status update │           │
│  │ • Permissions   │ │ • Renegotiation │ │ • Quality adapt │ │ • Transcription │ │ • Heartbeat     │           │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘ └─────────────────┘           │
│                                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                    │                          │                                    │
                    ▼                          ▼                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         REAL-TIME SERVICES                                                       │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                                  │
│  ┌─────────────────────────────────┐  ┌─────────────────────────────────┐  ┌─────────────────────────────────┐  │
│  │      AZURE SIGNALR SERVICE      │  │   AZURE COMMUNICATION SERVICES  │  │      AZURE EVENT HUBS           │  │
│  │                                 │  │                                 │  │                                 │  │
│  │  • WebSocket connections        │  │  • STUN servers                 │  │  • Call events (start/end)     │  │
│  │  • SDP/ICE message relay        │  │  • TURN relay servers           │  │  • Quality metrics              │  │
│  │  • 1M+ concurrent connections   │  │  • PSTN connectivity            │  │  • Analytics pipeline           │  │
│  └─────────────────────────────────┘  └─────────────────────────────────┘  └─────────────────────────────────┘  │
│                                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                   │
                                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                           MEDIA PLANE                                                            │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                                  │
│       Participant A          ┌─────────────────────────────────────────────┐          Participant B             │
│      ┌────────────┐          │            SFU CLUSTER                      │         ┌────────────┐             │
│      │  Simulcast │ UDP/SRTP │  ┌─────────────────────────────────────┐   │UDP/SRTP │  Receives  │             │
│      │  1080p     │─────────►│  │  Forwards appropriate quality       │   │◄────────│  720p      │             │
│      │  720p      │          │  │  based on receiver bandwidth        │   │         │  (selected │             │
│      │  360p      │          │  └─────────────────────────────────────┘   │         │   by SFU)  │             │
│      └────────────┘          └─────────────────────────────────────────────┘         └────────────┘             │
│                                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                                   │
                                                   ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         STORAGE & COMPLIANCE                                                     │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                                  │
│  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐             │
│  │  AZURE BLOB STORAGE │  │  AZURE COSMOS DB    │  │  MICROSOFT PURVIEW  │  │  AZURE AI SEARCH    │             │
│  │  • Recordings       │  │  • Call metadata    │  │  • Retention        │  │  • Transcript search│             │
│  │  • Transcripts      │  │  • Participants     │  │  • Legal hold       │  │  • Meeting search   │             │
│  └─────────────────────┘  └─────────────────────┘  └─────────────────────┘  └─────────────────────┘             │
│                                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Azure Services Summary

| Layer | Azure Service | Purpose |
|-------|---------------|---------|
| **Edge** | Azure Front Door | Global load balancing, WAF, DDoS protection |
| **Compute** | Azure Kubernetes Service (AKS) | Runs microservices |
| **Signaling** | Azure SignalR Service | WebSocket connections for SDP/ICE exchange |
| **Media** | Azure Communication Services | STUN/TURN servers, PSTN |
| **Events** | Azure Event Hubs | Call events, metrics streaming |
| **Streaming** | Azure Media Services | Transcode for large meetings |
| **CDN** | Azure CDN | Edge caching for broadcasts |
| **Storage** | Azure Blob Storage | Recordings, transcripts |
| **Database** | Azure Cosmos DB | Call metadata |
| **Compliance** | Microsoft Purview | Retention, eDiscovery |

---

## Call Flow Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              VIDEO CALL LIFECYCLE - SERVICE FLOW                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 PHASE 1: CALL INITIATION
═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════

  User A                                                                                           User B
     │                                                                                                │
     │ ① POST /calls  [SYNC]                                                                         │
     │ ──────────────────────► CALL CONTROL SERVICE                                                  │
     │                              │                                                                 │
     │                              │ ② Validate permissions (Cosmos DB) [SYNC]                      │
     │                              │ ③ Create call record (Cosmos DB) [SYNC]                        │
     │                              │ ④ Publish event (Event Hubs) [ASYNC]                           │
     │                              │                                                                 │
     │ ⑤ Response: {callId}        │                                                                 │
     │ ◄──────────────────────────────                                                                │
     │                              │                                                                 │
     │                              │ ⑥ Check presence (Redis) [SYNC]                                │
     │                              │ ⑦ Notify via SignalR [ASYNC] ──────────────────────────────────►
     │                              │                                                    "Incoming call"


═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 PHASE 2: SIGNALING (WebRTC Handshake)
═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════

  User A                    SIGNALR SERVICE                SIGNALING SERVICE                    User B
     │                            │                              │                                  │
     │ ⑧ Send SDP Offer          │                              │                                  │
     │ ──────────────────────────►│ ─────────────────────────────►                                  │
     │                            │   [SYNC - must process]      │ ⑨ Allocate SFU                  │
     │                            │                              │ ⑩ Get TURN URLs (ACS) [SYNC]    │
     │                            │◄──────────────────────────────                                  │
     │                            │──────────────────────────────────────────────────────────────────►
     │                            │                              │                                  │
     │                            │ ⑪ User B sends SDP Answer   │                                  │
     │                            │◄──────────────────────────────────────────────────────────────────
     │◄────────────────────────────                              │                                  │
     │                            │                              │                                  │
     │                            │ ⑫ Exchange ICE Candidates [ASYNC - trickle ICE]                │
     │◄───────────────────────────►◄──────────────────────────────►◄──────────────────────────────────►


═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 PHASE 3: MEDIA FLOW
═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════

  User A                              SFU CLUSTER                           User B
     │                                     │                                   │
     │  ⑬ UDP/SRTP: Simulcast streams     │                                   │
     │ ═══════════════════════════════════►│                                   │
     │     [REAL-TIME - continuous]        │                                   │
     │                                     │ ⑭ Select quality per receiver    │
     │                                     │═══════════════════════════════════►
     │                                     │    [REAL-TIME - continuous]       │
     │                                     │                                   │
     │  ⑮ RTCP: Quality feedback [REAL-TIME - every few seconds]              │
     │◄════════════════════════════════════►◄══════════════════════════════════►


═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════
 PHASE 4: CALL END
═══════════════════════════════════════════════════════════════════════════════════════════════════════════════════

  User A                     CALL CONTROL              EVENT HUBS              POST-PROCESSING
     │                            │                        │                        │
     │ ⑯ "End call" [SYNC]       │                        │                        │
     │ ──────────────────────────►│                        │                        │
     │                            │ ⑰ Update DB [SYNC]    │                        │
     │                            │ ⑱ Publish event ──────►│                        │
     │                            │    [ASYNC]             │ ⑲ Trigger processing  │
     │                            │                        │ ───────────────────────►
     │ ⑳ Response [SYNC]         │                        │    [ASYNC]             │
     │◄────────────────────────────                        │                        │
     │                            │                        │    ㉑ Transcription    │
     │                            │                        │    ㉒ Index in Search  │
     │                            │                        │    [ASYNC - minutes]   │
```

### Sync vs Async Summary

| Step | Operation | Sync/Async | Why? |
|------|-----------|------------|------|
| ①②③ | Create call, validate | **SYNC** | User waiting, need callId |
| ④ | Analytics event | **ASYNC** | Non-blocking, fire-and-forget |
| ⑥ | Check presence | **SYNC** | Need to know notification path |
| ⑦ | Push notification | **ASYNC** | Fire-and-forget |
| ⑧-⑪ | SDP exchange | **SYNC** | Must complete handshake |
| ⑫ | ICE candidates | **ASYNC** | Trickle as discovered |
| ⑬-⑮ | Media flow | **REAL-TIME** | Continuous streaming |
| ⑱-㉒ | Post-processing | **ASYNC** | Background pipeline |

---

## NAT Traversal Deep Dive

### The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Users behind NAT don't know their public IP. Direct connection fails.                  │
│                                                                                          │
│  User A (192.168.1.10) ──✗──► User B (192.168.2.20)                                    │
│  (behind NAT)                  (behind NAT)                                             │
│                                                                                          │
│  They only know private IPs - can't reach each other!                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### STUN: Discover Public IP

```
  Client                      NAT Router                    STUN Server
  192.168.1.10                203.0.113.5                   (Public)
       │                            │                             │
       │ 1. "What's my IP?"         │                             │
       │───────────────────────────►│ 2. NAT rewrites src to      │
       │                            │    203.0.113.5:54321        │
       │                            │────────────────────────────►│
       │                            │                             │
       │                            │ 3. "You are 203.0.113.5:54321"
       │                            │◄────────────────────────────│
       │ 4. Now knows public IP!    │                             │
       │◄───────────────────────────│                             │
```

### TURN: Relay When Direct Fails

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  ~80% of calls: STUN works → Direct P2P connection                                      │
│  ~20% of calls: Symmetric NAT → Need TURN relay                                         │
│                                                                                          │
│  SYMMETRIC NAT PROBLEM:                                                                 │
│  • NAT assigns DIFFERENT external port per destination                                  │
│  • STUN returns port 54321, but peer gets port 61234                                   │
│  • Connection fails!                                                                    │
│                                                                                          │
│  SOLUTION: Both peers connect to TURN server                                           │
│  ┌────────┐          ┌────────────┐          ┌────────┐                                │
│  │ User A │ ════════►│   TURN     │◄════════ │ User B │                                │
│  └────────┘          │  (Relay)   │          └────────┘                                │
│                      └────────────┘                                                     │
│                      Adds ~50ms latency                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Large Meeting Architecture

> **For 100+ attendees, pure SFU doesn't scale economically. Switch to CDN broadcast.**

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                    LARGE MEETING (1000+ attendees)                                       │
└─────────────────────────────────────────────────────────────────────────────────────────┘

   PRESENTERS (≤10)                                              ATTENDEES (1000+)
   ┌────────────────┐                                           ┌────────────────────────┐
   │ P1  P2  P3  P4 │    WebRTC (bidirectional)                │   A1  A2  A3  A4  ...  │
   └───────┬────────┘                                           └───────────┬────────────┘
           │                                                                │
           ▼                                                                │
   ┌───────────────┐                                                        │
   │      SFU      │  (Only for presenters)                                 │
   └───────┬───────┘                                                        │
           │                                                                │
           ▼                                                                │
   ┌───────────────┐                                                        │
   │ Azure Media   │  • Transcode to HLS/DASH                               │
   │ Services      │  • Multiple quality ladders                            │
   └───────┬───────┘                                                        │
           │                                                                │
           ▼                                                                │
   ┌───────────────────────────────────────────────────────────────────────┐
   │                         AZURE CDN                                      │
   │   ┌────────┐  ┌────────┐  ┌────────┐                                  │
   │   │ Edge   │  │ Edge   │  │ Edge   │  ◄────────────────────────────────
   │   │ (US)   │  │ (EU)   │  │ (Asia) │        HLS/DASH streaming
   │   └────────┘  └────────┘  └────────┘        (10-30 sec delay)
   └───────────────────────────────────────────────────────────────────────┘

ATTENDEE FEATURES:
  • One-way video (watch only)
  • 10-30 second delay (acceptable for broadcast)
  • Q&A and reactions via separate WebSocket (SignalR)
```

---

## Recording Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         RECORDING FLOW                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

  1. RECORDING BOT                2. MEDIA SERVICES              3. STORAGE
  ┌──────────────────┐           ┌──────────────────┐           ┌──────────────────┐
  │                  │           │                  │           │                  │
  │ • Joins as       │  streams  │ • Composes grid  │   MP4     │ • Azure Blob     │
  │   participant    │ ─────────►│ • Encodes H.264  │ ─────────►│ • Lifecycle tiers│
  │ • Receives all   │           │ • Multiple       │           │   (Hot→Cool)     │
  │   streams        │           │   qualities      │           │                  │
  └──────────────────┘           └──────────────────┘           └──────────────────┘
                                                                        │
                                         ┌──────────────────────────────┘
                                         ▼
                                 4. POST-PROCESSING
                                 ┌──────────────────┐
                                 │ Azure Cognitive  │
                                 │ • Transcription  │
                                 │ • Speaker ID     │
                                 │ • Captions (VTT) │
                                 └────────┬─────────┘
                                          │
                         ┌────────────────┼────────────────┐
                         ▼                ▼                ▼
                 ┌────────────┐   ┌────────────┐   ┌────────────┐
                 │ AI Search  │   │  Purview   │   │ Cosmos DB  │
                 │ (searchable│   │ (retention │   │ (metadata) │
                 │ transcript)│   │  policies) │   │            │
                 └────────────┘   └────────────┘   └────────────┘
```

---

## Screen Sharing Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SCREEN SHARING                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────┐
  │  Screen Capture API │  getDisplayMedia()
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────┐
  │   Video Encoder     │  VP9/AV1 (better for static content)
  │   Content hint:     │
  │   • "detail" (text) │  Higher res, lower framerate
  │   • "motion" (video)│  Lower res, higher framerate
  └──────────┬──────────┘
             │
             ▼
  ┌─────────────────────────────────────────────────────────────────┐
  │                    SEPARATE MEDIA TRACKS                         │
  │                                                                  │
  │   Audio Track ─────►  Microphone                                │
  │   Video Track ─────►  Camera                                    │
  │   Screen Track ────►  Screen capture  ← Independent bandwidth   │
  │                                                                  │
  │   SFU handles each track independently                          │
  └─────────────────────────────────────────────────────────────────┘

OPTIMIZATIONS:
  • Dirty-region encoding: Only encode changed portions
  • Content-adaptive: Detect slides vs video playback
  • System audio: Include computer audio (opt-in)
```

---

## Quality Adaptation

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         BANDWIDTH ADAPTATION                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

SENDER SIDE:
  • GCC (Google Congestion Control) monitors packet loss, jitter
  • Adjusts encoding bitrate in real-time

SFU SIDE:
  • Receives simulcast: 1080p (2.5Mbps) + 720p (1Mbps) + 360p (0.3Mbps)
  • Selects per receiver based on:
    - Receiver's REMB (bandwidth estimate)
    - Viewport size (spotlight vs thumbnail)
    - Active speaker (prioritize current speaker)

EXAMPLE - Gallery view with 9 participants:
┌─────────┬─────────┬─────────┐
│ Speaker │   P2    │   P3    │  Speaker: 720p (1.0 Mbps)
│  720p   │  180p   │  180p   │  Others:  180p (0.1 Mbps each)
├─────────┼─────────┼─────────┤
│   P4    │   P5    │   P6    │  Total: ~1.8 Mbps
│  180p   │  180p   │  180p   │
├─────────┼─────────┼─────────┤  When bandwidth drops:
│   P7    │   P8    │   P9    │  • Speaker → 360p
│  180p   │  180p   │  180p   │  • Others → 90p or paused
└─────────┴─────────┴─────────┘
```

---

## Technologies Deep Dive

📚 **Learn more about the key technologies:**

| Technology | Role | Reference |
|------------|------|-----------|
| WebRTC | Peer-to-peer media transport | [WebRTC Guide](../technologies/webrtc.md) |
| SignalR | Signaling for call setup | [SignalR Guide](../technologies/signalr.md) |
| CDN | Large meeting streaming | [CDN Guide](../technologies/cdn.md) |
| Kafka | Call events, analytics | [Kafka Guide](../technologies/kafka.md) |

---

[← Back to Video Overview](./video.md) | [Back to Main](./README.md)
