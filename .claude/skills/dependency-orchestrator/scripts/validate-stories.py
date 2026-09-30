#!/usr/bin/env python3
"""Validate a structured user stories YAML file against the schema.

Replaces the LLM parser subagent + verify-parse.py + precount-stories.py.
Runs as a fail-fast gate before any orchestration work.

Usage:
    python3 validate-stories.py <stories.yaml>

Exit codes:
    0 — valid, prints summary JSON to stdout
    1 — validation errors, prints diagnostics to stderr

Stdout on success (JSON):
    {
      "story_count": 47,
      "group_count": 3,
      "release_count": 2,
      "groups": [{"id": "...", "name": "...", "priority": 1}, ...],
      "releases": [{"number": 1}, ...],
      "story_ids": ["1.1", "1.2", ...]
    }
"""

import json
import subprocess
import sys
import argparse
from pathlib import Path

try:
    import yaml
except ImportError:
    subprocess.check_call(
        [sys.executable, "-m", "pip", "install", "--quiet",
         "--break-system-packages", "pyyaml"])
    import yaml

# Fields allowed per object type (matches additionalProperties: false in schema)
ALLOWED_GROUP_FIELDS = {"id", "name", "priority"}
ALLOWED_RELEASE_FIELDS = {"number", "description"}
ALLOWED_STORY_FIELDS = {"id", "title", "acceptance_criteria", "group", "release"}


def load_yaml(path: str) -> dict:
    """Load and parse the YAML file."""
    with open(path, encoding="utf-8") as f:
        data = yaml.safe_load(f)
    if not isinstance(data, dict):
        raise ValueError(f"Expected a YAML mapping (dict) at top level, got {type(data).__name__}")
    return data


def check_structure(data: dict) -> list[str]:
    """Validate structure and field types per the JSON schema."""
    errors = []

    # Top-level: only groups, releases, stories allowed
    allowed_top = {"groups", "releases", "stories"}
    extra_top = set(data.keys()) - allowed_top
    if extra_top:
        errors.append(f"Unknown top-level field(s): {sorted(extra_top)}")

    # ── stories (required, non-empty array) ──────────────────────────
    if "stories" not in data:
        errors.append("Missing required field: 'stories'")
        return errors

    if not isinstance(data["stories"], list) or len(data["stories"]) == 0:
        errors.append("'stories' must be a non-empty array")
        return errors

    for i, story in enumerate(data["stories"]):
        prefix = f"stories[{i}]"
        if not isinstance(story, dict):
            errors.append(f"{prefix}: must be an object")
            continue
        extra = set(story.keys()) - ALLOWED_STORY_FIELDS
        if extra:
            errors.append(f"{prefix}: unknown field(s): {sorted(extra)}")
        for field in ("id", "title", "acceptance_criteria"):
            if field not in story:
                errors.append(f"{prefix}: missing required field '{field}'")
        if "id" in story and (not isinstance(story["id"], str) or not story["id"]):
            errors.append(f"{prefix}: 'id' must be a non-empty string")
        if "title" in story and (not isinstance(story["title"], str) or not story["title"]):
            errors.append(f"{prefix}: 'title' must be a non-empty string")
        if "acceptance_criteria" in story:
            ac = story["acceptance_criteria"]
            if not isinstance(ac, list) or len(ac) == 0:
                errors.append(f"{prefix}: 'acceptance_criteria' must be a non-empty array")
            elif not all(isinstance(c, str) and c for c in ac):
                errors.append(f"{prefix}: all acceptance_criteria must be non-empty strings")
        if "group" in story and (not isinstance(story["group"], str) or not story["group"]):
            errors.append(f"{prefix}: 'group' must be a non-empty string")
        if "release" in story:
            r = story["release"]
            if not isinstance(r, int) or r < 1:
                errors.append(f"{prefix}: 'release' must be an integer >= 1")

    # ── groups (optional) ────────────────────────────────────────────
    if "groups" in data:
        if not isinstance(data["groups"], list):
            errors.append("'groups' must be an array")
        else:
            for i, group in enumerate(data["groups"]):
                prefix = f"groups[{i}]"
                if not isinstance(group, dict):
                    errors.append(f"{prefix}: must be an object")
                    continue
                extra = set(group.keys()) - ALLOWED_GROUP_FIELDS
                if extra:
                    errors.append(f"{prefix}: unknown field(s): {sorted(extra)}")
                for field in ("id", "name"):
                    if field not in group:
                        errors.append(f"{prefix}: missing required field '{field}'")
                if "id" in group:
                    gid = group["id"]
                    if not isinstance(gid, str) or not gid:
                        errors.append(f"{prefix}: 'id' must be a non-empty string")
                    elif not _is_snake_case(gid):
                        errors.append(f"{prefix}: 'id' must be snake_case (got '{gid}')")
                if "name" in group and (not isinstance(group["name"], str) or not group["name"]):
                    errors.append(f"{prefix}: 'name' must be a non-empty string")
                if "priority" in group:
                    p = group["priority"]
                    if not isinstance(p, int) or p < 1:
                        errors.append(f"{prefix}: 'priority' must be an integer >= 1")

    # ── releases (optional) ──────────────────────────────────────────
    if "releases" in data:
        if not isinstance(data["releases"], list):
            errors.append("'releases' must be an array")
        else:
            for i, release in enumerate(data["releases"]):
                prefix = f"releases[{i}]"
                if not isinstance(release, dict):
                    errors.append(f"{prefix}: must be an object")
                    continue
                extra = set(release.keys()) - ALLOWED_RELEASE_FIELDS
                if extra:
                    errors.append(f"{prefix}: unknown field(s): {sorted(extra)}")
                if "number" not in release:
                    errors.append(f"{prefix}: missing required field 'number'")
                elif not isinstance(release["number"], int) or release["number"] < 1:
                    errors.append(f"{prefix}: 'number' must be an integer >= 1")
                if "description" in release and not isinstance(release["description"], str):
                    errors.append(f"{prefix}: 'description' must be a string")

    return errors


