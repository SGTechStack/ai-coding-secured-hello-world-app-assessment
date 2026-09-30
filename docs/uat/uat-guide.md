# User Acceptance Test guide

This guide holds one manual test script for each of the 12 user stories in
[`prd/assessment-prd.md`](../../prd/assessment-prd.md). Each script quotes the story and its acceptance criteria
(AC), lists its preconditions and test data, and gives numbered steps. Each step has an expected result and a
Pass/Fail box. Every AC is covered by at least one step. The **Covers** column names the AC a step proves, so
`AC2` means the story's second criterion.

Where the application deliberately behaves differently from the PRD's wording, the script says so under
**Deviations** and cites the record. The full PRD mapping is [`docs/prd-coverage.md`](../prd-coverage.md).

## Before you start

1. Follow the [Setup](../../README.md#setup) in the root README, up to and including step 6, on an **empty
   database**. Start the backend with the `Tee-Object` (or `tee`) variant from Setup step 4, so that its console is
   also saved in `backend-console.log`. **Skip Setup step 7**: it is the start of
   [UAT-12](#uat-12-initial-admin-bootstrap), so run UAT-12 part A first.
2. Keep three things open: the browser, the backend console, and your **authenticator app**, which UAT-12 part B
   enrols for a new administrator. `demo-admin` needs no app: the **Demo accounts** panel on the sign-in page always
   shows its current code (see [Signing in as the administrator](#signing-in-as-the-administrator)). "The backend
   console" below means terminal 1 or, more reliably, `backend-console.log` (README Setup, step 6, shows how to pull
   the newest link out of it).
3. Recommended order:
   1. UAT-12 parts A and B.
   2. Create `uat-carol` and `uat-dave` ([Appendix A](#appendix-a-register-and-activate-an-account)), then run UAT-03
      steps 1–5 and UAT-07 part B steps 1–3. Both start a clock.
   3. While the clocks run: UAT-01, 02, 04, 05, 06, 07 part A, 08, 09, 10 and 11, and UAT-03 steps 9–11.
   4. UAT-03 steps 6–8 (at least 20 minutes after its step 2) and UAT-07 part B steps 4–6 (more than 30 minutes after
      its step 2).
   5. UAT-12 parts C and D last, because they restart the backend, and part D needs an empty database.

### Things a tester must know

- **One browser holds one signed-in account.** The session cookie belongs to `localhost`, so every tab and window of
  one browser profile shares it. A step that needs two people signed in at once, such as an administrator and a
  user, says **Window A** and **Window B**: use a normal window and a private (incognito) window, or two different
  browsers. Private windows share one cookie jar among themselves.
- **One session per account.** Signing in as the same account somewhere else signs the first session out.
- **Uniform messages are the point.** Sign-in never says *why* it failed. A locked, disabled, deleted or unknown
  account shows the same "The username or password is not correct." as a wrong password.
- **Idle timeout.** A session ends after 15 minutes without a request. An administrator window left open during a
  wait is signed out, so sign in again when a script says "Window A is signed in as the administrator".
- **Rate limits.** Everything local comes from one address. Registration allows 5 attempts at once and then one
  every 12 seconds. Reset requests for one email address allow 3, then one more every 20 minutes. If you see
  "Too many attempts…", wait a few seconds and repeat the step.
- **Finding an account's `user.id`.** Audit rows name accounts by id, never by username. An account's id is the last
  part of its admin page's address, `/admin/users/<id>`. It is also in the `Login succeeded.` row of its last
  sign-in.
- **Step-up codes.** Administrator changes need an authenticator code from the last 10 minutes. If it is older, a
  **TOTP Verification** dialog asks for a new code. Enter it, choose **Verify**, and the change completes. This is
  expected. For `demo-admin`, read the code from a second tab of the same window left open on
  http://localhost:5173/sign-in: its **Demo accounts** panel shows the current code even while you are signed in. A
  code can be used once only, so if one is refused just after you used it, wait for the next.
- **Waiting.** The lockout lasts **20 minutes** and a reset link **30 minutes**. There is no dev-only clock shortcut.
  The only shortcut for a lock is an administrator's **Unlock account**, which UAT-03 lists as an optional step but
  which does not prove the cooldown.
- **Console snippets.** Four checks send a request the UI never sends, so that the server's own refusal can be seen.
  Open the browser's developer tools on the `http://localhost:5173` tab (**F12**, then **Console**) and paste the
  snippet. Chrome asks you to type `allow pasting` before the first paste. The snippets use `http://localhost:8080`,
  so change it if your API runs elsewhere.
- **Audit log.** `backend/logs/audit.ndjson` holds one JSON line per security event. Search it with
  `Select-String -Path backend\logs\audit.ndjson -Pattern '<text>'` (bash: `grep '<text>' backend/logs/audit.ndjson`).
  A few high-volume rows, `Password reset requested.` and `Request throttled for its source.` among them, are
  collapsed per source. They are written once, with an `event.count`, when their 15-minute window closes, up to
  about 16 minutes later. Do not expect them at once.

### Test data

Each script creates the accounts it needs, following [Appendix A](#appendix-a-register-and-activate-an-account).
Every password below passes the password policy. A password that contains part of its own username or email is
refused, which is why each account has its own.

| Account | Email | Password | Used in |
|---|---|---|---|
| `demo-admin` | `demo-admin@demo.invalid` (seeded, dev only) | `granite-falcon-ember-quarry`, and the code from the sign-in page's demo panel | UAT-08 to UAT-12 |
| `demo-user` | `demo-user@demo.invalid` (seeded, dev only) | `violet-harbour-signal-meadow` | UAT-12 |
| `uat-ivy` | `ivy@example.test` (invited as an administrator) | `tulip-anchor-velvet-comet`, then `juniper-cobalt-rain-lantern` | UAT-12 |
| `uat-alice` | `alice@example.test` | `copper-willow-evening-drum` | UAT-01, 04, 05, 08 |
| `uat-bob` | `bob@example.test` | `saffron-glacier-tuesday-kite` | UAT-02, 03 |
| `uat-carol` | `carol@example.test` | `maple-orbit-canvas-thunder` | UAT-03 |
| `uat-dave` | `dave@example.test` | `pebble-lighthouse-mango-rain` | UAT-07 part B |
| `uat-erin` | `erin@example.test` | `violet-pumpkin-harbor-spoon`, reset to `orchid-thunder-basket-lemon` | UAT-06, 07 |
| `uat-frank` | `frank@example.test` | `walnut-comet-silver-parade` | UAT-09 |
| `uat-grace` | `grace@example.test` | `harbor-quartz-melody-fern` | UAT-10 |
| `uat-henry` | `henry@example.test` | `ember-lagoon-pixel-orchard` | UAT-11 |

### Signing in as the administrator

`demo-admin` is seeded already enrolled, and its password never has to change. Go to
http://localhost:5173/sign-in. In the **Demo accounts** panel, choose **Fill in demo-admin** (or type `demo-admin` /
`granite-falcon-ember-quarry`) and note the code shown beside **Code**, then choose **Sign in**. The **TOTP
Verification** page opens. Enter the code and choose **Verify**. The **Users** list opens. If the code has changed
since you noted it, a second tab on the sign-in page shows the current one.

---

## UAT-01 Register an account

> **Story 1:** As a **visitor**, I want to register an account with a username, email, and password, so that I can
> log in and access the protected app.

**Acceptance criteria** (PRD, Story 1):

1. Given a visitor submits a unique username, unique email, and a password meeting the minimum strength policy
   (length ≥ 12), when they submit registration, then an account is created with role `USER`, `enabled = true`, and
   the password stored as a BCrypt hash.
2. Given a visitor submits a username or email that's already registered, when they submit registration, then the
   request is rejected with a clear validation error (username/email conflict), and no account is created.
3. Given a visitor submits a password that fails the strength policy, when they submit registration, then the request
   is rejected with a validation error and no account is created.
4. Given any registration attempt, when handled, then the plaintext password is never logged or stored.

**Deviations:**

- Registration is two-step. The form takes a username and email only, and the password is chosen on the
  activation page reached from the emailed link. The account is enabled when the password is set (R-CRED-009,
  [ADR-032](../adr/0032-two-step-enumeration-resistant-registration.md)).
- The minimum length is 15, not 12, and the policy also rejects breached, guessable and context-derived passwords
  ([ADR-002](../adr/0002-fifteen-character-password-minimum.md),
  [ADR-005](../adr/0005-zxcvbn-gate-replaces-composition-rules.md)).
- A taken **username** gets a clear error. A taken **email** gets the same "Check your email" answer as a new one,
  and no account or link is created. Saying "email already registered" would let anyone test which addresses have
  accounts (R-CRED-018).

**Preconditions:** the app is running (Setup), UAT-12 part A is done, and nobody is signed in in Window A.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Window A: open http://localhost:5173/sign-in and choose **Register**. | The **Register** form shows **Username** and **Email address**, and no password field. | AC1 | ☐ |
| 2 | Enter username `Alice UAT` and email `alice@example.test`, then choose **Register**. | Under the username: "Use 3 to 32 characters: lowercase letters, digits, dots, hyphens and underscores. No @ or spaces." Nothing is sent. | AC1 | ☐ |
| 3 | Change the username to `uat-alice` and choose **Register**. | **Check your email**: "If alice@example.test can be registered, an activation link is on its way to it. Open the link within 24 hours to set your password." | AC1 | ☐ |
| 4 | In the backend console, find the newest `Dev-only ACTIVATION link` line. | The link has the form `http://localhost:5173/activate#token=…`. | AC1 | ☐ |
| 5 | Open the link. Under **Choose a password**, enter `short-pass` and choose **Activate**. | "Use at least 15 characters. A few unrelated words make a good passphrase." | AC3 | ☐ |
| 6 | Enter `passwordpassword` and choose **Activate**. | "This password appears in known breaches or on our list of banned passwords. Choose another." The meter shows **Very weak**. | AC3 | ☐ |
| 7 | Enter `uat-alice-secret-password` and choose **Activate**. | "Do not use your username, your email address or the name of this service in your password." | AC3 | ☐ |
| 8 | Enter `copper-willow-evening-drum` and choose **Activate**. | **Account activated**: "Your password is set. You can now sign in." | AC1 | ☐ |
| 9 | Open the same activation link again, enter `another-lovely-harbour-tune` and choose **Activate**. | "This activation link is not valid. It may have expired, been used already or been replaced by a newer one…" | AC1 | ☐ |
| 10 | Go to http://localhost:5173/sign-in and sign in as `uat-alice` / `copper-willow-evening-drum`. | The page shows **Hello, uat-alice**. Choose **Sign out**. | AC1 | ☐ |
| 11 | Register again with username `uat-alice` and email `someone-else@example.test`. | Under the username: "That username is not available. Choose another." | AC2 | ☐ |
| 12 | Register with username `uat-alice2` and the taken email `alice@example.test`. | The same **Check your email** answer as step 3. The backend console shows **no** new `Dev-only ACTIVATION link` line. | AC2 | ☐ |
| 13 | Sign in as the administrator ([how](#signing-in-as-the-administrator)) and look at the **Users** list. | `uat-alice` is listed with `alice@example.test`, role `USER`, status `Enabled` and today's date in UTC (before 08:00 in Singapore that is yesterday). No `uat-alice2` account exists. Sign out. | AC1, AC2 | ☐ |
| 14 | Search the backend console and `backend/logs/audit.ndjson` for `copper-willow-evening-drum`, `passwordpassword` and `short-pass`. | No match anywhere. | AC4 | ☐ |
| 15 | The BCrypt check needs the backend stopped, so it is done in UAT-12 part C, step 2. | See UAT-12. | AC1, AC4 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-02 Log in

> **Story 2:** As a **registered user**, I want to log in with my username and password, so that I can access my
> session and the protected app content.

**Acceptance criteria** (PRD, Story 2):

1. Given a registered, enabled, non-locked account with correct credentials, when the user submits login, then a
   server-side session is created, a secure session cookie is set, and `failed_login_attempts` resets to 0.
2. Given incorrect credentials, when the user submits login, then the request is rejected with a generic error
   message that does not reveal whether the username exists, and `failed_login_attempts` increments.
3. Given an account that is currently locked (`locked_until` in the future), when the user submits login with
   correct credentials, then the request is still rejected until the lockout expires.

**Deviations:** in dev the cookie is named `SESSION` and is not `Secure`, because local dev runs over plain HTTP,
as the PRD accepts. Outside dev it is `__Host-SESSION` and `Secure`
([ADR-058](../adr/0058-samesite-strict-not-lax.md)). The failure counter is not shown anywhere, so steps 7–9 prove
the reset and the increment through the lockout threshold of 5.

**Preconditions:** `uat-bob` / `bob@example.test` / `saffron-glacier-tuesday-kite` exists
([Appendix A](#appendix-a-register-and-activate-an-account)). Nobody is signed in.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Open http://localhost:5173/sign-in and sign in as `uat-bob` / `saffron-glacier-tuesday-kite`. | The page shows **Hello, uat-bob**. | AC1 | ☐ |
| 2 | Open developer tools, then **Application** (Chrome or Edge) or **Storage** (Firefox), then **Cookies** → `http://localhost:5173`. | A `SESSION` cookie with **HttpOnly** ✓, **SameSite** `Strict`, and expiry **Session**. Choose **Sign out**. | AC1 | ☐ |
| 3 | Sign in as `uat-bob` with password `not-the-right-password-1`. | "The username or password is not correct." The password field is cleared. | AC2 | ☐ |
| 4 | Sign in as `uat-nobody` (no such account) with password `not-the-right-password-1`. | Exactly the same message as step 3. | AC2 | ☐ |
| 5 | Optional, same check at the API: in developer tools, open **Network**, repeat steps 3 and 4, and compare the two `login` responses. | Both are `401` with the same body: `code` `AUTHENTICATION_FAILED` and `detail` "Authentication is required, or the credentials were not accepted." Only `traceId` differs. | AC2 | ☐ |
| 6 | Sign in as `uat-bob` with the correct password. | **Hello, uat-bob**. The one or two failures so far did not lock the account. This success resets its counter. Choose **Sign out**. | AC1 | ☐ |
| 7 | Sign in as `uat-bob` with 4 different wrong passwords (`wrong-again-1` to `wrong-again-4`). | The generic message each time. | AC2 | ☐ |
| 8 | Sign in as `uat-bob` with the correct password. | **Hello, uat-bob**. Had step 6 not reset the counter, step 7 would have brought it to 5 or more failures within 20 minutes, and the account would now be locked. Sign out. | AC1, AC2 | ☐ |
| 9 | Search `backend/logs/audit.ndjson` for `Login failed.` and `Login succeeded.` | Rows of both, with `source.ip_hash` and no password. The rows for `uat-bob` carry its `user.id`. The row for the unknown `uat-nobody` has none. | AC1, AC2 | ☐ |
| 10 | The locked case is UAT-03 steps 1–5. | See UAT-03. | AC3 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-03 Lockout and throttling

> **Story 3:** As a **security-conscious operator**, I want repeated failed logins to trigger account lockout and
> IP-level throttling, so that brute-force credential guessing is blunted.

**Acceptance criteria** (PRD, Story 3):

1. Given N consecutive failed login attempts against one account within a window (e.g. 5 attempts), when the Nth
   failure occurs, then the account is locked for a cooldown period (e.g. 15 minutes) by setting `locked_until`.
2. Given a locked account, when the cooldown period elapses and the correct password is submitted, then login
   succeeds and `failed_login_attempts` resets.
3. Given repeated failed login attempts from one IP address across multiple usernames, when a threshold is exceeded,
   then further attempts from that IP are throttled independently of any single account's lockout state — so an
   attacker cannot lock out a legitimate user merely by failing that user's password from one source.

**Deviations:**

- N is 5 within 20 minutes, and the first cooldown is **20 minutes**. Repeated locks escalate to 40 and then 60
  minutes ([ADR-011](../adr/0011-escalating-lockout-ladder.md)).
- The lock is never announced. A locked account gets the same message as a wrong password
  ([ADR-033](../adr/0033-password-lockout-never-on-the-wire.md)).
- The per-source throttle (60 sign-ins at once, then 1 per second) is independent of lockout, as AC3 asks. The PRD's
  last clause cannot fully hold while per-account lockout exists, because five failures from anywhere still lock an
  account. This is a recorded, accepted partial (R-LCK-002, [`prd-coverage.md`](../prd-coverage.md)).

**Preconditions:** `uat-carol` / `carol@example.test` / `maple-orbit-canvas-thunder` exists. `uat-bob` from UAT-02
is needed from step 10 onwards. Nobody is signed in. Note the time at step 5, because step 6 comes 20 minutes
later.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Sign in as `uat-carol` with `wrong-guess-number-1`. | "The username or password is not correct." | AC1 | ☐ |
| 2 | Repeat with `wrong-guess-number-2`, `-3`, `-4` and `-5`, all within 20 minutes. | The same message each time. | AC1 | ☐ |
| 3 | Search `backend/logs/audit.ndjson` for `Account locked.` | One row with `uat-carol`'s `user.id`, at `WARN`. | AC1 | ☐ |
| 4 | Sign in as `uat-carol` with the **correct** password. | Still "The username or password is not correct.", and you stay on **Sign in**. | AC1; Story 2 AC3 | ☐ |
| 5 | Note the time. Carry on with other scripts. | | AC2 | ☐ |
| 6 | At least 20 minutes after step 2, sign in as `uat-carol` with the correct password. | **Hello, uat-carol**. Sign out. | AC2 | ☐ |
| 7 | Sign in as `uat-carol` with 4 different wrong passwords, then with the correct password. | The generic message 4 times, then **Hello, uat-carol**. The counter was reset at step 6, so 4 failures do not lock. Sign out. | AC2 | ☐ |
| 8 | Optional, admin shortcut, which does not prove AC2: lock `uat-carol` again (steps 1–2). As the administrator, open `uat-carol` on the **Users** page, leave the reason as the default and choose **Unlock account**. Then sign in as `uat-carol`. | "Account unlocked. Its password lockout and factor lock are cleared." Then **Hello, uat-carol**. | — | ☐ |
| 9 | Signed out, on the `http://localhost:5173/sign-in` tab, paste the **throttle snippet** below into the developer tools console. It sends 100 sign-ins at once, each for a different, non-existent username. | The console prints a tally such as `{401: 67, 429: 33}`: about the first 60 get `401`, and the rest get `429 Too Many Requests`. | AC3 | ☐ |
| 10 | Within a few seconds, sign in as `uat-bob` with the correct password. | **Hello, uat-bob**. The throttle refills at one sign-in per second and lifts on its own, and it does not touch `uat-bob`'s account, which signs in normally. If you are quick enough to see "Too many attempts. Wait a moment, then try again.", wait a few seconds and retry. | AC3 | ☐ |
| 11 | Up to about 16 minutes later, search the audit file for `Request throttled for its source.` | A row with reason `RATE_LIMITED_SOURCE` and an `event.count`. | AC3 | ☐ |

Throttle snippet (step 9):

```js
const api = 'http://localhost:8080'
const { token } = await (await fetch(api + '/api/csrf', { credentials: 'include' })).json()
const tally = {}
await Promise.all(Array.from({ length: 100 }, (_, i) => fetch(api + '/api/login', {
  method: 'POST', credentials: 'include',
  headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
  body: JSON.stringify({ username: 'uat-nobody-' + i, password: 'not-a-real-password' }),
}).then((r) => { tally[r.status] = (tally[r.status] || 0) + 1 })))
console.log(tally)
```

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-04 Log out

> **Story 4:** As a **logged-in user**, I want to log out, so that my session is fully ended and cannot be reused.

**Acceptance criteria** (PRD, Story 4):

1. Given an active session, when the user calls logout, then the server-side session is invalidated and the session
   cookie is cleared.
2. Given a session cookie captured before logout, when it is replayed after logout, then the server rejects it as
   unauthenticated.

**Preconditions:** `uat-alice` exists (UAT-01). `curl.exe` is available (Setup, step 1).

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Sign in as `uat-alice` / `copper-willow-evening-drum`. | **Hello, uat-alice**. | AC1 | ☐ |
| 2 | In developer tools → **Application** → **Cookies** → `http://localhost:5173`, copy the value of the `SESSION` cookie. | A long Base64 value. | AC2 | ☐ |
| 3 | In a terminal, run `curl.exe -i http://localhost:8080/api/hello -H "Cookie: SESSION=<value>"`. | `HTTP/1.1 200` with `{"message":"Hello, uat-alice"}`, which shows the cookie is live. | AC2 | ☐ |
| 4 | In the browser, choose **Sign out**. | The **Sign in** page opens. In developer tools, the `SESSION` cookie is gone. | AC1 | ☐ |
| 5 | Run the same `curl.exe` command again with the copied value. | `HTTP/1.1 401` with `"code":"AUTHENTICATION_FAILED"`. The replayed cookie is rejected. | AC1, AC2 | ☐ |
| 6 | In the browser, open http://localhost:8080/api/hello. | A `401` JSON body with `"code":"AUTHENTICATION_FAILED"`. | AC1 | ☐ |
| 7 | Open http://localhost:5173/hello, then press the browser's **Back** button. | Both land on **Sign in**, and no greeting is shown. | AC1 | ☐ |
| 8 | Search `backend/logs/audit.ndjson` for `Logout succeeded.` | A row with `uat-alice`'s `user.id`. | AC1 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-05 Personalized greeting

> **Story 5:** As a **logged-in user**, I want to see a personalized greeting, so that I can confirm my
> authentication actually worked.

**Acceptance criteria** (PRD, Story 5):

1. Given an authenticated session, when the user requests `GET /api/hello`, then the response is
   `"Hello, <username>"`.
2. Given no session (or an invalid/expired one), when a request is made to `GET /api/hello`, then the response is
   unauthorized (401).

**Deviations:** the greeting is a JSON object, `{"message":"Hello, <username>"}`, not a bare string.
`GET /api/hello` is for the `USER` role. An administrator gets `403`, and the SPA does not send an administrator to
the greeting page.

**Preconditions:** `uat-alice` exists. Start signed out.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Signed out, open http://localhost:8080/api/hello. | `401` with a JSON body whose `code` is `AUTHENTICATION_FAILED`. | AC2 | ☐ |
| 2 | Open http://localhost:5173/hello. | The app goes to **Sign in**. | AC2 | ☐ |
| 3 | Sign in as `uat-alice` / `copper-willow-evening-drum`. | The page heading is **Hello, uat-alice**. | AC1 | ☐ |
| 4 | In a new tab of the same window, open http://localhost:8080/api/hello. | `200` with `{"message":"Hello, uat-alice"}`. | AC1 | ☐ |
| 5 | In a terminal, run `curl.exe -i http://localhost:8080/api/hello -H "Cookie: SESSION=bm90LWEtcmVhbC1zZXNzaW9u"` (an invalid session). | `401` and `"code":"AUTHENTICATION_FAILED"`. | AC2 | ☐ |
| 6 | Optional, expiry: stay signed in with no activity for more than 15 minutes, then reload http://localhost:5173/hello. | **Sign in** opens, because the idle timeout ended the session. | AC2 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-06 Request a password reset

> **Story 6:** As a **user who forgot their password**, I want to request a password reset via my registered email,
> so that I can regain access without contacting an admin.

**Acceptance criteria** (PRD, Story 6):

1. Given a request with an email address, when submitted to the password-reset-request endpoint, then the response
   is a generic success message regardless of whether the email is registered — so account existence cannot be
   inferred.
2. Given the email matches a registered user, when the request is processed, then a single-use reset token is
   generated, its hash (not the plaintext token) is stored with a short expiry (15–30 min), and
   `EmailService.sendPasswordResetEmail(...)` is called (stub implementation logs the link instead of sending mail).

**Deviations:** the stub is `EmailService.send(...)`, implemented by `DevLinkLogger`, rather than a method named
`sendPasswordResetEmail`. It logs the link only under the `dev` profile. Outside dev, self-service reset delivers nothing,
and recovery is an administrator-issued link (R-CRED-021, [ADR-057](../adr/0057-dev-only-reset-link-logger.md)).
The token lasts 30 minutes.

**Preconditions:** `uat-erin` / `erin@example.test` / `violet-pumpkin-harbor-spoon` exists. Signed out.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | On http://localhost:5173/sign-in choose **Forgot password?** | **Forgot your password?** with an **Email address** field. | AC1 | ☐ |
| 2 | Enter `nobody-here@example.test` and choose **Send reset link**. | **Check your email**: "If nobody-here@example.test belongs to an account, a reset link is on its way to it. Open the link within 30 minutes to choose a new password." | AC1 | ☐ |
| 3 | Check the backend console. | **No** new `Dev-only PASSWORD_RESET link` line. | AC1 | ☐ |
| 4 | Go to **Forgot password?** again, enter `erin@example.test` and choose **Send reset link**. | The same message as step 2, with this address. Nothing on screen tells steps 2 and 4 apart. | AC1 | ☐ |
| 5 | Check the backend console. | A new line: `Dev-only PASSWORD_RESET link (no mail is sent): http://localhost:5173/reset#token=…`. Keep the link for UAT-07. | AC2 | ☐ |
| 6 | Search `backend/logs/audit.ndjson` for the token part of the link, which is everything after `#token=`. | No match. The token is not written to the audit log. | AC2 | ☐ |
| 7 | Up to about 16 minutes after step 2, search the audit file for `Password reset requested.` | A row with an `event.count` covering the requests from this source, and no email address or `user.id`. | AC1 | ☐ |
| 8 | The check that only the token's hash is stored needs the backend stopped, so it is UAT-12 part C, step 2. Single use is UAT-07 step 8, and the expiry is UAT-07 part B. | See those steps. | AC2 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-07 Reset the password with a token

> **Story 7:** As a **user with a valid reset token**, I want to set a new password, so that I can regain access to my
> account.

**Acceptance criteria** (PRD, Story 7):

1. Given a valid, unexpired, unused reset token and a new password meeting the strength policy, when submitted to the
   password-reset-confirm endpoint, then the password is updated, the token is marked used, and all existing
   sessions for that user are invalidated.
2. Given an expired token, when submitted, then the request is rejected and the password is not changed.
3. Given a token that has already been used once, when submitted again, then the request is rejected (single-use
   enforcement).

**Preconditions:** the reset link for `uat-erin` from UAT-06 step 5, less than 30 minutes old. For part B,
`uat-dave` / `dave@example.test` / `pebble-lighthouse-mango-rain` exists.

### Part A: valid and reused token

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | **Window A:** sign in as `uat-erin` / `violet-pumpkin-harbor-spoon`. | **Hello, uat-erin**. Leave the window open. | AC1 | ☐ |
| 2 | **Window B** (private window or other browser): open erin's reset link. | **Reset your password**, with **Choose a password**. | AC1 | ☐ |
| 3 | Enter `password1234567` and choose **Reset password**. | Refused: "This password appears in known breaches or on our list of banned passwords. Choose another." | AC1 | ☐ |
| 4 | Enter `orchid-thunder-basket-lemon` and choose **Reset password**. | **Password reset**: "Your password is set. You can now sign in." | AC1 | ☐ |
| 5 | **Window A:** reload the page. | **Sign in** opens, because erin's existing session was ended. | AC1 | ☐ |
| 6 | Sign in as `uat-erin` with the old password `violet-pumpkin-harbor-spoon`. | "The username or password is not correct." | AC1 | ☐ |
| 7 | Sign in as `uat-erin` with `orchid-thunder-basket-lemon`. | **Hello, uat-erin**. Sign out. | AC1 | ☐ |
| 8 | **Window B:** open the same reset link again, enter `another-fresh-harbour-tune` and choose **Reset password**. | "This reset link is not valid. It may have expired, been used already or been replaced by a newer one. Request a new link." | AC3 | ☐ |
| 9 | Search `backend/logs/audit.ndjson` for `Password reset completed.` | A row with erin's `user.id`. | AC1 | ☐ |

### Part B: expired token

Start steps 1–3 early, because the link must be more than 30 minutes old at step 4.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Signed out, request a reset for `dave@example.test` (**Forgot password?**). | The generic **Check your email** message. | AC2 | ☐ |
| 2 | Copy the new `Dev-only PASSWORD_RESET link` from the backend console. Do not open it yet. | A `/reset#token=…` link. | AC2 | ☐ |
| 3 | Note the time, and wait **more than 30 minutes**. | | AC2 | ☐ |
| 4 | Open the link, enter `fresh-kiwi-anchor-meadow` and choose **Reset password**. | "This reset link is not valid. It may have expired…" | AC2 | ☐ |
| 5 | Sign in as `uat-dave` with `fresh-kiwi-anchor-meadow`. | "The username or password is not correct." The password was not changed. | AC2 | ☐ |
| 6 | Sign in as `uat-dave` with `pebble-lighthouse-mango-rain`. | **Hello, uat-dave**. Sign out. | AC2 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-08 Admin lists users

> **Story 8:** As an **admin**, I want to see a list of all registered users, so that I can review who has access to
> the system.

**Acceptance criteria** (PRD, Story 8):

1. Given an authenticated admin, when they call `GET /api/admin/users`, then the response lists each user's username,
   email, role, enabled status, and created-at date — never password hashes.
2. Given an authenticated non-admin user, when they call `GET /api/admin/users`, then the response is forbidden
   (403).

**Deviations:** "authenticated admin" means password **and** a verified TOTP code
([ADR-021](../adr/0021-session-scoped-factor-authority.md)). The list also carries each account's `id` and
`activated` flag.

**Preconditions:** UAT-12 part A done, and `uat-alice` exists.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | **Window A:** sign in as the administrator ([how](#signing-in-as-the-administrator)). | The **Users** page. | AC1 | ☐ |
| 2 | Look at the table. | Columns **Username**, **Email**, **Role**, **Status**, **Created**, with one row per account, for example `uat-alice` · `alice@example.test` · `USER` · `Enabled` · today's date in UTC. No password column. | AC1 | ☐ |
| 3 | In a new tab of Window A, open http://localhost:8080/api/admin/users. | `200` with a JSON array. Each item has only `id`, `username`, `email`, `role`, `enabled`, `activated` and `createdAt`. Nothing contains `password`, `hash` or `$2a$`. | AC1 | ☐ |
| 4 | **Window B:** sign in as `uat-alice` / `copper-willow-evening-drum`, then open http://localhost:5173/admin/users. | The app sends alice back to **Hello, uat-alice**. The admin pages never render. | AC2 | ☐ |
| 5 | In a new tab of Window B, open http://localhost:8080/api/admin/users. | `403` with `"code":"ACCESS_DENIED"`. | AC2 | ☐ |
| 6 | Search `backend/logs/audit.ndjson` for `Administrator listed users.` | Rows with the admin's `user.id` and `user.target.count`. | AC1 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-09 Admin enables or disables an account

> **Story 9:** As an **admin**, I want to enable or disable another user's account, so that I can suspend access
> without deleting their data.

**Acceptance criteria** (PRD, Story 9):

1. Given an admin targets another user's account, when they call the status-toggle endpoint, then the account's
   `enabled` flag is updated accordingly, and a disabled user can no longer log in.
2. Given an admin targets their own account via the status-toggle endpoint, when the request is made, then it is
   rejected — an admin cannot disable themselves.

**Deviations:** a disable also ends the user's sessions at once. Re-enabling issues a forced-change credential, so
the user must change their password at the next sign-in
([ADR-046](../adr/0046-lazy-forced-change-expiry-pre-authentication.md)). The UI does not offer self-actions, so the
server's refusal is checked with a console snippet. Disabling one of exactly two enrolled administrators is also
refused ([ADR-048](../adr/0048-two-admin-invariant-guarded-and-monitored.md)). That rule does not apply here: the
target is a user.

**Preconditions:** `uat-frank` / `frank@example.test` / `walnut-comet-silver-parade` exists. Window A is signed in as
the administrator.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | **Window B:** sign in as `uat-frank`. | **Hello, uat-frank**. Leave it open. | AC1 | ☐ |
| 2 | **Window A:** on **Users**, choose `uat-frank`, then **Disable account**. If a code is asked for, enter it. | "Account disabled. The user has been signed out." **Status** shows `Disabled`. | AC1 | ☐ |
| 3 | **Window B:** reload the page. | **Sign in** opens. | AC1 | ☐ |
| 4 | **Window B:** sign in as `uat-frank` with the correct password. | "The username or password is not correct." A disabled user cannot sign in. | AC1 | ☐ |
| 5 | **Window A:** choose **Enable account**. | "Account enabled. The user must change their password when they next sign in." **Status** shows `Enabled`, and the **Users** list agrees. | AC1 | ☐ |
| 6 | **Window B:** sign in as `uat-frank` with the correct password. | **Change password** opens, with "You must choose a new password before you can continue." The account works again. Choose **Sign out**. | AC1 | ☐ |
| 7 | **Window A:** go back to **Users** and choose `demo-admin` (your own account). | "You cannot change your own account." No **Disable account**, **Make user** or **Delete account** button. | AC2 | ☐ |
| 8 | **Window A:** within 10 minutes of your last code, on a `http://localhost:5173` tab, paste the **self-action snippet** below into the console. If it prints `412 MISSING_FACTOR`, your code is older than 10 minutes: make any change in the UI (for example, disable and re-enable `uat-frank`), enter the code, and rerun. | The first line is `PUT /enabled -> 403 ACCESS_DENIED`. | AC2 | ☐ |
| 9 | Reload `demo-admin`'s page. | **Role** `ADMIN`, **Status** `Enabled`: nothing changed. | AC2 | ☐ |
| 10 | Search `backend/logs/audit.ndjson` for `Account disabled.`, `Account enabled.` and `Administrative action refused.` | One of each at least, naming the actor (`user.id`) and target (`user.target.id`). The refusal row has reason `SELF_ACTION`. | AC1, AC2 | ☐ |

Self-action snippet (steps 9.8, 10.8 and 11.7). It sends your own account's disable, demote and delete requests:

```js
const api = 'http://localhost:8080'
const me = await (await fetch(api + '/api/profile', { credentials: 'include' })).json()
const { token } = await (await fetch(api + '/api/csrf', { credentials: 'include' })).json()
const headers = { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token }
for (const [method, path, body] of [['PUT', '/enabled', { enabled: false }], ['PUT', '/role', { role: 'USER' }], ['DELETE', '', null]]) {
  const r = await fetch(api + '/api/admin/users/' + me.id + path, { method, credentials: 'include', headers, body: body && JSON.stringify(body) })
  console.log(method + ' ' + (path || '(account)') + ' -> ' + r.status + ' ' + (await r.json()).code)
}
```

Expected output:

```text
PUT /enabled -> 403 ACCESS_DENIED
PUT /role -> 403 ACCESS_DENIED
DELETE (account) -> 403 ACCESS_DENIED
```

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-10 Admin changes a role

> **Story 10:** As an **admin**, I want to change another user's role between USER and ADMIN, so that I can grant or
> revoke admin privileges.

**Acceptance criteria** (PRD, Story 10):

1. Given an admin targets another user's account, when they call the role-change endpoint with a valid role, then the
   account's role is updated.
2. Given an admin targets their own account via the role-change endpoint, when the request is made, then it is
   rejected — an admin cannot demote themselves.

**Deviations:** a role change ends the target's sessions. A new administrator must enrol an authenticator before the
admin pages open. Demoting one of exactly two enrolled administrators is refused. That rule does not apply here: the
promoted account never enrols ([ADR-048](../adr/0048-two-admin-invariant-guarded-and-monitored.md)).

**Preconditions:** `uat-grace` / `grace@example.test` / `harbor-quartz-melody-fern` exists. Window A is signed in as
the administrator.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | **Window B:** sign in as `uat-grace`. | **Hello, uat-grace**. | AC1 | ☐ |
| 2 | **Window A:** open `uat-grace` and choose **Make administrator**. | A dialog: "Make uat-grace an administrator?" | AC1 | ☐ |
| 3 | Choose **Change role**, and enter a code if asked. | "Role changed to administrator. The user has been signed out." **Role** shows `ADMIN`. | AC1 | ☐ |
| 4 | **Window B:** reload, then sign in as `uat-grace` again. | The reload opens **Sign in**. After signing in, grace lands on **Two-factor authentication** (enrolment), because she is now an administrator. Do not enrol. | AC1 | ☐ |
| 5 | **Window A:** choose **Make user**, then **Change role**. | "Role changed to user. The user has been signed out." **Role** shows `USER`. | AC1 | ☐ |
| 6 | **Window B:** sign in as `uat-grace`. | **Hello, uat-grace**. She is a user again. Sign out. | AC1 | ☐ |
| 7 | **Window A:** open `demo-admin`. | "You cannot change your own account." No **Make user** button. | AC2 | ☐ |
| 8 | Run the self-action snippet from UAT-09 (or read its output if you have just run it). | The second line is `PUT /role -> 403 ACCESS_DENIED`. `demo-admin` is still `ADMIN`. | AC2 | ☐ |
| 9 | Search the audit file for `Account role changed to administrator.` and `Account role changed to user.` | Both rows, with actor and target. | AC1 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-11 Admin deletes an account

> **Story 11:** As an **admin**, I want to delete another user's account, so that I can remove accounts that should no
> longer exist.

**Acceptance criteria** (PRD, Story 11):

1. Given an admin targets another user's account, when they call the delete endpoint, then the account is removed.
2. Given an admin targets their own account via the delete endpoint, when the request is made, then it is rejected —
   an admin cannot delete themselves.

**Deviations:** deletion leaves a tombstone, so the username and email can never be registered again
([ADR-044](../adr/0044-deletion-leaves-a-tombstone.md)).

**Preconditions:** `uat-henry` / `henry@example.test` / `ember-lagoon-pixel-orchard` exists. Window A is signed in as
the administrator.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | **Window A:** open `uat-henry` and choose **Delete account**. | A dialog: "Delete uat-henry?", saying the action cannot be undone. | AC1 | ☐ |
| 2 | Choose **Delete**, and enter a code if asked. | "Account deleted. The user has been signed out, and the username and email cannot be used again." | AC1 | ☐ |
| 3 | Choose **Back to the user list**. | `uat-henry` is no longer listed. | AC1 | ☐ |
| 4 | **Window B:** sign in as `uat-henry`. | "The username or password is not correct." | AC1 | ☐ |
| 5 | **Window B:** register the username `uat-henry` with `henry2@example.test`. | "That username is not available. Choose another." | AC1 | ☐ |
| 6 | **Window A:** open `demo-admin`. | "You cannot change your own account." No **Delete account** button. | AC2 | ☐ |
| 7 | Run the self-action snippet from UAT-09. | The third line is `DELETE (account) -> 403 ACCESS_DENIED`. `demo-admin` is still listed. | AC2 | ☐ |
| 8 | Search the audit file for `Account deleted.` | A row with actor and target. | AC1 | ☐ |

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## UAT-12 Initial admin bootstrap

> **Story 12:** As an **operator deploying the app for the first time**, I want an initial admin account to be
> created automatically, so that there's a way into the admin module without manual database edits.

**Acceptance criteria** (PRD, Story 12):

1. Given no `ADMIN` user exists in the database, when the application starts, then one is seeded using credentials
   supplied via configuration (e.g. `app.admin.username`, `app.admin.password`), with the password hashed identically
   to any other account.
2. Given an `ADMIN` user already exists, when the application restarts, then no duplicate seed account is created.

**Deviations:** the seed credential comes from the `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` environment
variables, which bind to `app.admin.username` and `app.admin.password`. It is a forced-change credential: it must be
changed at first sign-in and expires 30 days after issue. An administrator must then enrol TOTP
([ADR-047](../adr/0047-bootstrap-refresh-validation-runner-seeding.md),
[ADR-046](../adr/0046-lazy-forced-change-expiry-pre-authentication.md)). Under the `dev` profile the first start
also seeds two **dev-only demo accounts** before the bootstrap runs: `demo-user`, and `demo-admin`, an administrator
already enrolled in TOTP with no forced change. The bootstrap then finds an `ADMIN` and seeds none. Part B shows the
forced change and the enrolment on a newly invited administrator; part D turns the demo accounts off to show the
bootstrap from configuration.

**Preconditions:** part A runs on an **empty** database (no `backend/data/` folder before the first start) with the
demo values from the Setup. Part B needs the authenticator app. Parts C and D run after the other scripts.

### Part A: first start

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Start the backend for the first time (Setup, step 4). Search its console for `Demo accounts` and `Administrator bootstrap`. | `Demo accounts: seeded demo-user`, `Demo accounts: seeded demo-admin, enrolled in TOTP`, then `Administrator bootstrap: an ADMIN account exists, so none is seeded`. | AC1, AC2 | ☐ |
| 2 | Open http://localhost:5173. | **Sign in**, with a **Demo accounts** panel listing `demo-user` and `demo-admin` with their passwords, and a six-digit **Code** for `demo-admin` that changes every 30 seconds. | AC1 | ☐ |
| 3 | Sign in as `demo-admin` with a wrong password, `wrong-password-for-demo`. | "The username or password is not correct." | AC1 | ☐ |
| 4 | Choose **Fill in demo-admin**, note the code, and choose **Sign in**. | **TOTP Verification**: no password change is asked for. | AC1 | ☐ |
| 5 | Enter `000000` and choose **Verify**. | "That code was not accepted." | AC1 | ☐ |
| 6 | Enter the code from the panel and choose **Verify**. If it has changed since you noted it, open http://localhost:5173/sign-in in a new tab and use the one shown there. | **Users** lists `demo-admin` · `demo-admin@demo.invalid` · `ADMIN` · `Enabled`, and `demo-user` · `demo-user@demo.invalid` · `USER` · `Enabled`. | AC1 | ☐ |

### Part B: a new administrator, forced change and enrolment

A forced-change credential and TOTP enrolment, on an administrator created through the invite. Window A is signed in
as `demo-admin`.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | **Window A:** on **Users**, choose **Invite a user**. Enter username `uat-ivy`, email `ivy@example.test`, role **Administrator**, and choose **Create invitation**. Enter a code if asked. | **Invitation for uat-ivy**, with a link shown once. | AC1 | ☐ |
| 2 | **Window B:** open the link. Enter `tulip-anchor-velvet-comet` under **Choose a password** and choose **Activate**. | "Your password is set. You can now sign in." | AC1 | ☐ |
| 3 | **Window A:** choose **Back to the user list**, open `uat-ivy`, choose **Disable account**, then **Enable account**. Enter a code if asked. | "Account enabled. The user must change their password when they next sign in." A re-enable issues a forced-change credential. | AC1 | ☐ |
| 4 | **Window B:** sign in as `uat-ivy` / `tulip-anchor-velvet-comet`. | **Change password**, with "You must choose a new password before you can continue." | AC1 | ☐ |
| 5 | Open http://localhost:5173/admin/users. | The app returns to **Change password**. Nothing else is reachable until the change. | AC1 | ☐ |
| 6 | Enter current password `tulip-anchor-velvet-comet` and new password `uat-ivy-password-2026`, then choose **Change password**. | The server refuses it: "Do not use your username, your email address or the name of this service in your password." | AC1 | ☐ |
| 7 | Enter new password `juniper-cobalt-rain-lantern` and choose **Change password**. | **Two-factor authentication**: "Administrators must use an authenticator app…" | AC1 | ☐ |
| 8 | Choose **Generate QR code**. Scan the QR code, or type the key shown (time-based, 6 digits, 30 seconds), into your authenticator app. | A QR code and a key of 32 letters and digits, in groups. | AC1 | ☐ |
| 9 | Enter `000000` and choose **Confirm**. | "That code was not accepted. Check the code in your authenticator app and try again." | AC1 | ☐ |
| 10 | Enter the app's current code and choose **Confirm**. | "Your authenticator app is set up…" | AC1 | ☐ |
| 11 | Choose **Continue to the user list**. | **Users** lists `uat-ivy` · `ivy@example.test` · `ADMIN` · `Enabled`. Choose **Sign out**. | AC1 | ☐ |

### Part C: restart, and the storage checks

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | Stop the backend with **Ctrl+C** in terminal 1. | The process ends. | AC2 | ☐ |
| 2 | In terminal 1, still in the repository root, run the storage check below. | `BCrypt cost-12 hashes found:` a number of at least 10, and `False` on every other line. | AC1; Story 1 AC1, AC4; Story 6 AC2 | ☐ |
| 3 | In the same terminal, set a **different** seed username, then start the backend again with the same command as before: `$env:APP_ADMIN_USERNAME = "uat-second-admin"` (bash: `export APP_ADMIN_USERNAME=uat-second-admin`). | The backend starts. | AC2 | ☐ |
| 4 | Search the new console output for `Demo accounts` and `Administrator bootstrap`. | `Demo accounts: demo-user exists or is held by a tombstone, so it is left as it is` (the same for `demo-admin`), and `Administrator bootstrap: an ADMIN account exists, so none is seeded`. | AC2 | ☐ |
| 5 | Sign in as the administrator ([how](#signing-in-as-the-administrator)). | The **Users** list has the `ADMIN` accounts `demo-admin` and `uat-ivy` only, and no `uat-second-admin`. | AC2 | ☐ |
| 6 | Sign in as `uat-second-admin` / `lantern-orchard-copper-tide`. | "The username or password is not correct." No such account exists. | AC2 | ☐ |
| 7 | Stop the backend, and set `$env:APP_ADMIN_USERNAME = "demo-admin"` back. | | — | ☐ |

### Part D: the bootstrap from configuration

With the demo accounts turned off, the first start on an empty database seeds the administrator from
`APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD`, as every non-dev deployment does.

| # | Action | Expected result | Covers | P/F |
|---|---|---|---|---|
| 1 | With the backend stopped, rename `backend\data` to `backend\data-uat` (it keeps the database of parts A to C). Run `$env:APP_DEV_DEMOACCOUNTS_ENABLED = "false"` (bash: `export APP_DEV_DEMOACCOUNTS_ENABLED=false`), and start the backend with the same command as before. | The backend starts. Its console has `Administrator bootstrap: seeded user.id=… with a forced-change credential`, and no `Demo accounts` line. | AC1 | ☐ |
| 2 | Open http://localhost:5173. | **Sign in**, with no **Demo accounts** panel. | AC1 | ☐ |
| 3 | Sign in as `demo-admin` / `lantern-orchard-copper-tide`. | **Change password**, with "You must choose a new password before you can continue." The seed holds a forced-change credential. | AC1 | ☐ |
| 4 | Choose **Sign out** and stop the backend. Run `Remove-Item Env:APP_DEV_DEMOACCOUNTS_ENABLED` (bash: `unset APP_DEV_DEMOACCOUNTS_ENABLED`), delete `backend\data`, and rename `backend\data-uat` back to `backend\data` if you want the UAT database again. | | — | ☐ |

Storage check (part C, step 2), in PowerShell from the repository root, with the backend stopped because H2 locks the file
while it runs. It reads every activation and reset token from `backend-console.log` (Setup step 4), so run it before
restarting the backend, which overwrites that file. Add any other password you used to the list:

```powershell
$db = [IO.File]::ReadAllText("$PWD\backend\data\secured-hello.mv.db", [Text.Encoding]::GetEncoding(28591))
"BCrypt cost-12 hashes found: " + ([regex]::Matches($db, '\{bcrypt\}\$2a\$12\$')).Count
foreach ($p in 'granite-falcon-ember-quarry', 'violet-harbour-signal-meadow', 'tulip-anchor-velvet-comet', 'juniper-cobalt-rain-lantern', 'copper-willow-evening-drum', 'saffron-glacier-tuesday-kite', 'orchid-thunder-basket-lemon') {
  "$p stored in plaintext: " + $db.Contains($p)
}
$tokens = Select-String -Path backend-console.log -Pattern '#token=([A-Za-z0-9_-]+)' | ForEach-Object { $_.Matches[0].Groups[1].Value }
"tokens checked: " + @($tokens).Count
"any token stored in plaintext: " + [bool]($tokens | Where-Object { $db.Contains($_) })
```

The `tokens checked` line must show a number above 0 (one per link the backend logged), and its result line `False`.
Every password set during the UAT, the demo accounts' included, went through the same password service, and the
database holds only `{bcrypt}$2a$12$…` hashes of them: BCrypt at cost 12. The count is higher than the number of
accounts because the database file keeps old versions of changed rows and the password history. No password and no
token appears in plaintext. The token table holds only their SHA-256 hashes.

**Result:** ☐ Pass ☐ Fail  Notes: ______________________

---

## Appendix A: register and activate an account

For each test account the scripts need, and at most five in quick succession (then one every 12 seconds):

1. Signed out, open http://localhost:5173/register.
2. Enter the account's **Username** and **Email address** from [Test data](#test-data), and choose **Register**.
   "Check your email" appears.
3. Copy the newest `Dev-only ACTIVATION link` from the backend console (README Setup, step 6), and open it.
4. Enter the account's password under **Choose a password** and choose **Activate**. "Your password is set. You can
   now sign in." appears.
