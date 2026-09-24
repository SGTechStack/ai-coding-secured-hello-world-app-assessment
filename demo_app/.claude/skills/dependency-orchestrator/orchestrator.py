#!/usr/bin/env python3
"""Dependency Orchestrator — single entry point.

Replaces the ~600-line SKILL.md orchestration with code that enforces
invariants structurally. The LLM still does the judgment work (parsing,
backward mapping) — it just gets *called by* code instead of *calling* code.

Exit codes:
    0 — success
    1 — StageError (something broke)
    2 — NeedsUser (only a human can resolve this)

Usage:
    python3 dependency-orchestrator/orchestrator.py [--config PATH] [OPTIONS]
"""

from __future__ import annotations

import argparse
import asyncio
import hashlib
import json
import sys
from pathlib import Path

# Ensure the skill directory is on the path
sys.path.insert(0, str(Path(__file__).resolve().parent))

from pipeline.config import RunConfig
from pipeline import artifacts
from pipeline.stages import (
    StageError,
    NeedsUser,
    load_and_validate_stories,
    batch_stories,
    map_all_batches,
    merge_all,
    process_dag,
    generate_reports,
    _log,
)


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()


def _extract_result_metrics(proc_output_path: Path, dag_path: Path) -> dict:
    """Extract metrics from processor output for the run archive."""
    try:
        with open(proc_output_path) as f:
            proc = json.load(f)
        summary = proc.get("summary", {})
        dag_data = proc.get("dag", {})
        return {
            "stories": len(dag_data.get("user_stories", [])),
            "nodes": len(dag_data.get("nodes", [])),
            "edges": len(dag_data.get("edges", [])),
            "makespan_days": summary.get("makespan_days"),
            "max_useful_developers": summary.get("max_useful_developers"),
            "critical_path_days": summary.get("critical_path_days"),
            "dag_sha256": _sha256_file(dag_path) if dag_path.exists() else None,
        }
    except (json.JSONDecodeError, OSError):
        return {}


def _print_history(limit: int | None = None) -> None:
    """Print run history as a table."""
    history = artifacts.read_history(limit)
    if not history:
        print("No run history found.")
        return

    # Header
    print(f"{'Started':<22} {'Outcome':<12} {'Model':<25} "
          f"{'Stories':>8} {'Makespan':>9} {'Max Devs':>9}")
    print("-" * 90)

    for rec in history:
        started = rec.get("started", "?")[:19]
        outcome = rec.get("outcome", "?")
        model = rec.get("model", "?")
        stories = rec.get("stories", "")
        makespan = rec.get("makespan_days", "")
        max_devs = rec.get("max_useful_developers", "")
        print(f"{started:<22} {outcome:<12} {model:<25} "
              f"{stories:>8} {makespan:>9} {max_devs:>9}")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Dependency Orchestrator — build project DAGs and reports"
    )
    p.add_argument("--config", default="artifacts/run-config.json",
                   help="Path to run-config.json (default: artifacts/run-config.json)")
    p.add_argument("--reset", action="store_true",
                   help="Reset run state and start fresh")
    p.add_argument("--clean", action="store_true",
                   help="Remove transient artifacts (keeps cache and history)")
    p.add_argument("--clean-cache", action="store_true",
                   help="Remove cached batch outputs")
    p.add_argument("--clean-all", action="store_true",
                   help="Remove everything including history (requires --yes)")
    p.add_argument("--yes", action="store_true",
                   help="Skip confirmation for destructive operations")
    p.add_argument("--remap", action="store_true",
                   help="Re-derive all batches on the current model")
    p.add_argument("--archive-dag", action="store_true",
                   help="Copy project-dag.json to runs/ on success")
    p.add_argument("--history", action="store_true",
                   help="Print run history and exit")
    return p.parse_args()


