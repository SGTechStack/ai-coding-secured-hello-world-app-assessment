#!/usr/bin/env python3
"""Merge a backward mapping batch output into the project DAG.

Deterministic merge — nodes matched by exact ID. If a node ID already
exists in the DAG, the duplicate is skipped but edges may still be
created depending on release context.

Edge rules:
  - If either endpoint is from a PREVIOUS release: skip the edge.
    Cross-release ordering is handled separately by --seal-release.
  - If an endpoint is from the SAME release (earlier batch): keep the edge.
  - Back-edges (new node → previous release node) are always skipped.

Release-aware merging (--release N):
  When a new release number is seen for the first time, the script snapshots
  the current node IDs and terminal nodes as "previous release."

Sealing a release (--seal-release):
  After all batches for a release are merged, run --seal-release to inject
  cross-release edges: all prior-release terminal nodes → all current-release
  entry nodes. This enforces "all of R(N) completes before any of R(N+1) starts."

Release state is tracked in a sidecar file (<dag-path>.releases.json).

Usage:
    python3 merge-batch.py <project-dag.json> <batch-output.json> [--release N]
    python3 merge-batch.py <project-dag.json> --seal-release

The merged DAG is written back to the DAG path.
"""

import json
import sys
import argparse

# ── Prefix-to-category mapping for nodes ──────────────────────────
_PREFIX_CATEGORY = {
    "fe_": "frontend",
    "be_": "backend",
    "mob_": "mobile",
}


def _infer_category(node_id):
    """Derive category from node ID prefix. Returns None if no prefix matches."""
    for prefix, category in _PREFIX_CATEGORY.items():
        if node_id.startswith(prefix):
            return category
    return None


def _normalize_edge(e):
    """Accept both compact [from, to] and verbose {"from": ..., "to": ...}."""
    if isinstance(e, list):
        return {"from": e[0], "to": e[1]}
    return e


def _normalize_batch(batch):
    """Apply defaults and normalizations to a batch before merging.

    - Edges: convert compact [from, to] to {"from": ..., "to": ...}
    - Nodes: default status to "pending", infer category from ID prefix
    """
    batch["edges"] = [_normalize_edge(e) for e in batch.get("edges", [])]
    for node in batch.get("nodes", []):
        node.setdefault("status", "pending")
        if "category" not in node:
            inferred = _infer_category(node["id"])
            if inferred:
                node["category"] = inferred
            else:
                node["category"] = "infrastructure"
    return batch


def _load_release_state(dag_path):
    """Load or initialize the release state sidecar."""
    state_path = dag_path + ".releases.json"
    try:
        with open(state_path) as f:
            return json.load(f)
    except FileNotFoundError:
        return {"current_release": None, "prior_node_ids": [], "prior_terminal_ids": []}


def _save_release_state(dag_path, state):
    """Write the release state sidecar."""
    state_path = dag_path + ".releases.json"
    with open(state_path, "w") as f:
        json.dump(state, f, indent=2)
        f.write("\n")


def _find_terminal_nodes(node_ids, edges):
    """Find nodes with no outgoing edges (within the given node set)."""
    has_outgoing = {e["from"] for e in edges if e["from"] in node_ids and e["to"] in node_ids}
    return node_ids - has_outgoing


def _find_entry_nodes(node_ids, edges):
    """Find nodes with no incoming edges from other nodes in the same set."""
    has_incoming = {e["to"] for e in edges if e["from"] in node_ids and e["to"] in node_ids}
    return node_ids - has_incoming


def seal_release(dag_path):
    """Add cross-release edges: prior-release terminals → current-release entries."""
    with open(dag_path) as f:
        dag = json.load(f)

    release_state = _load_release_state(dag_path)
    prior_node_ids = set(release_state.get("prior_node_ids", []))
    prior_terminals = set(release_state.get("prior_terminal_ids", []))

    if not prior_terminals:
        print("No previous release to seal against.")
        return

    # Current release nodes = all nodes NOT in prior_node_ids
    all_node_ids = {n["id"] for n in dag["nodes"]}
    current_node_ids = all_node_ids - prior_node_ids

    if not current_node_ids:
        print("No current-release nodes found. Nothing to seal.")
        return

    # Entry nodes of current release: no incoming edges from other current-release nodes
    entry_nodes = _find_entry_nodes(current_node_ids, dag["edges"])

    # Add prior_terminal → current_entry edges
    existing_edges = {(e["from"], e["to"]) for e in dag["edges"]}
    count = 0
    for t in sorted(prior_terminals):
        for e in sorted(entry_nodes):
            key = (t, e)
            if key not in existing_edges:
                dag["edges"].append({"from": t, "to": e})
                existing_edges.add(key)
                count += 1

    with open(dag_path, "w") as f:
        json.dump(dag, f, indent=2)
        f.write("\n")

    print(f"Sealed release: {len(prior_terminals)} terminals × {len(entry_nodes)} entries = {count} cross-release edges added")


