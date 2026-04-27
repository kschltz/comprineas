# Create and Manage Shared Lists

| PRD ID | Feature | Status | Linked Quint Spec | Author | Date |
|--------|---------|--------|-------------------|--------|------|
| 0003 | Create and Manage Shared Lists | **accepted** | `docs/specs/0003-shared-lists.qnt` | agent | 2026-04-27 |

---

## 1. Problem Statement

Users can now authenticate (PRD-0001, PRD-0002) but have nothing to do after logging in. The core value proposition of Comprineas is creating and collaborating on shared grocery lists. A user needs to create a list (which generates a shareable code), view and rename their lists, and eventually mark a list as completed. Other users will join lists by code (covered in PRD-0004), but the creation and management of lists must exist first.

## 2. Goals

- Allow a logged-in user to create a new shared list with a name and an auto-generated shareable code.
- Display a "My Lists" dashboard showing lists the user created or joined, with status indicators.
- Allow the list creator (or any participant) to rename an active list.
- Allow any participant to mark an active list as completed, which archives it and makes it read-only.
- Allow any participant to delete an active list (soft delete — marks it deleted, hides from all views).
- Generate human-readable, collision-resistant share codes (e.g., `abc123`, not UUIDs).
- Push list metadata changes (rename, complete, delete) to all connected viewers via SSE in real-time.
- Maintain data integrity per ADR-0003: optimistic locking on list metadata, last-write-wins on items (items PRD-0005).

### Non-Goals

- Joining a list by code (PRD-0004).
- Adding, checking, or deleting items on a list (PRD-0005).
- Copying a completed list as a starting point for a new list (PRD-0006).
- List participant presence display or management beyond creation tracking.
- List ownership or permission levels (flat access model per ADR-0003).
- Email invitations to lists.
- Search across lists.
- Pagination on the dashboard (deferred until proven necessary).

## 3. User Stories

- As a logged-in user, I want to create a new list so that I can start adding items to it.
- As a logged-in user, I want to see a dashboard of my lists (created and joined) so that I can find them easily.
- As a participant on a list, I want to rename the list so that it's easier to identify.
- As a participant on a list, I want to complete the list when I'm done shopping so that it's archived.
- As a participant on a list, I want to delete the list so that it's removed from all views.
- As a logged-in user, I want to copy the shareable code of a list so that I can share it with others.

## 4. Detailed Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | The system shall provide a "Create List" form accepting a list name (1–100 characters). | must | |
| FR-2 | On creation, the system shall generate a unique, human-readable share code (6 alphanumeric characters, collision-resistant), create the list in the database with status "active", add the creator as a participant, and redirect to the list view page. | must | Per ADR-0003: `lists` table with `code` UNIQUE, `status` default 'active'. |
| FR-3 | The system shall display a "My Lists" dashboard at `/` (home page for logged-in users) showing all lists the user created or joined, grouped by status (active first, completed second). | must | |
| FR-4 | Each list card on the dashboard shall display: list name, share code, status badge (active/completed), item count, and created date. | must | |
| FR-5 | The system shall provide a "Rename List" action for active lists accepting a new name (1–100 characters) using optimistic locking on the list's `version` column. | must | Per ADR-0003: `UPDATE lists SET name=?, version=version+1 WHERE id=? AND version=?`. `updated_at` is set to `now()` on every metadata mutation (rename, complete, delete). |
| FR-6 | If a rename conflicts (version mismatch), the system shall return an error suggesting a refresh. | must | |
| FR-7 | The system shall provide a "Complete List" action that marks an active list as completed (`status='completed'`), sets `completed_at`, archives item data to `completed_lists`, and increments the list version. | must | Per ADR-0003: active `list_items` rows are retained; a copy goes to `completed_lists.archived_data`. Archival is performed transactionally with the status update to ensure no items are lost. |
| FR-8 | The system shall provide a "Delete List" action that soft-deletes the list (`status='deleted'`) and increments the version. Deleted lists are excluded from all views. | must | |
| FR-9 | Share codes shall consist of 6 alphanumeric characters (a-z, 0-9), generated randomly with at least 36 bits of entropy, and guaranteed unique via database UNIQUE constraint. | must | Implementation: select 6 characters uniformly from `[a-z0-9]` using `java.security.SecureRandom`. 36^6 ≈ 2.18B combinations (~31 bits of entropy). The UNIQUE DB constraint is the final collision safeguard, with up to 3 retry attempts on collision. | |
| FR-10 | The system shall broadcast list metadata changes (rename, complete, delete) to all SSE-connected viewers of that list via an event with type `list-updated` and payload containing the updated list fields. | must | Per ADR-0006: SSE via http-kit async handlers. |
| FR-11 | Completed and deleted lists shall be read-only: the system shall reject item mutations and rename/complete/delete actions on non-active lists with a 409 Conflict response. | must | |
| FR-12 | The "My Lists" dashboard shall be the default landing page (`/`) for authenticated users. Unauthenticated users shall be redirected to `/login`. | must | |

