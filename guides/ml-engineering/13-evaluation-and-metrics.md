# Evaluation & Model Selection

> **🎯 Goal**: Know how to measure model quality and choose the right model
> **⏱ Time**: 3–4h study + 1h exercises
> **📦 Deliverable**: Ability to select the right metric for any ML problem and avoid common traps

---

## Table of Contents

1. [Why Accuracy Is Not Enough](#1-why-accuracy-is-not-enough)
2. [Confusion Matrix](#2-confusion-matrix)
3. [Classification Metrics](#3-classification-metrics)
4. [AUC-ROC Curve](#4-auc-roc-curve)
5. [Precision-Recall Curve](#5-precision-recall-curve)
6. [Regression Metrics](#6-regression-metrics)
7. [Cross-Validation](#7-cross-validation)
8. [Bias-Variance Tradeoff](#8-bias-variance-tradeoff)
9. [Overfitting & Underfitting Detection](#9-overfitting--underfitting-detection)
10. [Model Selection Workflow](#10-model-selection-workflow)

---

## 1. Why Accuracy Is Not Enough

### Intuition (Backend Analogy)

A fraud detection model where only 1% of transactions are fraudulent can score 99%
accuracy by predicting "not fraud" for everything — and miss every fraud case.

> **⚠️ Rule of thumb**: If your classes are imbalanced (>90/10 split), accuracy is
> almost meaningless. You need metrics that look at each class separately.

### How It Works

```
Fraud Detection Dataset:  10,000 transactions
├── 9,900 legitimate  (99%)
└──   100 fraudulent  ( 1%)

"Always predict legitimate" model:
├── Accuracy:  99%   ← looks great!
├── Fraud caught:  0  ← catastrophic
└── Business loss: all 100 fraud cases missed
```

### When to Use Accuracy

| Scenario | Accuracy OK? | Better Metric |
|----------|-------------|---------------|
| Balanced classes (50/50) | ✅ Yes | F1 still better |
| Imbalanced classes | ❌ No | Precision, Recall, F1, AUC |
| Cost of errors differs | ❌ No | Weighted metrics, business cost |
| Multi-class | ⚠️ Partial | Per-class precision/recall |

---

## 2. Confusion Matrix

### Intuition (Backend Analogy)

Think of an **alerting system** for production incidents:

| | Alert Fired | No Alert |
|---|---|---|
| **Real Incident** | ✅ True Positive — correctly flagged | ❌ False Negative — missed bug |
| **No Incident** | ⚠️ False Positive — false alarm | ✅ True Negative — correctly cleared |

- **TP** = correctly flagged real issues (on-call gets paged, it's a real outage)
- **FP** = false alarms (on-call gets paged at 3AM for nothing)
- **FN** = missed bugs (outage happened, nobody was alerted)
- **TN** = correctly cleared (no alert, no incident — business as usual)

### How It Works

```
                        PREDICTED
                   Positive    Negative
                 ┌───────────┬───────────┐
     Positive    │    TP     │    FN     │
  A              │  (Hit!)   │ (Missed!) │
  C              ├───────────┼───────────┤
  T              │    FP     │    TN     │
  U   Negative   │ (False    │ (All      │
  A              │  Alarm)   │  Clear)   │
  L              └───────────┴───────────┘

  Precision = TP / (TP + FP)  ← "Of my alarms, how many were real?"
  Recall    = TP / (TP + FN)  ← "Of real incidents, how many did I catch?"
```

### Recipe

```python
from sklearn.metrics import confusion_matrix

y_true = [1, 0, 1, 1, 0, 1, 0, 0, 1, 0]
y_pred = [1, 0, 1, 0, 0, 1, 1, 0, 1, 0]
cm = confusion_matrix(y_true, y_pred)
print(cm)  # [[3, 1], [1, 5]] — row=actual, col=predicted
```

> Always start with a confusion matrix — it's the **source of truth** for every
> classification metric.

---

## 3. Classification Metrics

### 3a. Precision — "When You Say YES, How Often Are You Right?"

Precision = **signal-to-noise ratio of your alerting system**. High precision means
when you page someone at 3AM, it's almost always a real incident.

```
Precision = TP / (TP + FP)

  Your model says "fraud" 100 times
  ├── 90 were actually fraud    (TP = 90)
  └── 10 were legitimate        (FP = 10)
  Precision = 90 / 100 = 0.90
```

> **Prioritize precision when**: False positives are expensive.
> Spam filter — blocking a real email is worse than letting spam through.

### 3b. Recall (Sensitivity) — "Of All Real Issues, How Many Did You Catch?"

Recall = your **incident detection rate**. High recall means you catch nearly every
real outage, even if you also fire some false alarms.

```
Recall = TP / (TP + FN)

  There are 100 actual fraud cases
  ├── 60 were detected          (TP = 60)
  └── 40 were missed            (FN = 40)
  Recall = 60 / 100 = 0.60
```

> **Prioritize recall when**: Missing a positive is catastrophic.
> Cancer screening — you'd rather have a false alarm than miss a tumor.

### 3c. F1 Score — The Harmonic Mean

F1 = the **SLA that balances availability and correctness**. A system that's always
available but wrong is useless; so is one that's correct but rarely responds.

```
F1 = 2 × (Precision × Recall) / (Precision + Recall)

  Precision = 0.90, Recall = 0.60
  F1 = 2 × (0.90 × 0.60) / (0.90 + 0.60)
  F1 = 1.08 / 1.50 = 0.72
```

> **Why harmonic mean?** It punishes imbalance. If either precision or recall is
> low, F1 stays low. Arithmetic mean would hide the problem.

### 3d. Specificity — "Of All Negatives, How Many Did You Clear?"

```
Specificity = TN / (TN + FP)

  "Of all healthy patients, how many did you correctly clear?"
  Complement of False Positive Rate:  Specificity = 1 - FPR
```

### Recipe

```python
from sklearn.metrics import precision_score, recall_score, f1_score

y_true = [1, 0, 1, 1, 0, 1, 0, 0, 1, 0]
y_pred = [1, 0, 1, 0, 0, 1, 1, 0, 1, 0]

print(f"Precision: {precision_score(y_true, y_pred):.2f}")  # 0.80
print(f"Recall:    {recall_score(y_true, y_pred):.2f}")     # 0.80
print(f"F1:        {f1_score(y_true, y_pred):.2f}")         # 0.80
```

---

## 4. AUC-ROC Curve

### Intuition (Backend Analogy)

Like adjusting a **traffic light timer**: more green time → more cars through (higher
TPR) but also more red-light runners (higher FPR). The ROC curve plots this tradeoff
at every threshold. AUC measures the area under that curve.

### How It Works

```
  ROC Curve: TPR (Recall) vs FPR

  TPR (Recall)
  1.0 ┤                          ╭──── Perfect model (AUC=1.0)
      │                      ╭───╯
  0.8 ┤                  ╭───╯
      │              ╭───╯
  0.6 ┤          ╭───╯         ╱ Random baseline
      │      ╭───╯          ╱   (AUC = 0.5)
  0.4 ┤  ╭───╯           ╱
      │ ╭╯            ╱
  0.2 ┤╭╯          ╱
      │╯        ╱
  0.0 ┼──────────────────────── FPR
      0   0.2  0.4  0.6  0.8  1.0

  AUC: 0.5 = random | 0.7 = decent | 0.8 = good | 0.9+ = excellent | 1.0 = perfect
```

**The Math:** AUC = area under the ROC curve = P(score(positive) > score(negative)).
AUC = 0.5 means random guessing; AUC = 1.0 means perfect separation.

### Recipe

```python
from sklearn.metrics import roc_auc_score, roc_curve

y_true  = [0, 0, 1, 1, 0, 1, 0, 1, 1, 0]
y_proba = [0.1, 0.4, 0.7, 0.9, 0.3, 0.8, 0.2, 0.6, 0.95, 0.5]

auc = roc_auc_score(y_true, y_proba)
fpr, tpr, thresholds = roc_curve(y_true, y_proba)

print(f"AUC-ROC: {auc:.3f}")  # Higher = better class separation
```

### When to Use

- **Use AUC-ROC when**: Classes are roughly balanced, threshold-independent evaluation.
- **Avoid AUC-ROC when**: Heavily imbalanced — TN dominates, making ROC misleading. Use PR curve.

---

## 5. Precision-Recall Curve

### Intuition (Backend Analogy)

PR curve is a **targeted load test on the minority class**. It ignores TN and focuses
only on how well you handle positives — the metric for imbalanced problems.

### How It Works

```
  Precision-Recall Curve

  Precision
  1.0 ┤████▓▓
      │      ▓▓▓▓
  0.8 ┤          ▓▓▓▓              Good model
      │              ▓▓▓▓          (high AP)
  0.6 ┤                  ▓▓▓▓
      │                      ▓▓▓
  0.4 ┤ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  Random baseline
      │                            (= proportion of positives)
  0.2 ┤
      │
  0.0 ┼──────────────────────────── Recall
      0   0.2  0.4  0.6  0.8  1.0

  Average Precision (AP) = area under this curve
  Higher AP = better on the minority class
```

**The Math:** AP = Σₙ (Rₙ − Rₙ₋₁) · Pₙ (area under the PR curve).
AP ignores TN entirely — it only measures how well you handle the minority class.

### Recipe

```python
from sklearn.metrics import average_precision_score, precision_recall_curve

y_true  = [0, 0, 1, 1, 0, 1, 0, 1, 1, 0]
y_proba = [0.1, 0.4, 0.7, 0.9, 0.3, 0.8, 0.2, 0.6, 0.95, 0.5]

ap = average_precision_score(y_true, y_proba)
precision, recall, thresholds = precision_recall_curve(y_true, y_proba)

print(f"Average Precision: {ap:.3f}")
```

### When to Use

| Dataset Balance | Use ROC? | Use PR Curve? |
|-----------------|----------|---------------|
| Balanced (50/50) | ✅ Yes | ✅ Also fine |
| Imbalanced (95/5) | ⚠️ Misleading | ✅ Better choice |
| Highly imbalanced (99/1) | ❌ Avoid | ✅ Required |

---

## 6. Regression Metrics

### Intuition (Backend Analogy)

Regression asks "how long will this request take?" — like measuring
a load balancer's **latency** vs its **error rate**.

### 6a. MSE / RMSE — Mean Squared Error

```
MSE  = (1/n) × Σ(actual - predicted)²
RMSE = √MSE    ← same units as your target

  Analogy: Like measuring latency variance.
  Big errors get squared → outliers dominate.
  One request taking 10s when predicted 1s contributes 81 to MSE.
```

### 6b. MAE — Mean Absolute Error

```
MAE = (1/n) × Σ|actual - predicted|

  Analogy: Like average absolute deviation from SLA.
  Treats all errors equally — one 10s outlier contributes 9, not 81.
  More robust to outliers than MSE.
```

### 6c. R² Score — Proportion of Variance Explained

```
R² = 1 - (SS_residual / SS_total)

  R² = 1.0  → perfect predictions
  R² = 0.0  → no better than predicting the mean
  R² < 0    → worse than the mean (broken model)

  Analogy: Like % of traffic your cache handles.
  R²=0.85 means your model explains 85% of the variance.
```

---

```
MAPE = (1/n) × Σ|actual - predicted| / |actual| × 100%

  Analogy: Like reporting "we were 12% off on capacity planning."
  Business-friendly: stakeholders understand percentages.
  ⚠️ Warning: Blows up when actual values are near zero.
```

### When to Use Which

| Metric | Best For | Watch Out |
|--------|----------|-----------|
| **RMSE** | Technical evaluation, penalize big errors | Sensitive to outliers |
| **MAE** | Robust evaluation, all errors equal | Doesn't penalize large errors enough |
| **R²** | Overall model quality, comparing models | Can be misleading with few features |
| **MAPE** | Business reporting, stakeholder comms | Undefined when actual = 0 |

### Recipe

```python
from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score
import numpy as np

y_true = [3.0, 5.0, 2.5, 7.0, 4.5]
y_pred = [2.8, 5.2, 2.0, 6.5, 4.8]

print(f"RMSE: {np.sqrt(mean_squared_error(y_true, y_pred)):.3f}")
print(f"MAE:  {mean_absolute_error(y_true, y_pred):.3f}")
print(f"R²:   {r2_score(y_true, y_pred):.3f}")
```

---

## 7. Cross-Validation

### Intuition (Backend Analogy)

A single train/test split is like running **one A/B test**. Cross-validation runs
**K independent A/B tests** and averages the results — much more reliable.

### How It Works

```
  5-Fold Cross-Validation

  Data: [████████████████████████████████████████]

  Fold 1: [TEST] [TRAIN] [TRAIN] [TRAIN] [TRAIN]  → score₁
  Fold 2: [TRAIN] [TEST] [TRAIN] [TRAIN] [TRAIN]  → score₂
  Fold 3: [TRAIN] [TRAIN] [TEST] [TRAIN] [TRAIN]  → score₃
  Fold 4: [TRAIN] [TRAIN] [TRAIN] [TEST] [TRAIN]  → score₄
  Fold 5: [TRAIN] [TRAIN] [TRAIN] [TRAIN] [TEST]  → score₅

  Final Score = mean(score₁ ... score₅) ± std
```

**The Math:** CV Score = (1/K) Σ score_k. Report as mean ± std.
**Stratified K-Fold** preserves class proportions — critical for imbalanced data.

### Recipe

```python
from sklearn.model_selection import cross_val_score, StratifiedKFold
from sklearn.ensemble import RandomForestClassifier

model = RandomForestClassifier(n_estimators=100, random_state=42)
cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)

scores = cross_val_score(model, X, y, cv=cv, scoring='f1')
print(f"F1: {scores.mean():.3f} ± {scores.std():.3f}")
```

### When to Use

| Situation | Recommendation |
|-----------|---------------|
| Small dataset (<10K samples) | 5- or 10-fold CV (must use) |
| Medium dataset (10K–100K) | 5-fold CV (recommended) |
| Large dataset (>100K) | Single train/val/test split is OK |
| Imbalanced classes | Stratified K-Fold (always) |
| Time-series data | TimeSeriesSplit (never shuffle!) |

---

## 8. Bias-Variance Tradeoff

### Intuition (Backend Analogy)

- **High bias (underfitting)**: A load balancer using **round-robin** when your
  servers have vastly different capacities. Too simple — ignores important patterns.
- **High variance (overfitting)**: A load balancer that **memorizes last week's
  traffic** and routes based on that exact pattern. Falls apart when traffic changes.
- **Sweet spot**: A load balancer using **weighted round-robin** based on real-time
  health checks. Complex enough to be useful, simple enough to generalize.

### How It Works

```
  Model Complexity →

  Error
  ▲
  │╲                              ╱
  │  ╲                          ╱
  │    ╲   Total Error        ╱
  │      ╲      curve       ╱
  │        ╲              ╱
  │          ╲    ╭─────╱─── Variance (overfitting)
  │            ╲╭─╯  ╭╱
  │             ╳───╯
  │           ╭─╲╮
  │         ╱    ╰──────── Bias (underfitting)
  │       ╱
  │     ╱
  ├───╱──────────────────────────────►
       ↑            ↑           ↑
    Too Simple   Sweet Spot   Too Complex

```

**The Math:** E[(ŷ − y)²] = Bias²(ŷ) + Var(ŷ) + σ².
Bias = systematic error (underfitting), Variance = sensitivity to training data
(overfitting), σ² = irreducible noise. Goal: minimize Bias² + Var.

### When to Use

> **Rule of thumb**: Start simple. Add complexity only when your **validation metric**
> improves. The moment validation performance starts dropping while training
> performance keeps improving — stop. You've crossed from learning to memorizing.

---

## 9. Overfitting & Underfitting Detection

### Intuition (Backend Analogy)

Think of **load testing** your service:
- **Underfitting** = your service can't handle even the training traffic.
  Both training and test error are high. The model is too weak.
- **Overfitting** = your service handles the training traffic perfectly but
  crashes on new patterns. Training error is low, test error is high.
  The model memorized the training data instead of learning the pattern.

### How It Works — Learning Curves

```
  OVERFITTING                       UNDERFITTING
  Loss                              Loss
  ▲                                 ▲
  │   ╭── Validation loss           │
  │  ╱  ╲    (going up!)            │  ──────── Validation loss
  │ ╱    ╲                          │              (stays high)
  │╱      ╲──────                   │
  │                                 │  ──────── Training loss
  │  ───────── Training loss        │              (also high)
  │              (keeps dropping)   │
  ├──────────────────► Epochs       ├──────────────────► Epochs

  Train loss ↓ + Val loss ↑         Both losses HIGH
  = OVERFITTING                     = UNDERFITTING

```

### Fixes

| Problem | Symptom | Fix |
|---------|---------|-----|
| **Overfitting** | Train ↓, Val ↑ | More data, regularization, dropout, simpler model, early stopping |
| **Underfitting** | Both high | More features, more complex model, train longer, reduce regularization |
| **Good fit** | Both low, small gap | Ship it 🚀 |

### Recipe

```python
from sklearn.ensemble import GradientBoostingClassifier
model = GradientBoostingClassifier(n_estimators=500, validation_fraction=0.2,
                                    n_iter_no_change=10, random_state=42)
model.fit(X_train, y_train)
print(f"Stopped at {model.n_estimators_} iterations")
```

---

## 10. Model Selection Workflow

### Intuition (Backend Analogy)

Model selection is like **choosing a tech stack** — start simple, measure, scale
only when needed:

```
  1. BASELINE   → Simple model (Logistic Reg / Random Forest)
  2. MEASURE    → Cross-validated metrics (establish benchmarks)
  3. ITERATE    → Try complex models (XGBoost, Neural Nets)
  4. COMPARE    → Same CV folds, same metric (fair comparison)
  5. DECIDE     → Metric + Speed + Interpretability → Ship it!
```

### Decision Matrix

| Factor | Logistic Regression | Random Forest | XGBoost | Neural Net |
|--------|-------------------|---------------|---------|------------|
| **Accuracy** | ★★☆ | ★★★ | ★★★★ | ★★★★ |
| **Speed** | ★★★★ | ★★★ | ★★☆ | ★☆☆ |
| **Interpretability** | ★★★★ | ★★☆ | ★★☆ | ★☆☆ |
| **Data needed** | Low | Medium | Medium | High |
| **Tuning effort** | Low | Low | Medium | High |

### Recipe

```python
from sklearn.model_selection import cross_val_score, StratifiedKFold
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier

models = {
    "Logistic Regression": LogisticRegression(max_iter=1000),
    "Random Forest": RandomForestClassifier(n_estimators=100),
    "Gradient Boosting": GradientBoostingClassifier(n_estimators=100),
}
cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)

for name, model in models.items():
    scores = cross_val_score(model, X, y, cv=cv, scoring="f1")
    print(f"{name:25s}  F1={scores.mean():.3f} ± {scores.std():.3f}")
```

### When to Use

> **Rule of thumb**: If a simple model gets you within 2-3% of a complex model,
> ship the simple one. The maintenance cost, latency, and debugging pain of
> complex models is real. A model you can explain and debug in production is
> worth more than 2% F1.

---

## Exercises

### Exercise 1: Conceptual

**Problem**: Your fraud detection model has 95% precision and 20% recall.
Explain to a non-technical stakeholder what this means in business terms.
Should you adjust the threshold?

<details>
<summary><strong>Solution</strong></summary>

"When our system flags fraud, it's correct 95% of the time — but it only catches
20% of actual fraud. **80% of real fraud slips through.**"

**Action**: Lower the threshold. Trade some precision (more false alarms) for much
higher recall (catch more fraud). In fraud detection, missing fraud costs more than
manual reviews of false positives.

</details>

---

### Exercise 2: Implementation

**Problem**: Compute precision, recall, F1, and AUC-ROC for a binary classifier.
Print a confusion matrix. Use sklearn, max 10 lines.

<details>
<summary><strong>Solution</strong></summary>

```python
from sklearn.metrics import (precision_score, recall_score, f1_score,
                             roc_auc_score, confusion_matrix)

y_true  = [1, 0, 1, 1, 0, 1, 0, 0, 1, 0]
y_pred  = [1, 0, 1, 0, 0, 1, 1, 0, 1, 0]
y_proba = [0.9, 0.2, 0.8, 0.4, 0.1, 0.85, 0.6, 0.15, 0.95, 0.3]

print(f"Precision: {precision_score(y_true, y_pred):.2f}")
print(f"Recall:    {recall_score(y_true, y_pred):.2f}")
print(f"F1:        {f1_score(y_true, y_pred):.2f}")
print(f"AUC-ROC:   {roc_auc_score(y_true, y_proba):.2f}")
print(f"Confusion Matrix:\n{confusion_matrix(y_true, y_pred)}")
```

</details>

---

### Exercise 3: Real-World Scenario

**Problem**: You're comparing 3 models:
- **(A)** Logistic Regression: F1=0.78, trains in 2s
- **(B)** Random Forest: F1=0.82, trains in 30s
- **(C)** XGBoost: F1=0.83, trains in 5min

Which would you deploy and why?

<details>
<summary><strong>Solution</strong></summary>

**Deploy Model B (Random Forest)**. A→B gives +4% F1 (worth the 15× training
time). B→C gives only +1% F1 for 10× more time — likely within CV noise.
RF is also easier to debug, faster to retrain, and simpler to maintain.

> **Rule**: If a simple model is within 2-3% of a complex one, ship the simple one.

</details>

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────┐
│                 EVALUATION & METRICS CHEAT SHEET                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CLASSIFICATION                                                 │
│  ─────────────                                                  │
│  Precision   = TP/(TP+FP)    "How many alerts are real?"        │
│  Recall      = TP/(TP+FN)    "How many issues did we catch?"    │
│  F1          = 2·P·R/(P+R)   "Balance of both"                  │
│  Specificity = TN/(TN+FP)    "How many negatives cleared?"      │
│  AUC-ROC    → threshold-independent, use for balanced data      │
│  Avg Prec   → use for imbalanced data                           │
│                                                                 │
│  REGRESSION                                                     │
│  ──────────                                                     │
│  RMSE = √mean((y-ŷ)²)       Penalizes big errors               │
│  MAE  = mean(|y-ŷ|)          Robust to outliers                 │
│  R²   = 1 - SS_res/SS_tot   % variance explained               │
│  MAPE = mean(|y-ŷ|/|y|)     Business-friendly percentage       │
│                                                                 │
│  VALIDATION                                                     │
│  ──────────                                                     │
│  Small data  → K-Fold CV (K=5 or 10)                            │
│  Imbalanced  → Stratified K-Fold                                │
│  Time series → TimeSeriesSplit (never shuffle!)                  │
│  Large data  → Single train/val/test split is OK                │
│                                                                 │
│  DECISION RULES                                                 │
│  ──────────────                                                 │
│  FP expensive    → optimize Precision  (spam filter)            │
│  FN expensive    → optimize Recall     (cancer, fraud)          │
│  Both matter     → optimize F1                                  │
│  Need threshold  → use ROC/PR curves                            │
│  Imbalanced data → PR curve > ROC curve                         │
│  Simple ≈ Complex? → ship the simple model                      │
│                                                                 │
│  OVERFITTING DETECTION                                          │
│  ─────────────────────                                          │
│  Train ↓ Val ↑     = overfitting  → regularize, more data      │
│  Both high         = underfitting → more features, complexity   │
│  Both low + gap ok = good fit     → ship it 🚀                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```
