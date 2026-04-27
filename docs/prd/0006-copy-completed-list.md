# Copy Completed List as New List

| PRD ID | Feature | Status | Linked Quint Spec | Author | Date |
|--------|---------|--------|-------------------|--------|------|
| 0006 | Copy Completed List as New List | **draft** | `docs/specs/0006-copy-completed-list.qnt` | prd6 | 2026-04-27 |

---

## 1. Problem Statement

Users create recurring shopping lists (e.g., weekly groceries). After completing a list, the items and structure are archived (PRD-0003 FR-7). There is currently no way to reuse a completed list's items as a starting point for a new list. Users must either manually re-enter every item or never complete their lists at all. This feature allows any participant of a completed list to copy its archived items into a fresh active list, preserving item details while resetting checked states and generating a new share code.

## 2. Goals

- Allow any user who can view a completed list to copy its archived items into a new active list.
- The new list receives a fresh 6-character share code, a user-editable name (default: original name + " (Copy)"), and status "active".
- Copied items retain their name, quantity, and observations but are all unchecked with new IDs and positions.
- The copying user is automatically added as a participant of the new list.
- The original completed list is unchanged — copy is purely additive.
- Track the origin relationship via a new `copied_from` column on the `lists` table.
- The new list appears on the copier's dashboard in real-time (SSE push).
- Copying an empty completed list (zero items) creates a valid empty new list.

### Non-Goals

- Copying active (not completed) lists.
- Copying deleted lists (not visible, therefore not actionable).
- Bulk-copying multiple completed lists at once.
- Copying individual items from a completed list (all-or-nothing per list).
- Converting a completed list back to active (undo complete).
- Merging items from multiple completed lists into one new list.
- Preserving the original list's participant set in the new list.
- Preserving the original list's share code or version number.
- Copying list metadata beyond name and items (e.g., creation date, completion date, participant history).

## 3. User Stories

- As a user who completed a grocery list, I want to copy it as a new list so that I can reuse the same items for next week's shopping.
- As a participant on a completed list, I want to copy the list so that I can start my own shopping trip without re-entering every item.
- As a user copying a completed list, I want to edit the new list's name before it's created so that I can distinguish it from the original.
- As a user, I want to see the relationship between the new list and the original so that I know where the items came from.

## 4. Detailed Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | The system shall display a "Copy as New List" action on the completed list detail page (`/list/:code`) and on the completed list card in the dashboard. | must | Visible only for lists with status "completed". |
| FR-2 | When a user triggers "Copy as New List", the system shall present a confirmation/preview dialog pre-filling the new list name as `{original name} (Copy)` and allow the user to edit the name before confirming. If the original name followed by " (Copy)" would exceed 100 characters, the original name is truncated to 93 characters before appending " (Copy)". | must | Name field: 1–100 characters, same validation as create list. |
| FR-3 | On confirmation, the system shall create a new list with: a fresh unique 6-character share code, the user-submitted name (or default), status "active", version 1, the copying user as `created_by`, and `copied_from` set to the original list's `id`. | must | |
| FR-4 | The system shall copy all items from the original completed list's archived data to the new list. Each copied item shall have: the same `name`, `quantity`, and `observations`; `checked` = false; a new sequential `position` starting from 1; new item IDs; and the new list's ID as `list_id`. | must | Items are read from `completed_lists.archived_data` (JSONB) for the original list. |
| FR-5 | The system shall add the copying user as a participant of the new list (`list_participants`), using the same mechanism as creating a new list from scratch. | must | Duplicate-safe via UNIQUE(list_id, user_id). |
| FR-6 | The original completed list shall remain unchanged by the copy operation. No items are moved or deleted; `completed_at`, `archived_data`, status, and all other fields stay as-is. | must | |
| FR-7 | If the original completed list has zero items in its archive, the system shall still create the new empty list (name, code, participant) without error. | must | |
| FR-8 | The system shall broadcast a `list-created` SSE event (per ADR-0006) so the new list appears on the copier's dashboard in real-time. | must | The copier is SSE-connected to their dashboard if they have it open. |
| FR-9 | If the original list's `completed_lists` archive record is missing, or if `archived_data` is NULL, the system shall return a 404 error with message "Completed list data not found." | must | Defensive: should never happen under normal operation. |
| FR-10 | The system shall not allow copying an active or deleted list. Only lists with status "completed" are eligible. If a copy is attempted on a deleted list, return HTTP 404 with message "List not found." | must | The "Copy as New List" UI element is present only on completed lists. |
| FR-11 | The system shall store the `copied_from` relationship as an INTEGER column on `lists` referencing `lists(id)` with `ON DELETE SET NULL`. | must | Requires a migration on the `lists` table. |
| FR-12 | The system shall display the `copied_from` relationship on the new list's detail page (e.g., "Copied from: Weekly Groceries") when `copied_from` is non-null. | should | UX nicety; not critical for correctness. |
| FR-13 | The system shall ensure the copy operation is performed atomically: the new list, its items, and the participant row are created in a single database transaction. | must | Prevents partial state (list without items or without participant). |

