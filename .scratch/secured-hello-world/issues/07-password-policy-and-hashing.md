# 07 — Decide the password policy and hashing parameters

Type: grilling
Status: resolved
Blocked by: 01, 02, 19

## Question

What are the exact password rules, and exactly how is a password hashed, given BCrypt is already the
chosen algorithm?

## Settled by ticket 19 — do not re-litigate

**The minimum is 15 characters, for every account, one rule.**
[Resolve the MFA scope conflict raised by IM8 ac-2](19-mfa-scope-conflict.md) settled this alongside the
MFA decision, because they are the same decision seen from two directions. NIST SP 800-63B-4 §3.1.1.2
sets 15 for a password used as single-factor authentication and permits 8 only within a multi-factor
process; our TOTP factor gates the admin surface only, so regular USER accounts remain single-factor and
the 15 floor binds for them regardless. The PRD's 12 is overridden, with an ADR recording it. Two *Consolidated into the register (ticket 33): R-CRED-023. Amend the table by ID, not this list.*
minimums by role were considered and rejected as complexity buying nothing.

Consequence this ticket inherits: the permitted band is **15 characters to 72 bytes**, and the upper end
is a byte limit while the lower is a character count, so the window narrows under non-ASCII input. That
edge is yours.

## Settled going in

BCrypt, by user decision. The PRD mandates it and the App Standard permits it ("BCrypt is acceptable
for existing systems") even while preferring Argon2id. The deviation from the standard's preference
needs an ADR, not a re-litigation.

## What to decide

**Hashing parameters.**

- BCrypt work factor. Check current OWASP guidance via "Verify the App Standard's controls are still
  current practice". Note the direct trade-off with the timing-attack mitigation in "Decide the API
  error envelope": a higher cost widens the gap between a fast unknown-user rejection and a slow
  password verification.
- **The 72-byte truncation problem.** BCrypt silently ignores input past 72 bytes. The PRD sets a
  12-character minimum and no maximum, so a long passphrase is silently truncated — two different
  passphrases sharing a 72-byte prefix become the same password. Decide: enforce a maximum length,
  pre-hash before BCrypt (and accept the known password-shucking caveat), or document the limit.
  A decision is required; leaving it implicit is the bug.
- Use `DelegatingPasswordEncoder` with an `{bcrypt}` prefix so the stored format is
  self-describing and a future algorithm migration is possible? Recommended, but confirm.

**Strength policy.**

- Minimum length: PRD says 12. Confirm against IM8 (via "Extract the IM8 and ARC controls") — IM8
  may demand more.
- Composition rules: the standard imposes them on *admin-generated* passwords (12 chars with
  lower, upper, digit, special). Do they also apply to user-chosen passwords? Current NIST guidance
  discourages composition rules — reconcile using the currency research.
- Breached-password screening: Spring Security's `CompromisedPasswordChecker` uses the Pwned
  Passwords k-anonymity API. NIST recommends screening; the standard omits it. Decide whether to
  include it, and whether an outbound call to a third-party API is acceptable under IM8. If it isn't,
  decide whether a bundled offline list is worth the weight.
- Maximum length, permitted character set, Unicode normalisation, and whether leading/trailing
  whitespace is trimmed or preserved. Small decisions that cause real bugs when left unstated.

**Password history.** The standard mandates 3. The currency research may report that NIST has moved
away from history requirements. If they conflict, the standard wins per the map's conflict rule, but
the ADR should record the tension. Decide what is stored (BCrypt hashes of prior passwords), how *Consolidated into the ADR routing (ticket 34): REJ-007. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-001. Amend the table by ID, not this list.*
comparison works (`matches()` against each historical hash — note this means N BCrypt verifications
per change, a deliberate cost), and the eviction rule.

**Where the policy lives.** One shared validator applied identically by registration, reset
redemption, and self-service change, so the three paths cannot drift.

## Done when

Work factor, truncation handling, strength rules, breached-password decision, and history mechanics
are all written down as values an implementer can configure.

## Answer

Resolved by grilling, September 2026. Primary sources read directly rather than through ticket 02's summary:
[NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html) §3.1.1.1, §3.1.1.2, §8.1.2.2 and
Appendix A.3; the OWASP Password Storage and Authentication cheat sheets via ticket 02; `im8-review` **as-5**
and **as-6**; and the binding corpus at `App-Standards/Appfw-User-Standards/User_Standalone/`. Content from
external sources was rephrased for compliance with licensing restrictions; quoted fragments are kept short
and attributed inline.

Reading §3.1.1.2 at source changed two things that a summary had obscured, and both mattered. They are
recorded under "Premises corrected" below rather than buried, because the wrong readings are the ones a
future reader is most likely to arrive at independently.

### Configuration surface — the values an implementer sets

```yaml
app:
  security:
    password:
      min-length: 15                 # code points, after NFC normalisation
      max-bytes: 72                  # UTF-8 bytes, after NFC normalisation — BCrypt's hard input limit
      history-length: 3              # includes the current password as entry zero
      bcrypt-strength: 12
      min-strength-score: 3          # zxcvbn 0-4 scale; 3 is its "safely unguessable" band
      admin-generated-length: 20
      blocklist-resource: classpath:security/password-blocklist.txt
```

New dependency: `com.nulab-inc:zxcvbn:1.9.0`, pinned. Joins the OWASP Dependency-Check surface the map
already binds to the Maven `verify` phase.

The `@ConfigurationProperties` class behind this prefix **must be written**: the privileged recipe calls
`passwordProperties.getMaxPasswordHistoryLength()` against a `PasswordProperties` type that is defined
nowhere in the corpus. The prefix deviates deliberately from the corpus's only password property key,
`spring.password.sso.max-password-history-length` — that key carries `sso` into a standalone application and
squats in the framework's `spring.*` namespace. `app.security.password.*` matches the one convention the
corpus is consistent about (`app.security.auth.*`, `app.security.rate-limit.*` in the session-login recipe). *Consolidated into the register (ticket 33): R-CFG-002. Amend the table by ID, not this list.*

### 1. Hashing — BCrypt cost 12, behind `DelegatingPasswordEncoder`

`DelegatingPasswordEncoder` with `idForEncode = "bcrypt"`, not a bare `BCryptPasswordEncoder`. Confirmed
necessary from two independent directions: NIST §3.1.1.2 wants a reference to the scheme and cost factor
stored per password so algorithms can be migrated, which the `{bcrypt}$2a$12$…` prefix satisfies; and IM8
**as-6** names `DelegatingPasswordEncoder` in its allowlist of acceptable encoder beans. Migration to Argon2id
later is then re-hash-on-next-login rather than a data migration. Ticket 12 must size the credential column
for the prefixed format plus room for a longer Argon2 hash.

Cost **12**, from the corpus's own floor — `Standalone_User_Access_Control_Application_Standard_Questions.md`
Q12 says BCrypt with a cost factor of at least 12. OWASP's floor is 10 and its budget is under one second per
hash. NIST gives no number, only that the cost factor should be as high as practical without hurting verifier
performance and should rise over time.

**The measurement that binds is not a single verify.** A self-service change runs five BCrypt operations —
one verify of the current password, three history verifies, one encode — so at cost 12 it is a 1.5–2 second
request. That is the operation whose latency sets the ceiling, and it is the number the ADR must record,
alongside the machine it was measured on. A single-verify measurement will mislead whoever next revisits the *Consolidated into the ADR routing (ticket 34): ADR-001. Amend by ID, not this list.*
cost factor.

**Batch admin reset is computationally infeasible at this cost and is exported to ticket 11.**
`ResetPasswordCommand` pairs a history-regeneration loop with a batch endpoint capped at
`spring.user-management.batchPayloadMaxSize: 5000`. 5000 × 4 BCrypt operations × ~300ms is roughly 100
minutes in one synchronous request. The endpoint is prescribed by the recipe and absent from the PRD, so the
scope call belongs to ticket 11; the arithmetic is recorded here so it is not rediscovered late.

Replicate the null-hash guard `MFA_Core/Base_Standalone_Reimplementation_Recipes.md` documents:
`matches(x, null)` throws `IllegalArgumentException` rather than returning false.

### 2. The permitted band — 15 characters to 72 bytes

Lower bound 15 **code points**, settled by ticket 19 and not re-litigated. It clears IM8 **as-5**, which asks
only for a minimum-length constraint and recommends at least 8, with room to spare.

Upper bound **72 UTF-8 bytes**, enforced in our own validator before the encoder ever sees the input, rejected
explicitly, never truncated. NIST §3.1.1.2 requires the verifier to request the password in full and verify
the entire submission without truncating it, and Spring Security 7 now throws on over-long input rather than
silently ignoring the tail — behaviour that arrived via
[CVE-2025-22228](https://spring.io/security/cve-2025-22228) and whose fix then broke the timing mitigation in
[CVE-2025-22234](https://spring.io/security/cve-2025-22234). So an unvalidated long password produces a
framework exception instead of our error envelope unless we check first.

The band is asymmetric — a character floor against a byte ceiling — so it narrows under non-ASCII input. At
3 bytes per character the ceiling lands at 24 characters, below the at-least-64-characters **SHOULD** in
§3.1.1.2. **We accept that deviation.** It is a SHOULD, the affected population for this application is
plausibly empty, and `DelegatingPasswordEncoder` keeps the exit cheap. Recorded as an ADR naming non-ASCII
users as the affected population, and noting that this deviation and the Argon2id one share a single upstream
cause: the PRD's BCrypt mandate. *Consolidated into the register (ticket 33): R-CRED-007. Amend the table by ID, not this list.*

The limit must be expressed and surfaced **in bytes**, and ticket 14's client must count the same way.

### 3. Composition rules — replaced, not removed

**No composition rules on user-chosen passwords.** NIST §3.1.1.1 and §3.1.1.2 make this a **SHALL NOT** for
mixtures of character types, and Appendix A.3 explains why: users answer such rules predictably, turning
`password` into `Password1` and then `Password1!`, buying no strength for the memorability cost.

**This is not a deviation from the binding Standard — verified at source.** The Standard never imposed
complexity on user-chosen passwords:

- **§3.5 "Password Policy" has four bullets** — adaptive hash, history `3`, reset-token invalidation on
  self-service change, and admin-generated passwords. The four character classes appear **only** under
  "Administrative password reset must generate a 12-character random password containing at least", scoped to
  that path and nowhere else.
- **§6 defines the term for us.** The configuration line reads "Password minimum strength requirements
  (minimum length)" — the Standard's notion of a strength requirement *is* minimum length, stated
  parenthetically. Every other use of "password strength" in the document (§2 flows 11 and 13, Failure Path 18,
  the §5 test line, the forced-change sequence diagram) refers back to that configured minimum and defines
  nothing further.
- **The corpus's own questionnaire recommends against complexity.** Q12 offers "No mandatory character types
  (NIST recommendation)" as a selectable option, and its Recommendation block advises verifiers not to impose
  character-composition rules because they produce predictable patterns and reduce real entropy.

So the complexity regex is the **recipe's alone**, and it contradicts the questionnaire sitting beside it in the
same corpus. Our policy of length + blocklist + strength gate is *more* than §6 asks for, not less. The ADR
should lead with this rather than with the NIST citation: "the Standard asked for a minimum length and we
supplied three controls" is a stronger position at ticket 18 than "NIST told us to delete something".

**Doing all three — quotas as well — was considered and rejected on security grounds, not just compliance
grounds.** A filter only adds security if it rejects something bad the others accept. Class quotas do not:
`Password123!@#$` is predictable *and* satisfies all four classes, so the gate catches it and the quotas do
not; `my neighbour keeps unusual bees` is unpredictable *and* missing three classes, so the quotas reject it
and the gate correctly accepts it. There is no third category, because the blocklist and the gate measure
predictability directly rather than using character variety as a proxy for it. The entire marginal effect of
adding quotas is **false rejection of strong passphrases**. If a reviewer ever forces the issue, the
least-harmful shape is length-**or**-complexity (four classes, or 20+ characters), which at least lets
passphrases through; it is not worth building speculatively.

The prescribed `PasswordPolicy` regex
`(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\S+$).{12,}` is **deleted rather than repaired**, because
a repaired regex is still a SHALL NOT violation. Three defects it carried, worth recording so nobody restores
it:

1. It imposes four class quotas on every password it validates — and admin-create calls it on the admin's
   supplied string, so the corpus has already leaked the quotas onto a user-chosen path.
2. `(?=\S+$)` **forbids whitespace outright**, so no passphrase containing a space can ever be set. NIST says
   accept all printing ASCII and the space character; the questionnaire in the same corpus offers "all
   printable ASCII (recommended for passphrases)" as an option two lines from the regex that forbids it.
3. The special-character sets disagree. §3.5 names `!@#$%^&*()-_=+[]{}|;:,.<>?` for admin-generated
   passwords; the regex accepts only `@#$%^&+=`. An admin-generated password whose only special character is
   `*` satisfies the standard and is rejected by the validator the same corpus prescribes.

**The ADR frames this as replacement, not removal, and demonstrates it.** `Password123!@#$` is 15 characters,
satisfies all four class quotas, and scores 1–2 on zxcvbn: the old rules accept it, the new rules reject it.
That example carries more weight at ticket 18 than the NIST citation does.

**Guard against the inverse inference.** This ticket establishes that we will exceed NIST where NIST is a
floor (see §4). A SHALL NOT is a prohibition, not a minimum — exceeding it is violating it. Composition rules
do not come back on the grounds that we went stricter elsewhere.

### 4. Two rejection controls — a blocklist and a strength gate

NIST requires the first. We add the second deliberately, exceeding the guideline.

**Blocklist (`BLOCKLISTED`).** §3.1.1.2 makes it a **SHALL**: on establish-or-change, compare the prospective
secret against a blocklist of known common, expected or compromised values. Exact match after NFC
normalisation. Sourced from a **breach corpus sliced at ≥15 characters** (SecLists, or the HIBP downloadable
corpus filtered by occurrence count), version-pinned as a checked-in resource — *not* from a top-N
common-password list, which is where an earlier estimate in this ticket went wrong and produced a list of a
few hundred entries. A breach-corpus slice yields tens of thousands of real strings real humans chose, so
exact match catches `passwordpassword` and `qwertyuiopasdfgh`.

Slicing at the minimum length is prescribed, not a shortcut: Appendix A.3 says that since a minimum length
requirement already governs the choice, the dictionary only needs entries meeting that requirement. §3.1.1.2
adds that excessively large blocklists give little incremental benefit because online attacks are already
throttled. Both citations belong in the ADR, or "our blocklist starts at 15 characters" reads as negligence.

**Context terms (`CONTEXT_TERM`).** NIST names the service name, the username and derivatives as legitimate
list content. The username and email local-part must be checked per request and cannot be baked into a file.
Kept as a distinct rule code even though the estimator also absorbs these as inputs, because "your password
contains your username" is the one rejection a user can act on immediately.

**Strength gate (`TOO_WEAK`) — stricter than NIST, deliberately.** `com.nulab-inc:zxcvbn:1.9.0`, minimum
score **3**. This closes a hole no blocklist can: `aaaaaaaaaaaaaaaaaaaa` and `111111111111111111111` clear any
length floor, are absent from most corpora, and score 0 because the estimator matches repeats and sequences
structurally rather than by lookup.

Chosen over [nbvcxz](https://github.com/gosimplellc/nbvcxz) because [zxcvbn4j](https://github.com/nulab/zxcvbn4j)
is a direct port of the original algorithm and therefore shares an ancestor with the frontend library, so the
two scores track each other; nbvcxz is an independent reimplementation whose scoring would visibly disagree
with the meter.

**Why 3 and not 4.** A threshold set too high recreates the problem §3 just deleted — users bolt symbols on
to clear it, and a composition rule arrives through the back door. At a 15-character floor the only
candidates scoring below 3 *are* patterns: repeats, sequences, keyboard walks, single dictionary words and
leet variants. Anything with real word variety clears 3; four uncommon words score 4. So the gate rejects the
hole and little else, and that is a **testable property, not an assertion** — ticket 16 asserts the split by
feeding it the pattern family against a set of legitimate passphrases. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-004. Amend the table by ID, not this list.*

Pass the username, email local-part and service name to the estimator as user inputs so it penalises them too.

**Trigger points: establish, change and reset only. Never login.** The checker is implemented as a
`CompromisedPasswordChecker` so the API-backed one remains a one-line swap, but it is **deliberately not
registered as a bean**. `DaoAuthenticationProvider` picks such a bean up automatically and raises
`CompromisedPasswordException`, which would make a correct-but-breached password produce a response
distinguishable from `AUTHENTICATION_FAILED` — an account-existence *and* valid-credential oracle on the one
endpoint ticket 06 pinned to a single identical 401. The withheld bean is the control, not an omission, and
the ADR must say so or the next reader will "fix" it.

NIST's guidance **SHALL** is discharged by the rejection carrying an actionable reason (§5 below) and by the
frontend meter; §3.1.1.2 notes guidance matters most right after a blocklist rejection, because that is what
discourages trivial modification.

### 5. The rejection contract

`errors: [{field, rule}]` on `PASSWORD_REJECTED` (400), per ticket 06, with `rule` a closed enum of six:

| `rule` | Cause |
|---|---|
| `MIN_LENGTH` | under 15 code points |
| `MAX_BYTES` | over 72 UTF-8 bytes — the rejection users least expect, so copy matters |
| `BLOCKLISTED` | exact match in the breach-corpus slice |
| `CONTEXT_TERM` | contains the username, email local-part, or service name |
| `TOO_WEAK` | zxcvbn score below 3 |
| `HISTORY_REUSE` | matches a retained prior hash |

This satisfies Failure Path 18's demand for a specific error naming the violated rule without shipping
English through the API — the SPA renders its own copy from the enum. The §3.2 uniformity rule is not
breached: these describe the string the caller just typed, not the account.

Ticket 06's **ordering rule stands**: on the reset path the token check must pass *before* any
password-quality validation, or a specific strength error confirms the token was valid.

### 6. Password history — 3, with the current password as entry zero

Kept because §3.5 and §6 mandate it and the map's conflict rule gives the standard the control's behaviour.
NIST neither requires nor forbids it — §3.1.1.2 has no history requirement, and reuse rejection appears only
in the informative §8.1.2.2 — so this is compliance with our standard, not with NIST.

It buys less than it appears to, but not nothing. With the blocklist and the strength gate both in place,
`Passphrase one` → `Passphrase two` is the one weakness **neither** catches, so history has a narrow job of
its own. §8.1.2.2 also names prior use alongside blocklist hits as a rejection owing actionable feedback,
which is why `HISTORY_REUSE` is its own rule code.

Mechanics, since the corpus underspecifies or contradicts itself on all of them:

- Comparison is `matches(newPassword, entry.passwordHash)` against each retained entry. There is no cheaper
  way; three BCrypt verifications per change is the cost, priced in §1.
- **`history-length: 3` spans the current password plus two priors.** The recipe inserts the new hash on
  every change, so the current password is entry zero. Stated explicitly because "history of 3" reads as
  three *previous* to most implementers, and that misreading is an off-by-one in a security control.
- Eviction: insert at head, drop the tail past the configured length.
- Ticket 12 owns the table. History rows are BCrypt hashes, so they are offline-attack targets and a
  retention obligation: whether they purge on account deletion or follow the tombstone is a data-model
  decision, not an afterthought.

The standard's §1 Scope line about expiring stale credentials must **not** become periodic password expiry —
§3.1.1.2 makes that a **SHALL NOT**, forced change being permitted only on evidence of compromise. Ticket 17's
deferral wording should say we would not implement it, not merely that we have not. *Consolidated into the register (ticket 33): R-CRED-002. Amend the table by ID, not this list.*

### 7. Canonicalisation — one pipeline, applied identically everywhere

Accept all printing characters including space; accept Unicode. Then, in this order:

1. **NFC-normalise.** §3.1.1.2 makes this a SHOULD when Unicode is accepted, applied before hashing the byte
   string.
2. **Count code points** for the 15 floor — §3.1.1.2 makes each Unicode code point count as one character.
3. **Count UTF-8 bytes** for the 72 ceiling.

**No trimming, no case folding, no space collapsing.** §3.1.1.2 explicitly *permits* mistyping allowances —
stripping leading and trailing whitespace, tolerating a differing leading-character case — and Appendix A.3
adds collapsing repeated spaces on a failed first attempt. All three are **MAY**, and we decline all three.
Recorded as declined rather than left silent, so a reviewer does not read the absence as an oversight.
Rationale: any asymmetry between set and verify silently locks the user out, with no error message that could
ever explain it. *Consolidated into the register (ticket 33): R-CRED-003. Amend the table by ID, not this list.*

The pipeline lives **inside** the component in §8 so no call site can skip a step. Normalising at set but not
at verify makes a Unicode password permanently unverifiable — the single most likely way to get this wrong.

### 8. One seam — `PasswordService` owns `encode()`

The ticket asked for a shared validator. A shared validator is necessary and insufficient: a call site can
simply not call it, which is precisely what the corpus shipped. `PasswordPolicy.validate()` exists once, in
admin-create; `ChangeCurrentUserPasswordCommand` performs **no strength validation at all**;
`ResetPasswordCommand` bypasses the validator and checks only history; and history checking is duplicated in
two different shapes.

So the seam is **"set a password", not "validate a password"**. A single `PasswordService` owns
`passwordEncoder.encode()` outright, and no other class in the application may call it. One method takes the
raw password plus the account and runs the whole sequence atomically: normalise → length → bytes → blocklist →
context terms → strength → history → encode → set hash → insert history entry → evict. Validation becomes
unskippable because the only route to a hash runs through it. That is an architecture-test-able invariant —
`encode` has exactly one call site — rather than a code-review convention. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-005. Amend the table by ID, not this list.*

Called by all four paths: self-registration, admin-create, reset-token redemption, self-service change.
**No exceptions, including the admin-generated path** (see §9). An exception is the crack that becomes a
`skipValidation` flag.

Two inherited constraints it must not break: ticket 06's **Rule 2**, no encoder wrapper or fast path may skip
`matches()`; and the dummy-hash timing mitigation, whose dummy value must carry the `{bcrypt}` prefix at the
**same cost** as live hashes or it reintroduces the gap CVE-2025-22234 was filed for.

### 9. Admin-generated passwords — 20 characters, same pipeline

`PasswordUtil.generateRandomPassword()` as prescribed emits **12** characters, which the 15-character floor
rejects — so the corpus's own happy path fails. Re-specified: **20 characters** drawn from the full
printable-ASCII set, still guaranteeing one character from each of the four classes so §3.5's list and §6's
"12-character minimum" are both satisfied literally, and the floor is cleared with margin. Use the standard's
**broad** special-character set; §3 deletes the narrow regex, so that conflict disappears.

Class quotas are kept **on this path only**. §3.5 binds literally here, no human is being frustrated, and the
rejection-sampling cost to keyspace is negligible at 20 characters.

**It goes through the same `PasswordService`, with no checks skipped.** An earlier position in this ticket
carved out the blocklist check for this path; that contradicted §8's whole point. A 20-character uniform draw
passes every check trivially — score 4, no blocklist hit — so the cost is one hash-set lookup and one
estimator call per admin-created account, which was never worth an exception.

**The `while` regeneration-against-history loop is dropped.** Collision probability is zero to any precision
that matters, and it costs three BCrypt verifications per generated password — 15,000 of them in a 5000-row
batch. If ticket 18 demands literal recipe fidelity, keep it with a comment stating it can never execute,
rather than implying it is a real control.

### Not taken, with reasons

- **Pepper / keyed second pass** (NIST SHOULD, OWASP optional defence in depth). Ticket 02 deferred this as
  "blocked on unresolved secrets handling". **That reason has expired** — ticket 19 put the TOTP secret under
  an environment-supplied AES-GCM key and graduated ticket 24, so the facility now exists. The decision
  stands on a new and stronger reason: **a peppered hash cannot be rotated.** The TOTP secret can be
  decrypted and re-encrypted under a new key with no user involvement; a password hash peppered under key *K*
  cannot be re-derived under *K'* without the plaintext, which we do not hold and must never hold. Ticket 24's
  yearly-rotation obligation would therefore be unsatisfiable for password hashes, and we cannot escape by
  forcing a global reset because §3.1.1.2 forbids requiring periodic password change. The alternatives are an
  indefinitely retained dual-key read path, or a pepper that silently never rotates beneath a policy saying
  it must. Both are traps whose failure mode is every account locked out simultaneously.
- **Pre-hashing to escape the 72-byte ceiling.** Falls with the pepper: the only construction OWASP sanctions *Consolidated into the register (ticket 33): R-CRED-004. Amend the table by ID, not this list.*
  is `bcrypt(base64(hmac-sha384(password, pepper)))`. Worth recording that this construction *does* answer
  both objections in the ticket body — password shucking needs the attacker to know the inner hash, which a
  keyed HMAC denies, and base64 removes the NUL byte that truncates bcrypt input — so it was rejected on
  rotation grounds, not on cryptographic ones. *Consolidated into the ADR routing (ticket 34): ADR-004. Amend by ID, not this list.*
- **Live HIBP k-anonymity API.** Not prohibited: a sweep of the IM8 and ARC sources found **no control *Consolidated into the register (ticket 33): R-CRED-007. Amend the table by ID, not this list.*
  governing outbound calls to third-party services** — the only egress mention is `im8-review`'s data-flow
  check, which asks that external calls be documented, not absent. Declined for the availability dependency
  on every password-set path and the fail-open/fail-closed decision it would owe. The local blocklist plus the
  strength gate covers the same ground without it. *Consolidated into the register (ticket 33): R-CRED-005. Amend the table by ID, not this list.*
- **Argon2id**, despite it being what the standard's prose *and* every recipe in the corpus prescribe
  (`Argon2PasswordEncoder(16, 32, 1, 19456, 2)`). The reason is the PRD mandate and the recorded stack
  decision, not a security argument, and the ADR must say that rather than implying parity. The costs are
  concrete: BCrypt's ~4 KiB working set does not penalise GPU or ASIC attackers the way Argon2id's 19 MiB
  floor does, so offline cracking of a stolen hash file is materially cheaper at equal verification latency;
  and the 72-byte ceiling is a BCrypt artefact, so choosing BCrypt is what forces §2's restriction on our
  users.
- **Composition rules on user-chosen passwords** — §3 above.
- **Screening at login** — §4 above.
- **Mistyping allowances** — §7 above. *Consolidated into the register (ticket 33): R-CRED-003. Amend the table by ID, not this list.*

### Premises corrected during this ticket

Recorded because each wrong reading is one a future reader would plausibly reach independently.

1. **NIST treated as a ceiling rather than a floor.** An earlier position in this ticket counted "goes beyond
   NIST" as a cost against the strength gate. For a compliance-assessed application, exceeding a floor costs
   an explanation, nothing more.
2. **"The entire password SHALL be subject to comparison, not substrings"** (§3.1.1.2) constrains *how the
   blocklist comparison works*, and its purpose is anti-over-rejection — do not refuse `mypasswordishere`
   because `password` sits inside it. It does not forbid a separate strength control that matches patterns
   internally. It was read here initially as a tension with the zxcvbn gate. It is not one.
3. **Appendix A.3's closing "no additional password requirements are imposed"** is informative, and it is
   NIST describing *its own* scope — why NIST declines to add further requirements. It is not a prohibition
   on implementers. Misreading it as binding is what produced the initial advisory-meter-only recommendation.
4. **The blocklist sizing.** An earlier estimate of "a few hundred entries" came from filtering a top-100k
   common-password list. The correct source is a breach corpus, whose ≥15-character slice is orders of
   magnitude larger and catches most of the family by exact match.
5. **IM8 does not demand more than 15 characters.** as-5 asks only that a minimum-length constraint exist and
   recommends at least 8. The ticket asked whether IM8 might escalate the floor; it does not.
6. **An advisory-only frontend meter is weaker on the compliance axis too**, not just the security axis. as-5
   asks the reviewer to verify frontend and backend password rules are consistent; a meter the backend ignores
   is exactly the inconsistency being looked for.
7. **The ticket's own framing of composition rules** — that the standard imposes them on admin-generated
   passwords and the open question is whether they reach user-chosen ones — understates the problem. The
   prescribed validator already applies them to every password it sees, and admin-create calls it on the
   admin's supplied string. The leak has happened in the corpus.

### Constraints exported

- **Ticket 09** — per-IP rate limiting must run **before** the password pipeline, not after validation.
  Registration is unauthenticated and now runs a blocklist lookup, context checks, zxcvbn estimation and a
  BCrypt encode at cost 12, making it the most expensive unauthenticated endpoint in the application.
  Validate-then-throttle hands an attacker that CPU cost for free on every rejected request.
- **Ticket 11** — batch admin reset is infeasible at cost 12 (~100 minutes for a 5000-row payload). The
  endpoint is prescribed by the recipe and absent from the PRD; if it stays, it must be asynchronous or the
  payload cap must drop to double digits.
- **Ticket 12** — credential column sized for `{bcrypt}` prefix plus Argon2 headroom; password-history table
  shape, and whether history rows purge on deletion or follow the tombstone.
- **Ticket 14** — count the ceiling in **bytes**; six `rule` enum values need SPA copy, and that copy is how
  NIST's guidance SHALL is discharged; zxcvbn-ts for live feedback (the maintained fork, not Dropbox's
  original), with the **backend authoritative and the meter indicative** — the two libraries have refreshed
  dictionaries and will not score identically. Plus a **show-password toggle**: §3.1.1.2 makes offering it a
  SHOULD, and it **collides with IM8 as-6**, which checks that password fields use `type="password"`. A toggle
  flips that attribute, so a literal grep reads it as a violation — the same failure mode ticket 01 found with
  `im8-review`'s Boot 3.4 config spellings. Pre-write the note.
- **Ticket 16** — the pattern-family split test (repeats, sequences, keyboard walks and leet variants rejected;
  legitimate passphrases accepted) as the evidence for `min-strength-score: 3`; a >72-byte password rejected by
  *our* validator rather than the encoder; NFC round-trip at set and verify; `history-length: 3` meaning the
  current password plus two priors; and the architecture test that `encode` has exactly one call site.
- **Ticket 17** — seven ADRs (below), and the credential-expiry deferral worded as "would not" rather than
  "have not".
- **Ticket 24** — its key-rotation policy must **scope itself to keys that can rotate**, and state explicitly *Consolidated into the register (ticket 33): R-CRED-002. Amend the table by ID, not this list.*
  that no password-hash pepper exists and why. Otherwise the policy reads as covering something it cannot
  cover, and whoever implements rotation later goes looking for a pepper that was deliberately never created. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-004, T-CRED-005, T-CRED-001, T-CRED-006, T-CRED-002. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-004. Amend the table by ID, not this list.*

### ADRs owed

1. BCrypt over Argon2id — PRD mandate, not a security argument; memory-hardness and 72-byte costs stated. *Consolidated into the ADR routing (ticket 34): ADR-001. Amend by ID, not this list.*
2. The 72-byte ceiling and the at-least-64-characters SHOULD deviation, naming non-ASCII users as affected. *Consolidated into the ADR routing (ticket 34): ADR-003. Amend by ID, not this list.*
3. Composition rules replaced by a strength gate. **Lead with the fact that the binding Standard never imposed
   complexity on user-chosen passwords** (§3.5 scopes the four classes to admin-generated only; §6 defines
   strength as minimum length; Q12 recommends against character types) — so this is recipe deviation, not
   Standard deviation. Then the `Password123!@#$` demonstration, then the note that a SHALL NOT is not a floor. *Consolidated into the ADR routing (ticket 34): ADR-005. Amend by ID, not this list.*
4. Blocklist sourcing and sizing, citing Appendix A.3's minimum-length filter and §3.1.1.2's
   excessive-size caution. *Consolidated into the ADR routing (ticket 34): REJ-004. Amend by ID, not this list.*
5. The zxcvbn gate as a deliberate stricter-than-NIST control, with the score-3 threshold justified and the
   three misread clauses named so they are not re-litigated from the same wrong premise. *Consolidated into the ADR routing (ticket 34): ADR-005. Amend by ID, not this list.*
6. `CompromisedPasswordChecker` implemented but **not registered as a bean**, and why registering it would
   break ticket 06's uniform 401. *Consolidated into the ADR routing (ticket 34): REJ-005. Amend by ID, not this list.*
7. `app.security.password.*` in place of `spring.password.sso.max-password-history-length`, and the
   15-character floor overriding the PRD's 12 (carried from ticket 19). *Consolidated into the ADR routing (ticket 34): ADR-002 / REJ-006. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-023. Amend the table by ID, not this list.*

---

## Amendment from ticket 11 — the NIST floor, verified against the final publication

While resolving [ticket 11](11-admin-module-role-model-and-bootstrap.md) the 15-character floor was challenged
on the grounds that NIST makes 15 a `SHOULD` and only 8 a `SHALL`. **Checked against the primary text, and this
ticket's floor is vindicated.** SP 800-63B-4 §3.1.1.1 Password Verifiers
([NIST](https://pages.nist.gov/800-63-4/sp800-63b/authenticators/)) requires passwords used as a
**single-factor** authentication mechanism to be a minimum of **15 characters** as a `SHALL`, and permits the
8-character minimum only for passwords used as part of a multi-factor process. Content rephrased for compliance
with licensing restrictions.

The widely-quoted "SHALL eight / SHOULD fifteen" formulation is **draft-era text**; the final publication
restructured it. Consequence for the ADR wording: say we were below a `SHALL`, not that we adopted a
recommendation. Getting this backwards in either direction is costly — an overstatement invites a reviewer to
discount the rest, and an understatement gives away a requirement we actually meet. *Consolidated into the ADR routing (ticket 34): ADR-002 (attached amendment). Amend by ID, not this list.*

**Second-order finding nobody on the map had noticed.** Because ticket 19 put TOTP on the **admin surface only**,
admin passwords are "used as part of multi-factor authentication processes" and would qualify for the
8-character floor, while regular `USER` accounts are single-factor by design and are therefore what forces 15.
The long-password requirement is driven by the *least* privileged population, which is the inverse of the
intuition — and it means the floor cannot be relaxed by pointing at MFA unless MFA covers every account, which
is explicitly out of scope. The single floor of 15 for everyone is both simpler and the only compliant option.

Two further confirmations from the same primary text, both strengthening decisions recorded above:

- **Composition rules are a `SHALL NOT`**, not merely discouraged: other composition requirements "SHALL NOT be
  imposed". This ticket's rejection of character-class quotas is therefore compelled, not just preferred. *Consolidated into the ADR routing (ticket 34): ADR-005 (attached amendment). Amend by ID, not this list.*
- **Peppering is a `SHOULD`** whose key "SHALL be stored separately from the hashed passwords" and "SHOULD be
  stored and used within a hardware-protected area" such as an HSM or TPM. We have neither, which strengthens
  the decline — but it remains a declined `SHOULD` and the ADR should name it as such rather than presenting
  the rotation argument alone. *Consolidated into the ADR routing (ticket 34): ADR-004 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-004. Amend the table by ID, not this list.*

Also relevant to this ticket's generator: ticket 11 sets the admin-generated password at **20 characters**, and
routes the bootstrap seed credential through the same `PasswordService` seam, so the 15-character floor and the
zxcvbn gate apply to it. The bootstrap path is not a policy exemption.

## Amendment from ticket 10 (credential flows)

- **§9's 20-character admin generator is deleted, not re-specified.** Ticket 10 turned admin-create into an
  invite token, so no password is generated for a user anywhere in the build. ASVS 6.4.6 (L3) is the reason —
  an administrator may initiate a reset but must not be able to choose the user's password.
- **6.1.2 / 6.2.11 (L2):** the mechanism here already satisfies the *use* half — username, email local-part and
  service name go to zxcvbn as user inputs, with rule `CONTEXT_TERM`. What is owed is the **documented** list,
  extended to organisation, product and project-codename permutations. Near-free, and it catches
  `SecuredHelloWorld2026!`, which no breach corpus will.
- **6.2.4 (L1) vindicates the blocklist sourcing.** It requires screening against passwords "which match the *Consolidated into the register (ticket 33): R-CRED-006. Amend the table by ID, not this list.*
  application's password policy, e.g. minimum length" — which is exactly the breach corpus sliced at ≥15
  characters, and exactly why the earlier top-100k-filtered sizing was wrong. *Consolidated into the ADR routing (ticket 34): REJ-004 (attached amendment). Amend by ID, not this list.*
- **6.2.10 (L2)** confirms the refusal to let history become periodic expiry. **6.2.8 (L1)** is satisfied *and*
  documented by §7: NFC round-trips at set and verify, trimming / case folding / space collapsing are declined *Consolidated into the register (ticket 33): R-CRED-002. Amend the table by ID, not this list.*
  on record, and over-72-byte input is rejected rather than truncated, which is the classic violation.
- **6.2.9 (L2) is knowingly failed, and it is a consequence of a decision already taken here rather than a new *Consolidated into the register (ticket 33): R-CRED-003. Amend the table by ID, not this list.*
  one.** The requirement is "passwords of at least 64 **characters** are permitted"; the ceiling here is 72
  **bytes**. 64 ASCII characters fit; 64 characters of CJK text is roughly 192 bytes and is rejected, as is a
  40-character accented-Latin passphrase. The cause is the pre-hash examined and declined in this ticket because
  a peppered hash cannot be rotated. Affected population: non-Latin-script users. Written down deliberately —
  given the map's PDPA framing, "we quietly reject long passwords in Chinese" is a finding to own rather than to
  have found. *Consolidated into the ADR routing (ticket 34): ADR-003 (attached amendment). Amend by ID, not this list.*
- **One negative assertion owed as a test:** ticket 11's identifier canonicalisation helper is never applied to *Consolidated into the register (ticket 33): R-CRED-007. Amend the table by ID, not this list.*
  a password field. It is the obvious thing for a later contributor to reuse on the wrong argument, and §7
  already established that any set/verify asymmetry locks a user out with no error that could explain it.
- The five-BCrypt cost figure recorded here is what made ticket 10's registration timing oracle measurable. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-007. Amend the table by ID, not this list.*

---

## Amendment from ticket 12 (data model reconciliation)

**This ticket's non-rotatability argument now governs a key that actually exists, and the two cases resolve in opposite
directions — so the cross-reference matters, or they read as a contradiction.**

This ticket declined the pepper and the pre-hash escape from the 72-byte ceiling on the ground that **a peppered hash
cannot be rotated**. Ticket 11 then chose to store the tombstone email as a keyed HMAC, and
[ticket 24](24-secrets-and-configuration-handling.md) carries the consequence: because `deleted_users` holds no
plaintext address, **that key can never rotate either**, which 24 records as the second entry in the category this
ticket opened — the difference being that the pepper was declined and therefore never exists, while the HMAC key exists
and is permanently frozen.

Ticket 12 supplied the part that was missing: not the constraint, which 11 and 24 both had, but the **justification**
for accepting it, on the ground that the HMAC is a **blinding key for a pseudonymised reuse index rather than a
confidentiality key protecting a secret at rest** — its compromise discloses only that a given address once held an
account, and only to someone already holding the tombstone table.

**The asymmetry is what makes both decisions consistent rather than contradictory, and belongs in this ticket's ADR:**
a pepper protects a **credential**, so unbounded-in-time compromise is intolerable and non-rotatability is
disqualifying; the HMAC protects a **one-bit fact**, so the identical constraint is tolerable and is accepted
explicitly. Anyone reading only one of the two tickets will otherwise conclude the map applied one rule twice and got
two answers. *Consolidated into the ADR routing (ticket 34): ADR-004 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-004. Amend the table by ID, not this list.*

Two schema facts from ticket 12 that touch this ticket directly, neither changing a decision:

- The credential column is **`VARCHAR(255)`**, sized for `{bcrypt}` plus 60 characters today and Argon2id's ~108
  tomorrow. The justification is *not* truncation — MySQL has shipped `STRICT_TRANS_TABLES` by default since 5.7, so
  an over-long value errors — but that a `DelegatingPasswordEncoder` upgrade is a **code change that ships with no
  migration**.
- **Password history is purged by `ON DELETE CASCADE`**, and the purge turns out to be *forced by referential
  integrity* rather than chosen on retention grounds, since ticket 11's delete removes the `users` row outright. The
  retention argument this ticket would have made still holds and is recorded there, including that NIST SP 800-63B-4
  requires no password history at all.

---

## Amendment from ticket 25 — the 15-character floor is a SHALL, barred two independent ways

Recorded because this ticket's band (15 characters to 72 bytes) currently reads as a conservative choice inherited
from ticket 19, and a future "relax to 8, the admins have MFA anyway" proposal would otherwise have to be argued
down rather than simply failing.

**NIST SP 800-63B-4 §3.1.1.2, verbatim:** "Verifiers and CSPs SHALL require passwords that are used as a
single-factor authentication mechanism to be a minimum of 15 characters in length." The relaxation in the same
bullet is narrower than it is usually quoted: "Verifiers and CSPs MAY allow passwords that are only used as part of
multi-factor authentication processes to be shorter ... but SHALL require them to be a minimum of eight characters
in length." So it is a **MAY-to-shorten bounded by a SHALL-eight**, and the permission is scoped to passwords used
*only* inside multi-factor processes.

**Two independent bars, which is the point:**

1. **The user population.** Ticket 23 §8 settled that MFA gates the admin surface only, so **regular users are
   password-only** — their passwords are a single-factor mechanism and 15 is mandatory outright. This bar fails the
   relaxation before the admin question is even reached.
2. **The admin recovery path.** Ticket 25 adopted NIST §4.2.2.2 option 2 for break-glass — an issued recovery code
   plus "authentication with a single-factor authenticator that is bound to the subscriber account", which is the
   admin's password. The moment the password serves as that companion, the single-factor floor applies to admins too.

Also worth stating so the number is not read as a ceiling: **15 is a floor a CSP may raise.** Nothing in §3.1.1.2
caps the minimum, and a higher minimum length is **not** a composition rule — composition rules are what the same
section SHALL NOT impose ("Verifiers and CSPs SHALL NOT impose other composition rules (e.g., requiring mixtures of
different character types) for passwords"). So this ticket's rejection of quotas and its 15-character floor are
consistent with each other, which was previously implicit. *Consolidated into the ADR routing (ticket 34): ADR-002 (attached amendment). Amend by ID, not this list.*

Vocabulary note for citations: 800-63B-4 retired "memorized secret" — its glossary reads "memorized secret — See
password" — so any inherited phrasing using that term is 63B-3 vocabulary. This map uses it nowhere, checked; keep
it that way.

**Word list, a second refresh obligation.** Ticket 25's required content on blocklist refresh named **one** list.
There are two, and both decay the same way: the breach-corpus slice (ASVS 6.2.4 L1, 6.2.12 L2) and the
**documented context-specific word list** (6.1.2 + 6.2.11, both **L2**), which this ticket and ticket 10 both
record as owed and which had no owner until now. 6.1.2 requires the list to exist; 6.2.11 requires it to be used —
so an unrefreshed list fails a binding pair, not a nicety. *Consolidated into the register (ticket 33): R-CRED-006. Amend the table by ID, not this list.*
