## Lists
| list | file | heading or first words | first line | last line | n tests | keys |
|---|---|---|---|---|---|---|
| L11-a | 11 | Authorization matrix: "an assertion test proving chain and annotations agree" | 175 | 175 | 1 | S11-01 |
| L11-b | 11 | Central guard: "Backed by an architecture test asserting no controller ..." | 247 | 247 | 1 | S11-02 |
| L11-c | 11 | Wire contract: "Two tests: an architecture test ... and an API-wide assertion" | 417 | 419 | 2 | S11-03, S11-04 |
| L11-d | 11 | Amendment from 09: "Keep the assertion test" (alwaysPerformAdditionalChecksOnUser) | 587 | 590 | 1 | S11-05 |
| L11-e | 11 | Amendment from 10: "one negative assertion added: the helper is never applied to a password field" | 658 | 658 | 1 | S11-06 |
| L11-f | 11 | Amendment from 23 §3: "ticket 23 adds a one-line assertion that no RoleHierarchy bean exists" | 682 | 683 | 1 | S11-07 |
| L11-g | 11 | Amendment from 12 §1: "Keep the API-wide test" | 722 | 722 | 0 | (ref on S11-04) |
| L11-h | 11 | Amendment from 15 §2: "the remedy is an assertion enumerated from this ticket's matrix" | 887 | 888 | 1 | S11-08 |
| L11-i | 11 | Amendment from ticket 16 (test plan) | 957 | 968 | 6 | S11-09 to S11-14 |
| L12-a | 12 | §2: "negative-test set that proves validation is running" (five items) | 270 | 285 | 5 | S12-01 to S12-05 |
| L12-b | 12 | §2: "And a second set for everything validate structurally cannot see" (invariant set) | 287 | 291 | 8 | S12-06 to S12-13 |
| L12-c | 12 | §3 argument 4: "A /do-work integration test covers it" | 323 | 327 | 1 | S12-14 |
| L12-d | 12 | §12: "One copy only ... A test strips the header" (blob-hash test) | 778 | 783 | 1 | S12-15 |
| L12-e | 12 | §12: "The functional gate is an integration test that persists a session ..." | 797 | 799 | 1 | S12-16 |
| L12-f | 12 | Handoffs → 16: "the five-item negative-test set and the DB-invariant set are yours" | 991 | 993 | 0 | (refs on S12-01, S12-07, S12-15) |
| L12-g | 12 | Amendment from 09 §R: "Both columns belong in your five-item negative-test set" | 1087 | 1088 | 1 | S12-17 |
| L12-h | 12 | Amendment from 29: "Ticket 29 §9 test 5 asserts it" | 1112 | 1112 | 1 | S12-18 |
| L12-i | 12 | §3 argument 1: "plus an API-wide test to enforce it" | 315 | 317 | 0 | (ref on S11-04) |

