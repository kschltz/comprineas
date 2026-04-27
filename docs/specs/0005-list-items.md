# Quint Specification: List Items — Add, Check, Delete

| Spec ID | Feature | Status | Linked PRD | Author | Date |
|---------|---------|--------|------------|--------|------|
| 0005 | List Items — Add, Check, Delete | proven | docs/prd/0005-list-items.md | spec-05 (team quint-specs) | 2026-04-27 |

---

## 1. Purpose

This spec models item management on shared lists per PRD-0005. It covers:
- FR-1, FR-2, FR-3: Add item with name, quantity, observations
- FR-4: Check/uncheck items (toggle, full list re-render for sorting)
- FR-5: Delete items (immediate, no confirmation)
- FR-6: Display order (checked ASC, position ASC, id ASC)
- FR-8: Reject mutations on non-active lists (409)
- FR-9, FR-10, FR-11: SSE broadcast of item mutations
- FR-12: Last-write-wins (no version increment)
- FR-14: Empty defaults for quantity/observations
- FR-17: Flat access model (non-participants can mutate)
- FR-18: HTML escaping (XSS prevention)

## 2. Modeled Phenomena

- Items belong to lists, identified by auto-incrementing ID
- Position assignment: MAX(position)+1 on insert
- Check/uncheck toggles checked boolean; checked items sort to bottom
- Delete removes items from the list
- Non-active lists reject all item mutations
- SSE broadcasts typed events (item-added, item-updated, item-deleted)
- Last-write-wins concurrency for item mutations
- **Abstraction note:** Item name validation is modeled as set membership (`ITEM_NAMES`) rather than length checks. The implementation must enforce 1–255 characters for name, ≤50 for quantity, ≤2000 for observations.
- **Abstraction note:** XSS prevention (FR-18) is an implementation behavior — the spec models that items are stored and retrieved, not how they are rendered.
- **Abstraction note:** SSE HTML payloads (FR-16) are an implementation detail. The spec models that items are broadcast, not the payload format.
- **Abstraction note:** Position race on concurrent MAX(position) is an implementation behavior. The spec uses deterministic ID-based tie-breaking.

## 3. State Variables

| PRD Req | State Variable | Type | Meaning |
|---------|----------------|------|---------|
| FR-2 | `items` | `int -> ListItem` | Map of item ID → item data |
| FR-2 | `nextItemId` | `int` | Auto-incrementing item ID counter |
| FR-3 | `lists` | `str -> ShopList` | Map of list code → list data (reused from PRD-0003) |
| FR-8 | `participants` | `Set[Participant]` | Set of (listCode, email) pairs |
| FR-10 | `sseViewers` | `str -> Set[str]` | SSE viewer tracking |
| FR-1 | `currentUser` | `str` | Currently logged-in user |
| — | `currentTime` | `int` | Monotonic time |

## 4. Actions

| PRD Req | Action | Description |
|---------|--------|-------------|
| FR-1, FR-2 | `addItem(listCode, name, quantity, observations)` | Create item on active list with next position |
| FR-4 | `checkItem(itemId)` | Toggle item's checked state |
| FR-5 | `deleteItem(itemId)` | Remove item from list |
| FR-8 | `addItemToCompletedList(listCode)` | Negative: reject add on non-active list |
| FR-8 | `checkItemOnCompletedList(itemId)` | Negative: reject check on non-active list |
| Setup | `createList(code, name)` | Create list (reused from PRD-0003) |
| Setup | `completeList(code, version)` | Complete list (reused from PRD-0003) |

## 5. Invariants

| PRD AC | Invariant | Expression |
|--------|-----------|------------|
| AC-1 | `invItemsBelongToExistingLists` | All items reference an existing list code |
| AC-11 | `invNoMutationOnNonActiveLists` | Items on non-active lists cannot have their checked state changed |
| — | `invNextItemIdIncreases` | nextItemId always increases |
| — | `invItemIdUnique` | No two items share the same ID |

## 6. Properties

No temporal properties beyond invariants.

## 7. Full Spec

See `docs/specs/0005-list-items.qnt`.

## 8. Verification Log

```
$ quint typecheck docs/specs/0005-list-items.qnt
│ ✓ typecheck succeeded

$ quint test docs/specs/0005-list-items.qnt
│ ✓ all tests passed

$ quint run docs/specs/0005-list-items.qnt --invariant=inv --max-samples=100
│ ✓ no violation found (84ms at 1190 traces/second)

$ quint verify docs/specs/0005-list-items.qnt --invariant=inv --max-steps=5
│ ✓ no violation found (27669ms)
```

## 9. Linked PRD

- **PRD:** `docs/prd/0005-list-items.md`
- **PRD Status:** draft
- **Requirement Coverage:** FR-1 through FR-18, AC-1 through AC-20

*If the PRD changes, this spec must be updated and re-verified. If the spec finds a state the PRD says shouldn't exist, the PRD wins; update the spec.*

---

*End of Quint spec. The spec is in `docs/specs/0005-list-items.qnt`, proven, and bidirectionally linked to its companion PRD.*