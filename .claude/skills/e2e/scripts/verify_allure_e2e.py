"""Reject stale, incomplete, or implementation-coupled Playwright Allure evidence.

The checks keep a story report tied to its requested behavior, fresh execution,
and inspectable UI evidence rather than a green shell or hidden setup.
"""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

# Why: disposable diagnostics are allowed outside the final runner tree, but a discovery/scratch
# spec here can be picked up by the evidence command and mutate state outside the story contract.
# Every runnable spec in the final tree must be a real story spec.
SCRATCH_TITLE_PATTERN = re.compile(
    r"\btest(?:\s*\.\s*(?:describe|only|fixme|skip|step))?\s*\(\s*[`'\"]\s*"
    r"(discovery|scratch|debug|explore|sandbox|temp|throwaway|cleanup)\b",
    re.IGNORECASE,
)
TEST_CALL_PATTERN = re.compile(r"\btest(?:\s*\.\s*\w+)?\s*\(")
STORY_METADATA_PATTERN = re.compile(
    r"allure\s*\.\s*description\s*\(|allure\s*\.\s*attachment\(\s*['\"]Test plan['\"]",
)
AC_SUBSUITE_PATTERN = re.compile(r"^(AC\d+[a-z]?)\s*:\s*(\S.*)$", re.IGNORECASE)


def scan_scratch_specs(spec_dir: Path) -> list[str]:
    # Reject disposable diagnostics in the final spec tree, whether or not they produced results.
    failures: list[str] = []
    for path in sorted(spec_dir.rglob("*.spec.*")):
        try:
            source = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        # Why: a file with no test() call is not a runnable spec, so it cannot waste a runner cycle.
        if not TEST_CALL_PATTERN.search(source):
            continue
        scratch = SCRATCH_TITLE_PATTERN.search(source)
        if scratch:
            failures.append(
                f"{path}: diagnostic test title '{scratch.group(1)}' is not allowed in the final spec tree; "
                "move it outside the runner tree and remove it before final verification"
            )
        # Why: runnable final specs require story metadata; a diagnostic belongs outside this tree.
        if not STORY_METADATA_PATTERN.search(source):
            failures.append(
                f"{path}: final spec has test() calls but no Allure story metadata "
                "(allure.description / 'Test plan' attachment); move diagnostics outside the runner tree"
            )
    return failures

# Why: story agents must fail closed; only the harness owner may refresh hashes.
FOUNDATION_OWNER_HINT = (
    "Harness owner only - regenerate with "
    "`python \"<skill-root>/scripts/create_e2e_foundation_manifest.py\" "
    "e2e/e2e-foundation.json <protected-files...>`, then `git add` the manifest. "
    "Story agents must not repair this."
)

FORBIDDEN_IMPORT_TOKENS = ("child_process", "execfilesync")
FORBIDDEN_IMPORT_PATTERNS = (
    (r"\b(?:exec|execfile|spawn)\s*(?:sync)?\s*\(", "process execution"),
    (r"\bdocker\s+compose\b", "Docker Compose control"),
    (r"\bgetbytestid\s*\(", "test-id selector"),
    (r"\bdata-testid\b", "test-id selector"),
    (r"\b(?:test|describe)\s*\.\s*only\s*\(", "focused test"),
    (r"\b(?:page|context)\s*\.\s*request\b", "hidden API request"),
    (r"\brequest\s*\.\s*(?:get|post|put|patch|delete)\s*\(", "hidden API request"),
    (r"\bfetch\s*\(", "hidden API request"),
    (r"\baxios\s*\.", "hidden API request"),
    (r"\b(?:supertest|prisma|sequelize|knex|typeorm)\b", "hidden data access"),
)

# Forced interaction bypasses the normal user-actionability checks that make browser evidence
# meaningful. Keep this narrow: only literal `force: true` on UI actions is rejected.
FORCED_UI_ACTION_PATTERN = re.compile(
    r"\.\s*(click|dblclick|tap|check|uncheck|hover|dragTo)\s*\(\s*[^)]{0,400}?\bforce\s*:\s*true\b",
    re.IGNORECASE | re.DOTALL,
)


def forced_ui_action_failure(name: str, source: str) -> str | None:
    match = FORCED_UI_ACTION_PATTERN.search(source)
    if match:
        return (
            f"{name}: forced Playwright UI action '.{match.group(1)}({{ force: true }})' is not allowed; "
            "use normal actionability or report the blocked overlay/state"
        )
    return None


def flatten(steps: list[dict]) -> list[dict]:
    # Allure nests evidence beneath hooks and user steps; inspect every level.
    output: list[dict] = []
    for step in steps:
        output.append(step)
        output.extend(flatten(step.get("steps", [])))
    return output


def attachment_file(results_dir: Path, attachment: dict, name: str) -> tuple[Path | None, str | None]:
    # A report link is not evidence if its source is missing, empty, or escapes this campaign.
    source = attachment.get("source")
    # Why: an Allure label without a file cannot be inspected or reproduced.
    if not source:
        return None, f"{name}: attachment '{attachment.get('name', '<unnamed>')}' has no source"
    path = (results_dir / source).resolve()
    try:
        path.relative_to(results_dir.resolve())
    # Why: campaign evidence must not point to a convenient file outside this run.
    except ValueError:
        return None, f"{name}: attachment source escapes results directory: {source}"
    # Why: a missing or empty attachment makes a clickable report link misleading.
    if not path.is_file() or path.stat().st_size == 0:
        return None, f"{name}: attachment '{attachment.get('name', '<unnamed>')}' is missing or empty: {source}"
    return path, None


def verify_trace(path: Path, name: str) -> str | None:
    # Require a real Playwright trace archive, not merely an attachment named "trace".
    try:
        with zipfile.ZipFile(path) as archive:
            # Why: an arbitrary ZIP named trace cannot explain browser behavior.
            trace_members = [member for member in archive.infolist() if member.filename.endswith("trace.trace")]
            if not trace_members or not any(member.file_size for member in trace_members):
                return f"{name}: trace attachment is not a Playwright trace ZIP"
            # Why: a ZIP directory can be readable while one of its data members is corrupt.
            if archive.testzip() is not None:
                return f"{name}: trace attachment has corrupt ZIP contents"
    # Why: a corrupt archive cannot open in Trace Viewer.
    except zipfile.BadZipFile:
        return f"{name}: trace attachment is not a readable ZIP"
    return None


def verify_test_plan_content(
    name: str, content: str, story: str, require_steps: bool = False
) -> tuple[list[str], list[str], list[tuple[str, str]]]:
    # Keep the report scannable and make every visible outcome traceable to an AC.
    failures: list[str] = []
    # `Steps:` (the user-level procedure) is an authoring rule enforced at preflight;
    # post-run verification does not re-gate an already-frozen plan on it.
    required = ("Data", "Isolation", "Steps", "Visible outcomes", "Acceptance mapping")
    for section in required:
        if section == "Steps" and not require_steps:
            continue
        if not re.search(rf"(?m)^{section}:\s*$", content):
            failures.append(f"{name}: Test plan lacks required '{section}:' section")
    # Why: a reader should follow the procedure without opening Trace Viewer; keep it to
    # user-level actions ("select Medic, enter credentials, submit"), never selectors.
    if require_steps and not re.search(r"(?m)^Steps:[ \t]*\r?\n(?:[ \t]*(?:-|\d+\.)[ \t]+\S)", content):
        failures.append(f"{name}: Test plan 'Steps:' must list at least one user-level action")
    match = re.search(r"(?m)^Visible outcomes:[ \t]*\r?\n((?:\s*-\s+[^\r\n]*(?:\r?\n|$))*)", content)
    outcomes = [re.sub(r"^\s*-\s+", "", line).strip() for line in match.group(1).splitlines() if re.match(r"^\s*-\s+\S", line)] if match else []
    # Why: a scenario needs at least one human-visible claim, not only setup activity.
    if not outcomes:
        failures.append(f"{name}: Test plan 'Visible outcomes:' must list at least one outcome")
    mapping_match = re.search(r"(?m)^Acceptance mapping:[ \t]*\r?\n((?:\s*-\s+[^\r\n]*(?:\r?\n|$))*)", content)
    mappings: list[tuple[str, str, str, str]] = []
    for line in mapping_match.group(1).splitlines() if mapping_match else []:
        entry = re.fullmatch(r"\s*-\s*(AC\d+) \| Story clause: (.+?) \| Actor: (.+?) \| Visible outcome: (.+)", line)
        # Keep the actor in the plan for reviewers; natural-language role interpretation belongs
        # in the bounded semantic review, not a brittle static parser.
        if not entry:
            failures.append(f"{name}: invalid Acceptance mapping entry: {line}")
            continue
        criterion, clause, actor, outcome = (value.strip() for value in entry.groups())
        mappings.append((criterion, clause, actor, outcome))
        if not actor:
            failures.append(f"{name}: Acceptance mapping actor is empty for {criterion}")
        # Why: every mapped claim must be visible in the reviewable outcome list.
        if normalize_phrase(outcome) not in {normalize_phrase(item) for item in outcomes}:
            failures.append(f"{name}: Acceptance mapping outcome is not declared under Visible outcomes: {outcome}")
    for outcome in outcomes:
        # Why: prevents undeclared outcome assertions from drifting into adjacent stories.
        if not any(normalize_phrase(mapped) == normalize_phrase(outcome) for _, _, _, mapped in mappings):
            failures.append(f"{name}: Visible outcome lacks an Acceptance mapping: {outcome}")
    # Why: one compound AC may need several distinct visible outcomes, so the same ACn may
    # repeat — but not the same (ACn, outcome) pair, which would be duplicate filler.
    criterion_outcome_pairs = [(criterion, normalize_phrase(outcome)) for criterion, _, _, outcome in mappings]
    if len(set(criterion_outcome_pairs)) != len(criterion_outcome_pairs):
        failures.append(f'{name}: Acceptance mapping repeats an (AC, visible outcome) pair')
    boundary_failures, _ = verify_boundary_coverage(name, content, story, require_complete=False)
    failures.extend(boundary_failures)
    return failures, outcomes, [(criterion, actor) for criterion, _, actor, _ in mappings]


