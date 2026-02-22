# Appendix — Real-World AI Coding Agents: Cline & Roo Code

> **🎯 Goal**: Understand how production AI agents implement the patterns you've learned throughout this syllabus.

> **⏱ Time**: 2–3h reading + exploration

> **📦 Deliverable**: A mental model of how real-world coding agents are built and the key design decisions behind them

---

## Table of Contents

1. [Why Study Real-World Agents?](#why-study-real-world-agents)
2. [Cline — Deep Dive](#cline--deep-dive)
3. [Roo Code — Deep Dive](#roo-code--deep-dive)
4. [Head-to-Head Comparison](#head-to-head-comparison)
5. [Key Patterns to Learn From](#key-patterns-to-learn-from)
6. [Connection to Syllabus Concepts](#connection-to-syllabus-concepts)
7. [Further Reading](#further-reading)

---

## Why Study Real-World Agents?

Throughout this syllabus, you learned to build agents from scratch — ReAct loops, planners, RAG pipelines, multi-agent systems. But how do **production agents** apply these same patterns at scale?

Cline and Roo Code are two of the most popular open-source AI coding agents. They run inside VS Code, read and edit your code, execute terminal commands, and automate browser tasks — all powered by LLMs. Studying them reveals:

- How theoretical patterns (ReAct, planning, memory) translate to real products
- Engineering trade-offs in context management, tool design, and safety
- The emergence of **MCP (Model Context Protocol)** as a universal tool standard
- How mode/role-based architectures create specialized agent behaviors

```
┌─────────────────────────────────────────────────────────────────┐
│                    WHAT YOU'LL LEARN                             │
│                                                                  │
│  Syllabus Theory          →    Real-World Implementation        │
│  ────────────────              ──────────────────────            │
│  ReAct Loop (Day 3)       →    Cline's Plan/Act/Approve cycle   │
│  Tools (Day 3)            →    MCP protocol & tool ecosystem    │
│  Planner/Executor (Day 4) →    Roo Code's Architect + Code      │
│  RAG + Memory (Day 5)     →    Memory Bank & context windows    │
│  Multi-Agent (Day 6)      →    Roo Code Cloud agent teams       │
│  Full System (Day 7)      →    Production-grade architecture    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Cline — Deep Dive

### What Is Cline?

Cline is an open-source autonomous coding agent that runs as a VS Code extension. Originally named "Claude Dev," it evolved into a model-agnostic agent with 5M+ installs. Its defining philosophy: **human-in-the-loop safety** — every action requires explicit user approval.

### Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     CLINE ARCHITECTURE                            │
│                                                                   │
│  ┌──────────────┐    ┌──────────────────┐    ┌────────────────┐  │
│  │  VS Code      │    │  Extension Host   │    │  LLM Provider  │  │
│  │  (User UI)    │◄──►│  (TypeScript)     │◄──►│  (API)         │  │
│  └──────────────┘    └────────┬─────────┘    └────────────────┘  │
│                               │                                   │
│                    ┌──────────┼──────────┐                       │
│                    │          │          │                        │
│               ┌────▼───┐ ┌───▼────┐ ┌───▼────┐                  │
│               │ File    │ │Terminal│ │Browser │                  │
│               │ System  │ │        │ │(headl.)│                  │
│               └────────┘ └────────┘ └────────┘                  │
│                                                                   │
│  ┌──────────────┐    ┌──────────────────┐                        │
│  │  React       │    │  Go CLI           │                        │
│  │  Webview     │    │  (standalone)     │                        │
│  └──────────────┘    └──────────────────┘                        │
│                                                                   │
│  Communication: gRPC / Protocol Buffers                          │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

**Key components:**

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Extension Host | TypeScript / Node.js | Core agent logic, tool orchestration |
| Webview UI | React / TypeScript | Chat interface, diff previews, approvals |
| CLI | Go | Standalone terminal usage without VS Code |
| Inter-process | gRPC + Protobuf | Communication between extension, CLI, and MCP servers |

### The Agent Loop

Cline implements a variation of the **ReAct pattern** (Day 3) with an explicit approval gate:

```
┌───────────────────────────────────────────────────────────────┐
│                    CLINE AGENT LOOP                            │
│                                                                │
│  User Task                                                     │
│      │                                                         │
│      ▼                                                         │
│  ┌─────────────┐                                               │
│  │ PLAN MODE   │  Analyze codebase, propose step-by-step plan  │
│  │ (Reasoning) │  Show plan to user for review                 │
│  └──────┬──────┘                                               │
│         │ User approves plan                                   │
│         ▼                                                      │
│  ┌─────────────┐                                               │
│  │ ACT MODE    │  Execute next step using tools                │
│  │ (Action)    │  File edit, terminal cmd, browser action       │
│  └──────┬──────┘                                               │
│         │                                                      │
│         ▼                                                      │
│  ┌─────────────┐                                               │
│  │ OBSERVE     │  Read tool output, analyze results            │
│  │ (Result)    │  Check for errors, unexpected behavior        │
│  └──────┬──────┘                                               │
│         │                                                      │
│         ▼                                                      │
│  ┌─────────────┐      ┌──────────┐                             │
│  │ APPROVE?    │─No──►│ User     │                             │
│  │ (Human Gate)│      │ Rejects  │──► Revise plan              │
│  └──────┬──────┘      └──────────┘                             │
│     Yes │                                                      │
│         ▼                                                      │
│    More steps? ──Yes──► Loop back to ACT MODE                  │
│         │                                                      │
│         No                                                     │
│         ▼                                                      │
│     ✅ Done                                                    │
│                                                                │
└───────────────────────────────────────────────────────────────┘
```

**Key insight**: This is the ReAct loop from Day 3, but with a **human approval gate** inserted between every action. This makes Cline safer but slower — a fundamental design trade-off.

### Tool System

Cline provides built-in tools that map directly to the tool concepts from Day 3:

```python
# Conceptual representation of Cline's tool registry
CLINE_TOOLS = {
    # File operations
    "read_file":       {"input": "path", "output": "file_contents"},
    "write_to_file":   {"input": "path, content", "output": "success/fail"},
    "apply_diff":      {"input": "path, diff", "output": "patched_file"},
    "list_files":      {"input": "directory, recursive?", "output": "file_tree"},

    # Search
    "search_files":    {"input": "regex, path", "output": "matches"},  # ripgrep-based
    "list_code_definitions": {"input": "path", "output": "AST_symbols"},  # tree-sitter

    # Execution
    "execute_command": {"input": "command", "output": "stdout + stderr"},

    # Browser
    "browser_action":  {"input": "url, action, selector", "output": "screenshot + console"},

    # MCP (extensible)
    "use_mcp_tool":    {"input": "server, tool, args", "output": "tool_result"},
}
```

**Search capabilities:**
- **Regex search**: Uses `ripgrep` under the hood for fast codebase-wide pattern matching
- **AST-based search**: Uses `tree-sitter` to parse code and extract function/class/method definitions
- **File tree exploration**: Recursive directory listing with smart filtering

### MCP — Model Context Protocol

MCP is one of the most important concepts to learn from these agents. It's a **universal standard for connecting LLMs to external tools** — think of it as "USB for AI tools."

```
┌────────────────────────────────────────────────────────────────┐
│                   MCP ARCHITECTURE                              │
│                                                                 │
│  ┌─────────┐         ┌──────────┐         ┌─────────────────┐  │
│  │  Cline  │◄──JSON──►│  MCP     │◄──JSON──►│  External Tool  │  │
│  │  Agent  │  RPC     │  Server  │  RPC     │  (any language) │  │
│  └─────────┘         └──────────┘         └─────────────────┘  │
│                                                                 │
│  Transport options:                                             │
│  ┌─────────────┐  ┌──────────────┐                             │
│  │ STDIO       │  │ HTTP / SSE   │                             │
│  │ (local)     │  │ (remote)     │                             │
│  │ stdin/stdout│  │ streaming    │                             │
│  └─────────────┘  └──────────────┘                             │
│                                                                 │
│  Examples of MCP tools:                                         │
│  • GitHub API         • Jira/Linear                             │
│  • Database queries   • CI/CD triggers                          │
│  • Slack messaging    • Custom scripts                          │
│  • Web scraping       • Security scanners                       │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

**How MCP works:**
1. An MCP server declares the tools it provides (name, description, input schema)
2. The agent discovers available tools at startup
3. When the LLM decides to use an MCP tool, it sends a JSON-RPC request
4. The server executes the tool and returns structured results
5. The agent incorporates results into its reasoning

**Why this matters**: MCP decouples tool implementation from the agent. Anyone can write an MCP server in any language, and any MCP-compatible agent can use it. This is the same concept as the tool abstraction from Day 3, but standardized across the industry.

### Context Management

Cline uses several strategies to manage context efficiently:

```
┌────────────────────────────────────────────────────────────────┐
│                CONTEXT MANAGEMENT STRATEGIES                    │
│                                                                 │
│  1. Smart File Reading                                          │
│     ┌──────────┐     ┌──────────┐     ┌──────────┐            │
│     │ Read only │────►│ AST parse│────►│ Send     │            │
│     │ relevant  │     │ symbols  │     │ context  │            │
│     │ files     │     │ only     │     │ to LLM   │            │
│     └──────────┘     └──────────┘     └──────────┘            │
│                                                                 │
│  2. Checkpoint System                                           │
│     Every action creates a snapshot for rollback                │
│     (like Git commits but for AI-driven changes)                │
│                                                                 │
│  3. Context Window Slicing                                      │
│     Breaks large codebases into manageable chunks               │
│     that fit within LLM token limits                            │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### Memory & Project Knowledge

Cline uses structured project files for persistent memory:

| File | Purpose |
|------|---------|
| `projectbrief.md` | High-level project goals and architecture |
| `progress.md` | What's been done, what's remaining |
| `techstack.md` | Technologies, frameworks, conventions |
| `currentTask.md` | Active task context and status |

This is a practical implementation of the **memory types** from Day 3 (short-term, long-term, episodic) and the **RAG concepts** from Day 5.

### Model Support

Cline is model-agnostic — it works with any LLM provider:

| Provider | Models | Use Case |
|----------|--------|----------|
| Anthropic | Claude Sonnet, Opus, Haiku | Primary coding models |
| OpenAI | GPT-4, GPT-4o | Alternative provider |
| Google | Gemini Pro, Flash | Cost-effective option |
| AWS | Bedrock (Claude, etc.) | Enterprise deployments |
| Local | Ollama, LM Studio | Privacy-first, offline use |

---

## Roo Code — Deep Dive

### What Is Roo Code?

Roo Code (formerly "Roo Cline") is a fork of Cline that evolved into a **multi-agent, role-based coding platform**. While Cline focuses on safety-first single-agent interaction, Roo Code emphasizes **speed, specialization, and cost optimization** through its Modes system.

### Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                   ROO CODE ARCHITECTURE                           │
│                                                                   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                    VS Code Extension                      │    │
│  │  ┌─────────────────────────────────────────────────┐     │    │
│  │  │              MODE ROUTER                         │     │    │
│  │  │  Selects active mode and model per task          │     │    │
│  │  └──────────┬──────────┬──────────┬────────────────┘     │    │
│  │             │          │          │                       │    │
│  │  ┌──────────▼┐  ┌─────▼──────┐  ┌▼──────────┐          │    │
│  │  │ Architect │  │   Code     │  │  Debug    │  ...      │    │
│  │  │ Mode      │  │   Mode     │  │  Mode     │           │    │
│  │  │ (plan)    │  │   (build)  │  │  (fix)    │           │    │
│  │  └───────────┘  └────────────┘  └───────────┘          │    │
│  └──────────────────────────────────────────────────────────┘    │
│                          │                                       │
│          ┌───────────────┼───────────────┐                      │
│          │               │               │                       │
│   ┌──────▼──────┐ ┌─────▼─────┐ ┌──────▼──────┐               │
│   │ MCP Servers │ │ File/Term │ │ Browser     │               │
│   │ (plugins)   │ │ (native)  │ │ (headless)  │               │
│   └─────────────┘ └───────────┘ └─────────────┘               │
│                                                                   │
│  ┌────────────────────────────────────────────────┐              │
│  │            ROO CODE CLOUD (optional)            │              │
│  │  Multi-agent: Planner, Coder, Reviewer, Tester │              │
│  │  Team collaboration via Slack, GitHub, Web      │              │
│  └────────────────────────────────────────────────┘              │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### The Modes System

Roo Code's most distinctive feature is its **Modes** — specialized agent personas, each with constrained behaviors and tool access. This is a direct implementation of the **agent roles** concept from Day 6 (Multi-Agent Collaboration).

```
┌─────────────────────────────────────────────────────────────────┐
│                     ROO CODE MODES                               │
│                                                                  │
│  ┌───────────────┐  Purpose: High-level planning & architecture │
│  │  🏗️ Architect  │  Tools: Read files, search — NO file writes  │
│  │     Mode       │  Model: Premium (Claude Opus, GPT-4)        │
│  └───────────────┘                                               │
│                                                                  │
│  ┌───────────────┐  Purpose: Write and modify code               │
│  │  💻 Code       │  Tools: Read, write, terminal, browser       │
│  │     Mode       │  Model: Standard (Claude Sonnet, GPT-4o)    │
│  └───────────────┘                                               │
│                                                                  │
│  ┌───────────────┐  Purpose: Answer questions about codebase     │
│  │  ❓ Ask        │  Tools: Read files, search — NO writes       │
│  │     Mode       │  Model: Fast/cheap (Haiku, GPT-4o-mini)     │
│  └───────────────┘                                               │
│                                                                  │
│  ┌───────────────┐  Purpose: Diagnose and fix bugs               │
│  │  🐛 Debug      │  Tools: Read, terminal, logs — limited write │
│  │     Mode       │  Model: Standard + reasoning models          │
│  └───────────────┘                                               │
│                                                                  │
│  ┌───────────────┐  Purpose: Write and run tests                 │
│  │  🧪 Test       │  Tools: Read, write test files, terminal     │
│  │     Mode       │  Model: Standard                             │
│  └───────────────┘                                               │
│                                                                  │
│  ┌───────────────┐  Purpose: User-defined specialized behaviors  │
│  │  ⚙️ Custom     │  Tools: User-configured                      │
│  │     Modes      │  Model: User-configured                      │
│  └───────────────┘                                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Why modes matter:**
- Each mode has a **constrained tool set** — the Architect can't write files, preventing accidental changes during planning
- Each mode can use a **different LLM** — use expensive models for complex planning, cheap models for quick questions
- Custom modes let you create **domain-specific agents** (security reviewer, docs writer, performance optimizer)
- This is the Planner/Executor pattern from Day 4, but user-configurable and extensible

### MCP Integration

Roo Code extends Cline's MCP support with a more structured management layer:

```
┌────────────────────────────────────────────────────────────────┐
│               ROO CODE MCP MANAGEMENT                           │
│                                                                 │
│  ┌──────────────────┐                                           │
│  │ McpServerManager │  Singleton — one per workspace            │
│  │  (coordinator)   │  Discovers & manages all MCP connections  │
│  └────────┬─────────┘                                           │
│           │                                                      │
│           ▼                                                      │
│  ┌──────────────────┐                                           │
│  │     McpHub       │  Active connection & execution layer      │
│  │  (runtime)       │  Routes requests to correct server        │
│  └────────┬─────────┘                                           │
│           │                                                      │
│     ┌─────┼─────────────────┐                                   │
│     │     │                 │                                    │
│  ┌──▼───┐ ┌──▼───┐    ┌────▼────┐                              │
│  │STDIO │ │STDIO │    │HTTP/SSE │                               │
│  │Server│ │Server│    │Server   │                               │
│  │(local)│ │(local)│   │(remote) │                              │
│  └──────┘ └──────┘    └─────────┘                               │
│                                                                 │
│  Configuration hierarchy:                                       │
│  ┌──────────────────────────────────────────┐                   │
│  │ Global: mcp_settings.json (all projects) │                   │
│  │ Project: .roo/mcp.json (per-project)     │ ← version control│
│  └──────────────────────────────────────────┘                   │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

**MCP configuration example:**

```json
{
  "mcpServers": {
    "github-tools": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": { "GITHUB_TOKEN": "${GITHUB_TOKEN}" }
    },
    "database-query": {
      "command": "python",
      "args": ["mcp_db_server.py"],
      "env": { "DB_URL": "postgresql://localhost/mydb" }
    },
    "remote-security-scanner": {
      "url": "https://security-scan.example.com/mcp",
      "transport": "http"
    }
  }
}
```

### Memory Bank

Roo Code introduces **persistent memory** that survives across sessions — a production implementation of the memory concepts from Day 3 and the RAG pipeline from Day 5:

```
┌────────────────────────────────────────────────────────────────┐
│                  ROO CODE MEMORY BANK                           │
│                                                                 │
│  Session Memory (short-term)                                    │
│  ┌──────────────────────────────────────────┐                  │
│  │ Current conversation context              │                  │
│  │ Active file contents & diffs              │                  │
│  │ Tool execution results                    │                  │
│  └──────────────────────────────────────────┘                  │
│                     │                                           │
│                     ▼                                           │
│  Profile Memory (long-term)                                     │
│  ┌──────────────────────────────────────────┐                  │
│  │ Project architecture decisions            │                  │
│  │ Coding conventions & style rules          │                  │
│  │ Known issues & workarounds                │                  │
│  │ Persists across sessions                  │                  │
│  └──────────────────────────────────────────┘                  │
│                     │                                           │
│                     ▼                                           │
│  Semantic Index (episodic / RAG)                                │
│  ┌──────────────────────────────────────────┐                  │
│  │ Vector embeddings of codebase             │                  │
│  │ Semantic search over project knowledge    │                  │
│  │ Context condensation for large repos      │                  │
│  └──────────────────────────────────────────┘                  │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### Cost Optimization

One of Roo Code's most practical innovations is **task-based model routing**:

```
┌────────────────────────────────────────────────────────────────┐
│              COST-AWARE MODEL ROUTING                           │
│                                                                 │
│  Task Complexity Assessment                                     │
│       │                                                         │
│       ├──► Simple question ──► Haiku / GPT-4o-mini  ($0.001)   │
│       │                                                         │
│       ├──► Code generation ──► Sonnet / GPT-4o      ($0.01)    │
│       │                                                         │
│       ├──► Architecture    ──► Opus / GPT-4          ($0.05)    │
│       │                                                         │
│       └──► Custom task     ──► User-configured model            │
│                                                                 │
│  Result: Up to 97% cost reduction vs. always using top models   │
│                                                                 │
│  Profile configuration:                                         │
│  ┌──────────────────────────────────────────┐                  │
│  │ profile: "cost-efficient"                │                  │
│  │   architect_model: "claude-sonnet"       │                  │
│  │   code_model: "deepseek-coder"           │                  │
│  │   ask_model: "claude-haiku"              │                  │
│  │   debug_model: "claude-sonnet"           │                  │
│  └──────────────────────────────────────────┘                  │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### Roo Code Cloud

Roo Code Cloud extends the local extension into a **multi-agent collaboration platform** — the production version of Day 6's multi-agent system:

```
┌────────────────────────────────────────────────────────────────┐
│                 ROO CODE CLOUD                                  │
│                                                                 │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐  │
│  │  Planner  │  │   Coder   │  │ Reviewer  │  │  Tester   │  │
│  │  Agent    │  │   Agent   │  │  Agent    │  │  Agent    │  │
│  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  │
│        │              │              │              │          │
│        └──────────────┴──────────────┴──────────────┘          │
│                          │                                      │
│                    ┌─────▼─────┐                                │
│                    │Orchestrator│                               │
│                    │           │                                │
│                    └─────┬─────┘                                │
│                          │                                      │
│              ┌───────────┼───────────┐                          │
│              │           │           │                          │
│         ┌────▼────┐ ┌───▼───┐ ┌────▼────┐                     │
│         │ GitHub  │ │ Slack │ │  Web    │                      │
│         │         │ │       │ │  UI     │                      │
│         └─────────┘ └───────┘ └─────────┘                      │
│                                                                 │
│  Workflow: Task → Plan → Code → Review → Test → Merge          │
│  Human oversight at configurable checkpoints                    │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

---

## Head-to-Head Comparison

### Architecture & Stack

| Aspect | Cline | Roo Code |
|--------|-------|----------|
| **Origin** | Independent open-source project | Fork of Cline, expanded |
| **Extension** | VS Code (TypeScript) | VS Code (TypeScript) |
| **UI** | React webview | React webview |
| **CLI** | Go-based standalone CLI | — |
| **IPC** | gRPC + Protobuf | VS Code API + MCP |
| **Cloud** | — | Roo Code Cloud (multi-agent) |

### Agent Behavior

| Aspect | Cline | Roo Code |
|--------|-------|----------|
| **Agent Pattern** | Single ReAct agent | Multi-mode, role-based agents |
| **Planning** | Plan mode (single agent) | Architect Mode (dedicated) |
| **Execution** | Act mode (same agent) | Code Mode (specialized) |
| **Approval** | Every action needs approval | Configurable (strict ↔ autonomous) |
| **Custom Roles** | Not supported | Fully customizable modes |

### Tools & Search

| Aspect | Cline | Roo Code |
|--------|-------|----------|
| **File I/O** | Read, write, diff | Read, write, diff |
| **Terminal** | Command execution + output | Command execution + output |
| **Browser** | Headless automation | Headless automation |
| **Code Search** | Regex (ripgrep) + AST (tree-sitter) | Regex + AST + semantic embeddings |
| **MCP** | Full support | Full support + management layer |

### Context & Memory

| Aspect | Cline | Roo Code |
|--------|-------|----------|
| **Context Strategy** | AST parsing, smart windowing | Large windows, context condensation |
| **Memory** | Per-session + project files | Memory Bank (persistent, cross-session) |
| **Checkpoints** | Manual (via approvals) | Session & profile-based snapshots |
| **Semantic Search** | — | Vector embeddings, semantic index |

### Cost & Models

| Aspect | Cline | Roo Code |
|--------|-------|----------|
| **Model Support** | Multi-provider, configurable | Multi-provider, per-mode profiles |
| **Cost Control** | Manual model selection | Task-based auto-routing |
| **Local Models** | Ollama, LM Studio | Ollama, LM Studio |
| **Cost Savings** | Depends on user choices | Up to 97% with smart routing |

### When to Use Which?

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Choose CLINE when:                Choose ROO CODE when:         │
│  ─────────────────────             ───────────────────────       │
│  ✓ Safety is paramount            ✓ Speed matters most           │
│  ✓ Regulated codebase             ✓ Cost optimization needed     │
│  ✓ Learning agent concepts        ✓ Team collaboration           │
│  ✓ Want full control              ✓ Complex multi-step tasks     │
│  ✓ Simple, predictable UX        ✓ Custom agent behaviors        │
│  ✓ CLI usage needed              ✓ Large codebase handling        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Key Patterns to Learn From

### 1. MCP as Universal Tool Protocol

Both agents use MCP, making it the emerging standard for AI tool integration. Key takeaway: **decouple your tools from your agent**. Write tools as MCP servers, and any agent can use them.

```
Agent-specific tools     →    MCP standard tools
─────────────────────          ─────────────────
• Locked to one agent          • Reusable across agents
• Custom integration           • Standard JSON-RPC
• Hard to share                • Easy to distribute
• One language                 • Any language
```

### 2. Mode/Role-Based Specialization

Roo Code's modes prove that **constrained agents outperform general-purpose ones**. Instead of one agent that does everything, create specialized agents with:
- Limited tool access (Architect can't write files)
- Optimized prompts (Debug mode has different system prompts than Code mode)
- Right-sized models (cheap models for simple tasks)

### 3. Human-in-the-Loop vs. Autonomous

Cline and Roo Code represent two ends of a spectrum:

```
Cline                                                     Roo Code
│ ◄────────────── APPROVAL SPECTRUM ──────────────► │
│                                                      │
│  Every action    Configurable     Most actions       │
│  approved        checkpoints      autonomous         │
│                                                      │
│  Safer           Balanced         Faster             │
│  Slower          Flexible         Riskier            │
```

**Design lesson**: Let users choose their position on this spectrum based on risk tolerance and task type.

### 4. Persistent Memory & Session Management

Both agents solve the "amnesia problem" differently:
- **Cline**: Structured markdown files (`projectbrief.md`, `progress.md`)
- **Roo Code**: Memory Bank with profiles and semantic indexing

This connects directly to Day 5's RAG concepts — persistent memory is just RAG applied to the agent's own history.

### 5. Cost-Aware Model Routing

Roo Code's profile system demonstrates a critical production pattern: **not every task needs the best model**. Design your agent systems to route tasks to appropriately-sized models.

### 6. IDE-Native Agent Design

Both agents are built into VS Code rather than running as standalone tools. This gives them:
- Direct file system access (no upload/download cycle)
- Terminal integration (run commands in the user's environment)
- Diff previews (show changes before applying)
- Native UI (no context switching between tools)

**Lesson**: The best agent UX minimizes friction by meeting developers where they already work.

---

## Connection to Syllabus Concepts

This table maps every concept from the 7-day syllabus to its real-world implementation in Cline and Roo Code:

| Syllabus Day | Concept | Cline Implementation | Roo Code Implementation |
|---|---|---|---|
| **Day 1**: LLM Foundations | Tokenization, context windows | Smart context slicing, AST parsing | Context condensation, large windows |
| **Day 1**: LLM Foundations | Model selection | Multi-provider support | Per-mode model profiles |
| **Day 2**: Prompt Engineering | System prompts, CoT | Mode-specific prompts | Role-based system prompts per mode |
| **Day 2**: Prompt Engineering | Structured output | JSON tool call formatting | JSON tool calls + MCP schemas |
| **Day 3**: ReAct Agent | Think → Act → Observe loop | Plan → Act → Approve cycle | Mode Router → Execute → Observe |
| **Day 3**: ReAct Agent | Tool definitions | Built-in tool registry | Built-in + MCP extensible tools |
| **Day 3**: ReAct Agent | Memory types | Project files (short + long term) | Memory Bank (short + long + episodic) |
| **Day 4**: Planner/Executor | Task decomposition | Plan mode proposes steps | Architect mode creates plans |
| **Day 4**: Planner/Executor | Critic loops | Human approval as critic | Configurable review checkpoints |
| **Day 4**: Planner/Executor | Guardrails | All actions require approval | Tool restrictions per mode |
| **Day 5**: RAG + Memory | Vector search | — | Semantic index with embeddings |
| **Day 5**: RAG + Memory | Persistent knowledge | Markdown project files | Memory Bank + profiles |
| **Day 5**: RAG + Memory | Chunking strategies | File-level context slicing | Context condensation algorithms |
| **Day 6**: Multi-Agent | Agent roles | Single agent (all roles) | Architect, Code, Debug, Test, Custom |
| **Day 6**: Multi-Agent | Orchestration | — | Mode Router + Roo Code Cloud |
| **Day 6**: Multi-Agent | Communication | — | Cloud: Planner↔Coder↔Reviewer |
| **Day 7**: Full System | Production architecture | VS Code + gRPC + MCP | VS Code + MCP + Cloud platform |
| **Day 7**: Full System | Safety & governance | Human-in-the-loop everywhere | Configurable approval + SOC2 |
| **Day 7**: Full System | Scalability | Single user, local | Team collaboration, cloud agents |

---

## Further Reading

### Cline
- **GitHub**: [github.com/cline/cline](https://github.com/cline/cline)
- **VS Code Marketplace**: [Cline Extension](https://marketplace.visualstudio.com/items?itemName=saoudrizwan.claude-dev)
- **DeepWiki Architecture**: [deepwiki.com/cline/cline](https://deepwiki.com/cline/cline)
- **Community**: Discord (linked from GitHub)

### Roo Code
- **GitHub**: [github.com/RooCodeInc/Roo-Code](https://github.com/RooCodeInc/Roo-Code)
- **Documentation**: [docs.roocode.com](https://docs.roocode.com/)
- **MCP Guide**: [docs.roocode.com/features/mcp](https://docs.roocode.com/features/mcp/overview/)
- **Roo Code Cloud**: [roocode.com](https://roocode.com/)

### MCP Standard
- **Specification**: [modelcontextprotocol.io](https://modelcontextprotocol.io/docs/getting-started/intro)
- **Server Examples**: [github.com/modelcontextprotocol](https://github.com/modelcontextprotocol)

### Comparisons
- [Cline vs Roo Code vs Cursor (Better Stack)](https://betterstack.com/community/comparisons/cline-vs-roo-code-vs-cursor/)
- [Roo Code vs Cline (Qodo)](https://www.qodo.ai/blog/roo-code-vs-cline/)
- [Roo Code vs Cline (Openxcell)](https://www.openxcell.com/blog/roo-code-vs-cline/)

---

**← Back to [Syllabus Overview](README.md)**
