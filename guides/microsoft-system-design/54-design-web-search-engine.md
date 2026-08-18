# 54. Design a Web Search Engine (Google / Bing)

---

## Problem Statement
Design a web-scale search engine like Google or Bing that crawls billions of web pages, builds a searchable index, and returns highly relevant results in milliseconds. The system must handle tens of billions of indexed pages, 8.5 billion searches per day, support autocomplete, spell correction, knowledge panels, ads, and personalized ranking — all with sub-second latency worldwide.

## Clarifying Questions to Ask

1. **Scale**: How many pages to index? (100B+ pages in the known web)
2. **Query volume**: How many searches per day? (8.5B+ for Google)
3. **Freshness**: How quickly should new/changed pages appear in results? (minutes for news, days for evergreen)
4. **Features**: Just web results, or also images, videos, news, maps, knowledge graph, ads?
5. **Autocomplete**: Provide query suggestions as the user types?
6. **Spell correction**: "Did you mean...?" for typos?
7. **Personalization**: Personalized results based on search history, location, language?
8. **Ads**: Auction-based sponsored results alongside organic results?
9. **Latency**: Target for end-to-end search result page? (< 200ms)
10. **International**: Multi-language support? Different ranking for different regions?

## Design Reasoning

> **Core philosophy**: Decouple expensive offline computation (crawling, PageRank, ML training) from latency-critical online serving — achieve sub-200ms query latency across 100 billion pages through document-partitioned parallel fan-out, tiered indexing, and multi-stage ranking that dynamically trades freshness, relevance, and speed.

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Index sharding | Document-partitioned across 1,000+ shards | Multi-term queries resolved locally within each shard; embarrassingly parallel fan-out; each shard is a self-contained mini-search engine | Term-partitioned — multi-term queries need cross-shard joins, creating massive network latency at 100K+ QPS |
| Index implementation | Custom inverted index with delta-encoded posting lists (4-8× compression) | No off-the-shelf engine handles 100B docs at petabyte scale with sub-10ms latency; enables optimal SSD layout | Elasticsearch/Lucene — can't meet scale and latency requirements for 100B+ documents |
| Ranking pipeline | Three-stage: BM25 (recall) → LambdaMART (precision on top 1K) → Neural/BERT (semantics on top 50) | BM25 is microseconds/doc; LambdaMART is 100× faster than neural; neural provides deep semantic understanding on final candidates | BM25 alone — insufficient quality; all-neural — too slow for 100K QPS |
| Indexing architecture | Dual-path: batch (1-30 day recrawl) + real-time stream (RSS/social, 2-5 min freshness) | Batch is efficient for 10B pages/day; real-time ensures breaking news in results within minutes via Tier 0 segments | Batch-only — misses breaking news; all real-time — wastes resources crawling low-priority content |
| Index tiering | Tier 0 (in-memory, hours) → Tier 1 (SSD, top 10B pages) → Tier 2 (all 100B pages) | 90%+ queries satisfied by Tier 1 alone; reduces working set and improves cache hits; Tier 2 for long-tail coverage | Single unified index — much slower; all queries would traverse 100B docs instead of high-quality 10B subset |
| Authority signal | PageRank via graph-parallel (Pregel/GraphX) on 4 trillion edges, weekly full + daily incremental | Foundational ranking signal; graph-parallel distributes computation across thousands of machines | Centralized computation — impossible at 4T edges; MapReduce alone — billions of shuffle operations |
| Web crawling | 10K+ distributed crawlers with priority + per-host politeness queues (1 req/sec/domain) | Two-level queues: global priority by freshness tier + per-host FIFO respecting robots.txt; achieves 10B pages/day | Aggressive crawling without rate limits — gets blocked by target websites |
| Result caching | Redis for head queries (top 10K = ~30% traffic) + autocomplete trie + rate limiting | Sub-millisecond access for hot queries avoids entire index lookup; atomic ops for ad budget tracking | Memcached — lacks persistence for budget tracking; CDN caching — misses personalization |

---

## Requirements & Sizing

### Functional
- **Web Search**: User types a query, gets ranked list of web page results (title, snippet, URL)
- **Crawling**: Continuously discover and fetch web pages across the internet
- **Indexing**: Build and maintain an inverted index over billions of documents
- **Ranking**: Return the most relevant results using hundreds of ranking signals
- **Autocomplete**: Query suggestions as the user types (typeahead)
- **Spell Correction**: "Did you mean..." for misspelled queries
- **Knowledge Panel**: Structured answers for entity queries (people, places, facts)
- **Ads**: Sponsored results via real-time ad auction
- **Snippets**: Generate highlighted text excerpts matching the query
- **Freshness**: Near-real-time indexing for breaking news

### Non-functional
| Metric | Target |
|--------|--------|
| Search latency | **< 200ms** p99 end-to-end |
| Crawl coverage | **100B+ pages** indexed |
| Crawl freshness | **Minutes** for news, **days** for long-tail pages |
| Availability | **99.99%** (four 9s) |
| Query volume | **100K+ queries/second** (8.5B/day) |
| Index size | **100+ PB** (inverted index + document store) |
| Autocomplete latency | **< 50ms** |

### Capacity Estimates
```
Queries:
- 8.5B queries/day ≈ 100K queries/second (average)
- Peak: 300K+ queries/second (during major events)
- Average query: 3-4 words, ~25 bytes
- Query log storage: 8.5B × 100 bytes ≈ 850 GB/day

Index:
- 100B web pages indexed
- Average page: 50 KB raw HTML → 10 KB extracted text
- Raw content: 100B × 10 KB = 1 EB (exabyte)
- Inverted index: ~100-200 PB (compressed posting lists)
- Document metadata store: ~50 PB

Crawling:
- Recrawl cycle: Top 1B pages every day, long-tail every 30 days
- Crawl rate: ~10B pages/day ≈ 120K pages/second
- Bandwidth: 120K × 50 KB = 6 GB/second inbound crawl traffic
- robots.txt cache: ~500M domains × 2 KB ≈ 1 TB

Serving:
- Each query fans out to 1,000+ index shards in parallel
- Each shard returns top ~100 candidates in < 10ms
- Merger combines and re-ranks → final top 10 in < 50ms
- Total with network + rendering: < 200ms
```

---