### 4.2 Data Requirements

Per ADR-0003, the schema uses these tables:

- **`lists` (new table):**
  - `id` SERIAL PRIMARY KEY
  - `code` VARCHAR(6) UNIQUE NOT NULL — shareable join code
  - `name` VARCHAR(100) NOT NULL
  - `status` VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'completed', 'deleted'))
  - `version` INTEGER NOT NULL DEFAULT 1 — optimistic locking
  - `created_by` INTEGER REFERENCES users(id) ON DELETE SET NULL
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `completed_at` TIMESTAMPTZ — NULL until completed
  - `updated_at` TIMESTAMPTZ DEFAULT now()
  - INDEX on (`code`) for fast lookups
  - INDEX on (`status`) for dashboard filtering
  - INDEX on (`created_by`) for "my lists" queries

- **`list_participants` (new table):**
  - `id` SERIAL PRIMARY KEY
  - `list_id` INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE
  - `user_id` INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE
  - `joined_at` TIMESTAMPTZ DEFAULT now()
  - UNIQUE(`list_id`, `user_id`)

- **`completed_lists` (new table):**
  - `id` SERIAL PRIMARY KEY
  - `original_list_id` INTEGER REFERENCES lists(id) ON DELETE SET NULL
  - `code` VARCHAR(6)
  - `name` VARCHAR(100)
  - `completed_at` TIMESTAMPTZ NOT NULL
  - `archived_data` JSONB — snapshot of items at completion time
  - INDEX on (`original_list_id`)

- **`list_items` (stub table, expanded in PRD-0005):**
  - `id` SERIAL PRIMARY KEY
  - `list_id` INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE
  - `name` VARCHAR(255) NOT NULL
  - `quantity` VARCHAR(50)
  - `observations` TEXT
  - `checked` BOOLEAN DEFAULT false
  - `position` INTEGER NOT NULL
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `updated_at` TIMESTAMPTZ DEFAULT now()
  - INDEX on (`list_id`, `position`) for ordered retrieval
  - INDEX on (`list_id`) WHERE `checked = false` for active items

### 4.3 UI / UX Requirements

- **Home page (`/`):**
  - If not logged in: redirect to `/login`.
  - If logged in: show "My Lists" dashboard.
  - "Create List" button prominently at the top.
  - Lists displayed in cards, active lists first, completed lists in a separate section below.
  - Each card shows: name, share code (with copy button), status badge, item count, date.
  - Active list cards have: Rename, Complete, Delete actions.
  - Completed list cards have: View (read-only) action only.

- **Create List dialog/page (`/lists/new` or modal):**
  - Single text input: list name (1–100 characters).
  - "Create" button.
  - On success: redirect to the list view page (`/list/:code`).

- **List view page (`/list/:code`):**
  - Shows list name (editable for active lists), share code (with copy button), status.
  - Item list area (populated by PRD-0005; for now, shows "No items yet").
  - Action buttons: Rename, Complete, Delete (for active lists); View-only for completed/deleted.
  - SSE connection for real-time updates.

- **Rename List (HTMX inline edit or modal):**
  - Text input pre-filled with current name.
  - "Save" and "Cancel" buttons.
  - On conflict: error message "This list was modified by someone else. Please refresh."

- **Complete List confirmation:**
  - "Are you sure? This will archive the list and make it read-only."
  - On success: list moves to "Completed" section in dashboard.

- **Delete List confirmation:**
  - "Are you sure? This cannot be undone."
  - On success: list disappears from dashboard.

### 4.4 Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Share code generation must be collision-resistant (birthday problem): with 36 characters, 6-char codes give ~2 billion combinations; database UNIQUE constraint ensures absolute uniqueness. | security |
| NFR-2 | List creation must complete in < 500 ms (p95). | performance |
| NFR-3 | Dashboard load must complete in < 300 ms (p95) for ≤ 100 lists per user. | performance |
| NFR-4 | SSE events for list metadata changes must reach all connected clients within 1 second. | real-time |
| NFR-5 | Optimistic locking conflicts on rename must be clearly communicated to the user with an actionable error. | usability |

