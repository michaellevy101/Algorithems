# Vector Databases

> **🎯 Goal**: Understand how to store, index, and search vectors at scale
> **⏱ Time**: 2–3h study + 1h exercises
> **📦 Deliverable**: Ability to choose and integrate a vector database for similarity search

---

## Table of Contents

1. [The Problem — Why Not Just Brute Force?](#1-the-problem--why-not-just-brute-force)
2. [Approximate Nearest Neighbors (ANN)](#2-approximate-nearest-neighbors-ann)
3. [HNSW (Hierarchical Navigable Small World)](#3-hnsw-hierarchical-navigable-small-world)
4. [IVF (Inverted File Index)](#4-ivf-inverted-file-index)
5. [FAISS (Facebook AI Similarity Search)](#5-faiss-facebook-ai-similarity-search)
6. [Dedicated Vector Databases](#6-dedicated-vector-databases)
7. [PostgreSQL + pgvector](#7-postgresql--pgvector)
8. [Integration Patterns](#8-integration-patterns)
9. [Performance Tuning](#9-performance-tuning)
10. [Choosing the Right Solution](#10-choosing-the-right-solution)

---

## 1. The Problem — Why Not Just Brute Force?

### The Intuition (Backend Analogy)

You have a SQL table with 100 million rows and no indexes. Every query does a full
table scan. That's brute-force vector search — compare your query against every single
vector. At 100 rows, nobody cares. At 100 million rows with 768-dimensional vectors,
you're doing 100M × 768 floating-point operations **per query**. At 10 queries/sec,
you're dead.

You need an **index** — a data structure that lets you skip most of the data.

### How It Works

```
  BRUTE FORCE (Flat / Exact Search)

  Query vector: q = [0.2, -0.1, 0.8, ..., 0.3]   (768-dim)

  Compare against every stored vector:
  ┌──────────────────────────────────────┐
  │  v₁  [0.1, 0.3, ...]  → dist(q,v₁) = 0.82  │
  │  v₂  [0.9, -0.2, ...] → dist(q,v₂) = 0.15  │  ← close!
  │  v₃  [0.4, 0.1, ...]  → dist(q,v₃) = 0.71  │
  │  ...                                          │
  │  v₁₀₀ₘ  [...]         → dist(q,v₁₀₀ₘ)       │
  └──────────────────────────────────────┘

  Time: O(N × D) per query
  N = 100,000,000 vectors
  D = 768 dimensions
  ≈ 77 BILLION float ops per query  →  Way too slow
```

**The Math:**

```
  Nearest Neighbor Objective
  ─────────────────────────
  Given a query vector q and a set of stored vectors {v₁, v₂, ..., vₙ}:

    Find:  argmin  d(q, vᵢ)          (for distance metrics)
              i
           argmax  sim(q, vᵢ)        (for similarity metrics)
              i

  Brute force computes d(q, vᵢ) for ALL i = 1..N  →  O(N · D) per query

  Distance / Similarity Functions
  ───────────────────────────────
  L2 (Euclidean):   d(a,b) = √( Σᵢ (aᵢ - bᵢ)² )  =  ‖a - b‖₂
                    Use when absolute magnitude matters (e.g., image features)

  Cosine distance:  d(a,b) = 1 - (a · b) / (‖a‖ · ‖b‖)
                    Use when only direction matters, not magnitude
                    (e.g., text embeddings — "king" and "KING" differ in norm)

  Inner product:    sim(a,b) = a · b = Σᵢ aᵢ · bᵢ
                    Use when vector norms are meaningful (e.g., popularity-
                    weighted embeddings where larger norm = more important)
```

### When to Use (Brute Force)

- Less than ~50K vectors — exact search is fast enough
- You need **guaranteed exact** results (medical, legal)
- One-off batch jobs where latency doesn't matter

---

## 2. Approximate Nearest Neighbors (ANN)

### The Intuition (Backend Analogy)

Like a database query planner choosing a B-tree index scan over a full table scan.
You might miss a row that's technically in the result set, but you get your answer
in milliseconds instead of minutes. ANN makes the same trade: lose 1-5% recall,
gain 100-1000× speedup.

### How It Works

```
  Exact (brute force):  Check ALL vectors    → 100% recall, 500ms latency
  Approximate (ANN):    Check ~10K candidates → 95-99% recall, 2ms latency

  Algorithm families:  Tree-based (Annoy) | Hash-based (LSH) | Graph-based (HNSW) ★
  HNSW is most popular in practice: best recall-at-speed tradeoff.
```

### When to Use

- Almost always — exact search is the exception, not the rule
- Embedding similarity is inherently fuzzy, so approximate is fine
- The 1-5% you "miss" are usually borderline results anyway

---

## 3. HNSW (Hierarchical Navigable Small World)

### The Intuition (Backend Analogy)

Like DNS resolution. To find `api.us-east-1.prod.company.com`, you don't scan
every DNS record on Earth. You start at the **root** (`.com`), hop to `company`,
then `prod`, then `us-east-1`, then `api`. Each layer narrows the search space.

HNSW works the same way — a multi-layer graph where the top layer has few nodes
with long-range connections (highways), and the bottom layer has all nodes with
short-range connections (local roads).

### How It Works

```
  HNSW: Multi-Layer Skip-List Graph

  Layer 2 (few nodes, long jumps — "highways")
  ┌─────────────────────────────────────────────┐
  │                                             │
  │    [A] ─────────────────────── [M]          │
  │                                             │
  └──────────────────┬──────────────┬───────────┘
                     │              │
  Layer 1 (more nodes, medium jumps)│
  ┌──────────────────┼──────────────┼───────────┐
  │                  ▼              ▼            │
  │    [A] ──── [D] ──── [H] ──── [M] ── [P]   │
  │                                             │
  └──┬──────┬──────┬──────┬──────┬──────┬───────┘
     │      │      │      │      │      │
  Layer 0 (ALL nodes, short jumps — "local roads")
  ┌──┼──────┼──────┼──────┼──────┼──────┼───────┐
  │  ▼      ▼      ▼      ▼      ▼      ▼       │
  │ [A]─[B]─[C]─[D]─[E]─[F]─[G]─[H]─...─[P]   │
  │  Each node connected to M nearest neighbors  │
  └──────────────────────────────────────────────┘

  SEARCH for query Q (closest to node [F]):

  Step 1: Enter at Layer 2 → [A]
           [A] or [M]? → [A] is closer to Q → stay at [A]

  Step 2: Drop to Layer 1 → [A]
           Scan neighbors: [A]→[D]→[H]
           [D] is closest to Q → move to [D]

  Step 3: Drop to Layer 0 → [D]
           Scan neighbors: [D]→[E]→[F]
           [F] is closest to Q → FOUND!

  Total comparisons: ~12    (vs 16 for brute force)
  At 100M vectors: ~2000    (vs 100,000,000 for brute force)
```

### Key Parameters

```
  ┌─────────────────────┬──────────────────────────────────┐
  │ Parameter           │ What it controls                 │
  ├─────────────────────┼──────────────────────────────────┤
  │ M                   │ Connections per node (default 16)│
  │  (higher = better   │ More edges = better recall,      │
  │   recall, more RAM) │ but more memory and slower build │
  ├─────────────────────┼──────────────────────────────────┤
  │ efConstruction      │ Build-time quality (default 200) │
  │  (higher = better   │ More candidates during insert =  │
  │   index, slower     │ better graph, slower build       │
  │   build)            │                                  │
  ├─────────────────────┼──────────────────────────────────┤
  │ efSearch            │ Query-time quality (default 50)  │
  │  (higher = better   │ More candidates during search =  │
  │   recall, slower    │ better recall, higher latency    │
  │   queries)          │                                  │
  └─────────────────────┴──────────────────────────────────┘

  Rule of thumb: efSearch must be >= K (number of results you want)
```

**The Math:** Search time O(log N). Memory per vector ≈ D×4 bytes + M×2 layers×8 bytes.
Higher M → better recall, more memory. Higher ef → better recall, slower search.

### When to Use

- Default choice for most vector search use cases
- Low-latency serving (< 10ms for millions of vectors)
- When you can afford the memory (~1.5× raw vector size)

---

## 4. IVF (Inverted File Index)

### The Intuition (Backend Analogy)

Like database sharding. Instead of searching one giant table, you split data into
partitions. At query time, figure out which partitions are relevant, and only search
those. IVF partitions vector space using K-means clustering.

### How It Works

```
  IVF: Cluster → Probe → Search

  BUILD TIME:
  1. Run K-means on all vectors → N cluster centroids

     Centroid 1 ●          Centroid 2 ●         Centroid 3 ●
                |                     |                     |
          ┌─────┴─────┐        ┌─────┴─────┐        ┌─────┴─────┐
          │ v₁ v₅ v₈  │        │ v₂ v₃ v₉  │        │ v₄ v₆ v₇  │
          │ v₁₁ v₁₅   │        │ v₁₀ v₁₃   │        │ v₁₂ v₁₄   │
          └────────────┘        └────────────┘        └────────────┘
           Partition 1           Partition 2           Partition 3

  QUERY TIME:
  2. Compare query Q against N centroids (fast — only N comparisons)
  3. Pick top nprobe partitions
  4. Search only vectors in those partitions

  Query Q ──→ Nearest centroids: [2, 1]  (nprobe=2)
              ──→ Search partitions 2 and 1 only
              ──→ Skip partition 3 entirely!

  With 1000 partitions and nprobe=10:
  Search only 1% of data → ~100× speedup
```

**The Math:** K-means partitions vectors into K clusters. At query time: compare against
K centroids O(K·D), then search only top-nprobe partitions O(nprobe/K · N · D).

### IVF + PQ (Product Quantization)

```
  PQ compresses each vector to save memory:

  Original: [0.21, -0.05, 0.88, 0.14, ..., 0.33]   768 floats × 4 bytes = 3KB
                    ↓  (Product Quantization)
  Compressed: [42, 17, 8, 91, ..., 63]               96 bytes (32× smaller)

  IVF handles "where to search"
  PQ handles "how to store compactly"
  Together: IVF + PQ = fast search + low memory
```

**PQ Math:** Split D-dim vector into m sub-vectors, quantize each with k centroids.
Memory: m·log₂(k) bits per vector. Example: D=768, m=96, k=256 → 96 bytes (32× compression).

### When to Use

- Very large datasets (>10M vectors) where HNSW memory is too expensive
- High-throughput batch search (many queries at once)
- When you can trade some latency for lower memory

---

## 5. FAISS (Facebook AI Similarity Search)

### The Intuition (Backend Analogy)

FAISS is like a low-level database engine (think SQLite or RocksDB) — not a full
database server, but a fast, flexible library you embed in your application. It gives
you index primitives; you build the serving layer.

### How It Works

```
  FAISS Index Types (most useful):

  ┌─────────────────┬────────┬────────┬────────┬───────────┐
  │ Index           │ Speed  │ Recall │ Memory │ Use case  │
  ├─────────────────┼────────┼────────┼────────┼───────────┤
  │ Flat            │ Slow   │ 100%   │ High   │ <50K vecs │
  │ IVFFlat         │ Fast   │ ~97%   │ High   │ 50K-5M    │
  │ IVFPQ           │ V.Fast │ ~92%   │ Low    │ >5M vecs  │
  │ HNSW            │ V.Fast │ ~99%   │ V.High │ Low-lat.  │
  │ IVF+HNSW+PQ    │ V.Fast │ ~95%   │ Medium │ >10M vecs │
  └─────────────────┴────────┴────────┴────────┴───────────┘
```

### Recipe

```python
import numpy as np
import faiss

d = 128                                    # vector dimension
n = 100_000                                # number of vectors
vectors = np.random.randn(n, d).astype("float32")

# Build HNSW index
index = faiss.IndexHNSWFlat(d, 32)         # 32 connections per node
index.add(vectors)

# Search: find 5 nearest neighbors for a query
query = np.random.randn(1, d).astype("float32")
distances, indices = index.search(query, 5) # returns top-5
print(indices)                              # [[42, 7891, 234, 9102, 55]]
```

### When to Use

- You need maximum performance and control
- Building custom ML pipelines or research prototypes
- You're comfortable managing the index lifecycle yourself
- GPU acceleration needed (FAISS supports CUDA)

---

## 6. Dedicated Vector Databases

### The Intuition (Backend Analogy)

FAISS is like SQLite — a library. Dedicated vector databases are like PostgreSQL or
DynamoDB — full servers with APIs, replication, access control, and operational tooling.
They handle the infra so you focus on your application.

### Comparison

```
  Pinecone  → Managed-only, zero-ops, pay-per-use, scales to billions
  Weaviate  → ML-native (vectorize at insert), GraphQL API, great filtering
  Milvus    → Distributed/K8s, Go/C++, scales to billions
  Qdrant    → Rust, fast filtering, self-hosted control
  Chroma    → Python, embedded/self-host, best for prototyping (<1M vectors)
```
```

### Recipe — Qdrant (Self-Hosted)

```python
from qdrant_client import QdrantClient
from qdrant_client.models import VectorParams, Distance, PointStruct

client = QdrantClient("localhost", port=6333)
client.create_collection("docs", vectors_config=VectorParams(size=384, distance=Distance.COSINE))

# Upsert vectors with metadata (payload)
points = [PointStruct(id=i, vector=emb, payload={"title": t}) for i, (emb, t) in enumerate(data)]
client.upsert("docs", points)

# Search with metadata filter
results = client.search("docs", query_vector=q_vec, limit=5,
                         query_filter={"must": [{"key": "category", "match": {"value": "engineering"}}]})
```

### When to Use

- **Pinecone**: You want zero operational burden, pay-per-use pricing
- **Weaviate**: You want built-in ML modules (vectorize at insert time)
- **Milvus**: You need distributed scale on Kubernetes
- **Qdrant**: You want fast filtering with self-hosted control
- **Chroma**: You're prototyping or building a small RAG app

---

## 7. PostgreSQL + pgvector

### The Intuition (Backend Analogy)

Like adding full-text search to Postgres with `tsvector` and `tsquery`. It's not
Elasticsearch, but for most teams it's **good enough** — and you don't need another
database in your stack. Same idea: `pgvector` adds vector columns and similarity
indexes to the Postgres you already run.

### How It Works

```
  pgvector: Vector search inside your existing Postgres

  ┌─────────────────────────────────────────────────┐
  │  PostgreSQL                                     │
  │  ┌───────────────────────────────────────────┐  │
  │  │  products table                           │  │
  │  │  id │ name       │ price │ embedding      │  │
  │  │  1  │ "Widget A" │ 29.99│ [0.2,-0.1,...] │  │
  │  │  2  │ "Gadget B" │ 49.99│ [0.8, 0.3,...] │  │
  │  │  3  │ "Thing C"  │ 9.99 │ [0.1, 0.7,...] │  │
  │  └───────────────────────────────────────────┘  │
  │                                                 │
  │  CREATE INDEX ON products                       │
  │    USING hnsw (embedding vector_cosine_ops);    │
  │                                                 │
  │  Hybrid query:                                  │
  │    WHERE price < 50                             │
  │    ORDER BY embedding <=> query_vec             │
  │    LIMIT 10;                                    │
  │                                                 │
  │  ← Combines SQL filtering + vector similarity!  │
  └─────────────────────────────────────────────────┘
```

**Operators:** `<=>` cosine distance, `<->` L2 distance, `<#>` negative inner product.

### Recipe

```sql
-- Enable extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create table with a vector column
CREATE TABLE documents (
    id    SERIAL PRIMARY KEY,
    title TEXT,
    embedding vector(384)          -- 384-dimensional vector
);

-- Insert vectors
INSERT INTO documents (title, embedding)
VALUES ('ML Guide', '[0.1, -0.2, 0.3, ...]');

-- Create HNSW index for fast search
CREATE INDEX ON documents
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 200);

-- Query: find 5 most similar documents
SELECT id, title, embedding <=> '[0.2, -0.1, 0.4, ...]' AS distance
FROM documents
ORDER BY embedding <=> '[0.2, -0.1, 0.4, ...]'
LIMIT 5;
```

### When to Use

- You already run PostgreSQL (most teams do)
- Less than ~5M vectors
- You need hybrid queries (SQL filters + vector similarity)
- You want one fewer database to manage
- You value operational simplicity over peak performance

---

## 8. Integration Patterns

### RAG (Retrieval Augmented Generation)

The most common pattern. Instead of fine-tuning an LLM on your data, you **retrieve**
relevant context at query time and feed it to the LLM.

```
  OFFLINE: Documents → Chunk (500-1000 tokens) → Embed → Store in Vector DB
  ONLINE:  User question → Embed → Search nearest chunks → Feed to LLM → Answer
```

- **Semantic Search**: query → embed → vector DB → ranked results
- **Recommendation**: user/item embedding → nearest items
- **Deduplication**: embed → search within threshold → match = duplicate
- **Anomaly Detection**: embed → far from all clusters → anomaly

### Recipe — Minimal RAG with LangChain

```python
from langchain_community.vectorstores import FAISS
from langchain_openai import OpenAIEmbeddings, ChatOpenAI
from langchain.chains import RetrievalQA
from langchain_community.document_loaders import TextLoader

docs = TextLoader("company_docs.txt").load()
vectorstore = FAISS.from_documents(docs, OpenAIEmbeddings())
qa = RetrievalQA.from_chain_type(llm=ChatOpenAI(), retriever=vectorstore.as_retriever())

answer = qa.invoke("What is our refund policy?")
print(answer["result"])
```

### When to Use Each Pattern

- **RAG**: You want an LLM to answer questions about your data
- **Semantic Search**: User-facing search beyond keyword matching
- **Recommendation**: "More like this" or personalized feeds
- **Deduplication**: Content pipelines, data cleaning

---

## 9. Performance Tuning

### The Intuition (Backend Analogy)

Same principles as database tuning — right index type, right data types, batch
operations, and understanding the read/write tradeoff. The knobs are different,
but the thinking is identical.

### Key Levers

```
  DIMENSION REDUCTION:
  768-dim (BERT) → PCA/Matryoshka 256-dim → ~3× faster, ~3× less memory

  QUANTIZATION:
  float32 (4B/dim) → float16 (2B) → int8 (1B) → binary (1bit)
  At 100M × 768-dim: 300GB → 150GB → 75GB → 9.6GB
```

**Pre-filter**: apply filter first, then ANN on filtered subset (✓ exact, ✗ index rebuild).
**Post-filter**: ANN first, then filter results (✓ uses index, ✗ may return < K results).

### Tuning Checklist

```
  ┌─────────────────────────────────────────────────────────────┐
  │  Performance Tuning Checklist                               │
  ├─────────────────────────────────────────────────────────────┤
  │  □ Reduce dimensions (PCA, Matryoshka) if recall is OK     │
  │  □ Quantize vectors (float16 or int8) for memory savings   │
  │  □ Batch inserts (1000+ at a time, not one-by-one)         │
  │  □ Tune nprobe (IVF) or efSearch (HNSW) for recall/speed   │
  │  □ Pick index type: HNSW for latency, IVF for throughput   │
  │  □ Pre-compute and cache frequent queries                  │
  │  □ Use metadata pre-filtering when filter selectivity >50% │
  │  □ Monitor recall with a ground-truth test set              │
  └─────────────────────────────────────────────────────────────┘
```

### When to Tune

- Latency exceeds your SLA (p99 > 50ms)
- Memory costs are too high for your cluster
- Recall drops below acceptable threshold (~95%)
- Insert throughput can't keep up with your data pipeline

---

## 10. Choosing the Right Solution

### Decision Framework

```
  START HERE
     │
     ▼
  How many vectors?
     │
     ├─── < 100K ──────────→ FAISS Flat or pgvector (exact search is fine)
     │
     ├─── 100K - 10M ──┐
     │                  ├──→ Already using Postgres? → pgvector + HNSW
     │                  ├──→ Need rich filtering?    → Qdrant or Weaviate
     │                  └──→ Want zero ops?          → Pinecone
     │
     └─── > 10M ───────┐
                        ├──→ Max performance?        → FAISS IVF+PQ (+ GPU)
                        ├──→ Distributed/K8s?        → Milvus
                        └──→ Fully managed?          → Pinecone
```

### Quick Reference

```
  ┌──────────────────────┬────────────────────────────────────┐
  │ Situation            │ Recommendation                     │
  ├──────────────────────┼────────────────────────────────────┤
  │ Prototyping / POC    │ Chroma or FAISS (in-memory)        │
  │ Small + Postgres     │ pgvector (path of least resistance)│
  │ Production + managed │ Pinecone (zero ops)                │
  │ Production + control │ Qdrant or Weaviate (self-hosted)   │
  │ Massive scale        │ Milvus or FAISS + custom infra     │
  │ Research / benchmarks│ FAISS (most flexible)              │
  │ Tight budget         │ pgvector or Qdrant (open source)   │
  └──────────────────────┴────────────────────────────────────┘

  Remember: Start simple. pgvector or FAISS Flat covers 80% of use cases.
  Don't over-engineer — you can always migrate later.
```

### When to Migrate

- pgvector → dedicated DB: when queries exceed ~50ms p99 at scale
- FAISS → managed DB: when you're spending more time on infra than features
- Single node → distributed: when data won't fit in one machine's RAM

---

## Exercises

### Exercise 1: Conceptual

**Question**: Explain why approximate nearest neighbor search is acceptable for most
ML applications. When would you NOT want to use ANN?

<details>
<summary>Solution</summary>

ANN is acceptable because embeddings are already approximate, 95-99% recall is fine
for fuzzy similarity, and the 100-1000× speedup is massive. Avoid ANN only when exact
results are legally required, datasets are small enough for brute force (<50K), or
you need ground-truth evaluation.

</details>

---

### Exercise 2: Implementation

**Question**: Build a FAISS index with 10,000 random 128-dim vectors. Query with a
random vector and return the top-5 nearest neighbors with distances. Max 8 lines.

<details>
<summary>Solution</summary>

```python
import numpy as np
import faiss

vectors = np.random.randn(10_000, 128).astype("float32")
index = faiss.IndexFlatL2(128)
index.add(vectors)

query = np.random.randn(1, 128).astype("float32")
distances, indices = index.search(query, 5)
print(f"Nearest IDs: {indices[0]}, Distances: {distances[0]}")
```

Output:
```
Nearest IDs: [4821 7293  312 9044 1567], Distances: [18.42 19.01 19.23 19.38 19.51]
```

Note: distances are L2 (squared Euclidean). For cosine similarity, normalize vectors
first with `faiss.normalize_L2(vectors)` and use `IndexFlatIP` (inner product).

</details>

---

<details>
<summary>Solution</summary>

**pgvector with HNSW index** — you already run Postgres, 500K docs ≈ 2-5M chunks
(within pgvector's sweet spot). Use `text-embedding-3-small` or `all-MiniLM-L6-v2`.
Killer feature: hybrid SQL + vector queries (`WHERE dept='eng' ORDER BY embedding <=> $q`).
Migrate to a dedicated vector DB only if you hit performance limits.

</details>

---

## Key Takeaways

```
  ┌─────────────────────────────────────────────────────────────┐
  │  Vector Database Cheat Sheet                                │
  ├─────────────────────────────────────────────────────────────┤
  │                                                             │
  │  1. HNSW is the default ANN algorithm — start there        │
  │  2. pgvector is the pragmatic default for most teams        │
  │  3. FAISS for max performance or research                   │
  │  4. Dedicated DBs when you need scale or managed service    │
  │  5. RAG is the #1 use case driving vector DB adoption       │
  │  6. Start exact (Flat), add ANN only when you need speed    │
  │  7. Dimension reduction and quantization are free wins      │
  │  8. Batch your inserts — always                             │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```
