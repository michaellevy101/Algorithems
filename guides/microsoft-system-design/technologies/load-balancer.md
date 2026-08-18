# Load Balancer

## What It Is
A load balancer distributes incoming network traffic across multiple servers to ensure no single server is overwhelmed, improving availability and responsiveness.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         LOAD BALANCER OVERVIEW                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
        Clients               │  LOAD BALANCER  │               Servers
                              │                 │
    ┌─────────┐               │  Distributes    │           ┌─────────┐
    │ Client1 │──────────────▶│  traffic across │──────────▶│ Server1 │
    └─────────┘               │  healthy        │           └─────────┘
    ┌─────────┐               │  servers        │           ┌─────────┐
    │ Client2 │──────────────▶│                 │──────────▶│ Server2 │
    └─────────┘               │  Health checks  │           └─────────┘
    ┌─────────┐               │  detect failed  │           ┌─────────┐
    │ Client3 │──────────────▶│  servers        │──────────▶│ Server3 │
    └─────────┘               └─────────────────┘           └─────────┘
```

## Load Balancing Algorithms

### 1. Round Robin
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Requests distributed in order: A → B → C → A → B → C → ...                            │
│                                                                                          │
│  Request 1 → Server A                                                                   │
│  Request 2 → Server B                                                                   │
│  Request 3 → Server C                                                                   │
│  Request 4 → Server A                                                                   │
│                                                                                          │
│  ✓ Simple, fair distribution                                                            │
│  ✗ Doesn't account for server capacity or current load                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2. Weighted Round Robin
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Server A: weight 5 (powerful)                                                          │
│  Server B: weight 3 (medium)                                                            │
│  Server C: weight 2 (small)                                                             │
│                                                                                          │
│  10 requests: A A A A A B B B C C                                                       │
│                                                                                          │
│  ✓ Accounts for server capacity                                                         │
│  ✗ Static weights, doesn't adapt to actual load                                        │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3. Least Connections
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Route to server with fewest active connections                                         │
│                                                                                          │
│  Server A: 10 connections                                                               │
│  Server B: 5 connections  ← Next request goes here                                     │
│  Server C: 8 connections                                                                │
│                                                                                          │
│  ✓ Good for varying request durations                                                  │
│  ✓ Naturally adapts to server load                                                     │
│  ✗ Doesn't account for connection weight                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 4. IP Hash (Sticky Sessions)
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  hash(client_ip) % num_servers = server_index                                           │
│                                                                                          │
│  Client 192.168.1.10 → hash → always Server A                                          │
│  Client 192.168.1.20 → hash → always Server B                                          │
│                                                                                          │
│  ✓ Session persistence without shared state                                            │
│  ✗ Uneven distribution if client IPs not diverse                                       │
│  ✗ Server failure requires session migration                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 5. Least Response Time
```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  Route to server with lowest (connections × response_time)                             │
│                                                                                          │
│  Server A: 10 conn × 50ms = 500                                                        │
│  Server B: 5 conn × 80ms = 400  ← Choose this one                                      │
│  Server C: 3 conn × 200ms = 600                                                        │
│                                                                                          │
│  ✓ Best for user experience                                                            │
│  ✗ More complex to implement                                                           │
│  ✗ Requires active monitoring                                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## Layer 4 vs Layer 7

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         OSI LAYER COMPARISON                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

LAYER 4 (Transport - TCP/UDP):
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  • Operates on IP addresses and ports                                                   │
│  • Cannot inspect HTTP content                                                          │
│  • Fast, low overhead                                                                   │
│  • Good for: TCP passthrough, gaming, non-HTTP                                         │
│                                                                                          │
│  Client ──TCP──▶ LB ──TCP──▶ Server                                                    │
│            (same connection passed through)                                             │
└─────────────────────────────────────────────────────────────────────────────────────────┘

