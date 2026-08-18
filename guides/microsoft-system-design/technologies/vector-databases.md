# Vector Databases & Embeddings

## What They Are
Vector databases are specialized storage systems designed to store, index, and search high-dimensional vectors (embeddings). An **embedding** is a numerical representation of data (text, images, audio, code) as a dense array of floats — typically 256 to 3,072 dimensions — produced by a neural network. Vector databases enable **semantic search**: finding items by meaning, not just exact keywords.

```
Traditional DB:  "SELECT * FROM docs WHERE text LIKE '%machine learning%'"
                 → Exact keyword match only. Misses "ML", "AI", "neural network"

Vector DB:       "Find documents closest to embedding('machine learning')"
                 → Returns semantically similar content regardless of exact wording
                 → Also matches "deep learning techniques", "neural network training"
```

## How Embeddings Work

```
┌──────────────────────────────────────────────────────────────────┐
│                    EMBEDDING PIPELINE                             │
│                                                                   │
│  INPUT                    EMBEDDING MODEL              OUTPUT    │
│                                                                   │
│  "The cat sat on     ──▶  text-embedding-3-large  ──▶ [0.023,   │
│   the mat"                (OpenAI, 3072-dim)          -0.041,    │
│                                                        0.118,    │
│                                                        ...,      │
│                                                        0.067]    │
│                                                       (3072 floats)│
│                                                                   │
│  [photo of a cat]    ──▶  CLIP (768-dim)          ──▶ [0.112,   │
│                                                        0.034,    │
│                                                        ...,      │
│                                                        -0.089]   │
│                                                                   │
│  "def fibonacci(n):" ──▶  code-embedding model    ──▶ [0.056,   │
│                            (1536-dim)                   ...,     │
│                                                         0.041]   │
│                                                                   │
│  KEY PROPERTY:                                                   │
│  Similar meanings → vectors that are CLOSE in space              │
│  "happy" ≈ "joyful" ≈ "content"  (small distance between them)  │
│  "happy" ≠ "database"            (large distance between them)   │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Distance Metrics
```
┌──────────────────────────────────────────────────────────────────┐
│                    DISTANCE / SIMILARITY METRICS                  │
│                                                                   │
│  COSINE SIMILARITY (most common for text):                       │
│  ├── Measures angle between vectors (ignoring magnitude)         │
│  ├── Range: -1 (opposite) to +1 (identical)                     │
│  ├── cos(A, B) = (A · B) / (||A|| × ||B||)                     │
│  └── Best for: Text embeddings (normalize for length)            │
│                                                                   │
│  EUCLIDEAN DISTANCE (L2):                                        │
│  ├── Straight-line distance in N-dimensional space               │
│  ├── Range: 0 (identical) to ∞                                  │
│  ├── d(A, B) = √(Σ(ai - bi)²)                                  │
│  └── Best for: When magnitude matters (image features)           │
│                                                                   │
│  DOT PRODUCT (Inner Product):                                    │
│  ├── Fast computation, considers both direction and magnitude    │
│  ├── Range: -∞ to +∞ (higher = more similar)                   │
│  ├── A · B = Σ(ai × bi)                                        │
│  └── Best for: Pre-normalized vectors, recommendation systems   │
│                                                                   │
│  CHOOSING A METRIC:                                              │
│  ├── Text search → Cosine (standard choice)                     │
│  ├── Image similarity → Euclidean or Cosine                     │
│  ├── Recommendation → Dot Product (if vectors are normalized)   │
│  └── When in doubt → Cosine (most robust)                       │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## How Vector Search Works

