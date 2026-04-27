# Password Authentication

| PRD ID | Feature | Status | Linked Quint Spec | Author | Date |
|--------|---------|--------|-------------------|--------|------|
| 0002 | Password Authentication | **accepted** | `docs/specs/0002-password-authentication.qnt` | agent | 2026-04-26 |

---

## 1. Problem Statement

Magic link authentication (PRD-0001) requires users to wait for an email and check their inbox before every login. Some users prefer the immediacy of a password, especially on mobile devices or when email delivery is slow. Users who initially signed up via magic link also need a path to add a password to their account so they can log in with either method. A complete password authentication system requires registration, login, password reset via email, and the ability for existing users to set a password for the first time.

## 2. Goals

- Enable new users to register with email, password, and display name in a single form.
- Enable returning users to log in with email and password, creating a server-side session on success.
- Allow existing magic-link-only users to set a password on their account without re-registering.
- Provide a secure "forgot password" flow that sends a time-limited, single-use reset link via email.
- Protect against brute-force attacks with rate limiting on login attempts.
- Store passwords using bcrypt with a cost factor of at least 10.
- Maintain anti-enumeration: identical error messages and behavior for non-existent email vs. wrong password.

### Non-Goals

- Social login or OAuth integration (deferred, not required).
- Password complexity rules beyond a minimum length of 8 characters (grocery list app, not banking).
- Multi-factor authentication (deferred).
- Password history / preventing reuse of previous passwords (deferred).
- Admin dashboard for password management.
- Forced password expiration or rotation policies.
- Display name change UI (deferred to account-settings PRD).

## 3. User Stories

- As a new user, I want to register with email and password so that I can log in immediately without waiting for emails.
- As a returning user, I want to log in with my email and password so that I don't need to check my inbox.
- As a user who previously used only magic links, I want to set a password on my account so that I can use either login method.
- As a user who forgot my password, I want to reset it via a secure email link so that I can regain access.
- As any user, I want failed login attempts to be rate-limited so that my account cannot be brute-forced.

## 4. Detailed Requirements

### 4.1 Functional Requirements

| ID | Requirement | Priority | Notes |
|----|-------------|----------|-------|
| FR-1 | The system shall provide a registration form accepting email, password (≥ 8 characters), and display name (1–50 printable characters). | must | |
| FR-2 | The system shall reject registration if the email already exists in the `users` table. | must | Anti-enumeration rule AC-4 still applies to login; registration must be honest about duplicates to prevent account takeover. |
| FR-3 | On successful registration, the system shall create a user row with a bcrypt-hashed password, create a session, and redirect to the app home page. | must | bcrypt cost factor ≥ 10 per ADR-0007. |
| FR-4 | The system shall provide a login form accepting email and password. | must | |
| FR-5 | On login, the system shall look up the user by email. If the user does not exist, the system shall still perform a dummy bcrypt comparison and return a generic error. | must | Anti-enumeration. |
| FR-6 | On login, if the user exists and the bcrypt-comparison succeeds, the system shall create a server-side session and redirect to the app home page. | must | |
| FR-7 | On login, if the user exists but the bcrypt-comparison fails, the system shall return the same generic error as FR-5. | must | Anti-enumeration. |
| FR-8 | The system shall rate-limit login attempts to a maximum of 5 per IP address per 1-minute window. | must | Per ADR-0007. Exceeding limit returns 429. |
| FR-9 | The system shall provide a "forgot password" form that accepts an email address. | must | |
| FR-10 | On password-reset request, the system shall generate an HMAC-SHA256 signed reset token with 30-minute expiry and send it via email, regardless of whether the email exists. | must | Anti-enumeration: same success message for all emails. |
| FR-11 | The system shall store pending reset tokens in a `password_reset_tokens` table with expiry, single-use tracking, and email association. | must | Same structure as `magic_link_tokens` but 30-minute expiry. |
| FR-12 | Visiting the password-reset endpoint shall validate the token (signature, expiry, not used). If invalid, an error is shown. | must | |
| FR-13 | On valid reset token, the system shall accept a new password (≥ 8 characters), update the user's `password_hash`, invalidate the reset token, and redirect to the login page. | must | |
| FR-14 | The system shall allow a logged-in user without a password to set one via a "set password" form (≥ 8 characters). | should | Enables magic-link-only users to add password auth. |
| FR-15 | The logout endpoint (shared with PRD-0001) shall destroy the server-side session and clear the cookie. | must | Already implemented; referenced for completeness. |

