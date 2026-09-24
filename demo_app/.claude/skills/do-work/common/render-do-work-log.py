#!/usr/bin/env python3
"""Render do-work log state to deterministic Markdown and HTML.

Usage:
  python3 <do-work-skill-dir>/common/render-do-work-log.py checklists/do-work/json/12-issue-title.json

The JSON file is the source of truth. This script writes:
  - checklists/do-work/<issue-number>-<slug>.html, always
  - checklists/do-work/md/<issue-number>-<slug>-reviewer.md, when reviewer findings,
    human decisions, or detailed KB evidence exist
"""

from __future__ import annotations

import html
import json
from pathlib import Path
import re
import sys
from typing import Any


SEVERITY_ORDER = {
    "critical": 0,
    "high": 1,
    "medium": 2,
    "low": 3,
    "info": 4,
}

ALLOWED_LOG_FIELDS = {
    "root": {"issue", "detailed_kb_evidence", "implementer", "reviewer"},
    "issue": {"reference", "slug", "goal"},
    "role": {"updates"},
    "implementer_update": {"round", "kb_lanes", "status", "changed_files", "kb_queries", "evidence"},
    "implementer_evidence": {"task", "files", "kb_lanes", "query", "quote", "application"},
    "reviewer_update": {
        "round", "kb_lanes", "status", "changed_files", "kb_queries",
        "findings", "decisions", "comparisons",
    },
    "finding": {
        "evidence_id", "location", "kb_lane", "query", "recommendation",
        "violation", "proposed", "status",
    },
    "decision": {"evidence_id", "area", "kb_finding", "chosen_action", "reuse"},
    "comparison": {"evidence_id", "concern", "kb_lane", "query", "recommendation", "result"},
}


def reject_unknown_fields(value: Any, shape: str, path: str) -> None:
    if not isinstance(value, dict):
        return
    unknown = sorted(set(value) - ALLOWED_LOG_FIELDS[shape])
    if unknown:
        names = ", ".join(unknown)
        raise ValueError(f"Unknown log field(s) at {path}: {names}")


def validate_log_fields(data: dict[str, Any]) -> None:
    reject_unknown_fields(data, "root", "root")
    reject_unknown_fields(data.get("issue"), "issue", "issue")
    for role in ("implementer", "reviewer"):
        section = data.get(role)
        reject_unknown_fields(section, "role", role)
        for update_index, update in enumerate(as_list(section.get("updates") if isinstance(section, dict) else None)):
            update_shape = f"{role}_update"
            update_path = f"{role}.updates[{update_index}]"
            reject_unknown_fields(update, update_shape, update_path)
            if not isinstance(update, dict):
                continue
            if role == "implementer":
                for item_index, item in enumerate(as_list(update.get("evidence"))):
                    reject_unknown_fields(item, "implementer_evidence", f"{update_path}.evidence[{item_index}]")
                continue
            for key, shape in (("findings", "finding"), ("decisions", "decision"), ("comparisons", "comparison")):
                for item_index, item in enumerate(as_list(update.get(key))):
                    reject_unknown_fields(item, shape, f"{update_path}.{key}[{item_index}]")


def as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


def text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return ", ".join(str(item) for item in value)
    return str(value)


def md_cell(value: Any) -> str:
    rendered = text(value).strip()
    rendered = rendered.replace("\r\n", "\n").replace("\r", "\n")
    rendered = rendered.replace("\\", "\\\\")
    rendered = rendered.replace("|", "\\|")
    rendered = re.sub(r"[ \t]*\n[ \t]*\n+[ \t]*", "<br><br>", rendered)
    rendered = re.sub(r"[ \t]*\n[ \t]*", "<br>", rendered)
    return rendered or "-"


def h(value: Any) -> str:
    return html.escape(text(value), quote=True)


def slugify(value: str) -> str:
    slug = re.sub(r"[^a-zA-Z0-9]+", "-", value.strip().lower()).strip("-")
    return slug or "do-work-log"


def issue_info(data: dict[str, Any]) -> dict[str, Any]:
    issue = data.get("issue")
    if isinstance(issue, dict):
        return issue
    return {"reference": text(issue)}


def issue_slug(data: dict[str, Any], input_path: Path) -> str:
    issue = issue_info(data)
    explicit = text(issue.get("slug")).strip()
    if explicit:
        return slugify(explicit)
    reference = text(issue.get("reference")).strip()
    if reference:
        return slugify(reference)
    return slugify(input_path.stem.removesuffix("-do-work-log"))


def issue_number(data: dict[str, Any]) -> str:
    issue = issue_info(data)
    raw = text(issue.get("number") or issue.get("reference")).strip()
    if not raw:
        return ""
    match = re.search(r"(?:#|issues/|issue\s+)?(\d+)", raw, re.IGNORECASE)
    return match.group(1) if match else ""


def filename_slug(data: dict[str, Any], input_path: Path) -> str:
    slug = issue_slug(data, input_path)
    number = issue_number(data)
    if not number:
        return slug
    prefix = f"{int(number):02d}" if number.isdigit() and int(number) < 100 else number
    if re.match(rf"^{re.escape(prefix)}-", slug) or re.match(rf"^{int(number) if number.isdigit() else re.escape(number)}-", slug):
        return slug
    return f"{prefix}-{slug}"


def output_paths(input_path: Path, slug: str) -> tuple[Path, Path]:
    logs_dir = input_path.parent.parent if input_path.parent.name == "json" else input_path.parent
    md_dir = logs_dir / "md"
    md_dir.mkdir(parents=True, exist_ok=True)
    return (
        logs_dir / f"{slug}.html",
        md_dir / f"{slug}-reviewer.md",
    )


def issue_goal(data: dict[str, Any]) -> str:
    issue = issue_info(data)
    goal = text(issue.get("goal")).strip()
    return goal or text(issue.get("reference")).strip() or "Do Work"


def issue_kb_lanes(data: dict[str, Any], section: dict[str, Any] | None = None) -> str:
    if section:
        updates = as_list(section.get("updates"))
        for update in reversed(updates):
            lanes = as_list(update.get("kb_lanes") or update.get("areas"))
            if lanes:
                return ", ".join(text(lane) for lane in lanes)
    issue = issue_info(data)
    lanes = as_list(issue.get("kb_lanes") or issue.get("areas"))
    return ", ".join(text(lane) for lane in lanes) or "unknown"


