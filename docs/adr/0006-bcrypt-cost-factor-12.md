# ADR-0006: BCrypt with an explicit cost factor of 12

## Status

Accepted

## Context

The PRD mandates BCrypt for password storage. App-Standards (UAC §3.5) recommends a work factor
of at least 12. Relying on Spring Security's implicit default (currently 10) would let the
hashing cost change silently on a library upgrade.

## Decision

```java
@Bean
public PasswordEncoder passwordEncoder(@Value("${app.security.password.bcrypt-strength:12}") int strength) {
    return new BCryptPasswordEncoder(strength);
}
```

- The cost is explicit and configurable via `app.security.password.bcrypt-strength` (default 12).
- BCrypt only uses the first 72 bytes of a password, so `PasswordPolicy` rejects anything longer
  (at registration, reset and bootstrap). Login rejects it with the generic 401 instead of hashing
  a truncated value.

## Consequences

- Each hash costs roughly 4× the CPU of cost 10. Login latency rises a little, and rate limits
  (ADR-0005) matter more for DoS resistance. The test suite also runs slower.
- BCrypt stores the cost inside each hash. If the cost is raised later, existing hashes still
  verify, but they aren't re-hashed automatically.
- Argon2id (the App-Standards preference) is a documented deviation, because the PRD mandates
  BCrypt (ADR-0012).
