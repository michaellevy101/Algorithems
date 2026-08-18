# 51. Design AI-Powered SOC (Security Operations Center)

---

## Problem Statement
Design an AI-driven Security Operations Center platform that ingests massive volumes of security alerts, event logs, and telemetry from across an enterprise — stored in Kusto (Azure Data Explorer) tables and streaming via Event Hubs — and uses LLMs and ML models to autonomously triage, investigate, correlate, and respond to security incidents. The system should reduce analyst workload by 80%+ through automated investigation, natural language querying ("show me all lateral movement in the last 24h"), and AI-generated incident reports.

## Clarifying Questions to Ask

1. **Data scale**: How many events/second ingested? (100K-1M+ EPS typical for large enterprise)
2. **Alert volume**: How many raw alerts/day? (tens of thousands, most are noise)
3. **Data sources**: Which log types? (endpoint, network, identity, cloud, email, firewall)
4. **Kusto schema**: Are the tables normalized (ASIM) or vendor-specific schemas?
5. **Response scope**: Recommend actions only, or auto-execute containment (isolate host, disable account)?
6. **Analyst interaction**: Chat-based investigation or automated playbooks or both?
7. **Multi-tenant**: Single enterprise SOC or MSSP serving multiple customers?
8. **Compliance**: Do we need full audit trail of every AI decision for regulatory review?
9. **Existing tooling**: Integrating into Microsoft Sentinel, or building from scratch?
10. **SLA**: What's the target MTTR (Mean Time to Respond) for critical incidents?

## Requirements & Sizing

### Functional Requirements

| Requirement | Description |
|-------------|-------------|
| **Ingest & normalize** | Ingest security events from all enterprise sources (endpoint, identity, network, cloud, email) and normalize to a common schema (ASIM) |
| **Real-time detection** | Detect known attack patterns within 60 seconds of event arrival using streaming KQL rules |
| **ML triage** | Score every alert for true-positive probability, deduplicate, and correlate into incidents — reducing 10K alerts/day to ~500 incidents |
| **Autonomous investigation** | For high-priority incidents, autonomously investigate by querying Kusto tables, building entity timelines, mapping to MITRE ATT&CK, and assessing blast radius |
| **Tiered response** | Recommend and execute containment actions with tiered approval (auto-execute reversible, require approval for high-impact) |
| **Natural-language querying** | Analysts ask questions in plain English; system translates to KQL, executes, and interprets results |
| **Incident reporting** | Auto-generate investigation summaries, executive reports, and audit-ready documentation |
| **Feedback loop** | Capture analyst verdicts (true/false positive) to continuously improve triage accuracy |

### Non-Functional Requirements

| Requirement | Target | Rationale |
|-------------|--------|-----------|
| **Ingestion throughput** | 500K–1M events/sec sustained, 1.5M burst | Large enterprise with endpoint + network telemetry |
| **Detection latency** | < 60s from event arrival to alert | Streaming KQL rules must fire near-real-time |
| **Triage latency** | < 30s per alert | ML scoring + correlation must not bottleneck the pipeline |
| **Investigation latency** | 30s–5 min per incident | LLM ReAct loop with up to 20 KQL queries |
| **Query response** | < 5s for typical analyst KQL queries | Kusto hot cache on SSD for 30-day data |
| **Availability** | 99.95% (< 4.4h downtime/year) | SOC cannot have extended blind spots |
| **Data retention** | Hot 30d / Warm 90d / Cold 365d | Compliance + investigation lookback requirements |
| **Audit completeness** | 100% of AI decisions logged immutably | Regulatory and forensic review requirements |

### Capacity Estimation

```
EVENTS:
├── Endpoint events:   300K EPS  (largest source — process, file, registry, network)
├── Network flow logs: 400K EPS  (firewall, NSG, proxy)
├── Identity events:    50K EPS  (sign-in, audit, conditional access)
├── Cloud audit:        10K EPS  (Azure, AWS, GCP activity)
├── Email events:       20K EPS  (Exchange Online, attachments, URLs)
├── Threat intel:        1K EPS  (IoC feed updates)
└── TOTAL:             ~780K EPS sustained → design for 1M EPS headroom

STORAGE (per day at 1M EPS, ~500 bytes avg event):
├── Raw ingestion:     ~43 TB/day
├── Hot (30 days):     ~50 TB  (compressed, indexed in Kusto)
├── Warm (90 days):    ~200 TB
├── Cold (365 days):   ~500 TB (blob-backed, cheapest tier)
└── Total addressable: ~750 TB

ALERTS & INCIDENTS:
├── Raw alerts/day:    ~10,000  (from detection rules + Defender)
├── After correlation: ~500 incidents/day
├── High-priority:     ~50/day  (auto-investigated)
├── Critical:          ~5/day   (paged to on-call analyst)
└── Active incidents:  ~200 open at any time (Cosmos DB)

AI COST (per day):
├── Triage ML inference (10K alerts × <5ms each):       negligible (CPU-bound)
├── Investigation LLM (50 investigations × ~10K tokens): ~$5/day
├── KQL Copilot (200 analyst queries × ~4K tokens):      ~$3/day
├── Report generation (50 reports × ~6K tokens):          ~$2/day
└── TOTAL AI TOKEN COST:                                  ~$10/day = ~$300/month
    (At GPT-4o pricing: $2.50/M input, $10/M output)
```

---

## Design Reasoning

> **Core philosophy**: Shift analyst effort from noise-filtering to high-value judgment calls — massive alert volumes are automatically triaged via ML, autonomously investigated by an LLM reasoning engine, but human analysts remain the final decision-maker for high-risk actions, with full audit trails ensuring transparency.

| Decision | Choice | Why | Alternative Considered |
|----------|--------|-----|----------------------|
| Event ingestion | Azure Event Hubs (Kafka-compatible, partitioned by source/severity) | Handles 500K-1M events/sec; partitioning enables parallel agent consumption; 7-day replay window | Direct DB writes — creates bottlenecks; self-managed Kafka — higher operational overhead |
| Log analytics store | Azure Data Explorer (Kusto) with hot/warm/cold tiering | Purpose-built for time-series security analytics; KQL is the standard security query language; sub-second queries at petabyte scale | Elasticsearch/Splunk — higher cost at scale; Kusto is optimized for security data patterns |
| Active incident state | Cosmos DB with TTL-based cleanup | Low-latency CRUD for active investigations; TTL auto-removes stale incidents; globally distributable | Kusto for everything — optimized for append-only analytics, not rapid transactional updates |
| Entity risk scoring | Redis Cache with Kusto batch baselines + Stream Analytics real-time updates | Sub-millisecond risk lookups during triage (<30s SLA); baselines computed offline to avoid runtime cost | On-the-fly Kusto computation — too slow for real-time triage decisions |
| Alert triage ML | XGBoost/LightGBM for severity scoring (<5ms inference) | Fast enough for real-time scoring; interpretable feature importance; reduces 10K alerts/day to ~500 incidents | LLM-based triage — API calls too slow and expensive for hundreds-per-second scoring |
| Investigation engine | GPT-4o with ReAct loop + KQL tool-calling (read-only, max 20 queries, 5-min timeout) | LLM reasons about attack chains and generates contextual KQL; guardrails prevent runaway costs | Pre-built playbook templates — can't adapt to novel attacks; pure autonomous — safety/cost concerns |
| Response actions | Tiered approval: auto-approve reversible (block IP), require approval for high-impact (isolate device) | Balances speed for critical containment with safety for high-risk actions; full audit trail | Fully autonomous — organizational risk; approval-for-everything — creates bottleneck |
| Query interface | KQL as universal language (analysts + LLM agents) | Standard for Microsoft security; LLM-generated queries are auditable; safety-checkable via AST parsing | GraphQL API — adds indirection, harder audit trails; forces analysts to learn new syntax |

---

## High-Level Design

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                       AI-POWERED SOC ARCHITECTURE                                         │
└──────────────────────────────────────────────────────────────────────────────────────────┘

                              DATA SOURCES (Enterprise-wide)
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                                                                                           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │ Endpoint │ │ Identity │ │ Network  │ │  Cloud   │ │  Email   │ │ Firewall │         │
│  │  (EDR)   │ │  (Entra  │ │ (NSG,    │ │ (Azure,  │ │ (EOP,    │ │  / WAF   │         │
│  │ Defender │ │   ID)    │ │  Flows)  │ │  AWS)    │ │  Gmail)  │ │          │         │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘         │
│       └─────────────┴────────────┴────────────┴────────────┴────────────┘                │
│                                         │                                                │
└─────────────────────────────────────────┼────────────────────────────────────────────────┘
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│   EVENT HUBS         │  │   EVENT HUBS         │  │   EVENT HUBS         │
│   (Real-time stream) │  │   (Real-time stream) │  │   (Real-time stream) │
│   Partitioned by     │  │   Partitioned by     │  │   Partitioned by     │
│   source type        │  │   tenant / region    │  │   severity           │
└──────────┬───────────┘  └──────────┬───────────┘  └──────────┬───────────┘
           └──────────────────────────┼──────────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                          INGESTION & ENRICHMENT PIPELINE                                  │
