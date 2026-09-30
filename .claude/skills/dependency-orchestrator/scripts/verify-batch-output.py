#!/usr/bin/env python3
"""Verify a backward mapping batch output against the batch manifest.

Runs after each backward mapping subagent completes. Checks that the story
count in batch-N-output.json matches the count recorded in the manifest
for that batch.

Usage:
    python3 verify-batch-output.py <batch-output.json> <batch-manifest.json> [--batch-num N]

If --batch-num is not provided, it is inferred from the output filename
(e.g., batch-3-output.json → batch 3).

Exit codes:
    0 — story count matches
    1 — story count mismatch or structural error
"""

import json
import re
import sys
import argparse
import os


def infer_batch_num(output_path):
    """Extract batch number from filename like batch-3-output.json."""
    basename = os.path.basename(output_path)
    match = re.match(r'batch-(\d+)-output\.json', basename)
    if match:
        return int(match.group(1))
    return None


def main():
    parser = argparse.ArgumentParser(
        description="Verify batch output story count against manifest")
    parser.add_argument("batch_output", help="Path to batch-N-output.json")
    parser.add_argument("manifest", help="Path to batch-manifest.json")
    parser.add_argument("--batch-num", type=int, default=None,
                        help="Batch number (inferred from filename if omitted)")
    args = parser.parse_args()

    batch_num = args.batch_num or infer_batch_num(args.batch_output)
    if batch_num is None:
        print("ERROR: Could not determine batch number. Use --batch-num N.",
              file=sys.stderr)
        sys.exit(1)

    # Load manifest
    with open(args.manifest) as f:
        manifest = json.load(f)

    # Find this batch in manifest
    batch_entry = None
    for entry in manifest["batches"]:
        # Match by batch number from filename
        entry_file = entry["file"]
        entry_match = re.match(r'batch-(\d+)\.json', entry_file)
        if entry_match and int(entry_match.group(1)) == batch_num:
            batch_entry = entry
            break

    if batch_entry is None:
        print(f"ERROR: Batch {batch_num} not found in manifest", file=sys.stderr)
        sys.exit(1)

    expected_count = batch_entry["count"]

    # Load batch output
    with open(args.batch_output) as f:
        batch_output = json.load(f)

    # Count stories in the output
    output_stories = batch_output.get("user_stories", [])
    actual_count = len(output_stories)

    # Check for stories with empty owned_nodes
    empty_stories = [
        s["id"] for s in output_stories
        if not s.get("owned_nodes")
    ]

    print(f"Batch {batch_num}: expected {expected_count} stories, "
          f"got {actual_count} in output")

    errors = []

    if actual_count != expected_count:
        errors.append(
            f"Story count mismatch: manifest says {expected_count}, "
            f"output has {actual_count}")

    if empty_stories:
        errors.append(
            f"{len(empty_stories)} story/ies with empty owned_nodes: "
            f"{empty_stories}")

    # Check that output has required structural fields
    required_fields = ["nodes", "edges", "user_stories"]
    missing_fields = [f for f in required_fields if f not in batch_output]
    if missing_fields:
        errors.append(f"Missing required fields: {missing_fields}")

    # Check node count sanity — each story should produce at least 1 node
    node_count = len(batch_output.get("nodes", []))
    if node_count < actual_count and actual_count > 0:
        errors.append(
            f"Suspiciously few nodes ({node_count}) for "
            f"{actual_count} stories — expected at least 1 per story")

    # Check for cycles in this batch's edges
    edges = batch_output.get("edges", [])
    node_ids = {n["id"] for n in batch_output.get("nodes", [])}
    if edges and node_ids:
        adj = {}
        in_deg = {nid: 0 for nid in node_ids}
        for e in edges:
            src = e["from"] if isinstance(e, dict) else e[0]
            dst = e["to"] if isinstance(e, dict) else e[1]
            if src in node_ids and dst in node_ids:
                adj.setdefault(src, []).append(dst)
                in_deg[dst] = in_deg.get(dst, 0) + 1
        # Kahn's
        from collections import deque
        q = deque(nid for nid in node_ids if in_deg.get(nid, 0) == 0)
        processed = set()
        while q:
            n = q.popleft()
            processed.add(n)
            for s in adj.get(n, []):
                in_deg[s] -= 1
                if in_deg[s] == 0:
                    q.append(s)
        stuck = node_ids - processed
        if stuck:
            # Trace the cycle
            start = min(stuck)
            path, node, visited = [], start, set()
            while node not in visited:
                visited.add(node)
                path.append(node)
                node = next((s for s in adj.get(node, []) if s in stuck), node)
            ci = path.index(node)
            cycle = path[ci:] + [node]
            errors.append(f"Cycle detected: {' → '.join(cycle)}")

    if errors:
        print(f"\nVERIFICATION FAILED:")
        for err in errors:
            print(f"  ERROR: {err}")

        # Emit structured remediation hints for the retry prompt
        if empty_stories:
            print(f"\nREMEDIATION: The following {len(empty_stories)} story/ies "
                  f"have empty owned_nodes and MUST each own at least one node. "
                  f"For each story, derive its backward-mapping chain and assign "
                  f"owned_nodes. Stories that appear generic (Reports, Dashboard, "
                  f"Manage X) still need their own dedicated fe_ and/or be_ nodes.")
            # Print each story's title so the mapper knows exactly what to fix
            for story in output_stories:
                if str(story.get("id")) in [str(s) for s in empty_stories]:
                    print(f"  - Story {story['id']}: \"{story.get('title', 'unknown')}\" "
                          f"→ needs owned_nodes")

        sys.exit(1)
    else:
        print(f"VERIFICATION PASSED: {actual_count} stories, "
              f"{node_count} nodes")


if __name__ == "__main__":
    main()
