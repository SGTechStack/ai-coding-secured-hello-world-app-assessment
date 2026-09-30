#!/usr/bin/env python3
"""Serve all Story-E2E Allure reports from one stable localhost URL."""

from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

from verify_allure_e2e import story_intent

PORTAL_REPORT_DIR = "allure-report"
# A bare 3.x.y token anywhere in `allure --version` output (tolerates an npm
# notice or an "Allure Report 3.0.0" banner around it).
ALLURE_3_VERSION_PATTERN = re.compile(r"(?<![\d.])3\.\d+\.\d+(?:[-+][\w.]+)?(?![\d.])")

def state_path(root: Path) -> Path:
    digest = hashlib.sha256(str(root.resolve()).encode()).hexdigest()[:16]
    return Path(tempfile.gettempdir()) / f"story-e2e-report-viewer-{digest}.json"


def read_state(root: Path) -> dict[str, Any] | None:
    path = state_path(root)
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return None


def request(url: str, method: str = "GET") -> bytes | None:
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method=method), timeout=1.5) as response:
            return response.read()
    except (urllib.error.HTTPError, urllib.error.URLError, OSError, TimeoutError):
        return None


def response(url: str) -> tuple[int, str, bytes] | None:
    """Read a served portal asset, including its MIME type, without a browser agent."""
    try:
        with urllib.request.urlopen(url, timeout=3) as result:
            return result.status, result.headers.get_content_type(), result.read()
    except (urllib.error.HTTPError, urllib.error.URLError, OSError, TimeoutError):
        return None


def local_script_sources(index_html: str) -> list[str]:
    """Ignore analytics/CDN scripts; the portal must prove its own generated asset."""
    sources = re.findall(r'<script[^>]+src=["\']([^"\']+\.js)["\']', index_html)
    return [source for source in sources if not urllib.parse.urlsplit(source).scheme and not source.startswith("//")]


def healthy(root: Path, port: int) -> bool:
    body = request(f"http://127.0.0.1:{port}/_story-e2e/health")
    if not body:
        return False
    try:
        return json.loads(body).get("root") == str(root.resolve())
    except json.JSONDecodeError:
        return False


def available_port(preferred: int) -> int | None:
    for port in range(preferred, preferred + 20):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
            try:
                probe.bind(("127.0.0.1", port))
            except OSError:
                continue
            return port
    return None


def result_directories(root: Path) -> list[Path]:
    campaigns = root / "campaigns"
    if not campaigns.is_dir():
        return []
    results: list[Path] = []
    for campaign in campaigns.iterdir():
        if not campaign.is_dir():
            continue
        candidates = [
            item for item in campaign.glob("allure-results*")
            if item.is_dir() and any(item.glob("*-result.json"))
        ]
        if candidates:
            results.append(max(candidates, key=lambda item: item.stat().st_mtime))
    return sorted(results)


def allure_3_is_available(harness_root: Path) -> bool:
    """Fail early with a useful fix instead of invoking an incompatible v2 CLI."""
    command = [
        "npx.cmd" if os.name == "nt" else "npx",
        "--prefix", str(harness_root), "--no-install", "allure", "--version",
    ]
    try:
        completed = subprocess.run(
            command,
            cwd=harness_root.parent,
            text=True,
            capture_output=True,
            check=False,
            timeout=60,
        )
    except (subprocess.TimeoutExpired, OSError) as error:
        print(f"Story-E2E could not probe the Allure CLI ({error}).", file=sys.stderr)
        return False
    output = f"{completed.stdout}\n{completed.stderr}"
    if completed.returncode == 0 and ALLURE_3_VERSION_PATTERN.search(output):
        return True
    detail = (completed.stderr or completed.stdout).strip() or "not installed"
    version = completed.stdout.strip()
    print(
        "Story-E2E requires the foundation-pinned Allure 3 `allure` package "
        f"for `allure awesome`; found {version or detail!r}. "
        "Install a reviewed exact 3.x release during foundation setup (never `allure-commandline`).",
        file=sys.stderr,
    )
    return False


# The only two exception types that mean the OS or the text encoder itself
# rejected the write (disk-full/permission/AV-lock, and a failure encoding
# text to bytes — currently unreachable at every present call site, though
# for different reasons at each: ensure_ascii=True for the JSON writes in
# scrub_error_context/add_display_hierarchy, a hardcoded ASCII literal for
# the CSS write, and a strict-UTF-8 decode-on-read that already rejects any
# unpaired surrogate before it could reach the others' re-encode). Every
# other type in this module's
# except tuples for a write call is "an unexpected shape" instead: usually
# because it happened earlier, reading or normalizing already-loaded data —
# except RecursionError, which can also come from json.dumps at the write
# call's own argument expression, not an earlier step; it's still "an
# unexpected shape" (an abnormally deep tree), just not for the "happens
# earlier" reason the rest of this paragraph gives. _atomic_write_text's own
# retry loop, every
# caller's except tuple, and _log_skip's classifier all read from this one
# constant — not a fixed list of "the callers" enumerated here, since new
# ones get added — so a future exception type added to one can't silently
# drift out of sync with the others the way OSError and UnicodeEncodeError
# once did between the callers and _log_skip (a bug an earlier /code-review
# round caught) — and the same drift, caught again by a later round, one
# level down: _atomic_write_text's own retry/cleanup loop used to catch only
# OSError, so a UnicodeEncodeError from its own tmp.write_text() would have
# skipped both the retry and the temp-file cleanup, silently leaking a
# `.tmp-<pid>-<hex>` file forever.
_WRITE_FAILURE_TYPES = (OSError, UnicodeEncodeError)


