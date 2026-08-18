# Interview Tips

[← Back to Overview](./README.md) | [Databases](./databases.md) | [Technologies](./technologies.md)

---

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| Message storage | SQL | NoSQL (Cosmos) | **Cosmos** - flexible schema, multi-region |
| Real-time delivery | Polling | WebSocket | **WebSocket** - low latency |
| Video architecture | Mesh | SFU | **SFU** - scales to 1000s |
| Large meetings | SFU only | SFU + CDN broadcast | **Hybrid** - CDN for 100+ attendees |
| Consistency | Strong | Eventual | **Eventual** - availability > consistency |
| Recording | Client-side | Server-side | **Server-side** - reliable, compliant |
| Search | SQL LIKE | Dedicated search | **Azure AI Search** - full-text, fast |

---

## How to Explain It Well

> **Opening (30 sec)**: "Teams is a real-time collaboration platform. I'll design for 300M DAU supporting chat, video calls up to 1000 participants, and file sharing with 99.99% availability across multiple Azure regions."

> **Structure your answer**:
> 1. Clarify requirements (scale, features, compliance)
> 2. Draw high-level architecture with Azure services
> 3. Deep dive into real-time messaging (SignalR + Cosmos)
> 4. Explain video architecture (WebRTC + SFU + simulcast)
> 5. Discuss large meeting optimization (CDN broadcast)
> 6. Address scalability and trade-offs

> **Key talking points**:
> - "Real-time messaging uses Azure SignalR Service - managed WebSocket at scale"
> - "Messages stored in Cosmos DB partitioned by channel for locality"
> - "Video uses SFU architecture with simulcast - sender encodes 3 qualities, SFU selects per receiver"
> - "Large meetings (100+) switch to CDN broadcast mode for cost efficiency"
> - "TURN servers handle NAT traversal for ~20% of calls that can't go P2P"
> - "Recording bot joins as participant, streams to Media Services for encoding"

---

## Common Follow-up Questions

1. **How do you handle message ordering?**
   - Cosmos DB timestamp + client-side reordering
   - Event Hubs partitioned by channel ensures order within partition

2. **What if SFU server crashes mid-call?**
   - Client reconnects to different SFU
   - ICE restart negotiates new connection
   - Brief interruption (~2-3 seconds)

3. **How do you prevent echo in calls?**
   - Acoustic Echo Cancellation (AEC) on client
   - WebRTC includes AEC in getUserMedia

4. **How do you handle 1000-person meeting?**
   - Presenters on SFU (bidirectional)
   - Attendees receive via CDN (HLS/DASH broadcast)
   - Q&A/reactions via separate WebSocket channel

5. **How is end-to-end encryption handled?**
   - Currently Teams uses TLS in transit + encryption at rest
   - E2EE for 1:1 calls available (keys never leave client)
   - Group E2EE challenging with SFU (server can't forward what it can't read)
---

## All Pages

- [Overview](./README.md) - Architecture, components
- [Chat & Messaging](./chat.md) - Real-time messaging, notifications
- [Video & Calls](./video.md) - WebRTC, SFU, recording
- [File Sharing](./files.md) - Upload flow, CDN
- [Databases](./databases.md) - All database configurations

[← Back to Overview](./README.md)
