"""Ticket 33 build: staged rows + dedup.md -> docs/register/register.md, plus reconciliation and renderings.

Scaffolding. Lives in .scratch and dies with it. Re-runnable; the table it writes is the surviving artefact.
"""
import json
import re
from collections import OrderedDict, defaultdict
from pathlib import Path

from stagelib import COLS, HERE, STAGES, load_not_staged, load_stage_rows, section, table_rows

REPO = HERE.parents[3]
DOCS = REPO / "docs" / "register" / "register.md"
TESTPLAN = REPO / "docs" / "test-plan" / "test-plan.md"
ADRREADME = REPO / "docs" / "adr" / "README.md"
INVENTORY = HERE.parent / "inventory"

TABLE_COLS = ["pillar", "kind", "requirement", "level", "verdict", "subject", "decision", "rationale",
              "residual", "responsibility", "status", "priority", "sequence", "acceptance", "refs"]
PILLARS = ["AUTH", "SES", "CSRF", "LCK", "RL", "CRED", "ADM", "MFA", "AUD", "HDR", "CFG", "RUN", "OBS", "FE",
           "BLD", "DATA", "OPS", "STD"]
KINDS = {"deviation", "residual", "n/a", "not-built", "fidelity", "defect", "obligation", "trigger", "note"}
VERDICTS = {"pass", "pass-with-note", "conditional-pass", "partial", "fail", "n/a", "satisfied-by-procedure", "—"}
RESP = {"application", "deployer", "shared", "none"}
STATUS = {"enforced", "enforced-elsewhere-cited", "asserted-by-test", "procedural", "unmitigated"}
PRIO = {"blocking", "required", "recommended"}
SEQ = {"1", "2", "3", "4", "5", "6", "7", "L"}
SENTINEL = "none possible — attests a named role is filled and its holder reachable."
FORBIDDEN = [r"\b\d{2}:\d+", r"ticket \d", r"\d{2} ADR", r"\d{2} §", r"§R\b", r"\.scratch", r"research/"]
REF_RE = re.compile(r"(\d{2}):(\d+)(?:\s*[–-]\s*(\d+))?")

problems = []


def norm_list(cell):
    return [x.strip() for x in cell.split(";") if x.strip() and x.strip() not in ("—", "-")]


def union(a, b):
    seen = OrderedDict((x, None) for x in norm_list(a))
    for x in norm_list(b):
        seen.setdefault(x, None)
    return "; ".join(seen)


def parse_dedup():
    text = (HERE / "dedup.md").read_text(encoding="utf-8")
    amap = {c[0]: c for c in table_rows(section(text, "Map"))}
    edits = table_rows(section(text, "Edits"))
    minted = [dict(zip(COLS, c)) for c in table_rows(section(text, "Minted"))]
    for m in minted:
        m["stage"] = "M"
    return amap, edits, minted


def first_ref(row):
    for m in REF_RE.finditer(row["source"]):
        return int(m.group(1)), int(m.group(2))
    return 99, 0


