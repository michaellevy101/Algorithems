# Microsoft Teams: Video & Calls - High-Level Design

> **Interview Strategy**: Present this high-level design first (10-15 min). Only dive deeper if asked.  
> **Deep Dive**: [video-deep-dive.md](video-deep-dive.md) for detailed architecture, call flows, and edge cases.

---

## Quick Reference

| Term | One-Liner |
|------|-----------|
| **WebRTC** | Browser API for peer-to-peer video/audio using UDP |
| **SFU** | Forwards streams without decoding (scalable) |
| **STUN** | Discovers your public IP through NAT |
| **TURN** | Relay when direct connection fails (~20% of calls) |
| **Signaling** | Exchange connection info before media flows |
| **ICE** | Finds best network path between peers |
| **SDP** | Session Description Protocol — describes what codecs/resolutions each peer supports |
| **Simulcast** | Sender encodes video at multiple quality levels simultaneously |
| **SRTP** | Encrypted RTP — all media is encrypted end-to-hop |

---

## The Core Problem

Video calling sounds simple — "send my camera to the other person" — but it's actually one of the hardest distributed systems problems because of **three conflicting requirements**:

1. **Ultra-low latency** — humans perceive delay above 150ms as "laggy". This is 10-100x stricter than web page loads
2. **Unreliable networks** — users are on WiFi, cellular, behind corporate firewalls, switching between networks
3. **Scale** — Teams supports 1:1 calls, 10-person standups, 300-person all-hands, and 10,000-person town halls — all with the same system

These constraints drive every architectural decision below.

---

## High-Level Architecture

**Draw this first in an interview:**

```
                           ┌─────────────────────────────────────┐
                           │          AZURE FRONT DOOR           │
                           │         (1. All requests enter)     │
                           └──────────────────┬──────────────────┘
                                              │
              ┌───────────────────────────────┼───────────────────────────────┐
              │                               │                               │
              ▼                               ▼                               ▼
   ┌──────────────────┐          ┌──────────────────┐          ┌──────────────────┐
   │   AUTH SERVICE   │          │ SIGNALING SERVICE│          │  MEDIA SERVICE   │
   │  (Azure AD B2C)  │          │      (AKS)       │          │     (SFU)        │
   │                  │          │                  │          │                  │
   │ • JWT validation │◀─────────│ • WebSocket GW   │          │ • Stream forward │
   │ • Token issuance │ 2. Auth  │ • Room state     │          │ • Simulcast      │
   │ • RBAC policies  │          │ • SDP exchange   │          │ • TURN/STUN      │
   └──────────────────┘          └────────┬─────────┘          └────────┬─────────┘
                                          │                             │
                                          │ Control Plane (TCP)         │ Media Plane (UDP)
                                          │                             │
                                          ▼                             ▼
                           ┌─────────────────────────────────────────────────────┐
                           │                       CLIENTS                        │
                           │   Desktop App  │  Mobile App  │  Web Browser         │
                           └─────────────────────────────────────────────────────┘
```

### Why This Split Matters: Control Plane vs Media Plane

The most important architectural insight is that **control and media travel on completely separate paths**:

- **Control plane (TCP via WebSocket)** — "Who's in this call? What capabilities do they have? Who just muted?" These messages are small, infrequent, and must be reliable. TCP guarantees delivery. Runs through the **Signaling Service**.

- **Media plane (UDP via WebRTC/SRTP)** — "Here are the next 20ms of video/audio frames." These are massive, continuous, and latency-sensitive. If a packet is lost, **don't resend it** — by the time it arrives, it's too old to display. UDP accepts losses. Runs through the **SFU (Selective Forwarding Unit)**.

This separation means a bug in signaling doesn't kill active media, and scaling media servers is independent from scaling signaling.

### Four Core Components Explained

| Component | What It Does | Protocol | Why This Choice |
|-----------|--------------|----------|----------------|
| **Auth Service** | Validates JWT, issues tokens, RBAC | HTTPS | Stateless authentication scales horizontally. JWT tokens are self-contained — any server can validate without DB lookup |
| **Signaling** | Coordinates who joins, exchanges SDP offers/answers | WebSocket (TCP) | WebSocket provides persistent bidirectional connection needed for real-time room state updates. SDP must be reliably delivered |
| **Media Server (SFU)** | Receives one stream per sender, forwards to all receivers | WebRTC (UDP) | SFU avoids decoding video — just forwards packets. This means one SFU can handle hundreds of streams with minimal CPU |
| **STUN/TURN** | Helps establish connection through NAT firewalls | UDP/TCP | 80% of connections work with STUN alone (just discovers public IP). TURN relay only used when corporate firewalls block direct UDP |

