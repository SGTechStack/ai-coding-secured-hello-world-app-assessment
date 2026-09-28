# 28 — Decide the rebinding runner's output channel and accountability, given that stdout is collected

Type: grilling
Status: resolved
Blocked by: 09, 13, 24, 25
Graduated: 30 (whether ticket 11's one-admin bootstrap survives the removal of its premise)

## Question

The rebinding runner prints a plaintext credential to `System.out` "and never through the logging system".
Ticket 13 ships the audit stream to **stdout in every profile** and ticket 25 has the deployer install a
forwarder over it. What is the runner's output channel, once that is admitted?

Graduated from [ticket 15](15-threat-model.md) as **TM-12** (High), carrying three smaller findings on the
same channel because they share a session.

## The finding

Ticket 24 §R routes the rebinding token to `System.out` rather than through a logger, and the reasoning is
sound as far as it goes: routed through a logger "it lands in whatever appender the profile configures, which
is a fourth door into precisely what ticket 13 spent three controls closing — and unlike the reset link, this
one is reachable in **every** profile."

The distinction does not survive the deployment ticket 25 sequences.

- Ticket 13 §13 emits the audit stream as NDJSON to **stdout in every profile**, and calls stdout "the
  Enforced Constraint and the platform ingestion path and the only route to 16.4.3". It explicitly reversed
  an earlier draft that would have dropped the console copy outside `dev`.
- Ticket 25's deployment sequence, step 6, is **log forwarding and retention**, before the first real audit
  event.
- `System.out` and the audit appender's console target are **the same file descriptor**. A collector tails a
  descriptor. It does not distinguish bytes Logback wrote from bytes `System.out.println` wrote, and on any
  container runtime the collection is unconditional and is the point of the runtime.

So the enforced control — the token on ticket 13's negative list and a ticket 24 prohibited-configuration
entry — protects against *the application* writing the token to an appender, and does nothing against *the
platform* collecting the identical bytes. Ticket 25 §4 records this item as one of three that "**is**
enforceable", against four; that grading is what needs revisiting, not the intent.

The payload makes it worse than the leak it was avoiding. The reset link ticket 13 confined to `dev` is a
credential for one account; this is the credential of last resort, on a channel that exists in every profile
**by design**, for an account that by construction has no other route back in.

## Three more findings on the same channel

Recorded here rather than as separate tickets because they are all answered by the same session.

**1. The only privileged audit row with no nameable actor.** Every other row either carries `user.id` or is
keyless *by construction* on a path where no identity exists — ticket 13 is careful that the asymmetry is the
control. The rebinding row is the first case where a privileged actor exists, acts, and is structurally
unnameable: there is no authenticated principal, and authorization *is* possession of shell access. Ticket 25
converted three of four items on this tool into enforced checks and found "**only the ownership statement
cannot be converted**". Accountability for the invocation is a fifth item and it was not on the list. A
mandatory `--operator=<id>` recorded on the row is attributable-by-claim rather than verified, which is
weak — but it is the difference between a row that names nobody and a row that names someone who can be
asked.

**2. The runner bypasses `AdminActionGuard`.** The guard holds the self-action rule and the two-enrolled-admins
invariant under a pessimistic lock over `users` and `totp_user_details`, and the runner reaches the same state
through neither. With scope `both` it can take the system to zero enrolled admins — which it **must** be able
to do, because that is break-glass. The finding is that this needs stating: ticket 11's "the guard is
decrement-safe, not phantom-safe" now has a third exception after the cascade and the NIST cap, and the
generalisation is ticket 15's **TM-14**, amended onto tickets 11 and 21.

**3. The batch form has no stated dry-run.** Ticket 09 §R.3 requires a batch form because the event it answers
is hundreds of accounts, and ticket 25 records that "a one-at-a-time procedure fails exactly when it is
invoked". A batch that invalidates password hashes has an operator-error blast radius equal to its input file,
and nothing on the map specifies a preview, a confirmation, or a cap.

**4. Invocation must be argument-gated, not property-gated.** Boot binds command-line arguments as the
highest-precedence property source, and ticket 24 already found the adjacent failure on the same mechanism: a
leftover environment variable "silently wins over the mounted file you just rotated. Nothing fails." If the
runner keys on a bound property rather than on explicit argument parsing, a leftover `--rebind` or
`APP_REBIND` in a unit file or container spec re-fires it on **every restart** — invalidating the hash and
printing a fresh token each time, into the stream above. This is a negative assertion, not a decision, but it
belongs with whoever decides the channel.

## What to decide

- **The output channel, on the premise that stdout is collected.** Candidates: a mode-0600 file at a named
  path, which moves the problem to filesystem permissions ticket 13 already declined to assert from the
  application, and for a reason — that assertion "would pass on the one platform we run and mean nothing on
  the platform that matters"; a **TTY-only write that refuses when `stdout` is not a terminal**, which is
  enforceable, fails closed, and has the pleasant property that it is *inoperable* in exactly the automated
  contexts where the leak occurs — but which also breaks the batch form's plausible usage; or deferring
  delivery to mail once transport lands, which makes the tool's safety conditional on ticket 25's already-named
  prerequisite. Note that the third option collapses this ticket into TM-13's dependency, which may be the
  honest answer.
- **What ticket 25's row for this item should say instead.** Its §4 currently grades the output warning
  `enforced`. If the channel is still stdout, the row is `procedural` at best and the acceptance check cannot
  be "the warning exists". Ticket 25 is resolved, so this lands as an amendment to it — and it is worth
  noting that this is the second time ticket 25's own extraction has caught an item mis-graded rather than
  missing, which is an argument for the gate it built.
- **Whether the rehearsal in ticket 25's sequence step 7 covers this.** Step 7 rehearses the runner "on the
  deployed topology before real accounts exist" and confirms "the output warning holds". If the warning cannot
  hold on a collected stream, the rehearsal's acceptance check is currently unpassable, and it is the check
  that ticket 25 says "converts the weakest evidence in the document into the strongest". Decide what it
  checks instead.
- **Accountability**: `--operator=<id>` mandatory or not, and what the audit row carries.
- **Batch safety**: dry-run, confirmation, cap, or none with a reason.

## Done when

The output channel is decided against the collected-stdout premise; ticket 25's row grading and its step-7
acceptance check are corrected to match; the accountability and batch-safety questions are answered either
way; and the argument-gating negative assertion is handed to ticket 16.

---

## Answer

**The runner runs offline as a planned outage. Credentials go in and nothing secret comes out. Every destructive
run is plan-then-apply, bound to a digest of account state. The evidence is an intent row and an outcome row that
reach the collector after restart. The channel question was dissolved, not answered.**

Say plainly what this ticket turned out to be, because the headline undersells it. It opened as "pick an output
channel". It closed having found four things:

- The tool had **never been able to run**.
- The fix for that opened a **second path where the tool reports success and does nothing**.
- The precondition set first drafted to close that path **refused the tool's primary use case**.
- The standards citation that made the original design look compelled was **inverted**.

Four of the five findings that shaped this answer came from **checking premises, not from answering the question
as posed**. Three of the premises that failed were drafted during this session's own grilling. The next ticket
should inherit that habit, not just these conclusions.

Every external fact below is verified at source in the
[verification asset](../research/rebinding-runner-channel-and-process-model-verification.md), cited by section
(§n) and not restated.

### 1. Corrections to this ticket and to the tickets it inherits from

- **Ticket 13's stdout topology is §12** ("Destination, retention, protection"), **not §13**, which is the
  reset-link leak. Ticket 25 carries the same mis-citation. Both are corrected by amendment.
- **Ticket 25 has two sections numbered 4.** The grading is in the ticket-09 amendment's §4 (the enforceability
  section). The Answer's §4 is the schema spike, and it contains no rebinding row. §4 applies "is enforceable" to
  **two** items, not three; the batch procedure became code, not a check. There is no schema row to regrade, since
  the 47-row pass belongs to ticket 17. And **ticket 25 already carried a TM-12 placeholder** naming this ticket.
  So this ticket closes a placeholder and edits it in place, rather than opening a new question.
