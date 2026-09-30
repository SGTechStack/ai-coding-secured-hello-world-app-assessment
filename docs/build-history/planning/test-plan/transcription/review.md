# Review of docs/test-plan/test-plan.md (349 rows)

Scope: residual duplicates, rows contradicting later decisions, schema violations, envelope codes outside ticket 06's
closed enum. Governing rules: 16 Answer (16:464–). Table line numbers are 1-indexed lines of `docs/test-plan/test-plan.md`
(`TP:n`); ticket references are `NN:line` under `.scratch/secured-hello-world/issues/`.

Checked mechanically across all 349 rows: level/context pairing (U→none, A→archunit, F→vitest, E→playwright,
B→build, R→runner, C/P→Spring context), isolation vocabulary, `restart`→`own-DB`, polarity vocabulary, ASVS ids
carrying a level (all do), and P-only assertions (raw `Set-Cookie`, `RemoteIpValve`, Tomcat meters, duplicate
cookies, 8 KB budget, `Host`/`X-Forwarded-*`) at a level other than P (none found). A pairwise text-similarity pass
surfaced only deliberate facet splits (e.g. T-CRED-016/017, T-RL-013/014, T-CFG-012/013, the replay family).

## 1. Residual duplicates

| IDs | problem | evidence (file:line) | proposed fix |
|---|---|---|---|
| T-AUD-004, T-AUD-026 | Same control (`user.id` must not leak from MDC onto a later/pre-auth request's lines) with substantially the same observable: T-AUD-004 asserts "the lines of a following anonymous request carry neither" `user.id` nor `session.hash`; T-AUD-026 asserts key-absence of `user.id` on the pre-auth rows (2 unresolved, 5, 6, 11, 16, 17, 20), which are exactly the rows an anonymous follow-on request emits. Medium confidence: T-AUD-004 additionally inspects thread MDC state and `session.hash`. | TP:235; TP:257; 03:277 (MdcUserFilter, "Clear `user.id` in a `finally` block"); 13:567 ("`user.id` never leaks from MDC onto pre-auth rows") | Keep T-AUD-026 (ticket 13 owns the per-row `user.id` decision, 06 amendment from 13). Fold T-AUD-004's thread-MDC and `session.hash` clauses into it and add LOG §5:354 / 03:277 / 03:281 to its clause/source; retire T-AUD-004. If the standard's §5:354 test must keep its own ID, reword T-AUD-004 to the thread-state assertion only and drop its "following anonymous request" clause. |

No other residual duplicate found. Pairs examined and left as distinct because a source frames them separately:
T-AUD-013 vs T-AUD-019/020/021 (13:558–560 list each as a test), T-MFA-010 vs T-MFA-011 (MFA §5:394 and §5:395),
T-SES-032 vs T-SES-035 (29 §9 tests 4 and 8), T-RL-019 vs T-RL-013/014 (base case vs 09:1625–1628 facets),
T-RL-016 vs T-RL-023 (26 constants table vs 26 §11 test 8), T-SES-002 vs T-SES-011 (20 §3 two rows).

## 2. Rows contradicting a later decision

| IDs | problem | evidence (file:line) | proposed fix |
|---|---|---|---|
| T-AUD-013 | Registers "rebind tokens" as a server-generated canary captured from responses. Ticket 28 removed the runner token: the operator supplies the password on stdin and nothing secret is emitted; batch mints nothing. There is no rebind token to capture. The stale phrase is inherited from 16 Answer §5 itself. | TP:244 ("session id, CSRF, reset, activation, rebind tokens"); 28:205 ("**No secret crosses any stream**"); 28:212 ("**Batch run: mints nothing and emits nothing secret.**"); 16:554 | Drop "rebind tokens" from the registered set. Keep runner output as a scanned sink; the runner's stdin password is covered as a fixed client-supplied canary (T-AUD-027). Flag 16:554 for an erratum so the source and table agree. |
| T-AUTH-004, T-SES-005, T-CSRF-003, T-CSRF-002 | Paths use ticket 05's generic `/login` and `/csrf`. Later tickets fixed the API paths as `POST /api/login` and `GET /api/csrf` (exhaustive exempt list), and the rest of the table uses `/api/csrf` (T-CSRF-006, T-SES-026). A test written to `/login` hits an unmatched path (terminal `denyAll`/401) and proves nothing about Route C. | TP:48, TP:66, TP:99 ("POST `/login`"); TP:98 ("GET `/csrf`"); TP:102 ("GET /api/csrf"); 05:180, 05:1040 (source wording); 11:354 ("**Exempt list, exhaustive:** `POST /api/login`, `GET /api/csrf`, …") | Rewrite to `POST /api/login` in T-AUTH-004, T-SES-005, T-CSRF-003 and `GET /api/csrf` in T-CSRF-002. |
| T-CFG-009 | Asserts that ticket 24's refresh-phase validator refuses the reset-link logger property outside `dev`. Ticket 24 §9, which consolidated every bespoke startup check into the one validator, lists nine entries and this is not one of them; 24 later mentions only the `LOGGING_LEVEL_…` path (T-CFG-010). The conflict was flagged for decision during merge but the row was emitted as if settled. Low confidence that 24 intended to drop it. | TP:289; 13:632–633 ("A prohibited-configuration entry in ticket 24's refresh-phase validator for the logger's property being set outside `dev`"); 24:531–544 (entry table, no reset-link row); 24:912–915; 25:329–333; work/32/MERGE-SPEC.md:91 ("25:329 vs 24 §9 reset-link logger contradiction (decide what is tested)") | Keep T-CFG-009 (13 and 25 both require it) and record an amendment to 24 §9 adding the entry with 13:632 as its reason, so the validator the row tests is the one 24 specifies. |

Checked with no conflict found: SameSite (every row asserts `Strict`; T-SES-002 carries the Lax-deviation note), no
generic admin update endpoint and no `USERNAME_CHANGE_NOT_ALLOWED`/`BATCH_TOO_LARGE` (absent from the table), TM-13
(T-CRED-023 is structural with no pinned status, per 16:588–591), runner rows T-RUN-001…013 and T-AUD-027/029
(consistent with 28 §3–§8), 16 §9 supersessions (28 test 12 ↔ 24:1068–1070 merged into T-CFG-012…014; 15 row 4 as
restated by 27 in T-OBS-002…004/T-AUD-028), logout 403 on a dead session (T-CSRF-005 vs 06:252), T-AUD-011's
`max-history: 90` (in-app half per 13:602; the 16 §6(b) "not built" item is platform retention).

## 3. Schema violations

| IDs | problem | evidence (file:line) | proposed fix |
|---|---|---|---|
| T-SES-011 | Context `ctx-nondev` ("prod-only values") cannot produce the row's dev-profile half ("under the dev profile the name is SESSION and Secure is false"). One row spans two fixed contexts but names one. | TP:72; 16:511–517 (fixed contexts; 16:515 `ctx-nondev`: prod-only values); 20:173–174 ("a profile-scoped test asserting the name") | Keep one ID (20 frames it as one test) but set context to `restart` (two `SpringApplicationBuilder` runs on a real port, one per profile), or move the dev-profile half into T-SES-002 (`ctx-port`, dev) and leave T-SES-011 non-dev only. |
| T-AUTH-012 | Level F / `vitest`, but the assertion also validates "the backend envelope tests' response bodies", which cannot run in Vitest. 16 says the fixtures are validated "under their own T-ID". | TP:56; 06:484 ("validates both the backend's envelope tests and the frontend's MSW fixtures"); 16:605 ("Fixtures are validated against the backend enum schema under their own T-ID") | Narrow T-AUTH-012 to the MSW-fixture half (F). Cover the backend half by a C-level row (new ID, or add the schema check to T-AUTH-008/T-AUTH-011's assertions and cite 06:484). |
| T-CFG-023 | Level U / context `none`, but the assertion is "context refresh fails before the port binds", which a unit test with no web server cannot observe. Rows asserting the same ordering use C/`restart` (T-ADM-011, T-ADM-018, T-ADM-023). | TP:303; TP:191, TP:198, TP:203; 16:503–504 (level definitions) | Either drop "before the port binds" (keep U with `ApplicationContextRunner`) or move to C/`restart`/`own-DB`. |
| T-RUN-004 | Polarity `neg`, but the assertion is a presence check ("The runner's context contains ticket 24's refresh-phase validator bean"). The same assertion shape in T-CFG-033 is `pos`. | TP:321; TP:313; legend TP:41 ("`neg` asserts a refusal, an absence or a fail-closed outcome") | Set polarity to `pos`, or reword to the fail-closed form (a runner started without full refresh is refused) if that is what 28 §6 check 1 intends. |
| T-AUTH-015 | Clause cites "06 §406", which is not a section of ticket 06 (it has §1–§15 plus amendments). Line 406 is inside 06's amendment from 23, §2 (the entry-point matcher). | TP:59; 06:403–412 | Clause → "06 amendment from 23 §2; 14 §2". |

## 4. Suspect error codes

Closed enum per 06 §2 with struck rows removed (table at 06:131–146): `AUTHENTICATION_FAILED`, `PASSWORD_CHANGE_REQUIRED`,
`CSRF_TOKEN_INVALID`, `ACCESS_DENIED`, `VALIDATION_FAILED`, `PASSWORD_REJECTED`, `RESET_TOKEN_INVALID`,
`USER_EXISTS`, `TOO_MANY_REQUESTS`, `MISSING_FACTOR`, `INVALID_FACTOR`, `FACTOR_ENROLMENT_REQUIRED`,
`INTERNAL_ERROR`. Audit reasons in the table (`CSRF_MISSING`, `DUPLICATE_SESSION_COOKIE`, `UNKNOWN_OR_EXPIRED`,
`RATE_LIMITED_SOURCE_MISSES`, `DISK_RESERVE_SHED`) and `PASSWORD_REJECTED` rule names are not envelope codes and were
excluded.

| IDs | problem | evidence (file:line) | proposed fix |
|---|---|---|---|
| T-MFA-021, T-FE-001 | Code `FACTOR_DISABLED` (423) is not in 06 §2. It was decided by ticket 14, which lists the amendment to 06 as owed, but ticket 06 carries no amendment from 14 (its amendments are from 11, 10, 23, 13, 16). The rows follow the decision; the enum source is stale. | TP:230 ("gets 423 FACTOR_DISABLED"); TP:348 ("423 `FACTOR_DISABLED`"); 14:314 ("Tier 2 — `423 FACTOR_DISABLED` … taking the enum to 15 rows / 16 identifiers"); 14:637 ("Ticket 06 — `FACTOR_DISABLED` at 423"); 06:131–146 | Keep the rows. Apply ticket 14's owed amendment to 06 §2 (add the 423 row). Until then T-AUTH-012's schema, generated from the enum, will reject these fixtures. |
| T-MFA-019, T-FE-016 | Code `FACTOR_ALREADY_ENROLLED` (409) is not in the 06 §2 table, although 06's own amendment from 23 adds it. 06's amendment from 16 says the table "was edited in place", but this row was never added. | TP:228 ("gives 409 FACTOR_ALREADY_ENROLLED"); TP:363; 06:396 ("**1. The enum goes to 14: `FACTOR_ALREADY_ENROLLED` at `409`.**"); 06:131–146 | Keep the rows. Add the 409 row to 06 §2 so the enum table and T-AUTH-012's generated schema agree. |

## Counts

- Residual duplicates: 1
- Contradictions with a later decision: 3
- Schema violations: 5
- Suspect error codes: 2 (4 rows)
