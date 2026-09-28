# 13 — Build the audit event catalogue

Type: grilling
Status: resolved
Blocked by: 03, 09, 10, 11, 23

## Question

What is the complete list of audit events this app emits, and for each one: the exact fields, the log
level, and which requirement it satisfies?

## Starting point

The App Standard §3.3 and §3.4 give the required events and the shape. Every audit event must carry
timestamp, principal, outcome, request path, HTTP method, and correlation ID. Required events:

- Login success, login failure, logout success
- Lockout transitions (locked, unlocked)
- User account operations — create, unlock, update, delete — attributable to the acting admin
- Password reset token issuance and redemption
- Password changes, both admin-initiated and self-service
- Role assignments and changes
- Administrative deletion, preserving a tombstone record

Levels are prescribed: INFO for successes, WARN for failed logins, lockout transitions, rate-limit
breaches and authorization failures, ERROR for authentication system failures.

## What to decide

- The catalogue itself: one row per event with `event.action`, `event.outcome`, level, fields, and
  the PRD story or standard clause it satisfies. The exact `event.action` vocabulary comes from
  "Extract the binding structured logging and audit schema" — use it rather than coining names.
- **Actor and target identification.** The PRD wants admin actions logged with actor and target. The
  logging standard bans cleartext usernames and emails and mandates UUID `user.id`. Decide the field
  names for actor and target UUIDs — ECS has `user.id` for one subject, so a second subject needs a
  deliberate choice (`user.target.id`? a custom field?). This is the sharpest open question here.
- **Events with no resolved user.** A failed login for an unknown username has no UUID. The standard
  says omit user identity and rely on `session.hash` and `trace.id`. Confirm, and make sure that
  doesn't make failed logins useless for investigation — decide what *is* recorded so a brute-force
  campaign is still reconstructable without logging the attempted username.
- Where audit emission lives: a typed audit component (the logging standard has a recipe for
  centralising it) or inline calls. A central component is testable and makes "did we log it?" a
  real assertion.
- Correlation: how `trace.id` is generated and propagated, and how `session.hash` is computed.
- **Retention.** The standard requires 90 days retained durably. The PRD says structured log lines,
  no audit table. Decide whether log lines alone can satisfy durable 90-day retention with no
  infrastructure in scope, or whether this becomes a documented deferral. Check what "Extract the IM8
  and ARC controls" found — if IM8 requires a durable audit store, log lines fail and this needs the
  user's decision.
- The negative list, as testable assertions: never log passwords, reset token plaintext, reset token
  **hashes**, CSRF tokens, or raw session IDs.

## Done when

The catalogue is complete and each row is specific enough to write a log assertion against, the
actor/target field naming is decided, and the retention question is answered rather than deferred by
omission.

## Inherited from ticket 11 — the admin event set, and two new fields

[Decide the admin module, role model, and initial admin bootstrap](11-admin-module-role-model-and-bootstrap.md)
specified nine endpoints and attached an audit obligation to each, so the admin half of this catalogue is now a
transcription job rather than a design one. Rows owed:

| Operation | Emitter named by ticket 03 | Notes for this ticket |
|---|---|---|
| Admin list users | `adminUserListed` | **new field `user.target.count`** — rows returned |
| Admin read one user | none yet | event owed; `event.action: user-administration` |
| Admin create user | — | `event.action: user-provisioning`, creation |
| Enable / disable | `adminUserStatusChanged` | which direction must be distinguishable |
| Change role | `adminUserRoleChanged` | carries `user.target.roles`, new role only |
| Delete | `adminUserDeleted` | tombstone written in the same transaction |
| Unlock | none yet | event owed; **new field `user.target.unlockReason`** |
| Admin TOTP reset | `event.action: totp-remove` | ticket 19's row; also kills the target's sessions |
| Self-read | `event.action: profile-read` | enum already contains it |

Two fields are new, and both are deliberate rather than incidental:

- **`user.target.count`** on the list event. This is half of a control, not telemetry: a hard page cap plus a
  recorded row count is ticket 11's answer to returning every email in the system to every admin, chosen over
  masking because masking costs a PRD criterion and this does not. If the count is dropped, the control is gone.
- **`user.target.unlockReason`**, a closed enum — `USER_REQUEST | FALSE_POSITIVE | PASSWORD_RESET_COMPLETED |
  OTHER` — with **no free-text variant**. Free text here would write cleartext PII into the stream this pillar
  bans it from, and a newline in it would break the one-event-per-line NDJSON constraint. It is also the only
  form of this field that is queryable.

**The schema-amendment package owes three fields, not one.** Ticket 03's C8 registers `user.target.id` only,
while `user.target.roles` is used in ticket 03's own worked example and is equally absent from `Log_Schema.md`.
Adding `user.target.unlockReason` makes three. Whether `user.target.count` needs the same treatment is yours to
decide: `user.target` is an ECS-sanctioned reuse location, but `count` is not an ECS `user` field, so it may be
a fourth or it may need a different name.

**Retention asymmetry to state rather than discover.** Ticket 03 concluded the application is not responsible
for the 90-day TTL — it belongs to the central platform's index lifecycle policy. So the accountability record
for an admin unlock may expire after three months under a policy nobody on this project controls, while the
tombstone that unlock relates to is retained indefinitely. Ticket 11 relies on the audit trail as the *only*
record of who deleted whom and why an account was unlocked; if that trail is the compensating control for a
minimal tombstone, its lifetime being externally set is worth writing down.

One more, from ticket 11's soft-delete decision: after deletion, **no system can resolve a tombstone's `uuid`
back to an email address**, because the tombstone stores an HMAC and this pillar bans cleartext emails from
logs. Audit traceability therefore runs `uuid` → `user.target.id` in the log, and stops there. That is the
intended privacy outcome, but it means the audit trail is the sole route to "which account was this", so its
completeness on admin events matters more than it would otherwise.

---

## Answer

**Forty rows, `user.id` on resolved login failures, three custom fields not five, and the catalogue
compiled rather than written down.** The load-bearing finds are that ticket 09's only compensating
control for a declined NIST `SHALL` was not computable from the log stream ticket 09 itself
prescribed; that the field name this map had been carrying for a hashed client IP would have been
rejected at ingest; and that an audit row can leak the submitted password without any call site
mentioning it.

### §0 Precedence, stated once

Three of this ticket's decisions reverse an inherited one, so the rule is recorded here and cited
rather than re-argued per row:

**Governing user standard > logging standard > recipes.** *Consolidated into the ADR routing (ticket 34): REJ-047. Amend by ID, not this list.*

`Standalone_User_Access_Control_Application_Standard.md` is the standard this application is assessed
against; `Structured_Logging_Application_Standard.md` + `Log_Schema.md` govern shape and vocabulary;
recipes are worked examples and lose to both. Consequences, all recorded as ADRs rather than as
C-series notes:

| Reversal | Inherited position | Now | Authority |
|---|---|---|---|
| Lockout level | ERROR (ticket 03 C3, AuthN recipe `log.atError()`) | **WARN** | §3.4 Log Levels, twice; §3.3's own definition of ERROR |
| Client IP | cleartext `source.ip` on security events (ticket 03 C1) | **`source.ip_hash` only, never the address** | §3.3 prohibited content + ASVS 16.2.5 |
| `user.id` on login failure | omit always (ticket 09 §12, both recipes) | **present when the account resolves** | §3.4 Privacy and Data Protection |

C1 and C3 are **withdrawn, not silently dropped**. C1's ADR is replaced by a narrower one: we deviate
from the AuthN recipe's `source.ip`, not from §3.3. *Consolidated into the ADR routing (ticket 34): REJ-043 / ADR-054 / D:03-X-1 and D:03-X-2 dropped (superseded; see its routing §4). Amend by ID, not this list.*

### §1 The amendment rule this map has been deciding case by case

Four tickets have now asked whether a later ticket may amend a resolved one. The test, recorded once
so ticket 17 inherits a principle rather than four precedents:

> **Does the amendment weaken a compensating control standing in for a declined `SHALL`?
> If yes, reopen the ticket that declined it. If no, amend and cite.**

