# Magic Link Authentication

| PRD ID | Feature | Status | Linked Quint Spec | Author | Date |
|--------|---------|--------|-------------------|--------|------|
| 0001 | Magic Link Authentication | **accepted** | `docs/specs/0001-magic-link-auth.qnt` | agent | 2026-04-25 |

---

## 1. Problem Statement

Users need a way to log in without remembering a password. Magic links provide a secure, low-friction alternative to password authentication. A user provides their email, receives a time-limited single-use link via email, and clicking that link creates a server-side session, effectively logging them in. First-time users must choose a display name before accessing the app.

## 2. Goals

- Enable passwordless login via email magic links.
- Ensure magic links expire after 15 minutes and cannot be replayed.
- Create a server-side session upon successful magic link validation.
- Capture a display name for first-time users before granting app access.
- Prevent abuse via rate limiting on magic link requests.

**Note on list invites:** Magic links may carry an optional `list_code` parameter. When present, the user is both authenticated (session created) and added to the list as a participant. The list-join portion is modeled in the separate "Join List by Code" PRD/spec; this spec models only the auth state machine.

### Non-Goals

- Social login or OAuth integration (deferred, not required).
- Password-based authentication (separate PRD-0002).
- Email delivery mechanism choice or implementation details (SMTP vs SendGrid — ADR-0007 defers this).
- Account deletion or email change (deferred).
- Multi-factor authentication (deferred).

## 3. User Stories

- As a new user, I want to log in with just my email so that I don't need to remember a password.
- As a returning user, I want to receive a magic link and click it to be logged in immediately.
- As a first-time user, I want to set my display name after clicking the magic link so that other list participants see who I am.
- As any user, I want expired or already-used magic links to fail securely so that my account stays safe.
- As any user, I want rate limiting on magic link requests so that my inbox isn't flooded.

## 4. Detailed Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | The system shall accept an email address via a form and generate a magic link token. | must | Token is HMAC-SHA256 signed, contains email + timestamp. |
| FR-2 | The system shall enforce a rate limit of max 3 magic link requests per email per 15-minute window. | must | Prevents abuse and inbox flooding. |
| FR-3 | The magic link token shall expire 15 minutes after generation. | must | Per ADR-0007. |
| FR-4 | The magic link token shall be single-use: once validated successfully, it is invalidated. | must | Replay protection. |
| FR-5 | The system shall send an email containing the magic link to the provided email address. | must | Outcome: email sent or queued. |
| FR-6 | Visiting the magic link endpoint shall validate the token (signature, expiry, not used). | must | If invalid/expired/used → redirect to error page. |
| FR-7 | On valid token, if the email is not yet in the `users` table, the system shall create a new user row with `email` and `null` display name, then redirect to a display-name form. | must | First-time flow. |
| FR-8 | On valid token, if the email already exists in `users` with a display name, the system shall create a server-side session and redirect to the app home page. | must | Returning user fast path. |
| FR-9 | The display-name form shall accept a non-empty display name (1–50 printable characters) and update the user row, then create a session. | must |
| FR-10 | The session shall expire after 24 hours of inactivity (sliding expiry) or on explicit logout. | must | Per ADR-0007. |
| FR-11 | The system shall provide a logout endpoint that destroys the server-side session and clears the cookie. | must |

### 4.2 Data Requirements

The following entities are required. Schema references ADR-0003 (`users` table) and ADR-0007 (`sessions` table). Magic link tokens are ephemeral and HMAC-signed; they are **not** stored in the database — instead, a short-lived record tracks pending tokens to enforce single-use and rate limiting.

- **`magic_link_tokens` (ephemeral tracking table):**
  - `id` SERIAL PRIMARY KEY
  - `email` VARCHAR(255) NOT NULL
  - `token_hash` VARCHAR(64) NOT NULL — SHA-256 hash of the raw token string sent in the email
  - `expires_at` TIMESTAMPTZ NOT NULL — `now() + interval '15 minutes'`
  - `used_at` TIMESTAMPTZ — NULL until validated; set on first use
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - UNIQUE(`token_hash`)
  - INDEX on (`email`, `created_at`) for rate-limit queries
  - INDEX on (`expires_at`) for cleanup queries

- **`users` (already defined in ADR-0003):**
  - `id` SERIAL PRIMARY KEY
  - `email` VARCHAR(255) UNIQUE NOT NULL
  - `display_name` VARCHAR(50) — NULL until first login
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `updated_at` TIMESTAMPTZ DEFAULT now()

- **`sessions` (already defined in ADR-0007):**
  - `id` SERIAL PRIMARY KEY
  - `user_id` INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE
  - `token` VARCHAR(255) NOT NULL — opaque session identifier
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `expires_at` TIMESTAMPTZ NOT NULL — `now() + interval '24 hours'` (sliding)

