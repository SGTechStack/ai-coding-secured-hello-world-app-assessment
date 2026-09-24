#!/usr/bin/env python3
"""Aggregate skill invocation costs from JSONL files.

Usage:
  aggregate.py                        # default: skill-invocations.jsonl in script dir
  aggregate.py path/to/file.jsonl     # single file
  aggregate.py path/to/dir/           # all *.jsonl files in directory
"""

import json
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from xml.dom import minidom

# Anthropic pricing per 1M tokens (USD) — uses 5-minute cache write tier
PRICING = {
    "claude-fable-5": {
        "input_tokens": 10.00,
        "output_tokens": 50.00,
        "cache_creation_input_tokens": 12.50,
        "cache_read_input_tokens": 1.00,
    },
    "claude-mythos-5": {
        "input_tokens": 10.00,
        "output_tokens": 50.00,
        "cache_creation_input_tokens": 12.50,
        "cache_read_input_tokens": 1.00,
    },
    "claude-opus-4-8": {
        "input_tokens": 5.00,
        "output_tokens": 25.00,
        "cache_creation_input_tokens": 6.25,
        "cache_read_input_tokens": 0.50,
    },
    "claude-opus-4-7": {
        "input_tokens": 5.00,
        "output_tokens": 25.00,
        "cache_creation_input_tokens": 6.25,
        "cache_read_input_tokens": 0.50,
    },
    "claude-opus-4-6": {
        "input_tokens": 5.00,
        "output_tokens": 25.00,
        "cache_creation_input_tokens": 6.25,
        "cache_read_input_tokens": 0.50,
    },
    "claude-opus-4-5": {
        "input_tokens": 5.00,
        "output_tokens": 25.00,
        "cache_creation_input_tokens": 6.25,
        "cache_read_input_tokens": 0.50,
    },
    "claude-opus-4-1": {
        "input_tokens": 15.00,
        "output_tokens": 75.00,
        "cache_creation_input_tokens": 18.75,
        "cache_read_input_tokens": 1.50,
    },
    "claude-opus-4": {
        "input_tokens": 15.00,
        "output_tokens": 75.00,
        "cache_creation_input_tokens": 18.75,
        "cache_read_input_tokens": 1.50,
    },
    "claude-sonnet-5": {
        "input_tokens": 2.00,
        "output_tokens": 10.00,
        "cache_creation_input_tokens": 2.50,
        "cache_read_input_tokens": 0.20,
    },
    "claude-sonnet-4-6": {
        "input_tokens": 3.00,
        "output_tokens": 15.00,
        "cache_creation_input_tokens": 3.75,
        "cache_read_input_tokens": 0.30,
    },
    "claude-sonnet-4-5": {
        "input_tokens": 3.00,
        "output_tokens": 15.00,
        "cache_creation_input_tokens": 3.75,
        "cache_read_input_tokens": 0.30,
    },
    "claude-sonnet-4": {
        "input_tokens": 3.00,
        "output_tokens": 15.00,
        "cache_creation_input_tokens": 3.75,
        "cache_read_input_tokens": 0.30,
    },
    "claude-haiku-4-5-20251001": {
        "input_tokens": 1.00,
        "output_tokens": 5.00,
        "cache_creation_input_tokens": 1.25,
        "cache_read_input_tokens": 0.10,
    },
    "claude-3-5-haiku-20241022": {
        "input_tokens": 0.80,
        "output_tokens": 4.00,
        "cache_creation_input_tokens": 1.00,
        "cache_read_input_tokens": 0.08,
    },
}

# Fallback pricing if model not recognized
DEFAULT_PRICING = PRICING["claude-opus-4-6"]


def compute_cost(usage: dict, model: str) -> float:
    prices = PRICING.get(model, DEFAULT_PRICING)
    cost = 0.0
    for key, price_per_million in prices.items():
        tokens = usage.get(key, 0)
        cost += tokens * price_per_million / 1_000_000
    return cost


def resolve_jsonl_files(target: Path) -> list[Path]:
    if target.is_file():
        return [target]
    if target.is_dir():
        files = sorted(target.glob("*.jsonl"))
        if not files:
            print(f"No .jsonl files found in {target}")
            sys.exit(1)
        return files
    print(f"Not a file or directory: {target}")
    sys.exit(1)