### 4.2 Data Requirements

Schema references ADR-0003 (`users` table) and ADR-0007 (`sessions` table). A new migration extends the schema.

- **`users` (schema extension):**
  - Add `password_hash VARCHAR(255)` — NULL until user sets a password via registration, reset, or "set password" flow.

- **`password_reset_tokens` (new ephemeral table):**
  - `id` SERIAL PRIMARY KEY
  - `email` VARCHAR(255) NOT NULL
  - `token_hash` VARCHAR(64) NOT NULL — SHA-256 hash of the raw token string sent in the email
  - `expires_at` TIMESTAMPTZ NOT NULL — `now() + interval '30 minutes'`
  - `used_at` TIMESTAMPTZ — NULL until validated; set on first use
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - UNIQUE(`token_hash`)
  - INDEX on (`email`, `created_at`) for rate-limit / cleanup queries
  - INDEX on (`expires_at`) for cleanup queries

- **`sessions`** (unchanged from PRD-0001 / ADR-0007):
  - `id` SERIAL PRIMARY KEY
  - `user_id` INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE
  - `token` VARCHAR(255) NOT NULL UNIQUE
  - `created_at` TIMESTAMPTZ DEFAULT now()
  - `expires_at` TIMESTAMPTZ NOT NULL

### 4.3 UI / UX Requirements

- **Login page (`/login`):**
  - Email input + Password input + "Log in" button.
  - Link to "Forgot password?"
  - Link to "Create account" (registration).
  - Generic error: "Invalid email or password." (no distinction between missing user and wrong password).
  - Rate-limit error: "Too many attempts. Please wait a moment." (429 state).

- **Registration page (`/register`):**
  - Email input + Password input + Display name input + "Register" button.
  - Password help text: "At least 8 characters."
  - Error if email already exists: "This email is already registered."
  - Error if display name invalid: "Display name must be 1–50 characters."
  - Error if password < 8 chars: "Password must be at least 8 characters."
  - On success: redirect to home page (session created).

- **Forgot password page (`/forgot-password`):**
  - Email input + "Send reset link" button.
  - Success message (all cases): "If this email is registered, a reset link has been sent."
  - No distinction between existing and non-existing emails.

- **Password reset email:**
  - Plain text + HTML: "Reset your Comprineas password: [link]"
  - Link expires in 30 minutes.
  - "Didn't request this? Ignore this email."

- **Password reset form (from email link, `/reset-password/:token`):**
  - New password input + "Reset password" button.
  - Error if token invalid/expired/used: "This link is invalid or has expired."
  - Error if password < 8 chars: "Password must be at least 8 characters."
  - On success: redirect to login page with success message "Password updated. Please log in."

- **"Set password" form (for logged-in magic-link-only users, `/set-password`):**
  - New password input + "Set password" button.
  - On success: redirect to home with confirmation.

### 4.4 Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | Passwords must be hashed with bcrypt using a cost factor ≥ 10. | security |
| NFR-2 | Reset tokens must be HMAC-SHA256 signed with a server secret and contain email + timestamp. | security |
| NFR-3 | Token entropy must be ≥ 128 bits (≥ 22 URL-safe base64 characters). | security |
| NFR-4 | Login rate limit: 5 attempts per IP per minute; 429 on exceed. | availability |
| NFR-5 | Password reset and login validation must complete in < 200 ms (p95). | performance |
| NFR-6 | Old/expired `password_reset_tokens` rows must be purged by a background job at least daily. | ops |

## 5. Out of Scope

