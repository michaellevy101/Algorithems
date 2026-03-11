# AI & Agents — 1-Week Intensive Syllabus

> **LLMs + Agents: Beginner → Senior in 7 Days**

## 🎯 Weekly Goal

By the end of this week, you will build a fully working Agent system with planning, tools, memory (RAG), validation loops, and multi-agent orchestration — the foundation of a Senior-level understanding.

## Prerequisites

- Python 3.10+ installed
- Basic understanding of machine learning concepts (what a neural network is)
- Comfort with APIs (REST, JSON)
- `pip install openai tiktoken numpy chromadb` (installed as needed per day)

## Daily Schedule

Each day includes:
- **2–3h theory** — deep explanations with diagrams
- **1–3h hands-on coding** — runnable Python examples
- **A concrete deliverable** — something you built and can demonstrate

## 📅 Syllabus Overview

| Day | Topic | Key Concepts | Deliverable |
|-----|-------|-------------|-------------|
| [Day 1](01-llm-foundations.md) | Modern LLM Foundations | Transformers, tokenization, RLHF, inference | Tokenization explorer + sampling visualizer |
|  ↳ [Part A](01a-tokens-and-embeddings.md) | Tokens & Embeddings | BPE, subword tokenization, embedding space | |
|  ↳ [Part B](01b-transformer-and-attention.md) | Transformer & Attention | Q/K/V, multi-head attention, FFN, residuals | |
|  ↳ [Part C](01c-training-and-inference.md) | Training & Inference | Positional encoding, SFT, RLHF/DPO, sampling | |
|  ↳ [Appendix](01d-advanced-topics-appendix.md) | Advanced Techniques | MoE, GQA, LoRA, quantization, scaling laws | |
| [Day 2](02-prompt-engineering.md) | Advanced Prompt Engineering | CoT, structured output, function calling | Reusable prompt library with validators |
| [Day 3](03-single-agent-react.md) | Single-Agent Systems (ReAct) | ReAct loop, tools, memory types | Functioning ReAct agent with tools |
| [Day 4](04-planner-executor.md) | Planner/Executor Architecture | Task decomposition, critic loops, guardrails | Planner/Executor system with Critic |
| [Day 5](05-rag-vector-memory.md) | RAG + Vector Memory | Embeddings, chunking, hybrid search | Agent with RAG + dynamic memory |
| [Day 6](06-multi-agent-collaboration.md) | Multi-Agent Collaboration | Orchestration, agent roles, deadlocks | 3-agent system solving real tasks |
| [Day 7](07-final-project-copilot.md) | Final Project: Build Your Copilot | Full system integration | Production-quality agentic system |
| [Appendix](appendix-real-world-agents.md) | Real-World Agents: Cline & Roo Code | MCP, modes, memory, cost routing | Mental model of production agents |
| [Appendix](appendix-agent-protocols.md) | Agent Protocols: MCP, A2A, ACP & more | MCP, A2A, ACP, AG-UI, ANP | Protocol selection & composition |
| [Appendix](appendix-agent-authentication.md) | Agent Authentication & Identity | Managed Identity, Entra Agent ID, Foundry Blueprints, MCP auth | Identity proof per scenario |
| [Appendix](identity-security-guide.md) | Identity Security (CrowdStrike) | ITDR, FalconID, zero trust, attack landscape | Defense playbook & threat awareness |
| [Appendix](graphrag-guide.md) | GraphRAG & Book Summarization | Knowledge graphs, Leiden communities, iterative refinement, RAGAS | Iterative book summarizer pipeline |

## 🧭 How to Use This Guide

1. **Follow in order** — each day builds on the previous
2. **Type every code example** — don't just read, build muscle memory
3. **Complete the deliverable** before moving to the next day
4. **Experiment** — modify the examples, break things, understand why

## 🗺️ Learning Path

```
Day 1: LLM Foundations
    │
    ▼
Day 2: Prompt Engineering
    │
    ▼
Day 3: Single Agent (ReAct)     ← First agent!
    │
    ▼
Day 4: Planner/Executor         ← Multi-component agent
    │
    ▼
Day 5: RAG + Memory             ← Knowledge-grounded agent
    │
    ▼
Day 6: Multi-Agent              ← Agent collaboration
    │
    ▼
Day 7: Final Project            ← Senior-level system
    │
    ▼
Appendix: Cline & Roo Code     ← Production case studies
    │
    ▼
Appendix: Agent Protocols      ← MCP, A2A, ACP, AG-UI, ANP
    │
    ▼
Appendix: Agent Authentication ← Identity, Azure MI, Foundry, MCP auth
    │
    ▼
Appendix: Identity Security    ← CrowdStrike, ITDR, defense playbook
    │
    ▼
Appendix: GraphRAG             ← Knowledge graphs, book summarization
```

---

**Let's begin → [Day 1: Modern LLM Foundations](01-llm-foundations.md)**

**Or jump to → [Appendix: Real-World Agents (Cline & Roo Code)](appendix-real-world-agents.md)**

**Or jump to → [Appendix: Agent Protocols (MCP, A2A, ACP & more)](appendix-agent-protocols.md)**
