## Lists
| list | file | heading or first words | first line | last line | n tests | keys |
|---|---|---|---|---|---|---|
| L19-a | 19 | "16 (test plan): the reset path and the two-admin invariant … need ours" | 310 | 311 | 2 | S19-01, S19-02 |
| L20-a | 20 | "It makes two prescribed tests unimplementable" (CORS) | 141 | 144 | 2 | S20-01, S20-02 |
| L20-b | 20 | §3 "Mitigation is to assert `Strict` in our test with a comment pointing at the ADR" | 159 | 163 | 1 | S20-03 |
| L20-c | 20 | §3 "`SESSION` in dev, `__Host-SESSION` … with a profile-scoped test" | 173 | 174 | 1 | S20-04 |
| L20-d | 20 | §4 "`vite preview` is the CSP verification surface" | 227 | 230 | 1 | S20-05 |
| L20-e | 20 | §8 ticket 14 constraint "confirm the built `index.html` contains no inline script" | 305 | 306 | 1 | S20-06 |
| L20-f | 20 | §8 "Ticket 16 (test plan): the CSP assertion runs against `vite preview`" | 310 | 313 | 5 (restated) | S20-01, S20-02, S20-03, S20-04, S20-05 |
| L21-a | 21 | §1 "Probes disabled, and the test asserts the outcome" | 251 | 261 | 1 | S21-01 |
| L21-b | 21 | §1 "Actuator's health body is a seventh envelope producer" | 267 | 271 | 1 | S21-02 |
| L21-c | 21 | §2 "The two settings are one interlock, and the test says so" / "A third assertion" | 287 | 294 | 3 | S21-03, S21-04, S21-05 |
| L21-d | 21 | §2 "The two actuator CVE regression guards, aimed correctly" | 299 | 308 | 2 | S21-06, S21-07 |
| L21-e | 21 | §3 "Assertions are therefore on absent beans" | 352 | 354 | 1 | S21-08 |
| L21-f | 21 | §5 Rate-limiter buckets "the test asserts `cache.gets` is non-zero" | 390 | 396 | 1 | S21-09 |
| L21-g | 21 | §8 "consume one externalised property, asserted equal at resolved values" | 482 | 486 | 1 | S21-10 |
| L21-h | 21 | §12 "parameterised with property keys named and covered by the binding test" | 574 | 578 | 1 | S21-11 |
| L21-i | 21 | §14 "The test surface, which is unusually strong here" | 608 | 627 | 3 new + 7 restated | S21-12, S21-13, S21-14 (restates S21-01, S21-05, S21-06, S21-07, S21-08, S21-09, S21-10) |
| L21-j | 21 | Amendment from 25 "Both filenames go onto ticket 24's 13.4.1 jar-content test" | 719 | 724 | 1 | S21-15 |
| L21-k | 21 | Amendment from 29 "Your `diskSpace` indicator on the audit directory stays, with its binding test" | 837 | 837 | 1 (restated) | S21-10 |

