# ADR-0008: Playwright End-to-End Testing

| Field | Value |
|-------|-------|
| **ADR ID** | 0008 |
| **Title** | Playwright End-to-End Testing |
| **Status** | accepted |
| **Date** | 2026-04-27 |
| **Deciders** | pi |

---

## Context

The Comprineas application has 7 features (PRD 0001–0007) with 6 proven Quint state-machine specifications and extensive Clojure unit/integration tests. However, there are no browser-level end-to-end tests that verify the full stack: HTTP routing, Selmer template rendering, HTMX interactivity, Tailwind CSS styling, and SSE real-time delivery working together.

Without e2e tests, regressions in template rendering, route wiring, HTMX behavior, or real-time SSE delivery can only be caught manually.

---

## Decision Drivers

1. **Full-stack coverage**: Must verify HTML rendering, HTMX form submissions, SSE event handling in an actual browser.
2. **CI compatibility**: Must run in CI without external dependencies (no external PostgreSQL, no mail server).
3. **Low maintenance overhead**: Tests should be resilient to minor DOM changes, use semantic selectors where possible.
4. **Parallel execution**: Tests must be order-independent and safely parallelizable.
5. **Developer experience**: Clear failure diagnostics, readable test reports, optional headed mode for debugging.

---

## Considered Options

| Option | Pros | Cons |
|--------|------|------|
| **A. Playwright (JavaScript)** | Industry standard, multi-browser, first-class parallel + trace support, HTMX/Selmer agnostic | Separate language from Clojure codebase |
| **B. Etaoin (Clojure WebDriver)** | Native Clojure, no JS toolchain | Slower, less parallel support, fewer debugging tools |
| **C. Cypress** | Good DX, time-travel debugging | No multi-tab/context support (critical for SSE testing), slower parallel execution |
| **D. Selenium** | Battle-tested, multi-language | Slower, flakier, poor parallel ergonomics |

---

## Decision Outcome

**Chosen: Option A — Playwright.**

Playwright's multi-browser-context support is essential for testing SSE real-time features where two users collaborate on the same list simultaneously. Its trace viewer and HTML reporter provide excellent debugging for HTMX-driven interactions. Parallel execution with work-stealing maximizes throughput.

### Implementation

```
e2e/
├── package.json              # @playwright/test dependency
├── playwright.config.js      # baseURL http://localhost:3001, chromium, parallel
├── helpers/
│   ├── setup.js              # Starts Clojure app with embedded PG, waits for E2E_READY
│   └── teardown.js           # SIGTERM + cleanup
└── tests/
    ├── auth.spec.js          # PRD-0001, PRD-0002 — 5 tests
    ├── dashboard.spec.js     # PRD-0007 — 7 tests
    ├── items.spec.js         # PRD-0005 — 5 tests
    ├── lists.spec.js         # PRD-0003, 0004, 0006 — 4 tests
    └── realtime.spec.js      # SSE — 3 tests (multi-context)
```

**Server bootstrap** (`test/comprineas/e2e_server.clj`):
- Starts an embedded PostgreSQL instance (via `io.zonky.test.db.postgres.embedded`, already used by Clojure unit tests)
- Runs Migratus migrations
- Creates the Ring app with stub mailer
- Starts Jetty on port 3001
- Prints `E2E_READY port=3001` to stdout

Playwright's `globalSetup` spawns `clojure -M:test -m comprineas.e2e-server` and waits for the ready signal.

### Test Design Principles

1. **Order independence**: Each test registers a unique user (timestamp-based email), so tests never collide on shared DB state.
2. **Inline helpers**: Each spec file contains its own `registerAndLogin` / `login` / `createList` helpers — no shared fixture files that create coupling.
3. **HTMX-aware assertions**: Tests wait for HTMX responses using content-based selectors (`toContainText`, `waitForSelector`) rather than fixed wait times where possible.
4. **SSE timeouts**: Real-time tests use 5-second assertion timeouts, matching the ≤1s delivery target from PRD-0007 NFR-2.
5. **Multi-context for collaboration**: SSE tests use `browser.newContext()` to simulate two independent users without cookie/session leaks.

---

## Consequences

### Positive
- 24 e2e tests covering all 7 PRDs, including multi-user SSE scenarios
- Zero external dependencies for test runs (embedded PG, stub mailer)
- Full CI compatibility with isolated test databases
- Trace + screenshot on failure for debugging HTMX/SSE issues
- Parallel execution (4 workers) keeps suite fast

### Negative
- JavaScript toolchain (`npm install`) required alongside Clojure toolchain
- Tests are bound to Selmer template structure — template refactors may require test updates
- 5-second SSE wait timeouts could be flaky under high system load

### Mitigations
- Document selector conventions (use `data-testid` for critical elements in future template work)
- Increase SSE timeouts in CI via `playwright.config.js` env override
- Add `test.step()` wrapping for clear failure attribution in reports

---

## Confirmation

This ADR is confirmed by:
- All 24 tests discovered and parseable by `npx playwright test --list`
- Test structure maps 1:1 to PRD acceptance criteria
- Embedded PG pattern proven by existing Clojure integration tests

---

*End of ADR-0008.*