def _is_snake_case(s: str) -> bool:
    import re
    return bool(re.match(r'^[a-z][a-z0-9_]*$', s))


def check_unique_ids(data: dict) -> list[str]:
    """Check for duplicate IDs across stories, groups, and releases."""
    errors = []

    story_ids: dict[str, int] = {}
    for s in data.get("stories", []):
        sid = s.get("id", "")
        story_ids[sid] = story_ids.get(sid, 0) + 1
    for sid, count in story_ids.items():
        if count > 1:
            errors.append(f"Duplicate story ID '{sid}' appears {count} times")

    group_ids: dict[str, int] = {}
    for g in data.get("groups", []):
        gid = g.get("id", "")
        group_ids[gid] = group_ids.get(gid, 0) + 1
    for gid, count in group_ids.items():
        if count > 1:
            errors.append(f"Duplicate group ID '{gid}' appears {count} times")

    release_nums: dict[int, int] = {}
    for r in data.get("releases", []):
        rn = r.get("number", 0)
        release_nums[rn] = release_nums.get(rn, 0) + 1
    for rn, count in release_nums.items():
        if count > 1:
            errors.append(f"Duplicate release number {rn} appears {count} times")

    return errors


def check_referential_integrity(data: dict) -> list[str]:
    """Check that story group/release references resolve to defined entities."""
    errors = []

    defined_groups = {g["id"] for g in data.get("groups", []) if "id" in g}
    has_groups_section = "groups" in data

    defined_releases = {r["number"] for r in data.get("releases", []) if "number" in r}
    has_releases_section = "releases" in data

    for story in data.get("stories", []):
        sid = story.get("id", "?")

        # story.group → groups[].id
        group = story.get("group")
        if group is not None and has_groups_section and group not in defined_groups:
            errors.append(
                f"Story '{sid}' references group '{group}' which is not defined. "
                f"Valid groups: {sorted(defined_groups)}"
            )

        # story.release → releases[].number
        release = story.get("release")
        if release is not None and has_releases_section and release not in defined_releases:
            errors.append(
                f"Story '{sid}' references release {release} which is not defined. "
                f"Valid releases: {sorted(defined_releases)}"
            )

    # Stories reference groups but no groups section exists
    if not has_groups_section:
        story_groups = {s.get("group") for s in data.get("stories", []) if s.get("group") is not None}
        if story_groups:
            errors.append(
                f"Stories reference group(s) {sorted(story_groups)} but no 'groups' "
                f"section is defined. Add a 'groups' section or remove 'group' from stories."
            )

    # Stories reference releases but no releases section exists
    if not has_releases_section:
        story_releases = {s.get("release") for s in data.get("stories", []) if s.get("release") is not None}
        if story_releases:
            errors.append(
                f"Stories reference release(s) {sorted(story_releases)} but no 'releases' "
                f"section is defined. Add a 'releases' section or remove 'release' from stories."
            )

    # Unused groups
    if has_groups_section:
        used_groups = {s.get("group") for s in data.get("stories", []) if s.get("group") is not None}
        for gid in sorted(defined_groups - used_groups):
            errors.append(f"Group '{gid}' is defined but no stories reference it")

    # Unused releases
    if has_releases_section:
        used_releases = {s.get("release") for s in data.get("stories", []) if s.get("release") is not None}
        for rn in sorted(defined_releases - used_releases):
            errors.append(f"Release {rn} is defined but no stories reference it")

    return errors


