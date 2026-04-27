# List Items — Add, Check, Delete

| PRD ID | Feature | Status | Linked Quint Spec | Author | Date |
|--------|---------|--------|-------------------|--------|------|
| 0005 | List Items — Add, Check, Delete | **draft** | `docs/specs/0005-list-items.qnt` | prd5 | 2026-04-27 |

---

## 1. Problem Statement

Users can now create shared lists (PRD-0003) and join them by code (PRD-0004), but the lists are empty. The core value of Comprineas is collaborative item management: participants need to add grocery items, check them off as they shop, and delete mistakes or completed items. Without this, the app has no utility. Item mutations must appear instantly for all viewers on the same list via SSE, and the UX must feel snappy with HTMX inline swaps — no full page reloads for item actions.

## 2. Goals

- Allow any participant on an active list to add items (name, quantity, observations) via an inline HTMX form.
- Allow any participant to check and uncheck items, with checked items visually distinguished (grayed out) and sorted to the bottom of the list.
- Allow any participant to delete items from a list.
- Display items in position order (insertion order) with checked items pushed to the bottom.
- Broadcast all item mutations in real time to all SSE-connected viewers of the same list via typed events (`item-added`, `item-updated`, `item-deleted`).
- Use last-write-wins (no optimistic locking) for all item mutations per ADR-0003.
- Enforce read-only semantics on completed/deleted lists: item mutations must return 409 Conflict.

### Non-Goals

- Reordering items by drag-and-drop or move actions (deferred to a future PRD).
- Bulk operations (check all, delete all, multi-select).
- Item categories, tags, or grouping.
- Item search or filtering.
- List-level undo for item mutations.
- Offline support or conflict resolution beyond LWW.
- Pagination of item lists (deferred until proven necessary).

## 3. User Stories

- As a participant on a shared list, I want to add an item with a name and optional quantity/observations so that other shoppers know what to buy.
- As a participant on a shared list, I want to check off items as I find them so that everyone knows what's been collected.
- As a participant on a shared list, I want to uncheck an item if it was checked by mistake so that someone else can get it.
- As a participant on a shared list, I want to delete incorrect or unwanted items so that the list stays clean.
- As a participant on a shared list, I want checked items to move to the bottom so that I can focus on what's left to buy.
- As a participant on a shared list, I want other users' changes to appear instantly so that I always see the current state.

## 4. Detailed Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | The system shall provide an "Add Item" form on the list view page with fields: name (text, required, 1–255 characters), quantity (text, optional, max 50 characters), and observations (textarea, optional, max 2000 characters). | must | HTMX inline form at top of item list. |
| FR-2 | On valid add-item submission, the system shall create a `list_items` row with the provided fields, assign the next position value (`MAX(position) + 1` or 1 if empty), set `checked = false`, and return the rendered item HTML. | must | HTMX: `hx-post="/list/:code/items"`, target `#item-list`, swap `beforeend`. The server returns the new item row HTML fragment. |
| FR-3 | On add-item submission, if the name is empty or exceeds 255 characters, or quantity exceeds 50 characters, or observations exceeds 2000 characters, the system shall return a validation error (HTMX swap with error message). | must | Inline validation feedback via HTMX form error swap. |
| FR-4 | The system shall provide a "Check/Uncheck" action that toggles the `checked` boolean on a `list_items` row. The action returns the full sorted item list HTML replacing the `#item-list` container. | must | HTMX: `hx-post="/list/:code/items/:id/check"`, hx-target=`#item-list`, hx-swap=`innerHTML`. The server returns the full sorted item list. Toggle semantics: if `checked=true` → set to `false`; if `checked=false` → set to `true`. |
| FR-5 | The system shall provide a "Delete Item" action that deletes a `list_items` row (DELETE FROM list_items WHERE id = ?). The action removes the item's HTML element from the page. Delete is immediate. No confirmation dialog. (A confirmation may be added in a future iteration.) | must | HTMX: `hx-delete="/list/:code/items/:id"`, target `#item-:id`, swap `outerHTML` to empty/deleted placeholder, or `delete` swap. |
| FR-6 | Items shall be displayed in ascending `position` order. Checked items (`checked = true`) shall be pushed to the bottom of the list within position order (i.e., sorted first by `checked` ascending, then by `position` ascending, then by `id` ascending). | must | SQL: `ORDER BY checked ASC, position ASC, id ASC`. Unchecked items (false = 0) come first, checked items (true = 1) come second. |
| FR-7 | Checked items shall be visually distinguished with reduced opacity (Tailwind `opacity-50`) or line-through styling, clearly indicating they have been collected. | must | CSS via Tailwind utility classes on checked item rows. |
| FR-8 | The system shall reject any item mutation (add, check, delete) on lists with `status` of `completed` or `deleted`, returning HTTP 409 Conflict with an explanatory message. | must | Per PRD-0003 FR-11. Read-only enforcement. |
| FR-9 | The system shall broadcast an SSE event of type `item-added` to all connected viewers of the list when an item is created. The event payload shall contain the complete item data. | must | Per ADR-0006. Payload: `{id, list_id, name, quantity, observations, checked, position, created_at}`. |
| FR-10 | The system shall broadcast an SSE event of type `item-updated` to all connected viewers of the list when an item's `checked` state is toggled. The event payload shall contain the complete updated item data. | must | Per ADR-0006. |
| FR-11 | The system shall broadcast an SSE event of type `item-deleted` to all connected viewers of the list when an item is deleted. The event payload shall contain the deleted item's `id`. | must | Per ADR-0006. Payload: `{id}`. |
| FR-12 | Item mutations shall NOT increment `lists.version` and shall NOT use optimistic locking (last-write-wins per ADR-0003). | must | Multiple users can check different items or even the same item concurrently without conflict errors. |
| FR-13 | When the list has no items, the system shall display a placeholder message "No items yet." in the item list area. | should | Visible on fresh lists and after deleting the last item. |
| FR-14 | The quantity field default shall be empty. The observations field default shall be empty. | must | Both fields are optional when adding an item. |
| FR-15 | After adding an item, the add-item form input fields shall be cleared so the user can type the next item. | must | HTMX: form reset after successful add. |
| FR-16 | The SSE event payload for `item-added` and `item-updated` shall include the rendered HTML fragment of the item row for direct DOM swap by connected clients. | must | Simplifies client: clients with SSE receive pre-rendered HTML ready for `hx-swap-oob`. |
| FR-17 | When a non-participant (user not present in `list_participants` for the list) performs an item mutation, the system shall accept it. Participation tracking is for display only — anyone with the list code has full access. | must | Flat access model per ADR-0003. |