def main():
    staged = load_stage_rows()
    amap, edits, minted = parse_dedup()
    by_key = {r["key"]: r for r in staged + minted}
    if set(amap) != {r["key"] for r in staged}:
        raise SystemExit(f"map mismatch: {set(by_key) ^ set(amap)}")

    canon = OrderedDict()
    for r in staged + minted:
        act = amap.get(r["key"], [r["key"], "keep", "", ""])[1] if r["stage"] != "M" else "keep"
        if act == "keep":
            c = dict(r)
            c["members"] = [r["key"]]
            canon[r["key"]] = c
    merges = []
    for k, (key, act, target, reason) in amap.items():
        if act == "merge":
            if target not in canon:
                raise SystemExit(f"{k} merges into non-kept {target}")
            t, s = canon[target], by_key[k]
            for col in ("inv", "source", "refs"):
                t[col] = union(t[col], s[col])
            t["members"].append(k)
            merges.append((k, target, reason))
        elif act not in ("keep", "retire"):
            raise SystemExit(f"bad action {act} for {k}")
    for key, col, value, _reason in edits:
        if key not in canon:
            raise SystemExit(f"edit on non-kept {key}")
        if col not in TABLE_COLS:
            raise SystemExit(f"edit on bad column {col}")
        canon[key][col] = value
    apply_post_rulings(canon)

    # ---- validation ----
    tids = set(re.findall(r"^\| (T-[A-Z0-9]+-\d{3}) \|", TESTPLAN.read_text(encoding="utf-8"), re.M))
    adr_text = ADRREADME.read_text(encoding="utf-8")
    adrs = set(re.findall(r"\b(ADR-\d{3})\b", adr_text)) | set(re.findall(r"\b(REJ-\d{3})\b", adr_text))
    for c in canon.values():
        k = c["key"]
        chk(c["pillar"] in PILLARS, k, f"pillar {c['pillar']}")
        chk(c["kind"] in KINDS, k, f"kind {c['kind']}")
        chk(c["verdict"] in VERDICTS, k, f"verdict {c['verdict']}")
        chk(c["responsibility"] in RESP, k, f"resp {c['responsibility']}")
        chk(c["status"] in STATUS, k, f"status {c['status']}")
        if c["responsibility"] == "none":
            chk(c["priority"] == "", k, "priority present on none")
        else:
            chk(c["priority"] in PRIO, k, f"priority {c['priority']!r}")
        if c["responsibility"] in ("deployer", "shared"):
            chk(c["sequence"] in SEQ - {"L"}, k, f"sequence {c['sequence']!r} on {c['responsibility']}")
        else:
            chk(c["sequence"] in ("", "L"), k, f"sequence {c['sequence']!r} on {c['responsibility']}")
        chk(bool(c["acceptance"]), k, "no acceptance")
        chk(c["kind"] != "defect" or c["pillar"] == "STD", k, "defect outside STD")
        chk(c["pillar"] != "STD" or c["kind"] in ("defect", "note"), k, "STD non-defect non-note")
        refs = norm_list(c["refs"])
        c["refs"] = "; ".join(refs)
        for ref in refs:
            if ref.startswith("T-"):
                chk(ref in tids, k, f"unknown {ref}")
            elif ref.startswith(("ADR-", "REJ-")):
                chk(ref in adrs, k, f"unknown {ref}")
            else:
                chk(False, k, f"bad ref {ref}")
        if c["status"] == "asserted-by-test":
            chk(any(r.startswith("T-") for r in refs), k, "asserted-by-test without T-")
        if re.search(r"ASVS \d+\.\d+\.\d+", c["requirement"]) and not re.search(r"L[123]", c["level"]):
            chk(False, k, "ASVS without level")
        for col in TABLE_COLS:
            for pat in FORBIDDEN:
                if re.search(pat, c[col]):
                    chk(False, k, f"{col} matches {pat}: {re.search(pat, c[col]).group(0)}")
            chk("|" not in c[col], k, f"pipe in {col}")

    # ---- mint IDs ----
    ordered = sorted(canon.values(), key=lambda c: (PILLARS.index(c["pillar"]), first_ref(c), c["key"]))
    counters = defaultdict(int)
    for c in ordered:
        counters[c["pillar"]] += 1
        c["id"] = f"R-{c['pillar']}-{counters[c['pillar']]:03d}"
    key2id = {c["key"]: c["id"] for c in ordered}
    member2id = {m: c["id"] for c in ordered for m in c["members"]}

    write_docs(ordered)
    write_reconciliation(ordered, merges, member2id, amap)
    write_renderings(ordered)
    (HERE / "build-map.json").write_text(json.dumps(
        {"member2id": member2id, "rows": [{"id": c["id"], "key": c["key"], "source": c["source"], "inv": c["inv"]}
                                          for c in ordered]}, indent=1, ensure_ascii=False), encoding="utf-8")
    print(f"canonical rows: {len(ordered)}; by pillar: {dict(counters)}")
    if problems:
        print(f"{len(problems)} PROBLEMS:")
        for p in problems:
            print("  ", p)
        raise SystemExit(1)


def chk(ok, key, msg):
    if not ok:
        problems.append(f"{key}: {msg}")


POST_RULINGS = {
    # 25 §4 worked rows: grading from the schema spike wins (ticket 33 resolver).
    "SB-023": {"responsibility": "shared", "status": "procedural", "priority": "blocking", "sequence": "7"},
    "SD1-001": {"responsibility": "deployer", "status": "unmitigated", "priority": "required", "sequence": "6"},
    "SE1-007": {"responsibility": "deployer", "status": "procedural", "priority": "blocking", "sequence": "4",
                "acceptance": SENTINEL},
    # ASVS levels on trigger rows whose requirement names an ASVS ID.
    "SD1-021": {"level": "L2"},
    "SF2-017": {"level": "L3; L2"},
    "SE1-002": {"responsibility": "none", "status": "asserted-by-test", "priority": "", "sequence": "L"},
}


def apply_post_rulings(canon):
    for key, vals in POST_RULINGS.items():
        canon[key].update(vals)


def md_row(cells):
    return "| " + " | ".join(cells) + " |"


