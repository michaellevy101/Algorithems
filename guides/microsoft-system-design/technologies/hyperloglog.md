# HyperLogLog

## What It Is
HyperLogLog (HLL) is a probabilistic data structure for estimating the cardinality (count of unique elements) of a set using very little memory. It can count billions of unique items using only 12KB of memory with ~2% error.

## The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COUNTING UNIQUE VISITORS                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Exact counting:
  Store all unique user IDs in a Set
  50 million users × 8 bytes = 400 MB memory
  
  Memory grows with number of unique elements!
  
HyperLogLog:
  12 KB memory
  50 million users counted
  Error: ~2% (49-51 million)
  
  Memory is CONSTANT regardless of cardinality!
```

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HYPERLOGLOG INTUITION                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

COIN FLIP ANALOGY:
  If you flip coins and track the longest streak of heads:
  
  1 head in a row  → ~2 unique flips
  2 heads in a row → ~4 unique flips
  3 heads in a row → ~8 unique flips
  n heads in a row → ~2^n unique flips
  
  The max streak tells you roughly how many flips occurred!

HASH-BASED APPROACH:
  1. Hash each element to get random-looking bits
  2. Count leading zeros in hash
  3. Track maximum leading zeros seen
  
  hash("user1") = 0001011... (3 leading zeros)
  hash("user2") = 0000001... (6 leading zeros) ← new max!
  hash("user3") = 0101001... (1 leading zero)
  
  Max leading zeros = 6 → Estimate ≈ 2^6 = 64 unique elements
```

## Algorithm Details

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HYPERLOGLOG ALGORITHM                                            │
└─────────────────────────────────────────────────────────────────────────────────────────┘

PROBLEM: Single max is too variable. Solution: Use multiple "buckets"

1. HASH the element (64-bit hash)
   hash("user123") = 0110100101110010...

2. SPLIT hash into bucket index and value
   First 14 bits → bucket index (0-16383)
   Remaining 50 bits → count leading zeros
   
   ┌──────────────┬───────────────────────────────────────────┐
   │ 01101001011  │ 10010101101010101010101010101010101010... │
   │  (14 bits)   │              (50 bits)                    │
   │  bucket=1707 │              leading zeros = 0            │
   └──────────────┴───────────────────────────────────────────┘

3. UPDATE bucket's max
   buckets[1707] = max(buckets[1707], leading_zeros + 1)

4. ESTIMATE cardinality
   Use harmonic mean of 2^bucket[i] across all buckets
   Apply bias correction factors
   
MEMORY:
  16384 buckets × 6 bits each = 12 KB
  (6 bits can store values 0-63, enough for 2^63 cardinality)
```

## Implementation

```python
import hashlib

class HyperLogLog:
    def __init__(self, precision=14):
        self.p = precision              # 14 bits for bucket index
        self.m = 1 << precision         # 16384 buckets
        self.registers = [0] * self.m
        
        # Bias correction constant
        self.alpha = 0.7213 / (1 + 1.079 / self.m)
    
    def _hash(self, value):
        """64-bit hash"""
        h = hashlib.sha256(str(value).encode()).hexdigest()
        return int(h[:16], 16)  # First 64 bits
    
    def _leading_zeros(self, value, max_bits=50):
        """Count leading zeros after bucket bits"""
        if value == 0:
            return max_bits
        return max_bits - value.bit_length()
    
    def add(self, value):
        h = self._hash(value)
        
        # First p bits = bucket index
        bucket = h >> (64 - self.p)
        
        # Remaining bits = count leading zeros
        remaining = h & ((1 << (64 - self.p)) - 1)
        zeros = self._leading_zeros(remaining, 64 - self.p) + 1
        
        # Update max for this bucket
        self.registers[bucket] = max(self.registers[bucket], zeros)
    
    def count(self):
        # Harmonic mean
        Z = sum(2 ** (-r) for r in self.registers)
        E = self.alpha * self.m * self.m / Z
        
        # Bias correction for small cardinalities
        if E <= 2.5 * self.m:
            V = self.registers.count(0)  # Empty buckets
            if V > 0:
                E = self.m * math.log(self.m / V)  # Linear counting
        
        return int(E)

# Usage
hll = HyperLogLog()
for i in range(1000000):
    hll.add(f"user_{i}")

print(hll.count())  # ~1,000,000 (±2%)
```

## Redis Implementation

```redis
# Add elements
PFADD visitors user1 user2 user3

# Get count
PFCOUNT visitors
→ 3

# Add more (including duplicate)
PFADD visitors user2 user4 user5

# Count (user2 not double-counted)
PFCOUNT visitors
→ 5

# Merge multiple HLLs
PFADD visitors_us user1 user2 user3
PFADD visitors_eu user2 user4 user5
PFMERGE visitors_all visitors_us visitors_eu
PFCOUNT visitors_all
→ 5

# Memory usage
MEMORY USAGE visitors
→ ~12KB (regardless of cardinality!)
```

## Use Cases

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMMON USE CASES                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. UNIQUE VISITORS
   PFADD page:/home user1 user2 user3
   PFCOUNT page:/home → ~unique visitors today

2. UNIQUE SEARCH QUERIES
   PFADD searches:2024-01 "what is redis" "how to code"
   PFCOUNT searches:2024-01 → ~unique queries this month

3. UNIQUE IPs
   PFADD ips:api user_ip1 user_ip2
   PFCOUNT ips:api → ~unique IPs hitting API

4. COUNT DISTINCT IN STREAMS
   For each event in Kafka:
     PFADD users:hourly event.user_id
   Every hour: PFCOUNT users:hourly

5. CARDINALITY ESTIMATION FOR DATABASES
   Before: SELECT COUNT(DISTINCT user_id) FROM events  -- slow
   After:  Maintain HLL, get instant estimates
```

## Accuracy

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ERROR RATES                                                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Standard Error = 1.04 / √m

Precision (p) │ Buckets (m) │ Memory │ Std Error
──────────────┼─────────────┼────────┼──────────
     10       │    1024     │  1 KB  │   3.25%
     12       │    4096     │  3 KB  │   1.62%
     14       │   16384     │ 12 KB  │   0.81%  ← Redis default
     16       │   65536     │ 48 KB  │   0.41%
     18       │  262144     │192 KB  │   0.20%

EXAMPLE (p=14):
  Actual: 1,000,000 unique visitors
  Range:  ~984,000 to ~1,016,000 (±1.6% with 95% confidence)
  
  For dashboards, this is perfectly acceptable!
```

## When to Use

✅ **Good for:**
- Counting unique visitors/users
- Cardinality estimation in analytics
- Approximate COUNT DISTINCT
- Real-time dashboards (where ~2% error is OK)
- Memory-constrained environments

❌ **Not ideal for:**
- Exact counts required
- Need to enumerate elements
- Very small cardinalities (<1000)
- Need to remove elements (HLL is add-only)

## Trade-offs

| Pros | Cons |
|------|------|
| Constant memory (~12KB) | ~2% error |
| Very fast O(1) add/count | Can't list elements |
| Mergeable (union operation) | Can't remove elements |
| Works with streaming data | Not suitable for exact counts |

## Related Algorithms

| Algorithm | Use Case | Memory |
|-----------|----------|--------|
| HyperLogLog | Count unique | O(1) |
| Bloom Filter | Set membership | O(n) but small |
| Count-Min Sketch | Frequency estimation | O(1) |
| T-Digest | Percentiles | O(1) |

## Used In (This Guide)

- **Web Analytics (Q44)**: Unique visitor counts
- **Logging (Q22)**: Unique error counts
- **Real-time Analytics**: Dashboard metrics