def _atomic_write_text(path: Path, text: str, attempts: int = 3, retry_delay: float = 0.05) -> None:
    """Replace `path`'s content with `text` without ever leaving it truncated
    or corrupted if the write fails partway through.

    `Path.write_text()` opens its target in `"w"` mode, which truncates the
    file to zero bytes *before* writing a single byte of the new content — an
    OSError raised during the write itself (disk-full, a transient AV lock)
    would otherwise leave the file empty/corrupted rather than untouched, no
    matter how faithfully a caller's `except OSError` logs and moves on.
    Writing to a same-directory temp file first, then swapping it in with
    `os.replace` (atomic on both POSIX and Windows), means a failure at any
    point leaves the original file exactly as it was.

    A transient lock (e.g. antivirus scanning a just-written file) is usually
    gone within milliseconds, so a failed attempt is retried a few times with
    a short delay before giving up and letting the final OSError propagate —
    a `Path.write_text` call never gets that chance to recover on its own.

    The temp filename includes a random suffix, not just the pid: every
    current caller in this module runs sequentially, but two calls racing on
    the same `path` from the same process (e.g. a future multi-threaded
    caller) must not share one temp file and corrupt each other's write.

    `os.replace` swaps in a brand-new inode, so a target's pre-existing
    permissions aren't preserved across a rewrite — deliberately left
    unaddressed: no current caller in this module sets custom permissions on
    any of these files, and on Windows `os.replace` itself refuses to
    replace a read-only destination at all (confirmed directly), unlike
    POSIX's `rename()`, which only cares about the containing directory's
    permissions — so "preserve the target's mode" cannot even be exercised
    the one way it would matter on this platform, and chasing it further
    would add real complexity for a still-hypothetical, environment-
    dependent gap with no reproducible failure behind it.
    """
    tmp = path.with_name(f".{path.name}.tmp-{os.getpid()}-{secrets.token_hex(4)}")
    for attempt in range(attempts):
        try:
            tmp.write_text(text, encoding="utf-8")
            os.replace(tmp, path)
            return
        except _WRITE_FAILURE_TYPES:
            # Best-effort cleanup only: the same transient lock that just
            # made the write/replace fail could just as easily block deleting
            # the temp file it produced. That must not raise in turn and
            # short-circuit the retry loop to a single attempt — a leftover
            # `.tmp-<pid>` file is harmless and gets overwritten (or ignored)
            # by the next attempt or the next run under this same pid.
            try:
                tmp.unlink(missing_ok=True)
            except OSError:
                pass
            if attempt == attempts - 1:
                raise
            time.sleep(retry_delay)


def _log_skip(prefix: str, path: Path, error: BaseException) -> None:
    """Report one `*-result.json` being left as-is for the rest of this
    multi-campaign refresh, distinguishing a write failure (disk-full,
    permission-denied, AV lock — the file's shape was fine) from an
    unexpected shape (a hand-edited or partially-written file). One shared
    place for this classification, so `scrub_error_context` and
    `add_display_hierarchy` can't drift into disagreeing wording for the
    same two failure classes."""
    reason = "a write failure" if isinstance(error, _WRITE_FAILURE_TYPES) else "an unexpected shape"
    print(f"{prefix}: skipping {path} ({reason}: {error})", file=sys.stderr)


def _dict_list(value: Any) -> list[dict[str, Any]]:
    """Coerce `value` to a list containing only its dict elements, or `[]` if
    `value` isn't a list at all (missing, `null`, or some other type). The one
    place this "filter to dicts, default to empty" rule is written, so
    `load_result` and `_normalize_step_shape` can't drift into disagreeing on
    what counts as a normalized `labels`/`steps`/`attachments` list."""
    return [item for item in value if isinstance(item, dict)] if isinstance(value, list) else []


def _normalize_step_shape(node: dict[str, Any]) -> None:
    """Force `steps`/`attachments` to a list of dicts wherever missing,
    `null`, a non-list value, or containing a non-dict element, on this node
    and every nested step, so every reader can trust `.get(key, [])` to
    actually be an iterable list of objects with `.get()`. One reader tolerant
    of a stray non-dict element (via its own isinstance filter) and another
    that assumes every element is a dict would otherwise disagree on which
    files are "fine" — normalizing the shape itself, once, here, means every
    caller (namespacing, wrapper-step stripping, test-plan lookup) sees the
    exact same tree."""
    for key in ("steps", "attachments"):
        node[key] = _dict_list(node.get(key))
    for step in node["steps"]:
        _normalize_step_shape(step)


