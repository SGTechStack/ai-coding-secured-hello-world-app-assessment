"""The pipeline stages + retry/escalation logic.

Every stage is typed input -> artifact file(s) on disk, with no shared
mutable state. Two error types escape: StageError (exit 1) and NeedsUser
(exit 2).
"""

from __future__ import annotations

import asyncio
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

from pipeline import artifacts
from pipeline.agents import PromptContext, run_mapper

SKILL_DIR = Path(__file__).resolve().parent.parent
SCRIPTS_DIR = SKILL_DIR / "scripts"
CONFIGS_DIR = SKILL_DIR / "configs"


class StageError(RuntimeError):
    """Something broke. Exit 1."""
    pass


class NeedsUser(RuntimeError):
    """Only a human can resolve this. Exit 2."""
    pass


def _log(msg: str) -> None:
    print(f"[orchestrator] {msg}", flush=True)


def sh_ok(script: str, *args: str) -> str:
    """Run python3 scripts/<script> and raise StageError on non-zero."""
    cmd = ["python3", str(SCRIPTS_DIR / script), *args]
    _log(f"$ {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        # Include both stdout and stderr — stdout has structured remediation hints
        combined = (result.stdout.strip() + "\n" + result.stderr.strip()).strip()
        raise StageError(f"{script} failed (exit {result.returncode}): {combined}")
    return result.stdout


# ── Stage 1: Load and Validate Stories ────────────────────────────────────────

def load_and_validate_stories(cfg: Any) -> None:
    """Load the structured YAML stories file, validate it, and write stories.json.

    The YAML file is the source of truth for stories, groups, and releases.
    Validation is handled by RunConfig.stories_meta (which calls validate-stories.py).
    This stage converts the YAML into the stories.json format consumed by batching.
    """
    try:
        import yaml
    except ImportError:
        import subprocess as _sp
        _sp.check_call([sys.executable, "-m", "pip", "install", "--quiet",
                         "--break-system-packages", "pyyaml"])
        import yaml

    stories_path = cfg.stories_path
    try:
        meta = cfg.stories_meta  # triggers validation, raises ValueError on failure
    except ValueError as e:
        raise NeedsUser(
            f"Stories file validation failed. Please fix the file and re-run.\n{e}"
        )

    _log(f"Validated {meta.story_count} stories, "
         f"{meta.group_count} group(s), {meta.release_count} release(s)")

    # Load the YAML and write stories.json for downstream consumption
    with open(stories_path, encoding="utf-8") as f:
        data = yaml.safe_load(f)

    stories = data.get("stories", [])

    # Ensure defaults
    for story in stories:
        story.setdefault("group", "default")
        story.setdefault("release", 1)

    with open(artifacts.STORIES, "w") as f:
        json.dump(stories, f, indent=2)

    _log(f"Wrote {len(stories)} stories to {artifacts.STORIES}")


# ── Stage 2: Batch Stories ───────────────────────────────────────────────────

def batch_stories(cfg: Any) -> None:
    """Run batch-stories.py and enrich the manifest with content hashes."""
    sh_ok("batch-stories.py", str(artifacts.STORIES), str(artifacts.ARTIFACTS_DIR))

    # Enrich manifest with content_hash, output path, and story_ids
    manifest_path = artifacts.BATCH_MANIFEST
    with open(manifest_path) as f:
        manifest = json.load(f)

    for entry in manifest["batches"]:
        batch_file = artifacts.ARTIFACTS_DIR / entry["file"]
        batch_bytes = batch_file.read_bytes()
        key = artifacts.batch_cache_key(cfg, batch_bytes)
        entry["content_hash"] = key
        entry["output"] = f"cache/{key}.json"

        # Extract story IDs from the batch
        batch_data = json.loads(batch_bytes)
        if isinstance(batch_data, list):
            stories = batch_data
        elif isinstance(batch_data, dict) and "stories" in batch_data:
            stories = batch_data["stories"]
        else:
            stories = []
        entry["story_ids"] = [s.get("id", "") for s in stories]

    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)

    _log(f"Batched {manifest['total_stories']} stories into {len(manifest['batches'])} batches")


