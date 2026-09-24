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
import re
import sys
from collections import defaultdict, deque
from pathlib import Path
from typing import Optional

CONFIGS_DIR = Path(__file__).resolve().parent.parent / "configs"


def _read_testing_overhead_default() -> float:
    """Read the testing_overhead default from duration-defaults.md."""
    path = CONFIGS_DIR / "duration-defaults.md"
    if not path.exists():
        return 0.3
    text = path.read_text()
    m = re.search(r'\|\s*testing_overhead\s*\|\s*([\d.]+)\s*\|', text)
    return float(m.group(1)) if m else 0.3

# Priority → numeric sort value (lower = scheduled first).
# Accepts both string labels and integer values.
PRIORITY_RANK = {"critical": 0, "high": 1, "normal": 2, "low": 3}


def _priority_to_rank(value) -> int:
    """Convert a priority value (string or int) to a numeric rank."""
    if isinstance(value, int):
        return value
    return PRIORITY_RANK.get(value, 2)


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


# ── Story-level CPM ──────────────────────────────────────────────────────────

def _story_cpm(stories: dict, story_adj: dict, story_radj: dict,
               story_topo: list, story_effort: dict) -> dict:
    """CPM on the story-level DAG. Duration = story effort (sequential tasks)."""
    es, ef = {}, {}
    for sid in story_topo:
        es[sid] = round(max((ef[p] for p in story_radj.get(sid, [])), default=0), 1)
        ef[sid] = round(es[sid] + story_effort[sid], 1)

    project_duration = round(max(ef.values()), 1) if ef else 0

    lf, ls = {}, {}
    for sid in reversed(story_topo):
        lf[sid] = round(min((ls[s] for s in story_adj.get(sid, [])), default=project_duration), 1)
        ls[sid] = round(lf[sid] - story_effort[sid], 1)

    float_val = {sid: round(ls[sid] - es[sid], 1) for sid in stories}

    return {
        "es": es, "ef": ef,
        "ls": ls, "lf": lf,
        "float": float_val,
        "project_duration": project_duration,
        "critical_ids": {sid for sid in stories if abs(float_val[sid]) < 1e-9},
    }


# ── Story-level schedule simulation (hybrid critical-path + duration-balanced) ─

