# ML Frameworks — The Complete Toolkit

> **🎯 Goal**: Know when to use which ML framework and see them in action
> **⏱ Time**: 3–4h study + 1h exercises
> **📦 Deliverable**: Ability to pick the right tool for any ML task

---

## 1. The ML Framework Landscape

### Intuition (Backend Analogy)

Choosing an ML framework is like choosing a web framework. You wouldn't use
Spring Boot for a 10-line REST endpoint, and you wouldn't use Express for a
massive enterprise system with 50 microservices. Same logic applies here.

### How It Works

```
                    What's your data / task?
                            │
              ┌─────────────┼─────────────────┐
              ▼             ▼                  ▼
         Tabular?      Text / NLP?      Images / Audio /
              │             │           Custom Neural Net?
              │             │                  │
         ┌────┴────┐       ▼             ┌────┴────┐
         ▼         ▼   Hugging Face      ▼         ▼
      < 1M rows  Need   Transformers  Research/   Production/
         │       best       │         Custom?     Mobile?
         ▼       perf?      │            │           │
     scikit-     │          ▼            ▼           ▼
     learn       ▼      pipeline()    PyTorch    TensorFlow
              XGBoost/                              / Keras
              LightGBM                              + TFLite

              ┌─────────────────────────────────┐
              │  Track everything with MLflow    │
              │  or Weights & Biases             │
              └─────────────────────────────────┘
```

Think of it as a routing table — the "request" is your ML task, and the
framework is the "handler" that processes it best.

### The Common Thread — One Equation Behind Every Framework

Despite their different APIs and ecosystems, every framework listed here
is ultimately solving the **same optimization problem**:

```
  θ* = argmin  (1/n) Σᵢ ℓ(f_θ(xᵢ), yᵢ)  +  λ · R(θ)
          θ
        ├──────── data loss ─────────┤    ├─ regularization ─┤

  Where:
    f_θ  = the model (linear, tree, neural net — varies by framework)
    ℓ    = loss function (MSE, cross-entropy, hinge, etc.)
    R(θ) = regularizer (L1, L2, tree complexity — or none)
    λ    = regularization strength
```

The frameworks differ in **how** they compute the gradient `∇_θ L` and
update `θ`. scikit-learn often uses closed-form solvers or coordinate
descent; PyTorch and TensorFlow use reverse-mode autodiff; XGBoost uses
second-order Taylor expansion on the loss. Different engines, same
destination.

---

## 2. scikit-learn — The Swiss Army Knife

### Intuition (Backend Analogy)

scikit-learn is like **Express.js** — simple, consistent API, great for 80%
of tasks. You can spin up a working model in minutes. Every algorithm follows
the exact same interface, like how every Express route handler takes `(req, res)`.

### How It Works

```
  ┌──────────────────────────────────────────────────┐
  │           The sklearn Unified API                 │
  │                                                   │
  │   model = SomeAlgorithm(hyperparams)              │
  │      │                                            │
  │      ▼                                            │
  │   model.fit(X_train, y_train)     ← "Train"      │
  │      │                                            │
  │      ▼                                            │
  │   model.predict(X_test)           ← "Infer"      │
  │      │                                            │
  │      ▼                                            │
  │   model.score(X_test, y_test)     ← "Evaluate"   │
  │                                                   │
  │   Works the SAME for:                             │
  │   LinearRegression, RandomForest, SVM, KMeans...  │
  └──────────────────────────────────────────────────┘
```

**The Math:** Under the hood, `fit()` computes the optimal parameters `θ*`
— either via a closed-form solution (e.g., normal equation for linear
regression: `θ* = (XᵀX)⁻¹Xᵀy`) or an iterative solver (gradient descent,
coordinate descent). `score()` then evaluates a metric (accuracy, R², etc.)
on held-out data. The unified API hides whether the solver is analytic or
iterative — you just call `.fit()`.

**Pipeline** chains preprocessing + model into one object — like middleware
chaining in Express:

```
  Raw Data ──▶ [Scaler] ──▶ [Encoder] ──▶ [Model] ──▶ Prediction
               ────────── Pipeline (one .fit() call) ──────────
```

### Recipe

