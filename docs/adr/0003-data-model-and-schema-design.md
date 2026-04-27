---
status: accepted
date: 2026-04-25
decision-makers: user (kschltz), agent
consulted: {}
informed: {}
---

# Data Model and Schema Design

## Context and Problem Statement

ADR-0002 selected PostgreSQL as the primary database. We now need to design the concrete relational schema for the application's domain: users, shopping lists (identified by custom shareable codes), list items, list participation tracking, and completed list archives.

The data model must support:
- Flat access: anyone with a list code has full access (no ownership, no permission hierarchy; the `created_by` column on `lists` is metadata only for UX convenience).
- Real-time collaboration: multiple users may concurrently add, check, or delete items. Item-level mutations use **last-write-wins** (no version check) since the grocery list domain accepts overwriting. List-level metadata changes (rename, complete, delete) use optimistic locking.
- Application-level concurrency control, since MVCC handles transaction isolation but not application-level editing conflicts (deferred from ADR-0002).

## Decision Drivers

- ADR-0002 chose PostgreSQL — we now design its schema.
- Flat access model: no list ownership, no permission hierarchy. The `created_by` column on `lists` tracks who created the list for "my lists" UX convenience but **does not confer ownership rights**.
- Lists are identified by human-readable custom codes (the join key).
- Items have: name, quantity (VARCHAR for "a few", "1 kg"), observations (TEXT), checked (BOOLEAN DEFAULT false), position (INTEGER for ordering), updated_at (TIMESTAMPTZ).
- `list_participants` tracks recent viewers (for presence display), **not access control**. Anyone with the code has full access regardless of participation rows.
- Completed lists become archives (historical records). Active `list_items` rows are **copied** to the archive and **retained** for live querying.
- Need an application-level concurrency strategy: last-write-wins for item mutations; optimistic locking on `lists.version` for list-level metadata changes.
- `jsonb` available as denormalization escape hatch per ADR-0002: consider adding `metadata jsonb` to `list_items` for future flexible fields without schema migration.

## Considered Options

### 1. Fully Normalized Relational Schema

Separate tables for each entity with foreign-key constraints.

- `lists`: id SERIAL PRIMARY KEY, code VARCHAR(6) UNIQUE NOT NULL, name VARCHAR(100), status VARCHAR(20) CHECK (status IN ('active','completed','deleted')), version INTEGER DEFAULT 1, created_by INTEGER REFERENCES users(id) ON DELETE SET NULL, created_at TIMESTAMPTZ DEFAULT now(), completed_at TIMESTAMPTZ, updated_at TIMESTAMPTZ DEFAULT now()
- `list_items`: id SERIAL PRIMARY KEY, list_id INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE, name VARCHAR(255) NOT NULL, quantity VARCHAR(50), observations TEXT, checked BOOLEAN DEFAULT false, position INTEGER NOT NULL, created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now()
  ￫ observations has a 2000-character application-level limit (PRD-0005)
- `list_participants`: id SERIAL PRIMARY KEY, list_id INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, joined_at TIMESTAMPTZ DEFAULT now()
- `completed_lists`: id SERIAL PRIMARY KEY, original_list_id INTEGER REFERENCES lists(id) ON DELETE SET NULL, code VARCHAR(6), name VARCHAR(100), completed_at TIMESTAMPTZ, archived_data JSONB
  ￫ archived_data schema: JSONB array of {name, quantity, observations, checked, position} objects (PRD-0006)

**Concurrency strategy:** Item mutations (add, check, delete items) do **not** increment `lists.version`; they use **last-write-wins** since concurrent edits to different items are compatible and overwriting the same item's checked state is acceptable UX. List metadata changes (rename, complete, delete) **do** increment `lists.version` and use optimistic locking (`UPDATE lists SET ... WHERE id = ? AND version = ?`).

- Full ACID integrity via foreign keys.
- Easy querying, indexing, aggregation.
- Optimistic locking via `version` column on `lists`.

### 2. Hybrid: Normalized Lists with jsonb Items

`lists` table holds metadata; items stored as `jsonb` array inside `lists`.

- Fewer JOINs for list retrieval (single row).
- Atomic read of entire list state.
- `GIN` index on `jsonb` for item searching.
- Loses referential integrity on individual items (no FK constraints on array elements).
- Concurrent item edits require locking the entire list row.

### 3. Fully Denormalized Document Model

Single `lists` table with all data embedded as JSON.

- Maximum read performance: one row per list.
- Zero JOINs.
- No referential integrity at all.
- Difficult to query across lists (e.g., "all lists by user").
- Lock contention: any edit locks the entire document.

### 4. PostgreSQL System-Versioned Tables (Temporal)

