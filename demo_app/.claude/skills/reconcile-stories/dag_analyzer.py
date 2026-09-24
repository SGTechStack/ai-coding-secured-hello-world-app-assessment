#!/usr/bin/env python3
"""
dag-processor.py — Run story-level analysis on the project DAG
=========================================================================
Input:  A single project DAG JSON file (built by merge-batch.py), containing:
        { "project": str, "feature_groups": [...], "nodes": [...], "edges": [...],
          "user_stories": [...] }

Output: Single JSON with everything the report generator needs:
  - dag: the DAG (feature_groups, user_stories, nodes, edges)
  - summary: project stats, duration, max useful developers
  - story_dag: topo order, dependencies, critical path
  - delivery_waves, team_estimates, node_details, gantt data

Usage:
  python3 dag-processor.py project-dag.json
  echo '{"nodes":...}' | python3 dag-processor.py                      # stdin
"""

from __future__ import annotations

import json
import sys
from collections import defaultdict, deque
from typing import Optional

# Priority enum → numeric sort value (lower = scheduled first)
PRIORITY_RANK = {"critical": 0, "high": 1, "normal": 2, "low": 3}


# ── Node-level helpers ───────────────────────────────────────────────────────

def _build_graph(nodes: dict, edges_list: list) -> tuple:
    """Return (adj, radj, in_degree) or raise ValueError on unknown node refs."""
    adj = defaultdict(list)    # node -> [successors]
    radj = defaultdict(list)   # node -> [predecessors]
    in_degree = {nid: 0 for nid in nodes}

    for edge in edges_list:
        src, dst = edge["from"], edge["to"]
        if src not in nodes:
            raise ValueError(f"Edge references unknown node '{src}'")
        if dst not in nodes:
            raise ValueError(f"Edge references unknown node '{dst}'")
        adj[src].append(dst)
        radj[dst].append(src)
        in_degree[dst] += 1

    return adj, radj, in_degree


def _kahns_topo_sort(node_ids: set, adj: dict, in_degree: dict) -> Optional[list]:
    """
    Returns topological order as a list of IDs, or None if a cycle exists.
    Deterministic: ties broken by ID (alphabetical).
    """
    temp_in = dict(in_degree)
    queue = deque(sorted(nid for nid in node_ids if temp_in[nid] == 0))
    order = []

    while queue:
        nid = queue.popleft()
        order.append(nid)
        for succ in sorted(adj.get(nid, [])):
            temp_in[succ] -= 1
            if temp_in[succ] == 0:
                queue.append(succ)

    return order if len(order) == len(node_ids) else None


def _cpm(nodes: dict, adj: dict, radj: dict, topo: list, duration: dict) -> dict:
    """
    Forward pass  -> ES, EF
    Backward pass -> LF, LS
    Float         = LS - ES  (0 => on critical path)
    """
    es, ef = {}, {}
    for nid in topo:
        es[nid] = max((ef[p] for p in radj[nid]), default=0)
        ef[nid] = es[nid] + duration[nid]

    project_duration = max(ef.values()) if ef else 0

    lf, ls = {}, {}
    for nid in reversed(topo):
        lf[nid] = min((ls[s] for s in adj.get(nid, [])), default=project_duration)
        ls[nid] = lf[nid] - duration[nid]

    float_val = {nid: ls[nid] - es[nid] for nid in nodes}

    return {
        "es": es, "ef": ef,
        "ls": ls, "lf": lf,
        "float": float_val,
        "project_duration": project_duration,
        "critical_ids": {nid for nid in nodes if abs(float_val[nid]) < 1e-9},
    }


# ── Schedule simulation (node-level, for team estimates) ─────────────────────

