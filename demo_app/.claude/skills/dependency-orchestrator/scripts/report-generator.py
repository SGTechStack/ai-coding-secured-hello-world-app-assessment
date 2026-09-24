#!/usr/bin/env python3
"""
report-generator.py — Deterministic report generator for dependency DAG analysis
================================================================================

Takes the processor JSON output (from dag-processor.py) and produces
role-specific report files:

  pm-report.html   — PM-facing: stats, team table, critical path, risk
  dev-report.html  — Dev-facing: story delivery schedule as styled HTML cards
  dev-report.md    — Dev-facing: full schedule in markdown

Usage:
  python3 report-generator.py <processor-output.json> <output-dir> --role <pm|dev> [--team-size <N>]

  --role pm    → generates only pm-report.html
  --role dev   → generates only dev-report.html + dev-report.md (requires --team-size)
"""

from __future__ import annotations

import json
import html
import re
import sys
import datetime
from pathlib import Path


# ── Appfw standards collection ────────────────────────────────────────────────
# Standards are collected from dev_tasks_ordered (which carries appfw_standard
# per node from the processor). Walking tasks in topological order gives us
# build order for free. configs/standards.md is the single source of truth —
# no hardcoded mapping needed here.


def _collect_story_standards(story: dict, dev_tasks: list) -> list[str]:
    """Collect deduplicated union of appfw_standard values from dev_tasks_ordered, preserving topological order."""
    seen = set()
    standards = []
    for task in dev_tasks:
        val = task.get("appfw_standard")
        if not val:
            continue
        keys = val if isinstance(val, list) else [val]
        for key in keys:
            key = key.strip()
            if key in seen:
                continue
            seen.add(key)
            standards.append(key)
    return standards


# ── Data loading & derived computations ──────────────────────────────────────

def load_inputs(processor_path: str) -> dict:
    """Load processor output and compute all derived data needed for reports."""
    with open(processor_path) as f:
        analyzer = json.load(f)

    dag = analyzer.get("dag", {})

    if not analyzer.get("valid"):
        print(f"ERROR: Analyzer output is invalid: {analyzer.get('error')}", file=sys.stderr)
        sys.exit(1)

    summary = analyzer["summary"]
    waves = analyzer["delivery_waves"]
    cp = analyzer["story_dag"]["critical_path"]
    cp_ids = {s["id"] for s in cp}
    story_edges = analyzer["story_dag"]["edges"]
    te = analyzer["team_estimates"]["makespan_by_team_size"]
    node_details = {n["id"]: n for n in analyzer["node_details"]}

    # ── Team size table ──────────────────────────────────────────────────
    # Parse all team sizes first; the knee is recomputed after gantt overrides.
    team_sizes = []
    prev_dur = None
    for key in te:
        num = key.split("_")[0]
        try:
            devs = int(num)
        except ValueError:
            continue
        dur = te[key]
        saving = round(prev_dur - dur, 2) if prev_dur is not None else 0
        saving_str = f"{saving}d" if prev_dur is not None else "—"
        team_sizes.append({"devs": devs, "dur": dur, "saving": saving, "saving_str": saving_str})
        prev_dur = dur

    # ── Feature groups from DAG ─────────────────────────────────────────
    feature_groups = dag.get("feature_groups", [])
    # Build story→group mapping from feature_groups' story_ids (authoritative),
    # falling back to story-level "group" field
    story_group_map = {}
    # First pass: use story_ids from feature groups if available
    for g in feature_groups:
        for sid in g.get("story_ids", []):
            story_group_map[sid] = g["id"]
    # Second pass: fill in any stories not covered by feature groups
    for s in dag.get("user_stories", []):
        if s["id"] not in story_group_map:
            story_group_map[s["id"]] = s.get("group", "ungrouped")

    # ── Verifiable descriptions from DAG user_stories ────────────────────
    verifiable_map = {}
    for s in dag.get("user_stories", []):
        verifiable_map[s["id"]] = s.get("verifiable", "Story testable end-to-end")

    # ── Key nodes: owned nodes depended on by other stories ──────────────
    node_to_owner = {}
    for story in dag.get("user_stories", []):
        for nid in story.get("owned_nodes", []):
            node_to_owner[nid] = story["id"]

    cross_story_deps = {}  # node_id -> set of story_ids that depend on it
    for e in dag.get("edges", []):
        from_owner = node_to_owner.get(e["from"])
        to_owner = node_to_owner.get(e["to"])
        if from_owner and to_owner and from_owner != to_owner:
            cross_story_deps.setdefault(e["from"], set()).add(to_owner)

    key_nodes_by_story = {}
    for story in dag.get("user_stories", []):
        sid = story["id"]
        kn = []
        for nid in story.get("owned_nodes", []):
            if nid in cross_story_deps:
                name = node_details[nid]["name"] if nid in node_details else nid
                kn.append(name)
        if kn:
            key_nodes_by_story[sid] = kn

    # ── Risk & bottleneck metrics ────────────────────────────────────────
    cp_effort = sum(s["effort_days"] for s in cp)
    total_effort = summary["sequential_duration_days"]
    risk_pct = round(cp_effort / total_effort * 100, 1) if total_effort > 0 else 0

    # Story with longest relative wait (testable_day / effort_days),
    # scoped per-release so R3 stories aren't penalised for R1+R2 time.
    release_story_map = analyzer.get("release_story_map", {})
    story_to_release = {}
    for rel, sids in release_story_map.items():
        for sid in sids:
            story_to_release[sid] = rel

    # Compute per-release earliest testable_day (= release offset)
    release_min_testable = {}
    for w in waves:
        for s in w["stories"]:
            rel = story_to_release.get(s["id"])
            if rel is not None:
                cur = release_min_testable.get(rel)
                td = s["testable_day"] - s["effort_days"]
                if cur is None or td < cur:
                    release_min_testable[rel] = td

    max_wait = None
    max_wait_ratio = 0
    for w in waves:
        for s in w["stories"]:
            if s["effort_days"] > 0:
                rel = story_to_release.get(s["id"])
                offset = release_min_testable.get(rel, 0) if rel else 0
                ratio = (s["testable_day"] - offset) / s["effort_days"]
                if ratio > max_wait_ratio:
                    max_wait_ratio = ratio
                    max_wait = s

    # Story that unblocks the most other stories
    max_unblocks = None
    max_unblocks_count = 0
    for w in waves:
        for s in w["stories"]:
            if len(s["unblocks"]) > max_unblocks_count:
                max_unblocks_count = len(s["unblocks"])
                max_unblocks = s

    # ── Per-team-size story testable & gantt data (from analyzer) ───
    team_story_testable = analyzer.get("team_story_testable", {})
    team_story_gantt = analyzer.get("team_story_gantt", {})

    # Override team_sizes durations with actual max story end time from gantt
    # so that Est. Duration, team table, burndown, and gantt are all consistent.
    for t in team_sizes:
        gantt_key = str(t["devs"])
        if gantt_key in team_story_gantt:
            ends = [s["end"] for s in team_story_gantt[gantt_key].values()]
            if ends:
                t["dur"] = round(max(ends), 2)
    # Recompute savings after overriding durations
    prev_dur = None
    for t in team_sizes:
        if prev_dur is not None:
            saving = round(prev_dur - t["dur"], 2)
            t["saving"] = saving
            t["saving_str"] = f"{saving}d"
        else:
            t["saving"] = 0
            t["saving_str"] = "—"
        prev_dur = t["dur"]

    # Recompute knee after gantt overrides — last team size with saving >= 1d
    recommended = team_sizes[0]["devs"] if team_sizes else 1
    for i in range(1, len(team_sizes)):
        if team_sizes[i]["saving"] >= 1.0:
            recommended = team_sizes[i]["devs"]
        else:
            break
    # Trim table to only show up to the knee
    team_sizes = [t for t in team_sizes if t["devs"] <= recommended]

    # Width-constrained waves (parallel stories > recommended)
    wide_waves = []
    for w in waves:
        if len(w["stories"]) > recommended:
            wide_waves.append(w["wave"])

    # ── Dependency-based waves data (team-size independent) ──────────
    # Lightweight wave data for JS: [{wave, stories: [{id, title, effort_days, category, is_critical}]}]
    _story_lookup = {}
    for w in waves:
        for s in w["stories"]:
            _story_lookup[s["id"]] = s
    waves_data = []
    for w in waves:
        waves_data.append({
            "wave": w["wave"],
            "stories": [{
                "id": s["id"],
                "title": s["title"],
                "effort_days": s["effort_days"],
                "category": s.get("category", "feature"),
                "is_critical": s.get("is_critical", False),
            } for s in w["stories"]],
        })

    # ── Story readiness timeline (sorted by testable_day) ─────────
    # Build a base list of stories with metadata; testable_day varies by team size
    story_meta = []
    for w in waves:
        for s in w["stories"]:
            story_meta.append({
                "id": s["id"],
                "title": s["title"],
                "category": s["category"],
                "effort_days": s["effort_days"],
                "group": story_group_map.get(s["id"], "platform_setup"),
            })

    # Default readiness timeline uses the CPM (infinite parallelism) testable days
    readiness_timeline = []
    for w in waves:
        for s in w["stories"]:
            readiness_timeline.append({
                "id": s["id"],
                "title": s["title"],
                "category": s["category"],
                "testable_day": s["testable_day"],
                "effort_days": s["effort_days"],
            })
    readiness_timeline.sort(key=lambda x: (x["testable_day"], x["id"]))

    # ── Testing burndown (cumulative stories testable by day) ─────
    burndown = []
    cumulative = 0
    total_stories = summary["total_stories_with_infra"]
    # Group by testable_day
    day_groups: dict[float, int] = {}
    for entry in readiness_timeline:
        day = entry["testable_day"]
        day_groups[day] = day_groups.get(day, 0) + 1
    for day in sorted(day_groups):
        cumulative += day_groups[day]
        burndown.append({
            "day": day,
            "new_stories": day_groups[day],
            "cumulative": cumulative,
            "pct": round(cumulative / total_stories * 100) if total_stories > 0 else 0,
        })

    # ── Effort distribution by category (from node details) ───────
    # Split each node's duration into base implementation + testing overhead
    testing_overhead_frac = summary.get("testing_overhead_pct", 30) / 100
    testing_multiplier = 1 + testing_overhead_frac
    effort_by_category: dict[str, float] = {}
    total_testing_effort = 0.0
    for n in node_details.values():
        cat = n.get("category", "other")
        dur_with_testing = n["duration_days"]
        base_dur = round(dur_with_testing / testing_multiplier, 2)
        testing_dur = round(dur_with_testing - base_dur, 2)
        effort_by_category[cat] = effort_by_category.get(cat, 0) + base_dur
        total_testing_effort += testing_dur
    if total_testing_effort > 0:
        effort_by_category["testing"] = round(total_testing_effort, 2)
    _CATEGORY_LABELS = {"testing": "unit & integration testing"}
    effort_dist = []
    for cat in sorted(effort_by_category, key=lambda c: -effort_by_category[c]):
        dur = effort_by_category[cat]
        pct = round(dur / total_effort * 100, 1) if total_effort > 0 else 0
        label = _CATEGORY_LABELS.get(cat, cat)
        effort_dist.append({"category": label, "effort": dur, "pct": pct})

    # ── Per-story appfw standards reference (topological order from DAG) ──
    story_standards: dict[str, list[str]] = {}
    for w in waves:
        for st in w["stories"]:
            refs = _collect_story_standards(st, st.get("dev_tasks_ordered", []))
            if refs:
                story_standards[st["id"]] = refs

    return {
        "summary": summary,
        "waves": waves,
        "cp": cp,
        "cp_ids": cp_ids,
        "team_sizes": team_sizes,
        "recommended": recommended,
        "node_details": node_details,
        "verifiable_map": verifiable_map,
        "key_nodes_by_story": key_nodes_by_story,
        "risk_pct": risk_pct,
        "cp_effort": cp_effort,
        "total_effort": total_effort,
        "max_wait": max_wait,
        "max_unblocks": max_unblocks,
        "max_unblocks_count": max_unblocks_count,
        "wide_waves": wide_waves,
        "dag_edges": dag.get("edges", []),
        "readiness_timeline": readiness_timeline,
        "burndown": burndown,
        "effort_dist": effort_dist,
        "project_tooling": summary.get("project_tooling", []),
        "team_story_testable": team_story_testable,
        "team_story_gantt": team_story_gantt,
        "story_meta": story_meta,
        "story_standards": story_standards,
        "waves_data": waves_data,
        "feature_groups": feature_groups,
        "story_group_map": story_group_map,
        "story_edges": story_edges,
        "release_story_map": analyzer.get("release_story_map", {}),
    }


# ── Shared helpers for both reports ───────────────────────────────────────────

def _build_team_makespan_json(ctx: dict) -> str:
    """Build {devs: duration} lookup as JSON string."""
    return json.dumps({t["devs"]: t["dur"] for t in ctx["team_sizes"]})


def _build_waves_data_json(ctx: dict) -> str:
    """Build dependency-based waves data as JSON: [{wave, stories}]."""
    return json.dumps(ctx["waves_data"])


def _build_feature_groups_json(ctx: dict) -> str:
    """Build feature groups data for JS Gantt: [{id, name, storyIds}]."""
    groups = ctx["feature_groups"]
    story_group_map = ctx["story_group_map"]
    # Build group→story_ids mapping
    group_stories: dict[str, list[str]] = {g["id"]: [] for g in groups}
    for sid, gid in story_group_map.items():
        group_stories.setdefault(gid, []).append(sid)
    result = []
    known_ids = set()
    for g in groups:
        known_ids.add(g["id"])
        result.append({
            "id": g["id"],
            "name": g.get("name") or g.get("title", g["id"]),
            "storyIds": group_stories.get(g["id"], []),
        })
    # Add any groups that exist only in story_group_map (e.g. "ungrouped")
    for gid, sids in group_stories.items():
        if gid not in known_ids and sids:
            result.append({"id": gid, "name": gid.replace("_", " ").title(), "storyIds": sids})
    return json.dumps(result)