def merge_batch(dag_path, batch_path, release=None, project_name=None):
    # Load batch output
    with open(batch_path) as f:
        batch = json.load(f)
    _normalize_batch(batch)

    # Load or initialize DAG
    try:
        with open(dag_path) as f:
            dag = json.load(f)
        is_new_dag = False
    except FileNotFoundError:
        dag = {
            "project": project_name or batch.get("project", "Unnamed Project"),
            "feature_groups": [],
            "nodes": [],
            "edges": [],
            "user_stories": []
        }
        is_new_dag = True

    # --- Release tracking ---
    release_state = _load_release_state(dag_path)
    prior_node_ids = set(release_state.get("prior_node_ids", []))

    if release is not None and release != release_state["current_release"]:
        # New release — snapshot ALL current node IDs as prior-release nodes.
        # Edges involving these nodes will be skipped during merge.
        if not is_new_dag and dag["nodes"]:
            prior_node_ids = {n["id"] for n in dag["nodes"]}
            prior_terminals = _find_terminal_nodes(prior_node_ids, dag["edges"])
            release_state["prior_node_ids"] = sorted(prior_node_ids)
            release_state["prior_terminal_ids"] = sorted(prior_terminals)
            print(f"  Release {release} starting — {len(prior_node_ids)} prior-release nodes, {len(prior_terminals)} terminals")
        release_state["current_release"] = release

    # --- Pre-merge batch validation ---
    # Catch problems in the batch output BEFORE merging, so recovery is
    # cheap (re-run one subagent) rather than expensive (edit merged DAG).
    existing_node_ids = {n["id"] for n in dag["nodes"]}
    batch_node_ids = {n["id"] for n in batch["nodes"]}
    all_available_ids = existing_node_ids | batch_node_ids
    batch_errors = []

    for story in batch["user_stories"]:
        # Filter owned_nodes to nodes that will exist after merge
        resolved = [nid for nid in story.get("owned_nodes", [])
                    if nid in all_available_ids]
        if not resolved:
            batch_errors.append(
                f"Story '{story['id']}' has no owned_nodes (or all reference "
                f"unknown nodes) — the backward mapping subagent failed to "
                f"derive nodes for this story")

    # Check for orphan nodes within the batch (nodes not owned by any batch story)
    batch_owned = set()
    for story in batch["user_stories"]:
        batch_owned.update(nid for nid in story.get("owned_nodes", [])
                           if nid in batch_node_ids)
    batch_orphans = batch_node_ids - batch_owned - existing_node_ids
    if batch_orphans:
        batch_errors.append(
            f"{len(batch_orphans)} orphan node(s) in batch — nodes exist but "
            f"no story owns them: {sorted(batch_orphans)}")

    if batch_errors:
        print(f"\nBatch validation failed for {batch_path}:")
        for err in batch_errors:
            print(f"  ERROR: {err}")
        print(f"\nFix: re-run the backward mapping subagent for this batch.")
        sys.exit(1)

    # --- Merge nodes ---
    skipped_nodes = set()
    new_nodes = []
    for node in batch["nodes"]:
        if node["id"] in existing_node_ids:
            skipped_nodes.add(node["id"])
        else:
            new_nodes.append(node)

    new_node_ids = {n["id"] for n in new_nodes}

    # --- Merge stories (just store story→nodes mapping) ---
    existing_story_ids = {s["id"] for s in dag["user_stories"]}
    new_stories = []
    for story in batch["user_stories"]:
        if story["id"] in existing_story_ids:
            print(f"  WARNING: Story '{story['id']}' already exists — skipping")
            continue
        # Filter owned_nodes to only nodes that will exist in the DAG
        story["owned_nodes"] = [
            nid for nid in story.get("owned_nodes", [])
            if nid in new_node_ids or nid in existing_node_ids
        ]
        new_stories.append(story)

    # --- Track release→story mapping in the DAG ---
    release_story_map = dag.get("release_story_map", {})
    if release is not None:
        rel_key = str(release)
        existing_release_stories = release_story_map.get(rel_key, [])
        existing_release_stories.extend([s["id"] for s in new_stories])
        release_story_map[rel_key] = existing_release_stories
        dag["release_story_map"] = release_story_map

    # Deduplicate feature groups by ID, normalizing title→name
    existing_group_ids = {g["id"] for g in dag["feature_groups"]}
    new_groups = []
    for g in batch.get("feature_groups", []):
        if g["id"] in existing_group_ids:
            continue
        # Normalize: mapper may emit "title" instead of "name"
        if "name" not in g and "title" in g:
            g["name"] = g.pop("title")
        # Strip story-level fields that don't belong on groups
        for key in ("owned_nodes", "category", "verifiable"):
            g.pop(key, None)
        new_groups.append(g)

    # --- Process edges ---
    all_node_ids_after_merge = existing_node_ids | new_node_ids
    existing_edges = {(e["from"], e["to"]) for e in dag["edges"]}
    new_edges = []
    dropped_prior_release = 0
    dropped_dangling = 0

    for e in batch["edges"]:
        key = (e["from"], e["to"])

        # Skip duplicates
        if key in existing_edges:
            continue

        # Skip edges with dangling endpoints
        if e["from"] not in all_node_ids_after_merge or e["to"] not in all_node_ids_after_merge:
            dropped_dangling += 1
            continue

        # Skip edges involving previous-release nodes entirely.
        # Cross-release ordering is handled by --seal-release.
        if e["from"] in prior_node_ids or e["to"] in prior_node_ids:
            dropped_prior_release += 1
            continue

        new_edges.append(e)
        existing_edges.add(key)

    # --- Apply merge ---
    dag["feature_groups"].extend(new_groups)
    dag["nodes"].extend(new_nodes)
    dag["edges"].extend(new_edges)
    dag["user_stories"].extend(new_stories)

    # --- Validate ---
    all_node_ids = {n["id"] for n in dag["nodes"]}
    errors = 0

    for s in dag["user_stories"]:
        for nid in s.get("owned_nodes", []):
            if nid not in all_node_ids:
                print(f"  ERROR: Story '{s['id']}' references unknown node '{nid}'")
                errors += 1

    for e in dag["edges"]:
        if e["from"] not in all_node_ids:
            print(f"  ERROR: Edge references unknown from-node: {e['from']}")
            errors += 1
        if e["to"] not in all_node_ids:
            print(f"  ERROR: Edge references unknown to-node: {e['to']}")
            errors += 1

    if errors > 0:
        print(f"\n{errors} error(s) found. Merge aborted — DAG not modified.")
        sys.exit(1)

    # --- Write ---
    with open(dag_path, "w") as f:
        json.dump(dag, f, indent=2)
        f.write("\n")

    _save_release_state(dag_path, release_state)

    print(f"Merged {batch_path} into {dag_path}")
    print(f"  New nodes: {len(new_nodes)}, skipped (existing): {len(skipped_nodes)}")
    print(f"  New stories: {len(new_stories)}")
    print(f"  New groups: {len(new_groups)}")
    print(f"  New edges: {len(new_edges)}")
    if dropped_prior_release:
        print(f"  Dropped edges (prior-release): {dropped_prior_release}")
    if dropped_dangling:
        print(f"  Dropped edges (dangling): {dropped_dangling}")
    print(f"  Total nodes: {len(dag['nodes'])}, edges: {len(dag['edges'])}, stories: {len(dag['user_stories'])}")
    if skipped_nodes:
        print(f"  Skipped node IDs: {sorted(skipped_nodes)}")


