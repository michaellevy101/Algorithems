# LSM Tree (Log-Structured Merge Tree)

## What It Is
An LSM Tree is a data structure optimized for high write throughput by batching writes in memory and periodically flushing to disk. It's the storage engine behind Cassandra, RocksDB, LevelDB, and many modern databases.

## The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         B-TREE VS LSM TREE                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

B-TREE (Traditional):
  Write "key1" → Find leaf node → Update in place → Write to disk
  
  Problem: Random I/O for each write
  Disk seek = 10ms → Max ~100 writes/sec per disk
  
LSM TREE:
  Write "key1" → Append to memory buffer → Eventually flush to disk
  
  Advantage: Sequential I/O (100x faster than random)
  SSD: 500MB/s sequential vs 5MB/s random
```

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         LSM TREE ARCHITECTURE                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                            WRITES
                              │
                              ▼
                    ┌─────────────────────┐
                    │     MemTable        │  ← In-memory (sorted)
                    │  (Red-Black Tree)   │     e.g., 64MB
                    └──────────┬──────────┘
                               │ Flush when full
                               ▼
              ┌─────────────────────────────────────┐
              │        Level 0 (L0)                 │  ← Immutable SSTable files
              │  [SST1] [SST2] [SST3] [SST4]       │     Overlapping key ranges
              └─────────────────┬───────────────────┘
                                │ Compaction (merge)
                                ▼
              ┌─────────────────────────────────────┐
              │        Level 1 (L1)                 │  ← Non-overlapping
              │  [SST-A][SST-B][SST-C][SST-D]      │     Each file: sorted keys
              └─────────────────┬───────────────────┘
                                │ Compaction
                                ▼
              ┌─────────────────────────────────────┐
              │        Level 2 (L2)                 │  ← 10× larger than L1
              │  [files...................................] │
              └─────────────────────────────────────┘
                                │
                                ▼
                         (More levels...)
```

## Write Path

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WRITE OPERATION                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

PUT("user:123", "{name: 'Alice'}")

Step 1: Write to WAL (Write-Ahead Log)
        ┌──────────────────────────────────┐
        │ WAL (append-only, sequential)    │
        │ [PUT user:123 {...}]            │
        └──────────────────────────────────┘
        Purpose: Durability before crash

Step 2: Insert into MemTable
        ┌──────────────────────────────────┐
        │ MemTable (in-memory sorted tree) │
        │   user:100 → {...}              │
        │   user:123 → {name: 'Alice'} ← │
        │   user:200 → {...}              │
        └──────────────────────────────────┘

Step 3: When MemTable full → Flush to SSTable
        MemTable (64MB) → SSTable file on disk
        
        SSTable format:
        ┌────────────────────────────────────────────────────────┐
        │ Data Block 1 │ Data Block 2 │...│ Index │ Bloom Filter│
        │ (sorted KVs) │ (sorted KVs) │   │       │             │
        └────────────────────────────────────────────────────────┘

WRITE SPEED:
  - Memory write: O(log n)
  - Disk write: Sequential (fast!)
  - No random I/O during write
```

## Read Path

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         READ OPERATION                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

GET("user:123")

Step 1: Check MemTable
        Found? → Return value
        
Step 2: Check Immutable MemTable (being flushed)
        Found? → Return value
        
Step 3: Check L0 SSTables (most recent)
        Use Bloom Filter to skip files that definitely don't have key
        Binary search within file
        
Step 4: Check L1 SSTables
        Non-overlapping → Only one file could have the key
        
Step 5: Check L2, L3, etc.
        Each level 10× larger
        
READ OPTIMIZATION:
  ┌────────────────────────────────────────────────────────────────────────────────────────┐
  │ Bloom Filter: "Is key user:123 in this file?"                                         │
  │   → "Definitely NO" (skip file)                                                       │
  │   → "Maybe YES" (read file)                                                           │
  │                                                                                        │
  │ Saves disk reads for 99% of files that don't contain the key                          │
  └────────────────────────────────────────────────────────────────────────────────────────┘
```

## Compaction

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMPACTION PROCESS                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

WHY COMPACT?
  - Too many files = slow reads (check each file)
  - Deleted/overwritten keys waste space
  
LEVELED COMPACTION (LevelDB, RocksDB):

  L0: [0-100] [20-80] [50-200]  ← Overlapping ranges
       │       │       │
       └───────┴───────┘
               │ Merge sort
               ▼
  L1: [0-50] [51-100] [101-200]  ← Non-overlapping

PROCESS:
  1. Select files to compact
  2. Merge-sort keys from selected files
  3. Write new files to next level
  4. Delete old files

EXAMPLE:
  L0 file [A→1, B→2, C→3] (version 1)
  L0 file [B→5]           (version 2, B updated)
  
  After compaction → L1 file [A→1, B→5, C→3]
  Old B→2 is discarded (superseded)