_TOOLING_GROUP_STYLE = {
    "frontend": ("#eff6ff", "#1e40af", "#bfdbfe"),
    "backend":  ("#fef3c7", "#92400e", "#fde68a"),
    "testing":  ("#f0fdf4", "#166534", "#bbf7d0"),
    "infra":    ("#f8fafc", "#475569", "#e2e8f0"),
}

_TOOLING_GROUP_LABEL = {
    "frontend": "Frontend",
    "backend": "Backend",
    "testing": "Unit & Integration Testing",
    "infra": "Infrastructure",
}


def _tech_stack_html(ctx: dict) -> str:
    """Render possible toolings panel grouped by category."""
    tooling = ctx.get("project_tooling", {})
    if not tooling:
        return ""
    sections = ""
    for group, tools in tooling.items():
        if not tools:
            continue
        bg, fg, border = _TOOLING_GROUP_STYLE.get(group, ("#f8fafc", "#475569", "#e2e8f0"))
        label = _TOOLING_GROUP_LABEL.get(group, group.capitalize())
        tags = " ".join(
            f'<span style="display:inline-block;background:{bg};color:{fg};'
            f'padding:2px 8px;border-radius:4px;font-size:11px;font-weight:500;'
            f'border:1px solid {border}">{html.escape(t)}</span>'
            for t in tools
        )
        sections += (
            f'<div style="margin-bottom:8px">'
            f'<div style="font-size:11px;font-weight:600;color:#64748b;'
            f'text-transform:uppercase;letter-spacing:0.3px;margin-bottom:4px">{label}</div>'
            f'<div style="display:flex;flex-wrap:wrap;gap:4px">{tags}</div></div>'
        )
    return (
        f'<div style="flex:1;min-width:200px">'
        f'<p class="sub">Possible toolings (derived from dev tasks).</p>'
        f'<div style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;'
        f'padding:16px;margin-top:4px">{sections}</div></div>'
    )


def _render_acceptance_criteria(verif: str) -> str:
    """Render acceptance criteria as bulleted HTML list if multi-line, plain text otherwise."""
    lines = [l.strip() for l in verif.split("\n") if l.strip()]
    if len(lines) <= 1:
        return html.escape(verif)
    items = "".join(f"<li>{html.escape(l)}</li>" for l in lines)
    return f'<ul style="margin:4px 0 0;padding-left:18px">{items}</ul>'


def _render_assumptions_dropdown(da: str, css_prefix: str = "") -> str:
    """Render duration assumptions as a collapsible dropdown with bullet points."""
    if not da:
        return ""
    # Split on commas, semicolons, or newlines to get individual points
    points = [p.strip() for p in re.split(r'[,;\n]+', da) if p.strip()]
    if not points:
        return ""
    bullets = "".join(f"<li>{html.escape(p)}</li>" for p in points)
    sd = f"{css_prefix}sd" if css_prefix else "sd"
    return (
        f'<details class="{sd}-expand" style="font-size:12px;color:#64748b;margin:2px 0">'
        f'<summary style="cursor:pointer;list-style:none;display:flex;align-items:center;gap:4px">'
        f'<span style="font-size:10px;color:#94a3b8">▸</span>'
        f'<b>Duration assumptions</b></summary>'
        f'<ul style="margin:4px 0 4px 16px;padding:0;list-style:disc;line-height:1.6">'
        f'{bullets}</ul></details>'
    )


def _duration_assumption_note(testing_pct: int) -> str:
    """Short inline note for story cards showing what the duration includes/excludes."""
    return (
        f'<div style="margin-top:6px;padding:6px 10px;background:#fffbeb;border:1px solid #fde68a;'
        f'border-radius:4px;font-size:11px;color:#92400e;line-height:1.4">'
        f'Duration includes unit testing (+{testing_pct}%), integration testing, code review, '
        f'and documentation. Excludes performance testing, QA, UAT, deployment, '
        f'and requirements gathering. <a href="#faq-duration" style="color:#b45309">Details</a></div>'
    )


def _faq_section(testing_pct: int) -> str:
    """FAQ accordion section for duration estimate assumptions."""
    return f'''\
<div id="faq-duration" style="margin-top:32px">
<h2 style="font-size:16px;margin-bottom:8px">FAQ: Duration Estimates</h2>
<details style="margin-bottom:8px;border:1px solid #e2e8f0;border-radius:6px;background:#fff">
<summary style="padding:10px 14px;cursor:pointer;font-weight:600;font-size:13px;color:#1e293b">What do the duration estimates include?</summary>
<div style="padding:10px 14px;font-size:12px;color:#475569;line-height:1.6">
<ul style="margin:0;padding-left:18px">
<li><b>Development effort</b> — coding, configuration, and implementation of each dev task node</li>
<li><b>Unit testing (+{testing_pct}%)</b> — writing and running unit tests is baked into every task duration as a {testing_pct}% overhead</li>
<li><b>Integration testing</b> — cross-service and integration test effort is included in task estimates</li>
<li><b>Code review</b> — peer review cycles and rework from review feedback</li>
<li><b>Documentation</b> — user guides, API documentation, runbooks</li>
<li><b>Dependency sequencing</b> — critical path analysis determines which tasks block others and the minimum project duration</li>
</ul>
</div>
</details>
<details style="margin-bottom:8px;border:1px solid #e2e8f0;border-radius:6px;background:#fff">
<summary style="padding:10px 14px;cursor:pointer;font-weight:600;font-size:13px;color:#1e293b">What do the duration estimates exclude?</summary>
<div style="padding:10px 14px;font-size:12px;color:#475569;line-height:1.6">
<ul style="margin:0;padding-left:18px">
<li><b>E2E testing</b> — end-to-end test suites spanning the full application stack</li>
<li><b>Performance / load testing</b> — stress tests, benchmarking, and performance tuning</li>
<li><b>Security testing</b> — penetration testing, vulnerability scanning, security audits</li>
<li><b>QA / manual testing</b> — dedicated QA cycles and exploratory testing</li>
<li><b>UAT (User Acceptance Testing)</b> — stakeholder sign-off and acceptance testing rounds</li>
<li><b>Accessibility audits</b> — WCAG compliance testing (unless added as an optional node)</li>
<li><b>Requirements gathering</b> — discovery, analysis, and story refinement</li>
<li><b>Deployment / release management</b> — CI/CD setup, environment provisioning, release coordination</li>
<li><b>Data migration</b> — migrating existing data to new schemas or systems</li>
<li><b>Bug fixing / hardening</b> — post-development rework and stabilisation sprints</li>
<li><b>Non-functional requirements</b> — scalability, availability, and monitoring setup beyond what is structurally derived from user stories</li>
</ul>
</div>
</details>
<details style="margin-bottom:8px;border:1px solid #e2e8f0;border-radius:6px;background:#fff">
<summary style="padding:10px 14px;cursor:pointer;font-weight:600;font-size:13px;color:#1e293b">How are durations calculated?</summary>
<div style="padding:10px 14px;font-size:12px;color:#475569;line-height:1.6">
<p style="margin:0 0 8px">Each user story is decomposed into dev task nodes via backwards dependency mapping. Every node receives a <code>duration_days</code> based on task complexity (Tiny=0.25d, Small=0.5–1d, Medium=1–2d, Large=3–4d, Epic=5–7d), then a <b>{testing_pct}% testing overhead</b> (unit + integration) is applied. Estimates also include time for code review and documentation. Story effort is the sum of its owned nodes. Project duration is derived from critical path analysis — the longest chain of dependent stories that cannot be parallelised.</p>
<p style="margin:0">Durations assume AI-assisted development. Adjust upward if not using AI coding tools.</p>
</div>
</details>
</div>'''


def _shared_js_gantt_utils() -> str:
    """Shared JS: tooltip, scrollToStory, renderGantt. Returns plain JS (single braces)."""
    return '''\
function showGanttTip(evt, text) {
  const tip = document.getElementById("ganttTooltip");
  tip.textContent = text;
  tip.style.left = evt.pageX + 12 + "px";
  tip.style.top = evt.pageY - 20 + "px";
  tip.style.opacity = 1;
}
function hideGanttTip() {
  document.getElementById("ganttTooltip").style.opacity = 0;
}

function scrollToStory(id) {
  const el = document.getElementById("story-" + id);
  if (el) el.scrollIntoView({ behavior: "smooth", block: "center" });
}

function renderGantt(devs, storyFilter, targetId) {
  targetId = targetId || "ganttChart";
  const fullGantt = teamGantt[String(devs)];
  if (!fullGantt) return;

  // Filter gantt data if storyFilter provided
  const gantt = storyFilter ? {} : fullGantt;
  if (storyFilter) {
    for (const [sid, data] of Object.entries(fullGantt)) {
      if (storyFilter.has(sid)) gantt[sid] = data;
    }
  }
  if (Object.keys(gantt).length === 0) return;

  // Build a story lookup from waves data (for metadata like effort, category, is_critical)
  const storyLookup = {};
  const allWaves = wavesData;
  allWaves.forEach(w => w.stories.forEach(s => { storyLookup[s.id] = s; }));

  // Fallback: if no feature groups defined, create a single group with all stories
  const groups = featureGroups.length > 0 ? featureGroups : [{ id: "_all", name: "All Stories", storyIds: Object.keys(gantt) }];

  // Group stories by feature group, compute group completion time, sort groups
  const groupsWithEnd = groups.map(grp => {
    const stories = grp.storyIds
      .filter(sid => gantt[sid] && storyLookup[sid])
      .map(sid => ({ ...storyLookup[sid], id: sid, start: gantt[sid].start * (typeof estBuffer !== "undefined" ? estBuffer : 1), end: gantt[sid].end * (typeof estBuffer !== "undefined" ? estBuffer : 1) }))
      .sort((a, b) => a.start - b.start || a.end - b.end);
    const groupEnd = stories.length > 0 ? Math.max(...stories.map(s => s.end)) : 0;
    return { ...grp, stories, groupEnd };
  });
  groupsWithEnd.sort((a, b) => a.groupEnd - b.groupEnd);

  const ROW_H = 28, PAD_L = 160, PAD_R = 30, BAR_H = 18;
  const chartW = 900;
  const barArea = chartW - PAD_L - PAD_R;

  const rows = [];
  groupsWithEnd.forEach(grp => {
    if (grp.stories.length === 0) return;
    rows.push({ type: "group", label: grp.name });
    grp.stories.forEach(s => {
      rows.push({ type: "story", ...s });
    });
  });

  const storyRows = rows.filter(r => r.type === "story");
  const maxEnd = Math.max(...storyRows.map(r => r.end)) || 1;
  // For per-release charts, shift x-axis so bars start at 0 but labels show actual days
  const minStart = storyFilter ? Math.min(...storyRows.map(r => r.start)) : 0;
  const span = maxEnd - minStart || 1;
  const totalH = rows.length * ROW_H + 30;
  const x = d => PAD_L + ((d - minStart) / span) * barArea;

  let svg = `<svg viewBox="0 0 ${chartW} ${totalH}" style="width:100%;max-width:${chartW}px;font-family:system-ui,sans-serif">`;

  const niceSteps = [0.5, 1, 2, 5, 10, 15, 20, 25, 50];
  const step = niceSteps.find(s => span / s <= 10) || Math.ceil(span / 10);
  for (let tick = 0; tick <= span; tick += step) {
    const rounded = Math.round(tick * 10) / 10;
    const xp = x(rounded + minStart);
    const actualDay = Math.round((rounded + minStart) * 10) / 10;
    svg += `<line x1="${xp}" y1="0" x2="${xp}" y2="${totalH - 20}" stroke="#f1f5f9" stroke-width="1"/>`;
    svg += `<text x="${xp}" y="${totalH - 6}" text-anchor="middle" fill="#94a3b8" font-size="10">Day ${actualDay}</text>`;
  }

  rows.forEach((row, i) => {
    const yc = i * ROW_H + ROW_H / 2;
    if (row.type === "group") {
      svg += `<rect x="0" y="${i * ROW_H}" width="${chartW}" height="${ROW_H}" fill="#f1f5f9"/>`;
      svg += `<text x="12" y="${yc + 4}" fill="#475569" font-size="12" font-weight="bold">${row.label}</text>`;
    } else {
      const bx = x(row.start);
      const bw = Math.max(x(row.end) - bx, 3);
      const by = yc - BAR_H / 2;
      const color = row.is_critical ? "#ef4444" : (row.category === "infrastructure" ? "#94a3b8" : "#3b82f6");
      const label = row.id.length > 18 ? row.id.slice(0, 18) + "..." : row.id;

      svg += `<text x="${PAD_L - 8}" y="${yc + 4}" text-anchor="end" fill="#475569" font-size="11" style="cursor:pointer" onclick="scrollToStory('${row.id}')">${label}</text>`;
      svg += `<rect x="${bx}" y="${by}" width="${bw}" height="${BAR_H}" rx="3" fill="${color}" opacity="0.85" style="cursor:pointer" onclick="scrollToStory('${row.id}')" onmousemove="showGanttTip(event, '${row.id}: ${row.title.replace("'", "")} | Day ${row.start.toFixed(1)}\u2013${row.end.toFixed(1)} (${row.effort_days}d effort)')" onmouseout="hideGanttTip()"/>`;
      if (bw > 40) {
        svg += `<text x="${bx + bw / 2}" y="${yc + 4}" text-anchor="middle" fill="#fff" font-size="9" font-weight="bold" pointer-events="none">${row.effort_days}d</text>`;
      }
    }
  });

  svg += `</svg>`;
  document.getElementById(targetId).innerHTML = svg;
}'''