def verify_test_plan(path: Path, name: str, story: str) -> tuple[list[str], list[str], list[tuple[str, str]]]:
    try:
        content = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return [f"{name}: Test plan attachment must be UTF-8 text"], [], []
    return verify_test_plan_content(name, content, story)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def verify_foundation(manifest_path: Path) -> list[str]:
    # Story agents must not silently alter shared runner/auth helpers to make one story pass.
    # Why: a run cannot prove its runner/authentication baseline without this manifest.
    if not manifest_path.is_file():
        return [f"{manifest_path}: protected E2E foundation manifest is missing"]
    working_manifest = manifest_path.read_text(encoding="utf-8")
    try:
        repo_root = Path(subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            cwd=manifest_path.parent, check=True, capture_output=True, text=True,
        ).stdout.strip()).resolve()
        relative_manifest = manifest_path.resolve().relative_to(repo_root).as_posix()
        indexed_manifest = subprocess.run(
            ["git", "show", f":{relative_manifest}"],
            cwd=repo_root, check=True, capture_output=True, text=True,
        ).stdout
    except (subprocess.CalledProcessError, ValueError) as error:
        return [
            f"{manifest_path}: foundation manifest must be staged before a story run ({error}). "
            f"{FOUNDATION_OWNER_HINT}"
        ]
    # Why: a story agent must not rewrite the baseline it is being checked against.
    if working_manifest != indexed_manifest:
        return [
            f"{manifest_path}: differs from its staged foundation baseline. {FOUNDATION_OWNER_HINT}"
        ]
    try:
        protected = json.loads(indexed_manifest).get("protected_files")
    except json.JSONDecodeError as error:
        return [f"{manifest_path}: invalid foundation JSON ({error})"]
    # Why: an empty protected list would claim integrity protection while protecting nothing.
    if not isinstance(protected, list) or not protected:
        return [f"{manifest_path}: protected_files must be a non-empty list"]
    failures: list[str] = []
    foundation_root = manifest_path.parent.resolve()
    for entry in protected:
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str) or not isinstance(entry.get("sha256"), str):
            failures.append(f"{manifest_path}: each protected file needs path and sha256")
            continue
        candidate = (foundation_root / entry["path"]).resolve()
        try:
            candidate.relative_to(foundation_root)
        except ValueError:
            failures.append(f"{manifest_path}: protected path escapes foundation: {entry['path']}")
            continue
        # Why: a deleted shared helper leaves the trusted foundation incomplete.
        if not candidate.is_file():
            failures.append(f"{manifest_path}: protected file is missing: {entry['path']}")
        # Why: mutable runner/auth helpers could manufacture a passing story result.
        elif sha256(candidate) != entry["sha256"]:
            failures.append(
                f"{manifest_path}: protected file changed: {entry['path']}. {FOUNDATION_OWNER_HINT}"
            )
    return failures


def normalize_phrase(value: str) -> str:
    return re.sub(r"[^\w]+", " ", value).casefold().strip()


def verify_video(path: Path, name: str) -> str | None:
    """Reject an empty/renamed file that Allure merely labels as video."""
    try:
        header = path.read_bytes()[:16]
    except OSError:
        return f"{name}: video attachment cannot be read"
    # Playwright records WebM; MP4 support keeps the check portable for project-owned configs.
    if header.startswith(b"\x1a\x45\xdf\xa3") or header[4:8] == b"ftyp":
        return None
    return f"{name}: video attachment is not a recognised WebM or MP4 file"


def assertion_evidence_values(assertion_step: dict) -> tuple[str, str] | None:
    """Read proof from the assertion itself or its dedicated nested Evidence step."""
    for step in [assertion_step, *flatten(assertion_step.get("steps", []))]:
        values = re.search(
            r"(?:Evidence:\s*)?expected:\s*(.*?)\s*\|\s*observed:\s*(.*?)\s*$",
            str(step.get("name", "")),
            re.IGNORECASE | re.DOTALL,
        )
        if values:
            # Rendered text can legitimately contain a line break. Preserve the
            # proof while keeping the report parser independent of presentation.
            return tuple(re.sub(r"\s+", " ", value).strip() for value in values.groups())
    return None


def explicit_story_states(story: str) -> list[str]:
    # Why: capture only transition/status grammar, never role names or action labels.
    states: list[str] = []
    patterns = (
        r"\b(?i:(?:registration\s+)?status\s+(?:to|as))\s+([A-Z][A-Za-z0-9-]*(?:\s+[A-Z][A-Za-z0-9-]*)*)",
        r"\b(?i:move|mark|return|transition)\b[^.]{0,120}?\b(?i:as)\s+(?:(?i:a)\s+)?([A-Z][A-Za-z0-9-]*(?:\s+[A-Z][A-Za-z0-9-]*)*)",
        r"\b(?i:as)\s+(?:(?i:a)\s+)?([A-Z][A-Za-z0-9]*-[A-Za-z0-9-]*)",
    )
    for pattern in patterns:
        for match in re.finditer(pattern, story):
            state = match.group(1)
            if state not in states:
                states.append(state)
    return states


def canonical_boundary_condition(value: str) -> str | None:
    """Return a stable form for the small, explicit integer-boundary grammar.

    This intentionally handles only comparison/range lists such as ``< 50, 50-59,
    60``.  Parsing arbitrary prose would create a misleading, project-specific gate.
    """
    value = value.strip()
    comparison = re.fullmatch(r"(<=|>=|<|>|≤|≥)\s*(\d+)", value)
    if comparison:
        operator = {"≤": "<=", "≥": ">="}.get(comparison.group(1), comparison.group(1))
        return f"{operator}{int(comparison.group(2))}"
    numeric_range = re.fullmatch(r"(\d+)\s*(?:-|–|to)\s*(\d+)", value, re.IGNORECASE)
    if numeric_range:
        lower, upper = (int(part) for part in numeric_range.groups())
        return f"{lower}-{upper}" if lower <= upper else None
    if re.fullmatch(r"\d+", value):
        return str(int(value))
    return None


def expected_integer_boundary_conditions(story: str) -> dict[str, set[int]]:
    """Extract explicit integer bands from a parenthetical condition list in the story."""
    conditions: dict[str, set[int]] = {}
    for parenthetical in re.findall(r"\(([^()]*)\)", story):
        if not re.search(r"(?:<=|>=|<|>|≤|≥)\s*\d+|\b\d+\s*(?:-|–|to)\s*\d+\b", parenthetical):
            continue
        for item in re.split(r"[,;]", parenthetical):
            comparison = re.search(r"(<=|>=|<|>|≤|≥)\s*(\d+)", item)
            numeric_range = re.search(r"\b(\d+)\s*(?:-|–|to)\s*(\d+)\b", item, re.IGNORECASE)
            if comparison:
                condition = canonical_boundary_condition("".join(comparison.groups()))
            elif numeric_range:
                condition = canonical_boundary_condition(f"{numeric_range.group(1)}-{numeric_range.group(2)}")
            else:
                # A bare integer belongs to this numeric list only when the same
                # parenthetical also contains a comparator/range (for example, Red 60).
                bare = re.fullmatch(r"\s*(?:[A-Za-z][A-Za-z -]*\s+)?(\d+)\s*", item)
                condition = canonical_boundary_condition(bare.group(1)) if bare else None
            if not condition:
                continue
            if condition.startswith("<") and not condition.startswith("<="):
                required = {int(condition[1:]) - 1}
            elif condition.startswith(">") and not condition.startswith(">="):
                required = {int(condition[1:]) + 1}
            elif condition.startswith(("<=", ">=")):
                required = {int(condition[2:])}
            elif "-" in condition:
                lower, upper = (int(part) for part in condition.split("-", 1))
                required = {lower, upper}
            else:
                required = {int(condition)}
            conditions[condition] = required
    return conditions


def parse_boundary_coverage(content: str, name: str) -> tuple[list[str], dict[str, set[int]]]:
    """Validate a Test plan's explicit integer-boundary rows, if its story needs them."""
    failures: list[str] = []
    match = re.search(r"(?m)^Boundary coverage:[ \t]*\r?\n((?:- [^\r\n]*(?:\r?\n|$))*)", content)
    if not match:
        return failures, {}
    coverage: dict[str, set[int]] = {}
    for line in match.group(1).splitlines():
        entry = re.fullmatch(r"- (AC\d+) \| Condition: (.+?) \| Values: (\d+(?:\s*,\s*\d+)*)", line.strip())
        if not entry:
            failures.append(f"{name}: invalid Boundary coverage entry: {line}")
            continue
        _, raw_condition, raw_values = entry.groups()
        condition = canonical_boundary_condition(raw_condition)
        if not condition:
            failures.append(f"{name}: Boundary coverage condition is not an integer comparison/range: {raw_condition}")
            continue
        if condition in coverage:
            failures.append(f"{name}: Boundary coverage repeats condition '{condition}'")
            continue
        values = [int(value.strip()) for value in raw_values.split(",")]
        if len(values) != len(set(values)):
            failures.append(f"{name}: Boundary coverage repeats value(s) for '{condition}'")
            continue
        coverage[condition] = set(values)
    return failures, coverage


def verify_boundary_coverage(name: str, content: str, story: str, require_complete: bool) -> tuple[list[str], dict[str, set[int]]]:
    expected = expected_integer_boundary_conditions(story)
    failures, coverage = parse_boundary_coverage(content, name)
    if not expected:
        return failures, coverage
    if not coverage:
        return failures + [f"{name}: Test plan needs 'Boundary coverage:' for explicit integer bands/ranges in the story"], coverage
    for condition, values in coverage.items():
        if condition not in expected:
            failures.append(f"{name}: Boundary coverage condition '{condition}' is not an explicit story condition")
        elif not values <= expected[condition]:
            required = ", ".join(str(value) for value in sorted(expected[condition]))
            observed = ", ".join(str(value) for value in sorted(values))
            failures.append(f"{name}: Boundary coverage for '{condition}' may list only story boundary value(s) {required}, got {observed}")
    if require_complete:
        missing = sorted(set(expected) - set(coverage))
        if missing:
            failures.append(f"{name}: Boundary coverage is missing explicit story condition(s): {', '.join(missing)}")
        for condition, required in expected.items():
            missing_values = sorted(required - coverage.get(condition, set()))
            if missing_values:
                failures.append(
                    f"{name}: Boundary coverage for '{condition}' is missing value(s): {', '.join(str(value) for value in missing_values)}"
                )
    return failures, coverage


def source_for_result(result: dict, spec_dir: Path) -> Path | None:
    labels = {label.get("name"): label.get("value", "") for label in result.get("labels", [])}
    package = labels.get("package", "")
    matches = [candidate for candidate in spec_dir.rglob("*.spec.*") if package.endswith(candidate.name)]
    return matches[0] if len(matches) == 1 else None


_PROSE_SUBSTITUTIONS = (
    ("’", "'"), ("‘", "'"), ("“", '"'), ("”", '"'),
    ("–", "-"), ("—", "-"), ("…", "..."),
)


def normalize_prose(text: object) -> str:
    """Collapse whitespace and normalise quotes/dashes.

    Comparisons run on this form so a cosmetic story edit (a line rewrap, a
    smart quote) never forces every frozen spec to be re-authored and re-run.
    """
    collapsed = " ".join(str(text or "").split())
    for source, target in _PROSE_SUBSTITUTIONS:
        collapsed = collapsed.replace(source, target)
    return collapsed.strip()


