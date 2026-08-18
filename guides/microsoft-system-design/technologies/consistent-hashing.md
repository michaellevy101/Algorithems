# Consistent Hashing

## What It Is
Consistent hashing is a distributed hashing technique that minimizes key redistribution when nodes are added or removed. It's essential for distributed caches, databases, and load balancers.

## The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         NAIVE HASHING PROBLEM                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Simple modulo hashing: server = hash(key) % N

With 3 servers:
  hash("user:1") % 3 = 0 → Server 0
  hash("user:2") % 3 = 1 → Server 1
  hash("user:3") % 3 = 2 → Server 2

Add 1 server (now 4 servers):
  hash("user:1") % 4 = 1 → Server 1  ← CHANGED!
  hash("user:2") % 4 = 2 → Server 2  ← CHANGED!
  hash("user:3") % 4 = 3 → Server 3  ← CHANGED!

PROBLEM: Almost ALL keys need to move! (N-1)/N ≈ 75% with 4 servers
         This causes cache stampede and massive data migration.
```

## How Consistent Hashing Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HASH RING                                                        │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Map both servers AND keys to a ring (0 to 2^32 - 1):

                            0
                            │
                   S1 ●─────┼─────● K1
                  /         │         \
                 /          │          \
                /           │           \
        K4 ●───             │             ───● S2
              \             │             /
               \            │            /
                \           │           /
                 ───────────┼───────────
                            │
                   K3 ●─────┼─────● K2
                            │
                        ● S3

RULE: Key goes to the FIRST server clockwise from its position.

  K1 → S2 (next server clockwise)
  K2 → S3
  K3 → S3
  K4 → S1
```

## Adding a Server

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ADDING SERVER S4                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Before:                              After adding S4:
                            0                                0
                            │                                │
                   S1 ●─────┼─────● K1              S1 ●─────┼─────● K1
                  /         │         \            /         │         \
                 /          │          \          /          │          \
                /           │           \        /           │           \
        K4 ●───             │             ───● S2    K4 ●───       ● S4     ───● S2
              \             │             /              \     │             /
               \            │            /                \    │            /
                \           │           /                  \   │           /
                 ───────────┼───────────                    ───┼───────────
                            │                                  │
                   K3 ●─────┼─────● K2                K3 ●─────┼─────● K2
                            │                                  │
                        ● S3                               ● S3

ONLY K4 moves: K4 was → S1, now → S4
Other keys unchanged!

Keys moved = K / N (where K = keys, N = servers)
With 1000 keys and 4 servers, only ~250 keys move (not 750!)
```

## Virtual Nodes (Vnodes)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         PROBLEM: UNEVEN DISTRIBUTION                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

With few servers, distribution is uneven:

        ● S1                    Large gap between S1 and S2
       /    \                   S2 gets most keys!
      /      \
     /        ● S2
    /
   ● S3

SOLUTION: Virtual Nodes
Each physical server has multiple positions on the ring

┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Physical Server S1 → Virtual nodes: S1_0, S1_1, S1_2, S1_3, ...                       │
│  Physical Server S2 → Virtual nodes: S2_0, S2_1, S2_2, S2_3, ...                       │
│  Physical Server S3 → Virtual nodes: S3_0, S3_1, S3_2, S3_3, ...                       │
└─────────────────────────────────────────────────────────────────────────────────────────┘

           S1_0 ●           ● S2_1
                 \         /
                  \       /
         S3_1 ●    \     /    ● S1_2
                    \   /
                     \ /
          ────────────●────────────
                     / \
                    /   \
         S2_0 ●    /     \    ● S3_0
                  /       \
                 /         \
           S1_1 ●           ● S2_2

Now load is evenly distributed!
Typical: 100-200 virtual nodes per physical server
```

## Implementation