│                                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                │
│  │  Schema      │  │    Threat    │  │   Entity     │  │  Dedup &     │                │
│  │  Normalizer  │  │   Intel      │  │  Enrichment  │  │  Sampling    │                │
│  │  (ASIM)      │  │  Enrichment  │  │  (user,host, │  │              │                │
│  │              │  │  (IoC match) │  │   IP→context)│  │              │                │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘                │
│                                                                                           │
└────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                    ▼
┌──────────────────────┐  ┌───────────────────────┐  ┌──────────────────────┐
│  KUSTO / ADX CLUSTER │  │  STREAMING ANALYTICS  │  │   ALERT STORE        │
│  (Historical data)   │  │  (Real-time detect)   │  │   (Active alerts)    │
│                      │  │                       │  │                      │
│  SecurityEvent       │  │  Azure Stream          │  │  Cosmos DB           │
│  SigninLogs          │  │  Analytics / KQL       │  │  (hot alerts,        │
│  DeviceProcessEvents │  │  continuous queries    │  │   investigations)    │
│  NetworkFlows        │  │                       │  │                      │
│  CloudAuditLogs      │  │  Detect in < 60s      │  │                      │
│  ThreatIntel         │  │                       │  │                      │
│  ... 100+ tables     │  │                       │  │                      │
└──────────┬───────────┘  └───────────┬───────────┘  └──────────┬───────────┘
           └──────────────────────────┼──────────────────────────┘
                                      │
                                      ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                          AI BRAIN (Core Intelligence)                                     │
│                                                                                           │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐    │
│  │                      TRIAGE AGENT                                                 │    │
│  │  Alert arrives → ML severity scorer → Deduplicate → Correlate → Prioritize       │    │
│  │  Output: Ranked incident queue with auto-assigned severity                        │    │
│  └──────────────────────────────────────────────────────────────────────────────────┘    │
│                                      │                                                    │
│                                      ▼                                                    │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐    │
│  │                      INVESTIGATION AGENT                                          │    │
│  │  For each high-priority incident:                                                 │    │
│  │  ├── Generate & execute KQL queries against Kusto tables                          │    │
│  │  ├── Build entity timeline (user, device, IP, process)                            │    │
│  │  ├── Identify attack chain (MITRE ATT&CK mapping)                                │    │
│  │  ├── Assess blast radius (what else is affected?)                                 │    │
│  │  └── Produce investigation summary + evidence                                     │    │
│  └──────────────────────────────────────────────────────────────────────────────────┘    │
│                                      │                                                    │
│                                      ▼                                                    │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐    │
│  │                      RESPONSE AGENT                                               │    │
│  │  Based on investigation findings:                                                 │    │
│  │  ├── Recommend containment actions (isolate host, disable user, block IP)         │    │
│  │  ├── Auto-execute approved playbooks (with human-in-the-loop)                     │    │
│  │  ├── Generate incident report (natural language summary)                          │    │
│  │  └── Update SOAR/ticketing (ServiceNow, Jira)                                    │    │
│  └──────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                           │
└────────────────────────────────────────┬─────────────────────────────────────────────────┘
                                         │
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                          ANALYST INTERFACE                                                 │
│                                                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                │
│  │  Chat-based  │  │  Incident    │  │  KQL         │  │  Executive   │                │
│  │  Copilot     │  │  Dashboard   │  │  Copilot     │  │  Reports     │                │
│  │  ("explain   │  │  (priority   │  │  ("query     │  │  (auto-gen   │                │
│  │   this alert │  │   queue,     │  │   anomalous  │  │   daily      │                │
│  │   to me")    │  │   timelines) │  │   logins")   │  │   summaries) │                │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘                │
│                                                                                           │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## End-to-End Alert Lifecycle

A single security event traverses the pipeline as follows. Timing annotations show worst-case latency at each stage:

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                     END-TO-END: ONE ALERT THROUGH THE SYSTEM                              │
└──────────────────────────────────────────────────────────────────────────────────────────┘

T+0s        Defender EDR detects suspicious PowerShell on DESKTOP-JD01
            │
            ▼
T+0-2s      EVENT HUB (eh-endpoint-events)
            │  Event lands in partition (keyed by DeviceId)
            │  Three consumer groups pick it up in parallel:
            │  ├── "kusto-ingestion"      → stored in Kusto (DeviceProcessEvents)
            │  ├── "real-time-detection"  → Stream Analytics evaluates detection rules
            │  └── "enrichment"           → Enrichment pipeline starts
            ▼
T+2-5s      ENRICHMENT PIPELINE
            │  ├── Step 1: ASIM normalize (vendor-specific → common schema)
            │  ├── Step 2: Threat intel lookup (hash → known malware? IP → known C2?)
            │  ├── Step 3: Entity enrichment (user → role, department, risk score;
            │  │           device → asset criticality, OS, last patch)
            │  └── Step 4: Dedup check (same alert within 15 min window?)
            │
            │  Output: Enriched alert → eh-alerts-enriched
            ▼
T+5-10s     STREAM ANALYTICS (parallel path)
            │  Continuous KQL rule fires:
            │  "DeviceProcessEvents | where FileName == 'powershell.exe'
            │   | where ProcessCommandLine has '-encodedCommand'"
            │  → Generates SecurityAlert record in Kusto + pushes to alerts hub
            ▼
T+5-30s     TRIAGE AGENT
            │  ├── Step 1: ML severity scoring (XGBoost, <5ms)
            │  │           Features: source reputation, entity risk, TI match,
            │  │           time-of-day, historical FP rate, asset criticality
            │  │           Output: true_positive_probability = 0.92
            │  │
            │  ├── Step 2: Correlation
            │  │           Check: are there other alerts for this user/device
            │  │           in the last 15 min? → YES: 3 related alerts found
            │  │           Group into single incident INC-2026-4521
            │  │
            │  └── Step 3: Priority scoring
            │              Priority = 0.92 × 8 (DC access) × 1.87 (entity risk)
            │              = 13.8 → CRITICAL
            │
            │  Output: Incident written to Cosmos DB (state: NEW)
            │  Auto-escalation: Critical → trigger Investigation Agent + page analyst
            ▼
T+30s-5m    INVESTIGATION AGENT (ReAct loop, max 20 KQL queries)
            │  ├── Iteration 1: Examine the PowerShell command
            │  │                 KQL → DeviceProcessEvents → found encoded mimikatz
            │  ├── Iteration 2: Check threat intel
            │  │                 KQL → ThreatIntelIndicators → IP linked to APT29
            │  ├── Iteration 3: Trace lateral movement
            │  │                 KQL → DeviceNetworkEvents + SigninLogs → 3 hosts
            │  ├── Iteration 4: Build attack timeline + MITRE mapping
            │  │                 T1566→T1059.001→T1003→T1021→T1078
            │  └── Iteration 5: Assess blast radius
            │                   3 compromised hosts including DC01
            │
            │  Output: Investigation summary + recommended actions
            │  Incident state: NEW → INVESTIGATING → INVESTIGATED
            ▼
T+5m-6m     RESPONSE AGENT
            │  ├── Auto-execute (reversible):
            │  │   ├── Block external IP 185.x.x.x at firewall         ✅ done
            │  │   ├── Quarantine source email                          ✅ done
            │  │   └── Add mimikatz hash to blocklist                   ✅ done
            │  │
            │  └── Require approval (high-impact):
            │      ├── Isolate DESKTOP-JD01 from network                ⏳ pending
            │      ├── Disable jdoe@contoso.com                         ⏳ pending
            │      └── Force credential reset                           ⏳ pending
            │
            │  Incident state: INVESTIGATED → AWAITING_APPROVAL
            │  Notify analyst via Teams + PagerDuty
            ▼
T+6m+       ANALYST REVIEW
            │  Analyst sees: investigation summary, timeline, evidence,
            │  recommended high-impact actions with AI confidence score
            │
            │  Analyst clicks "Approve All" → Response Agent executes
            │  Incident state: AWAITING_APPROVAL → CONTAINED → RESOLVED
            │
            │  Analyst marks: "True Positive" → feedback stored for
            │  triage model retraining
            ▼
            TOTAL: ~6 minutes from event → containment
            (vs 4-8 HOURS for a human-only SOC)
