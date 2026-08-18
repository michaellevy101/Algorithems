# Circuit Breaker Pattern

## What It Is
The Circuit Breaker pattern prevents cascading failures in distributed systems by failing fast when a service is unhealthy, giving it time to recover instead of overwhelming it with requests.

## The Problem

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CASCADING FAILURE                                                │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Service A → Service B → Service C (down)

WITHOUT CIRCUIT BREAKER:
  ┌─────────┐     ┌─────────┐     ┌─────────┐
  │Service A│────▶│Service B│────▶│Service C│ ✗ Down
  └─────────┘     └─────────┘     └─────────┘
       │               │               │
       │   Threads     │   Threads     │
       │   waiting...  │   waiting...  │
       │               │               │
  
  • Service C is slow/down
  • Service B threads block waiting for C
  • Service B's thread pool exhausts
  • Service B becomes unresponsive
  • Service A's threads block waiting for B
  • Service A becomes unresponsive
  
  ONE SERVICE FAILURE → ENTIRE SYSTEM DOWN!
```

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CIRCUIT BREAKER STATES                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────┐
                    │       CLOSED        │
                    │  (Normal operation) │
                    │                     │
                    │  Requests pass      │
                    │  through to service │
                    └──────────┬──────────┘
                               │
                    Failure threshold exceeded
                    (e.g., 50% failures in 10s)
                               │
                               ▼
                    ┌─────────────────────┐
                    │        OPEN         │
                    │   (Failing fast)    │
                    │                     │
                    │  Requests fail      │◀─────┐
                    │  immediately        │      │ Still failing
                    └──────────┬──────────┘      │
                               │                 │
                    Timeout expires              │
                    (e.g., 30 seconds)           │
                               │                 │
                               ▼                 │
                    ┌─────────────────────┐      │
                    │     HALF-OPEN       │      │
                    │   (Testing health)  │      │
                    │                     │      │
                    │  Allow limited      │──────┘
                    │  test requests      │
                    └──────────┬──────────┘
                               │
                    Test requests succeed
                               │
                               ▼
                    ┌─────────────────────┐
                    │       CLOSED        │
                    │  (Back to normal)   │
                    └─────────────────────┘
```

## Implementation

```java
public class CircuitBreaker {
    private State state = State.CLOSED;
    private int failureCount = 0;
    private int successCount = 0;
    private long lastFailureTime = 0;
    
    // Configuration
    private final int failureThreshold = 5;      // Open after 5 failures
    private final long timeout = 30000;          // 30 seconds before half-open
    private final int halfOpenMaxCalls = 3;      // Test calls in half-open
    
    public <T> T execute(Supplier<T> action, Supplier<T> fallback) {
        if (!allowRequest()) {
            return fallback.get();  // Fail fast
        }
        
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            return fallback.get();
        }
    }
    
    private boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;
                
            case OPEN:
                if (System.currentTimeMillis() - lastFailureTime > timeout) {
                    state = State.HALF_OPEN;
                    successCount = 0;
                    return true;
                }
                return false;  // Still in timeout, fail fast
                
            case HALF_OPEN:
                return successCount < halfOpenMaxCalls;
                
            default:
                return false;
        }
    }
    
    private void onSuccess() {
        switch (state) {
            case HALF_OPEN:
                successCount++;
                if (successCount >= halfOpenMaxCalls) {
                    state = State.CLOSED;
                    failureCount = 0;
                }
                break;
            case CLOSED:
                failureCount = 0;
                break;
        }
    }
    
    private void onFailure() {
        lastFailureTime = System.currentTimeMillis();
        failureCount++;
        
        if (state == State.HALF_OPEN || failureCount >= failureThreshold) {
            state = State.OPEN;
        }
    }
}

// Usage
CircuitBreaker cb = new CircuitBreaker();

String result = cb.execute(
    () -> externalService.call(),           // Primary action
    () -> "Fallback response"               // Fallback when open
);
```

## Configuration Parameters

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         KEY PARAMETERS                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. FAILURE THRESHOLD
   • How many failures before opening circuit
   • Too low: Opens on transient errors
   • Too high: Doesn't protect fast enough
   • Typical: 5-10 failures or 50% failure rate

