# Azure ML & Cloud Solutions

> **🎯 Goal**: Know the Azure ML ecosystem and how to deploy ML in the cloud
> **⏱ Time**: 3–4h study + 1h exercises
> **📦 Deliverable**: Ability to architect ML solutions on Azure

---

## Table of Contents

1. [Azure ML Landscape Overview](#1-azure-ml-landscape-overview)
2. [Azure Machine Learning Workspace](#2-azure-machine-learning-workspace)
3. [Compute Targets](#3-compute-targets)
4. [Azure ML Pipelines](#4-azure-ml-pipelines)
5. [Model Registry & Deployment](#5-model-registry--deployment)
6. [Azure Cognitive Services](#6-azure-cognitive-services)
7. [Azure OpenAI Service](#7-azure-openai-service)
8. [MLOps on Azure](#8-mlops-on-azure)
9. [Azure Databricks for ML](#9-azure-databricks-for-ml)
10. [Cost Management](#10-cost-management)
11. [Architecture Patterns](#11-architecture-patterns)
12. [Decision Matrix](#12-decision-matrix--which-azure-service)

---

## 1. Azure ML Landscape Overview

### Intuition (Backend Analogy)

Azure offers multiple abstraction levels for ML, just like it does for compute:
- **Azure Functions** → **Cognitive Services** (just call an API, zero infrastructure)
- **App Service** → **Azure ML Managed Endpoints** (managed, some control)
- **AKS** → **Azure ML + custom compute** (full control, more ops work)
- **VMs** → **Databricks / custom clusters** (you manage everything)

Pick the highest abstraction that meets your requirements.

### How It Works

```
┌─────────────────────── Azure AI Ecosystem ───────────────────────┐
│                                                                   │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ Cognitive Svc    │  │ Azure OpenAI     │  │ AI Search      │  │
│  │ (Pre-built APIs) │  │ (GPT, DALL-E,    │  │ (Vector +      │  │
│  │ Vision, Language │  │  Embeddings)     │  │  Keyword)      │  │
│  │ Speech, Decision │  │                  │  │                │  │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬────────┘  │
│           │                     │                     │           │
│           ▼                     ▼                     ▼           │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              Azure Machine Learning (Full Platform)        │  │
│  │  Workspace │ Pipelines │ Compute │ Registry │ Endpoints    │  │
│  └────────────────────────────┬───────────────────────────────┘  │
│                               │                                   │
│  ┌────────────────────────────┴───────────────────────────────┐  │
│  │              Azure Databricks (Big Data + ML)              │  │
│  │  Spark │ MLflow │ Feature Store │ Delta Lake                │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  Infrastructure: Azure Storage │ Key Vault │ Container Registry  │
└───────────────────────────────────────────────────────────────────┘
```

### When to Use

| Abstraction Level | Service | You Manage |
|---|---|---|
| **Highest** (just call API) | Cognitive Services | Nothing — just an API key |
| **High** (deploy a model) | Azure OpenAI | Deployment config, prompts |
| **Medium** (train + deploy) | Azure ML | Data, code, compute choices |
| **Low** (big data + ML) | Databricks | Spark clusters, data pipelines |

---

## 2. Azure Machine Learning Workspace

### Intuition (Backend Analogy)

A Workspace is the **Resource Group for ML**. Just like a Resource Group organizes your App Service, SQL Database, and Storage Account — a Workspace organizes your compute, data, experiments, models, and endpoints.

### How It Works

```
┌──────────────── Azure ML Workspace ─────────────────┐
│                                                      │
│  ┌──────────────┐   ┌──────────────┐                │
│  │  Datastores   │   │   Datasets   │   ← Data      │
│  │  (Blob, ADLS) │   │  (versioned) │                │
│  └──────┬────────┘   └──────┬───────┘                │
│         │                   │                        │
│         ▼                   ▼                        │
│  ┌──────────────┐   ┌──────────────┐                │
│  │   Compute     │   │ Experiments  │   ← Training   │
│  │  (Instance,   │   │ (runs, logs, │                │
│  │   Cluster)    │   │  metrics)    │                │
│  └──────┬────────┘   └──────┬───────┘                │
│         │                   │                        │
│         ▼                   ▼                        │
│  ┌──────────────┐   ┌──────────────┐                │
│  │   Models      │   │  Endpoints   │   ← Serving    │
│  │  (Registry,   │   │ (Online,     │                │
│  │   versions)   │   │  Batch)      │                │
│  └──────────────┘   └──────────────┘                │
│                                                      │
│  Backing: Storage Account │ Key Vault │ ACR │ App    │
│           Insights                                   │
└──────────────────────────────────────────────────────┘

Two ways to interact: **Studio UI** (browser, no-code) or **SDK v2** (Python, automate everything).
```

### Recipe: Create Workspace + Submit Training Job

```python
from azure.ai.ml import MLClient, command
from azure.identity import DefaultAzureCredential

ml = MLClient(
    DefaultAzureCredential(),
    subscription_id="<sub-id>",
    resource_group_name="my-rg",
    workspace_name="my-ml-workspace",
)

job = command(
    code="./src",
    command="python train.py --lr 0.01 --epochs 10",
    environment="AzureML-sklearn-1.0@latest",
    compute="gpu-cluster",
)
ml.jobs.create_or_update(job)
```

### When to Use

- **Studio UI**: exploration, quick experiments, non-ML-engineer stakeholders
- **SDK v2**: automation, CI/CD pipelines, reproducible workflows
- **CLI v2**: shell scripts, DevOps pipelines, infrastructure-as-code

---

## 3. Compute Targets

### Intuition (Backend Analogy)

| Compute Target | Backend Equivalent | Use Case |
|---|---|---|
| **Compute Instance** | Dev VM / Codespace | Interactive development, notebooks |
| **Compute Cluster** | AKS node pool (auto-scales) | Training jobs (scales 0→N→0) |
| **Serverless Compute** | Azure Functions | Pay-per-job, no cluster management |
| **Managed Endpoint** | App Service | Real-time inference |
| **Attached Compute** | Bring-your-own AKS/VMs | Custom hardware, existing infra |

### How It Works

```
Developer Workflow:
  ┌──────────────┐   submit job   ┌──────────────┐
  │   Compute    │ ──────────────►│   Compute    │  Auto-scales
  │   Instance   │                │   Cluster    │  0 → N → 0
  │  (your dev   │                │ (training)   │  for training
  │   machine)   │                └──────┬───────┘
  └──────────────┘                       ▼
                                  ┌──────────────┐
                                  │   Managed    │  Auto-scaling
                                  │   Endpoint   │  inference
                                  └──────────────┘
```

### Cost Optimization Cheat Sheet

```
╔═══════════════════════════════════════════════════════╗
║  COST OPTIMIZATION RULES                              ║
╠═══════════════════════════════════════════════════════╣
║  1. Spot instances for training    → up to 80% off   ║
║  2. Auto-shutdown dev instances    → no overnight $   ║
║  3. Scale clusters to 0 min nodes  → pay only when   ║
║     running                                           ║
║  4. Don't use GPU for tree models  → CPU is enough   ║
║  5. Reserved instances for steady  → up to 60% off   ║
║     inference load                                    ║
║  6. Right-size: start small,       → scale up if     ║
║     benchmark, then decide           needed           ║
╚═══════════════════════════════════════════════════════╝
```

**The Math:**

```
Total Compute Cost:
  cost_total = Σᵢ (hoursᵢ × rateᵢ) + storage_cost + API_calls × cost_per_call + network_egress

  where rateᵢ = VM SKU hourly price (see table in §10)

Spot / Low-Priority Discount:
  effective_rate = on_demand_rate × (1 - discount)
  discount ≈ 0.60 to 0.80  (60–80% off, but job can be preempted)

Example — 8-hour training on NC6:
  on-demand  = 8 × $0.90 = $7.20
  spot (~70%) = 8 × $0.27 = $2.16   ← save $5.04
```

### When to Use

- **Dev/experimentation**: Compute Instance (Standard_DS3_v2, ~$0.20/hr)
- **Training**: Compute Cluster with spot VMs (min_nodes=0, scales automatically)
- **Quick one-off job**: Serverless Compute (zero setup)
- **Production inference**: Managed Online Endpoint (auto-scales on load)

---

## 4. Azure ML Pipelines

### Intuition (Backend Analogy)

Azure ML Pipelines are like **Azure DevOps Pipelines but for ML**:
- DevOps: build → test → package → deploy (code)
- ML: prep data → train → evaluate → register → deploy (models)

Each step is a **Component** (like a pipeline Task in DevOps) — reusable, versioned, parameterized.

### How It Works

```
┌────────────────── Azure ML Pipeline ──────────────────┐
│                                                        │
│  ┌──────────┐   ┌──────────┐   ┌──────────────────┐  │
│  │  Data     │   │  Train   │   │  Evaluate        │  │
│  │  Prep     │──►│  Model   │──►│  (accuracy >     │  │
│  │           │   │          │   │   threshold?)    │  │
│  └──────────┘   └──────────┘   └────────┬─────────┘  │
│                                     yes  │  no        │
│                                 ┌────────┘  └──► STOP │
│                                 ▼                     │
│                          ┌──────────┐                 │
│                          │ Register │                 │
│                          │ Model    │                 │
│                          └────┬─────┘                 │
│                               ▼                       │
│                          ┌──────────┐                 │
│                          │  Deploy  │                 │
│                          │ Endpoint │                 │
│                          └──────────┘                 │
│                                                        │
│  Triggers: schedule │ data change │ code push          │
└────────────────────────────────────────────────────────┘
```

### Recipe: Define a 3-Step Pipeline

```python
from azure.ai.ml import dsl, Input
from azure.ai.ml.entities import Pipeline

@dsl.pipeline(compute="cpu-cluster")
def training_pipeline(raw_data: Input):
    prep_step = prep_component(input_data=raw_data)
    train_step = train_component(
        training_data=prep_step.outputs.output_data
    )
    eval_step = eval_component(
        model=train_step.outputs.model_output,
        test_data=prep_step.outputs.test_split,
    )
    return {"model": train_step.outputs.model_output}

pipeline_job = training_pipeline(
    raw_data=Input(path="azureml:my-dataset@latest")
)
ml.jobs.create_or_update(pipeline_job)
```

### When to Use

- **Repeatable workflows**: any multi-step ML process
- **Scheduled retraining**: cron-like triggers for model freshness
- **Team collaboration**: shared components, versioned pipelines
- **Audit trail**: every run is logged with inputs, outputs, metrics

**The Math:**

```
Pipeline Cost:
  cost_pipeline = Σ_steps (compute_timeᵢ × compute_rateᵢ)

  Each step may use a different VM SKU, so rates vary per step.

Data Parallelism Speedup:
  ideal_speedup = N            (N = number of workers)
  actual_speedup ≈ N / (1 + α)  where α = communication_overhead / compute_time

  If α → 0  (compute-bound): near-linear speedup
  If α → ∞  (comms-bound):   adding workers doesn't help

Example — 4 workers, α = 0.2:
  speedup ≈ 4 / 1.2 ≈ 3.3×    (82% efficiency)
```

---

## 5. Model Registry & Deployment

### Intuition (Backend Analogy)

Model Registry = **Container Registry for ML models**. You push versioned models (like Docker images), tag them, and deploy to various targets (like deploying containers to App Service, AKS, or ACI).

### How It Works

```
 Register                        Deploy Targets
┌────────────┐
│ Model v1   │──┐   ┌────────────────────────────────────────┐
│ Model v2   │──┤   │ Managed Online Endpoint (real-time)   │ ← like App Service
│ Model v3 ★ │──┼──►│ Managed Batch Endpoint  (batch)       │ ← like WebJob
└────────────┘  │   │ AKS Deployment          (full ctrl)   │ ← like own K8s
                │   │ ACI                     (quick test)  │ ← like staging slot
                │   └────────────────────────────────────────┘

Blue-Green / Canary — gradually shift traffic between model versions:
  Model v2 (blue) ── 90%   Model v3 (green) ── 10%
```

### Recipe: Deploy Model to Managed Endpoint

```python
from azure.ai.ml.entities import (
    ManagedOnlineEndpoint, ManagedOnlineDeployment,
)

endpoint = ManagedOnlineEndpoint(
    name="my-model-endpoint", auth_mode="key"
)
ml.online_endpoints.begin_create_or_update(endpoint).result()

deployment = ManagedOnlineDeployment(
    name="blue",
    endpoint_name="my-model-endpoint",
    model="azureml:my-model@latest",
    instance_type="Standard_DS3_v2",
    instance_count=1,
)
ml.online_deployments.begin_create_or_update(deployment).result()
```

### When to Use

| Target | Latency | Scale | Control | Cost |
|---|---|---|---|---|
| **Managed Online** | Low (ms) | Auto | Medium | Per-instance |
| **Managed Batch** | High (min) | Massive | Medium | Per-job |
| **AKS** | Low (ms) | Manual/HPA | Full | Cluster cost |
| **ACI** | Medium | None | Low | Per-second |

**The Math:**

```
Autoscaling (Managed Endpoints):
  desired_replicas = ⌈ traffic / capacity_per_replica ⌉

  Scale trigger: avg(metric) > threshold  over  window
  Cooldown:      wait N seconds between scale actions to avoid flapping

  Example — 450 req/s, each replica handles 100 req/s:
    desired = ⌈450 / 100⌉ = 5 replicas

Inference Cost per Request:
  cost_per_request = instance_cost_per_hour / requests_per_hour + overhead

  Example — Standard_DS3_v2 ($0.20/hr) serving 3 600 req/hr:
    cost_per_request = $0.20 / 3600 ≈ $0.000056  (~$0.06 per 1K requests)

Batch vs Real-Time Trade-off:
  batch_cost  ≈ spot_rate × total_compute_hours   (cheaper, higher latency)
  online_cost ≈ on_demand_rate × hours_running     (costlier, always-on)
```

---

## 6. Azure Cognitive Services

### Intuition (Backend Analogy)

Cognitive Services = **managed database vs. self-hosted**. You don't train models, you don't manage infrastructure — you just call an API. Faster to start, less control, works great for standard tasks.

### How It Works

```
┌──────────────── Cognitive Services ──────────────────┐
│                                                       │
│  Vision:     Image classify, object detect, OCR       │
│  Language:   Sentiment, NER, translate, summarize, QA │
│  Speech:     Speech↔Text, real-time transcription     │
│  Decision:   Anomaly Detector, Content Moderator,     │
│              Personalizer                             │
│                                                       │
│  Usage: POST https://<endpoint>.cognitiveservices     │
│         .azure.com/<service>/v3.1/analyze             │
│         Header: Ocp-Apim-Subscription-Key: <key>      │
└───────────────────────────────────────────────────────┘
```

### Recipe: Sentiment Analysis in 5 Lines

```python
from azure.ai.textanalytics import TextAnalyticsClient
from azure.core.credentials import AzureKeyCredential

client = TextAnalyticsClient(
    endpoint="https://my-cog.cognitiveservices.azure.com",
    credential=AzureKeyCredential("<key>"),
)
result = client.analyze_sentiment(["The product is amazing!"])
print(result[0].sentiment)  # "positive"
```

### When to Use

- ✅ Standard NLP/Vision tasks (sentiment, OCR, object detection)
- ✅ Fast time-to-market (minutes, not weeks)
- ✅ No ML expertise on team
- ❌ Need custom categories or domain-specific accuracy
- ❌ Data cannot leave your VNet (some services support VNet, check docs)

---

## 7. Azure OpenAI Service

### Intuition (Backend Analogy)

Azure OpenAI = **running OpenAI's GPT inside your Azure subscription**. Like the difference between using a public SaaS API vs. deploying a managed service in your own VNet. Same models, but enterprise guardrails.

### How It Works

```
┌──────────── Azure OpenAI ──────────────┐
│  my-resource.openai.azure.com          │
│  ├─ YOUR Azure tenant + data residency │
│  ├─ VNet + Private Endpoint            │
│  ├─ Managed Identity + RBAC + audit    │
│  └─ Built-in content filtering         │
└────────────────────────────────────────┘

Deployment: Create Resource → Deploy Model (gpt-4, gpt-3.5-turbo,
            DALL-E 3, Whisper, text-embedding-*) → Call via SDK/REST
```

### Recipe: Chat Completion in 5 Lines

```python
from openai import AzureOpenAI

client = AzureOpenAI(
    azure_endpoint="https://my-resource.openai.azure.com",
    api_version="2024-02-01", azure_deployment="gpt-4",
)
response = client.chat.completions.create(
    model="gpt-4",
    messages=[
        {"role": "system", "content": "You are a helpful assistant."},
        {"role": "user", "content": "Explain Azure ML in one sentence."},
    ],
    temperature=0.7, max_tokens=200,
)
print(response.choices[0].message.content)
```

**The Math:**

```
Azure OpenAI Token Cost:
  cost ≈ (input_tokens × price_per_1K_input + output_tokens × price_per_1K_output) / 1000

  Approximate rates (GPT-4, may vary by region/tier):
    input  ≈ $0.03 per 1K tokens
    output ≈ $0.06 per 1K tokens

  Example — summarize a 2 000-token doc, 500-token output:
    cost = (2000 × 0.03 + 500 × 0.06) / 1000 = $0.09 per request

  At 10 000 requests/day:
    daily_cost ≈ 10 000 × $0.09 = $900/day

  Cost Reduction Levers:
    • Use GPT-3.5-Turbo (~10× cheaper) for simpler tasks
    • Cache common prompts to avoid repeat calls
    • Batch requests where latency allows
```

### When to Use

- ✅ Chat/conversational AI, document summarization, code generation
- ✅ Enterprise: need VNet, RBAC, data residency, audit logs
- ✅ Embeddings for semantic search (RAG architectures)
- ✅ Fine-tuning GPT on your domain data
- ❌ Simple classification → use Cognitive Services (cheaper)
- ❌ Need real-time image recognition → use Cognitive Services Vision

---

## 8. MLOps on Azure

### Intuition (Backend Analogy)

MLOps = **DevOps for ML**. You already do CI/CD for code. MLOps adds CI/CD for data and models: test your model like you test your code, deploy it like you deploy your app, monitor it like you monitor your service.

### How It Works

```
┌────────────────── MLOps Lifecycle ──────────────────────────┐
│                                                              │
│  ┌──────┐   ┌───────┐   ┌──────────┐   ┌────────────────┐  │
│  │ Code │──►│ Train  │──►│ Evaluate │──►│ Register Model │  │
│  │ Push │   │ (Azure │   │ (metrics │   │ (versioned in  │  │
│  │ (Git)│   │  ML)   │   │  gates)  │   │  registry)     │  │
│  └──────┘   └───────┘   └──────────┘   └───────┬────────┘  │
│      ▲                                          │            │
│      │                                          ▼            │
│  ┌───┴──────┐                          ┌────────────────┐   │
│  │ Retrain  │◄─────── trigger ─────────│ Deploy         │   │
│  │ (on data │                          │ (Managed       │   │
│  │  drift)  │                          │  Endpoint)     │   │
│  └──────────┘                          └───────┬────────┘   │
│      ▲                                         │            │
│      │                                         ▼            │
│  ┌───┴──────────────────────────────────────────────────┐   │
│  │                    Monitor                            │   │
│  │  • Data drift (input distribution changed?)           │   │
│  │  • Prediction quality (accuracy dropping?)            │   │
│  │  • Responsible AI (fairness, bias, errors)            │   │
│  └───────────────────────────────────────────────────────┘   │
│                                                              │
│  Tooling:                                                    │
│  ├─ GitHub Actions / Azure DevOps   (CI/CD orchestration)   │
│  ├─ Azure ML CLI v2                 (pipeline automation)    │
│  ├─ Azure Monitor + App Insights    (operational metrics)    │
│  └─ Responsible AI Dashboard        (fairness + explainability)│
└──────────────────────────────────────────────────────────────┘
```

### Key Monitoring Signals

| What to Monitor | How | Alert When |
|---|---|---|
| Data drift | Compare input distributions | PSI > 0.25 |
| Prediction quality | Compare vs ground truth | Accuracy below SLA |
| Latency / RPS | App Insights | p99 > target |
| Fairness | Responsible AI dashboard | Disparity across groups |

**The Math:**

```
Drift Detection — Population Stability Index (PSI):
  PSI = Σᵢ (aᵢ - eᵢ) · ln(aᵢ / eᵢ)

  where aᵢ = actual proportion in bin i
        eᵢ = expected (training) proportion in bin i

  Interpretation:
    PSI < 0.10  → no significant drift
    PSI 0.10–0.25 → moderate drift, investigate
    PSI > 0.25  → significant drift → trigger retrain

Latency SLAs: Track p50, p95, p99. Alert when p99 > target_ms.
Common uptimes: 99.9% (8.7h/yr down) | 99.95% (4.4h/yr) | 99.99% (53min/yr)
```

### When to Use

- **Always** for production ML. No MLOps = "works on my laptop" syndrome.
- Start with CI/CD for training pipelines (GitHub Actions + Azure ML CLI v2).
- Add monitoring after first deployment. Add Responsible AI for customer-facing models.

---

## 9. Azure Databricks for ML

### Intuition (Backend Analogy)

Databricks = **the Swiss Army knife for big data + ML**. If you already have data engineers using Spark and Delta Lake, Databricks lets ML engineers work in the same ecosystem. Like how a full-stack team uses the same monorepo for frontend and backend.

### How It Works

```
┌──────────────── Azure Databricks ──────────────────┐
│  Data Engineering          ML Engineering           │
│  ┌───────────┐            ┌───────────────┐        │
│  │ Delta Lake│────────────│ Feature Store  │        │
│  │ (storage) │            │ (curated feat.)│        │
│  └─────┬─────┘            └───────┬────────┘        │
│        ▼                          ▼                 │
│  Spark ETL Pipelines      MLflow (tracking)         │
│                            → AutoML (selection)     │
│                                                     │
│  Cluster: Spark workers with GPU/CPU auto-scaling   │
└─────────────────────────────────────────────────────┘
```

### When to Use

- ✅ Data is large (TBs+) and lives in a data lake
- ✅ Team already uses Spark / Delta Lake for ETL
- ✅ Need unified data engineering + ML platform
- ✅ Want MLflow-native experiment tracking
- ❌ Small datasets (< 10GB) → Azure ML is simpler
- ❌ Only need inference / deployment → Azure ML endpoints
- ❌ Just need pre-built AI → Cognitive Services or Azure OpenAI

---

## 10. Cost Management

### Intuition (Backend Analogy)

GPU VMs are the "premium database tier" of ML — powerful but expensive. Just like you wouldn't run a Premium SQL tier for a dev database, don't run GPU clusters for experiments that don't need them.

### How It Works

```
┌──────────── Cost Breakdown (typical ML project) ────────────┐
│  Training: ~60%  |  Inference: ~25%  |  Storage: ~10%       │
│                                                              │
│  GPU VM Hourly Costs (approximate):                          │
│  DS3_v2: $0.20 (CPU) | NC6: $0.90 (1 GPU) | NC24: $3.60    │
│  ND96: $27+ (large models)                                   │
│                                                              │
│  Savings: Spot ~80% off | Reserved ~60% | Auto-shutdown ~50% │
│  Right-sizing ~30% | Scale to 0 min nodes                    │
└──────────────────────────────────────────────────────────────┘
```

**The Math:**

```
Total Monthly Cloud ML Cost:
  cost = Σ(hours × rate) + storage_GB × rate + API_calls × cost/call + egress

Spot vs Reserved:
  Spot: 80% off, risk of preemption → use for fault-tolerant training
  Reserved: 60% off, committed → use for steady-state inference

Cost-per-Prediction:
  cost_per_pred = (training_cost / predictions_before_retrain) + inference_cost/req
  Example: retrain weekly $50, 1M predictions → $0.00005 + $0.00006 ≈ $0.00011/pred
```

### Rules of Thumb

1. **Training**: use spot instances + cluster with `min_nodes=0`
2. **Dev instances**: always enable auto-shutdown (after 30 min idle)
3. **Tree models** (XGBoost, Random Forest): CPU is enough — skip GPU
4. **Deep learning**: start with 1 GPU, benchmark, then scale
5. **Inference**: use managed endpoints with auto-scale (min 1 replica)
6. **Monitor**: set Azure Cost Management budgets + alerts

---

## 11. Architecture Patterns

### Pattern 1: Simple ML API

```
  Client → API Mgmt (rate limit, auth) → Managed Online Endpoint (auto-scale,
           blue-green) → Model v3 (from registry)
  Like: App Service behind API Management
```

### Pattern 2: RAG Application

```
┌──────────── RAG (Retrieval-Augmented Generation) ────────────┐
│                                                               │
│  User Question                                                │
│    │                                                          │
│    ▼                                                          │
│  ┌─────────────┐  embed   ┌────────────────┐                │
│  │ Azure OpenAI │────────►│ Azure AI Search │                │
│  │ (embeddings) │         │ (vector index)  │                │
│  └─────────────┘         └───────┬─────────┘                │
│                                   │ top-K docs               │
│                                   ▼                          │
│  ┌─────────────┐  prompt  ┌────────────────┐                │
│  │ Azure OpenAI │◄────────│ Orchestrator   │                │
│  │ (GPT-4)     │         │ (Function App  │                │
│  └──────┬──────┘         │  or App Svc)   │                │
│         │ answer          └────────────────┘                │
│         ▼                         ▲                          │
│  ┌─────────────┐          ┌──────┴──────┐                   │
│  │   User      │          │  Cosmos DB   │                   │
│  │   (answer)  │          │ (chat hist.) │                   │
│  └─────────────┘          └─────────────┘                   │
│                                                               │
│  Like: Search engine + LLM + conversation state              │
└───────────────────────────────────────────────────────────────┘
```

### Pattern 3: Batch Scoring

```
  Azure Data Factory (scheduler) → triggers Azure ML Batch Endpoint
  Blob Storage (input) → Batch Endpoint → Blob Storage (predictions)
  Like: WebJob reading from queue, writing results to storage
```

---

## 12. Decision Matrix — Which Azure Service?

```
┌──────────────────┬──────────────────┬────────────────────────────────┐
│ Need             │ Service          │ When                           │
├──────────────────┼──────────────────┼────────────────────────────────┤
│ Pre-built AI     │ Cognitive Svc    │ Standard tasks (OCR, sentiment)│
│ LLM / Chat       │ Azure OpenAI     │ GPT apps, RAG, embeddings     │
│ Custom ML model  │ Azure ML         │ Train + deploy custom models   │
│ Big Data ML      │ Databricks       │ TB-scale, existing Spark       │
│ Quick prototype  │ Azure ML AutoML  │ Automated algorithm selection  │
│ Document intel   │ AI Document Intel│ Form/invoice extraction        │
│ Search + AI      │ Azure AI Search  │ Vector/hybrid search, RAG      │
└──────────────────┴──────────────────┴────────────────────────────────┘
```

---

## Exercises

### Exercise 1: Conceptual — Document Summarization

**Your company wants to add AI-powered document summarization. You have Azure. Compare these three approaches:**

**(a) Azure OpenAI GPT-4**
**(b) Azure Cognitive Services Language (extractive summarization)**
**(c) Custom fine-tuned model on Azure ML**

**When would you choose each?**

<details>
<summary>Solution</summary>

| Approach | Pros | Cons | Choose When |
|---|---|---|---|
| **(a) Azure OpenAI GPT-4** | Best quality, abstractive, handles any domain | Higher cost per call (~$0.03/1K tokens), latency ~1-3s | General-purpose summarization, varied document types, quality is top priority |
| **(b) Cognitive Services** | Cheapest, lowest latency, no prompt engineering | Extractive only (picks sentences), less flexible | High volume, cost-sensitive, extractive is good enough |
| **(c) Custom fine-tuned** | Domain-specific accuracy, predictable behavior | Weeks to build, need labeled data, ongoing maintenance | Specialized domain (legal, medical), need specific output format, high volume justifies investment |

**Decision framework:**
1. Start with **(a)** Azure OpenAI — fastest to prototype, best quality
2. If cost is too high at scale or latency matters → try **(b)** Cognitive Services
3. If neither meets domain accuracy requirements → invest in **(c)** custom model

</details>

---

### Exercise 2: Implementation — Azure OpenAI SDK Call

**Write the Azure OpenAI SDK call for chat completion with system prompt and user message. Show temperature and max_tokens configuration. Max 10 lines.**

<details>
<summary>Solution</summary>

```python
from openai import AzureOpenAI

client = AzureOpenAI(
    azure_endpoint="https://my-resource.openai.azure.com",
    api_version="2024-02-01", azure_deployment="gpt-4",
)
response = client.chat.completions.create(
    model="gpt-4",
    messages=[
        {"role": "system", "content": "Summarize documents concisely."},
        {"role": "user", "content": "Summarize: <document text here>"},
    ],
    temperature=0.3, max_tokens=500,
)
print(response.choices[0].message.content)
```

Key params: `temperature` 0.0–0.3 for factual | `max_tokens` caps output | pin `api_version`

</details>

---

### Exercise 3: Real-World Scenario — Customer Support Chatbot

**You're architecting a customer support chatbot that needs to:**
- **Answer questions from a 10K document knowledge base**
- **Handle 1000 concurrent users**
- **Respect GDPR (data stays in EU)**

**Design the Azure architecture.**

<details>
<summary>Solution</summary>

```
Architecture: RAG-based Customer Support Chatbot (EU Region)

┌─────────────────────────────────────────────────────────────┐
│  All resources deployed in West Europe region (GDPR)        │
│                                                             │
│  ┌──────────┐    ┌──────────────┐    ┌─────────────────┐   │
│  │ Users    │───►│ API Mgmt     │───►│ App Service     │   │
│  │ (1000    │    │ (rate limit, │    │ (orchestrator,  │   │
│  │  conc.)  │    │  auth, WAF)  │    │  3+ instances)  │   │
│  └──────────┘    └──────────────┘    └────────┬────────┘   │
│                                           │         │       │
│                                    embed  │         │ chat  │
│                                           ▼         ▼       │
│     ┌────────────────┐        ┌───────────────────┐        │
│     │ Azure AI Search │◄───── │ Azure OpenAI      │        │
│     │ (vector index,  │       │ (West Europe,     │        │
│     │  10K docs       │       │  GPT-4, text-     │        │
│     │  embedded)      │       │  embedding-ada)   │        │
│     └────────────────┘        └───────────────────┘        │
│                                                             │
│     ┌────────────────┐        ┌───────────────────┐        │
│     │ Cosmos DB       │        │ Blob Storage      │        │
│     │ (chat history,  │        │ (10K source docs) │        │
│     │  session state) │        │                   │        │
│     └────────────────┘        └───────────────────┘        │
│                                                             │
│     ┌────────────────┐        ┌───────────────────┐        │
│     │ App Insights    │        │ Key Vault         │        │
│     │ (monitoring,   │        │ (API keys,        │        │
│     │  latency)      │        │  secrets)         │        │
│     └────────────────┘        └───────────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

**Key decisions:**

1. **GDPR compliance**: All resources in West Europe. Azure OpenAI in EU = data never leaves region. Private endpoints on all services.

2. **1000 concurrent users**: API Management for rate limiting + App Service (auto-scale 3–10 instances) + Azure OpenAI PTU (provisioned throughput) for predictable latency.

3. **10K document KB**: Pre-embed all docs with `text-embedding-ada-002`, store in Azure AI Search vector index. At query time: embed question → vector search top-5 docs → inject into GPT-4 prompt.

4. **Flow:** User question → embed → vector search top-5 docs → build prompt → GPT-4 → answer with citations → store conversation in Cosmos DB

5. **Cost estimate** (~10K queries/day): Azure OpenAI ~$500–1500/mo | AI Search ~$250/mo | App Service ~$200/mo | Cosmos DB ~$50/mo

</details>

---

## Quick Reference Card

```
╔═══════════════════════════════════════════════════════════════╗
║  AZURE ML QUICK REFERENCE                                    ║
╠═══════════════════════════════════════════════════════════════╣
║  Just need an API?  → Cognitive Services                     ║
║  Need GPT/LLM?      → Azure OpenAI                          ║
║  Need custom model?  → Azure ML                             ║
║  Big data (TBs)?     → Databricks                           ║
║  Save money: spot instances | auto-shutdown | scale to 0     ║
║  MLOps: GitHub Actions + Azure ML CLI v2 + Monitoring        ║
╚═══════════════════════════════════════════════════════════════╝
```

---

*Next: [18-ml-system-design-interview.md](18-ml-system-design-interview.md) — Putting it all together for system design interviews*