def _simulate_stories(story_ids: list, story_adj: dict, story_radj: dict,
                      story_in_degree: dict, story_effort: dict,
                      num_devs: int, story_float: dict,
                      story_priority: dict | None = None,
                      max_parallel_per_developer: int = 3,
                      duration_weight: float = 0.25) -> dict:
    """
    Hybrid critical-path + duration-balanced batch scheduling simulation.

    Each developer can work on up to `max_parallel_per_developer` stories
    in parallel.  Batch duration = max(story durations in batch).
    All stories in a batch complete simultaneously.

    Uses a hybrid heuristic combining critical-path importance with a
    duration penalty to select optimal batches.

    Returns {story_id: {start, end, dev}}.
    """
    from itertools import combinations
    import heapq

    if not story_ids:
        return {}

    # ── Cycle detection (Kahn's) ─────────────────────────────────────────
    _temp_in = dict(story_in_degree)
    _q = [sid for sid in story_ids if _temp_in[sid] == 0]
    _qi = 0
    while _qi < len(_q):
        _n = _q[_qi]; _qi += 1
        for _c in story_adj.get(_n, []):
            _temp_in[_c] -= 1
            if _temp_in[_c] == 0:
                _q.append(_c)
    if len(_q) != len(story_ids):
        raise ValueError(
            f"No completion events exist but {len(story_ids) - len(_q)} "
            f"unfinished stories remain.")

    # ── Calculate downstream critical-path length for every story ────────
    cp_memo: dict[str, float] = {}

    def _critical_path(sid: str) -> float:
        if sid in cp_memo:
            return cp_memo[sid]
        children = story_adj.get(sid, [])
        dur = story_effort.get(sid, 0)
        if not children:
            cp_memo[sid] = dur
        else:
            cp_memo[sid] = dur + max(_critical_path(c) for c in children)
        return cp_memo[sid]

    for sid in story_ids:
        _critical_path(sid)

    # ── Fresh mutable state ──────────────────────────────────────────────
    remaining_in = dict(story_in_degree)

    # ── Initialize READY stories ─────────────────────────────────────────
    ready: list[str] = sorted(
        [sid for sid in story_ids if remaining_in[sid] == 0],
        key=lambda s: (-cp_memo.get(s, 0), -story_effort.get(s, 0), s),
    )

    # ── Developer state ──────────────────────────────────────────────────
    dev_status = {i: "AVAILABLE" for i in range(num_devs)}
    dev_current_batch: dict[int, list[str]] = {i: [] for i in range(num_devs)}
    dev_available_at: dict[int, float] = {i: 0.0 for i in range(num_devs)}

    # ── Result tracking ──────────────────────────────────────────────────
    result: dict[str, dict] = {}
    completed_count = 0
    total = len(story_ids)
    current_time = 0.0

    # ── Event heap: (time, dev_index) ────────────────────────────────────
    completion_events: list[tuple[float, int]] = []

    # ── Batch selection function ─────────────────────────────────────────
    def _select_best_batch(ready_sids: list[str]) -> list[str]:
        if not ready_sids:
            return []

        # Rank by critical-path DESC, duration DESC, id ASC
        ranked = sorted(
            ready_sids,
            key=lambda s: (-cp_memo.get(s, 0), -story_effort.get(s, 0), s),
        )

        # Candidate window
        window_size = min(
            len(ranked),
            max(max_parallel_per_developer,
                max_parallel_per_developer * num_devs),
        )
        candidates = ranked[:window_size]

        batch_limit = min(max_parallel_per_developer, len(candidates))

        best_batch: tuple[str, ...] | None = None
        best_key: tuple | None = None

        for size in range(1, batch_limit + 1):
            for combo in combinations(candidates, size):
                cp_value = sum(cp_memo.get(s, 0) for s in combo)
                batch_dur = max(story_effort.get(s, 0) for s in combo)
                score = cp_value - duration_weight * batch_dur
                sorted_ids = sorted(combo)
                # Tie-breaking: higher score, higher cp, lower duration, lex smaller IDs
                key = (score, cp_value, -batch_dur, sorted_ids)

                if best_key is None or _batch_better(key, best_key):
                    best_key = key
                    best_batch = combo

        return list(best_batch) if best_batch else []

    def _batch_better(a, b) -> bool:
        if a[0] != b[0]: return a[0] > b[0]
        if a[1] != b[1]: return a[1] > b[1]
        if a[2] != b[2]: return a[2] > b[2]
        return a[3] < b[3]

    # ── Main simulation loop ─────────────────────────────────────────────
    while completed_count < total:
        # 1. Assign work to all available developers (sorted by dev ID)
        for dev in sorted(i for i in range(num_devs) if dev_status[i] == "AVAILABLE"):
            if not ready:
                break

            batch = _select_best_batch(ready)
            if not batch:
                break

            batch_set = set(batch)
            ready = [s for s in ready if s not in batch_set]

            batch_duration = max(story_effort.get(s, 0) for s in batch)

            for sid in batch:
                result[sid] = {
                    "start": round(current_time, 1),
                    "end": round(current_time + batch_duration, 1),
                    "dev": dev,
                }

            dev_status[dev] = "BUSY"
            dev_current_batch[dev] = list(batch)
            dev_available_at[dev] = current_time + batch_duration

            heapq.heappush(completion_events, (dev_available_at[dev], dev))

        # 2. Advance to next completion event
        if not completion_events:
            if completed_count < total:
                raise ValueError(
                    f"No completion events exist but {total - completed_count} "
                    f"unfinished stories remain."
                )
            break

        next_time = completion_events[0][0]
        current_time = next_time

        # 3. Process ALL events at this timestamp
        events_at_now: list[int] = []
        while completion_events and completion_events[0][0] == current_time:
            _, dev_id = heapq.heappop(completion_events)
            events_at_now.append(dev_id)

        events_at_now.sort()  # deterministic by dev ID

        for dev in events_at_now:
            batch_sids = dev_current_batch[dev]

            for sid in batch_sids:
                completed_count += 1

            # Unlock downstream nodes
            for sid in batch_sids:
                for succ in story_adj.get(sid, []):
                    remaining_in[succ] -= 1
                    if remaining_in[succ] == 0:
                        ready.append(succ)

            dev_current_batch[dev] = []
            dev_status[dev] = "AVAILABLE"

        # Re-sort ready queue after unlocking
        ready.sort(key=lambda s: (-cp_memo.get(s, 0), -story_effort.get(s, 0), s))

    return result


