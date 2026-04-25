---
status: accepted
date: 2026-04-25
decision-makers: user (kschltz), agent
consulted: {}
informed: {}
---

# Real-time Delivery Strategy

## Context and Problem Statement

The application requires real-time updates: when a user adds, checks, or deletes an item on a shared list, all other viewers of that list must see the change instantly. The stack is kit-clj (Ring-based, synchronous HTTP handlers by default), HTMX for client interactivity, and PostgreSQL as the database (ADR-0002). The database provides persistence but does not push changes to connected clients.

We need to solve: how does the server notify all clients viewing list "abc123" that an item was just added?

## Decision Drivers

- Real-time requirement: changes appear instantly for all viewers on the same list.
- The stack is kit-clj: Ring/HTTP handlers, synchronous by default.
- HTMX is the chosen UI layer (ADR-0004); it has native SSE support.
- WebSocket support in Ring requires an async adapter (http-kit, aleph).
- Need to broadcast updates only to clients viewing the relevant list (not all connected clients).
- ADR-0002 explicitly states: the database provides no built-in real-time push.
- Must handle client reconnections after network interruption.

## Considered Options

### 1. Server-Sent Events (SSE) over HTTP

A unidirectional server push mechanism over HTTP, served by **async Ring handlers** (http-kit `with-channel` or Aleph manifold streams).

- Server holds a persistent HTTP connection with `text/event-stream` content type.
- Server writes event lines (`data: {...}\n\n`) to the stream.
- Client reconnects automatically with `Last-Event-ID` for continuity.
- **Requires async Ring adapter** (http-kit `with-channel` / `send!` or Aleph manifold). Standard synchronous Ring handlers cannot serve SSE.
- HTMX supports SSE via extension: `<div hx-ext="sse" sse-connect="/list/abc123/events" sse-swap="#list-items">`.

### 2. WebSocket

A bidirectional persistent connection protocol.

- Full-duplex: clients and server can both send frames at any time.
- Lower per-message overhead than HTTP.
- Requires async Ring adapter (http-kit, aleph, or Immutant).
- More complex connection management (heartbeats, binary frames, subprotocols).
- HTMX does not natively support WebSocket; would need separate WebSocket client code.

### 3. Short/Long Polling

Client repeatedly requests updates from the server.

- Simple to implement with standard synchronous Ring handlers.
- Short polling: client requests every N seconds — high latency, wastes resources.
- Long polling: server holds request open until data is available, then client immediately re-requests — better but still overhead.
- Scales poorly: many idle connections or many wasted requests.
- No native HTMX support for the push model.

### 4. Hosted Real-time Service (Pusher, Ably, Socket.io)

Managed pub/sub service for real-time messaging.

- Zero server-side connection management.
- Channels model maps naturally to lists ("list-abc123" channel).
- External dependency and ongoing cost.
- Adds network latency (client → service → server → service → client).
- Less control over message format and delivery.

## Decision Outcome

Chosen option: **"Server-Sent Events (SSE)"**, because:
- Unidirectional push is sufficient: clients POST writes normally, server pushes updates via SSE.
- Native HTMX support (`hx-sse`) means no custom client-side JavaScript for the real-time layer.
- Works with standard Ring streaming or async adapters without full WebSocket complexity.
- SSE auto-reconnect with `Last-Event-ID` provides continuity after network interruptions.
- No external service dependency — self-contained.
- Simpler connection model than WebSocket (HTTP semantics, no binary frames).

WebSocket is rejected because the app only needs server-initiated pushes (not full-duplex chat). Polling is rejected because of latency and resource waste. Hosted services are rejected because they add external cost and latency for no clear gain.

### Consequences

- Good, because simple to implement with **http-kit async handlers** (`with-channel` / `send!`).
- Good, because HTMX SSE extension (`htmx-ext-sse`) means minimal client code.
- Good, because auto-reconnect with `Last-Event-ID` handles network interruptions.
- Good, because standard HTTP semantics — firewalls and proxies handle SSE better than WebSocket.
- Good, because no external service dependency.
- Neutral, because unidirectional: clients must POST separately for writes (matches our request/response model).
- Bad, because **HTTP/1.1 per-domain connection limit (max 6)** may be exhausted with multiple tabs + SSE + normal page loads. Budget: ~1 SSE + ~1-2 page loads per tab × N tabs = quickly exceeding 6. Mitigated by HTTP/2, connection consolidation, or reducing page weight.
- Bad, because server must maintain in-memory SSE connection state (e.g., `Atom<Map<list-code, Set<Channel>>>`). Lost on server restart — clients reconnect but may miss events.
- Bad, because **event batching is not addressed**: two users checking items simultaneously produce two SSE events. No debouncing is specified.

### Confirmation

The decision is confirmed by:
- An SSE endpoint at `/list/:code/events` using **http-kit async handlers** (`with-channel`) setting `Content-Type: text/event-stream`.
- HTMX markup receiving events via the SSE extension: `<div hx-ext="sse" sse-connect="/list/abc123/events" sse-swap="#list-items">`.
- Three simultaneous browser clients viewing the same list; adding an item on one client updates all three within 1 second.
- **Stress test**: 10 simultaneous clients; verify all receive updates within 2 seconds.
- Network interruption test: disconnect Wi-Fi, reconnect, client auto-resubscribes and receives updates.
- **Server restart test**: stop server for 5 seconds, restart, clients reconnect and receive updates from `Last-Event-ID`.
- Connection budget test: open 3 tabs, verify page loads and SSE connections stay within browser limits.

## Pros and Cons of the Options

### Server-Sent Events (SSE)

- Good, because native HTMX support via `hx-sse`.
- Good, because standard HTTP — no special protocol handling.
- Good, because auto-reconnect with event ID continuity.
- Bad, because unidirectional (clients must use separate POSTs for writes).
- Bad, because HTTP/1.1 connection limits per domain.
- Bad, because server must maintain in-memory connection state per list.

### WebSocket

- Good, because bidirectional — one connection for reads and writes.
- Good, because lower per-message overhead.
- Bad, because requires async Ring adapter (not default in kit-clj).
- Bad, because more complex connection lifecycle.
- Bad, because HTMX does not natively support WebSocket.
- Bad, because overkill for server-initiated push only.

### Short/Long Polling

- Good, because works with synchronous Ring handlers.
- Bad, because high latency or wasted resources.
- Bad, because does not scale with many concurrent viewers.
- Bad, because no native HTMX support for the push model.

### Hosted Real-time Service (Pusher, Ably)

- Good, because zero connection management.
- Good, because channels map naturally to lists.
- Bad, because external dependency and cost.
- Bad, because adds latency.
- Bad, because less control over delivery.

## More Information

- HTMX SSE extension: https://htmx.org/extensions/sse/
- Server-Sent Events spec: https://html.spec.whatwg.org/multipage/server-sent-events.html
- Ring async handlers (http-kit): http://www.http-kit.org/
- Connection state management patterns for SSE in Clojure: `Atom<Map<String, Set<Channel>>>` or `core.async` pub/sub.

