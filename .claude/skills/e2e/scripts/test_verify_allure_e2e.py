"""Focused regression tests for the Story-E2E evidence verifier.

Run this file with pytest from the directory it lives in (this script is
copied verbatim into each consuming repo's own skill install path, e.g.
.claude/skills/e2e/scripts/ or .agents/skills/playwright-story-e2e/scripts/ —
there is no one fixed path to name here):
    python -m pytest test_verify_allure_e2e.py
"""

from __future__ import annotations

import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from verify_allure_e2e import (
    assertion_evidence_values,
    description_failures,
    forced_ui_action_failure,
    normalize_prose,
    preflight_story_metadata_failures,
    scan_spec_graph,
    sha256,
    story_intent,
    verify_discovery_handoff,
    verify_report,
    verify_story_analysis,
    verify_test_plan_content,
    verify_trace,
)
from serve_e2e_reports import (
    ALLURE_3_VERSION_PATTERN,
    _atomic_write_text,
    _log_skip,
    _namespace_id,
    add_display_hierarchy,
    add_readable_tree_style,
    criterion_from_story,
    embed_trace_viewer,
    load_result,
    local_script_sources,
    scrub_error_context,
    strip_error_context,
)


def _stage_trace_viewer_bundle(root):
    """Create a minimal on-disk trace-viewer bundle plus an empty report_dir
    under `root`, and return `(report_dir, harness_root)` — the fixture every
    embed_trace_viewer test needs before it can even reach that function's
    real logic, written once instead of once per test."""
    harness_root = root / "harness"
    trace_viewer = harness_root / "node_modules/playwright-core/lib/vite/traceViewer"
    trace_viewer.mkdir(parents=True)
    (trace_viewer / "index.html").write_text("<html></html>", encoding="utf-8")
    report_dir = root / "report"
    report_dir.mkdir()
    return report_dir, harness_root


def _stderr_of(action):
    """Call `action()` with stderr captured; return (its return value, what
    it wrote to stderr). Every test that needs to assert on a logged
    diagnostic used this exact `with contextlib.redirect_stderr(io.StringIO())
    as captured: ...; captured.getvalue()` shape independently — written once,
    here, instead."""
    with contextlib.redirect_stderr(io.StringIO()) as captured:
        result = action()
    return result, captured.getvalue()


