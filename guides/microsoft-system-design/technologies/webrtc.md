# WebRTC — Technology Reference

> **What this guide covers**: WebRTC is the protocol stack that powers real-time video, audio, and data in Teams, Zoom, Google Meet, and every modern video calling app. This guide explains how it works, why each piece exists, and what trade-offs it makes.

---

## What WebRTC Is (And What It Isn't)

WebRTC (Web Real-Time Communication) is a **set of protocols + browser APIs** that enable peer-to-peer audio, video, and data communication directly between browsers — no plugins, no Flash, no installs.

- **Standardized by:** W3C (browser API) + IETF (wire protocols)
- **Developed by:** Google (acquired Global IP Solutions in 2010, open-sourced in 2011)
- **Supported by:** All major browsers (Chrome, Firefox, Safari, Edge) and native SDKs (iOS, Android, C++)

**What WebRTC gives you:**
- Camera/microphone capture via browser APIs
- Encrypted audio/video streaming over UDP
- NAT traversal (getting through firewalls)
- Bandwidth adaptation (adjusting quality in real-time)

**What WebRTC does NOT give you:**
- A signaling mechanism (you must build this yourself — WebSocket, HTTP, anything)
- A server for group calls (you need an SFU or MCU)
- Recording (that's a server-side concern)
- Chat (use WebSocket or DataChannel for text)

This distinction matters: WebRTC handles the **media transport**, but your application must handle everything around it — who calls whom, room management, permissions, etc.

---

## Key Terms

| Term | Full Name | What It Does | Plain English |
|------|-----------|--------------|---------------|
| **SDP** | Session Description Protocol | Describes what codecs, resolutions, and network endpoints each peer supports | "Here's my resume of capabilities" |
| **ICE** | Interactive Connectivity Establishment | Finds the best network path between peers by trying multiple paths in parallel | "Let me try every route and pick the fastest one that works" |
| **STUN** | Session Traversal Utilities for NAT | Lightweight server that tells you your public IP address | "Mirror that shows you how the internet sees you" |
| **TURN** | Traversal Using Relays around NAT | Relay server that forwards media when direct connection fails | "Middleman when the direct route is blocked" |
| **NAT** | Network Address Translation | Router that maps private IPs to a shared public IP | "Your apartment building's front desk — packages go in/out through one address" |
| **DTLS** | Datagram TLS | TLS handshake adapted for UDP — establishes encryption keys | "The handshake that sets up the secret code" |
| **SRTP** | Secure Real-time Transport Protocol | The actual encrypted audio/video packets | "Video data in a locked envelope" |
| **Candidate** | ICE Candidate | A possible IP:port path to reach a peer | "One of several doors you could knock on to reach me" |
| **Offer/Answer** | SDP Offer/Answer Model | Caller sends Offer (capabilities), callee responds with Answer (agreed subset) | "I propose → You accept what works" |

---

## The Big Picture: How a Call Gets Established

A WebRTC connection requires solving **three problems** in order:

1. **Signaling** — How do peers discover each other and agree on capabilities? (SDP)
2. **Connectivity** — How do peers reach each other through NATs and firewalls? (ICE/STUN/TURN)
3. **Media** — How does encrypted audio/video flow in real-time? (SRTP over UDP)

Here's the complete flow:

```
  PEER A                      SIGNALING SERVER                      PEER B
    │                              │                                   │
    │  ① Create Offer (SDP)        │                                   │
    │  "I support VP9, H.264,     │                                   │
    │   Opus audio, 720p max"      │                                   │
    │  ────────────────────────────►│                                   │
    │                              │  ② Forward Offer                  │
    │                              │  ─────────────────────────────────►│
    │                              │                                   │
    │                              │  ③ Create Answer (SDP)            │
    │                              │  "I'll use VP9, Opus,             │
    │                              │   here are my capabilities"       │
    │                              │  ◄─────────────────────────────────│
    │  ④ Receive Answer            │                                   │
    │  ◄────────────────────────────│                                   │
    │                              │                                   │
    │  ⑤ Exchange ICE Candidates (trickle, both directions)           │
    │  ◄────────────────────────────►───────────────────────────────►  │
    │     "Try reaching me at      │    "Try reaching me at            │
    │      192.168.1.5:8000,       │     10.0.0.8:9000,               │
    │      52.1.2.3:45123,         │     203.0.113.7:54321,           │
    │      or via TURN relay"      │     or via TURN relay"            │
    │                              │                                   │
    │══════════════════════════════════════════════════════════════════│
    │   ⑥ DIRECT MEDIA CONNECTION (UDP/SRTP, encrypted)               │
    │      (or through SFU for group calls)                            │
    │══════════════════════════════════════════════════════════════════│
```

**Key insight:** Steps ①-⑤ happen over your signaling server (TCP, reliable). Step ⑥ is direct peer-to-peer (UDP, real-time). The signaling server is like the phone book — it helps you find each other, but once you're connected, you talk directly.

---

## Step 1: Signaling — Peers Discover Each Other

### What Signaling Does

Before two peers can exchange media, they need to agree on:
- What video codecs they both support
- What audio codecs they both support
- What resolutions/framerates they can handle
- Where they are on the network (IP addresses)

This negotiation uses **SDP (Session Description Protocol)** — a text format that lists all capabilities.

### Why WebRTC Doesn't Define Signaling

This surprises many people: **WebRTC has no built-in signaling mechanism.** You must implement it yourself using any message transport — WebSocket, HTTP POST, Firebase, or even carrier pigeon (theoretically).

**Why this design choice?** Because different applications need different signaling:
- Teams uses SignalR (WebSocket) with room state management
- A simple demo might use Firebase Realtime Database
- A phone system might use SIP (Session Initiation Protocol)

By leaving signaling undefined, WebRTC stays flexible and doesn't force a specific server architecture.

### How SDP Negotiation Works

The negotiation follows an **Offer/Answer** model:

```
┌─────────────────────────────────────────────────────────────────────────┐
│  HOW SDP NEGOTIATION DECIDES WHAT CODECS TO USE                         │
│                                                                          │
│  1. SDP IS BUILT BY THE BROWSER (not your code)                         │
│     When you call createOffer(), the browser inspects:                   │
│     • Hardware: What camera/mic is available?                            │
│     • Codecs: What does this browser engine support?                     │
│     • Network: What are my local IPs?                                    │
│                                                                          │
│  2. SIGNALING SERVER IS JUST A MAILMAN                                   │
│     It passes SDP messages between peers.                                │
│     It doesn't read, modify, or understand them.                         │
│                                                                          │
│  3. RECEIVER PICKS THE INTERSECTION                                      │
│     The answer contains only codecs/formats both sides support:          │
│                                                                          │
│     Alice's Offer          Bob's Support         Bob's Answer            │
│     ─────────────          ──────────────         ───────────            │
│     Video: VP9, H.264      Video: H.264, VP9     Video: H.264 ✓        │
│     Audio: Opus, G.711     Audio: Opus            Audio: Opus ✓         │
│     Max res: 1080p         Max res: 720p          Max res: 720p ✓       │
│                                                                          │
│     Result: Both peers use H.264 video + Opus audio at up to 720p       │
└─────────────────────────────────────────────────────────────────────────┘
```

**Why the browser builds SDP, not your code:** SDP contains low-level codec parameters, DTLS fingerprints, and ICE credentials that depend on the browser's internal state. Letting the browser generate it ensures correctness and security.

### What Signaling Messages Look Like

```
Offer message (sent from A → signaling server → B):
{
  "type": "offer",
  "from": "user_a",
  "to": "user_b",
  "sdp": "v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\n..."
}

ICE candidate message (sent both directions, as discovered):
{
  "type": "ice-candidate",
  "from": "user_a",
  "to": "user_b",
  "candidate": {
    "candidate": "candidate:1 1 UDP 2122252543 192.168.1.100 54321 typ host",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

---

## Step 2: NAT Traversal — Reaching Through Firewalls

### Why This Is Hard

Most devices sit behind **NAT (Network Address Translation)** — your laptop has a private IP like `192.168.1.5`, but the internet sees your router's public IP `52.1.2.3`. Two devices behind different NATs **cannot directly connect** because neither knows the other's public address, and NAT routers block unsolicited inbound traffic.

This is the single biggest obstacle in WebRTC. Without solving NAT traversal, peer-to-peer connections would only work on the same local network.

### STUN: "What's My Public IP?" (~80% of calls)

**STUN (Session Traversal Utilities for NAT)** is a tiny, lightweight server with one job: tell you how the outside world sees your traffic.

**How it works mechanically:**

```
  Your Device              NAT Router                    STUN Server
  (Private IP)             (Rewrites src address)        (Public IP)
  192.168.1.10             203.0.113.5                   74.125.250.129
       │                        │                             │
       │  1. Send UDP packet    │                             │
       │     src: 192.168.1.10  │                             │
       │─────────────────────►  │                             │
       │                        │                             │
       │                        │  2. NAT rewrites source:    │
       │                        │     src → 203.0.113.5:54321 │
       │                        │     (assigns external port) │
       │                        │────────────────────────────►│
       │                        │                             │
       │                        │                             │  3. STUN reads the
       │                        │                             │     source IP:port
       │                        │                             │     from the UDP header
       │                        │                             │
       │                        │  4. Response:               │
       │                        │  "You appear as             │
       │                        │   203.0.113.5:54321"        │
       │                        │◄────────────────────────────│
       │                        │                             │
       │  5. Now you know your  │                             │
       │     public address!    │                             │
       │◄───────────────────────│                             │
       │                        │                             │
       │  This becomes your "server-reflexive candidate"      │
       │  (srflx) in ICE                                      │
```

**The trick:** The NAT router rewrites the source address on every outgoing packet. The STUN server just reads this rewritten address and echoes it back. There's no magic — it's just reading the UDP header.

Once you know your public IP:port, you share it with the other peer via signaling. If both peers' NATs allow traffic on the discovered ports, they can connect directly. **This works for ~80% of connections.**

### NAT Port Mapping — How Multiple Devices Share One Public IP

A natural question: "If my whole household shares one public IP, how does NAT know which device to route responses to?"

**Answer: Port mapping (NAPT).** Each device gets a unique external port:

```
  INSIDE NETWORK                    NAT ROUTER                 INTERNET
                                    Public IP: 203.0.113.5
                                         │
  ┌─────────────────┐                    │
  │ Alice's laptop  │                    │     NAT TABLE:
  │ 192.168.1.10    │──── port 5000 ────►│     ┌─────────────────────────────────┐
  └─────────────────┘                    │     │ Internal          → External    │
                                         │     ├─────────────────────────────────┤
  ┌─────────────────┐                    │     │ 192.168.1.10:5000 → :54321     │
  │ Bob's phone     │                    │     │ 192.168.1.11:5000 → :54322     │
  │ 192.168.1.11    │──── port 5000 ────►│     │ 192.168.1.12:5000 → :54323     │
  └─────────────────┘                    │     └─────────────────────────────────┘
                                         │
  ┌─────────────────┐                    │     Each device gets a DIFFERENT
  │ Carol's tablet  │                    │     external port, even though they
  │ 192.168.1.12    │──── port 5000 ────►│     all use the same private port
  └─────────────────┘                    │
```

When a response comes in to `203.0.113.5:54322`, the NAT router looks up its table and forwards to Bob's phone at `192.168.1.11:5000`. Each device discovers its own unique public address via STUN:

```
  Alice asks STUN → "You are 203.0.113.5:54321"
  Bob asks STUN   → "You are 203.0.113.5:54322"   ← Same IP, different port
  Carol asks STUN → "You are 203.0.113.5:54323"   ← Same IP, different port
```

### NAT Types and Why STUN Sometimes Fails

Not all NATs behave the same way. The critical difference is whether the NAT assigns the **same external port** regardless of destination, or a **different port per destination**:

```
CONE NAT (Most home routers) — STUN works ✓
─────────────────────────────────────────────
  Your device sends to ANY destination → Same external port (54321)

  192.168.1.10:5000 → STUN server  = 203.0.113.5:54321
  192.168.1.10:5000 → Peer B       = 203.0.113.5:54321  ← SAME PORT

  ✓ The port STUN discovered works for peer connections too
  ✓ ~80% of NATs work this way


SYMMETRIC NAT (Corporate firewalls) — STUN fails ✗
────────────────────────────────────────────────────
  Your device sends to DIFFERENT destination → DIFFERENT external port!

  192.168.1.10:5000 → STUN server  = 203.0.113.5:54321
  192.168.1.10:5000 → Peer B       = 203.0.113.5:61234  ← DIFFERENT PORT!

  ✗ STUN discovered port 54321, but peer B gets port 61234
  ✗ Peer B tries connecting to :54321 and fails — NAT only accepts on :61234
  ✗ Neither side can predict the other's actual port
```

| NAT Type | Behavior | STUN works? | Common in |
|----------|----------|-------------|-----------|
| **Full Cone** | Same port for all destinations, accepts any source | ✓ Yes | Home routers |
| **Restricted Cone** | Same port, only accepts from IPs you've sent to | ✓ Yes | Home routers |
| **Port Restricted** | Same port, only accepts from exact IP:port you've sent to | ✓ Yes | Some routers |
| **Symmetric** | Different port per destination | ✗ No | Corporate firewalls |

### TURN: The Relay Fallback (~20% of calls)

When STUN fails (symmetric NAT, or UDP fully blocked by a firewall), **TURN (Traversal Using Relays around NAT)** provides a relay server that both peers connect to:

```
┌────────┐   outbound    ┌────────────┐   outbound    ┌────────┐
│ User A │ ═════════════►│   TURN     │◄═════════════ │ User B │
│        │   (works      │  (Relay)   │   works       │        │
│        │   through     │            │   through     │        │
│        │   any NAT)    │  Forwards  │   any NAT)    │        │
└────────┘               │  all media │               └────────┘
                         └────────────┘
                         Adds ~30-50ms latency