- **Step 7 is a three-clause conjunction.** Only its third clause ("confirm the output warning holds") was
  unpassable. The break-glass worked row defines its acceptance check as "the rehearsal", so it inherits the new
  wording automatically.
- **Ticket 09 §R.3 has ASVS 6.4.1 and 6.4.6 inverted (§5).** It records 6.4.1 (L1) as "the binding one, because it
  forecloses the 'operator types a temporary password' variant". That is false. 6.4.1 governs *system-generated*
  initial secrets and says nothing about who chooses them. **6.4.6 (L3)** is the requirement that forbids operator
  choice. The error made the token design look compelled at the level we actually claim, and it is the same bias
  this map has recorded before.
- **Finding 4's precedence argument is wrong, but its conclusion survives (§3).** Command-line arguments are not
  the highest-precedence source, and environment variables rank *below* them. The real mechanism is a shared
  lookup namespace. That produces **two** distinct paths, and they are closed separately (§6).
- **The runner as specified could never execute (§1).** Ticket 24 mandates H2-file. H2's embedded file mode is
  single-writer, so `java -jar app.jar --rebind=…` cannot open the database while the application holds it.
  Nothing in tickets 09, 24, 25 or this ticket said so. This is the same shape as the inert
  `ImmutableSecurityHandler`, and it had been true for the entire documented life of the procedure that rehearses
  it.

