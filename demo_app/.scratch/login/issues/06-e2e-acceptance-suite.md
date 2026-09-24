# 06: End-to-end acceptance suite

**What to build:** A Playwright suite that runs the real frontend against the real backend and proves all 12 Gherkin scenarios from the spec source, one test per scenario, so the feature can be accepted as a whole. See spec §Seams (3) and §Testing Decisions.

**Blocked by:** 02, 03, 05

**Status:** done

**Spec scenarios:** Story 1 · Scenarios 1–8; Story 2 · Scenarios 1–4

- [x] Playwright configured (e.g. in `e2e/` or `frontend/`) to start the backend and frontend automatically via `webServer`
- [x] One test per scenario, named after the scenario, using the seeded `johndoe` account
- [x] Scenario 2 asserts no request to `/api/v1/auth/login` is made
- [x] Scenario 5 asserts the disabled/"Logging in..." state and a ≥400ms duration
- [x] Scenario 8 uses request interception to simulate both a `500` and a network abort
- [x] Story 2 Scenario 2 performs a real `page.reload()` and asserts "Hello, John!" and the URL stays `/`
- [x] Root `README.md` documents how to run the suite; the suite passes locally

## Comments

- Implementer (06): no product bugs found. All 12 scenarios pass against the real backend and frontend, and `--repeat-each=5` passed too (60/60). The suite lives in `frontend/e2e/` with `frontend/playwright.config.ts`. It uses a single Chromium project, and `npm run e2e` runs it. It is kept out of Vitest by the `include` setting in `vitest.config.ts`, while `tsc` (through `tsconfig.json` `include`) and Prettier still check it.
  - Scenario 2 records every page request to `/api/v1/auth/login` across three submits (both fields blank, username only, password only). It then waits 500ms, longer than the loading window, before asserting that none were sent.
  - Scenario 5 times the loading state inside the page with a `MutationObserver`, from when the button becomes disabled until it is re-enabled or unmounted, and asserts that this lasts at least 400ms. It also checks that the ellipsis dots have a running CSS animation. I confirmed that it fails, at about 256ms, when `MIN_SUBMITTING_MS` is set to `0`.
  - Scenario 6 checks the real `401` body for both a wrong password and an unknown username. Scenario 8 uses `page.route` to return a `500` and then an `abort("internetdisconnected")`, and clears the banner between the two cases.
  - Story 2 Scenario 2 reloads the page and asserts that `GET /me` returns `200`, so the greeting cannot come from the in-memory cache.