### 4.3 UI / UX Requirements

- **Magic link request page:**
  - Single email input field + "Send Magic Link" button.
  - Inline validation: valid email format (HTML5 `type="email"`).
  - Success state: "Check your email!" message (even if email doesn't exist — anti-enumeration).
  - Rate-limit error: "Too many requests. Please wait X minutes."

- **Magic link email:**
  - Plain text + HTML: "Click to log in to Comprineas: [link]"
  - Link expires in 15 minutes.
  - "Didn't request this? Ignore this email."

- **Magic link landing page (after click):**
  - If token valid + new user: display name form.
  - If token valid + returning user: redirect to home.
  - If token invalid/expired/used: clear error message + "Request a new link" button.

- **Display name form (first-time only):**
  - Single text input, 1–50 characters.
  - Submit → home page.
  - Error if empty or > 50 chars.

- **Logged-in state (visible in nav/header):**
  - Display name shown.
  - "Log out" button visible.

### 4.4 Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Magic link token must be cryptographically signed (HMAC-SHA256) with a server secret. | security |
| NFR-2 | Token entropy must be ≥ 128 bits (≥ 22 URL-safe base64 chars). | security |
| NFR-3 | Rate limit on requests: 3 per email per 15 min, 10 per IP per 5 min. | availability |
| NFR-4 | Magic link validation must complete in < 200ms (p95). | performance |
| NFR-5 | Old/expired `magic_link_tokens` rows must be purged by a background job at least daily. | ops |

## 5. Out of Scope

- Password authentication (separate PRD-0002).
- Email delivery provider selection or configuration (ADR-0007 defers; implementation picks between Postal, SendGrid, SES, etc.).
- Password reset / forgot password flow (only relevant for password auth).
- Account settings page (email change, display name change — deferred).
- Admin dashboard for user management.
- Social login / OAuth.
- Passkeys / WebAuthn.

## 6. Acceptance Criteria

- [ ] **AC-1:** Given an unauthenticated user, when they submit a valid email on the magic link request page, then a magic link token is generated and an email is sent.
- [ ] **AC-2:** Given a user who has already requested a magic link within 15 minutes, when they request again, then the request is rejected with a rate-limit message.
- [ ] **AC-3:** Given a user clicks a magic link within 15 minutes of generation, when the token is valid and unused, then a session is created and the user is redirected to the app.
- [ ] **AC-4:** Given a first-time user clicks a valid magic link, when the token is validated, then they are prompted to enter a display name before a session is created.
- [ ] **AC-5:** Given a user clicks an expired magic link ( > 15 min old), when the token is validated, then login fails with an error message.
- [ ] **AC-6:** Given a user clicks an already-used magic link, when the token is validated, then login fails with an error message.
- [ ] **AC-7:** Given a user with an active session, when they click "Log out", then the session is destroyed and the cookie is cleared.
- [ ] **AC-8:** Given a user with an active session, when they attempt to access the app after 24 hours of inactivity, then they are redirected to the login page.

## 7. Open Questions

- **Q1:** Do we store magic link tokens in the DB or purely in-memory/cache? — **Answer: PostgreSQL table** (`magic_link_tokens` as already proposed). Simple, consistent with ADR-0002, no extra infra.
- **Q2:** Should magic links also work for unauthenticated list participation (e.g., join a list via magic link), or is auth-only acceptable? — **Answer: List invites by magic link** are in scope for this feature. A magic link can both log the user in AND join them to a list in one flow.
- **Q3:** Is a daily cleanup job acceptable for expired tokens, or do we need a stricter purge schedule? — **Answer: Daily cleanup is acceptable.**

## 8. Linked ADRs

- **ADR-0007** — Authentication Strategy: chose session cookies + HMAC magic links + PostgreSQL session store.
- **ADR-0003** — Data Model & Schema Design: defines `users` and `sessions` tables.
- **ADR-0002** — PostgreSQL: session and token storage medium.
- **ADR-0004** — HTMX: UI interactivity model for request/landing pages.

## 9. Linked Quint Spec

**Mandatory.** Every PRD must reference its companion Quint specification.

- **Quint Spec:** `docs/specs/0001-magic-link-auth.qnt`
- **Quint Spec Status:** proven
- **Verification Summary:** All invariants pass — `invNoReplay`, `invSessionRequiresName`, `invRateLimit`, `invUsedWithinLifetime` verified via `quint typecheck` → `test` → `run` (100 samples) → `verify` (depth 5). See `docs/specs/0001-magic-link-auth.md` §8 for full output.

*Without a linked, proven Quint spec, this PRD may not proceed to implementation.*

---

*End of PRD. The linked Quint specification in `docs/specs/` must model every requirement marked FR-N above as a state variable, action, or invariant.*
