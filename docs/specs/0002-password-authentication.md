# Quint Specification: Password Authentication

| Spec ID | Feature | Status | Linked PRD | Author | Date |
|---------|---------|--------|------------|--------|------|
| 0002 | Password Authentication | proven | `docs/prd/0002-password-authentication.md` | agent | 2026-04-26 |

---

## 1. Purpose

This spec models the password authentication flow for the Comprineas application. It covers FR-1 through FR-15 from the linked PRD, including registration, login (success and failure), rate limiting, password reset, and "set password" for magic-link-only users.

## 2. Modeled Phenomena

- **Users:** Created via registration or simulated magic-link signup. Users may have a password hash ("" means none set).
- **Sessions:** Created on successful registration or login; destroyed on logout.
- **Password reset tokens:** Ephemeral HMAC-signed tokens with 30-step lifetime, single-use enforcement.
- **Failed login attempts:** Tracked per-email in a sliding window for rate limiting.
- **Time:** Discrete global time (`currentTime`) advancing each step.
- **Password validity:** Abstracted as membership in `VALID_PASSWORDS` (models ≥ 8 characters).

## 3. State Variables

| PRD Req | State Variable | Type | Meaning |
|---------|---------------|------|---------|
| FR-1, FR-2 | `users: str -> User` | Map | Registered users by email |
| FR-3, FR-6 | `sessions: str -> Session` | Map | Active server-side sessions |
| FR-9, FR-10 | `resetTokens: str -> ResetToken` | Map | Pending password reset tokens |
| FR-8 | `failedLogins: str -> Set[int]` | Map | Failed login timestamps per email |
| (time model) | `currentTime: int` | Int | Discrete global clock |

## 4. Actions

| PRD Req | Action | Description |
|---------|--------|-------------|
| (boundary) | `createMagicLinkUser(email, name)` | Simulate PRD-0001 creating a user without a password |
| FR-1, FR-2, FR-3 | `register(email, password, name)` | Create user with valid password and session |
| FR-6 | `loginSuccess(email, password)` | Valid credentials → create session |
| FR-5, FR-7, FR-8 | `loginFail(email, password)` | Invalid credentials → record failed attempt |
| FR-8 | `loginBlocked(email)` | Rate-limit guard blocks attempt |
| FR-10 | `requestPasswordReset(email)` | Generate reset token |
| FR-13 | `resetPassword(tokenId, newPassword)` | Consume valid token, update password |
| FR-14 | `setPassword(email, password)` | Magic-link-only user sets a password |
| FR-15 | `logout(sessionId)` | Destroy session |
| (system) | `tick` | Advance global time |

## 5. Invariants

| PRD AC | Invariant | Expression |
|--------|-----------|------------|
| AC-3, AC-11 | `invSessionRequiresUser` | Every session belongs to an existing user |
| AC-10 | `invNoReplayReset` | No reset token is both used and still valid |
| AC-6 | `invRateLimit` | No email exceeds max failed logins in window |
| AC-9, AC-10 | `invUsedWithinLifetime` | Every used reset token was consumed before expiry |
| AC-1 | `invValidPassword` | Every user with a password has a valid ("long enough") password |

## 6. Properties

No additional temporal properties beyond the state invariants are required for this feature.

## 7. Full Spec

See [`0002-password-authentication.qnt`](0002-password-authentication.qnt)

## 8. Verification Log

```
$ quint typecheck docs/specs/0002-password-authentication.qnt
TYPECHECK OK

$ quint test docs/specs/0002-password-authentication.qnt
  passwordAuth
    ok rateLimitTest passed 10000 test(s)

  1 passing (1169ms)

$ quint run docs/specs/0002-password-authentication.qnt --invariant=inv --max-samples=100
[ok] No violation found (116ms at 862 traces/second).
Trace length statistics: max=21, min=21, average=21.00

$ quint verify docs/specs/0002-password-authentication.qnt --invariant=inv --max-steps=5
The outcome is: NoError
[ok] No violation found (13720ms).
```

## 9. Linked PRD

**Mandatory.** Every Quint spec must reference its companion PRD.

- **PRD:** `docs/prd/0002-password-authentication.md`
- **PRD Status:** accepted
- **Requirement Coverage:** FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-7, FR-8, FR-9, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15

---
*End of Quint spec. The spec must be in `docs/specs/0002-password-authentication.qnt`, proven, and bidirectionally linked to its companion PRD before any code is written.*