## 5. Out of Scope

- Joining a list by code (PRD-0004).
- Adding, checking, deleting items (PRD-0005).
- Copying a completed list as a new list (PRD-0006).
- List ownership or role-based permissions (flat access per ADR-0003).
- Email invitations.
- Search across lists.
- Pagination or infinite scroll on dashboard.
- Real-time item updates (deferred to PRD-0005).
- List categories, tags, or sorting.

## 6. Acceptance Criteria

- [ ] **AC-1:** Given a logged-in user, when they submit the create list form with a name (1–100 characters), then a list is created with a unique 6-char code, status "active", the user is added as a participant, and they are redirected to `/list/:code`.
- [ ] **AC-2:** Given a create list form, when the name is empty or exceeds 100 characters, then an error "List name must be 1–100 characters." is shown.
- [ ] **AC-3:** Given the home page, when a logged-in user visits `/`, then a dashboard shows all lists they created or joined, grouped by status (active first).
- [ ] **AC-4:** Given an active list, when a participant renames it to a valid name, then the list name is updated and all SSE-connected viewers see the change within 1 second.
- [ ] **AC-5:** Given an active list, when two users attempt to rename simultaneously, then exactly one succeeds and the other receives a conflict error suggesting refresh (optimistic locking via version).
- [ ] **AC-6:** Given an active list, when a participant clicks "Complete", then the list status becomes "completed", `completed_at` is set, item data is archived to `completed_lists`, and all SSE viewers are notified.
- [ ] **AC-7:** Given an active list, when a participant clicks "Delete", then the list status becomes "deleted" and it is hidden from all dashboard views.
- [ ] **AC-8:** Given a completed or deleted list, when any action (rename, complete, delete, add item) is attempted, then the system returns 409 Conflict.
- [ ] **AC-9:** Given two list creations that generate the same random code, then the database UNIQUE constraint on `code` prevents the collision and the system retries with a new code (up to 3 attempts).
- [ ] **AC-10:** Given an unauthenticated user, when they visit `/`, then they are redirected to `/login`.
- [ ] **AC-11:** Given the dashboard, when the user clicks the share code Copy button, then the code is copied to the clipboard.
- [ ] **AC-12:** Given a logged-in user with existing lists, when they visit `/`, then the dashboard displays all their lists (created and joined) grouped by status (active first).
- [ ] **AC-13:** Given a list with items, when the dashboard is displayed, then each list card shows the list name, share code, status badge, item count, and created date.

## 7. Open Questions

- **Q1:** Should list codes use only lowercase letters + digits, or also uppercase (reducing collision risk but making codes case-sensitive)? — **Answer: Lowercase + digits only** (`a-z0-9`, 36 chars). Case-insensitive codes avoid user confusion.
- **Q2:** Should "Complete List" archive items immediately or lazily? — **Answer: Immediately** — snapshot at completion time into `completed_lists.archived_data`.
- **Q3:** Should deleting a list cascade-delete all items and participants, or soft-delete only? — **Answer: Soft-delete** (`status='deleted'`). Active items and participants are retained in the database but hidden from all user-facing queries. The `ON DELETE CASCADE` on foreign keys only fires if the row is actually deleted from `lists`.
- **Q4:** Should the SSE broadcast for metadata changes include the full list object or just a diff? — **Answer: Full list object** (id, code, name, status, version). Simpler for clients, small payload.

## 8. Linked ADRs

- **ADR-0003** — Data Model & Schema Design: defines `lists`, `list_items`, `list_participants`, `completed_lists` tables, optimistic locking via `version`, and last-write-wins for items.
- **ADR-0004** — HTMX: UI interactivity model for forms and HTMX attributes.
- **ADR-0006** — SSE: real-time delivery of metadata changes to connected viewers.
- **ADR-0007** — Authentication Strategy: session cookies and `wrap-auth` middleware provide `current-user`.
- **ADR-0002** — PostgreSQL: relational storage, migrations, indexing.

## 9. Linked Quint Spec

**Mandatory.** Every PRD must reference its companion Quint specification.

- **Quint Spec:** `docs/specs/0003-shared-lists.qnt`
- **Quint Spec Status:** **proven**
- **Verification Summary:** All invariants pass — `invValidStatus`, `invValidVersion`, `invCreatorIsParticipant`, `invCompletedAtSet`, `invNonActiveVersionIncremented` verified via `quint typecheck` → `quint test` → `quint run` (100 samples) → `quint verify` (depth 7+).

*Without a linked, proven Quint spec, this PRD may not proceed to implementation.*