```
┌──────────────────────────────────────────────────────────────────┐
│                    VECTOR SEARCH PIPELINE                         │
│                                                                   │
│  STEP 1: INDEX TIME (offline)                                    │
│  ├── For each document/chunk:                                    │
│  │   text → embedding model → [0.023, -0.041, ...] → store     │
│  │   Also store metadata: {source, title, page, date}           │
│  └── Build search index over all vectors                         │
│                                                                   │
│  STEP 2: QUERY TIME (online)                                    │
│  ├── User query → embedding model → query_vector                │
│  ├── Search index for top-K nearest neighbors                    │
│  ├── Return ranked results with distances/scores                 │
│  └── Optionally filter by metadata (date, category, etc.)       │
│                                                                   │
│  EXAMPLE:                                                        │
│  Query: "How to handle authentication in microservices?"         │
│       │                                                           │
│       ▼ (embed)                                                   │
│  query_vector = [0.034, -0.012, 0.098, ...]                     │
│       │                                                           │
│       ▼ (search top-5 nearest)                                   │
│  Results:                                                        │
│  ├── doc_42: "JWT token validation in API gateways" (0.92)      │
│  ├── doc_17: "OAuth 2.0 flows for service-to-service" (0.89)   │
│  ├── doc_103: "Implementing SSO across microservices" (0.87)    │
│  ├── doc_56: "API key management best practices" (0.83)        │
│  └── doc_201: "Zero-trust network architecture" (0.79)          │
│                                                                   │
│  Note: NONE of these contain the exact words "handle            │
│  authentication in microservices" — but they're all relevant!   │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Indexing Algorithms (ANN — Approximate Nearest Neighbor)

Exact nearest neighbor search (brute force) is O(n × d) per query — too slow for millions of vectors. Vector databases use **approximate** algorithms that trade a small accuracy loss for massive speed gains.

```
┌──────────────────────────────────────────────────────────────────┐
│                    ANN ALGORITHMS                                 │
│                                                                   │
│  1. HNSW (Hierarchical Navigable Small World) ⭐ MOST COMMON    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Multi-layer graph where each node connects to nearby     │    │
│  │  vectors. Search starts at top layer (sparse, long jumps) │    │
│  │  and drills down to bottom layer (dense, precise).        │    │
│  │                                                            │    │
│  │  Layer 3:  A ─────────────────── F                        │    │
│  │  Layer 2:  A ──── C ──── F                                │    │
│  │  Layer 1:  A ─ B ─ C ─ D ─ E ─ F ─ G                    │    │
│  │  Layer 0:  A B C D E F G H I J K L M N                   │    │
│  │                                                            │    │
│  │  ✅ Pros: Very fast queries, great recall (95-99%)        │    │
│  │  ❌ Cons: High memory (stores graph in RAM), slow builds  │    │
│  │  Used by: pgvector, Qdrant, Pinecone, Weaviate            │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  2. IVF (Inverted File Index)                                    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Cluster vectors using k-means, then only search the      │    │
│  │  nearest clusters (nprobe) instead of all vectors.        │    │
│  │                                                            │    │
│  │  Cluster 1: [v1, v5, v9, v12]                             │    │
│  │  Cluster 2: [v2, v3, v8, v15]   ← query lands here       │    │
│  │  Cluster 3: [v4, v7, v11, v13]  ← also search this       │    │
│  │  Cluster 4: [v6, v10, v14, v16]                           │    │
│  │                                                            │    │
│  │  ✅ Pros: Less memory than HNSW, good for very large sets │    │
│  │  ❌ Cons: Lower recall unless nprobe is high              │    │
│  │  Used by: FAISS, Milvus                                    │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  3. PQ (Product Quantization)                                    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  Compress vectors by splitting into sub-vectors and        │    │
│  │  replacing each with a codebook ID.                        │    │
│  │                                                            │    │
│  │  Original:  [0.12, 0.34, 0.56, 0.78, 0.91, 0.23, ...]   │    │
│  │  Quantized: [code_3, code_7, code_1, ...]                 │    │
│  │             (each code = 1 byte instead of 4 bytes/float) │    │
│  │                                                            │    │
│  │  ✅ Pros: 4-32x memory reduction                          │    │
│  │  ❌ Cons: Lower accuracy (lossy compression)              │    │
│  │  Often combined: IVF-PQ (FAISS), HNSW-PQ                  │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  PERFORMANCE COMPARISON (1M vectors, 1536-dim):                  │
│  ├── Brute force:   ~500ms/query,  100% recall, 6 GB RAM       │
│  ├── IVF (nprobe=10): ~5ms/query,  90% recall,  6 GB RAM       │
│  ├── HNSW:            ~1ms/query,  97% recall,  12 GB RAM      │
│  └── IVF-PQ:          ~2ms/query,  85% recall,  0.5 GB RAM     │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Vector Database Options

