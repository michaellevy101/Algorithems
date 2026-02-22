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
| [Day 1](01-llm-foundations.md) | Modern LLM Foundations | Transformers, tokenization, RLHF, inference | Running local model + performance notes |
| [Day 2](02-prompt-engineering.md) | Advanced Prompt Engineering | CoT, structured output, function calling | Reusable prompt library with validators |
| [Day 3](03-single-agent-react.md) | Single-Agent Systems (ReAct) | ReAct loop, tools, memory types | Functioning ReAct agent with tools |
| [Day 4](04-planner-executor.md) | Planner/Executor Architecture | Task decomposition, critic loops, guardrails | Planner/Executor system with Critic |
| [Day 5](05-rag-vector-memory.md) | RAG + Vector Memory | Embeddings, chunking, hybrid search | Agent with RAG + dynamic memory |
| [Day 6](06-multi-agent-collaboration.md) | Multi-Agent Collaboration | Orchestration, agent roles, deadlocks | 3-agent system solving real tasks |
| [Day 7](07-final-project-copilot.md) | Final Project: Build Your Copilot | Full system integration | Production-quality agentic system |

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
```

---

**Let's begin → [Day 1: Modern LLM Foundations](01-llm-foundations.md)**
