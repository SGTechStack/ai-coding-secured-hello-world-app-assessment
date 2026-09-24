# 07: Registration, end to end

**What to build:** A visitor can create an account from a "Create an account" link on the login page. They get clear field errors, including "username already taken", then land on the login page with "Account created. Please log in." Nobody can register as an admin, weak passwords are rejected, and anything typed into the first name is shown as text, never run. See spec §User Stories › Registration, §Backend modules › Password policy and Registration, §Frontend modules, and Acceptance scenarios › Story 4.

**Blocked by:** 02, 04, 05, 06

**Status:** ready-for-agent

- [ ] A shared password policy component checks 12–64 characters, ≤72 UTF-8 bytes and a bundled top-10k common-password list (case-insensitive). It has no composition rules and never echoes the password back.
- [ ] `POST /api/v1/auth/register` is anonymous and CSRF-protected, with body `{username, email, firstName, password}` validated as the spec describes. Username and email are normalised to lowercase.
- [ ] Success → `201` with the new `UserProfile` and **no session** created. The new user can then log in.
- [ ] `"role": "ADMIN"` or `"enabled"` in the body has no effect: the account is `USER` and enabled.
- [ ] A taken username or email (case-insensitive) → `409 ACCOUNT_CONFLICT` with `fieldErrors` naming the field(s). A short, common or >72-byte password → `400 VALIDATION_FAILED` naming `password`.
- [ ] An HTML or script payload in `firstName` is stored and returned verbatim as JSON.
- [ ] Registration allows at most 10 requests per IP per 15 minutes, then answers `429` using the limiter from 06.
- [ ] A `USER_REGISTERED` audit event is emitted. The plaintext password never appears in captured logs.
- [ ] A `/register` route, anonymous-only (a signed-in user is redirected to `/`), with username, email, first name, password and confirm password fields. It validates on submit using `FormField`, catches a mismatched confirmation, maps server `fieldErrors` onto fields, and announces errors to screen readers.
- [ ] The login page has a "Create an account" link and shows a one-shot notice from a typed, fixed-enum search param, never free text.
- [ ] Frontend XSS test: with a `firstName` of `<img src=x onerror=alert(1)>`, the landing page shows the literal text and renders no `img`.
- [ ] e2e Story 4, scenarios 1–4, passes under the CSP fixture.
