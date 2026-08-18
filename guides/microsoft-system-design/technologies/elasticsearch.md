# Elasticsearch

## What It Is
Elasticsearch is a distributed search and analytics engine built on Apache Lucene. It provides full-text search, structured search, and analytics with near real-time performance.

## How It Works

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         ELASTICSEARCH ARCHITECTURE                                       │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                              ELASTICSEARCH CLUSTER
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                          │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                                │
│   │   Node 1    │    │   Node 2    │    │   Node 3    │                                │
│   │  (Master)   │    │   (Data)    │    │   (Data)    │                                │
│   │             │    │             │    │             │                                │
│   │ ┌─────────┐ │    │ ┌─────────┐ │    │ ┌─────────┐ │                                │
│   │ │Shard 0  │ │    │ │Shard 0  │ │    │ │Shard 1  │ │                                │
│   │ │(Primary)│ │    │ │(Replica)│ │    │ │(Primary)│ │                                │
│   │ └─────────┘ │    │ └─────────┘ │    │ └─────────┘ │                                │
│   │ ┌─────────┐ │    │ ┌─────────┐ │    │ ┌─────────┐ │                                │
│   │ │Shard 1  │ │    │ │Shard 2  │ │    │ │Shard 2  │ │                                │
│   │ │(Replica)│ │    │ │(Primary)│ │    │ │(Replica)│ │                                │
│   │ └─────────┘ │    │ └─────────┘ │    │ └─────────┘ │                                │
│   └─────────────┘    └─────────────┘    └─────────────┘                                │
│                                                                                          │
│   Index "products" → 3 Primary Shards + 1 Replica each = 6 total shards               │
│                                                                                          │
└─────────────────────────────────────────────────────────────────────────────────────────┘

KEY CONCEPTS:
  • Index: Collection of documents (like a database table)
  • Document: JSON object (like a row)
  • Shard: Horizontal partition of an index
  • Replica: Copy of a shard for fault tolerance
```

## Inverted Index

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         INVERTED INDEX (Core of Search)                                  │
└─────────────────────────────────────────────────────────────────────────────────────────┘

DOCUMENTS:
  Doc 1: "The quick brown fox"
  Doc 2: "The quick brown dog"
  Doc 3: "The lazy brown fox"

INVERTED INDEX (term → documents):
┌──────────┬─────────────────────┐
│  Term    │  Document IDs       │
├──────────┼─────────────────────┤
│  the     │  [1, 2, 3]          │
│  quick   │  [1, 2]             │
│  brown   │  [1, 2, 3]          │
│  fox     │  [1, 3]             │
│  dog     │  [2]                │
│  lazy    │  [3]                │
└──────────┴─────────────────────┘

SEARCH: "quick fox"
  1. Look up "quick" → [1, 2]
  2. Look up "fox" → [1, 3]
  3. Intersection/Union based on query type
  4. Rank by relevance (TF-IDF, BM25)
```

## Index Mapping

```json
PUT /products
{
  "mappings": {
    "properties": {
      "name": {
        "type": "text",           // Full-text search
        "analyzer": "standard"
      },
      "name_keyword": {
        "type": "keyword"         // Exact match, aggregations
      },
      "description": {
        "type": "text",
        "analyzer": "english"     // Stemming: "running" → "run"
      },
      "price": {
        "type": "float"
      },
      "category": {
        "type": "keyword"         // For filtering, facets
      },
      "tags": {
        "type": "keyword"         // Array of keywords
      },
      "created_at": {
        "type": "date"
      },
      "location": {
        "type": "geo_point"       // Lat/lon for geo queries
      },
      "attributes": {
        "type": "nested"          // For complex objects
      }
    }
  }
}
```

## Common Queries

### Full-Text Search
```json
// Match query (analyzed, fuzzy)
GET /products/_search
{
  "query": {
    "match": {
      "name": "wireless headphones"
    }
  }
}

// Multi-match (search multiple fields)
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "wireless headphones",
      "fields": ["name^2", "description"],  // name has 2x weight
      "type": "best_fields"
    }
  }
}
```

### Filtered Search
```json
GET /products/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "name": "headphones" } }
      ],
      "filter": [
        { "term": { "category": "electronics" } },
        { "range": { "price": { "gte": 50, "lte": 200 } } },
        { "term": { "in_stock": true } }
      ]
    }
  }
}
```