def load_result(path: Path) -> dict[str, Any] | None:
    """Read and parse one `*-result.json`, or None if it can't be read/parsed
    or normalized into a usable shape.

    Allure's own schema always includes `labels`/`steps`/`attachments` as
    lists of objects, but a killed run, a partial write, or a hand-edited
    file can leave one of them explicitly `null`, a non-list value, or a list
    with a stray non-dict element, instead of the expected shape —
    `dict.get(key, [])` only substitutes its default when the key is
    *missing*, not when it's present with some other value, so every caller
    that assumed the untouched `.get(key, [])` idiom was safe has, in turn,
    crashed on this exact input (labels, then steps, then attachments, then a
    non-dict list element — the same bug rediscovered repeatedly at multiple
    call sites). Normalizing the whole shape once, recursively, right here,
    before any caller sees the dict, closes the whole class in one place.
    """
    try:
        # ValueError covers json.JSONDecodeError and a truncated multi-byte
        # UTF-8 sequence's UnicodeDecodeError alike — both are "this file is
        # not readable as JSON", not distinct failure modes worth splitting.
        result = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"load_result: skipping unreadable {path} ({error})", file=sys.stderr)
        return None
    if not isinstance(result, dict):
        print(f"load_result: skipping {path} (top level is not a JSON object)", file=sys.stderr)
        return None
    try:
        result["labels"] = _dict_list(result.get("labels"))
        _normalize_step_shape(result)
    except (AttributeError, TypeError, RecursionError) as error:
        print(f"load_result: dropping unnormalizable {path} ({error})", file=sys.stderr)
        return None
    return result