# ── Dependency-based waves (topological layers) ──────────────────────────────

def _dependency_waves(story_ids: list, story_adj: dict,
                      story_in_degree: dict) -> list[list[str]]:
    """Compute dependency-based waves (Kahn's levels).

    Wave 1 = all stories with no dependencies.
    Wave N = stories whose dependencies are all in waves < N.
    This is team-size independent — it shows what CAN be done in parallel.
    Stories within each wave are sorted by ID for determinism.
    """
    remaining_in = dict(story_in_degree)
    waves: list[list[str]] = []
    current_wave = sorted(sid for sid in story_ids if remaining_in[sid] == 0)

    while current_wave:
        waves.append(current_wave)
        next_wave_set: set[str] = set()
        for sid in current_wave:
            for succ in story_adj.get(sid, []):
                remaining_in[succ] -= 1
                if remaining_in[succ] == 0:
                    next_wave_set.add(succ)
        current_wave = sorted(next_wave_set)

    return waves


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


_CATEGORY_GROUP = {
    "frontend": "frontend",
    "design": "frontend",
    "backend": "backend",
    "database": "backend",
    "testing": "testing",
    "infrastructure": "infra",
    "mobile": "frontend",
}


def _group_tooling_by_category(nodes: dict) -> dict[str, list[str]]:
    """Group unique tooling items by broad category (frontend/backend/testing/infra)."""
    groups: dict[str, set[str]] = {}
    for n in nodes.values():
        cat = n.get("category", "infra")
        group = _CATEGORY_GROUP.get(cat, "infra")
        for t in n.get("tooling", []):
            groups.setdefault(group, set()).add(t)
    return {g: sorted(tools) for g, tools in sorted(groups.items())}


# ── Main analysis ────────────────────────────────────────────────────────────