async def run_pipeline(cfg: RunConfig, run: dict, args: argparse.Namespace) -> dict:
    """Execute the pipeline stages with resume guards."""

    if cfg.role == "dev" and artifacts.PROJECT_DAG.exists() and cfg.mode != "fresh":
        # Dev shortcut: DAG already exists, just process + report
        if not artifacts.is_complete(run, "process"):
            result = process_dag(cfg)
            artifacts.mark(run, "process", [str(artifacts.PROCESSOR_OUTPUT)])

        if not artifacts.is_complete(run, "report"):
            reports = generate_reports(cfg)
            artifacts.mark(run, "report", [str(p) for p in reports])

        return _extract_result_metrics(artifacts.PROCESSOR_OUTPUT, artifacts.PROJECT_DAG)

    # Dev from scratch needs stories_path and project_type
    if cfg.role == "dev" and not artifacts.PROJECT_DAG.exists():
        if not cfg.stories_path:
            raise NeedsUser(
                "No project DAG found and no stories_path configured. "
                "Please provide stories_path and project_type in run-config.json "
                "to build the DAG from scratch, or have a PM run first."
            )
        if not cfg.project_type:
            raise NeedsUser(
                "No project DAG found and no project_type configured. "
                "Please provide project_type in run-config.json to build from scratch."
            )

    # Full pipeline: load → batch → map → merge → process → report
    if not artifacts.is_complete(run, "load"):
        load_and_validate_stories(cfg)
        artifacts.mark(run, "load", [str(artifacts.STORIES)])

    if not artifacts.is_complete(run, "batch"):
        batch_stories(cfg)
        artifacts.mark(run, "batch", [str(artifacts.BATCH_MANIFEST)])

    if not artifacts.is_complete(run, "map"):
        await map_all_batches(cfg, run)
        artifacts.mark(run, "map", [str(artifacts.CACHE_DIR)])

    if not artifacts.is_complete(run, "merge"):
        merge_all(cfg)
        artifacts.mark(run, "merge", [
            str(artifacts.PROJECT_DAG),
            str(artifacts.RELEASES_SIDECAR),
        ])

    if not artifacts.is_complete(run, "process"):
        result = process_dag(cfg)
        artifacts.mark(run, "process", [str(artifacts.PROCESSOR_OUTPUT)])

    if not artifacts.is_complete(run, "report"):
        reports = generate_reports(cfg)
        artifacts.mark(run, "report", [str(p) for p in reports])

    return _extract_result_metrics(artifacts.PROCESSOR_OUTPUT, artifacts.PROJECT_DAG)


def main() -> int:
    args = parse_args()

    # History mode
    if args.history:
        _print_history()
        return 0

    # Cleanup modes
    if args.clean_all:
        if not args.yes:
            print("[orchestrator] WARNING: --clean-all removes ALL artifacts "
                  "including run history. Use --yes to confirm.")
            return 1
        artifacts.clean(include_cache=True, include_dag=True, include_history=True)
        _log("All artifacts removed")
        return 0

    if args.clean:
        artifacts.clean()
        _log("Transient artifacts removed (cache and history preserved)")
        return 0

    if args.clean_cache:
        artifacts.clean(include_cache=True)
        _log("Cache cleared (history preserved)")
        return 0

    # Load config
    config_path = Path(args.config)
    if not config_path.exists():
        print(f"[orchestrator] ERROR: Config not found: {config_path}")
        return 1

    try:
        cfg = RunConfig.load(config_path)
    except Exception as e:
        print(f"[orchestrator] ERROR: Invalid config: {e}")
        return 1

    # Ensure directories exist
    artifacts.ensure_dirs()

    # Remap mode
    if args.remap:
        _log("Remap mode: clearing cache, all batches will re-derive")
        artifacts.clean_for_remap()

    # Load or create run state
    run = artifacts.load_run(cfg, reset=args.reset)

    # Check for mixed-model cache
    warning = artifacts.cache_provenance_summary(cfg.model)
    if warning:
        _log(warning)

    # Stamp model provenance into DAG sidecar if DAG exists
    # (done at merge time in merge_all, but also stamp here for add-on runs)

    _log(f"Run {run['fingerprint'][:8]}... | model={cfg.model} | "
         f"mode={cfg.mode} | role={cfg.role}")

    # Resume info
    completed = [s for s, info in run.get("stages", {}).items()
                 if info.get("status") == "ok"]
    if completed:
        _log(f"Resuming: stages {', '.join(completed)} already complete")

    try:
        result = asyncio.run(run_pipeline(cfg, run, args))

        # Archive on success
        artifacts.archive_run(run, outcome="completed", cfg=cfg, result=result)

        # Optional DAG archive
        if args.archive_dag and artifacts.PROJECT_DAG.exists():
            import shutil
            started = run.get("started", "")
            fp8 = run["fingerprint"][:8]
            compact = artifacts._compact_timestamp(started)
            dag_archive = artifacts.RUNS_DIR / f"{compact}-{fp8}-dag.json"
            shutil.copy2(artifacts.PROJECT_DAG, dag_archive)
            _log(f"DAG archived to {dag_archive}")

        _log("Pipeline complete")
        return 0

    except NeedsUser as e:
        # Do NOT archive — run is paused, not finished
        print(f"\n[orchestrator] ACTION REQUIRED: {e}")
        print("[orchestrator] Update run-config.json and re-run. "
              "Completed stages will be skipped.")
        return 2

    except StageError as e:
        # Archive as failed
        artifacts.archive_run(
            run, outcome="failed", cfg=cfg, error=str(e)
        )
        print(f"\n[orchestrator] ERROR: {e}")
        print("[orchestrator] Re-running will skip completed stages.")
        return 1

    except Exception as e:
        # Unexpected error — archive as failed
        artifacts.archive_run(
            run, outcome="failed", cfg=cfg, error=str(e)
        )
        print(f"\n[orchestrator] UNEXPECTED ERROR: {e}")
        import traceback
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    sys.exit(main())
