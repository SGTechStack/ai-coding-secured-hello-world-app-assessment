# Dependency Orchestrator — Driver Architecture

## Layout

```
dependency-orchestrator/
├── SKILL.md                 # Interview-only skill (~150 lines)
├── SKILL.md.bak             # Full original skill (kept for reference)
├── orchestrator.py           # Single entry point — runs the pipeline
├── pipeline/
│   ├── config.py             # RunConfig (pydantic) — skill→driver contract
│   ├── artifacts.py          # Paths, fingerprint, resume state, cache, history
│   ├── agents.py             # Agent SDK wrappers + prompt assembly
│   ├── stages.py             # Six pipeline stages + retry/escalation
│   └── standards_filter.py   # Standards.md section filter by scope
├── prompts/
│   ├── parser.md             # Parser subagent prompt template
│   └── backward-mapper.md    # Backward mapping subagent prompt template
├── configs/                  # UNCHANGED — reference material
│   ├── backwards-mapping-rules.md
│   ├── standards.md
│   ├── duration-defaults.md
│   └── *.md (foundation templates)
├── scripts/                  # UNCHANGED — deterministic scripts
│   ├── batch-stories.py
│   ├── precount-stories.py
│   ├── verify-parse.py
│   ├── verify-batch-output.py
│   ├── merge-batch.py
│   ├── dag-processor.py
│   └── report-generator.py
└── tests/
    ├── test_config.py
    └── test_artifacts.py
```

## Module Responsibilities

| Module | Owns |
|---|---|
| `config.py` | Validates `run-config.json` eagerly. Fails in the first second, not 25 minutes in. |
| `artifacts.py` | File paths, fingerprinting (model excluded!), resume state, batch cache (content-hash keyed), run history archive. |
| `agents.py` | Wraps `claude_agent_sdk.query()` for parser and mapper subagents. `PromptContext` loads reference material once. |
| `stages.py` | Six stages: `parse_and_verify`, `batch_stories`, `map_all_batches`, `merge_all`, `process_dag`, `generate_reports`. Each is typed input → artifact files on disk. |
| `standards_filter.py` | Filters `configs/standards.md` to only include sections relevant to in-scope standard categories. |
| `orchestrator.py` | CLI entry point. Stage guards (`is_complete` checks), role branching (PM vs Dev), exit codes, history, cleanup. |

## Running Without the Skill

```bash
# 1. Write artifacts/run-config.json manually (see SKILL.md for schema)
# 2. Run:
python3 dependency-orchestrator/orchestrator.py --config artifacts/run-config.json

# Other commands:
python3 dependency-orchestrator/orchestrator.py --history     # view run history
python3 dependency-orchestrator/orchestrator.py --remap       # re-derive on current model
python3 dependency-orchestrator/orchestrator.py --clean       # remove transients
python3 dependency-orchestrator/orchestrator.py --clean-all --yes  # remove everything
```

## Tracing an Edge

To find which batch produced a specific story or edge:

```bash
# Find which batch contains story AFW-140
jq '.batches[] | select(.story_ids | index("AFW-140"))' artifacts/batch-manifest.json

# Find the cached output for that batch
jq '.batches[] | select(.story_ids | index("AFW-140")) | .output' artifacts/batch-manifest.json

# Read the cached output
cat artifacts/$(jq -r '.batches[] | select(.story_ids | index("AFW-140")) | .output' artifacts/batch-manifest.json)
```

## Key Design Decisions

- **Model is metadata, not a cache key.** Changing `model` in the config never invalidates cached work. Use `--remap` to explicitly re-derive.
- **Content-hash keyed cache.** Batch outputs are stored by `sha256(batch_content + prompt_version + platforms)`, not batch number. Edit one story and only the affected batch re-maps.
- **Run history is append-only.** Every completed or superseded run is archived to `artifacts/runs/` with full config snapshots and result metrics.
- **Durable artifacts are protected by allowlist.** `_invalidate_transient()` refuses to delete `project-dag.json`, `cache/`, `runs/`, or `batch-manifest.json`.

## Tests

```bash
python3 -m pytest tests/ -v
```
