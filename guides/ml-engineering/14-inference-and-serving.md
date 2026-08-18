# Inference & Model Serving

> **🎯 Goal**: Learn how to deploy ML models as production services
> **⏱ Time**: 2–3h study + 1h exercises
> **📦 Deliverable**: Ability to serve a model as a REST API with proper engineering practices

---

## Table of Contents

1. [Training vs Inference](#1-training-vs-inference)
2. [Model Serialization](#2-model-serialization)
3. [Serving Patterns](#3-serving-patterns)
4. [Building a Model API with FastAPI](#4-building-a-model-api-with-fastapi)
5. [Latency Optimization](#5-latency-optimization)
6. [ONNX Deep Dive](#6-onnx-deep-dive)
7. [Model Versioning & Registry](#7-model-versioning--registry)
8. [Monitoring in Production](#8-monitoring-in-production)
9. [Scaling Model Serving](#9-scaling-model-serving)

---

## 1. Training vs Inference

### Intuition (Backend Analogy)

Training is **compiling your code** — slow, resource-hungry, done once (or periodically).
Inference is **running the compiled binary** — fast, lightweight, done millions of times.

You'd never recompile on every HTTP request. Same idea: train offline, serve the
trained artifact online.

| Aspect       | Training                  | Inference                  |
|--------------|---------------------------|----------------------------|
| When         | Offline / batch           | Real-time / on-demand      |
| Hardware     | GPU clusters, hours       | CPU or single GPU, <100ms  |
| Frequency    | Daily / weekly            | Thousands of requests/sec  |
| Bottleneck   | Throughput (samples/sec)  | Latency (ms/request)       |
| Analogy      | `mvn compile`             | `java -jar app.jar`        |

### When to Use

This mental model drives every design decision below. If you're optimizing for
training, you care about throughput. If you're optimizing for inference, you care
about **latency** and **availability**.

---

## 2. Model Serialization

### Intuition (Backend Analogy)

Choosing a serialization format for your model is like choosing between JSON,
Protocol Buffers, and Avro for your API:

| Format      | Like…             | Tradeoff                              |
|-------------|-------------------|---------------------------------------|
| pickle      | JSON              | Easy, but Python-only, not secure     |
| ONNX        | Protocol Buffers  | Cross-language, optimized, standard   |
| TorchScript | Avro              | Framework-specific but fast           |
| SavedModel  | Thrift            | TensorFlow ecosystem, good tooling    |

### How It Works

```
                  ┌──────────────┐
  Training        │  Model       │
  (Python)        │  (weights +  │
                  │   graph)     │
                  └──────┬───────┘
                         │
           ┌─────────────┼─────────────┐
           ▼             ▼             ▼
    ┌────────────┐ ┌──────────┐ ┌────────────┐
    │  pickle /  │ │   ONNX   │ │ TorchScript│
    │  joblib    │ │  (.onnx) │ │  (.pt)     │
    └─────┬──────┘ └────┬─────┘ └─────┬──────┘
          │              │             │
          ▼              ▼             ▼
      Python only   Python/C++/    PyTorch
                    C#/Java        runtime
```

### Recipe — pickle/joblib (sklearn)

```python
import joblib

# Save
joblib.dump(model, "model.joblib")

# Load
model = joblib.load("model.joblib")
prediction = model.predict(X_new)
```

### Recipe — ONNX (sklearn)

```python
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

onnx_model = convert_sklearn(model, initial_types=[("X", FloatTensorType([None, 4]))])
with open("model.onnx", "wb") as f:
    f.write(onnx_model.SerializeToString())
```

### Recipe — TorchScript (PyTorch)

```python
scripted = torch.jit.script(model)
scripted.save("model.pt")

# Load — no need for original class definition
loaded = torch.jit.load("model.pt")
output = loaded(torch.randn(1, 10))
```

### When to Use

| Scenario                        | Format          | Why                               |
|---------------------------------|-----------------|-----------------------------------|
| Quick prototyping, sklearn      | joblib/pickle   | Simplest, 1 line save/load        |
| Cross-language production       | ONNX            | Universal, optimized runtime      |
| PyTorch production              | TorchScript     | No Python dependency at serve     |
| TensorFlow ecosystem            | SavedModel      | Native TF Serving support         |

> **⚠️ Warning**: Never unpickle models from untrusted sources — pickle executes
> arbitrary code on load. Same security concern as `eval()`.

---

## 3. Serving Patterns

### Intuition (Backend Analogy)

These are the same patterns you use for any service, just with `model.predict()`
as the business logic.

### How It Works

```
  ┌─────────────────────────────────────────────────────────────────┐
  │                    SERVING PATTERNS                             │
  ├─────────────┬──────────────┬──────────────┬─────────────────────┤
  │  ONLINE     │  BATCH       │  STREAMING   │  EMBEDDED           │
  │             │              │              │                     │
  │  Client     │  Data Lake   │  Kafka       │  Mobile App         │
  │    │        │    │         │    │         │    │                │
  │    ▼        │    ▼         │    ▼         │    ▼                │
  │  REST API   │  Spark Job   │  Consumer   │  On-device          │
  │    │        │    │         │    │         │  model              │
  │    ▼        │    ▼         │    ▼         │    │                │
  │  Predict    │  Predict     │  Predict     │    ▼                │
  │  (1 item)   │  (millions)  │  (per event) │  Predict            │
  │             │              │              │  (local)            │
  ├─────────────┼──────────────┼──────────────┼─────────────────────┤
  │ Latency:    │ Latency:     │ Latency:     │ Latency:            │
  │  <100ms     │  hours OK    │  <1s         │  <10ms              │
  │             │              │              │                     │
  │ Use: fraud  │ Use: nightly │ Use: real-   │ Use: autocorrect,   │
  │ detection,  │ recommenda-  │ time anomaly │ image filters,      │
  │ search      │ tions, ETL   │ detection    │ on-device ML        │
  └─────────────┴──────────────┴──────────────┴─────────────────────┘
```

| Pattern    | Pros                              | Cons                                |
|------------|-----------------------------------|-------------------------------------|
| Online     | Real-time, simple API             | Latency-sensitive, scaling costs    |
| Batch      | Simple, cost-efficient            | Stale predictions, not real-time    |
| Streaming  | Near real-time, event-driven      | Complex infra (Kafka, Flink)        |
| Embedded   | No network latency, works offline | Model size limits, hard to update   |

### When to Use

- **Default to online** (REST API) unless you have a reason not to.
- **Batch** when predictions don't need to be fresh (e.g., nightly email recs).
- **Streaming** when you already have Kafka and need sub-second on events.
- **Embedded** when offline/edge is a hard requirement (mobile, IoT).

---

## 4. Building a Model API with FastAPI

### Intuition (Backend Analogy)

This is just a microservice. The only difference: instead of a database query as
your business logic, it's `model.predict()`. Everything else — health checks,
input validation, error handling — is identical.

```
  ┌──────────────────────────────────────────────┐
  │          Typical Microservice                 │
  │                                               │
  │  Request → Validate → DB Query → Response     │
  │                                               │
  ├──────────────────────────────────────────────┤
  │          ML Microservice                      │
  │                                               │
  │  Request → Validate → model.predict() → Resp  │
  └──────────────────────────────────────────────┘
```

### Recipe — FastAPI Model Server

```python
from fastapi import FastAPI
from pydantic import BaseModel
import joblib

app = FastAPI()
model = joblib.load("model.joblib")          # Load ONCE at startup

class Features(BaseModel):
    values: list[float]

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/predict")
def predict(req: Features):
    pred = model.predict([req.values])
    return {"prediction": pred.tolist()}
```

> **⚠️ Critical**: Load the model **at startup** (`model = joblib.load(...)` at
> module level), not inside the request handler. Loading per-request adds seconds
> of latency and leaks memory. This is the #1 beginner mistake.

### When to Use

FastAPI is the **default choice** for Python model serving. Use it when:
- You need a quick, production-grade API
- Your model is Python-based (sklearn, PyTorch, TensorFlow)
- You want automatic OpenAPI docs for free

Consider alternatives when:
- Serving at massive scale → use Triton or TF Serving
- Latency is extreme (<5ms) → use ONNX Runtime with C++ server

---

## 5. Latency Optimization

### Intuition (Backend Analogy)

Same playbook as optimizing any API:

| ML Optimization  | Backend Equivalent                  |
|------------------|-------------------------------------|
| Quantization     | Compressing images to WebP          |
| Batching         | Batch INSERT instead of 1-by-1      |
| Caching          | Redis cache for repeated queries    |
| Async processing | Job queue (Celery, SQS)             |
| GPU acceleration | Using specialized hardware (FPGA)   |

### How It Works

```
  Request ──▶ Cache Check ──▶ Hit? ──Yes──▶ Return cached
                  │
                  No
                  │
                  ▼
            Batch Queue           (collect requests for ~10ms)
                  │
                  ▼
          ┌───────────────┐
          │  Model Infer  │       (process batch on GPU)
          │  (batched)    │
          └───────┬───────┘
                  │
                  ▼
          Cache Result ──▶ Return response
```

**The Math:**

- **Throughput** = requests / second
- **Little's Law**: `L = λ · W` — concurrent requests (`L`) = arrival rate (`λ`) × avg latency (`W`).
  If your server handles 100 req/s and each takes 200ms → you need `100 × 0.2 = 20` concurrent workers.
- **Percentile latency**: p50/p95/p99 = the latency below which 50%/95%/99% of requests complete.
  SLAs are typically set on p99 (worst-case user experience).
- **Batch tradeoff**: `throughput ≈ batch_size / latency(batch_size)`.
  Larger batch → better GPU utilization but higher per-request latency (each request waits for the batch to fill).

### Recipe — Quantization (PyTorch)

```python
import torch

# Post-training quantization: 32-bit → 8-bit
quantized = torch.quantization.quantize_dynamic(
    model, {torch.nn.Linear}, dtype=torch.qint8
)
# ~2-4x faster, ~4x smaller, <1% accuracy loss typically
```

**The Math:**

- **Scale factor**: `scale = (x_max - x_min) / (q_max - q_min)` — maps the float range to the integer range.
- **Quantize** (float → int8): `q = round(x / scale) + zero_point`
- **Dequantize** (int8 → float): `x̂ = scale · (q - zero_point)`

For INT8: `q_min = -128`, `q_max = 127`. A FP32 weight range of `[-3.0, 3.0]`
gives `scale = 6.0 / 255 ≈ 0.0235`. The approximation error per weight is at
most `scale / 2 ≈ 0.012` — tiny relative to the weight magnitudes, which is why
accuracy loss is typically <1%.

### Recipe — Prediction Caching

```python
from functools import lru_cache
import hashlib, json

@lru_cache(maxsize=10_000)
def cached_predict(features_hash):
    return model.predict([features_from_hash(features_hash)])

def predict(features):
    key = hashlib.md5(json.dumps(features).encode()).hexdigest()
    return cached_predict(key)
```

### When to Use

Apply optimizations in this order (easiest → hardest):

1. **Caching** — free wins if you have repeated inputs
2. **Quantization** — one line of code, big latency improvement
3. **ONNX Runtime** — swap runtime, no model changes needed
4. **Batching** — requires request queuing logic
5. **GPU** — only for deep learning models; tree models are CPU-fast
6. **Model distillation** — train a smaller model to mimic the big one

**The Math (Model Compression):**

- **Pruning**: remove weights where `|w| < threshold`. The remaining sparse
  network is smaller and faster. Typical threshold: remove bottom 50–90% of
  weights by magnitude.
- **Knowledge Distillation** loss:
  `L = α · L_hard(y, p_student) + (1 - α) · T² · L_soft(p_teacher/T, p_student/T)`
  where `T` = temperature (higher T → softer probability distributions, more
  knowledge transfer), `α` = balance between hard labels and teacher guidance.
  Typical values: `T = 3–20`, `α = 0.1–0.5`.

> **Rule of thumb**: Tree-based models(XGBoost, RandomForest) rarely need GPU.
> Neural networks almost always benefit from GPU at scale.

---

## 6. ONNX Deep Dive

### Intuition (Backend Analogy)

ONNX is the **universal translator** for ML models. Like how Protocol Buffers
let you define a schema once and generate code for any language, ONNX lets you
train in any framework and serve in any runtime.

```
  ┌──────────┐   ┌──────────┐   ┌──────────┐
  │ PyTorch  │   │  sklearn │   │ TF/Keras │
  └────┬─────┘   └────┬─────┘   └────┬─────┘
       │              │              │
       ▼              ▼              ▼
  ┌──────────────────────────────────────────┐
  │           ONNX Format (.onnx)            │
  │  (Standardized computation graph)        │
  └──────────────────┬───────────────────────┘
                     │
       ┌─────────────┼──────────────┐
       ▼             ▼              ▼
  ┌─────────┐  ┌──────────┐  ┌──────────┐
  │ Python  │  │  C++ /   │  │  C# /    │
  │ Runtime │  │  Java    │  │  Edge    │
  └─────────┘  └──────────┘  └──────────┘
```

### Recipe — sklearn to ONNX to Inference

```python
# Step 1: Export (see Section 2 recipe)
# Step 2: Inference with ONNX Runtime
import onnxruntime as ort
import numpy as np

session = ort.InferenceSession("model.onnx")
input_name = session.get_inputs()[0].name
result = session.run(None, {input_name: np.array([[5.1, 3.5, 1.4, 0.2]], dtype=np.float32)})
print(result[0])  # predictions
```

### Performance Comparison

```
  sklearn predict():      ████████████████████  100ms
  ONNX Runtime (CPU):     ████████              40ms
  ONNX Runtime (GPU):     ███                   15ms
  ONNX + Quantized:       ██                    10ms
```

> These are illustrative — actual speedups depend on model type and size.
> Tree models: 2–5x faster. Neural nets: 5–10x faster.

### When to Use

- **Always consider ONNX** for production serving — it's almost always faster
- Especially valuable when serving team uses C++/Java/C# (no Python in prod)
- Skip ONNX if your model uses custom ops not in the ONNX spec
- Skip if you're in a pure TensorFlow shop (SavedModel + TF Serving is fine)

---

## 7. Model Versioning & Registry

### Intuition (Backend Analogy)

A model registry is a **Docker registry for ML models**. Instead of tagging
container images (`v1.2.3`), you tag trained models with their version, metrics,
and training lineage.

```
  ┌─────────────────────────────────────────────────────────────┐
  │                   MODEL REGISTRY                            │
  │                                                             │
  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
  │  │  Model v1   │  │  Model v2   │  │  Model v3   │        │
  │  │  acc: 0.89  │  │  acc: 0.91  │  │  acc: 0.93  │        │
  │  │  date: Jan  │  │  date: Mar  │  │  date: Jun  │        │
  │  │  data: v4   │  │  data: v5   │  │  data: v6   │        │
  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
  │         │                │                │                │
  │         │          ┌─────┴─────┐    ┌─────┴─────┐         │
  │         │          │  Staging  │    │Production │         │
  │         │          │  (canary) │    │  (live)   │         │
  │         │          └───────────┘    └───────────┘         │
  └─────────────────────────────────────────────────────────────┘

  Deployment:  v3 serves 95% traffic  ◀── Blue (stable)
               v2 serves  5% traffic  ◀── Green (canary)
```

### Recipe — MLflow Model Registry

```python
import mlflow

# Log model during training
with mlflow.start_run():
    mlflow.log_params({"n_estimators": 100, "max_depth": 5})
    mlflow.log_metrics({"accuracy": 0.93, "f1": 0.91})
    mlflow.sklearn.log_model(model, "model", registered_model_name="fraud-detector")

# Load specific version for serving
model = mlflow.sklearn.load_model("models:/fraud-detector/3")
```

### When to Use

- **Always** use a model registry in production — never serve models from local files
- MLflow is the default open-source choice
- Managed options: AWS SageMaker Model Registry, Vertex AI Model Registry
- A/B testing and canary deployment are **essential** — never do big-bang model swaps

---

## 8. Monitoring in Production

### Intuition (Backend Analogy)

You already monitor latency, error rates, and throughput for your APIs. ML models
need the same — **plus** monitoring of the model's *quality* over time. Think of
it as Datadog/Prometheus for model accuracy.

```
  ┌─────────────────────────────────────────────────────────┐
  │              TRADITIONAL MONITORING                     │
  │  Latency │ Error Rate │ Throughput │ CPU/Memory         │
  ├─────────────────────────────────────────────────────────┤
  │              ML-SPECIFIC MONITORING                     │
  │  Data Drift │ Model Drift │ Prediction Distribution    │
  │                                                         │
  │  Input features          Model accuracy       Outputs   │
  │  shifting?  ──────▶      degrading?  ──────▶  biased?   │
  └─────────────────────────────────────────────────────────┘
```

### How It Works — Drift Detection

```
  Training Data Distribution        Production Data Distribution
  ┌───────────────────────┐         ┌───────────────────────┐
  │     ▄▄████▄▄          │         │          ▄▄████▄▄     │
  │   ▄██████████▄        │         │        ▄██████████▄   │
  │  ████████████████     │         │   ████████████████    │
  │  mean=50, std=10      │         │   mean=65, std=15     │
  └───────────────────────┘         └───────────────────────┘
           │                                  │
           └──────── Statistical Test ────────┘
                     (KS test, PSI)
                          │
                     Drift Score > threshold?
                          │
                    ┌─────┴─────┐
                    Yes         No
                    │           │
               Alert &      Continue
               Retrain      serving
```

**The Math:**

- **PSI (Population Stability Index)**:
  `PSI = Σᵢ (actualᵢ - expectedᵢ) · ln(actualᵢ / expectedᵢ)`
  where `actualᵢ` and `expectedᵢ` are the proportion of observations in bin `i`
  for production and training data respectively.
  Interpretation: PSI < 0.1 → stable; 0.1–0.25 → moderate drift; > 0.25 → significant drift.
- **KS (Kolmogorov-Smirnov) Statistic**:
  `D = sup_x |F_train(x) - F_prod(x)|`
  Maximum absolute difference between the training and production CDFs.
  Larger D → greater distributional shift. Commonly flag when `D > 0.05` with a
  significant p-value.

### What to Monitor

| Metric                  | What It Detects          | Backend Equivalent        |
|-------------------------|--------------------------|---------------------------|
| Prediction latency      | Slow inference           | API response time         |
| Error rate              | Failed predictions       | HTTP 5xx rate             |
| Prediction distribution | Output bias/shift        | Response payload analysis |
| Feature distributions   | Input data drift         | Request payload analysis  |
| Accuracy (if labeled)   | Model degradation        | Business KPI dashboards   |

### When to Retrain

| Strategy              | How It Works                          | Use When                    |
|-----------------------|---------------------------------------|-----------------------------|
| Scheduled             | Retrain weekly/monthly automatically  | Stable domain, low drift    |
| Drift-triggered       | Retrain when drift score > threshold  | Dynamic data distributions  |
| Performance-triggered | Retrain when accuracy drops below X%  | Labels available quickly    |
| Continuous            | Train on every new batch of data      | High-velocity data streams  |

> **Rule of thumb**: Start with scheduled retraining (e.g., weekly). Add drift
> detection once you've been in production for a month and have baseline metrics.

---

## 9. Scaling Model Serving

### Intuition (Backend Analogy)

Scaling model serving is **identical** to scaling any microservice — with one
extra consideration: GPU resources are expensive and shared differently than CPU.

### How It Works

```
                    Load Balancer
                    (round-robin)
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼
       ┌─────────┐ ┌─────────┐ ┌─────────┐
       │ Model   │ │ Model   │ │ Model   │
       │ Server  │ │ Server  │ │ Server  │
       │ Pod 1   │ │ Pod 2   │ │ Pod 3   │
       │ (CPU)   │ │ (CPU)   │ │ (GPU)   │
       └─────────┘ └─────────┘ └─────────┘
            │            │            │
            │            │            ├── DL models (BERT, etc.)
            │            │
            │            ├── Tree models (XGBoost)
            │
            ├── Tree models (RandomForest)

  HPA: scale pods based on latency P95 or request queue depth
```

**The Math:**

- **Autoscaling target**: `desired_replicas = ⌈current_traffic / capacity_per_replica⌉`
  e.g., 1000 req/s with 200 req/s per pod → `⌈1000/200⌉ = 5` replicas.
- **SLA constraint**: scale to keep `avg_latency ≤ SLA_target`.
  If P95 latency crosses the SLA, the HPA adds pods. If traffic drops and latency
  is well below target, HPA removes pods.
- Combined with **Little's Law** (Section 5): `L = λ · W` tells you the steady-state
  concurrency, which determines how many worker threads (and thus pods) you need.

### Scaling Options

| Approach                    | Best For                           | Tradeoff                     |
|-----------------------------|------------------------------------|------------------------------|
| Horizontal (more pods)      | Stateless models, easy scaling     | Linear cost growth           |
| GPU sharing (multi-model)   | Multiple small DL models           | Complex memory management    |
| Triton Inference Server     | High-throughput DL serving         | Operational complexity       |
| KServe on Kubernetes        | Standardized model deployment      | K8s expertise required       |
| Serverless (Lambda/Azure)   | Bursty traffic, light models       | Cold start latency, size cap |

### Recipe — K8s Deployment Sketch

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: model-server
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: model
        image: myregistry/model-server:v3
        resources:
          limits: { memory: "2Gi", cpu: "2" }
        readinessProbe:
          httpGet: { path: /health, port: 8080 }
```

### When to Use

- **Start simple**: single FastAPI container, scale horizontally with a load balancer
- **Add GPU** only for deep learning models with latency requirements
- **Triton/KServe** when you have 5+ models to serve and a platform team
- **Serverless** for low-traffic models or prototypes (watch cold starts)

> **Rule of thumb**: If your model runs fine on CPU in <50ms, don't bother with
> GPU serving. Most tree-based models and small neural nets are CPU-friendly.

---

## Exercises

### Exercise 1: Conceptual — Latency Optimization

> Your model inference takes 500ms on CPU. You need <50ms in production.
> List 4 optimization strategies in order of ease-of-implementation.

<details>
<summary>Solution</summary>

1. **ONNX Runtime** — Export model to ONNX, swap runtime. One-time effort, often
   2–5x speedup. Easiest because it requires no model changes.

2. **Quantization** — Convert 32-bit weights to 8-bit. A few lines of code,
   2–4x speedup with minimal accuracy loss.

3. **Caching** — If you have repeated/similar inputs, cache predictions.
   Requires a cache layer (Redis/in-memory) but no model changes.

4. **GPU serving** — Move inference to GPU. Requires infrastructure changes
   (GPU instances, CUDA drivers) but gives 5–10x for neural networks.

Other options (harder): model distillation, pruning, custom hardware (TPU).

</details>

---

### Exercise 2: Implementation — FastAPI Model Server

> Create a FastAPI endpoint that loads a pre-trained sklearn model and serves
> predictions. Include health check and input validation. Max 15 lines.

<details>
<summary>Solution</summary>

```python
from fastapi import FastAPI
from pydantic import BaseModel
import joblib

app = FastAPI()
model = joblib.load("model.joblib")

class PredictRequest(BaseModel):
    features: list[float]

@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": model is not None}

@app.post("/predict")
def predict(req: PredictRequest):
    prediction = model.predict([req.features])
    return {"prediction": prediction.tolist()}
```

Key points:
- Model loaded **once** at module level (not per-request)
- Pydantic validates input automatically (returns 422 on bad input)
- Health check confirms model is loaded
- 15 lines total

</details>

---

### Exercise 3: Real-World Scenario — Monitoring & Retraining

> Your recommendation model serves 10K requests/sec. Accuracy drops 5% over
> 3 months. Describe your monitoring and retraining strategy.

<details>
<summary>Solution</summary>

**Monitoring setup:**
1. **Operational metrics**: latency P50/P95/P99, error rate, throughput (same as
   any service — Prometheus + Grafana)
2. **Prediction distribution**: track what the model predicts over time. A shift
   from 60/40 to 90/10 split signals something changed.
3. **Feature drift**: compare production feature distributions against training
   data weekly using Population Stability Index (PSI).
4. **Business metrics**: click-through rate, conversion rate — the metrics that
   actually matter.

**Retraining strategy:**
1. **Scheduled baseline**: retrain weekly on the latest 90 days of data.
2. **Drift trigger**: if PSI > 0.2 on any key feature, trigger an immediate
   retrain.
3. **Canary deployment**: new model serves 5% of traffic for 24h. Compare
   business metrics (CTR, conversion) against the current model.
4. **Automatic rollback**: if new model's CTR is >1% worse, auto-rollback to
   the previous version.

```
  Weekly data ──▶ Retrain ──▶ Validate ──▶ Canary (5%) ──▶ Promote
                                │                │
                           Metrics OK?      Metrics OK?
                                │                │
                           No → Skip        No → Rollback
```

**Why accuracy dropped**: Most likely cause is **data drift** — user behavior
changed over 3 months (new products, seasonal trends, user demographics). The
model was trained on stale patterns.

</details>

---

## Quick Reference Card

```
  ┌──────────────────────────────────────────────────────┐
  │           INFERENCE & SERVING CHEAT SHEET            │
  ├──────────────────────────────────────────────────────┤
  │                                                      │
  │  Serialization:                                      │
  │    Prototype  → joblib/pickle                        │
  │    Production → ONNX (cross-lang) or TorchScript     │
  │                                                      │
  │  Serving Pattern:                                    │
  │    Default    → REST API (FastAPI)                   │
  │    Big data   → Batch (Spark)                        │
  │    Events     → Streaming (Kafka)                    │
  │    Edge       → Embedded (ONNX on device)            │
  │                                                      │
  │  Optimization Order:                                 │
  │    1. Cache  2. ONNX  3. Quantize  4. Batch  5. GPU │
  │                                                      │
  │  Monitoring:                                         │
  │    Standard  → latency, errors, throughput           │
  │    ML-extra  → data drift, prediction distribution   │
  │                                                      │
  │  Scaling:                                            │
  │    Start     → horizontal pods + load balancer       │
  │    Advanced  → Triton / KServe + GPU sharing         │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```
