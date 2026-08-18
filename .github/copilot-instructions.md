# Copilot Instructions

## Architecture

This repository has two main parts:

1. **Algorithm study guides** (`guides/algorithms/`, `guides/microsoft-system-design/`, `guides/ai-agents/`) — Markdown reference materials for interview prep. Each algorithm guide follows a consistent structure: theory/concepts first, then a "Common Problem Patterns" section with solutions. ASCII art is used for all diagrams (no external images).

2. **Java implementations** (`src/main/java/`, `src/test/java/`) — Algorithm implementations in Java 8, built with Maven. The `strings/` package contains string-matching algorithms (KMP, Morris-Pratt, Aho-Corasick) with a shared test base class `StringMatchingAlgorithmTest`. The `algos/` package contains data structure implementations like UnionFind.

Root-level compiled guides (`Algorithm_Theory_Guide.md`, `Algorithm_Problems_Guide.md`) are **generated** by `algorithm_tools.py split` — do not edit them directly.

## Build & Test

```bash
# Build
mvn compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=KMPSearchTest

# Run a single test method
mvn test -Dtest=KMPSearchTest#kmpTableTestEmptyPattern
```

## Guide Tooling

`algorithm_tools.py` is a Python CLI that manages the guides:

```bash
python algorithm_tools.py split          # Regenerate theory/problems compilations
python algorithm_tools.py pdf-theory     # Generate theory PDF (requires Node.js + md-to-pdf)
python algorithm_tools.py pdf-all        # Generate all PDFs
python algorithm_tools.py images         # Generate PNG from Mermaid diagrams
```

PDF generation uses `md-to-pdf` (Node.js) with styling configured in `pdf_config.js`.

## Conventions

- **Java source level**: Java 8 (no lambdas beyond what Java 8 supports).
- **Logging**: Use Log4j 2 (`LogManager.getLogger()`), not `System.out`.
- **Test pattern**: String-matching algorithm tests extend `StringMatchingAlgorithmTest`, which provides shared test cases. Subclasses override `invoke(text, pattern)` to plug in their algorithm.
- **Guide diagrams**: Use ASCII art inside fenced code blocks. Do not add image dependencies or `![Diagram](...)` links. Guides must be self-contained Markdown.
- **Guide structure**: Individual guides live in `guides/algorithms/XX_Name.md`. The split between theory and problems happens at the `## Common Problem Patterns` heading.