LAYER 7 (Application - HTTP):
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│  • Terminates HTTP, inspects headers/content                                            │
│  • Can route by URL path, headers, cookies                                             │
│  • SSL termination                                                                      │
│  • More features, more overhead                                                         │
│                                                                                          │
│  Client ──HTTP──▶ LB ──HTTP──▶ Server                                                  │
│          (LB terminates and creates new connection)                                    │
│                                                                                          │
│  ROUTING RULES:                                                                         │
│    /api/*     → API servers                                                            │
│    /images/*  → Static servers                                                         │
│    /admin/*   → Admin servers                                                          │
│    Host: api.example.com → API cluster                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

## Health Checks

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         HEALTH CHECK TYPES                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. TCP CHECK (Layer 4)
   • Connect to port, if success → healthy
   • Fast, but doesn't verify application
   
2. HTTP CHECK (Layer 7)
   GET /health
   200 OK → healthy
   5xx or timeout → unhealthy
   
3. SCRIPT CHECK
   Run custom script, check exit code
   
CONFIGURATION:
  health_check {
    interval: 10s          # Check every 10 seconds
    timeout: 5s            # Wait 5 seconds for response
    unhealthy_threshold: 3 # Mark unhealthy after 3 failures
    healthy_threshold: 2   # Mark healthy after 2 successes
  }

RESPONSE:
  {
    "status": "healthy",
    "database": "connected",
    "cache": "connected",
    "uptime": "5d 12h"
  }
```

## SSL/TLS Termination

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SSL TERMINATION                                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

OPTION 1: TERMINATE AT LOAD BALANCER
  Client ──HTTPS──▶ LB ──HTTP──▶ Server
                    │
                    └── Decrypts here, plain HTTP to backend
                    
  ✓ Backend servers don't need SSL certs
  ✓ Offloads CPU from app servers
  ✓ Centralized certificate management
  ✗ Traffic unencrypted inside network

OPTION 2: SSL PASSTHROUGH
  Client ──HTTPS──▶ LB ──HTTPS──▶ Server
                    │
                    └── Just routes, doesn't decrypt
                    
  ✓ End-to-end encryption
  ✗ Can't do L7 routing
  ✗ Each server needs certificate

OPTION 3: RE-ENCRYPTION
  Client ──HTTPS──▶ LB ──HTTPS──▶ Server
                    │
                    └── Decrypts and re-encrypts
                    
  ✓ End-to-end encryption
  ✓ Can do L7 routing
  ✗ More CPU overhead
```

## Azure Load Balancing Options

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         AZURE SERVICES                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────┬───────────┬────────────────────────────────────────────────────────┐
│ Service            │ Layer     │ Use Case                                               │
├────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ Azure Load         │ L4        │ Internal/external TCP/UDP                              │
│ Balancer           │           │ High performance, low latency                          │
├────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ Application        │ L7        │ HTTP/HTTPS, SSL termination                            │
│ Gateway            │           │ URL routing, WAF                                       │
├────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ Azure Front Door   │ L7        │ Global load balancing                                  │
│                    │ (Global)  │ CDN, WAF, SSL, acceleration                            │
├────────────────────┼───────────┼────────────────────────────────────────────────────────┤
│ Traffic Manager    │ DNS       │ DNS-based global routing                               │
│                    │           │ Failover, geo-routing                                  │
└────────────────────┴───────────┴────────────────────────────────────────────────────────┘

WHEN TO USE WHAT:
  • Single region, TCP/UDP → Azure Load Balancer
  • Single region, HTTP/HTTPS → Application Gateway
  • Multi-region HTTP → Azure Front Door
  • Multi-region DNS failover → Traffic Manager
```

## When to Use

✅ **Good for:**
- Distributing web traffic
- High availability (failover)
- Horizontal scaling
- SSL termination
- A/B testing (weighted routing)

❌ **Not ideal for:**
- Single server setups
- Stateful connections without sticky sessions
- Real-time gaming (use dedicated servers)

## Trade-offs

| Pros | Cons |
|------|------|
| High availability | Added latency |
| Horizontal scaling | Single point of failure (need HA LB) |
| SSL offloading | Configuration complexity |
| Health checking | Cost |

## Used In (This Guide)

- **Scalable Azure Service (Q2)**: Multi-region routing
- **Load Balancer Design (Q23)**: Full architecture
- **High Availability (Q14)**: Failover patterns
- **CDN (Q7)**: Edge load balancing
