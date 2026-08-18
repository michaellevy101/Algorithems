# Rate Limiter

## What It Is
A rate limiter controls the rate of incoming requests to a service, protecting it from being overwhelmed. It's essential for preventing abuse, ensuring fair usage, and protecting system stability.

## Why Rate Limiting

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WHY RATE LIMIT?                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. PREVENT ABUSE
   - DDoS attacks
   - Brute force attempts
   - Scraping/crawling

2. ENSURE FAIR USAGE
   - No single user monopolizes resources
   - Premium users get higher limits

3. COST CONTROL
   - Prevent runaway API costs
   - Limit expensive operations

4. SYSTEM STABILITY
   - Prevent cascading failures
   - Graceful degradation
```

## Common Algorithms

### 1. Token Bucket

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         TOKEN BUCKET                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Concept: Bucket holds tokens, requests consume tokens, tokens refill at fixed rate

          ┌─────────────────┐
  Refill  │                 │ ← Bucket (capacity = 10)
  1/sec   │  ●●●●●●●        │   Currently 7 tokens
          │                 │
          └────────┬────────┘
                   │ Request consumes 1 token
                   ▼
            ┌────────────┐
            │   API      │
            └────────────┘

EXAMPLE:
  - Bucket capacity: 10 tokens
  - Refill rate: 1 token/second
  - Each request costs: 1 token
  
  Time 0s:  10 tokens, 5 requests → 5 tokens remain
  Time 1s:  6 tokens (refilled 1)
  Time 2s:  7 tokens
  ...
  Time 5s:  10 tokens (capped at capacity)

ALLOWS BURSTS: User can use all 10 tokens instantly if available
```

```python
import time

class TokenBucket:
    def __init__(self, capacity, refill_rate):
        self.capacity = capacity      # Max tokens
        self.tokens = capacity        # Current tokens
        self.refill_rate = refill_rate  # Tokens per second
        self.last_refill = time.time()
    
    def _refill(self):
        now = time.time()
        elapsed = now - self.last_refill
        self.tokens = min(
            self.capacity,
            self.tokens + elapsed * self.refill_rate
        )
        self.last_refill = now
    
    def allow(self, tokens=1):
        self._refill()
        if self.tokens >= tokens:
            self.tokens -= tokens
            return True
        return False
```

### 2. Sliding Window Log

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SLIDING WINDOW LOG                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Keep timestamps of all requests in current window

  Window: 1 minute, Limit: 5 requests
  
  Time: 10:00:45
  Log: [10:00:10, 10:00:20, 10:00:30, 10:00:40, 10:00:43]
        ↑ expired (>1 min ago)
  
  After cleanup: [10:00:20, 10:00:30, 10:00:40, 10:00:43]
  Count: 4 → Can accept 1 more request
  
  ┌─────────────────────────────────────────────────────────────────┐
  │                    1 minute window                               │
  │  ──────────────────────────────────────────────────────────────▶│
  │  │    •    •    •    •    •  NOW                               │
  │  9:59:45                   10:00:45                             │
  │  (expired)                                                      │
  └─────────────────────────────────────────────────────────────────┘

PROS: Very accurate
CONS: Memory intensive (stores all timestamps)
```

### 3. Sliding Window Counter

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SLIDING WINDOW COUNTER                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Hybrid: Fixed windows + weighted average for current position

  Window: 1 minute, Limit: 100 requests
  
  Previous minute: 84 requests
  Current minute: 36 requests (25% into minute)
  
  Weighted count = 84 × 0.75 + 36 × 0.25 = 63 + 9 = 72
  
  ┌────────────────────────┬────────────────────────┐
  │   Previous: 84 req     │   Current: 36 req      │
  │   Weight: 75%          │   Weight: 25%          │
  │                        │▓▓▓▓▓░░░░░░░░░░░░░░░░░░│
  └────────────────────────┴────────────────────────┘
                               ↑ 25% into current window

PROS: Memory efficient (2 counters)
CONS: Approximation (not exact)
```

### 4. Leaky Bucket

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         LEAKY BUCKET                                                     │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Requests enter queue, processed at constant rate (like water leaking from bucket)

        Requests arrive (variable rate)
              ↓ ↓   ↓↓↓ ↓
          ┌─────────────────┐
          │  ▓▓▓▓▓▓▓▓▓▓    │ ← Queue (bucket)
          │  ▓▓▓▓▓▓▓▓▓▓    │   Overflow = rejected
          └───────┬─────────┘
                  │ Leak at constant rate (1/sec)
                  ▼
            ┌──────────┐
            │ Process  │ ← Smooth output
            └──────────┘

DIFFERENCE FROM TOKEN BUCKET:
  Token Bucket: Allows bursts (use all tokens at once)
  Leaky Bucket: Smooths traffic (constant output rate)