## Rows
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|
| S11-01 | ADM | Authorization matrix chain and @PreAuthorize agree | For every (role, method, path) entry of the bound authorization-matrix record, the authorizeHttpRequests chain decision and the @PreAuthorize expression on the routed service method agree (whitelist first, guards second, anyRequest().denyAll() last). | C | ctx-default | none | 11 §Authorization matrix | 11:175 | pos | | Role layer only; the factor layer is S11-08 (15 §2 records that @PreAuthorize is not defence in depth for the factor). |
| S11-02 | ADM | Central AdminActionGuard cannot be bypassed | No class in the admin controller package depends on a repository type directly; every admin mutation reaches persistence only through the single guarded service method. | A | archunit | none | 11 §Central guard | 11:247 | neg | | Pillar by control (central guard), not ARCH. |
| S11-03 | ADM | Entities never serialised | No controller method return type (including generic arguments such as ResponseEntity or Page) is or contains a JPA @Entity type; response records only. | A | archunit | none | 11 §Wire contract; PRD Story 8 | 11:417 | neg | | Source says "Two tests"; split per source. |
| S11-04 | ADM | No password hash or secret in any response | Across every endpoint and every response body including error envelopes, the strings passwordHash, totpSecret and emailHmac (field names) and the fixture's stored hash, TOTP secret and email HMAC values never appear. | C | ctx-default | keyed | 11 §Wire contract; PRD Story 8 | 11:418; 11:722; 12:315 | neg | | The API-wide "no password hash in any response" row. 11:722 (amendment from 12) keeps it with justification changed from enforcement to regression cover; 12:315 describes it as enforcing the never-return-the-PK rule, now vacuous (see problems). |
| S11-05 | AUTH | Additional checks always performed on user | The effective AbstractUserDetailsAuthenticationProvider (DaoAuthenticationProvider) bean has alwaysPerformAdditionalChecksOnUser = true, so disabled, expired and locked accounts take the same verify path (CVE-2026-22746 bypass not reintroduced). | C | ctx-default | none | 11 §Amendment from 09; CVE-2026-22746 | 11:587 | pos | ≡? 09 (CVE-2026-22746 assertion test) | "Keep the assertion test": the test is referenced here, likely listed in 09 or 06. |
| S11-06 | CRED | Canonicaliser never applied to passwords | The identifier canonicalisation helper (NFC, trim, lowercase) is never invoked on a password field: a password with mixed case, leading/trailing spaces and non-NFC input is hashed and verified byte-for-byte as submitted. | U | none | none | 11 §Amendment from 10; 10 canonicalisation | 11:658 | neg | ≡? 10 (canonicalisation negative assertion) | Level U assumed; could be A (no call path from password handling to the helper). |
| S11-07 | ADM | No RoleHierarchy bean | The application context contains no RoleHierarchy bean, so the hand-built AuthorityAuthorizationManager's NullRoleHierarchy default is correct. | C | ctx-default | none | 23 §3 (amendment to 11) | 11:682 | neg | ≡? 23 (RoleHierarchy assertion) | Owned by ticket 23 per source ("ticket 23 adds"). |
| S11-08 | MFA | Factor matrix over admin routes | Enumerated from the bound authorization matrix, not hand-written: for every /api/admin/** route and method, an ADMIN without FACTOR_TOTP is refused, GET succeeds with a TOTP factor of any age, and mutations are refused with a factor older than 10 minutes and succeed within it; one parameterised test. | C | ctx-default | keyed | TM-04; IM8 ac-2 | 11:887 | neg | ≡? 23:1167; 15 #1 | 16 §9: 11:888, 23:1167 and 15 #1 are one test; pillar MFA per 16 §2. Item starts at 11:887; 16 §9 cites 11:888. |
| S11-09 | ADM | Duplicate role definitions fail fast | A startup with a duplicated role name in app.security.roles aborts context refresh in the refresh-phase validator before the port binds; the duplicate is not collapsed silently. | C | restart | own-DB | Std §5:424 | 11:957 | neg | | Own SpringApplicationBuilder run expected to fail; no fixed context fits, restart harness used. Source says "duplicate", caller brief said "unknown" (see problems). |
| S11-10 | ADM | Role-definition paths: anonymous refused | Every HTTP method on /api/admin/roles/** from an anonymous caller returns the entry point's 401. | C | ctx-default | keyed | Std §5:425 | 11:958; 11:960 | neg | | Parameterised over all methods; one row per 16 amendment brief. |
| S11-11 | ADM | Role-definition paths: explicit denyAll | Every HTTP method on /api/admin/roles/** returns 403 with envelope code ACCESS_DENIED for both a USER and an ADMIN; mutating methods carry a valid CSRF token (not CSRF_TOKEN_INVALID), the ADMIN holds no TOTP factor (not the factor guard's 412/422), and fixtures have forcePasswordChange clear (not PASSWORD_CHANGE_REQUIRED). | C | ctx-default | keyed | Std §5:425 | 11:961 | neg | | One parameterised row (principal × method); CSRF, no-TOTP and forcePasswordChange-clear are fixture preconditions from 11:962–964. |
| S11-12 | ADM | Matrix change takes effect on next load | Two-run restart harness: run 1 binds matrix A and a request is decided per A; the context is fully closed, the matrix is changed, run 2 on the same file path decides the same request per the changed matrix. | C | restart | own-DB | Std §5:426 | 11:965 | pos | | Matrix binds once at refresh; restart is the only reload path. |
| S11-13 | ADM | Re-enable forces password change | After an admin re-enables a disabled user, that user's next request outside the five-path forced-change allowlist returns 403 PASSWORD_CHANGE_REQUIRED. | C | ctx-default | keyed | Std §5:485 | 11:967 | neg | | Decision at 11:229–230. |
| S11-14 | ADM | USER cannot read a user by id | A USER calling GET /api/admin/users/{uuid} gets 403. | C | ctx-default | keyed | Std §5:479 | 11:968 | neg | | |
| S12-01 | CFG | Schema gate: UUID retype caught | With a UUID column retyped to VARCHAR(36), startup schema validation fails. | C | restart | own-DB | 12 §2 | 12:273; 12:991 | neg | | Own schema per mutation; run outside the cached contexts. 12:991 hands the whole five-item set to 16. |
| S12-02 | CFG | Schema gate: totp_key BINARY retype caught | With totp_key retyped from VARBINARY(69) to BINARY(69), startup schema validation fails on H2 2.x. | C | restart | own-DB | 12 §2 | 12:276 | neg | | Held as an assertion about H2 2.x metadata. |
| S12-03 | CFG | Schema gate: NAMED unique-key validation active | With ux_users_username dropped, startup schema validation fails (proves NAMED is active and ux_ escapes the IDX skip). | C | restart | own-DB | 12 §2 | 12:281 | neg | | |
| S12-04 | CFG | Schema gate: composite index order checked | With the columns of ix_credential_tokens_user_id_type reordered, startup schema validation fails. | C | restart | own-DB | 12 §2 | 12:283 | neg | | |
| S12-05 | CFG | Schema gate: case false-failure absent | Lowercase mapped names against H2's upper-cased metadata pass validation. | C | restart | own-DB | 12 §2 | 12:284 | pos | | |
| S12-06 | CFG | DB invariant: NOT NULL columns | For each NOT NULL column in V1–V6, an insert or update writing null is rejected by the database; asserted outside the persistence context that made the change. | C | ctx-default | keyed | 12 §2 | 12:287 | neg | | Parameterised over columns. The §R counter's NOT NULL is carried in S12-17, not here. Fresh-transaction rule (12:293) applied. |
| S12-07 | MFA | DB invariant: TOTP ciphertext width | Both ck_totp_user_details_totp_key_len and ck_pending_totp_totp_key_len reject 48-byte and 81-byte totp_key values and accept 69 bytes; asserted in a fresh transaction. | C | ctx-default | keyed | 12 §4 | 12:287; 12:991 | neg | | "Both OCTET_LENGTH checks" as one parameterised row (table × width). |
| S12-08 | MFA | DB invariant: key_version range | ck_*_key_version_range on both MFA tables rejects key_version outside 0..255 (e.g. -1, 256) and accepts 0 and 255. | C | ctx-default | keyed | 12 §4 | 12:289 | neg | | |
| S12-09 | CRED | DB invariant: token type check | ck_credential_tokens_type rejects a type value other than ACTIVATION or PASSWORD_RESET. | C | ctx-default | keyed | 12 §6 | 12:289 | neg | | |
| S12-10 | CFG | DB invariant: foreign keys | Every foreign key (users.role → roles, and user_id on credential_tokens, password_history, totp_user_details, pending_totp) rejects a row referencing a non-existent parent. | C | ctx-default | keyed | 12 §6 | 12:290 | neg | | Parameterised over FKs. |
| S12-11 | ADM | DB invariant: delete cascades | Deleting a users row removes its credential_tokens, password_history, totp_user_details and pending_totp rows via ON DELETE CASCADE, and does not remove SPRING_SESSION rows; checked outside the deleting persistence context. | C | ctx-default | keyed | 12 §7 | 12:290 | neg | | Sessions-not-cascaded facet from 12 §7 kept inside the row. |
| S12-12 | CRED | DB invariant: password-history eviction | On password set, history is trimmed to the configured history-length (current plus two priors), evicting the oldest by created_at. | C | ctx-default | keyed | 12 §7 | 12:290 | pos | | |
| S12-13 | ADM | DB invariant: tombstone blocks both identifiers | A deleted user's username and email (by canonical email HMAC) are both refused for reuse by admin create (USER_EXISTS) and by self-registration (uniform response, nothing created). | C | ctx-default | keyed | 12 §6; 11 §Soft delete | 12:290 | neg | | |
| S12-14 | CRED | UUID assigned before execution | After persist() of a new user, its @UuidGenerator id is readable without a flush, and registration inserts the user and its ACTIVATION token in one transaction. | C | ctx-default | keyed | 12 §3 | 12:323 | pos | | Source: "A /do-work integration test covers it". |
| S12-15 | BLD | Session DDL verbatim copy | The test strips the V7__spring_session.sql header (leading run of -- lines plus one blank line), asserts the remainder begins CREATE TABLE SPRING_SESSION, then asserts SHA1("blob " + length + "\0" + bytes) equals be6e515720a5434a898bcb6d186f42d7b4766006. | B | build | none | 12 §12 | 12:778; 12:991 | neg | | Strip-failure check is a facet of the one test. Pillar BLD (artefact inspection); SES arguable. |
| S12-16 | SES | Session schema functional gate | A session carrying a FactorGrantedAuthority is persisted through JdbcIndexedSessionRepository and read back by principal name with the authority intact. | C | ctx-default | keyed | 12 §12 | 12:797 | pos | | Exercises IX3, LONGVARBINARY and JDK serialisation. |
| S12-17 | LCK | DB invariant: NIST cap columns | consecutive_failures_since_success rejects null and defaults to 0; password_disabled_at accepts null and round-trips a microsecond-precision instant as TIMESTAMP(6) WITH TIME ZONE; asserted in a fresh transaction. | C | ctx-default | keyed | 09 §R; 12 §2 | 12:1087 | neg | | 16 §9 applied: the source says "your five-item negative-test set", but these join the invariant set (S12-06 to S12-13), not the five-item set. 16 §9 cites 12:1088; item starts 12:1087. |
| S12-18 | SES | No negative MAX_INACTIVE_INTERVAL saved | No code path saves a SPRING_SESSION row with a negative MAX_INACTIVE_INTERVAL (DELETE_SESSION_QUERY would never reclaim it). | C | ctx-default | keyed | 29 §9 | 12:1112 | neg | ≡? 29 §9 test 5 | Owned and listed by ticket 29; referenced here only. |

## Not transcribed
| file:line | item | reason |
|---|---|---|
| 11:155 | YAML-versus-table startup validator | No test is stated as owed for the mismatch check; only the §5:424 duplicate check has an owned test (S11-09). |
| 11:389 | Bootstrap fails fast when the configured username is a tombstone | No test is stated in 11; per 16 §9 the 16:262 tombstone case belongs to the runner (ticket 30). |
| 11:759 | MySQL accent-insensitive collision (seam 6) | "Asserted and never executed"; out of harness scope (16 §6(a)). |
| 11:966 | Standard §5:473 | N/A by construction; USERNAME_CHANGE_NOT_ALLOWED removed. |
| 11:962 | CSRF valid on mutating methods | Fixture precondition of S11-11, not a separate test. |
| 11:963 | ADMIN holds no TOTP factor | Fixture precondition of S11-11, not a separate test. |
| 11:964 | forcePasswordChange clear on fixtures | Fixture precondition of S11-11, not a separate test. |
| 12:246 | mvn dependency:tree confirms Hibernate 7.4.5.Final | First-commit check handed to /do-work, not a test. |
| 12:293 | Every assertion runs outside the persistence context that made the change | Rule applied to S12-06 to S12-13 and S12-17, not a test. |
| 12:496 | credential_tokens "stores only a hash" DDL comment | DDL comment owed, not a test. |
| 12:854 | Seam register (six entries) | Asserted by review and never executed (16 §6(a)). |
| 12:1046 | Embedded-datasource fallback "worth one line in the test plan" | Explanatory line for the plan; the fail-closed absence check is ticket 24's test. |

## Counts
| source ticket/section | n tests | keys |
|---|---|---|
| 11 §Authorization matrix (175) | 1 | S11-01 |
| 11 §Central guard (247) | 1 | S11-02 |
| 11 §Wire contract (417–419) | 2 | S11-03, S11-04 (also refs 11:722, 12:315) |
| 11 Amendment from 09 (587) | 1 | S11-05 |
| 11 Amendment from 10 (658) | 1 | S11-06 |
| 11 Amendment from 23 (682) | 1 | S11-07 |
| 11 Amendment from 15 §2 (887) | 1 | S11-08 |
| 11 Amendment from 16 (957–968) | 6 | S11-09 to S11-14 |
| 12 §2 five-item set (273–285) | 5 | S12-01 to S12-05 (S12-01 also ref 12:991) |
| 12 §2 invariant set (287–291) | 8 | S12-06 to S12-13 (S12-07 also ref 12:991) |
| 12 §3 (323) | 1 | S12-14 |
| 12 §12 blob-hash (778) | 1 | S12-15 (also ref 12:991) |
| 12 §12 functional gate (797) | 1 | S12-16 |
| 12 Amendment from 09 §R (1087) | 1 | S12-17 |
| 12 Amendment from 29 (1112) | 1 | S12-18 |
| **Total** | **32** | |
