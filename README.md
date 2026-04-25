# Comprineas

A real-time shared grocery & shopping list application. Collaborate with family and friends: create a list, share the code, and see changes instantly.

---

## In a Nutshell

| Question | Answer |
|----------|--------|
| **What?** | Shared grocery/shopping lists — not a shopping cart with payments |
| **For whom?** | Families, roommates, event planners — anyone who needs a "let's all add to the same list" workflow |
| **How?** | Log in with email (password or magic link), pick a display name. Create a list or join one by typing its code. Anyone with the code has full, flat access. |
| **Real-time?** | Yes. SSE via HTMX — changes appear instantly for everyone viewing the same list. |
| **Archive?** | Completed lists move to a "past lists" view. You can copy them to start a new list. |

---

## Core Features (Planned)

```
┌─────────────────────────────────────────────────────────────┐
│  AUTH: Log in with email                                    │
│  ├─ Password-based (bcrypt, session cookies)              │
│  └─ Magic link (HMAC-signed, 15-min expiry)               │
├─────────────────────────────────────────────────────────────┤
│  LIST: Create a shared list                                 │
│  ├─ Generates a human-readable code                         │
│  └─ Code is the join key — flat access, no ownership        │
├─────────────────────────────────────────────────────────────┤
│  LIST: Join by code                                         │
│  └─ Anyone with the code has full access                    │
├─────────────────────────────────────────────────────────────┤
│  ITEM: Add / check / delete                                 │
│  ├─ Name, quantity ("1 kg", "a few"), observations          │
│  ├─ Checked items grayed out, pushed to bottom            │
│  └─ Instant SSE sync to all viewers                         │
├─────────────────────────────────────────────────────────────┤
│  LIST: Complete / archive                                   │
│  ├─ "Complete" manually — copies to `completed_lists`     │
│  ├─ Past lists viewable and copyable                        │
│  └─ Original `list_items` rows retained for live queries    │
├─────────────────────────────────────────────────────────────┤
│  "My Lists" dashboard                                       │
│  └─ Lists I created / joined, active and past               │
└─────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| **Backend** | [kit-clj](https://kit-clj.github.io/) (Clojure) | Ring/Integrant server-rendered; ADR-0001 |
| **Database** | PostgreSQL | ACID, MVCC, full-text search `jsonb`; ADR-0002 |
| **UI Interactivity** | HTMX | HTML-over-the-wire, native SSE support; ADR-0004 |
| **Styling** | Tailwind CSS (PostCSS build) | Utility-first, dev/prod parity; ADR-0005 |
| **Real-time** | SSE over HTTP (http-kit async) | Unidirectional push, auto-reconnect; ADR-0006 |
| **Auth** | Session cookies + bcrypt + magic link | Dual login, easy revocation; ADR-0007 |
| **Sessions** | PostgreSQL (JDBC store) | Dev/prod parity, one DB; ADR-0007 |
| **Templates** | Selmer (Clojure) | Server-rendered, HTMX-compatible |

---

## Development Workflow

This project follows a **specification-first** discipline. **No code is written before its PRD and Quint specification are created, proven, and linked.**

```
┌───────────────────────────────────────────────┐
│ 1. Feature idea → Document in PRD              │
│    docs/prd/NNNN-feature-name.md              │
├───────────────────────────────────────────────┤
│ 2. Formalize in Quint                          │
│    docs/specs/NNNN-feature-name.qnt             │
├───────────────────────────────────────────────┤
│ 3. Prove invariants                             │
│    quint typecheck → test → run → verify      │
├───────────────────────────────────────────────┤
│ 4. Implement (ONLY after proof passes)       │
│    src/...                                      │
└───────────────────────────────────────────────┘
```

---

## Project Structure

```
.
├── README.md                 # ← You are here
├── AGENTS.md               # Binding rules for all agents
├── docs/
│   ├── adr/               # Architecture Decision Records (MADR)
│   │   ├── 0001-use-kit-clj.md
│   │   ├── 0002-use-postgresql.md
│   │   ├── 0003-data-model-and-schema-design.md
│   │   ├── 0004-use-htmx-for-ui-interactivity.md
│   │   ├── 0005-use-tailwind-css-for-styling.md
│   │   ├── 0006-real-time-delivery-strategy.md
│   │   └── 0007-authentication-strategy.md
│   ├── prd/               # Product Requirements Documents (PRD)
│   │   # (empty — features not yet specified)
│   ├── specs/             # Quint state-machine specifications
│   │   # (empty — specs not yet written)
│   └── templates/         # Templates for ADR, PRD, Quint spec
├── src/                   # Source code (ONLY after spec proven)
└── ...
```

---

## ADR Reference (accepted)

| # | Decision | Key Points |
|---|----------|------------|
| 0001 | **kit-clj** as web framework | Integrant/Ring, Selmer templates, middleware pipeline |
| 0002 | **PostgreSQL** as primary DB | ACID, MVCC, `jsonb` escape hatch; managed or self-hosted |
| 0003 | **Data model & schema** | Flat access (no ownership), LWW item mutations, optimistic locking for list metadata |
| 0004 | **HTMX** for UI | SSE extension, async Ring required, multi-target via OOB-swap |
| 0005 | **Tailwind CSS** for styling | PostCSS in all envs, DaisyUI excluded, Selmer macros for verbosity |
| 0006 | **SSE** for real-time | http-kit `with-channel`, auto-reconnect, HTTP/2 recommended for tab limits |
| 0007 | **Session auth** (password + magic link) | PostgreSQL sessions, bcrypt 10+, rate limiter, HMAC magic tokens |

---

## Current Status

**Phase: Architecture complete. Feature specification pending.**

- ✅ All foundational ADRs accepted (7/7)
- ⏳ PRDs to write: "Create shared list" (PRD-0001), "Join list by code", "Add/check item", "Complete list", etc.
- ⏳ Quint specs to prove: one per PRD, `typecheck → test → run → verify`
- ⏳ Code: none yet — gated by PRD + Quint + proof

---

## Getting Involved

Every contributor must read `AGENTS.md` first. It contains:
- The **golden rule** (PRD → Quint → proof → code)
- ADR, PRD, and Quint spec templates
- Escalation rules
- Agent discipline requirements

---

*Comprineas — "let's buy together"*
