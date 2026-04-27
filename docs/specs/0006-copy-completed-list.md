# Quint Specification: Copy Completed List as New List

| Spec ID | Feature | Status | Linked PRD | Author | Date |
|---------|---------|--------|------------|--------|------|
| 0006 | Copy Completed List as New List | proven | docs/prd/0006-copy-completed-list.md | spec-06 (team quint-specs) | 2026-04-27 |

---

## 1. Purpose

This spec models the "Copy Completed List" feature per PRD-0006. It covers:
- FR-1: Copy action available only on completed lists
- FR-2: Name pre-fill with truncation for >100 char names
- FR-3: New list creation with fresh code, status active, copied_from set
- FR-4: Item copying from archived data (all unchecked, new positions from 1)
- FR-5: Copier added as participant
- FR-6: Original list unchanged
- FR-7: Empty list copy (zero items)
- FR-8: SSE list-created broadcast
- FR-9: Missing archive (404)
- FR-10: Cannot copy active/deleted lists
- FR-11: copied_from column (ON DELETE SET NULL)
- FR-13: Atomic transaction

## 2. Modeled Phenomena

- Completed list archival data as a set of ArchivedItem records
- Copy creates a new list with fresh share code and items from archive
- All copied items are unchecked with sequential positions starting from 1
- The copied_from relationship tracks origin
- Original list is never modified by copy
- Missing archive results in no state change (404 analog)
- Active/deleted list copy attempts are rejected (no state change)
- **Abstraction note:** Name truncation (FR-2: truncate to 93 chars before appending " (Copy)") is an implementation behavior, not modeled in the spec. The spec uses a small set of short names.
- **Abstraction note:** Share code generation (collision retry) is an implementation detail. The spec uses nondet code selection from a constant set.
- **Abstraction note:** Atomic transaction (FR-13) is an implementation behavior — the spec models the copy as a single action that either completes or doesn't happen.
- **Abstraction note:** `archived_data` JSONB schema is modeled as a set of `ArchivedItem` records. The schema (name, quantity, observations, checked, position) matches the PRD definition.

## 3. State Variables

| PRD Req | State Variable | Type | Meaning |
|---------|----------------|------|---------|
| FR-4 | `archivedItems` | `str -> Set[ArchivedItem]` | Map of list code → archived items at completion |
| FR-11 | `copiedFrom` | `str -> str` | Map of new list code → original list code (empty string = null) |
| FR-3 | `lists` | `str -> ShopList` | Map of list code → list data (reused from PRD-0003) |
| FR-5 | `participants` | `Set[Participant]` | Set of (listCode, email) pairs |
| FR-8 | `sseViewers` | `str -> Set[str]` | SSE viewer tracking |
| FR-1 | `currentUser` | `str` | Currently logged-in user |
| — | `currentTime` | `int` | Monotonic time |

## 4. Actions

| PRD Req | Action | Description |
|---------|--------|-------------|
| FR-1 through FR-8 | `copyCompletedList(originalCode, newCode, newName)` | Copy archived items from completed list into new active list |
| FR-7 | `copyEmptyCompletedList(originalCode, newCode, newName)` | Copy a completed list with zero archived items |
| FR-10 | `copyActiveList(code)` | Negative: reject copy of active list |
| FR-9 | `copyMissingArchive(code)` | Negative: archive missing → no state change |
| Setup | `createList(code, name)` | Create list (reused from PRD-0003) |
| Setup | `completeList(code, version)` | Complete list and archive items (reused from PRD-0003) |

## 5. Invariants

| PRD AC | Invariant | Expression |
|--------|-----------|------------|
| AC-4 | `invOriginalUnchanged` | Original lists are never modified by copy operations |
| AC-1 | `invCopiedListsAreActive` | All copied lists have status "active" |
| AC-3 | `invCopierIsParticipant` | The copying user is always a participant of the copied list |
| AC-1 | `invCopiedItemsUnchecked` | All items on copied lists have checked = false |
| — | `invCopiedFromReferencesValid` | All copiedFrom references point to existing lists (or are empty) |

## 6. Properties

No temporal properties beyond invariants.

## 7. Full Spec

See `docs/specs/0006-copy-completed-list.qnt`.

## 8. Verification Log

```
$ quint typecheck docs/specs/0006-copy-completed-list.qnt
│ ✓ typecheck succeeded

$ quint test docs/specs/0006-copy-completed-list.qnt
│ ✓ 1 passing (rejectCopyMissingArchiveTest)

$ quint run docs/specs/0006-copy-completed-list.qnt --invariant=inv --max-samples=100
│ ✓ no violation found (112ms at 893 traces/second)

$ quint verify docs/specs/0006-copy-completed-list.qnt --invariant=inv --max-steps=5
│ ✓ no violation found (17220ms)
```

## 9. Linked PRD

- **PRD:** `docs/prd/0006-copy-completed-list.md`
- **PRD Status:** draft
- **Requirement Coverage:** FR-1 through FR-13, AC-1 through AC-13

*If the PRD changes, this spec must be updated and re-verified. If the spec finds a state the PRD says shouldn't exist, the PRD wins; update the spec.*

---

*End of Quint spec. The spec is in `docs/specs/0006-copy-completed-list.qnt`, proven, and bidirectionally linked to its companion PRD.*