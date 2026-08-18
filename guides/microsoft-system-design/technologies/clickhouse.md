# ClickHouse

## What It Is
ClickHouse is an open-source columnar database management system (DBMS) designed for online analytical processing (OLAP). It's optimized for fast aggregation queries over large datasets, capable of processing billions of rows per second.

## Column-Oriented Storage

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ROW VS COLUMN STORAGE                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

ROW-ORIENTED (PostgreSQL, MySQL):
  Store: [id, name, age, city], [id, name, age, city], ...
  
  ┌────┬───────┬─────┬──────────┐
  │ 1  │ Alice │ 25  │ Seattle  │
  │ 2  │ Bob   │ 30  │ Boston   │
  │ 3  │ Carol │ 35  │ Chicago  │
  └────┴───────┴─────┴──────────┘
  
  Good for: SELECT * FROM users WHERE id = 1
  Read entire row at once

COLUMN-ORIENTED (ClickHouse):
  Store: [1, 2, 3], [Alice, Bob, Carol], [25, 30, 35], [Seattle, Boston, Chicago]
  
  id:    [1, 2, 3]
  name:  [Alice, Bob, Carol]
  age:   [25, 30, 35]
  city:  [Seattle, Boston, Chicago]
  
  Good for: SELECT AVG(age) FROM users
  Only read 'age' column, skip others!

ANALYTICS QUERY:
  SELECT city, AVG(age) FROM users GROUP BY city
  
  Row store: Read 4 columns × 1M rows = 4M values
  Column store: Read 2 columns × 1M rows = 2M values (50% I/O)
```

## Compression Benefits

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMPRESSION                                                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Same data type in column = excellent compression

city column: [Seattle, Seattle, Seattle, Boston, Boston, Chicago, Chicago, Chicago, ...]

Dictionary encoding:
  0 = Seattle
  1 = Boston  
  2 = Chicago
  
  → [0, 0, 0, 1, 1, 2, 2, 2, ...]
  
Run-length encoding:
  → [(0, 3), (1, 2), (2, 3), ...]
  
COMPRESSION RATIOS:
  • Raw data: 1 TB
  • ClickHouse: 50-200 GB (5-20× compression)
  
  Less disk = faster I/O = faster queries!
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CLICKHOUSE ARCHITECTURE                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              ClickHouse Cluster                                          │
│                                                                                          │
│   ┌─────────────────────┐     ┌─────────────────────┐     ┌─────────────────────┐      │
│   │  Node 1 (Shard 1)   │     │  Node 2 (Shard 2)   │     │  Node 3 (Shard 3)   │      │
│   │                     │     │                     │     │                     │      │
│   │  ┌──────────────┐   │     │  ┌──────────────┐   │     │  ┌──────────────┐   │      │
│   │  │ MergeTree    │   │     │  │ MergeTree    │   │     │  │ MergeTree    │   │      │
│   │  │ Tables       │   │     │  │ Tables       │   │     │  │ Tables       │   │      │
│   │  └──────────────┘   │     │  └──────────────┘   │     │  └──────────────┘   │      │
│   │                     │     │                     │     │                     │      │
│   │  Replica: Node 4    │     │  Replica: Node 5    │     │  Replica: Node 6    │      │
│   └─────────────────────┘     └─────────────────────┘     └─────────────────────┘      │
│                                                                                          │
│   Distributed table spans all shards                                                    │
│   Query: SELECT ... → Scatter to shards → Gather results                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## MergeTree Engine

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         MERGETREE - PRIMARY TABLE ENGINE                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

CREATE TABLE events (
    event_date Date,
    event_time DateTime,
    user_id UInt64,
    event_type String,
    data String
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)  -- Monthly partitions
ORDER BY (user_id, event_time)      -- Primary key / sort order
TTL event_date + INTERVAL 90 DAY;   -- Auto-delete after 90 days

DATA ORGANIZATION:
  ┌────────────────────────────────────────────────────────────────────────────────────┐
  │  Partition 202401                                                                   │
  │  ├── Part 1 (sorted by user_id, event_time)                                        │
  │  │   ├── event_date.bin (compressed column data)                                   │
  │  │   ├── user_id.bin                                                               │
  │  │   ├── event_type.bin                                                            │
  │  │   ├── primary.idx (sparse index)                                                │
  │  │   └── ...                                                                       │
  │  ├── Part 2                                                                        │
  │  └── Part 3                                                                        │
  │                                                                                     │
  │  Partition 202402                                                                   │
  │  └── ...                                                                           │
  └────────────────────────────────────────────────────────────────────────────────────┘

MERGING:
  Small parts → Background merge → Larger parts
  Similar to LSM tree compaction
```

