#!/usr/bin/env python3
"""Verify parsed stories.json against the raw file using heuristic precount.

Runs after the parser subagent writes stories.json. Checks that the parsed
story count is within tolerance of the heuristic count from the raw file.

Usage:
    python3 verify-parse.py <stories.json> <raw-stories-file> [--user-count N] [--exact]

Exit codes:
    0 — count is within tolerance
    1 — count mismatch (parsed count outside tolerance)

If --user-count is provided, also checks against the user-provided count
using exact or approximate (+-10%) matching.
"""

import json
import re
import sys
import argparse


def _count_nesting_levels(lines):
    """Count items at each nesting depth in a hierarchically numbered file.

    Returns a dict of {depth: count}.
    """
    pattern = re.compile(
        r'^\s{0,6}(?:#{1,6}\s+)?(\d+(?:\.\d+)*)[\.\):]?\s+\S'
    )
    depth_counts = {}
    for line in lines:
        m = pattern.match(line)
        if m:
            number_part = m.group(1)
            depth = number_part.count('.') + 1
            depth_counts[depth] = depth_counts.get(depth, 0) + 1
    return depth_counts


def heuristic_count(file_path, user_count=None):
    """Run heuristics, return (best_guess, method_description)."""
    with open(file_path, encoding="utf-8", errors="replace") as f:
        content = f.read()

    lines = content.splitlines()

    # Heuristic 1: Story ID patterns
    id_pattern = re.compile(r'\b[A-Z]{2,10}[-_]\d{1,5}\b')
    id_matches = set()
    for line in lines:
        for match in id_pattern.finditer(line):
            id_matches.add(match.group())

    # Heuristic 2: "As a..." lines
    as_a_pattern = re.compile(r'^\s*(?:\d+[\.\)]\s*)?[*-]?\s*(?:"|\')?As an?\s', re.IGNORECASE)
    as_a_count = sum(1 for line in lines if as_a_pattern.match(line))

    # Heuristic 3: Nesting-level analysis
    depth_counts = _count_nesting_levels(lines)
    numbered_count = depth_counts.get(1, 0)
    hierarchical_count = sum(c for d, c in depth_counts.items() if d >= 2)

    # Heuristic 4: Bullet points
    bullet_pattern = re.compile(r'^\s{0,3}[-*]\s+\S')
    bullet_count = sum(1 for line in lines if bullet_pattern.match(line))

    # Pick best guess — use user_count to resolve nesting ambiguity
    if user_count and len(depth_counts) > 1:
        best_depth = min(
            depth_counts,
            key=lambda d: abs(depth_counts[d] - user_count)
        )
        return depth_counts[best_depth], f"nesting depth {best_depth} (closest to user count {user_count})"
    elif len(id_matches) > 0:
        return len(id_matches), "story IDs"
    elif as_a_count > 0:
        return as_a_count, "'As a...' lines"
    elif hierarchical_count > numbered_count:
        return hierarchical_count, "hierarchical numbered items"
    elif numbered_count > 0:
        return numbered_count, "numbered list items"
    else:
        return bullet_count, "bullet points"


def load_parsed(stories_path):
    """Load stories from parsed stories.json. Returns the list."""
    with open(stories_path) as f:
        data = json.load(f)

    if isinstance(data, list):
        return data
    elif isinstance(data, dict) and "stories" in data:
        return data["stories"]
    else:
        print("ERROR: Unexpected stories.json format", file=sys.stderr)
        sys.exit(1)


def check_duplicate_ids(stories):
    """Return list of duplicate IDs, if any."""
    seen = {}
    for s in stories:
        sid = s.get("id", "")
        seen[sid] = seen.get(sid, 0) + 1
    return {sid: count for sid, count in seen.items() if count > 1}


def main():
    parser = argparse.ArgumentParser(
        description="Verify parsed story count against raw file heuristics")
    parser.add_argument("stories_json", help="Path to parsed stories.json")
    parser.add_argument("raw_file", help="Path to the raw user stories file")
    parser.add_argument("--user-count", type=int, default=None,
                        help="User-provided story count for additional validation")
    parser.add_argument("--exact", action="store_true",
                        help="Require exact match against user-provided count (default: +-10%%)")
    args = parser.parse_args()

    stories = load_parsed(args.stories_json)
    parsed_count = len(stories)
    heuristic, method = heuristic_count(args.raw_file, user_count=args.user_count)

    print(f"Parsed stories: {parsed_count}")
    print(f"Heuristic count: {heuristic} (based on {method})")

    errors = []

    # Check 0: duplicate IDs
    dupes = check_duplicate_ids(stories)
    if dupes:
        dupe_detail = ", ".join(f"'{k}' x{v}" for k, v in dupes.items())
        errors.append(f"Duplicate story IDs: {dupe_detail}")
        print(f"  FAIL: {errors[-1]}")
    else:
        print(f"  OK: all story IDs are unique")

    # Check 1: parsed vs heuristic (90% threshold)
    if heuristic > 0:
        ratio = parsed_count / heuristic
        if ratio < 0.9:
            errors.append(
                f"Parsed count ({parsed_count}) is below 90% of heuristic "
                f"count ({heuristic}). Ratio: {ratio:.1%}")
            print(f"  FAIL: {errors[-1]}")
        else:
            print(f"  OK: parsed/heuristic ratio = {ratio:.1%}")

    # Check 2: parsed vs user-provided count (if given)
    if args.user_count is not None:
        if args.exact:
            if parsed_count != args.user_count:
                errors.append(
                    f"Exact mismatch: parsed {parsed_count} != "
                    f"user-provided {args.user_count}")
                print(f"  FAIL: {errors[-1]}")
            else:
                print(f"  OK: exact match with user count ({args.user_count})")
        else:
            tolerance = args.user_count * 0.1
            diff = abs(parsed_count - args.user_count)
            if diff > tolerance:
                errors.append(
                    f"Approximate mismatch: parsed {parsed_count} vs "
                    f"user-provided {args.user_count} (diff {diff}, "
                    f"tolerance +-{tolerance:.0f})")
                print(f"  FAIL: {errors[-1]}")
            elif diff > 0:
                print(f"  WARN: parsed {parsed_count} vs user {args.user_count} "
                      f"(within +-10% tolerance)")
            else:
                print(f"  OK: matches user count ({args.user_count})")

    if errors:
        print(f"\nVERIFICATION FAILED: {len(errors)} issue(s)")
        sys.exit(1)
    else:
        print(f"\nVERIFICATION PASSED: {parsed_count} stories")


if __name__ == "__main__":
    main()
