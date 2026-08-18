# Bloom Filter

## What It Is
A Bloom filter is a space-efficient probabilistic data structure that tests whether an element is a member of a set. It can have false positives (saying "maybe in set" when not) but never false negatives (if it says "definitely not", it's not).

## The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CHECKING SET MEMBERSHIP                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Exact checking (HashSet):
  Store 100 million URLs = ~5 GB memory
  Query: O(1) but expensive storage
  
Bloom Filter:
  100 million URLs with 1% false positive = ~120 MB
  Query: O(k) where k = number of hash functions
  
  Trade space for occasional false positives!
```

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         BLOOM FILTER STRUCTURE                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Structure: Bit array of m bits, all initialized to 0
           k independent hash functions

ADD("apple"):
  h1("apple") = 3
  h2("apple") = 7
  h3("apple") = 11
  
  Set bits 3, 7, 11 to 1
  
  Bit array: [0 0 0 1 0 0 0 1 0 0 0 1 0 0 0 0]
                   ↑         ↑       ↑
                   3         7      11

ADD("banana"):
  h1("banana") = 1
  h2("banana") = 7  ← already set (overlap!)
  h3("banana") = 14
  
  Bit array: [0 1 0 1 0 0 0 1 0 0 0 1 0 0 1 0]
               ↑                           ↑
               1                          14
```

## Query Operation

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         MEMBERSHIP TEST                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

QUERY("apple"):
  h1("apple") = 3  → bit[3] = 1 ✓
  h2("apple") = 7  → bit[7] = 1 ✓
  h3("apple") = 11 → bit[11] = 1 ✓
  
  All bits set → "POSSIBLY IN SET" (true positive)

QUERY("grape"):
  h1("grape") = 2  → bit[2] = 0 ✗
  
  Any bit is 0 → "DEFINITELY NOT IN SET" (always correct!)

QUERY("cherry"):
  h1("cherry") = 3  → bit[3] = 1 ✓  (set by "apple")
  h2("cherry") = 1  → bit[1] = 1 ✓  (set by "banana")
  h3("cherry") = 7  → bit[7] = 1 ✓  (set by both)
  
  All bits set → "POSSIBLY IN SET" (FALSE POSITIVE!)
  Cherry was never added, but all its hash positions were set by other elements
```

## Mathematical Properties

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         FALSE POSITIVE RATE                                              │
└─────────────────────────────────────────────────────────────────────────────────────────┘

False positive probability ≈ (1 - e^(-kn/m))^k

Where:
  m = number of bits
  n = number of elements
  k = number of hash functions

OPTIMAL k = (m/n) × ln(2) ≈ 0.693 × (m/n)

EXAMPLE:
  Want: 1% false positive rate, 1 million elements
  
  m = -n × ln(p) / (ln(2))²
  m = -1,000,000 × ln(0.01) / (ln(2))²
  m ≈ 9,585,059 bits ≈ 1.2 MB
  
  k = (m/n) × ln(2) ≈ 7 hash functions

SIZE CALCULATOR:
┌───────────────┬────────────┬──────────┬──────────┐
│ Elements (n)  │ FP Rate    │ Bits (m) │ Memory   │
├───────────────┼────────────┼──────────┼──────────┤
│ 1 million     │ 1%         │ 9.6M     │ 1.2 MB   │
│ 1 million     │ 0.1%       │ 14.4M    │ 1.8 MB   │
│ 100 million   │ 1%         │ 958M     │ 120 MB   │
│ 1 billion     │ 1%         │ 9.6B     │ 1.2 GB   │
└───────────────┴────────────┴──────────┴──────────┘
```

## Implementation

```python
import mmh3  # MurmurHash3
from bitarray import bitarray

class BloomFilter:
    def __init__(self, size, num_hash_functions):
        self.size = size
        self.num_hash = num_hash_functions
        self.bit_array = bitarray(size)
        self.bit_array.setall(0)
    
    def _hashes(self, item):
        """Generate k hash positions"""
        for i in range(self.num_hash):
            yield mmh3.hash(str(item), i) % self.size
    
    def add(self, item):
        """Add item to filter"""
        for pos in self._hashes(item):
            self.bit_array[pos] = 1
    
    def __contains__(self, item):
        """Check if item might be in filter"""
        return all(self.bit_array[pos] for pos in self._hashes(item))
    
    @classmethod
    def optimal_params(cls, n, fp_rate):
        """Calculate optimal size and hash count"""
        import math
        m = int(-n * math.log(fp_rate) / (math.log(2) ** 2))
        k = int((m / n) * math.log(2))
        return m, k

# Usage
n = 1_000_000  # Expected elements
fp_rate = 0.01  # 1% false positive

size, num_hash = BloomFilter.optimal_params(n, fp_rate)
bf = BloomFilter(size, num_hash)

# Add URLs
bf.add("https://example.com/page1")
bf.add("https://example.com/page2")

# Query
if "https://example.com/page1" in bf:
    print("Possibly in set (need to verify)")
    
if "https://new-url.com" not in bf:
    print("Definitely NOT in set (100% certain)")
```

## Redis Implementation

```redis
# Redis has built-in Bloom Filter module
# (requires RedisBloom module)

# Create filter
BF.RESERVE urls 0.01 1000000
# 0.01 = 1% false positive rate
# 1000000 = expected elements

# Add elements
BF.ADD urls "https://example.com/page1"
BF.MADD urls "url2" "url3" "url4"

# Check membership
BF.EXISTS urls "https://example.com/page1"
→ 1 (possibly exists)

BF.EXISTS urls "never-added-url"
→ 0 (definitely doesn't exist)

# Batch check
BF.MEXISTS urls "url1" "url2" "url999"
→ [1, 1, 0]

# Info
BF.INFO urls
→ Capacity: 1000000
   Size: 1247510 (bytes)
   Number of filters: 1
   Number of items inserted: 4
   Expansion rate: 2
```

## Use Cases

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMMON USE CASES                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. CACHE MISS PREVENTION
   Before querying database:
   
   if url not in bloom_filter:
       return "Not found"  # Avoid DB query!
   
   result = database.query(url)  # Only if possibly exists
   
2. DUPLICATE DETECTION
   For each incoming URL:
   
   if url in bloom_filter:
       skip("Possibly duplicate")
   else:
       bloom_filter.add(url)
       process(url)

3. PASSWORD CHECK (Have I Been Pwned)
   Store 600M breached passwords
   Bloom filter: ~1 GB (vs 20+ GB for full list)
   
   if password in breached_bloom_filter:
       warn("This password may have been compromised")

4. MALWARE URL DETECTION (Google Safe Browsing)
   Store billions of malicious URLs
   Check every URL visited by browser
   
5. DATABASE OPTIMIZATION
   BigTable, Cassandra, RocksDB use Bloom filters
   to avoid disk reads for non-existent keys
```

## Bloom Filter Variants

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         VARIANTS                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. COUNTING BLOOM FILTER
   Use counters instead of bits
   Supports deletion!
   
   Add: counter[h(x)]++
   Delete: counter[h(x)]--
   
   4 bits per counter = 4× memory
   
2. SCALABLE BLOOM FILTER
   Chain of filters that grow automatically
   When one fills up, create a new one
   
   [Filter1] → [Filter2] → [Filter3]
      ↓           ↓           ↓
   Check all, add to latest

3. CUCKOO FILTER
   Better deletion support
   Better space efficiency for low FP rates
   Slightly more complex

4. QUOTIENT FILTER
   Supports deletion
   Better cache locality
```

## When to Use

✅ **Good for:**
- Cache existence checks
- Duplicate detection in streams
- Database query optimization
- Web crawlers (URL dedup)
- Network packet filtering

❌ **Not ideal for:**
- Need to enumerate elements
- Need deletion (use Counting Bloom Filter)
- Need 100% accuracy
- Very small datasets (use HashSet)

## Trade-offs

| Pros | Cons |
|------|------|
| Space efficient | False positives |
| O(k) operations | No deletion (standard) |
| Simple implementation | One-way (can't list elements) |
| No false negatives | Fixed size (or use Scalable BF) |

## Used In (This Guide)

- **Search Autocomplete (Q5)**: Filter out invalid queries
- **Web Crawler (Q9)**: URL deduplication
- **Caching Systems**: Avoid cache stampede
- **Distributed Storage**: Skip non-existent key lookups
