---
status: accepted
date: 2026-04-25
decision-makers: user (kschltz), agent
consulted: {}
informed: {}
---

# Use HTMX for UI Interactivity

## Context and Problem Statement

The application needs interactive UI capabilities — adding items, checking them off, completing lists, and joining lists by code — while delivering real-time updates to all viewers on the same list. The stack is kit-clj (server-rendered Clojure), not a single-page application (SPA) framework.

We need a UI interactivity approach that:
- Works with server-rendered HTML (kit-clj / Selmer templates).
- Supports progressive enhancement (pages work without JS, enhanced with JS).
- Enables real-time updates for collaborative list editing.
- Avoids JavaScript build pipeline complexity.

## Decision Drivers

- The user explicitly requested HTMX.
- The stack is kit-clj: server-rendered HTML via Selmer templates.
- Real-time collaboration requires server-initiated UI updates (SSE).
- Progressive enhancement is a goal: core functionality works without JavaScript.
- No dedicated frontend team: minimize build tooling and framework complexity.

## Considered Options

### 1. HTMX

A library that gives HTML elements AJAX, CSS transitions, WebSocket, and SSE capabilities via attributes.

- Works with **async** Ring handlers (http-kit's `with-channel` / `send!`) that return streaming responses. Standard synchronous Ring handlers cannot serve SSE.
- HTMX SSE extension (`htmx-ext-sse`) maps a single SSE event type to a single DOM target per connection.
- **Multi-target updates** require out-of-band swaps (`hx-swap-oob="true"`) in the SSE payload or a custom `EventSource` wiring.
- Progressive enhancement: add `hx-*` attributes; the page still works as a normal form without JS. **Real-time updates require JavaScript** — without JS, users see updates only on manual page refresh.
- Zero build step — single JS file included via CDN or local asset.
- Deeply integrated with Clojure/Ring server-rendered model.

### 2. React / Vue / Svelte

Modern SPA frameworks.

- Rich component model, virtual DOM, reactive state.
- Requires a separate build pipeline (Vite, webpack, cljsbuild for ClojureScript).
- Needs a JSON API layer — the server must serve APIs, not HTML.
- ClojureScript integration exists but adds complexity.
- Overkill for a server-rendered app with simple interactivity.

### 3. Alpine.js

A lightweight JavaScript framework for adding behavior directly in HTML via `x-*` attributes.

- Similar progressive enhancement philosophy to HTMX.
- Client-side focused: handles UI state (show/hide, toggles) but doesn't do server communication natively.
- Would still need `fetch()` or HTMX for server calls.
- Less server-driven than HTMX.

### 4. Vanilla JavaScript + fetch()

Manual XMLHttpRequest / fetch with DOM manipulation.

- Full control, zero dependencies.
- Significantly more code for every interactive element.
- Manual error handling, retry logic, loading states.
- SSE would need custom EventSource wiring.
- Maintenance burden grows with every feature.

### 5. Unpoly

A framework similar to HTMX with broader scope (layers, modals, caching).

- More feature-rich than HTMX.
- Less adoption in the Clojure ecosystem.
- Steeper learning curve; more concepts to learn.

## Decision Outcome

Chosen option: **"HTMX"**, because:
- The user explicitly requested it.
- It fits the kit-clj server-rendered model perfectly — HTML fragments travel both ways.
- SSE support is native and simple, critical for real-time collaboration.
- Progressive enhancement means the app degrades gracefully without JS.
- No build pipeline eliminates an entire category of tooling decisions.
- HTMX + kit-clj is a well-documented pattern in the Clojure community.

React/Vue are rejected because they require a JS build pipeline and an API layer, both of which are unnecessary for this app. Alpine.js is rejected because it is client-state-centric and doesn't solve server communication. Vanilla JS is rejected because HTMX provides the same outcome with vastly less code. Unpoly is rejected because HTMX is simpler and has better Clojure ecosystem support.

### Consequences

- Good, because progressive enhancement: forms work without JS (full page reload), enhanced with JS (AJAX swaps). Real-time features require JS.
- Good, because SSE via HTMX SSE extension (`htmx-ext-sse`) works with http-kit async handlers.
- Neutral, because **multi-target updates** (items list + participants + status) require out-of-band swap (`hx-swap-oob="true"`) in SSE payloads or custom event wiring — single `hx-sse` only supports one swap target.
- Good, because server remains the source of truth; no client-side state synchronization problems.
- Good, because HTMX fits naturally with Ring handlers returning HTML fragments.
- Bad, because SSE requires **async Ring adapter** (http-kit `with-channel` / `send!` or Aleph manifold); standard synchronous Ring handlers cannot serve SSE.
- Bad, because **multi-target DOM updates** from a single SSE connection require out-of-band swap design or custom `EventSource` wiring beyond basic `hx-sse`.
- Bad, because progressive enhancement claim is **partial**: core CRUD works without JS, but real-time collaboration (the core differentiating feature) requires JavaScript.
- Neutral, because HTTP/1.1 limits 6 connections per domain; tab proliferation and multiple event targets may exhaust the budget. Mitigated by HTTP/2 or consolidating SSE endpoints.

### Confirmation

The decision is confirmed by:
- A kit-clj route using **http-kit async** (`with-channel`) rendering HTML with HTMX attributes (e.g., `hx-post`, `hx-target`, `hx-swap`).
- An SSE endpoint using http-kit's async support streaming list update events.
- HTMX SSE extension receiving events and swapping a single DOM target (e.g., `#list-items`).
- **Out-of-band swap test**: an SSE event payload containing an element with `hx-swap-oob="true:#participants"` updates both items and participants.
- Manual test: disable JavaScript — forms still submit and pages reload correctly (no AJAX, no real-time).

## Pros and Cons of the Options

### HTMX

- Good, because native SSE support for real-time collaboration.
- Good, because zero build step, single JS file.
- Good, because server-rendered HTML + HTMX is a well-documented Clojure pattern.
- Good, because progressive enhancement out of the box.
- Bad, because not suited for complex client-side state management.
- Bad, because offline-first requires additional architecture.

### React / Vue / Svelte

- Good, because rich component ecosystem and reactive state.
- Good, because virtual DOM enables sophisticated UI patterns.
- Bad, because requires separate build pipeline and JSON API.
- Bad, because adds complexity inconsistent with the server-rendered stack.
- Bad, because overkill for the application's interactivity needs.

### Alpine.js

- Good, because lightweight progressive enhancement.
- Good, because `x-*` attributes are intuitive.
- Bad, because doesn't handle server communication natively.
- Bad, because not the user's requested technology.
- Bad, because less server-driven than HTMX for this use case.

### Vanilla JavaScript + fetch()

- Good, because zero dependencies, full control.
- Bad, because significantly more code for every interaction.
- Bad, because manual SSE wiring, error handling, loading states.
- Bad, because maintenance burden grows with each feature.

### Unpoly

- Good, because feature-rich (layers, modals, caching).
- Bad, because steeper learning curve than HTMX.
- Bad, because less Clojure ecosystem support.
- Bad, because not the user's requested technology.

## More Information

- HTMX documentation: https://htmx.org/
- HTMX + SSE: https://htmx.org/extensions/sse/
- HTMX + Clojure Ring: https://github.com/htmx/htmx/tree/master#server-side
- Kit-clj Selmer templates: https://github.com/kit-clj/kit