def _shared_js_dropdown_init() -> str:
    """Shared JS: populate devSelectGlobal dropdown. Returns plain JS (single braces)."""
    return '''\
(function() {
  const sel = document.getElementById("devSelectGlobal");
  if (!sel) return;
  for (let d = 1; d <= maxDevs; d++) {
    const opt = document.createElement("option");
    opt.value = d;
    opt.textContent = d + " dev" + (d > 1 ? "s" : "");
    if (d === Math.min(defaultDevs, maxDevs)) opt.selected = true;
    sel.appendChild(opt);
  }
})();'''


# ── PM Report (pm-report.html) ──────────────────────────────────────────────

def generate_pm_report(ctx: dict) -> str:
    s = ctx["summary"]
    rec = ctx["recommended"]
    risk = ctx["risk_pct"]
    cp_eff = ctx["cp_effort"]
    tot_eff = ctx["total_effort"]
    mw = ctx["max_wait"]
    mu = ctx["max_unblocks"]
    mu_cnt = ctx["max_unblocks_count"]
    vm = ctx["verifiable_map"]
    cp_ids = ctx["cp_ids"]
    waves = ctx["waves"]

    # Duration for recommended (knee) team size
    rec_dur = next((t["dur"] for t in ctx["team_sizes"] if t["devs"] == rec), s["project_duration_days"])

    # Team table rows — only up to the knee (max useful developers)
    team_rows = ""
    for t in ctx["team_sizes"]:
        cls = ' class="rec"' if t["devs"] == rec else ""
        lbl = " &larr; max useful" if t["devs"] == rec else ""
        team_rows += f'<tr{cls} data-dur="{t["dur"]}"><td>{t["devs"]}{lbl}</td><td>{t["dur"]}d</td><td>{t["saving_str"]}</td></tr>\n'

    # Critical path chain
    cp_chain = " &rarr; ".join(
        f'[{x["id"]}: {html.escape(x["title"][:60])}] (Day {x["testable_day"]})' for x in ctx["cp"])

    # Risk bullets
    risk_desc = (
        "This is high — the plan is fragile with little schedule slack."
        if risk > 40 else
        "Moderate — some parallel tracks provide schedule buffer."
    )
    mw_bullet = '<li id="maxWaitBullet"></li>'
    mu_bullet = (
        f'<li><b>{mu["id"]}: {html.escape(mu["title"][:60])}</b> unblocks {mu_cnt} downstream stories. '
        f'A delay here cascades widely. Consider scheduling a buffer after this milestone.</li>'
    ) if mu else ""

    # ── Build story cards flat (PM-level: no dev tasks, no standards) ─────
    # Cards rendered flat; JS regroups by wave when team size changes.
    testing_pct = s.get("testing_overhead_pct", 30)
    assumption_note = _duration_assumption_note(testing_pct)
    faq_html = _faq_section(testing_pct)
    story_cards = ""
    all_stories = {s["id"]: s for w in waves for s in w["stories"]}
    for sid, st in all_stories.items():
        is_crit = st["id"] in cp_ids
        cb = ' <span class="pm-cb">★ Critical</span>' if is_crit else ""
        pri = st.get("priority", "normal")
        pri_badge = f' <span class="pm-pri pm-pri-{pri}">{pri}</span>' if pri != "normal" else ""
        cc = "pm-infra" if st["category"] == "infrastructure" else "pm-feat"
        verif = vm.get(st["id"], "Story testable end-to-end")
        deps = ", ".join(st["depends_on"]) if st["depends_on"] else "None"
        ub = ", ".join(st["unblocks"]) if st["unblocks"] else "None (leaf story)"
        pm_assumptions_line = _render_assumptions_dropdown(
            st.get("duration_assumptions", ""), css_prefix="pm-")
        deps_json = html.escape(json.dumps(st["depends_on"]), quote=True)
        ub_json = html.escape(json.dumps(st["unblocks"]), quote=True)
        story_cards += f'''<div class="pm-sc {cc}{" pm-crit" if is_crit else ""}" id="story-{st["id"]}" data-story-id="{st["id"]}" data-deps="{deps_json}" data-unblocks="{ub_json}" style="display:none">
<div class="pm-sh"><strong>{html.escape(st["id"])}</strong>: {html.escape(st["title"])}{cb}{pri_badge}<span class="pm-sm">effort: {st["effort_days"]}d</span></div>
<div class="pm-sd"><b>Acceptance Criteria:</b> {_render_acceptance_criteria(verif)}</div>
<details class="pm-sd-expand"><summary class="pm-sd-toggle"><b>Depends on:</b> <span class="pm-sd-count">{len(st["depends_on"])} {("story" if len(st["depends_on"]) == 1 else "stories")}</span></summary><div class="pm-sd">{deps}</div></details>
<details class="pm-sd-expand"><summary class="pm-sd-toggle"><b>Unblocks:</b> <span class="pm-sd-count">{len(st["unblocks"])} {("story" if len(st["unblocks"]) == 1 else "stories")}</span></summary><div class="pm-sd">{ub}</div></details>
<div style="margin:4px 0"><span class="pm-dep-link" onclick="togglePmDepGraph(this)">show dependency graph</span></div>
{pm_assumptions_line}
<div class="pm-dep-graph-popup"></div>
</div>\n'''

    # ── Build actual completions timeline for reconciled reports ─────
    actual_burndown = []
    if ctx.get("reconciliation") and ctx.get("story_status"):
        recon = ctx["reconciliation"]
        story_status = ctx["story_status"]
        # Determine project start date
        recon_date = datetime.date.fromisoformat(recon["reconciliation_date"])
        elapsed = recon["elapsed_days"]
        project_start = recon_date - datetime.timedelta(days=int(elapsed))
        # Collect actual close days (calendar days from project start)
        close_events: dict[int, list[str]] = {}
        for sid, info in story_status.items():
            if info["state"] == "CLOSED" and info.get("closed_at"):
                closed_str = info["closed_at"]
                try:
                    closed_dt = datetime.datetime.fromisoformat(
                        closed_str.replace("Z", "+00:00")).date()
                    day = (closed_dt - project_start).days
                    if day < 0:
                        day = 0
                    close_events.setdefault(day, []).append(sid)
                except (ValueError, TypeError):
                    pass
        if close_events:
            cum = 0
            cum_ids: list[str] = []
            for day in sorted(close_events):
                ids = close_events[day]
                cum_ids.extend(ids)
                cum += len(ids)
                actual_burndown.append({
                    "day": day,
                    "cum": cum,
                    "stories": ids,
                    "allStories": list(cum_ids),
                })

    # ── Embed per-team-size data as JSON for JS interactivity ─────
    story_meta_json = json.dumps(ctx["story_meta"])
    team_testable_json = json.dumps(ctx["team_story_testable"])
    team_gantt_json = json.dumps(ctx["team_story_gantt"])
    effort_dist_json = json.dumps(ctx["effort_dist"])
    team_makespan_json = _build_team_makespan_json(ctx)
    waves_data_json = _build_waves_data_json(ctx)
    feature_groups_json = _build_feature_groups_json(ctx)
    actual_burndown_json = json.dumps(actual_burndown)
    release_story_map_json = json.dumps(ctx["release_story_map"])

    # Build per-release HTML containers
    release_map = ctx["release_story_map"]
    has_releases = len(release_map) > 1
    if has_releases:
        burndown_containers = ""
        gantt_containers = ""
        for rel in sorted(release_map.keys(), key=lambda k: int(k)):
            burndown_containers += f'<h3 style="margin:16px 0 4px;font-size:14px;color:#475569">Release {rel}</h3>\n<div id="burndownChart-{rel}" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:16px;margin:4px 0 12px"></div>\n'
            gantt_containers += f'<h3 style="margin:16px 0 4px;font-size:14px;color:#475569">Release {rel}</h3>\n<div id="ganttContainer-{rel}" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:0;margin:4px 0 12px;overflow-x:auto"><div id="ganttChart-{rel}"></div></div>\n'
    else:
        burndown_containers = '<div id="burndownChart" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:16px;margin:12px 0"></div>'
        gantt_containers = '<div id="ganttContainer" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:0;margin:12px 0;overflow-x:auto"><div id="ganttChart"></div></div>'

    return f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>{html.escape(s["project"])} — PM Report</title>
