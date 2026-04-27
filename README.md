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

## Core Features

| # | Feature | Status |
|---|---------|--------|
| 0001 | Magic Link Auth | ✅ Implemented |
| 0002 | Password Auth | ✅ Implemented |
| 0003 | Create Shared List | ✅ Implemented |
| 0004 | Join List by Code | ✅ Implemented |
| 0005 | Add / Check / Delete Items | ✅ Implemented |
| 0006 | Complete & Copy Lists | ✅ Implemented |
| 0007 | My Lists Dashboard | 📝 PRD drafted |

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
| **E2E Testing** | Playwright (JavaScript) | Multi-browser, multi-context SSE testing; ADR-0008 |
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
├── AGENTS.md                 # Binding rules for all agents
├── deps.edn                  # Clojure dependencies
├── resources/
│   ├── config.edn            # Aero application config
│   ├── migrations/
│   │   ├── 001-init-auth.sql
│   │   ├── 002-password-auth.sql
│   │   └── 003-shared-lists.sql
│   ├── system.edn            # Integrant system config
│   └── templates/
│       ├── auth/
│       │   ├── display-name.html
│       │   ├── email-sent.html
│       │   ├── error.html
│       │   ├── forgot-password.html
│       │   ├── forgot-password-sent.html
│       │   ├── login.html
│       │   ├── register.html
│       │   ├── reset-password.html
│       │   └── set-password.html
│       ├── items/
│       │   ├── item-list.html
│       │   └── item-row.html
│       └── lists/
│           ├── copy-modal.html
│           └── join-form.html
├── docs/
│   ├── adr/                  # Architecture Decision Records (MADR)
│   │   ├── 0001-use-kit-clj.md
│   │   ├── 0002-use-postgresql.md
│   │   ├── 0003-data-model-and-schema-design.md
│   │   ├── 0004-use-htmx-for-ui-interactivity.md
│   │   ├── 0005-use-tailwind-css-for-styling.md
│   │   ├── 0006-real-time-delivery-strategy.md
│   │   └── 0007-authentication-strategy.md
│   ├── prd/                  # Product Requirements Documents (PRD)
│   │   ├── 0001-magic-link-auth.md
│   │   ├── 0002-password-authentication.md
│   │   ├── 0003-shared-lists.md
│   │   ├── 0004-join-list-by-code.md
│   │   ├── 0005-list-items.md
│   │   └── 0006-copy-completed-list.md
│   ├── specs/                # Quint state-machine specifications
│   │   ├── 0001-magic-link-auth.md
│   │   ├── 0001-magic-link-auth.qnt
│   │   ├── 0002-password-authentication.md
│   │   ├── 0002-password-authentication.qnt
│   │   ├── 0003-shared-lists.md
│   │   ├── 0003-shared-lists.qnt
│   │   ├── 0004-join-list-by-code.md
│   │   ├── 0004-join-list-by-code.qnt
│   │   ├── 0005-list-items.md
│   │   ├── 0005-list-items.qnt
│   │   ├── 0006-copy-completed-list.md
│   │   └── 0006-copy-completed-list.qnt
│   └── templates/            # Templates for ADR, PRD, Quint spec
├── src/                      # Source code
│   └── comprineas/
│       ├── config.clj
│       ├── core.clj
│       ├── db/
│       │   ├── core.clj
│       │   └── migrations.clj
│       ├── auth/
│       │   ├── cleanup.clj
│       │   ├── handlers.clj
│       │   ├── mailer.clj
│       │   ├── middleware.clj
│       │   ├── password.clj
│       │   ├── rate_limit.clj
│       │   ├── reset_tokens.clj
│       │   ├── sessions.clj
│       │   ├── tokens.clj
│       │   └── users.clj
│       ├── items/
│       │   ├── db.clj
│       │   └── handlers.clj
│       ├── lists/
│       │   ├── codes.clj
│       │   ├── copy.clj
│       │   ├── db.clj
│       │   ├── handlers.clj
│       │   ├── join.clj
│       │   └── sse.clj
│       └── web/
│           ├── routes.clj
│           └── server.clj
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
| 0008 | **Playwright** for e2e | 24 browser tests, embedded PG, multi-user SSE scenarios |

---

## Current Status

**Phase: Wire-up and Dashboard in progress — Features 0001–0006.**

- ✅ All foundational ADRs accepted (7/7)
- ✅ PRD-0001: Magic Link Authentication — accepted, spec proven, **code implemented**
- ✅ PRD-0002: Password Authentication — accepted, spec proven, **code implemented**
- ✅ PRD-0003: Shared Lists — accepted, spec proven, **code implemented**
- ✅ PRD-0004: Join List by Code — accepted, spec proven, **code implemented**
- ✅ PRD-0005: List Items — accepted, spec proven, **code implemented**
- ✅ PRD-0006: Copy Completed List — accepted, spec proven, **code implemented**
- 🚧 Route wiring: list/item routes being connected to handlers
- 🚧 Templates: list-view.html and dashboard.html being created
- ⏳ PRD-0007: My Lists Dashboard — writing phase
- ⏳ Spec-0007: My Lists Dashboard — pending PRD completion
- ⏳ Dashboard SSE handler — pending

---

## Getting Involved

Every contributor must read `AGENTS.md` first. It contains:
- The **golden rule** (PRD → Quint → proof → code)
- ADR, PRD, and Quint spec templates
- Escalation rules
- Agent discipline requirements

---

*Comprineas — "let's buy together"*