def story_intent(story: str) -> str | None:
    """Return the explicit user-story intent when the source supplies one.

    The intent is the leading `As a … I want … so that …` sentence, which may be
    wrapped across lines; it ends where the numbered / `AC<n>` criteria begin,
    whether those are a blank line away or just the next line.
    """
    for paragraph in re.split(r"\r?\n\s*\r?\n", str(story).strip()):
        lines: list[str] = []
        for line in paragraph.strip().splitlines():
            if re.match(r"(?i)^\s*(?:AC\d+|\d+)[.):]", line):
                break
            lines.append(line)
        normalized = normalize_prose(" ".join(lines))
        if re.match(r"(?i)^As\s+", normalized):
            return normalized
    return None


def description_failures(
    name: str,
    description: object,
    sub_suite: object,
    story: str,
    acceptance_criteria: dict[str, str] | None = None,
    status: str = "passed",
) -> list[str]:
    """Bind report prose to the result's own AC, for every status.

    subSuite and the Description's AC line are both the verbatim frozen
    criterion (an AC<n>b scenario keeps the suffix on the label only). A
    non-passing scenario appends exactly one plain-language reason paragraph
    after the AC line — ``Failure: <why>`` when failed, ``Blocked: <why>`` when
    skipped — required for that status and forbidden otherwise. Comparison is
    whitespace/quote-normalised, so a compound multi-line criterion and a
    cosmetic rewrap both pass.
    """
    sub_suite_match = AC_SUBSUITE_PATTERN.fullmatch(normalize_prose(sub_suite))
    if not sub_suite_match:
        return [f"{name}: Allure subSuite must be 'AC<n>: <acceptance criterion>'"]
    label = sub_suite_match.group(1)
    base_ac = re.fullmatch(r"(AC\d+)[a-z]?", label, re.IGNORECASE).group(1).upper()
    criterion = acceptance_criteria.get(base_ac) if acceptance_criteria else None
    if acceptance_criteria is not None and not criterion:
        return [f"{name}: Allure subSuite names an AC absent from frozen story-analysis.json"]

    failures: list[str] = []
    if criterion and normalize_prose(sub_suite) != normalize_prose(f"{label}: {criterion}"):
        failures.append(f"{name}: Allure subSuite must be the verbatim '{label}: <frozen acceptance criterion>'")

    trailer_keyword = {"failed": "Failure", "skipped": "Blocked"}.get(status)
    paragraphs = [p.strip() for p in re.split(r"\r?\n\s*\r?\n", str(description or "").strip()) if p.strip()]
    trailer = re.match(r"(?is)^(Failure|Blocked)\s*:\s*\S", paragraphs[-1]) if paragraphs else None
    if trailer:
        found = trailer.group(1).capitalize()
        paragraphs.pop()
        if trailer_keyword is None:
            failures.append(f"{name}: only a failed or skipped scenario may carry a '{found}:' explanation in its Description")
        elif found != trailer_keyword:
            failures.append(f"{name}: a {status} scenario's Description trailer must be '{trailer_keyword}: <why>', not '{found}:'")
    elif trailer_keyword:
        failures.append(f"{name}: a {status} scenario's Description must append a plain-language '{trailer_keyword}: <why>' paragraph after the AC line")

    description_text = normalize_prose("\n\n".join(paragraphs))
    if not description_text:
        return failures + [f"{name}: missing Allure Description"]

    intent = story_intent(story)
    if intent:
        if not description_text.startswith(intent):
            return failures + [f"{name}: Allure Description must be the user-story intent, then the {base_ac} line"]
        description_text = description_text[len(intent):].strip()

    if criterion:
        if description_text != normalize_prose(f"{base_ac}: {criterion}"):
            failures.append(f"{name}: Allure Description must be exactly '{base_ac}: <frozen acceptance criterion>'")
    elif not re.match(rf"(?i){re.escape(base_ac)}\s*:\s*\S", description_text):
        failures.append(f"{name}: Allure Description must start with '{base_ac}:' and describe only that AC")
    return failures


def verify_result(
    path: Path,
    results_dir: Path,
    story: str,
    spec_dir: Path,
    acceptance_criteria: dict[str, str],
    isolation_id: str | None = None,
) -> tuple[list[str], str]:
    # Bind this result to the requested story so nearby implementation behavior cannot drift into scope.
    result = json.loads(path.read_text(encoding="utf-8"))
    name = result.get("name", path.name)
    failures: list[str] = []
    status = result.get("status", "unknown")
    # A final story verdict must be intelligible to a user. Playwright/Allure's
    # infrastructure-only statuses require diagnosis and a fresh run, not handoff.
    if status not in {"passed", "failed", "skipped"}:
        failures.append(
            f"{name}: final story evidence status must be passed, failed, or skipped; got {status!r}"
        )
    labels = {label.get("name"): label.get("value") for label in result.get("labels", [])}
    for label in ("parentSuite", "suite", "subSuite"):
        if not labels.get(label):
            failures.append(f"{name}: missing Allure {label}")
    failures.extend(
        description_failures(name, result.get("description"), labels.get("subSuite"), story, acceptance_criteria, status)
    )
    parameter_values = {
        parameter.get("name"): str(parameter.get("value", ""))
        for parameter in result.get("parameters", [])
    }
    parameters = set(parameter_values)
    for parameter in ("persona", "e2e_configuration"):
        # Why: persona/configuration determine what the browser could actually prove.
        if parameter not in parameters:
            failures.append(f"{name}: missing Allure parameter '{parameter}'")
    # A concurrent worker must bind its evidence to its own isolated lifecycle. This keeps a
    # valid-looking result from a neighbouring worker's stack out of the final handoff.
    if isolation_id:
        isolation_marker = rf"(?:^|[;,\s])isolation_id={re.escape(isolation_id)}(?:$|[;,\s])"
        if not re.search(isolation_marker, parameter_values.get("e2e_configuration", "")):
            failures.append(
                f"{name}: e2e_configuration lacks isolation_id={isolation_id}"
            )
    # Inspect nested steps for the plan, assertions, video, and trace produced by this exact scenario.
    steps = flatten(result.get("steps", []))
    attachments = [attachment for step in steps for attachment in step.get("attachments", [])]
    plans = [attachment for attachment in attachments if attachment.get("name", "").lower() == "test plan"]
    # Why: a trace without a stated plan cannot show which behavior was intentional.
    if not plans:
        failures.append(f"{name}: missing Test plan attachment")
    visible_outcomes: list[str] = []
    acceptance_mappings: list[tuple[str, str]] = []
    boundary_values: set[int] = set()
    for plan in plans:
        plan_path, error = attachment_file(results_dir, plan, name)
        if error:
            failures.append(error)
        elif plan_path:
            plan_failures, plan_outcomes, plan_acceptance_mappings = verify_test_plan(plan_path, name, story)
            failures.extend(plan_failures)
            visible_outcomes.extend(plan_outcomes)
            acceptance_mappings.extend(plan_acceptance_mappings)
            _, plan_coverage = parse_boundary_coverage(plan_path.read_text(encoding="utf-8"), name)
            boundary_values.update(value for values in plan_coverage.values() for value in values)
    faux_skips = [
        step.get("name", "")
        for step in steps
        if re.match(r"\s*(?:SKIPPED|BLOCKED)\s*:", step.get("name", ""), re.IGNORECASE)
    ]
    for faux_skip in faux_skips:
        failures.append(
            f"{name}: inline skipped step is not an Allure skipped scenario: {faux_skip}"
        )
    # A blocked precondition is valid only when reported as a human-readable skipped outcome.
    # Why: skips need separate evidence so a passing test cannot hide an untested clause.
    if status == "skipped":
        if re.search(r"\bblocked\b", name, re.IGNORECASE):
            failures.append(f"{name}: skipped test name must state the attempted story outcome, not its skip reason")
        short_reason = result.get("statusDetails", {}).get("message", "").strip()
        if not short_reason:
            failures.append(f"{name}: skipped scenario lacks a short status message")
        # The Test plan is also text/plain; require a separate named diagnosis rather than letting it satisfy this check.
        blockers = [attachment for attachment in attachments if attachment.get("name", "").lower() == "blocker evidence" and attachment.get("type") == "text/plain"]
        if not blockers:
            failures.append(f"{name}: skipped scenario lacks a text blocker-evidence attachment")
        for blocker in blockers:
            _, error = attachment_file(results_dir, blocker, name)
            if error:
                failures.append(error)
        return failures, status
    # Named expected/observed assertions make the report understandable without opening the trace.
    assertion_steps = [step for step in steps if step.get("name", "").startswith("Assertion:")]
    assertions = [str(step.get("name", "")) for step in assertion_steps]
    # Why: a green result with no named user outcome is not reviewable evidence.
    if not assertions:
        failures.append(f"{name}: no named outcome assertion step")
    evidence_by_assertion: list[tuple[str, tuple[str, str]]] = []
    for assertion_step, assertion in zip(assertion_steps, assertions):
        # Why: reviewers need visible expected and observed values without opening Trace Viewer.
        values = assertion_evidence_values(assertion_step)
        if values is None:
            failures.append(f"{name}: assertion omits expected/observed values: {assertion}")
            continue
        evidence_by_assertion.append((assertion, values))
        if all(
            re.fullmatch(r"(?:HTTP\s*)?[1-5]\d{2}", value.strip(), re.IGNORECASE)
            for value in values
        ):
            failures.append(
                f"{name}: assertion uses a bare HTTP status as its user-visible outcome: {assertion}"
            )
    # Why: a declared boundary is evidence only when the fresh result reports that concrete
    # value from a runtime assertion, rather than merely naming it in the authored Test plan.
    observed_assertions = "\n".join(values[1] for _, values in evidence_by_assertion)
    for value in sorted(boundary_values):
        if not re.search(rf"(?<!\d){value}(?!\d)", observed_assertions):
            failures.append(f"{name}: Boundary coverage value {value} is absent from fresh assertion observed values")
    for criterion, _ in acceptance_mappings:
        # Bind each mapped AC to a named runtime assertion. Actor/action/polarity interpretation
        # remains a review task because natural-language roles cannot be parsed reliably.
        pattern = rf"Assertion:\s*\[{re.escape(criterion)}\](?=\s|\||$)"
        if not any(re.match(pattern, assertion, re.IGNORECASE) for assertion in assertions):
            failures.append(
                f"{name}: Acceptance mapping {criterion} lacks a matching "
                f"'Assertion: [{criterion}]' step"
            )
    # A domain transition need not be literal UI copy. But if a report claims an exact
    # state label in expected/observed values, its source must read that state from a visible locator.
    source_path: Path | None = None
    source = ""
    for state in explicit_story_states(story):
        normalized_state = normalize_phrase(state)
        state_is_claimed = any(
            normalized_state in normalize_phrase(assertion)
            and values is not None
            for assertion, values in evidence_by_assertion
        )
        if not state_is_claimed:
            continue
        if source_path is None:
            source_path = source_for_result(result, spec_dir)
            if source_path is None:
                failures.append(f"{name}: cannot identify its source spec for rendered-state verification")
            else:
                source = source_path.read_text(encoding="utf-8")
        state_literal = re.escape(state)
        if not re.search(rf"assertVisibleState\s*\(\s*['\"]{state_literal}['\"]", source):
            failures.append(f"{name}: claimed state '{state}' must use assertVisibleState with a rendered UI locator")
    videos = [attachment for attachment in attachments if attachment.get("type", "").startswith("video/")]
    # Why: video gives an independent quick view of the human flow.
    if not videos:
        failures.append(f"{name}: missing video attachment")
    for video in videos:
        video_path, error = attachment_file(results_dir, video, name)
        if error:
            failures.append(error)
        elif video_path:
            video_error = verify_video(video_path, name)
            if video_error:
                failures.append(video_error)
    traces = [attachment for attachment in attachments if attachment.get("type") == "application/vnd.allure.playwright-trace"]
    # Why: trace preserves inspectable action and DOM history when video is ambiguous.
    if not traces:
        failures.append(f"{name}: missing Playwright trace attachment")
    for trace in traces:
        trace_path, error = attachment_file(results_dir, trace, name)
        if error:
            failures.append(error)
        elif trace_path:
            trace_error = verify_trace(trace_path, name)
            if trace_error:
                failures.append(trace_error)
    return failures, status


