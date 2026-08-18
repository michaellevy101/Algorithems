# 31. Design Teams Video Conferencing

**Key Terms:**

| Term | Full Name | What it does |
|------|-----------|--------------|
| **WebRTC** | Web Real-Time Communication | Browser API for peer-to-peer audio/video |
| **SFU** | Selective Forwarding Unit | Server that forwards video without transcoding |
| **MCU** | Multipoint Control Unit | Server that mixes/transcodes all streams |
| **TURN** | Traversal Using Relays around NAT | Relay server when direct connection fails |
| **ICE** | Interactive Connectivity Establishment | Protocol to find best connection path |
| **Simulcast** | Multiple quality streams | Sender encodes multiple resolutions |
| **NAT** | Network Address Translation | Router hiding local IPs behind public IP |

## Problem Statement
Design a video conferencing system like Microsoft Teams Meetings that supports thousands of concurrent meetings with features like screen sharing, recording, and live captions.

## Clarifying Questions to Ask

1. **Scale**: Max participants per meeting? Concurrent meetings globally?
2. **Features**: Video, audio, screen share, recording, live captions?
3. **Latency**: Max acceptable delay? (<150ms for real-time?)
4. **Quality**: Adaptive bitrate? Resolution options?
5. **Recording**: Where stored? Who can access?
6. **Breakout rooms**: Support splitting into smaller groups?
7. **Integration**: Calendar, chat, file sharing during meeting?

## Design Reasoning

> **Core philosophy**: Minimize latency and maximize video quality through selective forwarding and adaptive streaming, keeping media routing on the server while avoiding costly transcoding.

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Media architecture | SFU (Selective Forwarding Unit) | Lower server CPU, no quality loss from transcoding; scales to 1000 participants | MCU (Multipoint Control Unit) — high CPU cost, quality loss from mixing streams |
| Streaming protocol | WebRTC | Browser-native, no plugin required, built-in NAT traversal and encryption | Custom protocol — requires client installation, longer development cycle |
| Signaling transport | WebSocket (SignalR) | Low-latency bidirectional signaling for ICE negotiation and session setup | HTTP polling — too slow for real-time session negotiation |
| Quality adaptation | Simulcast (multiple resolutions) | Receivers pick quality based on available bandwidth; no server transcoding needed | Single stream — poor experience on slow connections, no adaptation |
| NAT traversal | TURN relay servers | Guarantees connectivity when direct P2P fails (~15% of users behind symmetric NAT) | STUN only — fails for users behind symmetric NAT |
| Recording | Server-side composite | Reliable capture regardless of participant's client state or disconnection | Client-side recording — unreliable, depends on participant's device and connection |
| Live captions | Azure Speech Services | Real-time transcription with enterprise-grade accuracy and multi-language support | On-device speech-to-text — inconsistent quality, high client CPU usage |

## High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│              VIDEO CONFERENCING ARCHITECTURE                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      PARTICIPANTS                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │ User A   │  │ User B   │  │ User C   │  │ User D   │        │
│  │(Camera)  │  │(Screen)  │  │(Audio)   │  │(Viewer)  │        │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘        │
└───────┼─────────────┼─────────────┼─────────────┼───────────────┘
        │ WebRTC      │ WebRTC      │ WebRTC      │ WebRTC
        └─────────────┼─────────────┼─────────────┘
                      │             │
                      ▼             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   MEDIA SERVER (SFU)                             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Selective Forwarding Unit                                │   │
│  │                                                           │   │
│  │  - Receives streams from all participants                │   │
│  │  - Selectively forwards based on:                        │   │
│  │    • Active speaker detection                            │   │
│  │    • Participant's bandwidth                             │   │
│  │    • Layout preferences (gallery vs speaker view)        │   │
│  │                                                           │   │
│  │  User A ──┐                    ┌──▶ User A (B,C streams) │   │
│  │  User B ──┼──▶ SFU Server ────┼──▶ User B (A,C streams) │   │
│  │  User C ──┘                    └──▶ User C (A,B streams) │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────────┘
                          │
    ┌─────────────────────┼─────────────────────┐
    ▼                     ▼                     ▼