def _simulate(nodes: dict, adj: dict, in_degree: dict, duration: dict,
              num_devs: int, float_vals: dict,
              node_priority: dict | None = None) -> tuple[float, dict, list]:
    """
    Discrete-event simulation: greedy assignment (priority tier first,
    then minimum float, then descending duration).  Returns (makespan,
    node_finish_times, assignments) where assignments is a list of
    {node, dev, start, end}.
    """
    remaining_in = dict(in_degree)
    completed: set = set()
    in_progress: dict = {}   # nid -> (finish_time, dev_index)
    finish_times: dict = {}  # nid -> finish_time
    assignments: list = []   # [{node, dev, start, end}, ...]
    _np = node_priority or {}

    # Track which devs are busy; use a set of free dev indices
    free_devs: list = list(range(num_devs))

    def priority(nid):
        return (_np.get(nid, 2), float_vals.get(nid, 0), -duration[nid])

    available = sorted(
        [nid for nid in nodes if remaining_in[nid] == 0],
        key=priority,
    )
    current_time = 0.0

    while len(completed) < len(nodes):
        while available and free_devs:
            task = available.pop(0)
            dev = free_devs.pop(0)
            finish_time = current_time + duration[task]
            in_progress[task] = (finish_time, dev)
            assignments.append({
                "node": task,
                "dev": dev,
                "start": current_time,
                "end": finish_time,
            })

        if not in_progress:
            break

        next_time = min(ft for ft, _ in in_progress.values())
        current_time = next_time

        newly_done = [n for n, (t, _) in in_progress.items() if t == current_time]
        for task in newly_done:
            _, dev = in_progress[task]
            del in_progress[task]
            completed.add(task)
            finish_times[task] = current_time
            free_devs.append(dev)
            free_devs.sort()
            for succ in adj[task]:
                remaining_in[succ] -= 1
                if remaining_in[succ] == 0:
                    available.append(succ)
                    available.sort(key=priority)

    return current_time, finish_times, assignments


# ── Story-level schedule simulation ──────────────────────────────────────────

def _simulate_stories(story_ids: list, story_adj: dict, story_radj: dict,
                      story_in_degree: dict, story_effort: dict,
                      num_devs: int, story_float: dict,
                      story_priority: dict | None = None) -> dict:
    """
    Greedy story-level simulation. Each story is scheduled as a single unit
    with its effort duration. Returns {story_id: {start, end}}.
    """
    remaining_in = dict(story_in_degree)
    completed: set = set()
    in_progress: dict = {}   # sid -> (finish_time, dev_index)
    result: dict = {}
    free_devs: list = list(range(num_devs))
    _sp = story_priority or {}

    def priority(sid):
        return (_sp.get(sid, 2), story_float.get(sid, 0), -story_effort.get(sid, 0))

    available = sorted(
        [sid for sid in story_ids if remaining_in[sid] == 0],
        key=priority,
    )
    current_time = 0.0

    while len(completed) < len(story_ids):
        while available and free_devs:
            sid = available.pop(0)
            dev = free_devs.pop(0)
            dur = story_effort.get(sid, 0)
            finish_time = current_time + dur
            in_progress[sid] = (finish_time, dev)
            result[sid] = {"start": round(current_time, 4), "end": round(finish_time, 4)}

        if not in_progress:
            break

        next_time = min(ft for ft, _ in in_progress.values())
        current_time = next_time

        newly_done = [s for s, (t, _) in in_progress.items() if t == current_time]
        for sid in newly_done:
            _, dev = in_progress[sid]
            del in_progress[sid]
            completed.add(sid)
            free_devs.append(dev)
            free_devs.sort()
            for succ in story_adj.get(sid, []):
                remaining_in[succ] -= 1
                if remaining_in[succ] == 0:
                    available.append(succ)
                    available.sort(key=priority)

    return result


# ── Derive delivery waves from simulation ────────────────────────────────────

def _derive_waves_from_simulation(sim_result: dict) -> list[list[str]]:
    """
    Derive delivery waves from greedy simulation results.

    A wave contains stories that overlap in time. A new wave starts when
    all previous stories have finished before any new ones begin.
    Stories within each wave are ordered by start time, then end time.
    """
    if not sim_result:
        return []

    sorted_stories = sorted(
        sim_result.items(), key=lambda x: (x[1]["start"], x[1]["end"]))

    waves: list[list[str]] = []
    current_wave: list[str] = []
    wave_end = -1.0

    for sid, times in sorted_stories:
        if current_wave and times["start"] >= wave_end:
            waves.append(current_wave)
            current_wave = []
            wave_end = -1.0

        current_wave.append(sid)
        wave_end = max(wave_end, times["end"])

    if current_wave:
        waves.append(current_wave)

    return waves


# ── Parallel phases (node-level) ─────────────────────────────────────────────

def _parallel_phases(topo: list, radj: dict) -> dict[int, list]:
    level = {}
    for nid in topo:
        level[nid] = max((level[p] + 1 for p in radj[nid]), default=0)
    phases: dict[int, list] = defaultdict(list)
    for nid in topo:
        phases[level[nid]].append(nid)
    return dict(phases)


