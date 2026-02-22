# Day 1 — Modern LLM Foundations

> **🎯 Goal**: Understand how LLMs work at a deep, engineering-oriented level.

> **⏱ Time**: 2–3h theory + 1–2h practice

> **📦 Deliverable**: A running local model + performance notes

---

## Table of Contents

1. [The Transformer Architecture](#the-transformer-architecture)
2. [Attention Mechanism](#attention-mechanism)
3. [Multi-Head Attention](#multi-head-attention)
4. [Feed-Forward Layers](#feed-forward-layers)
5. [Positional Encodings](#positional-encodings)
6. [Putting It All Together](#putting-it-all-together)
7. [Tokenization](#tokenization)
8. [Training Lifecycle](#training-lifecycle)
9. [Inference Optimizations](#inference-optimizations)
10. [Practice Exercises](#practice-exercises)

---

## The Transformer Architecture

The Transformer (Vaswani et al., 2017) is the architecture behind every modern LLM. Before Transformers, we used RNNs and LSTMs which processed tokens **sequentially** — one word at a time. Transformers process **all tokens in parallel**, which is why they train so much faster.

### High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                    TRANSFORMER                       │
│                                                      │
│  ┌──────────────────┐    ┌──────────────────┐       │
│  │     ENCODER       │    │     DECODER       │       │
│  │                   │    │                   │       │
│  │  ┌─────────────┐ │    │  ┌─────────────┐ │       │
│  │  │  Self-Attn   │ │    │  │ Masked Self  │ │       │
│  │  │  + Add&Norm  │ │    │  │   Attention  │ │       │
│  │  └──────┬──────┘ │    │  └──────┬──────┘ │       │
│  │         │         │    │         │         │       │
│  │  ┌──────▼──────┐ │    │  ┌──────▼──────┐ │       │
│  │  │  Feed-Fwd   │ │    │  │ Cross-Attn   │ │       │
│  │  │  + Add&Norm  │ │    │  │  + Add&Norm  │ │       │
│  │  └─────────────┘ │    │  └──────┬──────┘ │       │
│  │                   │    │         │         │       │
│  │   × N layers      │───▶│  ┌──────▼──────┐ │       │
│  │                   │    │  │  Feed-Fwd   │ │       │
│  └──────────────────┘    │  │  + Add&Norm  │ │       │
│                           │  └─────────────┘ │       │
│                           │                   │       │
│                           │   × N layers      │       │
│                           └──────────────────┘       │
└─────────────────────────────────────────────────────┘
```

**Key insight for modern LLMs**: GPT-style models (GPT-4, Llama, Claude) use **decoder-only** architecture. They dropped the encoder entirely. BERT uses **encoder-only**. The original Transformer used both.

### Decoder-Only Architecture (What GPT/Llama Use)

```
Input: "The cat sat on the"
         │    │   │   │   │
         ▼    ▼   ▼   ▼   ▼
    ┌─────────────────────────┐
    │   Token Embeddings       │  ← Convert tokens to vectors
    │   + Positional Encoding  │  ← Add position information
    └────────────┬────────────┘
                 │
    ┌────────────▼────────────┐
    │   Masked Self-Attention  │  ← Each token attends only
    │   + Add & LayerNorm      │    to previous tokens
    └────────────┬────────────┘
                 │
    ┌────────────▼────────────┐
    │   Feed-Forward Network   │  ← Transform each position
    │   + Add & LayerNorm      │
    └────────────┬────────────┘
                 │
            × N layers (e.g., 32 for Llama 3 8B)
                 │
    ┌────────────▼────────────┐
    │   Linear + Softmax       │  ← Project to vocabulary size
    └────────────┬────────────┘
                 │
                 ▼
    Output: probability distribution over next token
            → "mat" (0.15), "floor" (0.08), ...
```

**Why decoder-only?** For text generation, you only need to predict the next token. The causal mask (each token only sees previous tokens) naturally fits autoregressive generation.

---

## Attention Mechanism

Attention is the core innovation. It answers: **"When generating the current token, how much should I focus on each previous token?"**

### The Intuition

Imagine reading: *"The animal didn't cross the street because **it** was too tired."*

What does "it" refer to? "The animal." Your brain **attends** to "animal" when processing "it". That's exactly what attention does — it creates a weighted focus over all previous tokens.

### The Math (Simplified)

For each token, we create three vectors:
- **Q (Query)**: "What am I looking for?"
- **K (Key)**: "What do I contain?"
- **V (Value)**: "What information do I provide?"

```
Attention(Q, K, V) = softmax(Q × K^T / √d_k) × V
```

### Step-by-Step Walkthrough

```
Input sentence: "The cat sat"

Step 1: Each token gets Q, K, V vectors (via learned weight matrices)
─────────────────────────────────────────────────────────
Token    │  Q (Query)      │  K (Key)        │  V (Value)
─────────┼─────────────────┼─────────────────┼──────────────
"The"    │  [0.1, 0.3]     │  [0.2, 0.1]     │  [0.5, 0.8]
"cat"    │  [0.8, 0.2]     │  [0.7, 0.9]     │  [0.3, 0.6]
"sat"    │  [0.4, 0.7]     │  [0.5, 0.4]     │  [0.9, 0.1]

Step 2: Compute attention scores (Q × K^T)
──────────────────────────────────────────
For "sat" (Q=[0.4, 0.7]):
  Score with "The":  0.4×0.2 + 0.7×0.1 = 0.15
  Score with "cat":  0.4×0.7 + 0.7×0.9 = 0.91  ← highest!
  Score with "sat":  0.4×0.5 + 0.7×0.4 = 0.48

Step 3: Scale by √d_k (prevents large values)
──────────────────────────────────────────
  d_k = 2, √d_k = 1.41
  Scaled: [0.11, 0.65, 0.34]

Step 4: Softmax (convert to probabilities)
──────────────────────────────────────────
  Weights: [0.21, 0.45, 0.34]
           "The"  "cat"  "sat"

Step 5: Weighted sum of Values
──────────────────────────────
  Output = 0.21×[0.5,0.8] + 0.45×[0.3,0.6] + 0.34×[0.9,0.1]
         = [0.105+0.135+0.306, 0.168+0.270+0.034]
         = [0.546, 0.472]

Result: "sat" now carries information weighted toward "cat"
```

### Why Scale by √d_k?

Without scaling, when `d_k` is large (e.g., 128), dot products become very large numbers. Softmax of large numbers saturates to 0s and 1s, killing gradients. Dividing by `√d_k` keeps values in a reasonable range.

### Causal Masking (Decoder-Only)

In GPT-style models, each token can only attend to **previous** tokens (not future ones). This is enforced by setting future positions to `-infinity` before softmax:

```
                    Attention Scores (before softmax)
                    ┌──────────────────────────────┐
                    │  "The"  "cat"  "sat"  "on"   │
          ┌────────┼──────────────────────────────┤
  "The"   │  0.5    -∞      -∞      -∞            │
  "cat"   │  0.3    0.8     -∞      -∞            │
  "sat"   │  0.1    0.9     0.4     -∞            │
  "on"    │  0.2    0.3     0.7     0.5           │
          └────────┴──────────────────────────────┘

After softmax, -∞ becomes 0 → token can't "see" the future
```

---

## Multi-Head Attention

Instead of one attention calculation, we run **multiple attention heads in parallel**, each learning different relationships.

### Why Multiple Heads?

One head might learn **syntactic** relationships (subject-verb), another might learn **semantic** relationships (coreference), another might learn **positional** patterns.

```
┌─────────────────────────────────────────────────┐
│              Multi-Head Attention                 │
│                                                   │
│   Input (d_model = 512)                          │
│      │                                            │
│      ├──────────┬──────────┬──────────┐          │
│      ▼          ▼          ▼          ▼          │
│   ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐       │
│   │Head 1│  │Head 2│  │Head 3│  │ ... 8│       │
│   │d_k=64│  │d_k=64│  │d_k=64│  │d_k=64│       │
│   └──┬───┘  └──┬───┘  └──┬───┘  └──┬───┘       │
│      │         │         │         │             │
│      └─────────┴─────┬───┴─────────┘             │
│                      │                            │
│              Concatenate (512)                    │
│                      │                            │
│              Linear Projection                    │
│                      │                            │
│              Output (512)                         │
│                                                   │
└─────────────────────────────────────────────────┘

Each head operates on d_model / n_heads dimensions
  512 / 8 = 64 dimensions per head
  Total compute ≈ same as single full-sized attention
```

### Real Model Sizes

| Model | d_model | n_heads | d_k (per head) | Layers |
|-------|---------|---------|-----------------|--------|
| GPT-2 | 768 | 12 | 64 | 12 |
| GPT-3 175B | 12288 | 96 | 128 | 96 |
| Llama 3 8B | 4096 | 32 | 128 | 32 |
| Llama 3 70B | 8192 | 64 | 128 | 80 |

---

## Feed-Forward Layers

After attention, each token passes through a **position-wise feed-forward network** — the same network applied independently to each position.

```
FFN(x) = GELU(x × W₁ + b₁) × W₂ + b₂
```

### What Does It Do?

Attention **mixes information across positions** (tokens talk to each other). The FFN **transforms each token's representation independently** — it's where the model stores "knowledge" (facts, patterns).

```
┌──────────────────────────────────────────┐
│         Feed-Forward Network              │
│                                           │
│  Input: d_model (e.g., 4096)             │
│     │                                     │
│     ▼                                     │
│  Linear: d_model → d_ff (e.g., 11008)   │
│     │                                     │
│     ▼                                     │
│  GELU Activation                          │  ← Non-linearity
│     │                                     │
│     ▼                                     │
│  Linear: d_ff → d_model (e.g., 4096)    │
│     │                                     │
│     ▼                                     │
│  Output: d_model                          │
│                                           │
│  Note: d_ff is typically ~2.7× d_model   │
│  This expansion lets the model learn      │
│  more complex transformations             │
└──────────────────────────────────────────┘
```

**Key insight**: Research suggests the FFN layers are where **factual knowledge** is stored. Attention layers decide **what to combine**; FFN layers decide **what to say about it**.

### Residual Connections & Layer Normalization

Every sub-layer (attention, FFN) has a **residual connection** and **layer norm**:

```
Output = LayerNorm(x + SubLayer(x))

Why residual connections?
  - Allow gradients to flow directly through the network
  - Enable training very deep models (32–96 layers)
  - Without them, gradients vanish in deep networks

Why layer normalization?
  - Stabilizes training by normalizing activations
  - Applied per-token across the feature dimension
  - RMSNorm (used in Llama) is a simplified version
```

---

## Positional Encodings

Transformers process all tokens in parallel — they have **no inherent notion of order**. Without positional encodings, "The cat sat on the mat" and "mat the on sat cat The" would look identical.

### Types of Positional Encoding

**1. Sinusoidal (Original Transformer)**

```
PE(pos, 2i)   = sin(pos / 10000^(2i/d_model))
PE(pos, 2i+1) = cos(pos / 10000^(2i/d_model))

Position 0: [sin(0), cos(0), sin(0), cos(0), ...]  = [0, 1, 0, 1, ...]
Position 1: [sin(1), cos(1), sin(0.01), cos(0.01), ...] = [0.84, 0.54, ...]

Key property: PE(pos+k) can be represented as a linear function of PE(pos)
             → the model can learn relative positions
```

**2. Learned Positional Embeddings (GPT-2)**

Just a lookup table — each position (0 to max_seq_len) has a learned vector. Simple but limited to fixed max length.

**3. RoPE — Rotary Position Embeddings (Llama, Modern LLMs)**

```
RoPE applies rotation matrices to Q and K vectors:

  q_rotated = rotate(q, position)
  k_rotated = rotate(k, position)

  The dot product q·k then naturally encodes relative position:
  dot(rotate(q, pos_m), rotate(k, pos_n)) depends on (m - n)

Why RoPE is preferred:
  ✓ Encodes relative positions (not absolute)
  ✓ Decays attention with distance (natural)
  ✓ Can extrapolate to longer sequences than training
  ✓ No extra parameters needed
```

---

## Putting It All Together

A complete forward pass through a decoder-only Transformer:

```
Input: "The cat sat on the" → predict next token

Step 1: TOKENIZE
  "The cat sat on the" → [464, 3797, 3332, 373, 262]

Step 2: EMBED (token embedding + position encoding)
  Each token ID → d_model vector (e.g., 4096-dim)
  Add positional encoding (RoPE applied later in attention)
  Result: [5 × 4096] matrix

Step 3: PASS THROUGH N TRANSFORMER BLOCKS
  For each layer (1 to 32 for Llama 3 8B):
    ┌─────────────────────────────────┐
    │ a) RMSNorm                       │
    │ b) Multi-Head Self-Attention     │
    │    (with RoPE and causal mask)   │
    │ c) Residual connection           │
    │ d) RMSNorm                       │
    │ e) Feed-Forward (SwiGLU)         │
    │ f) Residual connection           │
    └─────────────────────────────────┘

Step 4: FINAL PROJECTION
  Last layer output → Linear(4096 → vocab_size)
  → logits for each of ~128,000 tokens

Step 5: SAMPLE
  Apply temperature, top-p, top-k
  softmax(logits / temperature) → probability distribution
  Sample from distribution → token ID → "mat"

Step 6: APPEND AND REPEAT
  Append "mat" to input → "The cat sat on the mat"
  Repeat from Step 1 (but use KV cache — see Inference section)
```

---

## Tokenization

Tokenization converts text into numbers the model can process. It's **not** word-level — modern LLMs use **subword** tokenization.

### Why Not Word-Level?

```
Word-level problems:
  - Vocabulary would be millions of words
  - Can't handle new/rare words ("ChatGPT", "unfathomable")
  - Can't handle misspellings ("teh", "recieve")

Subword solution:
  - Vocabulary of ~32K–128K tokens
  - Common words = single token: "the" → [1]
  - Rare words = multiple tokens: "unfathomable" → ["un", "f", "ath", "om", "able"]
  - Can represent ANY text, even made-up words
```

### BPE (Byte Pair Encoding) — Step by Step

BPE is the most common tokenization algorithm (used by GPT, Llama).

```
Training BPE (done once, offline):

Start with: character-level vocabulary
Corpus: "low lower newest widest"

Step 0: Split into characters
  l o w </w>   l o w e r </w>   n e w e s t </w>   w i d e s t </w>

Step 1: Count all adjacent pairs
  (l,o)=2  (o,w)=2  (w,</w>)=1  (w,e)=2  (e,r)=1  (e,s)=2
  (s,t)=2  (n,e)=1  (e,w)=1  (w,i)=1  (i,d)=1  (d,e)=1  ...

Step 2: Merge most frequent pair → (e,s) → "es"
  l o w </w>   l o w e r </w>   n e w es t </w>   w i d es t </w>

Step 3: Repeat — merge (es,t) → "est"
  l o w </w>   l o w e r </w>   n e w est </w>   w i d est </w>

Step 4: Repeat — merge (l,o) → "lo"
  lo w </w>   lo w e r </w>   n e w est </w>   w i d est </w>

Step 5: Repeat — merge (lo,w) → "low"
  low </w>   low e r </w>   n e w est </w>   w i d est </w>

... continue until vocabulary reaches desired size (e.g., 32000)

Result: "lowest" → ["low", "est"]  (2 tokens instead of 6 characters)
```

### Tokenization Quirks That Matter

```
Problem 1: Token boundaries affect reasoning
  "123 + 456" might tokenize as ["123", " +", " 456"]     ← 3 tokens, clean
  "12345+67890" might tokenize as ["123", "45", "+", "678", "90"] ← 5 tokens, messy

Problem 2: Whitespace is significant
  " Hello" and "Hello" are DIFFERENT tokens
  Leading spaces are part of the token

Problem 3: Code tokenization
  "def calculate_total_price(items):" may become 6+ tokens
  Variable names get split: "getUserById" → ["get", "User", "By", "Id"]

Problem 4: Non-English languages use more tokens
  "Hello" = 1 token
  "こんにちは" = 3-5 tokens (depending on tokenizer)
  → Same content costs more in non-English
```

### SentencePiece

SentencePiece (used by Llama, T5) treats the input as a **raw byte stream** — it doesn't need pre-tokenized words. This means it works for any language, including ones without spaces (Chinese, Japanese).

```
SentencePiece vs BPE:
  BPE: requires pre-tokenization (splitting by whitespace first)
  SentencePiece: works on raw text directly
  
  SentencePiece uses "▁" (U+2581) to represent spaces:
    "Hello world" → ["▁Hello", "▁world"]
    
  This preserves the information that "world" had a space before it
```

---

## Training Lifecycle

Modern LLMs go through multiple training phases:

```
┌─────────────────────────────────────────────────────────────────┐
│                    LLM Training Pipeline                         │
│                                                                  │
│  Phase 1: PRETRAINING                                           │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Data: Trillions of tokens (web, books, code)               │ │
│  │ Objective: Next-token prediction                           │ │
│  │ Compute: Thousands of GPUs, weeks/months                   │ │
│  │ Result: Base model (good at completing text, not following  │ │
│  │         instructions)                                       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                           │                                      │
│                           ▼                                      │
│  Phase 2: SUPERVISED FINE-TUNING (SFT)                          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Data: ~100K high-quality (instruction, response) pairs     │ │
│  │ Objective: Learn to follow instructions                    │ │
│  │ Compute: Tens of GPUs, hours/days                          │ │
│  │ Result: Instruction-tuned model                            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                           │                                      │
│                           ▼                                      │
│  Phase 3: ALIGNMENT (RLHF or DPO)                               │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Data: Human preference rankings                            │ │
│  │ Objective: Align with human values (helpful, harmless)     │ │
│  │ Compute: Moderate                                           │ │
│  │ Result: Aligned, safe, helpful model                       │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Phase 1: Pretraining

```
Objective: Given tokens [t1, t2, ..., tn], predict tn+1

Training example:
  Input:  "The capital of France is"
  Target: "Paris"

  The model sees EVERY adjacent pair:
    "The" → predict "capital"
    "The capital" → predict "of"
    "The capital of" → predict "France"
    "The capital of France" → predict "is"
    "The capital of France is" → predict "Paris"

Loss function: Cross-entropy between predicted and actual next token
  Loss = -log(P(correct_token))
  
  If model predicts P("Paris") = 0.8 → Loss = -log(0.8) = 0.22 (low, good)
  If model predicts P("Paris") = 0.01 → Loss = -log(0.01) = 4.6 (high, bad)

Scale of pretraining:
  Llama 3 8B: trained on 15 trillion tokens
  GPT-4: estimated 13+ trillion tokens
  Compute: millions of GPU-hours
```

### Phase 2: Supervised Fine-Tuning (SFT)

```
Training data format:
{
  "instruction": "Summarize the following article in 3 bullet points.",
  "input": "The European Central Bank announced...",
  "output": "• ECB raised interest rates by 0.25%\n• ..."
}

What changes:
  Before SFT: "What is 2+2?" → "What is 2+3? What is 2+4? ..."  (completion)
  After SFT:  "What is 2+2?" → "2 + 2 = 4"                       (instruction-following)

Key insight: SFT doesn't teach the model NEW knowledge.
  It teaches the model to USE the knowledge it already has
  in the FORMAT humans expect (Q&A, instruction-following).
```

### Phase 3: RLHF (Reinforcement Learning from Human Feedback)

```
┌──────────────────────────────────────────────────────────┐
│                     RLHF Pipeline                         │
│                                                           │
│  Step 1: Collect human preferences                       │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Prompt: "Explain quantum computing"                  │ │
│  │                                                      │ │
│  │ Response A: "Quantum computing uses qubits that..."  │ │
│  │ Response B: "Quantum computers are like magic..."    │ │
│  │                                                      │ │
│  │ Human says: A is better than B  (A ≻ B)             │ │
│  └─────────────────────────────────────────────────────┘ │
│                         │                                 │
│                         ▼                                 │
│  Step 2: Train a Reward Model (RM)                       │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Input: (prompt, response) → Score (scalar)           │ │
│  │ Trained to satisfy: RM(A) > RM(B) when A ≻ B       │ │
│  │ Loss: -log(sigmoid(RM(A) - RM(B)))                  │ │
│  └─────────────────────────────────────────────────────┘ │
│                         │                                 │
│                         ▼                                 │
│  Step 3: Optimize policy with PPO                        │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Generate response → Get reward score → Update model  │ │
│  │                                                      │ │
│  │ Objective: maximize RM(response)                     │ │
│  │ Constraint: don't drift too far from SFT model       │ │
│  │   (KL penalty prevents reward hacking)               │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### DPO (Direct Preference Optimization) — Simpler Alternative

```
RLHF problem: Need separate reward model + RL training loop (complex)

DPO insight: We can skip the reward model entirely!

DPO loss (simplified):
  Loss = -log(sigmoid(β × (log π(y_w|x) - log π(y_l|x)
                           - log π_ref(y_w|x) + log π_ref(y_l|x))))

Where:
  y_w = preferred response ("winner")
  y_l = dispreferred response ("loser")
  π = current model
  π_ref = reference model (SFT checkpoint)
  β = temperature controlling deviation from reference

In plain English:
  "Increase probability of preferred responses,
   decrease probability of dispreferred responses,
   but don't change too much from the SFT model."

Why DPO is popular:
  ✓ No reward model needed
  ✓ No RL training loop (just supervised learning)
  ✓ More stable training
  ✓ Comparable results to RLHF
```

---

## Inference Optimizations

Once trained, making the model **fast** for users is critical.

### KV Cache — The Most Important Optimization

```
Problem: Autoregressive generation is wasteful

Without KV Cache:
  Generate "The"     → process ["The"]                    = 1 token
  Generate "cat"     → process ["The", "cat"]             = 2 tokens
  Generate "sat"     → process ["The", "cat", "sat"]      = 3 tokens
  Generate "on"      → process ["The", "cat", "sat", "on"]= 4 tokens
  Total work: 1 + 2 + 3 + 4 = 10 token computations

  For 1000 tokens: 1+2+...+1000 = 500,500 computations → O(n²)!

With KV Cache:
  Generate "The"     → compute K,V for "The", cache them
  Generate "cat"     → compute K,V for "cat" only, reuse cached K,V for "The"
  Generate "sat"     → compute K,V for "sat" only, reuse cached K,V for "The","cat"
  Total work: 1 + 1 + 1 + 1 = 4 token computations

  For 1000 tokens: 1000 computations → O(n)

┌─────────────────────────────────────────────────────┐
│                  KV Cache Mechanism                   │
│                                                       │
│  Step 1 (prefill): Process full prompt at once        │
│    "Tell me about cats" → compute all K,V → cache     │
│                                                       │
│  Step 2 (decode): Generate one token at a time        │
│    New token Q attends to ALL cached K,V              │
│    Compute new K,V → append to cache                  │
│    Output next token                                   │
│                                                       │
│  Memory cost: O(layers × seq_len × d_model)          │
│  Llama 3 8B, 4096 context:                            │
│    32 layers × 4096 × 4096 × 2 (K+V) × 2 bytes      │
│    ≈ 2 GB of KV cache                                 │
└─────────────────────────────────────────────────────┘
```

### Quantization

```
Quantization = reducing precision of model weights to save memory and speed up inference

┌──────────┬───────┬──────────────┬──────────────────────────────┐
│ Format   │ Bits  │ Memory (7B)  │ Quality Impact               │
├──────────┼───────┼──────────────┼──────────────────────────────┤
│ FP32     │ 32    │ ~28 GB       │ Full precision (training)    │
│ FP16     │ 16    │ ~14 GB       │ Negligible loss              │
│ BF16     │ 16    │ ~14 GB       │ Better training stability    │
│ INT8     │ 8     │ ~7 GB        │ Very slight degradation      │
│ INT4/Q4  │ 4     │ ~3.5 GB      │ Noticeable on complex tasks  │
│ GPTQ/AWQ │ 4     │ ~3.5 GB      │ Better than naive INT4       │
└──────────┴───────┴──────────────┴──────────────────────────────┘

How INT4 quantization works (simplified):
  Original FP16 weights: [0.123, -0.456, 0.789, -0.234, ...]
  
  1. Find min/max of a group of weights (e.g., 128 weights)
     min = -0.456, max = 0.789
  2. Map to INT4 range [0, 15]:
     0.123 → round((0.123 - (-0.456)) / (0.789 - (-0.456)) × 15) = 7
  3. Store: INT4 value (7) + scale + zero_point per group
  4. Dequantize at runtime: 7 × scale + zero_point ≈ 0.123

Trade-off: 4× less memory, ~2× faster, slightly worse quality
```

### Batching Strategies

```
Naive batching:
  Wait for N requests → process together → return all at once
  Problem: high latency for early arrivals

Continuous batching:
  ┌───────────────────────────────────────────────┐
  │ Time →                                         │
  │                                                │
  │ Req 1: ████████████████                       │
  │ Req 2:     ████████████████████               │  ← starts when slot opens
  │ Req 3:         ██████████████████████         │
  │ Req 4:              ████████████████          │
  │                                                │
  │ As soon as one request finishes generating,    │
  │ a new request can start in that "slot"         │
  └───────────────────────────────────────────────┘

  Result: GPU stays busy, lower average latency

Speculative decoding:
  Use a small, fast "draft" model to generate N candidate tokens
  Then verify all N with the large model in ONE forward pass
  If draft model guesses correctly → N tokens for the cost of 1
  Speedup: 2-3× for well-matched draft models
```

### Sampling Strategies

```
Temperature (controls randomness):
  T=0.0: Always pick highest probability token (deterministic)
  T=0.7: Moderate creativity (good default for chat)
  T=1.0: Sample from raw distribution
  T=2.0: Very random (rarely useful)

  Math: P(token) = softmax(logits / T)
  Low T → sharp distribution (confident)
  High T → flat distribution (random)

Top-K sampling:
  Only consider the K most probable tokens
  top_k=50: Sample from top 50 tokens
  Prevents extremely unlikely tokens from being selected

Top-P (nucleus) sampling:
  Keep smallest set of tokens whose cumulative probability ≥ P
  top_p=0.9: Keep tokens that make up 90% of probability mass
  
  Example: If top token has P=0.95, only that token is considered
           If top 10 tokens share probability, all 10 are considered
  
  More adaptive than top-K
```

---

## Practice Exercises

### Exercise 1: Run a Local Model

```python
# Option A: Using Hugging Face Transformers
# pip install transformers torch accelerate

from transformers import AutoTokenizer, AutoModelForCausalLM
import torch
import time

model_name = "meta-llama/Llama-3.2-1B"  # Start small for practice
# For larger: "meta-llama/Llama-3.1-8B"

tokenizer = AutoTokenizer.from_pretrained(model_name)
model = AutoModelForCausalLM.from_pretrained(
    model_name,
    torch_dtype=torch.float16,  # Use FP16
    device_map="auto"           # Automatically use GPU if available
)

prompt = "Explain what a neural network is in simple terms:"
inputs = tokenizer(prompt, return_tensors="pt").to(model.device)

# Measure performance
start = time.time()
with torch.no_grad():
    outputs = model.generate(
        **inputs,
        max_new_tokens=200,
        temperature=0.7,
        top_p=0.9,
        do_sample=True
    )
elapsed = time.time() - start

response = tokenizer.decode(outputs[0], skip_special_tokens=True)
n_tokens = outputs.shape[1] - inputs["input_ids"].shape[1]

print(f"Response: {response}")
print(f"\n--- Performance ---")
print(f"Generated {n_tokens} tokens in {elapsed:.2f}s")
print(f"Speed: {n_tokens/elapsed:.1f} tokens/sec")
print(f"Model dtype: {model.dtype}")
print(f"Model memory: {model.get_memory_footprint()/1e9:.2f} GB")
```

```python
# Option B: Using llama-cpp-python (for quantized models)
# pip install llama-cpp-python

from llama_cpp import Llama
import time

# Download a GGUF quantized model first
# e.g., from https://huggingface.co/TheBloke/Llama-2-7B-GGUF
model_path = "./models/llama-3-8b-q4_k_m.gguf"

llm = Llama(
    model_path=model_path,
    n_ctx=4096,     # Context window
    n_gpu_layers=-1 # Offload all layers to GPU (-1 = all)
)

prompt = "Explain what a neural network is in simple terms:"

start = time.time()
output = llm(
    prompt,
    max_tokens=200,
    temperature=0.7,
    top_p=0.9,
)
elapsed = time.time() - start

text = output["choices"][0]["text"]
n_tokens = output["usage"]["completion_tokens"]

print(f"Response: {text}")
print(f"\n--- Performance ---")
print(f"Generated {n_tokens} tokens in {elapsed:.2f}s")
print(f"Speed: {n_tokens/elapsed:.1f} tokens/sec")
```

### Exercise 2: Compare FP16 vs Q4 Performance

```python
import time
from transformers import AutoTokenizer, AutoModelForCausalLM, BitsAndBytesConfig
import torch

model_name = "meta-llama/Llama-3.2-1B"
tokenizer = AutoTokenizer.from_pretrained(model_name)
prompt = "Write a detailed explanation of how computers work:"
inputs_template = tokenizer(prompt, return_tensors="pt")

def benchmark_model(model, label, inputs, n_runs=3):
    """Run generation multiple times and report average stats."""
    times = []
    tokens_list = []
    
    for _ in range(n_runs):
        inp = {k: v.to(model.device) for k, v in inputs.items()}
        start = time.time()
        with torch.no_grad():
            out = model.generate(**inp, max_new_tokens=100, do_sample=False)
        elapsed = time.time() - start
        n_tokens = out.shape[1] - inp["input_ids"].shape[1]
        times.append(elapsed)
        tokens_list.append(n_tokens)
    
    avg_time = sum(times) / len(times)
    avg_tokens = sum(tokens_list) / len(tokens_list)
    memory_gb = model.get_memory_footprint() / 1e9
    
    print(f"\n{'='*50}")
    print(f"Model: {label}")
    print(f"Memory: {memory_gb:.2f} GB")
    print(f"Avg time: {avg_time:.2f}s for {avg_tokens:.0f} tokens")
    print(f"Speed: {avg_tokens/avg_time:.1f} tokens/sec")
    print(f"{'='*50}")
    return {"label": label, "memory_gb": memory_gb, 
            "tokens_per_sec": avg_tokens/avg_time}

# Test 1: FP16
print("Loading FP16 model...")
model_fp16 = AutoModelForCausalLM.from_pretrained(
    model_name, torch_dtype=torch.float16, device_map="auto"
)
result_fp16 = benchmark_model(model_fp16, "FP16", inputs_template)
del model_fp16
torch.cuda.empty_cache()

# Test 2: INT4 (4-bit quantized)
print("\nLoading INT4 model...")
bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_compute_dtype=torch.float16,
    bnb_4bit_quant_type="nf4"
)
model_int4 = AutoModelForCausalLM.from_pretrained(
    model_name, quantization_config=bnb_config, device_map="auto"
)
result_int4 = benchmark_model(model_int4, "INT4", inputs_template)

# Compare
print("\n" + "="*50)
print("COMPARISON")
print(f"Memory reduction: {result_fp16['memory_gb']/result_int4['memory_gb']:.1f}×")
print(f"Speed difference: {result_int4['tokens_per_sec']/result_fp16['tokens_per_sec']:.2f}×")
```

### Exercise 3: Explore Tokenization

```python
# pip install tiktoken  (for OpenAI tokenizers)
# pip install transformers  (for Llama/HuggingFace tokenizers)

import tiktoken

# GPT-4 tokenizer
enc = tiktoken.encoding_for_model("gpt-4")

test_strings = [
    "Hello, world!",
    "The quick brown fox jumps over the lazy dog.",
    "def fibonacci(n): return n if n <= 1 else fibonacci(n-1) + fibonacci(n-2)",
    "12345 + 67890 = 80235",
    "こんにちは世界",  # Japanese: "Hello World"
    "🎉🚀💻",          # Emojis
    "   spaces   matter   ",
    "supercalifragilisticexpialidocious",
    "HTTP/1.1 200 OK\r\nContent-Type: application/json",
]

print(f"{'Text':<55} {'Tokens':>7}  Token IDs")
print("=" * 100)

for text in test_strings:
    tokens = enc.encode(text)
    # Decode each token individually to see the pieces
    pieces = [enc.decode([t]) for t in tokens]
    display = text[:52] + "..." if len(text) > 55 else text
    print(f"{display:<55} {len(tokens):>7}  {pieces}")

# Experiment: How does tokenization affect math?
print("\n\nTokenization of numbers:")
for num in ["42", "123", "1234", "12345", "123456", "1234567"]:
    tokens = enc.encode(num)
    pieces = [enc.decode([t]) for t in tokens]
    print(f"  {num:>10} → {len(tokens)} tokens: {pieces}")
```

---

## ✔ Day 1 Deliverable Checklist

- [ ] Successfully ran a local LLM (any size)
- [ ] Measured tokens/sec performance
- [ ] Compared at least two precision levels (FP16 vs Q4)
- [ ] Explored tokenization of various prompts
- [ ] Can explain: What is attention and why does it matter?
- [ ] Can explain: What is the KV cache and why is it important?
- [ ] Can explain: What are the three phases of LLM training?
- [ ] Can explain: Why do modern LLMs use decoder-only architecture?

---

**Next → [Day 2: Advanced Prompt Engineering](02-prompt-engineering.md)**
