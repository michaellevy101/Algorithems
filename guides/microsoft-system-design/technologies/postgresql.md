# PostgreSQL

## What It Is
PostgreSQL is a powerful, open-source relational database known for its robustness, extensibility, and SQL compliance. It's often called "the most advanced open-source database" and is widely used for transactional workloads.

## Key Features

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         POSTGRESQL HIGHLIGHTS                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. ACID TRANSACTIONS
   • Atomicity, Consistency, Isolation, Durability
   • Multi-version concurrency control (MVCC)
   
2. EXTENSIBILITY
   • Custom data types
   • User-defined functions (PL/pgSQL, Python, etc.)
   • Extensions (PostGIS, pg_trgm, etc.)
   
3. JSON SUPPORT
   • JSONB type (binary JSON, indexed)
   • Document + relational hybrid
   
4. ADVANCED FEATURES
   • Full-text search
   • Window functions
   • CTEs, recursive queries
   • Table inheritance
```

## MVCC (Multi-Version Concurrency Control)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HOW MVCC WORKS                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Instead of locks, PostgreSQL creates new versions of rows.

Transaction T1 reads row          Transaction T2 updates row
at time t=100                     at time t=150

┌─────────────────────┐           ┌─────────────────────┐
│ Row version 1       │           │ Row version 2       │
│ xmin=100, xmax=∞    │           │ xmin=150, xmax=∞    │
│ name='Alice'        │           │ name='Bob'          │
└─────────────────────┘           └─────────────────────┘
         │                                  │
         ▼                                  ▼
    T1 sees 'Alice'                   New transactions
    (snapshot at t=100)               see 'Bob'

BENEFITS:
  • Readers don't block writers
  • Writers don't block readers
  • High concurrency

CLEANUP:
  • VACUUM process removes old row versions
  • Autovacuum runs in background
```

## Indexing

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         INDEX TYPES                                                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. B-TREE (Default)
   CREATE INDEX idx_users_email ON users(email);
   
   Good for: =, <, >, <=, >=, BETWEEN, IN, IS NULL
   
2. HASH
   CREATE INDEX idx_users_id ON users USING HASH(id);
   
   Good for: = only (exact match)
   
3. GIN (Generalized Inverted Index)
   CREATE INDEX idx_doc_content ON documents USING GIN(content);
   
   Good for: Arrays, JSONB, full-text search
   
4. GiST (Generalized Search Tree)
   CREATE INDEX idx_location ON places USING GIST(geom);
   
   Good for: Geometric data, PostGIS, ranges
   
5. BRIN (Block Range Index)
   CREATE INDEX idx_ts ON events USING BRIN(created_at);
   
   Good for: Large tables with naturally ordered data
   Very small index size

EXAMPLE USAGE:
  -- Partial index (only active users)
  CREATE INDEX idx_active_users ON users(email)
  WHERE status = 'active';
  
  -- Covering index (include extra columns)
  CREATE INDEX idx_orders ON orders(user_id)
  INCLUDE (total, status);
```

## JSONB

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         JSON IN POSTGRESQL                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

CREATE TABLE products (
  id SERIAL PRIMARY KEY,
  name TEXT,
  attributes JSONB  -- Binary JSON, indexable
);

INSERT INTO products (name, attributes) VALUES
  ('Laptop', '{"brand": "Dell", "specs": {"ram": 16, "ssd": 512}}');

-- Query JSON
SELECT * FROM products
WHERE attributes->>'brand' = 'Dell';

-- Deep path query
SELECT * FROM products
WHERE attributes->'specs'->>'ram' = '16';

-- Index on JSON field
CREATE INDEX idx_brand ON products ((attributes->>'brand'));

-- GIN index for contains queries
CREATE INDEX idx_attrs ON products USING GIN(attributes);

SELECT * FROM products
WHERE attributes @> '{"brand": "Dell"}';  -- Contains
```

## Transactions & Isolation Levels

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ISOLATION LEVELS                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┬───────────────┬───────────────┬───────────────┬───────────────────────┐
│ Level            │ Dirty Read    │ Non-Repeat    │ Phantom       │ Use Case              │
├──────────────────┼───────────────┼───────────────┼───────────────┼───────────────────────┤
│ READ UNCOMMITTED │ Possible*     │ Possible      │ Possible      │ (Not used in PG)      │
│ READ COMMITTED   │ No            │ Possible      │ Possible      │ Default, most apps    │
│ REPEATABLE READ  │ No            │ No            │ No**          │ Reports, analytics    │
│ SERIALIZABLE     │ No            │ No            │ No            │ Financial, critical   │
└──────────────────┴───────────────┴───────────────┴───────────────┴───────────────────────┘

* PostgreSQL's READ UNCOMMITTED = READ COMMITTED
** PostgreSQL's REPEATABLE READ prevents phantoms too (differs from SQL standard)

EXAMPLE:
BEGIN;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- Transfer money
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;