Applied here: this ticket's change to the failed-login row *restores* computability of ticket 09's
only compensating control, so it is unambiguously an amendment. *Consolidated into the ADR routing (ticket 34): REJ-048. Amend by ID, not this list.*

### §2 The catalogue

Every row carries the Tier A/B field set from ticket 03 plus, per §3.3's "All audit events must
include … Request path, HTTP method, and correlation ID", **`url.path` and `http.request.method` on
every request-scoped row** — which is wider than ticket 03's C4 proposed (admin rows only). `trace.id`
is Tier A. `event.kind: event` and `event.category: ["process"]` are constant for every row below
and omitted from the table.

**Authentication and session**

| # | Row | `event.action` | `event.type` | Level | Sev | Identity | Reason vocabulary |
|---|---|---|---|---|---|---|---|
| 1 | Login success | `user-authentication` | `["user"]` | INFO | low | `user.id` | — (`auth.method: password`) |
| 2 | Login failure | `user-authentication` | `["user"]` | WARN | medium | **`user.id` when resolved, omitted when not** | `BAD_CREDENTIALS` \| `UNKNOWN_USER` \| `ACCOUNT_LOCKED` \| `ACCOUNT_DISABLED` \| `CREDENTIAL_EXPIRED` |
| 3 | Lockout engaged | `user-authentication` | `["error"]` | **WARN** | high | `user.id` always | `THRESHOLD_REACHED` |
| 4 | Lockout cleared | `user-authentication` | `["change"]` | INFO | low | `user.id` | `AUTO_LIFT` \| `ADMIN_UNLOCK` \| `RESET_REDEMPTION` |
| 5 | Per-IP throttle breach | `access-control` | `["denied"]` | WARN | medium | none | `RATE_LIMITED_SOURCE` |
| 6 | Per-account throttle breach | `access-control` | `["denied"]` | WARN | medium | **none, by construction** | `RATE_LIMITED_IDENTIFIER` |
| 7 | Logout | `user-logout` | `["end"]` | INFO | low | `user.id` | — |
| 8 | Session id rotated at authentication | `session-start` | `["start"]` | INFO | low | `user.id` | `LOGIN` \| `FACTOR_GRANT` \| `PASSWORD_CHANGE` |
| 9 | Session ended — absolute lifetime | `session-end` | `["end"]` | INFO | low | `user.id` | `ABSOLUTE_TIMEOUT` |
| 10 | Session ended — concurrent eviction | `session-end` | `["end"]` | INFO | low | displaced `user.id` | `CONCURRENT_EVICTION` |
| 11 | Invalid session presented | `session-end` | `["end"]` | INFO | low | none | `UNKNOWN_OR_EXPIRED` |
| 12 | Authorisation denied (403) | `access-control` | `["denied"]` | WARN | medium | actor `user.id` | `INSUFFICIENT_ROLE` |
| 13 | CSRF validation failure | `access-control` | `["denied"]` | WARN | medium | `user.id` when authenticated | `CSRF_MISSING` \| `CSRF_INVALID` |
| 14 | Factor required (412) | `access-control` | `["denied"]` | WARN | medium | `user.id` | `FACTOR_MISSING` \| `FACTOR_EXPIRED` |
| 15 | Authentication system failure | `user-authentication` | `["error"]` | ERROR | high | where resolved | `SYSTEM_FAILURE` |

Row 13 is **new to this map**. §3.3 requires logging "security control bypass attempts, including
validation, business logic, and anti-automation"; ticket 08 specified session-bound header-only CSRF
and six envelope producers but no audit row for a CSRF rejection. Without it the one control that
stands between a cross-origin attacker and every state-changing endpoint is silent.

Row 4 is also new, and closes §3.3's "lockout transitions (locked, **unlocked**)", which had no
producer: the 20-minute lift is lazy and there is no scheduler, so the transition is observable only
where the counter is cleared — successful authentication, admin unlock, or ticket 10's reset
redemption. All three are in one row with a reason, which is also the only place the map records that
three different mechanisms clear the same state.

**Credential flows**

| # | Row | `event.action` | `event.type` | Level | Sev | Identity | Reason vocabulary |
|---|---|---|---|---|---|---|---|
| 16 | Registration accepted | `user-provisioning` | `["creation"]` | INFO | low | new `user.id` | `NEW_ACCOUNT` \| `EXISTING_ADDRESS` |
| 17 | Registration rejected — username taken | `user-provisioning` | `["creation"]` | WARN | low | none | `USERNAME_UNAVAILABLE` |
| 18 | Credential token issued | `password-reset` | `["change"]` | INFO | low | see below | `ACTIVATION` \| `SELF_RESET` \| `ADMIN_RESET` \| `ADMIN_INVITE` |
| 19 | Credential token redeemed | `password-reset` | `["change"]` | INFO | low | `user.id` | `ACTIVATION` \| `RESET` \| `INVITE` |
| 20 | Credential token redemption failed | `password-reset` | `["change"]` | WARN | medium | none | `TOKEN_UNKNOWN` \| `TOKEN_EXPIRED` \| `TOKEN_CONSUMED` |
| 21 | Self-service password change | `password-reset` | `["change"]` | INFO | low | `user.id` | — |
| 22 | Self-service password change failed | `password-reset` | `["change"]` | WARN | medium | `user.id` | `CURRENT_PASSWORD_MISMATCH` |
| 23 | Password rejected by policy | `password-reset` | `["change"]` | WARN | low | `user.id` when authenticated | ticket 07's six: `MIN_LENGTH` \| `MAX_BYTES` \| `BLOCKLISTED` \| `CONTEXT_TERM` \| `TOO_WEAK` \| `HISTORY_REUSE` |
| 24 | Forced password change completed | `password-change-enforcement` | `["change"]` | INFO | low | `user.id` | — |

Row 18's identity splits on the axis ticket 06 established, and the split *is* the control:
`SELF_RESET` **omits `user.id`** because the request is by email against a uniform 202 (ticket 03 C9);
`ADMIN_RESET` and `ADMIN_INVITE` carry **actor `user.id` + `user.target.id`**, and ticket 10 named the
admin-issued row "the sole detector of admin abuse". `ACTIVATION` carries the new `user.id`.

Row 23 inherits ticket 07's ordering rule as a *logging* constraint: on the reset path the token check
must pass before password quality is evaluated, so this row can never precede a successful token
check. A row emitted before it would confirm a valid token by the presence of a strength reason.

**Administration**

All carry actor `user.id` and, except row 25, `user.target.id`.

| # | Row | `event.action` | `event.type` | Level | Sev | Extra fields |
|---|---|---|---|---|---|---|
| 25 | List users | `user-administration` | `["access"]` | INFO | low | **`user.target.count`** |
| 26 | Read one user | `user-administration` | `["access"]` | INFO | low | — |
| 27 | Create user (invite issued) | `user-provisioning` | `["creation"]` | INFO | low | — |
| 28 | Enable account | `user-administration` | `["change"]` | INFO | low | message `Account enabled.` |
| 29 | Disable account | `user-administration` | `["change"]` | INFO | low | message `Account disabled.` |
| 30 | Change role | `user-administration` | `["change"]` | INFO | low | `user.target.roles` (new role only) |
| 31 | Delete account | `user-administration` | `["deletion"]` | INFO | low | — |
| 32 | Unlock account | `user-administration` | `["change"]` | INFO | low | **`user.target.unlock_reason`** |
| 33 | Reset target's TOTP | `totp-remove` | `["change"]` | INFO | low | — |
| 34 | Administrative attempt failed | `user-administration` | `["error"]` | WARN | medium | `SELF_ACTION` \| `TWO_ADMIN_INVARIANT` \| `LOCK_TIMEOUT` \| `TRANSACTION_ROLLBACK` |
| 35 | Self profile read | `profile-read` | `["allowed"]` | INFO | low | — |

Row 34 answers §3.4's "failed administrative attempts", which had no rows anywhere on this map. It is
emitted **per rejected attempt**, not per operation, so it is not a doubling of the admin row count.

**TOTP**