def analyze_dag(data: dict, testing_overhead: float = 0.3) -> dict:
    nodes_list = data.get("nodes", [])
    edges_list = data.get("edges", [])
    stories_list = data.get("user_stories", [])

    if not nodes_list:
        return {"valid": False, "error": "No nodes provided"}
    if not stories_list:
        return {"valid": False, "error": "No user_stories provided"}

    nodes = {n["id"]: n for n in nodes_list}
    multiplier = 1 + testing_overhead
    duration = {nid: round(max(0.25, float(nodes[nid].get("duration_days", 1))) * multiplier, 1)
                for nid in nodes}

    # ── Validate stories ─────────────────────────────────────────────────────
    stories = {s["id"]: s for s in stories_list}
    node_to_story = {}
    errors = []

    for sid, story in stories.items():
        owned = story.get("owned_nodes", [])
        if not owned:
            errors.append(f"Story '{sid}' has no owned_nodes — every story must own at least one node")
        for nid in owned:
            if nid not in nodes:
                errors.append(f"Story '{sid}' references unknown node '{nid}'")
            elif nid in node_to_story:
                # Normal: stories across releases often share domain nodes.
                # Just track the first owner for scheduling purposes.
                pass
            else:
                node_to_story[nid] = sid

    # Build priority mappings
    story_priority = {}  # sid -> numeric rank
    for sid, story in stories.items():
        story_priority[sid] = _priority_to_rank(story.get("priority", "normal"))

    orphan_nodes = set(nodes) - set(node_to_story)
    if orphan_nodes:
        errors.append(
            f"{len(orphan_nodes)} orphan node(s) with no owning story: {sorted(orphan_nodes)}")

    if errors:
        return {"valid": False, "error": "Story validation failed", "details": errors}

    # ── Node-level graph (for story dependency derivation) ────────────────────
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
        stuck = set(nodes) - processed
        # Trace one cycle: follow edges from any stuck node
        cycle_path = []
        start = min(stuck)
        node = start
        visited = set()
        while node not in visited:
            visited.add(node)
            cycle_path.append(node)
            node = next((s for s in adj.get(node, []) if s in stuck), node)
        # Trim to just the cycle
        cycle_start = cycle_path.index(node)
        cycle_path = cycle_path[cycle_start:] + [node]
        cycle_str = " → ".join(cycle_path)
        cycle_edges = [{"from": cycle_path[i], "to": cycle_path[i + 1]}
                       for i in range(len(cycle_path) - 1)]
        return {
            "valid": False,
            "error": f"Cycle detected in node DAG: {cycle_str}",
            "cycle_path": cycle_path,
            "cycle_edges": cycle_edges,
            "cycle_nodes": sorted(stuck),
        }

    # ── Transitive predecessors ──────────────────────────────────────────────
    trans_preds = _transitive_predecessors(set(nodes), radj)

    # ── Story-level DAG ──────────────────────────────────────────────────────
    story_adj, story_radj, story_in_degree, story_edges = _build_story_dag(
        stories, node_to_story, trans_preds)

    story_topo = _kahns_topo_sort(set(stories), story_adj, story_in_degree)
    if story_topo is None:
        # story_topo is None means cycle — Kahn's processed fewer than all stories
        # Re-run to get the partial order so we know which stories are stuck
        temp_in = dict(story_in_degree)
        q = deque(sid for sid in stories if temp_in[sid] == 0)
        processed_stories = set()
        while q:
            s = q.popleft()
            processed_stories.add(s)
            for succ in story_adj.get(s, []):
                temp_in[succ] -= 1
                if temp_in[succ] == 0:
                    q.append(succ)
        stuck_stories = set(stories) - processed_stories
        # Trace the cycle using in-degree: stuck stories with edges from other
        # stuck stories form the cycle. Walk successors that are also stuck.
        # Use story_radj (reverse edges) to find a story in the cycle core —
        # one that has at least one stuck predecessor.
        core = {s for s in stuck_stories if story_adj.get(s, []) and
                any(succ in stuck_stories for succ in story_adj.get(s, []))}
        if not core:
            core = stuck_stories
        start = min(core)
        cycle_path = []
        node = start
        visited = set()
        while node not in visited:
            visited.add(node)
            cycle_path.append(node)
            # Follow only successors that are stuck AND not the current node
            successors = [s for s in story_adj.get(node, [])
                          if s in stuck_stories and s != node]
            if successors:
                node = successors[0]
            else:
                # No valid successor — cycle must involve this node via longer path
                # Try any stuck story we haven't visited
                unvisited_stuck = stuck_stories - visited
                if unvisited_stuck:
                    node = min(unvisited_stuck)
                else:
                    break
        if node in visited:
            cycle_start = cycle_path.index(node)
            cycle_path = cycle_path[cycle_start:] + [node]
        else:
            # Fallback: just report all stuck stories
            cycle_path = sorted(stuck_stories) + [min(stuck_stories)]
        # Annotate with titles and the node-level edges causing each link
        cycle_detail = []
        for i in range(len(cycle_path) - 1):
            sid_a, sid_b = cycle_path[i], cycle_path[i + 1]
            # Find node edges: nodes owned by A that are predecessors of nodes owned by B
            nodes_a = set(stories[sid_a].get("owned_nodes", []))
            nodes_b = set(stories[sid_b].get("owned_nodes", []))
            causing_edges = [
                {"from": nid_a, "to": nid_b}
                for nid_b in nodes_b
                for nid_a in trans_preds.get(nid_b, set()) & nodes_a
            ]
            cycle_detail.append({
                "from": sid_a, "from_title": stories[sid_a]["title"],
                "to": sid_b, "to_title": stories[sid_b]["title"],
                "via_node_edges": causing_edges[:5],
                "via_node_edges_total": len(causing_edges),
            })
        return {
            "valid": False,
            "error": f"Cycle detected in story-level DAG: {' → '.join(cycle_path)}",
            "cycle_path": cycle_path,
            "cycle_detail": cycle_detail,
            "stuck_stories": sorted(stuck_stories),
        }

    # ── Story effort (sum of owned node durations) ───────────────────────────
    story_effort = {}
    for sid, story in stories.items():
        owned = story.get("owned_nodes", [])
        story_effort[sid] = round(sum(duration[nid] for nid in owned), 1)

    total_work = round(sum(duration.values()), 1)

    # ── Story-level CPM (using effort as duration) ───────────────────────────
    story_cpm_result = _story_cpm(
        stories, story_adj, story_radj, story_topo, story_effort)
    story_critical_ids = story_cpm_result["critical_ids"]
    project_duration = story_cpm_result["project_duration"]

    # ── Story-level simulation for all team sizes ────────────────────────────
    dev_counts = sorted({1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20} & set(range(1, len(stories) + 1)))
    if not dev_counts:
        dev_counts = [1]

    team_estimates = {}
    team_sim_results = {}  # dev_count -> simulation result dict
    for d in dev_counts:
        sim = _simulate_stories(
            list(stories.keys()), story_adj, story_radj,
            dict(story_in_degree), story_effort, d, story_cpm_result["float"],
            story_priority)
        team_sim_results[d] = sim
        makespan = round(max((s["end"] for s in sim.values()), default=0), 1)
        label = f"{d}_developer{'s' if d > 1 else ''}"
        team_estimates[label] = makespan

    # ── Knee of makespan curve (max useful developers) ───────────────────────
    # The knee is the last team size where adding a developer still reduces
    # the duration by at least 1 day. Beyond this point, extra developers
    # sit idle — the critical path is the bottleneck, not developer count.
    # Capped at 20 for realistic team sizes.
    prev_makespan = None
    knee = 1
    for d in dev_counts:
        label = f"{d}_developer{'s' if d > 1 else ''}"
        ms = team_estimates[label]
        if prev_makespan is not None:
            saving = prev_makespan - ms
            if saving >= 1.0:
                knee = d
            else:
                break
        prev_makespan = ms
    knee = min(knee, 20)

    # ── Per-team-size story testable days ─────────────────────────────────────
    # Story testable day = story end time from simulation.
    team_story_testable = {}
    for d in dev_counts:
        if d > knee:
            break
        sim = team_sim_results[d]
        team_story_testable[str(d)] = {
            sid: round(sim[sid]["end"], 1) for sid in sim
        }

    # ── Per-team-size story gantt & dev allocation ────────────────────────────
    team_story_gantt = {}
    team_dev_gantt = {}
    for d in dev_counts:
        if d > knee:
            break
        sim = team_sim_results[d]

        # Story gantt: start/end/dev per story
        team_story_gantt[str(d)] = {
            sid: {"start": sim[sid]["start"], "end": sim[sid]["end"],
                  "dev": sim[sid]["dev"]}
            for sid in sim
        }

        # Dev gantt: per-developer list of batches with parallel stories
        dev_batches: dict[int, dict[tuple, list]] = {i: {} for i in range(d)}
        for sid, info in sim.items():
            dev_idx = info["dev"]
            batch_key = (info["start"], info["end"])
            dev_batches[dev_idx].setdefault(batch_key, []).append({
                "story_id": sid,
                "story_title": stories[sid]["title"],
            })
        # Build ordered batch list per developer
        dev_gantt_output: dict[str, list] = {}
        for dev_idx in range(d):
            batches = []
            for (start, end), batch_stories in sorted(dev_batches[dev_idx].items()):
                batch_stories.sort(key=lambda x: x["story_id"])
                batches.append({
                    "start": start,
                    "end": end,
                    "parallel_stories": batch_stories,
                })
            dev_gantt_output[f"dev_{dev_idx}"] = batches
        team_dev_gantt[str(d)] = dev_gantt_output

    # ── Delivery waves (dependency-based, team-size independent) ────────────
    # These show what CAN be done in parallel based purely on DAG dependencies.
    wave_groups = _dependency_waves(
        list(stories.keys()), story_adj, dict(story_in_degree))

    # Node topo position lookup (for ordering dev tasks within stories)
    node_topo_pos = {nid: i for i, nid in enumerate(node_topo)}

    # ── Dev tasks ordered (per story) ────────────────────────────────────────
    # All owned_nodes + their transitive predecessors, sorted by
    # topological order. No per-node timing — timing is at story level only.
    story_dev_tasks = {}
    for sid, story in stories.items():
        owned = set(story.get("owned_nodes", []))
        all_nodes = set(owned)
        for nid in owned:
            all_nodes |= trans_preds.get(nid, set())

        ordered = sorted(all_nodes, key=lambda n: (node_topo_pos.get(n, 0), n))
        story_dev_tasks[sid] = [
            {
                "id": nid,
                "name": nodes[nid]["name"],
                "duration_days": duration[nid],
                "owned_by": node_to_story[nid],
                "is_predecessor": nid not in owned,
                **({"appfw_standard": nodes[nid]["appfw_standard"]}
                   if nodes[nid].get("appfw_standard") else {}),
            }
            for nid in ordered
        ]

    # ── Story-level critical path ────────────────────────────────────────────
    story_critical_path = [
        {
            "id": sid,
            "title": stories[sid]["title"],
            "testable_day": story_cpm_result["ef"][sid],
            "effort_days": story_effort[sid],
        }
        for sid in story_topo
        if sid in story_critical_ids
    ]

    # ── Build delivery waves output ──────────────────────────────────────────
    delivery_waves = []
    for wave_idx, wave_sids in enumerate(wave_groups, 1):
        delivery_waves.append({
            "wave": wave_idx,
            "stories": [
                {
                    "id": sid,
                    "title": stories[sid]["title"],
                    "category": stories[sid].get("category", "feature"),
                    "testable_day": story_cpm_result["ef"][sid],
                    "effort_days": story_effort[sid],
                    "priority": stories[sid].get("priority", "normal"),
                    "is_critical": sid in story_critical_ids,
                    "depends_on": sorted(story_radj.get(sid, [])),
                    "unblocks": sorted(story_adj.get(sid, [])),
                    "dev_tasks_ordered": story_dev_tasks[sid],
                    **({"duration_assumptions": stories[sid]["duration_assumptions"]}
                       if stories[sid].get("duration_assumptions") else {}),
                    "appfw_standards": sorted(_collect_node_standards(
                        nodes, stories[sid].get("owned_nodes", [])
                    )),
                }
                for sid in wave_sids
            ],
        })

    # ── Assemble output ──────────────────────────────────────────────────────
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
            "max_useful_developers": knee,
            "testing_overhead_pct": round(testing_overhead * 100),
            "project_tooling": _group_tooling_by_category(nodes),
        },
        "story_dag": {
            "edges": story_edges,
            "topological_order": story_topo,
            "critical_path": story_critical_path,
        },
        "delivery_waves": delivery_waves,
        "team_estimates": {
            "note": "Hybrid critical-path + duration-balanced batch simulation; developers take up to 3 parallel stories per batch.",
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
                "story_id": node_to_story[nid],
                **({"tooling": nodes[nid]["tooling"]}
                   if nodes[nid].get("tooling") else {}),
            }
            for nid in node_topo
        ],
    }