---

## Main Call Flow (Happy Path)

Here's what actually happens when User A calls User B, broken into **4 phases** that happen sequentially:

### Phase 1: Call Initiation (Who's calling whom?)

Before any video flows, the system needs to authenticate the caller, find the callee, and notify them.

```
User A                   Auth              Signaling                 Redis (Presence)    User B
  │                       │                    │                         │                  │
  │── 1. "Join call" ────►│                    │                         │                  │
  │   (JWT token)         │                    │                         │                  │
  │                       │── 2. Validate ────►│                         │                  │
  │                       │   JWT signature,   │                         │                  │
  │                       │   permissions,     │                         │                  │
  │                       │   room access      │                         │                  │
  │                       │◄── 2b. OK ─────────│                         │                  │
  │                       │                    │                         │                  │
  │                       │                    │── 3. "Is User B online?"│                  │
  │                       │                    │──────────────────────────►                  │
  │                       │                    │◄── "Yes, on desktop" ───│                  │
  │                       │                    │                         │                  │
  │◄── 4. Room State ─────│────────────────────│                         │                  │
  │   (participants list) │                    │                         │                  │
  │                       │                    │── 5. Push notification ─────────────────────►
  │                       │                    │   via SignalR           │    "Incoming call 
  │                       │                    │   (ASYNC, fire-and-    │     from User A"
  │                       │                    │    forget)              │                  │
```

**What's happening and why:**
- **Step 1-2**: Authentication happens FIRST, before anything else. The JWT token contains the user's identity, tenant, and permissions. The Auth Service verifies the signature without hitting a database (JWT is self-contained)
- **Step 3**: We check Redis (in-memory, <1ms) for User B's presence — are they online? On which device(s)? This avoids sending notifications to offline users
- **Step 4**: The caller gets back the room state so their UI can show "Waiting for User B..."
- **Step 5**: Notification is **async** — we don't wait for User B to see it. This keeps the caller's response fast

### Phase 2: WebRTC Handshake (Negotiating the connection)

Now both users are aware of the call. Before video can flow, they need to agree on:
- What video codecs they both support (VP9? H.264?)
- What resolutions they can handle
- How to reach each other through the network (NAT traversal)

This negotiation uses **SDP (Session Description Protocol)** — a text format describing media capabilities.

```
User A                   Signaling (WebSocket)              SFU                 User B
  │                            │                              │                    │
  │── 6. SDP Offer ───────────►│                              │                    │
  │   "I can send VP9 at      │                              │                    │
  │    1080p, 720p, 360p.     │                              │                    │
  │    My ICE candidates       │── Forward to SFU ───────────►│                    │
  │    are: [...]"             │                              │                    │
  │                            │                              │── Allocate         │
  │                            │                              │   media port       │
  │                            │                              │   for this call    │
  │                            │◄── SFU ready + TURN URLs ────│                    │
  │                            │                              │                    │
  │                            │── Forward offer to B ─────────────────────────────►│
  │                            │                              │                    │
  │                            │◄── 7. SDP Answer from B ─────────────────────────│
  │◄── SDP Answer ─────────────│   "I support VP9. Let's     │                    │
  │                            │    use 720p max. My ICE     │                    │
  │                            │    candidates are: [...]"    │                    │
  │                            │                              │                    │
  │◄── 8. ICE Candidates ─────►◄── trickle as discovered ───►◄───────────────────►│
  │   (network paths:          │   (ASYNC — candidates       │                    │
  │    local IP, STUN IP,      │    arrive over seconds)      │                    │
  │    TURN relay)             │                              │                    │
```

**What's happening and why:**
- **Step 6 (SDP Offer)**: User A describes everything they can do. This is sent via the reliable WebSocket (not UDP) because losing this message means the call can't start
- **SFU allocation**: The SFU pre-allocates a media port and resources. It also provides TURN server URLs as a fallback relay path
- **Step 7 (SDP Answer)**: User B responds with the intersection of capabilities. "You offered VP9 and H.264; I'll use VP9"
- **Step 8 (ICE Candidates)**: These are network paths — "try reaching me at 192.168.1.5:8000 (local), 52.1.2.3:45123 (STUN-discovered public), or via TURN relay." They **trickle in** asynchronously as the ICE agent discovers them. The connection starts as soon as ANY path works

