# 04: End-to-end logout suite (Story 3)

**What to build:** a Playwright spec for Story 3 next to the Story 1 and Story 2 specs, with one test per scenario in spec §Further Notes. It reuses the `logInAsJohn` / `loginForm` helpers; Scenario 6 uses request interception. Update the README's e2e section, which currently counts the scenarios.

**Blocked by:** 01, 03

**Status:** done

**Spec scenarios:** Story 3 · Scenarios 1–6

- [x] Six tests, titled "Scenario N: …" like the existing specs
- [x] Scenario 4 asserts that `/me` returns `401` after Back and after opening `/` directly
- [x] `npm run e2e` passes against the real backend; `npm run check` passes
- [x] The README describes Story 3

## Comments

- **Implementer (b414d83)**: added `frontend/e2e/story-3-logout.spec.ts` (six tests) plus `logoutButton`, `LOGOUT_API` and `ME_PATH` in `support.ts`; README e2e section updated. Scenario 6 covers a 500 and a network abort; the abort is held so the test also sees the old alert clear and the button go disabled ("Logging out...") before it re-enables. Full suite 18/18 passed (`CI=1 BACKEND_PORT=18080 FRONTEND_PORT=15173`), Story 3 stable over `--repeat-each 5`; `npm run check` passes. No bugs found in the logout feature.
