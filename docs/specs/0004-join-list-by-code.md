# Quint Specification: Join Shared List by Code

| Spec ID | Feature | Status | Linked PRD | Author | Date |
|---------|---------|--------|------------|--------|------|
| 0004 | Join Shared List by Code | proven | docs/prd/0004-join-list-by-code.md | spec-04 (team quint-specs) | 2026-04-27 |

---

## 1. Purpose

This spec models the "Join List by Code" feature per PRD-0004. It covers:
- FR-1, FR-2: Join form validation (6-char alphanumeric code, case-insensitive)
- FR-3: List lookup by code
- FR-4, FR-5: Error handling for non-existent/completed/deleted lists
- FR-6, FR-7: Already-a-participant redirect
- FR-8: New participant insertion and SSE broadcast
- FR-9: Rate limiting (10 attempts per 60s per user)
- FR-10: SSE participant-joined event
- FR-11: Dashboard form placement
- FR-12: Co-location with Create List
- FR-13: Keyboard submission
- FR-14: Accessibility (role="alert")

## 2. Modeled Phenomena

- Share code validation and case-insensitive lookup
- Participant uniqueness via UNIQUE(list_id, user_id) constraint
- Rate limiting with sliding window per user
- Already-a-participant success case (no error)
- SSE broadcast of participant-joined events
- List status checks (active, completed, deleted)
- **Abstraction note:** Code validation is modeled as set membership (`CODES`) rather than regex/length checks because Quint lacks string operators. The implementation must enforce exactly 6 alphanumeric characters.
- **Abstraction note:** Whitespace trimming (FR-2 note) is an implementation behavior, not modeled in the spec.
- **Abstraction note:** Rate limit window uses discrete time steps (each step = 1s). The sliding window of 60s is modeled as `currentTime - windowStart < 60`.

## 3. State Variables

| PRD Req | State Variable | Type | Meaning |
|---------|----------------|------|---------|
| FR-2 | `lists` | `str -> ShopList` | Map of list code → list data (reused from PRD-0003) |
| FR-8 | `participants` | `Set[Participant]` | Set of (listCode, email) pairs |
| FR-10 | `sseViewers` | `str -> Set[str]` | Map of list code → set of emails viewing that list |
| FR-1 | `currentUser` | `str` | Email of the currently logged-in user |
| — | `currentTime` | `int` | Monotonic time |
| FR-9 | `rateLimitAttempts` | `str -> int` | Map of user email → attempt count in current window |
| FR-9 | `rateLimitWindowStart` | `str -> int` | Map of user email → time when current window started |

## 4. Actions

| PRD Req | Action | Description |
|---------|--------|-------------|
| FR-1 through FR-8 | `joinList(code)` | Validate code, lookup list, check status, add participant or redirect, broadcast SSE |
| FR-7 | `joinAlreadyParticipant(code)` | User is already a participant on the list — redirect, no duplicate row |
| FR-9 | `actionOnInvalidCode(code)` | Code format invalid or list not found/completed/deleted — no state change |
| FR-9 | `rateLimitExceeded(user)` | Rate limit hit — no state change |
| FR-1, FR-2 (setup) | `createList(code, name)` | Create list (reused from PRD-0003) |
| FR-7 (setup) | `completeList(code, version)` | Complete list (reused from PRD-0003) |
| FR-10 | `viewerConnects(code, email)` | SSE viewer connects |
| FR-10 | `viewerDisconnects(code, email)` | SSE viewer disconnects |

## 5. Invariants

| PRD AC | Invariant | Expression |
|--------|-----------|------------|
| AC-3 | `invRateLimit` | Rate limit counts never exceed 10 within the sliding window |
| AC-2 | `invParticipantUnique` | No duplicate (listCode, email) pairs in participants |
| AC-5 | `invParticipantsHaveValidList` | Every participant's listCode corresponds to an existing list |
| — | `invRateLimitNonNegative` | All rate limit counts are non-negative |

## 6. Properties

No temporal properties beyond invariants.

## 7. Full Spec

See `docs/specs/0004-join-list-by-code.qnt`.

## 8. Verification Log

```
$ quint typecheck docs/specs/0004-join-list-by-code.qnt
│ ✓ typecheck succeeded

$ quint test docs/specs/0004-join-list-by-code.qnt
│ ✓ all tests passed

$ quint run docs/specs/0004-join-list-by-code.qnt --invariant=inv --max-samples=100
│ ✓ no violation found (88ms at 1136 traces/second)

$ quint verify docs/specs/0004-join-list-by-code.qnt --invariant=inv --max-steps=5
│ ✓ no violation found (8968ms)
```

## 9. Linked PRD

- **PRD:** `docs/prd/0004-join-list-by-code.md`
- **PRD Status:** draft
- **Requirement Coverage:** FR-1 through FR-14, AC-1 through AC-16

*If the PRD changes, this spec must be updated and re-verified. If the spec finds a state the PRD says shouldn't exist, the PRD wins; update the spec.*

---

*End of Quint spec. The spec is in `docs/specs/0004-join-list-by-code.qnt`, proven, and bidirectionally linked to its companion PRD.*