### Aggregations (Facets)
```json
GET /products/_search
{
  "size": 0,  // Don't return documents, just aggregations
  "aggs": {
    "categories": {
      "terms": { "field": "category", "size": 10 }
    },
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          { "to": 50 },
          { "from": 50, "to": 100 },
          { "from": 100 }
        ]
      }
    },
    "avg_price": {
      "avg": { "field": "price" }
    }
  }
}

// Response:
{
  "aggregations": {
    "categories": {
      "buckets": [
        { "key": "electronics", "doc_count": 1250 },
        { "key": "clothing", "doc_count": 890 }
      ]
    },
    "price_ranges": {
      "buckets": [
        { "key": "*-50", "doc_count": 500 },
        { "key": "50-100", "doc_count": 800 },
        { "key": "100-*", "doc_count": 450 }
      ]
    }
  }
}
```

### Autocomplete (Search-as-you-type)
```json
// Mapping with completion suggester
PUT /products
{
  "mappings": {
    "properties": {
      "name_suggest": {
        "type": "completion"
      }
    }
  }
}

// Query
GET /products/_search
{
  "suggest": {
    "product-suggest": {
      "prefix": "wire",
      "completion": {
        "field": "name_suggest",
        "size": 5,
        "fuzzy": { "fuzziness": 1 }
      }
    }
  }
}
```

## Analyzers

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         TEXT ANALYSIS PIPELINE                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

INPUT: "The QUICK Brown Fox's"

1. CHARACTER FILTERS
   └── Remove HTML, convert characters
   └── "The QUICK Brown Fox's"

2. TOKENIZER
   └── Split into tokens
   └── ["The", "QUICK", "Brown", "Fox's"]

3. TOKEN FILTERS
   └── Lowercase: ["the", "quick", "brown", "fox's"]
   └── Remove possessive: ["the", "quick", "brown", "fox"]
   └── Remove stopwords: ["quick", "brown", "fox"]
   └── Stem: ["quick", "brown", "fox"]

FINAL TOKENS: ["quick", "brown", "fox"]

COMMON ANALYZERS:
  • standard: Default, lowercases, removes punctuation
  • english: Stemming, stopwords (the, a, is)
  • keyword: No analysis, exact match
  • whitespace: Split on whitespace only
```

## Scaling Elasticsearch

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                         SCALING STRATEGIES                                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

1. HORIZONTAL SCALING (Add nodes)
   • More shards = more parallelism
   • Each shard on different node
   • Replicas for read scaling

2. SHARD SIZING
   • Target: 10-50 GB per shard
   • Too many small shards = overhead
   • Too few large shards = slow queries
   
3. INDEX LIFECYCLE
   • Hot: Current data, fast SSDs
   • Warm: Older data, cheaper storage
   • Cold: Archive, rarely accessed
   • Delete: Auto-delete after retention

4. QUERY OPTIMIZATION
   • Use filters (cached) over queries
   • Avoid wildcard prefix queries
   • Use routing for related documents
```

## When to Use

✅ **Good for:**
- Full-text search
- Log analytics (ELK stack)
- Product search with facets
- Autocomplete/suggestions
- Geospatial search
- Real-time analytics

❌ **Not ideal for:**
- Primary database (use PostgreSQL)
- Transactions (no ACID)
- Frequent updates (reindex overhead)
- Joins across indices
- Strong consistency requirements

## Trade-offs

| Pros | Cons |
|------|------|
| Fast full-text search | Not a primary database |
| Distributed, scalable | Memory intensive |
| Rich query DSL | Complex cluster management |
| Near real-time | No transactions |
| Built-in analytics | Learning curve |

## Elasticsearch vs Alternatives

| Feature | Elasticsearch | Solr | PostgreSQL FTS |
|---------|--------------|------|----------------|
| Scaling | Excellent | Good | Limited |
| Real-time | Yes | Near real-time | Yes |
| Analytics | Excellent | Good | Basic |
| Complexity | High | High | Low |
| Standalone | Yes | Yes | Part of DB |

## Used In (This Guide)

- **Teams (Q1)**: Message search
- **Search Autocomplete (Q5)**: Query suggestions
- **E-commerce (Q46)**: Product search with facets
- **Logging (Q22)**: Log aggregation and search
- **Web Crawler (Q9)**: Indexed content search