# ── Transitive predecessors (for story dependency derivation) ────────────────

def _transitive_predecessors(node_ids: set, radj: dict) -> dict[str, set]:
    """For each node, compute the full set of transitive predecessors via BFS."""
    cache: dict[str, set] = {}

    def _get(nid: str) -> set:
        if nid in cache:
            return cache[nid]
        visited = set()
        queue = deque(radj.get(nid, []))
        while queue:
            pred = queue.popleft()
            if pred not in visited:
                visited.add(pred)
                queue.extend(p for p in radj.get(pred, []) if p not in visited)
        cache[nid] = visited
        return visited

    for nid in node_ids:
        _get(nid)
    return cache


# ── Story-level DAG construction ─────────────────────────────────────────────

def _build_story_dag(stories: dict, node_to_story: dict,
                     trans_preds: dict) -> tuple:
    """
    Derive story-level edges from transitive node dependencies.

    Story B depends on Story A if any of B's owned_nodes has a transitive
    predecessor that belongs to A's owned_nodes.

    Returns (story_adj, story_radj, story_in_degree, story_edges).
    """
    story_adj = defaultdict(set)
    story_radj = defaultdict(set)
    story_in_degree = {sid: 0 for sid in stories}

    for sid_b, story_b in stories.items():
        for node_id in story_b["owned_nodes"]:
            for pred_node in trans_preds.get(node_id, set()):
                sid_a = node_to_story.get(pred_node)
                if sid_a and sid_a != sid_b:
                    if sid_b not in story_adj[sid_a]:
                        story_adj[sid_a].add(sid_b)
                        story_radj[sid_b].add(sid_a)
                        story_in_degree[sid_b] += 1

    # Convert sets to sorted lists for determinism
    story_adj_list = defaultdict(list)
    story_radj_list = defaultdict(list)
    for sid in stories:
        story_adj_list[sid] = sorted(story_adj[sid])
        story_radj_list[sid] = sorted(story_radj[sid])

    story_edges = []
    for sid_a in sorted(stories):
        for sid_b in story_adj_list[sid_a]:
            story_edges.append({"from": sid_a, "to": sid_b})

    return story_adj_list, story_radj_list, story_in_degree, story_edges


def _story_cpm(stories: dict, story_adj: dict, story_radj: dict,
               story_topo: list, story_durations: dict) -> dict:
    """CPM on the story-level DAG. Same algorithm as node-level."""
    es, ef = {}, {}
    for sid in story_topo:
        es[sid] = max((ef[p] for p in story_radj.get(sid, [])), default=0)
        ef[sid] = es[sid] + story_durations[sid]

    project_duration = max(ef.values()) if ef else 0

    lf, ls = {}, {}
    for sid in reversed(story_topo):
        lf[sid] = min((ls[s] for s in story_adj.get(sid, [])), default=project_duration)
        ls[sid] = lf[sid] - story_durations[sid]

    float_val = {sid: ls[sid] - es[sid] for sid in stories}

    return {
        "es": es, "ef": ef,
        "ls": ls, "lf": lf,
        "float": float_val,
        "project_duration": project_duration,
        "critical_ids": {sid for sid in stories if abs(float_val[sid]) < 1e-9},
    }


# ── Node-level standards collection ──────────────────────────────────────────

def _collect_node_standards(nodes: dict, owned_node_ids: list) -> set[str]:
    """Derive deduplicated set of appfw sub-standards from owned nodes' appfw_standard fields."""
    standards: set[str] = set()
    for nid in owned_node_ids:
        val = nodes[nid].get("appfw_standard")
        if not val:
            continue
        if isinstance(val, list):
            standards.update(v for v in val if v)
        else:
            standards.add(val)
    return standards


# ── Main analysis ────────────────────────────────────────────────────────────

