# Scaffolding for ticket 38: rewrites docs/test-plan/test-plan.md. Dies with .scratch/.
import re, collections, glob, sys

ROOT = '.'
TP = f'{ROOT}/docs/test-plan/test-plan.md'
DS = f'{ROOT}/.scratch/secured-hello-world/de-scratch'

def expand(text):
    out = set()
    for m in re.finditer(r'T-([A-Z0-9]+)-(\d{3})(?:[–-](\d{3}))?', text):
        p, a, b = m.group(1), int(m.group(2)), m.group(3)
        for n in range(a, (int(b) if b else a) + 1):
            out.add(f'T-{p}-{n:03d}')
    return out

# Reverse maps: T-row -> decision IDs (ADR/REJ) and register rows
dec = collections.defaultdict(set)
for l in open(f'{ROOT}/.scratch/secured-hello-world/adr-routing/routing.md', encoding='utf-8'):
    m = re.match(r'\| (ADR-\d{3}|REJ-\d{3}) \|', l)
    if m:
        for t in expand(l.rstrip().rstrip('|').split(' | ')[-1]):
            dec[t].add(m.group(1))
for f in glob.glob(f'{ROOT}/docs/adr/0*.md'):
    n = re.search(r'(\d{4})-', f.replace('\\', '/').split('/')[-1]).group(1)
    for t in expand(open(f, encoding='utf-8').read()):
        dec[t].add(f'ADR-{int(n):03d}')
for l in open(f'{ROOT}/docs/adr/README.md', encoding='utf-8'):
    m = re.search(r'\b(REJ-\d{3})\b', l)
    if m and l.lstrip().startswith('|'):
        for t in expand(l):
            dec[t].add(m.group(1))
reg = collections.defaultdict(set)
for l in open(f'{ROOT}/docs/register/register.md', encoding='utf-8'):
    m = re.match(r'\| (R-[A-Z]+-\d{3}) \|', l)
    if m:
        for t in expand(l.rstrip().rstrip('|').split(' | ')[-1]):
            reg[t].add(m.group(1))

def table(path, ncols_min=2):
    out = {}
    for l in open(path, encoding='utf-8'):
        m = re.match(r'\| (T-[A-Z0-9]+-\d{3}) \| (.+?) \|', l)
        if m:
            out[m.group(1)] = m.group(2).strip()
    return out

manual = {}
manual.update(table(f'{DS}/clause-manual-1.md'))
manual.update(table(f'{DS}/clause-manual-2.md'))
manual.update({
    # Resolved here: RFC 9110 §15.5.2 checked at rfc-editor.org; register row added by this ticket.
    'T-AUTH-007': 'RFC 9110 §15.5.2 (deviation); R-AUTH-005; ADR-031',
})
# Abbreviation: the MFA_Core recipes file.
manual = {k: v.replace('MFA-RCP Recipe', 'MFA Recipe') for k, v in manual.items()}

rationale = table(f'{DS}/rationale.md')
# Citations completed here (verified at rfc-editor.org and Spring Boot 2.2.0-M4 release notes).
rationale['T-CFG-032'] = rationale['T-CFG-032'].replace(
    'a `Max-Age` turns the browser-session cookie into a persistent one',
    'a `Max-Age` sets the cookie\'s persistent flag (RFC 6265 §5.3 step 3), turning the browser-session cookie into one')
rationale['T-OBS-005'] = rationale['T-OBS-005'].replace(
    'because the Tomcat thread meters are not instrumented without it',
    'because Tomcat\'s MBean registry is disabled by default and Tomcat\'s thread meters are published only when it is enabled (Spring Boot 2.2.0-M4 release notes)')