# ── Cycle breaking ───────────────────────────────────────────────────────────

def _find_direct_edges_on_path(src: str, dst: str, adj: dict) -> list[dict]:
    """BFS from src toward dst, return direct edges on the shortest path."""
    parent = {src: None}
    queue = deque([src])
    while queue:
        node = queue.popleft()
        if node == dst:
            break
        for succ in adj.get(node, []):
            if succ not in parent:
                parent[succ] = node
                queue.append(succ)
    else:
        return []  # no path found

    # Reconstruct path and extract edges
    edges = []
    node = dst
    while parent[node] is not None:
        edges.append({"from": parent[node], "to": node})
        node = parent[node]
    edges.reverse()
    return edges


def _break_story_cycle(data: dict, cycle_detail: list) -> list[dict]:
    """Remove direct DAG edges to break the weakest link in a story-level cycle.

    Returns the list of removed edges (as {"from", "to"} dicts).
    """
    # Find the weakest story-level edge (fewest transitive node connections)
    weakest = min(cycle_detail, key=lambda d: d["via_node_edges_total"])

    sid_a, sid_b = weakest["from"], weakest["to"]
    stories = {s["id"]: s for s in data["user_stories"]}
    nodes_a = set(stories[sid_a]["owned_nodes"])
    nodes_b = set(stories[sid_b]["owned_nodes"])

    # Build forward adjacency from DAG edges
    adj = defaultdict(list)
    for edge in data["edges"]:
        adj[edge["from"]].append(edge["to"])

    # Strategy 1: direct cross-story edges (A's node → B's node)
    direct_cross = [
        e for e in data["edges"]
        if e["from"] in nodes_a and e["to"] in nodes_b
    ]

    if direct_cross:
        edges_to_remove = direct_cross
    else:
        # Strategy 2: find direct edges on the shortest path between
        # causing node pairs, pick the edge closest to the boundary
        edges_to_remove = []
        for causing in weakest["via_node_edges"]:
            path_edges = _find_direct_edges_on_path(
                causing["from"], causing["to"], adj)
            if path_edges:
                # Pick the last edge on the path (closest to destination story)
                edges_to_remove.append(path_edges[-1])
                break  # one is enough to break the transitive link

    if not edges_to_remove:
        return []

    # Remove from DAG
    remove_set = {(e["from"], e["to"]) for e in edges_to_remove}
    data["edges"] = [
        e for e in data["edges"]
        if (e["from"], e["to"]) not in remove_set
    ]

    return [{"from": e["from"], "to": e["to"],
             "reason": f"broke cycle between stories {sid_a} → {sid_b} "
                       f"(weakest link: {weakest['via_node_edges_total']} "
                       f"transitive node connections)"}
            for e in edges_to_remove]