```

**Why TURN always works:** Both peers make **outbound** connections to the TURN server. Outbound connections work through virtually any NAT or firewall because the NAT creates a mapping for return traffic. The TURN server then relays packets between the two connections.

**TURN over TCP/443:** When even UDP is blocked (some strict corporate networks), TURN can tunnel through TCP port 443 — the same port as HTTPS. This makes video traffic indistinguishable from normal web browsing to the firewall. It adds more latency (~50-100ms) but is the ultimate fallback.

### ICE: The Strategy That Ties It All Together

**ICE (Interactive Connectivity Establishment)** is the framework that orchestrates STUN, TURN, and direct connection attempts. It gathers all possible network paths ("candidates") and tests them **in parallel**, picking the best working path.

```
ICE gathers three types of candidates (in priority order):

  1. host    — Local IP address                        (fastest, ~0ms added)
               Only works if peers are on same network
               Example: candidate:1 UDP 192.168.1.100:54321 typ host

  2. srflx   — Server-Reflexive (STUN-discovered)      (works ~80%, ~5ms)
               Public IP:port learned from STUN
               Example: candidate:2 UDP 203.0.113.50:54321 typ srflx

  3. relay   — TURN relay                              (always works, ~30-50ms)
               Traffic routed through TURN server
               Example: candidate:3 UDP turn.example.com:3478 typ relay
