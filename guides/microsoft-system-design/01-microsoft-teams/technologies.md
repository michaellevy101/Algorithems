# Technologies Reference

[← Back to Overview](./README.md)

This page links to the **common technology reference** used across all system design questions.

---

## Technologies Used in Teams

| Technology | Used For | Deep Dive |
|------------|----------|-----------|
| **WebRTC** | Video/audio calls, screen sharing | [WebRTC Guide](../technologies/webrtc.md) |
| **SignalR** | Real-time message delivery, presence | [SignalR Guide](../technologies/signalr.md) |
| **WebSocket** | Persistent bidirectional connection | [WebSocket Guide](../technologies/websocket.md) |
| **Redis** | Presence tracking (TTL), caching | [Redis Guide](../technologies/redis.md) |
| **Cosmos DB** | Message storage (partitioned) | [Cosmos DB Guide](../technologies/cosmos-db.md) |
| **Kafka** | Message fanout via Event Hubs | [Kafka Guide](../technologies/kafka.md) |
| **CDN** | File delivery, static assets | [CDN Guide](../technologies/cdn.md) |
| **AKS** | Container orchestration | [AKS Guide](../technologies/aks-kubernetes.md) |

---

## Quick Reference for Interview

| When asked about... | Technology | Key point |
|---------------------|------------|-----------|
| "How do video calls work?" | [WebRTC](../technologies/webrtc.md) | P2P with SFU for groups, STUN/TURN for NAT |
| "How do messages arrive instantly?" | [SignalR](../technologies/signalr.md) | WebSocket with auto-reconnect, Azure managed |
| "How do you track who's online?" | [Redis](../technologies/redis.md) | TTL-based keys, heartbeat every 30s |
| "How do you scale to millions?" | [Cosmos DB](../technologies/cosmos-db.md) | Partition by channelId, multi-region |
| "How do you handle offline users?" | Push + Sync | Messages in Cosmos, sync on reconnect |

---

## All Technologies

See the complete [Technology Reference](../technologies/README.md) for:
- Databases & Storage (Redis, Kafka, PostgreSQL, Elasticsearch, ClickHouse)
- Real-time (WebSocket, SignalR, WebRTC)
- Infrastructure (CDN, Load Balancer, AKS)
- Algorithms (Consistent Hashing, Geohash, Raft, Bloom Filter)
- Patterns (Saga, Circuit Breaker, Rate Limiter)

---

[← Back to Overview](./README.md)
