# Common brief for every ticket agent (read fully)

You are implementing ONE ticket of the "Secured Hello World" build, inside your own git worktree.
Other agents are implementing other tickets in parallel in sibling worktrees; an orchestrator merges your branch afterwards.

## Workflow: /do-work
- Invoke the `do-work` skill (Skill tool, skill name `do-work`, args = the ticket path). If the Skill tool can't load it, read
  `C:\Users\UserAdmin\SGTechStack\.claude\skills\do-work\SKILL.md` and follow it; all paths it references resolve
  relative to `C:\Users\UserAdmin\SGTechStack\.claude\skills\do-work\`.
- HYBRID do-work (orchestrator's rule; overrides SUBAGENTS.md/DEFAULT.md where they differ):
  1. Implement the ticket YOURSELF following `DEFAULT.md` step 1 items 1–5 (prepare-work, TDD, implement). Do not
     spawn an implementer.
  2. When implementation is green, spawn ONE batch of review subagents together, in a single message: one independent
     reviewer (tell it to read and follow `do-work/reviewer/reviewer.md`) plus one subagent per registered
     code-reviewer check plus the aggregator, per `../code-reviewer/SKILL.md`. Give each absolute paths (your
     worktree, the skill files, the ticket file) because they start cold. They run in the background.
     MODEL SPLIT (set the Agent tool's `model` on every spawn):
     - `opus`: the independent reviewer, the `im8` check and the `springboot` check (judgement-heavy security review).
     - `sonnet`: the `build`, `react`, `thermo-nuclear`, `code-review` and `semgrep` checks, and the aggregator.
     Tell EVERY review subagent to write its full findings to a file under `<worktree>/artifacts/review/` (e.g.
     `reviewer.md`, `check-<name>.md`; the aggregator writes the compliance report as code-reviewer says) and to end
     with a ONE-LINE final message: `<name>: PASS|WARN|FAIL, must-fix=<n>, file=<path>`. Their final messages reach
     the orchestrator, not you, so keep them to that one line. Add `artifacts/review/` to `.gitignore` if it isn't.
  3. IMPORTANT: you will be handed back to the orchestrator while they run; that's expected. Before ending your
     turn, write a short `review-pending` note in your final message listing what's done, the spawned review agents
     and what remains. The orchestrator will relay the review results to you and resume you.
  4. On resume: fix every must-fix and FAIL blocker yourself, rerun the affected tests, then (if code changed) spawn
     one more review batch; otherwise run the mutation gate, commit, and close out.
  If you lack the Agent tool, run the reviews inline per `DEFAULT.md`. State in your final report which path ran and
  how many subagents you spawned.
- AWS Bedrock KB retrieval: run the `--check` described in query-kb; if it fails, KB = no. KB log mode = off.
- There is no GitHub issue tracker for these tickets. The ticket is a tracked markdown file INSIDE YOUR WORKTREE at
  `.scratch/secured-hello-world-build/issues/<NN>-*.md`. Closeout = tick the satisfied checkboxes in that file,
  change `**Status:**` to `done` (or `partial` with a reason), commit it with your work, and put the Verification
  summary in your final output.
- Do all git work with `git -C <your worktree>` or from inside it. Do not use the Agent tool's worktree isolation.

## What already exists (ticket 01, merged)
- `backend/` Boot 4.1.1 skeleton. Reusable harness in `sg.securedhello.testsupport`: `Proves` (takes `String[]`),
  `MutableClock` / `TestClock.shared()`, base classes `CtxDefaultTest`, `CtxLockTimeoutTest`, `CtxPortTest`
  (RANDOM_PORT, `RestTestClient`), `CtxNondevTest` (`productionProperty`, `productionContextRunner`), and
  `TemporaryH2FileInitializer`. ArchUnit rules all live in `sg.securedhello.architecture.ArchitectureRules`.
  Read these before writing tests; extend them instead of duplicating.
- Also merged: 04 (Flyway V1–V7 + JPA entities, incl. Spring Session tables), 06 (config validator; `TestSecrets`
  canaries fed by the harness; `OriginsProperties` = `app.origins.spa/api`; `RequiredPropertiesPostProcessor.REQUIRED`),
  07 (`SourceKeyResolver`, `ClientIpProperties`), 05 (`ErrorCode` enum + `ProblemDetailWriter.write(req,res,code[,ext])`,
  `AuthorizationMatrix` rows in YAML under `app.security.authorization`, `testsupport/ProblemAssertions`,
  generated `docs/api/error-contract.{md,schema.json}` with `ErrorContractDriftIT` — adding a code or extension member
  means updating the enum/renderer and regenerating with `-Derror-contract.regenerate=true`; SPA `src/lib/api/{errors,client}.ts`,
  MSW fixtures in `src/test/msw/problems.ts`).
- Ticket 03 (merged at 2184851): `@Proves` traceability gate (`TraceabilityGateIT`), pending ledger
  `docs/test-plan/pending-ledger.txt` (remove each T-ID you prove), register drift gate (`-Dregister.regenerate=true`),
  Dependency-Check in `verify` (ALWAYS pass `-Ddependency-check.skip=true` here: keyless NVD fails the build),
  PIT `-Pmutation` (class-name scope, threshold 85). Ledger reconcile helper:
  `mvn -f backend/pom.xml verify -Ddependency-check.skip=true -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TraceabilityGateIT`
  (writes `backend/target/traceability/ledger-proven.txt`; failure message says which lines to add/remove).
- "Human Decision Needed": you cannot ask the user. Pick the option most consistent with docs/spec.md, ADRs, and the
  test plan, record it under "Decisions made" in your final report, and continue. Return blocked only if truly impossible.
- If a registered code-reviewer check needs a tool that isn't installed (e.g. semgrep), note it as SKIPPED with the
  reason; don't spend long installing things.

## Sources of truth (in your worktree)
- `docs/spec.md` (build spec), `CONTEXT.md` (glossary), `docs/adr/*.md`, `docs/test-plan/test-plan.md` (T-IDs),
  `docs/register/register.md` (R-IDs / REJ-IDs), `docs/threat-model/`, `prd/`.
- Only read what your ticket needs. Cite only T-IDs that exist in the test plan; never invent IDs.

- Also merged (tickets 08–10): audit emitter `sg.securedhello.audit` (add an event = one `AuditEvent` constant + a
  typed context record; regenerate `docs/audit/log-inventory.md` with `-Daudit-inventory.regenerate=true`;
  `LogOutputGuard` fails tests on degraded rows or leaked canaries); Spring Session JDBC + header-only CSRF
  (`sg.securedhello.session`, `sg.securedhello.security.csrf`, tests use `testsupport/CsrfSession`, `csrf()` banned;
  new session-stored types go in `SessionAttributeAllowlist.PATTERN`; the rate-limiter filter slot is reserved in
  `SecurityConfig` Javadoc); JSON sign-in/sign-out/hello/profile (`sg.securedhello.security.login`, `SignedInUser`,
  `UserAccountRepository`, `profile/ProfileController`), test helpers `Accounts`, `AuditCapture`, `SignedIn`,
  `PasswordMatchCountTest`; SPA sign-in/hello pages, `routes.tsx`, `apiFetch(..., { csrfRetry: false })`,
  RHF + Zod + shadcn button/input/label; Playwright runs a real backend from test sources
  (`sg.securedhello.e2e.E2eBackend`, fixture accounts; ports 18010/15110) — extend it for new e2e flows.
- Also merged (ticket 11): `sg.securedhello.security.ratelimit` — `AuthRateLimiter`, `RateLimit` enum,
  `RateLimitProperties`; adding a budget = one `Route` component + YAML values under `app.security.rate-limit.*` + one
  `RateLimit` constant (see its Javadoc) and extend `RateLimitBindingTest` (T-RL-010 stays on the ledger until the last
  route lands). Shared test contexts raise budgets via `harness-budgets.properties`; `CtxBudgetTest` runs real budgets.
  `AuditKeying` provides tier-1/tier-2 keyed rows. `ClockTimes` gives a Caffeine `Ticker`/Bucket4j `TimeMeter` from the Clock.
- Also merged (tickets 12–13): lockout (`security/lockout/LockoutCounter`, `LockoutLadder`, `LockoutRecorder`,
  `login/PreAuthenticationChecks` with a no-op forced-change slot for ticket 16, `user/PasswordLockoutState`,
  `security/ratelimit/LockoutCardinality`); `password/PasswordService.setPassword(UUID, raw)` (joins the caller's
  transaction, throws `PasswordRejectedException` → 400 `PASSWORD_REJECTED` with `rule`, deletes pending reset tokens
  via `credential/CredentialTokenRepository`, clears forced-change flags), `PasswordPolicy.normalise`,
  `session/SessionTerminationService.endAll(username)` / `endAllExcept(username, keptId)` (after-commit);
  `PATCH /api/profile/password`; SPA `ChangePasswordPage`, `lib/password/{policy,strength}.ts`.
- Also merged (tickets 14–15): `credential/CredentialTokens` (`mint`/`redeem`, pure `CredentialTokenHash`/
  `CredentialTokenConsumption`), `RESET_TOKEN_INVALID` advice; `user/Identifiers` (canonicalisation,
  `RESERVED_USERNAMES`), `Tombstones`, `DeletedUserRepository`; `registration/` (register/activate);
  `email/EmailService`, `LinkEmail`, `CredentialLinks` (token in URL fragment), `DevLinkLogger`; `passwordreset/`
  (request/confirm; clears lockout+cap, ends all sessions); `PasswordLockoutState.CLEAR`; `SessionTerminationService`
  is also called by `LockoutRecorder`. Test harness: `CapturedEmails` (@TestBean EmailService), `Registrations`,
  `PasswordResets`, `RestartHarness.run(...)`, `E2eMailbox` (`GET /e2e/mailbox?to=`) and fixture accounts in
  `E2eBackend` (which loads `harness-budgets.properties`). SPA: register/activate/forgot/reset pages,
  `LinkPasswordForm`, `NewPasswordHints`. After pulling, `npm ci` in `frontend/`.
- Known open gap (not yours unless your ticket says so): concurrent registrations on one identifier give a 500.
- Maven: if downloading a new artifact fails with a PKIX error, set `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`.
- `backend/README.md` documents running locally and shared dev demo values; keep it accurate if you change startup.

## Repo layout conventions (all agents must agree)
- `backend/` — the Maven project (Spring Boot 4.1.x, Security 7.1.x, Session 4.1.x, Java 21). groupId `sg.securedhello`,
  artifactId `secured-hello-world`, base package `sg.securedhello`. Sub-packages by module, e.g. `sg.securedhello.config`,
  `.security`, `.audit`, `.error`, `.session`, `.user`, `.web`, `.time`. Tests under matching packages.
  `@Proves` lives at `sg.securedhello.testsupport.Proves` (test sources).
- `frontend/` — the Vite + React 19 + strict TS SPA.
- Root `.gitignore` covers `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, test reports, H2 `*.db` files, logs.
- Main code gets time only from the injected `Clock` bean (once ticket 01 lands).

## Parallel-safety rules
- Stay inside your worktree. Never touch the main checkout or other worktrees.
- Other tickets in the same wave also edit `backend/pom.xml`, `application.yml`, `SecurityConfig` and
  `ArchitectureRules`. Make small, additive edits there (no reformatting or reordering) to keep merges clean.
- Do NOT push. Commit only to your worktree's current branch (via the git-commit-skill, Conventional Commits).
  End every commit message with:
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`
- Tests must use random ports (`RANDOM_PORT`), never fixed 8080/5173. If you launch the app by hand, use port
  `18000 + <ticket number>` for the backend and `15100 + <ticket number>` for Vite, and stop it afterwards.
- Playwright: when another ticket runs in parallel, run it with `PW_API_PORT=18000+<ticket>` and
  `PW_PREVIEW_PORT=15100+<ticket>` (e.g. ticket 12 → 18012/15112) so parallel runs never share ports.
- OWASP Dependency-Check: always run with `-Ddependency-check.skip=true` (the orchestrator runs the full scan once at
  the end with the user's NVD key). Never use `-DskipITs` or `-Dmaven.test.skip`.
- Every test-plan row you prove: remove it from `docs/test-plan/pending-ledger.txt`. Don't claim a row that is only
  partly covered; say so in your report.
- Rate limits: every new route adds its own budget row from the spec's budget table, via the `AuthRateLimiter`
  pattern from ticket 11 (once merged).
- Keep changes scoped to your ticket; don't pre-build later tickets. Small, clean, idiomatic code.

## Gate rules (user-approved)
- Inner loop: run only the targeted tests for what you are changing.
- One full `verify` per final state; the build review agent reuses that log instead of running its own.
- PIT: scoped to the changed classes per ticket; the full scope runs once, at close-out.
- Playwright: a ticket runs only its own or touched specs; the full suite on both browsers runs once, on the merged
  tree. Always `--workers=2`. A failing spec is rerun alone, and is called load-flaky only if it then passes.
- Merges are serialized. At most ~2 tickets run in parallel.
- Long jobs (full verify, PIT, full Playwright) run in the foreground with long timeouts.

## Final report (your last message; the orchestrator relays it)
1. Status: done | partial | blocked (+ why)
2. Commits (hash + subject) on your branch
3. Files/areas touched (brief)
4. T-IDs now proven by tests (`@Proves` or test names) — exact list
5. Commands run and their results (e.g. `mvn verify` pass, test counts; `npm run build`, vitest, playwright)
6. do-work path used (SUBAGENTS.md or DEFAULT.md, and why) with subagent count; code-reviewer check outcomes
   (PASS/WARN/FAIL/SKIPPED per check) and compliance report path
7. Decisions made, and known gaps/deviations from acceptance criteria
Keep it under ~60 lines.
