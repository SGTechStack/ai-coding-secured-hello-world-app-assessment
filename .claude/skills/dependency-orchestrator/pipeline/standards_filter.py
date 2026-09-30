"""Filter configs/standards.md to only include in-scope standard categories.

Keeps: in-scope category sections (catalog + dependencies + node mappings),
       "How Standards Drive the DAG" (always), "All other nodes" (always).
Drops: out-of-scope category sections.
"""

from __future__ import annotations

import re

# Map category names to the heading patterns they appear under in standards.md.
# Each category can match in three places: Standards Catalog, Dependencies, and
# Node to Standard Mapping.
_CATEGORY_HEADING_KEYWORDS = {
    "User": "User Standards",
    "MFA": "MFA Standards",
    "MCC": "MCC Standards",
    "Report": "Report Standards",
    "File": "File Standards",
    "Interface": "Interface Standards",
    "Logging": "Logging Standards",
}

# Sections always included regardless of scope
_ALWAYS_INCLUDE = {
    "How Standards Drive the DAG",
    "All other nodes",
    "Foundation nodes",
    "Concept-based mapping",
}


def filter_sections(standards_md: str, in_scope: list[str]) -> str:
    """Return standards_md with only in-scope category sections retained.

    The structure of standards.md has three top-level sections that contain
    per-category subsections:
      - ## Standards Catalog  →  ### User Standards, ### MFA Standards, ...
      - ## Dependencies Between Standards  →  ### User Standards, ### MFA Standards, ...
      - ## Node to Standard Mapping  →  ### Foundation nodes, ### Concept-based mapping, ### All other nodes

    We keep a ### subsection if:
      1. Its heading matches an in-scope category, OR
      2. Its heading is in _ALWAYS_INCLUDE
    """
    in_scope_keywords = set()
    for cat in in_scope:
        kw = _CATEGORY_HEADING_KEYWORDS.get(cat)
        if kw:
            in_scope_keywords.add(kw)

    lines = standards_md.split("\n")
    result = []
    skip = False
    skip_level = 0

    for line in lines:
        heading_match = re.match(r'^(#{1,4})\s+(.*)', line)

        if heading_match:
            level = len(heading_match.group(1))
            title = heading_match.group(2).strip()
            # Strip parenthetical from heading, e.g. "MFA Standards (8 build paths)"
            clean_title = re.sub(r'\s*\(.*?\)\s*$', '', title)
            # Also strip backtick-quoted bits like "(`Appfw-User-Standards`)"
            clean_title = re.sub(r'\s*\(`[^`]*`\)\s*$', '', clean_title)

            if level <= 2:
                # Top-level or second-level headings are always included
                skip = False
                result.append(line)
                continue

            # Level 3+ subsection: check if it should be included
            is_always = clean_title in _ALWAYS_INCLUDE
            is_in_scope = any(kw in clean_title for kw in in_scope_keywords)

            if is_always or is_in_scope:
                skip = False
                result.append(line)
                continue
            else:
                # Check if this is a category subsection we should skip
                is_category = any(kw in clean_title for kw in _CATEGORY_HEADING_KEYWORDS.values())
                if is_category:
                    skip = True
                    skip_level = level
                    continue
                else:
                    # Unknown subsection — include it
                    skip = False
                    result.append(line)
                    continue

        if skip:
            # Check if we hit a heading at the same or higher level (would end skip)
            continue

        result.append(line)

    return "\n".join(result)