```

---

## Incident State Machine

Every incident transitions through well-defined states. Transitions are triggered by system events or analyst actions:

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                         INCIDENT STATE MACHINE                                            │
│                                                                                           │
│                                                                                           │
│   ┌───────┐   Triage agent      ┌──────────┐   Investigation    ┌───────────────┐       │
│   │  NEW  │──────creates────────►│ TRIAGED  │──────agent─────────►│ INVESTIGATING │       │
│   └───────┘    incident          └──────────┘    picks up         └──────┬────────┘       │
│       │                               │                                  │                 │
│       │ Analyst suppresses            │ Analyst closes                   │ Agent           │
│       │ (false positive)              │ (known benign)                   │ completes       │
│       ▼                               ▼                                  ▼                 │
│   ┌──────────┐                  ┌──────────┐                    ┌───────────────┐         │
│   │SUPPRESSED│                  │  CLOSED  │◄───── analyst ────│  INVESTIGATED │         │
│   └──────────┘                  │(resolved)│      closes        └──────┬────────┘         │
│                                 └──────────┘                           │                   │
│                                      ▲                                 │                   │
│                                      │                                 │ Has high-impact   │
│                                      │ All actions                     │ actions?          │
│                                      │ confirmed                       │                   │
│                                      │                          ┌──────┴────────┐          │
│                                      │                     NO   │               │ YES      │
│                                 ┌────┴─────┐              │    │               │          │
│                                 │CONTAINED │◄─────────────┘    │               ▼          │
│                                 │          │                   │      ┌────────────────┐  │
│                                 └────┬─────┘                   │      │   AWAITING     │  │
│                                      │                         │      │   APPROVAL     │  │
│                                      │ Analyst confirms        │      └───────┬────────┘  │
│                                      │ resolution              │              │            │
│                                      ▼                         │   Analyst     │ Analyst   │
│                                 ┌──────────┐                   │   approves    │ rejects   │
│                                 │ RESOLVED │                   │              │            │
│                                 └──────────┘                   │   ┌──────────┘            │
│                                                                │   │                       │
│                                                                │   ▼                       │
│                                                                │  CONTAINED ──► RESOLVED   │
│                                                                │                           │
│                                                                └──► INVESTIGATING          │
│                                                                    (re-investigate          │
│                                                                     with analyst notes)     │
│                                                                                           │
│   STATE METADATA (stored in Cosmos DB):                                                   │
│   ├── state: enum (NEW, TRIAGED, INVESTIGATING, INVESTIGATED,                             │
│   │          AWAITING_APPROVAL, CONTAINED, RESOLVED, CLOSED, SUPPRESSED)                  │
│   ├── state_changed_at: UTC timestamp                                                     │
│   ├── state_changed_by: "system:triage-agent" | "system:investigation-agent" | "user:jsmith" │
│   ├── state_history: array of {state, timestamp, actor, reason}                           │
│   └── sla_breach_at: computed deadline per state (triage < 30s, investigation < 5m)       │
│                                                                                           │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Design

### Alert & Incident APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/v1/alerts/ingest` | Ingest a batch of raw alerts |
| `GET` | `/v1/incidents?severity=critical&status=open&since=2026-03-01T00:00:00Z&limit=50&cursor=abc` | List incidents (filter by severity, status, time; cursor-based pagination) |
| `GET` | `/v1/incidents/{id}` | Get incident details + AI investigation summary |
| `POST` | `/v1/incidents/{id}/investigate` | Trigger AI investigation on an incident |
| `POST` | `/v1/incidents/{id}/respond` | Execute a response action (with approval) |
| `PATCH` | `/v1/incidents/{id}` | Update incident status (analyst override) |
| `POST` | `/v1/incidents/{id}/feedback` | Submit analyst verdict (true/false positive) for ML retraining |

### AI Chat / Copilot APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/v1/copilot/chat` | Natural language security query (streaming SSE) |
| `POST` | `/v1/copilot/kql` | Generate KQL from natural language |
| `POST` | `/v1/copilot/explain` | Explain an alert, script, or command in plain English |
| `POST` | `/v1/copilot/summarize` | Summarize an incident for executive report |

### Query & Data APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/v1/kusto/query` | Execute KQL query against ADX cluster |
| `GET` | `/v1/entities/{type}/{id}/timeline` | Get entity timeline (user, device, IP) |
| `GET` | `/v1/entities/{type}/{id}/risk` | Get entity risk score and contributing factors |
| `GET` | `/v1/threat-intel/ioc/{value}` | Look up indicator of compromise |