```python
import hashlib
from bisect import bisect_right

class ConsistentHash:
    def __init__(self, nodes=None, replicas=100):
        self.replicas = replicas  # Virtual nodes per physical node
        self.ring = {}           # hash -> node mapping
        self.sorted_keys = []    # Sorted hash values
        
        if nodes:
            for node in nodes:
                self.add_node(node)
    
    def _hash(self, key):
        """Generate hash for a key"""
        return int(hashlib.md5(key.encode()).hexdigest(), 16)
    
    def add_node(self, node):
        """Add a node with virtual nodes"""
        for i in range(self.replicas):
            virtual_key = f"{node}:{i}"
            hash_val = self._hash(virtual_key)
            self.ring[hash_val] = node
            self.sorted_keys.append(hash_val)
        self.sorted_keys.sort()
    
    def remove_node(self, node):
        """Remove a node and its virtual nodes"""
        for i in range(self.replicas):
            virtual_key = f"{node}:{i}"
            hash_val = self._hash(virtual_key)
            del self.ring[hash_val]
            self.sorted_keys.remove(hash_val)
    
    def get_node(self, key):
        """Get the node responsible for this key"""
        if not self.ring:
            return None
        
        hash_val = self._hash(key)
        
        # Find first node clockwise from hash
        idx = bisect_right(self.sorted_keys, hash_val)
        if idx == len(self.sorted_keys):
            idx = 0  # Wrap around
        
        return self.ring[self.sorted_keys[idx]]

# Usage
ch = ConsistentHash(['server1', 'server2', 'server3'])
print(ch.get_node('user:123'))  # → server2
print(ch.get_node('user:456'))  # → server1

ch.add_node('server4')
print(ch.get_node('user:123'))  # → might still be server2
```

## Replication with Consistent Hashing

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         REPLICATION STRATEGY                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

For replication factor = 3, store on the next 3 DISTINCT physical servers:

           ● S1
          / 
    K1 ●─/─────▶ S1 (primary)
      \        ↘ S2 (replica 1) 
       \        ↘ S3 (replica 2)
        \
         ● S2
             \
              ● S3

Skip virtual nodes of the same physical server!

def get_replicas(key, n=3):
    primary_hash = hash(key)
    replicas = []
    seen_servers = set()
    
    idx = find_next_node(primary_hash)
    while len(replicas) < n:
        server = ring[sorted_keys[idx]]
        if server not in seen_servers:
            replicas.append(server)
            seen_servers.add(server)
        idx = (idx + 1) % len(sorted_keys)
    
    return replicas
```

## Real-World Usage

### Amazon DynamoDB / Cassandra
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  • Partition key hashed to ring                                                         │
│  • Data replicated to N successive nodes                                                │
│  • Coordinator node handles read/write                                                  │
│  • Virtual nodes for even distribution                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Load Balancer (Sticky Sessions)
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  hash(session_id) → server                                                              │
│  Same session always routes to same server                                              │
│  Server failure: only that server's sessions need re-routing                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### Distributed Cache
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  hash(cache_key) → cache_server                                                         │
│  Adding cache server: minimal cache invalidation                                        │
│  Removing server: only its keys need refetching                                        │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## When to Use

✅ **Good for:**
- Distributed caches (Redis cluster, Memcached)
- Distributed databases (DynamoDB, Cassandra)
- Load balancing with sticky sessions
- Sharding data across servers
- Content delivery networks

❌ **Not ideal for:**
- Single server setups
- When all data must be on all servers
- Very small clusters (<3 nodes)

## Trade-offs

| Pros | Cons |
|------|------|
| Minimal redistribution | More complex than modulo |
| Horizontal scaling | Virtual nodes need memory |
| Fault tolerant | Non-uniform distribution without vnodes |
| Incremental growth | Hash function consistency critical |

## Key Metrics

```
Redistribution on node change:
  Naive hashing: (N-1)/N keys move (~75% for 4 servers)
  Consistent hashing: K/N keys move (~25% for 4 servers)
  
  With 1M keys and 10 servers:
    Naive: ~900K keys move
    Consistent: ~100K keys move
```

## Used In (This Guide)

- **Sharding (Q17)**: Database partitioning strategy
- **Distributed Cache (Q38)**: Key distribution across cache nodes
- **Key-Value Store (Q40)**: Data partitioning
- **Load Balancer (Q23)**: Server selection
