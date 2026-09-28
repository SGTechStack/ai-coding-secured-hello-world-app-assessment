"""Write index.md: one compact line per staged row, for the dedup pass."""
from stagelib import HERE, load_stage_rows

rows = load_stage_rows()
keys = [r["key"] for r in rows]
dups = {k for k in keys if keys.count(k) > 1}
if dups:
    raise SystemExit(f"duplicate keys: {sorted(dups)}")
lines = ["# Compact index of staged rows (generated)", "",
         "| key | pillar | kind | requirement | verdict | resp | subject | inv | dup |", "|---|---|---|---|---|---|---|---|---|"]
for r in sorted(rows, key=lambda r: (r["pillar"], r["kind"], r["key"])):
    lines.append("| " + " | ".join([r["key"], r["pillar"], r["kind"], r["requirement"][:90], r["verdict"],
                                    r["responsibility"], r["subject"], r["inv"], r["dup"][:120]]) + " |")
(HERE / "index.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(len(rows), "rows indexed")