### Phase 3: Media Flow (Video is live!)

Once ICE finds a working path, media streams through the SFU:

```
User A                              SFU                              User B
  │                                  │                                  │
  │  Simulcast: 3 quality levels    │                                  │
  │  ━━━━━ 1080p (2.5 Mbps) ━━━━━━►│                                  │
  │  ━━━━━ 720p  (1.0 Mbps) ━━━━━━►│  Selects best quality           │
  │  ━━━━━ 360p  (0.3 Mbps) ━━━━━━►│  for B based on:                │
  │                                  │  • B's available bandwidth     │
  │                                  │  • B's viewport size           │
  │                                  │  • Whether A is active speaker │
  │                                  │                                  │
  │                                  │━━━ 720p (selected) ━━━━━━━━━━━━►│
  │                                  │                                  │
  │◄━━━━━━ RTCP feedback ━━━━━━━━━━►│◄━━━━━━ RTCP feedback ━━━━━━━━━━►│
  │  "I'm seeing 2% packet loss"    │  "My bandwidth is 1.5 Mbps"     │
  │  "Please reduce bitrate"        │                                  │
  │                                  │                                  │
  │  All media is SRTP encrypted    │  SFU doesn't decrypt video —    │
  │  (AES-128 or AES-256)           │  just forwards encrypted packets│
```

**What's happening and why:**
- **Simulcast**: The sender doesn't know what quality each receiver needs, so they send ALL quality levels. The SFU picks the right one per receiver. In a 9-person gallery view: the active speaker might get 720p while thumbnails get 180p
- **RTCP feedback loop**: Every few seconds, receivers report their network conditions back to the SFU. If User B's WiFi gets congested, the SFU switches them from 720p to 360p instantly — no renegotiation needed
- **SFU doesn't decode**: This is crucial for scalability. The SFU just looks at packet headers and forwards. It never touches the video content, so one SFU server can handle hundreds of concurrent streams

### Phase 4: Call End (Cleanup)

```
User A                     Call Control           Event Hubs          Post-Processing
  │                            │                      │                     │
  │── "End call" ─────────────►│                      │                     │
  │                            │── Update call ───────►│                     │
  │                            │   record in DB       │── Trigger async ───►│
  │◄── "Call ended" ───────────│   (duration, stats)  │   pipeline          │
  │                            │                      │                     │
  │  (Done — user sees UI)     │                      │   • Transcription   │
  │                            │                      │   • Speaker ID      │
  │                            │                      │   • Searchable text │
  │                            │                      │   (takes minutes,   │
  │                            │                      │    user doesn't     │
  │                            │                      │    wait for this)   │
```

---

## Why SFU? (The Key Architectural Decision)

This is the most important decision in the entire design. **Why not just connect users directly?**

### 3-way call comparison:

```
Peer-to-Peer (Mesh)           SFU (Star)                 MCU (Decode & Recompose)
     A ←───→ B                 A ───→ SFU ───→ B          A ───→ MCU ───→ B
      ↖   ↗                        ↖ ↗                        ↖ ↗
        C                            C                            C

Upload: 2 streams each        Upload: 1 stream each       Upload: 1 stream each
Download: 2 streams each      Download: 2 streams each    Download: 1 mixed stream
CPU: High (encode per peer)   CPU: Low (encode once)      CPU: Minimal on client
Server: None                  Server: Low CPU (forward)   Server: Very high CPU (decode ALL)
```

| Approach | Upload per User | Download per User | Server CPU | When to Use |
|----------|----------------|-------------------|-----------|-------------|
| **P2P Mesh** | N-1 streams | N-1 streams | None | 1:1 calls only |
| **SFU** ✅ | **1 stream** | N-1 streams | **Low** (just forwarding) | **3+ participants** |
| **MCU** | 1 stream | 1 stream | **Very high** (decode everything) | Very weak clients (rare today) |

**Why SFU wins for Teams:**
- **Upload bandwidth**: Each user sends only ONE stream (in simulcast layers). With P2P mesh, a 10-person call requires each person to upload 9 separate streams — that's 9x the upload bandwidth, which kills mobile users
- **Server cost**: SFU doesn't decode video, so it uses minimal CPU. An MCU must decode every stream, compose them into a grid, and re-encode — that's ~50x more CPU per call
- **Flexibility**: SFU can give each receiver a different quality. Active speaker gets 720p, thumbnails get 180p. MCU would need to compose different grids per receiver
- **Latency**: SFU adds <5ms (just packet forwarding). MCU adds 50-200ms (decode + compose + encode)