COMMIT;
```

## Connection Pooling

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CONNECTION POOLING                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Problem: PostgreSQL forks a new process per connection (~10MB RAM each)
         1000 connections = 10GB RAM overhead + context switching

Solution: Connection pooler (PgBouncer, Pgpool-II)

         App Servers              PgBouncer         PostgreSQL
    ┌─────────────────┐       ┌─────────────┐     ┌─────────────┐
    │ 1000 app        │       │ Pool of 50  │     │ 50 backend  │
    │ connections     │──────▶│ connections │────▶│ processes   │
    └─────────────────┘       └─────────────┘     └─────────────┘

PGBOUNCER MODES:
  • session: 1 app connection = 1 DB connection (while session lasts)
  • transaction: 1 app connection borrows DB connection per transaction
  • statement: 1 app connection borrows per statement (limited use)

CONFIGURATION:
  [databases]
  mydb = host=localhost dbname=mydb
  
  [pgbouncer]
  listen_port = 6432
  pool_mode = transaction
  max_client_conn = 1000
  default_pool_size = 50
```

## Replication

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         REPLICATION OPTIONS                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. STREAMING REPLICATION (Physical)
   
   Primary                      Standby
   ┌─────────────────┐         ┌─────────────────┐
   │  All writes     │ ──WAL──▶│  Read-only      │
   │                 │  stream │  Hot standby    │
   └─────────────────┘         └─────────────────┘
   
   • Byte-by-byte copy
   • Fast, simple
   • Standby is exact replica

2. LOGICAL REPLICATION
   
   Publisher                    Subscriber
   ┌─────────────────┐         ┌─────────────────┐
   │  CREATE         │         │  Receives only  │
   │  PUBLICATION    │────────▶│  subscribed     │
   │  orders_pub     │         │  tables         │
   └─────────────────┘         └─────────────────┘
   
   • Table-level selection
   • Can have different schemas
   • Cross-version replication

SYNCHRONOUS VS ASYNC:
  synchronous_commit = on     # Wait for standby (safe, slow)
  synchronous_commit = off    # Don't wait (fast, risk data loss)
```

## Partitioning

```sql
-- Range partitioning by date
CREATE TABLE events (
  id SERIAL,
  event_type TEXT,
  created_at TIMESTAMP,
  data JSONB
) PARTITION BY RANGE (created_at);

-- Create partitions
CREATE TABLE events_2024_01 PARTITION OF events
  FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE events_2024_02 PARTITION OF events
  FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Query automatically routes to correct partition
SELECT * FROM events
WHERE created_at >= '2024-01-15' AND created_at < '2024-01-20';
-- Only scans events_2024_01

-- Drop old data efficiently
DROP TABLE events_2023_01;  -- Instant, no vacuum needed
```

## Performance Tuning

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         KEY SETTINGS                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

# Memory
shared_buffers = 4GB              # 25% of RAM
work_mem = 256MB                  # Per-operation memory (sorts, hashes)
maintenance_work_mem = 1GB        # For VACUUM, CREATE INDEX
effective_cache_size = 12GB       # OS cache hint (75% of RAM)

# WAL
wal_buffers = 64MB
checkpoint_completion_target = 0.9
max_wal_size = 4GB

# Connections
max_connections = 200             # Keep low, use pooler

# Query planner
random_page_cost = 1.1            # SSD (default 4.0 for HDD)
effective_io_concurrency = 200    # SSD parallelism

EXPLAIN ANALYZE:
  EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
  SELECT * FROM orders WHERE user_id = 123;
  
  Look for:
  • Seq Scan on large tables (need index?)
  • High actual rows vs estimated (stale statistics?)
  • Buffers read (cache misses)
```

## Azure Database for PostgreSQL

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         AZURE OPTIONS                                                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. FLEXIBLE SERVER (Recommended)
   • Zone-redundant HA
   • Built-in connection pooling (PgBouncer)
   • Stop/start capability
   • Burstable, GP, Memory-optimized tiers
   
2. SINGLE SERVER (Legacy)
   • Being deprecated
   • Migrate to Flexible Server

FEATURES:
  • Automatic backups (up to 35 days)
  • Point-in-time restore
  • Read replicas (up to 5)
  • Private link support
  • Azure AD authentication
```

## When to Use

✅ **Good for:**
- Transactional workloads (OLTP)
- Complex queries with joins
- Data integrity requirements
- Hybrid document + relational
- Geospatial data (PostGIS)

❌ **Not ideal for:**
- Massive horizontal scaling (use NoSQL)
- Simple key-value access (use Redis)
- Time-series at scale (use TimescaleDB or InfluxDB)
- Graph queries (use Neo4j or Cosmos Gremlin)

## Trade-offs

| Pros | Cons |
|------|------|
| ACID compliance | Scaling complexity |
| Rich feature set | Connection overhead |
| Extensibility | Vacuum maintenance |
| JSON + relational | Single-region by default |

## Used In (This Guide)

- **E-commerce (Q46)**: Order transactions
- **Payment Gateway (Q47)**: Financial records
- **User Management (Q8)**: User profiles
- **Ticket Booking (Q42)**: Seat reservations