```

## Redis Implementation

```redis
-- Token Bucket in Redis (Lua script for atomicity)
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

-- Get current state
local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(data[1]) or capacity
local last_refill = tonumber(data[2]) or now

-- Refill tokens
local elapsed = now - last_refill
tokens = math.min(capacity, tokens + elapsed * refill_rate)

-- Check and consume
if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 60)
    return 1  -- Allowed
else
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 60)
    return 0  -- Denied
end
```

```python
# Python client
import redis
import time

class RedisRateLimiter:
    def __init__(self, redis_client, capacity, refill_rate):
        self.redis = redis_client
        self.capacity = capacity
        self.refill_rate = refill_rate
        self.script = self.redis.register_script(LUA_SCRIPT)
    
    def allow(self, user_id, tokens=1):
        key = f"ratelimit:{user_id}"
        now = time.time()
        result = self.script(
            keys=[key],
            args=[self.capacity, self.refill_rate, now, tokens]
        )
        return result == 1

# Usage
limiter = RedisRateLimiter(redis.Redis(), capacity=10, refill_rate=1)

if limiter.allow("user123"):
    process_request()
else:
    return_429_too_many_requests()
```

## Distributed Rate Limiting

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         DISTRIBUTED ARCHITECTURE                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘

CHALLENGE: Multiple servers need consistent rate limiting

SOLUTION 1: Centralized (Redis)
  ┌─────────┐     ┌─────────┐     ┌─────────┐
  │ Server1 │     │ Server2 │     │ Server3 │
  └────┬────┘     └────┬────┘     └────┬────┘
       │               │               │
       └───────────────┼───────────────┘
                       ▼
                 ┌──────────┐
                 │  Redis   │ ← Single source of truth
                 │ (Cluster)│
                 └──────────┘

SOLUTION 2: Local + Sync
  Each server has local limiter
  Periodically sync with central store
  
  Pros: Lower latency
  Cons: Less accurate (eventual consistency)

SOLUTION 3: Sticky Sessions
  Route same user to same server
  Local rate limiting per server
  
  Pros: No coordination needed
  Cons: Uneven distribution
```

## HTTP Headers

```http
# Response headers for rate limiting

HTTP/1.1 200 OK
X-RateLimit-Limit: 100        # Max requests per window
X-RateLimit-Remaining: 42     # Requests remaining
X-RateLimit-Reset: 1620000000 # Unix timestamp when window resets
Retry-After: 30               # Seconds to wait (on 429)

# When rate limited:
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 30

{
  "error": "Rate limit exceeded",
  "retry_after": 30,
  "limit": 100,
  "window": "1 minute"
}
```

## Comparison

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ALGORITHM COMPARISON                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────┬───────────────┬──────────────┬───────────────────────────────────┐
│ Algorithm           │ Memory        │ Accuracy     │ Best For                          │
├─────────────────────┼───────────────┼──────────────┼───────────────────────────────────┤
│ Token Bucket        │ O(1)          │ Good         │ Bursty traffic, APIs              │
├─────────────────────┼───────────────┼──────────────┼───────────────────────────────────┤
│ Leaky Bucket        │ O(queue size) │ Exact        │ Smoothing traffic                 │
├─────────────────────┼───────────────┼──────────────┼───────────────────────────────────┤
│ Fixed Window        │ O(1)          │ Boundary bug │ Simple cases                      │
├─────────────────────┼───────────────┼──────────────┼───────────────────────────────────┤
│ Sliding Window Log  │ O(n)          │ Exact        │ When accuracy critical            │
├─────────────────────┼───────────────┼──────────────┼───────────────────────────────────┤
│ Sliding Window Cnt  │ O(1)          │ Approximate  │ Most production systems           │
└─────────────────────┴───────────────┴──────────────┴───────────────────────────────────┘

Fixed Window Boundary Bug:
  Limit: 5 req/min
  Time 0:59: 5 requests (allowed)
  Time 1:01: 5 requests (allowed)
  → 10 requests in 2 seconds! (exploits window boundary)
```

## When to Use

✅ **Good for:**
- API rate limiting
- Login attempt limiting
- Preventing abuse
- Fair resource allocation
- Cost control

❌ **Consider alternatives for:**
- Internal service-to-service (use circuit breaker)
- Very high throughput (adds latency)
- When exact limits not critical

## Trade-offs

| Pros | Cons |
|------|------|
| Protects services | Adds latency |
| Prevents abuse | Complexity |
| Fair resource usage | False positives possible |
| Cost control | User frustration |

## Used In (This Guide)

- **API Gateway (Q2)**: Request throttling
- **DDoS Protection (Q35)**: Attack mitigation
- **User Authentication**: Login attempt limits
- **Payment Processing (Q47)**: Transaction limits
