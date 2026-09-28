---
status: accepted
---

# ADR-067: No `test` profile; tests override capture beans in the default context

There is no `application-test.yml` and no `test` Spring profile, even though a `test` profile is the Spring Boot
convention. Tests run the real `dev` configuration. The only change is one fixed bean override: a capturing
`EmailService` replaces the stub. Production-only values are asserted by binding tests that read the production
configuration, and are never executed.

## Context

The configuration has exactly two postures: `dev`, and everything else. Every rule for non-local environments keys
on "not `dev`":

- the `__Host-SESSION` cookie name and the `Secure` flag;
- every secret being mandatory, with no default;
- the prohibited-configuration entries;
- the refresh-phase validator's datasource rule.

So a `test` profile is not neutral. Under these rules it is **a production profile**. That leaves two ways it can
go:

- it inherits the production posture, which a local test run cannot satisfy; or
- it carries its own relaxations. That makes a third configuration surface that nothing proves matches either of the
  real ones, and it teaches the codebase that "not `dev`" does not mean "production".

## Decision

- `ctx-default` runs the `dev` profile with exactly one override: a capturing `EmailService` bean in place of the
  stub. Tests read reset and activation tokens from the capture.
  - Because of the override, the `dev`-only reset-link logger never receives a token in shared contexts.
  - One dedicated test runs the real stub, to prove the reset link stays confined to `dev`.
- Every context uses a temporary-directory `jdbc:h2:file` path, because the validator rejects in-memory H2 in every
  profile.
- `ctx-nondev` exists to assert production values: BCrypt cost 12, the lockout constants, the session cleanup cron,
  and `LOCK_TIMEOUT=1000`. Production-only values are asserted by reading the production configuration, or by
  `ApplicationContextRunner` or direct property-binding checks, without refreshing a production context. A test
  that refreshes one is executing a posture the plan says is never executed.
- Test-only settings that do not touch the security posture, such as BCrypt cost 4 in the shared contexts or the
  cleanup cron set to `-`, are properties of the named contexts, not of a profile.

## Considered options

- **An `application-test.yml`.** The Boot convention. Rejected for the reason above.
- **Tests on the `dev` profile with ad-hoc `@MockitoBean`s.** Each set of mocks fragments the context cache
  (ADR-065), and a mocked collaborator is a control that is not under test.
- **`dev` plus one fixed capture-bean override (chosen).**

## Consequences

- Any future gate that must behave differently when mail transport exists keys on a **declared transport
  property**, never on the `EmailService` bean type. `ctx-default` replaces that bean, so a bean-type gate would read
  "transport present" in every shared test. This binds the recovery-code route and the recovery-address confirmation
  when they are built (ADR-070, ADR-071; T-CRED-023).
- Adding a second fixed override needs a stated reason, because it changes what `ctx-default` proves.