### 4.2 Data Requirements

The `list_items` table as defined in ADR-0003 and referenced in PRD-0003. No schema changes required for this PRD.

- **`list_items`** (existing table stub, fully utilized in this PRD):
  - `id` SERIAL PRIMARY KEY — auto-generated, immutable
  - `list_id` INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE — FK to parent list
  - `name` VARCHAR(255) NOT NULL — item name, 1–255 characters
  - `quantity` VARCHAR(50) — optional, e.g., "1 kg", "a bunch", "2", empty string by default
  - `observations` TEXT — optional, e.g., "organic only", "green not ripe", empty string by default
  - `checked` BOOLEAN NOT NULL DEFAULT false — unchecked on creation, toggled by check/uncheck
  - `position` INTEGER NOT NULL — ordering: `MAX(position) + 1` on insert, 1 for first item. Not re-assigned on delete (gaps are acceptable).
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `updated_at` TIMESTAMPTZ DEFAULT now()
  - INDEX on (`list_id`, `position`) — for ordered retrieval (`ORDER BY checked ASC, position ASC`)
  - INDEX on (`list_id`) WHERE `checked = false` — for active-items-only queries

**Position semantics:**
- On insert, position is assigned as `COALESCE(MAX(position), 0) + 1` within the same `list_id`.
- Deletions leave gaps (no re-indexing of positions). Gaps are harmless since ordering is deterministic.
- Position is never updated after insert (reordering out of scope).
- Display sort: `ORDER BY checked ASC, position ASC`.

**Concurrency (last-write-wins):**
- No version column on `list_items`.
- Concurrent inserts: both succeed with different `position` values (race on `MAX(position)` is acceptable — two items may get the same position; both display in order and a tie-breaking sort by `id` is acceptable for deterministic display).
- Concurrent check/uncheck on the same item: the last `UPDATE` wins.
- Concurrent delete: SQL `DELETE` is idempotent; second delete affects 0 rows (no error).
- The server executes the mutation and immediately broadcasts the SSE event for the new state.

### 4.3 UI / UX Requirements