### 4.2 Data Requirements

**New column on `lists` table (mini-migration):**

- `copied_from` INTEGER REFERENCES `lists(id)` ON DELETE SET NULL — nullable, tracks the original list this was copied from.
  - `ON DELETE SET NULL`: if the original list is hard-deleted, the reference becomes NULL (soft integrity).
  - Index on `(copied_from)` for queries like "what lists were copied from this one?"

**Existing tables referenced (per ADR-0003):**

- **`lists`:**
  - `id` SERIAL PRIMARY KEY
  - `code` VARCHAR(6) UNIQUE NOT NULL
  - `name` VARCHAR(100) NOT NULL
  - `status` VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'deleted'))
  - `version` INTEGER NOT NULL DEFAULT 1
  - `created_by` INTEGER REFERENCES users(id) ON DELETE SET NULL
  - `copied_from` INTEGER REFERENCES lists(id) ON DELETE SET NULL — **NEW, nullable**
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `completed_at` TIMESTAMPTZ — NULL for new lists
  - `updated_at` TIMESTAMPTZ DEFAULT now()

- **`completed_lists`:**
  - `id` SERIAL PRIMARY KEY
  - `original_list_id` INTEGER REFERENCES lists(id) ON DELETE SET NULL
  - `code` VARCHAR(6)
  - `name` VARCHAR(100)
  - `completed_at` TIMESTAMPTZ NOT NULL
  - `archived_data` JSONB — snapshot of items at completion time; this is the data source for copying items. **Schema:** JSONB array of objects, each with keys: `name` (text), `quantity` (text or null), `observations` (text or null), `checked` (boolean), `position` (integer). Array element order equals display order at completion time (sorted by `checked ASC, position ASC, id ASC` per PRD-0005 FR-6).

