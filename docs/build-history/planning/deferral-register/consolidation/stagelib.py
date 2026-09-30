"""Shared parsing for ticket 33 consolidation (scaffolding; dies with .scratch)."""
import re
from pathlib import Path

HERE = Path(__file__).parent
STAGES = ["A", "B", "C", "D1", "D2", "E1", "E2", "F1", "F2", "G1", "G2", "G3", "H"]
COLS = ["key", "inv", "source", "pillar", "kind", "requirement", "level", "verdict", "subject",
        "decision", "rationale", "residual", "responsibility", "status", "priority", "sequence",
        "acceptance", "refs", "dup", "note"]


def split_row(line):
    line = line.strip()
    assert line.startswith("|") and line.endswith("|"), line[:80]
    return [c.strip() for c in line[1:-1].split("|")]


def section(text, name):
    m = re.search(r"^## " + re.escape(name) + r"\s*$(.*?)(?=^## |\Z)", text, re.S | re.M)
    return m.group(1) if m else ""


def table_rows(block):
    out = []
    for ln in block.splitlines():
        if ln.startswith("|") and not re.match(r"^\|\s*-", ln):
            out.append(split_row(ln))
    return out[1:] if out else []  # drop header


def load_stage_rows():
    rows = []
    for s in STAGES:
        text = (HERE / f"stage-{s}.md").read_text(encoding="utf-8")
        for cells in table_rows(section(text, "Rows")):
            if len(cells) != len(COLS):
                raise SystemExit(f"stage {s}: {cells[0]} has {len(cells)} cells")
            r = dict(zip(COLS, cells))
            r["stage"] = s
            rows.append(r)
    return rows


def load_not_staged():
    out = []
    for s in STAGES:
        text = (HERE / f"stage-{s}.md").read_text(encoding="utf-8")
        for cells in table_rows(section(text, "Not staged")):
            out.append([s] + cells)
    return out
