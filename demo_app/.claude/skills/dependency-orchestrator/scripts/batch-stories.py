#!/usr/bin/env python3
"""Deterministic batching of stories.json into batch files + manifest.

Reads a normalized stories.json, splits stories into batches of ≤10,
keeping stories from the same feature_group together where possible.
Stories are sorted by release first, then grouped by feature_group.

Usage:
    python3 batch-stories.py <stories.json> <output-dir>

Outputs:
    <output-dir>/batch-1.json, batch-2.json, ...
    <output-dir>/batch-manifest.json
"""

import json
import sys
import os
import argparse
from collections import defaultdict

MAX_BATCH_SIZE = 10
MAX_BATCH_BYTES = 30_000  # ~30KB — keeps mapper output within token limits


def _batch_bytes(batch):
    """Estimate serialized size of a batch."""
    return len(json.dumps(batch).encode())


def _should_flush(current_batch, additions=None):
    """Check if the batch should be flushed before adding more stories."""
    if not current_batch:
        return False
    count_after = len(current_batch) + (len(additions) if additions else 0)
    if count_after > MAX_BATCH_SIZE:
        return True
    if additions:
        size_after = _batch_bytes(current_batch + additions)
    else:
        size_after = _batch_bytes(current_batch)
    return size_after > MAX_BATCH_BYTES


def batch_stories(stories_path, output_dir):
    with open(stories_path) as f:
        data = json.load(f)

    # Accept either a list or {"stories": [...]}
    if isinstance(data, list):
        stories = data
    elif isinstance(data, dict) and "stories" in data:
        stories = data["stories"]
    else:
        print(f"ERROR: Unexpected stories.json format — expected a list or {{\"stories\": [...]}}",
              file=sys.stderr)
        sys.exit(1)

    if not stories:
        print("ERROR: stories.json contains no stories", file=sys.stderr)
        sys.exit(1)

    os.makedirs(output_dir, exist_ok=True)

    # Group by release, then by feature_group within each release
    by_release = defaultdict(list)
    for story in stories:
        release = story.get("release", 1)
        by_release[release].append(story)

    batches = []
    batch_num = 0

    for release in sorted(by_release.keys()):
        release_stories = by_release[release]

        # Group by feature_group within this release
        by_group = defaultdict(list)
        ungrouped = []
        for story in release_stories:
            group = story.get("group") or story.get("feature_group")
            if group:
                by_group[group].append(story)
            else:
                ungrouped.append(story)

        # Build batches: pack groups into batches of ≤MAX_BATCH_SIZE
        current_batch = []

        for group_id in sorted(by_group.keys()):
            group_stories = by_group[group_id]

            # If this group alone exceeds limits, split it into chunks
            if len(group_stories) > MAX_BATCH_SIZE or _batch_bytes(group_stories) > MAX_BATCH_BYTES:
                # Flush current batch first
                if current_batch:
                    batch_num += 1
                    batches.append((batch_num, release, current_batch))
                    current_batch = []

                # Split group by both count and byte size
                chunk = []
                for story in group_stories:
                    if _should_flush(chunk, [story]):
                        batch_num += 1
                        batches.append((batch_num, release, chunk))
                        chunk = []
                    chunk.append(story)
                if chunk:
                    batch_num += 1
                    batches.append((batch_num, release, chunk))
                continue

            # If adding this group would exceed limits, flush current batch
            if _should_flush(current_batch, group_stories):
                batch_num += 1
                batches.append((batch_num, release, current_batch))
                current_batch = []

            current_batch.extend(group_stories)

        # Add ungrouped stories
        for story in ungrouped:
            if _should_flush(current_batch, [story]):
                batch_num += 1
                batches.append((batch_num, release, current_batch))
                current_batch = []
            current_batch.append(story)

        # Flush remaining
        if current_batch:
            batch_num += 1
            batches.append((batch_num, release, current_batch))
            current_batch = []

    # Write batch files and manifest
    manifest_batches = []
    total_stories = 0

    for num, release, batch in batches:
        batch_file = f"batch-{num}.json"
        batch_path = os.path.join(output_dir, batch_file)

        with open(batch_path, "w") as f:
            json.dump(batch, f, indent=2)
            f.write("\n")

        manifest_batches.append({
            "file": batch_file,
            "release": release,
            "count": len(batch)
        })
        total_stories += len(batch)

    manifest = {
        "batches": manifest_batches,
        "total_stories": total_stories
    }

    manifest_path = os.path.join(output_dir, "batch-manifest.json")
    with open(manifest_path, "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")

    print(f"Batched {total_stories} stories into {len(batches)} batch(es)")
    for entry in manifest_batches:
        print(f"  {entry['file']}: {entry['count']} stories (release {entry['release']})")


def main():
    parser = argparse.ArgumentParser(
        description="Split stories.json into batches of ≤20 stories")
    parser.add_argument("stories", help="Path to stories.json")
    parser.add_argument("output_dir", help="Output directory for batch files and manifest")
    args = parser.parse_args()

    batch_stories(args.stories, args.output_dir)


if __name__ == "__main__":
    main()
