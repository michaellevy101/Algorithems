# CDN (Content Delivery Network)

## What It Is
A CDN is a geographically distributed network of servers that caches and delivers content (images, videos, JS, CSS) from locations close to users, reducing latency and offloading origin servers.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WITHOUT CDN                                                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘

User in Tokyo                                              Origin Server in Seattle
     │                                                              │
     │ ─────────── Request image.jpg (200ms RTT) ─────────────────▶│
     │ ◀────────── Response 500KB ────────────────────────────────│
     │                                                              │
     │   Latency: 200ms + transfer time                            │
     │   Every user hits origin server                             │


┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         WITH CDN                                                         │
└─────────────────────────────────────────────────────────────────────────────────────────┘

User in Tokyo             Edge Server in Tokyo              Origin Server in Seattle
     │                           │                                    │
     │ ── Request ──────────────▶│                                    │
     │    (20ms RTT)             │                                    │
     │                           │ (CACHE MISS - first request)       │
     │                           │────── Fetch from origin ──────────▶│
     │                           │◀───── Response ───────────────────│
     │ ◀─ Response ──────────────│ (Cache for future)                │
     │                           │                                    │
     │                           │                                    │
Next User in Tokyo               │                                    │
     │ ── Request ──────────────▶│                                    │
     │ ◀─ Response (CACHE HIT) ──│    Origin never touched!          │
     │    (20ms total!)          │                                    │
```

## CDN Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CDN NETWORK                                                      │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │  ORIGIN SERVER  │
                              │   (Your app)    │
                              └────────┬────────┘
                                       │
                    ┌──────────────────┼──────────────────┐
                    │                  │                  │
                    ▼                  ▼                  ▼
            ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
            │   SHIELD     │   │   SHIELD     │   │   SHIELD     │
            │  (US West)   │   │  (EU West)   │   │  (Asia)      │
            │              │   │              │   │              │
            │ Mid-tier     │   │ Mid-tier     │   │ Mid-tier     │
            │ cache        │   │ cache        │   │ cache        │
            └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
                   │                  │                  │
         ┌─────────┼─────────┐       ...       ┌─────────┼─────────┐
         ▼         ▼         ▼                 ▼         ▼         ▼
    ┌────────┐┌────────┐┌────────┐        ┌────────┐┌────────┐┌────────┐
    │  EDGE  ││  EDGE  ││  EDGE  │        │  EDGE  ││  EDGE  ││  EDGE  │
    │Seattle ││Portland││  LA    │        │ Tokyo  ││ Seoul  ││  HK    │
    └────────┘└────────┘└────────┘        └────────┘└────────┘└────────┘
        │         │         │                 │         │         │
      Users     Users     Users             Users     Users     Users

HIERARCHY:
  1. Edge (closest to users) - many locations, small cache
  2. Shield (regional) - fewer locations, larger cache
  3. Origin (your server) - single location, source of truth
```

## Cache Control Headers

```http
# Tell CDN how to cache
Cache-Control: public, max-age=31536000, immutable
│              │       │               │
│              │       │               └── Never revalidate (versioned assets)
│              │       └── Cache for 1 year
│              └── CDN can cache (not just browser)
└── Header name

# Common patterns:
Cache-Control: public, max-age=86400              # Cache 1 day
Cache-Control: private, max-age=3600              # Browser only, 1 hour
Cache-Control: no-cache                           # Revalidate every time
Cache-Control: no-store                           # Never cache (sensitive data)
Cache-Control: s-maxage=3600, max-age=60          # CDN: 1hr, Browser: 1min

# Versioned assets (immutable)
/static/app.a1b2c3d4.js  → max-age=31536000, immutable
/static/style.x9y8z7.css → max-age=31536000, immutable

# Dynamic content
/api/user/profile → private, no-cache
/api/products     → public, max-age=60, stale-while-revalidate=300
```

## Cache Invalidation

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         CACHE INVALIDATION STRATEGIES                                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. TIME-BASED (TTL)
   └── Content expires after max-age
   └── Simple but may serve stale content

2. VERSIONED URLS (Recommended for assets)
   └── /app.js → /app.v2.js or /app.a1b2c3.js
   └── New URL = new cache entry
   └── Old versions naturally expire