| # | Row | `event.action` | `event.type` | Level | Sev | Identity | Notes |
|---|---|---|---|---|---|---|---|
| 36 | TOTP provisioned | `totp-enrol` | `["creation"]` | INFO | low | `user.id` | reason `PROVISIONED`; no secret, no `otpauthUri`, no ciphertext, no key material |
| 37 | TOTP enrolment confirmed | `totp-enrol` | `["creation"]` | INFO | **medium** | `user.id` | reason `CONFIRMED`; **alert-worthy**; the compensating record for the failed NIST §4.1.2.1 notification `SHALL` |
| 38 | Factor verified | `user-authentication` | `["user"]` | INFO | low | `user.id` | `auth.method: totp`; pairs with row 8 `FACTOR_GRANT` |
| 39 | Factor verification failed | `user-authentication` | `["user"]` | WARN | medium | `user.id` | attempt count; **never the submitted code** |
| 40 | Factor tier-1 lock | `user-authentication` | `["error"]` | WARN | high | `user.id` | once per transition, detected inside the row lock |
| 41 | Factor tier-2 disable | `user-authentication` | `["error"]` | ERROR | critical | `user.id` | 100-failure cap; the break-glass trigger |
| 42 | TOTP decrypt context mismatch | `KMS_DECRYPT` | `["error"]` | ERROR | critical | `user.id` | ticket 23 §9; only trips when rows have been moved |

Row 42 uses **`KMS_DECRYPT`, an existing enum member**, rather than requesting a new one — the one
place where the enum's file-and-batch heritage happened to supply what we needed.

**Lifecycle**

| # | Row | `event.action` | `event.type` | Level | Sev | Notes |
|---|---|---|---|---|---|---|
| 43 | Startup | `application-startup` | `["start"]` | INFO | low | `host.name`, `host.ip` (§3.1 requires both here), active profiles, ticket 24's key fingerprints, **and Q13's enabled audit-relevant logger list, folded into this row rather than a second one** |
| 44 | Shutdown | `application-shutdown` | `["end"]` | INFO | low | — |

**Deliberately not logged.** `GET /api/hello` and every successful authorisation decision: ASVS
16.3.2 requires logging *failed* authorisation at L2 and all decisions only at **L3**, and §8.1 of the
AuthN recipe says not to log every authorisation check. Recorded as a decision, not an omission.

**Three §3.3 required events with no operation to attach to**, recorded as negative assertions rather
than left to read as gaps:

- **Security header configuration changes** — headers are static, set once in the filter chain and in
  the Vite-templated meta tag. No runtime mutation path exists. Asserted by the absence of any
  endpoint or property that mutates them.
- **Critical configuration changes** — no runtime configuration mutation path; row 43 records the
  effective values at startup instead.
- **Significant business decisions / bulk data export** — no business domain; the nearest analogue is
  row 25, which is why `user.target.count` exists. *Consolidated into the register (ticket 33): R-AUD-005. Amend the table by ID, not this list.*

### §3 The field-extension package: three custom, two where our schema is behind ECS

Verified against the ECS reference rather than inferred:

- **`user.target.*` is a documented ECS field-reuse location** ("Targeted user of action taken"), and
  **`user.roles` is a real ECS field**. So `user.target.id` and `user.target.roles` are **valid ECS
  that `Log_Schema.md` is simply behind on** — a materially easier amendment to land than "please add
  our invention", and worth writing that way.
- **Genuinely custom: three.** `user.target.unlock_reason`, `user.target.count`, `source.ip_hash`. *Consolidated into the register (ticket 33): R-AUD-002. Amend the table by ID, not this list.*
- **`unlockReason` is renamed to `unlock_reason`.** Ticket 11 coined it in camelCase, which matches
  neither ECS's underscore convention nor the org corpus. Values stay upper-snake
  (`USER_REQUEST | FALSE_POSITIVE | PASSWORD_RESET_COMPLETED | OTHER`), matching both precedents in
  the corpus.
- **`labels.*` was considered and rejected for `count`.** ECS documents every `labels` value as stored
  as **keyword**, which would strip a numeric count of numeric aggregation — and that count is half a
  control, not telemetry.
- **`query.count` reuse was considered and rejected.** The org schema already defines it, but its
  description is scoped to MPDS personnel retrieval, so borrowing it would misdescribe the row.
- `user.hash` is itself a legitimate ECS field, so the recipes ban it on **security** grounds, not
  schema grounds. It is never emitted.

**There is no declaration process.** The only precedent anywhere in `Appfw-Logging-Standards` is
`error.category`, which became legitimate by being written into `Log_Schema.md`. So the package is an
amendment request with that precedent cited, recorded as an integrator obligation. Ticket 03's *Consolidated into the register (ticket 33): R-AUD-002. Amend the table by ID, not this list.*
inference that `addKeyValue("user.target.id", …)` writes cleanly because `user` is not pre-sealed
**stays an inference** and becomes a named first-implementation test. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-010. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): REJ-044. Amend by ID, not this list.*

### §4 `source.ip_hash`, and why the inherited name would have been rejected at ingest

`source.ip` is **`type: ip`, level core** in ECS and `string` in `Log_Schema.md`. Either way a
sub-field forces `source.ip` to become an object where every other producer on the shared platform
emits a scalar — a mapping conflict, i.e. dropped documents, which is precisely the cross-service
breakage the closed enum exists to prevent. `source.ip.hash`, which this map had been carrying since
ticket 09, is therefore **prohibited**.

Resolution: **`source.ip_hash`**, a sibling scalar under `source`, which cannot collide with
`source.ip`. `labels.source_ip_hash` is the ECS-guidance-safest alternative and is recorded as
considered; `source.ip_hash` wins on keeping one prefix for everything about the client and on
matching the corpus's existing habit of custom leaves beside ECS sets (`auth.method`,
`session.max_inactive_interval`, `export.id`). Residual: a future ECS field of that exact name. *Consolidated into the register (ticket 33): R-AUD-006. Amend the table by ID, not this list.*

**The rotation interval is a constraint, not a note.** Ticket 24 gave this hash its own key,
`app.security.hmac.log.key`, and recorded rotation as "freely — correlation need not span a
rotation". That is now qualified: rotation **breaks source correlation across the boundary**, so the
interval is pinned at **≥ the investigation window**, or the previous key is retained for
verification. A key that rotates faster than the window silently destroys the only thing the field
exists for, and the failure is invisible — every row still has a hash.

`session.hash` shares the same key, and ASVS **16.2.5** is the positive citation for hashing both
(logging enforced by the data's protection level, session tokens hashed or masked as its example).
ASVS does not name IP addresses, so the citation is 16.2.5's general rule plus V16's control
objective — not an implication that ASVS names IPs.

### §5 `user.id` on resolved login failures — four deviation points, one mandate

**Decision: emit `user.id` on a failure row when the account resolves; omit it when it does not;
never a username, email, or `user.hash`.**

The authority is the §3.4 **Privacy and Data Protection** clause, which *mandates* `user.id` and
carves out only "authentication entry points where the UUID is not yet resolved" — a mandate with an
exception, not a permission. §3.4's Log Levels parenthetical is **not** the citation: it grammatically
attaches to *account lockout transitions*. Getting that right matters because it is the line a
compliance reviewer opens first.

**The decisive argument is that the protection had already been spent.** Ticket 06 §3 routes the
internal failure reason — wrong password, unknown user, locked, disabled, grace-expired — to the audit
log at WARN. The log already distinguishes unknown-user from wrong-password. Withholding `user.id` was
therefore **pure cost against zero protection**.

**Deviated from in four places**, all quoted so the entry survives inspection:

1. `Logging_AuthN_And_AuthZ_Events.md` §3 line 31 — "Omit it entirely and rely on `trace.id` for
   correlation — do not fall back to logging a hashed username".
2. Same file §10 Verification line 464 — "No `user.id` (account may not exist — omitting avoids
   confirming account existence)".
3. Same file §11 Key Takeaways line 489 — "**Omit `user.id` on failure events**".
4. `Centralising_Audit_Logging_With_A_Typed_Module.md` **line 305** — a printed test assertion,
   `assertThat(kv).doesNotContainKey("user.id").doesNotContainKey("user.hash")`, inside
   `loginFailure_emitsWarnEvent_withNoUserIdentity`. **This assertion is inverted**, which is the
   sharpest single departure from the corpus on this map and the reason this is its own ADR.