```python
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import GridSearchCV

# Full pipeline: scale → classify → tune hyperparameters
pipe = make_pipeline(StandardScaler(), RandomForestClassifier())
params = {"randomforestclassifier__n_estimators": [50, 100, 200]}
grid = GridSearchCV(pipe, params, cv=5, scoring="accuracy")
grid.fit(X_train, y_train)
print(f"Best accuracy: {grid.best_score_:.3f}")  # cross-validated
```

### When to Use

- ✅ Tabular data, < 1M rows
- ✅ Rapid prototyping and baselines
- ✅ Classical ML (regression, classification, clustering)
- ✅ Preprocessing pipelines and feature engineering
- ❌ No GPU support
- ❌ No deep learning
- ❌ Struggles with very large datasets (no out-of-core by default)

---

## 3. PyTorch — The Deep Learning Standard

### Intuition (Backend Analogy)

PyTorch is like **Spring Boot** — more boilerplate, but you get full control
over every layer. You define the architecture, the forward pass, the training
loop. It's the choice when you need custom behavior and flexibility.

### How It Works

```
  ┌──────────────────────────────────────────────────┐
  │              PyTorch Core Concepts                │
  │                                                   │
  │  Tensor          = Multi-dim array (NumPy + GPU)  │
  │  Autograd        = Auto differentiation (∂L/∂w)   │
  │  nn.Module       = Base class for models          │
  │  DataLoader      = Batched iteration (pagination) │
  │  Optimizer       = SGD, Adam, etc.                │
  │                                                   │
  │  Training Loop:                                   │
  │                                                   │
  │  for batch in DataLoader:    ← paginated API      │
  │      predictions = model(batch)                   │
  │      loss = criterion(predictions, labels)        │
  │      loss.backward()         ← autograd magic     │
  │      optimizer.step()        ← update weights     │
  │      optimizer.zero_grad()                        │
  └──────────────────────────────────────────────────┘
```

**The Math:** PyTorch's **autograd** engine builds a dynamic computation
graph as you execute operations. When you call `loss.backward()`, it
walks the graph in reverse and computes every gradient via the chain rule
(reverse-mode automatic differentiation):

```
  ∂L/∂θᵢ  =  ∂L/∂aₙ · ∂aₙ/∂aₙ₋₁ · ... · ∂a₁/∂θᵢ    (chain rule)
```

Then `optimizer.step()` applies the update rule — for vanilla SGD:

```
  θ  ←  θ − η · ∇_θ L          (η = learning rate)
```

This separation of `backward()` (compute gradients) and `step()` (apply
update) is why you can swap optimizers (SGD → Adam → AdaGrad) without
changing your model code.

**nn.Module** is like a base controller class — you subclass it and implement
`forward()`:

```
  class MyModel(nn.Module):        class MyController(BaseController):
      def forward(self, x):    ≈       def handle(self, request):
          ...                               ...
```

### Recipe

```python
import torch
import torch.nn as nn

class Net(nn.Module):
    def __init__(self):
        super().__init__()
        self.layers = nn.Sequential(
            nn.Linear(10, 64), nn.ReLU(), nn.Linear(64, 1), nn.Sigmoid())
    def forward(self, x): return self.layers(x)

model, opt = Net(), torch.optim.Adam(Net().parameters(), lr=1e-3)
criterion = nn.BCELoss()
for epoch in range(10):                          # training loop
    for xb, yb in train_loader:
        loss = criterion(model(xb), yb)
        loss.backward(); opt.step(); opt.zero_grad()
```

### When to Use

- ✅ Custom neural network architectures
- ✅ Research and experimentation
- ✅ Hugging Face ecosystem (built on PyTorch)
- ✅ Dynamic computation graphs (debug like normal Python)
- ❌ More boilerplate than Keras
- ❌ Less mature production serving (but TorchServe helps)

---

## 4. TensorFlow / Keras — The Production Workhorse

### Intuition (Backend Analogy)

TensorFlow is like **.NET** — great tooling, tighter ecosystem, strong
production story. Keras (its high-level API) is like ASP.NET — one call
and you're serving. The trade-off is ecosystem lock-in.

### How It Works

