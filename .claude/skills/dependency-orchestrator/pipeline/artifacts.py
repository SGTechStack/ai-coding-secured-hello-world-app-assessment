"""Path constants, resume state, batch cache, and run history.

Two independent layers:
  1. Stage-level resume via run.json (fingerprint + stage completion)
  2. Batch-level cache via cache/ (content-hash keyed, survives across runs)

Critical invariant: _invalidate_transient() must NEVER delete durable artifacts.
"""

from __future__ import annotations

import hashlib
import json
import os
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional

# ── Path constants ───────────────────────────────────────────────────────────

ARTIFACTS_DIR = Path("artifacts")
CACHE_DIR = ARTIFACTS_DIR / "cache"
RUNS_DIR = ARTIFACTS_DIR / "runs"
REPORTS_DIR = Path("reports")

# Key file paths
RUN_CONFIG = ARTIFACTS_DIR / "run-config.json"
RUN_STATE = ARTIFACTS_DIR / "run.json"
STORIES = ARTIFACTS_DIR / "stories.json"
BATCH_MANIFEST = ARTIFACTS_DIR / "batch-manifest.json"
PROJECT_DAG = ARTIFACTS_DIR / "project-dag.json"
RELEASES_SIDECAR = ARTIFACTS_DIR / "project-dag.json.releases.json"
PROCESSOR_OUTPUT = ARTIFACTS_DIR / "processor-output.json"
CACHE_INDEX = CACHE_DIR / "index.json"
RUNS_INDEX = RUNS_DIR / "index.jsonl"

# ── Durable set — NEVER deleted by _invalidate_transient() ──────────────────
# This is an explicit allowlist, not a glob exclusion. _invalidate_transient()
# refuses to unlink anything matching these. This is the single highest-cost
# bug in the design and it must be impossible by construction.

DURABLE_PATHS = frozenset({
    PROJECT_DAG,
    RELEASES_SIDECAR,
    BATCH_MANIFEST,
    CACHE_DIR,
    CACHE_INDEX,
    RUNS_DIR,
    RUNS_INDEX,
})

# Transient files — safe to delete on fingerprint change
_TRANSIENT_GLOBS = [
    "stories.json",
    "batch-*.json",       # input batch files only (cache holds outputs)
    "processor-output.json",
]


def _is_durable(path: Path) -> bool:
    """Check if a path is in the durable set or under a durable directory."""
    resolved = path.resolve()
    for dp in DURABLE_PATHS:
        dp_resolved = dp.resolve()
        if resolved == dp_resolved:
            return True
        # Check if path is under a durable directory
        try:
            resolved.relative_to(dp_resolved)
            return True
        except ValueError:
            pass
    return False


def ensure_dirs() -> None:
    """Create all required directories."""
    ARTIFACTS_DIR.mkdir(exist_ok=True)
    CACHE_DIR.mkdir(exist_ok=True)
    RUNS_DIR.mkdir(exist_ok=True)
    REPORTS_DIR.mkdir(exist_ok=True)


# ── Fingerprint ──────────────────────────────────────────────────────────────

def fingerprint(cfg: Any) -> str:
    """Compute a fingerprint for the run config + stories file content.

    Deliberately EXCLUDES model (see "Model is metadata, not a cache key").
    INCLUDES prompt_version (the deliberate lever for re-derivation).
    """
    h = hashlib.sha256()

    # Stories file content
    if cfg.stories_path and Path(cfg.stories_path).exists():
        h.update(Path(cfg.stories_path).read_bytes())

    # Config identity (minus model)
    # Story count, releases, and feature groups are derived from the YAML file,
    # which is already included via stories file content hash above.
    identity = {
        "project_name": cfg.project_name,
        "project_type": cfg.project_type,
        "mode": cfg.mode,
        "prompt_version": cfg.prompt_version,
        "monorepo": cfg.monorepo,
    }
    h.update(json.dumps(identity, sort_keys=True).encode())

    return h.hexdigest()[:16]


# ── Batch cache ──────────────────────────────────────────────────────────────

def batch_cache_key(cfg: Any, batch_bytes: bytes) -> str:
    """Content-hash for a batch. Model is deliberately NOT included."""
    h = hashlib.sha256()
    h.update(batch_bytes)
    h.update(cfg.prompt_version.encode())
    h.update((cfg.project_type or "").encode())
    return h.hexdigest()[:32]


def cache_get(key: str) -> Optional[dict]:
    """Return cached batch output if it exists, else None."""
    path = CACHE_DIR / f"{key}.json"
    if not path.exists():
        return None
    try:
        with open(path) as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return None


