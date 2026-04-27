# PRD-0007: Enhanced My Lists Dashboard

| Field | Value |
|-------|-------|
| **PRD ID** | 0007 |
| **Feature** | Enhanced My Lists Dashboard |
| **Status** | draft |
| **Linked Quint Spec** | `docs/specs/0007-my-lists-dashboard.qnt` |
| **Author** | pi |
| **Date** | 2026-04-27 |

---

## 1. Problem Statement

PRD-0003 defines a basic "My Lists" dashboard as the landing page for authenticated users (FR-3, FR-4, §4.3). PRD-0004 layers the join-by-code form on top. However, the current dashboard definition lacks important UX polish: no personal greeting or user context, no empty-state guidance for new users, no at-a-glance list metadata (item counts, recency), no real-time updates when someone else creates a list the user just joined, and no copy-completed-list action directly from the dashboard. The dashboard should feel welcoming, informative, and live — not just a static index of links.

---

## 2. Goals / Non-Goals

### Goals
1. Default landing page after authentication: `/dashboard` is the first thing users see after login.
2. Personal greeting: show the user's display name and a logout option.
3. Active lists section: grid of list cards showing name, code, and metadata.
4. Past (completed) lists section: separate visual section with copy-to-new-list action.
5. Create-list form: always available at the top of the dashboard.
6. Join-by-code form: always available at the top of the dashboard.
7. Real-time updates: new lists appear via SSE without manual refresh.
8. Empty state: helpful text when a user has no lists yet.
9. Consistent visual design: match existing auth page conventions (Tailwind, indigo-600, Selmer).
10. Dashboard loads in under 500ms.
11. SSE-driven changes reflect within 1 second.

### Non-Goals
- List ownership or admin permissions (flat access, no hierarchy).
- Drag-and-drop reordering of list cards.
- List search, filter, or sort UI.
- Pagination (assumes all users have ≤50 lists initially).
- Deleting archived/completed lists.
- Favorites or pinning of lists.
- Notification badges for unseen activity.
- Multi-tenant or org-level dashboards.
- Inline item editing from the dashboard.
- Dashboard analytics or usage statistics.
- Custom themes or colour schemes.
- Mobile app or PWA-specific layout (responsive is fine, but no dedicated mobile treatment).
- Offline dashboard support.
- Dashboard-embedded SSE for anything other than list-created events (per-list SSE stays on list-view pages).

---

## 3. User Stories

1. **As a returning user, I want to see a personal greeting** so that I know I'm logged into the right account.
2. **As a user with many lists, I want to see active and completed lists separated** so that I can focus on what's current.
3. **As a user, I want to see each list's share code on the dashboard** so that I can copy and share it without opening the list.
4. **As a user, I want to create a new list directly from the dashboard** so that I don't need to navigate elsewhere.
5. **As a user who received a share code, I want to join a list directly from the dashboard** so that my workflow is frictionless.
6. **As a user, I want clicking a list card to take me to the list** so that navigation is intuitive.
7. **As a user with a completed list, I want to copy it to a new list from the dashboard** so that I can reuse it without extra steps.
8. **As a user viewing the dashboard, I want new lists to appear automatically** when someone creates one I'm participating in, so that I don't need to refresh.
9. **As a new user with no lists, I want clear guidance on what to do first** so that I'm not staring at an empty page.
10. **As a user, I want the dashboard to look clean and consistent** with the rest of the application so that it feels professional.
11. **As a returning user, I want to see at-a-glance metadata** like creation date so that I can quickly identify which list I need.

---

## 4. Detailed Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | `/dashboard` is the default landing page for authenticated users | P0 | Unauthenticated users redirect to `/login` |
| FR-2 | Dashboard shows all lists the user has access to (created or joined) | P0 | Uses `lists.db/user-lists`; excludes `status = 'deleted'` |
| FR-3 | Active lists (`status = 'active'`) are displayed in a labelled section | P0 | Section heading: "Active Lists" |
| FR-4 | Completed lists (`status = 'completed'`) are displayed in a separate section | P0 | Section heading: "Past Lists"; hidden when empty |
| FR-5 | Each list card displays: list name, share code, and creation timestamp | P1 | Name is a clickable link to `/list/:code` |
| FR-6 | "Create a New List" form with a name field and submit button | P0 | Posts to `POST /lists`; name required, ≤100 chars |
| FR-7 | "Join an Existing List" form with a code field and submit button | P0 | Posts to `POST /join`; follows PRD-0004 validation rules |
| FR-8 | Completed list cards include a "Copy as New List" button | P1 | Triggers `GET /list/:code/copy` (PRD-0006 copy-list-page modal) |
| FR-9 | Dashboard receives SSE `list-created` events and reloads to reflect new lists | P1 | Uses `broadcast-dashboard!` from `comprineas.lists.sse` |
| FR-10 | New users with no lists see an empty-state message | P1 | Message: "No active lists yet. Create one or join one above!" |