def validate_stories(dag_path, stories_path, release=None):
    """Validate that feature stories exist in the DAG.

    If release is given, only stories with that release number are checked.
    Otherwise all stories are checked.
    """
    with open(dag_path) as f:
        dag = json.load(f)
    with open(stories_path) as f:
        stories = json.load(f)

    dag_story_ids = {s["id"] for s in dag["user_stories"]}

    # stories.json is a list of story objects with "id" fields
    if isinstance(stories, list):
        source_stories = stories
    elif isinstance(stories, dict) and "stories" in stories:
        source_stories = stories["stories"]
    else:
        source_stories = list(stories.values()) if isinstance(stories, dict) else []

    # Filter to current release if specified
    if release is not None:
        source_stories = [s for s in source_stories if s.get("release") == release]

    missing = []
    for story in source_stories:
        sid = story.get("id", story.get("story_id", ""))
        if not sid:
            continue
        # Skip infrastructure stories — they're derived, not from the source
        if sid.startswith("INFRA-"):
            continue
        if sid not in dag_story_ids:
            title = story.get("title", "")
            missing.append(f"  {sid}: {title}")

    feature_count = sum(1 for s in source_stories
                        if not s.get("id", "").startswith("INFRA-"))
    release_label = f" (release {release})" if release is not None else ""

    if missing:
        print(f"ERROR: {len(missing)} of {feature_count} feature story(ies) missing from DAG{release_label}:")
        for m in missing:
            print(m)
        sys.exit(1)
    else:
        dag_feature_count = sum(1 for s in dag["user_stories"]
                               if s.get("category") != "infrastructure")
        print(f"All {feature_count} feature stories{release_label} present in DAG ({dag_feature_count} feature stories in DAG)")


def main():
    parser = argparse.ArgumentParser(
        description="Merge a backward mapping batch into the project DAG")
    parser.add_argument("dag",
                        help="Path to project DAG (created if missing)")
    parser.add_argument("batch_output", nargs="?", default=None,
                        help="Path to batch output JSON")
    parser.add_argument("--release", type=int, default=None,
                        help="Release number for this batch")
    parser.add_argument("--seal-release", action="store_true",
                        help="Add cross-release terminal→entry edges after all batches for a release are merged")
    parser.add_argument("--validate-stories", metavar="STORIES_JSON", default=None,
                        help="Validate all feature stories from STORIES_JSON exist in the DAG")
    parser.add_argument("--project-name", default=None,
                        help="Project name to set on the DAG (used when creating a new DAG)")
    args = parser.parse_args()

    if args.validate_stories:
        validate_stories(args.dag, args.validate_stories, release=args.release)
    elif args.seal_release:
        seal_release(args.dag)
    elif args.batch_output:
        merge_batch(args.dag, args.batch_output, args.release, args.project_name)
    else:
        parser.error("Provide a batch_output file, --seal-release, or --validate-stories")


if __name__ == "__main__":
    main()