- **`list_items` (the new list's items):**
  - `id` SERIAL PRIMARY KEY (new auto-generated)
  - `list_id` INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE (the new list's ID)
  - `name` VARCHAR(255) NOT NULL (copied from archived data)
  - `quantity` VARCHAR(50) (copied from archived data)
  - `observations` TEXT (copied from archived data)
  - `checked` BOOLEAN DEFAULT false (always false for copies)
  - `position` INTEGER NOT NULL (sequential starting from 1)
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `updated_at` TIMESTAMPTZ DEFAULT now()

- **`list_participants`:**
  - `id` SERIAL PRIMARY KEY
  - `list_id` INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE (new list)
  - `user_id` INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE (copying user)
  - `joined_at` TIMESTAMPTZ DEFAULT now()
  - UNIQUE(list_id, user_id)

**Item copy mapping:**

| Archived field | New item field | Transformation |
|----------------|----------------|----------------|
| `name` | `name` | Copied verbatim |
| `quantity` | `quantity` | Copied verbatim (null → null) |
| `observations` | `observations` | Copied verbatim (null → null) |
| (not used) | `checked` | Always `false` |
| (not used) | `position` | Sequential integers starting from 1, ordered by archived item order |
| (not used) | `id` | Auto-generated SERIAL |
| (not used) | `list_id` | New list's ID |
| `checked` | (not used) | Discarded — all items start unchecked |

**copied_from chain behavior:** If a list in the middle of a copy chain (A→B→C) is hard-deleted, B.copied_from becomes NULL (ON DELETE SET NULL). C.copied_from still points to B. Under current soft-delete-only design, this situation does not arise.

### 4.3 UI / UX Requirements

- **Completed list detail page (`/list/:code`, status = completed):**
  - A "Copy as New List" button/link, styled as a secondary action (not the primary CTA).
  - Clicking opens a modal or inline confirmation form.
  - The form shows the suggested name `{original name} (Copy)` in a text input, editable.
  - A brief summary: "This will create a new active list with all items from this completed list. The original list will not be modified."
  - "Cancel" and "Copy" buttons.
  - On success: the user is redirected to the new list's view page (`/list/:new-code`).
  - On error: error message is displayed inline.

- **Dashboard completed list card:**
  - Each completed list card includes a "Copy as New List" action (icon button or link).
  - Same modal flow as above.
  - On success: the new list appears in the "Active" section of the dashboard (SSE if connected, or page redirect).

- **New list detail page (after copy):**
  - If `copied_from` is non-null, display subtle text: "Copied from: {original list name}" with a link to the original list's detail page (if accessible — may be completed, but still viewable).
  - All items appear unchecked, in the same order as the original.

- **Error states:**
  - Original list was deleted between the user seeing it and clicking copy → handled by server check; return 404 with message.
  - Archived data missing → return 404 with "Completed list data not found."
  - Name validation failure → inline error (name must be 1–100 characters).
  - If the user navigates away while the copy dialog is open, no partial state is created — the POST has not been sent.

### 4.4 Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | The copy operation (create list + insert items + add participant) must complete in < 500 ms (p95) for lists with ≤ 200 items. No explicit maximum list item count; performance degrades gracefully beyond 200 items. | performance |
| NFR-2 | The copy must be atomic — if any step fails, the entire operation rolls back (no orphan lists). | data integrity |
| NFR-3 | The new list's share code must be collision-resistant per PRD-0003 FR-9 standards (36^6 combinations, UNIQUE constraint, up to 3 retries). | reliability |
| NFR-4 | The `copied_from` column migration must be backward-compatible: existing lists have `copied_from = NULL` and are unaffected. | migration |

## 5. Out of Scope

- Copying active (non-completed) lists — only completed lists can be copied.
- Copying deleted lists — not visible, not actionable.
- Bulk-copying multiple completed lists at once.
- Copying individual items from a completed list (all-or-nothing).
- Undoing a complete (converting a completed list back to active).
- Merging items from multiple completed lists.
- Preserving the original participant set in the copy.
- Displaying the `copied_from` relationship on the original list (e.g., "This list was copied N times").
- Template or preset functionality beyond copying from a completed list.
- Copying list item check states or positions as they were at completion.

## 6. Acceptance Criteria

- [ ] **AC-1:** Given a completed list with 3 archived items, when a participant clicks "Copy as New List" and confirms with the default name, then a new active list is created with share code, name `{original name} (Copy)`, status "active", version 1, `copied_from` set to the original list's ID, and all 3 items copied with the same name/quantity/observations, `checked = false`, and sequential positions 1, 2, 3.
- [ ] **AC-2:** Given a completed list, when a participant clicks "Copy as New List" and edits the name to "My Custom Name" before confirming, then the new list has name "My Custom Name" (not the default).
- [ ] **AC-3:** Given a completed list, when a participant clicks "Copy as New List", then the copying user is added as a participant of the new list and appears in its participant list.
- [ ] **AC-4:** Given a completed list, after a successful copy, the original completed list is unchanged — same status, same archived items, same `completed_at`.
- [ ] **AC-5:** Given a completed list with zero archived items, when a participant copies it, then a new empty list is created (no items, valid share code, name, participant).
- [ ] **AC-6:** Given the new list detail page, when `copied_from` is non-null, then the page displays "Copied from: {original list name}" with a link to the original list's page.
- [ ] **AC-7:** Given an active list, when a user looks for a "Copy as New List" action, then no such action is present (the action is only for completed lists).
- [ ] **AC-8:** Given a copy operation in progress, when the database fails mid-operation (e.g., item insert fails), then no new list is created (full rollback).
- [ ] **AC-9:** Given a completed list that was deleted between the user seeing it and clicking copy, when the user triggers "Copy as New List", then a 404 error is returned.
- [ ] **AC-10:** Given a user copies a completed list, when they navigate to the dashboard, then the new list is visible in the "Active" section on next dashboard page load, or pushed via SSE if the dashboard has an active SSE connection.
- [ ] **AC-11 (Missing archive, FR-9):** Given a completed list whose `completed_lists` row is missing, when a user attempts to copy it, then the server returns HTTP 404 with message "Completed list data not found."
- [ ] **AC-12 (Name validation failure):** Given the copy dialog, when the user submits with an empty name or a name exceeding 100 characters, then an inline validation error is shown and the copy is not performed.
- [ ] **AC-13 (Copied_from migration, FR-11):** Given an existing database without the `copied_from` column, when the migration runs, then all existing list rows have `copied_from = NULL` and no data is lost.

## 7. Open Questions

- **Q1:** Should the `copied_from` link be clickable on the new list detail page even if the original list is completed? — **Answer: Yes.** The original list is still viewable (read-only). The link redirects to `/list/:original-code`.
- **Q2:** Should the original list page show how many times it was copied (e.g., "Copied 3 times")? — **Answer: Deferred.** This is a nice-to-have analytics feature that can be added later via a COUNT query on `lists.copied_from`. Out of scope for this PRD.
- **Q3:** Should the copy operation preserve the original item ordering (position values from archive)? — **Answer: Yes.** The `archived_data` JSONB array preserves order at completion. New items get sequential positions 1..N following that order. The original position values are not preserved literally (they may have gaps); the order is preserved.
- **Q4:** Should the copy operation check if the user already has the maximum number of active lists? — **Answer: No limit** defined in the current data model. Deferred until a business rule is established.
- **Q5:** Should copying a list that was itself copied (a chain of copies) preserve the original `copied_from` chain or always point to the immediate source? — **Answer: Point to the immediate source.** If List B was copied from List A, and someone copies List B to create List C, then List C has `copied_from = B.id`. The relationship chain can be traversed by following the link.

## 8. Linked ADRs

- **ADR-0003** — Data Model & Schema Design: defines `lists`, `list_items`, `list_participants`, `completed_lists` tables. PRD-0006 adds the `copied_from` column to `lists`.
- **ADR-0006** — SSE: real-time delivery of `list-created` event to dashboard viewers.
- **ADR-0007** — Authentication Strategy: session cookies provide `current-user` for authorization checks.

## 9. Linked Quint Spec

**Mandatory.** Every PRD must reference its companion Quint specification.

- **Quint Spec:** `docs/specs/0006-copy-completed-list.qnt`
- **Quint Spec Status:** proven
- **Verification Summary:** Pass — quint typecheck ✅, quint test ✅, quint run (100 samples) ✅, quint verify (depth 5) ✅

*Without a linked, proven Quint spec, this PRD may not proceed to implementation.*

---

*End of PRD. The linked Quint specification in docs/specs/ must model every requirement marked FR-N above as a state variable, action, or invariant.*