- **List view page (`/list/:code`):**
  - **Header:** List name, share code (with copy button), status badge.
  - **Add Item form** (prominent, top of item area):
    - Text input for name (required, placeholder: "Add item…").
    - Optional quantity text input (placeholder: "Qty").
    - Optional observations textarea (placeholder: "Observations…").
    - "Add" button.
    - HTMX: `hx-post="/list/:code/items"`, `hx-target="#item-list"`, `hx-swap="beforeend"`. Form resets after success.
    - Validation errors swap into a small inline error element below the input.
  - **Item list** (`#item-list`):
    - Unchecked items at top, checked items at bottom.
    - Each item row displays: checkbox (toggle check), name, quantity in parentheses if present, observations in italic if present, delete button (trash icon).
    - Each item row has a unique `id` attribute: `id="item-:id"`.
    - Checked items: Tailwind `opacity-50` + `<s>` strikethrough on name.
    - Empty state: "No items yet." centered message.
  - **Item row actions:**
    - Checkbox: `hx-post="/list/:code/items/:id/check"`, `hx-target="#item-list"`, `hx-swap="innerHTML"` (full list re-render for re-sorting).
    - Delete button (trash icon): `hx-delete="/list/:code/items/:id"`, `hx-target="#item-:id"`, `hx-swap="outerHTML"`. The server returns an empty `<div>` that replaces the item row. No confirmation dialog.
  - **SSE connection:** `<div hx-ext="sse" sse-connect="/list/:code/events" sse-swap="message">` or custom EventSource handling. SSE events carry `hx-swap-oob` fragments for item rows.

- **Read-only lists (completed/deleted):**
  - No add-item form visible.
  - No checkboxes or delete buttons on items.
  - Items displayed in a static list with a banner: "This list is completed." or "This list has been deleted."

- **Real-time updates (other viewers):**
  - When user A adds an item, user B's page receives an `item-added` SSE event and the new item HTML is appended to `#item-list`.
  - When user A checks an item, user B's page receives an `item-updated` event with the full `#item-list` innerHTML for re-sorting.
  - When user A deletes an item, user B's page receives an `item-deleted` event containing an empty `<div hx-swap-oob="true" id="item-:id">` that replaces the item row with nothing.
  - HTMX out-of-band swap (`hx-swap-oob="true"`) handles the DOM updates from SSE event payloads.

### 4.4 Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Item add must complete in < 200 ms (p95). | performance |
| NFR-2 | Item check/uncheck must complete in < 100 ms (p95). | performance |
| NFR-3 | Item delete must complete in < 100 ms (p95). | performance |
| NFR-4 | SSE broadcast for any item mutation must reach all connected clients within 500 ms. | real-time |
| NFR-5 | Item mutations on completed/deleted lists must return 409 Conflict in < 100 ms (DB read required to check list status). | performance |
| NFR-6 | The system must handle 50 concurrent item mutations on the same list without data loss or errors. (Load-test target, not verifiable via single-request ACs.) | scalability |

## 5. Out of Scope

- Reordering items (drag-and-drop, move up/down) — deferred to future PRD.
- Bulk check/uncheck or delete-all actions.
- Item categories, tags, or grouping.
- Item search, filter, or sort options beyond checked-at-bottom.
- List-level undo.
- Offline support or optimistic client-side state.
- Pagination of item list.
- Position re-indexing after deletes.
- Item editing beyond check/uncheck (editing name/quantity/observations after creation) — deferred to future PRD.
- Shopping mode (showing only unchecked items).
- Barcode scanning or image attachment.
- Price or store fields on items.

## 6. Acceptance Criteria

- [ ] **AC-1:** Given an active list with no items, when a participant submits the add-item form with a valid name, then the item appears at the top of the list in position 1 with `checked = false`, and the form inputs are cleared.
- [ ] **AC-2:** Given an active list with existing items, when a participant submits the add-item form with name, quantity, and observations, then the item is created with `position = MAX(position) + 1` and all fields are correctly stored and displayed.
- [ ] **AC-3:** Given the add-item form, when the name is empty or exceeds 255 characters, then a validation error is displayed inline and the item is not created.
- [ ] **AC-4:** Given the add-item form, when the quantity exceeds 50 characters, then a validation error is displayed inline and the item is not created.
- [ ] **AC-5:** Given a list with an unchecked item, when a participant clicks the checkbox, then the item's `checked` becomes `true`, the full sorted item list is re-rendered (the item moves to the bottom), and the item row gets `opacity-50` / strikethrough styling.
- [ ] **AC-6:** Given a list with a checked item, when a participant clicks the checkbox again, then the item's `checked` becomes `false`, the full sorted item list is re-rendered (the item moves back to the unchecked section), and the item loses the checked styling.
- [ ] **AC-7:** Given a list with an item, when a participant clicks the delete button, then the item row is removed from the DOM and the item is deleted from the database.
- [ ] **AC-8:** Given a participant on an active list, when they add an item, then all other SSE-connected viewers of the same list see the item appear in their list within 500 ms.
- [ ] **AC-9:** Given a participant on an active list, when they check/uncheck an item, then all other SSE-connected viewers see the item's checked state update within 500 ms.
- [ ] **AC-10:** Given a participant on an active list, when they delete an item, then all other SSE-connected viewers see the item removed from their list within 500 ms.
- [ ] **AC-11:** Given a completed or deleted list, when any participant attempts to add, check, or delete an item, then the system returns HTTP 409 Conflict with a message and the item is not modified.
- [ ] **AC-12:** Given a list with both checked and unchecked items, when the page loads, then unchecked items appear first (ordered by position), followed by checked items (ordered by position).
- [ ] **AC-13:** Given two users concurrently adding items to the same list, then both items are created successfully with deterministic ordering by position ASC then id ASC (last-write-wins, no conflict errors).
- [ ] **AC-14:** Given two users concurrently checking the same item, then the item ends up in the state of whichever check action completed last (LWW), with no error.
- [ ] **AC-15:** Given a list, when a non-participant (user not in `list_participants`) with the list code adds an item, then the item is created successfully (flat access model).
- [ ] **AC-16:** Given a list after all items have been deleted, when the page loads or receives an SSE update, then the "No items yet." placeholder is shown.
- [ ] **AC-17:** Given an SSE event arriving for a non-existent item (e.g., already deleted by another client), then the client ignores the event without JavaScript errors and the DOM is unchanged.
- [ ] **AC-18 (Empty defaults, FR-14):** Given the add-item form, when a participant submits with only the item name, then quantity and observations are stored as empty strings (not NULL).
- [ ] **AC-19 (SSE HTML payload, FR-16):** Given an item mutation on a list with SSE-connected clients, when the event is emitted, then the SSE payload includes an `html` field containing the server-rendered fragment.
- [ ] **AC-20 (XSS prevention, FR-18):** Given an add-item form, when a participant submits a name containing `<script>alert('x')</script>`, then the script is not executed and is displayed as escaped text.