class TestPlanContractTests(unittest.TestCase):
    def test_minimal_plan_needs_no_source_template_or_checklist(self):
        plan = """Data:
- Controlled member account.
Isolation:
- Isolated E2E stack.
Steps:
1. Sign in as the member.
2. Open the dashboard.
Visible outcomes:
- dashboard shows the member packages
Acceptance mapping:
- AC1 | Story clause: member sees packages | Actor: Member | Visible outcome: dashboard shows the member packages
"""
        failures, outcomes, mappings = verify_test_plan_content(
            "example", plan, "Any story wording is accepted by semantic review."
        )
        self.assertEqual(failures, [])
        self.assertEqual(outcomes, ["dashboard shows the member packages"])
        self.assertEqual(mappings, [("AC1", "Member")])

    def test_unmapped_visible_outcome_is_rejected(self):
        plan = """Data:
- Controlled member account.
Isolation:
- Isolated E2E stack.
Steps:
1. Sign in as the member.
2. Open the dashboard.
Visible outcomes:
- dashboard shows packages
- dashboard shows invoices
Acceptance mapping:
- AC1 | Story clause: member sees packages | Actor: Member | Visible outcome: dashboard shows packages
"""
        failures, _, _ = verify_test_plan_content("example", plan, "Story")
        self.assertTrue(any("lacks an Acceptance mapping" in failure for failure in failures), failures)

    def test_preflight_accepts_semicolon_in_literal_story_and_validates_plan(self):
        story = "Given a member opens the dashboard; Then they see their packages."
        source = """const STORY = `Given a member opens the dashboard; Then they see their packages.`;
const AC_DESCRIPTION = 'AC1: Then they see their packages.';
await allure.description(AC_DESCRIPTION);
await allure.attachment('Test plan', [
  'Data:', '- Member account.', 'Isolation:', '- Isolated E2E stack.',
  'Steps:', '- Sign in, then open the dashboard.',
  'Visible outcomes:', '- dashboard shows packages', 'Acceptance mapping:',
  '- AC1 | Story clause: they see packages | Actor: Member | Visible outcome: dashboard shows packages',
].join('\\n'), 'text/plain');
"""
        self.assertEqual(preflight_story_metadata_failures("example", source, story), [])

    def test_preflight_accepts_literal_array_story(self):
        story = "Given a member opens the dashboard.\nThen they see their packages."
        source = """const STORY = [
  'Given a member opens the dashboard.',
  'Then they see their packages.',
].join('\\n');
const AC_DESCRIPTION = 'AC1: Then they see their packages.';
await allure.description(AC_DESCRIPTION);
await allure.attachment('Test plan', [
  'Data:', '- Member account.', 'Isolation:', '- Isolated E2E stack.',
  'Steps:', '- Sign in, then open the dashboard.',
  'Visible outcomes:', '- dashboard shows packages', 'Acceptance mapping:',
  '- AC1 | Story clause: they see packages | Actor: Member | Visible outcome: dashboard shows packages',
].join('\\n'), 'text/plain');
"""
        self.assertEqual(preflight_story_metadata_failures("example", source, story), [])

    def test_preflight_rejects_story_drift_and_bad_plan_mapping(self):
        source = """const STORY = `Different story`;
const AC_DESCRIPTION = 'AC1: packages are visible';
await allure.description(AC_DESCRIPTION);
await allure.attachment('Test plan', [
  'Data:', '- Member account.', 'Isolation:', '- Isolated E2E stack.',
  'Steps:', '- Sign in, then open the dashboard.',
  'Visible outcomes:', '- dashboard shows packages', 'Acceptance mapping:',
  '- AC1 | Story clause: packages | Actor: Member | Visible outcome: dashboard shows invoices',
].join('\\n'), 'text/plain');
"""
        failures = preflight_story_metadata_failures("example", source, "Expected story")
        self.assertTrue(any("const STORY does not match" in failure for failure in failures), failures)
        self.assertTrue(any("not declared under Visible outcomes" in failure for failure in failures), failures)

    def test_preflight_rejects_static_assertion_label_for_the_wrong_ac(self):
        source = """const STORY = `Expected story`;
const AC_DESCRIPTION = 'AC4: packages are visible';
await allure.description(AC_DESCRIPTION);
await allure.attachment('Test plan', [
  'Data:', '- Member account.', 'Isolation:', '- Isolated E2E stack.',
  'Steps:', '- Sign in, then open the dashboard.',
  'Visible outcomes:', '- dashboard shows packages', 'Acceptance mapping:',
  '- AC4 | Story clause: packages | Actor: Member | Visible outcome: dashboard shows packages',
].join('\\n'), 'text/plain');
await allure.step('Assertion: [AC4b] actor: Member | dashboard shows packages', async () => {});
"""
        failures = preflight_story_metadata_failures("example", source, "Expected story")
        self.assertTrue(any("matching Assertion: [AC4]" in failure for failure in failures), failures)

    def test_preflight_rejects_full_story_as_allure_description(self):
        source = """const STORY = `Expected story`;
await allure.description(STORY);
await allure.attachment('Test plan', [
  'Data:', '- Member account.', 'Isolation:', '- Isolated E2E stack.',
  'Steps:', '- Sign in, then open the dashboard.',
  'Visible outcomes:', '- dashboard shows packages', 'Acceptance mapping:',
  '- AC1 | Story clause: packages | Actor: Member | Visible outcome: dashboard shows packages',
].join('\\n'), 'text/plain');
"""
        failures = preflight_story_metadata_failures("example", source, "Expected story")
        self.assertTrue(any("only this AC" in failure for failure in failures), failures)

    def test_preflight_accepts_equivalent_intent_and_ac_description_when_story_has_intent(self):
        story = """As a member, I want to edit my profile so that my details stay current.

AC1: Given an editable profile, When I save, Then I see confirmation."""
        criteria = {"AC1": "Given an editable profile, When I save, Then I see confirmation."}
        source = """const STORY = `As a member, I want to edit my profile so that my details stay current.

AC1: Given an editable profile, When I save, Then I see confirmation.`;
const STORY_INTENT = 'As a member, I want to edit my profile so that my details stay current.';
const AC_DESCRIPTION = 'AC1: Given an editable profile, When I save, Then I see confirmation.';
await allure.description(`${STORY_INTENT}\\n\\n${AC_DESCRIPTION}`);
await allure.attachment('Test plan', [
  'Data:', '- Member account.', 'Isolation:', '- Isolated E2E stack.',
  'Steps:', '- Sign in, then open the dashboard.',
  'Visible outcomes:', '- confirmation is visible', 'Acceptance mapping:',
  '- AC1 | Story clause: see confirmation | Actor: Member | Visible outcome: confirmation is visible',
].join('\\n'), 'text/plain');
"""
        self.assertEqual(preflight_story_metadata_failures("example", source, story, criteria), [])
        literal_source = source.replace(
            "`${STORY_INTENT}\\n\\n${AC_DESCRIPTION}`",
            "'As a member, I want to edit my profile so that my details stay current. AC1: Given an editable profile, When I save, Then I see confirmation.'",
        )
        self.assertEqual(preflight_story_metadata_failures("example", literal_source, story), [])
        failures = preflight_story_metadata_failures(
            "example", source.replace("${STORY_INTENT}\\n\\n", ""), story, criteria
        )
        self.assertTrue(any("user-story intent" in failure for failure in failures), failures)
        failures = preflight_story_metadata_failures(
            "example", source.replace("STORY_INTENT = 'As a member, I want to edit my profile so that my details stay current.'", "STORY_INTENT = 'Different intent'"), story, criteria
        )
        self.assertTrue(any("STORY_INTENT must match" in failure for failure in failures), failures)

    def test_result_description_binds_to_its_own_ac(self):
        # An AC<n>b subSuite still binds its Description to the base AC label.
        self.assertEqual(
            description_failures("example", "AC6: the second clause is visible", "AC6b: the second clause is visible", "Given a page is open"),
            [],
        )
        self.assertEqual(
            description_failures(
                "example",
                "AC1: Given a member,\nWhen they save,\nThen it is visible",
                "AC1: Given a member, When they save, Then it is visible",
                "Given a page is open",
                {"AC1": "Given a member, When they save, Then it is visible"},
            ),
            [],
        )
        # Prose that is not the AC line → rejected.
        failures = description_failures("example", "Given a member opens a page\nThen they see it", "AC1: page visible", "Given a page is open")
        self.assertTrue(any("must start with 'AC1:'" in failure for failure in failures), failures)
        # Wrong AC label.
        failures = description_failures("example", "AC2: unrelated criterion", "AC1: page visible", "Given a page is open")
        self.assertTrue(any("must start with 'AC1:'" in failure for failure in failures), failures)
        # Right label, text does not match the frozen criterion.
        failures = description_failures("example", "AC1: different wording", "AC1: page visible", "Given a page is open", {"AC1": "the page is visible"})
        self.assertTrue(any("frozen acceptance criterion" in failure for failure in failures), failures)

    def test_frozen_acceptance_criterion_rejects_an_authored_summary_early_and_after_run(self):
        story = "Given a member opens the dashboard, When they sign in, Then they see their packages."
        criteria = {"AC1": story}
        source = """const STORY = `Given a member opens the dashboard, When they sign in, Then they see their packages.`;
const AC_DESCRIPTION = 'AC1: packages are visible';
await allure.subSuite(AC_DESCRIPTION);
await allure.description(AC_DESCRIPTION);
"""
        failures = preflight_story_metadata_failures("example", source, story, criteria)
        self.assertTrue(any("frozen acceptance criterion" in failure for failure in failures), failures)
        exact = f"AC1: {story}"
        self.assertEqual(
            description_failures("example", exact, exact, story, criteria),
            [],
        )
        # Verbatim subSuite, but a summarised Description → still rejected.
        failures = description_failures("example", "AC1: packages are visible", exact, story, criteria)
        self.assertTrue(any("frozen acceptance criterion" in failure for failure in failures), failures)
        unused_constant = source.replace("await allure.description(AC_DESCRIPTION);", "await allure.description('AC1: packages are visible');")
        failures = preflight_story_metadata_failures("example", unused_constant, story, criteria)
        self.assertTrue(any("must use AC_DESCRIPTION" in failure for failure in failures), failures)

    def test_result_description_requires_story_intent_when_present(self):
        story = """US1: Profile

As a member, I want to edit my profile so that my details stay current.

AC1: Given an editable profile, When I save, Then I see confirmation."""
        description = "As a member, I want to edit my profile so that my details stay current.\n\nAC1: Given an editable profile, When I save, Then I see confirmation."
        ac_line = "AC1: Given an editable profile, When I save, Then I see confirmation."
        self.assertEqual(description_failures("example", description, ac_line, story), [])
        failures = description_failures("example", ac_line, ac_line, story)
        self.assertTrue(any("user-story intent" in failure for failure in failures), failures)

    def test_cosmetic_story_edits_do_not_break_a_frozen_description(self):
        # A smart-quote / rewrap in the story doc must not force a spec re-freeze.
        criteria = {"AC1": "Given the member's profile, When they save, Then it's confirmed"}
        self.assertEqual(
            description_failures(
                "example",
                "AC1: Given the member’s profile, When they save,\nThen it’s confirmed",
                "AC1: Given the member’s profile, When they save, Then it’s confirmed",
                "story",
                criteria,
            ),
            [],
        )

    def test_acnb_description_binds_to_the_base_ac(self):
        # subSuite carries the "b" suffix on the label; its text is the AC6 criterion,
        # and the Description's AC line uses the base label AC6.
        self.assertEqual(
            description_failures(
                "example",
                "AC6: the second clause is visible",
                "AC6b: the second clause is visible",
                "Given a page is open",
                {"AC6": "the second clause is visible"},
            ),
            [],
        )
        failures = description_failures(
            "example", "AC6b: the second clause is visible", "AC6b: the second clause is visible",
            "Given a page is open", {"AC6": "the second clause is visible"},
        )
        self.assertTrue(any("must be exactly 'AC6:" in f for f in failures), failures)

    def test_preflight_accepts_a_multiline_ac_description(self):
        story = "Given a member,\nWhen they save,\nThen it is visible"
        criteria = {"AC1": "Given a member, When they save, Then it is visible"}
        source = (
            "const STORY = `Given a member,\nWhen they save,\nThen it is visible`;\n"
            "const AC_DESCRIPTION = `AC1: Given a member,\nWhen they save,\nThen it is visible`;\n"
            "await allure.subSuite('AC1: it is visible');\n"
            "await allure.description(AC_DESCRIPTION);\n"
            "await allure.attachment('Test plan', [\n"
            "  'Data:', '- Member.', 'Isolation:', '- Isolated E2E stack.',\n"
            "  'Steps:', '- Save.', 'Visible outcomes:', '- it is visible',\n"
            "  'Acceptance mapping:',\n"
            "  '- AC1 | Story clause: it is visible | Actor: member | Visible outcome: it is visible',\n"
            "].join('\\n'), 'text/plain');\n"
        )
        failures = preflight_story_metadata_failures("example", source, story, criteria)
        self.assertFalse(any("absent from story-analysis" in f for f in failures), failures)
        self.assertFalse(any("AC_DESCRIPTION must be" in f for f in failures), failures)

    def test_non_passing_scenarios_carry_a_reason_trailer(self):
        story = "As a member, I want packages.\n\nAC1: Given a member, Then packages are visible."
        criteria = {"AC1": "Given a member, Then packages are visible."}
        title = "AC1: Given a member, Then packages are visible."
        ac_line = "As a member, I want packages.\n\n" + title
        fail_t = "\n\nFailure: the story needs a package list but the page shows an empty panel; the list query is never rendered."
        block_t = "\n\nBlocked: no seeded member has zero packages, so the empty-state cannot be reached without an out-of-story data step."
        # passed: bare Description ok, any trailer forbidden
        self.assertEqual(description_failures("x", ac_line, title, story, criteria, "passed"), [])
        self.assertTrue(any("only a failed or skipped" in f
            for f in description_failures("x", ac_line + fail_t, title, story, criteria, "passed")))
        # failed → Failure: trailer required; skipped → Blocked: trailer required
        self.assertEqual(description_failures("x", ac_line + fail_t, title, story, criteria, "failed"), [])
        self.assertEqual(description_failures("x", ac_line + block_t, title, story, criteria, "skipped"), [])
        self.assertTrue(any("'Failure: <why>' paragraph" in f
            for f in description_failures("x", ac_line, title, story, criteria, "failed")))
        # wrong trailer keyword for the status
        self.assertTrue(any("must be 'Blocked: <why>', not 'Failure:'" in f
            for f in description_failures("x", ac_line + fail_t, title, story, criteria, "skipped")))
        # the trailer does not disturb the AC binding
        self.assertFalse(any("must be exactly" in f
            for f in description_failures("x", ac_line + fail_t, title, story, criteria, "failed")))

    def test_portal_keeps_the_verbatim_criterion_and_reason_trailer(self):
        # Multi-line compound criterion whose body contains a numbered sub-list.
        crit_ml = "The dashboard lists, top to bottom:\n1. the package list\n2. the renewal banner"
        crit_flat = "The dashboard lists, top to bottom: 1. the package list 2. the renewal banner"
        with tempfile.TemporaryDirectory() as directory:
            results = Path(directory)
            (results / "plan.txt").write_text(
                "Data:\n- Member.\nSteps:\n- Open dashboard.\nVisible outcomes:\n- list visible\n"
                "Acceptance mapping:\n- AC1 | Story clause: list visible | Actor: Member | Visible outcome: list visible\n",
                encoding="utf-8",
            )
            result = {
                "name": "AC1",
                "status": "failed",
                "description": f"As a member, I want a dashboard.\n\nAC1: {crit_ml}\n\n"
                               "Failure: the panel is empty. Likely causes:\n1. the query never runs\n2. the panel never mounts",
                "labels": [
                    {"name": "feature", "value": "Feature: Packages"},
                    {"name": "suite", "value": "US1: Packages"},
                    {"name": "subSuite", "value": f"AC1: {crit_ml}"},
                ],
                "steps": [{"attachments": [{"name": "Test plan", "source": "plan.txt"}], "steps": []}],
            }
            (results / "abc-result.json").write_text(json.dumps(result), encoding="utf-8")
            add_display_hierarchy(results)
            out = json.loads((results / "abc-result.json").read_text(encoding="utf-8"))
        # full verbatim criterion survives in both the label and the Description;
        # neither the trailer's "1." nor the criterion's own "1." truncates it
        sub = next(l["value"] for l in out["labels"] if l["name"] == "subSuite")
        self.assertEqual(" ".join(sub.split()), f"AC1: {crit_flat}")
        paras = out["description"].split("\n\n")
        self.assertEqual(" ".join(paras[1].split()), f"AC1: {crit_flat}")   # AC line not truncated
        self.assertTrue(paras[2].startswith("Failure: the panel is empty."))  # trailer preserved

    def test_story_intent_stops_at_the_first_criterion_line(self):
        # Intent and ACs separated by a single newline (not a blank line).
        story = "As a member, I want to save\nso that it persists.\nAC1: Given a form, Then it saves.\n2. Given nothing, Then nothing."
        self.assertEqual(normalize_prose(story_intent(story)), "As a member, I want to save so that it persists.")

    def test_criterion_from_story_normalises_label_and_keeps_text_verbatim(self):
        # AC<n>b label -> base AC<n>, text unchanged, collapsed to one line.
        self.assertEqual(
            criterion_from_story("AC6b: the second clause is visible"),
            "AC6: the second clause is visible",
        )
        # A numbered sub-list inside the criterion is preserved verbatim, not truncated.
        self.assertEqual(
            criterion_from_story("AC1: the dashboard lists:\n1. packages\n2. renewals"),
            "AC1: the dashboard lists: 1. packages 2. renewals",
        )

    def test_test_plan_requires_a_steps_section(self):
        plan = """Data:
- Member account.
Isolation:
- Isolated E2E stack.
Visible outcomes:
- dashboard shows packages
Acceptance mapping:
- AC1 | Story clause: packages | Actor: Member | Visible outcome: dashboard shows packages
"""
        # Enforced at preflight (require_steps=True), not post-run verification.
        self.assertEqual(verify_test_plan_content("example", plan, "story")[0], [])
        failures, _, _ = verify_test_plan_content("example", plan, "story", require_steps=True)
        self.assertTrue(any("'Steps:'" in failure for failure in failures), failures)

    def test_allure_3_version_pattern_tolerates_a_banner(self):
        self.assertTrue(ALLURE_3_VERSION_PATTERN.search("Allure Report 3.0.0\nnpm notice update available"))
        self.assertTrue(ALLURE_3_VERSION_PATTERN.search("3.11.2"))
        self.assertIsNone(ALLURE_3_VERSION_PATTERN.search("2.29.0"))

    def test_strip_error_context_removes_playwright_prompt_attachment(self):
        result = {
            "attachments": [{"name": "stdout", "source": "a.txt"}, {"name": "error-context.md", "source": "c.md"}],
            "steps": [
                {"name": "step", "attachments": [{"name": "_error-context", "source": "e.md"}],
                 "steps": [{"name": "inner", "attachments": [{"name": "error-context", "source": "f.md"}]}]},
            ],
        }
        strip_error_context(result)
        self.assertEqual([a["name"] for a in result["attachments"]], ["stdout"])
        self.assertEqual(result["steps"][0]["attachments"], [])
        self.assertEqual(result["steps"][0]["steps"][0]["attachments"], [])

    def test_strip_error_context_drops_the_genuine_playwright_wrapper_step(self):
        # allure-playwright's own attachment wrapper: name matches, no status,
        # no sub-steps, exactly one attachment that is itself error-context.
        result = {
            "attachments": [],
            "steps": [
                {"name": "error-context", "status": None, "attachments": [{"name": "error-context"}], "steps": []},
                {"name": "Navigate", "status": "passed", "attachments": [], "steps": []},
            ],
        }
        strip_error_context(result)
        self.assertEqual([s["name"] for s in result["steps"]], ["Navigate"])

    def test_strip_error_context_keeps_a_real_step_with_the_same_name(self):
        # A real, user-authored step that happens to share the wrapper's name
        # but carries its own status/children/attachments must survive intact.
        result = {
            "attachments": [],
            "steps": [
                {
                    "name": "error-context",
                    "status": "passed",
                    "attachments": [{"name": "evidence.png"}],
                    "steps": [{"name": "nested check", "attachments": [], "steps": []}],
                },
            ],
        }
        strip_error_context(result)
        self.assertEqual(len(result["steps"]), 1)
        self.assertEqual(result["steps"][0]["attachments"], [{"name": "evidence.png"}])
        self.assertEqual(result["steps"][0]["steps"], [{"name": "nested check", "attachments": [], "steps": []}])

    def test_strip_error_context_keeps_a_step_that_only_mentions_the_phrase(self):
        original = {"name": "Confirm error-context banner clears after retry", "status": "passed",
                    "attachments": [], "steps": []}
        result = {"attachments": [], "steps": [dict(original)]}
        strip_error_context(result)
        self.assertEqual(result["steps"], [original])

    def test_namespace_id_resolves_delimiter_ambiguous_collision(self):
        # A plain "campaign_id:" join would make these two collide on "A:B:C".
        first = _namespace_id("C", "A:B")
        second = _namespace_id("B:C", "A")
        self.assertNotEqual(first, second)

    def test_namespace_id_handles_empty_none_and_non_string(self):
        self.assertEqual(_namespace_id("", "camp1"), "5:camp1:")
        self.assertIsNone(_namespace_id(None, "camp1"))
        self.assertEqual(_namespace_id(42, "camp1"), "5:camp1:42")

    def test_scrub_error_context_tolerates_null_labels(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y", "labels": None, "attachments": [],
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "campX")
            result = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(result["historyId"], "5:campX:x")

    def test_refresh_pipeline_tolerates_null_labels_end_to_end(self):
        # scrub_error_context and add_display_hierarchy run back-to-back on the
        # same file in refresh(); a fix that only makes the first survive
        # `labels: null` is not enough if the second still crashes on it.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y", "labels": None, "attachments": [], "steps": [],
                "description": "Story — User Story 1: Test\n\nAs a user, I can do X.",
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "campX")
            add_display_hierarchy(Path(directory))  # must not raise

    def test_refresh_pipeline_tolerates_null_steps_and_attachments_nested(self):
        # test_plan_text's walk() recurses through every nested step's own
        # `steps`/`attachments`; a top-level-only normalization would still
        # crash here, since the null is two levels deep.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "a", "testCaseId": "b", "labels": [], "attachments": [],
                "description": "US1: Test\n\nAC1: given a user, then something happens.",
                "steps": [{"name": "outer", "attachments": None,
                           "steps": [{"name": "inner", "attachments": None, "steps": None}]}],
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "campY")
            add_display_hierarchy(Path(directory))  # must not raise

    def test_add_display_hierarchy_tolerates_a_null_element_inside_labels(self):
        # load_result only normalizes a wholly-null field; a null *element*
        # sitting inside an otherwise-present list is a different corruption
        # shape that add_display_hierarchy must still survive, not just skip
        # scrub_error_context's own guard.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y",
                "labels": [None, {"name": "feature", "value": "F"}],
                "attachments": [], "steps": [],
                "description": "US1: Test\n\nAC1: given a user, then something happens.",
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "campX")
            add_display_hierarchy(Path(directory))  # must not raise

    def test_load_result_tolerates_wrong_type_not_just_null(self):
        # load_result() is called before either function's own try/except, so
        # a field that is present with the WRONG TYPE (not just null) must be
        # normalized inside load_result itself, or the crash happens upstream
        # of both guards and still takes down the whole refresh().
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y",
                "labels": "not-a-list", "attachments": {"weird": "dict"}, "steps": 5,
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "camp1")
            add_display_hierarchy(Path(directory))  # must not raise

    def test_load_result_drops_a_non_dict_element_consistently(self):
        # A stray None inside a step's attachments must not make
        # scrub_error_context and add_display_hierarchy disagree on the
        # file's shape — one crashing (leaving namespacing un-applied) while
        # the other tolerates it and includes the file anyway would silently
        # reintroduce the cross-campaign collision namespacing exists to stop.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "dupID", "testCaseId": "dupID2", "labels": [],
                "attachments": [], "steps": [{"name": "weird", "attachments": [None], "steps": []}],
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "camp1")
            result = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(result["historyId"], "5:camp1:dupID")
            add_display_hierarchy(Path(directory))
            result = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(result["historyId"], "5:camp1:dupID")

    def test_load_result_logs_and_drops_an_unnormalizable_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            deep = {"name": "leaf", "attachments": [], "steps": []}
            for _ in range(3000):
                deep = {"name": "wrap", "attachments": [], "steps": [deep]}
            path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y", "labels": [], "attachments": [], "steps": [deep],
            }), encoding="utf-8")
            result, stderr_text = _stderr_of(lambda: load_result(path))
            self.assertIsNone(result)
            self.assertIn("dropping unnormalizable", stderr_text)

    def test_load_result_logs_and_drops_a_truncated_utf8_file(self):
        # A killed/partial write can leave a truncated multi-byte UTF-8
        # sequence — a ValueError, not OSError/JSONDecodeError — which must
        # be skipped like any other unreadable file, not crash refresh().
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_bytes(b'{"historyId": "x", "bad": "' + bytes([0xFF]) + b'"}')
            result, stderr_text = _stderr_of(lambda: load_result(path))
            self.assertIsNone(result)
            self.assertIn("skipping unreadable", stderr_text)

    def test_scrub_error_context_survives_a_lone_surrogate_on_write(self):
        # json.loads happily parses a lone UTF-16 surrogate escape (e.g. a
        # truncated `\ud83d` from a killed process) into a str, but encoding
        # that str back to UTF-8 bytes on write raises UnicodeEncodeError
        # unless the dump escapes it as ASCII instead of a raw code point.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y", "labels": [], "attachments": [],
                "steps": [], "description": "bad \ud83d surrogate",
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "camp1")  # must not raise
            add_display_hierarchy(Path(directory))  # must not raise

    def test_scrub_error_context_round_trips_real_unicode_text(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            text = "Café — naïve test with éè characters"
            path.write_text(json.dumps({
                "historyId": "a", "testCaseId": "b", "labels": [], "attachments": [],
                "steps": [], "description": text,
            }), encoding="utf-8")
            scrub_error_context(Path(directory), "camp2")
            result = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(result["description"], text)

    def test_atomic_write_text_leaves_the_target_untouched_on_failure(self):
        # A plain path.write_text() opens in "w" mode, which truncates the
        # file before writing a single byte — so an OSError during the write
        # itself (disk-full, AV lock) would leave it empty/corrupted, not
        # merely "unwritten". _atomic_write_text must never do that: original
        # content must survive every attempt failing.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "target.json"
            original = '{"kept": "original"}'
            path.write_text(original, encoding="utf-8")
            with mock.patch("os.replace", side_effect=OSError("disk full (simulated)")), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                with self.assertRaises(OSError):
                    _atomic_write_text(path, '{"new": "content"}')
            self.assertEqual(path.read_text(encoding="utf-8"), original)
            # No leftover .tmp-<pid> sibling file from the failed attempts.
            leftovers = [item for item in Path(directory).iterdir() if item != path]
            self.assertEqual(leftovers, [])

    def test_atomic_write_text_cleans_up_after_a_unicode_encode_error_too(self):
        # A prior round only caught OSError here, not UnicodeEncodeError,
        # even though _WRITE_FAILURE_TYPES (used by every caller) names both
        # as the same failure class — so a lone surrogate reaching
        # tmp.write_text would have skipped both the retry and the temp-file
        # cleanup, leaking a `.tmp-<pid>-<hex>` file forever.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "target.json"
            original = '{"kept": "original"}'
            path.write_text(original, encoding="utf-8")
            with mock.patch("serve_e2e_reports.time.sleep"):
                with self.assertRaises(UnicodeEncodeError):
                    _atomic_write_text(path, "bad \ud83d surrogate", attempts=1)
            self.assertEqual(path.read_text(encoding="utf-8"), original)
            leftovers = [item for item in Path(directory).iterdir() if item != path]
            self.assertEqual(leftovers, [], "the failed attempt's temp file must not be left behind")

    def test_atomic_write_text_retries_even_if_cleanup_of_a_failed_attempt_also_fails(self):
        # The same transient lock that just made os.replace fail could just
        # as easily block deleting the temp file that failed attempt left
        # behind. That cleanup failure must be swallowed, not propagate and
        # short-circuit the retry loop down to a single attempt.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "target.json"
            path.write_text('{"kept": "original"}', encoding="utf-8")
            replace_calls: list[None] = []

            def flaky_replace(src, dst):
                replace_calls.append(None)
                raise OSError("disk full (simulated)")

            with mock.patch("os.replace", side_effect=flaky_replace), \
                 mock.patch.object(Path, "unlink", side_effect=OSError("locked (simulated)")), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                with self.assertRaises(OSError):
                    _atomic_write_text(path, '{"new": "content"}')
            self.assertEqual(len(replace_calls), 3)
            self.assertEqual(path.read_text(encoding="utf-8"), '{"kept": "original"}')

    def test_log_skip_classifies_each_exception_type_correctly(self):
        # OSError and UnicodeEncodeError both only happen at the write step
        # (never while normalizing an already-parsed dict), so both must be
        # reported as "a write failure"; anything else (a genuine shape bug
        # like AttributeError/TypeError/IndexError/UnicodeDecodeError) must
        # stay "an unexpected shape".
        for error, expected in [
            (OSError("disk full"), "a write failure"),
            (UnicodeEncodeError("utf-8", "x", 0, 1, "surrogate"), "a write failure"),
            (AttributeError("no such attribute"), "an unexpected shape"),
            (UnicodeDecodeError("utf-8", b"\xff", 0, 1, "invalid start byte"), "an unexpected shape"),
        ]:
            _, stderr_text = _stderr_of(lambda: _log_skip("some_func", Path("r1-result.json"), error))
            self.assertIn(expected, stderr_text, f"for {type(error).__name__}")

    def test_atomic_write_text_retries_a_transient_failure(self):
        # An AV lock or similar is usually gone within milliseconds; a write
        # that fails once or twice should still succeed rather than giving up
        # on the very first attempt.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "target.json"
            path.write_text('{"kept": "original"}', encoding="utf-8")
            real_replace = os.replace
            calls: list[None] = []

            def flaky_replace(src, dst):
                calls.append(None)
                if len(calls) < 3:
                    raise OSError("locked (simulated)")
                return real_replace(src, dst)

            with mock.patch("os.replace", side_effect=flaky_replace), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                _atomic_write_text(path, '{"new": "content"}')  # must not raise
            self.assertEqual(len(calls), 3)
            self.assertEqual(path.read_text(encoding="utf-8"), '{"new": "content"}')

    def test_scrub_error_context_tolerates_a_recursion_error(self):
        # A *-result.json whose steps tree is deep enough to survive
        # load_result's own normalization walk, but not quite deep enough to
        # survive strip_error_context's or json.dumps's separate recursive
        # walk over that same tree, must be skipped like any other
        # unexpected-shape failure — not aborting refresh() for every other
        # campaign. Not verified via a real stack-depth reproduction (fragile
        # and Python-version-dependent); instead, directly confirms the
        # except tuple's RecursionError branch actually fires and continues.
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "r1-result.json"
            path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y", "labels": [], "attachments": [], "steps": [],
            }), encoding="utf-8")
            with mock.patch("json.dumps", side_effect=RecursionError("maximum recursion depth exceeded")):
                _, stderr_text = _stderr_of(lambda: scrub_error_context(Path(directory), "camp1"))  # must not raise
            self.assertIn("an unexpected shape", stderr_text)

    def test_scrub_error_context_tolerates_a_write_failure_on_one_file(self):
        # A disk-full/permission-denied/AV-locked write on this disposable
        # temp copy must be as non-fatal to the whole multi-campaign refresh
        # as a bad read is — it must not escape scrub_error_context and abort
        # every other campaign's processing in refresh()'s loop, and (since
        # _atomic_write_text is what actually performs the write) the failing
        # file's ENTIRE original content, not just one field, must survive
        # byte-for-byte rather than being truncated.
        with tempfile.TemporaryDirectory() as directory:
            bad_path = Path(directory) / "r1-result.json"
            good_path = Path(directory) / "r2-result.json"
            original_bad_text = json.dumps({
                "historyId": "x", "testCaseId": "y", "labels": [],
                "attachments": [], "steps": [],
            })
            bad_path.write_text(original_bad_text, encoding="utf-8")
            good_path.write_text(json.dumps({
                "historyId": "x", "testCaseId": "y", "labels": [],
                "attachments": [], "steps": [],
            }), encoding="utf-8")
            real_replace = os.replace

            def flaky_replace(src, dst):
                if Path(dst) == bad_path:
                    raise OSError("disk full (simulated)")
                return real_replace(src, dst)

            with mock.patch("os.replace", side_effect=flaky_replace), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                _, stderr_text = _stderr_of(lambda: scrub_error_context(Path(directory), "camp1"))  # must not raise
            self.assertIn("a write failure", stderr_text)
            # The file that failed to write keeps its exact original content
            # (not merely a re-derived equivalent field) — no truncation.
            self.assertEqual(bad_path.read_text(encoding="utf-8"), original_bad_text)
            # The other file in the same batch was still processed normally.
            self.assertEqual(json.loads(good_path.read_text(encoding="utf-8"))["historyId"], "5:camp1:x")

    def test_add_display_hierarchy_tolerates_a_recursion_error(self):
        # Same reasoning as scrub_error_context's sibling test: a tree deep
        # enough to survive load_result's own normalization but not
        # simplify_assertion_steps's/json.dumps's separate recursive walks
        # over it must be skipped, not crash refresh() for every campaign.
        with tempfile.TemporaryDirectory() as directory:
            results = Path(directory)
            (results / "plan.txt").write_text(
                "Acceptance mapping:\n- AC1 | Story clause: packages | Actor: Member | "
                "Visible outcome: dashboard shows packages\n",
                encoding="utf-8",
            )
            result = {
                "description": "As a member, I want a dashboard.\n\nAC1: dashboard shows packages",
                "labels": [
                    {"name": "feature", "value": "Feature: Packages"},
                    {"name": "suite", "value": "US1: Packages"},
                ],
                "steps": [{"attachments": [{"name": "Test plan", "source": "plan.txt"}], "steps": []}],
            }
            (results / "r1-result.json").write_text(json.dumps(result), encoding="utf-8")
            with mock.patch("json.dumps", side_effect=RecursionError("maximum recursion depth exceeded")):
                _, stderr_text = _stderr_of(lambda: add_display_hierarchy(results))  # must not raise
            self.assertIn("an unexpected shape", stderr_text)

    def test_add_display_hierarchy_tolerates_a_write_failure_on_one_file(self):
        with tempfile.TemporaryDirectory() as directory:
            results = Path(directory)
            (results / "plan.txt").write_text(
                "Acceptance mapping:\n- AC1 | Story clause: packages | Actor: Member | "
                "Visible outcome: dashboard shows packages\n",
                encoding="utf-8",
            )
            good_result = {
                "description": "As a member, I want a dashboard.\n\nAC1: dashboard shows packages",
                "labels": [
                    {"name": "feature", "value": "Feature: Packages"},
                    {"name": "suite", "value": "US1: Packages"},
                ],
                "steps": [{"attachments": [{"name": "Test plan", "source": "plan.txt"}], "steps": []}],
            }
            bad_path = results / "r1-result.json"
            good_path = results / "r2-result.json"
            original_bad_text = json.dumps(good_result)
            bad_path.write_text(original_bad_text, encoding="utf-8")
            good_path.write_text(json.dumps(good_result), encoding="utf-8")
            real_replace = os.replace

            def flaky_replace(src, dst):
                if Path(dst) == bad_path:
                    raise OSError("locked (simulated)")
                return real_replace(src, dst)

            with mock.patch("os.replace", side_effect=flaky_replace), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                _, stderr_text = _stderr_of(lambda: add_display_hierarchy(results))  # must not raise
            self.assertIn("a write failure", stderr_text)
            # Untouched, byte-for-byte — not truncated by the failed write.
            self.assertEqual(bad_path.read_text(encoding="utf-8"), original_bad_text)
            # The sibling file in the same batch was still relabeled normally.
            good_labels = {l["name"]: l["value"] for l in json.loads(good_path.read_text(encoding="utf-8"))["labels"]}
            self.assertEqual(good_labels["subSuite"], "AC1: packages")

    def test_add_readable_tree_style_names_the_css_file_when_that_write_fails(self):
        # Purely cosmetic: a write failure here must not abort refresh() for
        # every campaign the way it would if this function's writes weren't
        # wrapped at all — unlike scrub_error_context/add_display_hierarchy,
        # there's no second file in the same batch to fall back on, so
        # "tolerates" here means "logs and returns", not "skips one of many".
        # The CSS write happens first, so an unconditional os.replace failure
        # exercises "the CSS file is what actually failed" — the sibling test
        # below covers the other of the two files this function writes.
        with tempfile.TemporaryDirectory() as directory:
            report_dir = Path(directory)
            css_path = report_dir / "story-e2e-tree.css"
            original = "<html><head></head><body></body></html>"
            (report_dir / "index.html").write_text(original, encoding="utf-8")
            with mock.patch("os.replace", side_effect=OSError("disk full (simulated)")), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                _, stderr_text = _stderr_of(lambda: add_readable_tree_style(report_dir))  # must not raise
            self.assertIn("add_readable_tree_style: skipping", stderr_text)
            self.assertIn(str(css_path), stderr_text)
            self.assertNotIn("index.html", stderr_text)
            # index.html is untouched — the CSS write failed before it was ever reached.
            self.assertEqual((report_dir / "index.html").read_text(encoding="utf-8"), original)

    def test_add_readable_tree_style_names_index_html_when_that_write_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            report_dir = Path(directory)
            index_path = report_dir / "index.html"
            css_path = report_dir / "story-e2e-tree.css"
            index_path.write_text("<html><head></head><body></body></html>", encoding="utf-8")
            real_replace = os.replace

            def flaky_replace(src, dst):
                if Path(dst) == index_path:
                    raise OSError("disk full (simulated)")
                return real_replace(src, dst)

            with mock.patch("os.replace", side_effect=flaky_replace), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                _, stderr_text = _stderr_of(lambda: add_readable_tree_style(report_dir))  # must not raise
            self.assertIn(str(index_path), stderr_text)
            # The CSS write succeeded before the index.html write failed.
            self.assertTrue(css_path.exists())

    def test_embed_trace_viewer_tolerates_a_failure_staging_the_bundle(self):
        # shutil.copytree, staging the trace-viewer bundle itself, used to be
        # completely unguarded — a disk-full/AV-lock/stale-locked-directory
        # failure there would crash refresh() for every campaign, exactly the
        # fatal-cosmetic-write failure mode this function's per-script writes
        # were hardened against one step later in the same function.
        with tempfile.TemporaryDirectory() as directory:
            report_dir, harness_root = _stage_trace_viewer_bundle(Path(directory))
            target = report_dir / "_trace-viewer"

            def flaky_copytree(src, dst):
                # A real copytree that fails partway through leaves some
                # files already copied — reproduce that, not just "nothing
                # was ever written", so the cleanup this test checks for is
                # actually exercised rather than trivially satisfied.
                Path(dst).mkdir(parents=True, exist_ok=True)
                (Path(dst) / "partial.txt").write_text("partial", encoding="utf-8")
                raise OSError("disk full (simulated)")

            with mock.patch("shutil.copytree", side_effect=flaky_copytree):
                _, stderr_text = _stderr_of(lambda: embed_trace_viewer(report_dir, harness_root))  # must not raise
            self.assertIn("embed_trace_viewer: skipping", stderr_text)
            self.assertIn("a write failure", stderr_text)
            # A partially-staged bundle must not be left behind as debris for
            # refresh()'s later shutil.move to promote into the published report.
            self.assertFalse(target.exists())

    def test_embed_trace_viewer_tolerates_a_write_failure_on_one_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            report_dir, harness_root = _stage_trace_viewer_bundle(Path(directory))
            matching_js = 'a="https://trace.playwright.dev",b=`${a}/`;'
            bad_script = report_dir / "app-bad.js"
            good_script = report_dir / "app-good.js"
            bad_script.write_text(matching_js, encoding="utf-8")
            good_script.write_text(matching_js, encoding="utf-8")
            real_replace = os.replace

            def flaky_replace(src, dst):
                if Path(dst) == bad_script:
                    raise OSError("locked (simulated)")
                return real_replace(src, dst)

            with mock.patch("os.replace", side_effect=flaky_replace), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                _, stderr_text = _stderr_of(lambda: embed_trace_viewer(report_dir, harness_root))  # must not raise
            self.assertIn("embed_trace_viewer: skipping", stderr_text)
            # The file that failed to write keeps its original, unpatched text.
            self.assertEqual(bad_script.read_text(encoding="utf-8"), matching_js)
            # The sibling bundle in the same batch was still patched normally.
            self.assertIn("location.origin", good_script.read_text(encoding="utf-8"))

    def test_embed_trace_viewer_reports_a_read_failure_distinctly_from_no_match(self):
        # A read failure (AV lock, permission, a race deleting the file
        # between glob() and read()) means the pattern's match status against
        # that script is genuinely unknown — "pattern not matched" would be
        # an unverified claim, not an observed fact, and would wrongly point
        # whoever reads it at a regex/version-drift hypothesis instead of the
        # real read failure (already logged per-file above via _log_skip).
        with tempfile.TemporaryDirectory() as directory:
            report_dir, harness_root = _stage_trace_viewer_bundle(Path(directory))
            (report_dir / "app-one.js").write_text("irrelevant content", encoding="utf-8")
            with mock.patch.object(Path, "read_text", side_effect=OSError("locked (simulated)")):
                _, stderr_text = _stderr_of(lambda: embed_trace_viewer(report_dir, harness_root))  # must not raise
            self.assertIn("Could not check", stderr_text)
            self.assertIn("app-*.js", stderr_text)
            self.assertNotIn("pattern not matched", stderr_text)

    def test_embed_trace_viewer_reports_matched_but_all_writes_failed_distinctly(self):
        # If every script the pattern hits then fails to write (e.g. the
        # whole report directory is on a full disk), the diagnostic must say
        # so — not the "pattern not matched" message, which would wrongly
        # suggest an Allure version bump changed the bundle's own shape
        # rather than every write having failed.
        with tempfile.TemporaryDirectory() as directory:
            report_dir, harness_root = _stage_trace_viewer_bundle(Path(directory))
            matching_js = 'a="https://trace.playwright.dev",b=`${a}/`;'
            (report_dir / "app-one.js").write_text(matching_js, encoding="utf-8")
            (report_dir / "app-two.js").write_text(matching_js, encoding="utf-8")
            with mock.patch("os.replace", side_effect=OSError("disk full (simulated)")), \
                 mock.patch("serve_e2e_reports.time.sleep"):
                _, stderr_text = _stderr_of(lambda: embed_trace_viewer(report_dir, harness_root))  # must not raise
            self.assertIn("pattern matched but every write failed", stderr_text)
            self.assertNotIn("pattern not matched", stderr_text)

    def test_story_analysis_missing_criteria_does_not_cascade_per_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "story-analysis.json"
            path.write_text(json.dumps({
                "version": 2,
                "story": "Given a member, When they sign in, Then they see packages.",
                "setup": {"base_url": "http://x", "isolation": "lifecycle",
                          "allowed_files": ["e2e/tests/x.spec.ts"], "startup_commands": ["npm run dev"]},
                "claims": [
                    {"ac": "AC1", "clause": "they see packages", "actor": "member",
                     "visible_outcome": "packages listed", "surface": "Dashboard", "observation": "rows"},
                    {"ac": "AC2", "clause": "they sign in", "actor": "member",
                     "visible_outcome": "session starts", "surface": "Login", "observation": "redirect"},
                ],
            }), encoding="utf-8")
            failures = verify_story_analysis(path)
        self.assertTrue(any("acceptance_criteria must be a non-empty array" in f for f in failures), failures)
        self.assertFalse(any("is absent from acceptance_criteria" in f for f in failures), failures)

    def test_report_requires_allure_3_statistics(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory)
            widgets = report / "widgets"
            widgets.mkdir()
            (widgets / "summary.json").write_text('{"statistic":{"total":1}}', encoding="utf-8")
            failures = verify_report(report, 1, 1, 1, {"passed": 1}, 0)
            self.assertTrue(any("Allure 3 report statistics" in failure for failure in failures), failures)
            (widgets / "statistic.json").write_text(
                '{"total":1,"passed":1,"failed":0,"broken":0,"skipped":0,"unknown":0}',
                encoding="utf-8",
            )
            self.assertEqual(verify_report(report, 1, 1, 1, {"passed": 1}, 0), [])

    def test_multiline_evidence_is_normalized_without_losing_assertion(self):
        assertion = {
            "name": "Assertion: [AC1] actor: Member | dashboard opens",
            "steps": [{"name": "Evidence: expected: dashboard visible | observed: dashboard\nvisible"}],
        }
        self.assertEqual(
            assertion_evidence_values(assertion),
            ("dashboard visible", "dashboard visible"),
        )


