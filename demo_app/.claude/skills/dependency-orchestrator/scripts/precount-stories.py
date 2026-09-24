#!/usr/bin/env python3
"""Heuristic precount of user stories from a raw file.

Uses simple heuristics to count stories WITHOUT understanding their
structure. This is a fallback validation when the parser subagent's
count doesn't match the user-provided count.

When --user-count is provided, the script analyzes nesting levels and
picks the level whose count is closest to the user's estimate.

Usage:
    python3 precount-stories.py <raw-stories-file> [--user-count N]

Outputs the count from each heuristic so the caller can judge which is
closest to their expectation.
"""

import re
import sys
import argparse


def _count_nesting_levels(lines):
    """Count items at each nesting depth in a hierarchically numbered file.

    Detects patterns like:
        1. Section          → depth 1
        1.1 Story           → depth 2
        1.1.1 Sub-story     → depth 3

    Also handles markdown heading prefixes (e.g., "## 1.", "### 1.1").

    Returns a dict of {depth: count}, e.g., {1: 22, 2: 105, 3: 12}.
    """
    # Match any numbered item, with optional heading prefix
    # Captures the number portion (e.g., "1", "14.1", "14.1.2")
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


def precount(file_path, user_count=None):
    with open(file_path, encoding="utf-8", errors="replace") as f:
        content = f.read()

    lines = content.splitlines()

    # Heuristic 1: Story ID patterns (US-001, JIRA-42, AFW-101, etc.)
    id_pattern = re.compile(
        r'\b[A-Z]{2,10}[-_]\d{1,5}\b'
    )
    id_matches = set()
    for line in lines:
        for match in id_pattern.finditer(line):
            id_matches.add(match.group())

    # Heuristic 2: "As a..." lines
    as_a_pattern = re.compile(r'^\s*(?:\d+[\.\)]\s*)?[*-]?\s*(?:"|\')?As an?\s', re.IGNORECASE)
    as_a_count = sum(1 for line in lines if as_a_pattern.match(line))

    # Heuristic 3: Nesting-level analysis
    depth_counts = _count_nesting_levels(lines)
    # Flatten for backward compat: depth 1 = "numbered", depth 2+ = "hierarchical"
    numbered_count = depth_counts.get(1, 0)
    hierarchical_count = sum(c for d, c in depth_counts.items() if d >= 2)

    # Heuristic 4: Markdown headings that look like stories
    heading_pattern = re.compile(r'^#{1,4}\s+.*(?:story|user|feature|US|AFW|JIRA)', re.IGNORECASE)
    heading_count = sum(1 for line in lines if heading_pattern.match(line))

    # Heuristic 5: Bullet points (top-level only, - or *)
    bullet_pattern = re.compile(r'^\s{0,3}[-*]\s+\S')
    bullet_count = sum(1 for line in lines if bullet_pattern.match(line))

    print(f"Precount results for: {file_path}")
    print(f"  Story IDs (e.g., US-001, AFW-101):  {len(id_matches)}")
    print(f"  'As a...' lines:                    {as_a_count}")
    print(f"  Numbered items (top-level):          {numbered_count}")
    print(f"  Hierarchical numbered items:         {hierarchical_count}")
    if depth_counts:
        for depth in sorted(depth_counts):
            print(f"    depth {depth} (e.g., {'N' + '.N' * (depth - 1)}):  {depth_counts[depth]}")
    print(f"  Story-like headings:                 {heading_count}")
    print(f"  Top-level bullet points:             {bullet_count}")
    print(f"  Total lines in file:                 {len(lines)}")

    # --- Best guess logic ---

    # If user count is provided and we have multiple nesting levels,
    # pick the level whose count is closest to the user's estimate
    if user_count and len(depth_counts) > 1:
        best_depth = min(
            depth_counts,
            key=lambda d: abs(depth_counts[d] - user_count)
        )
        best = depth_counts[best_depth]
        method = f"nesting depth {best_depth} (closest to user count {user_count})"
        print(f"\n  Nesting analysis: depth {best_depth} has {best} items "
              f"(user said ~{user_count})")
    elif len(id_matches) > 0:
        best = len(id_matches)
        method = "story IDs"
    elif as_a_count > 0:
        best = as_a_count
        method = "'As a...' lines"
    elif hierarchical_count > numbered_count:
        best = hierarchical_count
        method = "hierarchical numbered items"
    elif numbered_count > 0:
        best = numbered_count
        method = "numbered list items"
    else:
        best = bullet_count
        method = "bullet points"

    print(f"\n  Best guess: {best} stories (based on {method})")
    return best


def main():
    parser = argparse.ArgumentParser(
        description="Heuristic story count from a raw user stories file")
    parser.add_argument("file", help="Path to the raw user stories file")
    parser.add_argument("--user-count", type=int, default=None,
                        help="User-provided story count to guide nesting-level selection")
    args = parser.parse_args()

    precount(args.file, user_count=args.user_count)


if __name__ == "__main__":
    main()