### 4.2 Data Requirements

No new database tables or columns are required. The dashboard reuses existing entities:

**`lists` table** (from PRD-0003):
- `id`, `code`, `name`, `status`, `created_by`, `created_at`, `completed_at`, `version`

**`list_participants` table** (from PRD-0003):
- `list_id`, `user_id`, `joined_at`

**`users` table** (from PRD-0002):
- `id`, `display_name`

**Query pattern** for dashboard data:
```sql
SELECT DISTINCT l.* FROM lists l
LEFT JOIN list_participants lp ON l.id = lp.list_id
WHERE (l.created_by = ? OR lp.user_id = ?)
  AND l.status != 'deleted'
ORDER BY l.created_at DESC;
```

**SSE channel**: The existing `broadcast-dashboard!` function sends events to a special `"dashboard"` channel key in the `sse-channels` atom. Dashboard pages connect to `/dashboard/events` (to be routed per this PRD).

### 4.3 UI/UX Requirements

**Layout:**
1. **Header bar**: "Comprineas" brand (left), "Hi, {display_name}" + logout button (right). Styled as a white bar with bottom border shadow.
2. **Two-panel forms area** (below header): side-by-side "Create a New List" (left) and "Join an Existing List" (right). Uses CSS grid: `md:grid-cols-2`.
3. **Active Lists section**: grid of cards (`sm:grid-cols-2 lg:grid-cols-3`). Each card is a white rounded container with border and hover shadow.
4. **Past Lists section** (below active): same grid layout. Cards have reduced opacity (`opacity-75`) and a "Copy as New List" button styled with `bg-indigo-50`.
5. **Footer**: "Comprineas — shared grocery lists, made simple." centred, small text.

**List card design:**
- List name: bold, indigo hover colour, truncates long names.
- Code badge: `bg-gray-100` small badge, monospace font.
- Creation date: small grey text below.

**Empty state:**
- Centred container, no lists icon or just text: "No active lists yet. Create one or join one above!"

**SSE behaviour:**
- SSE connection opens on page load to `/dashboard/events`.
- On `list-created` event: triggers `hx-get="/dashboard"` with `hx-target="body" hx-swap="outerHTML"` to reload the full dashboard.

**Error/edge case states:**
- If `user-lists` returns empty: show empty state for active, hide past section.
- If only completed lists exist: show past section, active section shows empty state.
- SSE connection failure: HTMX automatically reconnects (SSE extension default behaviour).
- Network offline: no special handling — page simply doesn't update until reconnect.

### 4.4 Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Dashboard page load time (server render) | < 500ms p95 |
| NFR-2 | SSE event delivery latency | < 1s from broadcast to DOM update |
| NFR-3 | Dashboard query uses indexed columns | `lists.created_by`, `list_participants.user_id` (covered by ADR-0003 schema) |
| NFR-4 | Dashboard works correctly with 0, 1, and 50+ lists | No pagination; all lists rendered in one page |
| NFR-5 | Security: dashboard only shows lists the user participates in | Enforced server-side via `user-lists` query |
| NFR-6 | SSE connection respects per-list broadcast isolation | Dashboard SSE channel is separate from per-list channels |
| NFR-7 | Accessibility: semantic HTML, ARIA labels on form inputs, keyboard-navigable | WCAG 2.1 AA where applicable |

---

## 5. Out of Scope

1. List search, filter, or sort controls.
2. Pagination or infinite scroll for large list counts.
3. List owner permissions or role-based access control.
4. Deleting archived/completed lists.
5. Favouriting, pinning, or reordering list cards.
6. Notification badges or unread-activity indicators.
7. Dashboard-level statistics (total items across all lists, etc.).
8. Multi-tenant or organisation-level dashboards.
9. Dashboard customisation (themes, layouts, widgets).
10. Offline/PWA dashboard support.
11. Inline item editing or preview from the dashboard.
12. Sorting list cards by date, name, or status.
13. Bulk actions on multiple lists (delete multiple, archive multiple).
14. Dashboard-level "last viewed" or "recently accessed" indicators.

---

## 6. Acceptance Criteria

