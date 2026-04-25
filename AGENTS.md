# AGENTS.md

> **This document is binding.** Every agent working on this project must read it completely before contributing code, documentation, or decisions. If a rule conflicts with an instruction given in a task, escalate immediately — do not guess.

---

## 1. Project Overview

This is a real-time shared grocery & shopping list application.

- **Stack:** kit-clj (Clojure), PostgreSQL, HTMX, Tailwind CSS.
- **Users log in with email** (password or magic link), choose a display name, create or join a shared list by typing a custom code.
- **Collaborative:** Anyone with the list code has full, flat access — add items, tick them off, complete the list.
- **Real-time:** Changes appear instantly for all viewers currently on the same list.
- **Items have three fields:** name, quantity, observations.
- **Checked-off items** are grayed out and pushed to the bottom.
- **Completed lists** move to a "past lists" archive and can be copied as a starting point for a new list.

---

## 2. Mandatory Workflow — Read This Section First

### 2.1 The Golden Rule

> **NO CODE MAY BE WRITTEN until the corresponding PRD and Quint specification have been created, proven, and linked.**

This is the only workflow:

```
1. Feature idea / requirement
2. Write PRD → docs/prd/NNNN-feature-name.md
3. Write Quint spec → docs/specs/NNNN-feature-name.md
4. Prove invariants via quint typecheck + run + verify
5. Only then: open an ADR if architecturally significant
6. Only then: implement code
```

### 2.2 Architectural Decision Records (ADRs)

**When an ADR is required:**
- Any decision that affects system structure, dependencies, data model, deployment, or non-functional qualities.
- Technology choices (database, framework, library, protocol).
- Schema or API design decisions.
- Any decision that would make a future developer ask: *"Why did they do it this way?"*

**When an ADR is NOT required:**
- Implementation details inside a module that don't affect other components.
- Refactoring that changes nothing architecturally.
- Bug fixes that don't change design intent.

**Process:**
1. Copy `docs/templates/adr-template.md` to `docs/adr/NNNN-title-with-dashes.md`.
2. Fill out the full MADR template: Context, Decision Drivers, Considered Options, Decision Outcome, Consequences, Confirmation, Pros/Cons.
3. Status starts as `proposed`. Transition to `accepted` only after stakeholder review.
4. If superseded, mark `superseded by ADR-XXXX` and do not delete.

**Naming:** Sequential 4-digit numbers (0001, 0002...) — never reuse numbers.

### 2.3 Product Requirements Documents (PRDs)

**Every feature gets a PRD.**

- File: `docs/prd/NNNN-feature-name.md`
- Sequentially numbered, 4 digits, matching the Quint spec.
- Must follow `docs/templates/prd-template.md`.
- Must be complete enough that a different agent could implement the feature from it without asking questions.
- **Must contain a `Linked Quint Spec` section that references `docs/specs/NNNN-feature-name.qnt` by exact path.**
- Every PRD must have exactly one linked Quint spec. No PRD without a spec. No spec without a PRD.

**Contents must include:**
- Feature name and problem statement
- Goals and non-goals
- User stories
- Detailed requirements (functional, data, UI/UX)
- Out of scope
- Acceptance criteria (testable, unambiguous)
- Open questions

**1:1 parity rule:** Every requirement in the PRD must have a corresponding Quint state variable, action, or invariant. Every Quint action must trace back to a PRD requirement. The PRD links the spec; the spec links the PRD. Missing linkage in either direction blocks implementation.

### 2.4 Quint Specifications (`docs/specs/`)

**Every feature gets a Quint state-machine specification.**

- File: `docs/specs/NNNN-feature-name.md` (or `.qnt` for the spec itself)
- Sequentially numbered, matching the PRD.
- Must follow `docs/templates/quint-spec-template.md`.
- Must be runnable with `quint typecheck`, `quint run`, and `quint verify`.
- **Must contain a `Linked PRD` section that references `docs/prd/NNNN-feature-name.md` by exact path.**
- Every spec must have exactly one linked PRD. No orphan PRDs. No orphan specs.

**Before any implementation:**

