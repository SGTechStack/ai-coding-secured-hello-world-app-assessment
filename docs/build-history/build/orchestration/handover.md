# Orchestration handover: tickets 18–28

Handover from the orchestrating session that built tickets 01–17 plus a review-fix wave (2026-09-28 to 2026-09-29).
A fresh session picks up here. Read this file first, then `common-brief.md`.

## Your role

You **orchestrate**. You don't implement. Keep your own context small:

- Plan waves by dependency. Spawn one `general-purpose` agent per ticket, and let each run `/do-work` on the
  **hybrid flow** described in `common-brief.md`.
- Delegate any merge that may conflict to a merge subagent. It reports only the outcome.
- Relay results and make coordination decisions. Ask the user about design decisions, with a recommendation.
- Don't read big files or diffs yourself. Agents report compact summaries.

Every agent prompt starts: "Implement ticket NN using /do-work. FIRST read the common brief and follow it exactly:
`<repo>/.scratch/secured-hello-world-build/orchestration/common-brief.md`". Then add the worktree path, the ticket
file path, what runs in parallel, the Playwright ports and any carry-over notes (see below).

## State at handover

- Repo: `C:\Users\UserAdmin\SGTechStack\ai-coding-secured-hello-world-app-assessment`. Work branch: `zacharylim`,
  which is never pushed by agents. The user pushes.
- Merged: tickets 01–17, and review-fix batches A (config hardening), B (runtime fixes) and C (test and gate
  strengthening). See the "Review-fix wave" section below.
- Pending ledger: `docs/test-plan/pending-ledger.txt` (about 150 rows). Every ticket removes the rows it fully proves;
  ticket 28 deletes the file.
- Tickets 18–28: `.scratch/secured-hello-world-build/issues/NN-*.md`. Agents tick them and set `**Status:**`.
- The last green `clean verify -Ddependency-check.skip=true` on the merged fix wave had 1083 unit tests and 6 ITs.
  PIT scored 99%. Vitest passed 141 tests and Playwright 18/18, twice.
- HEAD at handover: `1a8626b`.
- **Dependency-Check is green.** The full release gate `mvn -f backend/pom.xml clean verify`, with the NVD key and no
  skip flags, passes. Tomcat is pinned to 11.0.26 through `tomcat.version` in `backend/pom.xml`, because Boot 4.1.1
  ships 11.0.24 with 9 CVEs at CVSS 7 or higher. Drop the override once Boot manages 11.0.25 or later. One finding
  is left, CVE-2026-54285 (5.3) on opentelemetry-api 1.62.0. It is below the threshold and is likely a false
  positive (it matches opentelemetry-js).

## Wave plan

| Wave | Tickets | Blocked by | How |
|---|---|---|---|
| 1 | 18, 26 | 18: 17 · 26: 09, 11 | parallel worktrees |
| 2 | 19, 20 | 18 | parallel worktrees |
| 3 | 21, 22, 23, 24 | 21, 22: 20 · 23: 12, 15, 19, 20 · 24: 19, 20 | parallel worktrees, merged by a merge subagent |
| 4 | 25, 27 | 25: 12, 19, 22 · 27: 16, 24 | parallel worktrees |
| 5 | 28 | 01–27 | alone, in place on `zacharylim` |

**Worktrees:** use them only for parallel tickets, at `C:\Users\UserAdmin\SGTechStack\shw-wt\tNN` on branch
`tNN-<slug>`, created from `zacharylim`. A ticket that runs alone works in place on `zacharylim`. After a merge,
remove the worktree and delete the branch. If the merged tree equals the verified branch (`git diff --quiet`),
skip re-verifying.

**Ports:** Playwright uses `PW_API_PORT=18000+NN` and `PW_PREVIEW_PORT=15100+NN`. Parallel runs must never share
ports.

## Model split (user decision)

Set `model` on every spawn; don't let it default to yours.

| Role | Model |
|---|---|
| Ticket implementation agents | `opus` |
| Independent reviewer, `im8` check, `springboot` check (spawned by ticket agents) | `opus` |
| `build`, `react`, `thermo-nuclear`, `code-review`, `semgrep` checks and the aggregator (spawned by ticket agents) | `sonnet` |
| Merge agents | `sonnet`. Escalate to `opus` if conflicts are semantic, not just additive. |
| Dependency bumps, docs lookups, other bounded chores | `sonnet` |
| Trivial probes | `haiku` |

`common-brief.md` tells ticket agents which model each review agent gets.

## How the hybrid flow behaves (important)

- A ticket agent implements the ticket itself, then spawns one review batch: a reviewer, one agent per code-reviewer
  check (7 of them), and an aggregator. Each review agent writes its findings to `<worktree>/artifacts/review/*.md`.
- While those review agents run, the ticket agent **is handed back to you** with a `review-pending` note. This is
  expected. Sometimes it resumes on its own as the review results arrive. If it doesn't, resume it with
  `SendMessage`: "reviews done, findings in artifacts/review/, fix must-fix/FAIL, re-review INLINE (no new
  subagents), -Pmutation, commit, final report".
- Review agents' one-line verdicts may arrive in your context. Keep them one line.
- **Usage limits** hit roughly every few hours. When one resets, check each worktree's `git log` and `git status` and
  its `artifacts/review/`, then resume each agent with `SendMessage`, which keeps its context. Don't respawn.