```

**ICE Process:**
1. **Gather** all candidates (host, srflx, relay) — happens simultaneously
2. **Exchange** candidates with the other peer via signaling (they trickle in as discovered)
3. **Pair** each local candidate with each remote candidate (e.g., "my host with their srflx")
4. **Test** each pair with STUN connectivity checks (in parallel)
5. **Select** the best working pair (highest priority that succeeded)

**Why trickle ICE?** Candidates are discovered at different speeds — local IPs are instant, STUN takes a round-trip, TURN takes longer. Rather than waiting for ALL candidates before starting, **trickle ICE** sends each candidate to the other peer as soon as it's discovered. This dramatically reduces connection setup time.

---

## Step 3: Media Flow — Encrypted Real-Time Streaming

Once ICE establishes a working path, media begins flowing. Here's the protocol stack:

```
┌─────────────────────────────────────────────────────────────┐
│                    WEBRTC PROTOCOL STACK                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  APPLICATION                                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ getUserMedia() / getDisplayMedia() / DataChannel     │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
│  ENCRYPTION                                                  │
│  ┌─────────────────────┐ ┌──────────────────────────────┐   │
│  │ DTLS (key exchange) │ │ SRTP (encrypted media)       │   │
│  │ One-time handshake  │ │ Every audio/video packet     │   │
│  └─────────────────────┘ └──────────────────────────────┘   │
│                          │                                   │
│  TRANSPORT                                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ UDP (real-time, no retransmission)                   │   │
│  │ If UDP blocked: TCP fallback via TURN                │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
│  NETWORK                                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ICE (selected candidate pair)                        │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Why UDP, not TCP?**
This is a fundamental design choice. TCP guarantees delivery by retransmitting lost packets, but this creates **head-of-line blocking** — if packet #5 is lost, packets #6, #7, #8 wait in a buffer until #5 is retransmitted and arrives. In a video call, this means a single lost packet freezes ALL video for 50-200ms.