## Query Examples

```sql
-- Aggregation on billions of rows
SELECT 
    toDate(event_time) AS date,
    event_type,
    count() AS events,
    uniqExact(user_id) AS unique_users
FROM events
WHERE event_date >= '2024-01-01'
GROUP BY date, event_type
ORDER BY date;

-- Time-series analysis
SELECT 
    toStartOfHour(event_time) AS hour,
    avg(response_time) AS avg_response,
    quantile(0.95)(response_time) AS p95
FROM api_requests
WHERE event_date = today()
GROUP BY hour;

-- Approximate distinct count (faster)
SELECT uniq(user_id) FROM events;  -- HyperLogLog, ~2% error

-- Exact distinct count (slower)
SELECT uniqExact(user_id) FROM events;

-- Array functions
SELECT 
    user_id,
    groupArray(event_type) AS events,  -- Collect into array
    arrayDistinct(groupArray(event_type)) AS unique_events
FROM events
GROUP BY user_id;
```

## Materialized Views

```sql
-- Pre-aggregate data for faster queries

-- Source table (raw events)
CREATE TABLE events (
    event_time DateTime,
    user_id UInt64,
    event_type String,
    value Float64
) ENGINE = MergeTree()
ORDER BY (event_time, user_id);

-- Materialized view (auto-updated aggregate)
CREATE MATERIALIZED VIEW events_hourly_mv
ENGINE = SummingMergeTree()
ORDER BY (hour, event_type)
AS SELECT
    toStartOfHour(event_time) AS hour,
    event_type,
    count() AS event_count,
    sum(value) AS total_value
FROM events
GROUP BY hour, event_type;

-- Query the MV instead of raw data (100x faster!)
SELECT * FROM events_hourly_mv
WHERE hour >= '2024-01-01 00:00:00';
```

## Ingestion

```python
from clickhouse_driver import Client

client = Client('localhost')

# Batch insert (recommended)
data = [
    ('2024-01-15', '2024-01-15 10:00:00', 123, 'click', '{}'),
    ('2024-01-15', '2024-01-15 10:00:01', 456, 'view', '{}'),
    # ... thousands more rows
]

client.execute(
    'INSERT INTO events (event_date, event_time, user_id, event_type, data) VALUES',
    data
)

# From Kafka
CREATE TABLE events_queue (
    event_date Date,
    event_time DateTime,
    user_id UInt64,
    event_type String
) ENGINE = Kafka()
SETTINGS
    kafka_broker_list = 'kafka:9092',
    kafka_topic_list = 'events',
    kafka_group_name = 'clickhouse_consumer',
    kafka_format = 'JSONEachRow';

CREATE MATERIALIZED VIEW events_mv TO events AS
SELECT * FROM events_queue;
```

## Performance Tips

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         OPTIMIZATION                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. PARTITION BY TIME
   PARTITION BY toYYYYMM(date)
   • Enables efficient date range queries
   • Easy data lifecycle management (DROP PARTITION)

2. ORDER BY FREQUENTLY FILTERED COLUMNS
   ORDER BY (tenant_id, user_id, timestamp)
   • First columns = most selective filters
   • Enables index usage

3. USE APPROPRIATE TYPES
   • UInt32 vs UInt64 (smaller = faster)
   • LowCardinality(String) for enums
   • Date vs DateTime (Date is 2 bytes)

4. BATCH INSERTS
   • Insert 10K-100K rows at once
   • Avoid single-row inserts (creates tiny parts)

5. PREWHERE (auto-optimized)
   SELECT * FROM events
   WHERE user_id = 123  -- Evaluated first (PREWHERE)
     AND complex_condition(data)
```

## When to Use

✅ **Good for:**
- Analytics dashboards
- Log analysis
- Time-series data
- Ad-hoc aggregations
- Real-time analytics

❌ **Not ideal for:**
- OLTP (transactional workloads)
- Frequent updates/deletes
- Point queries by arbitrary key
- Small datasets (<1M rows)

## Trade-offs

| Pros | Cons |
|------|------|
| Extremely fast aggregations | No transactions |
| Excellent compression | Updates are expensive |
| SQL interface | Learning curve |
| Linear scaling | Point queries slower |

## Used In (This Guide)

- **Web Analytics (Q44)**: Pageview aggregations
- **Logging (Q22)**: Log analysis
- **Ad Platform (Q45)**: Click/impression analytics
- **Real-time Dashboard (Q20)**: Metrics queries