def main():
    if len(sys.argv) > 1:
        target = Path(sys.argv[1])
    else:
        target = Path(__file__).parent / "skill-invocations.jsonl"

    if not target.exists():
        print(f"Not found: {target}")
        sys.exit(1)

    jsonl_files = resolve_jsonl_files(target)
    output_path = (target if target.is_dir() else target.parent) / "aggregated-report.xml"

    # Keyed by (session_id, skill, model)
    stats = defaultdict(lambda: {"count": 0, "cost": 0.0, "tokens": defaultdict(int)})

    for jsonl_path in jsonl_files:
        with open(jsonl_path) as f:
            for line_no, line in enumerate(f, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    entry = json.loads(line)
                except json.JSONDecodeError:
                    print(f"Warning: skipping malformed JSON in {jsonl_path.name}:{line_no}", file=sys.stderr)
                    continue

                session_id = entry.get("session_id", "unknown")
                skill = entry.get("skill", "unknown")
                model = entry.get("model", "unknown")
                usage = entry.get("usage", {})

                key = (session_id, skill, model)
                stats[key]["count"] += 1
                stats[key]["cost"] += compute_cost(usage, model)
                for tok_type in ["input_tokens", "output_tokens", "cache_creation_input_tokens", "cache_read_input_tokens"]:
                    stats[key]["tokens"][tok_type] += usage.get(tok_type, 0)

    if not stats:
        print("No data found.")
        sys.exit(0)

    # ---------- Build report ----------
    details = []
    session_totals = defaultdict(lambda: {"count": 0, "cost": 0.0})
    skill_totals = defaultdict(lambda: {"count": 0, "cost": 0.0})

    for (session_id, skill, model), data in sorted(stats.items()):
        details.append({
            "session_id": session_id,
            "skill": skill,
            "model": model,
            "calls": data["count"],
            "cost": round(data["cost"], 6),
            "tokens": dict(data["tokens"]),
        })
        session_totals[session_id]["count"] += data["count"]
        session_totals[session_id]["cost"] += data["cost"]
        skill_totals[skill]["count"] += data["count"]
        skill_totals[skill]["cost"] += data["cost"]

    grand_count = sum(d["count"] for d in session_totals.values())
    grand_cost = sum(d["cost"] for d in session_totals.values())

    root = ET.Element("report")

    details_el = ET.SubElement(root, "details")
    for d in details:
        entry = ET.SubElement(details_el, "entry")
        ET.SubElement(entry, "session_id").text = d["session_id"]
        ET.SubElement(entry, "skill").text = d["skill"]
        ET.SubElement(entry, "model").text = d["model"]
        ET.SubElement(entry, "calls").text = str(d["calls"])
        ET.SubElement(entry, "cost").text = str(d["cost"])
        tokens_el = ET.SubElement(entry, "tokens")
        for tok_type, count in d["tokens"].items():
            ET.SubElement(tokens_el, tok_type).text = str(count)

    by_session_el = ET.SubElement(root, "by_session")
    for sid, d in sorted(session_totals.items()):
        session_el = ET.SubElement(by_session_el, "session")
        ET.SubElement(session_el, "session_id").text = sid
        ET.SubElement(session_el, "calls").text = str(d["count"])
        ET.SubElement(session_el, "cost").text = str(round(d["cost"], 6))

    by_skill_el = ET.SubElement(root, "by_skill")
    for sk, d in sorted(skill_totals.items(), key=lambda x: -x[1]["cost"]):
        skill_el = ET.SubElement(by_skill_el, "skill_entry")
        ET.SubElement(skill_el, "skill").text = sk
        ET.SubElement(skill_el, "calls").text = str(d["count"])
        ET.SubElement(skill_el, "cost").text = str(round(d["cost"], 6))

    grand_el = ET.SubElement(root, "grand_total")
    ET.SubElement(grand_el, "calls").text = str(grand_count)
    ET.SubElement(grand_el, "cost").text = str(round(grand_cost, 6))

    xml_str = minidom.parseString(ET.tostring(root, encoding="unicode")).toprettyxml(indent="  ")
    with open(output_path, "w") as out:
        out.write(xml_str)

    n = len(jsonl_files)
    print(f"Report written to {output_path} ({n} file{'s' if n != 1 else ''} aggregated)")


if __name__ == "__main__":
    main()
