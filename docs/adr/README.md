# Architecture decision records

An ADR exists for a decision only if it passes one test: **would a maintainer reading the code and the spec
plausibly undo this, and get away with it?** One decision per ADR. A decision is the set of choices that would be
reversed together. Amendments are folded into the ADR's text, never filed separately.

A candidate that fails the test is not lost. It goes to one of four other places, and the rejection log below
says which:

- **Deferral register:** deviations from a standard or the PRD, residuals, N/A and not-built items.
- **Test plan, `rationale` column:** an invariant that looks wrong but is not, where a named test fails the moment
  someone reverses it and one sentence carries the reason.
- **Handover document:** anything a deployer or operator must do.
- **Spec:** what the system does.

IDs are permanent. They are never renumbered or reused.

## Index

`reserved` means the ID and title are fixed, and the file is not written yet. A written ADR's file is
`NNNN-slug.md`, where `NNNN` is its number, and its status is its front-matter `status`.

| ID | Title | Status |
|---|---|---|
| ADR-001 | [BCrypt at cost 12 behind `DelegatingPasswordEncoder`, not Argon2id](0001-bcrypt-cost-12-delegating-encoder.md) | accepted |
| ADR-002 | [15-character password minimum, overriding the PRD's 12](0002-fifteen-character-password-minimum.md) | accepted |
| ADR-003 | [Reject passwords over 72 UTF-8 bytes rather than pre-hash](0003-reject-passwords-over-72-bytes.md) | accepted |
| ADR-004 | [No pepper or keyed pre-hash](0004-no-pepper-or-keyed-pre-hash.md) | accepted |
| ADR-005 | [A zxcvbn score-3 strength gate replaces composition rules](0005-zxcvbn-gate-replaces-composition-rules.md) | accepted |
| ADR-006 | [Admins issue credentials by single-use token, never by generated password](0006-admins-issue-tokens-not-passwords.md) | accepted |
| ADR-007 | [One `credential_tokens` table, domain-separated SHA-256, no HMAC, no Spring OTT](0007-one-credential-tokens-table.md) | accepted |
| ADR-008 | [A self-service password-change endpoint exists and always requires the current password](0008-password-change-always-requires-current-password.md) | accepted |
| ADR-009 | [Reset redemption clears the password lockout, never TOTP state](0009-reset-redemption-clears-password-lockout-only.md) | accepted |
| ADR-010 | [Dual rate limiting: per-account lockout and per-source throttling as independent limiters](0010-dual-rate-limiting.md) | accepted |
| ADR-011 | [Escalating lockout ladder 20/40/60 minutes instead of the PRD's flat 15](0011-escalating-lockout-ladder.md) | accepted |
| ADR-012 | [Failures count only inside an observation window](0012-lockout-observation-window.md) | accepted |
| ADR-013 | [NIST SP 800-63B-4 §3.2.2 cap: the password authenticator is disabled after 100 consecutive failures](0013-nist-cap-disables-password-authenticator.md) | accepted |
| ADR-014 | [No sleep-based progressive delay](0014-no-sleep-based-progressive-delay.md) | accepted |
| ADR-015 | [A per-source cardinality axis caps distinct accounts driven into lockout](0015-lockout-cardinality-axis.md) | accepted |
| ADR-016 | [Narrow multi-authenticator reading of NIST §3.2.2: a tier-2 factor disable forces password rebinding](0016-narrow-multi-authenticator-reading.md) | accepted |
| ADR-017 | [The budget table stays a request-rate allowlist; unlisted routes are metered on session-store misses](0017-budget-allowlist-and-session-miss-budget.md) | accepted |
| ADR-018 | [Unmatched paths are not rejected before `CsrfFilter`](0018-no-early-rejection-of-unmatched-paths.md) | accepted |
| ADR-019 | [A two-tier emitter bound caps audit volume, because a limiter cannot](0019-two-tier-audit-emitter-bound.md) | accepted |
| ADR-020 | [Source key: IPv6 aggregated to /64 by one property, from one resolver, with the raw-address path banned](0020-source-key-ipv6-64.md) | accepted |
| ADR-021 | [Session-scoped factor authority instead of per-request possession proof, with bounded validity](0021-session-scoped-factor-authority.md) | accepted |
| ADR-022 | [The TOTP seed encryption key lives outside the database](0022-totp-key-outside-the-database.md) | accepted |
| ADR-023 | [TOTP is required for administrators and offered to no one else](0023-totp-for-administrators-only.md) | accepted |
| ADR-024 | [Recovery codes deferred; two enrolled admins and break-glass compensate](0024-recovery-codes-deferred.md) | accepted |
| ADR-025 | [Provisioning returns a JSON envelope with a manual-entry secret, and the SPA renders the server PNG](0025-json-provisioning-envelope.md) | accepted |
| ADR-026 | [Factor rules are hand-composed per matcher, role first, not `@EnableMultiFactorAuthentication`](0026-hand-composed-factor-rules.md) | accepted |
| ADR-027 | [Two-tier TOTP lockout: tier 1 auto-lifts, tier 2 disables cumulatively](0027-two-tier-totp-lockout.md) | accepted |
| ADR-028 | [A context prefix inside the plaintext substitutes for AES-GCM AAD](0028-context-prefix-instead-of-aad.md) | accepted |
| ADR-029 | [Server-side session cookies, not JWT](0029-server-side-session-cookies-not-jwt.md) | accepted |
| ADR-030 | [Spring Session JDBC, not Redis or container sessions](0030-spring-session-jdbc.md) | accepted |
| ADR-031 | [One RFC 9457 envelope from one writer; `sendError` prohibited](0031-one-rfc-9457-envelope-one-writer.md) | accepted |
| ADR-032 | [Enumeration-resistant two-step registration: uniform 202, password set at activation](0032-two-step-enumeration-resistant-registration.md) | accepted |
| ADR-033 | [Password lockout is never a wire code; the factor lock is, behind the password](0033-password-lockout-never-on-the-wire.md) | accepted |
| ADR-034 | [A failed login does not invalidate an existing session](0034-failed-login-keeps-existing-session.md) | accepted |
| ADR-035 | [Self-service credential change terminates the user's other sessions, not the current one](0035-credential-change-ends-other-sessions.md) | accepted |
| ADR-036 | [Session-bound synchronizer CSRF token, header only, with the same-site constraint it imposes](0036-session-bound-header-only-csrf-token.md) | accepted |
| ADR-037 | [Sessions are invalidated on admin disable, role change, delete, lockout and cap disable](0037-session-invalidation-triggers.md) | accepted |
| ADR-038 | [The session id rotates on factor grant and credential change, with one `AUTH_INSTANT` stamping point](0038-session-id-rotation-and-auth-instant.md) | accepted |
| ADR-039 | [Session invalidation is dispatched after commit, backed by an idempotent reconciliation sweep](0039-after-commit-session-invalidation.md) | accepted |
| ADR-040 | [Anonymous sessions are created on demand, by exactly one route](0040-anonymous-sessions-one-route.md) | accepted |
| ADR-041 | [Anonymous-session shedding: a count line and a free-space reserve](0041-anonymous-session-shedding.md) | accepted |
| ADR-042 | [Two roles, one per user: YAML is the truth, and a seeded read-only `roles` table enforces the foreign key](0042-two-roles-yaml-truth-read-only-roles-table.md) | accepted |
| ADR-043 | [The authorization matrix is adopted narrowly, whitelist first](0043-authorization-matrix-whitelist-first.md) | accepted |
| ADR-044 | [Deletion leaves a tombstone: HMAC of the email, plaintext username, indefinite retention](0044-deletion-leaves-a-tombstone.md) | accepted |
| ADR-045 | [Identifier canonicalisation: NFC, trim, lowercase, no dot or tag folding; the canonical value replaces the stored one](0045-identifier-canonicalisation.md) | accepted |
| ADR-046 | [Forced-change credentials expire lazily at login after 30 days, refused before the password check, not by a reaper](0046-lazy-forced-change-expiry-pre-authentication.md) | accepted |
| ADR-047 | [Bootstrap: refresh-phase validation and runner seeding, one admin, conditional on a second enrolled admin](0047-bootstrap-refresh-validation-runner-seeding.md) | accepted |
| ADR-048 | [Two-admin invariant: guarded on mutation paths, monitored everywhere](0048-two-admin-invariant-guarded-and-monitored.md) | accepted |
| ADR-049 | [Factor reset is exempt from the two-admin count](0049-factor-reset-exempt-from-two-admin-count.md) | accepted |
| ADR-050 | [UUIDv4 as the sole primary key, generated by the application, not UUIDv7](0050-uuidv4-sole-primary-key.md) | accepted |
| ADR-051 | [`ddl-auto: validate` is demoted; the schema gate is a negative-test set with `NAMED` validation](0051-schema-gate-negative-tests-named-validation.md) | accepted |
| ADR-052 | [Tombstone HMAC key: forward-only versioning, old versions never retire](0052-tombstone-hmac-key-forward-only-versioning.md) | accepted |
| ADR-053 | [MFA enrolment is row existence on shared-key tables, not a flag](0053-mfa-enrolment-is-row-existence.md) | accepted |
| ADR-054 | [Log correlation fields are keyed hashes: `source.ip_hash` over the source key and `session.hash`, never the address](0054-keyed-hash-log-correlation-fields.md) | accepted |
| ADR-055 | [One data-driven `AuditEvent` enum and a single `emit`, not one method per event](0055-data-driven-audit-event-enum.md) | accepted |
| ADR-056 | [The audit stream is duplicated to stdout, against the separate-destination constraint](0056-audit-stream-duplicated-to-stdout.md) | accepted |
| ADR-057 | [Reset links are logged only in `dev`, by a non-audit logger with three enforcement controls](0057-dev-only-reset-link-logger.md) | accepted |
| ADR-058 | [`SameSite=Strict`, not the standard's `Lax`](0058-samesite-strict-not-lax.md) | accepted |
| ADR-059 | [Two origins, not Boot serving the bundle, and therefore no CSP nonce](0059-two-origins-no-csp-nonce.md) | accepted |
| ADR-060 | [Document CSP is delivered in three layers, with a templated meta tag](0060-three-layer-document-csp.md) | accepted |
| ADR-061 | [Actuator exposes `health` only, read-only, and metrics are pushed over OTLP](0061-actuator-health-only-otlp-push.md) | accepted |
| ADR-062 | [Secrets bind through `@Validated @ConfigurationProperties`, not `@Value`](0062-secrets-bind-through-configuration-properties.md) | accepted |
| ADR-063 | [Inbound trace context is restarted at the application boundary](0063-restart-inbound-trace-context.md) | accepted |
| ADR-064 | [The threat model is an artefact of the plan, not the build](0064-threat-model-is-a-plan-artefact.md) | accepted |
| ADR-065 | [Security-control tests boot the full context; `@WebMvcTest` and all slices are banned](0065-security-tests-boot-the-full-context.md) | accepted |
| ADR-066 | [A forward-only mutable clock and clock-built cache adapters are production design](0066-forward-only-clock-is-production-design.md) | accepted |
| ADR-067 | [No `test` profile; tests override capture beans in the default context](0067-no-test-profile.md) | accepted |
| ADR-068 | [A traceability gate binds each test to a canonical table row with `@Proves`](0068-traceability-gate-with-proves.md) | accepted |
| ADR-069 | [One generated source table renders both the register and the handover document, gated for drift in `verify`](0069-one-source-table-two-renderings.md) | accepted |
| ADR-070 | [Break-glass recovery as NIST SP 800-63B-4 §4.2.2.2 option 2, under an adopted reading of §4.2.1](0070-break-glass-as-nist-recovery-option-2.md) | accepted |
| ADR-071 | [A distinct recovery address, not recovery contacts or saved codes](0071-distinct-recovery-address.md) | accepted |
| ADR-072 | [Operator recovery is an offline same-jar runner run inside a planned outage](0072-offline-recovery-runner.md) | accepted |
| ADR-073 | [The runner takes a credential in and emits nothing; batch mode mints nothing](0073-runner-credential-in-nothing-out.md) | accepted |
| ADR-074 | [Runner changes are bound by a plan/apply digest](0074-runner-plan-apply-digest.md) | accepted |
| ADR-075 | [Device cookies split the password lockout into an untrusted lane and per-device lanes](0075-device-cookie-lockout-lanes.md) | accepted |

## Rejection log

Each line names a candidate, where the decision lives instead, and why it gets no ADR.

| ID | Candidate | Lives in | Why no ADR |
|---|---|---|---|
| REJ-001 | No Logback `MaskingJsonGeneratorDecorator`; redaction at source and in the custom encoder | spec (logging) | The decorator is incompatible with the structured encoder, so an attempt to add it fails at build time. |
| REJ-002 | `user.id` omitted on password-reset-requested rows, included on reset-completed | spec (audit event catalogue) | The catalogue states the field set per row, and the reason (a request row must not confirm an account) is visible where the rows are defined. |
| REJ-003 | Flyway for migrations, not Liquibase and not `ddl-auto` schema generation | register; spec (data model) | Nothing in the code invites a switch. The deviation from the bootstrap guidance's Liquibase option is a register row. |
| REJ-004 | Breach blocklist sliced to entries of 15 characters or more, plus a context word list | spec (password policy); handover (refresh); register (ASVS 6.1.2, 6.2.11 (L2)) | Shorter entries cannot pass the length floor, so the slice is self-evident. Refreshing the lists is an operator duty. |
| REJ-005 | `CompromisedPasswordChecker` implemented but not registered as a bean | test plan T-AUTH-013 | The test fails the moment the bean appears, and its rationale sentence carries the whole reason. |
| REJ-006 | Password settings under `app.security.password.*`, not the recipe's `spring.password.sso.*` | register | A property prefix is cosmetic. The register records the recipe deviation, with the other namespace choices. |
| REJ-007 | Password history of three kept, despite NIST SP 800-63B-4's silence on history | test plan T-CRED-002; register (currency note) | The governing standard requires it, and the test and its rationale stop removal. |
| REJ-008 | No `Max-Age` on the session cookie | test plan T-CFG-032 | Prohibited configuration, so startup and the test both refuse it, and one sentence explains why. |
| REJ-009 | Two of the standard's prescribed tests reinterpreted (redeemed CSRF tokens; CSRF-cookie attributes) | register | These are standards defects: the tests are untestable or dead text as printed. The register is where defects live. |
| REJ-010 | `Clear-Site-Data` sent for compliance, with SPA-side clearing as the real control | test plan T-HDR-005; register; handover | The test pins the origin scoping, and the deployer note covers the domain condition. |
| REJ-011 | Dropping `.deleteCookies("JSESSIONID","SESSION")` from logout | spec (session lifecycle) | An implementation note. Trivially reversible and harmless either way. |
| REJ-012 | Session limits of 15 minutes idle, 8 hours absolute, one concurrent session | spec (session lifecycle) | These are the standard's own values. There was no trade-off. |
| REJ-013 | Withdrawn (2026-09-30). Was: PRD Story 3's third criterion not met as stated; the state-independence reading adopted | ADR-075; register (PRD Story 3 AC3) | Withdrawn: device cookies (ADR-075) now meet the criterion's promised outcome for trusted browsers, so there is again a design choice a maintainer could reverse, and it has an ADR. |
| REJ-014 | ASVS 6.1.1 (L1) passed with a documented malicious-lockout residual | register | A verdict, not a design choice. |
| REJ-015 | `server.forward-headers-strategy: framework` prohibited; trusted proxies named explicitly | test plan T-CFG-025; handover | Prohibited configuration fails startup, and the test and its rationale carry the reason. Naming the proxies is the deployer's job. |
| REJ-016 | Admin issuance of a reset token does not clear the lock | spec (credential flows); handover | Unlocking is a separate, audited admin action under the standard, and the user's own redemption clears the lock (ADR-009). |
| REJ-017 | `is_account_non_locked` derived, not stored | spec (data model) | A derived column is the obvious shape, and no maintainer would add a stored copy. |
| REJ-018 | Distributed-limiter test deferred; single instance asserted at startup | register (fidelity) | Single-instance deployment is a declared limitation, not a choice to reverse. |
| REJ-019 | Cap refusal placed in the pre-authentication check slot | test plan T-LCK-009 | The test pins the placement, and one sentence carries the oracle argument. |
| REJ-020 | Own-state columns for the NIST cap, not `enabled = false` | spec (data model) | The spec defines authenticator disable and admin account disable as different states. |
| REJ-021 | Reserved-name username denylist, one set, read at bootstrap and registration | test plan T-ADM-022 | The test and its rationale cover it. |
| REJ-022 | Reset and activation link origins never come from request input | test plan T-CRED-016, T-CRED-017, T-CRED-018 | Three tests fail on the obvious regression, and the rationale cites the OWASP guidance. |
| REJ-023 | `ROLE_*` authorities checked with `hasRole` | register (standards defects); spec (authorization) | The corpus uses three conventions that contradict each other. There is no single prescribed form to revert to. |
| REJ-024 | Admin paths mapped in controllers; `spring.mvc.servlet.path` prohibited | test plan T-CFG-024 | Prohibited configuration, cited to CVE-2026-22753 in the rationale. |
| REJ-025 | PRD Story 10 kept; the standard's role-assignment clause read as barring self-assignment only | register | A PRD feature kept under a stated reading. The register records the reading. |
| REJ-026 | Batch password reset not built | register (N/A) | Not in the PRD, and declined on scope. |
| REJ-027 | Usernames may not contain `@` | spec (identifiers); register (product cost) | A validation rule, stated in the spec, with its user-visible cost registered. |
| REJ-028 | Unlock reason recorded as a closed enum in the audit event, not a column | spec (audit event catalogue) | Unsurprising, and trivially reversible. |
| REJ-029 | Population declaration for six IM8 controls | register | A compliance declaration, graded where controls are graded. |
| REJ-030 | Vendor neutrality asserted by a closed seam register, not executed | register (seam rows) | Each seam is a register row. There is nothing to reverse. |
| REJ-031 | `TIMESTAMP(6) WITH TIME ZONE` with no Hibernate type settings | spec (data model); register (MySQL seam) | Adding the global settings would be harmless. |
| REJ-032 | Digest columns as lowercase hex `VARCHAR(64)` | spec (data model) | A column type with no trade-off worth reversing. |
| REJ-033 | `OCTET_LENGTH` and `key_version` range checks | test plan T-MFA-003, T-MFA-004 | The tests pin them. They compensate for `validate` not seeing widths. |
| REJ-034 | `ON DELETE CASCADE` as the single deletion mechanism; tombstone IDs unconstrained | test plan T-ADM-021 | Adding a foreign key fails the test at once, and the rationale says why the tombstone must outlive its rows. |
| REJ-035 | Password history purged by cascade on delete | spec (data model) | Follows from the cascade rule. Nothing to undo. |
| REJ-036 | Cross-table username-reuse race accepted; no reservations table | register (residual) | An accepted residual with a reopening trigger. No maintainer would add the table unprompted. |
| REJ-037 | Admin list sort columns deliberately unindexed | register | Adding an index is harmless. The reopening trigger is real user volume. |
| REJ-038 | No optimistic locking (`@Version`); admin edits are last-write-wins | register | Adding `@Version` would be an improvement, not a regression. |
| REJ-039 | No last-activity column | register (with the account-hygiene deferral) | Part of a declared deferral. |
| REJ-040 | Spring Session DDL copied verbatim, checked by blob hash | test plan T-BLD-001 | The test is the control. |
| REJ-041 | Migrations split by concern and append-only; Flyway `clean` disabled | spec (data model) | Standard migration hygiene. |
| REJ-042 | `user.id` recorded on failed logins for existing accounts | test plan T-AUD-025; register (existence-oracle residual) | The test fails if a maintainer follows the recipes, and its rationale states the trade. |
| REJ-043 | Lockout logged at WARN, not ERROR | spec (audit event catalogue) | The catalogue fixes each row's level, following the governing standard over the logging recipe. |
| REJ-044 | Three custom fields requested as an extension to the org log schema | register (integrator obligation) | An outstanding request to the schema owner, not a design choice. |
| REJ-045 | No `total-size-cap` on the audit appender | test plan T-AUD-011; register (disk monitoring); handover | The test pins it, and the deployer monitors the disk. |
| REJ-046 | Idle expiry observed lazily; no session reaper | register | A scope decline recorded with its residual. |
| REJ-047 | Standards precedence: governing user standard over the logging standard over recipes | spec (standards precedence) | A rule for reading the standards. The spec states it once. |
| REJ-048 | A rule for when an amendment reopens a decision | none | A planning-process rule. It has no subject once the plan is handed off. |
| REJ-049 | `423 FACTOR_DISABLED` on three surfaces | spec (error contract); register (off-label status) | Part of the error contract. The off-label use is registered. |
| REJ-050 | `409` for the two-admin invariant, `403 ACCESS_DENIED` for self-action | spec (error contract) | Part of the error contract. |
| REJ-051 | Sign-out is terminal in the SPA | test plan T-FE-017 | The test pins it. |
| REJ-052 | SPA retry predicate: retry only when there is no status | spec (frontend) | A client behaviour rule with no plausible regression. |
| REJ-053 | Document-wide `Referrer-Policy` meta tag, first in `<head>` | spec (frontend) | A header placement, stated in the spec. |
| REJ-054 | `style-src` left unsplit | register | Kept whole so IM8 as-9's literal check reads it correctly. A tooling accommodation. |
| REJ-055 | A dedicated ADR for the `GET /api/hello` authorization-matrix row | spec (authorization) | Trivially reversible, unsurprising, and no trade-off. It is one matrix row. |
| REJ-056 | Timing uniformity verified by call count, not wall clock | test plan T-AUTH-003; register (verified-by-mechanism) | The test is the control. |
| REJ-057 | Playwright, narrow: Chromium and Firefox, about eight tests | spec (test harness) | Easy to reverse, and harmless either way. |
| REJ-058 | Test suite runs sequentially | spec (test harness) | Easy to reverse. |
| REJ-059 | MFA response contract departs from the corpus's enrolment statuses | register (standards defects); spec (error contract) | The corpus contradicts itself, so there is no single prescribed form to revert to. |
| REJ-060 | `'unsafe-inline'` in two directives in `dev` only; `vite preview` as the CSP verification surface | test plan T-HDR-004 | The test pins the production CSP. |
| REJ-061 | `__Host-` cookie prefix, with a per-profile cookie name | test plan T-SES-011; register (draft-specification status) | The test pins it, and the prefix's draft status is registered. |
| REJ-062 | API-side CSP kept as labelled defence in depth | register | A labelling decision. The register records that it is not the XSS control. |
| REJ-063 | Kubernetes probes and `db` health disabled, `diskspace` kept | spec (observability); handover | Re-enabling probes is a deployer step, listed in the handover document. |
| REJ-064 | Three-class alert taxonomy, and absence detection at the collector | handover; spec (observability) | Alert rules live in the deployer's collector, not in the code. |
| REJ-065 | Frontend half of IM8 lm-16 declined (no client error ingestion) | register | A declared partial. |
| REJ-066 | Actuator health body exempt from the error envelope | test plan T-AUTH-017 | The test pins the exemption to `/actuator/**`. |
| REJ-067 | `server.tomcat.mbeanregistry.enabled: true` | test plan T-OBS-005 | The test and its rationale stop removal as cruft. |
| REJ-068 | The test harness disables OTLP export specifically | spec (test harness) | A harness setting, stated in the spec. |
| REJ-069 | TOTP provisioning is a POST, not the standard's GET | register; spec (MFA endpoints) | A secret-minting endpoint cannot be a safe method. The register records the deviation. |
| REJ-070 | TOTP code sent as a JSON body, not an `X-TOTP` header | register; spec (MFA endpoints) | Recorded deviation. Reversing it gains nothing. |
| REJ-071 | Successful enrolment confirmation grants the factor authority | spec (MFA endpoints) | Confirmation and verification share one mechanism. Stated in the spec. |
| REJ-072 | One unlock endpoint clears the password and TOTP tier-1 locks, never tier 2 | test plan T-LCK-005 | The test pins it. |
| REJ-073 | `412` for a missing factor, and other off-label statuses | register | Recorded deviations from RFC 9110 status semantics, kept for envelope consistency. |
| REJ-074 | TOTP accepts ±1 step of skew | test plan T-MFA-011; register (ASVS 6.5.5 (L2)) | A registered deviation, pinned by test. |
| REJ-075 | Handover row schema: responsibility, status, priority | spec (register and handover schema) | A document schema. |
| REJ-076 | Deployment-sequence ordering by application failure point | spec (register and handover schema) | A document ordering rule. |
| REJ-077 | Roles, not people, with vacancies as unmet acceptance checks | spec (register and handover schema) | A document convention. |
| REJ-078 | Count and first-seen fields on keyed audit rows | spec (audit event catalogue) | A row shape. |
| REJ-079 | Truncation row carries two exact counts and the truncated row's identity | spec (audit event catalogue) | A row shape. |
| REJ-080 | Audit keying constants: 20 sources, 500 users, 15-minute window | spec (rate limiting); trigger | Tunable constants with a recomputation trigger. Changing them is expected. |
| REJ-081 | Raw URI capped at 256 characters with a truncation marker | test plan T-AUD-024 | The test pins it. |
| REJ-082 | One audit row keyed rather than deleted | register (recipe deviation) | A recipe deviation with its residual recorded. |
| REJ-083 | Explicit `CookieSerializer` bean paired with the session-id resolver | test plan T-SES-031 | The bean looks redundant, and the test and its rationale stop its removal. |
| REJ-084 | Daily audit volume as a formula with one deployer input | spec (observability); handover | The deployer supplies the input and recomputes it. |
| REJ-085 | Generated three-registry endpoint coverage assertion | test plan T-ARCH-005; register (unverified exclusions) | The test is the control. |
| REJ-086 | Cheap ASVS L2 hooks adopted against the declared L1 target | register | A compliance note. |
| REJ-087 | Trace baggage disabled | spec (logging) | Trivially reversible. |
| REJ-088 | `correlation.id` dropped | spec (logging) | Unsurprising once `trace.id` is the join key. |
| REJ-089 | Runner-mode preconditions: full refresh, no migration, no seeder | spec (recovery runner) | Behaviour of the runner, stated in the spec and pinned by its tests. |
| REJ-090 | Runner audit shape: dry-run, intent and outcome rows | spec (audit event catalogue) | A row shape. |
| REJ-091 | Anonymous session expiry pinned at creation plus the idle interval | test plan T-SES-032; register (deviation) | The test pins it, and one sentence carries the reason. |
| REJ-092 | MFA error `detail` no longer prose-matched; clients branch on `code` | spec (error contract); register (MFA_Core §3.2 deviation) | The contract's closed `code` enum makes the alternative unworkable. |
