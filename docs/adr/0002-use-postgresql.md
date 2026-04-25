---
status: proposed
date: 2026-04-25
decision-makers: user (kschltz), agent
consulted: {}
informed: {}
---

# Use PostgreSQL as the Primary Database

## Context and Problem Statement

The application requires a persistent store for relational data: users (email-based authentication), shopping lists (identified by custom code), list items (with name, quantity, observations, and checked state), and completed list archives. We need ACID guarantees for concurrent modifications by multiple users viewing the same list in real time. The user has explicitly requested PostgreSQL.

We want to record why PostgreSQL is the right choice versus available alternatives, given the operational and data-modeling needs of a small but concurrent collaborative application.

## Decision Drivers

- We need a relational model with foreign-key constraints: users own lists, lists contain items, completed lists become orders/archives.
- Concurrent access: multiple users may add, check, or delete items on the same list simultaneously. We need transactions that prevent lost updates and ensure consistency.
- The user explicitly prefers PostgreSQL (stated in project brief).
- The app is lightweight but data integrity matters: a lost item in a grocery list is a real bug.
- We may need JSON support for flexible metadata in the future (e.g., item categories) without a full schema migration.
- Developer familiarity: the team is comfortable with SQL and the PostgreSQL ecosystem.

## Considered Options

### 1. PostgreSQL

A mature, open-source relational database with full ACID support, rich data types, and strong JSON capabilities.

- Full SQL support, foreign keys, constraints, triggers.
- MVCC handles concurrent reads/writes without heavy locking.
- `jsonb` for schema-flexible extensions if needed later.
- Excellent integration with Clojure via `next.jdbc`.

### 2. SQLite

A serverless, zero-configuration embedded SQL database.

- Extremely simple to deploy; no separate server process.
- Single-writer lock can be a bottleneck under concurrent writes.
- No native network access; would require a separate server or WAL mode workarounds.
- Fine for single-user or low-concurrency, but not designed for real-time collaboration.

### 3. MySQL / MariaDB

Widely deployed open-source relational databases.

- Full relational support, good performance.
- Some differences in SQL dialect and MVCC behavior compared to PostgreSQL.
- No specific feature advantage over PostgreSQL for this project's scale.

### 4. MongoDB

A document-oriented NoSQL database.

- Flexible schema; easy to embed list items inside list documents.
- Loses ACID transaction guarantees across documents without careful design.
- Adds operational complexity (running a separate MongoDB instance or service).
- The data model is inherently relational (users → lists → items); NoSQL brings no real benefit here.

## Decision Outcome

Chosen option: **"PostgreSQL"**, because the user explicitly requested it, and it satisfies all decision drivers: full ACID transactions for concurrent collaborative edits, a relational model that maps naturally to our domain, and `jsonb` as an escape hatch if we need flexibility later. SQLite is too limited for concurrent writes; MySQL offers no advantage at this scale; MongoDB introduces complexity where a relational model is the better fit.

### Consequences

- Good, because ACID transactions guarantee consistency when multiple users modify the same list simultaneously.
- Good, because the relational schema (users, lists, items, orders) maps cleanly to SQL tables with foreign-key constraints.
- Good, because `next.jdbc` in Clojure has first-class PostgreSQL support.
- Good, because `jsonb` allows us to add flexible metadata later without schema migrations.
- Bad, because it requires running a PostgreSQL server (or managed instance) rather than embedding a database.
- Bad, because schema migrations (e.g., Migratus) are required for schema evolution, adding a step to deployment.

### Confirmation

The decision is confirmed by a running PostgreSQL instance accessible to the application, a `migratus` migration creating the `users`, `lists`, `list_items`, and `orders` tables, and successful connectivity tests from the Clojure REPL via `next.jdbc/get-connection`.

## Pros and Cons of the Options

### PostgreSQL

- Good, because full ACID + MVCC + foreign keys = data integrity under concurrency.
- Good, because `jsonb` gives NoSQL flexibility inside a relational database.
- Good, because PostgreSQL is the standard recommendation for Clojure projects using JDBC.
- Neutral, because it requires running a separate server process.
- Bad, because schema changes require migration tooling.

### SQLite

- Good, because zero-configuration deployment is ideal for single-user or embedded use cases.
- Bad, because single-writer lock limits concurrent real-time collaboration.
- Bad, because no native network access complicates multi-process or containerized deployments.

### MySQL / MariaDB

- Good, because widely available and well-documented.
- Neutral, because feature parity with PostgreSQL for this scale; no compelling advantage.
- Bad, because SQL dialect differences add friction for Clojure/JDBC users accustomed to PostgreSQL.

### MongoDB

- Good, because flexible document model avoids schema migrations early on.
- Bad, because ACID across documents requires careful design (or transactions, which add overhead).
- Bad, because relational data in a document store often leads to application-level joins.
- Bad, because it adds operational complexity (separate service) for no clear gain.

## More Information

- PostgreSQL docs: https://www.postgresql.org/docs/
- Migratus (Clojure migration tool): https://github.com/yogthos/migratus
- next.jdbc docs: https://cljdoc.org/d/seancorfield/next.jdbc
- If we later need horizontal read scaling, we can add read replicas or connection-pool-level routing without changing the application model.
- Schema design decisions will be captured in ADR-0003 (Data Model and Schema Design).