UDP simply drops lost packets. In a 30fps video stream, losing one frame means 33ms of slightly degraded video — users barely notice. That's far better than 200ms of frozen screen that TCP would cause.

**Security model:**
- **DTLS handshake** happens once at connection start — peers exchange certificates and derive encryption keys
- **SRTP** encrypts every subsequent media packet with AES-128 or AES-256
- Media is encrypted end-to-hop (client ↔ SFU) in standard mode, or end-to-end (client ↔ client) in E2EE mode

---

## Mid-Call Changes: Renegotiation

Once a call is established, the SDP describes what media is being exchanged. But calls aren't static — users share screens, switch cameras, people join and leave. **Renegotiation** is a new Offer/Answer exchange that modifies the active session.

### When Renegotiation Happens

| Trigger | What Changes | Example |
|---------|-------------|---------|
| Screen share starts | New video track added | `[audio][camera]` → `[audio][camera][screen]` |
| Screen share stops | Video track removed | `[audio][camera][screen]` → `[audio][camera]` |
| Camera switch | Track replaced | Laptop cam → external webcam |
| Participant joins (SFU) | New streams to receive | SFU signals new tracks to existing peers |
| Codec change | Different encoding | VP9 → VP8 (network degraded, need simpler codec) |
| Network change | New ICE candidates | WiFi → cellular (ICE restart — see below) |

