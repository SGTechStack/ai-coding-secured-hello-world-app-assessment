#!/usr/bin/env python3
"""
issue-creator.py -- Create GitHub issues from dependency-orchestrator output
=============================================================================
Reads processor-output.json (the single output of dag-processor.py) and creates
one GitHub issue per user story in Gantt scheduling order.

Each issue includes:
  - Acceptance criteria
  - Dev tasks in implementation order
  - Standards references
  - Scheduling metadata (effort, float, critical path)
  - Dependency links (blocks / blocked-by)

Usage:
  python3 issue-creator.py <processor-output.json> [--team-size N] [--repo OWNER/REPO] [--dry-run] [--label LABEL]...

  --team-size N    Team size for Gantt schedule (default: max_useful_developers from processor)
  --repo OWNER/REPO  Target GitHub repo (default: current repo via gh)
  --dry-run        Print issue bodies without creating them
  --label LABEL    Add label(s) to every issue (repeatable)
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import textwrap


APPFW_STANDARD_REFS: dict[str, dict] = {
    "Appfw-Logging-Standards": {
        "label": "Logging",
        "standard": "Appfw-Logging-Standards/Structured_Logging_Application_Standard.md",
        "recipes": "Appfw-Logging-Standards/Recipes/",
        "questions": "Appfw-Logging-Standards/Structured_Logging_Application_Standard_Questions.md",
    },
    "Appfw-User-Standards/User_SSO": {
        "label": "User SSO",
        "standard": "Appfw-User-Standards/User_SSO/SSO_User_Access_Control_Application_Standard.md",
        "recipes": "Appfw-User-Standards/User_SSO/SSO_User_Access_Control_Recipes/",
        "questions": "Appfw-User-Standards/User_SSO/SSO_User_Access_Control_Application_Standard_Questions.md",
    },
    "Appfw-User-Standards/User_Standalone": {
        "label": "User Standalone",
        "standard": "Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Application_Standard.md",
        "recipes": "Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/",
        "questions": "Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Application_Standard_Questions.md",
    },
    "Appfw-User-Standards/Shared_Recipes": {
        "label": "User Shared Recipes",
        "standard": None,
        "recipes": "Appfw-User-Standards/Shared_Recipes/",
        "questions": None,
    },
    "Appfw-Mfa-Standards/MFA_Core": {
        "label": "MFA Core",
        "standard": "Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Application_Standard.md",
        "recipes": "Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Reimplementation_Recipes.md",
        "questions": "Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Application_Standard_Questions.md",
    },
    "Appfw-Mfa-Standards/MFA_MCC": {
        "label": "MFA MCC",
        "standard": "Appfw-Mfa-Standards/MFA_MCC/MCC_MFA_Application_Standard.md",
        "recipes": "Appfw-Mfa-Standards/MFA_MCC/MCC_MFA_Reimplementation_Recipes.md",
        "questions": "Appfw-Mfa-Standards/MFA_MCC/MCC_MFA_Questions.md",
    },
    "Appfw-Mfa-Standards/MFA_Critical_Transaction": {
        "label": "MFA Critical Transaction",
        "standard": "Appfw-Mfa-Standards/MFA_Critical_Transaction/Critical_Transaction_MFA_Standard.md",
        "recipes": "Appfw-Mfa-Standards/MFA_Critical_Transaction/Critical_Transaction_MFA_Reimplementation_Recipes.md",
        "questions": "Appfw-Mfa-Standards/MFA_Critical_Transaction/Critical_Transaction_MFA_Questions.md",
    },
    "Appfw-Mfa-Standards/MFA_Frontend/Standalone": {
        "label": "MFA Frontend Standalone",
        "standard": "Appfw-Mfa-Standards/MFA_Frontend/Standalone/MFA_Frontend_Standalone_Standard.md",
        "recipes": "Appfw-Mfa-Standards/MFA_Frontend/Standalone/MFA_Frontend_Standalone_Reimplementation_Recipes.md",
        "questions": None,
    },
    "Appfw-Mfa-Standards/MFA_Frontend/MCC": {
        "label": "MFA Frontend MCC",
        "standard": "Appfw-Mfa-Standards/MFA_Frontend/MCC/MFA_Frontend_MCC_Standard.md",
        "recipes": "Appfw-Mfa-Standards/MFA_Frontend/MCC/MFA_Frontend_MCC_Reimplementation_Recipes.md",
        "questions": None,
    },
    "Appfw-Mcc-Standards/Appfw-Shared-Auth-Standards": {
        "label": "MCC Shared Auth",
        "standard": "Appfw-Mcc-Standards/Appfw-Shared-Auth-Standards/MCC_Shared_Auth_Standard.md",
        "recipes": "Appfw-Mcc-Standards/Appfw-Shared-Auth-Standards/MCC_Shared_Auth_Recipes.md",
        "questions": None,
    },
    "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core": {
        "label": "MCNS Core",
        "standard": "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core/MCNS_Core_Standard.md",
        "recipes": "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core/MCNS_Core_Recipes.md",
        "questions": "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core/MCNS_Core_Questions.md",
    },
    "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Batch": {
        "label": "MCNS Batch",
        "standard": "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Batch/MCNS_Batch_Standard.md",
        "recipes": "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Batch/MCNS_Batch_Recipes.md",
        "questions": "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Batch/MCNS_Batch_Questions.md",
    },
    "Appfw-Mcc-Standards/Appfw-Mpds-Standards": {
        "label": "MPDS Retrieval",
        "standard": "Appfw-Mcc-Standards/Appfw-Mpds-Standards/MPDS_Retrieval_Standard.md",
        "recipes": "Appfw-Mcc-Standards/Appfw-Mpds-Standards/MPDS_Retrieval_Recipes.md",
        "questions": None,
    },
    "Appfw-Report-Standards/Report_Core": {
        "label": "Report Core",
        "standard": "Appfw-Report-Standards/Report_Core/Report_Core_Standards.md",
        "recipes": "Appfw-Report-Standards/Report_Core/Report_Core_Recipes.md",
        "questions": "Appfw-Report-Standards/Report_Core/Report_Core_Questions.md",
    },
    "Appfw-Report-Standards/Report_Programmatic": {
        "label": "Report Programmatic",
        "standard": "Appfw-Report-Standards/Report_Programmatic/Report_Programmatic_Design_Standard.md",
        "recipes": "Appfw-Report-Standards/Report_Programmatic/Report_Programmatic_Design_Recipes.md",
        "questions": "Appfw-Report-Standards/Report_Programmatic/Report_Programmatic_Design_Questions.md",
    },
    "Appfw-Interface-Standards": {
        "label": "Interface & Batch",
        "standard": "Appfw-Interface-Standards/Interface_And_Batch_Application_Standard.md",
        "recipes": "Appfw-Interface-Standards/Recipes/",
        "questions": "Appfw-Interface-Standards/Interface_And_Batch_Application_Standard_Questions.md",
    },
    "Appfw-File-Standards/mcc": {
        "label": "File Management MCC",
        "standard": "Appfw-File-Standards/mcc/standards/file_management_standards_aws.md",
        "recipes": "Appfw-File-Standards/mcc/recipes/",
        "questions": "Appfw-File-Standards/mcc/questions/",
    },
    "Appfw-File-Standards/standalone": {
        "label": "File Management Standalone",
        "standard": "Appfw-File-Standards/standalone/standards/file_management_standards_standalone.md",
        "recipes": "Appfw-File-Standards/standalone/recipes/",
        "questions": "Appfw-File-Standards/standalone/questions/",
    },
}


def load_json(path: str) -> dict:
    with open(path) as f:
        return json.load(f)


def build_verifiable_map(dag: dict) -> dict[str, str]:
    return {
        s["id"]: s.get("verifiable", "Story testable end-to-end")
        for s in dag.get("user_stories", [])
    }


def build_standards_map(processor: dict) -> dict[str, list[dict]]:
    """Map story_id -> list of {standard} from delivery_waves story data."""
    story_standards = {}
    for wave in processor.get("delivery_waves", []):
        for story in wave.get("stories", []):
            sid = story["id"]
            standards = []
            for std in story.get("appfw_standards", []):
                std = std.strip()
                if std and not std.startswith("RESOLVE:"):
                    standards.append({"standard": std})
            if standards:
                story_standards[sid] = standards
    return story_standards


def get_gantt_order(processor: dict, team_size: int) -> list[str]:
    """Return story IDs in Gantt scheduling order for the given team size."""
    key = str(team_size)
    gantt = processor.get("team_story_gantt", {}).get(key, {})
    return sorted(gantt.keys(), key=lambda sid: (gantt[sid]["start"], gantt[sid]["end"]))


def get_node_float(processor: dict) -> dict[str, float]:
    """Return {node_id: float_days} from node_details."""
    result = {}
    for nd in processor.get("node_details", []):
        result[nd["id"]] = nd.get("float_days", 0)
    return result


def build_story_metadata(processor: dict) -> dict[str, dict]:
    """Build rich metadata per story from processor output."""
    dag = processor.get("dag", {})
    verif_map = build_verifiable_map(dag)
    standards_map = build_standards_map(processor)
    node_float = get_node_float(processor)
    cp_ids = {s["id"] for s in processor.get("story_dag", {}).get("critical_path", [])}

    # Build story detail from delivery_waves
    story_detail = {}
    for wave in processor.get("delivery_waves", []):
        for st in wave["stories"]:
            story_detail[st["id"]] = st

    # Compute story-level float as minimum float across owned nodes
    story_float = {}
    for story in dag.get("user_stories", []):
        sid = story["id"]
        owned = story.get("owned_nodes", [])
        floats = [node_float.get(nid, 0) for nid in owned if nid in node_float]
        story_float[sid] = min(floats) if floats else 0

    result = {}
    for story in dag.get("user_stories", []):
        sid = story["id"]
        detail = story_detail.get(sid, {})

        result[sid] = {
            "id": sid,
            "title": story.get("title", sid),
            "category": story.get("category", "feature"),
            "priority": story.get("priority", "normal"),
            "verifiable": verif_map.get(sid, "Story testable end-to-end"),
            "effort_days": detail.get("effort_days", 0),
            "is_critical": sid in cp_ids,
            "depends_on": detail.get("depends_on", []),
            "unblocks": detail.get("unblocks", []),
            "dev_tasks": detail.get("dev_tasks_ordered", []),
            "standards": standards_map.get(sid, []),
            "float_days": story_float.get(sid, 0),
        }

    return result


def format_dev_tasks(tasks: list[dict]) -> str:
    """Format dev tasks as a numbered markdown list."""
    if not tasks:
        return "_No implementation tasks_"
    lines = []
    for i, t in enumerate(tasks, 1):
        owned = t.get("is_predecessor", False)
        prefix = "(prereq) " if owned else ""
        lines.append(f"{i}. {prefix}**{t['name']}** ({t['duration_days']}d)")
    return "\n".join(lines)


def format_standards(standards: list[dict]) -> str:
    """Format standards references with standard doc, recipes, and questions paths."""
    if not standards:
        return ""
    # Resolve standard keys to full refs
    seen = set()
    resolved = []
    for s in standards:
        raw = s["standard"]
        for part in raw.split(", "):
            part = part.strip()
            if part and part not in seen:
                seen.add(part)
                ref = APPFW_STANDARD_REFS.get(part)
                if ref:
                    resolved.append({"key": part, **ref})
                else:
                    resolved.append({"key": part, "label": part, "standard": None, "recipes": None, "questions": None})
    if not resolved:
        return ""

    lines = ["", "### Standards to Refer", ""]
    lines.append("| # | Sub-Standard | Standard | Recipes | Questions |")
    lines.append("|---|-------------|----------|---------|-----------|")
    has_questions = False
    for i, r in enumerate(resolved, 1):
        std_cell = f"`{r['standard']}`" if r["standard"] else "---"
        rec_cell = f"`{r['recipes']}`" if r["recipes"] else "---"
        qns_cell = f"`{r['questions']}`" if r["questions"] else "---"
        if r["questions"]:
            has_questions = True
        lines.append(f"| {i} | {r['label']} | {std_cell} | {rec_cell} | {qns_cell} |")

    if has_questions:
        lines.append("")
        lines.append("> **Mandatory:** Every question in the Questions document(s) listed above must be presented to the engineer and answered explicitly before implementation begins. Do not pre-answer, skip, or collapse questions based on assumptions from the codebase --- each question must be surfaced for human confirmation regardless of how obvious the answer appears.")

    return "\n".join(lines)


def format_issue_body(meta: dict) -> str:
    """Build the full GitHub issue body markdown."""
    sections = []

    # Header with scheduling badge
    critical_badge = " **[CRITICAL PATH]**" if meta["is_critical"] else ""
    category_badge = f"`{meta['category']}`"

    sections.append(f"{category_badge}{critical_badge}")
    sections.append("")

    # Scheduling metadata table
    float_label = "Critical" if meta["is_critical"] else f"{meta['float_days']}d"

    sections.append("### Schedule")
    sections.append("")
    sections.append("| Metric | Value |")
    sections.append("|--------|-------|")
    sections.append(f"| Effort | **{meta['effort_days']}d** |")
    sections.append(f"| Float | {float_label} |")
    sections.append("")

    # Acceptance criteria
    sections.append("### Acceptance Criteria")
    sections.append("")
    sections.append(meta["verifiable"])
    sections.append("")

    # Dev tasks
    sections.append("### Dev Tasks (Implementation Order)")
    sections.append("")
    sections.append(format_dev_tasks(meta["dev_tasks"]))
    sections.append("")

    # Standards
    std_section = format_standards(meta["standards"])
    if std_section:
        sections.append(std_section)
        sections.append("")

    # Dependencies
    if meta["depends_on"]:
        sections.append("### Blocked By")
        sections.append("")
        sections.append(", ".join(f"`{d}`" for d in meta["depends_on"]))
        sections.append("")

    if meta["unblocks"]:
        sections.append("### Unblocks")
        sections.append("")
        sections.append(", ".join(f"`{u}`" for u in meta["unblocks"]))
        sections.append("")

    return "\n".join(sections)


BUILT_IN_LABELS = {
    "auto-generated": {"description": "Created by automation", "color": "ededed"},
    "high-priority": {"description": "High priority story", "color": "d93f0b"},
    "critical-path": {"description": "On the project critical path", "color": "d73a4a"},
}


def ensure_labels(repo: str | None, extra_labels: list[str]) -> None:
    """Create any missing labels in the target repo before issue creation."""
    all_labels = dict(BUILT_IN_LABELS)
    for lbl in extra_labels:
        if lbl not in all_labels:
            all_labels[lbl] = {"description": "", "color": "0075ca"}

    for name, meta in all_labels.items():
        cmd = ["gh", "label", "create", name,
               "--description", meta["description"],
               "--color", meta["color"], "--force"]
        if repo:
            cmd.extend(["--repo", repo])
        subprocess.run(cmd, capture_output=True, text=True)


def create_issue(title: str, body: str, repo: str | None, labels: list[str],
                  is_critical: bool = False, is_high_priority: bool = False) -> str | None:
    """Create a GitHub issue via gh CLI. Returns the issue URL or None on failure."""
    cmd = ["gh", "issue", "create", "--title", title, "--body", body]
    if repo:
        cmd.extend(["--repo", repo])
    for label in labels:
        cmd.extend(["--label", label])
    cmd.extend(["--label", "auto-generated"])
    if is_critical:
        cmd.extend(["--label", "critical-path"])
    if is_high_priority:
        cmd.extend(["--label", "high-priority"])

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"  ERROR: {result.stderr.strip()}", file=sys.stderr)
        return None
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(
        description="Create GitHub issues from dependency-orchestrator processor output"
    )
    parser.add_argument("processor_json", help="Path to processor-output.json")
    parser.add_argument("--team-size", type=int, default=None,
                        help="Team size for Gantt schedule (default: max_useful_developers from processor)")
    parser.add_argument("--repo", default=None, help="Target GitHub repo (OWNER/REPO)")
    parser.add_argument("--dry-run", action="store_true", help="Print without creating")
    parser.add_argument("--label", action="append", default=[], help="Label(s) to add")
    args = parser.parse_args()

    processor = load_json(args.processor_json)

    if not processor.get("valid"):
        print("ERROR: processor output is not valid", file=sys.stderr)
        sys.exit(1)

    # Default team size: max_useful_developers (knee of the makespan curve)
    team_size = args.team_size or processor.get("summary", {}).get("max_useful_developers", 2)
    available_sizes = [int(k) for k in processor.get("team_story_gantt", {}).keys()]
    if team_size not in available_sizes and available_sizes:
        closest = min(available_sizes, key=lambda x: abs(x - team_size))
        print(f"WARNING: team size {team_size} not in processor data, using {closest}",
              file=sys.stderr)
        team_size = closest

    project_name = processor.get("summary", {}).get("project", "Project")
    story_meta = build_story_metadata(processor)
    gantt_order = get_gantt_order(processor, team_size)

    print(f"Project: {project_name}")
    print(f"Team size: {team_size}")
    print(f"Stories: {len(gantt_order)}")
    print(f"Labels: {args.label or ['(none)']}")
    print(f"Mode: {'DRY RUN' if args.dry_run else 'LIVE'}")
    print("=" * 60)

    if not args.dry_run:
        print("Ensuring labels exist...")
        ensure_labels(args.repo, args.label)

    created = []
    for i, sid in enumerate(gantt_order, 1):
        meta = story_meta.get(sid)
        if not meta:
            print(f"  [{i}/{len(gantt_order)}] SKIP {sid} (no metadata)")
            continue

        title = f"{sid}: {meta['title']}"
        body = format_issue_body(meta)

        critical_tag = " [CRITICAL]" if meta["is_critical"] else ""
        print(f"  [{i}/{len(gantt_order)}] {sid}{critical_tag} ({meta['effort_days']}d)")

        if args.dry_run:
            print(f"    Title: {title}")
            print(f"    Body length: {len(body)} chars")
            if i == 1:
                print("    --- SAMPLE BODY ---")
                print(textwrap.indent(body, "    "))
                print("    --- END SAMPLE ---")
        else:
            url = create_issue(title, body, args.repo, args.label,
                              is_critical=meta["is_critical"],
                              is_high_priority=meta["priority"] == "high")
            if url:
                created.append((sid, url))
                print(f"    -> {url}")
            else:
                print(f"    -> FAILED")

    print("=" * 60)
    if args.dry_run:
        print(f"DRY RUN complete. {len(gantt_order)} issues would be created.")
    else:
        print(f"Created {len(created)}/{len(gantt_order)} issues.")
        if created:
            print("\nIssue URLs:")
            for sid, url in created:
                print(f"  {sid}: {url}")


if __name__ == "__main__":
    main()