## Rows
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| S19-01 | MFA | Admin TOTP factor reset path | `DELETE /api/admin/users/{uuid}/totp` succeeds only for an ADMIN holding the TOTP factor; it deletes the target's `TOTP_USER_DETAILS` and any `PENDING_TOTP` row, invalidates every session of the target, and emits the `totp-remove` audit event. | C | ctx-default | keyed | IM8 ac-2; 19 §Answer; 11 §R | 19:310; 19:344; 19:433 | pos | ≡? 11; 23 | 19:310 owes "the reset path" without detail; facets taken from 19's amendments from 11 (session invalidation, audit action) and 23 item 5 (PENDING_TOTP deletion). Merge should fold into 11/23's reset tests. |
| S19-02 | ADM | Two-enrolled-admins invariant | Disable, demote or delete of an admin is refused when it would leave fewer than two enabled, enrolled admins, under the pessimistic lock; the factor-reset path is exempt from the count and succeeds at exactly two admins. | C | ctx-default | own-DB | 19 §Answer; 11 §R; 30 §3 | 19:310; 19:347; 19:520 | neg | ≡? 11; 30 | Exemption of the factor-reset path applied from 19's amendment from ticket 30 (19:518–525). Global count, so own-DB or delta per 16 §5. |
| S20-01 | HDR | CORS preflight from allow-listed origin | A preflight `OPTIONS` with `Origin: http://localhost:5173` succeeds and returns `Access-Control-Allow-Origin` naming that exact origin (never `*`), `Access-Control-Allow-Credentials: true` and `X-CSRF-TOKEN` among allowed headers. | C | ctx-default | none | Std §5:500; PRD L119; 20 §2 | 20:141; 20:312 | pos | ≡? 16:64 | One of the two prescribed CORS tests the topology keeps alive. Allow-list value from 20 §8/§10 Q27. |
| S20-02 | HDR | CORS non-allow-listed origin rejected | A CORS request or preflight from an origin not on the allow-list is rejected (no `Access-Control-Allow-Origin` echoed), and the configured allow-list contains no wildcard origin. | C | ctx-default | none | Std §5:501; PRD L119; 20 §2 | 20:143; 20:312 | neg | ≡? 16:64 | Second prescribed CORS test; source frames the two as separate tests. |
| S20-03 | SES | Session cookie SameSite=Strict | The raw `Set-Cookie` for the session cookie carries `SameSite=Strict` and `HttpOnly`; the test carries a comment naming the SameSite=Strict ADR so the contradiction with the standard's prescribed `Lax` test reads as a decision. | P | ctx-port | keyed | Std §5:499 (deviation by ADR); 20 §3; 20 ADR 1 | 20:159; 20:310 | pos | ≡? 16:57 | Deliberately fails the standard's line-499 wording (`SameSite=Lax`). Raw Set-Cookie so level P. |
| S20-04 | SES | Profile-scoped session cookie name | The session cookie is named `SESSION` under the dev profile and `__Host-SESSION` under the non-dev profile (with `Secure`, `Path=/`, no `Domain`). | P | ctx-nondev | keyed | 20 §3; 20 ADR 6 | 20:173; 20:311 | pos | ≡? 16:61 | One profile-scoped test; the dev half runs in ctx-port. The only test that exercises the production cookie configuration. |
| S20-05 | HDR | Document CSP on vite preview | Against `vite preview` of the built bundle, the document response carries the `.env.production` policy both as a header (via `preview.headers`, including `frame-ancestors 'none'`) and as the templated `<meta http-equiv="Content-Security-Policy">` tag (without `frame-ancestors`); the API-origin CSP is not accepted as evidence for the document policy. | E | playwright | none | IM8 as-9; Std §3.5; 20 §4; 20 ADR 3 | 20:215; 20:227; 20:310 | pos | ≡? 16:52; 16:68 | Includes the document-vs-API distinction (16's "trap"). Never asserted against `vite dev`. Separate from 16 §8's zero-`securitypolicyviolation` E test. |
| S20-06 | HDR | Built index.html has no inline script | The built `dist/index.html` contains no inline `<script>` (including no `type="importmap"`), so `script-src 'self'` holds without nonce or `'unsafe-inline'`. | B | build | none | IM8 as-9; 20 §5; 20 §8 | 20:305 | neg | ≡? 14 | Stated as a "confirm" constraint handed to ticket 14, not explicitly as a test; merge to confirm it is owed as a B row. |
| S21-01 | OBS | Health probes disabled | `/actuator/health/liveness` and `/actuator/health/readiness` both return 404 (outcome asserted, not the property). | C | ctx-default | none | IM8 as-13; 21 §1 | 21:251; 21:624 | neg |  | Listed twice in 21 (§1 and §14). |
| S21-02 | AUTH | Actuator envelope exemption | A 503 health response has body `{"status":"DOWN"}`, not `application/problem+json`, and the exemption from the error-envelope contract applies to `/actuator/**` and to no other path. | C | ctx-default | none | 06 envelope contract; 21 §1; 21 ADR 6 | 21:268 | pos | ≡? 06 | Pillar AUTH per the envelope guidance; could be OBS if the merge files it by actuator. |
| S21-03 | OBS | Health permitAll and show-details interlock | Anonymous `GET /actuator/health` returns 200 with no details, and `/actuator/health/db` and other health subpaths return 404 because `show-details: never`. | C | ctx-default | none | IM8 as-13; 21 §2 | 21:289 | pos |  | "The two settings are one interlock, and the test says so." |
| S21-04 | OBS | Other actuator endpoints denied | Every actuator endpoint other than health is refused by the explicit `denyAll()` rule (e.g. `/actuator/info`, `/actuator/env`). | C | ctx-default | none | IM8 as-13; IM8 ac-1; 21 §2 | 21:291 | neg |  | Inferred as the second of three §2 assertions ("A third assertion confirms …"); source does not state it as a separate sentence. |
| S21-05 | OBS | Management security auto-config backed off | `ManagementWebSecurityAutoConfiguration` is absent from the context, so public health comes from our rule. | C | ctx-default | none | IM8 as-13; 21 §2 | 21:292; 21:626 | pos |  | Listed in §2 and §14. |
| S21-06 | CFG | No health-group additional-path (CVE-2026-22731 guard) | No `management.endpoint.health.group.*.additional-path` is set in any profile or config source. | C | ctx-default | none | 21 §2 | 21:303; 21:627 | neg |  | Configuration-shape guard, not a vulnerability mitigation on 4.1.x. Should scan every profile's config source. |
| S21-07 | CFG | No route at /cloudfoundryapplication (CVE-2026-22733 guard) | No application route resolves at or beneath `/cloudfoundryapplication`, including under `server.servlet.context-path`. | C | ctx-default | none | 21 §2 | 21:305; 21:627 | neg |  | Split from S21-06: source names two predicates. |
| S21-08 | OBS | OTLP metrics export dormant | `OtlpMeterRegistry`, `OtlpConfig` and `OtlpMetricsConnectionDetails` beans are absent (never asserted on the resolved URL). | C | ctx-default | none | 21 §3 | 21:352; 21:623 | neg | ≡? 24 | Listed in §3 and §14. |
| S21-09 | OBS | Limiter cache statistics recorded | After exercising the rate limiter, `cache.gets` for the Caffeine limiter structures is non-zero (the gauge exists and moves), proving `.recordStats()` is on the builders. | C | ctx-default | delta | IM8 lm-16; 21 §5 | 21:396; 21:611; 21:625 | pos | ≡? 09:1014 | 21:682 (amendment from 09) extends `recordStats()` to the per-source cardinality set; merge may widen the assertion to that third structure. |
| S21-10 | OBS | Disk-space indicator path binding | The resolved `management.health.diskspace.path` equals the audit appender's resolved `<file>` directory (one externalised property), asserted at resolved values. | C | ctx-default | none | IM8 lm-16; 21 §8 | 21:483; 21:626; 21:837 | pos |  | 21:837 (amendment from 29) calls this the diskSpace indicator's binding test. Threshold value not bound by any named test (see problems). |
| S21-11 | OBS | Alert-threshold binding | The effective `app.security.lockout.nist.alert-threshold` binds 50 off configuration, as the parameterised alert table's key. | C | ctx-nondev | none | 21 §12; 09 §R | 21:575 | pos | ≡? 09:1590 | KEY substituted from 09's amendment-from-16 table (09:1596 row "NIST alert threshold"). Source says "the binding test" for the table's keys generally. |
| S21-12 | OBS | 429-by-route meter shape | A request short-circuited by a limiter with 429 records `http.server.requests` with `uri=UNKNOWN` and `status=429`, and the `max-uri-tags` limit (100) is confirmed unreached. | C | ctx-default | keyed | IM8 lm-16; 21 §4 | 21:612; 21:624 | pos |  | Justifies the limiter-supplied `route` tag. |
| S21-13 | OBS | Meter test harness not inert | In every test context the injected `MeterRegistry` is a `SimpleMeterRegistry` (not an empty `CompositeMeterRegistry`), `management.defaults.metrics.export.enabled` is not set false, and `management.metrics.use-global-registry` is false. | C | ctx-default | none | 21 §14; 21 ADR 8 | 21:615 | pos |  | The "harness trap"; source states it as a harness decision with an ADR, not an explicit test. Merge to confirm it is owed as a row. |
| S21-14 | AUTH | Timing uniformity by matches() count | Exactly one `PasswordEncoder.matches()` per login request reaching the provider and zero per limiter refusal, for known and unknown usernames alike (a `UserCache` hit would show as a deviation). | C | ctx-default | keyed | ASVS 6.3.8 (L3); 09 §R; 16 §5 | 21:627 | pos | ≡? 16 §R-5; 09:1177 | 21's item reads "no `UserCache` bean (ticket 09's, but surfaced here)". Restated per 16 §9 ("21:624 is the same test as 09 §R row 5"). |
| S21-15 | BLD | No git/build-info metadata in jar | The built jar contains neither `BOOT-INF/classes/git.properties` nor `build-info.properties`, added as lines to ticket 24's jar-content test. | B | build | none | ASVS 13.4.6 (L3); ASVS 13.4.5 (L2) | 21:722 | neg | ≡? 24 | Amendment to 24's 13.4.1 jar-content test; clause corrected by 21:726. |