### Renegotiation Flow (Screen Share Example)

```
  PEER A (starts sharing)              SIGNALING               PEER B (receives)
       │                                   │                         │
       │  ═══════════════════════════════════════════════════════════│
       │       EXISTING CONNECTION (audio + camera)                  │
       │  ═══════════════════════════════════════════════════════════│
       │                                   │                         │
       │  1. User clicks "Share Screen"    │                         │
       │     getDisplayMedia() captures    │                         │
       │     the screen                    │                         │
       │                                   │                         │
       │  2. addTrack(screenTrack)         │                         │
       │     Triggers 'negotiationneeded'  │                         │
       │     event automatically           │                         │
       │                                   │                         │
       │  3. Create NEW SDP offer          │                         │
       │     (now includes 3 tracks)       │                         │
       │  ───────────────────────────────► │                         │
       │                                   │  4. Forward new offer   │
       │                                   │  ───────────────────────►│
       │                                   │                         │
       │                                   │  5. Peer B sees new     │
       │                                   │     screen track in SDP │
       │                                   │                         │
       │                                   │  6. Create answer       │
       │                                   │     (accepts screen)    │
       │                                   │  ◄───────────────────────│
       │  7. Receive answer                │                         │
       │  ◄─────────────────────────────── │                         │
       │                                   │                         │
       │  ═══════════════════════════════════════════════════════════│
       │      NOW: audio + camera + screen share                     │
       │  ═══════════════════════════════════════════════════════════│
```