### Comparison Matrix

| Database | Type | ANN Algorithm | Max Vectors | Metadata Filter | Hosting | Best For |
|----------|------|--------------|-------------|-----------------|---------|----------|
| **pgvector** | Extension (Postgres) | HNSW, IVF | ~10M practical | Full SQL WHERE | Self-hosted / managed PG | Teams already on Postgres, < 10M vectors |
| **Azure AI Search** | Managed service | HNSW | Billions | OData filters | Azure managed | Enterprise RAG with hybrid search |
| **Pinecone** | Managed service | Proprietary | Billions | Metadata filters | Fully managed (SaaS) | Serverless, don't want to manage infra |
| **Qdrant** | Dedicated vector DB | HNSW | Billions | Rich payload filters | Self-hosted / cloud | High-performance, complex filtering |
| **Weaviate** | Dedicated vector DB | HNSW | Billions | GraphQL filters | Self-hosted / cloud | Multi-modal (text + image), integrated vectorization |
| **Milvus** | Dedicated vector DB | IVF, HNSW, DiskANN | Billions | Attribute filters | Self-hosted / Zilliz Cloud | Very large scale, GPU-accelerated |
| **ChromaDB** | Embedded | HNSW | ~1M practical | Metadata filters | Embedded (in-process) | Prototyping, small projects, local dev |
| **FAISS** | Library (not a DB) | IVF, HNSW, PQ | Billions | None built-in | In-process (Python/C++) | Research, batch offline search, custom pipelines |
| **Redis Vector** | Extension (Redis) | HNSW, Flat | ~10M practical | Redis query filters | Self-hosted / Redis Cloud | Already using Redis, need caching + search |
| **Cosmos DB (vCore)** | Extension (Mongo API) | DiskANN | Millions | MongoDB query syntax | Azure managed | Azure-native, combined with document storage |

### pgvector (PostgreSQL Extension) — Detailed

