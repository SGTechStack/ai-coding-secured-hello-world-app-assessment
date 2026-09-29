# Handoff

The wayfinder map is complete. This directory is what the delivery pipeline consumes.

| File | What it is |
|---|---|
| [`spec.md`](spec.md) | The consolidated specification — every decision from the map's 21 tickets in one document |
| [`prd-deltas.md`](prd-deltas.md) | Every place the build will differ from `prd/assessment-prd.md`, with the ruling that caused it. **Read this before reviewing the build against the PRD** |
| [`secured-login-app.stories.yaml`](secured-login-app.stories.yaml) | 24 stories, 141 acceptance criteria. Validated against `dependency-orchestrator/schemas/stories-schema.json` |
| [`validation.md`](validation.md) | The validation record: what was audited, the twelve defects fixed, and the two residual risks. **Verdict: approved** |

The tickets themselves are in [`../issues/`](../issues/) and the index is [`../map.md`](../map.md). Nothing here restates their reasoning — where a decision looks arbitrary, the ticket has the citations.

---

## Two blockers before the pipeline can run

Both are environment, not planning. The artefacts above are complete regardless.

### 1. `gh` CLI is not installed

`stories-to-issues` shells out to `gh label create` and `gh issue create` (`issue-creator.py:263-288`). Without it, issues cannot be published and the chain stops after the orchestrator.

```bash
winget install --id GitHub.cli
gh auth login
gh issue list -L 1   # also confirms Issues are enabled on the repo
```

Whether GitHub Issues are enabled on `SGTechStack/ai-coding-secured-hello-world-app-assessment` **could not be determined** without `gh`. Worth checking before assuming this route works at all — if Issues are disabled, the pipeline needs a different tracker and `stories-to-issues` does not apply.

### 2. Python dependencies are missing

`python3` is 3.14.6 but has neither `pyyaml` nor `pydantic`, both of which `dependency-orchestrator` needs. The stories file above was validated using a throwaway venv at `%TEMP%\wfv` — **nothing was installed into your environment**, and that venv is disposable.

```bash
python3 -m pip install pyyaml pydantic jsonschema
```

---

## Running the pipeline

`convert-stories` is **not needed** — it exists to turn an *unstructured* file into the stories schema, and ours is already authored against that schema and validated.

### Step 1 — dependency-orchestrator

Local and non-destructive, but it spawns up to 20 concurrent mapper agents, so it is the expensive step. Decide the role first:

- `pm` → a resourcing report (duration, critical path, team-size options). Requires `project_type`.
- `dev` → an implementation schedule. Requires `team_size`.

```bash
mkdir -p artifacts
cat > artifacts/run-config.json << 'ENDJSON'
{
  "role": "dev",
  "project_name": "Secured Login App",
  "model": "claude-sonnet-4-6",
  "prompt_version": "1",
  "project_type": "web_fullstack",
  "stories_path": "C:/Users/TaniaKoh/Downloads/code/ai-coding-secured-hello-world-app-assessment/.scratch/secured-login-app/handoff/secured-login-app.stories.yaml",
  "mode": "fresh",
  "known_constraints": [],
  "team_size": 1,
  "max_concurrent_mappers": 20,
  "max_batch_attempts": 3,
  "monorepo": true
}
ENDJSON

python3 .agents/skills/dependency-orchestrator/orchestrator.py --config artifacts/run-config.json
```

Set the Bash timeout to **600000 ms** — the skill requires it. Outputs land in `artifacts/` (notably `processor-output.json`) and `reports/`.

⚠️ **Do not copy `.agents/skills/dependency-orchestrator/artifacts/run-config.json` as a template.** It is a stale legacy shape — it carries `platforms`, `story_count` and `feature_groups`, lacks `project_type`, and its `stories_path` points at someone else's machine. It fails today's validator. The block above follows `SKILL.md:173-188`.

**`known_constraints` is worth populating** from [`spec.md`](spec.md) §12 — the five ArchUnit rules, the Testcontainers requirement and the no-`@Scheduled` ban are real scheduling constraints, not just build gates.

### Step 2 — stories-to-issues *(blocked on `gh`)*

```bash
python3 .agents/skills/stories-to-issues/issue-creator.py artifacts/processor-output.json --dry-run
```

**Run `--dry-run` first.** Without it the script creates one live GitHub issue per story immediately — there is no confirmation prompt.

### Step 3 — issue-verification *(optional, recommended)*

Note the ordering wrinkle: its description says it vets issues "before publishing", but `stories-to-issues` publishes directly with no staging handoff. In practice it runs **after** issues exist and amends them in place.

### Step 4 — do-work, one issue at a time

`docs/agents/issue-tracker.md` **does not exist** in this repo, so `/do-work` cannot select the next issue itself — **every invocation must be passed an explicit issue reference**, or it will return `Status: blocked`.

`/do-work` also blocks before coding if the issue lacks acceptance criteria or identifiable test seams. The stories above carry criteria; the test seams come from [`spec.md`](spec.md) §12–13.

---

## Definition of done

Settled in [14](../issues/14-test-and-validation-plan.md). **The gates below run against the build, not against this handoff** — that distinction is the answer to the map's "handoff definition of done" question, which turned out to be incoherent as posed: there is no code at the moment this map closes.

**Per slice, inside `/do-work`:** `mvn verify` green (unit, H2 integration, Testcontainers PostgreSQL) · five ArchUnit rules · `semgrep` clean on changed code · frontend `tsc --noEmit`, lint, Vitest.

**Once, before the build is called finished:** `im8-review` clean · `dependency-check-maven` with no CVSS ≥ 7 · `browser-test` against the twelve PRD stories · all ArchUnit rows green.

**`pre-prod-check` is not required.** Three of its seven gates re-derive this map's work and it terminates in a human sign-off rather than a build gate. Recommended before any real deployment; not a condition for calling this build done.

---

## Three things to tell whoever builds this

1. **First boot is three steps** — log in, change password, log in again. The seeded administrator is flagged `requirePasswordChange`, and a successful change invalidates all sessions including the caller's. Say this in any demo script or it reads as broken software on the first thing a reviewer does.
2. **One claim on this map was never verified**: that a `Secure` cookie round-trips over `http://localhost`. [09](../issues/09-http-security-csrf-cors-headers.md) kept `secure: true` in every profile on that basis. It is the **first** test to run, and if it fails the fix is a one-line `dev`-only override.
3. **Re-pinning the `App-Standards` submodule invalidates every citation on this map.** They are `path:line` against `ff5ab820`. Treat a bump as a map-level event, not a routine update.