text_fixes = {
    'T-SES-019': ('the test carries its reason inline citing 08:522.', 'the test carries its reason inline: the account has no sessions yet (ADR-037).'),
    'T-SES-020': ('the test carries its reason inline citing 08:522.', 'the test carries its reason inline: the account has no sessions yet (ADR-037).'),
    'T-SES-021': ('drive U to the ticket 09 §R consecutive-failure cap', 'drive U to the 100-consecutive-failure cap (ADR-013)'),
    'T-SES-035': ("keep ticket 08's `AUTH_INSTANT` absolute-lifetime behaviour", 'keep the `AUTH_INSTANT` absolute-lifetime behaviour (ADR-038)'),
    'T-LCK-004': ('(acceptance after the lift is Story 3 (i), 16:545)', "(acceptance after the lift is asserted separately, as PRD Story 3's tier-1 auto-lift)"),
    'T-CRED-023': ("ticket 26's three-registry disposition test", 'the three-registry disposition test (T-ARCH-005)'),
    'T-MFA-006': ("ticket 09's early per-IP filter", 'the early per-IP filter'),
    'T-AUD-024': ("ticket 13's CR/LF/pipe strip", "the audit emitter's CR/LF/pipe strip"),
    'T-CFG-036': ("for ticket 29's disk-reserve shedding", 'for the disk-reserve shedding (ADR-041)'),
    'T-RUN-004': ("ticket 24's refresh-phase validator bean", 'refresh-phase prohibited-configuration validator bean (T-CFG-033)'),
    'T-RUN-007': ("(ticket 11's seeding `ApplicationRunner` excluded)", '(the bootstrap seeding `ApplicationRunner` is excluded)'),
    'T-ARCH-005': None,  # handled below
    'T-BLD-008': ('`verify` fails when the test-plan file parses zero rows.',
                  '`verify` fails when the test-plan file parses zero rows, or when its table header is not exactly `ID`, `pillar`, `control`, `assertion`, `level`, `context`, `isolation`, `clause`, `rationale`, `polarity` in that order.'),
}

LOCAL = re.compile(r'^(\d{2}\b|ADR \d|STD §|Recipe \d)')
ID_ORDER = {'ADR': 0, 'REJ': 1, 'R': 2}

def idkey(i):
    return (ID_ORDER[i.split('-')[0]], i)

lines = open(TP, encoding='utf-8').read().split('\n')
out_rows = []
changed = 0
header_idx = None
for i, l in enumerate(lines):
    if l.startswith('| ID | pillar |'):
        header_idx = i
    if not l.startswith('| T-'):
        continue
    c = [x.strip() for x in l.strip().strip('|').split(' | ')]
    assert len(c) == 10, c[0]
    tid = c[0]
    toks = [t.strip() for t in c[7].split(';') if t.strip()]
    if tid in manual:
        cell = [t.strip() for t in manual[tid].split(';')]
        prim = [t for t in toks if not LOCAL.match(t)]
        # keep original primary tokens the manual cell did not already carry
        new = prim + [t for t in cell if t not in prim]
    elif any(LOCAL.match(t) for t in toks):
        prim = [t for t in toks if not LOCAL.match(t)]
        ids = sorted(dec[tid], key=idkey) + sorted(reg[tid])
        new = prim + [x for x in ids if x not in prim]
        if not new:
            sys.exit(f'no replacement for {tid}')
    else:
        new = toks
    if new != toks:
        changed += 1
    c[7] = '; '.join(new)
    fx = text_fixes.get(tid)
    if fx:
        assert fx[0] in c[3], tid
        c[3] = c[3].replace(fx[0], fx[1])
    if tid == 'T-ARCH-005':
        for s in (' (11)', ' (09)', ' (13)'):
            assert s in c[3]
            c[3] = c[3].replace(s, '')
    r = rationale.get(tid, '')
    assert '|' not in r
    c[8] = r
    lines[i] = '| ' + ' | '.join(c) + ' |'

lines[header_idx] = '| ID | pillar | control | assertion | level | context | isolation | clause | rationale | polarity |'
missing = set(rationale) - {l.split(' | ')[0][2:] for l in lines if l.startswith('| T-')}
assert not missing, missing
open(TP, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
print('clause cells changed:', changed, 'rationale rows:', len(rationale), 'manual:', len(manual))
