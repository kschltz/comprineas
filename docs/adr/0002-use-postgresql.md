---
status: accepted
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

- User preference: PostgreSQL was explicitly requested. This ADR documents why PostgreSQL satisfies the project's needs, not an open evaluation.
- We need a relational model with foreign-key constraints: users create lists but do not own them (flat access via shareable code), lists contain items, completed lists are archived as historical records.
- Concurrent access: multiple users may add, check, or delete items on the same list simultaneously. Database transaction isolation is necessary but not sufficient; application-level concurrency strategy is deferred to ADR-0003.
- The app is lightweight but data integrity matters: a lost item in a grocery list is a real bug.
- Lists are identified by a human-readable custom code that must be unique and fast to look up.
- We may need JSON support for flexible metadata in the future (e.g., item categories) without a full schema migration, or as a denormalization option for list items.
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
- WAL mode allows concurrent reads and thousands of TPS — technically sufficient for the expected write volume (<10 writes/second peak, <5 concurrent writers per list).
- Single-writer design serializes write transactions; at human interaction speeds the lock duration is sub-millisecond, but it still limits headroom.
- No native network access; would require a separate server or WAL mode workarounds in containerized deployments.
- Fine for this workload, but rejected because PostgreSQL provides headroom, team familiarity, and a clearer migration path if scale grows.

### 3. MySQL / MariaDB

Widely deployed open-source relational databases.

- Full relational support, good performance.
- Some differences in SQL dialect and MVCC behavior compared to PostgreSQL.
- No specific feature advantage over PostgreSQL for this project's scale.

### 4. MongoDB

A document-oriented NoSQL database.

- Flexible schema; easy to embed list items inside list documents.
- Loses ACID transaction guarantees across documents without careful design.
- Requires a separate server process or managed service, just as PostgreSQL does.
- The data model is inherently relational (users → lists → items); NoSQL brings no real benefit here.

### 5. Datomic

An immutable, append-only database designed by Rich Hickey (Clojure's creator), queried with Datalog.

- Immutable history naturally supports archiving and time-travel queries (e.g., "what did this list look like 2 hours ago?").
- Concurrent edits append rather than conflict — no mutable-state concurrency problems.
- Clojure-native Datalog queries map directly to Clojure data structures.
- Proprietary licensing and operational complexity (transactor + storage backend).
- Steeper learning curve for developers unfamiliar with Datalog/logic programming.
- Rejected because PostgreSQL satisfies all domain needs with lower operational and cognitive overhead.

### 6. DynamoDB / Firestore

Serverless NoSQL databases with zero operational overhead.

- On-demand capacity scales to zero; no server management.
- DynamoDB Streams could feed real-time SSE events.
- Eventually consistent by default; no JOINs; forces denormalization.
- Rejected because the data model is relational and the project uses PostgreSQL by preference.

## Decision Outcome

Chosen option: **"PostgreSQL"**, because the user explicitly requested it, and it satisfies all decision drivers: full ACID transactions for data integrity, a relational model that maps naturally to our domain, `jsonb` as an escape hatch, and unique-index support for human-readable list codes. SQLite is sufficient for the expected workload but provides less headroom; MySQL offers no advantage at this scale; MongoDB and DynamoDB force denormalization where a relational model fits; Datomic's operational complexity outweighs its benefits for this project.

### Consequences

- Good, because ACID transactions guarantee data integrity when multiple users modify the same list.
- Good, because the relational schema (users, lists, list_items, completed_lists) maps cleanly to SQL tables with foreign-key constraints.
- Good, because `next.jdbc` in Clojure has first-class PostgreSQL support.
- Good, because `jsonb` allows flexible metadata or denormalized storage without schema migrations.
- Good, because unique/partial indexes enforce custom code uniqueness with minimal overhead.
- Neutral (managed): managed PostgreSQL (e.g., Supabase, Neon, Railway) eliminates most operational overhead; introduces a cloud dependency.
- Bad (self-hosted): requires provisioning, patching, backups, and connection pool tuning — significant operational burden for a small team.
- Bad, because MVCC handles transaction isolation, not application-level concurrent editing; a separate concurrency strategy (optimistic locking, serialization, or last-write-wins) is required, deferred to ADR-0003.
- Bad, because the database provides no built-in real-time push; SSE or WebSocket delivery is an application-layer concern.
- Neutral, because schema migrations (e.g., Migratus) are standard practice for versioned schema evolution — necessary for any serious production database.

### Confirmation

The decision is confirmed by:
- A running PostgreSQL instance (local Docker or managed service) accessible to the application.
- HikariCP connection pool configured in the application's system configuration.
- A `migratus` migration creating the `users`, `lists`, `list_items`, and `completed_lists` tables.
- Successful connectivity test from the Clojure REPL via `next.jdbc/get-connection`.
- SSL/TLS enforced on production connections (`sslmode=require`).
- Dev/prod parity: all environments use the same PostgreSQL version (no SQLite in dev).

## Pros and Cons of the Options

### PostgreSQL

- Good, because full ACID + MVCC + foreign keys = data integrity under concurrency.
- Good, because `jsonb` gives NoSQL flexibility inside a relational database, and can serve as a denormalization option if joins become problematic.
- Good, because unique and partial indexes enforce custom code uniqueness with minimal overhead.
- Good, because PostgreSQL is the standard recommendation for Clojure projects using JDBC.
- Neutral (managed), because managed PostgreSQL eliminates most operational overhead.
- Bad (self-hosted), because it requires provisioning, patching, backups, and connection pool tuning.

### SQLite

- Good, because zero-configuration deployment is ideal for single-user or embedded use cases.
- Neutral, because WAL mode allows concurrent reads and handles the expected write volume (<10 writes/second, <5 concurrent writers).
- Bad, because single-writer design serializes all write transactions, limiting headroom for unforeseen scale.
- Bad, because no native network access complicates multi-process or containerized deployments.

### MySQL / MariaDB

- Good, because widely available and well-documented.
- Neutral, because feature parity with PostgreSQL for this scale; no compelling advantage.
- Bad, because SQL dialect differences add friction for Clojure/JDBC users accustomed to PostgreSQL.

### MongoDB

- Good, because flexible document model avoids schema migrations early on.
- Bad, because ACID across documents requires careful design (or transactions, which add overhead).
- Bad, because relational data in a document store often leads to application-level joins.
- Bad, because its operational model (replica sets, sharding at scale) is less familiar than PostgreSQL's.

## More Information

- PostgreSQL docs: https://www.postgresql.org/docs/
- Migratus (Clojure migration tool): https://github.com/yogthos/migratus
- next.jdbc docs: https://cljdoc.org/d/seancorfield/next.jdbc
- If we later need horizontal read scaling, we can add read replicas or connection-pool-level routing without changing the application model.
- Schema design decisions will be captured in ADR-0003 (Data Model and Schema Design).
