# Shared Grocery List

A real-time collaborative shopping list app. Log in with email, create a list with a custom code, invite others by sharing that code. Everyone with the code has full access. Changes appear instantly.

**Stack:** kit-clj (Clojure) · PostgreSQL · HTMX · Tailwind CSS

---

## Quickstart (once code exists)

```bash
# 1. Start the kit-clj REPL and run migrations

# 2. Start the server
clojure -M:dev

# 3. Open http://localhost:3000
```

---

## Governing Document

**Read `AGENTS.md` first.** It is binding on every agent. It defines:

- The mandatory workflow: PRD → Quint spec → proof → *then* code.
- When an ADR is required and when it is not.
- Bidirectional linkage between every PRD and its Quint spec.
- Escalation rules.

---

## Project Structure

```
.
├── AGENTS.md              # ← Bindings for all agents
├── docs/
│   ├── adr/               # Architecture Decision Records (MADR format)
│   ├── prd/               # Product Requirements Documents
│   ├── specs/             # Quint state-machine specifications
│   └── templates/         # Templates for ADR, PRD, Quint spec
└── src/                   # Source code (only after spec proven)
```

---

## How Decisions Are Made

1. **Architectural decisions** → ADR in `docs/adr/` following MADR template.
2. **Features** → PRD in `docs/prd/` + Quint spec in `docs/specs/`.
3. **1:1 parity** between PRD requirements and Quint state variables/actions/invariants.
4. **All Quint invariants must be proven** (`typecheck` → `test` → `run` → `verify`) before implementation.
5. **Bidirectional linking** — every PRD links its spec; every spec links its PRD. Missing linkage blocks work.

---

## Status

**Pre-alpha — documentation phase.**

No code written yet. All features flow through:

```
Feature idea → ADR (if architecturally significant)
           → PRD → Quint spec → prove invariants
           → Implementation
```
