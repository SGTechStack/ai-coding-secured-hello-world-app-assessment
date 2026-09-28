"""Done-when checks for ticket 33 (scaffolding)."""
import re
from build import DOCS, REPO

text = DOCS.read_text(encoding="utf-8")
print("== done-when grep on docs/register/register.md ==")
for pat in [r"\b\d{2}:\d+", r"ticket \d", r"\d{2} ADR", r"\.scratch"]:
    hits = [(i + 1, m.group(0)) for i, ln in enumerate(text.splitlines()) for m in re.finditer(pat, ln)]
    print(f"{pat!r}: {len(hits)} {hits[:5]}")

refs = set(re.findall(r"\b(ADR-\d{3}|REJ-\d{3})\b", text))
routing = (REPO / ".scratch/secured-hello-world/adr-routing/routing.md").read_text(encoding="utf-8")
s5 = re.search(r"## 5\..*?(?=## 6\.)", routing, re.S).group(0)
want = set()
for m in re.finditer(r"(ADR|REJ)-(\d{3})((?:,\s*(?:and\s+)?\d{3}|–\d{3})*)", s5):
    pre, first, rest = m.group(1), int(m.group(2)), m.group(3)
    want.add(f"{pre}-{first:03d}")
    for part in re.findall(r"[,–]\s*(?:and\s+)?(\d{3})", rest):
        want.add(f"{pre}-{int(part):03d}")
# expand REJ-025–027 style ranges
for a, b in re.findall(r"REJ-?(\d{3})–(\d{3})|(?<=, )(\d{3})–(\d{3})", s5) and []:
    pass
for a, b in re.findall(r"(\d{3})–(\d{3})", s5):
    pre = "REJ" if int(a) > 0 else "ADR"
    for n in range(int(a), int(b) + 1):
        want.add(f"REJ-{n:03d}")
missing = sorted(w for w in want if w not in refs)
print(f"== routing §5 IDs: {len(want)} wanted, missing from table refs: {missing}")
s6 = re.search(r"## 6\..*?(?=## 7\.)", routing, re.S).group(0)
for story in sorted(set(re.findall(r"Story \d+(?: AC\d)?", s6))):
    n = len(re.findall(re.escape("PRD " + story), text))
    print(f"PRD {story}: {n} rows")
