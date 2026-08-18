# Cassandra

## What It Is
Apache Cassandra is a distributed NoSQL database designed for high availability and linear scalability. It's optimized for write-heavy workloads and can handle massive amounts of data across many commodity servers with no single point of failure.

## Key Features

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CASSANDRA HIGHLIGHTS                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. DISTRIBUTED & DECENTRALIZED
   • No master node (peer-to-peer)
   • Every node can accept reads/writes
   • No single point of failure
   
2. LINEAR SCALABILITY
   • Add nodes → Increase throughput linearly
   • Handles petabytes of data
   
3. TUNABLE CONSISTENCY
   • Choose consistency level per query
   • Trade-off between consistency and availability
   
4. WIDE-COLUMN STORE
   • Flexible schema within partitions
   • Optimized for time-series and IoT
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         RING ARCHITECTURE                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Nodes arranged in a ring, each owns a token range:

                        Token: 0
                         Node A
                      ┌─────────┐
                ┌─────│         │─────┐
                │     └─────────┘     │
        Token:  │                     │  Token:
         192    │                     │   64
      ┌─────────┐                     ┌─────────┐
      │ Node D  │                     │ Node B  │
      └─────────┘                     └─────────┘
                │                     │
                │     ┌─────────┐     │
                └─────│ Node C  │─────┘
                      └─────────┘
                        Token: 128

DATA PLACEMENT:
  hash(partition_key) = token → Placed on node owning that range
  
  hash("user:123") = 50 → Node B (owns 0-64)
  hash("user:456") = 150 → Node D (owns 128-192)

REPLICATION:
  Replication Factor = 3 → Data stored on 3 consecutive nodes
  
  Token 50 → Node B, Node C, Node D
```

## Data Model

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         KEYSPACE > TABLE > PARTITION > ROW                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

CREATE KEYSPACE ecommerce
WITH replication = {
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 3
};

CREATE TABLE orders (
    user_id UUID,           -- Partition key
    order_date DATE,        -- Clustering column
    order_id UUID,          -- Clustering column
    total DECIMAL,
    status TEXT,
    PRIMARY KEY ((user_id), order_date, order_id)
) WITH CLUSTERING ORDER BY (order_date DESC, order_id ASC);

PHYSICAL STORAGE:
  ┌─────────────────────────────────────────────────────────────────────────────────────┐
  │  Partition: user_id = abc-123                                                       │
  │  ┌─────────────────────────────────────────────────────────────────────────────┐    │
  │  │ order_date │ order_id  │ total │ status     │                              │    │
  │  ├────────────┼───────────┼───────┼────────────┤  Sorted by clustering        │    │
  │  │ 2024-01-15 │ order-789 │ 99.00 │ delivered  │  columns within partition    │    │
  │  │ 2024-01-14 │ order-456 │ 50.00 │ shipped    │                              │    │
  │  │ 2024-01-10 │ order-123 │ 25.00 │ completed  │                              │    │
  │  └─────────────────────────────────────────────────────────────────────────────┘    │
  └─────────────────────────────────────────────────────────────────────────────────────┘
  
  ┌─────────────────────────────────────────────────────────────────────────────────────┐
  │  Partition: user_id = def-456                                                       │
  │  └── Different physical location                                                    │
  └─────────────────────────────────────────────────────────────────────────────────────┘

KEY CONCEPTS:
  Partition Key: Determines which node stores the data
  Clustering Columns: Determines sort order within partition
```

## Consistency Levels

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         TUNABLE CONSISTENCY                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

For Replication Factor = 3:

READ CONSISTENCY:
  ┌───────────────┬───────────────────────────────────────────────────────────────────────┐
  │ Level         │ Meaning                                                               │
  ├───────────────┼───────────────────────────────────────────────────────────────────────┤
  │ ONE           │ Return after 1 replica responds (fastest, least consistent)          │
  │ TWO           │ Return after 2 replicas respond                                       │
  │ QUORUM        │ Return after majority (2/3) respond                                   │
  │ ALL           │ Return after all replicas respond (slowest, most consistent)         │
  │ LOCAL_QUORUM  │ Majority in local datacenter                                          │
  └───────────────┴───────────────────────────────────────────────────────────────────────┘

WRITE CONSISTENCY:
  Same levels - determines how many replicas must acknowledge write

STRONG CONSISTENCY:
  Write CL + Read CL > Replication Factor
  
  Example: QUORUM write (2) + QUORUM read (2) > 3 = Strong consistency
  
EVENTUAL CONSISTENCY:
  ONE write + ONE read = May read stale data
