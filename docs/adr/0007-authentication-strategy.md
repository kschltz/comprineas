---
status: accepted
date: 2026-04-25
decision-makers: user (kschltz), agent
consulted: {}
informed: {}
---

# Authentication Strategy

## Context and Problem Statement

The application requires user authentication for list creation and participation tracking. Users log in with email, and the system must support both password-based authentication and magic links (passwordless). The authentication system must integrate with the kit-clj Ring-based server and store session state reliably.

Key requirements:
- Email-based login (password or magic link).
- No social login (not in requirements).
- Simple onboarding: email + display name, no complex account registration.
- Session management for logged-in users.
- User identity needed for list participation tracking ("my lists" view).
- Email delivery is required for magic links.

## Decision Drivers

- The user explicitly prefers email-based auth with password and magic link options.
- No social login or OAuth providers requested.
- Server-rendered Ring application — session-based auth is natural.
- Need both password and magic link to coexist.
- Scaling target is small (family/friends use), not enterprise.
- Must store user credentials securely (bcrypt for passwords, HMAC-SHA256 for magic links).
- **Email delivery mechanism must be operational for magic links** (SendGrid, AWS SES, or SMTP — choose in implementation, but confirm account before accepting this ADR).
- Minimum password requirements must be defined: at least 8 characters, no complexity required (grocery list app, not banking).

## Considered Options

### 1. Session Cookies with Server-Side Sessions

Stateful sessions stored on the server, referenced by a signed cookie.

- Ring middleware (e.g., `ring.middleware.session`) reads/writes session cookies.
- Session data stored in **PostgreSQL** (`:store (jdbc-store db-spec)`) for dev/prod parity per ADR-0002. Redis deferred until horizontal scaling is needed.
- Both password+bcrypt and magic links supported: user authenticates, server creates session.
- Session revocation is immediate: delete the session row.
- **Magic link tokens**: HMAC-SHA256 signed with server secret, 15-minute expiry, single-use (deleted after validation or on next login).
- Fits the server-rendered model perfectly.

### 2. JWT Tokens (Stateless)

Signed JSON Web Tokens stored client-side.

- Server issues JWT on login; client sends JWT in `Authorization` header.
- No server-side session storage needed.
- Token revocation is hard: requires a blacklist or short expiry + refresh tokens.
- Refresh token lifecycle adds complexity.
- Overkill for a server-rendered app where cookies are simpler.

### 3. Password Only (No Magic Links)

Standard username/password authentication.

- Simple, well-understood.
- Users must remember passwords.
- No email dependency.
- Doesn't meet the requirement for magic link support.

### 4. Magic Links Only (Passwordless)

Users log in by clicking a link sent to their email.

- No passwords to manage or breach.
- Email delivery is a hard dependency.
- Users might wait for email; UX is slower than password entry.
- Doesn't meet the requirement for password support.

### 5. OAuth2 / OIDC (Google, GitHub, etc.)

External identity providers.

- Delegates auth to trusted providers.
- No password storage needed.
- Users may not want to link personal accounts to a grocery list app.
- Not requested by the user.
- Adds complexity: OAuth flow, redirect URLs, provider app registration.

### 6. Passkeys / WebAuthn

Modern phishing-resistant authentication using cryptographic credentials.

- Very secure; no passwords.
- Limited browser support and user familiarity.
- More complex to implement than passwords or magic links.
- Overkill for a grocery list app.

## Decision Outcome

Chosen option: **"Session Cookies with Server-Side Sessions, supporting both password+bcrypt and magic links simultaneously"**, because:
- The user wants both password and magic link support.
- Server-side sessions fit the Ring/kit-clj server-rendered model naturally.
- Session revocation is trivial (delete row) — important for logout and security.
- No JWT complexity or token refresh lifecycle.
- Session storage in PostgreSQL aligns with ADR-0002 (same database, no extra infra).
- Email delivery (for magic links) is the only external dependency.