**Teams uses SFU** because it's the optimal balance of scale, quality, and cost for 3-1000 person calls.

---

## NAT Traversal: Why STUN/TURN Exist

### The Problem

Most devices sit behind NAT (Network Address Translation) — your laptop's real IP is `192.168.1.5`, but the outside world sees your router's IP `52.1.2.3`. Two devices behind different NATs **cannot directly connect** because neither knows the other's public address.

```
[User A: 192.168.1.5] ──NAT──► [Router: 52.1.2.3]
                                        │
                                        │ Can these two talk directly?
                                        │ Only if they know each other's
[User B: 10.0.0.8] ────NAT──► [Router: 203.0.113.7]     public IP:port
```

### STUN: "What's my public IP?" (~80% of calls)

STUN is a lightweight server that simply tells you how the outside world sees you:

```
[Client: 192.168.1.5] ──NAT──► [Router: 52.1.2.3:45123] ──► [STUN Server]
                                                                │
     "Your public address is 52.1.2.3:45123" ◄─────────────────┘
```

Now User A knows their public address and can share it with User B via the signaling channel. If both users' NATs allow incoming traffic on the discovered port, they can connect directly through the SFU. **This works for ~80% of calls.**

### TURN: Relay When Direct Fails (~20% of calls)

Corporate firewalls often use **symmetric NAT**, which assigns a different external port for each destination. STUN discovers port 45123, but when you try to reach the SFU on a different port, the NAT blocks it.

**Solution: Both sides connect outbound to a TURN relay server** (outbound connections almost always work, even through strict firewalls):

```
┌────────┐          ┌────────────┐          ┌────────┐
│ User A │ ════════►│   TURN     │◄════════ │ User B │
│        │ outbound │  (Relay)   │ outbound │        │
└────────┘          └────────────┘          └────────┘
                    Adds ~30-50ms latency
                    but ALWAYS works
```

**TURN over TCP/443**: When UDP is fully blocked (some enterprise networks), TURN can tunnel over TCP port 443 — the same port as HTTPS. This makes video traffic look like normal web traffic to the firewall.

### The ICE Framework: Trying Everything in Parallel

**ICE (Interactive Connectivity Establishment)** is the strategy for finding the best path. It tries all options simultaneously and picks the first one that works:

```
Priority order (ICE tries all in parallel):
  1. Direct host-to-host (same network)     ← fastest, ~0ms added latency
  2. STUN-discovered path (through NAT)      ← works 80% of the time, ~5ms
  3. TURN relay (when NAT is strict)         ← always works, ~30-50ms
  4. TURN over TCP/443 (UDP fully blocked)   ← last resort, ~50-100ms
```

---

## Scaling: From 1:1 Calls to Town Halls

Teams uses **three different architectures** depending on meeting size, because one size does NOT fit all:

```
┌──────────────────────────────────────────────────────────────────────────┐
│                    SCALING STRATEGY BY MEETING SIZE                       │
│                                                                          │
│  1:1 Calls (2 people)                                                   │
│  ├── Architecture: Direct WebRTC through SFU                            │
│  ├── Latency: <100ms                                                    │
│  └── Why: Simple, both users are interactive participants                │
│                                                                          │
│  Group Calls (3-300 people)                                             │
│  ├── Architecture: SFU with simulcast                                   │
│  ├── Latency: <200ms                                                    │
│  ├── Why: SFU scales linearly (each user uploads 1 stream)             │
│  └── Optimization: Selective subscription — only receive video for      │
│      participants visible on screen (not all 300)                       │
│                                                                          │
│  Town Halls / Webinars (300-10,000+ people)                             │
│  ├── Architecture: Presenters on SFU → Transcode → CDN → Viewers      │
│  ├── Latency: 10-30 seconds (acceptable for broadcast)                  │
│  ├── Why: CDN can serve 10,000 identical streams from edge caches      │
│  └── Tradeoff: Viewers are watch-only (interact via chat/reactions)     │
│                                                                          │
│  Cost comparison for 1000-person meeting:                               │
│  • Pure SFU: 1000 WebRTC connections = ~50 SFU servers = $$$           │
│  • CDN mode: 10 presenters on SFU + CDN = 1 SFU + CDN edge = $        │
└──────────────────────────────────────────────────────────────────────────┘
```