**Residual, stated rather than discovered:** the *presence or absence* of `user.id` is itself an
existence oracle, unavoidable under any include-when-resolved scheme, and already implied by ticket
06's reason field. It is a log-reader oracle, not a wire oracle, and log readers are already
privileged. A spray against non-existent usernames stays invisible per-account and is caught only by
`source.ip_hash`. *Consolidated into the register (ticket 33): R-AUD-007. Amend the table by ID, not this list.*

**Implementation constraint that keeps the deviation contained:** `user.id` on a failure row is set
**explicitly at the listener**, never via MDC. Ticket 03's `MdcUserFilter` puts `user.id` on every
line in a request; on a pre-auth path it is absent and **must stay absent**, or the UUID leaks onto
unrelated rows in the same request.

**Scope is three rows, one change.** Row 2 changes. **Row 3 is unchanged** and already carries
`user.id` — it is the row §3.4's parenthetical actually attaches to, and the UUID is resolved with
certainty because ticket 09's listener holds the row lock by primary key at the transition. **Row 6 is
unchanged and keyless by construction, not by policy**: ticket 09 placed the per-account limiter in
the `AuthenticationConverter`, throwing before `authenticationManager.authenticate`, so no repository
lookup has happened and no UUID exists at that call site. Both rows are therefore governed by the
*same* Privacy carve-out, for one principled reason rather than two competing policies.

> **Invariant: do not add a repository lookup to the converter to make rows 2 and 6 consistent.**
> This is the "fix" a later session will propose on seeing the asymmetry. It would put an existence
> check in front of the framework's timing mitigation (ticket 06), reintroduce the CVE-2026-22746
> shape, and destroy the guarantee ticket 09 bought by placement. **The asymmetry is the control.**

**The upside nobody had claimed.** Once resolved failures carry `user.id` and unresolved ones do not,
a high ratio of identity-absent failures from one `source.ip_hash` is a **username-enumeration
signature**. Ticket 09 §4 argued the submitted-string bucket was the only account-axis control on
discovery traffic against non-existent accounts; that traffic is now also *visible*, which is the half
that was missing. Handed to ticket 21 alongside the 50-in-24h alert. The same field is simultaneously
a log-reader oracle and a defender's signal. *Consolidated into the ADR routing (ticket 34): REJ-042. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-007. Amend the table by ID, not this list.*

### §6 Ticket 09's alert is now computable, verbatim

Ticket 09 §9 declined NIST SP 800-63B-4 §3.2.2's cumulative cap and replaced it with one commitment:
alert when a single account exceeds **50 failed logins in 24 hours**. With no account key on failure
rows that was **not computable from the log stream** — and it is the sole compensating control for a
declined `SHALL`, with lm-16 (no monitoring) already an unacknowledged IM8 FAIL. §5 makes it
computable **as written**; the earlier proposal to restate it as "≥10 lockout transitions" is dropped,
because it would have missed exactly the attacker ticket 09 already admitted it cannot stop — the
paced attacker who fails four times, waits out the staleness reset, and never locks.

**NIST framing corrected against the primary source** (verified at
`pages.nist.gov/800-63-4/sp800-63b/authenticators/`, Rate Limiting (Throttling)) and handed to ticket
17 as an amendment to ticket 09's register entry:

- The `SHALL` limits **consecutive** failed attempts on one account to no more than 100 **by disabling
  that authenticator**, and disabled authenticators **SHALL rebind**.
- Reset-on-success is a **SHOULD**, and it is the **only** sanctioned reset. There is no time-based
  reset anywhere in §3.2.2, so our 20-minute staleness window means the consecutive counter can never
  approach 100 and the `SHALL` cannot be met by that counter at all.
- **The deviation is the remedy, not the ceiling.** NIST states 100 is an upper bound that agencies
  MAY lower; locking at 5 *is* a lower consecutive limit. What we do not do is disable and require
  rebinding.
- NIST's own rationale for choosing 100 is balancing guess-likelihood against "the potential need for
  account recovery when the limit is exceeded" — **ticket 09's auto-lift argument in NIST's own
  words**, which strengthens the deferral rather than excusing it.
- **Progressive delays are additions, not alternatives.** NIST lists bot-detection challenges, an
  increasing wait after failures (30 seconds up to an hour), and risk-based signals as techniques to
  reduce *the chance an attacker locks out the legitimate claimant*. Ticket 09's parenthetical that
  backoff and lockout "are alternatives, not additions" is wrong, and progressive delay would have
  partly addressed the malicious-lockout residual. Recorded; not reopened. *Consolidated into the register (ticket 33): R-LCK-006. Amend the table by ID, not this list.*
- Ticket 23's note that its cumulative TOTP reading is stricter than NIST's consecutive `SHALL` is
  **confirmed correct**.

### §7 Discriminators: the enum is lossy and the path is not stable

`event.action` collapses six admin operations onto `user-administration`, and `event.type` cannot
separate them either — enable, disable, role change and update all land on `change`. Resolution:

- **Primary: the static `message` string**, which §3.3 already requires be static and is therefore
  queryable, plus a precise `event.type`. Enable versus disable is two messages
  (`Account enabled.` / `Account disabled.`), **not** an `event.reason` value — because a *failed*
  enable attempt would then have two claimants for one field, the direction and ticket 06's internal
  reason, and row 34 makes that collision real rather than hypothetical. `event.reason` is reserved
  for explaining `event.outcome`, which is what the schema says it does.
- **`url.path` + `http.request.method` are mandatory context on every request-scoped row**, per §3.3
  read literally.
- **`url.path` is the matched route pattern**, from `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`,
  not the requested URI. The raw URI is unique per *target* because five admin endpoints carry a UUID
  in the path, so the earlier claim that path-plus-method "discriminates all eight admin endpoints
  uniquely" is true only of the pattern. The standard also **externalises the API base path via
  application properties** (§ Configuration; Questions Q31; the recipes template `${api.base-path}`
  throughout), so any saved query keyed on a literal path breaks when a deployer changes it — which is
  why the pattern is context and the message is the discriminator. *Consolidated into the register (ticket 33): R-AUD-016. Amend the table by ID, not this list.*
- **Handler-scoped rows therefore have no injection surface at all**, since a matched pattern is
  server-derived from a closed set. The surface survives only on rows emitted **before handler
  mapping** — the security-chain 401/403/429 and unmatched paths — where the raw URI is client bytes.
- `http.request.method` goes through a **known-method whitelist with an `OTHER` fallback**, since the
  method token is also client-supplied.

### §8 Session rows: the join, the ordering, and the row that cannot be produced

Ticket 08 rotates the session id at login, at factor grant, and at password change, so `session.hash`
changes at each. Dropping a row at authentication would leave **nothing linking the pre-auth failure
rows to the successful login** — `trace.id` is per-request, so there would be no join key.

**Resolution needs no new field.** Rotation happens *inside* the login request, so row 8 (pre-rotation
hash) and row 1 (post-rotation hash) **share one `trace.id`**, and the pre-rotation hash is the one
the failure rows already carry. Chain complete, zero schema cost. *(Amended by
[ticket 27](27-inbound-trace-context.md): "zero schema cost" is true because `trace.id` is a server-side fact.
Every inbound trace is restarted, so no caller can pin it. If continuation is ever re-enabled, this join needs a
new field.)* *Consolidated into the register (ticket 33): R-OBS-001. Amend the table by ID, not this list.*

**Ordering is load-bearing.** Ticket 08's composite is `ConcurrentSessionControl` → `ChangeSessionId`
→ `RegisterSession` → `AuthInstantStamping`. The audit strategy must sit **after
`ConcurrentSessionControl`** (so displacement is already decided and observable) and **before
`ChangeSessionId`** (so the row still sees the pre-rotation id). Placed last, both rows carry the
post-rotation hash and the join silently evaporates — no test fails, the chain just stops working.

**Displacement needs a named mechanism**, because `SessionInformation.expireNow()` returns nothing:
either decorate `ConcurrentSessionControlAuthenticationStrategy`, or have the audit strategy call
`SessionRegistry.getAllSessions(principal, true)` and emit row 10 per session now marked expired. The
second is preferred — no framework subclass. Ticket 08 accepted **lazy** eviction, so a row emitted on
*detection* might never be written at all; row 10 is emitted at **displacement time**.

