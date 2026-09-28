# Clause de-scratch, manual batch 1

Scaffolding. Proposed replacements for `clause` cells in `docs/test-plan/test-plan.md` that cite only tickets.
Standard line numbers are 1-based, read from the files under `App-Standards/`. `MFA-RCP` below means
`App-Standards/Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Reimplementation_Recipes.md`. Write it out in full
in the table if the test plan has no abbreviation for it.

## A. Rows whose clause cell was ticket-only

| T-ID | new clause cell | evidence (file:line you read, one short line) |
|---|---|---|
| T-AUTH-004 | Std §3.2:245; Std §5:509; ADR-031 | Std:245 validation failures get a 4xx with a stable machine-readable body; Std:509 bodies match the schema; 05:606/05:1038 malformed body → envelope, not 500 |
| T-AUTH-007 | UNRESOLVED | 06:244 no standard requires or forbids it; RFC 9110 §15.5.2 / §11.6.1 say a 401 MUST carry WWW-Authenticate, so this test proves a deviation and no register row records it |
| T-AUTH-016 | Std §3.2:247; Std §5:506; ASVS 6.3.8 (L3) | 16:127–128 a lock-timeout 500 would be an account-state oracle; Std:247/506 identical status/body for every account state |
| T-SES-023 | R-SES-011; ADR-041 | register:85 cleanup cron pinned at 0 * * * * * (source of the 510-row and disk-sizing figures); 29:454–458 |
| T-CSRF-002 | Std §5:443; Spring Security 7.1 `CacheControlHeadersWriter` (default header set) | Std:443 CSRF token endpoint is not cacheable; 05:230–240 default writer emits the three headers, skipped if the handler sets its own |
| T-LCK-014 | ADR-011; R-LCK-014 | routing:64 ADR-011 escalating ladder; register:100 ladder startup floor (19 locks, 840 min to the cap); 09:1595 |
| T-LCK-015 | ADR-013; R-LCK-012; R-LCK-014 | register:98 runbook for the alert at 50 consecutive failures; register:100 floor covers the alert threshold; 09:1596 |
| T-LCK-019 | Std §5:450; Std §5:506 | 16:124–128 row lock keeps the threshold exact, and a lock timeout must not change the 401; 16:370 this binding pairs with that test |
| T-RL-005 | ADR-020; ADR-054; ADR-015 | 16:154 source.ip_hash and the cardinality axis both read the details (they fail in opposite ways); routing:73/129/68 |
| T-RL-021 | ADR-017 | 26:200–203 miss metering relies on `requestedSessionCached`; routing:70 unlisted routes metered on session-store misses |
| T-CRED-009 | ADR-013; ADR-009 | 16:157 a reset routed through AuthenticationManager is blocked by the disabled password authenticator; routing:66/57 |
| T-CRED-015 | Std §5:460; Std §5:508 | 10:325–327 a validation failure rolls back the consume; Std:460 only expired or redeemed tokens fail; Std:508 specific rule error |
| T-MFA-001 | Spring Security 7.1 `AbstractUserDetailsAuthenticationProvider#createSuccessAuthentication` (adds `FactorGrantedAuthority.PASSWORD_AUTHORITY`); ADR-026 | 05:128–134 source-derived: extra factor authority, so check the exact set before writing authority rules; routing:84 |
| T-MFA-006 | ADR-033; ADR-031 | 14:305–312 the per-IP 429 must not carry `factor`, and 14:312 says this moved to ADR-033; routing:98/96 |
| T-AUD-013 | Std §3.4:319; LOG §3.3:223–225; ASVS 16.2.5 (L2) | Std:319 no passwords, token plaintext, CSRF tokens, raw session ids or OTPs; LOG:223–225 no secrets, raw session ids or encryption keys |
| T-AUD-016 | LOG §3.2:199; ADR-055 | 13:494 no throwable parameter on `emit`, the primary §10 control; LOG:199 exception text may expose internals; routing:130 one `emit` |
| T-AUD-022 | LOG §3.3:225; LOG §3.3:223; LOG §3.2:199 | 24:360 failure message gives the observed length only; 24:919–921 never logged, not even via a throwable; LOG:225 no encryption keys |
| T-CFG-007 | ADR-051 | 12:287 second negative set for what `validate` cannot see, NOT NULL included; routing:121 validate demoted, schema gate is negative tests |
| T-CFG-008 | ADR-051; ADR-042 | 12:287–290 every foreign key, from the same set; routing:112 the seeded `roles` table enforces the users.role FK |
| T-CFG-013 | ADR-072; R-RUN-004 | 24:1069, 28:316 FILE_LOCK=NO turns off the single-writer lock; register:261 runner outage relies on H2 single-writer mode |

## B. Abbreviation checks

| T-ID | new clause cell | evidence (file:line you read, one short line) |
|---|---|---|
| T-MFA-012 | RFC 6238 App. B; MFA-RCP Recipe 9:716; MFA §5.1:393 | MFA-RCP:716 "Recipe 9: Deterministic TOTP Computation (Testing)"; :727 Appendix B vector, 94287082; MFA:393 fixed-secret computation test |
| T-MFA-013 | RFC 6238 App. B; MFA §3.4:229; MFA §3.4:230 | "STD" is MFA_Core: MFA:222 §3.4 Security Contract; :229 truncation mod 10^digits; :230 exactly 6 digits |
| T-MFA-014 | MFA §2.5:75; MFA §3.4:231; ASVS 6.5.1 (L2) | MFA:71 §2.5 Enforced Decision Logic; :75 replay check on matched counter ≤ lastUsedCounter; :231 ±1 skew; 22:395–398 leaves +1 only |
| T-MFA-016 | MFA §2.2a:51; MFA-RCP Recipe 10:710 | MFA:51 wrong code leaves `PENDING_TOTP` intact; MFA-RCP:617 Recipe 10 "TOTP Setup Confirmation"; :710 rule covers the write-failure rollback only |
| T-CFG-017 | MFA §4.1:312; ADR-026 | MFA:269 §4.1 Factor Provider Model; :312 provider registration checked at startup; 22:285 the rule carries over to the ProviderManager wiring |
| T-CFG-018 | MFA §4.1:312; ADR-026 | same clause, applied to the rule sets (22:285–286); routing:84 hand-composed factor rules |
| T-ADM-027 | ASVS 6.1.1 (L1); ADR-049 | routing:119 ADR-049 "Factor reset is exempt from the two-admin count", covers T-ADM-027–029; 30:201 |
| T-AUD-040 | R-AUD-036; ADR-020 | register:226 R-AUD-036 lists T-AUD-040 (serialVersionUID, JDBC round-trip, no address in toString); 31:337 |

Notes on part B:

- STD §2.5, §3.4 and §4.1 point to MFA_Core (`Base_Standalone_Application_Standard.md`), and each heading matches
  its row. They cannot point to the user standard: its §3.4 is the Logging Contract, and its §4 has no numbered
  subsections.
- The part B cells replace the whole cell. They also drop the remaining ticket refs (`23 §1`, `23 §2`, `30 §3`,
  `31 §4`).
- T-MFA-014 could also cite R-MFA-013 (replay is atomic under the row lock; it lists T-MFA-014).
