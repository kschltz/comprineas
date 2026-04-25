---
status: proposed
date: 2026-04-25
decision-makers: user (kschltz), agent
consulted: {}
informed: {}
---

# Use kit-clj as the Web Framework

## Context and Problem Statement

We need a backend framework for a real-time collaborative grocery/shopping list application. The app must serve HTML to browsers, persist state to a relational database, and push real-time updates to connected clients. The user prefers Clojure and has explicitly requested kit-clj.

We have prior experience specifying the stack as: kit-clj (backend), PostgreSQL (database), HTMX (frontend interactivity), Tailwind CSS (styling). Before committing to kit-clj specifically, we want to record the forces that make it the right choice against reasonable alternatives.

## Decision Drivers

- The language preference is Clojure; we need a framework that embraces idiomatic Clojure.
- We need server-rendered HTML (not an SPA) to minimize client-side complexity and keep HTMX interactivity lightweight.
- We need Integrant-based component lifecycle management for clean system startup/shutdown and REPL-driven development.
- The framework should provide routing, middleware stacks, and database connection pooling out of the box without prescribing a frontend build step.
- We prefer REPL-driven, interactive development where the server can be reloaded without restart.
- Real-time features (SSE or WebSocket) should be achievable without fighting the framework.

## Considered Options

### 1. kit-clj

A batteries-included Clojure web framework built on Integrant, Reitit, Ring, and next.jdbc. It provides project-level scaffolding, a REPL-driven workflow, and modular component configuration via `system.edn`.

- Provides Integrant system management, Reitit routing, and Ring middleware out of the box.
- No frontend bundler or JavaScript framework required.
- Supports server-sent events (SSE) through Ring and HTTP-kit.
- Uses next.jdbc for database interaction, compatible with PostgreSQL.

### 2. Luminus

A popular Clojure micro-framework with a broad set of bundled libraries.

- Established and widely used.
- Can be configured to use the exact same underlying libraries (Ring, Reitit, Integrant).
- Heavier default template; often includes more than needed for a small project.

### 3. Raw Ring + Reitit + Integrant (hand-rolled)

Assemble the same stack manually without a framework scaffold.

- Maximum control, minimal assumptions.
- Requires writing all the boilerplate (system startup, config, middleware stack, routing).
- Easy to get wrong early; harder for other agents to pick up.

### 4. Pedestal

An alternative server framework from Cognitect with an interceptor-based model instead of Ring middleware.

- Excellent for API-heavy, async services.
- Interceptor model is more complex than Ring middleware.
- Less natural fit for straightforward server-rendered HTML with HTMX.

## Decision Outcome

Chosen option: **"kit-clj"**, because the user expressed a clear preference for it, and it directly satisfies all decision drivers without the overhead of alternatives.

kit-clj packages exactly the libraries we want (Integrant, Reitit, Ring, next.jdbc) under a coherent project structure that other agents and developers can read quickly. It removes the need for manual assembly while staying lightweight enough for HTMX + Tailwind. Luminus is heavier; hand-rolled is too much boilerplate; Pedestal's interceptor model is unnecessary complexity.

### Consequences

- Good, because the project structure is opinionated and familiar to Clojure devs — `system.edn`, `src/clj/`, Integrant components.
- Good, because REPL-driven development is first-class; the `user` namespace reloads the system in development.
- Good, because SSE-based real-time updates fit naturally into the Ring/HTTP-kit adapter that kit-clj uses.
- Good, because no frontend build pipeline is needed; Tailwind can be compiled separately or via a CDN in development.
- Bad, because it couples us to the specific versions and module choices of kit-clj. Upgrades may require more care.
- Bad, because kit-clj is less widely documented than Luminus; tribal knowledge matters more.

### Confirmation

The decision is confirmed by the presence of `kit/kit` in `deps.edn` and a working `system.edn` at the project root. We verify by starting the REPL, running `(reset)`, and confirming the Integrant system reports all keys (web server, database connection pool) as `:started`. We also confirm a basic Ring handler returns 200 OK at `http://localhost:3000`.

## Pros and Cons of the Options

### kit-clj

- Good, because it bundles exactly the libraries we want without prescribing unwanted ones.
- Good, because Integrant + `system.edn` provide a clean, declarative component system.
- Good, because the `kit` module system lets us add only what we need (e.g., no need to include a frontend build module).
- Neutral, because it has less community reach than Luminus; fewer Stack Overflow answers.
- Bad, because it is a younger project; long-term maintenance is less certain.

### Luminus

- Good, because it is mature and has extensive documentation.
- Good, because it provides many template options out of the box.
- Bad, because the default template includes more dependencies than we need, increasing surface area.
- Bad, because it favors Mount over Integrant by default, which does not align with the user's stated stack.

### Raw Ring + Reitit + Integrant

- Good, because maximum control means no hidden framework behavior.
- Bad, because we would be writing boilerplate that kit-clj already solves.
- Bad, because onboarding other agents becomes harder without an established project skeleton.

### Pedestal

- Good, because interceptors are powerful for cross-cutting concerns and async processing.
- Bad, because the interceptor model adds cognitive overhead for simple handler chains.
- Bad, because it is less commonly paired with server-rendering and HTMX than Ring.

## More Information

- kit-clj docs: https://kit-clj.github.io/
- If kit-clj sunsets or stagnates, we can eject: the underlying libraries (Integrant, Reitit, Ring, next.jdbc) are all standard and portable. Ejection cost is moderate.
- Future ADRs may cover: how to manage real-time connections (ADR-0002 SSE strategy), the exact database schema decisions (ADR-0003 Postgres data model), and how Tailwind integrates (ADR-0004 CSS pipeline).