def update_title(index: int, update: dict[str, Any]) -> str | None:
    explicit = text(update.get("label")).strip()
    if explicit:
        return explicit
    if index == 0:
        return None
    return f"Update {index}"


def table(headers: list[str], rows: list[list[Any]]) -> str:
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    if rows:
        lines.extend("| " + " | ".join(md_cell(cell) for cell in row) + " |" for row in rows)
    else:
        lines.append("| " + " | ".join("-" for _ in headers) + " |")
    return "\n".join(lines) + "\n"


def implementer_rows(update: dict[str, Any]) -> list[list[Any]]:
    rows = []
    for item in as_list(update.get("evidence")):
        if not isinstance(item, dict):
            continue
        application = text(item.get("application") or item.get("how_code_follows_it")).strip()
        files = item.get("files") or item.get("file") or item.get("location") or item.get("locations")
        verify = [text(path) for path in as_list(files) if text(path).strip()]
        if verify:
            verify_lines = "\n".join(f"- {path}" for path in verify)
            application = f"{application}\n\nVerify in:\n{verify_lines}" if application else f"Verify in:\n{verify_lines}"
        row = [
            item.get("task"),
            item.get("kb_lanes") or item.get("kb_lane"),
            item.get("query"),
            item.get("quote") or item.get("quoted_kb_text"),
            application,
        ]
        rows.append(row)
    return rows


def implementer_headers(update: dict[str, Any]) -> list[str]:
    return ["Task", "KBs routed", "KB Query", "Quoted KB Texts", "How Code Follows It"]


def finding_rows(update: dict[str, Any]) -> list[list[Any]]:
    rows = []
    for finding in as_list(update.get("findings")):
        if not isinstance(finding, dict):
            continue
        rows.append(
            [
                finding.get("location") or finding.get("file"),
                finding.get("kb_lane"),
                finding.get("query"),
                finding.get("recommendation") or finding.get("quoted_recommendation"),
                finding.get("violation"),
                finding.get("proposed") or finding.get("proposed_fix_or_decision"),
                finding.get("status"),
            ]
        )
    return rows


def short_markdown_location(value: Any) -> str:
    rendered = text(value).strip()
    return re.split(r"[/\\\\]", rendered)[-1] if rendered else ""


def markdown_finding_row(finding: dict[str, Any]) -> list[Any]:
    violation = text(finding.get("violation")).strip()
    locations = []
    for value in as_list(finding.get("location") or finding.get("file")):
        locations.extend(
            part.strip()
            for part in re.split(r"\s*;\s*", text(value))
            if part.strip()
        )
    if locations:
        rendered_locations = "<br>".join(
            f"<code>{h(short_markdown_location(location))}</code>"
            for location in locations
        )
        verify = (
            "<details><summary><strong>Verify in</strong></summary><br>"
            f"{rendered_locations}</details>"
        )
        violation = f"{violation}\n\n{verify}" if violation else verify
    return [
        violation,
        finding.get("proposed") or finding.get("proposed_fix_or_decision"),
        finding.get("status"),
    ]


def markdown_finding_rows(update: dict[str, Any]) -> list[list[Any]]:
    rows = []
    for finding in as_list(update.get("findings")):
        if not isinstance(finding, dict):
            continue
        rows.append(markdown_finding_row(finding))
    return rows


def markdown_finding_rows_from(findings: list[dict[str, Any]]) -> list[list[Any]]:
    return [markdown_finding_row(finding) for finding in findings]


def decision_rows(update: dict[str, Any]) -> list[list[Any]]:
    rows = []
    for decision in as_list(update.get("decisions")):
        if not isinstance(decision, dict):
            continue
        rows.append(
            [
                decision.get("area"),
                decision.get("kb_finding"),
                decision.get("chosen_action"),
                decision.get("reuse") or decision.get("when_to_reuse"),
            ]
        )
    return rows


def decision_rows_from(decisions: list[dict[str, Any]]) -> list[list[Any]]:
    return [
        [
            decision.get("area"),
            decision.get("kb_finding"),
            decision.get("chosen_action"),
            decision.get("reuse") or decision.get("when_to_reuse"),
        ]
        for decision in decisions
    ]


def comparison_rows(update: dict[str, Any]) -> list[list[Any]]:
    rows = []
    for item in as_list(update.get("comparisons")):
        if not isinstance(item, dict):
            continue
        rows.append(
            [
                item.get("concern") or item.get("file"),
                item.get("kb_lane"),
                item.get("query"),
                item.get("recommendation") or item.get("quoted_recommendation"),
                item.get("result") or item.get("comparison_result"),
            ]
        )
    return rows


def html_log_contents(data: dict[str, Any]) -> str:
    if data.get("detailed_kb_evidence"):
        return "implementation KB queries and guidance, full reviewer code comparisons, findings, and human decisions."
    return "reviewer findings and human decisions."


