# 14: System documentation & residual-control register

**What to build:** The system documentation required by IM8, plus a recorded register of the compliance controls deferred to runtime/ops, so reviewers can see how the app satisfies (or consciously defers) each policy control. Verifiable by the docs existing and matching the shipped app.

**Blocked by:** 01 (baseline exists to document); best done after the API surface stabilises but can proceed in parallel once endpoints are known.

**Status:** ready-for-agent

- [ ] **IM8 pm-6 (AUTO-FIX):** architecture doc, API specification, and a data-flow diagram + network topology are added to the repo.
- [ ] **IM8 as-15 (AUTO-FIX):** documented operator-triggered forced-password-reset path for suspected account compromise.
- [ ] **IM8 ac-3 (WAIVED):** documented waiver — inactivity-based disable out of scope; admin manual disable + `last_login_at` recorded.
- [ ] **IM8 ac-4 (WAIVED):** documented waiver — periodic access review is an org/ops process out of scope; admin user list supports ad-hoc review.
- [ ] **IM8 lm-16 (WAIVED):** documented waiver — RED/USE monitoring infra out of scope; structured audit logging retained.
- [ ] **IM8 ac-2 (WAIVED):** documented waiver — MFA out of scope for all users; compensating controls recorded.
- [ ] Transport/HTTPS + HSTS deployment assumption documented; local-HTTP gap explicitly stated. (IM8 as-10, dp-3)
- [ ] The Compliance Waivers section lists every waived IM8 control with rationale + compensating controls.
