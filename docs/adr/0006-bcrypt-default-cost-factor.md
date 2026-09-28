# ADR-0006: BCrypt with library-default cost factor

## Status

Accepted

## Context

Password storage uses `BCryptPasswordEncoder` with no explicit strength
argument:

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

This resolves to Spring Security's current default cost factor (strength
10, i.e. 2^10 rounds). No alternative cost factor was evaluated against
this app's expected load or threat model before choosing the default.

## Decision

Accept the library default rather than specifying a cost factor explicitly,
on the basis that it's a reasonable, widely-used baseline for a reference
app and avoids a magic number that would need its own justification.

## Consequences

- The effective cost factor is implicitly whatever Spring Security ships as
  its default at the version this app depends on — if that default ever
  changes across a major Spring Security upgrade, this app's hashing cost
  changes silently along with it, with no test or config surfacing the
  change.
- If this app is ever used as a template for a deployment with real
  password-cracking exposure or specific compliance requirements, the cost
  factor should be made explicit (e.g. `new BCryptPasswordEncoder(12)`) and
  benchmarked against acceptable login-latency budgets, rather than
  inherited implicitly.