def cache_put(key: str, obj: dict, provenance: dict) -> Path:
    """Write batch output to cache and update the index."""
    path = CACHE_DIR / f"{key}.json"
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)

    # Update cache index
    index = _load_cache_index()
    index[key] = provenance
    with open(CACHE_INDEX, "w") as f:
        json.dump(index, f, indent=2)

    return path


def _load_cache_index() -> dict:
    if CACHE_INDEX.exists():
        try:
            with open(CACHE_INDEX) as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            pass
    return {}


def cache_provenance_summary(current_model: str) -> Optional[str]:
    """Return a warning line if cache holds outputs from other models, else None."""
    index = _load_cache_index()
    if not index:
        return None

    other_models: dict[str, int] = {}
    for entry in index.values():
        cached_model = entry.get("model", "unknown")
        if cached_model != current_model:
            other_models[cached_model] = other_models.get(cached_model, 0) + 1

    if not other_models:
        return None

    parts = []
    for model, count in other_models.items():
        parts.append(f"{model} ({count} batches)")

    return (
        f"cache holds outputs from {', '.join(parts)}; "
        f"current model is {current_model}. Continuing with cached results. "
        f"Use --remap to re-derive everything."
    )


# ── Run state (resume) ──────────────────────────────────────────────────────

def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _compact_timestamp(iso: str) -> str:
    """Convert ISO timestamp to shell-friendly filename component."""
    return iso.replace(":", "").replace("-", "").replace("+00:00", "").replace("T", "T")[:15]


def _fresh_run(fp: str, model: str) -> dict:
    return {
        "fingerprint": fp,
        "model": model,
        "prompt_version": "1",
        "started": _now_iso(),
        "stages": {},
    }


def load_run(cfg: Any, *, reset: bool = False) -> dict:
    """Load or create run state. Archives on fingerprint mismatch.

    If no current run matches, searches run history for the same fingerprint
    and restores its completed stages (so work isn't lost if run.json was
    superseded or deleted).
    """
    fp = fingerprint(cfg)

    if reset and RUN_STATE.exists():
        old = _read_run_state()
        if old:
            archive_run(old, outcome="superseded", superseded_by=fp)
        _invalidate_transient()
        run = _fresh_run(fp, cfg.model)
        _write_run_state(run)
        return run

    if RUN_STATE.exists():
        old = _read_run_state()
        if old and old.get("fingerprint") == fp:
            # Same fingerprint → resume. Nothing archived.
            return old
        # Different fingerprint → supersession
        if old:
            archive_run(old, outcome="superseded", superseded_by=fp)
        _invalidate_transient()

    # Check archive for a previous run with the same fingerprint
    restored = _restore_from_archive(fp, cfg.model)
    if restored:
        _write_run_state(restored)
        return restored

    run = _fresh_run(fp, cfg.model)
    _write_run_state(run)
    return run


def _restore_from_archive(fp: str, model: str) -> Optional[dict]:
    """Search run history for a matching fingerprint and restore its stages.

    Only restores stages whose output files still exist on disk.
    """
    if not RUNS_DIR.exists():
        return None

    # Search archive files for matching fingerprint (newest first by filename)
    candidates = sorted(RUNS_DIR.glob("*.json"), reverse=True)
    for path in candidates:
        if path.name == "index.jsonl":
            continue
        try:
            with open(path) as f:
                record = json.load(f)
        except (json.JSONDecodeError, OSError):
            continue
        if record.get("fingerprint") != fp:
            continue

        # Found a match — rebuild run state with stages that are still valid
        run = _fresh_run(fp, model)
        for stage, info in record.get("stages", {}).items():
            if info.get("status") == "ok":
                # Only restore if all output files still exist
                outputs_exist = all(
                    Path(p).exists() for p in info.get("outputs", [])
                )
                if outputs_exist:
                    run["stages"][stage] = info

        if run["stages"]:
            restored = [s for s in run["stages"]]
            import logging
            logging.getLogger(__name__).info(
                "Restored stages %s from archived run %s", restored, path.name
            )
            return run

    return None


def _read_run_state() -> Optional[dict]:
    try:
        with open(RUN_STATE) as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return None


def _write_run_state(run: dict) -> None:
    ARTIFACTS_DIR.mkdir(exist_ok=True)
    with open(RUN_STATE, "w") as f:
        json.dump(run, f, indent=2)


def is_complete(run: dict, stage: str) -> bool:
    """A stage is complete only if recorded as 'ok' AND all output files exist."""
    info = run.get("stages", {}).get(stage)
    if not info or info.get("status") != "ok":
        return False
    for out in info.get("outputs", []):
        if not Path(out).exists():
            return False
    return True


