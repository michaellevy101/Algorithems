# Eigenvalues, Norms & Tensor Operations

> **🎯 Goal**: Master the advanced linear algebra tools used daily in ML engineering
> **⏱ Time**: 1.5–2h study + 30min exercises
> **📦 Deliverable**: Working knowledge of eigenvalues (for PCA), norms, broadcasting, sparse vectors, and tensor shapes

*Continues from [Chapter 01 — Vectors & Matrices](01-vectors-and-matrices.md)*

---

## Table of Contents

1. [Eigenvalues & Eigenvectors Intuition](#1-eigenvalues--eigenvectors-intuition)
2. [Norms — Measuring Vector Size](#2-norms--measuring-vector-size)
3. [Distance Metrics — How to Measure “Closeness”](#3-distance-metrics--how-to-measure-closeness)
4. [Broadcasting — NumPy’s Superpower](#4-broadcasting--numpys-superpower)
5. [Sparse vs Dense Vectors](#5-sparse-vs-dense-vectors)
6. [Matrix Rank & Linear Dependence](#6-matrix-rank--linear-dependence)
7. [Tensors & Shape Operations](#7-tensors--shape-operations)
8. [Exercises](#8-exercises)
9. [Quick Reference Card](#quick-reference-card)

---

> 📺 **Companion Video Series**: This chapter pairs with 3Blue1Brown's
> [Essence of Linear Algebra](https://www.youtube.com/playlist?list=PLZHQObOWTQDPD3MizzM2xVFitgF8hE_ab) —
> the best visual explanation of these concepts. Links to specific episodes below.

## 1. Eigenvalues & Eigenvectors Intuition

### The Intuition (Backend Analogy)

> Eigenvectors are the **principal highways in a road network**. When you apply a
> transformation (traffic flow) to a city, most streets change direction. But the
> highways? They stay pointing the same way — they just get busier or quieter. The
> eigenvalue tells you the traffic multiplier on each highway.

### How It Works

For a matrix `A`, an eigenvector `v` satisfies: `A × v = λ × v`

The transformation only **scales** the eigenvector, never rotates it:

```
  Regular vector:                 Eigenvector:

       * (original)                    * (original)
      /                                |
     /  -> rotated & scaled            |  -> only scaled (same direction)
    /                                  |
   * (after A×v)                       * (after A×v = λ×v)

   Direction changed!                  Direction preserved!
   NOT an eigenvector                  IS an eigenvector (λ = scale factor)
```

In ML, **eigenvectors of the covariance matrix** point along the directions of
maximum variance in your data:

```
                     eigenvector 1 (most variance)
                    /
       * *  *  *  /  *  *
     *   *  *   */ *   *
    *  *   *   /  *  *
   * *  * * * / *   *
    *   *   */ *  *
       *   /  *
           |
           eigenvector 2 (less variance)

  PCA says: "keep eigenvector 1, drop eigenvector 2"
  -> dimensionality reduction with minimal info loss
```

### The Geometric View (3Blue1Brown)

During a linear transformation, most vectors get **knocked off their span** — they
end up pointing in a completely different direction. But some special vectors only
get **stretched or squished** along their original line. These are the **eigenvectors**.

The **eigenvalue** λ tells you the stretching factor:
- λ = 2: the eigenvector gets doubled in length
- λ = 0.5: the eigenvector gets halved
- λ = -1: the eigenvector gets flipped (reversed direction)
- λ = 0: the eigenvector gets squished to zero (dimension lost)

**Why this matters for ML (PCA):** The covariance matrix's eigenvectors point in the
directions of maximum variance in your data. The eigenvalues tell you HOW MUCH
variance each direction captures. PCA = find the eigenvectors, keep the ones with the
biggest eigenvalues, throw away the rest.

> 📺 **Watch**: [3Blue1Brown — Eigenvectors and eigenvalues](https://www.youtube.com/watch?v=PFDu9oVAE-g) — the best visual explanation of why eigenvectors are special

### Recipe

```python
import numpy as np

# Covariance matrix of some 2D data
data = np.random.randn(100, 2) @ np.array([[3, 1], [1, 1]])
cov = np.cov(data.T)

# Eigenvectors = principal directions, eigenvalues = variance along each
eigenvalues, eigenvectors = np.linalg.eigh(cov)
print(f"Variance along each axis: {eigenvalues}")
print(f"Principal direction: {eigenvectors[:, -1]}")  # largest eigenvalue
```

### When to Use

- **PCA**: eigenvalue = variance captured. Drop dimensions with small eigenvalues.
- **Spectral clustering**: eigenvectors of the graph Laplacian reveal cluster structure
- **Stability check**: near-zero eigenvalues = your matrix is nearly singular (see Section 9)
- You rarely compute these by hand — but knowing what they MEAN lets you debug PCA
  and pick the right number of components

---

## 2. Norms — Measuring Vector Size

### The Intuition

> A **norm** is just the **length** of a vector — how far the point is from
> the origin. You already saw `||A||` in the cosine formula — that's the L2
> norm. But there are different ways to measure "length," and which you pick
> changes how your model behaves.

### Three Norms You Need to Know

```
  L1 Norm (Manhattan / "taxi cab"):     L2 Norm (Euclidean / "straight line"):
  |x₁| + |x₂| + ... + |xₙ|            sqrt(x₁² + x₂² + ... + xₙ²)

  Walk the grid:                        Fly direct:

        B                                    B
        |                                   /
        |___                               /
            |                             /
            |___                         /
                A                       A

  Distance = 4 + 2 = 6                 Distance = sqrt(16+4) ≈ 4.47

  L-infinity Norm:
  max(|x₁|, |x₂|, ..., |xₙ|)          Just the biggest dimension.
```

### Why Norms Matter — Regularization

In ML, norms are used as **penalties** on model weights to prevent overfitting.
The norm you choose controls WHAT the penalty does:

```
  ┌─────────┬──────────────────┬────────────────────────────────┐
  │ Norm    │ Regularization   │ Effect on model weights        │
  ├─────────┼──────────────────┼────────────────────────────────┤
  │ L1      │ Lasso            │ Pushes weights to EXACTLY 0    │
  │         │                  │ → automatic feature selection!  │
  ├─────────┼──────────────────┼────────────────────────────────┤
  │ L2      │ Ridge            │ Shrinks all weights toward 0   │
  │         │                  │ → no zeros, just smaller        │
  ├─────────┼──────────────────┼────────────────────────────────┤
  │ L1 + L2 │ ElasticNet       │ Both effects combined          │
  └─────────┴──────────────────┴────────────────────────────────┘
```

WHY does L1 create zeros but L2 doesn't? Geometric intuition:

```
  L1 constraint:         L2 constraint:

       /\                    ___
      /  \                 /     \
     / ←hit\              |  ←hit |
      \  /                 \     /
       \/                    ---

  Diamond has CORNERS      Circle is smooth — almost
  on axes → the optimal    never hits exactly on an
  solution often lands     axis → weights shrink but
  on a corner → some       rarely reach zero.
  weight = exactly 0.
```

### Recipe

```python
import numpy as np

v = np.array([3, -4, 0, 2])

l1   = np.linalg.norm(v, ord=1)    # |3|+|-4|+|0|+|2| = 9
l2   = np.linalg.norm(v, ord=2)    # sqrt(9+16+0+4) ≈ 5.39
linf = np.linalg.norm(v, np.inf)   # max(|3|,|-4|,|0|,|2|) = 4

# Normalize to unit vector (what cosine similarity does internally)
unit_v = v / np.linalg.norm(v)     # now ||unit_v|| = 1.0
```

### When to Use

- **L2 norm**: default for everything — distances, cosine similarity, Ridge
- **L1 norm**: when you want **sparsity** (feature selection via Lasso)
- **L-infinity norm**: adversarial ML, constraint checking

---

## 3. Distance Metrics — How to Measure "Closeness"

### The Intuition

> Different metrics answer different questions about similarity.
> There's no single "best" one — it depends on what you're comparing.

### The Metrics You'll Actually Use

```
  ┌───────────────────┬────────────────────┬──────────────────────────────────┐
  │ Metric            │ Formula            │ When to Use                      │
  ├───────────────────┼────────────────────┼──────────────────────────────────┤
  │ Euclidean (L2)    │ sqrt(Σ(aᵢ-bᵢ)²)  │ Default. Actual geometric        │
  │                   │                    │ distance between two points.     │
  ├───────────────────┼────────────────────┼──────────────────────────────────┤
  │ Cosine Similarity │ dot(a,b)           │ When DIRECTION matters, not      │
  │                   │ / (||a||·||b||)    │ magnitude. Text, embeddings.     │
  ├───────────────────┼────────────────────┼──────────────────────────────────┤
  │ Manhattan (L1)    │ Σ|aᵢ - bᵢ|        │ High-dimensional or sparse data. │
  │                   │                    │ More robust to outliers.         │
  ├───────────────────┼────────────────────┼──────────────────────────────────┤
  │ Dot Product       │ Σ(aᵢ · bᵢ)        │ When magnitude IS meaningful.    │
  │                   │                    │ "More active = better match."    │
  ├───────────────────┼────────────────────┼──────────────────────────────────┤
  │ Jaccard           │ |A∩B| / |A∪B|     │ Binary/set data. "How much       │
  │                   │                    │ overlap between two sets?"       │
  └───────────────────┴────────────────────┴──────────────────────────────────┘
```

### Quick Decision Guide

```
  Are your vectors already normalized (length = 1)?
  ├── YES → Dot product (fastest, same result as cosine)
  └── NO
       ├── Care about direction or magnitude?
       │   ├── Direction only → Cosine similarity
       │   └── Magnitude too → Euclidean distance
       └── Is data sparse or very high-dimensional?
           ├── YES → Manhattan (L1) or Cosine
           └── NO  → Euclidean (L2)
```

### Recipe

```python
from sklearn.metrics.pairwise import cosine_similarity, euclidean_distances
import numpy as np

a = np.array([[1, 2, 3]])
b = np.array([[4, 5, 6]])

cos  = cosine_similarity(a, b)[0, 0]     # direction similarity
euc  = euclidean_distances(a, b)[0, 0]    # geometric distance
manh = np.sum(np.abs(a - b))              # city-block distance
```

---

## 4. Broadcasting — NumPy's Superpower

### The Intuition

> In normal programming, adding a 3-element array to a 100\u00d73 matrix is a type
> error. In NumPy, it **automatically expands** the smaller array to match \u2014
> like auto-scaling a service to handle the load. This is called **broadcasting**,
> and misunderstanding it causes the most insidious ML bugs.

### How It Works

```
  Normal programming:                NumPy broadcasting:

  [1, 2, 3] + [[10, 20, 30],       [1, 2, 3] + [[10, 20, 30],
                [40, 50, 60]]                     [40, 50, 60]]
  = ERROR! shapes don't match       = [[11, 22, 33],
                                       [41, 52, 63]]

  NumPy "stretches" [1,2,3] into:   [[1, 2, 3],
                                      [1, 2, 3]]
  Then adds element-wise.
```

### Broadcasting Rules

```
  Compare shapes right-to-left. Dimensions are compatible when:
  1. They are equal, OR
  2. One of them is 1 (it gets "stretched")

  Example:
    Matrix:  (100, 3)     100 users, 3 features
    Vector:       (3,)    mean of each feature
    \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
    Result:  (100, 3)     \u2714 vector broadcast across all 100 rows

  Another:
    A: (64, 256, 1)
    B:       (256,)       \u2190 same as (1, 1, 256)
    Result: (64, 256, 256) \u2714

  TRAP:
    A: (100, 3)
    B: (100,)             3 \u2260 100 and neither is 1 \u2192 ERROR!
    Fix: B.reshape(100, 1) makes shape (100, 1), then broadcasts to (100, 3)
```

### Why It Matters

Without broadcasting, you'd write slow Python loops. With it, one line
replaces a loop AND runs on the GPU:

```python
# WITHOUT broadcasting (slow, wrong approach):
for i in range(len(users)):
    users[i] = (users[i] - mean) / std     # Python loop = slow

# WITH broadcasting (fast, correct):
users = (users - mean) / std                # mean=(3,), users=(100,3)
                                            # NumPy broadcasts mean to (100,3)
```

### Common Broadcasting Patterns

```python
import numpy as np

data = np.random.randn(100, 3)       # 100 users, 3 features

# Center data (subtract mean of each feature)
mean = data.mean(axis=0)              # shape (3,)
centered = data - mean                # (100,3) - (3,) = (100,3) \u2714

# Normalize each feature
std = data.std(axis=0)                # shape (3,)
normalized = (data - mean) / std      # broadcasts TWICE

# Add bias vector to all rows
bias = np.array([0.1, -0.2, 0.5])    # shape (3,)
result = data + bias                  # (100,3) + (3,) = (100,3) \u2714
```

### The #1 Broadcasting Bug

```python
# You expect column-wise operation but get garbage:
a = np.array([1, 2, 3])              # shape (3,)  -- it's 1D, not a column!
b = np.array([10, 20, 30])           # shape (3,)

outer = a.reshape(3, 1) * b          # (3,1) * (3,) = (3,3) outer product!
inner = a * b                         # (3,) * (3,) = (3,) element-wise
# Forgetting reshape is the most common broadcasting mistake.
```

---

## 5. Sparse vs Dense Vectors

### The Intuition

> In security/identity systems, a user's permission vector might have 10,000
> possible permissions but only 15 are active. Storing 10,000 numbers (9,985
> of which are zero) wastes memory. A **sparse** representation stores only
> the non-zero entries.

### Dense vs Sparse

```
  Dense vector (stores ALL values):
  [0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, ...]
  Memory: 10,000 floats = 40 KB

  Sparse vector (stores only non-zeros):
  {3: 1.0, 8: 1.0, 13: 1.0}
  Memory: 3 entries = ~24 bytes

  Same information, ~1700x less memory!
```

### When Data is Sparse

```
  \u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u252c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u252c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510
  \u2502 Data Type              \u2502 Sparsity        \u2502 Example               \u2502
  \u251c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u253c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u253c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2524
  \u2502 One-hot encoded text   \u2502 >99% zeros      \u2502 50K vocab, 20 words    \u2502
  \u2502 User permissions       \u2502 >99% zeros      \u2502 10K permissions, 15 on \u2502
  \u2502 Recommendation matrix  \u2502 >95% zeros      \u2502 Users \u00d7 Products        \u2502
  \u2502 Network adjacency      \u2502 >99% zeros      \u2502 Who connects to whom   \u2502
  \u2502 TF-IDF features        \u2502 >95% zeros      \u2502 Document \u00d7 Word          \u2502
  \u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2534\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2534\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518
```

### Recipe

```python
from scipy import sparse
import numpy as np

# Dense: wastes memory for mostly-zero data
dense = np.zeros(10000)
dense[3] = 1.0; dense[8] = 1.0; dense[13] = 1.0

# Sparse: stores only non-zeros
sp = sparse.csr_matrix(dense)        # Compressed Sparse Row format
print(sp.nnz)                        # 3 (number of non-zeros)
print(sp.data)                       # [1., 1., 1.]

# Convert between formats
back_to_dense = sp.toarray()         # when you need full array

# Sparse matrix multiply (sklearn supports sparse input natively)
from sklearn.linear_model import LogisticRegression
X_sparse = sparse.random(1000, 10000, density=0.01)  # 1% non-zero
model = LogisticRegression().fit(X_sparse, y)         # works directly!
```

### Engineering Rules

- **>50% zeros** \u2192 consider sparse format
- **>90% zeros** \u2192 always use sparse (memory AND speed gain)
- **CSR** (Compressed Sparse Row): fast for row slicing \u2014 use for ML training
- **CSC** (Compressed Sparse Column): fast for column slicing
- **COO** (Coordinate): fast for building, then convert to CSR
- scikit-learn, XGBoost, and LightGBM all accept sparse input natively

## 6. Matrix Rank & Linear Dependence

### The Intuition

> If you have a "height_cm" column AND a "height_inches" column, one is just
> 2.54 \u00d7 the other. They carry the SAME information. The matrix **rank** tells
> you how many TRULY independent columns you have.

### What is Rank?

```
  Full rank (all columns independent):

  height  weight  age
  [170,    65,    25 ]    \u2190 no column is a combo of others
  [180,    80,    30 ]       rank = 3 (all 3 columns are independent)
  [160,    55,    22 ]

  Rank-deficient (columns are dependent):

  height_cm  height_in  weight
  [170,       66.9,      65 ]    \u2190 height_in = height_cm / 2.54
  [180,       70.9,      80 ]       rank = 2 (only 2 independent columns!)
  [160,       63.0,      55 ]       the third adds NO new information
```

### Why It Matters in ML

```
  Problem 1: SINGULAR MATRIX (can't invert)

    Linear regression closed-form: w = (X\u1d40X)\u207b\u00b9 X\u1d40y
    If X has dependent columns \u2192 X\u1d40X is singular \u2192 (X\u1d40X)\u207b\u00b9 doesn't exist
    \u2192 model crashes or gives garbage weights!

  Problem 2: UNSTABLE TRAINING

    Near-dependent columns \u2192 tiny changes in data cause huge weight changes
    \u2192 model is unreliable and doesn't generalize

  Problem 3: WASTED COMPUTATION

    Redundant features = wasted memory + slower training + no benefit
```

### How to Detect and Fix

```python
import numpy as np

X = np.array([[170, 66.9, 65],
              [180, 70.9, 80],
              [160, 63.0, 55]])

print(np.linalg.matrix_rank(X))     # 2 (not 3!) \u2192 rank-deficient

# Fix 1: Drop redundant columns (use correlation matrix to find them)
corr = np.corrcoef(X.T)             # if corr > 0.95, columns are ~dependent

# Fix 2: Use regularization (Ridge/Lasso handle rank-deficiency gracefully)

# Fix 3: Use PCA to extract independent components
```

### The Geometric View (3Blue1Brown)

The **rank** of a matrix is the number of dimensions in its output. Think of it
through the transformation lens:

- **Rank 2** (full rank for 2×2): space stays 2D — no information lost
- **Rank 1**: space gets squished onto a line — lost a dimension
- **Rank 0**: everything maps to the origin — total collapse

A matrix is **singular** (not invertible) when its determinant is 0 — meaning
the transformation squishes space to a lower dimension. You can't "un-squish"
because the information about which dimension was lost is gone.

> 📺 **Watch**: [3Blue1Brown — Inverse matrices, column space and null space](https://www.youtube.com/watch?v=uQhTuRlWMxw) — understand why singular matrices can't be inverted

---

## 7. Tensors & Shape Operations

### The Intuition

> Vectors are 1D, matrices are 2D. In deep learning, you work with **tensors** \u2014
> N-dimensional arrays. A batch of color images is 4D:
> `[batch_size, channels, height, width]`. Getting shapes right is ~50% of
> debugging in PyTorch.

### The Dimension Hierarchy

```
  Scalar:  42                          0D  (just a number)
  Vector:  [1, 2, 3]                   1D  shape: (3,)
  Matrix:  [[1,2],[3,4]]               2D  shape: (2, 2)
  Tensor:  [[[1,2],[3,4]],             3D  shape: (2, 2, 2)
            [[5,6],[7,8]]]
```

### Real-World Tensor Shapes

```
  \u250c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u252c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u252c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2510
  \u2502 Data Type          \u2502 Shape                          \u2502 Example \u2502
  \u251c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u253c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u253c\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2524
  \u2502 Tabular data       \u2502 (batch, features)              \u2502 (64, 50) \u2502
  \u2502 Grayscale image    \u2502 (batch, 1, H, W)              \u2502 (32, 1, 28, 28) \u2502
  \u2502 Color image        \u2502 (batch, 3, H, W)              \u2502 (32, 3, 224, 224) \u2502
  \u2502 Text (token IDs)   \u2502 (batch, seq_len)              \u2502 (16, 512) \u2502
  \u2502 Text (embeddings)  \u2502 (batch, seq_len, embed_dim)   \u2502 (16, 512, 768) \u2502
  \u2502 Video              \u2502 (batch, frames, C, H, W)      \u2502 (8, 30, 3, 224, 224) \u2502
  \u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2534\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2534\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518
```

### The Essential Shape Operations

**reshape** \u2014 Change shape without changing data (like reinterpreting bytes):

```python
import torch

x = torch.arange(12)                 # [0,1,2,...,11]  shape: (12,)
a = x.reshape(3, 4)                  # 3 rows, 4 cols  shape: (3, 4)
b = x.reshape(2, 2, 3)               # 3D tensor       shape: (2, 2, 3)
c = x.reshape(4, -1)                 # -1 = infer      shape: (4, 3)
```

**squeeze / unsqueeze** \u2014 Remove or add dimensions of size 1:

```python
# squeeze: remove size-1 dimensions
x = torch.zeros(1, 3, 1, 5)          # shape: (1, 3, 1, 5)
x.squeeze().shape                     # (3, 5) \u2190 removed both 1s
x.squeeze(0).shape                    # (3, 1, 5) \u2190 removed only dim 0

# unsqueeze: add a size-1 dimension (needed for broadcasting!)
x = torch.tensor([1, 2, 3])          # shape: (3,)
x.unsqueeze(0).shape                  # (1, 3)  \u2190 row vector
x.unsqueeze(1).shape                  # (3, 1)  \u2190 column vector
```

**transpose / permute** \u2014 Swap dimensions:

```python
img = torch.randn(3, 224, 224)       # (C, H, W) \u2190 PyTorch format
img_np = img.permute(1, 2, 0)        # (H, W, C) \u2190 NumPy/matplotlib format

batch = torch.randn(32, 10, 768)     # (batch, seq, embed)
batch.transpose(1, 2).shape          # (32, 768, 10) \u2190 swap seq \u2194 embed
```

**view vs reshape** \u2014 In PyTorch:

```
  view():    requires contiguous memory (faster, fails if not contiguous)
  reshape(): always works (may copy data if needed)

  Rule: use reshape() unless you're optimizing hot paths.
```

### The Shape Debugging Pattern

When you get a shape error in PyTorch (and you WILL), print shapes at every step:

```python
def forward(self, x):
    print(f"input: {x.shape}")        # (32, 784)
    x = self.layer1(x)
    print(f"after layer1: {x.shape}") # (32, 256)
    x = x.reshape(32, 16, 16)        # reshape for conv
    print(f"after reshape: {x.shape}")# (32, 16, 16)
    return x
```

---

## 8. Exercises

### Exercise 1: Conceptual — Eigenvalues for PCA

You run PCA on a dataset and get eigenvalues [45.2, 12.1, 3.5, 0.8, 0.2, 0.1].
How many principal components would you keep, and what percentage of variance do
they explain?

<details>
<summary><strong>Solution</strong></summary>

Total variance = 45.2 + 12.1 + 3.5 + 0.8 + 0.2 + 0.1 = **61.9**

Cumulative variance explained:
- PC1: 45.2 / 61.9 = **73.0%**
- PC1+PC2: 57.3 / 61.9 = **92.6%**
- PC1+PC2+PC3: 60.8 / 61.9 = **98.2%**

**Keep 2–3 components.** Two components capture 92.6% of variance (a common threshold
is 90–95%). Adding a third gets you to 98.2% if you need higher fidelity.

```
  Eigenvalue:   45.2    12.1     3.5    0.8   0.2   0.1
  Cumulative:   73.0%   92.6%   98.2%  99.5%  99.8% 100%
                 ↑        ↑
              keep 2   or keep 3
```

</details>

---

### Exercise 2: Real-World Scenario — Broadcasting Bug

You’re computing per-feature z-score normalization: `(X - mean) / std`. Your data
matrix `X` has shape `(1000, 50)`, `mean` has shape `(50,)`, and `std` has shape
`(50,)`. A colleague reshapes `mean` to `(1, 1, 50)` and gets a broadcasting error.
What went wrong and how do you fix it?

<details>
<summary><strong>Solution</strong></summary>

**The problem**: `X` has 2 dimensions `(1000, 50)` but `mean` was reshaped to 3
dimensions `(1, 1, 50)`. NumPy broadcasting requires dimensions to match from the
**right side** — but 2D and 3D arrays don’t align properly here.

```
  X:      (1000, 50)
  mean:   (1, 1, 50)   <- 3D! Broadcasting tries to align:
                           (1, 1, 50) vs (1000, 50) → ERROR
```

**The fix**: `mean` with shape `(50,)` already broadcasts correctly against
`(1000, 50)` — NumPy automatically treats it as `(1, 50)`. No reshape needed:

```python
X_normalized = (X - mean) / std   # Just works!

# If you must reshape, use (1, 50) not (1, 1, 50):
mean_reshaped = mean.reshape(1, 50)   # (1, 50) broadcasts with (1000, 50)
```

**Broadcasting rule reminder**: Align shapes from the right. Dimensions must be
equal or one of them must be 1.

</details>

## Quick Reference Card

```
+---------------------+----------------------------------+----------------------------+
| Concept             | What It Is                       | ML Use Case                |
+---------------------+----------------------------------+----------------------------+
| Eigenvalue          | Variance along eigenvector       | PCA component selection    |
| L1 / L2 norm        | Vector length (taxicab / direct) | Regularization             |
| Cosine distance     | 1 − cosine similarity            | Similarity search          |
| Broadcasting        | Auto-expand shapes for math      | Vectorized NumPy/PyTorch   |
| Sparse vector       | Store only non-zero entries      | Permissions, text, graphs  |
| Matrix rank         | # of independent columns         | Detect redundant features  |
| Tensor              | N-dimensional array              | Images, text, batches      |
| reshape/squeeze     | Change tensor shape              | 50% of PyTorch debugging   |
+---------------------+----------------------------------+----------------------------+
```