### 2. Process model: the same jar, run offline

**Chosen.** Stop the application. Run the same jar with `spring.main.web-application-type=none`. Restart. **So
recovering a sole admin is a planned outage**, which nothing on this map had said. It is a cheap cost here,
because ticket 24 prohibits every clustering property and there is no HA posture to preserve.

**What it does not do.** It does **not** make the runner's output uncollected. An earlier draft of this ticket
claimed it did, and that was wrong: a Kubernetes Job runs as PID 1 and is collected, and a systemd one-shot unit
goes to the journal (§4). Whether stdout is collected depends on how the operator launches the runner, and the
application cannot observe that. The channel is settled by §3, not here.

**What the outage buys, named because it came up twice:**
- No concurrent `AdminActionGuard` exists to contend with (§10).
- The database is **quiescent between plan and apply**, which makes the digest in §5 a stronger interlock than
  the same pattern would be against a live system.

**Rejected, on the record:**
- **`AUTO_SERVER=TRUE`**: a permanent random-port listener that accepts remote connections, authorised by
  possession of `.lock.db` (§1).
- **An explicit loopback H2 TCP listener (§6).** Four reasons:
  - `h2.bindAddress` is a JVM system property, so it sits outside the refresh-phase validator.
  - H2's own documented Spring example passes `-tcpAllowOthers`, so the copy-paste configuration is the one that
    exposes the database to the network.
  - H2 lets its admin `CREATE ALIAS` onto arbitrary Java by default, and ticket 24 established that the
    application's database user *is* the admin. So a listener turns "can read the datasource password" into "can
    run code beside all three keys".
  - It is orthogonal, and this reason is sufficient on its own: it buys "no outage", not an answer to what the
    runner emits.

  (An earlier draft rested this rejection on the `.lock.db` key channel and the first-connection-close rollback.
  Both belong to `AUTO_SERVER` and were withdrawn.)
- **An in-app privileged trigger.** It destroys the one property ticket 09 valued, and it needs a surface of its
  own.

### 3. Channel: credential in, nothing out

**Single break-glass run.**
- The operator supplies the new password on **stdin or an interactive prompt, never as an argument** (CWE-214, §7).
- The runner sets it through ticket 07's `PasswordService` with **no call-site exception**, so the 15-character
  floor, the blocklist and the zxcvbn-3 gate all apply.
- It sets `force_password_change`, so ticket 11's filter fires at first login. That filter sits before
  `AuthorizationFilter`, covers every request except five exempt paths, and applies to the whole API surface,
  since the backend serves nothing else.
- It stamps `credential_issued_at`, making the runner that column's **fifth** trigger, so ticket 11's lazy 30-day
  expiry applies to a password that is never changed.
- It prints only a success line, the account UUID, and the §6 procedural identifiers.

**No secret crosses any stream**, so collection stops mattering, the TTY question dissolves, and ticket 24's
eleventh prohibited-configuration entry becomes **vacuous rather than unenforceable** (it is replaced, §7).

**Cost, kept rather than carved out.** The strength gate can refuse the password an operator types in the middle
of an emergency. A carve-out on "set a password" is the exact defect ticket 07 found in the corpus, so the gate
stays and the failure mode is documented in the runbook.