def analyze_dag(data: dict) -> dict:
    nodes_list = data.get("nodes", [])
    edges_list = data.get("edges", [])
    stories_list = data.get("user_stories", [])

    if not nodes_list:
        return {"valid": False, "error": "No nodes provided"}
    if not stories_list:
        return {"valid": False, "error": "No user_stories provided"}

    nodes = {n["id"]: n for n in nodes_list}
    duration = {nid: max(0.25, float(nodes[nid].get("duration_days", 1)))
                for nid in nodes}

    # ── Validate stories ─────────────────────────────────────────────────────
    stories = {s["id"]: s for s in stories_list}
    node_to_story = {}
    errors = []

    for sid, story in stories.items():
        for nid in story.get("owned_nodes", []):
            if nid not in nodes:
                errors.append(f"Story '{sid}' references unknown node '{nid}'")
            elif nid in node_to_story:
                errors.append(
                    f"Node '{nid}' owned by both '{node_to_story[nid]}' and '{sid}'")
            else:
                node_to_story[nid] = sid

    # Build priority mappings
    story_priority = {}  # sid -> numeric rank
    for sid, story in stories.items():
        story_priority[sid] = PRIORITY_RANK.get(
            story.get("priority", "normal"), 2)

    # Node-level priority: inherit from owning story
    node_priority = {}
    for nid, sid in node_to_story.items():
        node_priority[nid] = story_priority.get(sid, 2)

    orphan_nodes = set(nodes) - set(node_to_story)
    if orphan_nodes:
        print(f"WARNING: {len(orphan_nodes)} orphan node(s) (no owning story) — "
              f"ignored by story-level analysis: {sorted(orphan_nodes)}",
              file=sys.stderr)

    if errors:
        return {"valid": False, "error": "Story validation failed", "details": errors}

    # ── Node-level graph ─────────────────────────────────────────────────────
    try:
        adj, radj, in_degree = _build_graph(nodes, edges_list)
    except ValueError as exc:
        return {"valid": False, "error": str(exc)}

    node_topo = _kahns_topo_sort(set(nodes), adj, in_degree)
    if node_topo is None:
        processed = set()
        temp_in = dict(in_degree)
        q = deque(nid for nid in nodes if temp_in[nid] == 0)
        while q:
            n = q.popleft()
            processed.add(n)
            for s in adj[n]:
                temp_in[s] -= 1
                if temp_in[s] == 0:
                    q.append(s)
        return {
            "valid": False,
            "error": "Cycle detected — not a valid DAG.",
            "cycle_nodes": sorted(set(nodes) - processed),
        }

    # ── Node-level CPM (for timing) ──────────────────────────────────────────
    cpm = _cpm(nodes, adj, radj, node_topo, duration)
    es, ef = cpm["es"], cpm["ef"]
    node_critical_ids = cpm["critical_ids"]

    # ── Node-level parallel phases & team estimates ──────────────────────────
    phases = _parallel_phases(node_topo, radj)
    max_parallel = max(len(v) for v in phases.values())

    dev_counts = sorted({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, max_parallel} & set(range(1, len(nodes) + 1)))
    team_estimates = {}
    team_node_finish_times = {}  # dev_count -> {node_id: finish_time}
    team_assignments = {}        # dev_count -> [assignment dicts]
    for d in dev_counts:
        label = f"{d}_developer{'s' if d > 1 else ''}"
        makespan, finish_times, assignments = _simulate(
            nodes, adj, in_degree, duration, d, cpm["float"], node_priority)
        team_estimates[label] = makespan
        team_node_finish_times[d] = finish_times
        team_assignments[d] = assignments

    total_work = sum(duration.values())

    # ── Knee of makespan curve (max useful developers) ───────────────────────
    # The knee is the last team size where adding a developer still reduces
    # the duration by at least 0.25 days (the minimum task duration).
    # Beyond this point, extra developers sit idle — the critical path is
    # the bottleneck, not developer count.
    prev_makespan = None
    knee = 1
    for d in dev_counts:
        label = f"{d}_developer{'s' if d > 1 else ''}"
        ms = team_estimates[label]
        if prev_makespan is not None:
            saving = prev_makespan - ms
            if saving >= 0.25:
                knee = d
            else:
                break
        prev_makespan = ms

    # ── Transitive predecessors ──────────────────────────────────────────────
    trans_preds = _transitive_predecessors(set(nodes), radj)

    # ── Story-level DAG ──────────────────────────────────────────────────────
    story_adj, story_radj, story_in_degree, story_edges = _build_story_dag(
        stories, node_to_story, trans_preds)

    story_topo = _kahns_topo_sort(set(stories), story_adj, story_in_degree)
    if story_topo is None:
        return {
            "valid": False,
            "error": "Cycle detected in story-level DAG.",
        }

    # ── Story durations & testable_day ───────────────────────────────────────
    # A story's "duration" for CPM is the span from when its earliest
    # prerequisite story finishes to when its last owned node finishes.
    # testable_day = max(ef[node]) across owned_nodes (from node-level CPM).
    # effort = sum of owned node durations.

    story_testable_day = {}
    story_effort = {}
    for sid, story in stories.items():
        owned = story.get("owned_nodes", [])
        story_testable_day[sid] = max((ef[nid] for nid in owned), default=0)
        story_effort[sid] = sum(duration[nid] for nid in owned)

    # Story duration for story-level CPM: the actual wall-clock span
    # this story's work occupies = testable_day - earliest start of its
    # owned nodes (accounting for internal parallelism within the story).
    story_earliest_start = {}
    for sid, story in stories.items():
        owned = story.get("owned_nodes", [])
        story_earliest_start[sid] = min((es[nid] for nid in owned), default=0)

    story_durations = {
        sid: max(0.25, story_testable_day[sid] - story_earliest_start[sid])
        for sid in stories
    }

    story_cpm_result = _story_cpm(
        stories, story_adj, story_radj, story_topo, story_durations)
    story_critical_ids = story_cpm_result["critical_ids"]

    # ── Dev tasks ordered (per story) ────────────────────────────────────────
    # All owned_nodes + their transitive predecessors, sorted by
    # earliest_start_day ascending (ties broken by node ID).
    story_dev_tasks = {}
    for sid, story in stories.items():
        owned = set(story.get("owned_nodes", []))
        all_nodes = set(owned)
        for nid in owned:
            all_nodes |= trans_preds.get(nid, set())

        ordered = sorted(all_nodes, key=lambda n: (es[n], n))
        story_dev_tasks[sid] = [
            {
                "id": nid,
                "name": nodes[nid]["name"],
                "duration_days": duration[nid],
                "earliest_start_day": es[nid],
                "earliest_finish_day": ef[nid],
                "is_critical": nid in node_critical_ids,
                "owned_by": node_to_story[nid],
                "is_predecessor": nid not in owned,
            }
            for nid in ordered
        ]

    # ── Story-level critical path ────────────────────────────────────────────
    story_critical_path = [
        {
            "id": sid,
            "title": stories[sid]["title"],
            "testable_day": story_testable_day[sid],
            "effort_days": story_effort[sid],
        }
        for sid in story_topo
        if sid in story_critical_ids
    ]

    # ── Per-team-size story testable days (for PM report toggle) ────────────
    # For each team size 1..knee, compute when each story becomes testable
    # using the simulation's node finish times.
    team_story_testable = {}
    for d in range(1, knee + 1):
        ft = team_node_finish_times.get(d, {})
        story_days = {}
        for sid, story in stories.items():
            owned = story.get("owned_nodes", [])
            story_days[sid] = max((ft.get(nid, 0) for nid in owned), default=0)
        team_story_testable[str(d)] = story_days

    # ── Per-team-size story gantt & dev allocation ────────────────────────
    # Story gantt uses a story-level simulation so dependent stories never
    # overlap. Dev gantt uses the node-level simulation assignments.
    team_story_gantt = {}
    team_dev_gantt = {}
    for d in range(1, knee + 1):
        assigns = team_assignments.get(d, [])

        # Story gantt: story-level greedy simulation
        team_story_gantt[str(d)] = _simulate_stories(
            list(stories.keys()), story_adj, story_radj,
            dict(story_in_degree), story_effort, d, story_cpm_result["float"],
            story_priority)

        # Dev gantt: per-developer list of task assignments with story info
        dev_tasks: dict[int, list] = {i: [] for i in range(d)}
        for a in assigns:
            sid = node_to_story.get(a["node"], "")
            dev_tasks[a["dev"]].append({
                "node": a["node"],
                "node_name": nodes[a["node"]]["name"],
                "story_id": sid,
                "story_title": stories[sid]["title"] if sid in stories else "",
                "start": a["start"],
                "end": a["end"],
            })
        # Sort each dev's tasks by start time
        for dev_idx in dev_tasks:
            dev_tasks[dev_idx].sort(key=lambda x: x["start"])
        team_dev_gantt[str(d)] = {
            f"dev_{i}": dev_tasks[i] for i in range(d)
        }

    # ── Delivery waves (simulation-based, per team size) ────────────────────
    # Derive waves from each team size's simulation. A wave = stories that
    # overlap in time. A new wave starts when all previous stories finish
    # before any new ones begin.
    team_delivery_waves_ids = {}
    for d in range(1, knee + 1):
        sim = team_story_gantt.get(str(d), {})
        team_delivery_waves_ids[str(d)] = _derive_waves_from_simulation(sim)

    # Use knee team size as the primary delivery_waves
    wave_groups = team_delivery_waves_ids.get(str(knee), [])

    delivery_waves = []
    for wave_idx, wave_sids in enumerate(wave_groups, 1):
        delivery_waves.append({
            "wave": wave_idx,
            "stories": [
                {
                    "id": sid,
                    "title": stories[sid]["title"],
                    "category": stories[sid].get("category", "feature"),
                    "testable_day": story_testable_day[sid],
                    "effort_days": story_effort[sid],
                    "priority": stories[sid].get("priority", "normal"),
                    "is_critical": sid in story_critical_ids,
                    "depends_on": sorted(story_radj.get(sid, [])),
                    "unblocks": sorted(story_adj.get(sid, [])),
                    "dev_tasks_ordered": story_dev_tasks[sid],
                    "appfw_standards": sorted(_collect_node_standards(
                        nodes, stories[sid].get("owned_nodes", [])
                    )),
                }
                for sid in wave_sids
            ],
        })

    # ── Assemble output ──────────────────────────────────────────────────────
    project_duration = cpm["project_duration"]

    return {
        "valid": True,
        "dag": {
            "project": data.get("project", "Unnamed Project"),
            "feature_groups": data.get("feature_groups", []),
            "user_stories": data.get("user_stories", []),
            "nodes": data.get("nodes", []),
            "edges": data.get("edges", []),
        },
        "summary": {
            "project": data.get("project", "Unnamed Project"),
            "total_nodes": len(nodes),
            "total_edges": len(edges_list),
            "total_stories": sum(1 for s in stories.values() if s.get("category") != "infrastructure"),
            "total_stories_with_infra": len(stories),
            "project_duration_days": project_duration,
            "sequential_duration_days": total_work,
            "parallelism_speedup": round(total_work / project_duration, 2)
                if project_duration > 0 else 1,
            "max_parallel": max_parallel,
            "max_useful_developers": knee,
        },
        "story_dag": {
            "edges": story_edges,
            "topological_order": story_topo,
            "critical_path": story_critical_path,
        },
        "delivery_waves": delivery_waves,
        "team_delivery_waves": team_delivery_waves_ids,
        "team_estimates": {
            "note": "Greedy simulation on node-level DAG; priority tier first, then minimum float, then longest duration.",
            "makespan_by_team_size": team_estimates,
        },
        "team_story_testable": team_story_testable,
        "team_story_gantt": team_story_gantt,
        "team_dev_gantt": team_dev_gantt,
        "node_details": [
            {
                "id": nid,
                "name": nodes[nid]["name"],
                "category": nodes[nid].get("category", ""),
                "duration_days": duration[nid],
                "earliest_start_day": es[nid],
                "earliest_finish_day": ef[nid],
                "float_days": cpm["float"][nid],
                "is_critical": nid in node_critical_ids,
                "story_id": node_to_story[nid],
            }
            for nid in node_topo
        ],
    }


# ── Entry point ──────────────────────────────────────────────────────────────

def main():
    # ── Parse CLI ────────────────────────────────────────────────────────
    # Single positional arg: project DAG JSON file (built by merge-batch.py)
    # If no file provided, reads from stdin
    input_files = [arg for arg in sys.argv[1:] if not arg.startswith("--")]

    try:
        if input_files:
            with open(input_files[0]) as f:
                data = json.load(f)
        else:
            data = json.load(sys.stdin)
    except (json.JSONDecodeError, FileNotFoundError) as exc:
        print(json.dumps({"valid": False, "error": f"Input error: {exc}"}))
        sys.exit(1)

    # ── Run full analysis ────────────────────────────────────────────────
    result = analyze_dag(data)

    # Propagate release→story mapping if present in the DAG
    if "release_story_map" in data:
        result["release_story_map"] = data["release_story_map"]

    print(json.dumps(result, indent=2))
    if not result.get("valid"):
        sys.exit(1)


if __name__ == "__main__":
    main()