**Key point:** The media connection (UDP) stays alive during renegotiation. Only the SDP is re-exchanged over signaling (TCP). Users experience zero interruption — the screen share track appears seamlessly.

---

## Network Recovery: ICE Restart

When a user's network changes completely (WiFi drops → cellular connects), all existing ICE candidates become invalid because the IP addresses changed. **ICE Restart** gathers fresh candidates without tearing down the entire WebRTC session.

```
  Time 0:00 — On WiFi, call working fine
  ┌─────────────────┐                              ┌─────────────────┐
  │     Peer A      │══════════════════════════════│     Peer B      │
  │  WiFi: 10.0.0.5 │         Media flowing        │                 │
  │  Public: 1.2.3.4│                              │                 │
  └─────────────────┘                              └─────────────────┘

  Time 0:30 — WiFi drops, cellular connects
  ┌─────────────────┐                              ┌─────────────────┐
  │     Peer A      │       ╳ Connection lost! ╳   │     Peer B      │
  │  Cell: 10.1.1.1 │       (old IPs invalid)      │                 │
  │  Public: 5.6.7.8│                              │                 │
  └─────────────────┘                              └─────────────────┘

  ICE RESTART (takes ~5 seconds):
  ┌──────────────────────────────────────────────────────────────────┐
  │ 1. Peer A detects: iceConnectionState = 'disconnected'          │
  │ 2. Peer A calls restartIce() → generates NEW ICE credentials    │
  │ 3. Creates offer with { iceRestart: true }                      │
  │ 4. Sends via signaling (WebSocket — still works on cellular!)  │
  │ 5. Both peers gather fresh ICE candidates                       │
  │ 6. New candidate pairs tested → connection re-established       │
  └──────────────────────────────────────────────────────────────────┘

  Time 0:35 — Reconnected via cellular
  ┌─────────────────┐                              ┌─────────────────┐
  │     Peer A      │══════════════════════════════│     Peer B      │
  │  Cell: 10.1.1.1 │     Media flowing again!     │                 │
  │  Public: 5.6.7.8│                              │                 │
  └─────────────────┘                              └─────────────────┘
```

**Why this works:** The signaling channel (WebSocket over TCP) is **separate** from the media channel (UDP). When the media path dies, signaling can still coordinate the restart because it uses a different transport that reconnects independently.

---

## Group Calls: SFU vs MCU vs Mesh

For 1:1 calls, WebRTC can connect peers directly. For group calls, you need a server. There are three architectures:

### Architecture Comparison