### Playbook / Response APIs
| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/v1/playbooks` | List available response playbooks |
| `POST` | `/v1/playbooks/{id}/execute` | Execute playbook (requires approval for destructive actions) |
| `POST` | `/v1/actions/isolate-host` | Isolate a device from the network |
| `POST` | `/v1/actions/disable-user` | Disable an Entra ID account |
| `POST` | `/v1/actions/block-ip` | Add IP to firewall blocklist |

### Natural Language Chat Request
```json
POST /v1/copilot/chat
{
  "session_id": "sess_soc_001",
  "message": "Show me all lateral movement activity for user jdoe@contoso.com in the last 48 hours",
  "context": {
    "current_incident": "INC-2026-4521",
    "analyst_role": "Tier2",
    "tenant": "contoso"
  },
  "stream": true
}
```

### Chat Response (Streamed)
```json
{
  "session_id": "sess_soc_001",
  "steps": [
    {
      "type": "reasoning",
      "content": "I'll check RDP, SMB, PsExec, and WMI activity for this user across multiple hosts..."
    },
    {
      "type": "kql_query",
      "content": "DeviceNetworkEvents\n| where Timestamp > ago(48h)\n| where InitiatingProcessAccountUpn == 'jdoe@contoso.com'\n| where RemotePort in (3389, 445, 135, 5985)\n| summarize ConnectionCount=count(), DistinctHosts=dcount(RemoteIP) by RemoteIP, RemotePort\n| order by ConnectionCount desc",
      "query_id": "qry_abc123"
    },
    {
      "type": "kql_results",
      "content": "| RemoteIP | RemotePort | ConnectionCount | DistinctHosts |\n|----------|-----------|-----------------|---------------|\n| 10.0.3.45 | 3389 | 47 | 1 |\n| 10.0.3.112 | 445 | 23 | 1 |\n| 10.0.5.200 | 5985 | 8 | 1 |"
    },
    {
      "type": "analysis",
      "content": "🔴 **Suspicious lateral movement detected.** User jdoe connected to 3 internal hosts via RDP (3389), SMB (445), and WinRM (5985) — this is unusual for this user's baseline. The WinRM connections to 10.0.5.200 are particularly concerning as this is a domain controller. MITRE ATT&CK: T1021 (Remote Services)."
    },
    {
      "type": "recommendation",
      "content": "**Recommended actions:**\n1. Isolate device DESKTOP-JD01 (source of connections)\n2. Reset credentials for jdoe@contoso.com\n3. Check 10.0.5.200 (DC) for signs of compromise\n4. Review authentication logs for jdoe in the past 7 days"
    }
  ],
  "audit_trail_id": "audit_xyz789"
}
```

### Error Response Example
```json
{
  "error": {
    "code": "INVESTIGATION_TIMEOUT",
    "message": "Investigation exceeded 5-minute timeout after 20 KQL queries",
    "incident_id": "INC-2026-4521",
    "details": {
      "queries_executed": 20,
      "partial_findings": "Suspicious lateral movement confirmed. Full blast radius assessment incomplete.",
      "recommendation": "Review partial findings and trigger manual investigation for remaining scope."
    },
    "retry_after_seconds": 60,
    "audit_trail_id": "audit_xyz790"
  }
}
```

Other error codes: `RATE_LIMITED` (429), `KUSTO_QUERY_FAILED` (bad KQL syntax or timeout), `APPROVAL_REQUIRED` (action needs human approval), `ENTITY_NOT_FOUND` (unknown incident/entity ID), `INSUFFICIENT_PERMISSIONS` (analyst role cannot execute this action).

### Kusto Table Schema (ASIM-Normalized)
```
┌──────────────────────────────────────────────────────────────────┐
│                    KUSTO / ADX CLUSTER                            │
│                                                                   │
│  HOT DATA (< 30 days): SSD, instant queries                     │
│  WARM DATA (30-90 days): HDD, slightly slower                   │
│  COLD DATA (90-365 days): Blob-backed, cheapest                 │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  CORE SECURITY TABLES                                      │  │
│  │                                                              │  │
│  │  SecurityAlert          — Detection alerts from all sources │  │
│  │  ├── AlertId, Severity, Title, Description                  │  │
│  │  ├── Tactics, Techniques (MITRE ATT&CK)                    │  │
│  │  ├── Entities (users, hosts, IPs, files involved)           │  │
│  │  ├── SourceSystem (Defender, Sentinel, custom)              │  │
│  │  └── TimeGenerated, ProviderName                            │  │
│  │                                                              │  │
│  │  SigninLogs             — Entra ID authentications           │  │
│  │  ├── UserPrincipalName, IPAddress, Location                 │  │
│  │  ├── ResultType (success/failure/MFA challenge)             │  │
│  │  ├── ConditionalAccessStatus, RiskLevel                     │  │
│  │  └── AppDisplayName, DeviceDetail                           │  │
│  │                                                              │  │
│  │  DeviceProcessEvents   — Process execution on endpoints     │  │
│  │  ├── DeviceName, FileName, ProcessCommandLine               │  │
│  │  ├── InitiatingProcessAccountUpn                            │  │
│  │  ├── SHA256, ParentProcessName                              │  │
│  │  └── FolderPath, ProcessCreationTime                        │  │
│  │                                                              │  │
│  │  DeviceNetworkEvents   — Network connections from endpoints │  │
│  │  ├── DeviceName, LocalIP, RemoteIP, RemotePort              │  │
│  │  ├── Protocol, ActionType (ConnectionSuccess/Blocked)       │  │
│  │  └── InitiatingProcessFileName                              │  │
│  │                                                              │  │
│  │  DeviceFileEvents      — File creation, modification        │  │
│  │  DeviceRegistryEvents  — Registry changes                   │  │
│  │  EmailEvents           — Email metadata (sender, subject)   │  │
│  │  CloudAppEvents        — SaaS app activity (SharePoint,     │  │
│  │                           OneDrive, Teams)                  │  │
│  │  AuditLogs             — Azure/Entra admin activities       │  │
│  │  ThreatIntelIndicators — Known IoCs (hashes, IPs, domains)  │  │
│  │  UserBehaviorAnalytics — UEBA risk scores and anomalies     │  │
│  │                                                              │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                   │
│  SCALE:                                                          │
│  ├── 50-200 TB of hot data                                      │
│  ├── 500K-1M events/second ingestion                            │
│  ├── 100+ tables across all data sources                        │
│  └── Sub-second query response for typical analyst queries      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Event Hubs Streaming Architecture
```
┌──────────────────────────────────────────────────────────────────┐
│                    EVENT HUBS TOPOLOGY                            │
│                                                                   │
│  INGESTION HUBS (partitioned by source):                         │
│  ├── eh-endpoint-events     (32 partitions, 100K EPS)           │
│  ├── eh-identity-events     (16 partitions, 50K EPS)            │
│  ├── eh-network-events      (64 partitions, 500K EPS)           │
│  ├── eh-cloud-audit         (8 partitions, 10K EPS)             │
│  ├── eh-email-events        (8 partitions, 20K EPS)             │
│  └── eh-threat-intel        (4 partitions, 1K EPS)              │
│                                                                   │
│  CONSUMERS:                                                      │
│  ├── Consumer Group: "kusto-ingestion"                           │
│  │   └── ADX streaming ingestion (all events → Kusto tables)    │
│  ├── Consumer Group: "real-time-detection"                       │
│  │   └── Stream Analytics (continuous KQL detection rules)       │
│  ├── Consumer Group: "ai-triage"                                 │
│  │   └── AI Triage Agent (ML scoring + alert correlation)        │
│  └── Consumer Group: "enrichment"                                │
│      └── Enrichment pipeline (TI lookup, entity resolution)      │
│                                                                   │
│  ALERT OUTPUT HUB:                                               │
│  └── eh-alerts-enriched (triage agent writes scored alerts)      │
│      └── Consumed by: Investigation Agent, Dashboard, SOAR      │
│                                                                   │
│  RETENTION:                                                      │
│  ├── Event Hubs: 7 days (replay window for reprocessing)        │
│  └── Kusto: Hot 30d / Warm 90d / Cold 365d                      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Data Privacy & Compliance

```
┌──────────────────────────────────────────────────────────────────┐
│                    DATA PRIVACY CONSIDERATIONS                    │
│                                                                   │
│  PII IN SECURITY LOGS:                                           │
│  ├── Usernames, email addresses (SigninLogs, DeviceProcessEvents)│
│  ├── IP addresses (network flows, sign-in logs)                  │
│  ├── Device names and hardware IDs                               │
│  ├── File paths (may contain personal folder names)              │
│  └── Email subjects and sender/recipient (EmailEvents)           │
│                                                                   │
│  CONTROLS:                                                       │
│  ├── RBAC: Analysts see only their assigned tenants/regions      │
│  ├── Column-level masking: PII columns hashed for Tier1 analysts │
│  │   Tier2+ see full data (need-to-know for investigation)       │
│  ├── Row-level security: Kusto policies enforce TenantId filter  │
│  ├── Data residency: Kusto cluster per geo region                │
│  │   (EU data stays in EU, US data stays in US)                  │
│  ├── Retention enforcement: Kusto retention policies auto-purge  │
│  │   data beyond configured retention windows                    │
│  └── Right to erasure: For GDPR Article 17 requests, purge      │
│      specific user data via Kusto .purge command (async, audited)│
│                                                                   │
│  AUDIT REQUIREMENTS:                                              │
│  ├── Every AI decision logged to immutable blob storage          │
│  ├── Every KQL query (human or AI) logged with actor identity    │
│  ├── Every response action logged with approval chain            │
│  ├── Audit logs retained for 7 years (regulatory minimum)        │
│  └── Tamper-proof: Azure Immutable Blob with legal hold          │
│                                                                   │
│  COMPLIANCE FRAMEWORKS:                                           │
│  ├── SOC 2 Type II: Audit trail + access controls                │
│  ├── GDPR: Data residency, right to erasure, DPO notification   │
│  ├── HIPAA: BAA with Azure, encrypted at rest + in transit       │
│  └── FedRAMP: Azure Gov regions for government customers         │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## AI Brain: Three-Agent Architecture

