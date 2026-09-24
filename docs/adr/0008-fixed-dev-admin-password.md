# ADR 0008 — A fixed, known admin password in dev

- **Status:** Accepted, with the reopened finding recorded
- **Date:** 2026-09-24
- **Reverses:** part of the TM-07 remediation
- **Context:** local development, bootstrap credentials

## Decision

The `dev` profile defaults `app.admin.password` to `password1234`.

The base profile keeps no default at all, so outside `dev` an unset
`APP_ADMIN_PASSWORD` still fails startup.

## Why

Product decision: local development and demos want a predictable credential.
Copying a freshly generated password out of the backend console on every restart is
friction, and it makes a demo dependent on having the server log to hand.

## What this reverses, stated plainly

This is the credential the threat model recorded as **TM-07, a High finding**. Not
a similar one — the same literal string. The finding was that the dev profile
hardcoded `admin` / `password1234`, that the README published it and the login form
prefilled it, so any reachable dev or demo instance was a one-guess admin takeover
of an account that can disable, re-role and delete every other account.

Reinstating the default reopens that finding for any instance running this profile.
The threat model has been updated to say so; it does not continue to claim TM-07 is
closed. That is the point of recording this as an ADR rather than a one-line config
change: the decision is defensible, and it should not be discoverable only by
reading YAML.

## What limits the blast radius

Three things, and it is worth being precise about which are real controls and
which are not:

1. **The base profile has no default.** `${APP_ADMIN_PASSWORD}` with no fallback is
   unresolvable, so a non-dev deployment that forgets the variable fails to start
   rather than seeding an admin whose password is published in this repository.
   This is a real control, and `ProductionProfileTest` asserts it.
2. **The generate-on-blank path still exists.** Setting `APP_ADMIN_PASSWORD=`
   restores the previous behaviour — a random password per boot, logged once at
   `WARN`. Anyone running the dev profile somewhere reachable should use it.
   `AdminBootstrapPasswordGenerationTest` still covers that branch, including that
   a blank configured password is never hashed as-is.
3. **The login form does not prefill it.** This is the weakest of the three and
   should not be leaned on: the password is in this file, in `application.yml` and
   in the README. Not putting it in the DOM is tidiness, not protection.

## Consequences

- Any dev or demo instance on a reachable address is a one-guess admin takeover
  unless it overrides the password. There is no technical control preventing
  someone from running this profile in that situation.
- `AdminBootstrapPasswordGenerationTest` lost its assertion that `password1234`
  could not be the seeded password. That assertion was removed rather than
  adjusted, because it now contradicts the specified default — a test asserting the
  opposite of the requirement is worse than no test. What replaced it asserts the
  *generated* path still produces something long and unguessable, which is the
  property that branch exists for.
- If this application ever stops being an assessment exercise, this ADR should be
  the first thing revisited.

## Related

- Threat model TM-07 (reopened, High)
- ADR 0004 (H2 everywhere) — the other decision taken at the same time