## High-Level Design

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                         WEB SEARCH ENGINE ARCHITECTURE                                    │
└──────────────────────────────────────────────────────────────────────────────────────────┘

                                    CLIENTS
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐           │
│  │  Browser    │     │  Mobile App │     │  Voice       │     │  API        │           │
│  │  (Desktop / │     │  (iOS /     │     │  Assistant   │     │  Consumers  │           │
│  │   Mobile)   │     │   Android)  │     │  (Cortana)   │     │             │           │
│  └──────┬──────┘     └──────┬──────┘     └──────┬──────┘     └──────┬──────┘           │
│         └───────────────────┴───────────────────┴───────────────────┘                   │
│                                         │                                               │
└─────────────────────────────────────────┼───────────────────────────────────────────────┘
                                          │ HTTPS
                                          ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                              EDGE / GATEWAY LAYER                                         │
│                                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                │
│  │    CDN       │  │    Load      │  │    Rate      │  │    Geo       │                │
│  │  (Static     │  │   Balancer   │  │   Limiter    │  │   Router     │                │
│  │   assets)    │  │              │  │              │  │              │                │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘                │
└────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                         │
          ┌──────────────────────────────┼──────────────────────────────┐
          ▼                              ▼                              ▼
┌──────────────────┐  ┌───────────────────────────┐  ┌──────────────────────────┐
│  QUERY SERVING   │  │     OFFLINE PIPELINE       │  │    REAL-TIME PIPELINE     │
│  (Online Path)   │  │     (Batch Indexing)        │  │    (Fresh Content)        │
│                  │  │                             │  │                           │
│  ┌────────────┐  │  │  ┌──────────┐              │  │  ┌──────────┐             │
│  │ Query      │  │  │  │  Web     │              │  │  │ News /   │             │
│  │ Parser     │  │  │  │  Crawler │              │  │  │ RSS Feed │             │
│  ├────────────┤  │  │  ├──────────┤              │  │  │ Ingester │             │
│  │ Spell      │  │  │  │  HTML    │              │  │  ├──────────┤             │
│  │ Corrector  │  │  │  │  Parser  │              │  │  │ Stream   │             │
│  ├────────────┤  │  │  ├──────────┤              │  │  │ Indexer  │             │
│  │ Query      │  │  │  │  Content │              │  │  └──────────┘             │
│  │ Rewriter   │  │  │  │  Extract │              │  │                           │
│  ├────────────┤  │  │  ├──────────┤              │  └──────────────────────────┘
│  │ Index      │  │  │  │  Link    │              │
│  │ Searcher   │  │  │  │  Analyzer│              │
│  ├────────────┤  │  │  ├──────────┤              │
│  │ Ranker     │  │  │  │  Index   │              │
│  ├────────────┤  │  │  │  Builder │              │
│  │ Snippet    │  │  │  └──────────┘              │
│  │ Generator  │  │  │                             │
│  ├────────────┤  │  └───────────────────────────┘
│  │ Ad Auction │  │
│  ├────────────┤  │
│  │ Result     │  │
│  │ Blender    │  │
│  └────────────┘  │
└──────────────────┘
          │
          ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                              DATA / STORAGE LAYER                                         │
│                                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                │
│  │  Inverted    │  │  Document    │  │  Knowledge   │  │  PageRank    │                │
│  │  Index       │  │  Store       │  │  Graph       │  │  / Link      │                │
│  │  (Sharded)   │  │  (Metadata,  │  │  (Entities,  │  │  Graph       │                │
│  │              │  │   Snippets)  │  │   Facts)     │  │              │                │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘                │
│                                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                │
│  │  URL DB      │  │  Crawl       │  │  Query Log   │  │  Ad Index    │                │
│  │  (Frontier)  │  │  Store       │  │  (Analytics) │  │              │                │
│  │              │  │  (Raw HTML)  │  │              │  │              │                │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘                │
│                                                                                           │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Design

### Search APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/v1/search?q={query}&start={offset}&count={n}` | Web search (paginated) |
| `GET` | `/v1/suggest?q={prefix}&lang={lang}` | Autocomplete suggestions |
| `GET` | `/v1/images?q={query}` | Image search |
| `GET` | `/v1/news?q={query}` | News search |
| `GET` | `/v1/knowledge?entity={id}` | Knowledge panel for entity |
| `GET` | `/v1/spellcheck?q={query}` | Spell correction suggestion |

### Search Request
```json
GET /v1/search?q=distributed+systems+interview&start=0&count=10&lang=en&region=us

Headers:
  Authorization: Bearer <api_key>
  X-Request-ID: req_abc123
  Accept-Language: en-US
```

### Search Response
```json
{
  "query": "distributed systems interview",
  "corrected_query": null,
  "results": {
    "organic": [
      {
        "position": 1,
        "url": "https://example.com/distributed-systems-guide",
        "title": "Complete Guide to Distributed Systems Interviews",
        "snippet": "Master <b>distributed systems</b> concepts for your next <b>interview</b>...",
        "domain": "example.com",
        "favicon": "https://example.com/favicon.ico",
        "last_crawled": "2026-03-01T12:00:00Z",
        "sitelinks": [
          { "title": "CAP Theorem", "url": "https://example.com/cap" },
          { "title": "Consistent Hashing", "url": "https://example.com/hashing" }
        ]
      }
    ],
    "knowledge_panel": {
      "title": "Distributed Systems",
      "description": "A distributed system is a system whose components are located on different networked computers...",
      "source": "Wikipedia",
      "related_topics": ["CAP Theorem", "Consensus Algorithms", "MapReduce"]
    },
    "ads": [
      {
        "position": 0,
        "url": "https://sponsor.com/course",
        "title": "Learn Distributed Systems — Online Course",
        "snippet": "Ace your system design interview. 50,000+ students enrolled.",
        "label": "Sponsored"
      }
    ],
    "related_searches": [
      "system design interview questions",
      "distributed systems book recommendations",
      "CAP theorem explained"
    ]
  },
  "metadata": {
    "total_results": 142000000,
    "search_time_ms": 187,
    "next_cursor": "start=10"
  }
}
```

### Autocomplete Response
```json
GET /v1/suggest?q=distrib&lang=en

{
  "prefix": "distrib",
  "suggestions": [
    { "text": "distributed systems", "score": 0.95 },
    { "text": "distributed computing", "score": 0.82 },
    { "text": "distributed database", "score": 0.76 },
    { "text": "distributed systems interview questions", "score": 0.71 }
  ],
  "latency_ms": 12
}
```

---