## 7. Open Questions

- **Q1:** Should delete require a confirmation dialog? — **Answer: No.** Deletion is easily undone by re-adding the item. A future PRD may add undo. The delete action is immediate.
- **Q2:** Should the position field be re-indexed after deletions to avoid large gaps? — **Answer: No, deferred.** Gaps in position are harmless because `ORDER BY checked ASC, position ASC` produces a deterministic display order even with gaps. Re-indexing is unnecessary complexity for now.
- **Q3:** Should SSE events carry the full rendered HTML fragment or just JSON data for client-side rendering? — **Answer: Full rendered HTML fragment** via `hx-swap-oob`. This keeps the client-side code minimal (no client-side templating) and is consistent with the HTMX server-rendered approach (ADR-0004).
- **Q4:** Should the SSE event payload include both the item data and the rendered HTML fragment? — **Answer: Yes.** The rendered HTML fragment is used for DOM swap; the structured data is available for future client-side logic (e.g., notifications, badges).
- **Q5:** When an item is deleted, should the SSE payload include an HTML fragment with `hx-swap-oob="delete:#item-:id"` or should the delete handler return a response that instructs the requesting client to use `hx-swap="delete"`? — **Answer: SSE uses `delete` swap** via a placeholder fragment. The requesting client's HTMX handler uses `outerHTML` swap with an empty response; SSE clients receive a fragment targeting `#item-:id` with `delete` swap.
- **Q6:** Should the SSE endpoint filter events by list code, or should clients register interest in specific lists? — **Answer: SSE endpoint per list** (`/list/:code/events`). The server maintains a map from `list_code → Set[SSE-channel]` and broadcasts to the relevant set. This is consistent with ADR-0006.
- **Q7:** Should the SSE connection be separate from the page load, or should the page load return the initial event stream? — **Answer: Separate.** The page loads via a regular HTTP request (renders current state), and an independent SSE connection provides live updates. This allows the initial page to be served by a synchronous handler.

## 8. Linked ADRs

- **ADR-0003** — Data Model & Schema Design: defines `list_items` table; concurrency strategy of last-write-wins for item mutations (no version checks); flat access model.
- **ADR-0004** — HTMX: UI interactivity model used for add-item forms, check/uncheck, and delete actions; inline swaps without full page reloads.
- **ADR-0006** — SSE: real-time delivery of `item-added`, `item-updated`, `item-deleted` events to connected viewers; server-side connection map via http-kit async handlers.
- **ADR-0007** — Authentication Strategy: session cookies and `wrap-auth` middleware ensure only authenticated users can mutate items.
- **ADR-0002** — PostgreSQL: relational storage for `list_items`; SQL queries for ordered retrieval.

## 9. Linked Quint Spec

**Mandatory.** Every PRD must reference its companion Quint specification.

- **Quint Spec:** `docs/specs/0005-list-items.qnt`
- **Quint Spec Status:** proven
- **Verification Summary:** Pass — quint typecheck ✅, quint test ✅, quint run (100 samples) ✅, quint verify (depth 5) ✅

*Without a linked, proven Quint spec, this PRD may not proceed to implementation.*

---

*End of PRD. The linked Quint specification in `docs/specs/` must model every requirement marked FR-N above as a state variable, action, or invariant.*