### Agent 1: Triage Agent (Real-Time, < 30 seconds)
```
┌──────────────────────────────────────────────────────────────────┐
│                    TRIAGE AGENT                                   │
│                                                                   │
│  INPUT: Raw alert from Event Hub                                 │
│                                                                   │
│  STEP 1: ML SEVERITY SCORING                                    │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  Features:                                            │        │
│  │  ├── Alert source reputation (Defender = high signal) │        │
│  │  ├── Entity risk score (user/device UEBA score)       │        │
│  │  ├── Threat intel match (known IoC? = critical)       │        │
│  │  ├── Time of day (3 AM login = more suspicious)       │        │
│  │  ├── Historical false-positive rate for this rule     │        │
│  │  └── Asset criticality (domain controller > laptop)   │        │
│  │                                                        │        │
│  │  Output: true_positive_probability: 0.0 → 1.0        │        │
│  │  Model: XGBoost (fast, <5ms inference) or fine-tuned  │        │
│  │         classifier, retrained weekly on analyst        │        │
│  │         feedback (true positive / false positive)      │        │
│  └──────────────────────────────────────────────────────┘        │
│       │                                                           │
│       ▼                                                           │
│  STEP 2: ALERT CORRELATION & DEDUPLICATION                       │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  Group alerts by:                                     │        │
│  │  ├── Same entity (user, device, IP) within time       │        │
│  │  │   window (15 min)                                  │        │
│  │  ├── Same MITRE technique across entities             │        │
│  │  │   (multiple hosts → campaign)                      │        │
│  │  └── Kill chain progression                           │        │
│  │      (recon → initial access → lateral movement)      │        │
│  │                                                        │        │
│  │  10,000 raw alerts/day → ~500 correlated incidents    │        │
│  └──────────────────────────────────────────────────────┘        │
│       │                                                           │
│       ▼                                                           │
│  STEP 3: PRIORITY QUEUE                                          │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  Priority = severity_score × asset_criticality        │        │
│  │             × (1 + entity_risk) × recency_boost       │        │
│  │                                                        │        │
│  │  Queue: [Critical: 5] [High: 23] [Medium: 142]       │        │
│  │                                                        │        │
│  │  Auto-escalation: Critical incidents → immediate       │        │
│  │  Investigation Agent + page on-call analyst            │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                   │
│  OUTPUT: Correlated incident with severity, entities,            │
│          MITRE mapping, and priority score → Cosmos DB           │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### ML Operations: Triage Model Lifecycle

```
┌──────────────────────────────────────────────────────────────────┐
│                    TRIAGE MODEL LIFECYCLE                          │
│                                                                   │
│  TRAINING DATA:                                                  │
│  ├── Source: Analyst feedback on past incidents                   │
│  │   (true positive / false positive labels)                     │
│  ├── Features extracted from Kusto:                              │
│  │   ├── Alert source + detection rule ID                        │
│  │   ├── Entity risk score at time of alert                      │
│  │   ├── Threat intel match (binary + confidence)                │
│  │   ├── Time-of-day, day-of-week (cyclical encoding)            │
│  │   ├── Historical FP rate for this rule (30-day window)        │
│  │   ├── Asset criticality (1-10 scale)                          │
│  │   ├── Alert velocity (alerts for same entity in last 1h)      │
│  │   └── User behavioral anomaly score (UEBA)                   │
│  ├── Label balance: ~60% FP, ~40% TP (typical for enterprise)   │
│  └── Dataset: ~50K labeled incidents (rolling 6-month window)    │
│                                                                   │
│  RETRAINING CADENCE:                                             │
│  ├── Weekly scheduled retraining (Sunday 2 AM UTC)               │
│  ├── Emergency retrain if drift detected (see below)             │
│  └── Kusto query generates training dataset → export to Blob     │
│                                                                   │
│  MODEL VERSIONING & PROMOTION:                                   │
│  ┌───────────────────────────────────────────────────────┐       │
│  │  Train new model (v2)                                  │       │
│  │       │                                                │       │
│  │       ▼                                                │       │
│  │  Offline validation (holdout set):                     │       │
│  │  ├── AUC-ROC > 0.90? (vs current model v1)            │       │
│  │  ├── Precision at 90% recall > 0.70?                   │       │
│  │  └── False negative rate on critical alerts < 5%?      │       │
│  │       │                                                │       │
│  │       ▼ (pass)                                         │       │
│  │  Shadow mode (24h): Run v2 in parallel with v1         │       │
│  │  ├── Log v2 predictions but don't act on them          │       │
│  │  ├── Compare: does v2 catch incidents v1 missed?       │       │
│  │  └── Compare: does v2 have lower FP rate?              │       │
│  │       │                                                │       │
│  │       ▼ (no regression)                                │       │
│  │  Canary rollout: Route 10% of alerts to v2             │       │
│  │       │                                                │       │
│  │       ▼ (7 days, no issues)                            │       │
│  │  Full promotion: v2 becomes primary, v1 becomes        │       │
│  │  fallback (instant rollback if v2 degrades)            │       │
│  └───────────────────────────────────────────────────────┘       │
│                                                                   │
│  DRIFT DETECTION:                                                │
│  ├── Monitor: prediction confidence distribution (daily)         │
│  │   If median confidence drops >10% → alert ML team             │
│  ├── Monitor: FP rate reported by analysts (weekly)              │
│  │   If FP rate increases >15% → trigger emergency retrain       │
│  ├── Monitor: feature distribution shift (PSI > 0.2)             │
│  │   New attack types change feature patterns over time          │
│  └── Root cause: new detection rules, changed TI feeds,          │
│      seasonal patterns, new attack campaigns                     │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Agent 2: Investigation Agent (Deep Analysis, 30s-5min)
```
┌──────────────────────────────────────────────────────────────────┐
│                    INVESTIGATION AGENT                            │
│                                                                   │
│  INPUT: High-priority incident from Triage Agent                 │
│                                                                   │
│  AUTONOMOUS INVESTIGATION LOOP (ReAct pattern):                  │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ITERATION 1: Understand the alert                         │  │
│  │  ├── THINK: "Suspicious PowerShell on DESKTOP-JD01.        │  │
│  │  │   I need to check what commands were run and by whom."   │  │
│  │  ├── ACT: Generate & execute KQL:                          │  │
│  │  │   DeviceProcessEvents                                    │  │
│  │  │   | where DeviceName == "DESKTOP-JD01"                   │  │
│  │  │   | where Timestamp > ago(1h)                            │  │
│  │  │   | where FileName == "powershell.exe"                   │  │
│  │  │   | project Timestamp, ProcessCommandLine, AccountName   │  │
│  │  └── OBSERVE: Found encoded PowerShell downloading          │  │
│  │      mimikatz from external IP 185.x.x.x                   │  │
│  └────────────────────────────────────────────────────────────┘  │
│       │                                                           │
│       ▼                                                           │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ITERATION 2: Check threat intel                           │  │
│  │  ├── THINK: "Need to check if 185.x.x.x is known bad"     │  │
│  │  ├── ACT: Query ThreatIntelIndicators table +              │  │
│  │  │   external feeds (VirusTotal, AbuseIPDB)                │  │
│  │  └── OBSERVE: IP linked to APT29, flagged in 3 feeds       │  │
│  └────────────────────────────────────────────────────────────┘  │
│       │                                                           │
│       ▼                                                           │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ITERATION 3: Assess lateral movement                      │  │
│  │  ├── THINK: "Mimikatz = credential theft. Check if          │  │
│  │  │   stolen creds were used to access other hosts"          │  │
│  │  ├── ACT: Query SigninLogs + DeviceNetworkEvents for        │  │
│  │  │   jdoe@contoso.com → other hosts via RDP/SMB/WinRM     │  │
│  │  └── OBSERVE: jdoe connected to 3 additional servers       │  │
│  │      including domain controller DC01                       │  │
│  └────────────────────────────────────────────────────────────┘  │
│       │                                                           │
│       ▼                                                           │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  ITERATION 4: Build attack timeline                        │  │
│  │  ├── THINK: "Map the full kill chain"                      │  │
│  │  ├── ACT: Correlate all findings into timeline              │  │
│  │  └── OBSERVE: Full chain:                                   │  │
│  │      14:32 - Phishing email received                        │  │
│  │      14:35 - Malicious link clicked                         │  │
│  │      14:36 - PowerShell downloads mimikatz                  │  │
│  │      14:38 - Credential dumping (LSASS access)             │  │
│  │      14:42 - Lateral movement to 3 hosts                   │  │
│  │      14:45 - Access to domain controller DC01               │  │
│  │      MITRE: T1566→T1059.001→T1003→T1021→T1078              │  │
│  └────────────────────────────────────────────────────────────┘  │
│       │                                                           │
│       ▼                                                           │
│  OUTPUT:                                                         │
│  ├── Investigation summary (natural language)                    │
│  ├── Attack timeline with evidence links                         │
│  ├── MITRE ATT&CK mapping                                       │
│  ├── Blast radius (affected entities)                            │
│  ├── Confidence score (how sure is the AI?)                      │
│  ├── Recommended response actions                                │
│  └── All KQL queries executed (for analyst review)               │
│                                                                   │
│  TOOLS AVAILABLE TO AGENT:                                       │
│  ├── execute_kql(query, table) — run KQL against Kusto          │
│  ├── lookup_threat_intel(ioc) — check IoC databases             │
│  ├── get_entity_risk(entity) — UEBA risk score                  │
│  ├── get_asset_info(host) — asset criticality, owner, OS        │
│  ├── get_user_baseline(upn) — normal behavior profile           │
│  ├── search_similar_incidents(description) — past incidents     │
│  └── decode_payload(encoded_string) — decode base64/PS          │
│                                                                   │
│  GUARDRAILS:                                                     │
│  ├── Max 20 KQL queries per investigation (cost control)        │
│  ├── Max 5-minute wall clock per investigation                   │
│  ├── Read-only access to Kusto (cannot modify data)             │
│  ├── All queries logged to audit trail                           │
│  └── Escalate to human if confidence < 0.6                      │
│                                                                   │
│  HANDLING AMBIGUOUS / CONFLICTING EVIDENCE:                      │
│  ├── Conflicting signals (e.g., TI says malicious IP but the    │
│  │   user's baseline includes that IP):                          │
│  │   → Agent assigns a "conflict" tag and presents BOTH          │
│  │     findings to the analyst, explicitly noting the conflict   │
│  │   → Confidence score is reduced proportionally to conflicts   │
│  ├── Insufficient evidence (e.g., partial logs, gaps in data):   │
│  │   → Agent documents what data was unavailable and why         │
│  │   → Summary states "Investigation limited by: [data gaps]"    │
│  │   → Recommends analyst manually check missing data sources    │
│  ├── Multiple plausible attack narratives:                       │
│  │   → Agent presents ranked hypotheses with supporting          │
│  │     evidence for each, not a single conclusion                │
│  └── All ambiguity is surfaced, never hidden — the agent's job  │
│      is to organize evidence, not force a verdict                │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Agent 3: Response Agent (Action Execution)
```
┌──────────────────────────────────────────────────────────────────┐
│                    RESPONSE AGENT                                 │
│                                                                   │
│  INPUT: Investigation findings + recommended actions             │
│                                                                   │
│  APPROVAL MODES:                                                 │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  AUTO-APPROVE (low-risk, reversible actions):         │        │
│  │  ├── Block external IP at firewall                    │        │
│  │  ├── Quarantine email (move to junk)                  │        │
│  │  ├── Add IoC to threat intel blocklist                │        │
│  │  └── Create ServiceNow ticket                         │        │
│  │                                                        │        │
│  │  REQUIRE APPROVAL (high-impact actions):              │        │
│  │  ├── Isolate device from network                      │        │
│  │  ├── Disable user account                             │        │
│  │  ├── Force password reset                             │        │
│  │  ├── Wipe device                                      │        │
│  │  └── Revoke all active sessions                       │        │
│  │                                                        │        │
│  │  NEVER AUTO-EXECUTE:                                  │        │
│  │  ├── Delete data                                      │        │
│  │  ├── Modify firewall rules broadly                    │        │
│  │  └── Shut down production services                    │        │
│  └──────────────────────────────────────────────────────┘        │
│       │                                                           │
│       ▼                                                           │
│  EXECUTION:                                                      │
│  ├── Call Microsoft Graph API (disable user, revoke sessions)    │
│  ├── Call Defender API (isolate device, run AV scan)             │
│  ├── Call Firewall API (block IP/domain)                         │
│  ├── Call SOAR webhook (trigger existing playbook)               │
│  └── Update incident status in Cosmos DB                         │
│       │                                                           │
│       ▼                                                           │
│  POST-ACTION:                                                    │
│  ├── Verify action succeeded (poll API for confirmation)         │
│  ├── Log action to audit trail (who approved, when, why)         │
│  ├── Generate incident report (PDF/Markdown)                     │
│  └── Notify analyst via Teams/email/PagerDuty                    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## KQL Copilot: Natural Language → KQL

