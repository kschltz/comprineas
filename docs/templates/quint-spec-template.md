# Quint Specification: {Feature Name}

| Spec ID | Feature | Status | Linked PRD | Author | Date |
|---------|---------|--------|------------|--------|------|
| {NNNN} | {One-line description} | draft | docs/prd/{NNNN}-{feature}.md | {agent/user} | {YYYY-MM-DD} |

---

## 1. Purpose

{What feature does this spec model? Reference the PRD requirement IDs (FR-N) it covers.}

## 2. Modeled Phenomena

{What real-world entities and behaviors does this spec capture?}

- {Entity/behavior 1}
- {Entity/behavior 2}
- …

## 3. State Variables

Map each PRD data requirement to a state variable:

```quint
module {feature-name} {
  // Constants
  // const N: int
  // const SET: Set[str]

  // State variables (must match PRD §4.2 Data Requirements)
  // var counter: int
  // var map: str -> int
  // var set: Set[int]
}
```

| PRD Req | State Variable | Type | Meaning |
|---------|----------------|------|---------|
| FR-N | {var name} | {type} | {meaning} |
| … | | | |

## 4. Actions

Map each PRD functional requirement to an action:

```quint
  // action init = all { a' = 0, b' = "" }
  // action step = any { action_a, action_b }
```

| PRD Req | Action | Description |
|---------|--------|-------------|
| FR-N | {action_name} | {what it does} |
| … | | |

## 5. Invariants

{Map PRD acceptance criteria and data constraints to invariants. Every invariant must be provable.}

```quint
  // val safe = x >= 0
```

| PRD AC | Invariant | Expression |
|--------|-----------|------------|
| AC-N | {name} | {expression} |
| … | | |

## 6. Properties

{Temporal properties if needed: always, eventually, etc.}

```quint
  // temporal live = always(eventually(done))
```

## 7. Full Spec

```quint
module {feature-name} {
  // const declarations

  // type declarations

  // state variable declarations

  // pure definitions

  // action definitions

  // helper actions

  // init and step

  // invariants

  // temporal properties

  // test runs
}
```

## 8. Verification Log

{Paste the output of `quint typecheck`, `quint test`, `quint run`, and `quint verify` here. Every command must pass before the feature is cleared for implementation.}

```
$ quint typecheck docs/specs/{NNNN}-{feature}.qnt
│ ✓ typecheck succeeded

$ quint test docs/specs/{NNNN}-{feature}.qnt
│ ✓ all tests passed

$ quint run docs/specs/{NNNN}-{feature}.qnt --invariant=inv --max-samples=100
│ ✓ no counterexample found in 100 samples

$ quint verify docs/specs/{NNNN}-{feature}.qnt --invariant=inv --max-steps=10
│ ✓ no counterexample found up to depth 10
```

## 9. Linked PRD

**Mandatory.** Every Quint spec must reference its companion PRD.

- **PRD:** `docs/prd/NNNN-feature-name.md`
- **PRD Status:** {draft | accepted | rejected}
- **Requirement Coverage:** {list every FR-N from the PRD that this spec models}

*If the PRD changes, this spec must be updated and re-verified. If the spec finds a state the PRD says shouldn't exist, the PRD wins; update the spec.*

---

*End of Quint spec. The spec must be in `docs/specs/{NNNN}-{feature}.qnt`, proven, and bidirectionally linked to its companion PRD before any code is written.*
