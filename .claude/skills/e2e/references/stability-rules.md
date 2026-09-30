# Stability rules

Run only against an already-completed campaign's frozen specs and story result set.

1. Run every scenario at least three times on a fresh, healthy harness lifecycle. Use one worker and zero retries for every repetition.
2. Reject a truncated run or a broad wave of unrelated failures as environment degradation: discard it, use the project's documented harness teardown/start lifecycle, wait for its listeners/processes to be released and health to hold for several checks, then repeat only that run. Never use a fixed sleep or call it test flakiness. If the harness cannot stabilize, report the sweep environment-inconclusive and stop.
3. Compare `passed`, `failed`, `skipped`, and `broken` status by test identity across complete runs. Any status flip is flaky; a consistent FAIL or SKIP is a deterministic finding, not a flake.
4. Persist Allure 3 history across the repetitions and generate the report from the final run. Allure can then display its Flaky mark; Retry remains a separate mark for ordinary retried runs. The sweep verdict comes from the complete-run status comparison.
5. Write the result, run count, per-test status sets, and any excluded degraded runs to `artifacts/e2e/stability/<utc-id>-<campaign-slug>/`. Do not replace or amend the campaign's evidence.