def write_docs(ordered):
    head = DOCS_HEADER
    lines = [head, md_row(["ID"] + TABLE_COLS), md_row(["---"] * (len(TABLE_COLS) + 1))]
    for c in ordered:
        lines.append(md_row([c["id"]] + [c[col] for col in TABLE_COLS]))
    counts = defaultdict(int)
    for c in ordered:
        counts[c["pillar"]] += 1
    DOCS.parent.mkdir(parents=True, exist_ok=True)
    DOCS.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_renderings(ordered):
    comp = ["# Compliance rendering (derivation check, generated)", "",
            md_row(["ID", "requirement", "level", "verdict", "kind", "deviation", "residual", "anchor"]),
            md_row(["---"] * 8)]
    for c in ordered:
        anchor = f"handover#{c['id'].lower()}" if c["sequence"] else ""
        comp.append(md_row([c["id"], c["requirement"], c["level"], c["verdict"], c["kind"], c["decision"],
                            c["residual"], anchor]))
    (HERE / "render-compliance.md").write_text("\n".join(comp) + "\n", encoding="utf-8")
    ops = ["# Operational rendering (derivation check, generated)", ""]
    names = {"1": "Keys and generation commands", "2": "Trusted-proxy configuration", "3": "Time synchronisation",
             "4": "Headers and origins", "5": "Mail transport", "6": "Log forwarding and retention",
             "7": "Recovery rehearsal", "L": "Limitations (no one acts; stated so they are not misread)"}
    for step in ["1", "2", "3", "4", "5", "6", "7", "L"]:
        rows = [c for c in ordered if c["sequence"] == step]
        ops += [f"## {step}. {names[step]} ({len(rows)})", "",
                md_row(["ID", "what to do", "responsibility", "priority", "status", "how to prove it"]),
                md_row(["---"] * 6)]
        for c in rows:
            ops.append(md_row([c["id"], c["decision"], c["responsibility"], c["priority"], c["status"],
                               c["acceptance"]]))
        ops.append("")
    (HERE / "render-operational.md").write_text("\n".join(ops) + "\n", encoding="utf-8")


def inventory_live_heading_items():
    live, dead = [], []
    for f in sorted(INVENTORY.glob("part-*.md")):
        for ln in f.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^\| (\d{2}-H-[^ |]+) \| handover \|", ln)
            if m:
                cells = [x.strip() for x in ln.strip().strip("|").split("|")]
                (dead if re.match(r"(superseded|withdrawn|retired)", cells[5]) else live).append(
                    f"{f.stem[-1]}:{m.group(1)}")
    return live, dead


def in_25_content(src):
    for m in REF_RE.finditer(src):
        t, a = int(m.group(1)), int(m.group(2))
        if t == 25 and (21 <= a <= 444 or 808 <= a <= 960):
            return True
    return False