def int_value(value: Any) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def markdown_round_summary(
    events: list[tuple[str, int, dict[str, Any]]],
    finding_ids: set[str],
) -> str:
    updates = [update for _, _, update in events]
    kb_queries = sum(int_value(update.get("kb_queries")) for update in updates)
    findings = [
        finding
        for role, _, update in events
        if role == "reviewer"
        for finding in as_list(update.get("findings"))
        if isinstance(finding, dict)
    ]
    decisions = [
        decision
        for role, _, update in events
        if role == "reviewer"
        for decision in as_list(update.get("decisions"))
        if isinstance(decision, dict)
    ]
    comparisons = [
        comparison
        for role, _, update in events
        if role == "reviewer"
        for comparison in as_list(update.get("comparisons"))
        if isinstance(comparison, dict)
    ]

    bits = [f"{kb_queries} KB {'query' if kb_queries == 1 else 'queries'}"]
    must_fix = sum(1 for finding in findings if is_must_fix(status_value(finding)))
    open_decisions = sum(1 for finding in findings if is_human_decision(status_value(finding)))
    resolved = sum(1 for finding in findings if is_resolved(status_value(finding)))

    if must_fix:
        bits.append(f"{must_fix} must-fix")
    if open_decisions:
        bits.append(f"{open_decisions} {'decision' if open_decisions == 1 else 'decisions'} needed")
    if findings and resolved == len(findings):
        bits.append("all findings resolved")
    elif findings and not must_fix and not open_decisions:
        bits.append(f"{len(findings)} {'finding' if len(findings) == 1 else 'findings'}")
    elif not findings:
        resolved_comparisons = sum(
            1
            for comparison in comparisons
            if comparison_id(comparison) in finding_ids
            and (
                result_class(comparison.get("result") or comparison.get("comparison_result")) == "ok"
                or text(comparison.get("result") or comparison.get("comparison_result"))
                .lower()
                .startswith("accepted deviation")
            )
        )
        if resolved_comparisons:
            bits.append(
                f"{resolved_comparisons} "
                f"{'finding' if resolved_comparisons == 1 else 'findings'} resolved"
            )
    if decisions:
        bits.append(f"{len(decisions)} {'decision' if len(decisions) == 1 else 'decisions'} recorded")
    return " · ".join(bits)


def render_markdown_rounds(data: dict[str, Any]) -> str:
    round_events = explicit_rounds(data) or inferred_rounds(data)
    finding_ids = {
        comparison_id(finding)
        for finding in all_findings(data)
        if comparison_id(finding)
    }
    blocks = []
    for index, (label, events) in enumerate(round_events):
        implementation_rows = [
            row
            for role, _, update in events
            if role == "implementer"
            for row in implementer_rows(update)
        ]
        reviewer_rows = [
            row
            for role, _, update in events
            if role == "reviewer"
            for row in comparison_rows(update)
        ]
        if not implementation_rows and not reviewer_rows:
            continue

        summary = markdown_round_summary(events, finding_ids)
        lines = [
            "<details>",
            f"<summary><strong>Round {index + 1}: {h(label.lower())}</strong> · {h(summary)}</summary>",
            "",
        ]
        if implementation_rows:
            guidance_label = "Implementation guidance" if index == 0 else "Correction guidance"
            lines.extend(
                [
                    f"### {guidance_label}",
                    "",
                    table(
                        ["Task", "KB", "Query", "Quoted KB text", "How code follows it"],
                        implementation_rows,
                    ).rstrip(),
                    "",
                ]
            )
        if reviewer_rows:
            lines.extend(
                [
                    "### Reviewer comparisons",
                    "",
                    table(
                        ["Concern", "KB", "Query", "Quoted recommendation", "Comparison"],
                        reviewer_rows,
                    ).rstrip(),
                    "",
                ]
            )
        lines.append("</details>")
        blocks.append("\n".join(lines))
    return "\n\n---\n\n".join(blocks)


def render_reviewer_markdown(data: dict[str, Any], html_path: Path) -> str | None:
    section = data.get("reviewer")
    if not isinstance(section, dict) or not as_list(section.get("updates")):
        return None

    current = current_findings(data)
    recorded_decisions = all_decisions(data)
    history = render_markdown_rounds(data) if data.get("detailed_kb_evidence") else ""
    if not current and not recorded_decisions and not history:
        return None

    findings = markdown_finding_rows_from(current)
    decisions = decision_rows_from(recorded_decisions)
    lines: list[str] = []

    if findings:
        lines.extend(["## Findings", ""])
        lines.append(table(["Finding", "Required action", "Status"], findings))

    if decisions:
        lines.extend(["", "## Human decisions", ""])
        lines.append(table(["Area", "Decision", "Chosen action", "Reuse when"], decisions))

    if history:
        lines.extend(["", "## KB evidence and run history", "", history])

    lines.extend(
        [
            "",
            "## Full do-work log",
            "",
            f"Open locally: `{html_path.as_posix()}`",
            "",
            f"Includes {html_log_contents(data)}",
        ]
    )
    return "\n".join(lines).rstrip() + "\n"


def role_updates(data: dict[str, Any], role: str) -> list[dict[str, Any]]:
    section = data.get(role) if isinstance(data.get(role), dict) else {}
    return [update for update in as_list(section.get("updates")) if isinstance(update, dict)]


def update_label(index: int, update: dict[str, Any]) -> str:
    return update_title(index, update) or "Initial"