### Large Meeting CDN Architecture (300+ attendees)

```
   PRESENTERS (≤10)                                    ATTENDEES (1000+)
   ┌────────────────┐                                 ┌────────────────────────┐
   │ P1  P2  P3  P4 │    WebRTC (bidirectional)      │   A1  A2  A3  A4 ...  │
   └───────┬────────┘    <100ms latency               └───────────┬────────────┘
           │                                                       │
           ▼                                                       │
   ┌───────────────┐                                               │
   │      SFU      │  (Only for presenters — small scale)         │
   └───────┬───────┘                                               │
           │                                                       │
           ▼                                                       │
   ┌───────────────┐                                               │
   │ Azure Media   │  Transcode WebRTC → HLS/DASH                 │
   │ Services      │  Multiple quality levels:                     │
   │               │  1080p / 720p / 480p / 360p                   │
   └───────┬───────┘                                               │
           │                                                       │
           ▼                                                       │
   ┌───────────────────────────────────────────────────────────────┐
   │                         AZURE CDN                              │
   │   ┌────────┐  ┌────────┐  ┌────────┐                          │
   │   │ Edge   │  │ Edge   │  │ Edge   │ ◄────────────────────────│
   │   │ (US)   │  │ (EU)   │  │ (Asia) │      HLS/DASH pull
   │   └────────┘  └────────┘  └────────┘      (10-30 sec delay)
   └───────────────────────────────────────────────────────────────┘

   Attendee experience:
   • Watch-only video (one-way)
   • Q&A, reactions, and chat via separate WebSocket (SignalR)
   • Adaptive bitrate — CDN serves quality matching each viewer's bandwidth
```

---

## Recording: How It Works

Recording is NOT done by the sender or receiver — it's a **server-side bot that joins the call as a participant**:

```
   Normal participants:            Recording Bot:
   ┌─────┐  ┌─────┐               ┌──────────────────┐
   │  A  │  │  B  │               │  Recording Bot   │  ← Joins as invisible
   └──┬──┘  └──┬──┘               │  (server-side)   │     participant
      │        │                   └────────┬─────────┘
      │        │                            │
      ▼        ▼                            ▼
   ┌──────────────────────────────────────────┐
   │                  SFU                      │  SFU forwards ALL streams
   │  Forwards streams to ALL participants     │  to the recording bot,
   │  including the recording bot              │  just like any other peer
   └──────────────────────────────────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────┐
                    │ Azure Media Services       │
                    │ • Compose grid layout     │
                    │ • Encode to H.264/MP4     │
                    └───────────┬───────────────┘
                                │
                    ┌───────────▼───────────────┐
                    │ Azure Blob Storage         │
                    │ • Hot tier (first 30 days) │
                    │ • Cool tier (after 30 days)│
                    │ • Compliance retention     │
                    └───────────┬───────────────┘
                                │
                    ┌───────────▼───────────────┐
                    │ Post-Processing (async)    │
                    │ • Speech-to-text           │
                    │ • Speaker identification   │
                    │ • Searchable transcript    │
                    │ • Captions (VTT format)    │
                    └───────────────────────────┘
```

**Why a bot, not client-side recording?**
- **Reliability**: Client could crash, close laptop, lose connection. Server-side recording captures everything regardless
- **Compliance**: Enterprise recording policies (legal holds, GDPR retention) require server-controlled, tamper-proof storage
- **Compositing**: The recording bot receives all streams and can compose them into a single grid view — the same view you'd see in the meeting

---

## Screen Sharing: A Separate Media Track

Screen sharing runs as an **independent video track** alongside the camera track:

```
From User A's machine:

  ┌─────────────────┐     ┌──────────────────────────────────┐
  │ Camera          │────►│ Video Track 1 (camera)            │───► SFU
  │ (getUserMedia)  │     │ VP9, 720p, 30fps                  │
  └─────────────────┘     └──────────────────────────────────┘

  ┌─────────────────┐     ┌──────────────────────────────────┐
  │ Screen Capture  │────►│ Video Track 2 (screen)            │───► SFU
  │(getDisplayMedia)│     │ VP9/AV1, 1080p, 5-15fps           │
  └─────────────────┘     │ Content hint: "detail"            │
                          └──────────────────────────────────┘

  ┌─────────────────┐     ┌──────────────────────────────────┐
  │ Microphone      │────►│ Audio Track                       │───► SFU
  │ (getUserMedia)  │     │ Opus codec, 48kHz                 │
  └─────────────────┘     └──────────────────────────────────┘
```