3. PURGE API
   └── Explicitly invalidate cached content
   └── curl -X POST "https://cdn.example.com/purge" \
        -d '{"urls": ["/images/logo.png"]}'
   └── Use sparingly (takes time to propagate)

4. CACHE TAGS
   └── Tag content: product:123, category:electronics
   └── Purge by tag: "invalidate all product:123"
   └── Supported by Cloudflare, Fastly, etc.

5. STALE-WHILE-REVALIDATE
   └── Serve stale content immediately
   └── Fetch fresh content in background
   └── Best user experience
```

## CDN for Dynamic Content

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         EDGE COMPUTING                                                   │
└─────────────────────────────────────────────────────────────────────────────────────────┘

Modern CDNs can run code at the edge:

  Cloudflare Workers    │  Run JavaScript at 200+ locations
  AWS Lambda@Edge       │  Run Lambda at CloudFront edges
  Azure Front Door      │  Rules engine + Functions
  Fastly Compute@Edge   │  Run Wasm/JS at edge

USE CASES:
  • A/B testing (decide variant at edge)
  • Authentication (validate JWT at edge)
  • Personalization (user-specific content)
  • API gateway (rate limiting, routing)
  • Image optimization (resize on-the-fly)

EXAMPLE (Cloudflare Worker):
  addEventListener('fetch', event => {
    const request = event.request;
    const country = request.cf.country;
    
    // Route to nearest origin based on country
    const origin = country === 'JP' ? 'api-tokyo.example.com' 
                                    : 'api-us.example.com';
    
    event.respondWith(fetch(`https://${origin}${new URL(request.url).pathname}`));
  });
```

## CDN Providers

| Provider | Strengths | Use Case |
|----------|-----------|----------|
| Cloudflare | DDoS protection, Workers | General purpose |
| AWS CloudFront | AWS integration | AWS-native apps |
| Azure CDN/Front Door | Azure integration | Azure apps |
| Akamai | Enterprise, global reach | Large enterprises |
| Fastly | Real-time purge, edge compute | Dynamic content |

## Configuration Example (Azure CDN)

```json
{
  "name": "my-cdn-profile",
  "sku": "Standard_Microsoft",
  "endpoints": [{
    "name": "static-assets",
    "origins": [{
      "name": "blob-origin",
      "hostName": "mystorageaccount.blob.core.windows.net"
    }],
    "deliveryPolicy": {
      "rules": [{
        "name": "CacheStaticAssets",
        "conditions": [{
          "name": "UrlFileExtension",
          "parameters": {
            "extensions": ["js", "css", "png", "jpg", "woff2"]
          }
        }],
        "actions": [{
          "name": "CacheExpiration",
          "parameters": {
            "cacheBehavior": "Override",
            "cacheType": "All",
            "cacheDuration": "365.00:00:00"
          }
        }]
      }]
    }
  }]
}
```

## When to Use

✅ **Good for:**
- Static assets (images, JS, CSS, fonts)
- Video streaming
- Software downloads
- Global user base
- High traffic websites
- DDoS protection

❌ **Not ideal for:**
- Real-time data (use WebSocket)
- Personalized content without edge compute
- Small, local user base
- Frequently changing content (high purge rate)

## Trade-offs

| Pros | Cons |
|------|------|
| Reduced latency | Cache invalidation complexity |
| Offload origin servers | Cost at scale |
| DDoS protection | Debugging cache issues |
| Global availability | Stale content risk |
| Bandwidth savings | Additional configuration |

## Key Metrics

```
Cache Hit Ratio = Cache Hits / Total Requests
  Target: > 90% for static assets

Time to First Byte (TTFB):
  Without CDN: 200-500ms (depends on distance)
  With CDN: 20-50ms (from edge)

Bandwidth Savings:
  Origin bandwidth = Total bandwidth × (1 - Cache Hit Ratio)
```

## Used In (This Guide)

- **Teams (Q1)**: Profile pictures, static assets
- **Video Streaming (Q18)**: Video segment delivery
- **File Storage (Q12)**: File downloads
- **CDN Design (Q7)**: Full architecture
- **E-commerce (Q46)**: Product images