**Batch run: mints nothing and emits nothing secret.** It invalidates the credentials and clears the cap, and
recovery then rides the normal flow. One operator-chosen password shared across 240 accounts would be worse than
the leak this ticket opened on. Outside `dev` the normal flow has no transport, so the event **degrades to up to
240 sequential interactive runs**, each inside an outage window and each needing an out-of-band handover. That is
an **RTO nobody has costed**, recorded as such (§13). It is not "unrecoverable", which would invite a reviewer to
reject an availability trade that has a number attached. *Consolidated into the register (ticket 33): R-CRED-026, R-RUN-009. Amend the table by ID, not this list.*

**Both paths converge on mail** when TM-13's transport trigger fires. The batch leg is a second and much larger
cause on that trigger than the one currently recorded. *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*

**Standards position, graded against the declared L1 target.** 6.4.1 (L1) is satisfied: no system-generated
secret exists on this path, and forced change prevents the operator's password from becoming the long-term one.
6.4.6 (L3) was **adopted**, not merely observed: ticket 10 took the invite token mainly because of it, and ticket
07 deleted the admin generator because of it. So its loss here is a **scoped withdrawal of a volunteered L3 pass**,
not a knowing failure:
- It is still satisfied on the in-app admin-create and admin-reset paths, which keep issuing tokens, so neither
  decision it justified is disturbed.
- It is not satisfied on the break-glass runner path, which sits above target and behind host access.
- The protection was **already notional** on that path. In the token design the token printed to the *operator's*
  terminal, so the operator could always redeem it and choose the password. *Consolidated into the register (ticket 33): R-RUN-007. Amend the table by ID, not this list.*

The control that actually constrains operator abuse, in both designs, is the audit row plus its alert (§9).

### 4. Check 5, reduced, and why

Admissible states by scope:
- **Password:** the account exists and is not tombstoned.
- **TOTP:** a factor row exists, in any lock state.

The first draft required "capped for `password`, tier-2 for `totp`". That **refused the case this tool exists for**.
Ticket 25 (lines 96–99) names the lost authenticator as "the genuine break-glass case and the only one the runbook
must solve", and ticket 23 routes tier-2 in only as "a second trigger" on that control. An admin whose phone is in
a river has an enrolled, healthy account. Password scope reduced the same way: a sole admin who forgot their
password is not capped, and outside `dev` there is no mail to reset through. **Do not tighten this back up.** The
loop protection it was trying to provide lives in §5. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RUN-009. Amend the table by ID, not this list.*