JWT is rejected because stateless revocation is hard and adds unnecessary complexity. Password-only and magic-link-only are rejected because they don't meet the dual-support requirement. OAuth2 is rejected because it wasn't requested. Passkeys are rejected because they're overkill at this scale.

### Consequences

- Good, because fits Ring's session middleware model (`ring.middleware.session`).
- Good, because easy session revocation (delete session row or set flag).
- Good, because both password and magic link work identically after authentication — both create a server-side session.
- Good, because no JWT token refresh logic needed.
- Good, because PostgreSQL session storage aligns with existing database choice (ADR-0002).
- Bad, because scaling beyond a single server requires a shared session store (Redis or sticky sessions) — deferred; single-server target per ADR-0002.
- Bad, because **email delivery is a hard dependency** for magic links: needs operational SendGrid/AWS SES account or SMTP relay.
- Bad, because magic link tokens require HMAC-SHA256 signing with server secret and strict 15-minute expiry to prevent replay attacks.
- Bad, because **password security baseline must be enforced**: bcrypt cost factor ≥ 10, minimum password length 8 characters, rate-limited login attempts (max 5/minute per IP).

### Confirmation

The decision is confirmed by:
- Ring session middleware configured in kit-clj (`wrap-session` with **PostgreSQL JDBC store**, not in-memory).
- `sessions` table in PostgreSQL: `id`, `user_id`, `token`, `created_at`, `expires_at` (default 24h expiry, sliding).
- Password login: bcrypt (cost factor 10+) hash comparison, rate limiter active, session creation on success.
- Magic link: **HMAC-SHA256** signed URL sent via email, 15-minute expiry, single-use token validated on visit, session created on success.
- Logout: session row deleted, cookie cleared.
- **Password recovery**: reset token (HMAC-SHA256, 30-min expiry) sent via email; user sets new password.
- Manual test: log in via password, create a list, verify session persists across page loads.
- Manual test: log in via magic link, verify session created after clicking link.
- **Negative test**: 6 failed logins within 1 minute from same IP → temporary lockout (429 Too Many Requests).
- **Session expiry test**: wait 24 hours, verify re-auth required.

## Pros and Cons of the Options

### Session Cookies with Server-Side Sessions

- Good, because fits Ring/kit-clj server-rendered model.
- Good, because easy revocation.
- Good, because supports both password and magic link simultaneously.
- Bad, because requires session storage table.
- Bad, because scaling beyond one server needs shared store.

### JWT Tokens

- Good, because stateless — no server-side session storage.
- Good, because works well with API-first architectures.
- Bad, because revocation requires blacklist or short expiry.
- Bad, because refresh token lifecycle adds complexity.
- Bad, because less natural for server-rendered cookie-based apps.

### Password Only

- Good, because simple and well-understood.
- Bad, because doesn't meet magic link requirement.
- Bad, because users must remember/manage passwords.

### Magic Links Only

- Good, because no passwords to breach.
- Bad, because doesn't meet password requirement.
- Bad, because email delivery is a hard dependency.
- Bad, because UX is slower than password entry.

### OAuth2 / OIDC

- Good, because delegates security to trusted providers.
- Good, because no password storage needed.
- Bad, because not requested by the user.
- Bad, because adds OAuth flow complexity.
- Bad, because users may not want to link accounts.

### Passkeys / WebAuthn

- Good, because phishing-resistant.
- Bad, because limited browser support.
- Bad, because complex implementation.
- Bad, because overkill for a grocery list app.

## More Information

- Ring session middleware: https://github.com/ring-clojure/ring/wiki/Sessions
- bcrypt for Clojure: https://github.com/weavejester/crypto-password
- Magic link token signing: JWT or HMAC-SHA256 with server secret; short expiry (15-30 min).
- Email delivery in Clojure: https://github.com/drewr/postal (SMTP) or SendGrid API.
- Session storage: `ring.middleware.session.cookie` (in-memory, not for prod), PostgreSQL via custom store, or Redis via `carmine`.

