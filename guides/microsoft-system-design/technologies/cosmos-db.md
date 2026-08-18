# Cosmos DB

## What It Is
Azure Cosmos DB is Microsoft's globally distributed, multi-model NoSQL database service. It offers turnkey global distribution, elastic scaling, multiple consistency levels, and guaranteed single-digit millisecond latency at the 99th percentile.

## Key Features

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COSMOS DB HIGHLIGHTS                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. GLOBAL DISTRIBUTION
   • Replicate data to any Azure region with a click
   • Multi-region writes (active-active)
   • Automatic failover
   
2. FIVE CONSISTENCY LEVELS
   Strong → Bounded Staleness → Session → Consistent Prefix → Eventual
   (Choose your own CAP trade-off)
   
3. MULTIPLE APIs
   • SQL (document)
   • MongoDB
   • Cassandra
   • Gremlin (graph)
   • Table
   
4. GUARANTEED PERFORMANCE
   • <10ms read latency (P99)
   • <15ms write latency (P99)
   • 99.999% availability with multi-region
```

## Data Model

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HIERARCHY                                                        │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Account (cosmos-myapp)
    │
    ├── Database (ecommerce)
    │       │
    │       ├── Container (orders)
    │       │       │
    │       │       ├── Item { "id": "order-1", "userId": "user-123", ... }
    │       │       └── Item { "id": "order-2", "userId": "user-456", ... }
    │       │
    │       └── Container (products)
    │               │
    │               └── Items...
    │
    └── Database (analytics)
            └── Containers...

CONTAINER = Collection/Table
  • Unit of scalability
  • Has a partition key
  • Provisioned throughput (RU/s)
```

## Partition Key

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         PARTITIONING                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Each item must have a partition key. Items with same key are in same LOGICAL partition.

Example: Orders container with partition key = /userId

  Logical Partition "user-123"        Logical Partition "user-456"
  ┌──────────────────────────────┐    ┌──────────────────────────────┐
  │ { id: "order-1",             │    │ { id: "order-3",             │
  │   userId: "user-123",        │    │   userId: "user-456",        │
  │   total: 99.00 }             │    │   total: 50.00 }             │
  │                              │    │                              │
  │ { id: "order-2",             │    │ { id: "order-4",             │
  │   userId: "user-123",        │    │   userId: "user-456",        │
  │   total: 150.00 }            │    │   total: 200.00 }            │
  └──────────────────────────────┘    └──────────────────────────────┘

PHYSICAL PARTITIONS:
  • Multiple logical partitions per physical partition
  • Each physical partition: up to 50GB, 10K RU/s
  • Cosmos DB manages physical partitions automatically

GOOD PARTITION KEYS:
  ✓ High cardinality (many distinct values)
  ✓ Even distribution of requests
  ✓ Even distribution of storage
  
BAD PARTITION KEYS:
  ✗ /country (hot partition for US)
  ✗ /status (only "active"/"inactive")
  ✗ /createdDate (time-based hotspots)
```

## Request Units (RU/s)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         THROUGHPUT MODEL                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

RU = Request Unit = Cost of reading 1KB item by id

EXAMPLE COSTS:
  ┌──────────────────────────────────────────┬──────────────┐
  │ Operation                                │ ~RU Cost     │
  ├──────────────────────────────────────────┼──────────────┤
  │ Read 1KB item by id                      │ 1 RU         │
  │ Read 1KB item by query                   │ ~3 RU        │
  │ Write 1KB item                           │ ~5 RU        │
  │ Query scanning 100 items                 │ ~100 RU      │
  │ Complex query with aggregation           │ 100-1000 RU  │
  └──────────────────────────────────────────┴──────────────┘

PROVISIONED THROUGHPUT:
  • Allocate RU/s at container or database level
  • 400 RU/s minimum per container
  • Exceeding limit → 429 Too Many Requests

AUTOSCALE:
  • Set max RU/s (e.g., 10,000)
  • Scales 10%-100% based on demand
  • Pay for peak used each hour

SERVERLESS:
  • Pay per operation (no provisioning)
  • Good for dev/test, sporadic workloads
```

## Consistency Levels

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         FIVE CONSISTENCY LEVELS                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. STRONG
   Reads always return most recent committed write
   All regions see same data simultaneously
   
   Use: Financial transactions, inventory counts
   Cost: Highest latency, lowest throughput, only in single write region
   
2. BOUNDED STALENESS  
   Reads may lag writes by at most K versions or T seconds
   
   Use: Leaderboards, counters, showing "nearly live" data
   Config: "max 100 operations OR 5 seconds behind"
   
3. SESSION (Default)
   Within same session: read your own writes
   Other sessions: eventual consistency
   
   Use: User profiles, shopping carts, most apps
   Best balance of consistency and performance
   
4. CONSISTENT PREFIX
   Reads never see out-of-order writes
   If writes are A, B, C → reads see A or A,B or A,B,C (never B,A)
   
   Use: Social feeds, notifications (order matters)
   