# ── Stage 3: Map All Batches ─────────────────────────────────────────────────

async def map_all_batches(cfg: Any, run: dict) -> None:
    """Fan-out: run backward mapping for all batches with concurrency control."""
    # Build prompt context — pass full standards to mappers
    standards = (CONFIGS_DIR / "standards.md").read_text()
    ctx = PromptContext(cfg, standards)

    # Read manifest
    with open(artifacts.BATCH_MANIFEST) as f:
        manifest = json.load(f)

    batches = manifest["batches"]
    total = len(batches)

    _log(f"Starting backward mapping: {total} batches, "
         f"{manifest['total_stories']} stories, "
         f"concurrency={cfg.max_concurrent_mappers}")

    # Cost/scale guard
    if total > 20:
        raise NeedsUser(
            f"This run would launch {total} mapping subagents for "
            f"{manifest['total_stories']} stories. Please confirm or adjust "
            f"max_concurrent_mappers."
        )

    sem = asyncio.Semaphore(cfg.max_concurrent_mappers)
    failures: dict[str, str] = {}

    async def process_batch(entry: dict) -> None:
        batch_file = artifacts.ARTIFACTS_DIR / entry["file"]
        key = entry["content_hash"]
        out_path = artifacts.CACHE_DIR / f"{key}.json"

        # Check cache
        cached = artifacts.cache_get(key)
        if cached is not None:
            # Validate cached output
            try:
                sh_ok("verify-batch-output.py", str(out_path), str(artifacts.BATCH_MANIFEST),
                      "--batch-num", str(entry.get("batch_num", 1)))
                _log(f"Cache hit: {entry['file']} → {key[:8]}...")
                return
            except StageError:
                _log(f"Cached output for {entry['file']} failed validation, re-mapping")

        # Cache miss — run mapper
        async with sem:
            error_context = ""
            for attempt in range(1, cfg.max_batch_attempts + 1):
                _log(f"Mapping {entry['file']} (attempt {attempt}/{cfg.max_batch_attempts})")
                try:
                    result = await run_mapper(
                        cfg, ctx, str(batch_file), str(out_path),
                        error_context=error_context,
                    )

                    # Validate output
                    sh_ok("verify-batch-output.py", str(out_path),
                          str(artifacts.BATCH_MANIFEST),
                          "--batch-num", str(entry.get("batch_num", 1)))

                    # Cache it with provenance
                    artifacts.cache_put(key, result, {
                        "story_ids": entry.get("story_ids", []),
                        "model": cfg.model,
                        "models_used": [cfg.model],
                        "prompt_version": cfg.prompt_version,
                        "mapped_at": artifacts._now_iso(),
                        "batch_file": entry["file"],
                    })
                    _log(f"Mapped {entry['file']} → {key[:8]}...")
                    return

                except (StageError, RuntimeError) as e:
                    if attempt < cfg.max_batch_attempts:
                        error_context = (
                            f"Previous attempt failed with: {e}\n"
                            f"Fix these errors while PRESERVING existing node IDs "
                            f"(do not renumber)."
                        )
                    else:
                        failures[entry["file"]] = str(e)

    # Add batch_num to entries for verify-batch-output.py
    for i, entry in enumerate(batches, 1):
        entry["batch_num"] = i

    await asyncio.gather(*(process_batch(entry) for entry in batches))

    if failures:
        msg_parts = [f"  {name}: {err}" for name, err in failures.items()]
        raise StageError(
            f"{len(failures)} batch(es) failed mapping:\n" + "\n".join(msg_parts)
        )


# ── Stage 4: Merge All ──────────────────────────────────────────────────────