def unescape_ts_string(value: str) -> str:
    # Why: process one escape at a time so \\n stays backslash+n, not a newline.
    mapping = {"n": "\n", "t": "\t", "'": "'", '"': '"', "\\": "\\"}
    out: list[str] = []
    index = 0
    while index < len(value):
        char = value[index]
        if char == "\\" and index + 1 < len(value):
            out.append(mapping.get(value[index + 1], value[index : index + 2]))
            index += 2
            continue
        out.append(char)
        index += 1
    return "".join(out)


LOCAL_TEXT_CONST_PATTERN = re.compile(
    r"\bconst\s+(?P<name>[A-Z][A-Z0-9_]*)\s*=\s*\[(?P<body>.*?)]\s*"
    r"\.join\(\s*['\"]\\n['\"]\s*\)\s*;",
    re.DOTALL,
)
PLAN_TOKEN_PATTERN = re.compile(r"'((?:\\'|[^'])*)'|\"((?:\\\"|[^\"])*)\"|\b([A-Z][A-Z0-9_]*)\b")


def extract_local_text_constants(source: str) -> dict[str, str]:
    """Resolve only simple local string-array constants; never evaluate code or imports."""
    constants: dict[str, str] = {}
    literal_pattern = r"'((?:\\'|[^'])*)'|\"((?:\\\"|[^\"])*)\""
    for match in LOCAL_TEXT_CONST_PATTERN.finditer(source):
        body = match.group("body")
        literals = re.findall(literal_pattern, body)
        residue = re.sub(literal_pattern, "", body)
        if literals and re.fullmatch(r"[\s,]*", residue):
            constants[match.group("name")] = "\n".join(unescape_ts_string(a or b) for a, b in literals)
    return constants


def extract_simple_text_constant(source: str, name: str) -> str | None:
    """Read one literal string without evaluating TypeScript."""
    match = re.search(
        rf"\bconst\s+{re.escape(name)}\s*=\s*(?:`((?:\\.|[^`\\])*)`|'((?:\\.|[^'\\])*)'|\"((?:\\.|[^\"\\])*)\")\s*;?",
        source,
        re.DOTALL,
    )
    if match is None:
        return None
    return unescape_ts_string(next(value for value in match.groups() if value is not None)).strip()


def extract_plan_array(body: str, constants: dict[str, str]) -> str | None:
    """Reconstruct a literal Test-plan array with optional simple local text constants."""
    parts: list[str] = []
    residue: list[str] = []
    cursor = 0
    for token in PLAN_TOKEN_PATTERN.finditer(body):
        residue.append(body[cursor:token.start()])
        single, double, identifier = token.groups()
        if identifier:
            value = constants.get(identifier)
            if value is None:
                return None
            parts.append(value)
        else:
            parts.append(unescape_ts_string(single if single is not None else double))
        cursor = token.end()
    residue.append(body[cursor:])
    if not parts or not re.fullmatch(r"[\s,]*", "".join(residue)):
        return None
    return "\n".join(parts)


def extract_test_plans_from_source(source: str) -> list[str]:
    # Why: a spec may contain independent scenarios, each with its own Test plan.
    matches = re.finditer(
        r"allure\.attachment\(\s*['\"]Test plan['\"]\s*,\s*\[(.*?)]\s*\.join\(\s*['\"]\\n['\"]\s*\)",
        source,
        re.DOTALL,
    )
    constants = extract_local_text_constants(source)
    plans: list[str] = []
    for match in matches:
        if plan := extract_plan_array(match.group(1), constants):
            plans.append(plan)
    return plans


def preflight_story_metadata_failures(
    name: str, source: str, story: str, acceptance_criteria: dict[str, str] | None = None
) -> list[str]:
    """Reject story/plan drift before the browser evidence run.

    Supports the documented template literal and literal-array forms. Both are
    quote-aware, so punctuation such as a semicolon in the story can never
    terminate extraction early.
    """
    failures: list[str] = []
    authored_story = extract_simple_text_constant(source, "STORY") or extract_local_text_constants(source).get("STORY")
    if authored_story is None:
        failures.append(f"{name}: requires a literal const STORY for Allure story binding")
    elif normalize_prose(authored_story) != normalize_prose(story):
        failures.append(f"{name}: const STORY does not match story-analysis.json")
    description_calls = re.findall(
        r"allure\s*\.\s*description\s*\(\s*(.*?)\s*\)", source, re.DOTALL
    )
    description_source = "\n".join(description_calls)
    normalized_description_source = normalize_prose(description_source)
    intent = story_intent(story)
    if re.search(r"allure\s*\.\s*description\s*\(\s*STORY\s*\)", source):
        failures.append(f"{name}: Allure Description must identify only this AC, not STORY")
    elif not description_calls:
        failures.append(f"{name}: requires an Allure Description for this AC")
    else:
        if not re.search(r"\bAC_DESCRIPTION\b|\bAC\d+[a-z]?\s*:", description_source, re.IGNORECASE):
            failures.append(f"{name}: Allure Description must include an AC<n>: description")
        if intent and "STORY_INTENT" not in description_source and intent not in normalized_description_source:
            failures.append(f"{name}: Allure Description must include the user-story intent before its AC")

    if acceptance_criteria is not None:
        ac_description = extract_simple_text_constant(source, "AC_DESCRIPTION")
        if ac_description is None:
            failures.append(f"{name}: requires literal const AC_DESCRIPTION from story-analysis.json")
        else:
            # A compound criterion may span lines; match on the normalised form.
            label_match = AC_SUBSUITE_PATTERN.fullmatch(normalize_prose(ac_description))
            base_match = re.fullmatch(r"(AC\d+)[a-z]?", label_match.group(1), re.IGNORECASE) if label_match else None
            expected = acceptance_criteria.get(base_match.group(1).upper()) if base_match else None
            if not expected:
                failures.append(f"{name}: AC_DESCRIPTION names an AC absent from story-analysis.json")
            elif normalize_prose(ac_description) != normalize_prose(f"{base_match.group(1).upper()}: {expected}"):
                failures.append(f"{name}: AC_DESCRIPTION must be the frozen acceptance criterion, not a summary")
        if "AC_DESCRIPTION" not in description_source:
            failures.append(f"{name}: Allure Description must use AC_DESCRIPTION")
        if intent:
            authored_intent = extract_simple_text_constant(source, "STORY_INTENT")
            if authored_intent is None or normalize_prose(authored_intent) != intent:
                failures.append(f"{name}: STORY_INTENT must match the frozen user-story intent")
            if "STORY_INTENT" not in description_source:
                failures.append(f"{name}: Allure Description must use STORY_INTENT before AC_DESCRIPTION")

    plans = extract_test_plans_from_source(source)
    if not plans:
        failures.append(f"{name}: no statically readable Test plan attachment")
    mapped_criteria: set[str] = set()
    for index, plan in enumerate(plans, start=1):
        plan_failures, _, mappings = verify_test_plan_content(
            f"{name} Test plan #{index}", plan, story, require_steps=True
        )
        failures.extend(plan_failures)
        mapped_criteria.update(criterion.upper() for criterion, _ in mappings)

    # A literal assertion label lets preflight catch AC4/AC4b-style typos cheaply.
    # Dynamic labels are valid TypeScript, so leave those to the post-run verifier.
    static_assertions = {
        criterion.upper()
        for criterion in re.findall(r"Assertion:\s*\[(AC\d+[a-z]?)\]", source, re.IGNORECASE)
    }
    if static_assertions:
        for criterion in sorted(mapped_criteria - static_assertions):
            failures.append(
                f"{name}: Acceptance mapping {criterion} lacks a static matching Assertion: [{criterion}] title"
            )
    return failures


def load_protected_paths(foundation_manifest: Path) -> tuple[set[Path] | None, list[str]]:
    try:
        protected_paths = {
            (foundation_manifest.parent / entry["path"]).resolve()
            for entry in json.loads(foundation_manifest.read_text(encoding="utf-8"))["protected_files"]
            if isinstance(entry, dict) and isinstance(entry.get("path"), str)
        }
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        return None, [f"{foundation_manifest}: cannot read protected foundation imports ({error})"]
    return protected_paths, []


def scan_spec_graph(root_spec: Path, protected_paths: set[Path]) -> list[str]:
    # Why: unprotected imports and hidden API/DB setup must fail before expensive runs.
    failures: list[str] = []
    visited: set[Path] = set()

    def visit(path: Path) -> None:
        path = path.resolve()
        if path in visited or not path.is_file():
            return
        visited.add(path)
        # Why: an unprotected imported helper lets one story alter shared login/setup behavior.
        if path != root_spec.resolve() and path not in protected_paths:
            failures.append(
                f"{root_spec}: relative import is not protected by the foundation manifest: {path}"
            )
        contents = path.read_text(encoding="utf-8").lower()
        # The manifest is the trust boundary for harness-owned fixtures. Continue walking their
        # imports so an unprotected dependency still fails, but do not reject their approved
        # setup mechanics as if a story spec had authored them.
        if path == root_spec.resolve() or path not in protected_paths:
            for token in FORBIDDEN_IMPORT_TOKENS:
                if token in contents:
                    failures.append(f"{root_spec}: forbidden '{token}' reachable through {path}")
            for pattern, label in FORBIDDEN_IMPORT_PATTERNS:
                if re.search(pattern, contents):
                    failures.append(f"{root_spec}: forbidden {label} reachable through {path}")
        for module in re.findall(r"from\s+['\"](\.[^'\"]+)['\"]", contents):
            base = path.parent / module
            for candidate in (base, base.with_suffix(".ts"), base.with_suffix(".tsx"), base.with_suffix(".js"), base / "index.ts"):
                if candidate.is_file():
                    visit(candidate)
                    break

    visit(root_spec)
    return failures