```
┌──────────────────────────────────────────────────────────────────┐
│                    pgvector                                       │
│                                                                   │
│  Add vector search to your existing Postgres database.           │
│  No new infrastructure needed.                                   │
│                                                                   │
│  SETUP:                                                          │
│  CREATE EXTENSION vector;                                        │
│                                                                   │
│  CREATE TABLE documents (                                        │
│    id SERIAL PRIMARY KEY,                                        │
│    title TEXT,                                                    │
│    content TEXT,                                                  │
│    category TEXT,                                                 │
│    embedding VECTOR(1536)  -- OpenAI text-embedding-3-small      │
│  );                                                               │
│                                                                   │
│  -- Create HNSW index                                            │
│  CREATE INDEX ON documents                                       │
│    USING hnsw (embedding vector_cosine_ops)                      │
│    WITH (m = 16, ef_construction = 256);                         │
│                                                                   │
│  -- Search for similar documents                                 │
│  SELECT id, title, content,                                      │
│         1 - (embedding <=> query_embedding) AS similarity        │
│  FROM documents                                                   │
│  WHERE category = 'engineering'       -- metadata filter!        │
│  ORDER BY embedding <=> query_embedding                          │
│  LIMIT 10;                                                        │
│                                                                   │
│  OPERATORS:                                                      │
│  ├── <=>  Cosine distance                                        │
│  ├── <->  Euclidean distance (L2)                                │
│  └── <#>  Negative inner product                                 │
│                                                                   │
│  ✅ WHY USE IT:                                                  │
│  ├── No new database to manage — just an extension               │
│  ├── Full SQL power: JOINs, WHERE, GROUP BY, transactions       │
│  ├── Combine vector search with relational queries               │
│  ├── ACID compliance (vectors are transactional!)                │
│  ├── Works with existing Postgres tooling (pg_dump, replicas)    │
│  └── Hosted everywhere: RDS, Azure, Supabase, Neon              │
│                                                                   │
│  ❌ LIMITATIONS:                                                 │
│  ├── Performance degrades past ~5-10M vectors per table          │
│  ├── HNSW index build is slow (hours for millions of vectors)    │
│  ├── HNSW index lives in shared memory — eats RAM                │
│  ├── No built-in sharding (must use Citus or app-level)          │
│  ├── No GPU acceleration                                         │
│  └── ANN recall lower than dedicated vector DBs at scale         │
│                                                                   │
│  TUNING PARAMS:                                                  │
│  ├── m (HNSW): Max edges per node (default 16, higher = more    │
│  │   RAM but better recall)                                      │
│  ├── ef_construction: Build quality (default 64, higher =       │
│  │   slower build but better recall)                             │
│  └── ef_search (SET hnsw.ef_search = 100): Query quality        │
│      (higher = slower but better recall)                         │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Azure AI Search — Detailed

```
┌──────────────────────────────────────────────────────────────────┐
│                    AZURE AI SEARCH                                │
│                                                                   │
│  Enterprise-grade hybrid search (vector + keyword + semantic).   │
│                                                                   │
│  HYBRID SEARCH (the killer feature):                             │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  Query: "Azure Kubernetes pod networking issues"      │        │
│  │                                                        │        │
│  │  1. Vector search: embed query → find semantically    │        │
│  │     similar documents (catches "AKS CNI problems")    │        │
│  │                                                        │        │
│  │  2. Keyword search (BM25): find documents containing  │        │
│  │     exact terms "Azure", "Kubernetes", "pod"          │        │
│  │                                                        │        │
│  │  3. Semantic ranker: Cross-encoder model re-ranks      │        │
│  │     combined results for relevance                     │        │
│  │                                                        │        │
│  │  4. RRF (Reciprocal Rank Fusion): Merge rankings      │        │
│  │     from all three into final result list              │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  WHY HYBRID > VECTOR-ONLY:                                       │
│  ├── Vector search misses exact matches ("error code E4502")    │
│  ├── Keyword search misses semantic matches ("car" vs "vehicle")│
│  ├── Hybrid catches both → 15-30% better recall in practice     │
│  └── Semantic ranker adds another 5-10% relevance improvement   │
│                                                                   │
│  ✅ Best for: Enterprise RAG, when precision matters             │
│  ❌ Cost: More expensive than pgvector or open-source options    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## When to Use (and When NOT to Use)