def mark(run: dict, stage: str, outputs: list[str | Path], **extra: Any) -> None:
    """Record a stage as completed with its output files."""
    run.setdefault("stages", {})[stage] = {
        "status": "ok",
        "at": _now_iso(),
        "outputs": [str(p) for p in outputs],
        **extra,
    }
    _write_run_state(run)


def mark_failed(run: dict, stage: str, error: str) -> None:
    """Record a stage failure."""
    run.setdefault("stages", {})[stage] = {
        "status": "failed",
        "at": _now_iso(),
        "error": error,
    }
    _write_run_state(run)


# ── Run history (archive) ───────────────────────────────────────────────────

def archive_run(
    run: dict,
    *,
    outcome: str,
    cfg: Any = None,
    result: Optional[dict] = None,
    error: Optional[str] = None,
    superseded_by: Optional[str] = None,
) -> Path:
    """Write a run record to runs/ and append to index.jsonl. Write-once, never edited."""
    RUNS_DIR.mkdir(exist_ok=True)

    started = run.get("started", _now_iso())
    fp = run.get("fingerprint", "unknown")
    compact = _compact_timestamp(started)
    fp8 = fp[:8]
    filename = f"{compact}-{fp8}.json"
    record_path = RUNS_DIR / filename

    record = {
        "fingerprint": fp,
        "started": started,
        "ended": _now_iso(),
        "outcome": outcome,
        "superseded_by": superseded_by,
        "model": run.get("model"),
        "prompt_version": run.get("prompt_version"),
        "config": cfg.model_dump() if cfg and hasattr(cfg, "model_dump") else None,
        "stages": run.get("stages", {}),
        "counts": None,
        "result": result,
        "error": error,
    }

    with open(record_path, "w") as f:
        json.dump(record, f, indent=2)

    # Append to index.jsonl
    index_line = {
        "started": started,
        "fingerprint": fp,
        "outcome": outcome,
        "model": run.get("model"),
    }
    if result:
        for key in ("stories", "makespan_days", "max_useful_developers"):
            if key in result:
                index_line[key] = result[key]

    with open(RUNS_INDEX, "a") as f:
        f.write(json.dumps(index_line) + "\n")

    return record_path


def read_history(limit: Optional[int] = None) -> list[dict]:
    """Parse index.jsonl, return list of records (newest last)."""
    if not RUNS_INDEX.exists():
        return []
    records = []
    with open(RUNS_INDEX) as f:
        for line in f:
            line = line.strip()
            if line:
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
    if limit:
        records = records[-limit:]
    return records


# ── Cleanup ──────────────────────────────────────────────────────────────────

def _invalidate_transient() -> None:
    """Delete transient artifacts. NEVER touches durable paths."""
    if not ARTIFACTS_DIR.exists():
        return
    for pattern in _TRANSIENT_GLOBS:
        for path in ARTIFACTS_DIR.glob(pattern):
            if _is_durable(path):
                continue
            path.unlink(missing_ok=True)
    # Also remove run.json itself (it's being replaced)
    if RUN_STATE.exists():
        RUN_STATE.unlink(missing_ok=True)


def clean(
    *,
    include_cache: bool = False,
    include_dag: bool = False,
    include_history: bool = False,
) -> list[str]:
    """Clean artifacts. Returns list of removed paths."""
    removed = []

    # Always remove transients
    _invalidate_transient()
    removed.append("transient artifacts")

    if include_dag:
        for p in [PROJECT_DAG, RELEASES_SIDECAR]:
            if p.exists():
                p.unlink()
                removed.append(str(p))

    if include_cache:
        if CACHE_DIR.exists():
            # Remove cache JSON files but keep the directory and index
            for p in CACHE_DIR.glob("*.json"):
                if p.name != "index.json":
                    p.unlink()
                    removed.append(str(p))

    if include_history:
        if RUNS_DIR.exists():
            shutil.rmtree(RUNS_DIR)
            RUNS_DIR.mkdir(exist_ok=True)
            removed.append(str(RUNS_DIR))

    return removed


def clean_for_remap() -> None:
    """Drop cache outputs (keeping index history), leave DAG intact."""
    if CACHE_DIR.exists():
        for p in CACHE_DIR.glob("*.json"):
            if p.name != "index.json":
                p.unlink()
    # Also clear batch-related stage completion so they re-run
    if RUN_STATE.exists():
        run = _read_run_state()
        if run:
            for stage in ["map", "merge", "process", "report"]:
                run.get("stages", {}).pop(stage, None)
            _write_run_state(run)