def verify_specs(
    spec_dir: Path,
    newest_result_mtime: float,
    results: list[dict],
    foundation_manifest: Path,
    story: str,
) -> list[str]:
    # Reject implementation-coupled or focused specs, and reject source newer than its evidence.
    failures: list[str] = []
    protected_paths, protected_failures = load_protected_paths(foundation_manifest)
    if protected_paths is None:
        return protected_failures
    failures.extend(scan_scratch_specs(spec_dir))
    terminal_action = re.compile(r"getbyrole\s*\(\s*['\"]button['\"].{0,220}?(?:complete|no-show|cancel|delete|release)", re.IGNORECASE | re.DOTALL)
    isolated_declaration = re.compile(r"Isolation:\s*['\"`]?\s*,?\s*\n?\s*['\"`]?\s*-\s*Isolated E2E (?:stack|database)", re.IGNORECASE)
    source_specs: set[Path] = set()
    for result in results:
        source = source_for_result(result, spec_dir)
        if source is None:
            failures.append(f"{result.get('name', '<unnamed result>')}: cannot identify its source spec")
        else:
            source_specs.add(source)
    for path in sorted(source_specs):
        # Why: evidence older than its source may describe a different test.
        if path.stat().st_mtime > newest_result_mtime:
            failures.append(f"{path}: newer than the result files; rerun the suite")
        source = path.read_text(encoding="utf-8")
        if force_failure := forced_ui_action_failure(str(path), source):
            failures.append(force_failure)
        # Why: terminal actions must not irreversibly mutate shared workflow data.
        if terminal_action.search(source) and not isolated_declaration.search(source):
            failures.append(f"{path}: terminal UI action requires an 'Isolation:' declaration naming an isolated E2E stack/database")
        failures.extend(scan_spec_graph(path, protected_paths))
    # A story may split its bands across independently runnable specs.  Preflight validates
    # each row; final verification requires the set of source specs represented by this fresh
    # evidence to cover every explicit numeric condition from the story. The campaign's
    # results directory holds only this frozen story's specs.
    expected = expected_integer_boundary_conditions(story)
    if expected:
        coverage: dict[str, set[int]] = {}
        for path in source_specs:
            source = path.read_text(encoding="utf-8")
            for plan in extract_test_plans_from_source(source):
                plan_failures, plan_coverage = verify_boundary_coverage(str(path), plan, story, require_complete=False)
                failures.extend(plan_failures)
                for condition, values in plan_coverage.items():
                    coverage.setdefault(condition, set()).update(values)
        missing = sorted(set(expected) - set(coverage))
        if missing:
            failures.append(
                f"{story.splitlines()[0]}: Boundary coverage is missing explicit story condition(s) across fresh source specs: {', '.join(missing)}"
            )
        for condition, required in expected.items():
            missing_values = sorted(required - coverage.get(condition, set()))
            if missing_values:
                failures.append(
                    f"{story.splitlines()[0]}: Boundary coverage for '{condition}' is missing value(s) across fresh source specs: {', '.join(str(value) for value in missing_values)}"
                )
    return failures


def read_json_object(path: Path, label: str) -> tuple[dict | None, list[str]]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        return None, [f"{path}: cannot read {label} JSON ({error})"]
    if not isinstance(value, dict):
        return None, [f"{path}: {label} must be a JSON object"]
    return value, []


def nonempty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def verify_story_analysis(path: Path) -> list[str]:
    analysis, failures = read_json_object(path, "story analysis")
    if analysis is None:
        return failures
    if analysis.get("version") != 2:
        failures.append(f"{path}: story analysis version must be 2")
    story = analysis.get("story")
    if not nonempty_string(story):
        failures.append(f"{path}: story must be a non-empty string")
        story = ""
    normalized_story = normalize_prose(story)

    setup = analysis.get("setup")
    if not isinstance(setup, dict):
        failures.append(f"{path}: setup must be an object")
    else:
        for field in ("base_url", "isolation"):
            if not nonempty_string(setup.get(field)):
                failures.append(f"{path}: setup.{field} must be a non-empty string")
        for field in ("allowed_files", "startup_commands"):
            values = setup.get(field)
            if not isinstance(values, list) or not values or not all(nonempty_string(item) for item in values):
                failures.append(f"{path}: setup.{field} must be a non-empty list of strings")

    criteria = analysis.get("acceptance_criteria")
    criterion_by_ac: dict[str, str] = {}
    if not isinstance(criteria, list) or not criteria:
        failures.append(f"{path}: acceptance_criteria must be a non-empty array")
    else:
        for index, criterion in enumerate(criteria):
            prefix = f"{path}: acceptance_criteria[{index}]"
            if not isinstance(criterion, dict):
                failures.append(f"{prefix} must be an object")
                continue
            ac = criterion.get("ac")
            text = criterion.get("text")
            if not nonempty_string(ac) or not re.fullmatch(r"AC\d+", ac, re.IGNORECASE):
                failures.append(f"{prefix}.ac must match AC<number>")
                continue
            if not nonempty_string(text):
                failures.append(f"{prefix}.text must be a non-empty string")
                continue
            key = ac.upper()
            if key in criterion_by_ac:
                failures.append(f"{prefix}.ac repeats {ac}")
                continue
            if normalized_story and normalize_prose(text) not in normalized_story:
                failures.append(f"{prefix}.text is not a verbatim story fragment: {text}")
            criterion_by_ac[key] = text.strip()

    claims = analysis.get("claims")
    if not isinstance(claims, list) or not claims:
        return failures + [f"{path}: claims must be a non-empty array"]
    seen: set[tuple[str, str]] = set()
    for index, claim in enumerate(claims):
        prefix = f"{path}: claims[{index}]"
        if not isinstance(claim, dict):
            failures.append(f"{prefix} must be an object")
            continue
        for field in ("ac", "clause", "actor", "visible_outcome", "surface", "observation"):
            if not nonempty_string(claim.get(field)):
                failures.append(f"{prefix}.{field} must be a non-empty string")
        ac = claim.get("ac", "")
        if nonempty_string(ac) and not re.fullmatch(r"AC\d+", ac, re.IGNORECASE):
            failures.append(f"{prefix}.ac must match AC<number>")
        elif criterion_by_ac and nonempty_string(ac) and ac.upper() not in criterion_by_ac:
            failures.append(f"{prefix}.ac is absent from acceptance_criteria: {ac}")
        clause = claim.get("clause", "")
        if nonempty_string(clause) and normalized_story and normalize_prose(clause) not in normalized_story:
            failures.append(f"{prefix}.clause is not a verbatim story fragment: {clause}")
        if nonempty_string(ac) and nonempty_string(claim.get("visible_outcome")):
            key = (ac.upper(), normalize_phrase(claim["visible_outcome"]))
            if key in seen:
                failures.append(f"{prefix} repeats an (AC, visible_outcome) pair")
            seen.add(key)
    if criterion_by_ac and isinstance(claims, list):
        claimed = {
            claim.get("ac", "").upper()
            for claim in claims
            if isinstance(claim, dict) and nonempty_string(claim.get("ac"))
        }
        for ac in sorted(set(criterion_by_ac) - claimed):
            failures.append(f"{path}: acceptance_criteria {ac} has no claim")
    return failures


def verify_hashed_path(prefix: str, value: object, digest: object, label: str) -> list[str]:
    failures: list[str] = []
    if not nonempty_string(value):
        return [f"{prefix}.path must be a non-empty string"]
    if not nonempty_string(digest) or not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
        failures.append(f"{prefix}.sha256 must be a 64-character hexadecimal SHA-256")
    resolved = Path(value).resolve()
    if not resolved.is_file():
        failures.append(f"{prefix}.path does not exist: {value}")
    elif nonempty_string(digest) and re.fullmatch(r"[0-9a-fA-F]{64}", digest):
        if sha256(resolved) != digest.lower():
            failures.append(f"{prefix}: {label} SHA-256 differs from the handoff")
    return failures


def verify_author_handoff(path: Path) -> list[str]:
    handoff, failures = read_json_object(path, "author handoff")
    if handoff is None:
        return failures
    if handoff.get("version") != 1:
        failures.append(f"{path}: author handoff version must be 1")

    analysis = handoff.get("story_analysis")
    if not isinstance(analysis, dict):
        failures.append(f"{path}: story_analysis must be an object")
    else:
        failures.extend(
            verify_hashed_path(
                f"{path}: story_analysis", analysis.get("path"), analysis.get("sha256"), "story-analysis"
            )
        )
        analysis_path = analysis.get("path")
        analysis_digest = analysis.get("sha256")
        if (
            nonempty_string(analysis_path)
            and nonempty_string(analysis_digest)
            and re.fullmatch(r"[0-9a-fA-F]{64}", analysis_digest)
            and Path(analysis_path).resolve().is_file()
            and sha256(Path(analysis_path).resolve()) == analysis_digest.lower()
        ):
            failures.extend(verify_story_analysis(Path(analysis_path).resolve()))

    specs = handoff.get("specs")
    if not isinstance(specs, list) or not specs:
        failures.append(f"{path}: specs must be a non-empty array")
    else:
        seen_paths: set[Path] = set()
        for index, spec in enumerate(specs):
            prefix = f"{path}: specs[{index}]"
            if not isinstance(spec, dict):
                failures.append(f"{prefix} must be an object")
                continue
            spec_value = spec.get("path")
            if nonempty_string(spec_value):
                resolved = Path(spec_value).resolve()
                if resolved in seen_paths:
                    failures.append(f"{prefix}.path repeats an earlier spec")
                seen_paths.add(resolved)
            failures.extend(verify_hashed_path(prefix, spec_value, spec.get("sha256"), "spec"))

    preflight = handoff.get("preflight")
    if not isinstance(preflight, dict):
        failures.append(f"{path}: preflight must be an object")
    else:
        if preflight.get("status") != "passed":
            failures.append(f"{path}: preflight.status must be 'passed'")
        for field in ("command", "passed_at"):
            if not nonempty_string(preflight.get(field)):
                failures.append(f"{path}: preflight.{field} must be a non-empty string")
        if nonempty_string(preflight.get("command")) and "--preflight" not in preflight["command"]:
            failures.append(f"{path}: preflight.command must invoke --preflight")
    for field in ("phase_timings", "workspace_status"):
        if not nonempty_string(handoff.get(field)):
            failures.append(f"{path}: {field} must be a non-empty string")
    context_tokens = handoff.get("author_context_tokens")
    if not isinstance(context_tokens, int) or isinstance(context_tokens, bool) or context_tokens < 0:
        failures.append(f"{path}: author_context_tokens must be a non-negative integer")
    execution_mode = handoff.get("execution_mode")
    if execution_mode not in {"same-context", "fresh-executor"}:
        failures.append(f"{path}: execution_mode must be 'same-context' or 'fresh-executor'")
    elif isinstance(context_tokens, int) and not isinstance(context_tokens, bool):
        if context_tokens >= 100_000 and execution_mode != "fresh-executor":
            failures.append(f"{path}: author context at or above 100000 requires execution_mode 'fresh-executor'")
    if nonempty_string(handoff.get("phase_timings")):
        timings_path = Path(handoff["phase_timings"]).resolve()
        if not timings_path.is_file():
            failures.append(f"{path}: phase_timings path does not exist: {handoff['phase_timings']}")
    return failures


