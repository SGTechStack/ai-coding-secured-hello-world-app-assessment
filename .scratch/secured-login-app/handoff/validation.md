# Validation record — Secured Login App map

**Verdict: approved.** All 21 tickets resolved; the handoff artefacts are internally consistent and their load-bearing claims verify against the pinned sources. Twelve defects were found and fixed in place; none changed a decision. Two residual risks are carried forward, both already named by the map.

Audited against the `App-Standards` submodule at `ff5ab8205fdfb641210164eeaa2365e48e04846b` (confirmed via `git submodule status`). Everything below was checked directly, not inferred.

---

## What was checked, and how

| Check | Method | Result |
|---|---|---|
| Ticket completeness | `Status:` / `Blocked by:` headers across `issues/*.md` | 21/21 resolved; every `Blocked by` names a closed ticket |
| Open items | grep for `TBD`, `TODO`, `unresolved`, `open question`, `to be decided` across tickets, map and handoff | No live open item. Every hit is narrative about a *closed* conflict |
| Fog / frontier | `map.md` § *Not yet specified* | Empty, with each former patch recorded as graduated or dissolved |
| Citation resolvability | 156 distinct `file.md:line` citations resolved to a real file with an in-range line | 156/156 resolve |
| Citation accuracy | ~85 load-bearing citations read at the cited line and compared to the claim | 10 drifted by 1–8 lines (**fixed**); substance held in every case |
| `Std:` ambiguity | every `Std:NNN` in `spec.md` and `prd-deltas.md` resolved against *both* candidate files | Overload confirmed and **fixed** (see below) |
| Authorization matrix | row count and contents in `spec.md` §3 vs 08, 07's `unlock` addition, 10's `batchResetPassword` removal | Exactly 21 rows; `GET /users/{userId}` present, `batchResetPassword` absent, `unlock` present |
| Role model | grep for `ADMIN` / `ROLE_ADMIN` leakage across `handoff/` | None. `USER_MANAGER` used consistently (13 sites each in spec and stories) |
| Self-read path | `/currentUser` vs `/profile` across handoff artefacts | `/currentUser` everywhere; zero `/profile` |
| Audit events | 12's enumeration counted against 14's "all N asserted" claim | Miscount found and **fixed** (23 → 27) |
| Amendment propagation | 15's ArchUnit-row and appender counts, traced ticket → spec | Amendments correct in ticket and spec; `map.md`'s summary line was stale (**fixed**) |
| Stories file | story and acceptance-criteria counts vs `README.md`'s claim | 24 stories, 141 criteria — matches |
| Password max length | `BCrypt` disassembled from the pinned `spring-security-crypto-7.0.6.jar` in `~/.m2` | **Verified.** `hashpw` throws `IllegalArgumentException("password cannot be more than 72 bytes")` when `byte[].length > 72` — not truncation. 04's 72-byte cap is correct, and the recipe's 1024 would have produced a `500` |
| Environment blockers | `gh` on `PATH`; `import yaml, pydantic` | Both absent, exactly as `handoff/README.md` documents |

## Defects found and fixed

All are citation or bookkeeping errors. No decision was reopened.