```
┌──────────────────────────────────────────────────────────────────┐
│                    KQL COPILOT                                    │
│                                                                   │
│  Analyst types: "Find all failed logins from outside the US      │
│                  for admin accounts in the last 7 days"          │
│       │                                                           │
│       ▼                                                           │
│  PROMPT ASSEMBLY:                                                │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  System: You are a KQL expert for Microsoft Sentinel. │        │
│  │  Available tables: [SigninLogs, SecurityAlert, ...]   │        │
│  │  Table schemas: [columns, types, sample values]       │        │
│  │  Constraints: Read-only, max 10K results              │        │
│  │                                                        │        │
│  │  User: {analyst's natural language query}              │        │
│  └──────────────────────────────────────────────────────┘        │
│       │                                                           │
│       ▼                                                           │
│  LLM GENERATES KQL:                                              │
│  ┌──────────────────────────────────────────────────────┐        │
│  │  SigninLogs                                            │        │
│  │  | where TimeGenerated > ago(7d)                       │        │
│  │  | where ResultType != 0  // failed logins             │        │
│  │  | where Location !in ("US", "United States")          │        │
│  │  | where Identity has "admin"                          │        │
│  │  | summarize FailedAttempts = count(),                 │        │
│  │             DistinctIPs = dcount(IPAddress)            │        │
│  │      by UserPrincipalName, Location                    │        │
│  │  | order by FailedAttempts desc                        │        │
│  └──────────────────────────────────────────────────────┘        │
│       │                                                           │
│       ▼                                                           │
│  SAFETY CHECKS (before execution):                               │
│  ├── Parse KQL AST → verify no mutations (.set, .drop)          │
│  ├── Check estimated cost (row scan estimate)                    │
│  ├── Add automatic | take 10000 if no limit present              │
│  └── Validate table/column names exist in schema                 │
│       │                                                           │
│       ▼                                                           │
│  EXECUTE → Return results + AI interpretation                    │
│                                                                   │
│  FEEDBACK LOOP:                                                  │
│  ├── Analyst edits query → corrections stored for fine-tuning   │
│  ├── "This query was helpful" → positive signal                 │
│  └── Library of proven queries → few-shot examples for LLM      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### Multi-Turn Investigation Example

The KQL Copilot maintains session context, enabling conversational investigation:

```
TURN 1 — Analyst: "Show me all failed logins for admin accounts this week"

  KQL Copilot generates:
  SigninLogs
  | where TimeGenerated > ago(7d)
  | where ResultType != 0
  | where Identity has "admin"
  | summarize FailedAttempts=count(), DistinctIPs=dcount(IPAddress)
    by UserPrincipalName, Location
  | order by FailedAttempts desc

  Results: 3 admin accounts with unusual failures from unusual locations.

TURN 2 — Analyst: "Drill into the top one — where are those IPs from?"

  Context carried: The Copilot remembers the previous query and results.
  It knows "top one" = the admin with the most failures.

  KQL Copilot generates:
  SigninLogs
  | where TimeGenerated > ago(7d)
  | where UserPrincipalName == "admin-jsmith@contoso.com"
  | where ResultType != 0
  | summarize Attempts=count() by IPAddress, Location
  | order by Attempts desc

  Results: 47 attempts from Russia, 12 from Brazil — both first-time geos.

TURN 3 — Analyst: "Did any of those succeed? And check if those IPs hit other accounts too"

  KQL Copilot generates TWO queries:

  Query A (successful logins from those IPs):
  SigninLogs
  | where TimeGenerated > ago(7d)
  | where IPAddress in ("185.x.x.1", "45.x.x.2")
  | where ResultType == 0
  | project TimeGenerated, UserPrincipalName, IPAddress, AppDisplayName

  Query B (other accounts targeted):
  SigninLogs
  | where TimeGenerated > ago(7d)
  | where IPAddress in ("185.x.x.1", "45.x.x.2")
  | summarize TargetedAccounts=dcount(UserPrincipalName),
              Attempts=count() by IPAddress
  | order by TargetedAccounts desc

  Analysis: "🔴 One successful login found for admin-jsmith from the Russian IP
  at 3:14 AM. The same IPs targeted 12 other accounts — this looks like a
  credential stuffing campaign. Recommend: reset admin-jsmith credentials,
  block both IPs, check for post-compromise activity."
```

---

## Entity Risk Scoring (UEBA)

```
┌──────────────────────────────────────────────────────────────────┐
│                    ENTITY RISK ENGINE                             │
│                                                                   │
│  For each entity (user, device, IP), maintain a risk score:      │
│                                                                   │
│  RISK SIGNALS:                                                   │
│  ├── Anomalous login (new geo, impossible travel)         +15   │
│  ├── First-time access to sensitive resource              +10   │
│  ├── Unusual process execution on device                  +20   │
│  ├── Matching threat intelligence indicator               +40   │
│  ├── Multiple failed authentications                      +10   │
│  ├── Off-hours activity (outside normal pattern)          +5    │
│  ├── Privilege escalation attempt                         +25   │
│  └── Data exfiltration pattern (large upload)             +30   │
│                                                                   │
│  RISK DECAY: Score decays 10% per day with no new signals        │
│                                                                   │
│  BASELINE MODEL (per-entity):                                    │
│  ├── Normal login hours: 8 AM - 7 PM EST                        │
│  ├── Normal geolocations: [US-East, US-West]                    │
│  ├── Normal applications: [Outlook, Teams, SharePoint]           │
│  ├── Normal network patterns: Internal only                      │
│  └── Computed from 30-day rolling window via Kusto batch job     │
│                                                                   │
│  STORAGE:                                                        │
│  ├── Risk scores → Redis (real-time lookups, <1ms)              │
│  ├── Baselines → Kusto table (UserBehaviorAnalytics)            │
│  └── Risk history → Kusto (for trend analysis)                  │
│                                                                   │
│  EXAMPLE:                                                        │
│  Entity: jdoe@contoso.com                                        │
│  Risk Score: 87/100 (Critical)                                   │
│  Contributing factors:                                           │
│  ├── Login from Russia (never before)            +15            │
│  ├── Accessed domain controller (first time)     +10            │
│  ├── PowerShell encoded command execution        +20            │
│  ├── Mimikatz hash detected in process           +40            │
│  └── Baseline violation: 3 AM activity           +5             │
│                                                                   │
│  COLD-START HANDLING (new employees, new devices):               │
│  ├── New entities start with risk = 0 (no baseline yet)          │
│  ├── First 14 days: "learning period" — all activity logged      │
│  │   but no anomaly scoring (insufficient baseline data)         │
│  ├── During learning period, apply PEER GROUP baseline:          │
│  │   New employee in Finance → use aggregate Finance baseline    │
│  │   (login hours, apps, geos of all Finance department users)   │
│  ├── After 14 days: switch to individual baseline                │
│  ├── For new devices: inherit risk profile from the assigned     │
│  │   user until device-specific patterns are established         │
│  └── Exception: Any threat intel match or critical alert during  │
│      learning period still triggers immediate investigation      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

---

## Key Components

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Event Ingestion | Azure Event Hubs (Kafka-compatible) | High-throughput streaming ingestion (1M+ EPS) |
| Data Store | Azure Data Explorer (Kusto) | Petabyte-scale log analytics, sub-second KQL queries |
| Stream Processing | Azure Stream Analytics / KQL streaming | Real-time detection rules (<60s latency) |
| Alert Store | Cosmos DB | Hot incident data, investigation state, agent threads |
| Risk Scores | Redis Cache | Real-time entity risk scores (<1ms lookup) |
| Triage Agent | XGBoost + correlation engine | ML severity scoring, alert dedup and grouping |
| Investigation Agent | LLM (GPT-4o) + KQL tools | Autonomous investigation via ReAct loop |
| Response Agent | LLM + Graph/Defender APIs | Recommend and execute containment actions |
| KQL Copilot | LLM + schema-aware prompt | Natural language → KQL translation |
| Threat Intelligence | Kusto table + external APIs | IoC matching (hashes, IPs, domains, URLs) |
| UEBA Engine | Kusto batch jobs + Redis | Per-entity behavioral baselines and anomaly detection |
| Audit Trail | Azure Immutable Blob Storage | Every AI decision, query, and action logged immutably |
| Notification | Azure Logic Apps / Event Grid | PagerDuty, Teams, email alerts for critical incidents |
| SOAR Integration | REST webhooks | Trigger existing playbooks in Sentinel/XSOAR/Splunk |
| Dashboard | React + Kusto Direct Query | Real-time incident queue, entity timelines, metrics |

---

## Scalability Considerations

