#!/usr/bin/env python3
"""
reconcile.py -- Reconcile project schedule with actual GitHub issue closure
============================================================================
Reads the original project DAG + analyzer output, fetches GitHub issues to
determine which stories are closed, then recomputes the schedule with
completed stories' node durations set to zero.

The DAG structure (nodes, edges, dependencies) stays unchanged.
What changes: node durations for completed work -> 0, so CPM recomputes
float for all remaining nodes, and the greedy simulation re-prioritizes
to produce an updated projected finish.

Output:
  - reconciled-analyzer-output.json  (updated analysis)
  - pm-report.html                   (updated PM report with progress)
  - dev-report.html                  (updated dev report with progress)
  - dev-report.md                    (updated markdown report with progress)

Usage:
  python3 reconcile.py <project-dag.json> <analyzer-output.json> <output-dir> \
    [--repo OWNER/REPO] [--team-size N] [--label LABEL] [--project-start YYYY-MM-DD]

  If --repo is omitted, `gh` uses the current repo.
  If --label is provided, only issues with that label are matched.
  If --project-start is provided, elapsed days are computed from that date.
"""

from __future__ import annotations

import argparse
import copy
import json
import re
import subprocess
import sys
from datetime import datetime, date, timezone
from pathlib import Path

from dag_analyzer import analyze_dag
from report_generator import (
    load_inputs,
    generate_pm_report,
    generate_dev_report,
    generate_delivery_plan,
)


# -- GitHub issue fetching ---------------------------------------------------

def fetch_issues(repo: str | None = None, labels: list[str] | None = None) -> list[dict]:
    """Fetch all issues from the GitHub repo via gh CLI."""
    cmd = [
        "gh", "issue", "list",
        "--state", "all",
        "--json", "title,state,closedAt,createdAt,number,url",
        "--limit", "1000",
    ]
    if repo:
        cmd.extend(["--repo", repo])
    if labels:
        for label in labels:
            cmd.extend(["--label", label])

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"ERROR fetching issues: {result.stderr.strip()}", file=sys.stderr)
        sys.exit(1)

    return json.loads(result.stdout)


def parse_story_id(title: str) -> str | None:
    """Extract story ID from issue title. Expected format: 'STORY-ID: title'."""
    if ":" in title:
        return title.split(":")[0].strip()
    return None


def map_issues_to_stories(issues: list[dict], dag: dict) -> dict[str, dict]:
    """Map GitHub issues to story IDs. Returns {story_id: issue_info}."""
    story_ids = {s["id"] for s in dag.get("user_stories", [])}
    status = {}

    for issue in issues:
        sid = parse_story_id(issue.get("title", ""))
        if sid and sid in story_ids:
            # If multiple issues match a story, prefer the highest issue number
            # (most recently created) as it reflects the latest tracking state
            existing = status.get(sid)
            if existing and existing["number"] >= issue.get("number", 0):
                continue
            status[sid] = {
                "state": issue.get("state", "OPEN"),
                "closed_at": issue.get("closedAt"),
                "created_at": issue.get("createdAt"),
                "number": issue.get("number"),
                "url": issue.get("url", ""),
            }

    return status


# -- DAG modification --------------------------------------------------------

def reconcile_dag(dag: dict, completed_stories: set[str]) -> dict:
    """
    Create a modified copy of the DAG where completed stories' nodes
    have duration_days = 0. The structure (edges, stories) is unchanged.
    """
    modified = copy.deepcopy(dag)
    for node in modified["nodes"]:
        if node.get("story_id") in completed_stories:
            node["duration_days"] = 0
            node["status"] = "completed"
    return modified


# -- Elapsed time computation ------------------------------------------------

def compute_elapsed_days(
    story_status: dict[str, dict],
    project_start: date | None = None,
) -> float:
    """Compute working days elapsed from project start to now."""
    if project_start:
        start = project_start
    else:
        # Infer from earliest issue creation date
        earliest = None
        for info in story_status.values():
            created = info.get("created_at")
            if created:
                dt = datetime.fromisoformat(created.replace("Z", "+00:00"))
                if earliest is None or dt < earliest:
                    earliest = dt
        if earliest is None:
            return 0.0
        start = earliest.date()

    today = date.today()
    delta = (today - start).days
    return max(0.0, float(delta))


# -- Reconciliation summary -------------------------------------------------

def _team_makespan(analyzer: dict, team_size: int) -> float:
    """Return the makespan for a given team size from the story-level Gantt.

    Uses the story Gantt (same source as the rendered chart) so the stats bar
    and the Gantt always agree.  Falls back to node-level team_estimates only
    when the story Gantt is unavailable.
    """
    # Prefer story-level Gantt — this is what the report renders
    gantt = analyzer.get("team_story_gantt", {}).get(str(team_size), {})
    if gantt:
        return max(s["end"] for s in gantt.values())

    # Fallback to node-level team estimates
    te = analyzer.get("team_estimates", {}).get("makespan_by_team_size", {})
    for key, dur in te.items():
        try:
            devs = int(key.split("_")[0])
            if devs == team_size:
                return dur
        except ValueError:
            continue
    return analyzer["summary"]["project_duration_days"]


