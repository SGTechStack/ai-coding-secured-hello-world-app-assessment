
# 30 — Decide whether ticket 11's one-admin bootstrap survives the removal of its premise

Type: grilling
Status: resolved
Blocked by: 09, 11, 28

## Question

Ticket 11 decided **"One admin, not two"**. It rejected seeding two admins because that needs two env-supplied
passwords and two enrolments, and "creates a second standing credential just as likely to be shared or forgotten
— it converts a recovery problem into a credential-hygiene problem". The rejection rests on one stated premise:

> per-account lockout **auto-expires** after 20 minutes […], so a locked-out sole admin is delayed rather than
> locked out, and the break-glass runbook is reduced to the genuine case — lost authenticator with one enrolled
> admin.

**Ticket 09 §R removed that premise.** The NIST §3.2.2 cap disables at 100 consecutive failures with **no
auto-lift**, cleared only by rebinding. Ticket 09 §R.2 shows one host can drive 36 such disables an hour. Ticket
09 handled the consequence by narrowing ADR 13 and building an operator rebinding runner. It never revisited the
one-admin decision. [Ticket 28](28-out-of-band-privileged-channels.md) then found that the runner **could never
execute**, and made it work only as an offline planned outage requiring deploy-level access.

So the sole admin's recovery now rests entirely on a tool that needs a host operator and an outage. **Does "one
admin, not two" survive, now that the premise it states is gone?**

## What to decide

- **Whether the premise's removal changes the answer**, or whether ticket 11's hygiene argument carries the
  decision alone. It may: the second-credential cost is real either way.