def check_release_numbering(data: dict) -> list[str]:
    """Check that release numbers are sequential starting from 1."""
    releases = data.get("releases", [])
    if not releases:
        return []

    numbers = sorted(r["number"] for r in releases if "number" in r)
    expected = list(range(1, len(numbers) + 1))
    if numbers != expected:
        return [
            f"Release numbers must be sequential starting from 1. "
            f"Got: {numbers}, expected: {expected}"
        ]
    return []


def validate_stories(yaml_path: str) -> dict:
    """Validate a stories YAML file. Returns summary dict or raises SystemExit."""
    try:
        data = load_yaml(yaml_path)
    except yaml.YAMLError as e:
        print(f"YAML syntax error: {e}", file=sys.stderr)
        sys.exit(1)
    except ValueError as e:
        print(f"Format error: {e}", file=sys.stderr)
        sys.exit(1)

    errors = []
    errors.extend(check_structure(data))
    errors.extend(check_unique_ids(data))
    errors.extend(check_referential_integrity(data))
    errors.extend(check_release_numbering(data))

    if errors:
        print(f"VALIDATION FAILED: {len(errors)} error(s)\n", file=sys.stderr)
        for i, err in enumerate(errors, 1):
            print(f"  {i}. {err}", file=sys.stderr)
        sys.exit(1)

    stories = data["stories"]
    groups = data.get("groups", [])
    releases = data.get("releases", [])

    summary = {
        "story_count": len(stories),
        "group_count": len(groups) if groups else 1,
        "release_count": len(releases) if releases else 1,
        "groups": groups if groups else [{"id": "default", "name": "Default", "priority": 1}],
        "releases": releases if releases else [{"number": 1}],
        "story_ids": [s["id"] for s in stories],
    }

    return summary


def main():
    parser = argparse.ArgumentParser(
        description="Validate a user stories YAML file")
    parser.add_argument("stories_yaml", help="Path to the stories YAML file")
    args = parser.parse_args()

    summary = validate_stories(args.stories_yaml)

    print(json.dumps(summary, indent=2))

    print(f"\nVALIDATION PASSED", file=sys.stderr)
    print(f"  Stories:  {summary['story_count']}", file=sys.stderr)
    print(f"  Groups:   {summary['group_count']}", file=sys.stderr)
    print(f"  Releases: {summary['release_count']}", file=sys.stderr)


if __name__ == "__main__":
    main()