**Idle expiry cannot be produced at expiry.** Verified against the Spring Session reference: the JDBC
module documents **no session event publication**, and expiry is a **cron clean-up job that bulk-
DELETEs expired rows** — no per-row identity, no hook. (Destroyed/expired events are documented for
other repositories, not this one.) The same reference documents that the job can be disabled and
replaced, so a custom reaper could select-then-delete and emit one row per expired session — meaning
idle expiry is **producible in principle and declined on scope**, because scheduled jobs are out of
scope on this map and a reaper would import the ShedLock questions the hygiene-jobs deferral avoided.
So idle expiry is observed **lazily at the next request** as row 11, keyed by the hash of the presented
cookie, and **cannot be distinguished from a deleted or forged session id**. Stated as the honest
limit: §3.3's three session endings reduce to two producible ones plus one ambiguous one. *Consolidated into the ADR routing (ticket 34): REJ-046. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-015. Amend the table by ID, not this list.*

### §9 The emitter: the catalogue compiles

**A closed `AuditEvent` enum holds the schema constants; one `emit(event, context)` writes the row.**
Deviation from the typed-module recipe's "one method per event type" — 40 rows would be 40
near-identical builder chains, each a place a mandatory field can be forgotten. Not a deviation from
any Enforced Constraint: the `"audit"` logger name, the dedicated appender and the underscore
`error_*` keys all survive.

Five constraints, four of which exist to stop the data-driven shape reopening a control the typed
shape held structurally:

1. **The context is never `Map<String, Object>`.** Per-family context **records** carrying only UUIDs,
   enums and primitives. The masking recipe names passing a domain object to `addKeyValue` as the
   single most common defect; typed methods prevented it structurally and a generic map would reopen
   it.
2. **Unknown keys are rejected, not just missing ones.** A required-field check still lets a call site
   smuggle `user.email` onto a row. The whitelist derived from the definition is what enforces the
   negative list; the required-field check only enforces completeness.
3. **`emit` exposes no throwable parameter.** This is §10's primary control, made structural rather
   than asserted: with no parameter, no call site can attach a cause to an audit row.
4. **Fail soft at runtime, hard at test time.** After §11's after-commit dispatch, throwing from
   `emit` can undo nothing and could turn a committed operation into a 500 — ticket 09's "never fail
   open on responding" applies. Emit the degraded row plus an ERROR alert; the parameterised test is
   what makes the degraded path unreachable.
5. **Two tests, not one.** A consistency test walks every enum member asserting Tier A/B presence and
   reason-family match. A **completeness** test maps every §3.3 required event to at least one member,
   including the three N/A entries as explicit negative assertions — that second test is what actually
   answers "did we log it?". The reason family is bound to each event by a **sealed `AuditReason`
   hierarchy** so a mismatch is a compile error, and reasons serialise via an explicit **`code()`, not
   `name()`**, because these values become saved-query targets and a rename would silently break every
   dashboard. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-007, T-AUD-014. Amend the table by ID, not this list.*

**The ASVS 16.1.1 inventory table is generated from the enum as a snapshot test**, so the catalogue
document cannot drift from the code — the failure mode every catalogue this size eventually has. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-017. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): ADR-055. Amend by ID, not this list.*

### §10 The negative list, and the leak no field assertion would have caught

`.setCause(e)` auto-populates `error.message` and `error.stack_trace`. So **any** exception whose
message happens to carry request content reaches the audit stream with **no call site naming it** — a
malformed credential body, a constraint violation, a validation message. That defeats "never log
passwords" through a path no field-level assertion inspects. It is the general form, and §9's
no-throwable-parameter rule closes all of it.

The Jackson-specific instance was investigated and **downgraded from the blocking finding it first
appeared to be**:

- [jackson-core #991](https://github.com/FasterXML/jackson-core/issues/991) is **the fix, not the
  report** — its title is "Change `StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` default to `false` in
  Jackson 2.16", and the [2.16 release notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.16/11d9aeb85fc7e8f105797dbc358c5e94123f4c35)
  record the change as an improved security default with less information leakage.
  [#1039](https://github.com/FasterXML/jackson-core/issues/1039) renamed the suppressed-source marker
  to `REDACTED`. Our floor is Boot 4.1 on Jackson 3.x, so source inclusion is off by default.
- [GHSA-wf8f-6423-gfxg / CVE-2025-49128](https://github.com/advisories/ghsa-wf8f-6423-gfxg) is a
  **different mechanism**: parsing from a byte array with an offset, reading from the array start and
  exposing unrelated memory, with pooled-buffer stacks named as the impact case. Our converter reads
  the servlet `InputStream` on Tomcat, so that path is not ours. Its affected-condition line describes
  the feature as enabled by default, which sits oddly against the 2.16 change and **could not be
  resolved from the sources**.
- Nor could it be confirmed from a primary source that **Jackson 3.0 carries the 2.16 default
  forward**. It is very likely; "very likely" is exactly why the pin earns its place. *Consolidated into the register (ticket 33): R-AUD-008, R-AUD-017. Amend the table by ID, not this list.*

So: **pin it, and assert the outcome rather than the flag.**
`JsonMapperBuilderCustomizer` calling `builder.disable(tools.jackson.core.StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)`
— Boot 4 uses an immutable `JsonMapper`, not a mutable `ObjectMapper`, and Jackson 3 packages are
`tools.jackson.*` except `jackson-annotations`. The test is a **real malformed credential-bearing
request** against the login endpoint asserting the resulting message contains `REDACTED` and does not
contain the submitted password. A flag assertion proves a mechanism is configured; this proves the
property, and it survives a Boot upgrade reshaping the builder API — a live risk, not a hypothetical
one, given ticket 03's existing non-public `StructuredLogEncoder` coupling. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-018. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-008. Amend the table by ID, not this list.*

`spring.jackson.use-jackson2-defaults` was checked as a candidate prohibited-configuration entry and
**is not one**: it aligns the auto-configured mapper with Jackson 2's defaults **as of Spring Boot 3**,
and Boot 3 shipped Jackson ≥ 2.16 where source inclusion is already off, so it cannot reach back past
the 2.16 change. Its documented effects are serialization-side. Recorded because it is the obvious
property an implementer would reach for, and its absence from the register would otherwise read as an
oversight. Caveat: the source says "align as closely as possible" rather than enumerating. *Consolidated into the register (ticket 33): R-CFG-018. Amend the table by ID, not this list.*

**The negative-assertion set**, every item a test:

| Assertion | How |
|---|---|
| No password, in any field, on any appender | Content assertion on the malformed-body request above |
| No token plaintext and **no token hash** (§3.4 bans both) | Content assertion across all appenders on the reset flow |
| No CSRF token, no raw session id | Content assertion; `session.hash` present instead |
| No TOTP secret, no `otpauthUri`, no ciphertext, no key material | Content assertion on rows 36–42 |
| No raw username, no email, no `user.name`, no `user.hash` | Key-absence across every row |
| No key material at any level; rejected config values never logged | Ticket 24's rule, asserted here |
| No throwable attached to an audit row | Structural — `emit` has no such parameter |
| Log injection (ASVS 16.4.1) | CRLF payload through a real request against an **unmatched** path, asserting the sanitised form appears — per §5's rule to assert the sanitised form, not the raw payload |
| Unbounded client text | Length cap on the raw-URI field; without it a scanner writes arbitrary attacker-chosen text into the one stream that is meant to be evidence |
| `user.id` absent on unresolved failures, present on resolved ones | Two paired tests; inverts the recipe's line 305 |
| `user.id` never leaks from MDC onto pre-auth rows | Key-absence on rows 2, 5, 6, 11, 16(reason `EXISTING_ADDRESS`), 17, 20 *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-019, T-AUD-020, T-AUD-021, T-AUD-008, T-AUD-022, T-AUD-016, T-AUD-023, T-AUD-024, T-AUD-009, T-AUD-025, T-AUD-026, T-AUD-018. Amend the table by ID, not this list.* |

The `[\r\n|]` strip lives at the single emitter, so there is one home for it rather than 40 — which is
an argument for §9's shape independent of the schema-drift one.

### §11 Emission timing

**After commit for every state-changing row**, so the log never asserts a state change that did not
happen — consistent with ticket 10's `@TransactionalEventListener(AFTER_COMMIT)` for notifications.
Three consequences named rather than left implicit:

- **Rollback rows cannot come from an `AFTER_COMMIT` listener.** Row 34 is emitted from
  `AFTER_ROLLBACK` for transaction failures and from the guard's own exception path for the
  self-action, two-admin and lock-timeout rejections, which never open a transaction that commits.
- **The listener stays synchronous**, or MDC (`trace.id`, `session.hash`, `user.id`) is lost and every
  row loses its correlation keys.
- **An audit-write failure after commit can no longer fail the operation**, so it needs its own
  catch-and-alert path. That is the same requirement as §3.4's "alert when audit logging fails", and
  the same requirement as §9's fail-soft rule: **one control with three halves** — the degraded row,
  the application-logger ERROR with `error_follow_up_action: true`, and a Logback `<statusListener>`
  for appender-level failures the application never sees. The crash window between commit and write is
  **unclosable** without the audit table the PRD declined; recorded. *Consolidated into the register (ticket 33): R-AUD-009. Amend the table by ID, not this list.*

### §12 Destination, retention, protection — and this catalogue as the ASVS inventory

**This catalogue *is* the ASVS 16.1.1 (L2) log inventory**, which asks for events, formats,
destination, usage, access control and retention. So the generated table owes **destination, retention
and who-can-read columns**, not just fields and levels.

- **stdout in every profile**, NDJSON one event per line — this is the Enforced Constraint and the
  platform ingestion path, and the only route to 16.4.3. An earlier draft of this ticket proposed
  dropping the console copy outside dev; that was wrong, and it contradicted a constraint this ticket
  had itself quoted. A file on ephemeral disk with no shipper is worse on durability, worse on 16.4.3
  and worse on the 90-day claim than the stream it would have replaced.
- **Dedicated rolling file appender** bound to the `"audit"` logger as the durable local buffer the
  standard requires, daily pattern, `max-history: 90`, **no `total-size-cap`** — the omission is
  deliberate and asserted by a test, because a cap here is a compliance control that fails silently by
  deleting the oldest archives while every config value still reads compliant. The trade is a
  disk-full path, which is itself the §3.4 audit-failure condition and is alerted by §11. *Consolidated into the ADR routing (ticket 34): REJ-045. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-010. Amend the table by ID, not this list.*
- **Console duplication is ASVS-satisfied by documentation**: 16.2.3 requires logs go only to
  destinations recorded in the inventory. It remains a **deviation from the org standard's
  separate-destination constraint**, whose stated purpose includes independent access control. *Consolidated into the ADR routing (ticket 34): ADR-056. Amend by ID, not this list.*
- **Retention is answered, not deferred by omission**: satisfied locally for 90 days; the platform TTL
  and disk monitoring are named integrator obligations owned by ticket 25. The asymmetry stands — an
  admin-unlock accountability record expires on a policy nobody here controls, while the tombstone it
  explains is retained indefinitely, and since ticket 12 added no unlock-reason column, **the event is *Consolidated into the register (ticket 33): R-AUD-022. Amend the table by ID, not this list.*
  the only record of why an account was unlocked**. *Consolidated into the register (ticket 33): R-AUD-011. Amend the table by ID, not this list.*
- **ASVS status, honestly graded.** V16 contains **no L1 requirement at all** — every requirement is L2
  or L3 — so none of this bears on the declared L1 claim. Against L2: 16.1.1 **S** (this catalogue),
  16.2.1–16.2.5 **S**, 16.3.1–16.3.4 **S**, 16.4.1 **S** (§10's test), **16.4.2 F** (a file the
  application owns and rewrites is neither tamper-proof nor access-controlled), **16.4.3 F** (no
  separate system exists; stdout is the route but nothing consumes it here). Both failures carry named
  deployer obligations rather than compensating controls, because there is no application-side control
  that would honestly close them. *Consolidated into the register (ticket 33): R-AUD-012, R-AUD-013. Amend the table by ID, not this list.*
- **No file-permission assertion from the application.** It would pass on the one platform we run and
  mean nothing on the platform that matters — ticket 11's inert `ImmutableSecurityHandler` again. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-011. Amend the table by ID, not this list.*

### §13 The reset-link leak: contained, and the containment made checkable

The negative list is **absolute for the audit stream** — no token, no hash, ever. The PRD-mandated
stub's link goes to a **separate non-audit application logger that emits only under the `dev`
profile**. Ticket 10's finding stands: with the link in the log the leak is total for ordinary users
and partial for admins, contained only by TOTP, confidentiality-only. What changes is that the
containment becomes enforceable: *Consolidated into the register (ticket 33): R-CRED-020. Amend the table by ID, not this list.*

1. **A prohibited-configuration entry** in ticket 24's refresh-phase validator for the logger's
   property being set outside `dev`.
2. **An `ApplicationReadyEvent` check** reading the effective level through Boot's
   `LoggingSystem.getLoggerConfiguration(...)` — **not** Logback's `LoggerContext`, since ticket 03
   already carries one non-public coupling that must be retested on every Boot upgrade and there is no
   reason to add a second. This catches the path the validator structurally cannot see:
   `LOGGING_LEVEL_…=DEBUG` binds to no `@ConfigurationProperties` class and passes every validator we
   have.
3. **`/actuator/loggers` absent or read-only outside `dev`.** A runtime level change defeats both
   checks, and ticket 03 already recorded the standard's warning that runtime level changes can
   suppress required audit events — the same lever works in the other direction here.
4. **Row 43 records which audit-relevant loggers were actually in force**, folded into the mandated
   startup event rather than added as a second row. It is evidence, not a control; items 1–3 are the
   controls. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-009, T-CFG-010, T-CFG-011. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): ADR-057. Amend by ID, not this list.*

### §14 Premises this ticket found wrong in its own inputs

- **`source.ip.hash`** (ticket 09 §12, carried by ticket 24) would have been rejected at ingest. Fixed
  to `source.ip_hash`.
- **Ticket 09's 50-in-24h alert was not computable** from the stream ticket 09 specified — and it is
  the only compensating control for a declined `SHALL`.
- **Ticket 03's C1 and C3 both reverse** under the precedence rule in §0.
- **Ticket 03's C4 is too narrow**: §3.3 requires path and method on *every* audit event, not just
  admin ones.
- **`user.target.id` and `user.target.roles` are valid ECS**, so two of the five fields this ticket
  inherited are not extensions at all.
- **`unlockReason`** is camelCase against both ECS and the corpus. Renamed.
- **Ticket 09's "progressive backoff and lockout are alternatives, not additions"** contradicts NIST's
  own text. *Consolidated into the register (ticket 33): R-LCK-006. Amend the table by ID, not this list.*
- **§3.3's "unlocked" transition and "security control bypass attempts" had no producer** anywhere on
  this map — rows 4 and 13.
- **§3.4's "failed administrative attempts" had no rows** — row 34.
- **This ticket's own first draft was wrong twice**: it proposed `url.path` as the primary
  discriminator when the standard externalises the base path, and it proposed dropping the console
  copy outside dev, which contradicts an Enforced Constraint it had quoted itself.

### Counts

**Nine ADRs owed**: the `user.id`-on-resolved-failure deviation (four deviation points); WARN not ERROR
for lockout (reversing C3); `source.ip_hash` and no cleartext IP (replacing C1); the three-field
schema-amendment package with no declaration process; the data-driven emitter deviating from
one-method-per-event; `no total-size-cap` on the audit appender; console duplication against the
separate-destination constraint; the dev-only reset-link logger; idle expiry observed lazily and
indistinguishable.

**Five glossary terms**: audit row, discriminator, reason family, identity-absent failure ratio,
pre-handler row.

**Eleven register entries**: ASVS 16.4.2 **F** and 16.4.3 **F** with deployer obligations; the *Consolidated into the register (ticket 33): R-AUD-012, R-AUD-013. Amend the table by ID, not this list.*
platform TTL obligation; disk-full monitoring; the three-field amendment request; the `user.target.*` *Consolidated into the register (ticket 33): R-AUD-002, R-AUD-010, R-AUD-011. Amend the table by ID, not this list.*
encoder inference to verify empirically; the Jackson pin that cannot join ticket 24's validator; *Consolidated into the register (ticket 33): R-AUD-008. Amend the table by ID, not this list.*
`spring.jackson.use-jackson2-defaults` checked and excluded; NIST §3.2.2's corrected framing; *Consolidated into the register (ticket 33): R-CFG-018. Amend the table by ID, not this list.*
progressive delay as an unadopted NIST addition; the crash window between commit and audit write. *Consolidated into the register (ticket 33): R-AUD-009, R-LCK-006. Amend the table by ID, not this list.*

**Four reopening triggers**: any log-management platform entering scope (16.4.2/16.4.3 close, retention
moves); any scheduled job entering scope (idle-expiry rows become producible); `api.base-path` being *Consolidated into the register (ticket 33): R-AUD-014. Amend the table by ID, not this list.*
fixed rather than externalised (the pattern-versus-path argument collapses); Jackson re-enabling source *Consolidated into the register (ticket 33): R-AUD-015, R-AUD-016. Amend the table by ID, not this list.*
inclusion by default (the pin becomes load-bearing rather than defence in depth). *Consolidated into the register (ticket 33): R-AUD-017. Amend the table by ID, not this list.*

**Amendments owed to seven tickets**: 03, 06, 08, 09, 21, 24, 25.

**Status: resolved.**

---

## Amendment from ticket 21 (observability signals): rows 5 and 6 emit once per window transition

**As catalogued, rows 5 and 6 are a log-amplification path to disk exhaustion.** Rows 3 and 40 carry an
explicit "once per transition" annotation. Rows 5 and 6 — per-IP throttle breach and per-account throttle
breach — carry none, so the default reading is **one audit row per rejected request**. That inverts what the
rate limiter is for: ticket 09's limiter bounds the *work* a request causes, not the *logging* a rejection
causes, so an attacker sending 10,000 requests a minute against a 60/min bucket produces ~9,940 audit rows a
minute. Volume is then set by the attacker's request rate, not by the limiter.

Three reasons this is not merely untidy:

1. It is aimed at the **audit file**, whose failure is §3.4's named audit-failure condition, in a design that
   deliberately declined `total-size-cap` (§ above) so that nothing bounds the file's growth.
2. Ticket 21 adopts `DiskSpaceHealthIndicator` as the only in-process detector of that failure — so the
   detector would exist to catch an attack the audit logger itself enables.
3. It is the shape ticket 09 spent §2 and §5 defending against on the request path
   ("small-request/expensive-work amplifier"), reappearing on the logging path where nobody looked.

**Amendment: rows 5 and 6 emit once per bucket-exhaustion transition, not once per rejection** — row 3's and
row 40's existing idiom, applied to the two rows that were missed. An amendment rather than a reopening under
this ticket's own rule: it weakens no compensating control, and the reason vocabularies, fields and identity
rules are all unchanged.

**Transition-keying alone leaves the per-IP axis unbounded, so it is not the whole fix.** A first draft of this
amendment claimed ticket 09's `maximumSize(10_000)` supplied the ceiling. It does not: `maximumSize` caps
memory, and eviction does not suppress a breach row — it only means an evicted source must spend another
budget's worth of requests to breach again, which is already counted. Transitions per window are bounded by
`min(distinct sources, requests_in_window / per_IP_budget)`, and distinct sources is attacker-chosen. So
transition-keying buys a factor of the per-IP budget and nothing more; **the per-IP audit-volume axis has no
application-side ceiling**, only ingress rate divided by the budget. The per-account axis is bounded by the
user population.

**Second amendment, which supplies the ceiling: cap distinct-source breach rows per window, and record the
truncation.** Emit per-source breach rows for up to **N** distinct sources in a window; on the N+1th distinct
source, emit **one aggregate row** carrying the distinct-source count and a truncation flag, and nothing
further that window. Row **46**, `access-control` / `["denied"]`, WARN, severity **high**, no identity, reason
`RATE_LIMITED_SOURCE_TRUNCATED`, extra field `source.distinct_count`.

Three things this buys:

- **A ceiling the application owns**: `N × windows/day`, which is the bounded input ticket 21's disk
  arithmetic needs and could not otherwise have.
- **This ticket's own objection answered at the right layer.** The loss is recorded in the stream rather than
  performed silently by a file deleter — which is precisely why `total-size-cap` was declined, and it is worth
  noting that decline now rests on a second reason as well: `totalSizeCap`'s arithmetic is reported
  inconsistently in the field (per-period rather than total in some reports, wholesale archive deletion at
  large values in others), so it is unreliable *and* silent, and what it removes is audit records. *Consolidated into the ADR routing (ticket 34): REJ-045. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-010. Amend the table by ID, not this list.*
- **Commitment 2 stays computable** for realistic traffic, because truncation engages only at volumes that
  would have exhausted the disk anyway — and the aggregate row is itself a strong enumeration-campaign signal,
  arguably stronger than the per-source rows it replaces.

Precedents, both on this map: ticket 11's `user.target.count` records a count instead of a set, and this
ticket's degraded row fails soft while recording the degradation. One new parameter, no new mechanism.

Row 46 is **per-event** in ticket 21's taxonomy, not rate-above: reaching it means an attack large enough to
threaten the audit file, and clearing it requires someone to act.

Consequence for ticket 21's Q9 taxonomy: this keeps rows 5 and 6 in the **rate-above** class, where they were
placed, and makes the placement correct rather than coincidental — a transition-keyed row is a state change,
and the discriminator ("does clearing this state require action outside the normal flow?") answers *no*,
because a bucket refills itself.

---

## Amendment from ticket 09 (§R, the ticket 21 reopening) — three rows, one correction to your own argument, and an assertion

### 1. Your `grace-expired` reason is in the wrong argument

Your "the enumeration protection was already spent" reasoning lists the reasons ticket 06 routes to the audit log:
*wrong password, unknown user, locked, disabled, **grace-expired***. The first four leak **account state**.
`grace-expired` leaks **password correctness**, because ticket 11's 30-day expiry is implemented on
`isCredentialsNonExpired()`, which Spring evaluates in `DefaultPostAuthenticationChecks` — reached only when the
password already matched. So that reason is emitted **if and only if the guess was right**, and it was swept into an
enumeration argument where it does not belong. Verified at
[§18 of the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md);
filed as a reopen trigger on ticket 11, not on you. Your decision to put `user.id` on resolved failure rows is
unaffected and correct — it is the *pairing* of that UUID with this particular reason that is the oracle. *Consolidated into the register (ticket 33): R-AUD-018. Amend the table by ID, not this list.*

**Rule for the catalogue, so a future row does not repeat it:** before adding a reason to a failure row, ask
whether the refusal producing it can fire on a *wrong* password. If it cannot, the reason confirms the credential
and belongs nowhere near an identity-bearing row. *Consolidated into the register (ticket 33): R-AUD-018. Amend the table by ID, not this list.*

### 2. Three rows

- **Cap threshold crossing** (ticket 21 pre-allocated this as row 45) — once per transition off
  `consecutive_failures_since_success` at **50**, per-event alert class, identity present.
- **Authenticator disable** at **100** — ERROR / critical, mirroring your factor tier-2 disable row, with a reason
  distinguishing the automatic trip from an operator action. **Emitted at the pre-authentication checker, not from
  a listener**: ticket 09 §R.4's refusal throws a *custom* `AuthenticationException`, and
  `DefaultAuthenticationEventPublisher` resolves by exact class name with a null default, so **no event is
  published at all** and a listener-based emitter would silently never fire.
- **Cardinality-axis rate-limit breach** — the third limiter axis in ticket 09 §5, keyed on source. Distinct from
  your per-IP and per-account 429 rows, transition-keyed like them.

Your lockout-cleared row gains no new reason: the cap is cleared by **rebinding**, which is redemption, and
`RESET_REDEMPTION` already exists in that enum.

### 3. An assertion your negative list must carry

**The operator rebinding runner prints its token to `System.out` and never through the logging system.** Routed
through a logger it lands in whatever appender the profile configures — a fourth door into exactly what your §13
spent three controls closing. Your negative list is absolute for the audit stream; this extends it to a
non-audit path that produces a plaintext credential. Ticket 24 carries the same thing as a prohibited-configuration
entry, deliberately in both places.

### 4. One dependency that makes `source.ip_hash` real rather than nominal

Your failed-login and breach rows are written from an event listener with **no `HttpServletRequest`**, so the only
in-band carrier of the client IP is `Authentication.getDetails()` — and under ticket 05's Route C converter that is
`null` by default, because `authenticationDetailsSource` is read only by subclasses that build their own token.
**The converter must set `WebAuthenticationDetails`, asserted.** Unset, every row carries one key and still looks
correct, because a hash is present. See §20 of the verification asset. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-005. Amend the table by ID, not this list.*

---

## Amendment from ticket 25 (operational handover document)

**ASVS 16.3.3 (L2) has a second limb this catalogue does not satisfy, and it costs two event families — not a
re-architecture of the forty rows.**

Verbatim, the full requirement: "Verify that the application logs the security events that are defined in the
documentation" / "and also logs attempts to bypass the security controls, such as input validation, business
logic, and anti-automation." This catalogue grades 16.3.1–16.3.4 **S**; the first limb is genuinely satisfied —
the generated-from-enum inventory *is* the documentation-to-code reconciliation it asks for — but the second limb
was never assessed. *Consolidated into the register (ticket 33): R-AUD-019. Amend the table by ID, not this list.*

**Sized deliberately, because the obvious reading inflates it.** Three categories are named and the enumeration
is "such as", so it is illustrative rather than closed:

- **Anti-automation collapses to zero.** Ticket 09 declined CAPTCHA and progressive backoff, so rate limiting and
  lockout are our *only* anti-automation controls, and a throttle trigger or lockout transition **is** the
  detection point for an attempt to push past them. Those rows exist, and with §16.2.1's when/where/who/what
  metadata they satisfy this category as written. It would reopen only if a CAPTCHA, proof-of-work or bot-scoring
  control ever entered scope. *Consolidated into the register (ticket 33): R-AUD-020. Amend the table by ID, not this list.*
- **Authorization is not one of the three**, so the tempting argument that 16.3.2 absorbs part of this limb is
  wrong — 16.3.2 covers failed authorization attempts and subtracts nothing here. It also adds nothing.
- **16.3.4 does not absorb the other two.** Its scope is "unexpected errors and security control failures such as
  backend TLS failures" — the control *malfunctioning*. A rejected input is the control working correctly, which
  is an expected outcome and outside 16.3.4's wording.

**So: two new event families.** Input-validation rejections (server-side schema, type, length, canonicalisation
and deserialisation rejects, emitted as security events rather than only as a 400) and business-logic rule
violations (state and sequence violations, quota breaches, tampered-workflow detections). Both fold into the
existing `event.action` closed enum and the existing severity column rather than needing new fields.

One sizing note rather than a scope expansion: the open "such as" means rejections from other enforcement points
already present — CSRF and origin checks, integrity verification — sit inside the general class too, and are
cheapest folded into the same taxonomy at the point the two families are added rather than later.

**No level consequence.** V16 contains no L1 requirement at all, so this does not bear on the declared ASVS L1
claim — but it is an L2 gap under a verdict currently recorded **S**, which is the kind of thing ticket 18 finds. *Consolidated into the register (ticket 33): R-AUD-019. Amend the table by ID, not this list.*

---

## Amendment from ticket 28 — three runner rows, two keys, and one citation

**1. Citation.** Tickets 25 and 28 cite your stdout topology as "§13". It is **§12**. Both are corrected. Nothing
here changes.

**2. The ticket 09 amendment's `System.out` assertion is retired.** [Ticket 28](28-out-of-band-privileged-channels.md)
inverted the channel: the runner reads the new password from stdin and **emits no secret at all**. The negative
list's runner entry becomes a content assertion of the same shape as your §10 table: *run the runner with a known
password; that string appears in none of stdout, stderr or the audit file.* *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-027. Amend the table by ID, not this list.*

**3. Three rows, all under `user-administration`:**
- **Rebind dry-run**: INFO, non-state-changing.
- **Rebind intent**: emitted before the transaction with `event.outcome: unknown`. It asserts no state change, so
  it stays inside your §11 after-commit rule. It carries the resolved absolute database path and working
  directory in plaintext.
- **Rebind outcome**: emitted after commit or rollback. A batch produces **one** outcome row with
  `user.target.count` and the input digest, not one row per account.

All three carry the confirm digest, the target count, pre- and post-operation enrolled-admin counts, a mandatory
reason (encoded under 16.4.1, capped at 256 characters), and the two identity keys below. **No
`process.command_line` or `process.args`, ever**, since a secret mistakenly passed as an argument would be copied
in.

**4. Two keys, no new custom field.**
- **`labels.operator_claimed_id`**: ECS's own recommended shape for extra keyword values. Your rejection of
  `labels` was for the numeric `count` and does not transfer to a keyword.
- **`process.real_user.name`**: an ECS reuse of `user`. There is an owed build check on which uid
  `ProcessHandle.Info.user()` reports. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RUN-002, T-RUN-001. Amend the table by ID, not this list.*

The custom-field count **stays at three**. Your §10 key-absence assertion (`user.name`, `user.hash`) is *Consolidated into the register (ticket 33): R-AUD-002. Amend the table by ID, not this list.*
**untouched and needs no exception**: neither key is in `user.*`, and the value is not a username.

**"No fourth field" does not mean "nothing to document".** ASVS 16.1.1's inventory is this catalogue, so both keys
and all three rows are **new inventory rows**. An unlisted key is an undocumented field.

**5. Destination.** The runner's stdout, meaning an operator terminal, a journal, or a Job log, joins the
documented destinations for **16.2.3 (L2)**. 16.4.3 stays F, unchanged. The rows themselves go through your *Consolidated into the register (ticket 33): R-AUD-013. Amend the table by ID, not this list.*
rolling file appender, attached only after the runner's preconditions pass, and they reach the collector after
restart. *Consolidated into the register (ticket 33): R-AUD-034. Amend the table by ID, not this list.*

---

## Amendment from ticket 29 (anonymous session-row growth)

- **Row 5 gains reason `DISK_RESERVE_SHED`.** It is emitted once when a shed episode starts, with no identity and
  `url.path` set to `/api/csrf`. It sits in the **per-event** alert class, split by (row, reason) on row 34's
  precedent, and the other row 5 reasons stay rate-above.
- **New row 47, "Anonymous session shedding cleared".** `access-control` / `["change"]`, INFO / low, no identity,
  and it carries the episode's duration.
- **Bound:** at most one episode per 60 s, from the minimum dwell. That is at most 2 rows per minute, about 2,880
  per day. It needs none of ticket 26's tier machinery, because the key space is one.
- **A restart ends an episode with no row 47,** so a trail showing start, start is expected.

## Amendment from ticket 31 (IPv6 source keying)

- **`source.ip_hash` input is pinned:** `HMAC-SHA-256(app.security.hmac.log.key, "ip:" || source_key)`, where
  `source_key` is `4:<8 hex>` or `6:<32 hex>/<n>` ([ticket 31](31-ipv6-source-keying.md) §§1, 5). It hashes the
  **source key, not the address**, so row 46's distinct-source count and the enumeration signature (`13:374`) count
  what the limiters count.
- IPv4 hashes change from the dotted-quad input. Changing `ipv6-prefix-length` breaks IPv6 correlation across the
  change, as a key rotation does, and the startup log line is the only marker. *Consolidated into the ADR routing (ticket 34): ADR-054 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-AUD-021. Amend the table by ID, not this list.*
- No log of ours can distinguish hosts inside one /64. This is recorded as a residual. *Consolidated into the register (ticket 33): R-AUD-021. Amend the table by ID, not this list.*
- An unparseable client-IP token hashes the constant key `unparseable`. The raw token appears only escaped and
  truncated in its WARN or ERROR line, never in `source.ip_hash`.