## Not transcribed
| file:line | item | reason |
|---|---|---|
| 19:369 | Reset redemption clears the password lockout only, never any TOTP counter or enrolment state | Design pin in 10's amendment; not stated as a test here. Owned by ticket 10. |
| 20:119 | "Every prod-only value … expressed as profile config asserted by a test" | Umbrella statement; its concrete tests are S20-03/S20-04 and other tickets' non-dev binding rows (05, 08, 24). |
| 20:223 | "Verifiable with `curl -I` against the serving port" | Verification method, folded into S20-05. |
| 20:264 | API-side CSP "the label is the deliverable" | Labelling obligation (comment, ADR, evidence), not a test. |
| 20:287 | Never allow `localhost` origins in the production CORS allow-list | Deployment constraint for ticket 25, not a test. |
| 21:340 | `setRequiredProperties` on any profile that turns export on | Mechanism for a profile not built here; §13 records OTLP enablement assertions as never executed (21:589). |
| 21:557 | §11 deployer items 1–5 "Acceptance: …" | Acceptance checks for the deployer, not tests. |
| 21:569 | Deployer recomputes diskspace `threshold` | Deployer obligation. |
| 21:762 | Row 46's `N` has no property key and no named binding test | Owed by ticket 26, not here. |
| 21:802 | Per-event alert: enrolled-admin count reaches zero | Alert classification for the collector, not an application test. |
| 21:807 | Absence detection "intent with no outcome" | Collector-side deployer obligation. |
| 21:834 | `h2Data` indicator and two session gauges | Design from 29's amendment; no test named here. Owned by ticket 29. |
| 21:848 | Gauge of authenticable admins, strongly held | No test named; alert is the deployer's (21:855). |