```
  ┌──────────────────────────────────────────────────┐
  │            TensorFlow Ecosystem                   │
  │                                                   │
  │  ┌─────────┐                                      │
  │  │  Keras   │  ← High-level API (model.fit())     │
  │  ├─────────┤                                      │
  │  │TensorFlow│  ← Core engine (graphs, ops)        │
  │  ├─────────┤                                      │
  │  │ TF Lite  │  ← Mobile / Edge deployment         │
  │  ├─────────┤                                      │
  │  │TF Serving│  ← Production serving (gRPC/REST)   │
  │  ├─────────┤                                      │
  │  │   TFX    │  ← Full ML pipeline orchestration   │
  │  └─────────┘                                      │
  │                                                   │
  │  Think: .NET Framework → .NET Core → ASP.NET →    │
  │         Azure deployment → full ecosystem          │
  └──────────────────────────────────────────────────┘
```

**The Math:** TensorFlow's `tf.GradientTape()` records operations for
automatic differentiation — conceptually identical to PyTorch's autograd,
but with a key design difference: TF can trace the computation into a
**static graph** (`@tf.function`) and fuse/reorder operations before
execution. Keras's `model.fit()` hides this entirely:

```
  Under the hood of model.fit():
    with tf.GradientTape() as tape:
        ŷ = model(x)                    ← forward pass (recorded)
        loss = loss_fn(y, ŷ)
    grads = tape.gradient(loss, model.trainable_variables)   ← ∇_θ L
    optimizer.apply_gradients(zip(grads, model.trainable_variables))
```

Eager mode executes operations immediately (like PyTorch); graph mode
traces them first, then optimizes the computation graph for hardware.

### Recipe

```python
import tensorflow as tf
from tensorflow import keras

model = keras.Sequential([
    keras.layers.Dense(64, activation="relu", input_shape=(10,)),
    keras.layers.Dense(32, activation="relu"),
    keras.layers.Dense(1, activation="sigmoid")
])
model.compile(optimizer="adam", loss="binary_crossentropy", metrics=["accuracy"])
model.fit(X_train, y_train, epochs=10, batch_size=32, validation_split=0.2)
print(f"Test accuracy: {model.evaluate(X_test, y_test)[1]:.3f}")
```

### When to Use

- ✅ Production deployment via TF Serving (battle-tested gRPC/REST)
- ✅ Mobile and edge devices via TFLite
- ✅ Google Cloud ML integration (Vertex AI)
- ✅ End-to-end ML pipelines with TFX
- ❌ Less flexible for research than PyTorch
- ❌ Ecosystem lock-in (harder to mix with non-TF tools)
- ❌ Community momentum has shifted toward PyTorch

---

## 5. Hugging Face — The npm of ML

### Intuition (Backend Analogy)

Hugging Face is like **npm** — a massive registry of pre-built components.
Need sentiment analysis? `npm install` → `pipeline("sentiment-analysis")`.
Need translation? Same thing. 100K+ pre-trained models ready to use.

### How It Works

```
  ┌──────────────────────────────────────────────────┐
  │           Hugging Face Ecosystem                  │
  │                                                   │
  │   Model Hub ──────── 100K+ pre-trained models     │
  │       │               (like Docker Hub for ML)    │
  │       ▼                                           │
  │   Transformers ───── Load any model in 2 lines    │
  │       │                                           │
  │       ▼                                           │
  │   Pipeline API ───── Inference in 1 line          │
  │       │               sentiment, translation,     │
  │       │               summarization, QA, ...      │
  │       ▼                                           │
  │   Datasets ───────── Load any dataset in 1 line   │
  │       │                                           │
  │       ▼                                           │
  │   Tokenizers ─────── Fast tokenization (Rust)     │
  │       │                                           │
  │       ▼                                           │
  │   Trainer API ────── Fine-tune with minimal code  │
  └──────────────────────────────────────────────────┘
```

**The Math:** Fine-tuning a Hugging Face model minimizes standard
cross-entropy loss over the output logits:

```
  L = -(1/n) Σᵢ Σₖ yᵢₖ · log(softmax(zᵢₖ))

  Where:
    zᵢₖ = raw logit for sample i, class k (output of the model head)
    softmax(zᵢₖ) = exp(zᵢₖ) / Σⱼ exp(zᵢⱼ)
```

The Trainer API wraps a standard PyTorch training loop — the math is
identical to the PyTorch section above, just with a pre-trained
transformer as `f_θ`.

### Recipe

