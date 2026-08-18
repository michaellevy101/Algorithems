# Apache Kafka

## What It Is
Kafka is a distributed event streaming platform for high-throughput, fault-tolerant, real-time data pipelines. It acts as a durable message queue that can handle millions of events per second.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              KAFKA ARCHITECTURE                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

PRODUCERS                         KAFKA CLUSTER                        CONSUMERS
┌─────────┐                                                           ┌─────────┐
│ App 1   │───┐                  ┌─────────────────┐                ┌─│Consumer │
└─────────┘   │                  │     TOPIC:      │                │ │ Group A │
┌─────────┐   │    write         │   "orders"      │     read       │ └─────────┘
│ App 2   │───┼─────────────────▶│                 │───────────────▶│
└─────────┘   │                  │ ┌─────────────┐ │                │ ┌─────────┐
┌─────────┐   │                  │ │ Partition 0 │ │                └─│Consumer │
│ App 3   │───┘                  │ │ [0,1,2,3,4] │ │                  │ Group B │
└─────────┘                      │ └─────────────┘ │                  └─────────┘
                                 │ ┌─────────────┐ │
                                 │ │ Partition 1 │ │
                                 │ │ [0,1,2,3]   │ │
                                 │ └─────────────┘ │
                                 │ ┌─────────────┐ │
                                 │ │ Partition 2 │ │
                                 │ │ [0,1,2]     │ │
                                 │ └─────────────┘ │
                                 └─────────────────┘

KEY CONCEPTS:
  • Topic: Category/feed of messages (like a table)
  • Partition: Ordered, immutable sequence of messages
  • Offset: Position of message within partition
  • Consumer Group: Set of consumers sharing the work
```

## Topics and Partitions

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              PARTITIONING                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Topic: "user-events" with 3 partitions

Producer sends: {user_id: 123, event: "login"}
  │
  │  Partition = hash(user_id) % num_partitions
  │            = hash(123) % 3 = 1
  │
  ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Partition 0: [msg0, msg3, msg6, msg9, ...]    ← user_ids where hash % 3 = 0          │
│  Partition 1: [msg1, msg4, msg7, msg10, ...]   ← user_ids where hash % 3 = 1  ✓       │
│  Partition 2: [msg2, msg5, msg8, msg11, ...]   ← user_ids where hash % 3 = 2          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

WHY PARTITIONS?
  1. Parallelism: Each partition can be consumed independently
  2. Ordering: Messages within a partition are strictly ordered
  3. Scalability: Add partitions to increase throughput
  
ORDERING GUARANTEE:
  • Within partition: Total order (first in, first out)
  • Across partitions: No ordering guarantee
  • Use same key for related events (e.g., user_id) to maintain order
```

## Replication

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              REPLICATION (Fault Tolerance)                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Replication factor = 3 (each partition has 3 copies)

                    Broker 1              Broker 2              Broker 3
                   ┌─────────┐           ┌─────────┐           ┌─────────┐
Partition 0:       │ LEADER  │──────────▶│ Follower│──────────▶│ Follower│
                   └─────────┘           └─────────┘           └─────────┘
                   
                   ┌─────────┐           ┌─────────┐           ┌─────────┐
Partition 1:       │ Follower│◀──────────│ LEADER  │──────────▶│ Follower│
                   └─────────┘           └─────────┘           └─────────┘
                   
                   ┌─────────┐           ┌─────────┐           ┌─────────┐
Partition 2:       │ Follower│◀──────────│ Follower│◀──────────│ LEADER  │
                   └─────────┘           └─────────┘           └─────────┘

• Producers write to LEADER only
• Followers replicate from leader
• If leader fails, a follower is elected as new leader
• ISR (In-Sync Replicas): Followers that are caught up
```

## Consumer Groups

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              CONSUMER GROUPS                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Topic with 4 partitions, Consumer Group with 2 consumers:

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Partition 0 ─────┐                                                                     │
│  Partition 1 ─────┼───────────▶ Consumer 1 (handles P0, P1)                            │
│                   │                                                                     │
│  Partition 2 ─────┼───────────▶ Consumer 2 (handles P2, P3)                            │
│  Partition 3 ─────┘                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

RULES:
  • Each partition assigned to exactly ONE consumer in the group
  • A consumer can handle multiple partitions
  • Max useful consumers = number of partitions
  
MULTIPLE CONSUMER GROUPS:
  • Each group gets ALL messages (independent offsets)
  • Use case: Same data → analytics + search + notifications
```