A proposed alternative guard was also rejected. The rule was "refuse while `force_password_change` is still set /
refuse while no factor is enrolled". It fails two ways:
- **Password:** ticket 12 sets the flag on bootstrap seed, so a seeded admin capped before their first login (ticket
  09's third fresh-install path, the path ADR 13 says the runner closes) would be refused.
- **TOTP:** it only catches restarts that happen before re-enrolment. After each re-enrolment, a leftover invocation
  deletes the new factor again.

### 5. Plan/apply, bound to a state digest

Every destructive run, single or batch, requires `--confirm=<digest>` copied from its own dry-run. Dry-run is the
default, and the destructive form requires an explicit flag plus a mandatory reason string.

- **Single run.** The digest is SHA-256 over a canonical tuple of non-secret state: account UUID, scope,
  `password_disabled_at`, `credential_issued_at`, TOTP row existence and creation time, schema version, and the
  resolved absolute database path.
- **Batch run.** The digest hashes the **concatenation of per-account state tuples**, plus the input-file digest,
  so any one account changing between plan and apply invalidates the whole confirm. Hashing the input file alone
  would bind the intent and not the state. The state binding is the part doing the work.
- **`--operator` is deliberately excluded from the tuple.** That lets one person preview and a second person apply,
  which is the crude two-person split break-glass practice favours. Leaving it out is recorded as a choice, which
  makes it a control. For the split to be visible and not just possible, **the dry-run emits its own row** (§9).
- **What the digest guarantees, stated exactly.** Applying changes the state, so a digest can never match twice.
  A leftover invocation **can fire at most once and can never fire again**. In the realistic sequence (the operator
  applies by hand and the spec is left behind afterwards), it is refused from the first boot onward. This closes
  finding 4's persistent-spec path for **both** scopes.
- **What it does not close:** a stale copy (§6). If plan and apply both hit a backup or a snapshot, the digest
  matches.

### 6. Preconditions: what each is, and where it lives

All checks run before any write. The audit file appender is not attached until checks 1–5 have passed.

| # | Control | Lives in | Negative test (handed to ticket 16) |
|---|---|---|---|
| 1 | Runs through the **full context refresh**, so ticket 24's validator executes. `web-application-type=none` is permitted; what is prohibited is *bypassing refresh* (a `main()` that builds a `DataSource` directly) | code, structural | the runner's context contains the validator bean |
| 2 | Runner mode appends `IFEXISTS=TRUE`, so a wrong path fails instead of creating a database (H2 auto-creates, and resolves relative paths against the working directory, §9) | code | wrong path refuses and creates no file |
| 3 | **Never migrates.** A schema-version mismatch is a refusal whose message names both versions, never an upgrade performed under emergency conditions | code, plus a runbook item | newer jar against an older schema refuses |
| 4 | Ticket 11's seeding `ApplicationRunner` is excluded in runner mode. Runners execute regardless of web-application type, and on an empty database it would seed a fresh admin | code | the empty-database path seeds nothing |
| 5 | Existence by scope (§4) | code | missing account refuses; `totp` with no factor refuses |
| 6 | **App stopped.** This is a **design change, not a check**: in runner mode the audit file appender is attached only after 1–5 pass, and the H2 lock is the signal. Logback's file appender is single-writer unless prudent mode is on (§10). Written against the outcome because Boot's logging-init timing was not established at a primary source (§11) | code | with the app running: non-zero exit and **zero bytes appended** to the audit file |
| — | Digest match (§5) | code | a stale digest refuses; a leftover invocation fires at most once |
| — | Argument gating | code | `REBIND`/`APP_REBIND` in the environment fires nothing (the trigger is read from `ApplicationArguments`, never the `Environment`); EOF or `/dev/null` on stdin aborts and never hangs |
| — | **Stale copy** | **procedural** | none possible. The resolved absolute path, schema version and file mtime are printed on the dry-run, the success line and both audit rows, and the runbook's confirm step compares them against the deployed database *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RUN-004, T-RUN-005, T-RUN-006, T-RUN-007, T-RUN-008, T-RUN-010, T-RUN-011, T-RUN-003. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-RUN-008. Amend the table by ID, not this list.* |

**The stale copy is the one residual no application-side check can distinguish**: a backup, a volume snapshot or a
dev clone passes all six checks and the digest. It is graded `procedural` because that is what it is. *Consolidated into the register (ticket 33): R-RUN-008. Amend the table by ID, not this list.*

### 7. Prohibitions

Prohibited implementations, carried by ticket 24 with their reasons:

1. **Using `System.console()` nullness to choose a mode or to test for a terminal, in either direction.** On output,
   it was the TTY guard this ticket considered and eliminated. On input, it is the interactive-versus-piped branch.
   From JDK 22 the call returns a Console even when streams are redirected, and `Console.isTerminal()` does not
   exist on Java 21 (§2). So the mode is chosen by an explicit `--non-interactive` flag. Interactive mode may call
   `System.console().readPassword()` and **refuse** when the result is null, because that fails closed. That
   refusal **stops refusing on JDK 22+** (interactive mode would then read a piped secret instead of aborting,
   capped at one execution by §5). So this path is named in the rehearsal's JDK-upgrade trigger (§11), and the
   forward migration is recorded: once off Java 21, use `Console.isTerminal()`. *Consolidated into the register (ticket 33): R-RUN-006, R-RUN-011. Amend the table by ID, not this list.*
2. **`process.command_line` or `process.args` on any audit row.** If an operator passes a secret on the command
   line despite the rule, recording the arguments copies it into the audit stream (CWE-532).
3. **`System.getProperty("user.name")` as a fallback identity.** It is a JVM property the operator can override.
   If `ProcessHandle` reports nothing, the row records absence (§12).
4. **A `--password` argument, or any secret-bearing argument** (CWE-214, §7). The closest published instance is a
   credential read from `/proc/cmdline` on a host without `hidepid`. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-029, T-RUN-001. Amend the table by ID, not this list.*

**Refresh-phase validator entries** (URL parameters and beans the validator can see):
- `AUTO_SERVER=TRUE` in the resolved URL.
- **`FILE_LOCK=NO`** in the resolved URL. §6 check 6 rests entirely on the single-writer lock, and this parameter
  switches it off with nothing failing (§13). That is the same shape as every other finding here: a control whose
  enforcement mechanism can be disabled by configuration.
- Any H2 TCP server bean. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-012, T-CFG-013, T-CFG-014. Amend the table by ID, not this list.*

The first two close the ways of sharing the database; `FILE_LOCK=NO` closes the way of disabling the lock that
enforces the interlock. `h2.bindAddress` is a system property the validator **cannot** see, which makes it the
fourth recorded instance of "where a control cannot live". It is moot once the TCP server is prohibited.

### 8. Accountability

- **`--operator` is mandatory**, constrained by charset and length. That is a **format** check, not an existence
  check, and it is deliberately not ticket 11's canonical username form: the operator may be platform staff with no
  account. It is recorded as **`labels.operator_claimed_id`**. That is ECS's own recommended shape for extra keyword
  values (§14), it adds no custom field, and it adds none of ticket 13 §10's banned keys. **Ticket 13's key-absence
  assertion stands untouched and needs no exception.** The value is also deliberately **unresolvable**; resolving it
  against `users` would refuse the platform-engineer case.
- **Corroboration the operator doesn't type:** `process.real_user.name` from `ProcessHandle.current().info().user()`, *Consolidated into the register (ticket 33): R-AUD-033. Amend the table by ID, not this list.*
  plus hostname and PID. When the `Optional` is empty, the row records absence. There is an owed build check on
  which uid the JDK reports (§14).
- **A mandatory reason string**, encoded under **ASVS 16.4.1 (L2)** and capped at **256 characters** on ticket 26's
  precedent.
- **Neither field identifies a human.** `process.real_user.name` is very likely one service account shared by every
  operator, and `labels.operator_claimed_id` is an unverified claim. Human attribution lives in the host's sudo/ssh
  trail, which belongs to the deployer. That is a handover item, so a reviewer knows where to look and doesn't
  over-trust our row. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RUN-002. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-033, R-OPS-007. Amend the table by ID, not this list.*

### 9. Audit shape

- **Dry-run row**: INFO, non-state-changing. It records the previewer, the digest, the target count, and the
  procedural identifiers.
- **Intent row**, emitted after checks 1–5 pass and before the transaction opens, with **`event.outcome: unknown`**.
  It asserts no state change, so it stays inside ticket 13 §11's after-commit rule. It carries the digest, the
  target count, the operator fields, and **the resolved absolute database path and working directory in
  plaintext**. If the run dies inside the window, this row is the only evidence of which database was touched, and *Consolidated into the register (ticket 33): R-RUN-010. Amend the table by ID, not this list.*
  a digest can't be inverted.
- **Outcome row**, emitted after commit or rollback.
- **Batch: one transaction, one outcome row** carrying `user.target.count` (the field ticket 13 row 25 already uses)
  and the input digest, not 240 rows. The one transaction makes "137 of 240, silently split" impossible, where
  otherwise it could only be reported. The single row means a death after commit loses one row, not 240, and the
  intent row makes even that loss detectable. *Consolidated into the register (ticket 33): R-RUN-010. Amend the table by ID, not this list.*
- **Pre- and post-operation enrolled-admin counts** are recorded on both rows. A post-count of zero can't be
  interpreted without the pre-count.
- **ASVS:** 16.2.3 (L2) is satisfied once the operator's terminal is added to the documented destinations, since
  the runner's stdout goes to a destination the inventory must name. 16.4.3 (L2) is **unchanged F**, not newly
  failed: ticket 13 already grades it F with named deployer obligations. The evidence reaches the collector because *Consolidated into the register (ticket 33): R-AUD-034. Amend the table by ID, not this list.*
  the runner shares ticket 13's rolling file appender, which the forwarder reads after restart.

### 10. `AdminActionGuard`

The runner bypasses the guard, and it must: scope `both` has to be able to reach zero enrolled admins. This is
**TM-14's third exception**, after the cascade and the NIST cap. Under §2 the lock has nothing to contend with, and
the self-action rule has no subject because no principal exists. The invariant is made **observable rather than
enforced**, through the pre- and post-operation counts in §9 and ticket 21's per-event alert on a transition to
zero.

### 11. Ticket 25: rows and step 7

Edited **in place** in ticket 25's TM-12 section, not appended beside it:
- **The item is now "no secret is emitted"**, graded `status: asserted-by-test`, `responsibility: shared`. The
  deployer's half is the out-of-band handover of the password to the user.
- **The named test:** run the runner with a known password and assert that string appears in **none** of stdout,
  stderr, or the audit file.
- **Step 7's third clause** is replaced with:
  - confirm the audit rows are in the audit file and reached the collector after restart;
  - confirm forced change fires at first login;
  - run §6's negative cases on the deployed topology (wrong path, app running, leftover invocation);
  - compare the procedural identifiers against the deployed database.
- **Recurrence triggers:** any change to the runner, the password policy, or the bootstrap; **any JDK, Boot or H2
  major upgrade** (this map's repeated failure shape is a neighbouring default moving, and §7 prohibition 1 is an
  instance sitting in this ticket); plus a calendar ceiling the deployer sets. *Consolidated into the register (ticket 33): R-RUN-006. Amend the table by ID, not this list.*
- **ASVS 6.1.1 (L1) is re-asserted, not inherited.** It stands as **pass-with-note pending first rehearsal** and is
  not graded pass until the rehearsal has passed once. **If the rehearsal comes back red, 6.1.1 is graded F, with
  the failure as its cause, and this ticket reopens.** ADR 13's cap-path closure and ticket 25's break-glass *Consolidated into the register (ticket 33): R-RUN-002. Amend the table by ID, not this list.*
  conditional pass depend on the same outcome. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-027. Amend the table by ID, not this list.*

### Handover items (ticket 25)

- **A named, reachable runner operator with deploy-level access.** Discharges ASVS 6.1.1 (L1) conditionally,
  with ticket 25's ownership statement. Enforceable: no. Proof: none possible; this attests that a named role is
  filled and its holder is reachable (ticket 25's sentinel). *Consolidated into the register (ticket 33): R-RUN-003. Amend the table by ID, not this list.*
- **Recovery through the runner is a planned outage.** Stop, run dry-run, apply, restart. Discharges 6.1.1 (L1)
  (procedure). Enforceable: partly, since the H2 lock refuses a concurrent run (§6 check 6). Proof: the step-7
  rehearsal's "app running" negative case, executed on the deployed topology. *Consolidated into the register (ticket 33): R-RUN-004. Amend the table by ID, not this list.*
- **Use the jar that matches the deployed version.** Discharges 6.1.1 (L1) (procedure). Enforceable: partly;
  the runner refuses a schema mismatch and names both versions (§6 check 3). Proof: the rehearsal runs the
  deployed artefact, and the dry-run output shows matching versions. *Consolidated into the register (ticket 33): R-RUN-005. Amend the table by ID, not this list.*
- **Compare the printed database path, schema version and mtime against the deployed database before applying.**
  Discharges the stale-copy residual (no ASVS ID; register line 3). Enforceable: no. Proof: the rehearsal record
  shows the comparison was performed. *Consolidated into the register (ticket 33): R-RUN-008. Amend the table by ID, not this list.*
- **Hand the operator-set password to the user out of band, over a channel that is not the audit stream.** Discharges
  6.4.1 (L1) together with the application-enforced forced change. Enforceable: forced change and the 30-day
  lazy expiry, yes; the handover itself, no. Proof: the user's first login shows `PASSWORD_CHANGE_REQUIRED`. *Consolidated into the register (ticket 33): R-RUN-001. Amend the table by ID, not this list.*
- **The host's sudo/ssh trail is the human attribution for any runner invocation, retained at least as long as the
  audit file.** Discharges ASVS 16.2.1 (L2) "who", which our row supports only by claim. Enforceable: no.
  Proof: a sample invocation can be traced from the audit row's hostname, PID and time to a named human in the
  host trail. *Consolidated into the register (ticket 33): R-OPS-007. Amend the table by ID, not this list.*
- **Rehearse on the recurrence triggers in §11.** Discharges 6.1.1 (L1) and ticket 25's step 7. Enforceable: no.
  Proof: a dated rehearsal record newer than the most recent trigger. *Consolidated into the register (ticket 33): R-RUN-006. Amend the table by ID, not this list.*
- **Plan capacity for a mass-lockout event.** Up to 240 sequential interactive runs until mail transport exists.
  Discharges the availability residual (register line 4). Enforceable: no. Proof: a costed RTO, or an explicit
  acceptance of the uncosted one, signed by the deployer. *Consolidated into the register (ticket 33): R-RUN-009. Amend the table by ID, not this list.*

### 12. ADRs and register

**Five new ADRs:**
1. The offline process model and planned outage, with the `AUTO_SERVER`, loopback-listener and in-app rejections. *Consolidated into the ADR routing (ticket 34): ADR-072. Amend by ID, not this list.*
2. Credential in, nothing out; the batch leg mints nothing; the scoped 6.4.6 (L3) withdrawal. *Consolidated into the ADR routing (ticket 34): ADR-073. Amend by ID, not this list.*
3. Plan/apply digest: tuple composition, batch concatenation, `--operator` excluded as a two-person capability. *Consolidated into the ADR routing (ticket 34): ADR-074. Amend by ID, not this list.*
4. Runner-mode preconditions: full refresh, `IFEXISTS`, no migration, no seeder, lazily attached appender. *Consolidated into the ADR routing (ticket 34): REJ-089. Amend by ID, not this list.*
5. Audit shape: dry-run, intent and outcome rows, a single batch row, the `labels.operator_claimed_id` placement. *Consolidated into the ADR routing (ticket 34): REJ-090. Amend by ID, not this list.*

**Two amended:** ticket 09 §R.3's four-constraints ADR, whose constraints 1, 3 and 4 are superseded here and
whose 6.4.1 citation is corrected; and ticket 24's eleventh prohibited-configuration entry, which is replaced. *Consolidated into the ADR routing (ticket 34): ADR-072 (attached amendment) / register (the entry it replaces was withdrawn). Amend by ID, not this list.*

**Register, seven lines:**
1. **ASVS 6.4.6 (L3), a withdrawal of a volunteered pass, scoped to the runner path.** It is still satisfied on
   the in-app admin paths, the path is above target and behind host access, and the protection there was already
   notional. *Consolidated into the register (ticket 33): R-RUN-007. Amend the table by ID, not this list.*
2. **A PDPA trade on the staff identifier.** A raw staff identifier is retained in the audit stream for 90 days or
   more. That is the same class of data the map HMACs for tombstone emails and bans as raw usernames. It is kept
   in the clear because hashing it destroys its only purpose, which is being able to ask the person. The field is
   deliberately unresolvable (§8). *Consolidated into the register (ticket 33): R-AUD-033. Amend the table by ID, not this list.*
3. **Stale-copy residual**, `procedural`. *Consolidated into the register (ticket 33): R-RUN-008. Amend the table by ID, not this list.*
4. **The mass-lockout RTO, uncosted** (§3). *Consolidated into the register (ticket 33): R-RUN-009. Amend the table by ID, not this list.*
5. **The post-commit window**, made detectable by the intent row, not closed. *Consolidated into the register (ticket 33): R-RUN-010. Amend the table by ID, not this list.*
6. **JDK-22 console behaviour change**: bounded to one execution, carried on the recurrence trigger. *Consolidated into the register (ticket 33): R-RUN-011. Amend the table by ID, not this list.*
7. **ASVS 16.2.3 (L2)**: the operator terminal is added as a documented destination. *Consolidated into the register (ticket 33): R-AUD-034. Amend the table by ID, not this list.*

**No glossary terms.** Two candidates were considered and dropped: *planned-outage recovery* is a description,
and *plan/apply* is borrowed practice, not project language.

### 13. Amendments owed, and the graduation

- **09**: the 6.4.1/6.4.6 correction; §R.3 constraints 1, 3 and 4 superseded; the runner can now execute.
- **12**: the runner becomes `credential_issued_at`'s fifth issuance trigger.
- **13**: the §12 citation fix; the dry-run, intent and outcome rows; the two new keys.
- **15**: TM-12 closed.
- **16**: the negative tests in §6 and §11.
- **17**: register and ADR inputs.
- **21**: the zero-admins per-event alert, and a new absence-detection class, with its cost attached.
- **23**: the tier-2 break-glass route now runs through the runner.
- **24**: the four prohibitions and three validator entries.
- **25**: the in-place TM-12 edit.

**Graduated: [ticket 30](30-sole-admin-bootstrap-premise.md).** Ticket 11's "One admin, not two" rested on "a
locked-out sole admin is delayed rather than locked out", and ticket 09 §R removed that premise. Whether the
decision survives is ticket 11's question to answer, not this ticket's to redesign. It is too sharp to post as an
amendment on a resolved ticket that nobody would answer.

Status: resolved.