- **If a second line is wanted, which object.** Published emergency-access practice (Microsoft Entra's guidance;
  see ticket 28's [verification asset §8](../research/rebinding-runner-channel-and-process-model-verification.md))
  means **standing emergency accounts not assigned to individuals**. That is a different object from two seeded
  *human* admins who must each complete forced change and enrolment. An unassigned standing identity is a **shared
  credential**, which is exactly what ticket 11 rejected, and it would undo the per-invocation attribution ticket 28
  §8 just built. Two seeded humans don't give an always-available emergency identity either: ticket 09's third
  fresh-install path exists until **both** have enrolled.
- **Whether the two-enrolled-admins rule becomes a bootstrap precondition**, which ticket 11 explicitly declined,
  or stays a removal-only guard.
- **What this does to ADR 13** and to ticket 25's statement that "the system runs with **one** admin".

## Done when

The decision is restated against the current premise set, with the stated premise corrected in ticket 11 either
way. If the decision changes, the bootstrap, ADR 13, ticket 25's runbook framing and ticket 16's bootstrap tests
are amended to match.

## Answer

**Seed one admin, conditional on a second *enrolled* admin being invited before go-live; exempt factor reset (only)
from the two-admin count; and restate ADR 13 as three recovery routes keyed on one predicate.** The seeding decision
survives, but not on the grounds ticket 11 stated, and not on hygiene alone.

### 1. Premises checked before answering

Three things this ticket took for granted did not hold at source:

1. **The premise was not left standing when it fell; it was replaced, and the replacement is what weakened.**
   Ticket 09 §R's amendment to ticket 11 (11:789–806, 09:1235–1239) swapped "auto-expiry closes the sole-admin path"
   for "the operator rebinding runner closes the cap path". Ticket 28 then made that runner an offline planned outage
   needing deploy-level access. Ticket 11:394–400 was never corrected in place.
2. **A second admin does not close the targeted cap path.** Ticket 31's correction: capping is "never faster than
   ≈14 h per account, whatever the source count" (09:1545–1547), and one source works up to five chains at once
   (R.6). An attacker who can confirm usernames through `USERNAME_UNAVAILABLE` caps two admins in the same ≈14 h as
   one, and both still count as enrolled (11:810–814). A targeted all-admin cap ends at the runner at any admin count.
3. **"The third fresh-install path exists until both have enrolled"** is stated only in this ticket. Ticket 09 defines
   that path against the sole seeded admin before first enrolment (09:1235–1237).

And one mechanic found during grilling that changed the answer: **at exactly two enrolled admins, lost-phone recovery
was runner-only too.** `DELETE /api/admin/users/{uuid}/totp` runs through the invariant (11:217), evaluated against
the post-change state (11:252), so A resetting B's factor would leave one and is refused. Ticket 19's own compensating
control for deferring recovery codes ("admin-resets-admin plus a two-enrolled-admins invariant", 19:224–232) refused
the reset it was compensating with, at the minimum population it enforced.

### 2. Seeding: one, conditional on the invite

**Seed one admin.** Seeding two needs two env-supplied standing credentials (ticket 11's hygiene argument, which is
aimed at *seeding*, not at having two admins), and it would not close the targeted-cap path (§1.2).

**The decision holds only because a second enrolled admin exists before go-live.** With one admin, a forgotten
password and a lost phone — the everyday cases, not the attack — are runner-only planned outages; ticket 28 §4
relaxed check 5 for exactly these (28:241–245). A second admin, invited through ticket 10's activation-token flow,
sets their own password and enrols their own TOTP: no env-supplied credential, nothing shared, per-person
attribution. That converts the everyday cases to in-app recovery (§4, route 2).

**"Enrolled", not "invited".** A pending invite does not count (10:288–293); the expectation uses ticket 11's
`authenticable` predicate (11:816–818), which a pending invite fails on `activated`.

**Rejected:**
- *Two seeded humans* — two standing env credentials; does not close §1.2; does not give an always-available
  emergency identity either.
- *A standing emergency account not assigned to an individual* (Entra practice, [asset §8](../research/rebinding-runner-channel-and-process-model-verification.md))
  — a shared credential, which ticket 11 rejected, and it undoes ticket 28 §8's per-invocation attribution. The
  asset itself records that "nothing in it binds us".

### 3. The invariant: never a bootstrap gate; factor reset exempt from the count

**Not a bootstrap precondition.** Circularity is the weak reason (a gate could exempt the invite endpoint). The
reason that holds: a gate on "≥ 2" closes the admin surface on the survivor exactly when one of two admins is capped
or has lost their phone, making the second admin useless when needed. The invariant stays a removal-only guard.

**Scope: disable, demote, delete. Factor reset (`DELETE .../totp`) is exempt from the ≥ 2 count; `actor ≠ subject`
stays.** Zero is unreachable on that path regardless: the endpoint is factor-gated and actor ≠ subject, so the actor
is always an enrolled admin other than the subject.

**Why this path, and only this path, can be exempt — who can reverse it.** Disable and demote are *reversible*
(re-enable, promote); only delete is permanent. So "the others are permanent" is not the distinction. The distinction
is **who can restore the count**:
- After disable or demote, only another enrolled admin can restore it — at two admins, the actor who removed B.
- After a factor reset, **B restores it alone**: sessions killed, password-only login, 422
  `FACTOR_ENROLMENT_REQUIRED`, SPA routes to `/settings/mfa` (23:621–623).

What the invariant protects — a second admin able to act independently of the first — therefore survives a factor
reset and does not survive the other three. That is why exempting only this path is not an erosion.

**(a) Go live at three was rejected on cost, not security.** At three or more the guard bounds nothing on this path
either (11:907–908 already records "with three or more enrolled admins the composed path is open"), so (a) buys the
same security as the exemption at a higher staffing cost.

**Side effect, in the right direction:** a tier-2 TOTP disable is cleared only by `DELETE .../totp` (23:494); at two
admins that was also refused, and is now in-app.

**TM-08 residual, stated exactly.** At exactly two enrolled admins, A can now take B over completely — password reset
plus factor reset, and A holds B's reset token, so A can also be the one who re-enrols as B. Before, A got B's
password but not B's factor. At three or more nothing changes; it was already open. **Detectors:** ticket 13 row 18
reason `ADMIN_RESET` (13:194) and row 33 `totp-remove` (13:225). **Partial compensation, if and only if the deployer
enables export (§5):** the authenticable-admins gauge drops below 2 during the takeover step. Two limits: it also
fires on every legitimate factor reset at two, so it signals *that* something happened, not *whether* it was a
takeover; and at three or more it does not fire at all (3 → 2). *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*

### 4. ADR 13, final wording

Three routes, keyed on **one** predicate — ticket 11's `authenticable` (11:816–818; origin 09:1242–1245). Routes
point at that definition; they do not paraphrase it.

1. **Lockout (either axis): auto-expiry.** Password axis on the 20/40/60 ladder (09 §R.5); TOTP tier 1 at 20 minutes
   (23:484). Neither lock is a term of `authenticable`.
2. **Forgotten password, single-account cap, lost phone or tier-2 TOTP disable, when another `authenticable` admin
   exists: in-app** — admin password reset (redemption clears the cap, 10:719) or factor reset (exempt, §3).
   **If the other admin is `authenticable` but currently locked out, route 2 waits on route 1 and does not fall
   through to route 3.** Otherwise an operator goes to the runner for something that clears itself within the hour —
   the misreading 25:100–101 was written to prevent. *Consolidated into the register (ticket 33): R-ADM-019. Amend the table by ID, not this list.*
3. **No `authenticable` admin other than the subject** — sole admin, a targeted cap on every admin, zero admins:
   **ticket 28's runner, as a planned outage**, pass-with-note **pending the first rehearsal**. A red rehearsal grades
   ASVS 6.1.1 (L1) F and reopens ticket 28 (28:387–390, 09:1489–1492). *Consolidated into the register (ticket 33): R-RUN-002. Amend the table by ID, not this list.*

**Corrected premise for ticket 11:394–400** (edited in place there): the lockout path is closed by auto-expiry; the
everyday recovery cases are closed in-app only when a second authenticable admin exists, which is why the invite is a
go-live condition; the cap path when no other authenticable admin exists — targeted or not — is closed only by ticket
28's runner, as a planned outage and conditionally on the rehearsal; and a second *seeded* admin would change none of
that.

### 5. Detection of "nobody invited the second admin"

- **The transition signal cannot see it.** Ticket 28's enrolled-count-reaches-zero is per-event (21:802–805); a count
  that starts at one never transitions.
- **Startup check: dropped.** Every fresh install boots at one, so it fires on every first boot and never again.
- **Gauge: kept, but it is a deployer capability, not an application one.** It extends the **zero-authenticable-admins
  signal (21:676–679)**, not ticket 28's enrolled-count alert — two capped admins still count as two enrolled. A
  Micrometer gauge needs no scheduler (21:826–829), **but** export ships disabled (21:228–230), health is the only
  exposed endpoint (21:187–188), and lm-16's alerting half is still a High. With export off the gauge sits in a
  registry nothing reads. Its state object must be a strongly held bean or it reads `NaN` (29:235, 29:353). *Consolidated into the register (ticket 33): R-OBS-007. Amend the table by ID, not this list.*
- **The only control that works out of the box is the step-7 rehearsal attestation**, proven by the authenticable-admin
  count in the rehearsal record.

### Handover items (ticket 25)

- **Two enrolled admins before go-live.** The seeded admin invites a second through the activation-token flow; the
  invitee redeems, sets a password and enrols TOTP. Pending invites do not count. Discharges ticket 30's go-live
  condition on ADR 12 and route 2 of ADR 13, supporting ASVS 6.1.1 (L1). Enforceable: no — deliberately not a gate
  (§3). Proof: the step-7 rehearsal record shows an `authenticable` admin count ≥ 2 on the deployed database. *Consolidated into the register (ticket 33): R-ADM-017. Amend the table by ID, not this list.*
- **Promote before demote applies from day one.** At exactly two enrolled admins neither can be disabled, demoted or
  deleted until a third is enrolled (11:271, 25:103–105). Factor reset is not blocked. Discharges ticket 11's
  invariant (no ASVS ID; availability). Enforceable: yes, by `AdminActionGuard`. Proof: ticket 16's guard tests (§7). *Consolidated into the register (ticket 33): R-ADM-018. Amend the table by ID, not this list.*
- **Enable OTLP export and write the rule "authenticable admins < 2".** The gauge exists in the application; export
  and the alert rule are the deployer's. Discharges IM8 lm-16 (alerting half, High) for this signal. Enforceable: no.
  Proof: 21:563 item 4's acceptance (absent-bean assertions inverted), plus the rule firing in a test deployment
  after a factor reset at two admins. *Consolidated into the register (ticket 33): R-OBS-007. Amend the table by ID, not this list.*
- **Route 2 waits on route 1.** If the other admin is locked out, wait up to 60 minutes (password) or 20 minutes
  (TOTP tier 1) before considering the runner. Discharges ADR 13 route 2 (no ASVS ID; avoids an unneeded outage).
  Enforceable: no. Proof: the runbook text states it next to the lockout scenario. *Consolidated into the register (ticket 33): R-ADM-019. Amend the table by ID, not this list.*

### 6. ADRs, register, glossary

- **ADR 12 amended:** "one admin seeded" → "one admin seeded, **conditional on** a second enrolled admin invited before
  go-live". The condition belongs in the ADR, not only in the premise text. *Consolidated into the ADR routing (ticket 34): ADR-047 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-007. Amend the table by ID, not this list.*
- **ADR 13 amended:** §4's three routes. *Consolidated into the ADR routing (ticket 34): ADR-048 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-ADM-008. Amend the table by ID, not this list.*
- **One new ADR:** factor reset exempt from the two-admin count, on the who-can-reverse argument, with (a)'s rejection. *Consolidated into the ADR routing (ticket 34): ADR-049. Amend by ID, not this list.*
- **Register:** TM-08's residual is widened at exactly two admins (§3), detectors named. *Consolidated into the register (ticket 33): R-ADM-009. Amend the table by ID, not this list.*
- **No glossary terms.** `authenticable` is already defined once (11:816–818); this ticket consumes it.

### 7. Ticket 16 tests (seven)

Bootstrap (none existed beyond the reserved-name refusal at 16:206–207):
1. Missing or policy-failing `APP_ADMIN_*` fails refresh **before the web server starts**.
2. An existing `ADMIN` row → no seed.
3. A disabled `ADMIN` row → no reseed.
4. A tombstoned seed username → fail fast. (16:262 covers the runner, not the bootstrap.)

Exemption:
5. At two enrolled admins, A **can** reset B's TOTP.
6. At two enrolled admins, A still **cannot** disable, demote or delete B — only the factor-reset path is exempt.
7. `actor ≠ subject` still refuses A resetting A's own factor; and after (5), B re-enrols and the `authenticable`
   count returns to 2 — the self-reversal property the exemption rests on.

Plus the gauge's non-`NaN`-after-GC check from §5, on ticket 29's pattern (29:353). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-023, T-ADM-024, T-ADM-025, T-ADM-026, T-ADM-027, T-ADM-028, T-ADM-029, T-OBS-007. Amend the table by ID, not this list.*

### 8. Amendments posted

- **09:** 09:535–541 (the auto-lift is still unremovable, but now a ladder; argument two superseded by §R's cap);
  09:1235–1239 (the replaced premise at its origin). 09:117–122 left as question framing.
- **10:** 10:727–729 and 10:775 — the runner mints nothing (ticket 28 §3).
- **11:** premise corrected in place at 394–400; ADR 12 and 13 lines in place; invariant scope; 905–908's TM-08
  framing.
- **15:** TM-08 (15:196–197, 15:309–312).
- **16:** §7's seven tests.
- **17:** an "Inputs from ticket 30" section; unblocked.
- **19:** 19:512–514 — factor-reset path exempt.
- **21:** the gauge, its predicate, its deployer dependency.
- **23:** 23:30, 23:601–603, 23:630–633.
- **25:** the "one admin" framing (88–99), 146–151, and 402–410 / 621 pointed at the in-place TM-12 correction
  (25:891–902).
- **Threat model:** `report.md` §3 table row and §3.3; the JSON's TM-08 and TM-14.

Status: resolved.
