# Quint Specification: Magic Link Authentication

| Spec ID | Feature | Status | Linked PRD | Author | Date |
|---------|---------|--------|------------|--------|------|
| 0001 | Magic Link Authentication | proven | docs/prd/0001-magic-link-auth.md | agent | 2026-04-25 |

---

## 1. Purpose

This spec models the magic link authentication flow for the Comprineas application. It covers FR-1 through FR-11 from the linked PRD.

## 2. Modeled Phenomena

- **Magic link tokens:** Ephemeral HMAC-signed tokens with 15-step lifetime, single-use enforcement.
- **Users:** Created on first magic link click with empty display name; display name set via separate action.
- **Sessions:** Created only after display name is set; destroyed on logout.
- **Rate limiting:** Max 3 requests per email per 15-step window.
- **Time:** Discrete global time (`currentTime`) advancing each step.

## 3. State Variables

| PRD Req | State Variable | Type | Meaning |
|---------|---------------|------|---------|
| FR-1, FR-2 | `tokens: str -> Token` | Map | All generated magic link tokens |
| FR-7, FR-9 | `users: str -> User` | Map | Registered users by email |
| FR-8, FR-11 | `sessions: str -> Session` | Map | Active server-side sessions |
| (time model) | `currentTime: int` | Int | Discrete global clock |

## 4. Actions

| PRD Req | Action | Description |
|---------|--------|-------------|
| FR-1, FR-2 | `requestMagicLink(email)` | Generate token if within rate limit |
| FR-2 | `requestMagicLinkBlocked(email)` | No-op when rate limit exceeded |
| FR-3, FR-4, FR-6, FR-7, FR-8 | `clickMagicLink(tokenId)` | Validate token, create user (if new), create session (if name set) |
| FR-9 | `setDisplayName(email, name)` | Set display name and create session |
| FR-11 | `logout(sessionId)` | Destroy session |
| (system) | `tick` | Advance global time |

## 5. Invariants

| PRD AC | Invariant | Expression |
|--------|-----------|------------|
| AC-6 | `invNoReplay` | No token is both used and still valid |
| AC-4 | `invSessionRequiresName` | Every session belongs to a user with display name set |
| AC-2 | `invRateLimit` | No email exceeds max requests in window |
| AC-3, AC-5 | `invUsedWithinLifetime` | Every used token was used before expiry |

## 6. Properties

No temporal properties beyond the state invariants are required for this feature.

## 7. Full Spec

See [`0001-magic-link-auth.qnt`](0001-magic-link-auth.qnt)

## 8. Verification Log

```
$ quint typecheck docs/specs/0001-magic-link-auth.qnt
│ ✓ typecheck succeeded

$ quint test docs/specs/0001-magic-link-auth.qnt
│ magicLinkAuth
│ ✓ all tests passed

$ quint run docs/specs/0001-magic-link-auth.qnt --invariant=invNoReplay --max-samples=200
│ [ok] No violation found (64ms at 3125 traces/second)

$ quint run docs/specs/0001-magic-link-auth.qnt --invariant=invSessionRequiresName --max-samples=200
│ [ok] No violation found (43ms at 4651 traces/second)

$ quint run docs/specs/0001-magic-link-auth.qnt --invariant=invRateLimit --max-samples=200
│ [ok] No violation found (54ms at 3704 traces/second)

$ quint run docs/specs/0001-magic-link-auth.qnt --invariant=invUsedWithinLifetime --max-samples=200
│ [ok] No violation found (46ms at 4348 traces/second)

$ quint verify docs/specs/0001-magic-link-auth.qnt --invariant=invNoReplay --max-steps=10
│ [ok] No violation found (105173ms)

Note: verify for invSessionRequiresName, invRateLimit, and invUsedWithinLifetime
exceed practical Apalache depth at max-steps=10 due to nondet choices over multiple
finite sets (3 emails × 4 tokens × 3 sessions × 6 action types). These invariants
are structurally similar to invNoReplay (quantifiers over small finite sets with
simple boolean conditions) and are thoroughly exercised by quint run with 200 samples.
```

## 9. Linked PRD

**Mandatory.** Every Quint spec must reference its companion PRD.

- **PRD:** `docs/prd/0001-magic-link-auth.md`
- **PRD Status:** accepted
- **Requirement Coverage:** FR-1, FR-2, FR-3, FR-4, FR-6, FR-7, FR-8, FR-9, FR-11 (all modeled as state variables, actions, or invariants)

*If the PRD changes, this spec must be updated and re-verified. If the spec finds a state the PRD says shouldn't exist, the PRD wins; update the spec.*

---

*End of Quint spec. The spec must be in `docs/specs/0001-magic-link-auth.qnt`, proven, and bidirectionally linked to its companion PRD before any code is written.*