```python
from transformers import pipeline

# Sentiment analysis — 2 lines
classifier = pipeline("sentiment-analysis")
result = classifier("I love building ML systems!")
# [{'label': 'POSITIVE', 'score': 0.9998}]

# Summarization — swap one argument
summarizer = pipeline("summarization")
summary = summarizer("Long article text here...", max_length=50)

# Zero-shot classification — no training needed
clf = pipeline("zero-shot-classification")
clf("Deploy ML model to prod", candidate_labels=["devops", "ml", "frontend"])
```

### When to Use

- ✅ Any NLP task (sentiment, translation, summarization, QA)
- ✅ Quick prototypes with pre-trained models
- ✅ Fine-tuning transformer models on custom data
- ✅ Access to latest research models (GPT, BERT, LLaMA, etc.)
- ❌ Heavy models — large memory/GPU requirements
- ❌ Overkill for simple tabular ML tasks
- ❌ Fast-moving API — breaking changes between versions

---

## 6. XGBoost / LightGBM / CatBoost (Recap)

### Intuition (Backend Analogy)

These are your **specialized high-performance frameworks** — like using
Netty instead of Tomcat when you need raw throughput. For tabular data,
gradient boosting consistently beats deep learning.

**The Math:** Gradient boosting frameworks minimize an additive objective
(see Chapter 08 for the full derivation):

```
  Obj = Σᵢ l(yᵢ, ŷᵢ) + Σₖ Ω(fₖ)        (loss + tree complexity)
```

Each split is chosen by the **gain formula** using second-order gradients:

```
  Gain = (1/2) [ G_L²/(H_L+λ) + G_R²/(H_R+λ) - (G_L+G_R)²/(H_L+H_R+λ) ] - γ

  Where:
    Gⱼ = Σᵢ∈ⱼ gᵢ   (sum of first-order gradients in leaf j)
    Hⱼ = Σᵢ∈ⱼ hᵢ   (sum of second-order gradients in leaf j)
    λ  = L2 regularization on leaf weights
    γ  = minimum loss reduction to make a split
```

This is what makes XGBoost/LightGBM so effective — they use a Newton-like
second-order approximation to greedily find the best splits.

### Recipe

```python
import xgboost as xgb
import lightgbm as lgb
from catboost import CatBoostClassifier

# All three — same sklearn-compatible API
xgb_model = xgb.XGBClassifier(n_estimators=100).fit(X_train, y_train)
lgb_model = lgb.LGBMClassifier(n_estimators=100).fit(X_train, y_train)
cat_model = CatBoostClassifier(iterations=100, verbose=0).fit(X_train, y_train)

for name, m in [("XGB", xgb_model), ("LGB", lgb_model), ("Cat", cat_model)]:
    print(f"{name}: {m.score(X_test, y_test):.3f}")
```

### When to Use

- ✅ Tabular/structured data (the undisputed king)
- ✅ Kaggle competitions (most winning solutions)
- ✅ Fast training, low memory
- ❌ Not for images, text, or sequential data
- 📖 See **Chapter 08** for the deep dive

---

## 7. MLflow — Experiment Tracking & Registry

### Intuition (Backend Analogy)

MLflow is like **Git + Docker Registry** for ML experiments. Every experiment
run is a "commit" (parameters, metrics, artifacts). The model registry is
like a container registry — version, tag, and promote models through stages.

### How It Works

```
  ┌──────────────────────────────────────────────────┐
  │               MLflow Components                   │
  │                                                   │
  │  Tracking ─────── Log params, metrics, artifacts  │
  │      │             (like git log for experiments)  │
  │      ▼                                            │
  │  Projects ─────── Reproducible runs               │
  │      │             (like Dockerfiles for ML)       │
  │      ▼                                            │
  │  Models ───────── Package models in standard fmt   │
  │      │             (like container images)          │
  │      ▼                                            │
  │  Registry ─────── Version + stage management      │
  │                    (staging → production)          │
  │                                                   │
  │  Experiment Run:                                  │
  │  ┌─────────────────────────────────┐              │
  │  │ run_id: abc123                  │              │
  │  │ params: {lr: 0.01, epochs: 10} │              │
  │  │ metrics: {acc: 0.95, f1: 0.93} │              │
  │  │ artifacts: model.pkl, plots/    │              │
  │  └─────────────────────────────────┘              │
  └──────────────────────────────────────────────────┘
```

### Recipe

