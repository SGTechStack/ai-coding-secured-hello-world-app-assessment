# Ticket 17 sizing gate — inventory part C (tickets 10, 11, 12)

Files read in full: `10-credential-flows.md` (820 lines), `11-admin-module-role-model-and-bootstrap.md` (968 lines),
`12-data-model-reconciliation.md` (1113 lines). Line numbers are from the files as they stand now.

Conventions used in this part:

- Rows owned by a ticket outside group C, where that ticket's own numbering is unknown here, carry an `x` prefix on
  `n` (e.g. `15-R-x1`) so they cannot collide with the owning group's own numbering. The aggregator should renumber.
- `adr-amend` rows name the target as `→ NN-A-k` where the target is in this part, or `→ NN ADR (# unknown)`.
- No heading spelled exactly `### Handover items (ticket 25)` exists in any of the three files, so the `handover`
  kind is empty. Items routed to ticket 25 sit under `### Handoffs` or in prose and are noted in the tally.
- No candidate in these files is rejected *as failing the three-part test*, so `adr-rejected` is empty. Rejected
  design alternatives (OTT, UUIDv7, reservations table, HMAC-for-tokens, Flyway seeding) live inside their ADRs.

## 1. Items

### Ticket 10

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 10-A-1 | adr-new | 10 | 10:600–603 (also 10:44, 10:120, 10:244–246) | Self-registration split into two steps: uniform 202, password moved to activation; username conflicts specific, email axis uniform; also records the gating choice's corrected grounds. | live | adr |
| 10-A-2 | adr-new | 10 | 10:604–606 | Admin-create becomes an `ACTIVATION` invite token, amending ticket 11; retires "undeliverable link is a bricked account"; cites 6.4.6, 6.4.1. | live | adr |
| 10-A-3 | adr-new | 10 | 10:607–608 | Admin-initiated reset issuance exists and is token-based, satisfying §4:401 and resolving §2-vs-§3.5 contradiction against §3.5. | live | adr |
| 10-A-4 | adr-new | 10 | 10:609–611 (also 10:318–320, 10:342) | One `credential_tokens` table, domain-separated SHA-256; why not HMAC, why 256 bits first-principles, why Spring OTT declined on behaviour. | live (amended: 10-M-2, 10-M-3) | adr |
| 10-A-5 | adr-new | 10 | 10:612–613 | Self-service change endpoint exists despite §2:121/§4:401; always requires current password, no forced-change exemption (deviates §2:166–167). | live | adr |
| 10-A-6 | adr-new | 10 | 10:614–615 | Redemption clears password lockout, never TOTP state; WSTG-ATHN-03 tier 2; supersedes ticket 09's accepted malicious-lockout residual. | live (amended: 10-M-1) | adr |
| 10-A-7 | adr-new | 10 | 10:616 (also 10:561–567) | ASVS 6.2.9 (L2) failed for passwords over 72 UTF-8 bytes, consequence of ticket 07's declined pre-hash; non-Latin users affected. | live | register |
| 10-A-8 | adr-new | 10 | 10:617–618 (also 10:344–360) | Reset/activation link origin never from request-controlled input (Host, X-Forwarded-*, body, query); test list superseded by T-CRED-016/017/018. | live | borderline |
| 10-M-1 | adr-amend | 10 | 10:714–729 (from 09 §R) | → 10-A-6: redemption also clears `consecutive_failures_since_success` and `password_disabled_at` (NIST rebinding); issuance and unlock clear nothing. | live; runner-mints claim at 10:727–729 superseded by 10:812–817 | |
| 10-M-2 | adr-amend | 10 | 10:677–710 (from 12) | → 10-A-4: admin-soft-delete invalidation trigger deleted (cascade instead), five triggers become four; hex `VARCHAR(64)` hash; "stores only a hash" DDL comment. | live | |
| 10-M-3 | adr-amend | 10 | 10:768–806 (from 25) | → 10-A-4: new issued-recovery-code token type (24h email TTL, §3.2.2 throttling) and a recovery-address axis distinct from login address. | live; minting-source claim at 10:775 superseded by 10:812–817 | |
| 10-M-4 | adr-amend | 10 | 10:808–820 (from 30) | → 10-M-1/10-M-3: ticket 09 runner mints nothing (ticket 28 inverted it); admin-reset endpoint becomes 11-A-13's route 2. | live | |
| 10-R-1 | register | 10 | 10:122–128 | Standards defect 7 (ticket 10's count): Standard mandates two mutually exclusive admin-reset models; Q22:565 gives a third reading. | live | |
| 10-R-2 | register | 10 | 10:130–136 | Self-registration and user-initiated reset absent from the Standard; §4:401 leaves no reset-request slot. Unblessed design space, not a deviation. | live | |
| 10-R-3 | register | 10 | 10:138–141 | Standards defect 8: SHA-256 for tokens not mandated (non-normative note); Questions file stricter than Standard. | live | |
| 10-R-4 | register | 10 | 10:141–144 | Standards defect 9: 30-minute expiry stated in four incompatible modalities; no conformance test writable as printed. | live | |
| 10-R-5 | register | 10 | 10:146–148 | Standards defect 10: per-account limit on redemption unknowable before token lookup; forces ticket 09's per-IP keying. | live | |
| 10-R-6 | register | 10 | 10:149–152 | Corpus defect (unnumbered): self-service recipe never calls `PasswordPolicy.validate()`, current-password check unreachable, `revokeOtherSessions` never invoked. | live | |
| 10-R-7 | register | 10 | 10:199–204 | Residual: looping re-registration invalidates victim's activation token about every 12 s at 5/min; accepted, per-token counting not built. | live | |
| 10-R-8 | register | 10 | 10:245–246, 10:559–560 | ASVS 6.3.8 (L3) knowingly failed on username axis, bounded 5/min per IP; silent-202 alternative rejected (unfixable squat). | live | |
| 10-R-9 | register | 10 | 10:371–374 | Standards defect 11: redemption versus account state undefined (§2:131 covers only lock-not-cleared by admin reset). | live | |
| 10-R-10 | register | 10 | 10:376–382 | Accepted UX cost: reset against never-activated account is a no-op; owner's route to fresh activation link is re-registration. | live | |
| 10-R-11 | register | 10 | 10:409–413 | Automatic session invalidation on redemption chosen over cheat sheet's ask-the-user alternative; recorded as a choice, not omission. | live | |
| 10-R-12 | register | 10 | 10:435–437 | Standards defect 12: forced-change completion omits current password and history (§2:166–167) against §3.1:240. | live | |
| 10-R-13 | register | 10 | 10:448–451 | Standards defect 13: §2:121 and §4:401 do not admit a self-service change endpoint (deviation carried by 10-A-5). | live | |
| 10-R-14 | register | 10 | 10:491–494 | Standards defect 14: notification requirement only in test section §5:517; no notification at token issuance. | live | |
| 10-R-15 | register | 10 | 10:520–533 | Log leak: stub logs reset link, total takeover for users, partial for admins (TOTP-contained, confidentiality-only); deployment-blocking for ticket 25. | live; narrowed to `dev` by 10:731–742, sharpened by 10:800–806 | |
| 10-R-16 | register | 10 | 10:537–541 | ASVS target declared L1 plus cheap L2/L3; whole-app L2 not claimable because 6.3.3 needs MFA for regular users (out of scope). | live | |
| 10-R-17 | register | 10 | 10:574–578 | ASVS 6.2.7 (L1) at risk: paste/password managers, `autocomplete` values; mostly ticket 14's item. | live | |
| 10-R-18 | register | 10 | 10:579–584 | ASVS 6.3.4 (L2): seven authentication pathways; ticket 25 owes a pathway table (also a handover item under non-exact heading 10:643–644). | live | |
| 10-G-1 | glossary | 10 | 10:622 | pending registration | live | |
| 10-G-2 | glossary | 10 | 10:622 | activation token | live | |
| 10-G-3 | glossary | 10 | 10:622 | invite token | live | |
| 10-G-4 | glossary | 10 | 10:622 | credential token | live | |
| 10-G-5 | glossary | 10 | 10:622 | domain-separated token hash | live | |
| 10-G-6 | glossary | 10 | 10:623 | link origin | live | |
| 10-G-7 | glossary | 10 | 10:623 | authentication pathway | live | |
| 10-T-1 | trigger | 10 | 10:206–213 (also 10:664–665) | Self-service TOTP enrolment ever added reopens §2: unactivated record could accumulate a factor (CVE-2026-56081). | live | |
| 11-X-1 | adr-reversed | 11 | 10:604–606, 10:660–663; 11:613–634 | 11-A-10 (admin-created users immediately active with 20-char password) flipped to invite token by ticket 10. | withdrawn by 11:613 (10:604) | |
| 11-M-1 | adr-amend | 11 | 10:660–663; 11:624–631 | → 11-A-11: forced-change cases reduce four→two (bootstrap seed, re-enable); `credentialIssuedAt` not stamped on admin create/reset. | live (see 11-M-6, 11-M-12) | |
| 11-M-2 | adr-amend | 11 | 10:284–293, 10:660–661; 11:636–642 | → 11-A-13: invariant predicate becomes `activated_at IS NOT NULL` AND TOTP-enrolled, so pending invites never count. | live | |
| 06-M-x1 | adr-amend | 06 | 10:648–650 | → 06 ADR (# unknown): `VALIDATION_FAILED` gains `USERNAME_UNAVAILABLE`; enum stays 13; activation failure reuses `RESET_TOKEN_INVALID`; rule list = 07's six. | live (enum count challenged by 11:966) | |
| 07-M-x1 | adr-amend | 07 | 10:651–653, 10:570–573 | → 07 ADR (# unknown): 20-char admin generator deleted; context word list extended (6.1.2/6.2.11); 6.2.9 recorded failed. | live | |
| 08-M-x1 | adr-amend | 08 | 10:654–656 | → 08 ADR (# unknown): activation/invite redemption added as vacuous invalidation rows; `api.base-path` is `/api`, not `/api/v1`. | live | |
| 09-M-x1 | adr-amend | 09 | 10:388–391, 10:657–659 | → 09 register (accepted malicious-lockout residual): superseded by self-service unlock via redemption; change endpoint does not feed lockout counter. | live | |

### Ticket 11

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 11-A-1 | adr-new | 11 | 11:505–506 | Role model: two roles, one per user, YAML truth plus Flyway-seeded read-only `roles` table with FK and startup validator; no privileges/hierarchy/synchroniser. | live (amended: 11-M-15) | adr |
| 11-A-2 | adr-new | 11 | 11:507 | Authority naming `ROLE_*` with `hasRole`, rejecting both corpus conventions. | live | borderline |
| 11-A-3 | adr-new | 11 | 11:508–509 | Authorization matrix adopted narrowly, whitelist-first; doubles as ac-4 baseline, scheduled revoke deferred. | live (amended: 11-M-10, 11-M-13) | adr |
| 11-A-4 | adr-new | 11 | 11:510 | Path scheme `/api/admin/**` in controller mappings; `spring.mvc.servlet.path` prohibited (CVE-2026-22753). | live | adr |
| 11-A-5 | adr-new | 11 | 11:511 | Story 10 permitted; §3.5:384 read as barring self-assignment only. | live | adr |
| 11-A-6 | adr-new | 11 | 11:512 | Batch password reset declined; `BATCH_TOO_LARGE` removed from ticket 06's enum. | live | borderline |
| 11-A-7 | adr-new | 11 | 11:513–514 | Tombstone: HMAC email, plaintext username, indefinite retention, both reuses blocked, PDPA framing, pseudonymisation-not-anonymisation limit. | live (amended: 11-M-7) | adr |
| 11-A-8 | adr-new | 11 | 11:515–516 | Canonicalisation NFC/trim/lowercase, no dot/tag folding, shared live and tombstone; H2 lacks expression indexes. | live (amended: 11-M-8, 11-M-9) | adr |
| 11-A-9 | adr-new | 11 | 11:517 | Username rejects `@`. | live | borderline |
| 11-A-10 | adr-new | 11 | 11:518–519 | Admin-created users immediately active (stubbed transport), credential-leak cost acknowledged, NIST temporary-credential claim not relied on. | withdrawn by 11:613 (10:604–606); see 11-X-1 | adr |
| 11-A-11 | adr-new | 11 | 11:520 | Lazy 30-day forced-change expiry checked at login instead of a reaper, behind generic 401. | live (amended: 11-M-1, 11-M-6, 11-M-12; reopened by 09-T-x1) | adr |
| 11-A-12 | adr-new | 11 | 11:521–523 | Bootstrap: refresh-phase validation plus runner seeding; Flyway rejected on checksum immutability; one admin, conditional on second enrolled admin invited pre-go-live. | live (amended in place by 30, 11:934–940; 11-M-11) | adr |
| 11-A-13 | adr-new | 11 | 11:524–528 | Two-admin invariant: removal-only guard, pessimistic row lock on disable/demote/delete, factor reset exempt; three recovery routes keyed on `authenticable`. | live (amended: 11-M-2, 11-M-3, 11-M-4, 11-M-5, 11-M-14; wording per ticket 30 §4) | adr |
| 11-A-14 | adr-new | 11 | 11:529 | Unlock reason as closed enum in the audit event, not a column. | live | borderline |
| 11-A-15 | adr-new | 11 | 11:530 (also 11:459–475) | Population declaration (six controls) with its reopening trigger. | live | borderline |
| 11-M-3 | adr-amend | 11 | 11:743–752 (from 12) | → 11-A-13: lock set widens to `users` → `totp_user_details` on all mutating paths; guard is decrement-safe, not phantom-safe. | live ("four paths" narrowed to three by 11:946) | |
| 11-M-4 | adr-amend | 11 | 11:801–830 (from 09 §R) | → 11-A-13: auto-expiry closes lockout path, operator rebinding closes cap path; invariant monitorable not enforceable; one `authenticable` predicate, two readers. | live; route wording superseded by 11:524–528 (30) | |
| 11-M-5 | adr-amend | 11 | 11:890–913 (from 15, TM-14) | → 11-A-13: invariant guarded on mutation paths, monitored everywhere; three non-mutation channels (cascade, NIST cap, out-of-band) must feed ticket 21. | live | |
| 11-M-14 | adr-amend | 11 | 11:934–952 (from 30) | → 11-A-12/11-A-13: seeding conditional; `DELETE .../totp` exempt from count; four mutating paths become three; TM-08 framing superseded. | live | |
| 11-M-6 | adr-amend | 11 | 11:832–845 (from 09 §R.9) | → 11-A-11: third forced-change case (tier-2 TOTP disable) on the flag, `credentialIssuedAt` not stamped; ADR must say "deferred, not proportionate". | live | |
| 11-M-7 | adr-amend | 11 | 11:728–734 (from 12) | → 11-A-7: tombstone `uuid` renamed `user_id`; neither it nor `deleted_by_id` can be a foreign key. | live | |
| 11-M-8 | adr-amend | 11 | 11:754–760; 12:864–876, 12:965–970 (from 12) | → 11-A-8: seam entry 6, MySQL `utf8mb4_0900_ai_ci` folds accents NFC deliberately keeps, contradicting the canonicalisation principle. | live | |
| 11-M-9 | adr-amend | 11 | 11:762–768; 12:392–415 (from 12) | → 11-A-8: canonical value replaces stored value; username canonicalisation rejects, email transforms; ADR must name case-sensitive-provider reset failure mode. | live | |
| 11-M-10 | adr-amend | 11 | 11:864–875 (from 15, TM-05) | → 11-A-3: `GET /api/hello` gets a matrix row, `ROLE_USER`, no factor, not on forced-change allowlist. | live | |
| 11-M-11 | adr-amend | 11 | 11:847–858, 10:744–756 (from 09 §R.5) | → 11-A-12: reserved-name denylist, one set, two readers (bootstrap and registration validators); availability control, explicitly not 6.3.2. | live | |
| 11-M-12 | adr-amend | 11 | 12:1093–1099 (from 28) | → 11-A-11: break-glass operator password stamps `credential_issued_at`, a further forced-change trigger. Lists admin create/reset, stale against 11-M-1. | live (internally inconsistent; see §3) | |
| 11-M-13 | adr-amend | 11 | 11:957–965 (from 16) | → 11-A-3: duplicate role definitions fail fast; explicit `denyAll()` for `/api/admin/roles/**` before admin guards; restart is only reload path. | live | |
| 11-M-15 | adr-amend | 11 | 11:677–683 (from 23) | → 11-A-1: role-hierarchy exclusion now load-bearing for hand-built `AuthorityAuthorizationManager`; assert no `RoleHierarchy` bean exists. | live | |
| 11-R-1 | register | 11 | 11:186–187, 11:508–509, 11:548 | ac-4 scheduled compare-and-revoke half deferred with justification; baseline declared via authorization matrix. | live | |
| 11-R-2 | register | 11 | 11:463–464 | Population declaration: ac-12 (SSO for internal services) N/A; admins are application-local, external for this build. | live | |
| 11-R-3 | register | 11 | 11:465 | Population declaration: ac-8 (automated account lifecycle) N/A; manual admin CRUD appropriate. | live | |
| 11-R-4 | register | 11 | 11:466–467 | Population declaration: ac-7 binds and is satisfied by create/disable/delete endpoints plus audit trail. | live | |
| 11-R-5 | register | 11 | 11:468–469 | Population declaration: dp-8 (classification labels) binds regardless; remains ticket 14's. | live | |
| 11-R-6 | register | 11 | 11:470 | Population declaration: lm-18 N/A, declaration-dependent. | live | |
| 11-R-7 | register | 11 | 11:471 | Population declaration: st-3 N/A, declaration-dependent. | live | |
| 11-R-8 | register | 11 | 11:489 | Corpus defect 7 (ticket 11's count): `createUser` checks tombstones for username but not email — recovery-channel hijack. | live | |
| 11-R-9 | register | 11 | 11:490–491 | Corpus defect 8: two prescribed forced-change allowlists mutually incompatible; neither works with session-bound header-only CSRF. | live | |
| 11-R-10 | register | 11 | 11:492 | Corpus defect 9: `updateUser` permits self-disable, contradicting Decision Logic §120 and PRD Story 9. | live | |
| 11-R-11 | register | 11 | 11:493 | Standards defect 10: §3.5:384 "or others" contradicts the admin recipe's `existing.setRoles(request.roles())`. | live | |
| 11-R-12 | register | 11 | 11:494–495 | Corpus defect 11: RBAC `url-guards` does not bind or compile; `hasRole('USERS_*')` guards deny universally. | live | |
| 11-R-13 | register | 11 | 11:496 | Corpus defect 12: three authority conventions for one resource (`SELF_READ` / `USERS_*` / `USER_READ`), never reconciled. | live | |
| 11-R-14 | register | 11 | 11:498–501 | Compile-level corpus defects: `Set.of` over `Optional`, `Paths.get` for URLs, missing `@Slf4j`, batch cap 413-vs-400 indecision. | live | |
| 11-R-15 | register | 11 | 11:298–300 | Deliberate deviation: lowercasing the whole email address goes beyond RFC 5321's guarantee. | live | |
| 11-R-16 | register | 11 | 11:313–314 | Visible product cost of `@`-free usernames: users cannot log in with their email address; PRD silent. | live | |
| 11-R-17 | register | 11 | 11:454–457 | Unlock accountability record lives on a platform 90-day TTL nobody here configures, while its tombstone is indefinite. | live | |
| 11-G-1 | glossary | 11 | 11:532 | Deleted-user tombstone | live | |
| 11-G-2 | glossary | 11 | 11:532 | Canonical identifier | live | |
| 11-G-3 | glossary | 11 | 11:532–533 | Forced-change credential | live | |
| 11-G-4 | glossary | 11 | 11:533 | Enrolled admin | live | |
| 11-T-1 | trigger | 11 | 11:473–475 | If administrators are ever internal officers, ac-12 binds admin login and the SSO standard supersedes the admin authentication design. | live | |
| 09-T-x1 | trigger | 09 | 11:783–799 | Reopen: 30-day expiry's `CredentialsExpiredException` audit reason fires only on correct password — password oracle for log readers; such checks belong pre-auth. | live | |
| 23-R-x1 | register | 23 | 11:695–701 | Residual: first-enroller race — whoever reaches provisioning with the seed password first binds their authenticator; password-only enrolment is NIST-compliant. | live | |
| 15-R-x1 | register | 15 | 11:917–924 | Accepted risk TM-08: any admin can take over any other admin (reset + TOTP delete); detector row 18 `ADMIN_RESET`, 90-day horizon, ASVS 16.4.2 F. | live; framing "bounded by guard" superseded by 11:948–952 | |
| 15-R-x2 | register | 15 | 11:925–930 | Accepted risk TM-09: tombstone records no role; after 90 days privilege of a deleted account is unknowable; ac-7 binds; column declined on PDPA grounds. | live | |
| 15-R-x3 | register | 15 | 11:877–888 | TM-04: factor gate has one layer (request matchers) while role gate has two; accepted, remedy an enumerated assertion owed by ticket 16. | live | |
| 16-R-x1 | register | 16 | 11:966 | Standard §5:473 N/A by construction: no generic update endpoint, no lock route; `USERNAME_CHANGE_NOT_ALLOWED` removed from 06's enum. | live | |
| 07-M-x2 | adr-amend | 07 | 11:564–572 | → 07/02 ADR (# unknown): 800-63B-4 §3.1.1.1 15-char floor is a SHALL for single-factor; ADR says we were below a SHALL; USER population forces it. | live | |
| 06-M-x2 | adr-amend | 06 | 11:560, 11:966 | → 06 ADR (# unknown): `BATCH_TOO_LARGE` removed (enum 13); later `USERNAME_CHANGE_NOT_ALLOWED` also removed by ticket 16. | live; enum count now inconsistent (see §3) | |
| 03-M-x1 | adr-amend | 03 | 11:557, 11:451–454 | → 03 C8 schema amendment: package owes three custom fields (`user.target.id`, `.roles`, `.unlockReason`), not one. | live | |
| 19-M-x1 | adr-amend | 19 | 11:561–563 | → 19 ADR (# unknown): `Encryptors.stronger()` prohibition over-broad; rule is use `AesGcmBytesEncryptor`, never two-arg `AesBytesEncryptor`. | live | |

### Ticket 12

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 12-A-1 | adr-new | 12 | 12:919 (also 12:296–337) | UUID as sole primary key, application-generated `@UuidGenerator`, v4 not v7 because v7 discloses creation time. | live | adr |
| 12-A-2 | adr-new | 12 | 12:920 (also 12:202–219) | Vendor-neutrality restated as a closed six-entry seam register, asserted by review, never executed. | live | adr |
| 12-A-3 | adr-new | 12 | 12:921–923 (also 12:221–291) | `validate` demoted to typo-catcher; gate is a negative-test set, `NAMED` index/unique validation, `ux_`/`ix_` prefixes clearing the `IDX` skip. | live (amended: 12-M-1, 12-M-2) | adr |
| 12-A-4 | adr-new | 12 | 12:924–925 | `TIMESTAMP(6) WITH TIME ZONE` with zero Hibernate type settings, over plain `TIMESTAMP` plus two globals. | live | adr |
| 12-A-5 | adr-new | 12 | 12:926 | Digest columns as lowercase hex `VARCHAR(64)`, not `VARBINARY(32)` nor `CHAR(64)`. | live | borderline |
| 12-A-6 | adr-new | 12 | 12:927–928 | `OCTET_LENGTH` and `key_version` range checks as compensating control for validate's width blindness. | live | borderline |
| 12-A-7 | adr-new | 12 | 12:929–930 | Canonical value replaces stored value; username canonicalisation rejects, email transforms, reset-delivery failure mode named. | live | adr |
| 12-A-8 | adr-new | 12 | 12:931–932 | `ON DELETE CASCADE` single deletion mechanism; tombstone `user_id` and `deleted_by_id` deliberately unconstrained. | live | adr |
| 12-A-9 | adr-new | 12 | 12:933 | Password history purged by cascade, retention argument and NIST's silence on history recorded. | live | borderline |
| 12-A-10 | adr-new | 12 | 12:934–935 | Cross-table reuse race accepted; reservations table declined on key-dependency and partial-fix arguments. | live | adr |
| 12-A-11 | adr-new | 12 | 12:936–939 | Tombstone HMAC key rotation exception justified (blinding key); distinct key; HKDF pre-empted; forward-only versioning adopted, needs no version column. | live (text already folds in 12-X-1) | adr |
| 12-A-12 | adr-new | 12 | 12:940 | Shared primary keys on 1:1 MFA tables; enrolment derived from row existence, not a flag. | live | adr |
| 12-A-13 | adr-new | 12 | 12:941 | Admin-list sort allowlist deliberately unindexed, with reopening trigger. | live | register |
| 12-A-14 | adr-new | 12 | 12:942–943 | No `@Version` anywhere; non-security admin edits last-write-wins, accepted. | live | borderline |
| 12-A-15 | adr-new | 12 | 12:944 | No last-activity column. | live | register |
| 12-A-16 | adr-new | 12 | 12:945–946 | Spring Session DDL copied verbatim, single copy, blob-hash test, `eol=lf` versus SHA division of labour. | live | adr |
| 12-A-17 | adr-new | 12 | 12:947–948 | Migrations split by concern; append-only from first push to shared branch; Flyway `clean` stays disabled, no escape hatch. | live | borderline |
| 12-X-1 | adr-reversed | 12 | 12:725–728, 12:1008–1016 | §11's rejection of forward-only HMAC key versioning withdrawn; ticket 24's position adopted (ASVS 11.2.2 fail becomes partial). | withdrawn by 12:1008 | |
| 12-M-1 | adr-amend | 12 | 12:1037–1047 (from 24) | → 12-A-3: embedded-datasource fallback lets `validate` pass on ephemeral H2; ticket 24's absence-to-failure conversion is a precondition of the gate. | live | |
| 12-M-2 | adr-amend | 12 | 12:1056–1091 (from 09 §R) | → 12-A-3 / V2 DDL: adds `consecutive_failures_since_success`, `password_disabled_at` plus `ix_` index; both join negative-test set; `authenticable` spans two tables. | live | |
| 12-R-1 | register | 12 | 12:859 | Seam 1: `UUID` → Postgres `uuid`, MySQL `BINARY(16)`; asserted, never executed. | live | |
| 12-R-2 | register | 12 | 12:860 | Seam 2: `TIMESTAMP(6) WITH TIME ZONE` → MySQL `datetime(6)` (no zoned type, 2038 ceiling). | live | |
| 12-R-3 | register | 12 | 12:861 | Seam 3: `VARBINARY(69)` → Postgres `bytea`, width inexpressible; `OCTET_LENGTH` check is only guarantee. | live | |
| 12-R-4 | register | 12 | 12:862 | Seam 4: `BOOLEAN` → MySQL `bit`; validation break (metadata `tinyint`, HHH-6935). | live | |
| 12-R-5 | register | 12 | 12:863 | Seam 5: `LONGVARBINARY` (undocumented in H2 2.x) → `BYTEA` / `BLOB` plus InnoDB row format. | live | |
| 12-R-6 | register | 12 | 12:864–876 | Seam 6: MySQL default collation case- and accent-insensitive; `josé`/`jose` emails collide, contradicting 11's canonicalisation. | live | |
| 12-R-7 | register | 12 | 12:636–642 | `im8-review` grep for an MFA flag on User is a documented false negative (enrolment is row existence). | live | |
| 12-G-1 | glossary | 12 | 12:950 | Seam register | live | |
| 12-G-2 | glossary | 12 | 12:950 | Blinding key | live | |
| 12-T-1 | trigger | 12 | 12:882 | Real user volume reopens the unindexed sort allowlist (§9). | live | |
| 12-T-2 | trigger | 12 | 12:883 | Concurrent multi-admin deletion or self-service deletion reopens the reservations table (§10). | live | |
| 12-T-3 | trigger | 12 | 12:884 | Tombstone retention becoming bounded makes old HMAC key versions retirable, converting forward-only versioning to full rotation. | live | |
| 12-T-4 | trigger | 12 | 12:885 | Any real PostgreSQL or MySQL runtime moves the seam register from asserted to executed. | live | |
| 23-R-x2 | register | 23 | 12:164–168, 12:555–558 | Expired `pending_totp` rows not disposed (overwritten or linger encrypted); deferred with account-hygiene jobs. | live | |
| 23-R-x3 | register | 23 | 12:182–183, 12:558 | Not built: `PIN_USER_DETAILS` table, entity and recipes, PIN out of scope. | live | |
| 03-M-x2 | adr-amend | 03 | 12:954–956 | → 03 ADR (# unknown): prescribed `uuid` mapping unrunnable on H2; "uuid column distinct from PK" withdrawn; `user.id`/`user.target.id` map to `users.id`. | live | |
| 07-M-x3 | adr-amend | 07 | 12:957–961 | → 07 pepper ADR (# unknown): cross-reference tombstone HMAC key; asymmetry explained (credential vs one-bit fact). | live | |
| 19-M-x2 | adr-amend | 19 | 12:139–149, 12:971–973 | → 19 ADR (# unknown): ciphertext is 69 bytes not 48; MFA-flag shape mismatch answered as documented false negative. | live | |
| 23-M-x1 | adr-amend | 23 | 12:974–978 | → 23 ADR (# unknown): `pending_totp.expires_at` 15 min, `created_at` dropped, `key_version SMALLINT` 0..255, DB default −1 on `last_used_counter`. | live | |
| 24-M-x1 | adr-amend | 24 | 12:979–985, 12:709–749 | → 24 HMAC-key exception (# unknown): adds blinding-key justification, HKDF pre-emption, bounded-retention trigger. | live | |

## 2. Per-ticket tally (stated vs enumerated)

| ticket | kind | stated | enumerated | note |
|---|---|---|---|---|
| 10 | adr-new | 8 (numbered list 10:598–618; no count word) | 8 | match. Prose claims at 10:44, 10:120, 10:342 fold into 10-A-1 / 10-A-4, not extra ADRs. |
| 10 | adr-amend (own ADRs) | — | 4 | from tickets 09, 12, 25, 30 amendment sections. |
| 10 | register | none stated | 18 | **mismatch (no stated figure)**: defects 7–14 are numbered, residuals and ASVS fails are prose-only. |
| 10 | glossary | 7 | 7 | match. |
| 10 | trigger | none stated | 1 | named "reopening trigger" in prose only. |
| 10 | handover | — | 0 | **flag**: 25-bound items (log leak, pathway table, curl-403) sit under `### Handoffs` 10:643–644, not the exact heading. |
| 11 | adr-new | 15 (11:503, 11:548) | 15 | **mismatch in live count**: 11-A-10 withdrawn by ticket 10 (11:613), yet the list at 11:518 still states it. Live = 14. |
| 11 | adr-reversed | — | 1 | 11-X-1. |
| 11 | adr-amend (own ADRs) | — | 14 | ADR 13 alone is amended five times (10, 12, 09 §R, 15, 30). |
| 11 | register | "six-control declaration" + "ac-4 revoke half" (11:548) = 7 | 17 own + 5 foreign-owned | **mismatch**: handoff to 17 names 7; defects 7–12, compile defects, and three deviations/costs are also register material. |
| 11 | glossary | 4 | 4 | match. |
| 11 | trigger | 1 (11:473) | 1 own + 1 foreign (09-T-x1) | 09's reopen trigger lives in 11's file. |
| 11 | handover | — | 0 | **flag**: 25-bound items (fresh-install lockout scenarios, promote-before-demote, reset-then-unlock friction 11:605–606, second admin invite 11:939–940) are not under the exact heading. |
| 12 | adr-new | 17 (12:917, 12:994) | 17 | match. |
| 12 | adr-reversed | — | 1 | 12-X-1. The body at 12:709–749 still argues around the withdrawn rejection; the ADR text at 12:936–939 is already corrected. |
| 12 | adr-amend (own ADRs) | — | 2 | |
| 12 | register | 6 seam entries (12:852–853, 12:994) | 6 + 1 own + 2 foreign-owned | **mismatch**: 12-R-7 and the two ticket 23 not-built/deferred rows are not in the handoff to 17. The 10 "Findings" (12:887–915) are not marked for the register, so they are not counted. |
| 12 | glossary | 2 | 2 | match. |
| 12 | trigger | 4 (12:995) | 4 | match. |
| 12 | handover | — | 0 | Seam register routed to 25 as the porting document (12:996), not under the exact heading. |
| all | adr-rejected | — | 0 | No candidate is rejected against the three-part test in these files. |

Defect-numbering collision: ticket 10 numbers its standards defects **seventh to fourteenth** (10:122–494), and
ticket 11 numbers its own **seven to twelve** (11:487–496). Both claim numbers 7–12 "on this map". The aggregator
should renumber globally rather than trust either sequence.

## 3. Cross-file effects

Items in these files that amend, reverse or supersede something owned outside group C (or that need the aggregator
to reconcile across groups):

- **06** (10:648–650, 11:560, 11:966): closed enum gains rule `USERNAME_UNAVAILABLE`; `BATCH_TOO_LARGE` removed
  (enum = 13); then ticket 16 also removes `USERNAME_CHANGE_NOT_ALLOWED` (11:966). The stated "13 codes" is now
  stale by one unless 06 was amended elsewhere. Check 06's current count.
- **07** (10:651–653, 11:564–572, 12:957–961): 20-char admin generator deleted; context word list extended for
  6.1.2/6.2.11; 6.2.9 recorded failed; 15-char floor is a SHALL; pepper cross-referenced to the tombstone HMAC key.
- **08** (10:654–656): `api.base-path` is `/api`, not `/api/v1`; activation/invite redemption added as vacuous
  invalidation rows.
- **09** (10:388–391, 10:657–659): its accepted malicious-lockout residual is **superseded** by redemption-as-
  self-service-unlock (10-A-6). Mark it superseded by 10:388.
- **03** (11:557, 11:451–454, 12:954–956): C8 owes three custom fields, not one; "a `uuid` column distinct from the
  primary key" **withdrawn**. The UUID is the sole PK `id`.
- **19** (11:561–563, 12:139–149, 12:971–973, 10:664–665): `Encryptors.stronger()` prohibition narrowed;
  ciphertext width corrected 48 → 69 bytes; MFA-flag shape mismatch answered; 19 is load-bearing for log-leak
  containment; self-service TOTP enrolment is a reopening trigger (10-T-1).
- **23** (12:974–978, 12:164–183): `pending_totp` schema amendments adopted; two register rows (23-R-x2, x3) and a
  first-enroller residual (23-R-x1, 11:695–701) sit in group C files.
- **24** (12:979–985; 12:1003–1054): ticket 24 **reverses** 12's §11 rejection (12-X-1). Ticket 12 adds the
  blinding-key justification to 24's HMAC exception.
- **15** (11:860–932): TM-08, TM-09 accepted risks and TM-04 layering residual are owned by 15 but recorded only in
  11. TM-08's "bounded by the guard" framing is **superseded** by 11:948–952 (ticket 30).
- **16** (11:955–968): §5:473 N/A-by-construction row; test pins owed to ticket 32.
- **21** (11:820–830, 11:906–913): zero-authenticable-admins signal must observe all three non-mutation channels.
- **28 / 12 inconsistency** (12:1093–1099): the ticket 28 amendment lists "bootstrap seed, admin create, admin reset
  and re-enable" as prior `credential_issued_at` triggers and calls break-glass the fifth. Tickets 10 (10:446–447)
  and 11 (11:624–628) removed admin create and admin reset, so the live set is **three** (seed, re-enable,
  break-glass), plus the tier-2 case that deliberately does not stamp (11:839–841). 12:82–84 (the inherited
  section) carries the same stale four-trigger list.
- **25 / 12 inconsistency** (10:770–782 vs 12:485): ticket 25 adds a recovery-code token type to
  `credential_tokens` with its own TTL, but 12's DDL check constraint `ck_credential_tokens_type` admits only
  `ACTIVATION` and `PASSWORD_RESET`. Also, 10:778 says "four token types" where ticket 10 defines two. Ticket 12's
  table set was not amended for it.
- **30** (10:808–820, 11:934–952): runner mints nothing (10:727–729 and 10:775 stale); `DELETE .../totp` exempt
  from the two-admin count; 11-A-12 and 11-A-13 corrected in place.
- **29** (12:1103–1113): 12's session sizing notes (12:128–131, 12:800–802) corrected to 480/510 per source with an
  aggregate cap of 100,000. Not an ADR item, but 12-A-16's surrounding text is stale.