┌─────────────┐    ┌─────────────┐      ┌─────────────┐
│  Recording  │    │  Signaling  │      │ Transcription│
│  Service    │    │  Server     │      │  Service    │
│             │    │             │      │             │
│ (Azure Blob)│    │ (WebSocket) │      │ (Speech AI) │
└─────────────┘    └─────────────┘      └─────────────┘
```


## API Design

### Meeting APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/meetings` | Create a meeting |
| `POST` | `/api/v1/meetings/{meetingId}/join` | Join a meeting |
| `POST` | `/api/v1/meetings/{meetingId}/leave` | Leave a meeting |
| `GET` | `/api/v1/meetings/{meetingId}` | Get meeting details |
| `GET` | `/api/v1/meetings/{meetingId}/participants` | List participants |

### Media Control APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `PUT` | `/api/v1/meetings/{meetingId}/media` | Toggle audio/video |
| `POST` | `/api/v1/meetings/{meetingId}/screenshare/start` | Start screen sharing |
| `POST` | `/api/v1/meetings/{meetingId}/screenshare/stop` | Stop screen sharing |
| `PUT` | `/api/v1/meetings/{meetingId}/layout` | Change video layout |

### Signaling WebSocket Events
| Event | Direction | Payload |
|-------|-----------|---------|
| `participant.joined` | Server → Client | `{ userId, name, mediaState }` |
| `participant.left` | Server → Client | `{ userId }` |
| `media.offer` | Client → Server | `{ sdp }` |
| `media.answer` | Server → Client | `{ sdp }` |
| `media.ice` | Both | `{ candidate }` |
| `media.state` | Server → Client | `{ userId, audio, video }` |


## Key Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| Signaling Server | Session setup, ICE negotiation | WebSocket |
| SFU (Selective Forwarding) | Route media streams | Mediasoup, Janus |
| TURN Server | NAT traversal relay | CoTURN |
| Recording Service | Capture and store meetings | FFmpeg + Blob Storage |
| Transcription | Live captions | Azure Speech Services |
| Quality Monitor | Adapt to network conditions | Custom metrics |

## Media Architecture: SFU vs MCU

```
SFU (Selective Forwarding Unit) - RECOMMENDED:
┌─────────────────────────────────────────────────────────────────┐
│  Each participant sends ONE stream to server                   │
│  Server forwards selected streams to each participant          │
│                                                                  │
│  Participant A ─────▶ SFU ─────▶ Participant B                 │
│                      │     └───▶ Participant C                 │
│  Participant B ─────▶│                                         │
│  Participant C ─────▶│                                         │
│                                                                  │
│  Pros: Lower server CPU, better quality (no transcoding)       │
│  Cons: More bandwidth per client                               │
│  Best for: Teams meetings (up to 1000 participants)           │
└─────────────────────────────────────────────────────────────────┘

MCU (Multipoint Control Unit):
┌─────────────────────────────────────────────────────────────────┐
│  Server decodes all streams, mixes into one                    │
│  Each participant receives single mixed stream                 │
│                                                                  │
│  Pros: Low client bandwidth                                     │
│  Cons: High server CPU, quality loss from transcoding          │
│  Best for: Webinars with many passive viewers                  │
└─────────────────────────────────────────────────────────────────┘
```

## Scalability Considerations

- **Regional SFU servers**: Deploy media servers close to participants
- **Cascading**: Connect SFUs across regions for global meetings
- **Simulcast**: Sender transmits multiple quality levels
- **Bandwidth estimation**: Adapt quality based on network conditions
- **Large meetings**: Switch to broadcast mode for 100+ participants

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| Architecture | SFU | MCU | **SFU** - better quality |
| Protocol | WebRTC | Custom | **WebRTC** - browser native |
| Quality | Fixed | Adaptive | **Adaptive** - handles poor networks |
| Recording | Client-side | Server-side | **Server** - reliable |

## Technologies Deep Dive

📚 **Learn more about the key technologies used in this design:**

| Technology | Role in This Design | Reference |
|------------|---------------------|-----------|
| WebRTC | Peer-to-peer media streaming protocol | [Tech Guide](technologies/webrtc.md) |
| SignalR | Real-time signaling and session management | [Tech Guide](technologies/signalr.md) |
| CDN | Media relay and content distribution | [Tech Guide](technologies/cdn.md) |

---

## How to Explain It Well

> **Opening**: "Video conferencing needs low latency and adaptive quality. I'll use SFU architecture with WebRTC for peer-to-peer media."

> **Key talking points**:
> - "SFU: each user sends one stream, server selectively forwards"
> - "Simulcast: sender transmits 3 qualities, receiver picks based on bandwidth"
> - "TURN servers for NAT traversal when direct connection fails"
> - "Active speaker detection reduces streams to forward"
> - "Recording is server-side composite for reliability"

---