| ID | Criteria |
|----|----------|
| AC-1 | **Given** an authenticated user, **when** they navigate to `/dashboard`, **then** they see the dashboard page with header, forms, and their lists. |
| AC-2 | **Given** an unauthenticated request to `/dashboard`, **when** the page loads, **then** the user is redirected to `/login` (303). |
| AC-3 | **Given** a user with active and completed lists, **when** they view the dashboard, **then** active lists appear before past lists with clear section headings. |
| AC-4 | **Given** a user with only completed lists, **when** they view the dashboard, **then** the active section shows empty state and the past section shows their completed lists. |
| AC-5 | **Given** a user on the dashboard, **when** they submit the create-list form with a name, **then** a new list is created and they are redirected to `/list/:code`. |
| AC-6 | **Given** a user on the dashboard, **when** they submit the create-list form without a name, **then** an error is shown and no list is created. |
| AC-7 | **Given** a user on the dashboard, **when** they submit the join form with a valid code, **then** they are redirected to that list's page. |
| AC-8 | **Given** a user on the dashboard, **when** they submit the join form with an invalid code, **then** an inline error message appears without page navigation. |
| AC-9 | **Given** a user on the dashboard, **when** they click a list card, **then** they navigate to `/list/:code`. |
| AC-10 | **Given** a user with a completed list, **when** they click "Copy as New List", **then** the copy modal appears (PRD-0006). |
| AC-11 | **Given** a user viewing the dashboard, **when** another user creates a list the first user participates in, **then** the dashboard updates via SSE within 1 second. |
| AC-12 | **Given** a new user with no lists, **when** they view the dashboard, **then** they see "No active lists yet. Create one or join one above!" |
| AC-13 | **Given** a logged-in user, **when** the dashboard loads, **then** the header shows "Hi, {display_name}" and a logout button. |
| AC-14 | **Given** a user on the dashboard, **when** they click logout, **then** their session is destroyed and they are redirected to `/login`. |
| AC-15 | **Given** a list that was completed by another participant, **when** the user refreshes the dashboard, **then** the list appears in the past section instead of active. |
| AC-16 | **Given** the dashboard page, **when** it renders, **then** the total render time is under 500ms (server-side). |
| AC-17 | **Given** an SSE `list-created` event, **when** it fires, **then** the DOM update completes within 1 second of broadcast. |
| AC-18 | **Given** a user who has joined 50+ lists, **when** they view the dashboard, **then** all lists render correctly without errors (no pagination required). |

---

## 7. Open Questions

| # | Question | Answer / Decision |
|---|----------|-------------------|
| Q1 | Should the SSE `list-created` event be filtered per-user on the server, or broadcast to all dashboard clients with client-side filtering? | Server-side filtering: `broadcast-dashboard!` should only send to users who are participants. For MVP, a simple full-page reload on any `list-created` event is acceptable — the server already filters via `user-lists`. |
| Q2 | Should the past lists section collapse/expand, or always be shown? | Always shown when non-empty; hidden when empty. No collapse/expand toggle needed for MVP. |
| Q3 | Should timestamps be relative ("2 hours ago") or absolute? | Absolute for now ("2026-04-27 13:45"). Relative timestamps can be a future enhancement. |
| Q4 | Should the dashboard SSE connect immediately on page load or lazily? | Immediately on page load (via `hx-sse="connect:/dashboard/events"` on `<body>`). This matches the list-view SSE pattern. |
| Q5 | How should the "Copy as New List" button work — inline or modal? | Use the existing modal from PRD-0006 (`GET /list/:code/copy` → `copy-list-page`). This avoids duplicating the copy form. |
| Q6 | Should the dashboard show item counts per list? | Yes, if available from the current query. PRD-0005's `items.db/count-items-by-list` can be called per-list. For performance, batch-query or leave as a future optimisation. |

---

## 8. Linked ADRs

| ADR | Relevance |
|-----|-----------|
| ADR-0002 (PostgreSQL) | Dashboard data queried from PostgreSQL |
| ADR-0003 (Data Model) | Reuses existing `lists`, `list_participants` schema |
| ADR-0004 (HTMX) | HTMX drives form submissions, navigation, and SSE |
| ADR-0006 (SSE) | Dashboard real-time updates via dedicated SSE channel |
| ADR-0007 (Authentication) | Session middleware provides `current-user` for personalisation |

---

## 9. Linked Quint Spec

**Spec:** `docs/specs/0007-my-lists-dashboard.qnt`
**Status:** draft (not yet written)
**Verification pending:** `quint typecheck → test → run → verify`

The Quint specification must model:
- State: `var lists: Set[List]`, `var participants: Set[Participation]`, `var dashboardClients: Set[User]`
- Actions: `init`, `createList`, `joinList`, `completeList`, `copyList`, `navigateToDashboard`
- Invariants: only participant-accessible lists shown, active/completed segregation, empty state for new users
- All FR-1 through FR-10 and AC-1 through AC-18 must have corresponding Quint entities per the 1:1 parity rule.

---

*End of PRD-0007.*