class SafetyGateTests(unittest.TestCase):
    def test_portal_uses_its_local_script_not_analytics(self):
        html = '<script async src="https://www.googletagmanager.com/gtag/js"></script><script src="app.js"></script>'
        self.assertEqual(local_script_sources(html), ["app.js"])


    def test_hidden_api_request_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            spec = Path(directory) / "story.spec.ts"
            spec.write_text("await page.request.get('/api/private');", encoding="utf-8")
            failures = scan_spec_graph(spec, {spec.resolve()})
        self.assertTrue(any("hidden API request" in failure for failure in failures), failures)

    def test_forced_browser_action_is_rejected(self):
        failure = forced_ui_action_failure("story.spec.ts", "await button.click({ force: true });")
        self.assertIsNotNone(failure)

    def test_invalid_trace_attachment_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "trace.zip"
            trace.write_bytes(b"not a zip")
            failure = verify_trace(trace, "story")
        self.assertIn("not a readable ZIP", failure or "")


class DiscoveryHandoffTests(unittest.TestCase):
    def foundation_manifest(self, root: Path) -> Path:
        foundation = root / "e2e"
        foundation.mkdir()
        protected = foundation / "fixture.ts"
        protected.write_text("export const fixture = true;\n", encoding="utf-8")
        manifest = foundation / "e2e-foundation.json"
        manifest.write_text(json.dumps({
            "protected_files": [{"path": "fixture.ts", "sha256": sha256(protected)}],
        }), encoding="utf-8")
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)
        subprocess.run(["git", "add", "e2e"], cwd=root, check=True)
        return manifest

    def handoff(self, root: Path, include_facts: bool = True) -> Path:
        analysis = root / "story-analysis.json"
        analysis.write_text(json.dumps({
            "version": 2,
            "story": "Given a member opens the dashboard, Then they see their packages.",
            "acceptance_criteria": [{"ac": "AC1", "text": "Given a member opens the dashboard, Then they see their packages."}],
            "setup": {"base_url": "http://localhost:3000", "isolation": "test lifecycle", "allowed_files": ["e2e/tests/dashboard.spec.ts"], "startup_commands": ["npm run dev"]},
            "claims": [{"ac": "AC1", "clause": "they see their packages", "actor": "Member", "visible_outcome": "packages list is visible", "surface": "Dashboard", "observation": "rendered heading and rows"}],
        }), encoding="utf-8")
        timings = root / "phase-timings.json"
        timings.write_text("{}", encoding="utf-8")
        handoff = {
            "version": 1,
            "story_analysis": {"path": str(analysis), "sha256": sha256(analysis)},
            "foundation_manifest": str(self.foundation_manifest(root)),
            "stack": {"base_url": "http://localhost:3000", "lifecycle_id": "e2e-demo", "project": "e2e-demo"},
            "claim_facts": [{"ac": "AC1", "visible_outcome": "packages list is visible", "facts": [{"kind": "role", "value": "heading Dashboard", "live_evidence": "MCP snapshot"}]}] if include_facts else [],
            "fixtures": [], "constraints": [], "source_paths": [],
            "phase_timings": str(timings), "discovery_context_tokens": 123,
        }
        path = root / "discovery-handoff.json"
        path.write_text(json.dumps(handoff), encoding="utf-8")
        return path

    def test_valid_discovery_handoff(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual(verify_discovery_handoff(self.handoff(Path(directory))), [])

    def test_discovery_handoff_requires_facts_for_every_claim(self):
        with tempfile.TemporaryDirectory() as directory:
            failures = verify_discovery_handoff(self.handoff(Path(directory), include_facts=False))
        self.assertTrue(any("claim_facts" in failure for failure in failures), failures)


if __name__ == "__main__":
    unittest.main(verbosity=2)