```
MESH (P2P) — Each peer connects to every other peer
┌───────────────────────────┐
│     A ════ B              │   Each sends N-1 streams
│     ║ ╲  ╱ ║              │   Each receives N-1 streams
│     ║  ╲╱  ║              │   Total connections: N*(N-1)/2
│     C ════ D              │   4 users = 6 connections, 12 streams
│                           │
│   ❌ Upload bandwidth      │
│      explodes with N      │
└───────────────────────────┘

SFU (Selective Forwarding Unit) — Server forwards without decoding
┌──────────────────────────────────────────────────────────────┐
│              ┌───────────────────┐                            │
│  A ────────► │       SFU         │ ────────► B               │
│  B ────────► │  Just forwards    │ ────────► A               │
│  C ────────► │  (no transcoding) │ ────────► ...             │
│  D ────────► │                   │                            │
│              └───────────────────┘                            │
│                                                               │
│   Each sends 1 stream (simulcast layers)                     │
│   SFU selects which quality to forward per receiver          │
│   ✅ Low server CPU    ✅ Flexible quality per viewer        │
└──────────────────────────────────────────────────────────────┘

MCU (Multipoint Control Unit) — Server decodes, composites, re-encodes
┌──────────────────────────────────────────────────────────────┐
│              ┌───────────────────┐                            │
│  A ────────► │       MCU         │ ────────► All get single  │
│  B ────────► │  Decodes ALL      │           composited      │
│  C ────────► │  Makes grid view  │           stream          │
│  D ────────► │  Re-encodes       │                            │
│              └───────────────────┘                            │
│                                                               │
│   ❌ Enormous server CPU (decode + compose + encode)         │
│   ✅ Minimal client bandwidth (receives 1 stream)            │
└──────────────────────────────────────────────────────────────┘
```

| | Mesh | SFU ✅ | MCU |
|---|---|---|---|
| **Upload per user** | N-1 streams | 1 stream | 1 stream |
| **Download per user** | N-1 streams | N-1 streams | 1 stream |
| **Server CPU** | None | Low (forwarding) | Very high (transcoding) |
| **Server cost** | $0 | $ | $$$$ |
| **Latency added** | 0ms | <5ms | 50-200ms |
| **Max participants** | ~4 | ~300 | ~50 (CPU limited) |
| **Quality flexibility** | None | Per-receiver | Same for all |
| **Used by** | Simple demos | Teams, Zoom, Meet | Legacy systems |

**Why SFU dominates:** It's the sweet spot — each user uploads only ONE stream (in simulcast layers), the server does minimal work (just forwarding packets without decoding), and each receiver can get a different quality level. Teams, Zoom, and Google Meet all use SFU.

---

## Simulcast: Multiple Quality Layers

Simulcast solves a key SFU limitation: "If each user sends one stream, how does the SFU provide different qualities to different receivers?"

**Answer:** The sender encodes their video at **multiple quality levels simultaneously** and sends all of them to the SFU:

```
  ┌────────────────────┐
  │  Camera captures   │
  │  at 1080p          │
  └────────┬───────────┘
           │
   ┌───────┼───────┐
   ▼       ▼       ▼
┌──────┐┌──────┐┌──────┐
│1080p ││ 720p ││ 360p │   Three separate RTP streams
│2.5Mb ││1.0Mb ││0.3Mb │   encoded simultaneously
└──┬───┘└──┬───┘└──┬───┘
   │       │       │
   └───────┼───────┘
           ▼
       ┌───────┐
       │  SFU  │  Picks the right layer per receiver:
       └───┬───┘
           │
    ┌──────┼──────────┐
    ▼      ▼          ▼
  Bob    Carol      Dave
  720p   360p       1080p
  (WiFi) (mobile)  (ethernet)
```

**Why this matters in practice:** In a 9-person gallery view, the SFU sends:
- **Active speaker** → 720p (1.0 Mbps) — they're front and center
- **8 thumbnails** → 180p (0.1 Mbps each) — tiny views don't need quality
- **Total download**: ~1.8 Mbps — manageable even on mediocre WiFi

When bandwidth drops, the SFU instantly switches each receiver to a lower layer — no renegotiation, no delay, just lower quality.

---

## Code: Minimal Working Example

This shows the essential WebRTC API calls. In production, you'd add error handling, renegotiation, ICE restart, and UI logic.