## Counts
| source ticket/section | n tests | keys |
|---|---|---|
| 19 §Answer constraints to 16 (19:310) | 2 | S19-01, S19-02 (also 19:344, 19:347, 19:433, 19:520) |
| 20 §2 (20:141) | 2 | S20-01, S20-02 (also 20:312) |
| 20 §3 (20:159, 20:173) | 2 | S20-03, S20-04 (also 20:310, 20:311) |
| 20 §4 (20:215) | 1 | S20-05 (also 20:227, 20:310) |
| 20 §8 to ticket 14 (20:305) | 1 | S20-06 |
| 21 §1 (21:251, 21:268) | 2 | S21-01, S21-02 (S21-01 also 21:624) |
| 21 §2 (21:289–305) | 5 | S21-03, S21-04, S21-05, S21-06, S21-07 (also 21:626, 21:627) |
| 21 §3 (21:352) | 1 | S21-08 (also 21:623) |
| 21 §5 (21:396) | 1 | S21-09 (also 21:611, 21:625) |
| 21 §8 (21:483) | 1 | S21-10 (also 21:626, 21:837) |
| 21 §12 (21:575) | 1 | S21-11 |
| 21 §14 (21:612–627) | 3 | S21-12, S21-13, S21-14 |
| 21 amendment from 25 (21:722) | 1 | S21-15 |
| **Total** | **23** | |