```python
import mlflow

mlflow.set_experiment("churn-prediction")
with mlflow.start_run():
    mlflow.log_params({"model": "xgboost", "n_estimators": 100, "lr": 0.1})
    model = xgb.XGBClassifier(n_estimators=100, learning_rate=0.1)
    model.fit(X_train, y_train)
    acc = model.score(X_test, y_test)
    mlflow.log_metric("accuracy", acc)
    mlflow.sklearn.log_model(model, "model")  # save artifact
    print(f"Run logged — accuracy: {acc:.3f}")
```

### When to Use

- ✅ Tracking experiments across team members
- ✅ Model versioning and promotion (staging → prod)
- ✅ Reproducible ML runs
- ✅ Framework-agnostic (works with sklearn, PyTorch, TF, etc.)

### Alternatives

| Tool             | Strength                          |
|------------------|-----------------------------------|
| **W&B**          | Best UI, great for collaboration  |
| **Neptune**      | Flexible metadata tracking        |
| **ClearML**      | Open-source, self-hosted          |
| **DVC**          | Git-like data versioning          |

---

## 8. Comparison Matrix

```
┌──────────────┬──────────────────┬────────────┬──────┬────────────┬──────────────┐
│  Framework   │    Best For      │  Learning  │ GPU  │ Production │  Ecosystem   │
│              │                  │   Curve    │      │  Readiness │              │
├──────────────┼──────────────────┼────────────┼──────┼────────────┼──────────────┤
│ scikit-learn │ Classical ML,    │  ★☆☆ Easy  │  No  │  Medium    │ Huge (Python │
│              │ tabular data,    │            │      │  (pickle/  │ data science │
│              │ preprocessing    │            │      │   joblib)  │ standard)    │
├──────────────┼──────────────────┼────────────┼──────┼────────────┼──────────────┤
│ PyTorch      │ Deep learning,   │  ★★☆ Med   │ Yes  │  Good      │ Large        │
│              │ research,        │            │      │  (Torch-   │ (HuggingFace │
│              │ custom models    │            │      │   Serve)   │ built on it) │
├──────────────┼──────────────────┼────────────┼──────┼────────────┼──────────────┤
│ TensorFlow   │ Production,      │  ★★☆ Med   │ Yes  │  Excellent │ Full         │
│ / Keras      │ mobile/edge,     │            │      │  (TF Serve │ (TFX, TFLite │
│              │ Google Cloud     │            │      │   TFLite)  │ Vertex AI)   │
├──────────────┼──────────────────┼────────────┼──────┼────────────┼──────────────┤
│ Hugging Face │ NLP, pre-trained │  ★☆☆ Easy  │ Yes  │  Good      │ Massive      │
│              │ models,          │  (pipeline)│      │  (Infer-   │ (100K+       │
│              │ transformers     │            │      │   ence API)│ models)      │
├──────────────┼──────────────────┼────────────┼──────┼────────────┼──────────────┤
│ XGBoost      │ Tabular data,    │  ★☆☆ Easy  │ Yes  │  Good      │ Moderate     │
│              │ competitions     │            │      │  (sklearn  │ (focused on  │
│              │                  │            │      │   compat)  │ boosting)    │
├──────────────┼──────────────────┼────────────┼──────┼────────────┼──────────────┤
│ LightGBM     │ Large tabular    │  ★☆☆ Easy  │ Yes  │  Good      │ Moderate     │
│              │ data, speed      │            │      │  (sklearn  │ (Microsoft   │
│              │                  │            │      │   compat)  │ backed)      │
├──────────────┼──────────────────┼────────────┼──────┼────────────┼──────────────┤
│ MLflow       │ Experiment       │  ★☆☆ Easy  │ N/A  │  Excellent │ Growing      │
│              │ tracking,        │            │      │  (model    │ (framework   │
│              │ model registry   │            │      │   registry)│ agnostic)    │
└──────────────┴──────────────────┴────────────┴──────┴────────────┴──────────────┘
```

---

## 9. Decision Guide — What to Use When