```javascript
// ─── 1. Capture local camera and microphone ───
const localStream = await navigator.mediaDevices.getUserMedia({
  video: { width: 1280, height: 720 },
  audio: true
});
localVideo.srcObject = localStream;  // show preview

// ─── 2. Create peer connection with STUN/TURN servers ───
const pc = new RTCPeerConnection({
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },          // free Google STUN
    { urls: 'turn:turn.example.com:3478',                // your TURN relay
      username: 'user', credential: 'pass' }
  ]
});

// ─── 3. Add local tracks to the connection ───
localStream.getTracks().forEach(track => {
  pc.addTrack(track, localStream);
});

// ─── 4. Handle incoming remote tracks ───
pc.ontrack = (event) => {
  remoteVideo.srcObject = event.streams[0];  // display remote video
};

// ─── 5. Send ICE candidates to the other peer as discovered ───
pc.onicecandidate = (event) => {
  if (event.candidate) {
    signaling.send({ type: 'ice-candidate', candidate: event.candidate });
  }
};

// ─── 6. Create and send SDP offer ───
const offer = await pc.createOffer();
await pc.setLocalDescription(offer);
signaling.send({ type: 'offer', sdp: offer.sdp });

// ─── 7. When answer arrives from the other peer ───
signaling.onmessage = async (msg) => {
  if (msg.type === 'answer') {
    await pc.setRemoteDescription(new RTCSessionDescription(msg));
  } else if (msg.type === 'ice-candidate') {
    await pc.addIceCandidate(new RTCIceCandidate(msg.candidate));
  }
};
```

**What each step does:**
1. `getUserMedia` asks the browser for camera/mic access (user sees permission prompt)
2. `RTCPeerConnection` is the core WebRTC object — manages the entire connection lifecycle
3. `addTrack` tells WebRTC "include this audio/video in the connection"
4. `ontrack` fires when the remote peer's media arrives — attach it to a `<video>` element
5. `onicecandidate` fires as ICE discovers network paths — send each to the other peer
6. `createOffer` generates the SDP, `setLocalDescription` activates it
7. When the answer comes back, `setRemoteDescription` completes the handshake

---

## When to Use WebRTC

✅ **Use WebRTC for:**
- Video/audio calls (1:1 or group via SFU)
- Screen sharing
- Peer-to-peer file transfer (DataChannel)
- Live streaming (broadcaster side, sub-second latency)
- Real-time gaming (DataChannel for game state)
- IoT camera streaming

❌ **Don't use WebRTC for:**
- One-to-many broadcast to thousands (use HLS/DASH — CDN-friendly, much cheaper)
- Text chat (use WebSocket — simpler, TCP is fine for text)
- File storage/sharing (use HTTP upload — more reliable)
- Server-side recording (use a recording bot that joins as a participant)

**The decision rule:** If you need **sub-200ms latency** and **bidirectional** communication, WebRTC is the right tool. If you can tolerate 5-30 second delay (broadcast) or only need one-way data (file download), simpler protocols work better.

---

## Trade-offs Summary

| Advantage | Disadvantage |
|-----------|-------------|
| Ultra-low latency (<200ms) | Complex to implement correctly |
| Encrypted by default (DTLS/SRTP) | NAT traversal adds infrastructure (STUN/TURN servers) |
| No browser plugins needed | Debugging is hard (ICE failures, codec mismatches are opaque) |
| Adaptive bitrate (responds to network changes) | TURN server costs money (~$0.40/GB relayed) |
| Works cross-platform (browser, mobile, desktop) | Browser implementations differ subtly |
| P2P reduces server load | Group calls still need SFU server infrastructure |

---

## Where WebRTC Appears in This Guide

| System Design | How WebRTC Is Used |
|---------------|-------------------|
| **[Teams Video & Calls](../01-microsoft-teams/video.md)** | Core media transport for all video calls |
| **[Teams Video Deep Dive](../01-microsoft-teams/video-deep-dive.md)** | Detailed call flow with SFU, simulcast, quality adaptation |
| **Collaborative Editing** | DataChannel for real-time cursor positions and edits |