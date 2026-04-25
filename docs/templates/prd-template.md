# {Feature Title}

| PRD ID | Feature | Status | Linked Quint Spec | Author | Date |
|--------|---------|--------|-------------------|--------|------|
| {NNNN} | {One-line description} | draft | docs/specs/{NNNN}-{feature}.qnt | {agent/user} | {YYYY-MM-DD} |

---

## 1. Problem Statement

{What problem are we solving? In 2-4 sentences. Be concrete, not abstract.}

## 2. Goals

{What does success look like for this feature?}

- {Goal 1}
- {Goal 2}
- …

### Non-Goals

{What is explicitly NOT in scope? Be ruthless.}

- {Non-goal 1}
- {Non-goal 2}
- …

## 3. User Stories

{As a [role], I want [capability] so that [benefit].}

- As a {role}, I want {capability} so that {benefit}.
- As a {role}, I want {capability} so that {benefit}.
- …

## 4. Detailed Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | {The system shall …} | must | |
| FR-2 | {…} | must | |
| FR-3 | {…} | should | |
| … | | | |

### 4.2 Data Requirements

{What data entities, fields, and relationships are involved?}

- {Entity}: {fields, constraints, relationships}
- …

### 4.3 UI / UX Requirements

{What does the user see and do? Wireframes optional but descriptions mandatory.}

- {Screen/page}: {description of elements, interactions, states}
- …

### 4.4 Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | {Performance, security, availability, etc.} | {target value} |
| NFR-2 | … | |

## 5. Out of Scope

- {Clearly labeled items that are NOT being built now.}
- …

## 6. Acceptance Criteria

{Testable, unambiguous criteria. If you can't write a test for it, it's not a valid acceptance criterion.}

- [ ] {Given [precondition], when [action], then [expected result].}
- [ ] {…}
- …

## 7. Open Questions

- {Question} — {who should answer, by when?}
- …

## 8. Linked ADRs

- {ADR-NNNN} — {Reason for link}
- …

## 9. Linked Quint Spec

**Mandatory.** Every PRD must reference its companion Quint specification.

- **Quint Spec:** `docs/specs/NNNN-feature-name.qnt`
- **Quint Spec Status:** {draft | proven | failed}
- **Verification Summary:** {Pass/Fail — paste `quint verify` output here}

*Without a linked, proven Quint spec, this PRD may not proceed to implementation.*

---

*End of PRD. The linked Quint specification in docs/specs/ must model every requirement marked FR-N above as a state variable, action, or invariant.*