**Why separate tracks?**
- **Independent bandwidth**: Screen share can use high resolution (text must be readable) at low framerate (slides don't move). Camera is the opposite — lower res but higher framerate
- **Content-adaptive encoding**: The `content hint` tells the encoder what kind of content this is:
  - `"detail"` (slides, code, documents) → maximize resolution, minimize framerate
  - `"motion"` (playing a video) → maximize framerate, accept lower resolution

---

## Interview Deep-Dive Topics

When interviewer asks "tell me more about X", reference these:

| Topic | Key Points | See |
|-------|------------|-----|
| **Large Meetings** | CDN mode, simulcast, selective subscription | [Deep Dive](video-deep-dive.md#large-meeting-architecture) |
| **Recording** | Server-side bot, Azure Blob, compliance | [Deep Dive](video-deep-dive.md#recording-architecture) |
| **Quality Adaptation** | RTCP feedback, bandwidth estimation, simulcast | [Deep Dive](video-deep-dive.md#quality-adaptation) |
| **Screen Sharing** | getDisplayMedia, content hints, separate track | [Deep Dive](video-deep-dive.md#screen-sharing-architecture) |
| **Failure Handling** | ICE restart, SFU failover, reconnection | [Deep Dive](video-deep-dive.md#call-flow-lifecycle) |

---

## Common Interview Questions

| Question | Quick Answer |
|----------|--------------|
| "How do you handle 1000-person calls?" | Switch to CDN mode: presenters on SFU, viewers pull HLS/DASH from CDN edge. 10-30s delay but infinitely scalable |
| "What if UDP is blocked?" | TURN relay over TCP/443 (tunnels through firewalls by looking like HTTPS traffic) |
| "How do you sync video and audio?" | RTP timestamps + jitter buffer. Receiver holds frames in a buffer to reorder late packets and maintain lip sync |
| "How do you handle network changes?" | ICE restart: re-gather candidates without dropping the call. New path discovered, media switches seamlessly |
| "Why not just use WebSockets for video?" | TCP retransmits lost packets, causing head-of-line blocking. A single lost packet delays ALL subsequent frames. UDP just drops it — one blurry frame is better than 500ms of frozen video |
| "How does the SFU decide which quality to send?" | Per-receiver: active speaker gets 720p, thumbnails get 180p. Bandwidth estimate from RTCP feedback, viewport size from client hints |
| "What about end-to-end encryption?" | Standard Teams: encrypted client-to-SFU (SRTP). E2EE mode: encrypted client-to-client (SFU only sees encrypted blobs, can't decode) |

---

## Azure Services Used

| Service | Role | Why This Service |
|---------|------|-----------------|
| **Azure Front Door** | Global entry, SSL termination | Anycast routing directs users to nearest region. Built-in DDoS protection |
| **Azure Kubernetes Service** | Run signaling & SFU pods | Auto-scaling: spin up more SFU pods during peak hours, scale down at night |
| **Azure Communication Services** | Managed TURN/STUN | Microsoft-operated relay network — no need to run your own TURN servers |
| **Azure Blob Storage** | Recording storage | Tiered storage (Hot → Cool → Archive) matches recording lifecycle. Compliance retention policies built-in |
| **Azure CDN** | Large meeting distribution | Edge caches in 100+ locations serve identical stream to thousands of viewers without overloading the origin |
| **Application Insights** | Call quality monitoring | Real-time dashboards for packet loss, jitter, bitrate. Alerts when quality degrades below thresholds |

---

## What to Skip Unless Asked

Don't volunteer these unless the interviewer digs — they're implementation details, not architecture:
- Codec negotiation details (VP8/VP9/H.264/AV1)
- SDP format specifics (the actual text format is ugly and not insightful)
- SRTP encryption mechanics (just say "all media is encrypted")
- Jitter buffer implementation (just say "buffer to handle packet reordering")
- Bandwidth estimation algorithms (just say "RTCP feedback loop")

**Focus on architecture decisions and trade-offs**, not protocol minutiae.

---

## Next Steps

- **[Deep Dive Document](video-deep-dive.md)** - Full architecture with wire-level details
- **[WebRTC Technology](../technologies/webrtc.md)** - Protocol fundamentals
- **[Back to Teams Overview](README.md)** - Other Teams subsystems