def build_reconciliation_summary(
    original_analyzer: dict,
    reconciled_analyzer: dict,
    story_status: dict[str, dict],
    completed_stories: set[str],
    all_story_ids: set[str],
    elapsed_days: float,
    team_size: int,
) -> dict:
    """Build a summary comparing original vs reconciled projections."""
    original_duration = _team_makespan(original_analyzer, team_size)
    reconciled_duration = _team_makespan(reconciled_analyzer, team_size)

    remaining_stories = all_story_ids - completed_stories
    unmapped_stories = all_story_ids - set(story_status.keys())

    # Compute actual completion days for closed stories
    actual_completions = {}
    for sid in completed_stories:
        info = story_status.get(sid, {})
        actual_completions[sid] = info.get("closed_at", "unknown")

    # Find earliest issue creation date (project start date)
    earliest_created = None
    for info in story_status.values():
        created = info.get("created_at")
        if created:
            try:
                dt = datetime.fromisoformat(created.replace("Z", "+00:00")).date()
                if earliest_created is None or dt < earliest_created:
                    earliest_created = dt
            except (ValueError, TypeError):
                pass

    return {
        "reconciliation_date": str(date.today()),
        "project_start_date": str(earliest_created) if earliest_created else "",
        "elapsed_days": elapsed_days,
        "total_stories": len(all_story_ids),
        "completed_stories": len(completed_stories),
        "remaining_stories": len(remaining_stories),
        "unmapped_stories": sorted(unmapped_stories),
        "progress_pct": round(len(completed_stories) / len(all_story_ids) * 100, 1)
            if all_story_ids else 0,
        "original_duration_days": original_duration,
        "remaining_duration_days": reconciled_duration,
        "projected_total_days": round(elapsed_days + reconciled_duration, 2),
        "team_size": team_size,
        "completed_story_ids": sorted(completed_stories),
        "remaining_story_ids": sorted(remaining_stories),
        "actual_completions": actual_completions,
    }


# -- Versioned output filenames ---------------------------------------------

def compute_version(output_dir: Path) -> tuple[int, str]:
    """Compute next version number and timestamp for reconciled reports."""
    existing = list(output_dir.glob("pm-report-reconciled-v*.html"))
    max_version = 0
    for f in existing:
        m = re.search(r'-v(\d+)-', f.name)
        if m:
            max_version = max(max_version, int(m.group(1)))
    version = max_version + 1
    timestamp = datetime.now().strftime("%Y%m%dT%H%M")
    return version, timestamp


# -- Write reconciled analyzer output ---------------------------------------

def write_reconciled_output(
    reconciled_analyzer: dict,
    recon_summary: dict,
    output_path: Path,
):
    """Write the reconciled analyzer output with summary."""
    output = {
        "reconciliation": recon_summary,
        **reconciled_analyzer,
    }
    output_path.write_text(json.dumps(output, indent=2))