## Web Crawling Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│                    WEB CRAWLING PIPELINE                          │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                       URL FRONTIER                                │
│                                                                   │
│  Two-level queue system:                                         │
│                                                                   │
│  LEVEL 1: PRIORITY QUEUES (what to crawl)                        │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐    │
│  │ P0: News   │ │ P1: High   │ │ P2: Medium │ │ P3: Long   │    │
│  │ (minutes)  │ │ PageRank   │ │ sites      │ │ tail       │    │
│  │            │ │ (daily)    │ │ (weekly)   │ │ (monthly)  │    │
│  └────────────┘ └────────────┘ └────────────┘ └────────────┘    │
│                                                                   │
│  LEVEL 2: PER-HOST QUEUES (politeness)                           │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  [cnn.com]     → url1 → url2 → url3  (1 req/s)         │    │
│  │  [github.com]  → url4 → url5         (per robots.txt)   │    │
│  │  [wikipedia.org]→ url6               (respect crawl-delay)│   │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  URL SEEN CHECK (Bloom filter + DB):                             │
│  ├── Bloom filter: 100B URLs × 10 bits ≈ 125 GB in memory       │
│  ├── False positive rate: ~1% → check DB only for positives     │
│  └── Prevents re-adding already-queued URLs                      │
│                                                                   │
└─────────────────────────┬────────────────────────────────────────┘
                          │ Dequeue URL (respecting priority + politeness)
                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                    CRAWLER WORKERS (10,000+)                      │
│                                                                   │
│  For each URL:                                                   │
│  1. Check robots.txt (cached per domain, refresh every 24h)      │
│  2. Enforce politeness delay (per-host rate limit)               │
│  3. DNS resolution (cached, ~500M entries)                       │
│  4. HTTP GET with timeout (10s), follow redirects (max 5)        │
│  5. Detect content type (skip binary, images unless image crawl) │
│  6. Compute content fingerprint (SimHash) for deduplication      │
│  7. Check against known fingerprints → skip near-duplicates      │
│  8. Send raw HTML + metadata to processing pipeline              │
│                                                                   │
│  CRAWLER TRAPS:                                                  │
│  ├── Infinite URL spaces (calendars, session IDs in URLs)        │
│  ├── Detection: URL length limit, per-domain page limit          │
│  ├── Spider traps: Pattern detection, max depth per domain       │
│  └── Soft 404s: Detect via content analysis (not just HTTP 200)  │
│                                                                   │
│  RECRAWL SCHEDULING:                                             │
│  ├── News sites: Every 5-15 minutes                              │
│  ├── High-PageRank pages: Every 1-3 days                        │
│  ├── Medium sites: Every 1-2 weeks                               │
│  ├── Long-tail: Every 30 days                                    │
│  └── Changed pages (via ETag/Last-Modified): Prioritize recrawl  │
│                                                                   │
└─────────────────────────┬────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────────────┐
│                    DOCUMENT PROCESSING PIPELINE                   │
│                                                                   │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐     │
│  │  HTML    │──▶│  Content │──▶│   Link   │──▶│  Language │     │
│  │  Parser  │   │ Extractor│   │ Extractor│   │ Detector  │     │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘     │
│       │              │              │               │            │
│  Parse DOM,     Strip nav,     Extract all     Detect language   │
│  handle broken  ads, boiler-   outgoing links  (CLD2 / fastText)│
│  HTML           plate → clean  with anchor     for multi-lang   │
│                 body text      text             indexing         │
│                                                                   │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐                     │
│  │  Spam /  │──▶│  Entity  │──▶│  Index   │                     │
│  │  Quality │   │ Extractor│   │  Writer  │                     │
│  │  Filter  │   │          │   │          │                     │
│  └──────────┘   └──────────┘   └──────────┘                     │
│       │              │              │                             │
│  Detect spam,   Extract named  Write to inverted                │
│  thin content,  entities,      index + document                 │
│  link farms     topics for     store                            │
│                 knowledge                                        │
│                 graph                                             │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Inverted Index (Core Data Structure)

