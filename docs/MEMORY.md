# Project Memory

## Quint Verification Standards

All Quint specifications must be verified with the following minimum parameters:

- **`quint run`**: `--max-samples=200` (minimum)
- **`quint verify`**: `--max-steps=10` (minimum; note: deeper bounds may timeout due to Apalache's exponential state-space growth for specs with `nondet` over multiple finite sets)

This applies to ALL specs in `docs/specs/`. Verification with lower values is not considered sufficient proof.

For specs that timeout during `quint verify` beyond step 10, document which invariants passed
formal verification and which relied on simulation (`quint run`) coverage instead.