# -- Main --------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Reconcile project schedule with actual GitHub issue status"
    )
    parser.add_argument("project_dag", help="Path to project-dag.json")
    parser.add_argument("analyzer_output", help="Path to analyzer-output.json")
    parser.add_argument("output_dir", help="Output directory for reconciled reports")
    parser.add_argument("--repo", default=None, help="GitHub repo (OWNER/REPO)")
    parser.add_argument("--team-size", type=int, default=None,
                        help="Team size for scheduling (default: from project)")
    parser.add_argument("--label", action="append", default=[],
                        help="Filter issues by label (repeatable)")
    parser.add_argument("--project-start", default=None,
                        help="Project start date (YYYY-MM-DD)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show what would be reconciled without generating reports")
    args = parser.parse_args()

    # Load original data
    with open(args.project_dag) as f:
        dag = json.load(f)
    with open(args.analyzer_output) as f:
        original_analyzer = json.load(f)

    if not original_analyzer.get("valid"):
        print("ERROR: original analyzer output is not valid", file=sys.stderr)
        sys.exit(1)

    all_story_ids = {s["id"] for s in dag.get("user_stories", [])}
    team_size = (args.team_size
                 or dag.get("default_team_size")
                 or original_analyzer["summary"].get("max_useful_developers", 2))

    project_start = None
    if args.project_start:
        project_start = date.fromisoformat(args.project_start)

    # Fetch and map issues
    print("Fetching GitHub issues...")
    issues = fetch_issues(args.repo, args.label or None)
    print(f"  Found {len(issues)} issues")

    story_status = map_issues_to_stories(issues, dag)
    completed_stories = {
        sid for sid, info in story_status.items()
        if info["state"] == "CLOSED"
    }
    mapped_count = len(story_status)
    open_count = mapped_count - len(completed_stories)

    print(f"  Mapped {mapped_count}/{len(all_story_ids)} stories to issues")
    print(f"  Completed (closed): {len(completed_stories)}")
    print(f"  Remaining (open):   {open_count}")
    unmapped = all_story_ids - set(story_status.keys())
    if unmapped:
        print(f"  Unmapped stories:   {sorted(unmapped)}")
    print()

    if completed_stories:
        print("Completed stories:")
        for sid in sorted(completed_stories):
            info = story_status[sid]
            closed_at = info.get("closed_at", "?")
            if closed_at and closed_at != "?":
                dt = datetime.fromisoformat(closed_at.replace("Z", "+00:00"))
                closed_at = dt.strftime("%Y-%m-%d")
            print(f"  {sid} (closed {closed_at})")
        print()

    if args.dry_run:
        print("DRY RUN -- no reports generated.")
        return

    # Compute elapsed time
    elapsed_days = compute_elapsed_days(story_status, project_start)
    if elapsed_days > 0:
        print(f"Elapsed days since project start: {elapsed_days}")

    # Reconcile: modify DAG and rerun analysis
    print("Recomputing schedule with completed stories removed...")
    modified_dag = reconcile_dag(dag, completed_stories)
    reconciled_analyzer = analyze_dag(modified_dag)

    if not reconciled_analyzer.get("valid"):
        print(f"ERROR: reconciled analysis failed: {reconciled_analyzer.get('error')}",
              file=sys.stderr)
        sys.exit(1)

    # Build reconciliation summary
    recon_summary = build_reconciliation_summary(
        original_analyzer, reconciled_analyzer, story_status,
        completed_stories, all_story_ids, elapsed_days, team_size,
    )

    print(f"\nReconciliation Summary:")
    print(f"  Progress:            {recon_summary['progress_pct']}% "
          f"({recon_summary['completed_stories']}/{recon_summary['total_stories']} stories)")
    print(f"  Original estimate:   {recon_summary['original_duration_days']}d")
    print(f"  Remaining work:      {recon_summary['remaining_duration_days']}d")
    if elapsed_days > 0:
        print(f"  Elapsed:             {elapsed_days}d")
        print(f"  Projected total:     {recon_summary['projected_total_days']}d")
    print()

    # Generate reports
    output_dir = Path(args.output_dir)
    data_dir = output_dir / "data"
    reports_dir = output_dir / "reports"
    data_dir.mkdir(parents=True, exist_ok=True)
    reports_dir.mkdir(parents=True, exist_ok=True)

    # Compute versioned suffix
    version, timestamp = compute_version(reports_dir)
    suffix = f"-reconciled-v{version}-{timestamp}"
    print(f"Report version: v{version} ({timestamp})")

    # Write reconciled analyzer output (data)
    write_reconciled_output(
        reconciled_analyzer, recon_summary,
        data_dir / f"analyzer-output{suffix}.json",
    )
    print(f"Written: {data_dir}/analyzer-output{suffix}.json")

    # Write modified DAG (data)
    dag_out = data_dir / f"dag{suffix}.json"
    dag_out.write_text(json.dumps(modified_dag, indent=2))

    # Generate reports using report_generator
    # Build processor-compatible output (analyzer + embedded DAG)
    processor_output = {**reconciled_analyzer, "dag": modified_dag}
    tmp_processor = data_dir / "_tmp_processor.json"
    tmp_processor.write_text(json.dumps(processor_output, indent=2))

    ctx = load_inputs(str(tmp_processor))

    # Inject reconciliation context for report generators
    ctx["reconciliation"] = recon_summary
    ctx["completed_stories"] = completed_stories
    ctx["story_status"] = story_status
    ctx["fixed_team_size"] = team_size

    # Generate all three reports
    pm = generate_pm_report(ctx)
    dev = generate_dev_report(ctx)
    plan = generate_delivery_plan(ctx)

    (reports_dir / f"pm-report{suffix}.html").write_text(pm)
    print(f"Written: {reports_dir}/pm-report{suffix}.html")

    (reports_dir / f"dev-report{suffix}.html").write_text(dev)
    print(f"Written: {reports_dir}/dev-report{suffix}.html")

    (reports_dir / f"dev-report{suffix}.md").write_text(plan)
    print(f"Written: {reports_dir}/dev-report{suffix}.md")

    # Clean up temp files
    tmp_processor.unlink(missing_ok=True)

    print(f"\nDone. Reconciled reports (v{version}) generated in {reports_dir}/")


if __name__ == "__main__":
    main()
