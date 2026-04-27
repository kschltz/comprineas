# Quint Specification: Create and Manage Shared Lists

| Spec ID | Feature | Status | Linked PRD | Author | Date |
|---------|---------|--------|------------|--------|------|
| 0003 | Create and Manage Shared Lists | proven | docs/prd/0003-shared-lists.md | agent | 2026-04-27 |

---

## 1. Purpose

This spec models the creation and management of shared lists per PRD-0003. It covers:
- FR-1, FR-2: List creation with unique code
- FR-5, FR-6: Rename with optimistic locking
- FR-7: Complete list (status transition)
- FR-8: Delete list (soft delete)
- FR-10: SSE viewer tracking
- FR-11: Action rejection on non-active lists

## 2. Modeled Phenomena

- Lists identified by unique 6-character alphanumeric codes
- List lifecycle: active → completed / active → deleted
- Optimistic locking via version counter for metadata changes
- Participants tracking: creator is automatically added as a participant
- SSE connection tracking for real-time notifications
- Flat access model: any participant can rename, complete, or delete
- **Abstraction note:** Name validation is modeled as set membership (`NAMES`) rather than length bounds because Quint lacks string operators. The implementation must enforce 1–100 characters.
- **Abstraction note:** Share code collision retry (AC-9) is an implementation behavior, not modeled in the spec. The UNIQUE constraint guarantees uniqueness; retry logic is in the application layer.
- **Abstraction note:** SSE event payloads (type=`list-updated`, data=list fields) are an implementation detail. The spec models which viewers are connected, not what data is sent.
- **Abstraction note:** `ShopList` record omits `id` and `updated_at` (internal DB/automatic fields not relevant to invariants).

## 3. State Variables

| PRD Req | State Variable | Type | Meaning |
|---------|----------------|------|---------|
| FR-2 | `lists` | `str -> ShopList` | Map of list code → list data (name, status, version, creator) |
| FR-2 | `participants` | `Set[Participant]` | Set of (listCode, email) pairs |
| FR-10 | `sseViewers` | `str -> Set[str]` | Map of list code → set of emails viewing that list |
| FR-1 | `currentUser` | `str` | Email of the currently logged-in user |
| — | `currentTime` | `int` | Monotonic time for completed_at timestamps |

## 4. Actions

| PRD Req | Action | Description |
|---------|--------|-------------|
| FR-1, FR-2 | `createList(code, name)` | Create a new active list with unique code, add creator as participant |
| FR-5 | `renameList(code, newName, expectedVersion)` | Rename an active list with optimistic locking (AC-5) |
| FR-6 | `renameListConflict(code, newName, expectedVersion)` | Failed rename due to version mismatch |
| FR-7 | `completeList(code, expectedVersion)` | Mark active list as completed |
| FR-8 | `deleteList(code, expectedVersion)` | Soft-delete an active list |
| FR-10 | `viewerConnects(code, email)` | Track SSE connection |
| FR-10 | `viewerDisconnects(code, email)` | Remove SSE connection |
| FR-11 | `actionOnNonActive(code)` | Attempted action on completed/deleted list (no state change) |

## 5. Invariants

| PRD AC | Invariant | Expression |
|--------|-----------|------------|
| AC-9 | `invValidStatus` | All lists have status in {active, completed, deleted} |
| AC-5 | `invValidVersion` | All lists have version ≥ 1 |
| AC-1 | `invCreatorIsParticipant` | Creator is always in participants set |
| AC-6 | `invCompletedAtSet` | Completed lists have completedAt ≥ 0 |
| AC-8 | `invNonActiveVersionIncremented` | Non-active lists have version ≥ 2 |

## 6. Properties

_No temporal properties specified for this PRD. The lifecycle is simple (active → completed/deleted) with no cycles._

## 7. Full Spec

See `docs/specs/0003-shared-lists.qnt`

## 8. Verification Log

| Step | Command | Result |
|------|---------|--------|
| Typecheck | `quint typecheck docs/specs/0003-shared-lists.qnt` | ✅ Passed |
| Test | `quint test docs/specs/0003-shared-lists.qnt` | ✅ 4 test runs pass |
| Simulation | `quint run --invariant=inv --max-samples=100` | ✅ No violations in 100 samples |
| Verify | `quint verify --invariant=inv --max-steps=10` | ✅ All 5 invariants hold through depth 7+ |

Invariants verified: `invValidStatus`, `invValidVersion`, `invCreatorIsParticipant`, `invCompletedAtSet`, `invNonActiveVersionIncremented`.

## 9. Linked PRD

- **PRD:** `docs/prd/0003-shared-lists.md`
- **PRD Status:** accepted
- **Requirement Coverage:** FR-1, FR-2, FR-3 (dashboard — UI, not modeled), FR-4 (card content — UI, covered by AC-13 in PRD), FR-5, FR-6, FR-7, FR-8, FR-9 (code uniqueness modeled via `codeNotInUse`), FR-10, FR-11, FR-12 (auth redirect — implementation concern). FR-3, FR-4, FR-9 retry, and FR-12 are UI/infrastructure requirements verified by integration tests rather than formal spec actions.

*If the PRD changes, this spec must be updated and re-verified.*