1. **`Std:437` → `:438`** (9 sites). The global-SPA-interceptor clause is line 438; 437 is the plain "logout invalidates server-side session state". [13](../issues/13-session-policy.md) already recorded this fix for itself, but it was never propagated — 07, 08, 10, 12, 15, 21, `spec.md` (×2) and `map.md` still pointed at 437. This citation carries the *reason* three separate rulings exist (08's `400` for a wrong current password, 10's `code`-before-logout refinement, 21's no-bare-`401`/`403` rule), so it was worth correcting everywhere.
2. **`Std:` meant two different files.** In the audit and logging sections it resolves to `Structured_Logging_Application_Standard.md`; everywhere else to `Standalone_User_Access_Control_Application_Standard.md` — and §1 of `spec.md` used both spellings in one paragraph. Nine occurrences in `spec.md` and three in `prd-deltas.md` are now `Std_Logging:`, a prefix the tickets already used. A **citation legend** covering all 18 prefixes in use was added to `spec.md` and `map.md`.
3. **`Std:277` → `Std_Logging:169`** (`spec.md` §1, `map.md`). Neither file's line 277 names W3C Trace Context as the format to propagate; the logging standard's line 169 does. The enforced constraint requiring Micrometer Tracing on the classpath is `Std_Logging:321`, already cited correctly elsewhere.
4. **`Std_Logging:167` → `:164`** (8 sites) for *"MUST NOT suppress or drop it silently"* — 19's justification for making the encoder **create** the `error` object rather than strip the keys. Line 167 is the Singapore-timezone clause, which 02 and 15 cite correctly for that purpose; both readings of `:167` were live in the same map.
5. **`Std_Logging:328` → `:326`** (5 sites) for the boundary-masking enforced constraint — the clause that makes 19's encoder mandatory rather than optional. Line 328 is blank.
6. **`Std_Logging:283` → `:284`** (6 sites) for the log-inventory obligation. The *user* standard's `:283-284` citations for password-change audit events are correct and were left alone.
7. **`Questions.md:485` → `:482`** (3 sites) for the "roles without explicit privileges" note — the clause the entire no-privilege-layer decision rests on.
8. **`Priv:106` → `:109`** (3 sites) for the composition regex 04 overrode. Line 106 is the max-length throw.
9. **`RBAC:88` → `:83`** (03) for the `hasAuthority` call.
10. **`SelfRead:53` → `:45`** (5 sites) for where the recipe mounts its controller. `@RequestMapping("/api/v1/profile")` is line 45; 53 is the `@GetMapping` inside it. This is the citation carrying the `/currentUser`-versus-`/profile` contradiction 10 settled.
11. **`Std_Logging:326` → `:332`** (2 sites) for *"Mixing plain text with JSON logs in the same stream"*. Line 326 is the masking constraint, which after fix 5 was also cited as `:326` — so 19 and 14 had one number standing for two different clauses. Worth noting: [05](../issues/05-logging-standards-applicability.md)'s enforced-constraint table (`:319`–`:330`) is exact at every row and was the accurate source all along; the drift entered downstream in 19.
12. **Audit-event miscount: 23 → 27.** 12's list numbered 23 events and then added an unnumbered **System** group — self-read, application startup, application shutdown, bootstrap seed — all four independently mandated (`SelfRead:56-60`, `Std_Logging:252`, plus 16). 14 asserted emission for "all 23", which left those four with no assertion and no record of being skipped. They are now numbered 24–27 in 12, and 14, `spec.md`, `prd-deltas.md` and `map.md` all say 27. The PRD baseline was corrected from "seven" to nine (`prd/assessment-prd.md:122` lists nine), which keeps the ~3× multiple honest.

Two bookkeeping fixes alongside: `map.md`'s one-line summary of 15 still claimed **four** ArchUnit rows and **two** appenders, both superseded — by 08's matrix-coverage row and 13's `sendError` ban, and by 19's third appender. The ticket and `spec.md` were already correct; only the index was stale. It now flags both as amended.

## Checked and found sound — no change needed

- **`Std:416`** ("Generic user update is separate from password reset") has no matching row in the 21-row matrix. Not a gap: the clause constrains separation, not existence, and no generic user-update endpoint exists in this app. Self-service profile updates were ruled out by 10, and admin-side generic update by the absence of a PRD story; the `/users/**` backstop closes the path either way.
- **`Std_Logging:97` versus `Questions:298`** — 04 and 21 disagreed on whether the `ERROR`/`WARN` conflict was real. 21's reading is the correct one: `:97` is conditional (`WARN` when skippable, `ERROR` when definitive, e.g. a missing required field) and `:298` is a `> **Recommendation:**` blockquote scoped to `@Valid` input. They agree. 04's decision to route password failures through a domain exception stands on its own merits regardless.
- **`Std:238`** does prohibit `CookieCsrfTokenRepository` in the exact words 09 claims, and **`HDR:132-151`** does contain all three recipe defects 09 fixed — no `.cors(...)` call, an undefined `SecurityProperties`, and `${api.base-path}` unresolved inside a Java string literal.
- **`RBAC:43`** is `PATCH: /api/v1/users/*`, single-segment, exactly as 08's most consequential defect finding describes.
- **`Self-Service:269-272`** does prescribe the verification procedure 04 flagged as wrong for a four-value history window.
- **`Std:129`, `:240`, `:355`, `:372`, `:407`, `:415`, `:427`, `:429`, `:446`, `:474`** and the `Trace:` set (`:10`, `:14`, `:897`, `:912`, `:940`, `:949`) are all exact at the cited line.

## Residual risks, carried forward

1. **The `Secure` cookie on `http://localhost` is still unverified** — the map's own stated single load-bearing unverified assertion (09). It remains untested because there is no code. It must be the first test written; the fallback is a one-line `dev`-only override.
2. **Recipe citations drift more than standard citations.** Eight of the ten corrections above point into recipe files or the logging standard's dense clause lists, while citations into the user-access standard were exact almost without exception. Anyone re-pinning the submodule should expect to re-verify rather than trust the line numbers — which `handoff/README.md` already says, and which this audit confirms is the right instinct.

## Not a defect, but worth knowing before the build

- `Std:` and `Std_Logging:` are now distinct in the handoff artefacts, but the **tickets** still use the overloaded `Std:` internally. The legend in `map.md` resolves it; a reader who skips the legend can land in the wrong file.
- The pipeline is still blocked on `gh` and two Python packages. Neither is a planning gap, and neither was touched by this validation.