## Delivery Semantics

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              DELIVERY GUARANTEES                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘

AT-MOST-ONCE (may lose messages):
  1. Consumer reads message
  2. Commits offset
  3. Processes message  ← If crash here, message lost
  
AT-LEAST-ONCE (may duplicate, DEFAULT):
  1. Consumer reads message
  2. Processes message
  3. Commits offset     ← If crash before commit, reprocessed
  
EXACTLY-ONCE (hardest):
  • Idempotent producer (enable.idempotence=true)
  • Transactional writes
  • Consumer: Read committed + idempotent processing
```

## Producer Configuration

```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka1:9092,kafka2:9092");
props.put("key.serializer", "StringSerializer");
props.put("value.serializer", "StringSerializer");

// Durability settings
props.put("acks", "all");           // Wait for all replicas
props.put("retries", 3);            // Retry on failure
props.put("enable.idempotence", true);  // Exactly-once semantics

// Performance settings
props.put("batch.size", 16384);     // Batch messages
props.put("linger.ms", 5);          // Wait up to 5ms to batch
props.put("compression.type", "snappy");  // Compress batches

KafkaProducer<String, String> producer = new KafkaProducer<>(props);

// Send message
producer.send(new ProducerRecord<>("orders", "user123", orderJson));
```

## Consumer Configuration

```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka1:9092,kafka2:9092");
props.put("group.id", "order-processor");
props.put("key.deserializer", "StringDeserializer");
props.put("value.deserializer", "StringDeserializer");

// Offset management
props.put("auto.offset.reset", "earliest");  // Start from beginning if no offset
props.put("enable.auto.commit", false);      // Manual commit for at-least-once

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("orders"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        processOrder(record.value());
    }
    consumer.commitSync();  // Commit after processing
}
```

## Common Patterns

### Event Sourcing
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  All changes stored as events (append-only log)                                         │
│                                                                                          │
│  Topic: "account-events"                                                                │
│  [AccountCreated] → [MoneyDeposited] → [MoneyWithdrawn] → [MoneyDeposited]             │
│                                                                                          │
│  Current state = replay all events                                                      │
│  Audit trail built-in                                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### CQRS (Command Query Responsibility Segregation)
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│   Commands ───▶ Kafka ───▶ Command Handler ───▶ Write DB (PostgreSQL)                  │
│                   │                                                                      │
│                   └───▶ Event Processor ───▶ Read DB (Elasticsearch)                   │
│                                                     │                                    │
│   Queries ◀─────────────────────────────────────────┘                                   │
│                                                                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## When to Use

✅ **Good for:**
- Decoupling microservices
- Event-driven architecture
- Log aggregation
- Stream processing (with Flink/Spark)
- Data pipelines (ETL)
- Audit trails / event sourcing
- Buffering for traffic spikes

❌ **Not ideal for:**
- Request-response (use HTTP/gRPC)
- Low-latency (<10ms) requirements
- Small deployments (operational overhead)
- Simple job queues (consider Redis, RabbitMQ)

## Trade-offs

| Pros | Cons |
|------|------|
| Extremely high throughput | Operational complexity |
| Durable (persisted to disk) | Not designed for request-response |
| Horizontal scaling | Partition count hard to change |
| Replay capability | Learning curve |
| Strong ordering (per partition) | No global ordering |

## Kafka vs Alternatives

| Feature | Kafka | RabbitMQ | Redis Pub/Sub |
|---------|-------|----------|---------------|
| Throughput | Very high | Medium | High |
| Persistence | Yes (disk) | Optional | No |
| Ordering | Per partition | Per queue | No |
| Replay | Yes | No | No |
| Complexity | High | Medium | Low |

## Used In (This Guide)

- **Teams (Q1)**: Message fanout to users
- **Notification Service (Q11)**: Async notification delivery
- **Video Streaming (Q18)**: Event pipeline
- **Real-time Log Processing (Q25)**: Log ingestion
- **Web Analytics (Q44)**: Event collection
- **E-commerce (Q46)**: Order events