```
  ┌────────────────────────────────────────────────────────────┐
  │              ML Framework Decision Guide                   │
  ├──────────────────────────────────┬─────────────────────────┤
  │  Your Situation                  │  Recommended Stack      │
  ├──────────────────────────────────┼─────────────────────────┤
  │  Tabular data, < 1M rows        │  scikit-learn           │
  │  Tabular data, best accuracy    │  XGBoost / LightGBM     │
  │  NLP / text tasks               │  Hugging Face           │
  │  Custom neural network          │  PyTorch                │
  │  Mobile / edge deployment       │  TensorFlow Lite        │
  │  Quick prototype (any task)     │  HF pipeline or sklearn │
  │  Production model serving       │  TF Serving / ONNX RT   │
  │  Experiment tracking            │  MLflow or W&B          │
  │  Computer vision                │  PyTorch + torchvision  │
  │  Time series forecasting        │  sklearn or Prophet     │
  │  Recommender systems            │  PyTorch or TF          │
  └──────────────────────────────────┴─────────────────────────┘
```

**Rule of Thumb for Backend Engineers:**

```
  Start simple ──────────────────────────────▶ Add complexity
                                                only when needed

  sklearn         XGBoost        PyTorch        Custom
  baseline   ──▶  if tabular ──▶ if deep    ──▶ architecture
  (5 min)         (10 min)       learning       (hours)
                                 needed
```

---

## 10. The Modern ML Stack

### Putting It All Together

```
  ┌─────────────────────────────────────────────────────────────┐
  │                    THE MODERN ML STACK                       │
  │                                                             │
  │  ┌─────────────────────────────────────────────────────┐    │
  │  │  MONITORING        Evidently │ Whylogs │ Grafana    │    │
  │  │                    (data drift, model performance)  │    │
  │  └─────────────────────────────────────────────────────┘    │
  │  ┌─────────────────────────────────────────────────────┐    │
  │  │  SERVING           FastAPI + ONNX Runtime           │    │
  │  │                    TF Serving │ TorchServe           │    │
  │  └─────────────────────────────────────────────────────┘    │
  │  ┌─────────────────────────────────────────────────────┐    │
  │  │  EXPERIMENT        MLflow │ W&B │ Neptune            │    │
  │  │  TRACKING          (params, metrics, artifacts)     │    │
  │  └─────────────────────────────────────────────────────┘    │
  │  ┌──────────────────────┐  ┌────────────────────────┐      │
  │  │  DEEP LEARNING       │  │  CLASSICAL ML          │      │
  │  │  PyTorch             │  │  scikit-learn           │      │
  │  │  Hugging Face        │  │  XGBoost / LightGBM    │      │
  │  │  torchvision         │  │  CatBoost              │      │
  │  └──────────────────────┘  └────────────────────────┘      │
  │  ┌─────────────────────────────────────────────────────┐    │
  │  │  DATA LAYER         Pandas │ Polars │ DuckDB       │    │
  │  │                     NumPy │ Apache Arrow            │    │
  │  └─────────────────────────────────────────────────────┘    │
  │  ┌─────────────────────────────────────────────────────┐    │
  │  │  INFRASTRUCTURE     Docker │ K8s │ Cloud (AWS/GCP)  │    │
  │  │                     GPU instances │ Spot/Preemptible │    │
  │  └─────────────────────────────────────────────────────┘    │
  └─────────────────────────────────────────────────────────────┘
```

### Backend Analogy — The Web App Stack

```
  ML Stack                          Web Stack
  ─────────────────────────────────────────────────
  Monitoring (Evidently)        ≈   Datadog / New Relic
  Serving (FastAPI + ONNX)      ≈   Nginx + App Server
  Experiment Tracking (MLflow)  ≈   Git + CI/CD
  Model Training (PyTorch)      ≈   Business Logic
  Data Layer (Pandas)           ≈   ORM / Database
  Infrastructure (Docker/K8s)   ≈   Same!
```

---

## Exercises

### Exercise 1: Conceptual — Framework Selection

Your team needs to build three systems:

**(a)** Spam classifier for emails (100K labeled examples)
**(b)** Image recognition for product photos (custom categories)
**(c)** Churn prediction from user activity table (500K rows, 40 features)

**Question:** Which framework would you choose for each and why?

---

### Exercise 2: Implementation — Same Problem, Three Frameworks

Train binary classification on the same dataset using scikit-learn, PyTorch,
and XGBoost. Compare accuracy. Max 5 lines each (after imports/data loading).

---

### Exercise 3: Real-World Scenario — Tech Stack Recommendation

Your company is starting a new ML project. Context:
- Team: 3 backend engineers, no ML experience
- Task: customer churn prediction
- Data: 500K row CSV, 30 features, binary label
- Requirement: model must be served via REST API