## User decisions already made (don't re-ask)

- The model split above.
- `/do-work` on the hybrid flow. Worktrees only for parallel tickets. The orchestrator keeps its context small.
- The agent-teams feature was considered and not adopted: in-process teammates can't be resumed after a usage limit,
  and there's no worktree isolation. Revisit only if relays become costly.
- Slow-drip lockout: the account also locks on the 10th consecutive failure since the last success, and again on
  every 5th after that (`app.security.lockout.consecutive-threshold`, default 10). ADR-011 and ADR-012 are amended.
- Email-existence oracle: every well-formed registration holds the username for 24 h (table `username_holds`,
  migration V8), and pending registrations lapse after 24 h. ADR-032 is amended.
- New tested controls get their own test-plan rows with `@Proves`, rather than going untracked.
- Dev demo values are committed in `backend/README.md` and refused at startup outside `dev` (`PublishedDemoValues`).
  The dev admin is `demo-admin` / `lantern-orchard-copper-tide`: seed → forced password change → TOTP enrolment.

## Carry-over notes for specific tickets

- **18:**
  - `/api/profile` `factors` is still stubbed; ticket 18 owns it, along with the enrolment gate in the SPA landing
    order.
  - `TotpFactorGrant` (from 17) is reusable.
  - The ticket 17 e2e spec pushed the suite past REJ-057's ~8 tests; fold it into the T-E2E-005 golden path if it
    fits.
  - T-MFA-014 (replay after verification) and T-MFA-019 are waiting for 18.
- **17 leftovers** (Medium/Low; fold them into 18 or 21, where the page is touched):
  - `MfaSettingsPage` has cognitive complexity 20 (fallow audit exits 1).
  - The `PROFILE_KEY` cache is stale after enrolment.
  - The provisioning response isn't validated at runtime before `atob`.
  - Failed confirmations aren't audited.
  - There's no copy for a 409 at confirmation.
  - `aria-labelledby` sits on `<code>`.
- **T-RL-010** stays on the ledger until the last budgeted route (the MFA verification routes) is added. Extend
  `RateLimitBindingTest`.
- **20 (re-enable) and 27 (recovery runner):** call `PasswordService.issueForcedChangeCredential`. **27** must also
  stop `AdminBootstrap` from seeding while the runner runs (ADR-072).
- **23:** protect a pending invite by an explicit invite flag or column, **not** by role string. Review 14–16 M1:
  once USER-role invites exist, a stranger could re-register an invitee's address and cancel the admin-issued
  token. Also, a reset request outside dev mints an undeliverable token that cancels a pending admin-issued token
  (ADR-007); decide how to handle that. T-CRED-003, T-CRED-019 and T-ADM-002 wait for admin create and disable.
- **T-ADM-005** needs re-enable (20). **T-ADM-031** needs the tier-2 factor disable (19).
- **T-LCK-007** needs a restart harness on the same H2 file: `RestartHarness.onDatabase(dir)` exists. **T-LCK-008**
  needs a ctx-nondev concurrency proof at cost 12.
- **26:** OTLP export profile. Ticket 06 said the OTLP URL property must be added to
  `RequiredPropertiesPostProcessor.REQUIRED` under the export profile; that finishes T-CFG-038's last clause.
- **28:** delete the pending ledger; strict traceability. Also run the full release gate with the NVD key.

## Open items (not owned by any ticket yet; raise with the user at the end)

- Dependency-Check: re-run the full release gate after ticket 28, and whenever dependencies change (as ticket 17's
  zxing and commons-codec did). The OSS Index analyzer is disabled because it needs Sonatype credentials. The NVD
  key is set at Windows user level; a bash shell started earlier may need (never print the key):
  `export NVD_API_KEY="$(powershell -NoProfile -Command "[Environment]::GetEnvironmentVariable('NVD_API_KEY','User')" | tr -d '\r\n')"`
- Semgrep has never run (CLI not installed, Docker not running).
- The breach blocklist (`password/breach-slice.txt`) is a 35-entry seed, not a pinned corpus extract (R-CRED-006).
- Registration and activation write no audit rows. Deleting a lapsed pending registration is unaudited.
- A cross-lapse registration deadlock can give a 500 after the 1 s lock timeout (exotic).
- `SignIn` relies on Spring Session's private EXPIRED attribute name; T-SES-010 catches a rename.
- Timing differences on the email axis (register and reset request) and the username axis (a failed login on a real
  account does one extra write). Low.
- The early 429 lacks `HeaderWriterFilter` security headers (Low).
- Keyed audit rows (throttle, CSRF) appear up to about 16 minutes late, by design.
- Firefox Playwright flakiness appeared under heavy parallel load. It wasn't seen on two runs of the merged fix wave.
- The remaining Low findings are in `reviews/review-*.md`.

## Environment notes

- Maven: if a new download fails with PKIX, set `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`.
- After pulling, run `npm ci` in `frontend/`.
- Always pass `-Ddependency-check.skip=true` in the inner loop. Never pass `-DskipITs` or `-Dmaven.test.skip`.
- KB retrieval is unavailable because `uv` isn't installed.
