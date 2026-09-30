"""Append 'Consolidated into the register (ticket 33)' pointers to every consumed source line.

Appends to existing lines only, so every NN:line citation on the map still resolves. Writes pointers.md as the
audit trail. Idempotent: earlier pointers from this script are stripped and re-appended.
"""
import json
import re
from collections import defaultdict

from build import REF_RE, REPO
from stagelib import HERE, load_stage_rows

ISSUES = REPO / ".scratch" / "secured-hello-world" / "issues"
MARK = "(ticket 33)"
bm = json.loads((HERE / "build-map.json").read_text(encoding="utf-8"))
member2id = bm["member2id"]

targets = defaultdict(set)  # (ticket, line) -> R-IDs
for r in load_stage_rows():
    rid = member2id[r["key"]]
    for m in REF_RE.finditer(r["source"]):
        t, a, b = int(m.group(1)), int(m.group(2)), m.group(3)
        targets[(t, int(b) if b else a)].add(rid)

files = {int(p.name[:2]): p for p in ISSUES.glob("[0-9][0-9]-*.md")}
log, skipped = [], []
for t in sorted({t for t, _ in targets}):
    if t not in files:
        skipped.append((t, "-", "no issue file"))
        continue
    path = files[t]
    raw = path.read_text(encoding="utf-8")
    # idempotent: strip any pointer this script appended before, then re-append
    raw = re.sub(r" \*Consolidated into the register \(ticket 33\): R-[^*]*\*", "", raw)
    lines = raw.split("\n")
    n_before = len(lines)
    fence = []
    inside = False
    for ln in lines:
        if ln.lstrip().startswith("```"):
            inside = not inside
            fence.append(True)
        else:
            fence.append(inside)
    pending = defaultdict(set)
    for (tt, line), ids in targets.items():
        if tt != t:
            continue
        i = line - 1
        if i >= len(lines):
            skipped.append((t, line, "past end of file"))
            continue
        while i > 0 and not lines[i].strip():
            i -= 1  # blank: move to the item's last text line
        if fence[i]:
            skipped.append((t, line, "inside code fence"))
            continue
        if lines[i].lstrip().startswith("#"):
            skipped.append((t, line, "heading line"))
            continue
        pending[i] |= ids
    for i, ids in sorted(pending.items()):
        idlist = ", ".join(sorted(ids, key=lambda x: (x.split("-")[1], int(x.split("-")[2]))))
        ptr = f"*Consolidated into the register {MARK}: {idlist}. Amend the table by ID, not this list.*"
        ln = lines[i].rstrip()
        if ln.lstrip().startswith("|") and ln.endswith("|"):
            lines[i] = ln[:-1].rstrip() + " " + ptr + " |"
        else:
            lines[i] = ln + " " + ptr
        log.append((t, i + 1, idlist))
    assert len(lines) == n_before, path
    path.write_text("\n".join(lines), encoding="utf-8")

out = ["# Ticket 33 pointers (scaffolding)", "", f"{len(log)} lines carry a pointer; {len(skipped)} refs skipped.", "",
       "| ticket | line | R-IDs |", "|---|---|---|"] + [f"| {t} | {l} | {ids} |" for t, l, ids in log]
out += ["", "## Skipped", "", "| ticket | line | why |", "|---|---|---|"] + [f"| {a} | {b} | {c} |" for a, b, c in skipped]
(HERE / "pointers.md").write_text("\n".join(out) + "\n", encoding="utf-8")
print(len(log), "pointers;", len(skipped), "skipped")