def write_reconciliation(ordered, merges, member2id, amap):
    staged = load_stage_rows()
    not_staged = load_not_staged()
    by_key = {r["key"]: r for r in staged}
    out = ["# Ticket 33 reconciliation (scaffolding; dies with `.scratch/`)", "",
           "Generated by `build.py` from the stage files and `dedup.md`. The surviving artefact is "
           "`docs/register/register.md`.", ""]

    # per inventory source: items -> R-IDs
    inv_to_ids = defaultdict(set)
    for r in staged:
        for inv in norm_list(r["inv"]):
            inv_to_ids[inv].add(member2id[r["key"]])
    per_ticket = defaultdict(lambda: {"items": set(), "ids": set(), "dropped": 0})
    for inv, ids in inv_to_ids.items():
        m = re.match(r"^([A-G]):(\d{2})-", inv)
        grp = f"inventory {m.group(1)}, ticket {m.group(2)}" if m else f"non-inventory ({inv.split(':')[0]})"
        per_ticket[grp]["items"].add(inv)
        per_ticket[grp]["ids"] |= ids
    for ns in not_staged:
        inv = ns[1]
        m = re.match(r"^([A-G]):(\d{2})-", inv)
        grp = f"inventory {m.group(1)}, ticket {m.group(2)}" if m else f"stage {ns[0]} non-inventory"
        per_ticket[grp]["dropped"] += 1
    out += ["## Per source: items → R-IDs", "",
            "| source | items in rows | not staged | distinct R-IDs |", "|---|---|---|---|"]
    tot_i = tot_d = 0
    for grp in sorted(per_ticket):
        d = per_ticket[grp]
        tot_i += len(d["items"]); tot_d += d["dropped"]
        out.append(f"| {grp} | {len(d['items'])} | {d['dropped']} | {len(d['ids'])} |")
    out += [f"| **total** | **{tot_i}** | **{tot_d}** | **{len(ordered)}** (table) |", ""]

    out += ["## Staged key → R-ID", "", "| staged key | inventory ids | R-ID | action |", "|---|---|---|---|"]
    for r in staged:
        act = amap[r["key"]][1]
        out.append(f"| {r['key']} | {r['inv']} | {member2id[r['key']]} | {act} |")
    out += ["", "## Merges (every one named)", "", "| merged key | into | R-ID | why |", "|---|---|---|---|"]
    for k, t, reason in merges:
        out.append(f"| {k} | {t} | {member2id[k]} | {reason} |")
    out += ["", "## Drops (not staged), each cited to the line that superseded it", "",
            "| stage | inv | source | item | reason | superseded by |", "|---|---|---|---|---|---|"]
    for ns in not_staged:
        cells = (ns + [""] * 6)[:6]
        out.append(md_row(cells))

    # standards-defect renumbering
    out += ["", "## Standards defects: one global sequence (old ordinals recorded here only)", "",
            "| R-ID | old ordinal / note (from staging) | subject |", "|---|---|---|"]
    for c in ordered:
        if c["kind"] == "defect":
            notes = " / ".join(by_key[m]["note"] for m in c["members"] if m in by_key and by_key[m]["note"])
            ords = re.findall(r"[^.;]*(?:ordinal|defect \d+|\b(?:third|fourth|fifth|sixth|seventh|eighth|ninth|"
                              r"tenth|eleventh|twelfth|thirteenth|fourteenth)\b)[^.;]*", notes, re.I)
            out.append(f"| {c['id']} | {'; '.join(o.strip() for o in ords) or '—'} | {c['subject']} |")

    # handover population
    ops = [c for c in ordered if c["sequence"]]
    live_h, dead_h = inventory_live_heading_items()
    heading_rows = {member2id[r["key"]] for r in staged
                    if any(re.match(r"^[A-G]:\d{2}-H-", i) for i in norm_list(r["inv"]))}
    pre_rule = set()
    for r in staged:
        if r["stage"] == "H" and re.match(r"^25:", r["inv"]) or in_25_content(r["source"]):
            pre_rule.add(member2id[r["key"]])
    for ns in not_staged:
        if ns[0] == "H" and re.match(r"^25:", ns[1]):
            for k in re.findall(r"\bS[A-H]\d?-\d{3}\b", ns[4]):
                if k in member2id:
                    pre_rule.add(member2id[k])
    ops_ids = {c["id"] for c in ops}
    pre_ops, head_ops = pre_rule & ops_ids, heading_rows & ops_ids
    overlap = pre_ops & head_ops
    other = ops_ids - pre_ops - head_ops
    out += ["", "## Handover population, computed", "",
            "The operational view is every row with a `sequence` value (steps 1–7, plus `L` limitation rows).", "",
            "| term | value | what it is |", "|---|---|---|",
            f"| ticket 25 stated | 47 + 9 = 56 | verdicts resting on the document plus unowned obligations (25:455–457); "
            f"never enumerated in ticket 25 |",
            f"| pre-rule term, computed | {len(pre_ops)} | operational rows reached from ticket 25's own content "
            f"sections (25:21–444, 25:808–960), directly or through a stage-H `covered by` line |",
            f"| later heading items | {len(live_h)} live ({len(dead_h)} dead) inventory items → {len(head_ops)} "
            f"operational rows | bullets under the exact declaration heading |",
            f"| overlap | {len(overlap)} | rows in both terms |",
            f"| register-origin operational rows | {len(other)} | deployer/shared/limitation rows from register items "
            f"that neither term carried |",
            f"| **population** | **{len(pre_ops)} + {len(head_ops)} − {len(overlap)} + {len(other)} = {len(ops_ids)}** "
            f"| rows in the operational rendering |", ""]
    by_seq = defaultdict(int)
    for c in ops:
        by_seq[c["sequence"]] += 1
    out.append("By sequence step: " + ", ".join(f"{s}: {by_seq[s]}" for s in "1234567L") + ".")
    resp = defaultdict(int)
    for c in ordered:
        resp[c["responsibility"]] += 1
    out.append("")
    out.append("By responsibility (whole table): " + ", ".join(f"{k}: {v}" for k, v in sorted(resp.items())) + ".")
    kinds = defaultdict(int)
    for c in ordered:
        kinds[c["kind"]] += 1
    out.append("")
    out.append("By kind (whole table): " + ", ".join(f"{k}: {v}" for k, v in sorted(kinds.items())) + ".")
    (HERE / "reconciliation.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"handover population {len(ops_ids)} = pre {len(pre_ops)} + heading {len(head_ops)} - overlap "
          f"{len(overlap)} + other {len(other)}; live heading items {len(live_h)}")


DOCS_HEADER = (Path(__file__).parent / "docs-header.md").read_text(encoding="utf-8")

if __name__ == "__main__":
    main()
