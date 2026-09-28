# 41 — Write the platform ADRs: admin module and data model

Type: task
Status: resolved
Blocked by: 34
Blocks: 18

## Question

Write the twelve ADRs that [ticket 34](34-adr-candidate-list.md) routed to this ticket. They go to `docs/adr/`, in
the `domain-modeling` skill's ADR format: **ADR-042 to ADR-053**. That covers the following:

- the role model and the authorization matrix;
- the tombstone and identifier canonicalisation;
- lazy forced-change expiry and bootstrap;
- the two-admin invariant and the factor-reset exemption;
- UUIDv4 keys and the schema gate;
- the tombstone key's forward-only versioning;
- MFA enrolment as row existence.

*Split from [36](36-adrs-platform.md) by ticket 34.* It is owner 36's work, by 17 Answer §2's source split.

ADR-048 folds in eight amendments. Read every one before writing it.

**ADR-046 was amended by [ticket 44](44-forced-change-expiry-password-oracle.md) §Answer before it was written.** The
expiry is refused in `preAuthenticationChecks` with Spring's `CredentialsExpiredException`, and
`isCredentialsNonExpired()` always returns `true`. Write both into the decision. Put the post-authentication slot,
and the password oracle it creates in the audit stream, into Considered Options. Its routing row is already updated.
The primary sources are R-AUD-018 and R-LCK-001.

The ID, title, merged sources, attached amendments and filter answer for each ADR are in
[`adr-routing/routing.md`](../adr-routing/routing.md) §2.

## Rules

Same as [35](35-adrs-authentication.md) §Rules.

## Done when

- ADR-042 to ADR-053 exist, and their `docs/adr/README.md` index rows move from `reserved`.
- The grep in 35's Done-when returns nothing for these files.

## Answer

**ADR-042 to ADR-053 are written in `docs/adr/` and marked accepted in the index, as routed, with no regrouping.**
All eight ADR-048 amendments and ticket 44's ADR-046 brief are folded in. The Done-when grep returns nothing for
these files, and every `R-`, `T-` and `REJ-` ID they cite exists as a row in the register, test plan or index.
No decision reversed.

### Checked at source

H2 `SELECT … FOR UPDATE` restrictions and visibility (H2 commands page); Hibernate 7.4 `AbstractSchemaValidator`
(`IDX` skip, positional walk) and `ConstraintValidationType` (`NONE` when unset); the two `hibernate.tooling.schema.*`
property names (7.4 constant values); `@UuidGenerator` `AUTO` → `RANDOM` (v4); RFC 9562 §5.7; RFC 5321 §2.3.11 and
§2.4; Boot's event order (`ApplicationStartedEvent` before runners, `ApplicationReadyEvent` after) and `JavaMigration`
beans; the pre/post-check slots (verification asset §14, §18); the Standard's Decision Logic, §2 step 7 and its
Spring note, §3.5, §4, §5; Questions Q3 and Q29; PDPA's Retention Limitation Obligation (PDPC guidelines ch. 18).

### What the checking changed

- **ADR-046's stamping set, resolved against its stale amendment.** C:11-M-12 still lists admin create and admin reset.
  The live set is bootstrap seed, re-enable and the runner's single run. Tier-2 sets the flag without stamping.
- **ADR-046 carries one open item it cannot decide.** Nothing on the map says whether completing a forced change
  clears `credential_issued_at`. If it does not, a later tier-2 disable meets a stale issue time and the login is
  refused at once, which is what the no-stamp rule was meant to prevent. Recorded in the ADR as owed to the spec.
  Ticket 18 should check it.
- **ADR-046 cites the Standard's own warning.** §2 step 7's Spring note warns against `isCredentialsNonExpired()` for
  the *flag*. The ADR states why that warning does not cover the *deadline*, so it is not read as a contradiction.
- **ADR-048's channel list is restated.** Ticket 15 counted the cascade as a non-mutation channel, but it runs on the
  guarded delete path. The uniform lock set covers it. It stays on the monitoring list. Break-glass and the runner
  are one channel today (ADR-070), not two.
- **ADR-048's guard predicate.** The guard counts enabled ∧ activated ∧ TOTP row. The gauge reads all five
  `authenticable` terms. Both read the one definition. This is how ticket 09's "the guard reads three terms today"
  and ticket 12's lock set fit together.
- **ADR-049: tier 2 is also cleared by the runner** (per ADR-027), not only by factor reset.
- **ADR-047: ticket 11's "Q7" citation was wrong.** Q7 is session persistence, so the ADR cites Q3 only. Q29 does
  recommend indefinite tombstone retention, so ADR-044 cites it.
- **ADR-051: Hibernate has a third value, `ALL`.** The sources never weighed it. The ADR records why `NAMED` already
  covers every index here.
- **ADR-052 is worded to match ADR-004** as written: a *keyed* pepper could not retire; ADR-004 declines the
  encryption-layer variant on other grounds.

### Register corrections handed to ticket 18

- **R-ADM-002** still names the tombstone field `uuid`. It is `user_id`.
- **R-ADM-008** and **R-ADM-015** say "all four mutating paths". That is right for the lock set, but the *count*
  runs on three, because factor reset is exempt (ADR-049). The wording should say which.

### Handover items (ticket 25)

None new. The ones these ADRs rest on already exist: R-ADM-017, R-ADM-018, R-ADM-019 and R-OBS-007.
