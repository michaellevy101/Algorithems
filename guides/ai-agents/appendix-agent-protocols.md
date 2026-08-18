# Appendix — Agent Protocols: MCP, A2A, ACP, AG-UI & ANP

> **🎯 Goal**: Understand the emerging protocol landscape that enables agents to connect to tools, talk to each other, and interact with users and the wider internet.

> **⏱ Time**: 1.5–2h reading

> **📦 Deliverable**: A clear mental model of when and why to use each protocol, and how they compose together in production systems

---

## Table of Contents

1. [Why Agent Protocols Matter](#why-agent-protocols-matter)
2. [The Protocol Landscape](#the-protocol-landscape)
3. [Networking Primer: Building Blocks](#networking-primer-building-blocks)
4. [MCP — Model Context Protocol](#mcp--model-context-protocol)
5. [A2A — Agent-to-Agent Protocol](#a2a--agent-to-agent-protocol)
6. [ACP — Agent Communication Protocol](#acp--agent-communication-protocol)
7. [AG-UI — Agent-User Interaction Protocol](#ag-ui--agent-user-interaction-protocol)
8. [ANP — Agent Network Protocol](#anp--agent-network-protocol)
9. [Protocol Comparison](#protocol-comparison)
10. [Choosing the Right Protocol](#choosing-the-right-protocol)
11. [How They Compose Together](#how-they-compose-together)
12. [Interview Angle](#interview-angle)
13. [Further Reading](#further-reading)

---

## Why Agent Protocols Matter

Day 3 taught you to build an agent with tools. Day 6 showed multi-agent orchestration. But those systems assumed **you controlled everything** — the tools, the agents, and the communication between them.

Production systems face harder problems:

- Your agent needs to call a tool written by a **different team** in a **different language**
- Agents built on **different frameworks** (LangChain, CrewAI, AutoGen) must collaborate
- An agent must interact with a **user-facing frontend** and stream partial results
- Agents from **different organizations** must discover and trust each other

Protocols solve these interoperability problems by defining **standard interfaces** that any implementation can speak. Just as HTTP standardized how browsers talk to servers, agent protocols standardize how agents talk to tools, each other, users, and the wider network.

```
┌────────────────────────────────────────────────────────────────────┐
│               THE AGENT PROTOCOL STACK                              │
│                                                                     │
│   ┌───────────────────────────────────────────────────────────┐    │
│   │  ANP — Agent Network Protocol                             │    │
│   │  Discovery, identity, and trust across the open internet  │    │
│   └─────────────────────────┬─────────────────────────────────┘    │
│                             │                                       │
│   ┌─────────────┐   ┌──────┴──────┐   ┌────────────────────┐      │
│   │  AG-UI      │   │  A2A / ACP  │   │  MCP               │      │
│   │             │   │             │   │                    │      │
│   │  Agent ↔    │   │  Agent ↔    │   │  Agent ↔ Tool     │      │
│   │  User/UI    │   │  Agent      │   │  (data, APIs)     │      │
│   └─────────────┘   └─────────────┘   └────────────────────┘      │
│                                                                     │
│   Each protocol handles a different relationship:                   │
│   • MCP   = How agents USE tools and data                           │
│   • A2A   = How agents COLLABORATE with other agents                │
│   • ACP   = How agents MESSAGE each other (REST-native)             │
│   • AG-UI = How agents INTERACT with human users                    │
│   • ANP   = How agents DISCOVER each other on the open web          │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
```

---

## The Protocol Landscape

Before diving into each protocol, here's how they all relate:

```
                    ┌───────────────────────────────┐
                    │         HUMAN USER            │
                    └──────────────┬────────────────┘
                                   │ AG-UI
                                   │ (events, streaming, state)
                                   ▼
┌──────────┐  A2A / ACP   ┌──────────────┐  A2A / ACP   ┌──────────┐
│  Agent A  │◄────────────►│   Agent B    │◄────────────►│  Agent C  │
│ (Planner) │  (tasks,     │ (Researcher) │  (tasks,     │ (Coder)  │
└─────┬─────┘  messages)   └──────┬───────┘  messages)   └─────┬────┘
      │                           │                             │
      │ MCP                       │ MCP                         │ MCP
      │ (JSON-RPC)                │ (JSON-RPC)                  │ (JSON-RPC)
      ▼                           ▼                             ▼
┌───────────┐              ┌────────────┐               ┌────────────┐
│ Jira API  │              │ Web Search │               │ GitHub API │
└───────────┘              └────────────┘               └────────────┘

                    ANP (Agent Network Protocol)
         ┌──────────────────────────────────────────┐
         │  Identity · Discovery · Trust · Routing   │
         │  (operates across organizational bounds)  │
         └──────────────────────────────────────────┘
```

| Protocol | Created by | Year | Focus | Transport |
|----------|-----------|------|-------|-----------|
| **MCP** | Anthropic | 2024 | Agent ↔ Tool | JSON-RPC over STDIO / HTTP+SSE |
| **A2A** | Google | 2025 | Agent ↔ Agent | JSON-RPC over HTTP+SSE (optional gRPC) |
| **ACP** | IBM (BeeAI) | 2025 | Agent ↔ Agent | REST (HTTP+JSON) |
| **AG-UI** | CopilotKit | 2025 | Agent ↔ User/UI | Event streams over HTTP+SSE |
| **ANP** | AgentNetworkProtocol.com | 2025 | Agent ↔ Internet | HTTP + DID-based identity |

---

## Networking Primer: Building Blocks

Before diving into each protocol's networking details, let's understand the **shared building blocks** they all use. These are standard web technologies — nothing agent-specific.

### JSON-RPC 2.0

JSON-RPC is a lightweight remote procedure call protocol. Used by MCP and A2A as the wire format.

```
Three message types:

1. REQUEST — "Please do something"
   ┌──────────────────────────────────────────────────┐
   │ { "jsonrpc": "2.0",                              │
   │   "id": 1,              ← correlation ID         │
   │   "method": "tools/call",  ← what to do          │
   │   "params": { ... }     ← arguments              │
   │ }                                                 │
   └──────────────────────────────────────────────────┘

2. RESPONSE — "Here's the result"
   ┌──────────────────────────────────────────────────┐
   │ { "jsonrpc": "2.0",                              │
   │   "id": 1,              ← matches the request    │
   │   "result": { ... }     ← success payload        │
   │ }                                                 │
   │                         OR                        │
   │ { "jsonrpc": "2.0",                              │
   │   "id": 1,                                       │
   │   "error": { "code": -32600, "message": "..." }  │
   │ }                                                 │
   └──────────────────────────────────────────────────┘

3. NOTIFICATION — "FYI, no response needed"
   ┌──────────────────────────────────────────────────┐
   │ { "jsonrpc": "2.0",                              │
   │   "method": "notifications/progress",             │
   │   "params": { ... }     ← no "id" field!         │
   │ }                                                 │
   └──────────────────────────────────────────────────┘

Key rule: If a message has "id", it expects a response.
          If no "id", it's fire-and-forget.
```

### HTTP Request/Response

The foundation of the web. Agent protocols use HTTP for remote communication because it's universally supported, load-balancer friendly, and works through firewalls.

```
Client                                           Server
  │                                                 │
  │  POST /endpoint HTTP/1.1                        │
  │  Content-Type: application/json                 │
  │  Authorization: Bearer <token>                  │
  │                                                 │
  │  { "method": "...", "params": { ... } }         │
  │────────────────────────────────────────────────►│
  │                                                 │
  │  HTTP/1.1 200 OK                                │
  │  Content-Type: application/json                 │
  │                                                 │
  │  { "result": { ... } }                          │
  │◄────────────────────────────────────────────────│
  │                                                 │

Key properties:
• Stateless — each request is independent
• Well-supported by proxies, load balancers, API gateways
• Easy to debug with curl, Postman, browser dev tools
```

### SSE (Server-Sent Events)

SSE is a standard for **server-push** over HTTP. The server holds the connection open and sends a stream of events. Used by A2A, ACP, AG-UI, and MCP's Streamable HTTP transport for streaming results.

```
How SSE works:

Client                                            Server
  │                                                  │
  │  GET /stream HTTP/1.1                            │
  │  Accept: text/event-stream                       │
  │────────────────────────────────────────────────►│
  │                                                  │
  │  HTTP/1.1 200 OK                                 │
  │  Content-Type: text/event-stream                 │
  │                                                  │
  │  event: status                                   │
  │  data: {"state": "working"}                      │
  │                                                  │ ← one event
  │  event: status                                   │   (blank line
  │  data: {"state": "completed"}                    │    separates
  │                                                  │    events)
  │  (connection stays open until server closes it)  │

Wire format of a single SSE event:

  event: <event-type>\n       ← optional type tag
  data: <payload>\n           ← JSON string (can span multiple lines)
  id: <event-id>\n            ← optional, for resumption
  \n                          ← blank line = end of event

Key properties:
• One-directional: server → client only
• Built into every browser (EventSource API)
• Auto-reconnect with Last-Event-ID header
• Text-based, easy to debug
```

### STDIO Pipes (stdin / stdout / stderr)

For **local** communication, some protocols skip the network entirely. The client spawns the server as a child process and talks to it through standard I/O pipes.

```
┌──────────────────┐          ┌──────────────────┐
│  Client Process  │          │  Server Process  │
│  (e.g., VS Code) │          │  (e.g., MCP tool)│
│                  │  stdin   │                  │
│  writes ────────►│─────────►│  reads           │
│                  │          │                  │
│  reads  ◄────────│◄─────────│  writes          │
│                  │  stdout  │                  │
└──────────────────┘          └──────────────────┘
                       stderr → logging only (never protocol messages)

Framing: One JSON message per line, delimited by \n
         (newline-delimited JSON, aka NDJSON)

Advantages:
• Zero network overhead — pure inter-process communication
• No ports, no firewalls, no TLS setup
• Server lifecycle tied to client (client exits → server exits)

Used by: MCP (primary local transport)
```

### gRPC and Protocol Buffers

gRPC is Google's high-performance RPC framework. Messages are serialized with Protocol Buffers (Protobuf) — a compact binary format, much smaller than JSON. A2A supports gRPC as an optional transport for high-throughput deployments.

```
Key properties:
• Binary framing → smaller messages, faster parsing than JSON
• Bidirectional streaming — both client and server can stream
• Strongly typed — service definitions in .proto files
• HTTP/2 underneath — multiplexed connections

Trade-off vs JSON-RPC:
  JSON-RPC: Human-readable, debuggable with curl, universal
  gRPC:     Machine-optimized, faster, but needs tooling to inspect
```

---

## MCP — Model Context Protocol

> **One-liner**: "USB-C for AI tools" — the universal standard for connecting agents to external tools and data.

MCP is covered in depth in the [Real-World Agents appendix](appendix-real-world-agents.md#mcp--model-context-protocol). Here we summarize the key points and place MCP in the broader protocol landscape.

### What It Solves

Before MCP, every agent framework had its own tool format. A tool written for LangChain couldn't be used by CrewAI without a wrapper. MCP standardizes this — write a tool once, use it from any MCP-compatible agent.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     MCP ARCHITECTURE                             │
│                                                                  │
│  ┌──────────────┐         ┌──────────────┐                      │
│  │  MCP Client  │◄──JSON──►│  MCP Server  │                      │
│  │  (agent)     │  RPC     │  (tool host) │                      │
│  └──────────────┘         └──────┬───────┘                      │
│                                  │                               │
│                    ┌─────────────┼─────────────┐                │
│                    ▼             ▼             ▼                 │
│              ┌──────────┐ ┌──────────┐ ┌───────────┐           │
│              │ Tool A   │ │ Tool B   │ │ Resource C │           │
│              │ (action) │ │ (action) │ │ (data)     │           │
│              └──────────┘ └──────────┘ └───────────┘           │
│                                                                  │
│  Key primitives:                                                 │
│  • Tools     — actions the agent can invoke (e.g. create_issue) │
│  • Resources — data the agent can read (e.g. file contents)     │
│  • Prompts   — reusable prompt templates                         │
│                                                                  │
│  Transport:                                                      │
│  • STDIO (local) — agent spawns server as child process          │
│  • HTTP + SSE (remote) — server runs as a web service            │
│  • Streamable HTTP — newer bidirectional transport                │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### How It Works

1. **Discovery** — The MCP client connects to a server and calls `initialize` to learn what tools/resources are available
2. **Schema** — Each tool declares its name, description, and JSON Schema for parameters
3. **Invocation** — The LLM generates a `tool_call`; the client sends a JSON-RPC `tools/call` request
4. **Response** — The server executes the tool and returns structured results
5. **Iteration** — The agent incorporates results and decides what to do next

### Example: MCP Tool Declaration

```json
{
  "name": "search_issues",
  "description": "Search for GitHub issues by query",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string", "description": "Search query" },
      "repo":  { "type": "string", "description": "owner/repo" }
    },
    "required": ["query", "repo"]
  }
}
```

### Under the Hood: MCP Networking

MCP uses **JSON-RPC 2.0** as its wire format (see [Networking Primer](#networking-primer-building-blocks)) over two transport options:

#### Transport 1: STDIO (Local)

The client spawns the MCP server as a child process. Messages are **newline-delimited JSON** over stdin/stdout:

```
Client Process (Agent)                    Server Process (MCP Tool)
     │                                          │
     │  stdin ──►  {"jsonrpc":"2.0",            │
     │              "id":1,                      │
     │              "method":"initialize",        │
     │              "params":{"clientInfo":{...}}}│
     │  ─────────────────────────────────────►   │
     │                                          │
     │  stdout ◄── {"jsonrpc":"2.0",            │
     │              "id":1,                      │
     │              "result":{"serverInfo":{...},│
     │                "capabilities":{...}}}     │
     │  ◄─────────────────────────────────────   │
     │                                          │

Framing rules:
• One complete JSON object per line
• Lines delimited by \n
• No embedded newlines within a message
• stderr is for logging ONLY — never protocol messages
• Server lifecycle is tied to the client process
```

#### Transport 2: Streamable HTTP (Remote)

For remote/production use. The server exposes a **single HTTP endpoint** (e.g., `/mcp`). All messages go through it:

```
Client                                           Server (/mcp)
  │                                                  │
  │  POST /mcp HTTP/1.1                              │
  │  Content-Type: application/json                  │
  │  Accept: application/json, text/event-stream     │
  │                                                  │
  │  {"jsonrpc":"2.0","id":1,                        │
  │   "method":"initialize","params":{...}}          │
  │─────────────────────────────────────────────────►│
  │                                                  │
  │  HTTP/1.1 200 OK                                 │
  │  Content-Type: application/json                  │
  │  Mcp-Session-Id: sess_abc123    ← optional       │
  │                                                  │
  │  {"jsonrpc":"2.0","id":1,"result":{...}}         │
  │◄─────────────────────────────────────────────────│

For long-running tool calls, the server can UPGRADE to SSE:

  │  POST /mcp                                       │
  │  {"jsonrpc":"2.0","id":2,                        │
  │   "method":"tools/call",                         │
  │   "params":{"name":"long_search",...}}           │
  │─────────────────────────────────────────────────►│
  │                                                  │
  │  HTTP/1.1 200 OK                                 │
  │  Content-Type: text/event-stream     ← SSE mode  │
  │                                                  │
  │  event: message                                  │
  │  data: {"jsonrpc":"2.0","method":                │
  │         "notifications/progress",                │
  │         "params":{"progress":50}}                │
  │                                                  │  ← streaming
  │  event: message                                  │     progress
  │  data: {"jsonrpc":"2.0","id":2,                  │
  │         "result":{"content":[...]}}              │
  │                                                  │  ← final result
```

**Key design choice**: The `Accept` header lets the client say "I can handle both JSON and SSE." The server decides which to use based on whether the response is immediate or long-running.

#### Full Wire Example: Tool Discovery and Invocation

```
Step 1: Initialize (handshake)
─────────────────────────────
→ {"jsonrpc":"2.0","id":1,"method":"initialize",
    "params":{"protocolVersion":"2025-06-18",
              "clientInfo":{"name":"my-agent","version":"1.0"}}}
← {"jsonrpc":"2.0","id":1,"result":{
    "protocolVersion":"2025-06-18",
    "serverInfo":{"name":"github-mcp","version":"0.5"},
    "capabilities":{"tools":{}}}}

→ {"jsonrpc":"2.0","method":"notifications/initialized"}
  (notification — no id, no response expected)

Step 2: Discover available tools
────────────────────────────────
→ {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
← {"jsonrpc":"2.0","id":2,"result":{
    "tools":[
      {"name":"search_issues",
       "description":"Search GitHub issues",
       "inputSchema":{"type":"object","properties":{...}}}
    ]}}

Step 3: Call a tool
───────────────────
→ {"jsonrpc":"2.0","id":3,"method":"tools/call",
    "params":{"name":"search_issues",
              "arguments":{"query":"bug","repo":"owner/repo"}}}
← {"jsonrpc":"2.0","id":3,"result":{
    "content":[{"type":"text",
                "text":"Found 3 issues: #12, #45, #78"}]}}
```

### Strengths and Limitations

| Strengths | Limitations |
|-----------|-------------|
| Massive ecosystem (1000+ servers) | No agent-to-agent communication |
| Simple to implement | Limited async/streaming patterns |
| Works with any LLM provider | No built-in discovery across organizations |
| Strong tooling (SDKs for Python, TS, Java) | Sessions are client-initiated only |

---

## A2A — Agent-to-Agent Protocol

> **One-liner**: Google's open standard for agents to discover, delegate, and collaborate with other agents across vendors and frameworks.

### What It Solves

MCP lets agents use tools, but what happens when the "tool" is **another autonomous agent** with its own reasoning, planning, and tools? You need a protocol that treats agents as first-class peers, not just function endpoints.

A2A enables agents built on different frameworks (LangChain, CrewAI, Vertex AI, etc.) to collaborate without sharing internal state or requiring the same SDK.

### Core Concepts

```
┌─────────────────────────────────────────────────────────────────┐
│                    A2A CORE CONCEPTS                             │
│                                                                  │
│  ┌──────────────┐                                               │
│  │  Agent Card  │  Machine-readable "business card"              │
│  │  (JSON)      │  • Name, description, version                  │
│  │              │  • Skills (what I can do)                       │
│  │              │  • Auth requirements                            │
│  │              │  • Supported content types                      │
│  └──────────────┘                                               │
│         │                                                        │
│         │  discovered via /.well-known/agent.json                │
│         ▼                                                        │
│  ┌──────────────┐  sendMessage   ┌──────────────┐              │
│  │  Client      │───────────────►│  Remote       │              │
│  │  Agent       │◄───────────────│  Agent        │              │
│  └──────────────┘    Task +      └──────────────┘              │
│                      Artifacts                                   │
│                                                                  │
│  Task lifecycle:                                                 │
│                                                                  │
│  submitted ──► working ──► completed                             │
│                   │              │                                │
│                   ▼              ▼                                │
│              input-needed    failed                               │
│              (human-in-loop)                                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Agent Card

The Agent Card is how agents advertise their capabilities — published at a well-known URL so other agents can discover and evaluate them.

```json
{
  "name": "Code Review Agent",
  "description": "Reviews pull requests for bugs and style issues",
  "url": "https://review-agent.example.com",
  "version": "1.0.0",
  "skills": [
    {
      "id": "review_pr",
      "name": "Review Pull Request",
      "description": "Analyze code changes for bugs, security, and style"
    }
  ],
  "authentication": {
    "schemes": ["Bearer"]
  },
  "defaultInputModes": ["text/plain", "application/json"],
  "defaultOutputModes": ["text/plain", "application/json"]
}
```

### Task Lifecycle

A2A communication is built around **tasks** — structured units of work that one agent delegates to another:

```
Client Agent                          Remote Agent
     │                                      │
     │  1. GET /.well-known/agent.json      │
     │─────────────────────────────────────►│
     │◄─────────────────────────────────────│  Agent Card
     │                                      │
     │  2. POST /tasks/send (sendMessage)   │
     │     { message: "Review PR #42" }     │
     │─────────────────────────────────────►│
     │                                      │  3. Processing...
     │                                      │     (status: working)
     │  4. SSE stream / poll for updates    │
     │◄─────────────────────────────────────│
     │     status: completed                │
     │     artifacts: [review_report]       │
     │                                      │
```

### Key JSON-RPC Methods

| Method | Purpose |
|--------|---------|
| `sendMessage` | Send a task to a remote agent |
| `streamMessage` | Send a task and stream results via SSE |
| `getTask` | Check the status of a submitted task |
| `cancelTask` | Cancel a running task |

### Under the Hood: A2A Networking

A2A uses **JSON-RPC 2.0 over HTTP** (see [Networking Primer](#networking-primer-building-blocks)) with SSE for streaming. Optional gRPC support was added in v0.3.0 for high-throughput deployments.

#### Agent Card Discovery

Before any communication, the client agent fetches the remote agent's **Agent Card** from a well-known URL:

```
Client Agent                               Remote Agent
     │                                          │
     │  GET /.well-known/agent.json HTTP/1.1    │
     │  Host: review-agent.example.com          │
     │─────────────────────────────────────────►│
     │                                          │
     │  HTTP/1.1 200 OK                         │
     │  Content-Type: application/json          │
     │                                          │
     │  {                                       │
     │    "name": "Code Review Agent",          │
     │    "url": "https://review-agent...",     │
     │    "skills": [                           │
     │      { "id": "review_pr",               │
     │        "name": "Review Pull Request" }   │
     │    ],                                    │
     │    "authentication": {                   │
     │      "schemes": ["Bearer"]              │
     │    },                                    │
     │    "defaultInputModes": ["text/plain"],  │
     │    "defaultOutputModes": ["text/plain"]  │
     │  }                                       │
     │◄─────────────────────────────────────────│

The /.well-known/ path follows RFC 8615 — the same convention
used by Let's Encrypt, OAuth, and WebFinger.
```

#### Task Delegation via JSON-RPC

Once the client knows the agent's capabilities, it sends tasks via JSON-RPC POST:

```
Synchronous task (short-lived):
───────────────────────────────
POST /rpc HTTP/1.1
Host: review-agent.example.com
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJSUzI1...

{
  "jsonrpc": "2.0",
  "id": "task-001",
  "method": "sendMessage",
  "params": {
    "message": {
      "role": "user",
      "parts": [{"type": "text", "text": "Review PR #42"}]
    }
  }
}

Response (immediate):

HTTP/1.1 200 OK
Content-Type: application/json

{
  "jsonrpc": "2.0",
  "id": "task-001",
  "result": {
    "id": "task-xyz-987",
    "status": {"state": "completed"},
    "artifacts": [{
      "parts": [{"type": "text", "text": "PR looks good. Minor: ..."}]
    }]
  }
}
```

#### Streaming Tasks via SSE

For long-running tasks, use `streamMessage` — the response comes back as an SSE stream:

```
POST /rpc HTTP/1.1
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer eyJhbGciOiJSUzI1...

{
  "jsonrpc": "2.0",
  "id": "task-002",
  "method": "streamMessage",
  "params": {
    "message": {
      "role": "user",
      "parts": [{"type": "text", "text": "Analyze codebase for security issues"}]
    }
  }
}

Response (SSE stream):

HTTP/1.1 200 OK
Content-Type: text/event-stream

event: message
data: {"jsonrpc":"2.0","id":"task-002","result":{
data:   "id":"task-xyz-988",
data:   "status":{"state":"working",
data:     "message":"Scanning src/ directory..."}}}

event: message
data: {"jsonrpc":"2.0","id":"task-002","result":{
data:   "id":"task-xyz-988",
data:   "status":{"state":"working",
data:     "message":"Found 3 potential issues..."}}}

event: message
data: {"jsonrpc":"2.0","id":"task-002","result":{
data:   "id":"task-xyz-988",
data:   "status":{"state":"completed"},
data:   "artifacts":[{"parts":[{"type":"text",
data:     "text":"Security report: ..."}]}]}}
```

#### Full Sequence: Discovery → Delegation → Result

```
Client Agent                                    Remote Agent
     │                                               │
     │  1. GET /.well-known/agent.json               │
     │──────────────────────────────────────────────►│
     │◄──────────────────────────────────────────────│
     │   { skills: ["review_pr"], auth: "Bearer" }   │
     │                                               │
     │  2. POST /rpc  (sendMessage)                  │
     │     Authorization: Bearer <token>             │
     │     {"method":"sendMessage",                  │
     │      "params":{"message":"Review PR #42"}}    │
     │──────────────────────────────────────────────►│
     │                                               │
     │                        3. Agent processes...   │
     │                           Uses its own MCP    │
     │                           tools internally    │
     │                                               │
     │  4. Response                                  │
     │     {"result":{"status":"completed",          │
     │       "artifacts":[{report}]}}                │
     │◄──────────────────────────────────────────────│
     │                                               │

For streaming: step 4 becomes an SSE stream with
multiple status updates before the final artifact.
```

### Strengths and Limitations

| Strengths | Limitations |
|-----------|-------------|
| 100+ technology partners | Not for agent-to-tool invocation (use MCP) |
| Built-in discovery via Agent Cards | Newer protocol, ecosystem still growing |
| Supports long-running, async tasks | Requires HTTP endpoint per agent |
| Enterprise auth and security built-in | More complex than direct function calls |

---

## ACP — Agent Communication Protocol

> **One-liner**: IBM's REST-native protocol for agent-to-agent messaging, with strong session management and enterprise features.

### What It Solves

ACP addresses the same space as A2A (agent-to-agent communication) but with a **REST-first** philosophy. No special SDKs needed — agents communicate via standard HTTP endpoints that you can test with `curl`.

ACP was developed by IBM Research as part of the [BeeAI](https://github.com/i-am-bee) platform. As of late 2025, ACP and A2A governance are converging under the Linux Foundation's **Agentic AI Foundation**.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   ACP ARCHITECTURE                               │
│                                                                  │
│  ┌──────────────┐  REST / HTTP  ┌──────────────┐               │
│  │  Agent A     │◄────────────►│  Agent B      │               │
│  │              │               │               │               │
│  │  POST /messages/send         │               │               │
│  │  GET  /messages/{id}         │               │               │
│  │  POST /stream/send           │               │               │
│  │  GET  /stream/{id}/events    │               │               │
│  │  GET  /manifest              │               │               │
│  └──────────────┘               └──────────────┘               │
│                                                                  │
│  Key features:                                                   │
│  • Stateful sessions — maintain context across turns             │
│  • MIME-typed payloads — text, files, images, structured data    │
│  • Streaming — incremental results via SSE                       │
│  • Capability tokens — signed, verifiable authorization          │
│  • OpenTelemetry — built-in observability and tracing            │
│                                                                  │
│  Agent lifecycle states:                                         │
│  INITIALIZING → ACTIVE → DEGRADED → RETIRING → RETIRED          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Core REST Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/messages/send` | POST | Send a message (task/query/command) to another agent |
| `/messages/{id}` | GET | Poll for response to an async message |
| `/stream/send` | POST | Open a streaming task |
| `/stream/{id}/events` | GET | Stream partial results / events |
| `/manifest` | GET | Retrieve agent metadata and capabilities |
| `/capabilities/verify` | POST | Verify signed capability tokens |

### Under the Hood: ACP Networking

ACP takes a fundamentally different approach from MCP and A2A — it uses **plain REST** (standard HTTP verbs + JSON bodies), not JSON-RPC. This is its key design decision: any tool that speaks HTTP can participate without special libraries.

#### REST Endpoints on the Wire

```
ACP Server exposes standard HTTP endpoints:

  GET  /manifest              → Agent metadata & capabilities
  POST /messages/send         → Send a message (sync)
  GET  /messages/{id}         → Poll for async response
  POST /stream/send           → Open a streaming conversation
  GET  /stream/{id}/events    → Read streaming events (SSE)
  POST /capabilities/verify   → Verify signed auth tokens
```

#### Discovery: Fetching the Manifest

```
GET /manifest HTTP/1.1
Host: research-agent.example.com

HTTP/1.1 200 OK
Content-Type: application/json

{
  "name": "Research Agent",
  "version": "1.2.0",
  "description": "Searches and summarizes technical content",
  "capabilities": [
    {
      "id": "web_research",
      "description": "Search the web and return summaries",
      "inputMimeTypes": ["text/plain"],
      "outputMimeTypes": ["text/plain", "application/json"]
    }
  ],
  "lifecycle": "ACTIVE",
  "authentication": {
    "type": "capability_token",
    "verifyEndpoint": "/capabilities/verify"
  }
}
```

#### Synchronous Messaging

```
POST /messages/send HTTP/1.1
Host: research-agent.example.com
Content-Type: application/json
Authorization: CapToken eyJjYXBz...

{
  "sender": "did:web:planner.example.com",
  "recipient": "did:web:research-agent.example.com",
  "type": "task",
  "sessionId": "sess-42",
  "payload": {
    "action": "web_research",
    "input": "Latest developments in agent protocols"
  }
}

HTTP/1.1 200 OK
Content-Type: application/json

{
  "messageId": "msg-abc-789",
  "status": "completed",
  "sessionId": "sess-42",
  "payload": {
    "summary": "Five major protocols have emerged...",
    "sources": ["https://...", "https://..."]
  }
}
```

#### Asynchronous Messaging (Poll Pattern)

For long-running tasks, the server returns immediately with a message ID, and the client polls:

```
Client                                    ACP Server
  │                                          │
  │  POST /messages/send                     │
  │  { "action": "deep_analysis", ... }      │
  │─────────────────────────────────────────►│
  │                                          │
  │  202 Accepted                            │  ← not done yet
  │  { "messageId": "msg-xyz",               │
  │    "status": "processing" }              │
  │◄─────────────────────────────────────────│
  │                                          │
  │  GET /messages/msg-xyz                   │
  │─────────────────────────────────────────►│  ← poll
  │  { "status": "processing" }              │
  │◄─────────────────────────────────────────│
  │                                          │
  │  GET /messages/msg-xyz                   │
  │─────────────────────────────────────────►│  ← poll again
  │  { "status": "completed",                │
  │    "payload": { "report": "..." } }      │
  │◄─────────────────────────────────────────│
```

#### Streaming via SSE

For real-time updates, ACP uses SSE on a streaming endpoint:

```
POST /stream/send HTTP/1.1
Content-Type: application/json

{ "action": "live_monitoring", "sessionId": "sess-42" }

HTTP/1.1 200 OK
Location: /stream/stream-001/events

─────────────────────────────────────────

GET /stream/stream-001/events HTTP/1.1
Accept: text/event-stream

HTTP/1.1 200 OK
Content-Type: text/event-stream

event: progress
data: {"status":"scanning","detail":"Checking file 1/10..."}

event: progress
data: {"status":"scanning","detail":"Checking file 5/10..."}

event: result
data: {"status":"completed","payload":{"issues":3}}
```

#### ACP vs A2A: Wire-Level Contrast

```
Same task, different wire formats:

ACP (REST):
  POST /messages/send
  Content-Type: application/json
  { "action": "review_pr", "input": {"pr": 42} }

A2A (JSON-RPC):
  POST /rpc
  Content-Type: application/json
  { "jsonrpc":"2.0", "id":"t1", "method":"sendMessage",
    "params":{"message":{"parts":[{"text":"Review PR #42"}]}} }

ACP uses HTTP status codes for errors (400, 404, 500).
A2A uses JSON-RPC error objects inside 200 responses.
```

### ACP vs A2A

These two protocols target the same problem space with different philosophies:

```
┌──────────────────────────────────────────────────────────────┐
│                ACP vs A2A                                     │
│                                                               │
│  ACP (IBM)                        A2A (Google)                │
│  ──────────                       ──────────                  │
│  REST-native (HTTP verbs)         JSON-RPC over HTTP          │
│  curl-friendly                    SDK-friendly                │
│  Session-first design             Task-first design           │
│  Signed capability tokens         Standard OAuth/Bearer       │
│  OpenTelemetry built-in           Extensible auth framework   │
│  BeeAI reference impl             Vertex AI reference impl    │
│                                                               │
│  Converging: Both under Linux Foundation governance           │
│  In practice: ACP concepts flowing into A2A specification     │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

---

## AG-UI — Agent-User Interaction Protocol

> **One-liner**: An event-based protocol standardizing how AI agents connect to frontend applications — the missing link between agent backends and user interfaces.

### What It Solves

MCP handles agent-to-tool. A2A handles agent-to-agent. But how does the agent talk to the **user's screen**? AG-UI provides:

- **Streaming** — Token-by-token output for real-time UX
- **Shared state** — Synchronized data between agent and UI
- **Human-in-the-loop** — Approval gates, cancellations, mid-flow interventions
- **Generative UI** — Agents can produce UI components, not just text

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   AG-UI ARCHITECTURE                              │
│                                                                  │
│  ┌──────────────────┐  Events (SSE)  ┌───────────────────────┐  │
│  │  Frontend App    │◄──────────────│  Agent Backend         │  │
│  │  (React, Vue,    │               │  (any framework)       │  │
│  │   mobile, etc.)  │──────────────►│                        │  │
│  └──────────────────┘  User Input   └───────────────────────┘  │
│                                                                  │
│  Event types:                                                    │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  TEXT_MESSAGE_START → TEXT_MESSAGE_CONTENT → ...END        │  │
│  │  TOOL_CALL_START → TOOL_CALL_ARGS → TOOL_CALL_END         │  │
│  │  STATE_DELTA (shared state updates)                        │  │
│  │  STATE_SNAPSHOT (full state sync)                          │  │
│  │  INTERRUPT (agent asks user for input/approval)            │  │
│  │  CUSTOM (application-specific events)                      │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Key capabilities:                                               │
│  • Streaming text and tool calls token-by-token                  │
│  • Multimodal: text, images, files, audio                        │
│  • Shared typed state store with real-time diffs                 │
│  • Interrupts for approvals, cancellations, clarifications       │
│  • Tool handoffs between frontend and backend                    │
│  • Framework-agnostic (works with any agent backend)             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Event Flow Example

```
Agent Backend                                    Frontend App
     │                                                │
     │  TEXT_MESSAGE_START { messageId: "m1" }        │
     │───────────────────────────────────────────────►│
     │  TEXT_MESSAGE_CONTENT { delta: "Here is" }     │  User sees
     │───────────────────────────────────────────────►│  text appear
     │  TEXT_MESSAGE_CONTENT { delta: " my plan:" }   │  token by
     │───────────────────────────────────────────────►│  token
     │  TEXT_MESSAGE_END                              │
     │───────────────────────────────────────────────►│
     │                                                │
     │  INTERRUPT { type: "approval",                 │  Agent pauses,
     │    question: "Execute this plan?" }            │  user decides
     │───────────────────────────────────────────────►│
     │                                                │
     │◄───────────────────────────────────────────────│  User: "Yes"
     │                                                │
     │  TOOL_CALL_START { name: "run_tests" }        │  Agent resumes,
     │───────────────────────────────────────────────►│  UI shows
     │  TOOL_CALL_END { result: "All passed" }       │  tool activity
     │───────────────────────────────────────────────►│
```

### Under the Hood: AG-UI Networking

AG-UI uses **Server-Sent Events (SSE)** (see [Networking Primer](#networking-primer-building-blocks)) as its primary transport. The agent backend streams events to the frontend; the frontend sends user input back via regular HTTP POST.

#### SSE Wire Format

Every AG-UI event follows the standard SSE format — each is a small JSON object tagged with a type:

```
Raw bytes on the wire:

  event: TextMessageStart\n
  data: {"type":"TextMessageStart","messageId":"m1","role":"assistant"}\n
  id: evt-001\n
  \n

  event: TextMessageContent\n
  data: {"type":"TextMessageContent","messageId":"m1","delta":"Here is "}\n
  id: evt-002\n
  \n

  event: TextMessageContent\n
  data: {"type":"TextMessageContent","messageId":"m1","delta":"my analysis."}\n
  id: evt-003\n
  \n

  event: TextMessageEnd\n
  data: {"type":"TextMessageEnd","messageId":"m1"}\n
  id: evt-004\n
  \n

Each event:
• event: line = event type (used by EventSource API for routing)
• data: line  = JSON payload (the actual content)
• id: line    = unique ID (enables reconnection via Last-Event-ID)
• blank line  = end of this event, start of next
```

#### The 17 Event Types

AG-UI defines a precise vocabulary of events, grouped by purpose:

```
┌─────────────────────────────────────────────────────────────────┐
│              AG-UI EVENT TYPES                                   │
│                                                                  │
│  LIFECYCLE (structure the run):                                  │
│  ├── RunStarted         Run begins, includes runId & threadId    │
│  ├── RunFinished        Run completed successfully               │
│  ├── RunError           Run failed with error details            │
│  ├── StepStarted        Optional: multi-step workflow marker     │
│  └── StepFinished       Optional: step completed                 │
│                                                                  │
│  TEXT (streaming content):                                       │
│  ├── TextMessageStart   New message begins (role, messageId)     │
│  ├── TextMessageContent Token/chunk of text (delta field)        │
│  └── TextMessageEnd     Message complete                         │
│                                                                  │
│  TOOL CALLS (agent uses tools):                                  │
│  ├── ToolCallStart      Agent begins a tool call (name, id)      │
│  ├── ToolCallArgs       Streams tool arguments incrementally     │
│  └── ToolCallEnd        Tool call finished (with result)         │
│                                                                  │
│  STATE (shared data):                                            │
│  ├── StateSnapshot      Full state object at this point          │
│  ├── StateDelta         Incremental state update (JSON Patch)    │
│  └── MessagesSnapshot   Full conversation transcript so far      │
│                                                                  │
│  SPECIAL:                                                        │
│  ├── Interrupt          Agent pauses for user input/approval     │
│  ├── Custom             Application-specific events              │
│  └── Raw                Unstructured/passthrough data            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### Full Wire Trace: Agent Analyzes Code

```
Agent Backend                                    Frontend (Browser)
     │                                                │
     │  ── SSE Stream Opens ──                        │
     │                                                │
     │  event: RunStarted                             │
     │  data: {"type":"RunStarted",                   │
     │         "runId":"run-42",                      │
     │         "threadId":"thread-7"}                 │
     │───────────────────────────────────────────────►│  UI: "Agent
     │                                                │   is running"
     │  event: TextMessageStart                       │
     │  data: {"type":"TextMessageStart",             │
     │         "messageId":"m1","role":"assistant"}   │
     │───────────────────────────────────────────────►│
     │                                                │
     │  event: TextMessageContent                     │
     │  data: {"delta":"I found a "}                  │  User sees
     │───────────────────────────────────────────────►│  text appear
     │  event: TextMessageContent                     │  token by
     │  data: {"delta":"potential SQL injection "}    │  token
     │───────────────────────────────────────────────►│
     │  event: TextMessageContent                     │
     │  data: {"delta":"in auth.py line 42."}         │
     │───────────────────────────────────────────────►│
     │                                                │
     │  event: TextMessageEnd                         │
     │  data: {"messageId":"m1"}                      │
     │───────────────────────────────────────────────►│
     │                                                │
     │  event: Interrupt                              │  UI shows
     │  data: {"type":"Interrupt",                    │  approval
     │         "interruptType":"approval",            │  dialog
     │         "question":"Apply the fix?"}           │
     │───────────────────────────────────────────────►│
     │                                                │
     │      ◄── User clicks "Yes" ──                  │
     │  (HTTP POST from frontend with user response)  │
     │◄───────────────────────────────────────────────│
     │                                                │
     │  event: ToolCallStart                          │
     │  data: {"name":"edit_file","toolCallId":"tc1"} │
     │───────────────────────────────────────────────►│  UI: "Editing
     │                                                │   auth.py..."
     │  event: ToolCallEnd                            │
     │  data: {"toolCallId":"tc1",                    │
     │         "result":"File updated successfully"}  │
     │───────────────────────────────────────────────►│
     │                                                │
     │  event: RunFinished                            │
     │  data: {"type":"RunFinished","runId":"run-42"} │
     │───────────────────────────────────────────────►│  UI: "Done ✓"
```

#### Bidirectional Communication

AG-UI's SSE stream is **server → client only**. The frontend sends user input back via a separate HTTP POST:

```
SSE stream:   Agent ──────────────► Frontend   (events)
HTTP POST:    Agent ◄──────────────  Frontend   (user input)

This is a standard pattern — the same way ChatGPT's web UI works.
The SSE connection stays open for the duration of the agent run.
User responses (to Interrupts) are POSTed to the agent's HTTP endpoint.
```

### Connection to Day 3 (ReAct)

AG-UI is essentially the **protocol version** of the human-in-the-loop pattern from Day 3. The ReAct loop's "Observe" step (where the user sees what happened) becomes a formal event stream. The "Approve" step (Cline's model) becomes an AG-UI `INTERRUPT`.

---

## ANP — Agent Network Protocol

> **One-liner**: The "TCP/IP for agents" — enabling autonomous agents to discover, authenticate, and collaborate across the open internet.

### What It Solves

All the protocols above assume agents already know how to **find** each other. But what about agents across different organizations, clouds, or even different countries? ANP addresses the **network layer** — how agents establish identity and trust before they start communicating.

### Three-Layer Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                   ANP THREE-LAYER DESIGN                         │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Layer 3: Application Protocol                             │  │
│  │  Domain-specific protocols for tasks, data, workflows      │  │
│  │  (This is where A2A, ACP, AG-UI operate)                   │  │
│  └─────────────────────────┬─────────────────────────────────┘  │
│                             │                                    │
│  ┌─────────────────────────▼─────────────────────────────────┐  │
│  │  Layer 2: Meta-Protocol Negotiation                        │  │
│  │  Dynamic capability negotiation between agents             │  │
│  │  "I speak A2A v1.2 and ACP v1.0 — what do you speak?"     │  │
│  └─────────────────────────┬─────────────────────────────────┘  │
│                             │                                    │
│  ┌─────────────────────────▼─────────────────────────────────┐  │
│  │  Layer 1: Identity & Encrypted Communication               │  │
│  │  DID-based identity (Decentralized Identifiers)            │  │
│  │  End-to-end encrypted channels                             │  │
│  │  No central authority required for trust                   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ANP sits underneath the other protocols, providing:             │
│  • Agent identity (who are you?)                                 │
│  • Discovery (where are you? what can you do?)                   │
│  • Trust (should I collaborate with you?)                        │
│  • Routing (how do I reach you?)                                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Key Concepts

| Concept | Description |
|---------|-------------|
| **DID (Decentralized Identifier)** | Self-sovereign identity — agents prove who they are without a central authority |
| **Meta-protocol negotiation** | Agents dynamically agree on which application protocol to use |
| **Composable modules** | Mix-and-match capabilities (payment, data exchange, task delegation) |
| **Edge + cloud** | Works for both cloud-hosted and edge-deployed agents |

### Under the Hood: ANP Networking

ANP operates at a **lower level** than the other protocols — it provides the identity and trust infrastructure that other protocols run on top of. Think of it as the "TLS + DNS" layer for agents.

#### Layer 1: Identity & Encrypted Communication

ANP uses **DID:WBA** (Decentralized Identifiers, Web-Based Agent) for agent identity. Here's what happens when two agents first connect:

```
Agent A                                         Agent B
  │                                                │
  │  1. Resolve DID document                       │
  │  GET https://agent-b.example/.well-known/did   │
  │─────────────────────────────────────────────►  │
  │                                                │
  │  { "id": "did:wba:agent-b.example",            │
  │    "authentication": [{                        │
  │      "type": "X25519KeyAgreement2020",         │
  │      "publicKeyMultibase": "z6MkpT..."         │
  │    }],                                         │
  │    "service": [{                               │
  │      "type": "AgentService",                   │
  │      "serviceEndpoint": "https://..."          │
  │    }]                                          │
  │  }                                             │
  │◄─────────────────────────────────────────────  │
  │                                                │
  │  2. Key exchange (X25519 ECDH)                 │
  │  Agent A's ephemeral public key ──────────────►│
  │◄────────────── Agent B's ephemeral public key  │
  │                                                │
  │  Both derive shared secret →                   │
  │  symmetric encryption key established          │
  │                                                │
  │  3. All subsequent messages are encrypted       │
  │  ┌──────────────────────────────────┐          │
  │  │ ENCRYPTED ENVELOPE:              │          │
  │  │ ┌────────────────────────────┐   │          │
  │  │ │ Header:                    │   │          │
  │  │ │   sender DID              │   │          │
  │  │ │   receiver DID            │   │          │
  │  │ │   protocol version        │   │          │
  │  │ │   nonce + timestamp       │   │          │
  │  │ ├────────────────────────────┤   │          │
  │  │ │ Auth Block:               │   │          │
  │  │ │   DID signature proof     │   │          │
  │  │ ├────────────────────────────┤   │          │
  │  │ │ Encrypted Payload:        │   │          │
  │  │ │   (application data)      │   │          │
  │  │ └────────────────────────────┘   │          │
  │  └──────────────────────────────────┘          │
  │─────────────────────────────────────────────►  │
```

**Key difference from OAuth/API keys**: No central authority is needed. Agent A verifies Agent B's identity by resolving its DID document — a self-published cryptographic proof. This is how agents from different organizations can trust each other without a shared identity provider.

#### Layer 2: Meta-Protocol Negotiation

Once a secure channel is established, agents negotiate which application protocol to use:

```
Agent A (encrypted channel)                    Agent B
  │                                               │
  │  "I support: A2A v1.2, ACP v1.0"             │
  │──────────────────────────────────────────────►│
  │                                               │
  │  "I support: A2A v1.2, custom-v3"            │
  │◄──────────────────────────────────────────────│
  │                                               │
  │  Agreement: use A2A v1.2                      │
  │  ═══════════════════════════════════════════  │
  │                                               │
  │  (now A2A JSON-RPC flows over the            │
  │   encrypted ANP channel)                      │
  │                                               │

This negotiation uses JSON-LD for describing capabilities:

{
  "@context": "https://agentnetworkprotocol.com/ns/v1",
  "type": "AgentDescription",
  "id": "did:wba:agent-b.example",
  "protocols": [
    { "type": "A2A", "version": "1.2",
      "endpoint": "/a2a/rpc" },
    { "type": "ACP", "version": "1.0",
      "endpoint": "/acp/messages" }
  ],
  "capabilities": ["code_review", "testing"]
}
```

#### Layer 3: Application Protocol

This is where A2A, ACP, AG-UI, or any custom protocol runs. ANP doesn't define this layer — it just provides the secure, authenticated channel underneath.

```
┌─────────────────────────────────────────────────────────────┐
│                 PROTOCOL LAYERING                            │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Application: A2A sendMessage / ACP /messages/send  │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │  Negotiation: JSON-LD capability exchange            │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │  Identity: DID:WBA + X25519 encryption              │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │  Transport: HTTPS                                    │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │  Network: TCP/IP                                     │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ANP provides layers 2 and 3 from the bottom.               │
│  It does NOT replace TCP/IP or HTTPS — it builds on them.   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Maturity

ANP is the **newest and least mature** of the protocols covered here. It's best understood as a forward-looking vision for the "Agentic Web" — a future internet where agents are first-class citizens alongside humans and applications. Most production systems today don't need ANP yet, but the concepts (identity, discovery, trust) are worth understanding.

---

## Protocol Comparison

### Feature Matrix

```
┌───────────────────────────────────────────────────────────────────┐
│                  PROTOCOL COMPARISON MATRIX                        │
│                                                                    │
│  Feature              MCP    A2A    ACP    AG-UI   ANP            │
│  ─────────────────    ────   ────   ────   ─────   ────           │
│  Agent ↔ Tool          ✓      ✗      ✗      ✗      ✗             │
│  Agent ↔ Agent         ✗      ✓      ✓      ✗      ✗             │
│  Agent ↔ User/UI       ✗      ✗      ✗      ✓      ✗             │
│  Agent ↔ Internet      ✗      ✗      ✗      ✗      ✓             │
│                                                                    │
│  Streaming             △      ✓      ✓      ✓      ✗             │
│  Async tasks           △      ✓      ✓      ✓      ✓             │
│  Discovery             ✗      ✓      ✓      ✗      ✓             │
│  Session management    △      ✓      ✓      ✓      ✗             │
│  Identity/trust        ✗      △      △      ✗      ✓             │
│  Human-in-the-loop     ✗      ✓      ✗      ✓      ✗             │
│                                                                    │
│  ✓ = strong   △ = partial   ✗ = not covered                       │
│                                                                    │
│  Transport:                                                        │
│  MCP  : JSON-RPC / STDIO / HTTP+SSE                               │
│  A2A  : JSON-RPC / HTTP+SSE / gRPC (optional)                     │
│  ACP  : REST (HTTP+JSON) / SSE                                     │
│  AG-UI: HTTP+SSE event streams                                     │
│  ANP  : HTTP + DID authentication                                  │
│                                                                    │
│  Maturity (2026):                                                  │
│  MCP   ████████████████████  Very mature, massive ecosystem        │
│  A2A   ████████████████      Mature, 100+ partners                 │
│  ACP   ████████████          Established, merging into A2A         │
│  AG-UI ████████              Growing, adopted by CopilotKit/MSFT   │
│  ANP   ████                  Early stage, visionary                 │
│                                                                    │
└───────────────────────────────────────────────────────────────────┘
```

### When to Use Each

| Scenario | Protocol |
|----------|----------|
| Your agent needs to call a database, API, or CLI tool | **MCP** |
| Two agents from different frameworks need to collaborate on a task | **A2A** |
| You need REST-native agent messaging with strong session management | **ACP** |
| Your agent needs to stream output to a React/mobile frontend | **AG-UI** |
| Agents from different organizations must discover and trust each other | **ANP** |
| A coding agent needs to use GitHub, Jira, and Slack tools | **MCP** |
| A planner agent delegates subtasks to specialist agents | **A2A** |
| You're building a chat UI that shows live agent reasoning | **AG-UI** |

---

## Choosing the Right Protocol

Most systems don't need all five protocols. Here's a decision guide:

```
Start here: What are you building?

  "An agent that uses tools"
  └──► MCP (start here, it's the most mature)

  "Multiple agents collaborating"
  └──► A2A (industry standard)
       └──► Consider ACP if you need REST-native or heavy session management

  "An agent-powered UI/chat"
  └──► AG-UI + MCP
       (AG-UI for the frontend connection, MCP for backend tools)

  "A production multi-agent platform"
  └──► MCP + A2A + AG-UI
       (full stack: tools + collaboration + user interaction)

  "Cross-organization agent network"
  └──► All of the above + ANP for discovery and trust
       (this is the future, not common yet)
```

### Typical Production Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│             TYPICAL 2026 PRODUCTION STACK                        │
│                                                                  │
│  ┌──────────────────────────────────────────────────┐           │
│  │  User Interface (React/Mobile)                    │           │
│  └──────────────────────┬───────────────────────────┘           │
│                         │ AG-UI (streaming events)               │
│  ┌──────────────────────▼───────────────────────────┐           │
│  │  Orchestrator Agent                               │           │
│  │  • Receives user request                          │           │
│  │  • Decomposes into subtasks                       │           │
│  │  • Routes to specialist agents                    │           │
│  └───────┬──────────────────────────────┬───────────┘           │
│          │ A2A (delegate tasks)          │ A2A                   │
│  ┌───────▼───────┐              ┌───────▼───────┐              │
│  │ Research Agent │              │ Coding Agent  │              │
│  └───────┬───────┘              └───────┬───────┘              │
│          │ MCP                           │ MCP                  │
│  ┌───────▼───────┐              ┌───────▼───────┐              │
│  │ Web Search    │              │ GitHub API    │              │
│  │ MCP Server    │              │ MCP Server    │              │
│  └───────────────┘              └───────────────┘              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## How They Compose Together

The protocols are designed to be **complementary, not competing**. Here's how they layer:

```
 Layer     Protocol     Analogy
 ─────     ────────     ───────
 Network   ANP          DNS + TLS (find and trust agents)
 Agent     A2A / ACP    HTTP (agents talk to agents)
 Tool      MCP          System calls (agents use tools)
 UI        AG-UI        Web sockets (agents talk to users)
```

### Concrete Example: "Fix this Jira ticket"

```
1. User types "Fix PROJ-123" in the chat UI

2. AG-UI streams the orchestrator's thinking to the frontend:
   "I'll look up the ticket, plan a fix, and submit a PR"

3. Orchestrator uses MCP to call the Jira MCP server:
   tools/call → get_issue("PROJ-123") → returns ticket details

4. Orchestrator uses A2A to delegate to a Research Agent:
   sendMessage → "Find relevant code for this ticket"
   Research Agent uses MCP internally (GitHub search, codebase tools)
   Returns artifacts: relevant file paths and context

5. Orchestrator uses A2A to delegate to a Coding Agent:
   sendMessage → "Fix the bug described in PROJ-123"
   Coding Agent uses MCP internally (file read/write, test runner)
   Returns artifacts: code diff

6. AG-UI streams results back to user with an INTERRUPT:
   "Here's my proposed fix. Approve?"

7. User approves → Orchestrator uses MCP to create a PR via GitHub API
```

---

## Interview Angle

If asked about agent protocols in an interview, emphasize these points:

**Key insight**: "Agent protocols solve the interoperability problem at different layers — MCP for tools, A2A/ACP for agent collaboration, AG-UI for user interaction, and ANP for internet-scale discovery."

**Common questions and answers**:

| Question | Strong Answer |
|----------|---------------|
| "What is MCP?" | "A JSON-RPC protocol that standardizes how agents connect to external tools — like USB-C for AI. Write a tool once, any MCP client can use it." |
| "How do agents from different frameworks collaborate?" | "A2A defines Agent Cards for discovery and a task lifecycle for delegation. Agent A discovers Agent B's capabilities, sends a task, and streams results — all without sharing internal state." |
| "What's the difference between A2A and ACP?" | "Same problem space, different philosophy. A2A uses JSON-RPC and is task-oriented. ACP is REST-native and session-oriented. They're converging under the Linux Foundation." |
| "How do you handle the frontend in an agent system?" | "AG-UI provides event-based streaming between agent backends and UIs — text tokens, tool calls, state updates, and human-in-the-loop interrupts, all over SSE." |
| "What's the future of agent interoperability?" | "The stack is stabilizing: MCP for tools, A2A for agent-agent, AG-UI for UX, and eventually ANP for cross-org discovery. These complement each other — a production system uses multiple protocols at different layers." |

---

## Further Reading

- **MCP**: [modelcontextprotocol.io](https://modelcontextprotocol.io) — Official spec and SDKs
- **A2A**: [a2a-protocol.org](https://a2a-protocol.org) — Google's Agent-to-Agent protocol spec
- **ACP**: [agentcommunicationprotocol.dev](https://agentcommunicationprotocol.dev) — IBM's Agent Communication Protocol
- **AG-UI**: [docs.ag-ui.com](https://docs.ag-ui.com) — Agent-User Interaction Protocol
- **ANP**: [arxiv.org/abs/2508.00007](https://arxiv.org/abs/2508.00007) — Agent Network Protocol white paper
- **Survey**: [arxiv.org/abs/2505.02279](https://arxiv.org/abs/2505.02279) — "A Survey of Agent Interoperability Protocols"
- **Real-world MCP implementations**: See the [Real-World Agents appendix](appendix-real-world-agents.md) in this syllabus