<style>
*{{margin:0;padding:0;box-sizing:border-box}}
body{{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;color:#333;line-height:1.6;padding:24px}}
.c{{max-width:900px;margin:0 auto}}
h1{{font-size:22px;margin-bottom:4px}}
h2{{font-size:17px;margin:28px 0 10px;border-bottom:2px solid #3b82f6;padding-bottom:4px}}
.sb{{display:flex;flex-wrap:wrap;gap:12px;background:#1e293b;color:#fff;padding:16px 20px;border-radius:8px;margin:16px 0}}
.st{{text-align:center;flex:1;min-width:100px}}
.sv{{font-size:22px;font-weight:bold;color:#60a5fa}}
.sl{{font-size:10px;color:#94a3b8;text-transform:uppercase;letter-spacing:0.5px}}
table{{width:100%;border-collapse:collapse;margin:12px 0;background:#fff;border-radius:8px;overflow:hidden}}
th{{background:#1e293b;color:#fff;padding:8px 12px;text-align:left;font-size:13px}}
td{{padding:8px 12px;border-bottom:1px solid #e5e7eb;font-size:13px}}
tr:hover{{background:#f8fafc}}
tr.rec{{font-weight:bold}}
.cp{{background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;padding:16px;margin:12px 0;font-family:monospace;font-size:13px;line-height:1.8;word-break:break-word}}
ul{{margin:8px 0 8px 24px}} li{{margin:6px 0;font-size:13px}}
.dd{{display:inline-block;padding:4px 10px;border:1px solid #475569;border-radius:6px;font-size:13px;background:#1e293b;color:#60a5fa;cursor:pointer;font-weight:bold}}
.est-slider{{display:flex;flex-direction:column;align-items:center;gap:2px}}
.est-slider input[type=range]{{-webkit-appearance:none;width:100px;height:6px;border-radius:3px;background:#334155;outline:none;cursor:pointer}}
.est-slider input[type=range]::-webkit-slider-thumb{{-webkit-appearance:none;width:16px;height:16px;border-radius:50%;background:#60a5fa;border:2px solid #1e293b;cursor:pointer}}
.est-slider input[type=range]::-moz-range-thumb{{width:16px;height:16px;border-radius:50%;background:#60a5fa;border:2px solid #1e293b;cursor:pointer}}
.est-label{{font-size:11px;color:#94a3b8;font-weight:bold}}
.sub{{font-size:12px;color:#64748b;margin-bottom:8px}}
.wh{{background:#1e293b;color:#fff;padding:8px 16px;border-radius:6px 6px 0 0;margin-top:16px;font-weight:bold;font-size:14px}}
.pm-sc{{background:#fff;border:1px solid #e5e7eb;border-top:none;padding:12px 16px;cursor:default}}
.pm-sc.pm-infra{{border-left:4px solid #9ca3af}}
.pm-sc.pm-feat{{border-left:4px solid #3b82f6}}
.pm-sc.pm-crit{{border-left:4px solid #ef4444}}
.pm-sh{{font-size:14px;margin-bottom:4px}}
.pm-sm{{float:right;color:#64748b;font-size:12px;font-weight:normal}}
.pm-sd{{font-size:12px;color:#64748b;margin:2px 0}}
.pm-cb{{background:#fee2e2;color:#dc2626;padding:1px 6px;border-radius:4px;font-size:11px;font-weight:bold;margin-left:6px}}
.pm-pri{{padding:1px 6px;border-radius:4px;font-size:11px;font-weight:bold;margin-left:6px}}
.pm-pri-critical{{background:#fee2e2;color:#dc2626}}
.pm-pri-high{{background:#fff7ed;color:#c2410c}}
.pm-pri-low{{background:#f1f5f9;color:#64748b}}
.pm-sd-expand{{font-size:12px;color:#64748b;margin:2px 0}}
.pm-sd-toggle{{cursor:pointer;list-style:none;display:flex;align-items:center;gap:4px}}
.pm-sd-toggle::-webkit-details-marker{{display:none}}
.pm-sd-toggle::before{{content:"▸";font-size:10px;color:#94a3b8;transition:transform 0.15s}}
.pm-sd-expand[open]>.pm-sd-toggle::before{{transform:rotate(90deg)}}
.pm-sd-count{{color:#94a3b8;font-weight:normal}}
.pm-dep-link{{color:#3b82f6;cursor:pointer;font-size:11px;margin-left:8px}}
.pm-dep-link:hover{{text-decoration:underline}}
.pm-dep-graph-popup{{display:none;background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;margin-top:8px}}
.pm-dep-graph-popup.open{{display:block}}
</style></head><body>
<div class="c">
<h1>{html.escape(s["project"])} — PM Report</h1>
<p style="color:#64748b;font-size:13px">Generated {datetime.date.today()}</p>

<div class="sb">
<div class="st"><div class="sv"><select id="devSelectGlobal" class="dd" onchange="updateAll()"></select></div><div class="sl">Team Size</div></div>
<div class="st"><div class="est-slider"><input type="range" id="estSlider" min="0" max="2" step="1" value="1" oninput="updateEstimate()" onchange="updateEstimate()"><div class="est-label" id="estLabel">Likely</div></div><div class="sl">Estimate</div></div>
<div class="st"><div class="sv" id="statDuration">—</div><div class="sl"><a href="#faq-duration" style="color:inherit;text-decoration:underline dotted;text-underline-offset:2px" title="See what this estimate includes and excludes">Est. Duration</a></div></div>
<div class="st"><div class="sv">{rec}</div><div class="sl">Max Useful Devs</div></div>
<div class="st"><div class="sv">{s["total_stories"]}</div><div class="sl">Total Stories</div></div>
<div class="st"><div class="sv">{risk}%</div><div class="sl">Risk Concentration</div></div>
</div>

<h2>Team Size Estimates</h2>
<table id="teamTable"><tr><th>Developers</th><th>Estimated Duration</th><th>Saving vs Previous</th></tr>
{team_rows}</table>
<p style="font-size:12px;color:#64748b">Beyond {rec} developers the gains are marginal (less than 1 day per additional developer).</p>

<h2>Potential Blockers</h2>
<ul>
<li><b>Risk concentration: {risk}%</b> of total effort sits on the critical path ({cp_eff}d of {tot_eff}d). {risk_desc}</li>
{mw_bullet}
{mu_bullet}
</ul>

<h2>Stories Completed Over Time</h2>
<p class="sub">Cumulative stories ready for testing. The curve shifts left as you add developers.{"" if not actual_burndown else " The green line shows actual completions from closed GitHub issues."}</p>
{burndown_containers}

<h2>Effort Distribution</h2>
<div style="display:flex;gap:24px;flex-wrap:wrap;margin:12px 0;align-items:flex-start">
<div style="flex:0 0 auto">
<p class="sub">Effort by technical category.</p>
<div id="pieChart" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:16px"></div>
</div>
{_tech_stack_html(ctx)}
</div>

<h2>Story Timeline (Gantt)</h2>
<p class="sub">Story-level timeline grouped by business value. Controlled by the team size selector above.</p>
{gantt_containers}
<div id="ganttTooltip" style="position:absolute;background:#1e293b;color:#fff;padding:6px 10px;border-radius:6px;font-size:11px;pointer-events:none;white-space:nowrap;z-index:10;opacity:0;transition:opacity 0.15s"></div>
<div id="burndownTooltip" style="position:absolute;background:#1e293b;color:#fff;padding:10px 14px;border-radius:6px;font-size:11px;pointer-events:none;z-index:10;opacity:0;transition:opacity 0.15s;min-width:200px;max-width:340px;line-height:1.5"></div>

<h2>Implementation Order</h2>
<p class="sub">Stories in implementation order. Infrastructure stories are gray-bordered, features blue, critical-path stories red.</p>
{assumption_note}
<div id="waveCardsContainer">
{story_cards}
</div>

{faq_html}

<div style="margin-top:32px;padding:12px 16px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;font-size:11px;color:#94a3b8;text-align:center">
This report was generated with AI assistance. Estimates are indicative and based on AI-assisted development assumptions. Actual timelines may vary based on team velocity, scope changes, and external dependencies. Always validate with your team before committing to delivery dates.
</div>

</div>

<script>
const storyMeta = {story_meta_json};
const teamTestable = {team_testable_json};
const teamGantt = {team_gantt_json};
const effortDist = {effort_dist_json};
const teamMakespan = {team_makespan_json};
const wavesData = {waves_data_json};
const featureGroups = {feature_groups_json};
const actualBurndown = {actual_burndown_json};
const releaseStoryMap = {release_story_map_json};
const hasReleases = Object.keys(releaseStoryMap).length > 1;
const maxDevs = {rec};
const defaultDevs = Math.min(2, maxDevs);
const totalStories = {s["total_stories_with_infra"]};

// Estimate confidence: 0=optimistic (1.0x), 1=likely (1.15x), 2=pessimistic (1.3x)
const estBuffers = [1.0, 1.15, 1.3];
const estLabels = ["Optimistic", "Likely", "Pessimistic"];
let estBuffer = estBuffers[1];

function updateEstimate() {{
  const idx = parseInt(document.getElementById("estSlider").value);
  estBuffer = estBuffers[idx];
  document.getElementById("estLabel").textContent = estLabels[idx];
  updateTeamTable();
  updateAll();
}}

function updateTeamTable() {{
  let prevDur = null;
  document.querySelectorAll("#teamTable tr[data-dur]").forEach(row => {{
    const baseDur = parseFloat(row.getAttribute("data-dur"));
    const adjusted = Math.round(baseDur * estBuffer * 10) / 10;
    row.querySelector("td:nth-child(2)").textContent = adjusted + "d";
    const savingCell = row.querySelector("td:nth-child(3)");
    if (prevDur !== null) {{
      const saving = Math.round((prevDur - adjusted) * 10) / 10;
      savingCell.textContent = saving + "d";
    }} else {{
      savingCell.textContent = "\u2014";
    }}
    prevDur = adjusted;
  }});
}}

{_shared_js_gantt_utils()}

// Populate the single global dropdown
{_shared_js_dropdown_init()}

function regroupWaveCards(devs) {{
  const gantt = teamGantt[String(devs)] || {{}};
  const container = document.getElementById("waveCardsContainer");
  container.querySelectorAll("[data-story-id]").forEach(el => el.style.display = "none");
  container.querySelectorAll(".wh").forEach(el => el.remove());

  // Sort all stories by simulation start time (implementation order)
  const sorted = storyMeta
    .map(s => ({{ id: s.id, start: gantt[s.id] ? gantt[s.id].start : 9999, end: gantt[s.id] ? gantt[s.id].end : 9999 }}))
    .sort((a, b) => a.start - b.start || a.end - b.end);

  sorted.forEach(s => {{
    const card = container.querySelector('[data-story-id="' + s.id + '"]');
    if (card) {{
      card.style.display = "";
      container.appendChild(card);
    }}
  }});
}}

function togglePmDepGraph(el) {{
  const card = el.closest(".pm-sc");
  const popup = card.querySelector(".pm-dep-graph-popup");
  if (popup) popup.classList.toggle("open");
}}

function closePmDepGraph(btn) {{
  btn.closest(".pm-dep-graph-popup").classList.remove("open");
}}

function updateAll() {{
  const devs = document.getElementById("devSelectGlobal").value;
  const dur = teamMakespan[String(devs)];
  const adjusted = dur != null ? Math.round(dur * estBuffer * 10) / 10 : null;
  document.getElementById("statDuration").textContent = adjusted != null ? adjusted + "d" : "—";
  renderMaxWait(devs);
  if (hasReleases) {{
    for (const [rel, sids] of Object.entries(releaseStoryMap)) {{
      const filter = new Set(sids);
      renderGantt(devs, filter, "ganttChart-" + rel);
      renderBurndown(devs, filter, "burndownChart-" + rel, sids.length);
    }}
  }} else {{
    renderBurndown(devs);
    renderGantt(devs);
  }}
  regroupWaveCards(devs);
}}

function renderMaxWait(devs) {{
  const testable = teamTestable[String(devs)];
  if (!testable) return;
  // Build story→release and per-release offset so R3 stories aren't
  // penalised for R1+R2 duration.
  const storyToRelease = {{}};
  Object.entries(releaseStoryMap).forEach(([rel, sids]) => {{
    sids.forEach(sid => {{ storyToRelease[sid] = rel; }});
  }});
  const releaseMinTestable = {{}};
  storyMeta.forEach(s => {{
    const rel = storyToRelease[s.id];
    if (rel == null || !testable[s.id]) return;
    const td = (testable[s.id] - s.effort_days) * estBuffer;
    if (releaseMinTestable[rel] == null || td < releaseMinTestable[rel])
      releaseMinTestable[rel] = td;
  }});
  let maxRatio = 0, maxStory = null;
  storyMeta.forEach(s => {{
    const day = testable[s.id] * estBuffer;
    if (s.effort_days > 0) {{
      const rel = storyToRelease[s.id];
      const offset = rel != null ? (releaseMinTestable[rel] || 0) : 0;
      const ratio = (day - offset) / s.effort_days;
      if (ratio > maxRatio) {{
        maxRatio = ratio;
        maxStory = {{ ...s, day: Math.round(day * 100) / 100 }};
      }}
    }}
  }});
  const el = document.getElementById("maxWaitBullet");
  if (maxStory) {{
    el.innerHTML = `<b>${{maxStory.id}}: ${{maxStory.title.slice(0,60)}}</b> has the longest relative wait — testable at Day ${{maxStory.day}} despite only ${{maxStory.effort_days}}d of own effort (blocked by deep dependency chains).`;
  }}
}}

function renderBurndown(devs, storyFilter, targetId, storyCount) {{
  targetId = targetId || "burndownChart";
  const fullGantt = teamGantt[String(devs)];
  if (!fullGantt) return;

  // Group stories by their completion day (scaled by estimate buffer)
  const dayStories = {{}};
  storyMeta.forEach(s => {{
    if (!fullGantt[s.id]) return;
    if (storyFilter && !storyFilter.has(s.id)) return;
    const d = Math.round(fullGantt[s.id].end * estBuffer * 100) / 100;
    if (!dayStories[d]) dayStories[d] = [];
    dayStories[d].push(s.id);
  }});
  const sortedDays = Object.keys(dayStories).map(Number).sort((a, b) => a - b);
  const points = [];
  let cum = 0;
  const cumStories = [];
  // For per-release charts, start at the release's earliest completion day
  const minDay = storyFilter && sortedDays.length > 0 ? sortedDays[0] : 0;
  points.push({{day: minDay, cum: 0, newStories: [], allStories: []}});
  sortedDays.forEach(d => {{
    const newIds = dayStories[d];
    cumStories.push(...newIds);
    cum += newIds.length;
    points.push({{day: d, cum, newStories: newIds, allStories: [...cumStories]}});
  }});

  // Build actual completion points (from reconciliation data)
  const hasActual = !storyFilter && actualBurndown.length > 0;
  const actPoints = hasActual ? [{{day: 0, cum: 0, stories: [], allStories: []}}].concat(actualBurndown) : [];

  const W = 820, H = 280, P = {{l:50, r:20, t:20, b:40}};
  const cw = W - P.l - P.r, ch = H - P.t - P.b;
  const allDays = points.map(p => p.day).concat(actPoints.map(p => p.day));
  const maxDay = Math.max(...allDays) * 1.05 || 1;
  const span = maxDay - minDay || 1;
  const maxCum = storyCount || totalStories;

  const xScale = d => P.l + ((d - minDay) / span) * cw;
  const yScale = c => P.t + ch - (c / maxCum) * ch;

  let area = `M${{xScale(0)}},${{yScale(0)}}`;
  points.forEach(p => {{ area += ` L${{xScale(p.day)}},${{yScale(p.cum)}}`; }});
  area += ` L${{xScale(points[points.length-1].day)}},${{yScale(0)}} Z`;

  let line = `M${{xScale(points[0].day)}},${{yScale(points[0].cum)}}`;
  points.forEach(p => {{ line += ` L${{xScale(p.day)}},${{yScale(p.cum)}}`; }});

  // Actual completion line + area (green)
  let actArea = "", actLine = "", actDots = "";
  if (hasActual) {{
    actArea = `M${{xScale(0)}},${{yScale(0)}}`;
    actPoints.forEach(p => {{ actArea += ` L${{xScale(p.day)}},${{yScale(p.cum)}}`; }});
    actArea += ` L${{xScale(actPoints[actPoints.length-1].day)}},${{yScale(0)}} Z`;

    actLine = `M${{xScale(actPoints[0].day)}},${{yScale(actPoints[0].cum)}}`;
    actPoints.forEach(p => {{ actLine += ` L${{xScale(p.day)}},${{yScale(p.cum)}}`; }});

    actPoints.filter(p => p.day > 0).forEach(p => {{
      actDots += `<circle cx="${{xScale(p.day)}}" cy="${{yScale(p.cum)}}" r="4" fill="#10b981" stroke="#fff" stroke-width="2" pointer-events="none"/>`;
    }});
  }}

  let grid = "";
  const yTicks = [0, Math.round(maxCum/4), Math.round(maxCum/2), Math.round(maxCum*3/4), maxCum];
  yTicks.forEach(v => {{
    grid += `<line x1="${{P.l}}" y1="${{yScale(v)}}" x2="${{W-P.r}}" y2="${{yScale(v)}}" stroke="#e5e7eb" stroke-width="1"/>`;
    grid += `<text x="${{P.l-8}}" y="${{yScale(v)+4}}" text-anchor="end" fill="#94a3b8" font-size="11">${{v}}</text>`;
  }});

  let xLabels = "";
  const niceSteps = [0.5, 1, 2, 5, 10, 15, 20, 25, 50];
  let step = niceSteps.find(s => span / s <= 8) || Math.ceil(span / 8);
  for (let tick = 0; tick <= span; tick += step) {{
    const actualDay = Math.round((tick + minDay) * 10) / 10;
    xLabels += `<text x="${{xScale(actualDay)}}" y="${{H-P.b+18}}" text-anchor="middle" fill="#94a3b8" font-size="10">Day ${{actualDay}}</text>`;
  }}

  // Store chart state for mouse tracking
  window._bdState = {{ points, actPoints, hasActual, maxDay, P, cw, W, H }};

  let dots = "";
  points.filter(p => p.day > 0).forEach(p => {{
    dots += `<circle cx="${{xScale(p.day)}}" cy="${{yScale(p.cum)}}" r="4" fill="#3b82f6" stroke="#fff" stroke-width="2" pointer-events="none"/>`;
  }});

  // Legend (only when actual data present)
  let legend = "";
  if (hasActual) {{
    legend = `<rect x="${{W-P.r-180}}" y="${{P.t+4}}" width="12" height="3" rx="1" fill="#3b82f6"/>` +
      `<text x="${{W-P.r-164}}" y="${{P.t+10}}" fill="#94a3b8" font-size="10">Projected</text>` +
      `<rect x="${{W-P.r-95}}" y="${{P.t+4}}" width="12" height="3" rx="1" fill="#10b981"/>` +
      `<text x="${{W-P.r-79}}" y="${{P.t+10}}" fill="#94a3b8" font-size="10">Actual</text>`;
  }}

  // Invisible overlay rect covers the full chart area for mouse tracking
  const overlay = `<rect x="${{P.l}}" y="${{P.t}}" width="${{cw}}" height="${{ch}}" fill="transparent" style="cursor:crosshair" onmousemove="onBurndownMove(event)" onmouseout="hideBurndownTip()"/>`;

  const svg = `<svg id="burndownSvg" viewBox="0 0 ${{W}} ${{H}}" style="width:100%;max-width:${{W}}px;font-family:system-ui,sans-serif">
    ${{grid}}
    <line x1="${{P.l}}" y1="${{yScale(0)}}" x2="${{W-P.r}}" y2="${{yScale(0)}}" stroke="#cbd5e1" stroke-width="1"/>
    <line x1="${{P.l}}" y1="${{P.t}}" x2="${{P.l}}" y2="${{yScale(0)}}" stroke="#cbd5e1" stroke-width="1"/>
    <path d="${{area}}" fill="#dbeafe" opacity="0.5"/>
    <path d="${{line}}" fill="none" stroke="#3b82f6" stroke-width="2.5" stroke-linejoin="round"/>
    ${{dots}}
    ${{hasActual ? `<path d="${{actArea}}" fill="#d1fae5" opacity="0.4"/>` : ""}}
    ${{hasActual ? `<path d="${{actLine}}" fill="none" stroke="#10b981" stroke-width="2.5" stroke-linejoin="round"/>` : ""}}
    ${{actDots}}
    ${{legend}}
    <line id="bdCrosshair" x1="0" y1="${{P.t}}" x2="0" y2="${{yScale(0)}}" stroke="#94a3b8" stroke-width="1" stroke-dasharray="4,3" opacity="0"/>
    ${{overlay}}
    ${{xLabels}}
    <text x="${{P.l-8}}" y="${{P.t-6}}" text-anchor="end" fill="#94a3b8" font-size="10">Stories</text>
    <text x="${{W-P.r}}" y="${{H-P.b+18}}" text-anchor="end" fill="#94a3b8" font-size="10">Days</text>
  </svg>`;

  document.getElementById(targetId).innerHTML = svg;
}}

function _snapTo(pts, day) {{
  let snap = pts[0];
  for (let i = 1; i < pts.length; i++) {{
    if (pts[i].day <= day) snap = pts[i];
    else break;
  }}
  return snap;
}}

function _buildGroupSection(storyIds, label, color, groupNameMap, storyGroupMap, groupTotalStories, groupOrder) {{
  const groupCompleted = {{}};
  storyIds.forEach(id => {{
    const gid = storyGroupMap[id] || "platform_setup";
    groupCompleted[gid] = (groupCompleted[gid] || 0) + 1;
  }});
  const groupIds = [...new Set(Object.keys(groupCompleted))];
  groupIds.sort((a, b) => (groupOrder[a] ?? 999) - (groupOrder[b] ?? 999));

  let out = "";
  groupIds.forEach(gid => {{
    const done = groupCompleted[gid] || 0;
    const total = groupTotalStories[gid] || 0;
    const gPct = total > 0 ? Math.round(done / total * 100) : 0;
    const gName = groupNameMap[gid] || gid;
    const barColor = done === total ? "#10b981" : color;

    out += `<div style="margin-top:6px">`;
    out += `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:2px">`;
    out += `<span style="color:#e2e8f0;font-size:11px;font-weight:600">${{gName}}</span>`;
    out += `<span style="color:#94a3b8;font-size:10px">${{done}}/${{total}}</span>`;
    out += `</div>`;
    out += `<div style="height:6px;background:#334155;border-radius:3px;overflow:hidden">`;
    out += `<div style="width:${{gPct}}%;height:100%;background:${{barColor}};border-radius:3px"></div>`;
    out += `</div>`;
    const storiesInGroup = storyIds.filter(id => (storyGroupMap[id] || "platform_setup") === gid);
    storiesInGroup.forEach(id => {{
      out += `<div style="font-size:10px;color:#94a3b8;padding-left:4px">${{id}}</div>`;
    }});
    out += `</div>`;
  }});
  return out;
}}

function onBurndownMove(evt) {{
  const st = window._bdState;
  if (!st) return;

  // Convert mouse position to day value
  const svgEl = document.getElementById("burndownSvg");
  const rect = svgEl.getBoundingClientRect();
  const svgX = (evt.clientX - rect.left) / rect.width * st.W;
  const day = ((svgX - st.P.l) / st.cw) * st.maxDay;
  if (day < 0) return hideBurndownTip();

  const displayDay = Math.max(0, Math.round(day));

  // Snap projected
  const snapProj = _snapTo(st.points, displayDay);

  // Snap actual (if present)
  const snapAct = st.hasActual ? _snapTo(st.actPoints, displayDay) : null;

  // Show crosshair
  const crosshair = document.getElementById("bdCrosshair");
  const hoverX = st.P.l + (displayDay / st.maxDay) * st.cw;
  crosshair.setAttribute("x1", hoverX);
  crosshair.setAttribute("x2", hoverX);
  crosshair.setAttribute("opacity", "1");

  // Shared lookups
  const groupNameMap = {{}};
  featureGroups.forEach(g => {{ groupNameMap[g.id] = g.name; }});
  const storyGroupMap = {{}};
  const groupTotalStories = {{}};
  storyMeta.forEach(s => {{
    storyGroupMap[s.id] = s.group || "platform_setup";
    const gid = s.group || "platform_setup";
    groupTotalStories[gid] = (groupTotalStories[gid] || 0) + 1;
  }});
  const groupOrder = {{}};
  featureGroups.forEach((g, i) => {{ groupOrder[g.id] = i; }});

  const tip = document.getElementById("burndownTooltip");
  let html = "";

  if (st.hasActual) {{
    // Two-section tooltip: Actual + Projected
    const actPct = Math.round(snapAct.cum / totalStories * 100);
    const projPct = Math.round(snapProj.cum / totalStories * 100);

    html += `<div style="font-weight:bold;margin-bottom:8px">Day ${{displayDay}}</div>`;

    // Actual section
    html += `<div style="border-left:3px solid #10b981;padding-left:8px;margin-bottom:10px">`;
    html += `<div style="font-weight:bold;color:#10b981;font-size:12px;margin-bottom:4px">Actual: ${{snapAct.cum}}/${{totalStories}} (${{actPct}}%)</div>`;
    html += _buildGroupSection(snapAct.allStories, "Actual", "#10b981", groupNameMap, storyGroupMap, groupTotalStories, groupOrder);
    html += `</div>`;

    // Projected section
    html += `<div style="border-left:3px solid #3b82f6;padding-left:8px">`;
    html += `<div style="font-weight:bold;color:#60a5fa;font-size:12px;margin-bottom:4px">Projected: ${{snapProj.cum}}/${{totalStories}} (${{projPct}}%)</div>`;
    html += _buildGroupSection(snapProj.allStories, "Projected", "#3b82f6", groupNameMap, storyGroupMap, groupTotalStories, groupOrder);
    html += `</div>`;
  }} else {{
    // Single section (no reconciliation data)
    const pct = Math.round(snapProj.cum / totalStories * 100);
    html += `<div style="font-weight:bold;margin-bottom:6px">Day ${{displayDay}}: ${{snapProj.cum}}/${{totalStories}} stories (${{pct}}%)</div>`;
    html += _buildGroupSection(snapProj.allStories, "Projected", "#3b82f6", groupNameMap, storyGroupMap, groupTotalStories, groupOrder);
  }}

  tip.innerHTML = html;
  tip.style.left = evt.pageX + 14 + "px";
  tip.style.top = evt.pageY - 20 + "px";
  tip.style.opacity = 1;
}}
function hideBurndownTip() {{
  document.getElementById("burndownTooltip").style.opacity = 0;
  const ch = document.getElementById("bdCrosshair");
  if (ch) ch.setAttribute("opacity", "0");
}}

function renderPieChart() {{
  const colors = ["#3b82f6","#f59e0b","#10b981","#8b5cf6","#ef4444","#ec4899","#06b6d4","#f97316"];
  const R = 90, cx = 120, cy = 110, W = 380, H = 230;
  const total = effortDist.reduce((s, e) => s + e.effort, 0);

  let angle = -Math.PI / 2;
  let paths = "";
  let legend = "";
  effortDist.forEach((entry, i) => {{
    const frac = entry.effort / total;
    const endAngle = angle + frac * 2 * Math.PI;
    const large = frac > 0.5 ? 1 : 0;
    const x1 = cx + R * Math.cos(angle);
    const y1 = cy + R * Math.sin(angle);
    const x2 = cx + R * Math.cos(endAngle);
    const y2 = cy + R * Math.sin(endAngle);
    const color = colors[i % colors.length];

    paths += `<path d="M${{cx}},${{cy}} L${{x1}},${{y1}} A${{R}},${{R}} 0 ${{large}},1 ${{x2}},${{y2}} Z" fill="${{color}}" stroke="#fff" stroke-width="2">` +
      `<title>${{entry.category}}: ${{entry.effort}}d (${{entry.pct}}%)</title></path>`;

    const ly = 18 + i * 22;
    legend += `<rect x="250" y="${{ly}}" width="12" height="12" rx="2" fill="${{color}}"/>` +
      `<text x="268" y="${{ly+11}}" fill="#475569" font-size="12" style="text-transform:capitalize">${{entry.category}} ${{entry.pct}}%</text>`;

    angle = endAngle;
  }});

  const svg = `<svg viewBox="0 0 ${{W}} ${{H}}" style="width:100%;max-width:${{W}}px;font-family:system-ui,sans-serif">
    ${{paths}}${{legend}}
  </svg>`;
  document.getElementById("pieChart").innerHTML = svg;
}}

// ── Mini-DAG on each story card ───────────────────────────
(function() {{
  const storyInfo = {{}};
  const defaultWaves = wavesData;
  defaultWaves.forEach(w => w.stories.forEach(s => {{
    storyInfo[s.id] = {{ title: s.title, category: s.category || "feature" }};
  }}));

  document.querySelectorAll(".pm-sc[data-story-id]").forEach(card => {{
    const sid = card.getAttribute("data-story-id");
    const depsList = JSON.parse(card.getAttribute("data-deps") || "[]");
    const ubList = JSON.parse(card.getAttribute("data-unblocks") || "[]");
    if (depsList.length === 0 && ubList.length === 0) return;

    const popup = card.querySelector(".pm-dep-graph-popup");
    if (!popup) return;

    const NW = 120, NH = 36, CGAP = 60, RGAP = 10, PAD = 16;
    const hasDeps = depsList.length > 0;
    const hasUb = ubList.length > 0;
    const cols = (hasDeps ? 1 : 0) + 1 + (hasUb ? 1 : 0);
    const maxRows = Math.max(1, depsList.length, ubList.length);
    const totalW = PAD * 2 + cols * NW + (cols - 1) * CGAP;
    const totalH = PAD * 2 + maxRows * NH + (maxRows - 1) * RGAP;

    const nodes = {{}};
    let colIdx = 0;

    if (hasDeps) {{
      const colH = depsList.length * NH + (depsList.length - 1) * RGAP;
      const startY = PAD + (totalH - PAD * 2 - colH) / 2;
      depsList.forEach((d, i) => {{
        nodes[d] = {{ x: PAD + colIdx * (NW + CGAP), y: startY + i * (NH + RGAP), role: "dep" }};
      }});
      colIdx++;
    }}

    const centerY = PAD + (totalH - PAD * 2 - NH) / 2;
    nodes[sid] = {{ x: PAD + colIdx * (NW + CGAP), y: centerY, role: "self" }};
    colIdx++;

    if (hasUb) {{
      const colH = ubList.length * NH + (ubList.length - 1) * RGAP;
      const startY = PAD + (totalH - PAD * 2 - colH) / 2;
      ubList.forEach((u, i) => {{
        nodes[u] = {{ x: PAD + colIdx * (NW + CGAP), y: startY + i * (NH + RGAP), role: "succ" }};
      }});
    }}

    let svg = `<svg viewBox="0 0 ${{totalW}} ${{totalH}}" style="width:${{totalW}}px;height:${{totalH}}px;font-family:system-ui,sans-serif">`;
    svg += `<defs><marker id="ah-${{sid}}" viewBox="0 0 10 7" refX="10" refY="3.5" markerWidth="8" markerHeight="6" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#94a3b8"/></marker></defs>`;

    depsList.forEach(d => {{
      const from = nodes[d], to = nodes[sid];
      const x1 = from.x + NW, y1 = from.y + NH / 2;
      const x2 = to.x, y2 = to.y + NH / 2;
      const mx = (x1 + x2) / 2;
      if (Math.abs(y1 - y2) < 1) {{
        svg += `<line x1="${{x1}}" y1="${{y1}}" x2="${{x2}}" y2="${{y2}}" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }} else {{
        svg += `<path d="M${{x1}},${{y1}} L${{mx}},${{y1}} L${{mx}},${{y2}} L${{x2}},${{y2}}" fill="none" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }}
    }});

    ubList.forEach(u => {{
      const from = nodes[sid], to = nodes[u];
      const x1 = from.x + NW, y1 = from.y + NH / 2;
      const x2 = to.x, y2 = to.y + NH / 2;
      const mx = (x1 + x2) / 2;
      if (Math.abs(y1 - y2) < 1) {{
        svg += `<line x1="${{x1}}" y1="${{y1}}" x2="${{x2}}" y2="${{y2}}" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }} else {{
        svg += `<path d="M${{x1}},${{y1}} L${{mx}},${{y1}} L${{mx}},${{y2}} L${{x2}},${{y2}}" fill="none" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }}
    }});

    Object.entries(nodes).forEach(([nid, n]) => {{
      const info = storyInfo[nid] || {{ title: nid, category: "feature" }};
      let fill, stroke;
      if (n.role === "self") {{ fill = "#dbeafe"; stroke = "#2563eb"; }}
      else if (info.category === "infrastructure") {{ fill = "#f8fafc"; stroke = "#9ca3af"; }}
      else {{ fill = "#eff6ff"; stroke = "#3b82f6"; }}
      const label = nid.length > 14 ? nid.slice(0, 13) + "\u2026" : nid;
      const titleShort = info.title.length > 16 ? info.title.slice(0, 15) + "\u2026" : info.title;
      svg += `<rect x="${{n.x}}" y="${{n.y}}" width="${{NW}}" height="${{NH}}" rx="5" fill="${{fill}}" stroke="${{stroke}}" stroke-width="${{n.role === "self" ? 2 : 1.5}}"/>`;
      svg += `<text x="${{n.x + NW / 2}}" y="${{n.y + 14}}" text-anchor="middle" font-size="10" font-weight="600" fill="#1e293b">${{label}}</text>`;
      svg += `<text x="${{n.x + NW / 2}}" y="${{n.y + 27}}" text-anchor="middle" font-size="9" fill="#64748b">${{titleShort}}</text>`;
    }});

    svg += `</svg>`;
    popup.innerHTML = `<div style="overflow-x:auto;padding:12px">${{svg}}<div style="text-align:right;margin-top:6px"><button onclick="closePmDepGraph(this)" style="background:none;border:1px solid #e2e8f0;border-radius:4px;padding:2px 10px;cursor:pointer;font-size:11px;color:#64748b">close</button></div></div>`;
  }});
}})();

// Init
updateTeamTable();
updateAll();
renderPieChart();
</script>
</body></html>'''


# ── Dev Report (dev-report.html) ─────────────────────────────────────────────

def _render_task_chain_html(tasks: list) -> str:
    """Full dependency chain with predecessors marked ✓.

    When the chain exceeds 20 tasks, only the first 20 are shown
    with the rest inside a collapsible <details> element.
    """
    COLLAPSE_THRESHOLD = 20
    parts = []
    for t in tasks:
        if t["is_predecessor"]:
            parts.append(f'<span class="tp">{html.escape(t["name"])} ✓</span>')
        else:
            parts.append(
                f'<span class="to">{html.escape(t["name"])} '
                f'({t["duration_days"]}d)</span>')
    arrow = ' <span class="ar">→</span> '
    if len(parts) <= COLLAPSE_THRESHOLD:
        return arrow.join(parts)
    collapsed_count = len(parts) - COLLAPSE_THRESHOLD
    hidden = arrow.join(parts[:collapsed_count])
    visible = arrow.join(parts[collapsed_count:])
    return (f'<details class="chain-expand"><summary class="chain-more">'
            f'+{collapsed_count} earlier tasks</summary>'
            f'{hidden}{arrow}</details>{visible}')


def _render_impl_tasks_html(tasks: list) -> str:
    """Only this story's owned tasks (no predecessors), in implementation order."""
    owned = [t for t in tasks if not t["is_predecessor"]]
    if not owned:
        return ""
    parts = []
    for t in owned:
        parts.append(
            f'<span class="to">{html.escape(t["name"])} '
            f'({t["duration_days"]}d)</span>')
    return ' <span class="ar">→</span> '.join(parts)



def _render_standards_ref_html(story_id: str, standards: list[str]) -> str:
    """Render an HTML block listing referenced appfw sub-standards (paths only)."""
    if not standards:
        return ""
    rows = ""
    for i, path in enumerate(standards, 1):
        rows += f'<tr><td>{i}</td><td><code>{html.escape(path)}</code></td></tr>\n'

    return f'''<div class="sr">
<div class="sr-hdr"><b class="dtl">Standards Reference (build order)</b></div>
<table class="sr-tbl"><tr><th>#</th><th>Standard Path</th></tr>
{rows}</table></div>'''


def generate_dev_report(ctx: dict) -> str:
    s = ctx["summary"]
    cp_ids = ctx["cp_ids"]
    waves = ctx["waves"]
    vm = ctx["verifiable_map"]
    kn = ctx["key_nodes_by_story"]
    rec = ctx["recommended"]
    ss = ctx["story_standards"]
    fixed_team = ctx.get("fixed_team_size", 2)

    # Prepare JSON data for charts
    team_gantt_json = json.dumps(ctx["team_story_gantt"])
    team_makespan_json = _build_team_makespan_json(ctx)
    waves_data_json = _build_waves_data_json(ctx)
    feature_groups_json = _build_feature_groups_json(ctx)
    story_edges_json = json.dumps(ctx["story_edges"])
    release_story_map_json = json.dumps(ctx["release_story_map"])

    # Build per-release HTML containers
    release_map = ctx["release_story_map"]
    has_releases = len(release_map) > 1
    if has_releases:
        gantt_containers = ""
        alloc_containers = ""
        for rel in sorted(release_map.keys(), key=lambda k: int(k)):
            gantt_containers += f'<h3 style="margin:16px 0 4px;font-size:14px;color:#475569">Release {rel}</h3>\n<div id="ganttContainer-{rel}" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:0;margin:4px 0 12px;overflow-x:auto"><div id="ganttChart-{rel}"></div></div>\n'
            alloc_containers += f'<h3 style="margin:16px 0 4px;font-size:14px;color:#475569">Release {rel}</h3>\n<div id="allocContainer-{rel}" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:0;margin:4px 0 12px;overflow-x:auto"><div id="allocChart-{rel}"></div></div>\n<div id="allocLegend-{rel}" style="margin:4px 0 12px;font-size:11px;color:#64748b;display:flex;flex-wrap:wrap;gap:8px"></div>\n'
    else:
        gantt_containers = '<div id="ganttContainer" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:0;margin:12px 0;overflow-x:auto"><div id="ganttChart"></div></div>'
        alloc_containers = '<div id="allocContainer" style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:0;margin:12px 0;overflow-x:auto"><div id="allocChart"></div></div>\n<div id="allocLegend" style="margin:8px 0;font-size:11px;color:#64748b;display:flex;flex-wrap:wrap;gap:8px"></div>'

    # Critical path chain (no day values)
    cp_chain = " → ".join(
        f'[{x["id"]}: {html.escape(x["title"][:60])}]' for x in ctx["cp"])

    # Render story cards flat (JS regroups by wave when team size changes)
    testing_pct = s.get("testing_overhead_pct", 30)
    assumption_note = _duration_assumption_note(testing_pct)
    faq_html = _faq_section(testing_pct)
    cards = ""
    all_stories = {s["id"]: s for w in waves for s in w["stories"]}
    for sid, st in all_stories.items():
        is_crit = st["id"] in cp_ids
        cb = ' <span class="cb">★</span>' if is_crit else ""
        pri = st.get("priority", "normal")
        pri_badge = f' <span class="pri pri-{pri}">{pri}</span>' if pri != "normal" else ""
        cc = "infra" if st["category"] == "infrastructure" else "feat"
        verif = vm.get(st["id"], "Story testable end-to-end")
        deps = ", ".join(st["depends_on"]) if st["depends_on"] else "None (root story)"
        ub = ", ".join(st["unblocks"]) if st["unblocks"] else "None (leaf story)"
        kn_line = (
            f'<div class="sd"><b>Key nodes:</b> {", ".join(kn[st["id"]])}</div>'
            if st["id"] in kn else "")
        impl_line = _render_impl_tasks_html(st["dev_tasks_ordered"])
        chain_line = _render_task_chain_html(st["dev_tasks_ordered"])

        impl_section = (
            f'<div class="dt"><b class="dtl">Implementation tasks:</b> {impl_line}</div>'
            if impl_line else "")

        std_section = _render_standards_ref_html(st["id"], ss.get(st["id"], []))
        assumptions_section = _render_assumptions_dropdown(
            st.get("duration_assumptions", ""))

        # Build full story copy text
        copy_lines = []
        copy_lines.append(f'{st["id"]}: {st["title"]}')
        copy_lines.append(f'Effort: {st["effort_days"]}d')
        ac_items = [l.strip() for l in verif.split("\n") if l.strip()]
        if len(ac_items) <= 1:
            copy_lines.append(f'Acceptance Criteria: {verif}')
        else:
            copy_lines.append('Acceptance Criteria:')
            for ac in ac_items:
                copy_lines.append(f'  - {ac}')
        copy_lines.append(f'Depends on: {deps}')
        copy_lines.append(f'Unblocks: {ub}')
        if st["id"] in kn:
            copy_lines.append(f'Key nodes: {", ".join(kn[st["id"]])}')
        # Implementation tasks (owned only)
        owned_tasks = [t for t in st["dev_tasks_ordered"] if not t["is_predecessor"]]
        if owned_tasks:
            impl_parts = []
            for t in owned_tasks:
                impl_parts.append(f'{t["name"]} ({t["duration_days"]}d)')
            copy_lines.append(f'Implementation tasks: {" → ".join(impl_parts)}')
        # Full chain
        chain_parts = []
        for t in st["dev_tasks_ordered"]:
            if t["is_predecessor"]:
                chain_parts.append(f'{t["name"]} ✓')
            else:
                chain_parts.append(f'{t["name"]} ({t["duration_days"]}d)')
        copy_lines.append(f'Full chain: {" → ".join(chain_parts)}')
        # Standards reference
        story_stds = ss.get(st["id"], [])
        if story_stds:
            copy_lines.append("")
            copy_lines.append("Standards Reference (build order):")
            for ref_idx, path in enumerate(story_stds, 1):
                copy_lines.append(f"  {ref_idx}. {path}")
        # Duration assumptions
        if da:
            copy_lines.append(f"Duration assumptions: {da}")

        copy_text_escaped = html.escape(json.dumps("\n".join(copy_lines)), quote=True)
        btn_id = f"copyBtn_{st['id'].replace('-', '_')}"

        deps_json = html.escape(json.dumps(st["depends_on"]), quote=True)
        ub_json = html.escape(json.dumps(st["unblocks"]), quote=True)
        cards += f'''<div class="sc {cc}{" crit" if is_crit else ""}" id="story-{st["id"]}" data-story-id="{st["id"]}" data-deps="{deps_json}" data-unblocks="{ub_json}" style="display:none">
<div class="sh"><strong>{html.escape(st["id"])}</strong>: {html.escape(st["title"])}{cb}{pri_badge}<span class="sm"><button id="{btn_id}" class="cpb" data-copy="{copy_text_escaped}" onclick="copyStdRef(this)">Copy</button> effort: {st["effort_days"]}d</span></div>
<div class="sd"><b>Acceptance Criteria:</b> {_render_acceptance_criteria(verif)}</div>
<details class="sd-expand"><summary class="sd-toggle"><b>Depends on:</b> <span class="sd-count">{len(st["depends_on"])} {("story" if len(st["depends_on"]) == 1 else "stories")}</span></summary><div class="sd">{deps}</div></details>
<details class="sd-expand"><summary class="sd-toggle"><b>Unblocks:</b> <span class="sd-count">{len(st["unblocks"])} {("story" if len(st["unblocks"]) == 1 else "stories")}</span></summary><div class="sd">{ub}</div></details>
<div style="margin:4px 0"><span class="dep-link" onclick="toggleDepGraph(this)">show dependency graph</span></div>
{kn_line}
{impl_section}
<div class="dt"><b class="dtl">Full chain:</b> {chain_line}</div>
{std_section}
{assumptions_section}
<div class="dep-graph-popup"></div>
</div>\n'''

    return f'''<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>{html.escape(s["project"])} — Dev Report</title>
<style>
*{{margin:0;padding:0;box-sizing:border-box}}
body{{font-family:system-ui,-apple-system,sans-serif;background:#f5f5f5;color:#333;line-height:1.5;padding:24px}}
.c{{max-width:1100px;margin:0 auto}}
h1{{font-size:22px;margin-bottom:4px}}
.hdr{{color:#64748b;font-size:13px;margin-bottom:16px}}
.hdr b{{color:#1e293b}}
.wh{{background:#1e293b;color:#fff;padding:8px 16px;border-radius:6px 6px 0 0;margin-top:16px;font-weight:bold;font-size:14px}}
.sc{{background:#fff;border:1px solid #e5e7eb;border-top:none;padding:12px 16px}}
.sc.infra{{border-left:4px solid #9ca3af}}
.sc.feat{{border-left:4px solid #3b82f6}}
.sc.crit{{border-left:4px solid #ef4444}}
.sh{{font-size:14px;margin-bottom:6px}}
.sm{{float:right;color:#64748b;font-size:12px;font-weight:normal}}
.sd{{font-size:12px;color:#64748b;margin:2px 0}}
.cb{{background:#fee2e2;color:#dc2626;padding:1px 6px;border-radius:4px;font-size:11px;font-weight:bold}}
.pri{{padding:1px 6px;border-radius:4px;font-size:11px;font-weight:bold}}
.pri-critical{{background:#fee2e2;color:#dc2626}}
.pri-high{{background:#fff7ed;color:#c2410c}}
.pri-low{{background:#f1f5f9;color:#64748b}}
.stb{{background:#fef3c7;color:#92400e;padding:0 4px;border-radius:3px;font-size:10px}}
.dt{{margin-top:8px;font-size:11px;line-height:2;color:#475569}}
.tp{{color:#94a3b8}}
.to{{color:#1e40af;font-weight:500}}
.ar{{color:#cbd5e1}}
.chain-expand{{display:inline;font-size:11px}}
.chain-more{{display:inline;cursor:pointer;color:#3b82f6;font-weight:500;padding:1px 6px;border-radius:4px;background:#eff6ff}}
.chain-more:hover{{background:#dbeafe}}
.sd-expand{{font-size:12px;color:#64748b;margin:2px 0}}
.sd-toggle{{cursor:pointer;list-style:none;display:flex;align-items:center;gap:4px}}
.sd-toggle::-webkit-details-marker{{display:none}}
.sd-toggle::before{{content:"▸";font-size:10px;color:#94a3b8;transition:transform 0.15s}}
.sd-expand[open]>.sd-toggle::before{{transform:rotate(90deg)}}
.sd-count{{color:#94a3b8;font-weight:normal}}
.dtl{{color:#64748b;font-size:10px;text-transform:uppercase;letter-spacing:0.3px;font-weight:600}}
.cp{{background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;padding:12px 16px;margin:12px 0;font-family:monospace;font-size:12px;line-height:1.8;word-break:break-word;color:#9a3412}}
h2{{font-size:17px;margin:24px 0 10px;border-bottom:2px solid #3b82f6;padding-bottom:4px}}
table{{width:100%;border-collapse:collapse;margin:12px 0;background:#fff;border-radius:8px;overflow:hidden}}
th{{background:#1e293b;color:#fff;padding:8px 12px;text-align:left;font-size:13px}}
td{{padding:8px 12px;border-bottom:1px solid #e5e7eb;font-size:13px}}
tr:hover{{background:#f8fafc}}
.sr{{margin-top:10px;border-top:1px dashed #e5e7eb;padding-top:8px}}
.sr-hdr{{display:flex;align-items:center;justify-content:space-between;margin-bottom:6px}}
.sr-tbl{{font-size:11px}}
.sr-tbl th{{font-size:10px;padding:4px 8px}}
.sr-tbl td{{font-size:11px;padding:4px 8px}}
.sr-tbl code{{background:#f1f5f9;padding:1px 4px;border-radius:3px;font-size:10px;word-break:break-all}}
.cpb{{background:#1e293b;color:#fff;border:none;padding:3px 10px;border-radius:4px;font-size:10px;cursor:pointer;font-weight:600;letter-spacing:0.3px}}
.cpb:hover{{background:#334155}}
.cpb.copied{{background:#16a34a}}
.gantt-tooltip{{position:absolute;background:#1e293b;color:#fff;padding:6px 10px;border-radius:6px;font-size:11px;pointer-events:none;white-space:nowrap;z-index:10;opacity:0;transition:opacity 0.15s}}
.sc{{position:relative}}
.dep-graph-popup{{display:none;position:relative;z-index:20;background:#fff;border:1px solid #e5e7eb;border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,0.12);padding:12px;margin-top:8px;min-width:300px}}
.dep-graph-popup.open{{display:block}}
.dep-link{{color:#3b82f6;cursor:pointer;text-decoration:underline;text-decoration-style:dotted}}
.dep-link:hover{{color:#1d4ed8}}
.dep-close{{position:absolute;top:8px;right:10px;background:none;border:none;font-size:16px;cursor:pointer;color:#94a3b8;line-height:1}}
.dep-close:hover{{color:#1e293b}}
</style></head><body>
<div class="c">
<h1>{html.escape(s["project"])} — Dev Report</h1>
<div class="hdr">Stories: <b>{s["total_stories"]}</b> | Team size: <b>{fixed_team} dev{"s" if fixed_team > 1 else ""}</b></div>

<h2>Story Timeline (Gantt)</h2>
{gantt_containers}
<div id="ganttTooltip" class="gantt-tooltip"></div>

<h2>Developer Allocation</h2>
<p style="color:#64748b;font-size:12px;margin-bottom:0">Shows which developer works on which stories.</p>
{alloc_containers}

<h2>Implementation Order</h2>
<p style="color:#64748b;font-size:12px;margin-bottom:8px">Stories in the order developers pick them up.</p>
{assumption_note}
<div id="implOrderContainer">
{cards}
</div>

{faq_html}

<div style="margin-top:32px;padding:12px 16px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:6px;font-size:11px;color:#94a3b8;text-align:center">
This report was generated with AI assistance. Estimates are indicative and based on AI-assisted development assumptions. Actual timelines may vary based on team velocity, scope changes, and external dependencies. Always validate with your team before committing to delivery dates.
</div>

</div>

<script>
const teamGantt = {team_gantt_json};
const wavesData = {waves_data_json};
const teamMakespan = {team_makespan_json};
const featureGroups = {feature_groups_json};
const storyEdges = {story_edges_json};
const releaseStoryMap = {release_story_map_json};
const hasReleases = Object.keys(releaseStoryMap).length > 1;
const fixedDevs = {fixed_team};

function copyStdRef(btn) {{
  const text = JSON.parse(btn.getAttribute("data-copy"));
  navigator.clipboard.writeText(text).then(() => {{
    btn.textContent = "Copied!";
    btn.classList.add("copied");
    setTimeout(() => {{ btn.textContent = "Copy"; btn.classList.remove("copied"); }}, 2000);
  }});
}}

function toggleDepGraph(el) {{
  const card = el.closest(".sc");
  const popup = card.querySelector(".dep-graph-popup");
  if (popup) popup.classList.toggle("open");
}}

function closeDepGraph(btn) {{
  btn.closest(".dep-graph-popup").classList.remove("open");
}}

function reorderCards(devs) {{
  const gantt = teamGantt[String(devs)];
  if (!gantt) return;
  const container = document.getElementById("implOrderContainer");
  container.querySelectorAll("[data-story-id]").forEach(el => el.style.display = "none");

  // Sort stories by simulation start time (implementation order)
  const sorted = Object.keys(gantt)
    .map(sid => ({{ id: sid, start: gantt[sid].start, end: gantt[sid].end }}))
    .sort((a, b) => a.start - b.start || a.end - b.end);

  sorted.forEach(s => {{
    const card = container.querySelector('[data-story-id="' + s.id + '"]');
    if (card) {{
      card.style.display = "";
      container.appendChild(card);
    }}
  }});
}}

function updateAll(devs) {{
  if (hasReleases) {{
    for (const [rel, sids] of Object.entries(releaseStoryMap)) {{
      const filter = new Set(sids);
      renderGantt(devs, filter, "ganttChart-" + rel);
      renderAlloc(devs, filter, "allocChart-" + rel, "allocLegend-" + rel);
    }}
  }} else {{
    renderGantt(devs);
    renderAlloc(devs);
  }}
  reorderCards(devs);
}}

// ── Mini-DAG on hover for each story card ───────────────────────────
(function() {{
  // Build story title lookup from waves data
  const storyInfo = {{}};
  const defaultWaves = wavesData;
  defaultWaves.forEach(w => w.stories.forEach(s => {{
    storyInfo[s.id] = {{ title: s.title, category: s.category || "feature" }};
  }}));

  document.querySelectorAll(".sc[data-story-id]").forEach(card => {{
    const sid = card.getAttribute("data-story-id");
    const depsList = JSON.parse(card.getAttribute("data-deps") || "[]");
    const ubList = JSON.parse(card.getAttribute("data-unblocks") || "[]");
    if (depsList.length === 0 && ubList.length === 0) return;

    const popup = card.querySelector(".dep-graph-popup");
    if (!popup) return;

    // Layout: predecessors (col 0) → current (col 1) → successors (col 2)
    const NW = 120, NH = 36, CGAP = 60, RGAP = 10, PAD = 16;
    const hasDeps = depsList.length > 0;
    const hasUb = ubList.length > 0;
    const cols = (hasDeps ? 1 : 0) + 1 + (hasUb ? 1 : 0);
    const maxRows = Math.max(1, depsList.length, ubList.length);
    const totalW = PAD * 2 + cols * NW + (cols - 1) * CGAP;
    const totalH = PAD * 2 + maxRows * NH + (maxRows - 1) * RGAP;

    // Assign positions
    const nodes = {{}};
    let colIdx = 0;

    // Predecessors
    if (hasDeps) {{
      const colH = depsList.length * NH + (depsList.length - 1) * RGAP;
      const startY = PAD + (totalH - PAD * 2 - colH) / 2;
      depsList.forEach((d, i) => {{
        nodes[d] = {{ x: PAD + colIdx * (NW + CGAP), y: startY + i * (NH + RGAP), role: "dep" }};
      }});
      colIdx++;
    }}

    // Current story
    const centerY = PAD + (totalH - PAD * 2 - NH) / 2;
    nodes[sid] = {{ x: PAD + colIdx * (NW + CGAP), y: centerY, role: "self" }};
    colIdx++;

    // Successors
    if (hasUb) {{
      const colH = ubList.length * NH + (ubList.length - 1) * RGAP;
      const startY = PAD + (totalH - PAD * 2 - colH) / 2;
      ubList.forEach((u, i) => {{
        nodes[u] = {{ x: PAD + colIdx * (NW + CGAP), y: startY + i * (NH + RGAP), role: "succ" }};
      }});
    }}

    let svg = `<svg viewBox="0 0 ${{totalW}} ${{totalH}}" style="width:${{totalW}}px;height:${{totalH}}px;font-family:system-ui,sans-serif">`;
    svg += `<defs><marker id="ah-${{sid}}" viewBox="0 0 10 7" refX="10" refY="3.5" markerWidth="8" markerHeight="6" orient="auto"><polygon points="0 0, 10 3.5, 0 7" fill="#94a3b8"/></marker></defs>`;

    // Edges: deps → self
    depsList.forEach(d => {{
      const from = nodes[d], to = nodes[sid];
      const x1 = from.x + NW, y1 = from.y + NH / 2;
      const x2 = to.x, y2 = to.y + NH / 2;
      const mx = (x1 + x2) / 2;
      if (Math.abs(y1 - y2) < 1) {{
        svg += `<line x1="${{x1}}" y1="${{y1}}" x2="${{x2}}" y2="${{y2}}" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }} else {{
        svg += `<path d="M${{x1}},${{y1}} L${{mx}},${{y1}} L${{mx}},${{y2}} L${{x2}},${{y2}}" fill="none" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }}
    }});

    // Edges: self → successors
    ubList.forEach(u => {{
      const from = nodes[sid], to = nodes[u];
      const x1 = from.x + NW, y1 = from.y + NH / 2;
      const x2 = to.x, y2 = to.y + NH / 2;
      const mx = (x1 + x2) / 2;
      if (Math.abs(y1 - y2) < 1) {{
        svg += `<line x1="${{x1}}" y1="${{y1}}" x2="${{x2}}" y2="${{y2}}" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }} else {{
        svg += `<path d="M${{x1}},${{y1}} L${{mx}},${{y1}} L${{mx}},${{y2}} L${{x2}},${{y2}}" fill="none" stroke="#cbd5e1" stroke-width="1.5" marker-end="url(#ah-${{sid}})"/>`;
      }}
    }});

    // Draw nodes
    Object.entries(nodes).forEach(([nid, n]) => {{
      const info = storyInfo[nid] || {{ title: nid, category: "feature" }};
      let fill, stroke;
      if (n.role === "self") {{ fill = "#dbeafe"; stroke = "#2563eb"; }}
      else if (info.category === "infrastructure") {{ fill = "#f8fafc"; stroke = "#9ca3af"; }}
      else {{ fill = "#eff6ff"; stroke = "#3b82f6"; }}
      const label = nid.length > 14 ? nid.slice(0, 13) + "\u2026" : nid;
      const titleShort = info.title.length > 16 ? info.title.slice(0, 15) + "\u2026" : info.title;
      svg += `<rect x="${{n.x}}" y="${{n.y}}" width="${{NW}}" height="${{NH}}" rx="5" fill="${{fill}}" stroke="${{stroke}}" stroke-width="${{n.role === "self" ? 2 : 1.5}}"/>`;
      svg += `<text x="${{n.x + NW/2}}" y="${{n.y + 14}}" text-anchor="middle" fill="#1e293b" font-size="10" font-weight="bold">${{label}}</text>`;
      svg += `<text x="${{n.x + NW/2}}" y="${{n.y + 27}}" text-anchor="middle" fill="#64748b" font-size="8">${{titleShort}}</text>`;
    }});

    svg += `</svg>`;
    popup.innerHTML = `<button class="dep-close" onclick="closeDepGraph(this)">&times;</button><div style="font-size:10px;color:#94a3b8;margin-bottom:4px;text-transform:uppercase;letter-spacing:0.5px;font-weight:600">Dependencies</div>` + svg;
  }});
}})();

{_shared_js_gantt_utils()}

// ── Developer Allocation Chart ──────────────────────────────────────
// Assign a stable color to each story ID
const ALLOC_COLORS = ["#3b82f6","#f59e0b","#10b981","#8b5cf6","#ef4444","#ec4899","#06b6d4","#f97316","#6366f1","#14b8a6","#e11d48","#a855f7","#0ea5e9","#84cc16","#fb923c","#64748b"];
const storyColorMap = {{}};
(function() {{
  const allIds = [];
  const defaultWaves = wavesData;
  defaultWaves.forEach(w => w.stories.forEach(s => {{ if (!allIds.includes(s.id)) allIds.push(s.id); }}));
  allIds.forEach((id, i) => {{ storyColorMap[id] = ALLOC_COLORS[i % ALLOC_COLORS.length]; }});
}})();

function renderAlloc(devs, storyFilter, targetId, legendId) {{
  targetId = targetId || "allocChart";
  legendId = legendId || "allocLegend";
  const fullGantt = teamGantt[String(devs)];
  if (!fullGantt) return;

  // Filter gantt data if storyFilter provided
  const gantt = storyFilter ? {{}} : fullGantt;
  if (storyFilter) {{
    for (const [sid, data] of Object.entries(fullGantt)) {{
      if (storyFilter.has(sid)) gantt[sid] = data;
    }}
  }}
  if (Object.keys(gantt).length === 0) return;
  const maxEnd = Math.max(...Object.values(gantt).map(g => g.end)) || 1;
  // For per-release charts, shift x-axis so bars start at 0 but labels show actual days
  const minStart = storyFilter ? Math.min(...Object.values(gantt).map(g => g.start)) : 0;
  const span = maxEnd - minStart || 1;
  const numDevs = parseInt(devs);

  // Build story title lookup from waves data
  const storyTitleMap = {{}};
  wavesData.forEach(w => w.stories.forEach(s => {{ storyTitleMap[s.id] = s.title; }}));

  // Use actual dev assignments from simulation (gantt includes dev field)
  const devStories = Array.from({{ length: numDevs }}, () => []);
  Object.keys(gantt).forEach(sid => {{
    const g = gantt[sid];
    const dev = g.dev != null ? g.dev : 0;
    if (dev < numDevs) {{
      devStories[dev].push({{ id: sid, title: storyTitleMap[sid] || sid, start: g.start, end: g.end }});
    }}
  }});
  // Sort each dev's stories by start time
  devStories.forEach(arr => arr.sort((a, b) => a.start - b.start || a.end - b.end));

  const ROW_H = 36, PAD_L = 80, PAD_R = 30, BAR_H = 24, GAP = 1;
  const minDayWidth = 160;
  const chartW = Math.max(900, PAD_L + PAD_R + Math.ceil(span) * minDayWidth);
  const barArea = chartW - PAD_L - PAD_R;
  const totalH = numDevs * ROW_H + 30;
  const x = d => PAD_L + ((d - minStart) / span) * barArea;

  let svg = `<svg viewBox="0 0 ${{chartW}} ${{totalH}}" style="width:${{chartW}}px;min-width:${{chartW}}px;font-family:system-ui,sans-serif">`;

  // Day axis gridlines
  const niceSteps = [0.5, 1, 2, 5, 10, 15, 20, 25, 50];
  const step = niceSteps.find(s => span / s <= 10) || Math.ceil(span / 10);
  for (let tick = 0; tick <= span; tick += step) {{
    const rounded = Math.round(tick * 10) / 10;
    const xp = x(rounded + minStart);
    const actualDay = Math.round((rounded + minStart) * 10) / 10;
    svg += `<line x1="${{xp}}" y1="0" x2="${{xp}}" y2="${{totalH - 20}}" stroke="#f1f5f9" stroke-width="1"/>`;
    svg += `<text x="${{xp}}" y="${{totalH - 6}}" text-anchor="middle" fill="#94a3b8" font-size="10">Day ${{actualDay}}</text>`;
  }}

  // Row backgrounds and developer labels
  for (let i = 0; i < numDevs; i++) {{
    const y0 = i * ROW_H;
    const yc = y0 + ROW_H / 2;
    if (i % 2 === 1) {{
      svg += `<rect x="0" y="${{y0}}" width="${{chartW}}" height="${{ROW_H}}" fill="#fafafa"/>`;
    }}
    svg += `<text x="${{PAD_L - 10}}" y="${{yc + 4}}" text-anchor="end" fill="#475569" font-size="12" font-weight="600">Dev ${{i + 1}}</text>`;
  }}

  // Story blocks per developer
  for (let i = 0; i < numDevs; i++) {{
    const yc = i * ROW_H + ROW_H / 2;

    devStories[i].forEach(s => {{
      const bx = x(s.start);
      const bw = Math.max(x(s.end) - bx, 2);
      const by = yc - BAR_H / 2;
      const color = storyColorMap[s.id] || "#94a3b8";
      const effort = Math.round((s.end - s.start) * 10) / 10;
      const safeTitle = (s.id + ": " + s.title).replace(/'/g, "");
      const tipText = safeTitle + " | Day " + s.start.toFixed(1) + "–" + s.end.toFixed(1) + " (" + effort + "d)";

      svg += `<rect x="${{bx + GAP}}" y="${{by}}" width="${{Math.max(bw - GAP * 2, 1)}}" height="${{BAR_H}}" rx="3" fill="${{color}}" opacity="0.85" style="cursor:pointer" onclick="scrollToStory('${{s.id}}')" onmousemove="showGanttTip(event, '${{tipText}}')" onmouseout="hideGanttTip()"/>`;
      if (bw > 40) {{
        const label = s.id.length > Math.floor(bw / 7) ? s.id.slice(0, Math.floor(bw / 7) - 1) + "…" : s.id;
        svg += `<text x="${{bx + bw / 2}}" y="${{yc + 4}}" text-anchor="middle" fill="#fff" font-size="9" font-weight="600" pointer-events="none">${{label}}</text>`;
      }}
    }});
  }}

  svg += `</svg>`;
  document.getElementById(targetId).innerHTML = svg;

  // Legend: show story colors in gantt chart order (wave order)
  const seenStories = new Set();
  let legendHtml = "";
  const legendWaves = wavesData;
  legendWaves.forEach(w => {{
    w.stories.forEach(s => {{
      if (seenStories.has(s.id)) return;
      if (storyFilter && !storyFilter.has(s.id)) return;
      seenStories.add(s.id);
      const color = storyColorMap[s.id] || "#94a3b8";
      const title = s.title.length > 40 ? s.title.slice(0, 40) + "…" : s.title;
      legendHtml += `<span style="display:inline-flex;align-items:center;gap:4px;cursor:pointer" onclick="scrollToStory('${{s.id}}')"><span style="display:inline-block;width:10px;height:10px;background:${{color}};border-radius:2px"></span>${{s.id}}: ${{title}}</span>`;
    }});
  }});
  document.getElementById(legendId).innerHTML = legendHtml;
}}

// Render for the fixed team size
updateAll(fixedDevs);
</script>
</body></html>'''


# ── Delivery Plan (dev-report.md) ─────────────────────────────────────────

def _render_task_chain_md(tasks: list) -> str:
    """Full chain with predecessors marked ✓.

    When the chain exceeds 20 tasks, only the first 20 are shown
    with the rest inside a collapsible <details> block.
    """
    COLLAPSE_THRESHOLD = 20
    parts = []
    for t in tasks:
        if t["is_predecessor"]:
            parts.append(f'{t["name"]} ✓')
        else:
            parts.append(f'{t["name"]} ({t["duration_days"]}d)')
    if len(parts) <= COLLAPSE_THRESHOLD:
        return " → ".join(parts)
    collapsed_count = len(parts) - COLLAPSE_THRESHOLD
    hidden = " → ".join(parts[:collapsed_count])
    visible = " → ".join(parts[collapsed_count:])
    return f'<details><summary>+{collapsed_count} earlier tasks</summary>\n  {hidden} →\n  </details>\n  {visible}'


def _render_impl_tasks_md(tasks: list) -> str:
    """Only this story's owned tasks, in implementation order."""
    owned = [t for t in tasks if not t["is_predecessor"]]
    if not owned:
        return ""
    parts = []
    for t in owned:
        parts.append(f'{t["name"]} ({t["duration_days"]}d)')
    return " → ".join(parts)


def _compute_bottlenecks(ctx: dict) -> list[str]:
    """Compute 3-5 bottleneck bullets dynamically from the data."""
    bullets = []
    s = ctx["summary"]
    rec = ctx["recommended"]

    # 1. Story that unblocks the most
    mu = ctx["max_unblocks"]
    if mu:
        bullets.append(
            f'**{mu["id"]} ({mu["title"][:50]})** is the primary bottleneck — '
            f'unblocks {ctx["max_unblocks_count"]} downstream stories. '
            f'Any delay here cascades through the project. Schedule a buffer after this milestone.')

    # 2. Critical path description
    cp = ctx["cp"]
    if len(cp) > 2:
        chain_str = " → ".join(f'{c["id"]}: {c["title"][:40]}' for c in cp)
        bullets.append(
            f'**Critical path ({chain_str})** spans {s["project_duration_days"]}d '
            f'and concentrates {ctx["risk_pct"]}% of total effort. '
            f'Assign senior developers to this track.')

    # 3. Story with longest relative wait
    mw = ctx["max_wait"]
    if mw and mw != mu:
        bullets.append(
            f'**{mw["id"]}: {mw["title"][:60]}** has the longest relative wait — '
            f'testable at Day {mw["testable_day"]} despite only {mw["effort_days"]}d '
            f'of own effort (blocked by deep dependency chains).')

    # 4. Recommended team size
    ts = ctx["team_sizes"]
    one_dev = next((t["dur"] for t in ts if t["devs"] == 1), None)
    two_dev = next((t["dur"] for t in ts if t["devs"] == 2), None)
    three_dev = next((t["dur"] for t in ts if t["devs"] == 3), None)
    if one_dev:
        bullets.append(
            f'**Max useful developers: {rec}.** '
            f'With 1 dev: {one_dev}d'
            f'{f", 2 devs: {two_dev}d" if two_dev else ""}'
            f'{f", 3 devs: {three_dev}d" if three_dev else ""}. '
            f'Diminishing returns beyond {rec}.')

    # 5. Width-constrained waves
    ww = ctx["wide_waves"]
    if ww:
        wave_str = ", ".join(str(w) for w in ww)
        max_par = max(
            len(w["stories"]) for w in ctx["waves"] if w["wave"] in ww)
        bullets.append(
            f'**Waves {wave_str} are width-constrained** — '
            f'up to {max_par} parallel stories but limited by available developers. '
            f'This is where additional developers help most.')

    return bullets


def _escape_pipe(text: str) -> str:
    """Escape pipe characters for markdown table cells."""
    return text.replace("|", "\\|")


def generate_delivery_plan(ctx: dict) -> str:
    s = ctx["summary"]
    rec = ctx["recommended"]
    cp_ids = ctx["cp_ids"]
    vm = ctx["verifiable_map"]

    lines = []
    lines.append(f'# {s["project"]} — Delivery Plan\n')
    # Duration for recommended (knee) team size
    rec_dur = next((t["dur"] for t in ctx["team_sizes"] if t["devs"] == rec), s["project_duration_days"])

    lines.append(
        f'**Estimated completion:** {rec_dur} days '
        f'({rec} devs) | '
        f'**Sequential (1 dev):** {s["sequential_duration_days"]} days | '
        f'**Parallelism gain:** {s["parallelism_speedup"]}x | '
        f'**Total stories:** {s["total_stories"]}\n')

    # Story delivery schedule with section numbers
    lines.append("## Story Delivery Schedule\n")
    ss = ctx["story_standards"]

    for wave in ctx["waves"]:
        wave_num = wave["wave"]
        day0 = min(st["testable_day"] for st in wave["stories"])
        lines.append(f'### Wave {wave_num} — Day {day0}\n')

        for story_idx, st in enumerate(wave["stories"], 1):
            is_crit = st["id"] in cp_ids
            star = " ★" if is_crit else ""
            pri = st.get("priority", "normal")
            pri_tag = f" [{pri.upper()}]" if pri != "normal" else ""
            deps = ", ".join(st["depends_on"]) if st["depends_on"] else "—"
            verif = vm.get(st["id"], "Story testable end-to-end")
            impl_chain = _render_impl_tasks_md(st["dev_tasks_ordered"])
            full_chain = _render_task_chain_md(st["dev_tasks_ordered"])

            lines.append(f'#### {wave_num}.{story_idx} {st["id"]}: {st["title"]}{star}{pri_tag}\n')
            lines.append(f'- **Effort:** {st["effort_days"]}d | **Testable:** Day {st["testable_day"]} | **Deps:** {deps}')
            ac_lines = [l.strip() for l in verif.split("\n") if l.strip()]
            if len(ac_lines) <= 1:
                lines.append(f'- **Acceptance Criteria:** {verif}')
            else:
                lines.append('- **Acceptance Criteria:**')
                for ac in ac_lines:
                    lines.append(f'  - {ac}')
            if impl_chain:
                lines.append(f'- **Implementation tasks:** {impl_chain}')
            lines.append(f'- **Full chain:** {full_chain}')

            # Standards reference
            story_stds = ss.get(st["id"], [])
            if story_stds:
                lines.append("")
                lines.append(f'**Standards Reference (build order):**\n')
                for ref_idx, path in enumerate(story_stds, 1):
                    lines.append(f'{ref_idx}. `{path}`')

            # Duration assumptions
            md_da = st.get("duration_assumptions", "")
            if md_da:
                md_points = [p.strip() for p in re.split(r'[,;\n]+', md_da) if p.strip()]
                lines.append('- **Duration assumptions:**')
                for pt in md_points:
                    lines.append(f'  - {pt}')

            lines.append("")

    # Bottlenecks
    lines.append("---\n")
    lines.append("## Bottlenecks & Recommendations\n")
    for bullet in _compute_bottlenecks(ctx):
        lines.append(f"- {bullet}")

    # AI disclaimer
    lines.append("\n---\n")
    lines.append("*This report was generated with AI assistance. Estimates are indicative and based on AI-assisted development assumptions. Actual timelines may vary based on team velocity, scope changes, and external dependencies. Always validate with your team before committing to delivery dates.*")

    return "\n".join(lines)


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="Generate role-specific reports from processor output.")
    parser.add_argument("processor_json", help="Path to processor output JSON")
    parser.add_argument("output_dir", help="Output directory")
    parser.add_argument("--role", required=True, choices=["pm", "dev"],
                        help="Role: pm (PM report only) or dev (dev reports only)")
    parser.add_argument("--team-size", type=int, default=None,
                        help="Team size for dev role (required for --role dev)")
    args = parser.parse_args()

    if args.role == "dev" and args.team_size is None:
        parser.error("--team-size is required when --role is dev")

    reports_dir = Path(args.output_dir)
    reports_dir.mkdir(parents=True, exist_ok=True)

    ctx = load_inputs(args.processor_json)

    if args.role == "pm":
        pm = generate_pm_report(ctx)
        (reports_dir / "pm-report.html").write_text(pm)
        print(f"Written: {reports_dir}/pm-report.html")
        print(f"\nDone. 1 file generated in {reports_dir}/")
    else:
        ctx["fixed_team_size"] = args.team_size
        dev = generate_dev_report(ctx)
        plan = generate_delivery_plan(ctx)
        (reports_dir / "dev-report.html").write_text(dev)
        print(f"Written: {reports_dir}/dev-report.html")
        (reports_dir / "dev-report.md").write_text(plan)
        print(f"Written: {reports_dir}/dev-report.md")
        print(f"\nDone. 2 files generated in {reports_dir}/")


if __name__ == "__main__":
    main()