def merge_all(cfg: Any) -> None:
    """Deterministic, order-critical, single-threaded merge.

    Groups manifest entries by release, merges in ascending order,
    validates before sealing.
    """
    dag = str(artifacts.PROJECT_DAG)
    stories = str(artifacts.STORIES)

    with open(artifacts.BATCH_MANIFEST) as f:
        manifest = json.load(f)

    # If fresh mode, remove existing DAG first
    if cfg.mode == "fresh":
        for p in [artifacts.PROJECT_DAG, artifacts.RELEASES_SIDECAR]:
            if p.exists():
                p.unlink()
                _log(f"Removed {p} (fresh mode)")

    # Group by release
    by_release: dict[int, list[dict]] = {}
    for entry in manifest["batches"]:
        release = entry.get("release", 1)
        by_release.setdefault(release, []).append(entry)

    releases_sorted = sorted(by_release.keys())

    for idx, release in enumerate(releases_sorted):
        entries = by_release[release]
        _log(f"Merging release {release}: {len(entries)} batch(es)")

        for entry in entries:
            out_path = str(artifacts.ARTIFACTS_DIR / entry["output"])
            sh_ok("merge-batch.py", dag, out_path, "--release", str(release),
                  "--project-name", cfg.project_name)

        # Validate stories after all batches for this release
        _validate_stories(dag, stories, release)

        # Seal release (skip for the first release — no prior release to seal against)
        if idx > 0:
            sh_ok("merge-batch.py", dag, "--seal-release")
            _log(f"Sealed release {release}")

    _log("Merge complete")


def _validate_stories(dag: str, stories: str, release: int) -> None:
    """Validate that all stories are present in the DAG.

    TODO: Recovery flow — extract missing IDs → write recovery batch →
    run_mapper → merge with same --release → re-validate. For now, raise
    StageError so the failure is loud.
    """
    try:
        sh_ok("merge-batch.py", dag, "--validate-stories", stories, "--release", str(release))
    except StageError as e:
        raise StageError(
            f"Story validation failed for release {release}: {e}\n"
            f"Some stories are missing from the DAG. This usually means a "
            f"backward mapping subagent dropped stories."
        )


# ── Stage 5: Process DAG ────────────────────────────────────────────────────

def process_dag(cfg: Any) -> dict:
    """Run dag-processor.py with expected story count validation.

    If a cycle is detected, retries with --break-cycles to auto-remove
    the weakest edge(s). Logs all removed edges for auditability.
    """
    dag_path = artifacts.PROJECT_DAG
    if not dag_path.exists():
        raise StageError(f"DAG file not found: {dag_path}")

    with open(dag_path) as f:
        dag = json.load(f)

    expected = len(dag.get("user_stories", []))
    _log(f"Processing DAG: {expected} stories")

    output = sh_ok(
        "dag-processor.py", str(dag_path),
        "--expected-stories", str(expected),
    )

    # Write processor output
    artifacts.PROCESSOR_OUTPUT.write_text(output)

    result = json.loads(output)

    # If cycle detected, retry with automatic cycle breaking
    if not result.get("valid", False) and "cycle" in result.get("error", "").lower():
        _log("Cycle detected — retrying with --break-cycles")
        output = sh_ok(
            "dag-processor.py", str(dag_path),
            "--expected-stories", str(expected),
            "--break-cycles",
        )
        artifacts.PROCESSOR_OUTPUT.write_text(output)
        result = json.loads(output)

        # Log what was removed
        for brk in result.get("cycle_breaks", []):
            _log(f"Cycle break: removed {brk['from']} → {brk['to']} "
                 f"({brk['reason']})")

    if not result.get("valid", False):
        raise StageError(
            f"DAG processor reported invalid: {result.get('error', 'unknown error')}"
        )

    return result


# ── Stage 6: Generate Reports ───────────────────────────────────────────────

def generate_reports(cfg: Any) -> list[Path]:
    """Run report-generator.py for the configured role."""
    reports_dir = str(artifacts.REPORTS_DIR)
    proc_output = str(artifacts.PROCESSOR_OUTPUT)

    args = [proc_output, reports_dir, "--role", cfg.role]
    if cfg.role == "dev" and cfg.team_size:
        args.extend(["--team-size", str(cfg.team_size)])

    sh_ok("report-generator.py", *args)

    # Collect generated files
    generated = list(artifacts.REPORTS_DIR.glob("*.html")) + \
                list(artifacts.REPORTS_DIR.glob("*.md"))
    _log(f"Generated {len(generated)} report(s) in {reports_dir}")
    return generated
