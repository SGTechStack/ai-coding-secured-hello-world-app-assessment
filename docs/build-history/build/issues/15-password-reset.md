# 15: Password reset

**What to build:** A visitor who forgot their password requests a reset by email address, and a user redeems a reset link.

- **`POST /api/password-reset/request`** returns the same response whether or not the address is registered. Against a never-activated account it does nothing (R-CRED-012). The reset-requested audit row omits `user.id` (REJ-002).
  - In `dev`, the stubbed `EmailService` logs the link on a dedicated non-audit logger held by three controls (ADR-057; R-CRED-020).
  - Outside `dev`, the request produces nothing deliverable (R-CRED-021).
  - The link origin comes only from configuration, never from `Host`, `X-Forwarded-*` or the body (REJ-022).
- **`POST /api/password-reset/confirm`** redeems a 30-minute, single-use token through `PasswordService`. Redemption:
  - ends every session on the account (ADR-035; ADR-037);
  - clears the password lockout, the cap, the forced-change flag and `credential_issued_at` (ADR-009);
  - never touches TOTP state.

  It also redeems admin-issued reset tokens.
- **Rate limit:** add the request source (5 / 1 per 12 s), request identifier (3 / 1 per 20 min) and confirm source (10 / 1 per 6 s) rows.
- **Audit:** reset requested, reset completed, lock cleared (`PASSWORD_RESET_COMPLETED`).
- **SPA:** the forgot and reset pages.

**Blocked by:** 12, 14

**Status:** done

- [x] Registered and unregistered addresses get identical responses, and only a registered, activated one yields a link in `dev`.
- [x] A forged `Host` or `X-Forwarded-Host` doesn't change the link origin.
- [x] The token fails after 30 minutes and on second use.
- [x] After redemption, all prior sessions are gone, a locked or capped account can sign in, and TOTP rows are untouched.
- [x] In `ctx-nondev`, no link is logged.
- [x] A Playwright test covers forgot → reset → sign in.

**Verification:** `PasswordResetRequestTest` (identical 202 across activated, locked, not-activated, disabled and
unknown addresses; a link only for activated ones; T-CRED-012, T-CRED-018), `PasswordResetPortTest` (forged `Host` and
`X-Forwarded-Host` on the wire, T-CRED-016/017; T-SES-014), `PasswordResetConfirmTest` (30-minute expiry T-CRED-013,
single use, cross-type T-CRED-011, admin-minted token, T-LCK-017 with TOTP rows untouched, T-CRED-024, T-AUD-019),
`PasswordResetBudgetTest` (T-RL-004 and both source budgets), `ResetLinkConfinementTest` (no link logged outside
`dev`; T-AUD-030), ArchUnit T-CRED-009, and `frontend/e2e/reset.spec.ts` (forgot, reset, sign in; Chromium and
Firefox).