def labels_by_name(result: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {
        str(label.get("name")): label
        for label in result.get("labels") or []
        if isinstance(label, dict) and label.get("name")
    }


def test_plan_text(result: dict[str, Any], results_dir: Path) -> str:
    def walk(steps: list[dict[str, Any]]) -> list[dict[str, Any]]:
        found: list[dict[str, Any]] = []
        for step in steps:
            found.extend(item for item in step.get("attachments", []) if isinstance(item, dict))
            found.extend(walk(step.get("steps", [])))
        return found

    for attachment in walk(result.get("steps", [])):
        if attachment.get("name") == "Test plan" and isinstance(attachment.get("source"), str):
            try:
                return (results_dir / attachment["source"]).read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                return ""
    return ""


def user_story_intent(story: str) -> str:
    # One shared definition of "the first As… paragraph"; see verify_allure_e2e.
    return story_intent(story) or ""


def criterion_from_story(ac_label: str) -> str:
    """Rewrite an 'AC<n>[b]: <criterion>' label to the base 'AC<n>: <criterion>',
    one line, keeping the criterion text verbatim (it is already the frozen
    criterion under the current contract — no story lookup, so a numbered
    sub-list inside it is not mistaken for a scenario)."""
    match = re.fullmatch(r"(AC\d+)[a-z]?\s*:\s*(.+)", ac_label, re.IGNORECASE | re.DOTALL)
    if match is None:
        return ac_label
    return f"{match.group(1).upper()}: {' '.join(match.group(2).split())}"


# A leaf attachment's own name is matched by substring via _is_error_context_name
# (below), since that name has varied across Playwright versions ("error-context",
# "error-context.md", "_error-context") and an unlisted future variant should
# still match. The wrapper *step*'s name, in contrast, is checked against this
# exact set, deliberately not by substring: dropping a whole step (not just a
# leaf attachment) is destructive, so an unrecognised future step name is left
# alone (its orphaned-empty-row cosmetic issue persists) rather than risking a
# false-positive deletion of a real step's content.
_ERROR_CONTEXT_WRAPPER_NAMES = {"error-context", "error-context.md", "_error-context"}


def _is_error_context_name(name: Any) -> bool:
    return "error-context" in str(name or "").lower()


def _is_pure_error_context_wrapper(step: dict[str, Any]) -> bool:
    """True only for allure-playwright's own attachment-wrapper step: an exact
    known name, no status, no sub-steps, no parameters, and one or more
    attachments that are themselves all error-context. A real user-authored
    step that happens to share the name but carries any of its own status,
    children, parameters, or other attachments never matches, so it is never
    deleted — only this narrow, verified-empty shape is."""
    if str(step.get("name", "")).lower() not in _ERROR_CONTEXT_WRAPPER_NAMES:
        return False
    if step.get("status") or step.get("steps") or step.get("parameters"):
        return False
    attachments = step.get("attachments") or []
    return len(attachments) > 0 and all(_is_error_context_name(a.get("name")) for a in attachments)


def strip_error_context(node: dict[str, Any]) -> None:
    """Drop Playwright's auto-generated `error-context` attachment.

    It embeds a generic '# Instructions / Following Playwright test failed …'
    prompt into the evidence; the authored `blocker evidence` attachment is the
    only failure narrative this contract recognises.

    allure-playwright wraps each attachment (Test plan, screenshot, video,
    error-context, trace) in its own pseudo-step named after it. Emptying
    that step's attachment list alone leaves a contentless step behind (an
    orphaned "error-context" row with nothing inside it), so
    `_is_pure_error_context_wrapper` also drops the wrapper step itself when
    its whole shape (name, status, sub-steps, parameters, attachments) proves
    it holds nothing else — never by name alone, so a real user-authored step
    that happens to share the name but carries any real content of its own is
    never touched.
    """
    node["attachments"] = [
        attachment for attachment in node.get("attachments", [])
        if not _is_error_context_name(attachment.get("name"))
    ]
    kept_steps = []
    for step in node.get("steps", []):
        if _is_pure_error_context_wrapper(step):
            continue
        strip_error_context(step)
        kept_steps.append(step)
    node["steps"] = kept_steps


def _namespace_id(value: Any, campaign_id: str) -> str | None:
    """Return `value` prefixed with a length-delimited campaign marker, or
    None if it cannot be namespaced (missing).

    A plain `f"{campaign_id}:"` join is delimiter-ambiguous: campaign "A:B"
    with raw id "C" and campaign "A" with raw id "B:C" both produce "A:B:C".
    Length-prefixing (a marker of `"<len(campaign_id)>:<campaign_id>:"`) is
    unambiguous regardless of what characters campaign_id or the raw id
    contain, since the reader always knows exactly where campaign_id ends.
    """
    if value is None:
        return None
    text = value if isinstance(value, str) else str(value)
    return f"{len(campaign_id)}:{campaign_id}:" + text


def scrub_error_context(results_dir: Path, campaign_id: str) -> None:
    """Remove the `error-context` attachment and namespace retry/history ids.

    Allure 3 groups results across ALL supplied result dirs by testCaseId /
    historyId, treating same-id results as retries of ONE test and showing only
    a single representative (older ones become retry/history shadows). Because
    allure-playwright derives those ids from the test title path, two DIFFERENT
    scenarios in DIFFERENT campaigns can share an id and collide when the portal
    aggregates every campaign into one report — which mislabels a passed
    scenario as skipped/retried (and vice versa). The combined report is a
    per-campaign catalogue, NOT a retry history, so namespacing the ids by the
    (unique) campaign id keeps every campaign's scenario distinct. Trusted
    per-campaign evidence is untouched; only this disposable report copy changes.
    """
    def apply_namespace(container: dict[str, Any], key: str) -> None:
        namespaced = _namespace_id(container.get(key), campaign_id)
        if namespaced is not None:
            container[key] = namespaced

    for path in results_dir.glob("*-result.json"):
        result = load_result(path)
        if result is None:
            continue
        try:
            strip_error_context(result)
            apply_namespace(result, "historyId")
            apply_namespace(result, "testCaseId")
            for label in result.get("labels") or []:
                if isinstance(label, dict) and label.get("name") == "_fallbackTestCaseId":
                    apply_namespace(label, "value")
            # ensure_ascii=True trades a larger disposable temp copy (non-ASCII
            # text becomes 6-byte \uXXXX escapes) for never hitting a raw lone
            # surrogate on encode; the trusted per-campaign source is untouched.
            # _atomic_write_text (not a plain write_text) keeps this file's
            # prior content intact if the write itself fails.
            _atomic_write_text(path, json.dumps(result, ensure_ascii=True))
        except (AttributeError, TypeError, IndexError, RecursionError, UnicodeDecodeError, *_WRITE_FAILURE_TYPES) as error:
            # An unexpected shape (e.g. a hand-edited or partially-written
            # result) must not abort the combined report for every other
            # campaign; leave this one file exactly as it was read, but say
            # so — it keeps its raw error-context prompt and un-namespaced
            # ids, which is worth a human noticing rather than a silent skip.
            # OSError is included here too: a disk-full, permission-denied, or
            # AV-locked write on this disposable temp copy must be as
            # non-fatal to the whole multi-campaign refresh as a bad read is
            # (_atomic_write_text already retried transient locks and left
            # the file untouched before this exception was ever raised).
            # UnicodeEncodeError is unreachable today (ensure_ascii=True below
            # already escapes any lone surrogate before encoding), but stays
            # listed so it's classified as "a write failure" rather than
            # crashing uncaught if that invariant is ever weakened later.
            _log_skip("scrub_error_context", path, error)
            continue


def simplify_assertion_steps(steps: list[dict[str, Any]]) -> None:
    """Move runtime proof below the readable assertion title in a portal copy."""
    pattern = re.compile(
        r"^(?P<title>Assertion:.*?)(?:\s*\|\s*)expected:\s*(?P<expected>.*?)\s*\|\s*observed:\s*(?P<observed>.*?)\s*$",
        re.IGNORECASE,
    )
    for step in steps:
        name = str(step.get("name", ""))
        if match := pattern.match(name):
            step["name"] = match.group("title").rstrip()
            evidence = {
                "name": f"Evidence: expected: {match.group('expected')} | observed: {match.group('observed')}",
                "status": step.get("status", "passed"),
                "stage": "finished",
                "start": step.get("start", 0),
                "stop": step.get("stop", 0),
                "steps": [],
                "attachments": [],
                "parameters": [],
            }
            step.setdefault("steps", []).insert(0, evidence)
        simplify_assertion_steps(step.get("steps", []))


def add_display_hierarchy(results_dir: Path) -> None:
    """Make a temporary report copy readable without changing trusted evidence."""
    story_pattern = re.compile(
        r"^(?P<feature>.+?)\s*[—-]\s*User Story\s*(?P<number>\d+)\s*:\s*(?P<title>.+)$",
        re.IGNORECASE,
    )
    mapping_pattern = re.compile(r"(?m)^-\s*(AC\d+)\s*\|\s*Story clause:\s*(.+?)\s*\|\s*Actor:")
    for path in results_dir.glob("*-result.json"):
        result = load_result(path)
        if result is None:
            continue
        try:
            labels = labels_by_name(result)
            feature = str(labels.get("feature", {}).get("value", "")).strip()
            suite = str(labels.get("suite", {}).get("value", "")).strip()
            sub_suite = str(labels.get("subSuite", {}).get("value", "")).strip()
            updated = [label for label in result.get("labels") or [] if label.get("name") != "subSuite"]
            if not feature or not re.fullmatch(r"US\d+\s*:\s*.+", suite):
                story_lines = str(result.get("description") or suite).splitlines()
                match = story_pattern.match(story_lines[0].strip()) if story_lines else None
                if not match:
                    continue
                feature = match.group("feature").strip()
                title = re.split(r"\s+As\s+(?:an?|the)\b", match.group("title"), maxsplit=1, flags=re.IGNORECASE)[0].strip()
                suite = f"US{match.group('number')}: {title}"
                updated = [label for label in updated if label.get("name") not in {"feature", "suite"}]
                updated.extend(
                    [
                        {"name": "feature", "value": feature},
                        {"name": "suite", "value": suite},
                    ]
                )
            mapping = mapping_pattern.search(test_plan_text(result, results_dir))
            if not mapping:
                continue
            # Split off a failed/skipped reason trailer exactly as the verifier does:
            # the last blank-line-separated paragraph, only if it opens Failure:/Blocked:.
            paragraphs = re.split(r"\r?\n\s*\r?\n", str(result.get("description") or "").strip())
            reason = ""
            if len(paragraphs) > 1 and re.match(r"(?is)^(Failure|Blocked)\s*:\s*\S", paragraphs[-1].strip()):
                reason = " ".join(paragraphs.pop().split())
            criterion_source = "\n\n".join(paragraphs)
            # Normalise the (possibly multi-line verbatim) subSuite to one line before matching.
            sub_suite_line = " ".join(sub_suite.split())
            ac_label = sub_suite_line if re.fullmatch(r"AC\d+[a-z]?\s*:\s*.+", sub_suite_line, re.IGNORECASE) else f"{mapping.group(1)}: {mapping.group(2)}"
            intent = user_story_intent(criterion_source)
            if not feature.startswith("Feature: "):
                feature = f"Feature: {feature}"
                updated = [label for label in updated if label.get("name") != "feature"]
                updated.append({"name": "feature", "value": feature})
            updated.append({"name": "subSuite", "value": ac_label})
            result["labels"] = updated
            result["description"] = "\n\n".join(
                part for part in (intent, criterion_from_story(ac_label), reason) if part
            )
            simplify_assertion_steps(result.get("steps", []))
            # See scrub_error_context's identical call: ensure_ascii=True plus
            # _atomic_write_text together mean neither a lone surrogate nor a
            # write failure can corrupt or truncate this file.
            _atomic_write_text(path, json.dumps(result, ensure_ascii=True))
        except (AttributeError, TypeError, IndexError, RecursionError, UnicodeDecodeError, *_WRITE_FAILURE_TYPES) as error:
            # Matches scrub_error_context's guard: an unexpected shape (e.g. a
            # null element inside an otherwise-present list), or a write
            # failure (disk full, permission denied, AV lock) on this
            # disposable temp copy, must not abort the combined report for
            # every other campaign.
            _log_skip("add_display_hierarchy", path, error)
            continue


def add_readable_tree_style(report_dir: Path) -> None:
    """Let long feature/US/AC headings wrap without modifying Allure assets.

    Purely cosmetic: the underlying `allure awesome` report is already
    complete and correct without this stylesheet, so a write failure here
    (disk-full, AV lock, surviving even _atomic_write_text's retries) must
    not abort the whole refresh() the way it would have before this file's
    writes went through _atomic_write_text at all — it's logged and skipped,
    the same as one bad `*-result.json` further up the pipeline, not treated
    as fatal just because it happens to be a different function.
    """
    css_path = report_dir / "story-e2e-tree.css"
    index = report_dir / "index.html"
    # Tracks whichever of the two files below is currently being written (or
    # read), so the except clause reports the file that actually failed
    # instead of always naming one of them regardless of which it was.
    stage = css_path
    try:
        # This one is a brand-new file, but index.html below is the freshly
        # `allure awesome`-generated report's own entry point — a truncated
        # write to it (disk-full, AV lock) would break the whole portal, so
        # both go through _atomic_write_text for the same reason the
        # result-file writers in scrub_error_context/add_display_hierarchy do.
        _atomic_write_text(css_path, """/* Story-E2E portal: keep hierarchy labels readable. */
#app [class*="styles_tree-section__"] {
  align-items: flex-start !important;
  height: auto !important;
}
#app [class*="styles_tree-section-title__"] {
  overflow: visible !important;
  overflow-wrap: anywhere;
  text-overflow: clip !important;
  white-space: normal !important;
  line-height: 1.35 !important;
  padding-block: 2px;
}
""")
        stage = index
        contents = index.read_text(encoding="utf-8")
        _atomic_write_text(index, contents.replace("</head>", f'    <link rel="stylesheet" href="{css_path.name}">\n</head>', 1))
    except (*_WRITE_FAILURE_TYPES, UnicodeDecodeError) as error:
        # Routed through the same _log_skip classifier scrub_error_context/
        # add_display_hierarchy use, rather than an ad-hoc message here.
        _log_skip("add_readable_tree_style", stage, error)


def embed_trace_viewer(report_dir: Path, harness_root: Path) -> None:
    """Bundle Playwright's own trace viewer into the report and point the
    'open trace' link at it. A click then loads the trace from this same
    origin instead of racing the hosted https://trace.playwright.dev PWA,
    which Allure messages within ~300 ms of a cold load and often misses."""
    bundle = next(
        (
            candidate
            for candidate in (
                harness_root / "node_modules/playwright-core/lib/vite/traceViewer",
                *sorted(harness_root.glob("node_modules/**/playwright-core/lib/vite/traceViewer")),
            )
            if (candidate / "index.html").is_file()
        ),
        None,
    )
    if bundle is None:
        print("Local trace viewer bundle not found; the report keeps the hosted trace link.")
        return
    target = report_dir / "_trace-viewer"
    try:
        shutil.rmtree(target, ignore_errors=True)
        shutil.copytree(bundle, target)
    except _WRITE_FAILURE_TYPES as error:
        # Same "cosmetic enhancement, not fatal" treatment as the bundle-not-
        # found branch above: a disk-full/AV-lock/stale-locked-directory
        # failure staging the bundle must not crash refresh() for every
        # campaign just because this one report enhancement can't proceed.
        # Routed through _log_skip (like every other write-failure site in
        # this module) rather than an ad-hoc message, so a future widening of
        # _WRITE_FAILURE_TYPES can't leave this one site's classification
        # behind the way it once did before this exact fix.
        _log_skip("embed_trace_viewer", target, error)
        # copytree can fail partway through; a half-populated target must not
        # be promoted into the published report by refresh()'s later
        # shutil.move, nor left behind as orphaned debris on a future refresh.
        shutil.rmtree(target, ignore_errors=True)
        return
    pattern = re.compile(
        r'([A-Za-z_$][\w$]*)="https://trace\.playwright\.dev",'
        r'([A-Za-z_$][\w$]*)=`\$\{\1\}/`'
    )
    replacement = r'\1=location.origin,\2=new URL("_trace-viewer/",location.href).href'
    patched = 0
    matched = 0  # count of pattern occurrences found (not scripts), whether or not the write itself then succeeded
    errored = 0  # count of scripts whose read or write raised; checked only after `matched`, so it means
    # "errored without a known match" only where it's actually read below — see the elif chain
    for script in report_dir.glob("app-*.js"):
        try:
            text = script.read_text(encoding="utf-8")
            new_text, count = pattern.subn(replacement, text)
            matched += count
            if count:
                # This is the report's own JS app bundle — a truncated write
                # (disk-full, AV lock) would break the whole portal, same
                # reason add_readable_tree_style's index.html write goes
                # through this. A failure on one bundle (there can be more
                # than one) must not stop the loop or abort refresh() for
                # every campaign — same per-file skip-and-continue as
                # scrub_error_context/add_display_hierarchy, not a special
                # case just because this function writes JS instead of JSON.
                _atomic_write_text(script, new_text)
                patched += count
        except (*_WRITE_FAILURE_TYPES, UnicodeDecodeError) as error:
            errored += 1
            _log_skip("embed_trace_viewer", script, error)
            continue
    if patched:
        print(f"Report trace links now use the bundled local trace viewer ({patched} rewrite(s)).")
    elif matched:
        # Distinct from the "pattern not matched" case below: the rewrite was
        # found and attempted, but every attempt's write failed (each already
        # logged above via _log_skip) — a genuine write-failure outcome, not
        # evidence of an Allure version bump changing the bundle's own shape.
        print("Trace-link pattern matched but every write failed; hosted trace link unchanged.", file=sys.stderr)
    elif errored:
        # Distinct from both branches above: every candidate script errored
        # on its own read (also already logged above via _log_skip) before
        # the pattern could even be checked against it, so whether it would
        # have matched is genuinely unknown — "not matched" would be an
        # unverified claim, not an observed fact.
        print(f"Could not check {errored} app-*.js file(s) for the trace-link pattern; hosted trace link unchanged.", file=sys.stderr)
    else:
        print("Trace-link pattern not matched in the report; hosted trace link unchanged.")


def refresh(artifacts_root: Path, harness_root: Path) -> int:
    results = result_directories(artifacts_root)
    output = artifacts_root / PORTAL_REPORT_DIR
    if not results:
        shutil.rmtree(output, ignore_errors=True)
        print("No completed Allure results; cleared the combined report.")
        return 0
    if not allure_3_is_available(harness_root):
        return 1
    temporary = Path(tempfile.mkdtemp(prefix=".story-e2e-report-", dir=artifacts_root))
    staging = temporary / PORTAL_REPORT_DIR
    display_results: list[Path] = []
    for index, source in enumerate(results):
        destination = temporary / f"results-{index}"
        shutil.copytree(source, destination)
        # source is <campaign>/allure-results*, so its parent is the campaign
        # directory itself — the unique id each result gets namespaced with.
        campaign_id = source.parent.name
        scrub_error_context(destination, campaign_id)
        add_display_hierarchy(destination)
        display_results.append(destination)
    command = [
        "npx.cmd" if os.name == "nt" else "npx",
        "--prefix", str(harness_root), "--no-install", "allure", "awesome",
        *(str(item) for item in display_results), "--output", str(staging),
        "--group-by", "feature,suite,subSuite",
    ]
    completed = subprocess.run(command, cwd=harness_root.parent, text=True)
    if completed.returncode:
        shutil.rmtree(temporary, ignore_errors=True)
        return completed.returncode
    add_readable_tree_style(staging)
    embed_trace_viewer(staging, harness_root)
    shutil.rmtree(output, ignore_errors=True)
    shutil.move(str(staging), str(output))
    shutil.rmtree(temporary, ignore_errors=True)
    print(f"Combined {len(results)} campaign report(s): {output}")
    return 0


class ReportHandler(SimpleHTTPRequestHandler):
    root: Path
    token: str

    def log_message(self, _format: str, *_args: object) -> None:
        return

    def do_GET(self) -> None:  # noqa: N802
        path = urllib.parse.urlsplit(self.path).path
        if path == "/_story-e2e/health":
            self._json({"root": str(self.root.resolve())})
            return
        if path == "/":
            self._portal_index()
            return
        self._serve_campaign_file(path)

    def do_HEAD(self) -> None:  # noqa: N802
        path = urllib.parse.urlsplit(self.path).path
        if path == "/":
            self._portal_index(include_body=False)
            return
        self._serve_campaign_file(path, include_body=False)

    def do_OPTIONS(self) -> None:  # noqa: N802
        self.send_response(HTTPStatus.NO_CONTENT)
        self._send_cors_headers()
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _send_cors_headers(self) -> None:
        # The Allure report links traces to the hosted https://trace.playwright.dev
        # PWA, which fetches the .zip from this local server. Chrome's Private Network
        # Access blocks a public-origin fetch of a localhost resource unless the server
        # opts in with these headers and answers the OPTIONS preflight.
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Private-Network", "true")
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")

    def do_POST(self) -> None:  # noqa: N802
        if self.path == f"/_story-e2e/stop/{self.token}":
            self.send_response(HTTPStatus.NO_CONTENT)
            self.end_headers()
            self.server.shutdown()  # type: ignore[attr-defined]
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def _json(self, payload: dict[str, str]) -> None:
        encoded = json.dumps(payload).encode()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _portal_index(self, include_body: bool = True) -> None:
        if (self.root / PORTAL_REPORT_DIR / "index.html").is_file():
            self.send_response(HTTPStatus.FOUND)
            self.send_header("Location", f"/{PORTAL_REPORT_DIR}/index.html")
            self.end_headers()
            return
        self._no_reports(include_body)

    def _no_reports(self, include_body: bool = True) -> None:
        encoded = b"<!doctype html><title>Story E2E reports</title><h1>No completed reports yet.</h1>"
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        if include_body:
            self.wfile.write(encoded)

    def _serve_campaign_file(self, path: str, include_body: bool = True) -> None:
        requested = (self.root / path.lstrip("/")).resolve()
        allowed = ((self.root / "campaigns").resolve(), (self.root / PORTAL_REPORT_DIR).resolve())
        if not any(directory in requested.parents for directory in allowed):
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        if requested.is_dir() and (requested / "index.html").is_file():
            requested = requested / "index.html"
        if not requested.is_file():
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        mime, _encoding = mimetypes.guess_type(str(requested))
        content_type = mime or "application/octet-stream"
        if requested.suffix == ".js":
            content_type = "text/javascript"
        if requested.suffix == ".zip":
            content_type = "application/zip"
        if content_type.startswith("text/") or content_type in {"application/json", "application/javascript"}:
            content_type += "; charset=utf-8"
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(requested.stat().st_size))
        self._send_cors_headers()
        self.end_headers()
        if include_body:
            with requested.open("rb") as file:
                self.copyfile(file, self.wfile)