Use PostgreSQL temporal features or triggers for history tracking.

- Automatic audit trail of all changes.
- Time-travel queries native to the database.
- Complex to implement with standard tools; less mature in PostgreSQL than in SQL Server.
- Overkill for the archive requirement (completed lists need snapshot, not full history).

## Decision Outcome

Chosen option: **"Fully Normalized Relational Schema"** (Option 1), because:
- Referential integrity matters for data correctness (lost items are real bugs).
- The relational model maps cleanly to the domain (users create lists, lists have items, users participate in lists).
- Optimistic locking on the `lists` row solves application-level concurrent edits without pessimistic serialization.
- `jsonb` is reserved as a future escape hatch for flexible item metadata, not used for primary item storage.
- Completed lists are archived as a snapshot (`archived_data jsonb`) in `completed_lists`, capturing the final state without needing full temporal history.

### Consequences

- Good, because optimistic locking (`version` column on `lists`) handles list-level metadata changes gracefully.
- Good, because **last-write-wins for item mutations** avoids false conflicts when two users edit different items simultaneously.
- Good, because indexing on `lists.code`, `list_items.list_id`, `list_participants.user_id`, and `list_items(list_id, position)` enables fast lookups.
- Good, because the schema is extensible: adding `item_categories`, `list_templates`, or `metadata JSONB` to `list_items` follows the same pattern.
- Good, because `completed_lists.archived_data` (jsonb) captures a full snapshot while active `list_items` rows remain queryable.
- Bad, because retrieving a full list requires JOINs (`lists` + `list_items` + `list_participants`).
- Bad, because schema changes require migrations (addressed in ADR-0002).
- Bad, because item-level optimistic locking was **explicitly rejected**: list-level locking handles metadata, LWW handles item mutations. Item-level version is deferred to a future schema migration if conflict rates prove unacceptable.
- Neutral, because `jsonb` remains available if we later need flexible item metadata without schema migrations; a `metadata JSONB` column on `list_items` is the planned granularity.

### Confirmation

The decision is confirmed by:
- A `migratus` migration creating `users`, `lists`, `list_items`, `list_participants`, and `completed_lists` tables with the exact columns and data types specified above.
- Foreign-key constraints linking `list_items.list_id → lists.id` (ON DELETE CASCADE), `list_participants.list_id → lists.id` (ON DELETE CASCADE), `list_participants.user_id → users.id` (ON DELETE CASCADE), and `lists.created_by → users.id` (ON DELETE SET NULL).
- A unique index on `lists.code` for fast lookups.
- A composite index on `list_items(list_id, position)` for ordered item retrieval.
- A partial index on `list_items(list_id)` WHERE `checked = false` for "active items" queries.
- A `GIN` index on `completed_lists.archived_data` using `jsonb_path_ops` for snapshot existence queries (`@>`).
- A concurrency test: two simultaneous requests to check different items on the same list both succeed (last-write-wins); two simultaneous requests to rename the list produce exactly one success and one conflict.
- Successful REPL connectivity test via `next.jdbc` confirming all tables, indexes, and constraints exist.

## Pros and Cons of the Options

### Fully Normalized Relational Schema

- Good, because full referential integrity via foreign keys.
- Good, because optimistic locking naturally fits the relational model.
- Good, because SQL is expressive for reporting queries (e.g., "most popular items").
- Bad, because JOINs are required for full list retrieval.
- Bad, because schema evolution requires migrations.

### Hybrid: Normalized Lists with jsonb Items

- Good, because single-row list retrieval — no JOINs for items.
- Good, because atomic read of entire list state.
- Bad, because no referential integrity on individual items.
- Bad, because concurrent item edits must lock the entire list row.
- Bad, because querying items across lists (e.g., "all lists containing Milk") requires `jsonb` GIN queries, which are less efficient than indexed relational columns.

### Fully Denormalized Document Model

- Good, because maximum read performance; single row per list.
- Bad, because zero referential integrity.
- Bad, because querying across lists is difficult.
- Bad, because any edit locks the entire document — poor concurrent edit UX.

### PostgreSQL System-Versioned Tables

- Good, because automatic audit history.
- Good, because time-travel queries without application logic.
- Bad, because complex implementation; less mature tooling.
- Bad, because overkill for the archive snapshot requirement.

## More Information

- PostgreSQL FK constraints: https://www.postgresql.org/docs/current/ddl-constraints.html
- Optimistic locking patterns: https://www.postgresql.org/docs/current/transaction-iso.html
- `jsonb` indexing: https://www.postgresql.org/docs/current/datatype-json.html
- Schema design decisions for real-time collaboration (defer concurrency strategy specifics to feature PRDs + Quint specs).