# ── Entry point ──────────────────────────────────────────────────────────────

def main():
    # ── Parse CLI ────────────────────────────────────────────────────────
    import argparse as _argparse

    cli = _argparse.ArgumentParser(
        description="Run story-level analysis on the project DAG")
    cli.add_argument("dag_file", nargs="?", default=None,
                     help="Path to project DAG JSON (reads stdin if omitted)")
    cli.add_argument("--expected-stories", type=int, default=None,
                     metavar="N",
                     help="Expected total user story count (including infra). "
                          "If provided, exits with error when the DAG story "
                          "count doesn't match.")
    _default_overhead = _read_testing_overhead_default()
    cli.add_argument("--testing-overhead", type=float, default=_default_overhead,
                     metavar="PCT",
                     help="Fractional overhead for unit testing baked into "
                          f"each node duration (default: {_default_overhead} "
                          f"from duration-defaults.md)")
    cli.add_argument("--break-cycles", action="store_true",
                     help="Auto-break story-level cycles by removing the "
                          "weakest edge. Writes modified DAG back to file.")
    cli.add_argument("--max-cycle-breaks", type=int, default=5,
                     metavar="N",
                     help="Max cycle-breaking iterations (default: 5)")
    args = cli.parse_args()

    try:
        if args.dag_file:
            with open(args.dag_file) as f:
                data = json.load(f)
        else:
            data = json.load(sys.stdin)
    except (json.JSONDecodeError, FileNotFoundError) as exc:
        print(json.dumps({"valid": False, "error": f"Input error: {exc}"}))
        sys.exit(1)

    # ── Pre-analysis story count check (Check 4) ─────────────────────────
    if args.expected_stories is not None:
        dag_story_count = len(data.get("user_stories", []))
        if dag_story_count != args.expected_stories:
            print(
                f"ERROR: Expected {args.expected_stories} stories in DAG, "
                f"but found {dag_story_count}. DAG may be corrupted between "
                f"merge and processor — re-check merge outputs.",
                file=sys.stderr)
            print(json.dumps({
                "valid": False,
                "error": f"Story count mismatch: expected {args.expected_stories}, "
                         f"found {dag_story_count} in DAG",
            }))
            sys.exit(1)
        else:
            print(f"Story count check passed: {dag_story_count} stories",
                  file=sys.stderr)

    # ── Run full analysis (with optional cycle breaking) ────────────────
    all_cycle_breaks = []

    result = analyze_dag(data, testing_overhead=args.testing_overhead)

    if args.break_cycles and not result.get("valid") and "cycle_detail" in result:
        for iteration in range(1, args.max_cycle_breaks + 1):
            cycle_detail = result["cycle_detail"]
            removed = _break_story_cycle(data, cycle_detail)
            if not removed:
                print(f"Cycle break iteration {iteration}: could not find "
                      f"direct edges to remove", file=sys.stderr)
                break

            all_cycle_breaks.extend(removed)
            for r in removed:
                print(f"Cycle break iteration {iteration}: removed edge "
                      f"{r['from']} → {r['to']} ({r['reason']})",
                      file=sys.stderr)

            # Write modified DAG back to file
            if args.dag_file:
                with open(args.dag_file, "w") as f:
                    json.dump(data, f, indent=2)

            # Re-run analysis
            result = analyze_dag(data, testing_overhead=args.testing_overhead)
            if result.get("valid") or "cycle_detail" not in result:
                print(f"Cycles resolved after {iteration} iteration(s)",
                      file=sys.stderr)
                break

    if all_cycle_breaks:
        result["cycle_breaks"] = all_cycle_breaks

    # Propagate release→story mapping if present in the DAG
    if "release_story_map" in data:
        result["release_story_map"] = data["release_story_map"]

    # ── Post-analysis story count verification ───────────────────────────
    if result.get("valid") and args.expected_stories is not None:
        output_story_count = result["summary"]["total_stories_with_infra"]
        if output_story_count != args.expected_stories:
            print(
                f"ERROR: Processor output has {output_story_count} stories "
                f"but expected {args.expected_stories}. Stories lost during "
                f"analysis.",
                file=sys.stderr)
            result["valid"] = False
            result["error"] = (
                f"Post-analysis story count mismatch: expected "
                f"{args.expected_stories}, got {output_story_count}")

    print(json.dumps(result, indent=2))
    if not result.get("valid"):
        sys.exit(1)


if __name__ == "__main__":
    main()