def verify_discovery_handoff(path: Path) -> list[str]:
    handoff, failures = read_json_object(path, "discovery handoff")
    if handoff is None:
        return failures
    if handoff.get("version") != 1:
        failures.append(f"{path}: discovery handoff version must be 1")

    analysis = handoff.get("story_analysis")
    analysis_path: Path | None = None
    if not isinstance(analysis, dict):
        failures.append(f"{path}: story_analysis must be an object")
    else:
        failures.extend(
            verify_hashed_path(
                f"{path}: story_analysis", analysis.get("path"), analysis.get("sha256"), "story-analysis"
            )
        )
        analysis_value = analysis.get("path")
        analysis_digest = analysis.get("sha256")
        if (
            nonempty_string(analysis_value)
            and nonempty_string(analysis_digest)
            and re.fullmatch(r"[0-9a-fA-F]{64}", analysis_digest)
            and Path(analysis_value).resolve().is_file()
            and sha256(Path(analysis_value).resolve()) == analysis_digest.lower()
        ):
            analysis_path = Path(analysis_value).resolve()
            failures.extend(verify_story_analysis(analysis_path))

    foundation_manifest = handoff.get("foundation_manifest")
    if not nonempty_string(foundation_manifest):
        failures.append(f"{path}: foundation_manifest must be a non-empty string")
    else:
        failures.extend(verify_foundation(Path(foundation_manifest)))

    stack = handoff.get("stack")
    if not isinstance(stack, dict):
        failures.append(f"{path}: stack must be an object")
    else:
        for field in ("base_url", "lifecycle_id"):
            if not nonempty_string(stack.get(field)):
                failures.append(f"{path}: stack.{field} must be a non-empty string")
        if stack.get("project") is not None and not nonempty_string(stack.get("project")):
            failures.append(f"{path}: stack.project must be null or a non-empty string")

    facts = handoff.get("claim_facts")
    actual: set[tuple[str, str]] = set()
    if not isinstance(facts, list) or not facts:
        failures.append(f"{path}: claim_facts must be a non-empty array")
    else:
        for index, claim_facts in enumerate(facts):
            prefix = f"{path}: claim_facts[{index}]"
            if not isinstance(claim_facts, dict):
                failures.append(f"{prefix} must be an object")
                continue
            ac = claim_facts.get("ac")
            outcome = claim_facts.get("visible_outcome")
            if not nonempty_string(ac) or not re.fullmatch(r"AC\d+", ac, re.IGNORECASE):
                failures.append(f"{prefix}.ac must match AC<number>")
            if not nonempty_string(outcome):
                failures.append(f"{prefix}.visible_outcome must be a non-empty string")
            elif nonempty_string(ac):
                key = (ac.upper(), normalize_phrase(outcome))
                if key in actual:
                    failures.append(f"{prefix} repeats an (AC, visible_outcome) pair")
                actual.add(key)
            entries = claim_facts.get("facts")
            if not isinstance(entries, list) or not entries:
                failures.append(f"{prefix}.facts must be a non-empty array")
                continue
            for fact_index, fact in enumerate(entries):
                fact_prefix = f"{prefix}.facts[{fact_index}]"
                if not isinstance(fact, dict):
                    failures.append(f"{fact_prefix} must be an object")
                    continue
                for field in ("kind", "value", "live_evidence"):
                    if not nonempty_string(fact.get(field)):
                        failures.append(f"{fact_prefix}.{field} must be a non-empty string")

    if analysis_path is not None:
        analysis = json.loads(analysis_path.read_text(encoding="utf-8"))
        expected = {
            (claim["ac"].upper(), normalize_phrase(claim["visible_outcome"]))
            for claim in analysis["claims"]
            if isinstance(claim, dict)
            and nonempty_string(claim.get("ac"))
            and nonempty_string(claim.get("visible_outcome"))
        }
        for key in sorted(expected - actual):
            failures.append(f"{path}: claim_facts lacks {key[0]} visible outcome {key[1]!r}")
        for key in sorted(actual - expected):
            failures.append(f"{path}: claim_facts names unknown {key[0]} visible outcome {key[1]!r}")

    for field in ("fixtures", "constraints", "source_paths"):
        values = handoff.get(field, [])
        if not isinstance(values, list) or not all(nonempty_string(item) for item in values):
            failures.append(f"{path}: {field} must be an array of non-empty strings")
    for field in ("phase_timings",):
        if not nonempty_string(handoff.get(field)):
            failures.append(f"{path}: {field} must be a non-empty string")
        elif not Path(handoff[field]).resolve().is_file():
            failures.append(f"{path}: {field} path does not exist: {handoff[field]}")
    context_tokens = handoff.get("discovery_context_tokens")
    if not isinstance(context_tokens, int) or isinstance(context_tokens, bool) or context_tokens < 0:
        failures.append(f"{path}: discovery_context_tokens must be a non-negative integer")
    return failures


def verify_diagnostic_handoff(path: Path) -> list[str]:
    handoff, failures = read_json_object(path, "diagnostic handoff")
    if handoff is None:
        return failures
    if handoff.get("version") != 1:
        failures.append(f"{path}: diagnostic handoff version must be 1")
    if not nonempty_string(handoff.get("phase")):
        failures.append(f"{path}: phase must be a non-empty string")
    attempts = handoff.get("attempts")
    if not isinstance(attempts, int) or isinstance(attempts, bool) or attempts < 3:
        failures.append(f"{path}: attempts must be an integer of at least 3")
    for field in ("failure_evidence", "attempted_fix", "next_hypothesis"):
        if not nonempty_string(handoff.get(field)):
            failures.append(f"{path}: {field} must be a non-empty string")
    stack = handoff.get("stack")
    if stack is not None:
        if not isinstance(stack, dict):
            failures.append(f"{path}: stack must be null or an object")
        else:
            for field in ("project", "base_url"):
                if not nonempty_string(stack.get(field)):
                    failures.append(f"{path}: stack.{field} must be a non-empty string")
    return failures


def verify_run_manifest(path: Path) -> list[str]:
    manifest, failures = read_json_object(path, "run manifest")
    if manifest is None:
        return failures
    if manifest.get("version") != 1:
        failures.append(f"{path}: run manifest version must be 1")
    for field in ("campaign_suite", "results_dir", "report_dir"):
        if not nonempty_string(manifest.get(field)):
            failures.append(f"{path}: {field} must be a non-empty string")
    not_before = manifest.get("not_before")
    if not isinstance(not_before, (int, float)) or isinstance(not_before, bool) or not_before <= 0:
        failures.append(f"{path}: not_before must be a positive Unix timestamp")
    for field in ("isolation_id", "stack_project"):
        if manifest.get(field) is not None and not nonempty_string(manifest.get(field)):
            failures.append(f"{path}: {field} must be null or a non-empty string")
    story_analysis = manifest.get("story_analysis")
    if not isinstance(story_analysis, dict):
        failures.append(f"{path}: story_analysis must be an object")
    else:
        analysis_path_value = story_analysis.get("path")
        analysis_digest = story_analysis.get("sha256")
        if not nonempty_string(analysis_path_value):
            failures.append(f"{path}: story_analysis.path must be a non-empty string")
        if not nonempty_string(analysis_digest) or not re.fullmatch(r"[0-9a-fA-F]{64}", analysis_digest):
            failures.append(f"{path}: story_analysis.sha256 must be a 64-character hexadecimal SHA-256")
        if nonempty_string(analysis_path_value):
            analysis_path = Path(analysis_path_value).resolve()
            if not analysis_path.is_file():
                failures.append(f"{path}: story_analysis.path does not exist: {analysis_path_value}")
            elif nonempty_string(analysis_digest) and re.fullmatch(r"[0-9a-fA-F]{64}", analysis_digest):
                if sha256(analysis_path) != analysis_digest.lower():
                    failures.append(f"{path}: story-analysis SHA-256 differs from the frozen run manifest")
                else:
                    failures.extend(verify_story_analysis(analysis_path))
    if nonempty_string(manifest.get("results_dir")) and nonempty_string(manifest.get("report_dir")):
        if Path(manifest["results_dir"]).resolve() == Path(manifest["report_dir"]).resolve():
            failures.append(f"{path}: results_dir and report_dir must differ")
    specs = manifest.get("specs")
    if not isinstance(specs, list) or not specs:
        failures.append(f"{path}: specs must be a non-empty array")
    else:
        seen_paths: set[Path] = set()
        for index, spec in enumerate(specs):
            prefix = f"{path}: specs[{index}]"
            if not isinstance(spec, dict):
                failures.append(f"{prefix} must be an object")
                continue
            spec_value = spec.get("path")
            digest = spec.get("sha256")
            if not nonempty_string(spec_value):
                failures.append(f"{prefix}.path must be a non-empty string")
                continue
            spec_path = Path(spec_value).resolve()
            if spec_path in seen_paths:
                failures.append(f"{prefix}.path repeats an earlier spec")
            seen_paths.add(spec_path)
            if not nonempty_string(digest) or not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
                failures.append(f"{prefix}.sha256 must be a 64-character hexadecimal SHA-256")
            if not spec_path.is_file():
                failures.append(f"{prefix}.path does not exist: {spec_value}")
            elif nonempty_string(digest) and re.fullmatch(r"[0-9a-fA-F]{64}", digest):
                if sha256(spec_path) != digest.lower():
                    failures.append(f"{prefix}: spec SHA-256 differs from the frozen run manifest")
    return failures


def parse_utc_timestamp(value: object) -> float | None:
    """Parse the portable ISO-8601 timestamps used by phase-timings.json."""
    if not isinstance(value, str) or not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        return None
    return parsed.astimezone(timezone.utc).timestamp()