def serve(root: Path, harness_root: Path, port: int) -> None:
    token = secrets.token_urlsafe(24)
    state = state_path(root)
    # Unlike add_readable_tree_style/embed_trace_viewer's cosmetic writes,
    # this one is load-bearing: without it, healthy()/read_state() can never
    # find this server, so a persistent write failure here must still abort
    # startup — but with the same classified "a write failure" diagnostic the
    # rest of this module already gives, not a bare traceback in the
    # detached subprocess's log (this call is made before serve_forever(),
    # so start()'s health-poll loop is what a caller actually observes fail).
    try:
        _atomic_write_text(state, json.dumps({"port": port, "token": token}))
    except _WRITE_FAILURE_TYPES as error:
        print(f"serve: a write failure prevented writing {state} ({error}); the report portal cannot start.", file=sys.stderr)
        raise
    handler = type("ProjectReportHandler", (ReportHandler,), {"root": root, "token": token})
    server = ThreadingHTTPServer(("127.0.0.1", port), handler)
    try:
        server.serve_forever()
    finally:
        if read_state(root) == {"port": port, "token": token}:
            state.unlink(missing_ok=True)
        server.server_close()


def start(root: Path, harness_root: Path, port: int) -> int:
    refresh_code = refresh(root, harness_root)
    if refresh_code:
        return refresh_code
    existing = read_state(root)
    if existing and healthy(root, int(existing["port"])):
        print(f"Report portal: http://127.0.0.1:{existing['port']}/")
        return 0
    state_path(root).unlink(missing_ok=True)
    selected_port = available_port(port)
    if selected_port is None:
        print(f"No free report portal port in {port}-{port + 19}.", file=sys.stderr)
        return 1
    port = selected_port
    log = Path(tempfile.gettempdir()) / f"story-e2e-report-viewer-{port}.log"
    creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
    with log.open("ab") as output:
        subprocess.Popen(
            [
                sys.executable,
                str(Path(__file__).resolve()),
                "serve",
                "--artifacts-root",
                str(root),
                "--harness-root",
                str(harness_root),
                "--port",
                str(port),
            ],
            stdin=subprocess.DEVNULL,
            stdout=output,
            stderr=subprocess.STDOUT,
            creationflags=creationflags,
            start_new_session=os.name != "nt",
        )
    for _ in range(20):
        time.sleep(0.15)
        if healthy(root, port):
            print(f"Report portal: http://127.0.0.1:{port}/")
            return 0
    print(f"Report portal did not start. Check {log}", file=sys.stderr)
    return 1


