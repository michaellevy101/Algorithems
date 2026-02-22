# Day 5 — RAG + Vector Memory for Agents

> **🎯 Goal**: Integrate long-term knowledge into the agent.

> **⏱ Time**: 1.5h theory + 3h practice

> **📦 Deliverable**: Agent with working RAG + dynamic, long-term memory

---

## Table of Contents

1. [Why RAG?](#why-rag)
2. [Vector Retrieval Fundamentals](#vector-retrieval-fundamentals)
3. [Chunking Strategies](#chunking-strategies)
4. [Embedding Models](#embedding-models)
5. [Hybrid Search](#hybrid-search)
6. [Context Window Management](#context-window-management)
7. [Practice: Build a RAG Pipeline](#practice-build-a-rag-pipeline)

---

## Why RAG?

**RAG** = Retrieval-Augmented Generation. Instead of relying on the LLM's training data (which is frozen at a point in time), RAG lets the model **look up** information from external documents before answering.

```
Without RAG:
  Q: "What was our Q4 2024 revenue?"
  A: "I don't have access to your company's financial data."
      (or worse, hallucinated: "Your Q4 revenue was $12.5M")

With RAG:
  Q: "What was our Q4 2024 revenue?"
  
  1. RETRIEVE: Search company documents → find Q4 report
  2. AUGMENT:  Inject relevant chunks into the prompt
  3. GENERATE: LLM answers using the retrieved context
  
  A: "According to the Q4 2024 financial report, revenue was
      $18.3M, up 15% from Q3." ← Grounded in real data

┌─────────────────────────────────────────────────────────────┐
│                     RAG PIPELINE                             │
│                                                              │
│  User Question                                              │
│      │                                                       │
│      ▼                                                       │
│  ┌──────────────┐                                           │
│  │   EMBED      │  → Convert question to vector             │
│  │   Question   │                                           │
│  └──────┬───────┘                                           │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────┐                                           │
│  │   RETRIEVE   │  → Find similar chunks in vector DB       │
│  │   from DB    │     (cosine similarity)                   │
│  └──────┬───────┘                                           │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────┐                                           │
│  │   AUGMENT    │  → Inject chunks into prompt              │
│  │   Prompt     │     "Given this context: {chunks}..."     │
│  └──────┬───────┘                                           │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────┐                                           │
│  │   GENERATE   │  → LLM generates answer                  │
│  │   Answer     │     grounded in retrieved context         │
│  └──────────────┘                                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### RAG vs Fine-Tuning

```
┌──────────────────┬───────────────────────┬──────────────────────┐
│                  │ RAG                    │ Fine-Tuning          │
├──────────────────┼───────────────────────┼──────────────────────┤
│ Data freshness   │ Real-time updates     │ Frozen at training   │
│ Cost             │ Low (just embeddings) │ High (GPU training)  │
│ Setup time       │ Hours                 │ Days-weeks           │
│ Accuracy         │ High (grounded)       │ Variable             │
│ Hallucinations   │ Lower (has source)    │ Can still hallucinate│
│ Data volume      │ Any amount            │ Needs curated dataset│
│ Use case         │ Knowledge retrieval   │ Behavior change      │
│ Updatable        │ Add/remove docs live  │ Retrain required     │
└──────────────────┴───────────────────────┴──────────────────────┘

When to use RAG:
  ✓ Company documents, knowledge bases
  ✓ Data that changes frequently
  ✓ When you need source citations
  ✓ Multi-domain knowledge

When to use Fine-Tuning:
  ✓ Specific output style/format
  ✓ Domain-specific reasoning (medical, legal)
  ✓ When RAG context window is insufficient
```

---

## Vector Retrieval Fundamentals

### What Are Embeddings?

Embeddings convert text into **dense numerical vectors** that capture semantic meaning. Similar texts → similar vectors.

```
Text: "The cat sat on the mat"     → [0.23, -0.15, 0.87, 0.42, ...]
Text: "A kitten rested on the rug" → [0.21, -0.13, 0.85, 0.39, ...]
                                      ↑ Very similar! (synonyms)

Text: "Stock prices rose sharply"   → [-0.45, 0.72, -0.11, 0.33, ...]
                                      ↑ Very different (different topic)

Key properties:
  - Dimension: typically 384–1536 floats
  - Semantic similarity → vector proximity
  - Language-agnostic (similar concepts in different languages map closely)
```

### Cosine Similarity

The standard measure for comparing embeddings:

```
                    A · B         Σ(Ai × Bi)
cos(θ) = ─────────────────── = ─────────────────────
           ||A|| × ||B||      √Σ(Ai²) × √Σ(Bi²)

Values:
   1.0 = identical meaning
   0.0 = completely unrelated
  -1.0 = opposite meaning (rare in practice)

Example:
  A = [0.23, -0.15, 0.87]   ("cat on mat")
  B = [0.21, -0.13, 0.85]   ("kitten on rug")
  
  A·B = (0.23×0.21) + (-0.15×-0.13) + (0.87×0.85) = 0.0483 + 0.0195 + 0.7395 = 0.8073
  ||A|| = √(0.0529 + 0.0225 + 0.7569) = √0.8323 = 0.9123
  ||B|| = √(0.0441 + 0.0169 + 0.7225) = √0.7835 = 0.8852
  
  cos(θ) = 0.8073 / (0.9123 × 0.8852) = 0.8073 / 0.8076 = 0.9996
  
  Very similar! ✓
```

### Approximate Nearest Neighbor (ANN) Algorithms

Exact nearest-neighbor search over millions of vectors is too slow (O(n)). ANN algorithms trade slight accuracy for massive speed gains:

```
┌────────────────┬──────────────────────────────────────────────┐
│ Algorithm      │ How It Works                                  │
├────────────────┼──────────────────────────────────────────────┤
│ HNSW           │ Hierarchical graph — traverse layers from    │
│ (Hierarchical  │ coarse to fine. Most popular for in-memory.  │
│ Navigable      │ Used by: ChromaDB, Qdrant, pgvector          │
│ Small World)   │ Speed: ~O(log n)                             │
├────────────────┼──────────────────────────────────────────────┤
│ IVF            │ Clusters vectors into cells. Search only     │
│ (Inverted      │ nearby cells. Good for very large datasets.  │
│ File Index)    │ Used by: FAISS                               │
│                │ Speed: ~O(√n)                                │
├────────────────┼──────────────────────────────────────────────┤
│ Product        │ Compresses vectors by splitting into         │
│ Quantization   │ sub-vectors and quantizing. Saves memory.    │
│ (PQ)           │ Often combined with IVF.                     │
│                │ Used by: FAISS (IVF-PQ)                      │
└────────────────┴──────────────────────────────────────────────┘

For most use cases (< 10M vectors), HNSW is the best choice.
```

---

## Chunking Strategies

Before embedding documents, you must split them into **chunks** — smaller pieces that fit the embedding model's context window and provide focused, retrievable units.

### Why Chunking Matters

```
Problem: Your document is 50 pages long.
  - Can't embed the whole thing (embedding models have limits: 512-8K tokens)
  - Even if you could, one giant vector wouldn't be specific enough
  - Retrieving the whole doc wastes context window on irrelevant parts

Solution: Split into chunks, embed each separately.
  50-page doc → 200 chunks → 200 vectors
  Query matches the 3 most relevant chunks → focused, specific context
```

### Strategy 1: Fixed-Size Chunking

```
Split text into chunks of N tokens/characters with overlap.

Parameters:
  chunk_size: 500 tokens
  overlap: 50 tokens (10% overlap between consecutive chunks)

Document: "AAAA BBBB CCCC DDDD EEEE FFFF GGGG HHHH IIII JJJJ"

Chunk 1: "AAAA BBBB CCCC DDDD EEEE"
Chunk 2: "DDDD EEEE FFFF GGGG HHHH"   ← overlaps with chunk 1
Chunk 3: "GGGG HHHH IIII JJJJ"         ← overlaps with chunk 2

Why overlap?
  Without overlap, a sentence split across two chunks is lost.
  "The deadline is December 31" → "The deadline is" | "December 31"
  Neither chunk has the full information!
  With overlap, the full sentence appears in at least one chunk.

Pros: Simple, consistent chunk sizes
Cons: May split mid-sentence, mid-paragraph, mid-thought
```

### Strategy 2: Recursive Character Splitting

```
Split by natural boundaries, recursively trying smaller separators.

Separator hierarchy:
  1. "\n\n" (paragraph boundaries)
  2. "\n" (line boundaries)
  3. ". " (sentence boundaries)
  4. " " (word boundaries)
  5. "" (character-level, last resort)

Algorithm:
  1. Try splitting by "\n\n"
  2. If any chunk > max_size, split that chunk by "\n"
  3. If still > max_size, split by ". "
  4. Continue down the hierarchy

Result: Chunks respect natural text boundaries
  Paragraph-level chunks when possible
  Sentence-level when paragraphs are too long

Pros: Preserves semantic units
Cons: Variable chunk sizes
```

### Strategy 3: Semantic Chunking

```
Split based on topic/meaning changes, not character boundaries.

Algorithm:
  1. Split into sentences
  2. Embed each sentence
  3. Compare adjacent sentence embeddings
  4. When similarity drops below threshold → new chunk

Sentences:  S1    S2    S3    S4    S5    S6    S7    S8
Similarity:    0.9   0.85  0.82  0.3   0.88  0.91  0.4
                                  ↑                   ↑
                              Topic change!       Topic change!

Chunks: [S1,S2,S3,S4] | [S5,S6,S7] | [S8]

Pros: Best semantic coherence
Cons: Requires embedding each sentence (slow, expensive)
      Variable chunk sizes
```

### Chunking Comparison

```
┌──────────────┬────────────┬────────────┬──────────────┬───────────┐
│ Strategy     │ Quality    │ Speed      │ Consistency  │ Best For  │
├──────────────┼────────────┼────────────┼──────────────┼───────────┤
│ Fixed-size   │ Medium     │ Very fast  │ Uniform size │ Large vol │
│ Recursive    │ Good       │ Fast       │ Variable     │ General   │
│ Semantic     │ Best       │ Slow       │ Variable     │ Quality   │
└──────────────┴────────────┴────────────┴──────────────┴───────────┘

Recommended defaults:
  chunk_size: 500 tokens (~2000 characters)
  overlap: 50-100 tokens (10-20%)
  strategy: Recursive (best balance)
```

---

## Embedding Models

### Popular Embedding Models

```
┌──────────────────────────┬──────┬──────┬────────────────────────┐
│ Model                    │ Dims │ Max  │ Notes                  │
│                          │      │ Tok  │                        │
├──────────────────────────┼──────┼──────┼────────────────────────┤
│ text-embedding-3-small   │ 1536 │ 8191 │ OpenAI, cheap, fast    │
│ text-embedding-3-large   │ 3072 │ 8191 │ OpenAI, best quality   │
│ text-embedding-ada-002   │ 1536 │ 8191 │ OpenAI, legacy         │
│ all-MiniLM-L6-v2         │ 384  │ 256  │ Free, local, fast      │
│ all-mpnet-base-v2        │ 768  │ 512  │ Free, local, better    │
│ bge-large-en-v1.5        │ 1024 │ 512  │ Free, local, top tier  │
│ nomic-embed-text-v1.5    │ 768  │ 8192 │ Free, long context     │
│ Cohere embed-english-v3  │ 1024 │ 512  │ API, good quality      │
└──────────────────────────┴──────┴──────┴────────────────────────┘

Choosing a model:
  - Need free/local?  → all-MiniLM-L6-v2 (fastest) or bge-large (best)
  - Need API?         → text-embedding-3-small (best value)
  - Need max quality? → text-embedding-3-large
  - Need long chunks? → nomic-embed-text-v1.5
```

### Embedding Code Example

```python
# Option A: OpenAI embeddings
import openai

def embed_openai(texts: list[str], model="text-embedding-3-small") -> list:
    """Embed texts using OpenAI API."""
    response = openai.embeddings.create(
        model=model,
        input=texts
    )
    return [item.embedding for item in response.data]

# Option B: Local embeddings (free, no API key)
# pip install sentence-transformers
from sentence_transformers import SentenceTransformer

model = SentenceTransformer('all-MiniLM-L6-v2')

def embed_local(texts: list[str]) -> list:
    """Embed texts using local model."""
    embeddings = model.encode(texts, normalize_embeddings=True)
    return embeddings.tolist()

# Compare:
text = "How does vector search work?"
# OpenAI: 1536-dim vector, ~$0.00002 per text
# Local:  384-dim vector, free, ~10ms per text
```

---

## Hybrid Search

Pure vector search misses exact keyword matches. Pure keyword search misses semantic similarity. **Hybrid search** combines both.

### BM25 + Vector Search

```
┌─────────────────────────────────────────────────────────────┐
│                    HYBRID SEARCH                             │
│                                                              │
│  Query: "RLHF training for language models"                 │
│      │                                                       │
│      ├──────────────────┐                                   │
│      ▼                  ▼                                   │
│  ┌──────────┐    ┌──────────────┐                           │
│  │  BM25    │    │   VECTOR     │                           │
│  │ (keyword)│    │  (semantic)  │                           │
│  └────┬─────┘    └──────┬───────┘                           │
│       │                 │                                    │
│       ▼                 ▼                                    │
│  Results A:        Results B:                                │
│  1. "RLHF paper"   1. "Human feedback in training"          │
│  2. "RLHF impl"    2. "Preference learning"                 │
│  3. "PPO for LM"   3. "Reward model design"                 │
│       │                 │                                    │
│       └────────┬────────┘                                   │
│                ▼                                             │
│         ┌───────────┐                                       │
│         │  COMBINE  │  ← Reciprocal Rank Fusion (RRF)       │
│         │  & RERANK │     or weighted sum                    │
│         └─────┬─────┘                                       │
│               ▼                                              │
│  Final Results:                                              │
│  1. "RLHF paper"             (matched both!)                │
│  2. "Human feedback in ..."  (semantic match)                │
│  3. "RLHF implementation"   (keyword match)                 │
│  4. "Preference learning"    (semantic match)                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### BM25 Explained

```
BM25 is the classic information retrieval algorithm (used by Elasticsearch, 
Lucene, etc.). It scores documents based on term frequency and rarity.

Score(D, Q) = Σ IDF(qi) × (f(qi, D) × (k1 + 1)) / (f(qi, D) + k1 × (1 - b + b × |D|/avgdl))

Where:
  IDF(qi) = log((N - n(qi) + 0.5) / (n(qi) + 0.5))  ← How rare is this word?
  f(qi, D) = frequency of word qi in document D
  |D| = document length
  avgdl = average document length
  k1 = 1.2 (tuning parameter, controls term frequency saturation)
  b = 0.75 (tuning parameter, controls length normalization)

In plain English:
  "Score higher if the document contains rare query words,
   but don't over-reward having the same word many times,
   and penalize very long documents slightly."

BM25 is great at:
  ✓ Exact keyword matches ("RLHF", "Kubernetes", error codes)
  ✓ Rare/specific terms (product names, acronyms)
  ✓ Fast and well-understood

BM25 is bad at:
  ✗ Synonyms ("car" won't match "automobile")
  ✗ Paraphrases ("how to cook" won't match "recipe preparation")
  ✗ Conceptual similarity
```

### Reciprocal Rank Fusion (RRF)

```
Combines rankings from multiple search methods:

RRF_score(d) = Σ 1 / (k + rank_i(d))

Where k = 60 (constant, prevents outlier dominance)

Example:
  Document "RLHF paper":
    BM25 rank: 1,  Vector rank: 4
    RRF = 1/(60+1) + 1/(60+4) = 0.0164 + 0.0156 = 0.0320

  Document "Preference learning":
    BM25 rank: 8,  Vector rank: 2
    RRF = 1/(60+8) + 1/(60+2) = 0.0147 + 0.0161 = 0.0308

  "RLHF paper" scores higher because it ranks well in BOTH methods.
```

---

## Context Window Management

The retrieved chunks must fit in the LLM's context window along with the system prompt, user query, and room for the response.

```
┌─────────────────────────────────────────────────────────┐
│              CONTEXT WINDOW BUDGET                       │
│                                                          │
│  Total context: 8,192 tokens (Llama 3 8B example)       │
│                                                          │
│  ┌──────────────────────────┐                           │
│  │ System prompt: ~200 tok   │                           │
│  ├──────────────────────────┤                           │
│  │ Retrieved chunks:         │                           │
│  │   Chunk 1: ~500 tok       │                           │
│  │   Chunk 2: ~500 tok       │  ← RAG context           │
│  │   Chunk 3: ~500 tok       │    (~1500 tokens)        │
│  ├──────────────────────────┤                           │
│  │ User query: ~50 tok       │                           │
│  ├──────────────────────────┤                           │
│  │ Previous conversation:    │                           │
│  │   ~1000 tok               │                           │
│  ├──────────────────────────┤                           │
│  │ Reserved for response:    │                           │
│  │   ~1000 tok               │                           │
│  ├──────────────────────────┤                           │
│  │ REMAINING: ~3942 tokens   │  ← Buffer / more chunks  │
│  └──────────────────────────┘                           │
│                                                          │
│  Rule of thumb:                                          │
│    RAG chunks ≤ 50% of context window                   │
│    Leave 20% for response generation                     │
│    Leave 10% buffer for safety                          │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### Strategies for Managing Context

```
1. LIMIT CHUNKS: Retrieve only top-K (e.g., K=3-5)
   More chunks = more context but also more noise

2. RERANK: Retrieve top-20, rerank with a cross-encoder,
   keep top-5. Better quality than just top-5.

3. SUMMARIZE: If chunks are long, summarize them before
   injecting. Trades detail for fitting more information.

4. MAP-REDUCE: For very large retrievals:
   - Map: Ask LLM to extract relevant info from each chunk
   - Reduce: Combine extractions into final answer
   
5. SLIDING WINDOW: For conversation history:
   - Keep last N turns
   - Summarize older turns into a compact paragraph
```

---

## Practice: Build a RAG Pipeline

### Complete RAG Implementation

```python
"""
RAG Pipeline — Complete implementation with chunking, 
embedding, retrieval, and grounding evaluation.

Architecture:
  Documents → Chunker → Embedder → Vector Store
                                        │
  User Query → Embedder → Similarity Search → Top-K Chunks
                                                    │
                                              Prompt Builder → LLM → Answer
                                                    │
                                              Grounding Evaluator → Score
"""
import json
import math
import re
import hashlib
from dataclasses import dataclass, field
from typing import Optional


# ═══════════════════════════════════════════════════════════════
# SECTION 1: CHUNKING
# ═══════════════════════════════════════════════════════════════

@dataclass
class Chunk:
    """A chunk of text with metadata."""
    id: str
    text: str
    source: str  # Document name/path
    index: int   # Position in document
    metadata: dict = field(default_factory=dict)
    embedding: list = field(default_factory=list)


class RecursiveChunker:
    """
    Splits text into chunks using recursive character splitting.
    
    Tries to split at natural boundaries:
    1. Double newlines (paragraphs)
    2. Single newlines
    3. Sentences (periods)
    4. Words (spaces)
    """
    
    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 50):
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        self.separators = ["\n\n", "\n", ". ", " ", ""]
    
    def chunk_text(self, text: str, source: str = "unknown") -> list:
        """Split text into overlapping chunks."""
        raw_chunks = self._split_recursive(text, self.separators)
        
        # Merge small chunks and add overlap
        merged = self._merge_with_overlap(raw_chunks)
        
        chunks = []
        for i, chunk_text in enumerate(merged):
            chunk_id = hashlib.md5(
                f"{source}:{i}:{chunk_text[:50]}".encode()
            ).hexdigest()[:12]
            
            chunks.append(Chunk(
                id=chunk_id,
                text=chunk_text.strip(),
                source=source,
                index=i,
                metadata={
                    "char_count": len(chunk_text),
                    "word_count": len(chunk_text.split())
                }
            ))
        
        return chunks
    
    def _split_recursive(self, text: str, separators: list) -> list:
        """Recursively split text using separator hierarchy."""
        if not separators:
            return [text]
        
        sep = separators[0]
        remaining_seps = separators[1:]
        
        if sep == "":
            # Character-level split as last resort
            return [text[i:i+self.chunk_size] 
                    for i in range(0, len(text), self.chunk_size)]
        
        parts = text.split(sep)
        
        result = []
        current = ""
        
        for part in parts:
            candidate = current + sep + part if current else part
            
            if len(candidate) <= self.chunk_size:
                current = candidate
            else:
                if current:
                    result.append(current)
                
                if len(part) > self.chunk_size:
                    # This part is too big — split with next separator
                    sub_parts = self._split_recursive(part, remaining_seps)
                    result.extend(sub_parts)
                    current = ""
                else:
                    current = part
        
        if current:
            result.append(current)
        
        return result
    
    def _merge_with_overlap(self, chunks: list) -> list:
        """Add overlap between consecutive chunks."""
        if len(chunks) <= 1:
            return chunks
        
        result = []
        for i, chunk in enumerate(chunks):
            if i > 0 and self.chunk_overlap > 0:
                # Get overlap from end of previous chunk
                prev = chunks[i-1]
                overlap_text = prev[-self.chunk_overlap:]
                # Find word boundary
                space_idx = overlap_text.find(' ')
                if space_idx > 0:
                    overlap_text = overlap_text[space_idx+1:]
                chunk = overlap_text + " " + chunk
            
            result.append(chunk)
        
        return result


# ═══════════════════════════════════════════════════════════════
# SECTION 2: VECTOR STORE (In-Memory)
# ═══════════════════════════════════════════════════════════════

class SimpleVectorStore:
    """
    A minimal in-memory vector store.
    
    Uses brute-force cosine similarity (fine for < 100K vectors).
    In production, use ChromaDB, Pinecone, FAISS, or pgvector.
    """
    
    def __init__(self):
        self.chunks: list[Chunk] = []
    
    def add(self, chunks: list):
        """Add chunks with embeddings to the store."""
        for chunk in chunks:
            if not chunk.embedding:
                raise ValueError(f"Chunk {chunk.id} has no embedding")
            self.chunks.append(chunk)
    
    def search(self, query_embedding: list, top_k: int = 5) -> list:
        """Find the top-K most similar chunks."""
        if not self.chunks:
            return []
        
        scored = []
        for chunk in self.chunks:
            score = self._cosine_similarity(query_embedding, chunk.embedding)
            scored.append((chunk, score))
        
        scored.sort(key=lambda x: x[1], reverse=True)
        return scored[:top_k]
    
    def _cosine_similarity(self, a: list, b: list) -> float:
        """Compute cosine similarity between two vectors."""
        dot_product = sum(x * y for x, y in zip(a, b))
        norm_a = math.sqrt(sum(x * x for x in a))
        norm_b = math.sqrt(sum(x * x for x in b))
        
        if norm_a == 0 or norm_b == 0:
            return 0.0
        
        return dot_product / (norm_a * norm_b)
    
    def __len__(self):
        return len(self.chunks)


# ═══════════════════════════════════════════════════════════════
# SECTION 3: BM25 (KEYWORD SEARCH)
# ═══════════════════════════════════════════════════════════════

class SimpleBM25:
    """
    Simple BM25 implementation for keyword-based retrieval.
    """
    
    def __init__(self, k1: float = 1.2, b: float = 0.75):
        self.k1 = k1
        self.b = b
        self.docs = []       # List of tokenized documents
        self.doc_chunks = [] # Corresponding chunks
        self.avgdl = 0       # Average document length
        self.idf = {}        # Inverse document frequency
        self.doc_count = 0
    
    def add(self, chunks: list):
        """Index chunks for BM25 search."""
        for chunk in chunks:
            tokens = self._tokenize(chunk.text)
            self.docs.append(tokens)
            self.doc_chunks.append(chunk)
        
        self.doc_count = len(self.docs)
        self.avgdl = sum(len(d) for d in self.docs) / max(self.doc_count, 1)
        self._compute_idf()
    
    def search(self, query: str, top_k: int = 5) -> list:
        """Search for documents matching the query."""
        query_tokens = self._tokenize(query)
        
        scores = []
        for i, doc_tokens in enumerate(self.docs):
            score = self._score(query_tokens, doc_tokens)
            scores.append((self.doc_chunks[i], score))
        
        scores.sort(key=lambda x: x[1], reverse=True)
        return scores[:top_k]
    
    def _tokenize(self, text: str) -> list:
        """Simple tokenization."""
        return re.findall(r'\w+', text.lower())
    
    def _compute_idf(self):
        """Compute IDF for all terms."""
        df = {}  # Document frequency
        for doc in self.docs:
            unique_terms = set(doc)
            for term in unique_terms:
                df[term] = df.get(term, 0) + 1
        
        for term, freq in df.items():
            self.idf[term] = math.log(
                (self.doc_count - freq + 0.5) / (freq + 0.5) + 1
            )
    
    def _score(self, query_tokens: list, doc_tokens: list) -> float:
        """Compute BM25 score for a document."""
        score = 0.0
        doc_len = len(doc_tokens)
        
        # Count term frequencies in document
        tf = {}
        for token in doc_tokens:
            tf[token] = tf.get(token, 0) + 1
        
        for term in query_tokens:
            if term not in self.idf:
                continue
            
            term_freq = tf.get(term, 0)
            idf = self.idf[term]
            
            numerator = term_freq * (self.k1 + 1)
            denominator = term_freq + self.k1 * (
                1 - self.b + self.b * doc_len / max(self.avgdl, 1)
            )
            
            score += idf * numerator / denominator
        
        return score


# ═══════════════════════════════════════════════════════════════
# SECTION 4: EMBEDDING (Local, No API Needed)
# ═══════════════════════════════════════════════════════════════

class SimpleEmbedder:
    """
    A simple TF-IDF based embedder for practice.
    No API key needed. In production, use OpenAI or sentence-transformers.
    
    This creates sparse embeddings using word frequencies — not as good
    as neural embeddings, but demonstrates the concept perfectly.
    """
    
    def __init__(self, dim: int = 256):
        self.dim = dim
        self.vocabulary = {}
        self.idf = {}
        self.fitted = False
    
    def fit(self, texts: list):
        """Build vocabulary from a corpus of texts."""
        # Count document frequency for each word
        df = {}
        n_docs = len(texts)
        
        all_words = set()
        for text in texts:
            words = set(re.findall(r'\w+', text.lower()))
            all_words.update(words)
            for word in words:
                df[word] = df.get(word, 0) + 1
        
        # Keep top N words by document frequency
        sorted_words = sorted(df.items(), key=lambda x: x[1], reverse=True)
        for i, (word, freq) in enumerate(sorted_words[:self.dim]):
            self.vocabulary[word] = i
            self.idf[word] = math.log(n_docs / (freq + 1)) + 1
        
        self.fitted = True
    
    def embed(self, text: str) -> list:
        """Create an embedding vector for text."""
        if not self.fitted:
            raise ValueError("Embedder not fitted. Call fit() first.")
        
        words = re.findall(r'\w+', text.lower())
        word_counts = {}
        for w in words:
            word_counts[w] = word_counts.get(w, 0) + 1
        
        vector = [0.0] * self.dim
        for word, count in word_counts.items():
            if word in self.vocabulary:
                idx = self.vocabulary[word]
                tf = count / max(len(words), 1)
                vector[idx] = tf * self.idf.get(word, 1.0)
        
        # L2 normalize
        norm = math.sqrt(sum(x*x for x in vector))
        if norm > 0:
            vector = [x / norm for x in vector]
        
        return vector
    
    def embed_batch(self, texts: list) -> list:
        """Embed multiple texts."""
        return [self.embed(text) for text in texts]


# ═══════════════════════════════════════════════════════════════
# SECTION 5: RAG PIPELINE
# ═══════════════════════════════════════════════════════════════

class RAGPipeline:
    """
    Complete RAG pipeline with hybrid search and grounding evaluation.
    
    ┌─────────────────────────────────────────────────────────┐
    │                    RAG Pipeline                          │
    │                                                          │
    │  Ingestion:                                             │
    │    Documents → Chunker → Embedder → Vector Store        │
    │                                   → BM25 Index          │
    │                                                          │
    │  Query:                                                  │
    │    Question → Embedder → Vector Search ─┐               │
    │            → BM25 Search ───────────────┤               │
    │                                          ▼               │
    │                                    Hybrid Merge          │
    │                                          │               │
    │                                    Build Prompt          │
    │                                          │               │
    │                                    Generate Answer       │
    │                                          │               │
    │                                    Evaluate Grounding    │
    │                                          │               │
    │                                    Return Result         │
    │                                                          │
    └─────────────────────────────────────────────────────────┘
    """
    
    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 50,
                 top_k: int = 5, hybrid_alpha: float = 0.5):
        """
        Args:
            chunk_size: Maximum chunk size in characters
            chunk_overlap: Overlap between chunks
            top_k: Number of chunks to retrieve
            hybrid_alpha: Balance between vector (1.0) and BM25 (0.0)
        """
        self.chunker = RecursiveChunker(chunk_size, chunk_overlap)
        self.vector_store = SimpleVectorStore()
        self.bm25 = SimpleBM25()
        self.embedder = SimpleEmbedder()
        self.top_k = top_k
        self.hybrid_alpha = hybrid_alpha
        self.all_chunks = []
    
    def ingest(self, documents: dict):
        """
        Ingest documents into the RAG pipeline.
        
        Args:
            documents: dict of {name: text} pairs
        """
        print(f"📥 Ingesting {len(documents)} documents...")
        
        # Chunk all documents
        all_chunks = []
        for name, text in documents.items():
            chunks = self.chunker.chunk_text(text, source=name)
            all_chunks.extend(chunks)
            print(f"  📄 {name}: {len(chunks)} chunks")
        
        self.all_chunks = all_chunks
        
        # Fit embedder on all chunk texts
        all_texts = [c.text for c in all_chunks]
        self.embedder.fit(all_texts)
        
        # Embed all chunks
        for chunk in all_chunks:
            chunk.embedding = self.embedder.embed(chunk.text)
        
        # Add to stores
        self.vector_store.add(all_chunks)
        self.bm25.add(all_chunks)
        
        print(f"✅ Ingested {len(all_chunks)} total chunks")
        print(f"   Vector store: {len(self.vector_store)} vectors")
    
    def query(self, question: str, verbose: bool = True) -> dict:
        """
        Query the RAG pipeline.
        
        Returns:
            dict with: answer, chunks, scores, grounding_score
        """
        if verbose:
            print(f"\n🔍 Query: {question}")
        
        # Step 1: Retrieve relevant chunks
        retrieved = self._hybrid_search(question)
        
        if verbose:
            print(f"\n📚 Retrieved {len(retrieved)} chunks:")
            for chunk, score in retrieved:
                preview = chunk.text[:80].replace('\n', ' ')
                print(f"  [{score:.3f}] ({chunk.source}) {preview}...")
        
        # Step 2: Build prompt with context
        context = "\n\n---\n\n".join(
            f"[Source: {chunk.source}]\n{chunk.text}" 
            for chunk, _ in retrieved
        )
        
        prompt = self._build_prompt(question, context)
        
        # Step 3: Generate answer (simulated without API)
        answer = self._generate_answer(question, retrieved)
        
        # Step 4: Evaluate grounding
        grounding_score = self._evaluate_grounding(answer, retrieved)
        
        if verbose:
            print(f"\n💬 Answer: {answer}")
            print(f"\n📊 Grounding score: {grounding_score:.2f}")
        
        return {
            "question": question,
            "answer": answer,
            "chunks": [(c.text, c.source, s) for c, s in retrieved],
            "grounding_score": grounding_score,
            "prompt": prompt
        }
    
    def _hybrid_search(self, query: str) -> list:
        """Combine vector and BM25 search results."""
        # Vector search
        query_embedding = self.embedder.embed(query)
        vector_results = self.vector_store.search(query_embedding, self.top_k * 2)
        
        # BM25 search
        bm25_results = self.bm25.search(query, self.top_k * 2)
        
        # Reciprocal Rank Fusion
        rrf_scores = {}
        k = 60  # RRF constant
        
        for rank, (chunk, _) in enumerate(vector_results):
            rrf_scores[chunk.id] = rrf_scores.get(chunk.id, 0) + \
                self.hybrid_alpha / (k + rank + 1)
        
        for rank, (chunk, _) in enumerate(bm25_results):
            rrf_scores[chunk.id] = rrf_scores.get(chunk.id, 0) + \
                (1 - self.hybrid_alpha) / (k + rank + 1)
        
        # Build result list
        chunk_map = {c.id: c for c in self.all_chunks}
        results = [
            (chunk_map[cid], score) 
            for cid, score in rrf_scores.items()
            if cid in chunk_map
        ]
        results.sort(key=lambda x: x[1], reverse=True)
        
        return results[:self.top_k]
    
    def _build_prompt(self, question: str, context: str) -> str:
        """Build the RAG prompt."""
        return f"""Answer the question based ONLY on the provided context.
If the context doesn't contain enough information, say "I don't have enough 
information to answer this question."

Always cite which source document your answer comes from.

Context:
{context}

Question: {question}

Answer:"""
    
    def _generate_answer(self, question: str, 
                          retrieved: list) -> str:
        """
        Generate an answer from retrieved chunks.
        
        In production, this would call an LLM. For practice,
        we use extractive summarization (pulling relevant sentences).
        """
        if not retrieved:
            return "I don't have enough information to answer this question."
        
        # Simple extractive approach: find sentences most relevant to query
        query_words = set(re.findall(r'\w+', question.lower()))
        
        scored_sentences = []
        for chunk, chunk_score in retrieved:
            sentences = re.split(r'[.!?]+', chunk.text)
            for sentence in sentences:
                sentence = sentence.strip()
                if len(sentence) < 20:
                    continue
                
                words = set(re.findall(r'\w+', sentence.lower()))
                overlap = len(query_words & words)
                score = overlap * chunk_score
                scored_sentences.append((sentence, score, chunk.source))
        
        scored_sentences.sort(key=lambda x: x[1], reverse=True)
        
        # Take top sentences
        top_sentences = scored_sentences[:3]
        
        if not top_sentences:
            return "I don't have enough information to answer this question."
        
        answer_parts = []
        sources_used = set()
        for sent, _, source in top_sentences:
            answer_parts.append(sent)
            sources_used.add(source)
        
        answer = ". ".join(answer_parts)
        sources = ", ".join(sources_used)
        return f"{answer}. (Sources: {sources})"
    
    def _evaluate_grounding(self, answer: str, 
                             retrieved: list) -> float:
        """
        Evaluate how well the answer is grounded in retrieved chunks.
        
        Returns a score from 0.0 (not grounded) to 1.0 (fully grounded).
        
        Method: Check what fraction of answer words appear in the 
        retrieved context.
        """
        if not answer or not retrieved:
            return 0.0
        
        # Get all words from answer (excluding common stop words)
        stop_words = {"the", "a", "an", "is", "are", "was", "were", "be",
                      "been", "being", "have", "has", "had", "do", "does",
                      "did", "will", "would", "could", "should", "may",
                      "might", "can", "shall", "to", "of", "in", "for",
                      "on", "with", "at", "by", "from", "as", "into",
                      "through", "during", "before", "after", "and", "but",
                      "or", "not", "no", "this", "that", "these", "those",
                      "it", "its", "i", "don", "t", "s", "sources"}
        
        answer_words = set(re.findall(r'\w+', answer.lower())) - stop_words
        
        # Get all words from retrieved chunks
        context_words = set()
        for chunk, _ in retrieved:
            context_words.update(
                set(re.findall(r'\w+', chunk.text.lower())) - stop_words
            )
        
        if not answer_words:
            return 1.0
        
        # What fraction of answer words appear in context?
        grounded_words = answer_words & context_words
        score = len(grounded_words) / len(answer_words)
        
        return round(score, 3)
    
    def add_memory(self, key: str, text: str):
        """
        Add a piece of information to the agent's long-term memory.
        This is how agents can store things they learn for later retrieval.
        """
        chunks = self.chunker.chunk_text(text, source=f"memory:{key}")
        
        # Refit embedder with new vocabulary
        all_texts = [c.text for c in self.all_chunks] + [c.text for c in chunks]
        self.embedder.fit(all_texts)
        
        # Re-embed everything (in production, you'd use an incremental approach)
        for chunk in self.all_chunks:
            chunk.embedding = self.embedder.embed(chunk.text)
        for chunk in chunks:
            chunk.embedding = self.embedder.embed(chunk.text)
        
        self.all_chunks.extend(chunks)
        
        # Rebuild stores
        self.vector_store = SimpleVectorStore()
        self.vector_store.add(self.all_chunks)
        self.bm25 = SimpleBM25()
        self.bm25.add(self.all_chunks)
        
        print(f"🧠 Added memory '{key}': {len(chunks)} chunks")


# ═══════════════════════════════════════════════════════════════
# SECTION 6: DEMO WITH SAMPLE DOCUMENTS
# ═══════════════════════════════════════════════════════════════

# Sample knowledge base documents
SAMPLE_DOCS = {
    "transformer_architecture": """
The Transformer architecture was introduced in the paper "Attention Is All You Need" 
by Vaswani et al. in 2017. It revolutionized natural language processing by replacing 
recurrent neural networks with self-attention mechanisms.

The key innovation is the self-attention mechanism, which allows the model to weigh 
the importance of different parts of the input when processing each element. This 
enables parallel processing of all input tokens, making training much faster than 
sequential models like LSTMs.

The architecture consists of an encoder and decoder, each composed of multiple layers.
Each layer contains multi-head self-attention and feed-forward neural networks, with
residual connections and layer normalization.

Modern large language models like GPT-4 and Llama use a decoder-only variant of the
Transformer. They process text autoregressively, predicting one token at a time based
on all previous tokens.
""",
    
    "rlhf_training": """
Reinforcement Learning from Human Feedback (RLHF) is a technique used to align 
language models with human preferences. It was popularized by OpenAI and Anthropic
for training models like ChatGPT and Claude.

The RLHF process has three main steps:

1. Supervised Fine-Tuning (SFT): The base model is fine-tuned on a dataset of 
   high-quality (instruction, response) pairs to learn instruction-following behavior.

2. Reward Model Training: Human evaluators rank multiple model outputs for the same
   prompt. These rankings train a reward model that predicts human preferences.

3. Policy Optimization: The language model is further trained using reinforcement
   learning (specifically PPO) to maximize the reward model's score, with a KL
   divergence penalty to prevent deviating too far from the SFT model.

DPO (Direct Preference Optimization) is an alternative to RLHF that skips the 
reward model entirely. It directly optimizes the policy using preference data,
which is simpler and more stable than the full RLHF pipeline.
""",
    
    "rag_systems": """
Retrieval-Augmented Generation (RAG) is a technique that enhances language models
by allowing them to retrieve relevant information from external knowledge bases
before generating responses.

The RAG pipeline consists of several key components:

Chunking: Documents are split into smaller pieces (chunks) that can be individually
embedded and retrieved. Common strategies include fixed-size chunking, recursive
splitting by paragraph/sentence boundaries, and semantic chunking based on topic
changes.

Embedding: Each chunk is converted into a dense vector representation using an
embedding model. Popular models include OpenAI's text-embedding-3-small and 
open-source alternatives like all-MiniLM-L6-v2.

Vector Storage: Embeddings are stored in a vector database that supports efficient
similarity search. Options include ChromaDB, Pinecone, FAISS, and pgvector.

Retrieval: When a user asks a question, the question is embedded and compared
against stored chunk embeddings using cosine similarity. The top-K most similar
chunks are retrieved.

Hybrid Search: Combining vector search with keyword-based search (BM25) often
produces better results than either method alone. Reciprocal Rank Fusion (RRF)
is a popular method for combining multiple ranking lists.

Context Window Management: Retrieved chunks must fit within the LLM's context
window along with the system prompt and user query. Strategies include limiting
the number of chunks, reranking, and summarization.
""",

    "agent_architectures": """
AI agents are systems that use language models to autonomously plan, reason,
and take actions to accomplish tasks. Several key architectures have emerged:

ReAct (Reason + Act): The agent alternates between thinking (reasoning about
what to do) and acting (using tools). Each observation from a tool informs
the next reasoning step. This is effective for tasks requiring real-time
information gathering and step-by-step problem solving.

Plan-then-Execute: A planner generates a complete plan upfront, then an
executor carries out each step. A critic validates results after each step,
enabling retries and replanning. Better for complex multi-step tasks with
dependencies between steps.

Multi-Agent Systems: Multiple specialized agents collaborate on a task.
Common patterns include a Planner agent (decomposes tasks), Executor agent
(runs tools), Researcher agent (gathers information), and Critic agent
(validates quality). An orchestrator coordinates the agents.

Agent Memory: Effective agents use multiple memory types. Short-term memory
(the conversation scratchpad) handles the current task. Long-term memory
(vector stores, RAG) provides persistent knowledge. Episodic memory logs
past actions and outcomes for learning from experience.

Common failure modes include infinite loops (same action repeated),
hallucinated tool calls (inventing non-existent tools), and premature
termination (answering before having sufficient information).
"""
}


def demo():
    """Run the RAG pipeline demo."""
    # Initialize pipeline
    rag = RAGPipeline(chunk_size=400, chunk_overlap=50, top_k=3)
    
    # Ingest documents
    rag.ingest(SAMPLE_DOCS)
    
    # Test queries
    print("\n" + "="*60)
    print("QUERY 1: Factual retrieval")
    print("="*60)
    rag.query("What is the Transformer architecture and who created it?")
    
    print("\n" + "="*60)
    print("QUERY 2: Technical details")
    print("="*60)
    rag.query("How does RLHF work and what are its three steps?")
    
    print("\n" + "="*60)
    print("QUERY 3: Comparison")
    print("="*60)
    rag.query("What is the difference between ReAct and Plan-then-Execute?")
    
    print("\n" + "="*60)
    print("QUERY 4: RAG about RAG")
    print("="*60)
    rag.query("What are the components of a RAG pipeline?")
    
    # Demonstrate dynamic memory
    print("\n" + "="*60)
    print("ADDING DYNAMIC MEMORY")
    print("="*60)
    rag.add_memory(
        "team_decision",
        "Our team decided to use ChromaDB for the vector store because "
        "it's easy to set up, supports persistent storage, and has good "
        "Python integration. We chose text-embedding-3-small from OpenAI "
        "for embeddings due to its balance of quality and cost."
    )
    
    print("\n" + "="*60)
    print("QUERY 5: Using dynamic memory")
    print("="*60)
    rag.query("What vector database did our team decide to use and why?")
    
    # Show out-of-scope handling
    print("\n" + "="*60)
    print("QUERY 6: Out of scope")
    print("="*60)
    rag.query("What is the capital of France?")


if __name__ == "__main__":
    demo()
```

### Using ChromaDB (Production Alternative)

```python
"""
Production RAG with ChromaDB — use this when you need persistent, 
performant vector search.

pip install chromadb openai
"""
import chromadb
from chromadb.utils import embedding_functions
import openai

# Initialize ChromaDB
client = chromadb.PersistentClient(path="./chroma_db")

# Use OpenAI embeddings (or switch to local)
openai_ef = embedding_functions.OpenAIEmbeddingFunction(
    api_key="your-api-key",
    model_name="text-embedding-3-small"
)

# Create a collection
collection = client.get_or_create_collection(
    name="knowledge_base",
    embedding_function=openai_ef,
    metadata={"hnsw:space": "cosine"}  # Use cosine similarity
)

# Add documents
collection.add(
    documents=["The Transformer architecture was introduced in 2017...",
               "RLHF is used to align language models..."],
    metadatas=[{"source": "transformers.md"}, {"source": "rlhf.md"}],
    ids=["doc1", "doc2"]
)

# Query
results = collection.query(
    query_texts=["How does attention work?"],
    n_results=3,
    include=["documents", "metadatas", "distances"]
)

print(f"Top result: {results['documents'][0][0][:100]}...")
print(f"Distance: {results['distances'][0][0]:.4f}")
print(f"Source: {results['metadatas'][0][0]['source']}")

# Use in RAG prompt
context = "\n\n".join(results["documents"][0])
prompt = f"""Answer based on this context:

{context}

Question: How does attention work?
Answer:"""

response = openai.chat.completions.create(
    model="gpt-4",
    messages=[{"role": "user", "content": prompt}]
)
print(response.choices[0].message.content)
```

---

## ✔ Day 5 Deliverable Checklist

- [ ] Built a complete RAG pipeline (chunker + embedder + vector store)
- [ ] Implemented BM25 keyword search
- [ ] Combined vector + BM25 into hybrid search
- [ ] Added a grounding score evaluator
- [ ] Agent can store new memories dynamically
- [ ] Agent can retrieve relevant context for questions
- [ ] Tested with at least 4 different queries
- [ ] Can explain: What is RAG and when should you use it vs fine-tuning?
- [ ] Can explain: How does cosine similarity work?
- [ ] Can explain: Why is hybrid search better than pure vector search?
- [ ] Can explain: What are chunking strategies and their trade-offs?

---

**Next → [Day 6: Multi-Agent Collaboration](06-multi-agent-collaboration.md)**