def verify_phase_timings(path: Path, results: list[tuple[Path, dict]]) -> list[str]:
    """Bind the recorded final run window to Allure's runner-produced timestamps."""
    timings, failures = read_json_object(path, "phase timings")
    if timings is None:
        return failures
    if timings.get("version") != 1:
        failures.append(f"{path}: phase timings version must be 1")
    phases = timings.get("phases")
    if not isinstance(phases, list) or not phases:
        return failures + [f"{path}: phases must be a non-empty array"]

    run_windows: list[tuple[float, float]] = []
    preflight_windows: list[tuple[float, float]] = []
    for index, phase in enumerate(phases):
        prefix = f"{path}: phases[{index}]"
        if not isinstance(phase, dict):
            failures.append(f"{prefix} must be an object")
            continue
        if not nonempty_string(phase.get("name")):
            failures.append(f"{prefix}.name must be a non-empty string")
        started = parse_utc_timestamp(phase.get("started_at"))
        ended = parse_utc_timestamp(phase.get("ended_at"))
        if started is None:
            failures.append(f"{prefix}.started_at must be an ISO-8601 UTC timestamp")
        if ended is None:
            failures.append(f"{prefix}.ended_at must be an ISO-8601 UTC timestamp")
        if started is not None and ended is not None:
            if ended < started:
                failures.append(f"{prefix}.ended_at must not precede started_at")
            elif phase.get("name") == "run":
                run_windows.append((started, ended))
            elif phase.get("name") == "preflight":
                preflight_windows.append((started, ended))

    if not run_windows:
        failures.append(f"{path}: phases must contain a completed 'run' entry")
        return failures

    for run_started, _ in run_windows:
        if not any(preflight_ended <= run_started for _, preflight_ended in preflight_windows):
            failures.append(
                f"{path}: no completed 'preflight' entry ends before final evidence run starts "
                f"({datetime.fromtimestamp(run_started, timezone.utc).isoformat()})"
            )

    result_windows: list[tuple[float, float]] = []
    for result_path, result in results:
        try:
            started = float(result["start"]) / 1000
            ended = float(result["stop"]) / 1000
        except (KeyError, TypeError, ValueError):
            failures.append(f"{result_path}: missing usable Allure start/stop timestamps")
            continue
        result_windows.append((started, ended))
    if result_windows:
        actual_start = min(started for started, _ in result_windows)
        actual_end = max(ended for _, ended in result_windows)
        if not any(started <= actual_start and ended >= actual_end for started, ended in run_windows):
            failures.append(
                f"{path}: no recorded run window contains the actual Allure execution "
                f"({datetime.fromtimestamp(actual_start, timezone.utc).isoformat()} to "
                f"{datetime.fromtimestamp(actual_end, timezone.utc).isoformat()})"
            )
    return failures


def handoff_main() -> int:
    parser = argparse.ArgumentParser(description="Validate phased E2E handoff JSON")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--verify-story-analysis", type=Path)
    source.add_argument("--verify-discovery-handoff", type=Path)
    source.add_argument("--verify-author-handoff", type=Path)
    source.add_argument("--verify-run-manifest", type=Path)
    source.add_argument("--verify-diagnostic-handoff", type=Path)
    args = parser.parse_args()
    if args.verify_story_analysis:
        failures = verify_story_analysis(args.verify_story_analysis)
        label = "Story analysis"
    elif args.verify_discovery_handoff:
        failures = verify_discovery_handoff(args.verify_discovery_handoff)
        label = "Discovery handoff"
    elif args.verify_author_handoff:
        failures = verify_author_handoff(args.verify_author_handoff)
        label = "Author handoff"
    elif args.verify_run_manifest:
        failures = verify_run_manifest(args.verify_run_manifest)
        label = "Run manifest"
    else:
        failures = verify_diagnostic_handoff(args.verify_diagnostic_handoff)
        label = "Diagnostic handoff"
    if failures:
        print(f"{label} verification failed:")
        print("\n".join(f"- {failure}" for failure in failures))
        return 1
    print(f"{label} verified.")
    return 0


