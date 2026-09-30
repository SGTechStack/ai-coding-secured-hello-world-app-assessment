## Lists

| list | file | heading or first words | first line | last line | n tests | keys |
|---|---|---|---|---|---|---|
| L01-a | 01 | "Ticket 16's test plan gets a free specification." (amended in place by 16) | 144 | 144 | 6 | S01-01, S01-02, S01-03, S01-04, S01-05, S01-06 |
| L01-b | 01 | Constraint index row "16 Test plan: Assert `im8-review`'s PASS conditions as tests" (amended in place by 16) | 306 | 306 | 7 | S01-01, S01-02, S01-03, S01-04, S01-05, S01-06, S01-07 |
| L01-c | 01 | "## Amendment from ticket 16 (test plan)" | 318 | 324 | 2 | S01-04, S01-05 |
| L02-a | 02 | §3 "Flagged to: … 16 (test plan) for the observation-window and 429-reachability tests" | 197 | 198 | 2 | S02-01, S01-05 |
| L02-b | 02 | §5 "Flagged to: … 16 (test plan: a >72-byte password must be rejected by *our* validator" | 341 | 343 | 1 | S02-02 |
| L02-c | 02 | §6 "Flagged to: … 16 (cookie attribute assertions)" | 394 | 396 | 1 | S02-03 |
| L03-a | 03 | §6 "Testing: attach a Logback `ListAppender` to the `"audit"` logger … Feeds ticket 16" | 394 | 394 | 3 | S03-01, S03-02, S03-03 |
| L03-b | 03 | C8 "Encoder check … Verify this empirically on first implementation" | 467 | 467 | 1 | S03-04 |
| L03-c | 03 | Amendment from 13: "remains an inference and is now a named first-implementation test" | 618 | 619 | 1 | S03-04 |
| L03-d | 03 | Amendment from 13: "Retention is answered, not deferred … the omission being deliberate and asserted" | 635 | 638 | 1 | S03-05 |
| L03-e | 03 | Amendment from 27: "Baggage is off … The test is behavioural:" | 691 | 696 | 1 | S03-06 |
| L04-a | 04 | "11 admin module … Inherited as a ready-made acceptance list from the admin recipe §5 and Standard §2 Failure Paths 12–16" | 223 | 232 | 14 | S04-01 … S04-13 (13 rows; batch cap not transcribed) |
| L04-b | 04 | "16 test plan — inherits a ready-made checklist." | 298 | 306 | 15 | S04-14 … S04-26, S01-04 (14 tests; log-absence method not transcribed) |
| L04-c | 04 | Addendum "Net: … add it to ticket 16's test plan as a check against the SPA host" | 523 | 525 | 1 | S04-27 |

## Rows

| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| S01-01 | HDR | CSP header present on API responses | API responses carry a `Content-Security-Policy` header; its `script-src` (or fallback `default-src`) contains no `unsafe-inline` or `unsafe-eval` and no wildcard source. | C | ctx-default | none | IM8 as-9; Std §5:498 | 01:144; 01:306 | pos | ≡? 05 | Presence is the 01 wording; the no-unsafe/no-wildcard facet is as-9's FAIL condition (01 as-9 row). API-side CSP is defence in depth (04 addendum); SPA-origin CSP is S04-27. |
| S01-02 | HDR | HSTS max-age | A secure request's response carries `Strict-Transport-Security` with `max-age` at least 31536000; a non-secure request's response carries none. | C | ctx-default | none | IM8 as-10; Std §5:498 | 01:144; 01:306 | pos | ≡? 05:754 | Secure-only half applied per 16 §5 (§5:498, HSTS on secure requests only). |
| S01-03 | SES | Session idle timeout value | Binding test: the effective session idle timeout equals the configured property and is no more than 15 minutes. | C | ctx-nondev | none | IM8 as-11 | 01:144; 01:306 | pos | ≡? 08 | Production-value binding, so ctx-nondev; asserted off configuration, never a hard-coded number. |
| S01-04 | RL | Per-IP login 429 at budget N+1 | From one fresh source key, login attempt N+1 of the per-IP budget (N read from effective configuration) returns 429 with `Retry-After`; attempts 1..N do not. | P | ctx-port | keyed | IM8 as-4; 09 §R | 01:144; 01:306; 01:321; 04:301 | pos | ≡? 09 | Supersession applied (16 §9 "01's 6th attempt: amended"): replaces "429 on the 6th attempt". 04:301 "high-frequency → 429" folded in here. P because source keying runs through RemoteIpValve. |
| S01-05 | RL | Per-account login 429 on mixed traffic | Mixed traffic against one account (failures interleaved so the lockout threshold is never reached) hits the per-account budget and receives 429 with `Retry-After`, proving the per-account limiter is reachable. | P | ctx-port | keyed | IM8 as-4; 09 §R; 02 §3 | 01:144; 01:306; 01:322; 02:198 | pos | ≡? 09 | Supersession applied (16 §9, 01 amended). 02:198 "429-reachability" test is this one: 02 found the per-account 429 unreachable under 5-then-lock; 09's design makes it reachable on mixed traffic. |
| S01-06 | AUTH | Generic error body | Error responses (4xx and 5xx, including a forced server exception) contain no stack trace, exception message or binding-error detail, only the generic envelope. | C | ctx-default | keyed | IM8 as-13 | 01:144; 01:306 | neg | ≡? 06 | |
| S01-07 | ADM | 403 on role violation | An authenticated USER calling an `/api/admin/**` route receives 403. | C | ctx-default | keyed | IM8 ac-1; IM8 as-7 | 01:306 | neg | ≡? 11 | Only in the 01:306 list, not 01:144. |
| S02-01 | LCK | Lockout observation window | Failed logins spaced further apart than the configured observation window (clock advanced) do not accumulate to a lock; the same count inside the window does. | C | ctx-default | keyed | 02 §3; 09 §R | 02:198 | neg | ≡? 09 | Window length read from configuration. 02 raised the missing window; 09 owns its value. |
| S02-02 | CRED | 72-byte password ceiling in our validator | A password over 72 UTF-8 bytes (including a multi-byte case under 72 characters) is rejected by the application validator with the specific rule-violation error, before the encoder is called. | C | ctx-default | keyed | 02 §5; Std §3.5 Failure Path 18 | 02:342 | neg | ≡? 07 | "Before the encoder" can be proved by zero `encode()`/`matches()` calls on the encoder spy. |
| S02-03 | SES | Session and CSRF cookie attributes | Raw `Set-Cookie` for the session cookie carries `HttpOnly`, `SameSite=Strict`, `Path=/` and no `Domain`, plus `Secure` and the `__Host-` prefix where the profile enables them. | P | ctx-port | keyed | 02 §6; 08 | 02:395 | pos | ≡? 08 | One row for "cookie attribute assertions" (parameterised over attributes). `Secure`/`__Host-` are profile-driven (02 §6); non-dev form may need a ctx-nondev binding twin. |
| S03-01 | AUD | Audit event schema | For every `AuditEvent` value, the event captured by a `ListAppender` on the `"audit"` logger carries the Tier B fields (`event.kind`, `event.category`, `event.type`, `event.action`, `event.outcome`, `event.severity`) with closed-enum values. | U | none | none | IM8 lm-4; IM8 lm-15; 03 §1 | 03:394 | pos | ≡? 13 | Matrix-generated, one row. 13's amendment replaced 03's named `AuditLogger` methods with an `AuditEvent` enum plus `emit`. |
| S03-02 | AUD | No `user.id` on auth-failure events | Authentication-failure audit events contain no `user.id` key. | U | none | none | 03 C2; IM8 lm-19 | 03:394 | neg | ≡? 13 | |
| S03-03 | AUD | No `user.name` or `user.hash` anywhere | No audit event of any type contains a `user.name` or `user.hash` key. | U | none | none | 03 C5; IM8 lm-19 | 03:394 | neg | ≡? 13 | Parameterised over all `AuditEvent` values. |
| S03-04 | AUD | `user.target.id` encodes as a flat key | Through `CustomStructuredLogEncoder` with `<format>ecs</format>`, `addKeyValue("user.target.id", uuid)` produces a root-level key with no JSON writing error. | U | none | none | 03 C8 | 03:467; 03:618 | pos | ≡? 13 | 13's amendment made this a named first-implementation test. |
| S03-05 | AUD | Audit appender retention | The dedicated audit rolling-file appender has a daily file-name pattern and `max-history` 90, and has no `total-size-cap`. | C | ctx-default | none | 03 §4; 13 | 03:635 | neg | ≡? 13 | Source frames the absence of the cap as the asserted point. |
| S03-06 | OBS | Baggage does not reach MDC | With a probe key set in `correlation.fields` and `remote-fields`, sending it as a `baggage:` entry and as its own header reaches neither the MDC nor the log line. | P | ctx-port | none | 27 §4 | 03:691 | neg | ≡? 27 | P because the trace-header wrapper is in play. Needs the probe-key property override, which is not one of 16 §3's fixed contexts. |
| S04-01 | ADM | Reject duplicate username | Admin create with an existing username is rejected with the duplicate-user error. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:224 | neg | ≡? 11 | Items S04-01..13 are 04's acceptance list for 11; 04:303 calls the admin recipe's list "fourteen-item". |
| S04-02 | ADM | Reject username held by a tombstone | Admin create with a username that matches a deleted-user tombstone is rejected. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:225 | neg | ≡? 11 | |
| S04-03 | ADM | Reject duplicate email | Admin create with an existing email is rejected. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:225 | neg | ≡? 11 | |
| S04-04 | ADM | Username and email immutable | An update request that changes username or email leaves both unchanged in the database. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:225 | neg | ≡? 11 | 16 §5 (§5:473) removed `USERNAME_CHANGE_NOT_ALLOWED`; assert persistence unchanged, not that error code. |
| S04-05 | ADM | No password change via generic update | The generic user-update path cannot change a password hash. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:226 | neg | ≡? 11 | |
| S04-06 | ADM | No locking via generic update | The generic user-update path cannot change lock state. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:226 | neg | ≡? 11 | |
| S04-07 | ADM | No self-role-change | An admin changing their own role is refused and the role is unchanged. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; IM8 ac-1; 04 | 04:227 | neg | ≡? 11 | |
| S04-08 | ADM | No self-delete | An admin deleting their own account is refused and the account remains. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; IM8 ac-1; 04 | 04:227 | neg | ≡? 11 | |
| S04-09 | ADM | No self-unlock | An admin unlocking their own account is refused. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; IM8 ac-1; 04 | 04:227 | neg | ≡? 11 | |
| S04-10 | ADM | Tombstone written atomically before delete | Deleting a user writes the tombstone in the same transaction; a failure after the tombstone write leaves neither the delete nor the tombstone committed. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:227 | pos | ≡? 11 | |
| S04-11 | ADM | Tombstone records deleting admin | The tombstone row records the acting admin's id as `deletedById`. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:228 | pos | ≡? 11 | |
| S04-12 | SES | Sessions terminated on role or enabled change | Changing a user's roles or `enabled` flag invalidates every live session of that user; the old session's next request is unauthenticated. | C | ctx-default | keyed | Std §2 Failure Paths 12–16; 04 | 04:230 | neg | ≡? 08; 11 | |
| S04-13 | CRED | Forced-password-change filter allowlist | A user with `requirePasswordChange` set is refused on every endpoint except CSRF bootstrap, current-user read, change-password and the public whitelist. | C | ctx-default | keyed | IM8 as-15; IM8 ac-6; 04 | 04:231 | neg | ≡? 11; 10 | Parameterised over endpoints. |
| S04-14 | HDR | Security headers on every response | Every response carries the standard's security headers; HSTS only on secure requests; the document-versus-API CSP distinction is kept. | C | ctx-default | none | Std §5:498; 04 | 04:299 | pos | ≡? 05 | Restated per 16 §5 (§5:498). 04 says "all five headers"; 16 §5 names HSTS plus "the other three". Overlaps S01-01 and S01-02. |
| S04-15 | CSRF | CSRF bootstrap is JSON and sets no cookie | `GET /csrf` returns the token in a JSON body and the response sets no `XSRF-TOKEN` cookie. | P | ctx-port | keyed | Std §3.1; 04 | 04:299 | neg | ≡? 08 | P for raw `Set-Cookie`. |
| S04-16 | SES | Session id rotated on login | The session cookie value after a successful login differs from the pre-login value. | P | ctx-port | keyed | 04; 02 §6 | 04:300 | pos | ≡? 08 | Source says `JSESSIONID`; cookie name follows 08's decision. |
| S04-17 | LCK | Lockout at threshold and auto-lift | After the configured number of consecutive failures the account is locked (uniform 401, lock state in the DB), and the lock lifts after the configured duration (clock advanced). | C | ctx-default | keyed | Std §3.5; 04 | 04:300 | pos | ≡? 09; 16:Story 3 (i) | Source says "5 failures → 401 + 20-minute DB lockout"; values read from config. |
| S04-18 | SES | Absolute session timeout | A session older than the configured absolute timeout (8 h) is invalidated on its next request. | C | ctx-default | keyed | Std §3.5; 04 | 04:301 | neg | ≡? 08 | Age `SPRING_SESSION` rows or advance the shared clock; `Thread.sleep` prohibited (16 §4). |
| S04-19 | ADM | Role hierarchy inheritance | A higher role inherits the authorities of lower roles per the configured hierarchy. | C | ctx-default | keyed | 04 | 04:301 | pos | ≡? 11 | |
| S04-20 | ADM | Role mapping from configuration | Changing the role-to-authority mapping in configuration alone changes authorisation outcomes, with no code change. | U | none | none | Std §3.5; 04 | 04:301 | pos | ≡? 11 | Source: "YAML-only role change with no rebuild". U so it needs no extra context. |
| S04-21 | ADM | Default-deny for unconfigured endpoints | A request to an endpoint with no configured guard returns 403. | C | ctx-default | keyed | IM8 ac-1; IM8 as-7; 04 | 04:302 | neg | ≡? 05; 11 | |
| S04-22 | ADM | Self-read bounded to self | An IDOR attempt on the self-read endpoint (another user's id) returns only the caller's own record or is refused. | C | ctx-default | keyed | IM8 ac-1; 04 | 04:302 | neg | ≡? 11; 14 | |
| S04-23 | CRED | No credential fields in profile JSON | The self-read profile response contains no `password`, password-hash or `passwordHistory` field. | C | ctx-default | keyed | 04 | 04:302 | neg | ≡? 11 | |
| S04-24 | AUD | Admin-issued credential not logged | The plaintext credential an admin reset returns appears in no log line, including the audit appender. | C | ctx-default | keyed | IM8 lm-19; 04 | 04:303 | neg | ≡? 16 §5 canaries; 10 | 04 names the recipe's generated plaintext password; our design issues a reset token (04 finding 5). Likely absorbed by the canary sweep. |
| S04-25 | CRED | Password history rotation | With history depth N from configuration, the last N passwords are refused and the (N+1)-th prior password is accepted again. | C | ctx-default | keyed | Std §3.5; 04 | 04:304 | pos | ≡? 07; 10 | |
| S04-26 | AUTH | Timing uniformity by mechanism | Exactly one `matches()` call per request that reaches the provider and zero per limiter refusal, for known and unknown usernames alike. | C | ctx-default | keyed | ASVS 6.3.8 (L3); 16 §5 | 04:305 | pos | ≡? 09 §R row 5; 21:624 | Restated per 16 §5 and §9 ("identical response timing" becomes the `matches()` count; no wall-clock assertion). |
| S04-27 | HDR | CSP on the SPA origin | The SPA document served by its own host carries its own CSP (including `connect-src` for the API origin), and no `securitypolicyviolation` events fire. | E | playwright | none | IM8 as-9; 04 addendum | 04:524 | pos | ≡? 16 §8 (CSP enforcement); 14 | |

## Not transcribed

| file:line | item | reason |
|---|---|---|
| 01:144 | "429 on the 6th attempt" (struck) | Superseded by 16's amendment (01:318–324): the 6th failure gets the uniform 401. Replaced by S01-04 and S01-05. |
| 01:144 | "`im8-review`'s PASS and FAIL conditions are the de facto acceptance criteria" (general clause) | Not an enumerable test list; only the named examples are transcribed. See Problems. |
| 03:370 | Logging standard §5 test rule: sanitise injection payloads and assert the sanitised form | Restates a Logging standard §5 test rule; belongs to the LOG group. |
| 03:384 | §5 test criterion "audit events present in the dedicated audit log destination" | Restates a Logging standard §5 test; belongs to the LOG group. |
| 03:415 | §5 release test "request logs exclude client IP addresses" | Restates a Logging standard §5 test; belongs to the LOG group. Also affected by 13's `source.ip_hash` decision. |
| 03:457 | §5 release test "user identifiers appear only as `user.id` UUID" | Restates a Logging standard §5 test; belongs to the LOG group. |
| 03:703 | "Line 276 note": override sampling probability to 0.0 for ticket 16 row 4's sampling case | Fixture precondition for a test owned elsewhere (15 row 4, restated by 27), not a separate test. Carry it into that row's note. |
| 04:229 | Batch payload capped and rejected before any work | Superseded: 16 §5 (§5:494) makes the batch half N/A. |
| 04:306 | "proving a log line absence" | A method question, not a test. Answered by 16 §5 canaries. |

## Counts

| source ticket/section | n tests | keys |
|---|---|---|
| 01:144 (L01-a) | 6 | S01-01..S01-06 (S01-01..06 also at 01:306; S01-04/05 also at 01:321/322; S01-04 also at 04:301; S01-05 also at 02:198) |
| 01:306 (L01-b) | 1 | S01-07 |
| 02:198 (L02-a) | 1 | S02-01 |
| 02:342 (L02-b) | 1 | S02-02 |
| 02:395 (L02-c) | 1 | S02-03 |
| 03:394 (L03-a) | 3 | S03-01..S03-03 |
| 03:467 (L03-b) | 1 | S03-04 (also 03:618) |
| 03:635 (L03-d) | 1 | S03-05 |
| 03:691 (L03-e) | 1 | S03-06 |
| 04:223 (L04-a) | 13 | S04-01..S04-13 |
| 04:298 (L04-b) | 13 | S04-14..S04-26 |
| 04:523 (L04-c) | 1 | S04-27 |
| **Total** | **43** | |