5. EVENTUAL
   No ordering guarantees, highest performance
   
   Use: View counters, likes, non-critical metrics

VISUALIZATION:
  Strong ──────────────────────────────────────────── Eventual
    │                                                      │
    │  Higher latency                     Lower latency    │
    │  Lower throughput                   Higher throughput│
    │  More consistency                   Less consistency │
```

## Global Distribution

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         MULTI-REGION                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

SINGLE WRITE REGION + MULTIPLE READ REGIONS:
  
  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
  │  West US    │ ──────▶ │  East US    │ ──────▶ │  Europe     │
  │  (Write)    │         │  (Read)     │         │  (Read)     │
  │  Primary    │         │  Replica    │         │  Replica    │
  └─────────────┘         └─────────────┘         └─────────────┘
        │
        │ Async replication (<10ms typically)
        ▼
  ┌─────────────┐
  │  Asia       │
  │  (Read)     │
  └─────────────┘

MULTI-REGION WRITES (Active-Active):

  ┌─────────────┐         ┌─────────────┐
  │  West US    │ ◀─────▶ │  East US    │
  │  Read/Write │         │  Read/Write │
  └─────────────┘         └─────────────┘
        │                       │
        └───────────┬───────────┘
                    │
              ┌─────────────┐
              │  Europe     │
              │  Read/Write │
              └─────────────┘
              
CONFLICT RESOLUTION:
  • Last-Writer-Wins (default, based on _ts)
  • Custom merge procedures (stored proc)
```

## Indexing

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         AUTOMATIC INDEXING                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

By default, ALL properties are indexed automatically!

INDEX POLICY:
{
  "indexingMode": "consistent",  // or "lazy" or "none"
  "automatic": true,
  "includedPaths": [
    { "path": "/*" }             // Index everything
  ],
  "excludedPaths": [
    { "path": "/largeTextField/*" }  // Exclude large text
  ],
  "compositeIndexes": [          // For ORDER BY on multiple fields
    [
      { "path": "/city", "order": "ascending" },
      { "path": "/timestamp", "order": "descending" }
    ]
  ]
}

INDEX TYPES:
  • Range index: numeric/string comparisons (=, <, >, BETWEEN)
  • Spatial index: GeoJSON queries (ST_DISTANCE, ST_WITHIN)
  • Composite index: multi-field ORDER BY
```

## SDK Usage

```python
from azure.cosmos import CosmosClient, PartitionKey

# Initialize client
client = CosmosClient(endpoint, credential)
database = client.get_database_client("ecommerce")
container = database.get_container_client("orders")

# Create item
order = {
    "id": "order-123",
    "userId": "user-456",
    "items": [{"sku": "ABC", "qty": 2}],
    "total": 99.00
}
container.create_item(body=order)

# Read item by id (1 RU, fastest)
item = container.read_item(item="order-123", partition_key="user-456")

# Query (uses RU based on complexity)
query = "SELECT * FROM c WHERE c.total > 50"
items = list(container.query_items(
    query=query,
    partition_key="user-456"  # Efficient: single partition
))

# Cross-partition query (expensive!)
all_items = list(container.query_items(
    query="SELECT * FROM c WHERE c.total > 100",
    enable_cross_partition_query=True
))

# Upsert (insert or update)
container.upsert_item(body=updated_order)

# Delete
container.delete_item(item="order-123", partition_key="user-456")
```

## Best Practices

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         OPTIMIZATION TIPS                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. PARTITION KEY DESIGN
   ✓ /userId for user data
   ✓ /tenantId for multi-tenant
   ✓ /deviceId for IoT
   ✗ Avoid low cardinality keys

2. QUERIES
   ✓ Always filter by partition key when possible
   ✓ Use point reads (by id) when you have the id
   ✓ Project only needed fields: SELECT c.name, c.price
   ✗ Avoid cross-partition queries in hot path

3. THROUGHPUT
   ✓ Start with autoscale for unpredictable workloads
   ✓ Use serverless for dev/test
   ✓ Monitor RU consumption in Azure Portal

4. DATA MODELING
   ✓ Embed related data (denormalize)
   ✓ Duplicate data to avoid cross-partition joins
   ✓ Use Change Feed for derived data
```

## When to Use

✅ **Good for:**
- Global applications
- Variable/unpredictable traffic
- Multi-model requirements
- Low-latency reads/writes
- Document storage

❌ **Not ideal for:**
- Complex joins (use SQL)
- Highly relational data
- Small, predictable workloads (cost)
- Need for strong consistency globally

## Trade-offs

| Pros | Cons |
|------|------|
| Global distribution | Cost can be high |
| Multiple consistency levels | Partition key is immutable |
| Automatic scaling | Learning curve |
| SLA guarantees | RU model complexity |

## Used In (This Guide)

- **Microsoft Teams (Q1)**: Message storage
- **E-commerce Platform (Q46)**: Product catalog
- **User Session Storage (Q8)**: Session data
- **IoT Telemetry (Q17)**: Device data