### 1. Kusto Cluster Scaling
```
Challenge: 1M events/second, 100+ tables, analysts running ad-hoc queries simultaneously

Solutions:
├── Cluster auto-scale: Add/remove nodes based on CPU and ingestion lag
├── Hot/warm/cold tiering: SSD for recent data, HDD for older, Blob for archive
├── Materialized views: Pre-aggregate common queries (hourly alert counts, etc.)
├── Query limits: Per-user concurrent query cap, automatic timeout after 10 min
├── Follower databases: Read-only replicas for analyst queries (isolate from ingestion)
└── Partitioning: Partition large tables by TimeGenerated (most queries filter by time)

Sizing example (large enterprise):
├── 12-node cluster (D14v2: 16 cores, 112 GB RAM each)
├── Hot cache: 50 TB (30 days)
├── Warm storage: 200 TB (90 days)
├── Cold: 500 TB (365 days)
└── Ingestion: ~800K EPS sustained, 1.5M EPS burst
```

### 2. Event Hubs Scaling
```
Challenge: Bursty ingestion during incidents (2-5x normal volume)

Solutions:
├── Throughput Units: Auto-inflate from 20 → 100 TU during spikes
├── Partitioning: Partition by source type for parallel consumption
├── Consumer groups: Isolate Kusto ingestion from AI agents from dashboards
├── Capture: Auto-archive all events to Blob Storage (disaster recovery)
└── Kafka compatibility: Use Kafka consumer APIs for flexible processing

Partition strategy:
├── eh-endpoint-events: Partition by DeviceId (locality for per-device analysis)
├── eh-identity-events: Partition by TenantId (multi-tenant isolation)
└── eh-network-events: Partition by hash(SourceIP) (even distribution)
```

### 3. AI Agent Scaling
```
Challenge: Investigation agent uses expensive LLM calls + multiple KQL queries

Solutions:
├── Batch investigations: Group related incidents, investigate together
├── Caching: Cache KQL results for shared entities (same user, same host)
├── Model tiering: Use small model for triage, large model for investigation
├── Concurrency limit: Max 50 parallel investigations per cluster
├── Priority queue: Critical incidents get GPU priority over medium/low
└── Circuit breaker: If LLM API latency > 30s, queue and retry with backoff

Cost breakdown (per investigation):
├── KQL queries (5-10 queries): ~$0.01
├── LLM tokens (~8K input + 2K output): ~$0.10
├── Threat intel lookups: ~$0.001
└── Total: ~$0.12 per investigation
└── At 500 incidents/day: ~$60/day = ~$1,800/month
```

---

## Operational Metrics & SLOs

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                         SOC OPERATIONAL METRICS                                           │
│                                                                                           │
│  CORE TIMING METRICS (measure effectiveness of automation):                              │
│  ┌────────────────────────┬──────────────┬───────────────┬────────────────────┐          │
│  │ Metric                 │ SLO Target   │ Human-Only SOC│ AI SOC (Expected)  │          │
│  ├────────────────────────┼──────────────┼───────────────┼────────────────────┤          │
│  │ MTTD (Mean Time to     │ < 60s        │ 5-30 min      │ < 60s (streaming   │          │
│  │   Detect)              │              │               │   KQL rules)       │          │
│  │ MTTI (Mean Time to     │ < 5 min      │ 30-60 min     │ < 5 min (LLM agent)│          │
│  │   Investigate)         │              │               │                    │          │
│  │ MTTR (Mean Time to     │ < 15 min     │ 4-8 hours     │ < 15 min (auto +   │          │
│  │   Respond)             │ (critical)   │               │   approval)        │          │
│  │ Triage latency         │ < 30s        │ 10-30 min     │ < 30s (ML)         │          │
│  │ Alert-to-containment   │ < 10 min     │ 2-12 hours    │ < 10 min (for      │          │
│  │                        │ (critical)   │               │   auto-approve)    │          │
│  └────────────────────────┴──────────────┴───────────────┴────────────────────┘          │
│                                                                                           │
│  QUALITY METRICS (measure accuracy of AI decisions):                                     │
│  ┌────────────────────────┬──────────────┬────────────────────────────────────┐          │
│  │ Metric                 │ SLO Target   │ How Measured                       │          │
│  ├────────────────────────┼──────────────┼────────────────────────────────────┤          │
│  │ Triage precision       │ > 70%        │ % of AI-created incidents that     │          │
│  │                        │              │ analyst confirms as true positive  │          │
│  │ Triage recall          │ > 95%        │ % of real incidents caught by AI   │          │
│  │ (critical: > 99%)      │              │ (false negatives are dangerous)    │          │
│  │ Investigation quality  │ > 80%        │ Analyst rating of AI investigation │          │
│  │                        │              │ summary (1-5 scale, target > 4)    │          │
│  │ False positive rate    │ < 30%        │ % of incidents closed as FP        │          │
│  │ Response accuracy      │ > 95%        │ % of AI-recommended actions that   │          │
│  │                        │              │ analyst approves without changes   │          │
│  │ KQL accuracy           │ > 85%        │ % of generated KQL queries that    │          │
│  │                        │              │ execute successfully and return     │          │
│  │                        │              │ relevant results                   │          │
│  └────────────────────────┴──────────────┴────────────────────────────────────┘          │
│                                                                                           │
│  OPERATIONAL HEALTH METRICS:                                                             │
│  ├── Ingestion lag: Event Hub → Kusto (target < 5s, alert if > 30s)                     │
│  ├── Event Hub consumer lag: messages pending per consumer group                         │
│  ├── Kusto query P95 latency (analyst queries < 5s, agent queries < 10s)                │
│  ├── LLM API latency P95 (< 15s per call, alert if > 30s)                              │
│  ├── Investigation queue depth (pending incidents waiting for agent)                     │
│  ├── Agent error rate (failed investigations / total, target < 5%)                      │
│  └── Redis hit rate for entity risk lookups (target > 95%)                              │
│                                                                                           │
│  BUSINESS METRICS (reported weekly to CISO):                                             │
│  ├── Incidents handled autonomously vs escalated to human                                │
│  │   (target: 80% autonomous, 20% escalated)                                            │
│  ├── Analyst time saved (hours/week freed from triage and investigation)                 │
│  ├── Cost per incident (AI cost + analyst time, trending down over time)                 │
│  └── MITRE ATT&CK coverage (% of techniques with active detection rules)                │
│                                                                                           │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Failure Modes & Resilience

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│                         FAILURE MATRIX                                                    │
│                                                                                           │
│  COMPONENT          │ FAILURE MODE         │ DETECTION              │ MITIGATION          │
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  Kusto / ADX        │ Cluster unreachable  │ Health probe fails,    │ Follower DB in       │
│                     │                      │ query timeout > 30s    │ secondary region     │
│                     │                      │                        │ serves read queries; │
│                     │                      │                        │ events queue in      │
│                     │                      │                        │ Event Hubs (7d       │
│                     │                      │                        │ retention) and       │
│                     │                      │                        │ replay on recovery   │
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  Event Hubs         │ Partition loss /     │ Consumer lag spikes,   │ Auto-inflate TUs,    │
│                     │ throttling           │ Event Hubs metrics     │ Capture to Blob for  │
│                     │                      │ (throttled requests)   │ replay; cross-region │
│                     │                      │                        │ geo-DR namespace     │
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  Redis (risk cache) │ Cache eviction /     │ Cache miss rate > 20%, │ Fall back to Kusto   │
│                     │ node failure         │ Redis health check     │ direct query for     │
│                     │                      │                        │ risk scores (slower   │
│                     │                      │                        │ but functional);     │
│                     │                      │                        │ Redis Cluster with   │
│                     │                      │                        │ automatic failover   │
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  Cosmos DB          │ Region outage        │ Request latency > 1s,  │ Multi-region writes  │
│                     │                      │ HTTP 503s              │ with automatic       │
│                     │                      │                        │ failover; incident   │
│                     │                      │                        │ state is eventually  │
│                     │                      │                        │ consistent (OK for   │
│                     │                      │                        │ investigation state) │
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  LLM API (Azure     │ API outage / high    │ Latency > 30s,        │ Multi-region Azure   │
│  OpenAI)            │ latency / rate limit │ HTTP 429/503           │ OpenAI deployment;   │
│                     │                      │                        │ Triage continues     │
│                     │                      │                        │ (ML, no LLM);        │
│                     │                      │                        │ Investigation falls  │
│                     │                      │                        │ back to pre-built    │
│                     │                      │                        │ KQL playbook         │
│                     │                      │                        │ templates; queue     │
│                     │                      │                        │ incidents for LLM    │
│                     │                      │                        │ when recovered       │
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  Triage ML model    │ Model drift (FP rate │ Weekly FP rate check;  │ Auto-rollback to     │
│                     │ increases, misses    │ prediction confidence  │ previous model       │
│                     │ real threats)        │ distribution shift;    │ version; trigger     │
│                     │                      │ PSI > 0.2 on features │ emergency retraining │
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  Investigation      │ Agent stuck in loop  │ Wall clock > 5 min,   │ Hard timeout kills   │
│  Agent              │ (repetitive KQL      │ query count > 20,     │ investigation;       │
│                     │ queries, no progress)│ no new entities found  │ escalate to human    │
│                     │                      │ in last 3 iterations  │ with partial findings│
│  ───────────────────┼──────────────────────┼────────────────────────┼──────────────────── │
│  Threat intel feed  │ Feed stale / down    │ Last update > 6h,     │ Fall back to cached  │
│                     │                      │ zero new IoCs in 24h  │ IoC database; alert  │
│                     │                      │                        │ SecOps team; cross-  │
│                     │                      │                        │ check with secondary │
│                     │                      │                        │ TI feeds             │
│                                                                                           │
│  GRACEFUL DEGRADATION PRIORITY:                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐                     │
│  │  Level 1 (always available):                                    │                     │
│  │  ├── Event ingestion → Kusto (fundamental: no data = blind)    │                     │
│  │  ├── ML triage scoring (runs locally, no external dependency)  │                     │
│  │  └── Streaming detection rules (KQL, no LLM needed)            │                     │
│  │                                                                  │                     │
│  │  Level 2 (degraded without LLM):                                │                     │
│  │  ├── Investigation → falls back to playbook templates          │                     │
│  │  ├── KQL Copilot → analyst writes KQL manually                 │                     │
│  │  └── Report generation → template-based (less narrative)       │                     │
│  │                                                                  │                     │
│  │  Level 3 (degraded without Cosmos DB):                          │                     │
│  │  ├── Incident state → temporary write to Kusto table           │                     │
│  │  └── Investigation context → reconstructed from Kusto on       │                     │
│  │      Cosmos recovery                                            │                     │
│  └─────────────────────────────────────────────────────────────────┘                     │
│                                                                                           │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Trade-offs

