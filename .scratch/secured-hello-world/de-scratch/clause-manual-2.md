# Clause mapping, manual batch 2

Paths: `Std` = Standalone_User_Access_Control_Application_Standard.md, `MFA` = MFA_Core/Base_Standalone_Application_Standard.md, `FE-STD` = MFA_Frontend_Standalone_Standard.md, `REG` = docs/register/register.md, `ADR-README` = docs/adr/README.md. Ticket files are cited only as evidence, never in the new cell.

| T-ID | new clause cell | evidence (file:line you read, one short line) |
|---|---|---|
| T-CFG-027 | Spring Boot 4.1 `JdbcSessionProperties` (`initialize-schema` default `EMBEDDED`, `continue-on-error` default `true`); REJ-040 | 05:276-296 reads both defaults off Boot's JdbcSessionProperties/DatabaseInitializationProperties source (main branch) and hands the DDL to Flyway; 24:539 gives the same reason; ADR-README REJ-040 = session DDL copied verbatim, checked by hash |
| T-CFG-033 | REJ-008; REJ-015; REJ-024 | 24:551-552: the validator is a single point of deletion, so its presence is tested; ADR-README REJ-008/015/024 each rest on "prohibited configuration fails startup" |
| T-CFG-036 | ADR-041; R-RL-009 | 29:198 floor and lead_days validated non-negative for the shedding reserve; routing.md:106 ADR-041 = shedding count line and free-space reserve; REG:109 R-RL-009 sizing line includes `floor` |
| T-FE-002 | PRD L123; Std §3.5:385 | PRD:123 "Role checks enforced server-side ... never trusted from client-supplied state"; Std:385 user-management endpoints restricted by the authorization matrix; 14:394 guard is UX only |
| T-FE-004 | FE-STD §7.2:386; R-FE-005 | FE-STD:386 invalid code (412) → `description` on MFADialog, `role="alert"`; REG:293 R-FE-005 notes the corpus prescribes one `role="alert"` |
| T-FE-006 | R-FE-005 | REG:293 R-FE-005 lists "focus on error" among built and tested a11y behaviours; 14:541 |
| T-FE-007 | R-FE-005 | REG:293 R-FE-005 a11y row (dialog focus management from Base UI); 14:542 focus returned on dialog close |
| T-FE-008 | R-FE-005 | REG:293 R-FE-005 lists "live regions"; 14:542 |
| T-FE-009 | R-FE-005 | REG:293 R-FE-005 lists "keyboard paths through admin tables and dialogs"; 14:543 |
| T-FE-010 | R-FE-005 | 14:546 autofocus slot 0 decided under §13 Accessibility; 22:536 FE-STD prescribes no autofocus; REG:293 is the only surviving row for §13 (autofocus not named in its list) |
| T-FE-011 | R-FE-005 | 14:547 paste fills all slots, decided under §13; 22:536 FE-STD prescribes no paste handling; REG:293 as for T-FE-010 |
| T-FE-012 | FE-STD §3.2:164; ADR-027 | FE-STD:164 MFADialog: clicking Verify calls `setMfaCode(code)`; 14:547 no auto-submit so a mistyped code does not burn a tier-1 attempt; ADR-README ADR-027 = two-tier TOTP lockout |
| T-FE-014 | FE-STD §7.3:402; FE-STD §7.2:394 | FE-STD:402 MFA session data must not persist beyond the dialog interaction; FE-STD:394 codes cleared on Verify and on Cancel; 14:549 dismissal must drop the step-up queue |
| T-FE-015 | FE-STD §5:239; MFA §3.4:256 | FE-STD:239 key-exists probe on mount (22 found it fail-open, 14:552 inverts it); MFA:255-256 setup self-service guard: TOTP setup only when none exists |
| T-FE-027 | FE-STD §5:242; MFA §3.2:196; REJ-059 | FE-STD:242 verify success → `isVerified`, failure → user can retry; MFA:196 invalid-factor errors use 412; ADR-README REJ-059 = MFA response contract departs from corpus enrolment statuses |
| T-E2E-001 | Std §3.5:391; R-HDR-008; REJ-051 | 14:255 no service worker because Clear-Site-Data is ignored on SW-served responses; Std:391 Clear-Site-Data on logout; REG:234 R-HDR-008 SPA clearing is the real control; ADR-README REJ-051 sign-out terminal (same paragraph, 14:255) |
| T-E2E-003 | Std §5:500; Std §3.5:390; PRD L119; ADR-059; R-FE-006 | Std:500 CORS preflight from allowed origins succeeds; Std:390 explicit allowlist, credentials only for listed origins; PRD:119 CORS allow-list with credentials; routing.md:134 ADR-059 two origins; REG:294 R-FE-006 browser suite includes CORS |
| T-E2E-004 | R-HDR-007; ADR-059; R-FE-006 | REG:233 R-HDR-007 origin drift, acceptance = browser call from served SPA succeeds; routing.md:134 ADR-059; REG:294 R-FE-006 lists origin agreement |
| T-E2E-005 | PRD Story 2; IM8 ac-2; ADR-023; ADR-021; R-FE-006 | 16:602 golden path = login then admin TOTP step-up; PRD:44 Story 2 login; REG:178 R-MFA-008 IM8 ac-2 mandates MFA for privileged access (ADR-023); ADR-README ADR-021 bounded factor validity; REG:294 R-FE-006 lists one golden path |
| T-ARCH-002 | ADR-065 | 16:558 allocator enforced, extension fails requests from MockMvc's default 127.0.0.1; ADR-065:20,37-44 shared named full contexts with live rate limiters (indirect match: the ADR does not name the allocator) |

## Corrected T-FE-026

| T-ID | new clause cell | evidence |
|---|---|---|
| T-FE-026 | FE-STD §5:240 (over FE-STD §3.2:166); MFA §3.4:256; R-STD-048 | FE-STD:240 keyExists → Generate disabled; FE-STD:166 says always enabled; MFA:256 overwrite needs admin removal; REG:385 R-STD-048 records §5 followed over §3.2 |