def verify_common_journeys(journeys_path: Path, stories_dir: Path) -> list[str]:
    """Validate the owner-maintained inventory of reusable UI prerequisites.

    This deliberately checks provenance and shape, not UI outcomes: the linked smoke
    spec is the executable proof, while story scenarios remain responsible for their
    own acceptance criteria.
    """
    if not journeys_path.is_file():
        return [f"{journeys_path}: common-journeys baseline is missing"]
    if not stories_dir.is_dir():
        return [f"{stories_dir}: story-spec directory is missing"]
    try:
        document = json.loads(journeys_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return [f"{journeys_path}: invalid common-journeys JSON ({error})"]
    if not isinstance(document, dict) or document.get("version") != 1:
        return [f"{journeys_path}: expected an object with version 1"]
    journeys = document.get("journeys")
    if not isinstance(journeys, list) or not journeys:
        return [f"{journeys_path}: journeys must be a non-empty array"]

    failures: list[str] = []
    seen_ids: set[str] = set()
    allowed_root = stories_dir.resolve()
    for index, journey in enumerate(journeys, start=1):
        prefix = f"{journeys_path}: journeys[{index}]"
        if not isinstance(journey, dict):
            failures.append(f"{prefix}: must be an object")
            continue
        journey_id = journey.get("id")
        if not isinstance(journey_id, str) or not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", journey_id):
            failures.append(f"{prefix}: id must be lowercase kebab-case")
        elif journey_id in seen_ids:
            failures.append(f"{prefix}: duplicate journey id '{journey_id}'")
        else:
            seen_ids.add(journey_id)
        for field in ("purpose", "smoke_spec"):
            if not isinstance(journey.get(field), str) or not journey[field].strip():
                failures.append(f"{prefix}: {field} must be a non-empty string")
        smoke_spec = journey.get("smoke_spec")
        if isinstance(smoke_spec, str) and smoke_spec.strip():
            candidate = (journeys_path.parent / smoke_spec).resolve()
            try:
                candidate.relative_to(journeys_path.parent.resolve())
            except ValueError:
                failures.append(f"{prefix}: smoke_spec escapes harness: {smoke_spec}")
            else:
                if not candidate.is_file():
                    failures.append(f"{prefix}: smoke_spec is missing: {smoke_spec}")
        for field, minimum in (("personas", 1), ("steps", 2), ("reused_by", 2)):
            value = journey.get(field)
            if not isinstance(value, list) or len(value) < minimum:
                failures.append(f"{prefix}: {field} must contain at least {minimum} item(s)")
            elif field != "reused_by" and any(not isinstance(item, str) or not item.strip() for item in value):
                failures.append(f"{prefix}: {field} entries must be non-empty strings")

        story_paths: set[Path] = set()
        for reference in journey.get("reused_by", []) if isinstance(journey.get("reused_by"), list) else []:
            if not isinstance(reference, dict):
                failures.append(f"{prefix}: reused_by entries must be objects")
                continue
            story = reference.get("story")
            criteria = reference.get("criteria")
            if not isinstance(story, str) or not story.strip() or not isinstance(criteria, list) or not criteria:
                failures.append(f"{prefix}: each reused_by entry needs story and non-empty criteria")
                continue
            candidate = (stories_dir / story).resolve()
            try:
                candidate.relative_to(allowed_root)
            except ValueError:
                failures.append(f"{prefix}: story reference escapes {stories_dir}: {story}")
                continue
            if not candidate.is_file():
                failures.append(f"{prefix}: referenced story is missing: {story}")
                continue
            story_paths.add(candidate)
            source = candidate.read_text(encoding="utf-8")
            for criterion in criteria:
                if not isinstance(criterion, str) or not criterion.strip() or criterion not in source:
                    failures.append(f"{prefix}: criterion is not a verbatim fragment of {story}: {criterion!r}")
        if len(story_paths) < 2:
            failures.append(f"{prefix}: reused_by must cite two distinct story files")
    return failures


def verify_preflight_spec(
    spec_path: Path, foundation_manifest: Path, story: str, acceptance_criteria: dict[str, str]
) -> list[str]:
    # Reject known plan drift before an agent starts an isolated stack.
    failures: list[str] = []
    if not spec_path.is_file():
        return failures + [f"{spec_path}: story spec is missing"]
    try:
        source = spec_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return failures + [f"{spec_path}: story spec must be UTF-8 text"]
    if force_failure := forced_ui_action_failure(str(spec_path), source):
        failures.append(force_failure)
    failures.extend(preflight_story_metadata_failures(str(spec_path), source, story, acceptance_criteria))
    protected_paths, protected_failures = load_protected_paths(foundation_manifest)
    if protected_paths is None:
        failures.extend(protected_failures)
    else:
        failures.extend(scan_spec_graph(spec_path.resolve(), protected_paths))

    return failures


def verify_preflight(
    spec_paths: list[Path], foundation_manifest: Path, story_analysis_path: Path
) -> list[str]:
    failures = verify_foundation(foundation_manifest)
    failures.extend(verify_story_analysis(story_analysis_path))
    analysis, read_failures = read_json_object(story_analysis_path, "story analysis")
    failures.extend(read_failures)
    story = analysis.get("story") if analysis is not None else None
    if not isinstance(story, str) or not story.strip():
        return failures + [f"{story_analysis_path}: story must be a non-empty string"]
    for parent in {path.parent.resolve() for path in spec_paths}:
        failures.extend(scan_scratch_specs(parent))
    criteria = acceptance_criteria_by_ac(analysis) if isinstance(analysis, dict) else {}
    for spec_path in spec_paths:
        failures.extend(verify_preflight_spec(spec_path, foundation_manifest, story, criteria))
    return failures


def preflight_main() -> int:
    parser = argparse.ArgumentParser(description="Validate a story's specs before starting an E2E stack")
    parser.add_argument("--preflight", required=True, type=Path, nargs="+", help="all specs for this story")
    parser.add_argument("--story-analysis", required=True, type=Path, help="validated analyze/discovery handoff")
    parser.add_argument("--foundation-manifest", required=True, type=Path)
    args = parser.parse_args()
    failures = verify_preflight(args.preflight, args.foundation_manifest, args.story_analysis)
    if failures:
        print("E2E story preflight failed:")
        print("\n".join(f"- {failure}" for failure in failures))
        return 1
    print("E2E story preflight verified.")
    return 0


def common_journeys_main() -> int:
    parser = argparse.ArgumentParser(description="Validate reusable E2E journey provenance")
    parser.add_argument("--verify-common-journeys", required=True, type=Path)
    parser.add_argument("--stories-dir", required=True, type=Path)
    args = parser.parse_args()
    failures = verify_common_journeys(args.verify_common_journeys, args.stories_dir)
    if failures:
        print("Common E2E journeys verification failed:")
        print("\n".join(f"- {failure}" for failure in failures))
        return 1
    print("Common E2E journeys verified.")
    return 0

def verify_campaign_suite(
    results: list[tuple[Path, dict]], campaign_suite: str
) -> list[str]:
    # Why: a mixed parentSuite splits one campaign into misleading report groups.
    failures: list[str] = []
    for path, result in results:
        labels = {
            label.get("name"): label.get("value")
            for label in result.get("labels", [])
        }
        if labels.get("parentSuite") != campaign_suite:
            failures.append(
                f"{path.name}: parentSuite {labels.get('parentSuite')!r}, "
                f"expected campaign suite {campaign_suite!r}"
            )
    return failures


def verify_report(
    report_dir: Path,
    expected_story_count: int,
    story_result_count: int,
    campaign_result_count: int,
    statuses: Counter[str],
    newest_result_mtime: float,
) -> list[str]:
    # The campaign's results dir is one frozen story; story_result_count and
    # campaign_result_count are the same set, kept separate only for clarity.
    failures: list[str] = []
    # Why: a green run can still be missing an AC scenario of the frozen story.
    if story_result_count != expected_story_count:
        failures.append(f"frozen story: {story_result_count} result(s), expected {expected_story_count}")
    # Allure 3 is pinned by the foundation and required for the Awesome hierarchy.
    # Reject a legacy v2 report rather than treating a green old-style summary as evidence.
    statistic_path = report_dir / "widgets" / "statistic.json"
    if not statistic_path.is_file():
        return failures + [f"{statistic_path}: Allure 3 report statistics are missing"]
    stats_path = statistic_path
    statistics = json.loads(statistic_path.read_text(encoding="utf-8"))
    total = statistics.get("total")
    # Why: the report total must cover every result the campaign produced.
    if total != campaign_result_count:
        failures.append(f"{stats_path}: total {total}, expected {campaign_result_count} campaign results")
    # Why: an older static report can omit the latest run while looking healthy.
    if stats_path.stat().st_mtime < newest_result_mtime:
        failures.append(f"{stats_path}: older than fresh result JSON; regenerate the report")
    for status in set(statuses) | {"passed", "failed", "broken", "skipped", "unknown"}:
        if statistics.get(status, 0) != statuses.get(status, 0):
            failures.append(f"{stats_path}: {status} {statistics.get(status, 0)}, expected {statuses.get(status, 0)} campaign results")
    return failures


def resolve_analysis_from_manifest(path: Path) -> dict:
    """Read frozen story identity and acceptance criteria, never report prose."""
    manifest, failures = read_json_object(path, "run manifest")
    if manifest is None:
        raise ValueError("; ".join(failures))
    story_analysis = manifest.get("story_analysis")
    if not isinstance(story_analysis, dict) or not nonempty_string(story_analysis.get("path")):
        raise ValueError("run manifest has no story_analysis.path")
    analysis, failures = read_json_object(Path(story_analysis["path"]), "story analysis")
    if analysis is None:
        raise ValueError("; ".join(failures))
    analysis_failures = verify_story_analysis(Path(story_analysis["path"]))
    if analysis_failures:
        raise ValueError("; ".join(analysis_failures))
    return analysis


def acceptance_criteria_by_ac(analysis: dict) -> dict[str, str]:
    return {
        criterion["ac"].upper(): criterion["text"].strip()
        for criterion in analysis.get("acceptance_criteria", [])
        if isinstance(criterion, dict)
        and nonempty_string(criterion.get("ac"))
        and nonempty_string(criterion.get("text"))
    }


def verify_run_manifest_binding(path: Path, args: argparse.Namespace, story: str) -> list[str]:
    failures = verify_run_manifest(path)
    manifest, read_failures = read_json_object(path, "run manifest")
    failures.extend(read_failures)
    if manifest is None:
        return failures
    comparisons = (
        ("campaign_suite", manifest.get("campaign_suite"), args.campaign_suite),
        ("not_before", manifest.get("not_before"), args.not_before),
        ("isolation_id", manifest.get("isolation_id"), args.isolation_id),
    )
    for field, recorded, supplied in comparisons:
        if recorded != supplied:
            failures.append(f"{path}: {field} {recorded!r} does not match CLI value {supplied!r}")
    for field, supplied in (("results_dir", args.results_dir), ("report_dir", args.report_dir)):
        recorded = manifest.get(field)
        if nonempty_string(recorded) and Path(recorded).resolve() != supplied.resolve():
            failures.append(f"{path}: {field} {recorded!r} does not match CLI path {str(supplied)!r}")
    story_analysis = manifest.get("story_analysis")
    if isinstance(story_analysis, dict) and nonempty_string(story_analysis.get("path")):
        analysis, _ = read_json_object(Path(story_analysis["path"]), "story analysis")
        if analysis is not None and analysis.get("story", "").strip() != story.strip():
            failures.append(f"{path}: frozen story analysis does not match the requested story")
    for spec in manifest.get("specs", []) if isinstance(manifest.get("specs"), list) else []:
        spec_path = spec.get("path") if isinstance(spec, dict) else None
        if not nonempty_string(spec_path):
            continue
        try:
            Path(spec_path).resolve().relative_to(args.spec_dir.resolve())
        except ValueError:
            failures.append(f"{path}: spec path {spec_path!r} is outside --spec-dir {args.spec_dir}")
    return failures


def verify_manifest_result_specs(
    path: Path, story_results: list[tuple[Path, dict]], spec_dir: Path
) -> list[str]:
    manifest, failures = read_json_object(path, "run manifest")
    if manifest is None:
        return failures
    expected = {
        Path(spec["path"]).resolve()
        for spec in manifest.get("specs", [])
        if isinstance(spec, dict) and nonempty_string(spec.get("path"))
    }
    actual: set[Path] = set()
    for result_path, result in story_results:
        source = source_for_result(result, spec_dir)
        if source is None:
            failures.append(f"{result_path.name}: cannot bind result source to the frozen run manifest")
        else:
            actual.add(source.resolve())
    for spec_path in sorted(expected - actual):
        failures.append(f"{path}: frozen spec produced no requested-story result: {spec_path}")
    for spec_path in sorted(actual - expected):
        failures.append(f"{path}: requested-story result came from an unfrozen spec: {spec_path}")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("results_dir", type=Path)
    parser.add_argument("--report-dir", required=True, type=Path)
    parser.add_argument("--expected-count", required=True, type=int)
    parser.add_argument("--spec-dir", required=True, type=Path)
    parser.add_argument("--foundation-manifest", required=True, type=Path, help="committed manifest of protected shared E2E files")
    parser.add_argument("--campaign-suite", required=True, help="exact Allure parentSuite shared by every result of this campaign (one frozen story)")
    parser.add_argument("--not-before", required=True, type=float, help="Unix timestamp recorded immediately before the final Playwright run")
    parser.add_argument("--isolation-id", help="required evidence identity for a concurrent isolated worker; every e2e_configuration must contain isolation_id=<value>")
    parser.add_argument("--run-manifest", required=True, type=Path, help="validated frozen-run handoff")
    parser.add_argument("--phase-timings", required=True, type=Path, help="structured phase timings containing the final run window")
    args = parser.parse_args()
    try:
        analysis = resolve_analysis_from_manifest(args.run_manifest)
    except (OSError, json.JSONDecodeError, KeyError, ValueError) as error:
        print(f"Allure E2E evidence verification failed:\n- cannot read frozen story ({error})")
        return 1
    story = analysis.get("story", "").strip()
    if not story:
        print("Allure E2E evidence verification failed:\n- frozen story text is empty")
        return 1
    frozen_criteria = acceptance_criteria_by_ac(analysis)
    result_files = sorted(args.results_dir.glob("*-result.json"))
    failures = verify_run_manifest_binding(args.run_manifest, args, story)
    if not result_files:
        failures.append(f"{args.results_dir}: no result JSON files")
    parsed_results = [(path, json.loads(path.read_text(encoding="utf-8"))) for path in result_files]
    failures.extend(verify_phase_timings(args.phase_timings, parsed_results))
    failures.extend(verify_campaign_suite(parsed_results, args.campaign_suite))
    # A campaign owns one results directory for one frozen story; each of its raw
    # results is one AC scenario of that story. Source-package binding below still
    # rejects any result whose spec is not a frozen spec of this story.
    story_results = parsed_results
    story_result_files = [path for path, _ in story_results]
    if not story_result_files:
        failures.append(f"{args.results_dir}: no raw result JSON files for the frozen story")
    failures.extend(verify_manifest_result_specs(args.run_manifest, story_results, args.spec_dir))
    campaign_statuses: Counter[str] = Counter(result.get("status", "unknown") for _, result in parsed_results)
    for result_file in story_result_files:
        # Why: a previous green run must not satisfy a newly changed test.
        if result_file.stat().st_mtime < args.not_before:
            failures.append(f"{result_file}: older than final-run start; stale evidence")
        result_failures, _ = verify_result(
            result_file,
            args.results_dir,
            story,
            args.spec_dir,
            frozen_criteria,
            args.isolation_id,
        )
        failures.extend(result_failures)
    if args.foundation_manifest:
        failures.extend(verify_foundation(args.foundation_manifest))
    newest_result_mtime = max((path.stat().st_mtime for path in result_files), default=0)
    if result_files:
        failures.extend(verify_specs(args.spec_dir, newest_result_mtime, [result for _, result in parsed_results], args.foundation_manifest, story))
    failures.extend(verify_report(args.report_dir, args.expected_count, len(story_result_files), len(result_files), campaign_statuses, newest_result_mtime))
    if failures:
        print("Allure E2E evidence verification failed:")
        print("\n".join(f"- {failure}" for failure in failures))
        return 1
    print(f"Allure E2E evidence verified: {len(story_result_files)} story result(s) in {len(result_files)} campaign result(s).")
    return 0


if __name__ == "__main__":
    if any(
        flag in sys.argv
        for flag in (
            "--verify-story-analysis",
            "--verify-discovery-handoff",
            "--verify-author-handoff",
            "--verify-run-manifest",
            "--verify-diagnostic-handoff",
        )
    ):
        sys.exit(handoff_main())
    if "--verify-common-journeys" in sys.argv:
        sys.exit(common_journeys_main())
    if "--preflight" in sys.argv:
        sys.exit(preflight_main())
    if len(sys.argv) == 3 and sys.argv[1] == "--verify-foundation":
        foundation_failures = verify_foundation(Path(sys.argv[2]))
        if foundation_failures:
            print("E2E foundation verification failed:")
            print("\n".join(f"- {failure}" for failure in foundation_failures))
            sys.exit(1)
        print("E2E foundation verified.")
        sys.exit(0)
    sys.exit(main())