```

## Write Path

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WRITE OPERATION                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

INSERT INTO orders (...) VALUES (...) USING CONSISTENCY QUORUM;

1. CLIENT sends to any node (coordinator)
2. COORDINATOR forwards to all replicas
3. Each replica:
   a. Write to CommitLog (append-only, for durability)
   b. Write to MemTable (in-memory sorted structure)
   c. Acknowledge to coordinator
4. COORDINATOR responds after QUORUM (2) acknowledgments

                    ┌─────────────┐
     Client ───────▶│ Coordinator │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
         ┌───────┐    ┌───────┐    ┌───────┐
         │Replica│    │Replica│    │Replica│
         │   1   │    │   2   │    │   3   │
         └───────┘    └───────┘    └───────┘
              │            │
              └────────────┴── Wait for QUORUM (2)

FLUSH TO DISK:
  MemTable full → Flush to SSTable (immutable file)
  Compaction merges SSTables over time
```

## Read Path

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         READ OPERATION                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

SELECT * FROM orders WHERE user_id = 'abc-123' AND order_date >= '2024-01-01';

1. COORDINATOR routes to replicas
2. Each replica checks (in order):
   a. MemTable (in-memory)
   b. Row Cache (if enabled)
   c. Bloom Filter (skip SSTables that don't have key)
   d. Partition Index
   e. SSTables on disk
3. COORDINATOR merges results (newest wins by timestamp)
4. Returns to client

READ REPAIR:
  If replicas return different data:
  → Coordinator returns newest
  → Sends repair to out-of-date replicas
```

## Query Patterns

```cql
-- ✓ GOOD: Query by partition key
SELECT * FROM orders WHERE user_id = 'abc-123';

-- ✓ GOOD: Query by partition key + clustering column range
SELECT * FROM orders 
WHERE user_id = 'abc-123' 
  AND order_date >= '2024-01-01' 
  AND order_date <= '2024-01-31';

-- ✗ BAD: Query without partition key (full table scan!)
SELECT * FROM orders WHERE status = 'pending';
-- Requires ALLOW FILTERING (don't use in production)

-- SOLUTION: Create a separate table for status queries
CREATE TABLE orders_by_status (
    status TEXT,
    order_date DATE,
    order_id UUID,
    user_id UUID,
    PRIMARY KEY ((status), order_date, order_id)
);
-- Denormalize: Insert into both tables
```

## Time-Series Pattern

```cql
-- Sensor data with time-bucketing to control partition size

CREATE TABLE sensor_readings (
    sensor_id UUID,
    bucket DATE,          -- Partition by day
    reading_time TIMESTAMP,
    value DOUBLE,
    PRIMARY KEY ((sensor_id, bucket), reading_time)
) WITH CLUSTERING ORDER BY (reading_time DESC);

-- Insert
INSERT INTO sensor_readings (sensor_id, bucket, reading_time, value)
VALUES (uuid, '2024-01-15', '2024-01-15 10:30:00', 25.5);

-- Query: Last 24 hours of readings for a sensor
SELECT * FROM sensor_readings
WHERE sensor_id = ? AND bucket IN ('2024-01-15', '2024-01-14')
  AND reading_time >= '2024-01-14 10:30:00';

-- Time-To-Live for automatic expiry
INSERT INTO sensor_readings (...) VALUES (...) USING TTL 604800;  -- 7 days
```

## Anti-Patterns

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WHAT NOT TO DO                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. UNBOUNDED PARTITIONS
   ✗ PRIMARY KEY (user_id)  -- All user data in one partition
   ✓ PRIMARY KEY ((user_id, month), timestamp)  -- Time-bucketed

2. HIGH CARDINALITY CLUSTERING COLUMNS
   ✗ Millions of rows per partition (slow reads)
   ✓ Keep partitions < 100MB

3. SECONDARY INDEXES ON HIGH-CARDINALITY COLUMNS
   ✗ CREATE INDEX ON orders (order_id)
   ✓ Create a lookup table instead

4. READS BY NON-KEY COLUMNS
   ✗ SELECT * FROM orders WHERE total > 100  -- Full scan!
   ✓ Denormalize into a table with proper partition key

5. BATCH FOR PERFORMANCE
   ✗ Batching unrelated partitions (actually slower)
   ✓ Batch only for atomicity within same partition
```

## When to Use

✅ **Good for:**
- Write-heavy workloads
- Time-series data
- High availability requirements
- Geographically distributed data
- Simple query patterns (by partition key)

❌ **Not ideal for:**
- Complex queries (joins, aggregations)
- Small datasets
- Strong consistency requirements
- Ad-hoc queries
- Frequent updates

## Trade-offs

| Pros | Cons |
|------|------|
| Linear scalability | No joins |
| High availability | No transactions (LWT limited) |
| Excellent write performance | Query inflexibility |
| Multi-datacenter | Denormalization required |

## Used In (This Guide)

- **Messaging (Q4)**: Message storage
- **Time-Series (Q17)**: IoT sensor data
- **Activity Feed (Q6)**: User activity storage
- **Metrics (Q20)**: High-volume metrics storage
