# Technology Reference

Quick reference for technologies used in system design interviews. Each page explains how the technology works, when to use it, and common trade-offs.

## 📂 Contents

### Databases & Storage
| Technology | Description |
|------------|-------------|
| [Redis](redis.md) | In-memory cache, pub/sub, data structures |
| [Kafka](kafka.md) | Distributed event streaming platform |
| [PostgreSQL](postgresql.md) | Relational database with advanced features |
| [Cosmos DB](cosmos-db.md) | Azure globally distributed NoSQL |
| [Elasticsearch](elasticsearch.md) | Full-text search and analytics engine |
| [ClickHouse](clickhouse.md) | Columnar OLAP database for analytics |
| [Cassandra](cassandra.md) | Wide-column distributed NoSQL |
| [LSM Tree](lsm-tree.md) | Log-structured merge tree (storage engine) |
| [Vector Databases](vector-databases.md) | Embedding storage, ANN search, RAG |

### Messaging & Real-time
| Technology | Description |
|------------|-------------|
| [WebSocket](websocket.md) | Bidirectional real-time communication |
| [SignalR](signalr.md) | Azure real-time messaging abstraction |
| [WebRTC](webrtc.md) | Peer-to-peer audio/video/data |
| [Flink & Spark](flink-spark.md) | Stream and batch processing |

### Networking & Infrastructure
| Technology | Description |
|------------|-------------|
| [CDN](cdn.md) | Content delivery network |
| [Load Balancer](load-balancer.md) | Traffic distribution patterns |
| [AKS & Kubernetes](aks-kubernetes.md) | Container orchestration |
| [Circuit Breaker](circuit-breaker.md) | Fault tolerance pattern |

### Algorithms & Data Structures
| Technology | Description |
|------------|-------------|
| [Consistent Hashing](consistent-hashing.md) | Distributed key mapping |
| [Geohash](geohash.md) | Geospatial encoding |
| [Raft Consensus](raft-consensus.md) | Distributed consensus algorithm |
| [QuadTree](quadtree.md) | Spatial indexing structure |
| [Bloom Filter](bloom-filter.md) | Probabilistic set membership |
| [HyperLogLog](hyperloglog.md) | Cardinality estimation |

### Patterns & Architecture
| Technology | Description |
|------------|-------------|
| [Saga Pattern](saga-pattern.md) | Distributed transaction coordination |
| [Rate Limiter](rate-limiter.md) | Traffic control algorithms |

### Security
| Technology | Description |
|------------|-------------|
| [HSM](hsm.md) | Hardware security module |

---

## 🎯 Quick Reference by Use Case

| Need | Technology |
|------|------------|
| Cache data | Redis |
| Message queue | Kafka |
| Full-text search | Elasticsearch |
| Real-time updates | WebSocket, SignalR |
| Video calls | WebRTC |
| Analytics queries | ClickHouse |
| Geo queries | Geohash, QuadTree |
| Distributed consensus | Raft |
| Prevent overselling | Redis atomic ops |
| Handle failures | Circuit Breaker, Saga |
| Semantic / similarity search | Vector Databases (pgvector, Qdrant, Pinecone) |
| RAG (LLM grounding) | Vector Databases + Azure AI Search |