def stop(root: Path) -> int:
    state = read_state(root)
    if not state:
        print("Report portal is not running.")
        return 0
    url = f"http://127.0.0.1:{state['port']}/_story-e2e/stop/{state['token']}"
    if request(url, method="POST") is None:
        print("Report portal state was stale; removed it.")
        state_path(root).unlink(missing_ok=True)
        return 0
    for _ in range(20):
        time.sleep(0.15)
        if not healthy(root, int(state["port"])):
            state_path(root).unlink(missing_ok=True)
            break
    print("Report portal stopped.")
    return 0


def verify(root: Path) -> int:
    """Cheaply prove the generated portal and a real trace attachment are served."""
    state = read_state(root)
    if not state or not healthy(root, int(state["port"])):
        print("Report portal is not running; start it before verification.", file=sys.stderr)
        return 1
    base = f"http://127.0.0.1:{state['port']}"
    index = response(f"{base}/{PORTAL_REPORT_DIR}/index.html")
    if not index or index[0] != 200 or index[1] != "text/html":
        print("Report portal does not serve its Allure index HTML.", file=sys.stderr)
        return 1
    sources = local_script_sources(index[2].decode("utf-8", errors="replace"))
    if not sources:
        print("Allure index has no JavaScript asset to verify.", file=sys.stderr)
        return 1
    asset = response(urllib.parse.urljoin(f"{base}/{PORTAL_REPORT_DIR}/index.html", sources[0]))
    if not asset or asset[0] != 200 or asset[1] not in {"text/javascript", "application/javascript"} or not asset[2]:
        print("Report portal does not serve its JavaScript asset correctly.", file=sys.stderr)
        return 1
    attachments = sorted((root / PORTAL_REPORT_DIR / "data" / "attachments").glob("*.zip"))
    if not attachments:
        print("Generated report has no served trace ZIP to verify.", file=sys.stderr)
        return 1
    trace = attachments[0]
    trace_url = f"{base}/" + urllib.parse.quote(str(trace.relative_to(root)).replace("\\", "/"))
    served_trace = response(trace_url)
    if not served_trace or served_trace[0] != 200 or len(served_trace[2]) != trace.stat().st_size:
        print("Report portal does not serve its trace attachment correctly.", file=sys.stderr)
        return 1
    print("Report portal evidence verified: HTML, JavaScript, and served trace attachment.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("start", "stop", "serve", "refresh", "verify"))
    parser.add_argument("--artifacts-root", default="artifacts/e2e")
    parser.add_argument("--harness-root", default="e2e")
    parser.add_argument("--port", type=int, default=5050)
    args = parser.parse_args()
    root = Path(args.artifacts_root).resolve()
    harness_root = Path(args.harness_root).resolve()
    if not root.is_dir():
        parser.error(f"Artifacts root does not exist: {root}")
    if not harness_root.is_dir():
        parser.error(f"Harness root does not exist: {harness_root}")
    if args.command == "serve":
        serve(root, harness_root, args.port)
        return 0
    if args.command == "start":
        return start(root, harness_root, args.port)
    if args.command == "refresh":
        return refresh(root, harness_root)
    if args.command == "verify":
        return verify(root)
    return stop(root)


if __name__ == "__main__":
    raise SystemExit(main())