```
┌──────────────────────────────────────────────────────────────────┐
│                    INVERTED INDEX                                  │
│                                                                   │
│  The inverted index is THE critical data structure for search.   │
│  It maps every word → list of documents containing that word.    │
│                                                                   │
│  STRUCTURE:                                                      │
│                                                                   │
│  Term Dictionary (in-memory / SST):                              │
│  ┌────────────────┬────────────────────────────────────┐         │
│  │  Term          │  Posting List Pointer + Stats       │         │
│  ├────────────────┼────────────────────────────────────┤         │
│  │  "distributed" │  → offset=0x4A2, doc_freq=8.2M     │         │
│  │  "systems"     │  → offset=0x9F1, doc_freq=12.4M    │         │
│  │  "interview"   │  → offset=0xBB3, doc_freq=3.1M     │         │
│  │  "design"      │  → offset=0xC70, doc_freq=15.7M    │         │
│  └────────────────┴────────────────────────────────────┘         │
│                                                                   │
│  Posting Lists (on-disk, compressed):                            │
│                                                                   │
│  "distributed" → [doc_23, doc_891, doc_1042, doc_5539, ...]     │
│                                                                   │
│  Each posting entry:                                             │
│  ┌──────────┬───────────┬───────────────┬──────────────────┐     │
│  │  doc_id  │  term_freq│  positions[]  │  field (title/   │     │
│  │  (delta  │  (in this │  [14, 87,     │   body/anchor/   │     │
│  │   encoded)│  doc)    │   203]        │   URL)           │     │
│  └──────────┴───────────┴───────────────┴──────────────────┘     │
│                                                                   │
│  COMPRESSION:                                                    │
│  ├── Doc IDs: Delta encoding + variable-byte / PForDelta         │
│  │   [23, 891, 1042] → deltas [23, 868, 151] → varint encode   │
│  ├── Positions: Delta encoded within each document               │
│  ├── Compression ratio: ~4-8x over raw posting lists             │
│  └── Skip pointers: Every 128 docs for fast intersection         │
│                                                                   │
│  QUERY "distributed systems":                                    │
│  1. Look up posting list for "distributed" → [23, 891, 1042...]  │
│  2. Look up posting list for "systems"     → [23, 445, 891...]   │
│  3. INTERSECT both lists using merge-join:                       │
│     Two pointers scan sorted lists → docs in both = [23, 891...]│
│  4. Score each matching document using BM25 + PageRank + ...     │
│  5. Return top K by score                                        │
│                                                                   │
│  SHARDING:                                                       │
│  ├── Document-partitioned: Each shard holds a subset of docs     │
│  │   Shard 0: docs 0-99M,  Shard 1: docs 100M-199M, ...        │
│  ├── 1,000+ shards for 100B documents                            │
│  ├── Each query fans out to ALL shards in parallel               │
│  ├── Each shard returns local top-K                              │
│  └── Merger combines all shards' results → global top-K          │
│                                                                   │
│  WHY DOCUMENT-PARTITIONED (not term-partitioned)?                │
│  ├── Term-partitioned: Each shard owns certain terms             │
│  │   → Multi-term queries require cross-shard joins = slow      │
│  ├── Document-partitioned: Each shard is a mini search engine    │
│  │   → Each shard can answer any query independently            │
│  │   → Fan-out is embarrassingly parallel                       │
│  └── Trade-off: Higher fan-out, but simpler and faster           │
│                                                                   │
│  INDEX TIERS:                                                    │
│  ├── Tier 0 (Real-time): Last ~1 hour of content (in-memory)    │
│  ├── Tier 1 (Fast): High-quality pages (top 10B), SSD storage   │
│  ├── Tier 2 (Full): All 100B pages, mix of SSD + HDD            │
│  └── Query first hits Tier 1; if < 10 results, falls to Tier 2  │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Ranking System (Core Challenge)

```
┌──────────────────────────────────────────────────────────────────┐
│                    RANKING PIPELINE                               │
│                                                                   │
│  Query: "distributed systems interview"                          │
│                                                                   │
│  STAGE 0: QUERY UNDERSTANDING                                    │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  1. Tokenize: ["distributed", "systems", "interview"]│        │
│  │  2. Spell correction: None needed                     │        │
│  │  3. Query expansion / synonyms:                       │        │
│  │     "distributed systems" → also match "distributed   │        │
│  │      computing", "microservices"                      │        │
│  │  4. Intent classification:                            │        │
│  │     Informational (not navigational or transactional) │        │
│  │  5. Named entity recognition:                         │        │
│  │     "distributed systems" = CS topic                  │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  STAGE 1: RETRIEVAL (Recall phase — find candidates)             │
│  ├── Fan-out query to 1,000+ index shards in parallel            │
│  ├── Each shard: Intersect posting lists (AND / OR logic)        │
│  ├── Each shard scores with BM25 (lightweight):                  │
│  │                                                                │
│  │   BM25(doc, query) = Σ IDF(term) ×                            │
│  │     [ TF(term,doc) × (k1+1) ]                                │
│  │     ─────────────────────────────────────                     │
│  │     [ TF(term,doc) + k1 × (1 - b + b × |doc|/avgDL) ]       │
│  │                                                                │
│  │   Where: k1 = 1.2, b = 0.75                                  │
│  │   IDF = log((N - df + 0.5) / (df + 0.5))                     │
│  │                                                                │
│  ├── Each shard returns top 100 candidates                       │
│  └── Merger: Combine 1,000 × 100 = 100K candidates → top 1,000  │
│                                                                   │
│  STAGE 2: FEATURE EXTRACTION                                     │
│  For each of the ~1,000 candidate documents:                     │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  TEXT FEATURES:                                       │        │
│  │  ├── BM25 score (title, body, anchor text, URL)       │        │
│  │  ├── Term proximity (how close query terms appear)    │        │
│  │  ├── Exact phrase match in title?                     │        │
│  │  ├── Query coverage (% of query terms matched)        │        │
│  │  └── TF-IDF variants across fields                    │        │
│  │                                                        │        │
│  │  DOCUMENT FEATURES:                                   │        │
│  │  ├── PageRank (authority score from link graph)        │        │
│  │  ├── Domain authority (aggregate PageRank of domain)   │        │
│  │  ├── Page freshness (last modified / crawl date)       │        │
│  │  ├── Content length and quality score                  │        │
│  │  ├── HTTPS vs HTTP                                    │        │
│  │  ├── Mobile-friendliness                              │        │
│  │  └── Core Web Vitals (LCP, FID, CLS)                  │        │
│  │                                                        │        │
│  │  LINK FEATURES:                                       │        │
│  │  ├── Inbound link count (from unique domains)          │        │
│  │  ├── Anchor text relevance to query                    │        │
│  │  ├── Link diversity (links from many topics = good)    │        │
│  │  └── Spam link ratio                                  │        │
│  │                                                        │        │
│  │  USER SIGNALS:                                        │        │
│  │  ├── Click-through rate for this URL on similar queries│        │
│  │  ├── Dwell time (how long users stayed on page)        │        │
│  │  ├── Bounce-back rate (immediately returned to search) │        │
│  │  └── Pogo-sticking (clicked result, came back fast)    │        │
│  │                                                        │        │
│  │  CONTEXTUAL:                                          │        │
│  │  ├── User's language and region                        │        │
│  │  ├── Device type (mobile / desktop)                    │        │
│  │  └── Time of day (news queries rank fresher at morning)│        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  STAGE 3: ML RANKING (Learning to Rank)                          │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  Model: LambdaMART (gradient-boosted decision trees)  │        │
│  │         or Transformer-based neural ranker             │        │
│  │                                                        │        │
│  │  Input: Feature vector (200-1000 features per doc)     │        │
│  │  Output: Relevance score [0, 1]                        │        │
│  │                                                        │        │
│  │  Training data:                                        │        │
│  │  ├── Human raters (search quality raters)              │        │
│  │  ├── Click logs (implicit relevance feedback)          │        │
│  │  └── A/B tests (compare ranking changes)              │        │
│  │                                                        │        │
│  │  Inference: < 10ms for 1,000 candidates                │        │
│  │  (LambdaMART is ~100x faster than deep neural models;  │        │
│  │   neural re-ranker may only score top 50 candidates)   │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  STAGE 4: BUSINESS RULES & BLENDING                              │
│  ├── Inject knowledge panel (if entity query detected)           │
│  ├── Inject ads at positions 1-3 (above organic) + sidebar       │
│  ├── Inject "People also ask" related questions                  │
│  ├── Deduplicate (don't show 3 results from same domain)         │
│  ├── Apply safe search filter (if enabled)                       │
│  ├── Freshness boost for news/event queries                      │
│  └── Diversity: Mix result types (article, video, image, news)   │
│                                                                   │
│  OUTPUT: Ranked list of 10 results per page                      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## PageRank & Link Graph

```
┌──────────────────────────────────────────────────────────────────┐
│                    PAGERANK & LINK GRAPH                          │
│                                                                   │
│  PageRank is the foundational authority signal for web search.   │
│  "A page is important if important pages link to it."            │
│                                                                   │
│  FORMULA:                                                        │
│  PR(A) = (1-d)/N + d × Σ [ PR(Ti) / C(Ti) ]                    │
│                                                                   │
│  Where:                                                          │
│  ├── d = damping factor (0.85) — probability of following link   │
│  ├── N = total number of pages                                   │
│  ├── Ti = pages that link to A                                   │
│  ├── C(Ti) = number of outgoing links from page Ti               │
│  └── Iterate until convergence (~40-50 iterations)               │
│                                                                   │
│  EXAMPLE:                                                        │
│       ┌─────┐  link   ┌─────┐  link   ┌─────┐                   │
│       │  A  │────────▶│  B  │────────▶│  C  │                   │
│       │PR=.5│         │PR=.8│         │PR=.3│                   │
│       └─────┘         └──┬──┘         └─────┘                   │
│                          │ link                                   │
│                          ▼                                        │
│                       ┌─────┐                                    │
│                       │  D  │                                    │
│                       │PR=.9│  ← High PR because B (high PR)    │
│                       └─────┘    links to it                     │
│                                                                   │
│  LINK GRAPH STORAGE:                                             │
│  ├── 100B pages, average 40 outgoing links = 4 trillion edges   │
│  ├── Compressed adjacency list: ~20 PB                           │
│  ├── PageRank computation: MapReduce / Pregel (graph-parallel)   │
│  ├── Recomputed: Daily (incremental) or weekly (full)            │
│  └── Only the final PR score per doc is needed at serving time   │
│                                                                   │
│  SPAM DETECTION (link graph analysis):                           │
│  ├── Link farms: Clusters of pages that only link to each other  │
│  ├── Bought links: Sudden spike in inbound links from unrelated  │
│  │   domains                                                     │
│  ├── TrustRank: Start from known-good pages, propagate trust     │
│  │   (inverse of PageRank idea — trust decays with distance)    │
│  └── Penalize pages with unnatural link patterns                 │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Query Understanding & Spell Correction

```
┌──────────────────────────────────────────────────────────────────┐
│                    QUERY UNDERSTANDING                            │
│                                                                   │
│  SPELL CORRECTION:                                               │
│  ├── Input: "distribted systms intrvew"                          │
│  ├── Output: "distributed systems interview"                     │
│  │                                                                │
│  ├── APPROACH 1: Edit Distance (Levenshtein)                     │
│  │   For each word, find dictionary words within edit distance 2 │
│  │   "distribted" → "distributed" (edit distance 1: insert 'u') │
│  │                                                                │
│  ├── APPROACH 2: Noisy Channel Model (used at scale)             │
│  │   P(correction | misspelling) ∝ P(misspelling | correction)   │
│  │                                    × P(correction)            │
│  │   P(correction) = language model (n-gram from query logs)     │
│  │   P(misspelling | correction) = error model (common typos)    │
│  │                                                                │
│  ├── APPROACH 3: Sequence-to-Sequence Neural Model               │
│  │   Encoder: misspelled query → hidden state                    │
│  │   Decoder: hidden state → corrected query                     │
│  │   Trained on (misspelled, corrected) pairs from query logs    │
│  │                                                                │
│  └── FAST LOOKUP: SymSpell / BK-tree for candidate generation   │
│      Pre-compute all edits within distance 2 of dictionary words │
│      Lookup: O(1) average case                                   │
│                                                                   │
│  QUERY EXPANSION:                                                │
│  ├── Synonyms: "car" → also match "automobile", "vehicle"        │
│  ├── Stemming: "running" → "run" (match "runs", "runner")        │
│  ├── Acronyms: "ML" → "machine learning"                        │
│  └── Related terms from query co-occurrence logs                 │
│                                                                   │
│  INTENT CLASSIFICATION:                                          │
│  ├── Navigational: "facebook login" → user wants facebook.com    │
│  │   → Boost the exact domain, show site links                  │
│  ├── Informational: "how does DNS work" → user wants articles    │
│  │   → Boost comprehensive articles, show "People also ask"     │
│  ├── Transactional: "buy laptop online" → user wants to purchase │
│  │   → Boost shopping results, show product listings             │
│  └── Local: "pizza near me" → user wants local results           │
│      → Trigger maps/local pack, use geo location                 │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Snippet Generation

```
┌──────────────────────────────────────────────────────────────────┐
│                    SNIPPET GENERATION                             │
│                                                                   │
│  Snippets are the 2-3 line text excerpts shown under each        │
│  search result. They must be relevant, readable, and fast.       │
│                                                                   │
│  APPROACH:                                                       │
│  1. At index time: Store compressed page text in document store  │
│  2. At query time: For each result (top 10 only):                │
│     a. Load compressed text from document store                  │
│     b. Find passages containing query terms                      │
│     c. Score passages by:                                        │
│        ├── Number of query terms present                         │
│        ├── Proximity of query terms to each other                │
│        ├── Position in document (early paragraphs preferred)     │
│        └── Presence of complete sentences                        │
│     d. Select best passage, truncate to ~160 characters          │
│     e. Bold the matching query terms with <b> tags               │
│                                                                   │
│  EXAMPLE:                                                        │
│  Query: "distributed systems interview"                          │
│  Page text: "...this guide covers everything you need to         │
│  know about distributed systems for your next technical          │
│  interview. We cover consensus, replication, and..."             │
│                                                                   │
│  Snippet: "This guide covers everything you need to know         │
│  about <b>distributed systems</b> for your next technical        │
│  <b>interview</b>. We cover consensus, replication..."           │
│                                                                   │
│  FEATURED SNIPPETS (Position 0):                                 │
│  ├── Detect "what is X", "how to X" queries                     │
│  ├── Extract best paragraph / list / table from top result       │
│  └── Display above organic results in highlighted box            │
│                                                                   │
│  LATENCY BUDGET: < 20ms for 10 snippets                         │
│  ├── Document store must support fast random reads               │
│  ├── Compressed text cached on SSD, hot docs in memory           │
│  └── Snippet extraction runs in parallel for all 10 results      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Autocomplete (Typeahead)

```
┌──────────────────────────────────────────────────────────────────┐
│                    AUTOCOMPLETE SERVICE                           │
│                                                                   │
│  As user types "distri", suggest "distributed systems" etc.      │
│                                                                   │
│  DATA SOURCE:                                                    │
│  ├── Query logs: Aggregate popular queries from search logs      │
│  ├── Frequency: Count queries per day, weighted by recency       │
│  ├── Freshness: Boost trending queries (spike detection)         │
│  └── Filter: Remove offensive / low-quality suggestions          │
│                                                                   │
│  DATA STRUCTURE: Trie + Top-K Cache                              │
│                                                                   │
│  ┌──────────────────────────────────────────────────────┐        │
│  │                    TRIE                                │        │
│  │              (root)                                    │        │
│  │             /      \                                   │        │
│  │           d          h                                 │        │
│  │          /            \                                │        │
│  │        i               o                               │        │
│  │       /                 \                              │        │
│  │      s                   w                             │        │
│  │     / \                                                │        │
│  │    t   c                                               │        │
│  │   /     \                                              │        │
│  │ "distributed   "discord"                               │        │
│  │  systems"       [freq=8M]                              │        │
│  │  [freq=2M]                                             │        │
│  │                                                        │        │
│  │  At each node, cache top-10 completions:               │        │
│  │  Node "dis" → ["discord", "disney", "distributed       │        │
│  │                 systems", "discount code", ...]         │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  SERVING:                                                        │
│  ├── Trie is replicated across edge data centers                 │
│  ├── Rebuilt every ~15 minutes from updated query logs            │
│  ├── Size: ~10-50 GB per replica (top 100M queries)              │
│  ├── Lookup: O(prefix_length) ≈ sub-millisecond                 │
│  └── Client-side debounce: Only send request after 100ms pause   │
│                                                                   │
│  PERSONALIZATION:                                                │
│  ├── Blend global suggestions with user's recent searches        │
│  ├── Weight by user's language and region                        │
│  └── E.g., "py" → "python" for a developer vs "pyramid" for     │
│      a history student                                           │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Knowledge Graph

```
┌──────────────────────────────────────────────────────────────────┐
│                    KNOWLEDGE GRAPH                                │
│                                                                   │
│  Structured database of entities and their relationships.        │
│  Powers knowledge panels, instant answers, and entity search.    │
│                                                                   │
│  ENTITY EXAMPLE:                                                 │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  Entity: "Alan Turing"                                │        │
│  │  Type: Person                                         │        │
│  │  ├── born: 1912-06-23                                 │        │
│  │  ├── died: 1954-06-07                                 │        │
│  │  ├── occupation: Mathematician, Computer Scientist     │        │
│  │  ├── known_for: ["Turing Machine", "Enigma",          │        │
│  │  │               "Turing Test"]                        │        │
│  │  ├── education: Princeton University                   │        │
│  │  └── awards: ["OBE"]                                  │        │
│  │                                                        │        │
│  │  Relations:                                            │        │
│  │  ├── influenced_by: Alonzo Church                      │        │
│  │  ├── influenced: John von Neumann                      │        │
│  │  └── worked_at: Bletchley Park, University of          │        │
│  │                  Manchester                            │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  SOURCES:                                                        │
│  ├── Wikipedia / Wikidata (structured infoboxes)                 │
│  ├── Freebase (Google Knowledge Graph origin)                    │
│  ├── Web extraction (NER on crawled pages)                       │
│  └── Licensed data (medical, geographic, etc.)                   │
│                                                                   │
│  STORAGE:                                                        │
│  ├── Graph database or triple store (subject, predicate, object) │
│  ├── ~5B entities, ~500B facts/triples                           │
│  ├── Entity linking: Map query terms → entity IDs                │
│  └── Cached in memory for hot entities (top 100M)               │
│                                                                   │
│  USE IN SEARCH:                                                  │
│  ├── Query "Alan Turing" → show knowledge panel on right side    │
│  ├── Query "who invented the Turing machine" → instant answer    │
│  ├── Query "Turing machine" → entity disambiguation              │
│  └── Related entities: "People also search for..."               │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Ad Auction System

```
┌──────────────────────────────────────────────────────────────────┐
│                    AD SERVING (REAL-TIME AUCTION)                 │
│                                                                   │
│  Ads are the primary revenue source for search engines.          │
│  Every search triggers a real-time ad auction in < 20ms.         │
│                                                                   │
│  FLOW:                                                           │
│  User searches "best laptop 2026"                                │
│       │                                                           │
│       ▼                                                           │
│  1. AD RETRIEVAL                                                 │
│     ├── Match query to advertiser keyword bids                   │
│     ├── Filter by targeting (region, device, time, demographics) │
│     └── Candidate ads: ~100-1,000 eligible ads                   │
│       │                                                           │
│       ▼                                                           │
│  2. AD SCORING (Generalized Second-Price Auction)                │
│     ┌──────────────────────────────────────────────────┐         │
│     │  Ad Rank = Bid × Quality Score                    │         │
│     │                                                    │         │
│     │  Quality Score:                                    │         │
│     │  ├── Expected CTR (predicted click-through rate)   │         │
│     │  ├── Ad relevance to query                         │         │
│     │  ├── Landing page quality                          │         │
│     │  └── Ad format (extensions, sitelinks boost)       │         │
│     │                                                    │         │
│     │  Winner pays: (Next Ad Rank / Winner's QS) + $0.01│         │
│     │  (Generalized second-price → incentivizes bidding  │         │
│     │   true value)                                      │         │
│     └──────────────────────────────────────────────────┘         │
│       │                                                           │
│       ▼                                                           │
│  3. AD RENDERING                                                 │
│     ├── Top 1-3 ads shown above organic results                  │
│     ├── Bottom 1-3 ads shown below organic results               │
│     └── Each labeled "Sponsored" / "Ad"                          │
│                                                                   │
│  BUDGET MANAGEMENT:                                              │
│  ├── Each advertiser sets daily budget                           │
│  ├── Pacing: Spread budget evenly across the day                 │
│  ├── Real-time budget tracking (Redis counters)                  │
│  └── Stop showing ads when budget depleted                       │
│                                                                   │
│  CLICK FRAUD DETECTION:                                          │
│  ├── Detect repeated clicks from same IP / device                │
│  ├── Bot detection via behavioral analysis                       │
│  ├── Invalid click filtering: ~15-20% of clicks are invalid      │
│  └── Refund advertisers for detected fraud                       │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Real-Time Indexing Pipeline

```
┌──────────────────────────────────────────────────────────────────┐
│                    REAL-TIME INDEXING                             │
│                                                                   │
│  Breaking news must appear in search results within minutes,     │
│  not hours. This requires a separate real-time indexing path.    │
│                                                                   │
│  ┌──────────┐     ┌──────────┐     ┌──────────┐                 │
│  │  News    │     │  Social  │     │  High-   │                 │
│  │  RSS /   │────▶│  Media   │────▶│  Priority │                │
│  │  Atom    │     │  Firehose│     │  Recrawl  │                │
│  │  Feeds   │     │  (X API) │     │  Queue    │                │
│  └──────────┘     └──────────┘     └──────────┘                 │
│         │                │                │                       │
│         └────────────────┴────────────────┘                      │
│                          │                                        │
│                          ▼                                        │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  STREAM PROCESSOR (Kafka Streams / Flink)             │        │
│  │                                                        │        │
│  │  1. Deduplicate (content hash against seen-set)        │        │
│  │  2. Extract text, entities, links                      │        │
│  │  3. Compute lightweight ranking signals                │        │
│  │  4. Write to real-time index segment (in-memory)       │        │
│  └──────────────────────────────────────────────────────┘        │
│                          │                                        │
│                          ▼                                        │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  REAL-TIME INDEX (Tier 0)                              │        │
│  │                                                        │        │
│  │  ├── In-memory inverted index segment                  │        │
│  │  ├── Holds last 1-6 hours of freshly crawled content   │        │
│  │  ├── Merged into Tier 1 (SSD) every few hours         │        │
│  │  ├── Queried alongside Tier 1/2 at search time        │        │
│  │  └── Size: ~100K-1M documents per serving node        │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  FRESHNESS SIGNAL:                                               │
│  ├── Query "world cup score" → boost Tier 0 results heavily     │
│  ├── Query "history of Roman Empire" → freshness not important   │
│  └── Query classifier detects if freshness matters               │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Key Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| URL Frontier | Priority queue + per-host queues (Kafka / custom) | Manage crawl order with priority and politeness |
| Crawler Workers | Distributed HTTP fetchers (10K+ machines) | Download web pages in parallel |
| Document Processor | MapReduce / Spark pipeline | Parse HTML, extract text, entities, links |
| Inverted Index | Custom (Google: Caffeine; Bing: custom) on SSDs | Core search data structure — term → doc mapping |
| Document Store | Columnar storage (Bigtable / HBase) | Store page metadata, compressed text for snippets |
| Link Graph | Graph-parallel system (Pregel / GraphX) | Store link structure, compute PageRank |
| Knowledge Graph | Graph DB / Triple store | Entity database for knowledge panels |
| Ranking Service | LambdaMART + neural re-ranker (BERT-based) | Multi-stage relevance scoring |
| Spell Corrector | SymSpell + neural seq2seq model | "Did you mean..." suggestions |
| Autocomplete | Distributed trie + top-K cache | Query suggestions as user types |
| Ad Auction | Real-time bidding engine (< 20ms) | Match and rank ads per query |
| Real-Time Indexer | Kafka Streams / Flink + in-memory index | Index breaking content in minutes |
| Snippet Generator | Document store + passage scorer | Generate text excerpts for results |
| Query Logger | Kafka → HDFS / data warehouse | Store query logs for analytics and ML training |
| CDN | CloudFront / Akamai / Google's edge network | Serve static assets, cache homepage globally |

---

## Scalability Considerations

### 1. Index Serving Scalability
```
Challenge: 100B pages × 1,000+ shards × 100K queries/second

Solutions:
├── Document-partitioned sharding: Each shard is independent
│   → Each query fans out to ALL shards in parallel
│   → Merger aggregates top-K from each shard
├── Replicas: Each shard has 3-5 replicas for throughput
│   → 1,000 shards × 5 replicas = 5,000 index serving nodes
├── Tiered index: Query Tier 1 first (top 10B pages)
│   → Only fall through to Tier 2 if insufficient results
│   → 90%+ of queries satisfied by Tier 1 alone
├── Caching: Cache results for popular queries
│   → Top 10K queries (head queries) = ~30% of all traffic
│   → Cache hit avoids index lookup entirely
└── Geographic distribution: Index replicas in each region
    → US, Europe, Asia each have full index copies
    → Route queries to nearest data center
```

### 2. Crawl Scalability
```
Challenge: Crawl 10B pages/day while being polite to websites

Solutions:
├── Distributed crawlers: 10,000+ machines across data centers
├── Per-host rate limiting: 1 request per second per domain
├── DNS caching: Pre-resolve and cache 500M+ domain→IP mappings
├── Prioritized crawling: High-PageRank pages crawled more often
├── Incremental recrawl: Use HTTP ETag / If-Modified-Since
│   → Only re-download changed pages (saves ~60% bandwidth)
├── Bloom filter for URL dedup: 125 GB in-memory filter
│   → O(1) check for "have I seen this URL?"
└── Sitemaps: Parse sitemap.xml for efficient URL discovery
```

### 3. Ranking Scalability
```
Challenge: Score 1,000+ candidates per query in < 50ms

Solutions:
├── Multi-stage ranking: Cheap filter → expensive re-rank
│   Stage 1 (BM25): Score 100K candidates in 5ms (per shard)
│   Stage 2 (LambdaMART): Re-rank top 1,000 in 10ms
│   Stage 3 (Neural): Re-rank top 50 in 20ms
├── Feature pre-computation: PageRank, domain authority stored
│   at index time (not computed per query)
├── Model distillation: Compress large neural ranker into
│   smaller model for faster inference
└── GPU inference: For neural re-ranker stage, batch scoring
    on GPUs for throughput
```

### 4. Freshness vs Throughput
```
Challenge: New content must be searchable in minutes, but batch
           indexing is more efficient

Solutions:
├── Dual-path architecture:
│   ├── Batch path: Recrawl + reindex billions of pages (hours)
│   └── Real-time path: Stream new content into in-memory index
├── Tier 0 merge: Real-time segments merged into Tier 1 hourly
├── Priority signals: RSS feeds, social media spikes trigger
│   immediate recrawl of breaking content
└── QPS allocation: Reserve 10% of crawl capacity for
    real-time/priority crawls
```

---

## Trade-offs

| Decision | Option A | Option B | Search Engine Choice |
|----------|----------|----------|----------------------|
| Index partitioning | Document-partitioned | Term-partitioned | **Document-partitioned** — each shard handles any query; simpler, faster |
| Ranking model | BM25 only (simple) | ML-based (LambdaMART + neural) | **Multi-stage ML** — BM25 for recall, ML for precision |
| Crawl strategy | Crawl everything equally | Prioritized by importance | **Prioritized** — high-PageRank pages crawled more often |
| Freshness | Batch-only index rebuild | Real-time + batch hybrid | **Hybrid** — batch for base index, streaming for breaking content |
| Snippet generation | Pre-compute at index time | Generate at query time | **Query-time** — snippets must match the specific query terms |
| Autocomplete data | Full query logs | Aggregated popular queries | **Aggregated** — privacy + efficiency; filter offensive content |
| PageRank freshness | Recompute daily | Recompute weekly | **Weekly full + daily incremental** — balance freshness vs compute cost |
| Spell correction | Dictionary-based | Neural (seq2seq) | **Hybrid** — dictionary for speed, neural for hard cases |
| Ad placement | Mixed with organic | Clearly separated | **Clearly separated** — labeled "Sponsored" above and below organic |
| Cache strategy | Cache full result pages | Cache index postings | **Both** — full pages for head queries, postings for warm queries |

---

## Technologies Deep Dive

### Data Layer
| Technology | Purpose | Why |
|-----------|---------|-----|
| **Custom Inverted Index** (Caffeine / custom) | Core search index | No off-the-shelf solution handles 100B+ docs at this scale |
| **Bigtable / HBase** | Document store (metadata, snippets) | Petabyte-scale, fast random reads by doc_id |
| **HDFS / GFS** | Raw crawl data, index build intermediates | Distributed file system for batch processing |
| **Kafka** | Event streaming (crawl events, query logs) | High throughput, partitioned, replay for reprocessing |
| **Redis** | Query result cache, rate limiting, ad budgets | Sub-millisecond reads for hot query cache |
| **Graph DB / Pregel** | Link graph, PageRank computation | Graph-parallel processing at web scale |

### Compute
| Technology | Purpose | Why |
|-----------|---------|-----|
| **MapReduce / Spark** | Index building, PageRank, batch analytics | Batch processing of petabyte-scale data |
| **Kafka Streams / Flink** | Real-time indexing, trending queries | Stream processing for fresh content |
| **LambdaMART** | Learning-to-rank model | Fast inference (~μs per doc), interpretable features |
| **BERT / Transformer** | Neural re-ranker (top-50 candidates) | Deep semantic understanding of query-document relevance |
| **gRPC** | Inter-service communication | Low-latency, protobuf, streaming for index fan-out |

### Infrastructure
| Technology | Purpose | Why |
|-----------|---------|-----|
| **Custom hardware** | Index serving (SSD-heavy), crawling | Optimized cost per query at massive scale |
| **CDN (global edge)** | Static assets, cached result pages | Sub-50ms page load worldwide |
| **DNS-based load balancing** | Route users to nearest data center | Geographic latency optimization |
| **Kubernetes / Borg** | Service orchestration | Auto-scaling, rolling deploys |

---

## How to Explain It Well

### 30-Second Pitch
> "A web search engine has two main paths: the offline pipeline crawls billions of pages, extracts text, builds an inverted index (term → document mapping), and computes PageRank for authority. The online serving path takes a user query, fans it out to 1,000+ index shards in parallel, each shard returns top candidates scored by BM25, then a multi-stage ML ranker re-ranks the top 1,000 using hundreds of features — text relevance, PageRank, freshness, click signals. The final top 10 results get snippets generated and ads injected, all in under 200ms. The key insight is document-partitioned sharding: each shard is a mini search engine that can answer any query independently."

### Key Talking Points
1. **Inverted index is THE data structure**: Understand posting lists, intersection, compression, and sharding
2. **Document-partitioned sharding**: Each shard holds a subset of all documents and can answer any query
3. **Multi-stage ranking**: BM25 (fast, recall) → LambdaMART (features, precision) → Neural (top-50, deep relevance)
4. **PageRank**: "Important pages are linked to by important pages" — the foundational authority signal
5. **Freshness architecture**: Dual-path — batch indexing for base coverage, streaming for breaking news

### Whiteboard Sequence
1. Start with the query serving flow: Query → fan-out to shards → merge → rank → results
2. Draw the inverted index structure: term dictionary → posting lists → intersection
3. Show the offline crawl pipeline: Frontier → Crawler → Parser → Index Builder
4. Add PageRank: Link graph → iterative computation → score per document
5. Discuss multi-stage ranking: BM25 → ML re-ranker → business rules → final top 10

---

## Common Follow-up Questions

### "Why document-partitioned instead of term-partitioned?"
- **Term-partitioned**: Each shard owns certain terms (e.g., shard A has "distributed", shard B has "systems")
- Multi-term queries like "distributed systems" require fetching posting lists from **different shards** and joining them across the network — very slow
- **Document-partitioned**: Each shard is a complete mini search engine with all terms for its subset of documents
- Multi-term queries are answered **locally** on each shard — just intersect posting lists in memory
- Trade-off: Higher fan-out (query all shards), but each shard's work is independent and fast
- At Google/Bing scale, the network overhead of term-partitioned cross-shard joins is prohibitive

### "How does PageRank actually work at 100B page scale?"
- Represented as a **sparse matrix multiplication** problem: PR = d × M × PR + (1-d)/N
- Computed using **graph-parallel frameworks** (Google's Pregel, Apache Giraph, Spark GraphX)
- Each iteration: every page sends its current rank ÷ outgoing link count to all pages it links to
- Typically converges in **40-50 iterations**
- At 100B pages with 4T edges, this is a massive computation — runs on thousands of machines
- Full recomputation: weekly. Incremental updates for new/changed pages: daily
- The final output is just one float per document (the PageRank score), stored in the index

### "How do you handle 100K queries per second?"
- **Fan-out + parallelism**: Each query hits 1,000+ shards in parallel, each shard has 3-5 replicas
- **Caching**: Top 10K queries (~30% of traffic) served from result cache — no index lookup needed
- **Tiered serving**: 90% of queries answered by Tier 1 (top 10B pages) — smaller, faster index
- **Geographic replication**: Full index copies in US, Europe, Asia — route to nearest
- **Lightweight first pass**: BM25 scoring costs microseconds per doc; heavy ML only on top candidates
- Total serving fleet: ~50,000+ machines for index serving alone

### "How does real-time indexing work for breaking news?"
- News sources (RSS feeds, social media APIs) continuously monitored by ingestion workers
- New content enters a **Kafka topic**, processed by stream indexer (Flink / Kafka Streams)
- Stream indexer builds **in-memory index segments** (Tier 0) on each serving node
- These segments are queried alongside the persistent Tier 1/2 index
- A "freshness boost" in the ranker promotes Tier 0 content for time-sensitive queries
- Every few hours, Tier 0 segments are **merged into Tier 1** (compaction)
- End-to-end latency: Breaking news article → searchable in **2-5 minutes**

### "What about spam and SEO manipulation?"
- **Link spam**: Detected via link graph analysis — link farms, bought links, unnatural patterns
- **TrustRank**: Propagate trust from known-good seed pages; pages far from trusted seeds are suspicious
- **Content spam**: Keyword stuffing, hidden text, doorway pages — detected by content classifiers
- **Cloaking**: Show different content to crawler vs user — detected by re-fetching as a user agent
- **Manual penalties**: Human reviewers flag spam rings; penalties applied at domain level
- **ML classifier**: Trained on labeled spam/not-spam pages; features include link patterns, content quality, user engagement signals
- Arms race: Spammers adapt, search engine updates models continuously

### "How would you handle a query like 'weather' differently from 'history of quantum computing'?"
- **Intent classification** at query understanding stage detects query type
- "weather" → **Local + Instant Answer**: Trigger weather widget (structured data from weather API), boost local results, freshness matters
- "history of quantum computing" → **Informational**: Boost comprehensive long-form articles, show knowledge panel for "quantum computing" entity, freshness less important
- This classification affects: which index tiers to prioritize, freshness boosting weight, whether to show widgets/panels, ad relevance thresholds
- Classified using an ML model trained on query logs + human-labeled intent data
