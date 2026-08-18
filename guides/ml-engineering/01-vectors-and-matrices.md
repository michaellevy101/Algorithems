# Vectors & Matrices for ML

> **🎯 Goal**: Understand vectors as data, dot products as similarity, and matrices as transformations
> **⏱ Time**: 1.5–2h study + 30min exercises
> **📦 Deliverable**: Intuition for how data becomes math and how matrices transform it

---

## Table of Contents

1. [Vectors as Data](#1-vectors-as-data)
2. [Dot Product as Similarity](#2-dot-product-as-similarity)
3. [Matrix Operations](#3-matrix-operations)
4. [Linear Transformations & the Bias Term](#4-linear-transformations--the-bias-term)
5. [Exercises](#5-exercises)
6. [Quick Reference Card](#quick-reference-card)

---

> 📺 **Companion Video Series**: This chapter pairs with 3Blue1Brown's
> [Essence of Linear Algebra](https://www.youtube.com/playlist?list=PLZHQObOWTQDPD3MizzM2xVFitgF8hE_ab) —
> the best visual explanation of these concepts. Links to specific episodes below.

## 1. Vectors as Data

### The Intuition

> In ML, a **vector** is just an **ordered list of numbers** that describes something.
> A user described by 3 measurements — age=25, salary=80000, clicks=15 — becomes
> the vector `[25, 80000, 15]`. That's it. Not a database column, not a row —
> just **a list of numbers that together describe one entity**.

The mental model: each number is a **coordinate in a space**. A 3-number vector
is a point in 3D space. A 50-number vector is a point in 50D space (you can't
visualize it, but the math works exactly the same).

```
  What's a vector?

  NOT this:                     THIS:
  ┌──────────────────┐          A single entity described by numbers:
  │ DB column "age"  │
  │ 25               │          User A = [25, 80000, 15]
  │ 30               │                    ↑    ↑      ↑
  │ 22               │                    age  salary  clicks
  └──────────────────┘
  (that's many entities,        One user = one vector = one point in space
   one feature each)            Three users = three vectors = a matrix
```

### How It Works

Each number is a coordinate along an axis. The vector IS a point in that space:

```
  salary
  80k |         * User A = [25, 80k, 15]
      |
  60k |    * User B = [30, 60k, 42]
      |
  40k |              * User C = [22, 40k, 5]
      |
      +--------+--------+---------> age
      20      25       30

  (clicks axis points "out of the screen" — 3rd dimension)
```

**Key concept — dimensionality**:
- 3 features → 3D space (you can draw it)
- 50 features → 50D space (can't draw it, but math is identical)
- 768 features → 768D space (this is what BERT embeddings use!)

If you have 1000 users with 50 features each, you stack all vectors into a
**matrix** with shape `(1000, 50)`: 1000 rows (one per user), 50 columns
(one per feature).

```
  Matrix = many vectors stacked:

  User 0:  [25,  80000, 15, ...]   ← vector (1×50)
  User 1:  [30,  60000, 42, ...]   ← vector (1×50)
  User 2:  [22,  40000,  5, ...]   ← vector (1×50)
  ...
  User 999:[28,  75000, 33, ...]   ← vector (1×50)

  Shape: (1000, 50)  — "1000 points in 50-dimensional space"
```

### The Geometric View (3Blue1Brown)

A vector isn't just a list of numbers — it's an **arrow from the origin**. The numbers
are *instructions*: "walk 3 steps along the x-axis (î), then 2 steps along the y-axis (ĵ)."

The standard basis vectors î = [1,0] and ĵ = [0,1] define the coordinate system.
Every vector is a **linear combination** of these basis vectors:

```
  [3, 2] = 3 × î + 2 × ĵ = 3 × [1,0] + 2 × [0,1]
```

Different basis vectors = different coordinate system = different numbers for the same point.

> 📺 **Watch**: [3Blue1Brown — Vectors, what even are they?](https://www.youtube.com/watch?v=fNk_zzaMoSs) — see how coordinates are instructions, not just a list

### Recipe

```python
import numpy as np

# One user = one vector (a point in 3D feature space)
user_a = np.array([25, 80000, 15])

# Many users = a matrix (each row is one vector)
users = np.array([
    [25, 80000, 15],   # User A
    [30, 60000, 42],   # User B
    [22, 40000,  5],   # User C
])
print(users.shape)  # (3, 3) → 3 users, 3 features
```

### When to Use

Every time you feed data into an ML model, you're passing vectors. Feature
engineering (Chapter 5) is really just "pick better numbers to describe your
entity" — or in vector terms, "choose better coordinates in your space."

---

## 2. Dot Product as Similarity

### The Intuition (Backend Analogy)

> Imagine a dating app where users rate 5 interests on a scale of -1 (hate) to +1
> (love). To find compatible users, you **multiply their scores feature-by-feature
> and sum**. When both users love the same thing (+1 × +1 = +1) — match! When one
> loves and the other hates (+1 × -1 = -1) — mismatch! The final sum is the
> **compatibility score**. That's the dot product.

### How It Works — Step by Step

Two users rate 5 interests (hiking, coding, cooking, gaming, music):

```
  Alice = [+0.9, +0.8, -0.3, +0.1, +0.7]   (loves hiking, coding, music)
  Bob   = [+0.7, +0.9, -0.5, +0.2, +0.6]   (similar tastes!)
  Carol = [-0.8, -0.6, +0.9, +0.8, -0.3]   (opposite tastes)

  dot(Alice, Bob):
    hiking:  +0.9 × +0.7 = +0.63   ← both like it → positive
    coding:  +0.8 × +0.9 = +0.72   ← both like it → positive
    cooking: -0.3 × -0.5 = +0.15   ← both dislike → positive!
    gaming:  +0.1 × +0.2 = +0.02   ← both neutral → ~zero
    music:   +0.7 × +0.6 = +0.42   ← both like it → positive
                           ------
    TOTAL                 = +1.94   ← HIGH = very compatible ✓

  dot(Alice, Carol):
    hiking:  +0.9 × -0.8 = -0.72   ← Alice likes, Carol hates → negative!
    coding:  +0.8 × -0.6 = -0.48   ← opposite → negative
    cooking: -0.3 × +0.9 = -0.27   ← opposite → negative
    gaming:  +0.1 × +0.8 = +0.08
    music:   +0.7 × -0.3 = -0.21   ← opposite → negative
                           ------
    TOTAL                 = -1.60   ← NEGATIVE = incompatible ✗
```

**The rule**: positive dot product = similar direction, zero = unrelated,
negative = opposite.

### The Scale Problem

When features have different units/scales, the big ones dominate the dot product:

```
  User A = [age=25,  salary=80000,  clicks=15]
  User B = [age=30,  salary=60000,  clicks=42]

  dot(A, B) = (25×30) + (80000×60000) + (15×42)
            =  750   + 4,800,000,000  +  630
              ^^^^^^^^^^^^^^^^^^^^^^^^^^
              Salary drowns everything else!
              Age and clicks are irrelevant to the result.
```

### Cosine Similarity — Fixing the Scale Problem

**What it does**: shrink both vectors to **length 1** first, THEN dot product.
This strips away magnitude and compares only the **pattern** (direction).

**Why it works** — step by step:

```
  Step 1: Compute the "length" (norm) of each vector

    ||A|| = sqrt(25² + 80000² + 15²) ≈ 80000
    ||B|| = sqrt(30² + 60000² + 42²) ≈ 60000

    The ||...|| notation just means "length" — distance from origin to the point.
    (More on norms in Section 6.)

  Step 2: Divide each vector by its length → "unit vector" (length = 1)

    A/||A|| = [25/80000,  80000/80000,  15/80000]
            = [0.0003,    1.0000,       0.0002  ]    ← length is now 1

    B/||B|| = [30/60000,  60000/60000,  42/60000]
            = [0.0005,    1.0000,       0.0007  ]    ← length is now 1

  Step 3: Dot product of unit vectors = cosine similarity

    dot(A/||A||, B/||B||) ≈ 0.9999   ← almost identical direction
```

**The analogy**: Two servers — one handles 1M req/day, another 100 req/day.
Raw request counts are useless for comparison. But convert to **percentages**
(GET: 60%, POST: 30%, DELETE: 10%) and now you can compare the *pattern*
regardless of total volume. Dividing by the norm is like converting to
percentages — you remove the scale and keep the shape.

```
                  dot(A, B)
  cos(θ) = ─────────────────────
            ||A|| × ||B||

  This is EQUIVALENT to:

    cos(θ) = dot( A/||A||,  B/||B|| )
                  ───────   ───────
                  unit vec   unit vec
                  (len=1)    (len=1)

  Range:  +1  = same direction (same pattern of features)
           0  = perpendicular (completely unrelated)
          -1  = opposite direction (opposite pattern)
```

### Dot Product Duality (3Blue1Brown)

The dot product has **two equivalent interpretations**:

1. **Algebraic**: Multiply matching entries and add: `a·b = Σᵢ aᵢbᵢ`
2. **Geometric**: Project one vector onto the other, multiply by its length:
   `a·b = |proj_b(a)| × ||b|| = ||a|| × ||b|| × cos(θ)`

These are the SAME number. The projection interpretation is why the dot product
measures similarity — if two vectors point the same way, the projection is long (large
dot product). If they're perpendicular, the projection is zero.

> 📺 **Watch**: [3Blue1Brown — Dot products and duality](https://www.youtube.com/watch?v=LyGKycYT2v0) — the deep connection between "multiply and sum" and "projection"

### Recipe

```python
from numpy import array, dot
from numpy.linalg import norm

alice = array([0.9, 0.8, -0.3, 0.1, 0.7])
bob   = array([0.7, 0.9, -0.5, 0.2, 0.6])
carol = array([-0.8, -0.6, 0.9, 0.8, -0.3])

cosine = lambda a, b: dot(a, b) / (norm(a) * norm(b))
print(f"Alice↔Bob:   {cosine(alice, bob):.2f}")    # ~0.98 (similar)
print(f"Alice↔Carol: {cosine(alice, carol):.2f}")   # ~-0.90 (opposite)
```

### When to Use

- **Recommendation engines**: "users who liked similar things" = high cosine similarity
- **Search**: query vector vs. document vectors — rank by cosine score
- **Embeddings**: comparing sentence/word vectors (powers semantic search)
- **Rule of thumb**: use cosine similarity when feature scales differ (almost always).
  Use raw dot product only when magnitude matters (e.g., "more active = better match")

---

## 3. Matrix Operations

### The Intuition (Backend Analogy)

> A matrix is a **batch operation** — like a SQL `UPDATE` that transforms every row
> through the same formula. Instead of looping through records one at a time, you
> express the whole transformation as a single matrix multiply.

### How It Works

**Matrix × Vector = transform one data point**

Like passing one request through a middleware:

```
  Transformation       Input         Output
  Matrix (2×3)        Vector (3)    Vector (2)

  [ 1  0  0 ]       [ 25    ]     [ 25  ]    <- age (kept)
  [ 0  0  1 ]   ×   [ 80000 ]  =  [ 15  ]    <- clicks (kept)
                     [ 15    ]
                                   salary dropped!
  "SELECT age, clicks FROM users"  — as a matrix operation
```

**Matrix × Matrix = composing transformations**

Like chaining two middleware functions — the combined effect is a single matrix:

```
  Transform B    ×    Transform A    =    Combined (B∘A)
  (scale)             (rotate)            (rotate then scale)

  Applied right to left: A runs first, then B
  Same idea as: app.use(A); app.use(B);
```

**Transpose = switching rows ↔ columns**

Like pivoting a database table:

```
  Original (2×3):          Transposed (3×2):

  [ 1  2  3 ]             [ 1  4 ]
  [ 4  5  6 ]      ->     [ 2  5 ]
                           [ 3  6 ]

  Users × Features   ->   Features × Users
```

### Matrix = Transformation of Space (3Blue1Brown)

This is the single most important insight in linear algebra:

**The columns of a matrix tell you where the basis vectors land.**

When you multiply a matrix W by a vector v, you're not "doing arithmetic" — you're
**transforming space**. Every point moves. The grid lines stay parallel and evenly
spaced (that's what makes it *linear*), but they can stretch, rotate, shear, or flip.

**Matrix multiplication = composition of transformations.** When you compute B × A,
you're asking: "What single matrix has the same effect as first applying A, then B?"
The answer is a new matrix whose columns are where the original basis vectors end up
after BOTH transformations.

**The determinant = how much the transformation scales area.**
- det > 1: area grows (stretching)
- det = 1: area preserved (rotation)
- det = 0: space is squished to a lower dimension (information lost!)
- det < 0: orientation is flipped (like looking in a mirror)

> 📺 **Watch**: [3Blue1Brown — Linear transformations and matrices](https://www.youtube.com/watch?v=kYB8IZa5AuE) and [Matrix multiplication as composition](https://www.youtube.com/watch?v=XkY2DOUCWMU)

### Recipe

```python
import numpy as np

users = np.array([[25, 80000, 15],
                  [30, 60000, 42]])

# Matrix × vector: transform one user
projection = np.array([[1, 0, 0],
                       [0, 0, 1]])  # keep age + clicks
print(projection @ users[0])        # [25, 15]

# Matrix × matrix: transform all users at once
print(projection @ users.T)         # (2, 2) result

# Transpose
print(users.T.shape)                # (3, 2) — features × users
```

### When to Use

- **Neural networks**: every layer is a matrix multiply + activation function
- **Dimensionality reduction**: project 50 features down to 10 with a `(50×10)` matrix
- **Batch processing**: transform thousands of data points in one operation (GPU-friendly)
- **Data reshaping**: transpose when APIs expect `features × samples` instead of `samples × features`

---

## 4. Linear Transformations & the Bias Term

### The Intuition (Backend Analogy)

> A linear transformation is a **middleware that reshapes every request** passing
> through it. The middleware can scale fields, rotate the coordinate system, or
> project data into fewer dimensions — but it always does the same thing to every
> input. No if/else, no special cases. That's what "linear" means.

### How It Works

Every matrix multiplication is a linear transformation. Here's what different
transformations look like geometrically:

```
  ORIGINAL DATA          SCALED (stretch x2)     ROTATED 45°

      *  *                   *     *                 *
    *      *               *         *             *   *
    *      *       ->      *         *     ->      *     *
      *  *                   *     *               *   *
                                                     *
  (unit square)          (wider rectangle)       (diamond)
```

Common transformation types:

```
  +-------------------+---------------------------+----------------------+
  | Transformation    | What it does              | ML example           |
  +-------------------+---------------------------+----------------------+
  | Scaling           | Stretch/shrink axes       | Feature normalization|
  | Rotation          | Change basis directions   | PCA rotation         |
  | Projection        | Drop dimensions           | Dimensionality reduc.|
  | Shear             | Skew along an axis        | Data augmentation    |
  +-------------------+---------------------------+----------------------+
```

A neural network layer does: `output = activation(W × input + b)`

> 📺 **Watch**: [3Blue1Brown — Linear transformations and matrices](https://www.youtube.com/watch?v=kYB8IZa5AuE) — see how grid lines must stay parallel and evenly spaced (that's what "linear" means)

The matrix `W` is the linear transformation. But what's `b`?

### The Bias Term — Why W×x Isn't Enough

Without bias, every transformation must pass through the origin (0,0).
That's like saying every regression line must cross zero — it can't.

```
  Without bias (W×x only):       With bias (W×x + b):

        |   /                         |    /
        |  /  Must pass               |   / Can shift
        | /   through (0,0)           |  /  anywhere!
  ------+/----------->          ------+---------->
        |                             /
        |                            / ← b shifts the line up/down
```

`b` is a vector added AFTER the matrix multiply. It lets the model
**shift the output** independently of the input. Without it, a model
with all-zero inputs MUST produce all-zero outputs.

```
  Full neural network layer:

  output = activation( W × input + b )
                       ^^^^^       ^
                       rotate/     shift
                       scale       (translate)

  W has shape (out_features, in_features)
  b has shape (out_features,)  ← one bias value per output neuron
```

Training learns BOTH `W` and `b`. In PyTorch, `nn.Linear(4, 3)` creates
a layer with a (3×4) weight matrix AND a (3,) bias vector by default.

```
  Input (4 features)          Layer W (4×3)           Output (3 features)

  [ x1 ]                 [ w11  w12  w13 ]
  [ x2 ]       ->        [ w21  w22  w23 ]     ->    [ y1 ]
  [ x3 ]                 [ w31  w32  w33 ]            [ y2 ]
  [ x4 ]                 [ w41  w42  w43 ]            [ y3 ]

  (4-dim space)          (learned transform)          (3-dim space)
```

### Recipe

```python
import numpy as np

# Scaling transform (like feature normalization)
scaler = np.diag([1/100, 1/100000, 1/50])  # normalize ranges
user = np.array([25, 80000, 15])
normalized = scaler @ user  # [0.25, 0.8, 0.3]

# Projection: 4 features -> 2 features (simulates a layer)
W = np.random.randn(2, 4)  # learned weights
x = np.array([0.5, 0.8, 0.3, 0.1])
output = W @ x              # 2-dimensional result
```

### When to Use

- **Every neural network layer** is a linear transformation followed by a non-linearity
- **PCA** is finding the best rotation to align axes with data variance
- **Embeddings** (word2vec, user embeddings) are learned linear projections
- If you can express an operation as matrix math, the GPU can parallelize it

---

## 5. Exercises

### Exercise 1: Conceptual — Data Shape

You have 1000 users, each described by 50 features. What is the shape of your data
matrix? If you multiply it by a 50×10 matrix, what did you just do to the data?

<details>
<summary><strong>Solution</strong></summary>

The data matrix has shape **(1000, 50)** — 1000 rows (users), 50 columns (features).

Multiplying `(1000×50) × (50×10)` yields a **(1000×10)** matrix. You still have 1000
users, but each is now described by only **10 features** instead of 50.

What you did: **dimensionality reduction**. The 50×10 matrix is a projection that
compresses each user’s 50-dimensional representation into a 10-dimensional one.
This is exactly what happens in PCA or when an autoencoder’s bottleneck layer has
10 units.

```
  1000 users × 50 features          1000 users × 10 features
  +------------------+              +--------+
  |                  |    × (50×10) |        |
  |    (1000×50)     |   -------->  | (1000  |
  |                  |              |   ×10) |
  +------------------+              +--------+
```

</details>

---

### Exercise 2: Real-World Scenario — Normalization Bug

Your recommendation system uses dot product similarity but returns irrelevant results
for users with very different activity levels. A power user with 500 clicks gets
matched to another power user with 480 clicks, even though they have completely
different interests. What’s wrong and how would you fix it?

<details>
<summary><strong>Solution</strong></summary>

**The problem**: raw dot product is sensitive to **magnitude**, not just direction.
Users with high activity (large feature values) will always have high dot products
with each other, regardless of whether their interests align.

```
  Power User A: [500, 10,  2]   (clicks, sports, cooking)
  Power User B: [480,  2, 10]   (clicks, sports, cooking)
  Casual User C: [ 20,  8,  1]

  dot(A, B) = 240000 + 20 + 20 = 240040   <- "most similar" (WRONG)
  dot(A, C) =  10000 + 80 +  2 =  10082   <- lower (but C likes sports too!)
```

**Fixes (pick one or combine)**:

1. **Use cosine similarity** instead of dot product — normalizes out magnitude:
   ```python
   cos_sim = np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b))
   ```

2. **Feature-level normalization** — scale each feature to [0, 1] or zero mean/unit
   variance before computing similarity, so no single feature dominates.

3. **Separate magnitude from direction** — store activity level and interest profile
   as independent signals. Use interest profile (normalized) for matching, activity
   level for ranking.

The core lesson: **always normalize when you care about direction (interests), not
magnitude (activity level)**.

</details>

## Quick Reference Card

```
+---------------------+----------------------------------+----------------------------+
| Concept             | What It Is                       | ML Use Case                |
+---------------------+----------------------------------+----------------------------+
| Vector              | Ordered list of numbers          | One data point             |
| Dot product         | Multiply-and-sum                 | Similarity score           |
| Cosine similarity   | Dot product of unit vectors      | Recommendations, search    |
| Matrix multiply     | Batch transformation             | Neural network layer       |
| Bias term (b)       | Shift output away from origin    | Every NN layer: Wx + b     |
+---------------------+----------------------------------+----------------------------+
```