COMPACTION STRATEGIES:
  ┌───────────────────┬──────────────────────────────────────────────────────────────────┐
  │ Size-Tiered       │ Merge similar-sized files together                               │
  │                   │ Better write throughput, worse read (more files)                 │
  ├───────────────────┼──────────────────────────────────────────────────────────────────┤
  │ Leveled           │ Maintain non-overlapping ranges per level                        │
  │                   │ Better read, more compaction work                                │
  └───────────────────┴──────────────────────────────────────────────────────────────────┘
```

## Implementation Example

```python
class MemTable:
    def __init__(self, max_size):
        self.data = {}  # In practice: Red-Black tree or Skip List
        self.size = 0
        self.max_size = max_size
    
    def put(self, key, value):
        self.data[key] = value
        self.size += len(key) + len(value)
        return self.size >= self.max_size
    
    def get(self, key):
        return self.data.get(key)
    
    def flush_to_sstable(self, filename):
        # Sort and write to disk
        sorted_items = sorted(self.data.items())
        with open(filename, 'wb') as f:
            for k, v in sorted_items:
                f.write(encode(k, v))
        return SSTable(filename)

class SSTable:
    def __init__(self, filename):
        self.filename = filename
        self.index = self._build_index()
        self.bloom_filter = self._build_bloom_filter()
    
    def get(self, key):
        # Quick rejection with Bloom filter
        if not self.bloom_filter.might_contain(key):
            return None
        
        # Binary search in index
        position = self._binary_search(key)
        if position:
            return self._read_at_position(position)
        return None

class LSMTree:
    def __init__(self):
        self.memtable = MemTable(64 * 1024 * 1024)  # 64MB
        self.immutable_memtable = None
        self.levels = [[] for _ in range(7)]  # L0-L6
        self.wal = WriteAheadLog()
    
    def put(self, key, value):
        # Step 1: Write to WAL
        self.wal.append(key, value)
        
        # Step 2: Write to MemTable
        should_flush = self.memtable.put(key, value)
        
        # Step 3: Flush if full
        if should_flush:
            self._flush()
    
    def get(self, key):
        # Check MemTable
        value = self.memtable.get(key)
        if value is not None:
            return value
        
        # Check immutable MemTable
        if self.immutable_memtable:
            value = self.immutable_memtable.get(key)
            if value is not None:
                return value
        
        # Check SSTables level by level
        for level in self.levels:
            for sstable in level:
                value = sstable.get(key)
                if value is not None:
                    return value
        
        return None
    
    def _flush(self):
        self.immutable_memtable = self.memtable
        self.memtable = MemTable(64 * 1024 * 1024)
        
        # Background flush
        sstable = self.immutable_memtable.flush_to_sstable(...)
        self.levels[0].append(sstable)
        self.immutable_memtable = None
        
        # Trigger compaction if needed
        self._maybe_compact()
```

## Databases Using LSM Trees

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         LSM TREE DATABASES                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┬───────────────────────────────────────────────────────────────────────┐
│ Database        │ Notes                                                                 │
├─────────────────┼───────────────────────────────────────────────────────────────────────┤
│ Cassandra       │ Wide-column store, uses size-tiered compaction                       │
│ RocksDB         │ Facebook's fork of LevelDB, highly optimized                         │
│ LevelDB         │ Google's original implementation                                      │
│ HBase           │ Hadoop ecosystem, column-family store                                 │
│ InfluxDB        │ Time-series database                                                 │
│ Cockroach DB    │ Uses RocksDB as storage layer                                        │
│ TiKV            │ Uses RocksDB, TiDB's storage                                         │
└─────────────────┴───────────────────────────────────────────────────────────────────────┘
```

## When to Use

✅ **Good for:**
- Write-heavy workloads
- Append-mostly data (logs, events)
- Time-series data
- Key-value stores

❌ **Not ideal for:**
- Read-heavy, random access
- Small updates to existing data
- Strong read performance requirements
- Transactional updates

## Trade-offs

| Pros | Cons |
|------|------|
| Excellent write throughput | Read amplification |
| Sequential I/O | Write amplification (compaction) |
| Efficient range scans | Space amplification |
| Good compression | Compaction spikes |

## Write/Read Amplification

```
WRITE AMPLIFICATION:
  Data written to disk / Data written by user
  
  Each key may be written multiple times:
  MemTable → L0 → L1 → L2 → ...
  
  Typical: 10-30× write amplification

READ AMPLIFICATION:
  Disk reads per query
  
  Worst case: Check MemTable + all levels
  
  Mitigations:
  - Bloom filters (skip files)
  - Block cache (hot data in memory)
  - Compaction (fewer files)
```

## Used In (This Guide)

- **Time-Series Database (Q20)**: Event/metrics storage
- **Distributed Storage (Q24)**: Key-value backend
- **Logging System (Q22)**: Write-heavy log storage
- **Message Queue (Q4)**: Persistent message storage