| Decision | Option A | Option B | Our Choice |
|----------|----------|----------|------------|
| Detection engine | Custom ML models only | LLM-based detection | **Hybrid** — ML for real-time triage, LLM for deep investigation |
| Alert storage | All in Kusto | Hot in Cosmos DB, cold in Kusto | **Hot in Cosmos DB** — faster CRUD for active incidents, cheaper at alert-level scale |
| KQL generation | Pre-built query templates | LLM-generated dynamic KQL | **LLM with safety checks** — more flexible, but parse AST before execution |
| Response automation | Fully autonomous | Human-in-the-loop only | **Tiered** — auto-execute low-risk, require approval for high-impact |
| Entity risk model | Rule-based scoring | ML anomaly detection | **Hybrid** — rules for known patterns + ML for novel anomalies |
| Investigation depth | Fixed playbook | Agent explores freely | **Agent with guardrails** — ReAct loop but max 20 queries, 5 min timeout |
| Multi-tenant data | Shared Kusto cluster | Cluster per tenant | **Shared with row-level security** — cost-efficient, enforce tenant isolation via query policies |
| Audit trail | Standard logging | Immutable ledger | **Immutable storage** — regulatory requirement for security decisions |

---

## Technologies Deep Dive

### Data Platform
| Technology | Purpose | Why |
|-----------|---------|-----|
| **Azure Data Explorer (Kusto)** | Log analytics & detection | Purpose-built for time-series security data, KQL is the query language of security |
| **Azure Event Hubs** | Streaming ingestion | Kafka-compatible, millions of events/sec, consumer groups for fan-out |
| **Azure Stream Analytics** | Real-time detection | Continuous KQL queries over streaming data, <60s detection latency |
| **Cosmos DB** | Active incident store | Low-latency CRUD for investigation state, TTL for auto-cleanup |
| **Redis** | Entity risk cache | Sub-millisecond risk score lookups during triage |

### AI & ML
| Technology | Purpose | Why |
|-----------|---------|-----|
| **GPT-4o (Azure OpenAI)** | Investigation agent, KQL generation | Strong reasoning, tool-calling, KQL understanding |
| **XGBoost / LightGBM** | Triage severity scoring | Fast inference (<5ms), interpretable feature importance |
| **Sentence Transformers** | Alert similarity / dedup | Embed alert descriptions, cluster similar alerts |
| **ONNX Runtime** | ML model serving | Low-latency inference for triage scoring on CPU |

### Security & Compliance
| Technology | Purpose | Why |
|-----------|---------|-----|
| **Azure Immutable Blob Storage** | Audit trail | Write-once, tamper-proof logs of every AI decision |
| **Azure Key Vault** | Secrets management | API keys for Defender, Graph, SOAR integrations |
| **Managed Identity** | Auth to Azure services | No stored credentials, automatic token rotation |
| **Azure Private Link** | Network isolation | All traffic stays on Microsoft backbone |

---

## How to Explain It Well

### 30-Second Pitch
> "We're building an AI SOC analyst that processes the massive stream of security events — 1M events/second flowing through Event Hubs into Kusto tables — and uses a three-agent architecture to autonomously handle incidents. The Triage Agent uses ML to score and correlate raw alerts, reducing 10,000 alerts/day down to ~500 actionable incidents. The Investigation Agent uses an LLM in a ReAct loop, autonomously generating and executing KQL queries to build attack timelines and map to MITRE ATT&CK. The Response Agent recommends and executes containment actions with tiered approval — auto-execute for low-risk, human approval for high-impact. Analysts interact via a KQL Copilot that translates natural language to KQL and a chat-based investigator."

### Key Talking Points
1. **Data gravity in Kusto**: All security data lives in ADX — the AI agents query it directly via KQL, no data movement needed
2. **Three-agent separation**: Triage (fast ML, <30s) → Investigation (deep LLM, 30s-5min) → Response (action execution) — each with different latency and cost profiles
3. **KQL as the universal language**: Both human analysts and AI agents speak KQL — the LLM generates the same queries analysts would write
4. **Human-in-the-loop by design**: AI recommends, humans approve high-impact actions; full audit trail for every decision
5. **Alert fatigue is THE problem**: 10,000 alerts → 500 incidents → 50 requiring human attention. The AI handles the noise.

### Whiteboard Sequence
1. Start with data sources → Event Hubs → Kusto (the data foundation)
2. Draw the three agents: Triage → Investigation → Response pipeline
3. Show the Investigation Agent's ReAct loop with KQL tool-calling
4. Add the KQL Copilot for analyst interaction
5. Overlay the governance layer (audit trail, approval gates)

---

## Common Follow-up Questions

### "How do you prevent the LLM from running dangerous KQL queries?"
- **Parse the KQL AST** before execution — reject any mutations (`.set`, `.drop`, `.delete`)
- **Read-only connection**: The Kusto principal used by the agent has `Viewer` role only
- **Row limits**: Automatically append `| take 10000` if no limit is present
- **Cost estimation**: Estimate data scan size from table stats, reject queries scanning >1TB
- **Query timeout**: Hard 2-minute timeout per KQL query
- **Audit logging**: Every generated query is logged with the LLM reasoning that produced it

### "How do you handle false positives from the AI?"
- **Analyst feedback loop**: Every AI decision has "Confirm" / "False Positive" buttons
- **Weekly retraining**: Triage ML model retrained on analyst labels (true positive vs false positive)
- **Confidence scores**: Investigation Agent reports confidence; low confidence → escalate to human
- **Tunable thresholds**: Each organization adjusts auto-triage sensitivity per alert source
- **False positive suppression**: Repeated false positives auto-create suppression rules

### "How does this scale for an MSSP (multi-tenant)?"
- **Kusto row-level security**: Each query automatically filtered by `TenantId` — no cross-tenant leakage
- **Event Hubs partitioning**: Partition by TenantId for isolation and parallel processing
- **Per-tenant AI config**: Different severity thresholds, approved response actions, and LLM personas
- **Cost attribution**: Token usage and KQL compute metered per tenant
- **Shared infrastructure**: Single Kusto cluster with tenant isolation — far cheaper than cluster-per-tenant

### "How do you maintain the entity risk baselines at scale?"
- **Kusto scheduled jobs** (batch): Run daily aggregation queries over 30-day windows per entity
- **Materialized views**: Pre-compute `login_hour_distribution`, `geo_distribution`, `app_usage` per user
- **Redis snapshots**: Push updated risk scores to Redis every 15 minutes
- **Incremental updates**: Stream Analytics updates risk scores in near-real-time as events arrive
- **Cold start**: New entities start at baseline risk = 0, elevate based on first-seen signals

### "What happens when the LLM API is down?"
- **Graceful degradation**:
  1. Triage Agent continues (ML model runs locally, no LLM dependency)
  2. Investigation falls back to pre-built KQL playbook templates (no LLM reasoning)
  3. Response Agent queues recommended actions for when LLM recovers
  4. KQL Copilot shows "AI unavailable" — analyst writes KQL manually
- **Multi-region LLM**: Azure OpenAI deployed in 2+ regions with automatic failover
- **Queue-based architecture**: Incidents queue in Cosmos DB, processed when capacity returns