- Changing an already-set password while logged in (deferred to account-settings PRD).
- Social login / OAuth.
- Multi-factor authentication.
- Password strength meters or complexity rules.
- Admin user management.
- Account deletion or email change.
- Email delivery provider selection (deferred to implementation per ADR-0007).

## 6. Acceptance Criteria

- [ ] **AC-1:** Given an unauthenticated user, when they submit the registration form with a valid email, password ≥ 8 characters, and display name 1–50 characters, then a user is created with a bcrypt password hash, a session is created, and they are redirected to the home page.
- [ ] **AC-2:** Given a user already exists with an email, when someone submits registration with the same email, then registration fails with the message "This email is already registered."
- [ ] **AC-3:** Given a registered user with a password, when they submit correct email and password on the login form, then a session is created and they are redirected to the home page.
- [ ] **AC-4:** Given a non-existent email, when someone attempts to log in, then the system returns the same generic error message and HTTP status as a wrong-password attempt (anti-enumeration).
- [ ] **AC-5:** Given a registered user, when they submit a wrong password, then login fails with the generic error "Invalid email or password."
- [ ] **AC-6:** Given 5 failed login attempts from the same IP within 1 minute, when a 6th attempt is made, then the system returns HTTP 429 with a rate-limit message.
- [ ] **AC-7:** Given a registered user requests a password reset, when they submit their email on the forgot-password form, then a reset token is generated, stored, and an email is sent.
- [ ] **AC-8:** Given any email (registered or not), when submitted on the forgot-password form, then the displayed success message is identical (anti-enumeration).
- [ ] **AC-9:** Given a user clicks a valid, unused reset link within 30 minutes, when they submit a new password ≥ 8 characters, then the user's `password_hash` is updated, the token is marked used, and they are redirected to the login page.
- [ ] **AC-10:** Given a user clicks an expired or already-used reset link, when they visit the reset form, then an error is shown and the password is not changed.
- [ ] **AC-11:** Given a logged-in user without a password (e.g., magic-link-only signup), when they submit a password ≥ 8 characters on the "set password" form, then the password is stored and they can subsequently log in with it.
- [ ] **AC-12:** Given a logged-in user, when they click "Log out", then the session is destroyed and the cookie is cleared (same behavior as PRD-0001).

## 7. Open Questions

- **Q1:** Should the "set password" form for existing magic-link-only users be a standalone page or part of a future account-settings page? — **Answer: Standalone page at `/set-password` for now**; will be absorbed into account settings later if that PRD is written.
- **Q2:** Can password reset tokens and magic link tokens share the same table with a `type` column, or should they remain separate? — **Answer: Separate table** (`password_reset_tokens`). Different expiry semantics (30 min vs. 15 min) and single-purpose tables are simpler to reason about and clean up.
- **Q3:** Should existing `magic_link_tokens` infrastructure be reused for reset token generation/sending? — **Answer: Reuse the HMAC signing and email-sending utilities**, but store tokens in `password_reset_tokens` with distinct expiry.

## 8. Linked ADRs

- **ADR-0007** — Authentication Strategy: session cookies + bcrypt passwords + magic links; defines session store and rate limiting baseline.
- **ADR-0003** — Data Model & Schema Design: defines `users` table; this PRD extends it with `password_hash`.
- **ADR-0002** — PostgreSQL: session and token storage medium.
- **ADR-0004** — HTMX: UI interactivity model for forms.

## 9. Linked Quint Spec

**Mandatory.** Every PRD must reference its companion Quint specification.

- **Quint Spec:** `docs/specs/0002-password-authentication.qnt`
- **Quint Spec Status:** proven
- **Verification Summary:** All invariants pass — `invSessionRequiresUser`, `invNoReplayReset`, `invRateLimit`, `invUsedWithinLifetime`, `invValidPassword` verified via `quint typecheck` → `quint test` → `quint run` (100 samples) → `quint verify` (depth 5). See `docs/specs/0002-password-authentication.md` §8 for full output.

*Without a linked, proven Quint spec, this PRD may not proceed to implementation.*

---

*End of PRD. The linked Quint specification in `docs/specs/` must model every requirement marked FR-N above as a state variable, action, or invariant.*
