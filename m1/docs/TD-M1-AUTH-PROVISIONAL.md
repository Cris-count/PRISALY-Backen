# TD-M1-AUTH-PROVISIONAL

## Status

Open. Opaque bearer tokens stored in the JVM heap for **local integration only**.

## Not a security closure

M1 is not “done” while this debt remains. Do not present the prototype as ARQ-008.3-final auth.

## Behavior

- Cryptographically secure token bytes (`SecureRandom`).
- Explicit expiry.
- Invalidation on logout.
- Each request reloads user state: active account + effective permissions (not “token exists in map” alone).
- Spring Security `STATELESS` means no HTTP servlet session; the prototype **still** keeps auth state in memory.

## Exit criteria (substitute or ratify)

1. Architecture ratifies the definitive mechanism against ARQ-008.3.
2. Tokens survive process restart **or** a deliberate shared store is approved (without inventing BD-005.2 tables).
3. Revocation and inactive accounts are proven under concurrent use.
4. Multi-device policy is written and tested.
5. This document is closed with evidence and a dated decision.