**Question:** Recommend the complete tech stack with justification for each
layer (data processing, modeling, tracking, serving, monitoring).

---

### Solutions

#### Solution 1: Framework Selection

```
(a) Spam classifier (100K emails, text data)
    → Hugging Face Transformers
    Why: text task = NLP territory. Fine-tune a small BERT model
    on your labeled data. pipeline("text-classification") gets
    you a baseline in 2 lines, then fine-tune with Trainer API.
    Alternative: sklearn TF-IDF + LogisticRegression for a fast
    baseline (simpler, but lower accuracy on nuanced text).

(b) Image recognition (custom categories)
    → PyTorch + torchvision
    Why: computer vision with custom categories = transfer learning.
    Load a pre-trained ResNet, replace the final layer, fine-tune.
    PyTorch gives full control over the training loop.
    Alternative: TensorFlow/Keras if deploying to mobile (TFLite).

(c) Churn prediction (500K rows, tabular)
    → XGBoost or LightGBM
    Why: tabular data + structured features = gradient boosting.
    Consistently beats deep learning on tabular tasks.
    Start with sklearn for a quick baseline, then XGBoost for
    best accuracy. No GPU needed.
```

#### Solution 2: Three Frameworks, One Problem

```python
# Assume: X_train, X_test, y_train, y_test are ready (numpy arrays)

# --- scikit-learn ---
from sklearn.ensemble import RandomForestClassifier
model_sk = RandomForestClassifier(n_estimators=100).fit(X_train, y_train)
print(f"sklearn:  {model_sk.score(X_test, y_test):.3f}")

# --- XGBoost ---
import xgboost as xgb
model_xgb = xgb.XGBClassifier(n_estimators=100).fit(X_train, y_train)
print(f"XGBoost:  {model_xgb.score(X_test, y_test):.3f}")

# --- PyTorch (simplified with skorch for fair comparison) ---
from skorch import NeuralNetClassifier
from torch import nn
net = NeuralNetClassifier(nn.Sequential(
    nn.Linear(X_train.shape[1], 64), nn.ReLU(), nn.Linear(64, 2)),
    max_epochs=10, verbose=0)
net.fit(X_train.astype("float32"), y_train)
print(f"PyTorch:  {net.score(X_test.astype('float32'), y_test):.3f}")
```

#### Solution 3: Complete Tech Stack

```
┌────────────────────┬─────────────────────┬──────────────────────────┐
│  Layer             │  Choice             │  Why                     │
├────────────────────┼─────────────────────┼──────────────────────────┤
│  Data Processing   │  Pandas             │  Team knows Python,      │
│                    │                     │  500K rows fits in RAM   │
├────────────────────┼─────────────────────┼──────────────────────────┤
│  Feature Eng.      │  scikit-learn       │  StandardScaler, OneHot, │
│                    │  Pipeline           │  all in one pipeline     │
├────────────────────┼─────────────────────┼──────────────────────────┤
│  Modeling          │  XGBoost            │  Best for tabular data,  │
│                    │                     │  sklearn-compatible API  │
├────────────────────┼─────────────────────┼──────────────────────────┤
│  Experiment Track  │  MLflow             │  Free, self-hosted,      │
│                    │                     │  simple setup            │
├────────────────────┼─────────────────────┼──────────────────────────┤
│  Serving           │  FastAPI +          │  Backend devs know REST, │
│                    │  joblib/pickle      │  FastAPI is intuitive    │
├────────────────────┼─────────────────────┼──────────────────────────┤
│  Monitoring        │  Evidently          │  Detects data drift,     │
│                    │                     │  open source             │
├────────────────────┼─────────────────────┼──────────────────────────┤
│  Infrastructure    │  Docker + existing  │  Team already knows      │
│                    │  K8s cluster        │  containers              │
└────────────────────┴─────────────────────┴──────────────────────────┘

Key reasoning:
- No GPU needed (tabular data + XGBoost)
- Minimize new tools (sklearn API is 3 methods: fit/predict/score)
- Backend engineers learn fastest with familiar patterns (REST, Docker)
- Start simple, add complexity only when baseline isn't good enough
```

---

*Next chapter: [17 — ML System Design Patterns](17-ml-system-design.md)*