def all_findings(data: dict[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for update in role_updates(data, "reviewer"):
        findings.extend(item for item in as_list(update.get("findings")) if isinstance(item, dict))
    return findings


def all_decisions(data: dict[str, Any]) -> list[dict[str, Any]]:
    decisions: list[dict[str, Any]] = []
    for update in role_updates(data, "reviewer"):
        decisions.extend(item for item in as_list(update.get("decisions")) if isinstance(item, dict))
    return decisions


def all_comparisons(data: dict[str, Any]) -> list[dict[str, Any]]:
    comparisons: list[dict[str, Any]] = []
    for update in role_updates(data, "reviewer"):
        comparisons.extend(item for item in as_list(update.get("comparisons")) if isinstance(item, dict))
    return comparisons


def status_value(finding: dict[str, Any]) -> str:
    return text(finding.get("status")).strip() or "Open"


def is_resolved(status: str) -> bool:
    lowered = status.lower()
    return "resolved" in lowered or "accepted" in lowered


def is_human_decision(status: str) -> bool:
    return "human decision" in status.lower()


def is_must_fix(status: str) -> bool:
    lowered = status.lower()
    return "must-fix" in lowered or "must fix" in lowered


def status_class(status: str) -> str:
    lowered = status.lower()
    if is_resolved(status) or "completed" in lowered:
        return "ok"
    if is_human_decision(status):
        return "warn"
    if is_must_fix(status):
        return "bad"
    return "neutral"


def result_class(value: Any) -> str:
    lowered = text(value).lower()
    if lowered.startswith("accepted deviation"):
        return "warn"
    if (
        lowered.startswith("compliant")
        or lowered.startswith("pass")
        or lowered.startswith("conform")
        or lowered.startswith("aligned")
    ):
        return "ok"
    if (
        lowered.startswith("deviation")
        or lowered.startswith("fail")
        or lowered.startswith("partial")
        or lowered.startswith("mismatch")
        or lowered.startswith("noncompliant")
        or lowered.startswith("not compliant")
    ):
        return "warn"
    if lowered.startswith("blocked"):
        return "bad"
    return "neutral"


def result_label(value: Any) -> str:
    rendered = text(value).strip()
    if not rendered:
        return "evidence"
    return result_label_and_remainder(rendered)[0]


def result_label_and_remainder(value: Any) -> tuple[str, str]:
    rendered = text(value).strip()
    lowered = rendered.lower()
    aliases = [
        ("Compliant", ("compliant", "pass", "passes", "conform", "conforms", "aligned")),
        ("Accepted deviation", ("accepted deviation",)),
        ("Deviation", ("deviation", "fail", "fails", "partial", "mismatch", "noncompliant", "not compliant")),
        ("Blocked", ("blocked",)),
    ]
    for label, prefixes in aliases:
        for prefix in prefixes:
            match = re.match(rf"^{re.escape(prefix)}\b([\s:,-]*)(.*)$", lowered, re.DOTALL)
            if match:
                remainder = rendered[len(match.group(0)) - len(match.group(2)) :]
                if remainder.startswith((": ", ":")):
                    return label, remainder
                return label, f": {remainder.lstrip(' :,-')}" if remainder.strip() else ""

    first = re.split(r"[\s:,-]+", rendered, maxsplit=1)[0]
    return first.capitalize(), rendered[len(first) :]


def display_result(value: Any) -> str:
    rendered = text(value).strip()
    if not rendered:
        return ""
    label, remainder = result_label_and_remainder(rendered)
    return f"{label}{remainder}"


def display_original_result(value: Any) -> str:
    rendered = text(value).strip()
    if not rendered:
        return ""
    label, remainder = result_label_and_remainder(rendered)
    return f"{label}{remainder}"


def words(value: Any) -> set[str]:
    return {
        word
        for word in re.findall(r"[a-z0-9]+", text(value).lower())
        if len(word) > 2 and word not in {"java", "tsx", "the", "and", "for", "with"}
    }


def normalized_id(value: Any) -> str:
    return text(value).strip().lower()


def comparison_id(item: dict[str, Any]) -> str:
    return text(item.get("evidence_id") or item.get("comparison_id") or item.get("id")).strip()


def linked_comparison(
    finding: dict[str, Any],
    comparisons: list[dict[str, Any]],
) -> dict[str, Any] | None:
    explicit = comparison_id(finding)
    if explicit:
        for item in comparisons:
            if comparison_id(item) == explicit:
                return item

    location_words = words(finding.get("location") or finding.get("file"))
    query_words = words(finding.get("query"))
    lane = text(finding.get("kb_lane")).strip().lower()
    best: tuple[int, dict[str, Any] | None] = (0, None)

    for item in comparisons:
        score = 0
        if lane and text(item.get("kb_lane")).strip().lower() == lane:
            score += 3
        score += min(len(location_words & words(item.get("concern") or item.get("file"))) * 3, 9)
        score += min(len(query_words & words(item.get("query"))) * 2, 6)
        result = text(item.get("result") or item.get("comparison_result")).lower()
        status = status_value(finding).lower()
        if result.startswith("deviation") and not is_resolved(status):
            score += 2
        if score > best[0]:
            best = (score, item)

    return best[1] if best[0] >= 7 else None


def decision_id(item: dict[str, Any]) -> str:
    return text(
        item.get("finding_id")
        or item.get("kb_finding_id")
        or item.get("decision_id")
        or item.get("evidence_id")
        or item.get("id")
    ).strip()


def linked_decision(
    finding: dict[str, Any],
    decisions: list[dict[str, Any]],
) -> dict[str, Any] | None:
    explicit = decision_id(finding)
    if explicit:
        for item in decisions:
            if normalized_id(decision_id(item)) == normalized_id(explicit):
                return item

    finding_words = (
        words(finding.get("violation"))
        | words(finding.get("recommendation") or finding.get("quoted_recommendation"))
    )
    finding_words |= words(finding.get("location") or finding.get("file"))
    query_words = words(finding.get("query"))
    lane = text(finding.get("kb_lane")).strip().lower()
    best: tuple[int, dict[str, Any] | None] = (0, None)

    for item in decisions:
        score = 0
        area = text(item.get("area")).strip().lower()
        if lane and area == lane:
            score += 3
        score += min(len(finding_words & words(item.get("kb_finding"))) * 3, 12)
        score += min(len(query_words & words(item.get("kb_finding"))) * 2, 6)
        if score > best[0]:
            best = (score, item)

    return best[1] if best[0] >= 6 else None


def summary_finding_key(finding: dict[str, Any], decisions: list[dict[str, Any]]) -> str:
    decision = linked_decision(finding, decisions)
    if decision:
        decision_key = (
            decision.get("kb_finding")
            or decision_id(decision)
            or decision.get("title")
            or decision.get("chosen_action")
            or decision.get("action")
        )
        return "decision:" + normalized_id(decision_key)

    explicit = comparison_id(finding) or decision_id(finding)
    if explicit:
        return "id:" + normalized_id(explicit)

    lane = normalized_id(finding.get("kb_lane"))
    query_words = " ".join(sorted(words(finding.get("query"))))
    recommendation_words = " ".join(
        sorted(words(finding.get("recommendation") or finding.get("quoted_recommendation")))
    )
    return f"fallback:{lane}:{query_words}:{recommendation_words}"


def current_findings(data: dict[str, Any]) -> list[dict[str, Any]]:
    decisions = all_decisions(data)
    latest: dict[str, dict[str, Any]] = {}
    order: list[str] = []
    for finding in all_findings(data):
        key = summary_finding_key(finding, decisions)
        if key not in latest:
            order.append(key)
        latest[key] = finding
    return [latest[key] for key in order]


def resolved_summary_keys(data: dict[str, Any]) -> set[str]:
    decisions = all_decisions(data)
    return {
        summary_finding_key(finding, decisions)
        for finding in current_findings(data)
        if is_resolved(status_value(finding))
    }


def summary_counts(data: dict[str, Any]) -> dict[str, int]:
    findings = current_findings(data)
    recorded_decisions = all_decisions(data)
    open_must_fix = 0
    open_decisions = 0
    resolved = 0
    for finding in findings:
        status = status_value(finding)
        if is_resolved(status):
            resolved += 1
            continue
        if is_must_fix(status):
            open_must_fix += 1
        if is_human_decision(status):
            open_decisions += 1

    kb_queries = 0
    for role in ("implementer", "reviewer"):
        for update in role_updates(data, role):
            try:
                kb_queries += int(update.get("kb_queries") or 0)
            except (TypeError, ValueError):
                pass

    return {
        "open_must_fix": open_must_fix,
        "open_decisions": open_decisions,
        "recorded_decisions": len(recorded_decisions),
        "resolved_findings": resolved,
        "total_findings": len(findings),
        "kb_queries": kb_queries,
    }


def html_table(headers: list[str], rows: list[list[Any]], class_name: str = "") -> str:
    head = "".join(f"<th>{h(header)}</th>" for header in headers)
    if not rows:
        body = f'<tr><td colspan="{len(headers)}" class="muted">No rows</td></tr>'
    else:
        body = "\n".join(
            "<tr>" + "".join(f"<td>{h(cell)}</td>" for cell in row) + "</tr>" for row in rows
        )
    cls = f' class="{h(class_name)}"' if class_name else ""
    return f"<table{cls}><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table>"


def pill(label: Any, class_name: str = "neutral") -> str:
    return f'<span class="pill {h(class_name)}">{h(label)}</span>'


def metric(label: str, value: Any, class_name: str = "neutral") -> str:
    return (
        f'<div class="metric {h(class_name)}">'
        f'<div class="metric-value">{h(value)}</div>'
        f'<div class="metric-label">{h(label)}</div>'
        "</div>"
    )


def render_changed_files(update: dict[str, Any]) -> str:
    files = [text(item) for item in as_list(update.get("changed_files")) if text(item).strip()]
    if not files:
        return ""
    items = "".join(f"<li>{h(path)}</li>" for path in files)
    return f"<details><summary>Changed files ({len(files)})</summary><ul class=\"file-list\">{items}</ul></details>"


def render_supporting_comparison(item: dict[str, Any] | None, finding_status: str = "") -> str:
    if not item:
        return ""
    result = item.get("result") or item.get("comparison_result")
    label = result_label(result)
    heading = "Supporting KB comparison"
    comparison_text = display_result(result)
    if (
        is_resolved(finding_status)
        and result_class(result) in {"warn", "bad"}
        and not text(result).lower().startswith("accepted deviation")
    ):
        heading = "Original KB comparison"
        label = "Original Deviation" if label == "Deviation" else f"Original {label}"
        comparison_text = display_original_result(result)
    return f"""<div class="supporting-evidence">
  <div class="supporting-head">
    <strong>{heading}</strong>
    {pill(label, "neutral")}
  </div>
  <dl>
    <dt>Concern</dt><dd>{h(item.get("concern") or item.get("file"))}</dd>
    <dt>KB query</dt><dd>{h(item.get("query"))}</dd>
    <dt>KB evidence</dt><dd>{h(item.get("recommendation") or item.get("quoted_recommendation"))}</dd>
    <dt>Comparison</dt><dd>{h(comparison_text)}</dd>
  </dl>
</div>"""


def render_decision_inset(decision: dict[str, Any] | None) -> str:
    if not decision:
        return ""
    reuse = decision.get("reuse") or decision.get("when_to_reuse")
    rationale = decision.get("rationale")
    reuse_label = "Reuse when" if reuse else "Rationale"
    return f"""<div class="decision decision-inline">
  <div class="finding-head">
    <strong>Human decision</strong>
    {pill("recorded", "neutral")}
  </div>
  <dl>
    <dt>KB finding</dt><dd>{h(decision.get("kb_finding") or decision.get("title"))}</dd>
    <dt>Chosen action</dt><dd>{h(decision.get("chosen_action") or decision.get("action"))}</dd>
    <dt>{h(reuse_label)}</dt><dd>{h(reuse or rationale)}</dd>
  </dl>
</div>"""


def render_findings(
    findings: list[dict[str, Any]],
    comparisons: list[dict[str, Any]] | None = None,
    decisions: list[dict[str, Any]] | None = None,
    superseded_keys: set[str] | None = None,
) -> str:
    if not findings:
        return '<p class="muted empty">No reviewer findings.</p>'
    comparisons = comparisons or []
    decisions = decisions or []
    superseded_keys = superseded_keys or set()
    human_decision_count = sum(1 for item in findings if is_human_decision(status_value(item)))
    cards = []
    human_decision_index = 0
    for finding in findings:
        status = status_value(finding)
        display_status = status
        display_class = status_class(status)
        if (
            not is_resolved(status)
            and summary_finding_key(finding, decisions) in superseded_keys
        ):
            display_status = "Superseded"
            display_class = "neutral"
        evidence = linked_comparison(finding, comparisons)
        decision = linked_decision(finding, decisions)
        if is_human_decision(status):
            if decision is None and len(decisions) == 1:
                decision = decisions[0]
            elif decision is None and human_decision_count == len(decisions):
                decision = decisions[human_decision_index]
            human_decision_index += 1
        action_label = "Resolution"
        if not is_resolved(status):
            action_label = "Next action"
        elif decision:
            action_label = "Decision-guided resolution"
        action_text = (
            finding.get("resolution")
            if is_resolved(status) and finding.get("resolution")
            else finding.get("proposed") or finding.get("proposed_fix_or_decision")
        )
        cards.append(
            f"""<article class="finding {display_class}">
  <div class="finding-head">
    <strong>{h(finding.get("location") or finding.get("file") or "Unknown location")}</strong>
    {pill(display_status, display_class)}
  </div>
  <div class="mini-meta">
    {pill(finding.get("kb_lane") or "kb", "info")}
    <span>{h(finding.get("query"))}</span>
  </div>
  <dl>
    <dt>Violation</dt><dd>{h(finding.get("violation"))}</dd>
    <dt>Recommendation</dt><dd>{h(finding.get("recommendation") or finding.get("quoted_recommendation"))}</dd>
    <dt>{h(action_label)}</dt><dd>{h(action_text)}</dd>
  </dl>
  {render_decision_inset(decision)}
  {render_supporting_comparison(evidence, status)}
</article>"""
        )
    return "\n".join(cards)


def render_decisions(decisions: list[dict[str, Any]]) -> str:
    if not decisions:
        return '<p class="muted empty">No recorded human decisions.</p>'
    cards = []
    for decision in decisions:
        reuse = decision.get("reuse") or decision.get("when_to_reuse")
        rationale = decision.get("rationale")
        reuse_label = "Reuse when" if reuse else "Rationale"
        cards.append(
            f"""<article class="decision">
  <div class="finding-head">
    <strong>{h(decision.get("area") or "Decision")}</strong>
    {pill("recorded", "warn")}
  </div>
  <dl>
    <dt>KB finding</dt><dd>{h(decision.get("kb_finding") or decision.get("title"))}</dd>
    <dt>Chosen action</dt><dd>{h(decision.get("chosen_action") or decision.get("action"))}</dd>
    <dt>{h(reuse_label)}</dt><dd>{h(reuse or rationale)}</dd>
  </dl>
</article>"""
        )
    return "\n".join(cards)


def render_comparisons(update: dict[str, Any]) -> str:
    rows = []
    for item in as_list(update.get("comparisons")):
        if not isinstance(item, dict):
            continue
        result = item.get("result") or item.get("comparison_result")
        rows.append(
            [
                item.get("concern") or item.get("file"),
                item.get("kb_lane"),
                item.get("query"),
                item.get("recommendation") or item.get("quoted_recommendation"),
                display_result(result),
            ]
        )
    return html_table(
        ["File or concern", "KB lane", "KB Query", "Quoted Recommendation", "Comparison result"],
        rows,
        "evidence-table",
    )


def render_implementer_event(index: int, update: dict[str, Any]) -> str:
    rows = implementer_rows(update)
    phase = update_label(index, update)
    status = text(update.get("status")).strip() or "completed"
    kb_lanes = ", ".join(text(lane) for lane in as_list(update.get("kb_lanes") or update.get("areas"))) or "none"
    return f"""<article class="event implementer">
  <div class="event-head">
    <div>
      <span class="phase">{h(phase)}</span>
      <h3>Implementer</h3>
    </div>
    {pill(status, status_class(status))}
  </div>
  <div class="event-meta">KB lanes: {h(kb_lanes)} | {len(rows)} evidence rows | {h(update.get("kb_queries") or 0)} KB queries</div>
  {render_changed_files(update)}
  <details>
    <summary>KB guidance ({len(rows)})</summary>
    {html_table(implementer_headers(update), rows, "evidence-table")}
  </details>
</article>"""


def render_reviewer_event(
    index: int,
    update: dict[str, Any],
    detailed: bool,
    superseded_keys: set[str] | None = None,
) -> str:
    findings = [item for item in as_list(update.get("findings")) if isinstance(item, dict)]
    decisions = [item for item in as_list(update.get("decisions")) if isinstance(item, dict)]
    comparisons = comparison_rows(update)
    status = text(update.get("status")).strip() or "completed"
    kb_lanes = ", ".join(text(lane) for lane in as_list(update.get("kb_lanes") or update.get("areas"))) or "none"
    comparison_block = ""
    if detailed:
        comparison_block = f"""<details>
    <summary>KB comparisons ({len(comparisons)})</summary>
    {render_comparisons(update)}
  </details>"""
    phase = update_label(index, update)
    return f"""<article class="event reviewer">
  <div class="event-head">
    <div>
      <span class="phase">{h(phase)}</span>
      <h3>Reviewer</h3>
    </div>
    {pill(status, status_class(status))}
  </div>
  <div class="event-meta">KB lanes: {h(kb_lanes)} | {len(findings)} findings | {len(decisions)} decisions | {h(update.get("kb_queries") or 0)} KB queries</div>
  <details>
    <summary>Reviewer findings ({len(findings)})</summary>
    {render_findings(findings, [item for item in as_list(update.get("comparisons")) if isinstance(item, dict)], decisions, superseded_keys)}
  </details>
  {render_changed_files(update)}
  {comparison_block}
</article>"""


def update_round(update: dict[str, Any]) -> int | None:
    try:
        value = int(update.get("round") or update.get("current_round"))
    except (TypeError, ValueError):
        return None
    return value if value > 0 else None


def round_label(events: list[tuple[str, int, dict[str, Any]]], round_number: int | None = None) -> str:
    roles = [role for role, _, _ in events]
    if roles == ["reviewer"]:
        return "Review"
    if roles == ["implementer"]:
        return "Implementation" if round_number in (None, 1) else "Correction"
    if roles == ["implementer", "reviewer"]:
        return "Implementation and review" if round_number in (None, 1) else "Correction and review"
    return " -> ".join(role.title() for role in roles)


def explicit_rounds(data: dict[str, Any]) -> list[tuple[str, list[tuple[str, int, dict[str, Any]]]]]:
    grouped: dict[int, list[tuple[str, int, dict[str, Any]]]] = {}
    for role in ("implementer", "reviewer"):
        for index, update in enumerate(role_updates(data, role)):
            round_number = update_round(update)
            if round_number is None:
                continue
            grouped.setdefault(round_number, []).append((role, index, update))

    if not grouped:
        return []

    role_order = {"implementer": 0, "reviewer": 1}
    rounds = []
    for round_number in sorted(grouped):
        events = sorted(grouped[round_number], key=lambda item: (role_order[item[0]], item[1]))
        rounds.append((round_label(events, round_number), events))
    return rounds


def inferred_rounds(data: dict[str, Any]) -> list[tuple[str, list[tuple[str, int, dict[str, Any]]]]]:
    implementer = role_updates(data, "implementer")
    reviewer = role_updates(data, "reviewer")
    if not implementer and not reviewer:
        return []

    round_events: list[tuple[str, list[tuple[str, int, dict[str, Any]]]]] = []
    if implementer and len(reviewer) == len(implementer) + 1:
        round_events.append(("Review", [("reviewer", 0, reviewer[0])]))
        for index, update in enumerate(implementer):
            events = [("implementer", index, update)]
            reviewer_index = index + 1
            if reviewer_index < len(reviewer):
                events.append(("reviewer", reviewer_index, reviewer[reviewer_index]))
            round_events.append(("Correction and review", events))
        return round_events

    rounds = max(len(implementer), len(reviewer))
    for index in range(rounds):
        events = []
        if index < len(implementer):
            events.append(("implementer", index, implementer[index]))
        if index < len(reviewer):
            events.append(("reviewer", index, reviewer[index]))
        label = "Implementation and review" if len(events) > 1 else events[0][0].title()
        round_events.append((label, events))
    return round_events


def render_rounds(data: dict[str, Any], detailed: bool) -> str:
    round_events = explicit_rounds(data) or inferred_rounds(data)
    if not round_events:
        return '<p class="muted empty">No log updates.</p>'

    superseded_keys = resolved_summary_keys(data)
    blocks = []
    for index, (label, events) in enumerate(round_events):
        finding_count = sum(
            len([item for item in as_list(update.get("findings")) if isinstance(item, dict)])
            for role, _, update in events
            if role == "reviewer"
        )
        finding_label = "finding" if finding_count == 1 else "findings"
        bits = [
            '<details class="round">',
            f'<summary class="round-head"><h2>Round {index + 1}</h2><span>{h(finding_count)} {finding_label}</span></summary>',
        ]
        for role, event_index, update in events:
            if role == "implementer":
                bits.append(render_implementer_event(event_index, update))
            else:
                bits.append(render_reviewer_event(event_index, update, detailed, superseded_keys))
        bits.append("</details>")
        blocks.append("\n".join(bits))
    return "\n".join(blocks)


def render_html(data: dict[str, Any]) -> str:
    title = f"{issue_goal(data)} - do-work log"
    detailed = bool(data.get("detailed_kb_evidence"))
    issue = issue_info(data)
    counts = summary_counts(data)
    decisions = all_decisions(data)
    findings = current_findings(data)
    comparisons = all_comparisons(data)
    open_ok = "ok" if counts["open_must_fix"] == 0 else "bad"
    decision_status = "ok" if counts["open_decisions"] == 0 else "warn"

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{h(title)}</title>
  <style>
    * {{ box-sizing: border-box; }}
    body {{ font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; color: #172033; background: #f6f8fb; line-height: 1.5; }}
    main {{ max-width: 1180px; margin: 0 auto; padding: 28px; }}
    header, section, .round, .event {{ background: #fbfdff; border: 1px solid #dbe3ef; border-radius: 8px; }}
    header {{ padding: 22px; margin-bottom: 16px; }}
    section {{ padding: 18px; margin-bottom: 16px; }}
    h1 {{ margin: 0 0 8px; font-size: 24px; line-height: 1.25; max-width: 860px; }}
    h2 {{ font-size: 17px; margin: 0; }}
    h3 {{ font-size: 15px; margin: 2px 0 0; }}
    h4 {{ font-size: 13px; margin: 18px 0 8px; color: #475569; text-transform: uppercase; letter-spacing: 0.04em; }}
    .issue-ref {{ color: #64748b; font-size: 13px; }}
    .meta {{ display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; }}
    .metrics {{ display: grid; grid-template-columns: repeat(5, minmax(120px, 1fr)); gap: 10px; margin-top: 16px; }}
    .metric {{ padding: 12px; border-radius: 8px; background: #eef4fb; border: 1px solid #dbe3ef; }}
    .metric-value {{ font-size: 22px; font-weight: 750; line-height: 1; }}
    .metric-label {{ color: #64748b; font-size: 11px; text-transform: uppercase; letter-spacing: 0.04em; margin-top: 6px; }}
    .pill {{ display: inline-flex; align-items: center; width: fit-content; max-width: 100%; padding: 3px 9px; border-radius: 999px; background: #e2e8f0; color: #334155; font-size: 12px; font-weight: 700; overflow-wrap: anywhere; }}
    .ok {{ background: #dcfce7; color: #166534; border-color: #bbf7d0; }}
    .warn {{ background: #fef3c7; color: #92400e; border-color: #fde68a; }}
    .bad {{ background: #fee2e2; color: #991b1b; border-color: #fecaca; }}
    .info {{ background: #dbeafe; color: #1e40af; border-color: #bfdbfe; }}
    .neutral {{ background: #e2e8f0; color: #334155; border-color: #cbd5e1; }}
    .round {{ padding: 0; overflow: hidden; }}
    .round[open] {{ padding-bottom: 6px; }}
    .round-head {{ list-style: none; display: grid; grid-template-columns: 18px minmax(0, 1fr) max-content; gap: 10px; align-items: center; background: #172033; color: #f8fafc; margin: 0; padding: 12px 16px; border-radius: 0; cursor: pointer; }}
    .round:not([open]) .round-head {{ border-radius: 8px; }}
    .round-head::-webkit-details-marker {{ display: none; }}
    .round-head::before {{ content: "›"; color: #cbd5e1; font-size: 18px; line-height: 1; transform-origin: center; transition: transform 160ms ease-out; }}
    .round[open] .round-head::before {{ transform: rotate(90deg); }}
    .round-head h2 {{ min-width: 0; }}
    .round-head span {{ color: #cbd5e1; font-size: 12px; justify-self: end; white-space: nowrap; }}
    .round > .event {{ margin: 12px 16px 16px; }}
    .section-title {{ display: flex; justify-content: space-between; gap: 16px; align-items: flex-end; margin-bottom: 12px; }}
    .section-title p {{ margin: 4px 0 0; color: #64748b; font-size: 13px; }}
    .current-grid {{ display: grid; grid-template-columns: minmax(0, 1fr); gap: 14px; }}
    .summary-panel {{ background: #ffffff; border: 1px solid #dbe3ef; border-radius: 8px; overflow: hidden; }}
    .summary-panel h3 {{ margin: 0; padding: 10px 14px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #172033; color: #f8fafc; }}
    .summary-panel > .finding, .summary-panel > .decision, .summary-panel > .empty {{ margin: 12px 14px 14px; }}
    .summary-section, .history {{ background: transparent; border: 0; padding: 0; }}
    .history {{ margin-bottom: 0; }}
    .round {{ padding: 0; margin-bottom: 16px; overflow: hidden; }}
    .event {{ padding: 16px; margin-top: 12px; }}
    .event-head, .finding-head {{ display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; }}
    .event-head strong, .finding-head strong {{ min-width: 0; overflow-wrap: anywhere; }}
    .phase {{ color: #64748b; font-size: 11px; font-weight: 750; text-transform: uppercase; letter-spacing: 0.06em; }}
    .event-meta, .mini-meta {{ color: #64748b; font-size: 12px; margin: 8px 0 10px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }}
    .finding, .decision {{ border: 1px solid #dbe3ef; border-radius: 8px; padding: 12px; margin-top: 10px; background: #ffffff; }}
    .decision-inline {{ margin-top: 12px; background: #f8fafc; border-color: #cbd5e1; color: #334155; }}
    .finding.ok, .decision.ok {{ background: #f0fdf4; }}
    .finding.warn, .decision.warn {{ background: #fffbeb; }}
    .finding.bad, .decision.bad {{ background: #fef2f2; }}
    .supporting-evidence {{ margin-top: 12px; padding: 10px 12px; border-radius: 8px; border: 1px solid #dbe3ef; background: #f8fafc; color: #334155; }}
    .supporting-head {{ display: flex; justify-content: space-between; gap: 12px; align-items: center; }}
    dl {{ display: grid; grid-template-columns: 128px 1fr; gap: 6px 12px; margin: 10px 0 0; font-size: 13px; }}
    dt {{ color: #64748b; font-weight: 700; }}
    dd {{ margin: 0; min-width: 0; overflow-wrap: anywhere; }}
    details {{ margin-top: 12px; }}
    summary {{ cursor: pointer; color: #334155; font-weight: 700; font-size: 13px; }}
    .file-list {{ columns: 2; margin: 8px 0 0 20px; color: #475569; font-size: 12px; }}
    .file-list li {{ overflow-wrap: anywhere; }}
    table {{ width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 12px; background: #fff; }}
    th, td {{ border-bottom: 1px solid #e5e7eb; padding: 8px; vertical-align: top; text-align: left; }}
    th {{ background: #eef4fb; color: #475569; font-size: 10px; text-transform: uppercase; letter-spacing: 0.04em; }}
    td {{ white-space: pre-wrap; overflow-wrap: anywhere; }}
    .evidence-table {{ display: block; overflow-x: auto; }}
    .muted {{ color: #94a3b8; }}
    .empty {{ text-align: center; padding: 16px; border: 1px dashed #cbd5e1; border-radius: 8px; background: #f8fafc; }}
    .top-grid {{ display: grid; grid-template-columns: minmax(0, 1fr); gap: 16px; }}
    @media (max-width: 760px) {{
      main {{ padding: 16px; }}
      .metrics {{ grid-template-columns: repeat(2, minmax(0, 1fr)); }}
      .event-head, .finding-head {{ flex-direction: column; align-items: flex-start; }}
      .round-head {{ grid-template-columns: 18px minmax(0, 1fr); }}
      .round-head span {{ grid-column: 2; justify-self: start; }}
      .current-grid {{ grid-template-columns: 1fr; }}
      dl {{ grid-template-columns: 1fr; }}
      .file-list {{ columns: 1; }}
    }}
  </style>
</head>
<body>
<main>
  <header>
    <h1>{h(title)}</h1>
    <div class="issue-ref">{h(issue.get("reference"))} | KB lanes: {h(issue_kb_lanes(data))}</div>
    <div class="meta">
      {pill(f"Detailed KB evidence: {'on' if detailed else 'off'}", "info" if detailed else "neutral")}
    </div>
    <div class="metrics">
      {metric("Open must-fix", counts["open_must_fix"], open_ok)}
      {metric("Open decisions", counts["open_decisions"], decision_status)}
      {metric("Recorded decisions", counts["recorded_decisions"], "warn" if counts["recorded_decisions"] else "neutral")}
      {metric("Resolved findings", counts["resolved_findings"], "ok" if counts["resolved_findings"] else "neutral")}
      {metric("KB queries", counts["kb_queries"], "info")}
    </div>
  </header>
  <section class="summary-section">
    <div class="section-title">
      <div>
        <h2>Review Summary</h2>
        <p>Important findings with recorded decisions inline</p>
      </div>
    </div>
    <div class="current-grid">
      <div class="summary-panel">
      <h3>Reviewer findings <span class="pill info">{counts["total_findings"]} total</span></h3>
      {render_findings(findings, comparisons, decisions)}
      </div>
    </div>
  </section>
  <section class="history">
    <div class="section-title">
      <div>
        <h2>Run History</h2>
        <p>Chronological implementer and reviewer passes.</p>
      </div>
    </div>
  {render_rounds(data, detailed)}
  </section>
</main>
</body>
</html>
"""


def write_if_present(path: Path, content: str | None) -> list[Path]:
    if content is None:
        return []
    path.write_text(content, encoding="utf-8")
    return [path]


def main() -> None:
    if len(sys.argv) != 2:
        print("Usage: python3 render-do-work-log.py checklists/do-work/json/<issue-number>-<issue-slug>.json", file=sys.stderr)
        raise SystemExit(2)

    input_path = Path(sys.argv[1])
    data = json.loads(input_path.read_text(encoding="utf-8"))
    try:
        validate_log_fields(data)
    except ValueError as error:
        print(f"Invalid do-work log: {error}", file=sys.stderr)
        raise SystemExit(2)
    slug = filename_slug(data, input_path)
    html_path, reviewer_path = output_paths(input_path, slug)

    written: list[Path] = []
    written += write_if_present(reviewer_path, render_reviewer_markdown(data, html_path))

    html_path.write_text(render_html(data), encoding="utf-8")
    written.append(html_path)

    for path in written:
        print(path)


if __name__ == "__main__":
    main()