2. TIMEOUT (Reset timeout)
   • How long to wait before trying again
   • Too short: Doesn't give service time to recover
   • Too long: Unnecessarily slow recovery
   • Typical: 30-60 seconds

3. HALF-OPEN CALLS
   • How many test requests in half-open state
   • More calls = more confidence but slower recovery
   • Typical: 1-5 calls

4. SLIDING WINDOW
   • Time window for counting failures
   • Typical: 10-60 seconds

5. WHAT COUNTS AS FAILURE
   • Exceptions
   • Timeouts
   • HTTP 5xx responses
   • Slow responses (>2 seconds)
```

## Fallback Strategies

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         FALLBACK OPTIONS                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. RETURN CACHED DATA
   if (circuitOpen) {
       return cache.get(key);  // Stale but available
   }

2. RETURN DEFAULT VALUE
   if (circuitOpen) {
       return new Product("Unknown", 0.0);
   }

3. CALL BACKUP SERVICE
   if (circuitOpen) {
       return backupService.call();
   }

4. RETURN DEGRADED RESPONSE
   if (circuitOpen) {
       return recommendations.getPopular();  // Instead of personalized
   }

5. QUEUE FOR LATER
   if (circuitOpen) {
       queue.add(request);  // Process when service recovers
       return "Request queued";
   }

6. THROW EXCEPTION
   if (circuitOpen) {
       throw new ServiceUnavailableException("Try again later");
   }
```

## Libraries & Frameworks

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         POPULAR IMPLEMENTATIONS                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

RESILIENCE4J (Java):
  CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("serviceA");
  
  String result = circuitBreaker.executeSupplier(() -> 
      service.call()
  );

POLLY (.NET):
  var policy = Policy
      .Handle<HttpRequestException>()
      .CircuitBreaker(
          exceptionsAllowedBeforeBreaking: 5,
          durationOfBreak: TimeSpan.FromSeconds(30)
      );
  
  policy.Execute(() => service.Call());

HYSTRIX (Java, deprecated but influential):
  @HystrixCommand(fallbackMethod = "fallback")
  public String call() {
      return service.call();
  }

ISTIO (Service Mesh):
  apiVersion: networking.istio.io/v1alpha3
  kind: DestinationRule
  spec:
    trafficPolicy:
      outlierDetection:
        consecutive5xxErrors: 5
        interval: 30s
        baseEjectionTime: 30s
```

## Monitoring

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         METRICS TO MONITOR                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. CIRCUIT STATE
   • Current state (closed/open/half-open)
   • Time in current state
   • State transitions per hour

2. REQUEST METRICS
   • Total requests
   • Successful requests
   • Failed requests
   • Rejected requests (when open)

3. PERFORMANCE
   • Response time (p50, p95, p99)
   • Failure rate
   • Recovery time

ALERTS:
  • Circuit opened → Investigate service health
  • Circuit stuck open → Service not recovering
  • High rejection rate → Cascading impact
```

## When to Use

✅ **Good for:**
- Calling external services
- Database connections
- API calls to microservices
- Any remote call that can fail

❌ **Not ideal for:**
- Local method calls
- In-memory operations
- Critical paths that must never fail

## Trade-offs

| Pros | Cons |
|------|------|
| Prevents cascading failures | Added complexity |
| Fail fast | May reject valid requests |
| Gives services time to recover | Needs tuning |
| Provides fallback behavior | Monitoring required |

## Related Patterns

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Circuit Breaker + Retry:                                                               │
│    Retry for transient failures, circuit breaker for persistent failures               │
│                                                                                          │
│  Circuit Breaker + Timeout:                                                             │
│    Timeout prevents hanging, circuit breaker prevents repeated timeouts                │
│                                                                                          │
│  Circuit Breaker + Bulkhead:                                                            │
│    Bulkhead isolates failures, circuit breaker stops calling failed service           │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## Used In (This Guide)

- **Microservices (Q27)**: Service resilience
- **Payment Gateway (Q47)**: Handle processor failures
- **Distributed Systems**: All inter-service calls