```bash
quint typecheck docs/specs/NNNN-feature-name.qnt
quint test       docs/specs/NNNN-feature-name.qnt
quint run        docs/specs/NNNN-feature-name.qnt --invariant=inv --max-samples=100
quint verify     docs/specs/NNNN-feature-name.qnt --invariant=inv --max-steps=10
```

All invariants must **pass** before the feature is cleared for implementation.

**State variables must represent the PRD's domain:**
- If the PRD says "a list has items," the spec has `var items: Set[Item]`.
- If the PRD says "items can be checked off," the spec has an action `checkItem` and invariants about checked/unchecked state.

**Agents must delegate Quint syntax questions to `/home/kschltz/.pi/agent/skills/quint/SKILL.md` and its knowledge base. Never guess Quint syntax.**

---

## 3. What This Means Day to Day

### When Requirements Are Ambiguous

**STOP.** Do not proceed. Do not guess. Do not make assumptions.

1. Write down your understanding and the ambiguity.
2. Ask the user for clarification.
3. Update the PRD with the answer before continuing.

### When You Discover a New Architectural Decision Mid-Feature

**STOP.** Do not bake the decision into code.

1. Draft an ADR under `docs/adr/` as `proposed`.
2. If it changes the features already agreed upon, update or create a new PRD + Quint spec.
3. Only after the ADR is accepted and the specs are proven may you implement.

### When the PRD and Quint Spec Conflict

**The Quint spec wins.** If the model checker finds a state the PRD says shouldn't exist, the PRD is wrong. Update it.

### When You Want to Add a "Nice-to-Have"

Not in the PRD? Out of scope. Write a new PRD + Quint spec, get user confirmation, then implement.

---

## 4. Directory Structure

```
.
├── AGENTS.md              # ← You are here. Governing document.
├── README.md              # Project overview, quickstart
├── AGENTS.md              # ← Governing document for all agents
├── docs/
│   ├── adr/               # Architecture Decision Records (MADR format)
│   │   ├── 0001-use-postgres.md
│   │   └── 0002-use-htmx-over-react.md
│   ├── prd/               # Product Requirements Documents
│   │   ├── 0001-create-shared-list.md
│   │   ├── 0002-join-list-by-code.md
│   │   └── 0003-realtime-item-sync.md
│   ├── specs/             # Quint state-machine specifications
│   │   ├── 0001-create-shared-list.md / .qnt
│   │   ├── 0002-join-list-by-code.md / .qnt
│   │   └── 0003-realtime-item-sync.md / .qnt
│   └── templates/           # Templates for ADR, PRD, Quint spec
│       ├── adr-template.md
│       ├── prd-template.md
│       └── quint-spec-template.md
├── src/                   # Source code (ONLY written after spec proven)
└── ...
```

---

## 5. Escalation Rules

Escalate to the user (do not proceed) when:

1. **PRD and Quint spec requirements conflict.** The user decides which model is correct.
2. **An ADR would overturn a previously accepted ADR.** Superseding decisions need explicit confirmation.
3. **A feature cannot be expressed in Quint (too complex, external dependencies).** The user decides whether to simplify or accept the risk.
4. **Quint invariants cannot be proven within reasonable bounds.** If `verify` times out or exhausts memory on a small bound, the model may be too complex — escalate.
5. **A task asks you to skip the spec-verify step.** This is forbidden. Escalate.

---

## 6. Agent Discipline

- **One feature at a time.** A PRD + Quint spec pair is for exactly one feature.
- **Show your work.** After proving Quint invariants, paste the passing output into the spec file as a comment block.
- **No hidden decisions.** If you make a choice, it is documented in an ADR or justified in the PRD.
- **Keep it discoverable.** Future agents must be able to read the ADR + PRD + Quint spec and understand what was built and why.

---

## 7. Templates Reference

| Document | Template | Output Directory |
|----------|----------|-------------------|
| ADR | `docs/templates/adr-template.md` | `docs/adr/NNNN-title.md` |
| PRD | `docs/templates/prd-template.md` | `docs/prd/NNNN-feature.md` |
| Quint Spec | `docs/templates/quint-spec-template.md` | `docs/specs/NNNN-feature.md` |

---

*End of AGENTS.md. Nothing below this line is part of the governing rules.*