```
┌──────────────────────────────────────────────────────────────────┐
│                    WHEN TO USE VECTOR DATABASES                   │
│                                                                   │
│  ✅ USE WHEN:                                                    │
│                                                                   │
│  1. SEMANTIC / SIMILARITY SEARCH                                 │
│     "Find documents about X" where X may not appear literally    │
│     → Product search: "comfortable running shoes for flat feet"  │
│     → Support: "my screen is flickering" → matches "display     │
│       artifacts" knowledge article                               │
│                                                                   │
│  2. RAG (Retrieval-Augmented Generation)                         │
│     Ground LLM responses in your own data                        │
│     → User asks question → retrieve relevant chunks →           │
│       feed to LLM as context → accurate, grounded answer        │
│                                                                   │
│  3. RECOMMENDATION SYSTEMS                                       │
│     "Users who liked X also liked Y" via embedding similarity    │
│     → Embed user behavior → find similar users/items             │
│                                                                   │
│  4. DEDUPLICATION / NEAR-DUPLICATE DETECTION                     │
│     Find near-duplicates (plagiarism, image duplicates)          │
│     → Embed content → search for vectors within threshold       │
│                                                                   │
│  5. MULTI-MODAL SEARCH                                           │
│     Search images with text, or text with images                 │
│     → CLIP embeds both text and images in same vector space      │
│     → "sunset over ocean" → finds matching photos                │
│                                                                   │
│  6. ANOMALY DETECTION                                            │
│     Find outliers in high-dimensional data                       │
│     → Embed normal behavior → new data far from all clusters    │
│       = anomaly                                                  │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│                    WHEN NOT TO USE VECTOR DATABASES               │
│                                                                   │
│  ❌ DON'T USE WHEN:                                              │
│                                                                   │
│  1. EXACT MATCH / STRUCTURED QUERIES                             │
│     "SELECT * WHERE status = 'active' AND price < 100"          │
│     → Use SQL. Vector search is for fuzzy/semantic, not exact.  │
│                                                                   │
│  2. FULL-TEXT KEYWORD SEARCH                                     │
│     "Find all documents containing 'error code E4502'"          │
│     → Use Elasticsearch/Lucene. Vectors miss exact strings.     │
│     → Or use hybrid search (vector + keyword together).          │
│                                                                   │
│  3. SMALL DATASETS (< 10K items)                                │
│     Just use brute-force comparison. The overhead of ANN         │
│     indexing isn't worth it for small collections.               │
│     → numpy cosine_similarity is fine for < 10K vectors         │
│                                                                   │
│  4. FREQUENTLY CHANGING DATA                                     │
│     If >50% of vectors change daily, index rebuild is costly.   │
│     HNSW doesn't handle high-churn well (insertions OK,         │
│     deletions are expensive — tombstones degrade quality).       │
│                                                                   │
│  5. YOU NEED STRONG CONSISTENCY / TRANSACTIONS                   │
│     Most vector DBs are eventually consistent.                   │
│     If you need "insert then immediately search" guarantees →   │
│     use pgvector (ACID) or accept slight delay.                  │
│                                                                   │
│  6. COST IS PRIMARY CONCERN                                      │
│     Vectors are memory-heavy: 1M vectors × 1536 dims × 4 bytes │
│     = 6 GB just for raw vectors (+ 2-3x for HNSW index).        │
│     If you're cost-constrained, consider PQ compression          │
│     or use keyword search instead.                               │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Limitations & Pitfalls

```
┌──────────────────────────────────────────────────────────────────┐
│                    KEY LIMITATIONS                                │
│                                                                   │
│  1. RECALL vs SPEED TRADE-OFF                                    │
│  ├── ANN algorithms are APPROXIMATE — they miss results          │
│  ├── Typical recall: 85-97% (meaning 3-15% of true nearest      │
│  │   neighbors are missed)                                       │
│  ├── Higher recall = slower queries and more memory              │
│  └── You can tune (ef_search, nprobe) but can't get 100%        │
│      without brute force                                         │
│                                                                   │
│  2. EMBEDDING QUALITY IS EVERYTHING                              │
│  ├── Garbage in → garbage out. The DB can only find similar      │
│  │   vectors — if embeddings are bad, results are bad            │
│  ├── Embedding model must match your domain                      │
│  │   (generic model may not understand medical/legal jargon)     │
│  ├── Changing embedding models = RE-EMBED EVERYTHING             │
│  │   (old and new embeddings are incompatible)                   │
│  └── Embedding model version lock-in is a real operational cost  │
│                                                                   │
│  3. CHUNKING STRATEGY MATTERS MORE THAN DB CHOICE                │
│  ├── For RAG: How you split documents into chunks determines     │
│  │   retrieval quality more than which vector DB you use         │
│  ├── Too small chunks → lost context ("he" without knowing who) │
│  ├── Too large chunks → diluted embeddings (topic drift)        │
│  ├── Optimal: 200-500 tokens per chunk with 50-token overlap    │
│  └── Semantic chunking (split by topic) > fixed-size chunking   │
│                                                                   │
│  4. MEMORY COST                                                  │
│  ├── Vectors are fat: 1536 dims × 4 bytes = 6 KB per vector    │
│  ├── HNSW index: 2-3x overhead on top of raw vectors            │
│  ├── 10M vectors × 1536-dim → ~60 GB raw + ~120 GB index       │
│  │   → needs machines with 256 GB+ RAM                          │
│  ├── Mitigation: PQ compression, disk-based indexes (DiskANN),  │
│  │   or smaller embedding dimensions (256-dim vs 1536-dim)      │
│  └── Binary quantization: 32x reduction (4 bytes → 1 bit/dim)  │
│                                                                   │
│  5. FILTERED SEARCH IS HARD                                      │
│  ├── "Find similar docs WHERE category = 'finance'"             │
│  ├── Pre-filtering: Filter first, then ANN search on subset     │
│  │   → problem: small subsets may not have enough HNSW graph     │
│  │     connectivity → poor recall                                │
│  ├── Post-filtering: ANN search first, then filter results      │
│  │   → problem: if filter is very selective, most of top-K      │
│  │     results get filtered out → too few results                │
│  ├── Integrated filtering (Qdrant, Pinecone): Best approach     │
│  │   but implementation-specific and adds latency                │
│  └── pgvector: Use SQL WHERE + ORDER BY vector distance         │
│      (works but Postgres planner may choose bad plan)            │
│                                                                   │
│  6. NO STANDARD QUERY LANGUAGE                                   │
│  ├── Every vector DB has its own API and query syntax            │
│  ├── No "SQL for vectors" standard exists yet                    │
│  ├── Migration between vector DBs requires code changes          │
│  └── pgvector is closest to standard (it's just SQL)            │
│                                                                   │
│  7. COLD START / BOOTSTRAPPING                                   │
│  ├── Embedding millions of documents takes hours-days            │
│  ├── Cost: Embedding 1M docs × 500 tokens each via OpenAI      │
│  │   ≈ $10-20 (cheap, but re-embedding on model change = $$)   │
│  ├── Index building: HNSW over 10M vectors = hours               │
│  └── Plan for incremental updates, not full rebuilds             │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Decision Guide: Which Vector Database?

```
┌──────────────────────────────────────────────────────────────────┐
│                    DECISION FLOWCHART                             │
│                                                                   │
│  START HERE                                                      │
│       │                                                           │
│       ▼                                                           │
│  Do you have < 10K vectors?                                      │
│  ├── YES → Use brute force (numpy / FAISS Flat). No DB needed.  │
│  └── NO ↓                                                        │
│                                                                   │
│  Are you already using PostgreSQL?                               │
│  ├── YES → Do you have < 5-10M vectors?                         │
│  │         ├── YES → Use pgvector. Simplest option.              │
│  │         └── NO  → Consider dedicated vector DB                │
│  └── NO ↓                                                        │
│                                                                   │
│  Do you need hybrid search (vector + keyword)?                   │
│  ├── YES → Are you on Azure?                                    │
│  │         ├── YES → Azure AI Search (best hybrid)              │
│  │         └── NO  → Weaviate or Elasticsearch with vectors     │
│  └── NO ↓                                                        │
│                                                                   │
│  Do you want fully managed (no infra)?                           │
│  ├── YES → Pinecone (simplest managed) or Zilliz (Milvus SaaS) │
│  └── NO ↓                                                        │
│                                                                   │
│  Do you need complex metadata filtering?                         │
│  ├── YES → Qdrant (best filtering) or Weaviate                  │
│  └── NO ↓                                                        │
│                                                                   │
│  Do you need billion-scale vectors?                              │
│  ├── YES → Milvus (GPU support) or Pinecone                     │
│  └── NO  → Qdrant or Weaviate                                   │
│                                                                   │
│  Are you just prototyping / local dev?                           │
│  └── YES → ChromaDB (embedded, pip install chromadb, done)      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Common Patterns

### RAG Pipeline (Retrieval-Augmented Generation)
```
┌──────────────────────────────────────────────────────────────────┐
│                    RAG PATTERN                                    │
│                                                                   │
│  INDEXING (offline):                                             │
│  Documents → Chunk (500 tokens) → Embed → Store in Vector DB   │
│                                                                   │
│  QUERYING (online):                                              │
│  User question → Embed → Search Vector DB (top-5 chunks)       │
│       │                                                           │
│       ▼                                                           │
│  Prompt:                                                         │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  System: Answer based on the following context only.  │        │
│  │                                                        │        │
│  │  Context:                                              │        │
│  │  [chunk 1: "Authentication uses JWT tokens..."]        │        │
│  │  [chunk 2: "Token expiry is set to 1 hour..."]        │        │
│  │  [chunk 3: "Refresh tokens are stored in..."]         │        │
│  │                                                        │        │
│  │  Question: How does our auth system work?              │        │
│  └──────────────────────────────────────────────────────┘        │
│       │                                                           │
│       ▼                                                           │
│  LLM generates grounded answer using retrieved context           │
│                                                                   │
│  IMPROVING RAG QUALITY:                                          │
│  ├── Hybrid search (vector + keyword) for better retrieval       │
│  ├── Re-ranking: Use a cross-encoder to re-rank top-20 → top-5 │
│  ├── Query expansion: LLM rewrites user query for better recall │
│  ├── Chunk overlap: 50-100 token overlap between chunks          │
│  └── Metadata filtering: Restrict to relevant doc categories    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Semantic Cache
```
Cache LLM responses by semantic similarity of the question:
1. Embed the user's question
2. Search cache for similar past questions (cosine > 0.95)
3. If match → return cached LLM response (skip LLM call, save $$$)
4. If no match → call LLM, store question embedding + response

Saves 5-15% of LLM costs for repetitive workloads.
```

### Two-Stage Retrieval
```
Stage 1: Fast ANN search → top-100 candidates (< 5ms)
Stage 2: Cross-encoder re-ranker → re-rank to top-10 (< 50ms)

Why: ANN recall is ~95%. The cross-encoder (slower but exact)
     catches the 5% of results that ANN missed among the top-100.
     Much cheaper than running cross-encoder on all documents.
```

## Trade-offs

| Decision | Option A | Option B | Recommendation |
|----------|----------|----------|----------------|
| pgvector vs dedicated vector DB | Simple, no new infra | Higher performance at scale | **pgvector if < 5M vectors**, dedicated if bigger |
| HNSW vs IVF | Higher recall, more RAM | Less RAM, lower recall | **HNSW** (default choice for most workloads) |
| Vector-only vs hybrid search | Simpler, semantic only | Better precision, keyword + semantic | **Hybrid** for production RAG (vector-only misses exact terms) |
| Managed vs self-hosted | No ops, higher cost | Full control, lower cost | **Managed for startups/small teams**, self-hosted at scale |
| Small embedding dim (256) vs large (1536+) | Faster, less RAM | Better accuracy | **Start with large**, compress later if cost is an issue |
| PQ compression | 4-8x less RAM | 5-15% recall loss | **Use PQ for > 50M vectors** where RAM is the bottleneck |
| Single DB vs separate vector DB | Simpler architecture | Better separation of concerns | **pgvector for < 5M**, separate vector DB for larger or performance-critical |

## Configuration Tips

```
pgvector:
├── SET hnsw.ef_search = 100;   -- higher = better recall, slower
├── m = 16-32 for HNSW          -- connections per node
├── ef_construction = 256        -- higher = slower build, better recall
├── VACUUM ANALYZE after bulk insert (rebuild stats)
└── Use EXPLAIN ANALYZE to check if index is actually being used

Embedding dimensions:
├── text-embedding-3-large: 3072-dim (best quality, most expensive)
├── text-embedding-3-small: 1536-dim (good balance)
├── Cohere embed-v3: 1024-dim (competitive quality)
├── Use Matryoshka truncation: 3072 → 256 dims with ~5% quality loss
└── Smaller dims = less RAM, faster search, slightly lower quality

General:
├── Always normalize vectors before storing (for cosine similarity)
├── Benchmark YOUR data — generic benchmarks may not reflect your
│   distribution, dimensionality, and query patterns
├── Start simple (pgvector or ChromaDB), migrate when you hit limits
└── Monitor recall: Compare ANN results to brute-force on sample queries
```

## Used In (This Guide)
| System Design | Usage |
|--------------|-------|
| #5 Search Autocomplete | Semantic query understanding |
| #29 Recommendation Engine | User/item embedding similarity |
| #35 LinkedIn PYMK | People similarity via graph embeddings |
| #49 GitHub Copilot | RAG over repository code (semantic code search) |
| #50 Azure AI Foundry | RAG pipeline, Azure AI Search integration |
| #51 AI-Powered SOC | Alert similarity, threat pattern matching |
