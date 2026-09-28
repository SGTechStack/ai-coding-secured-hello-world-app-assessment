---
status: accepted
---

# ADR-018: Unmatched paths are not rejected before `CsrfFilter`

No filter answers "this path does not exist" before the security chain has run. A request to an unmapped path goes
through the same chain as any other and ends at the terminal `anyRequest().denyAll()`. An early `404` filter looks
like free hardening. It would remove the CSRF and audit cost of junk paths and the whole surface for injecting raw
URIs. But it would also turn the uniform denial into a route oracle.

## Context

- Unsafe requests to unmapped paths still reach `CsrfFilter`, which loads the session-bound CSRF token before
  comparing it. They also produce an audit row that carries the raw URI. An attacker can therefore buy database and
  log cost with paths that go nowhere.
- A startup-built allowlist is technically possible. `RequestMappingHandlerMapping.getHandlerMethods()` returns every
  controller mapping and can be read at startup instead of written by hand.
- But the mapped set is more than the controller mappings. It must also include actuator endpoints, `/error` and
  resource handling. It could not be verified that `getHandlerMethods()` leaves these out, so the union would become
  the hand-kept list the approach exists to avoid.
- Spring Security has nothing built in for this. CSRF scoping works by request matcher and does not know about
  handler mappings.

## Decision

Declined, on three grounds:

1. **Route oracle.** Today every unmatched path gets the same `401` (anonymous) or `403 ACCESS_DENIED`
   (authenticated) from the terminal `denyAll`. An early `404` for unmapped paths would tell a caller which paths
   exist before any authorization runs. That deliberately weakens the whitelist-first authorization matrix
   (ADR-043).
2. **The mapped set cannot be verified.** The allowlist would have to union handlers from sources that cannot be
   enumerated reliably. A missing entry would `404` a real endpoint, and an extra one would let a junk path through.
3. **It buys nothing the miss budget does not.** A fabricated session cookie on a *mapped* path costs the same
   session lookup, and the session-miss budget (ADR-017) meters that on every path. Audit volume from junk paths is
   capped at the emitter (ADR-019), and the raw URI is capped at 256 characters.

## Consequences

- Junk paths keep costing a trip through the chain, bounded per source by the miss budget and in the log by the
  emitter bound.
- Endpoint coverage is asserted a different way. A generated check compares the authorization matrix, the budget
  table and the audit catalogue against the registered handlers (T-ARCH-005). It does not route requests.
- Anyone reopening this needs a verified enumeration of every handler source, and a way to keep unmatched responses
  uniform with authorization denials.

## Sources

- Spring Framework 7 reference, `RequestMappingHandlerMapping.getHandlerMethods()`.
- Spring Security 7.1.x reference, `CsrfFilter` and request-matcher-based CSRF configuration;
  `authorizeHttpRequests` with `anyRequest().denyAll()`.
