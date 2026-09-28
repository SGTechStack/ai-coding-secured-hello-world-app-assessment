# 14 — Design the frontend architecture

Type: prototype
Status: resolved
Blocked by: 05, 06, 08, 20, 23

## Question

How is the SPA structured, and how does it behave around auth state, CSRF, and errors — when the
session cookie is HttpOnly and therefore invisible to JavaScript?

This is a prototype ticket rather than pure discussion: the screens and the interceptor behaviour are
far easier to judge from a rough, concrete artifact than from prose. Build cheap mocks, react to
them, then decide. Prototype code is throwaway and does not become the build.

## Settled going in

React 19.3 + Vite + TypeScript strict + shadcn/ui on Base UI + React Hook Form + Zod + TanStack
Query. Base UI for real focus management and ARIA. Separate origin from the API, CORS with
credentials.

## Inherited from ticket 06 — constraints, not open questions

[Decide the API error envelope and the enumeration-safe response contract](06-error-envelope-and-enumeration-contract.md)
fixed the wire contract the interceptor consumes:

- **Every error is RFC 9457 `application/problem+json`. The SPA branches on the `code` extension member, never
  on `type`, never on `detail`, never on status alone.** A closed enum of 14 codes; prose is never a branch key
  (this is why the MFA corpus's `detail === "User Details not found."` contract was dropped).
- **Interceptor rules:** `CSRF_TOKEN_INVALID` → re-bootstrap the token and retry **once**;
  `PASSWORD_CHANGE_REQUIRED` → route to the change-password screen, *not* a logout;
  `AUTHENTICATION_FAILED` → clear state and redirect to login; `ACCESS_DENIED` → show denial, never retry.
  The recipes' blanket "treat 401/403 alike" is explicitly superseded.
- **`fetch` must not send a restrictive `Accept` header.** Jackson treats `application/problem+json` as the
  producible media type for `ProblemDetail`, so an `Accept: application/json`-only client can get a 406.
- Field errors arrive as `errors: [{field, rule}]` with `rule` as an enum on `VALIDATION_FAILED` and
  `PASSWORD_REJECTED` only — the SPA renders its own copy from the enum, no server English in the UI.
- `traceId` is on every error response. Surface it to the user on 500s only; carry it silently on 4xx.
- MFA outcomes: 412 `MISSING_FACTOR` / `INVALID_FACTOR` (dialog stays open, retryable),
  422 `FACTOR_ENROLMENT_REQUIRED` (route to enrolment), 429 `TOO_MANY_REQUESTS` + `Retry-After` in seconds.
- Auth-failure responses are deliberately indistinguishable, so **the SPA cannot and must not try to explain
  why** a login failed.

## Inherited from ticket 07 — constraints, not open questions

[Decide the password policy and hashing parameters](07-password-policy-and-hashing.md) settled the policy the
Zod schemas mirror, and it lands here as four things:

- **The `PASSWORD_REJECTED` rule enum is closed at six values** — `MIN_LENGTH`, `MAX_BYTES`, `BLOCKLISTED`,
  `CONTEXT_TERM`, `TOO_WEAK`, `HISTORY_REUSE`. The SPA renders its own copy for each. **That copy is how NIST's
  guidance SHALL is discharged**, not a nice-to-have: §3.1.1.2 requires the verifier to help the subscriber
  choose a strong password, and says it matters most immediately after a rejection. `MAX_BYTES` is the one users
  will genuinely not understand without help. `CONTEXT_TERM` is the only rejection that is directly actionable
  ("your password contains your username") and exists as a separate code for that reason.
- **Count the ceiling in bytes, not characters.** The permitted band is 15 code points to **72 UTF-8 bytes**,
  measured after NFC normalisation. A 30-character password can exceed the ceiling; a client counting characters
  will let it through and the server will reject it.
- **zxcvbn on the client, and the backend is authoritative.** Use [zxcvbn-ts](https://github.com/zxcvbn-ts/zxcvbn),
  the maintained fork, not Dropbox's original. The backend gates on `com.nulab-inc:zxcvbn` at minimum score 3.
  The two libraries share an ancestor but zxcvbn-ts has refreshed dictionaries, so **scores will be close but
  not identical** — the meter is indicative, the server decides, and ticket 16 asserts backend behaviour rather
  than score equality. This also discharges IM8 **as-5**, which looks for a strength indicator and names zxcvbn
  by example, and which asks the reviewer to verify frontend and backend rules are *consistent* — so an advisory
  meter the backend ignored would fail that check.
- **A show-password toggle is required, and it collides with an IM8 check.** NIST §3.1.1.2 makes offering an
  option to display the password while entering it a SHOULD. IM8 **as-6** checks that password fields use
  `type="password"`. A toggle flips that attribute, so a literal grep reads the toggle as a violation — the same
  failure mode ticket 01 found with `im8-review`'s Boot 3.4 config spellings. Build the toggle and pre-write the
  reviewer note. as-6 also requires that `autocomplete` is **not** `off` (use `current-password` /
  `new-password`), and that nothing is hashed client-side.

## Inherited from ticket 20 — constraints, not open questions

[Decide the origin topology](20-deployment-origin-topology.md) kept the SPA on its own origin
(**`http://localhost:5173`**, not `3000`) and made this ticket the owner of the document CSP. Four
constraints, all of them things a build discovers the hard way if they arrive late:

- **Wrap the app in Base UI's `CSPProvider` with `disableStyleElements`.** Base UI injects inline
  `<style>` elements in `ScrollArea.Viewport` and in `Select.Popup`/`Select.List` when
  `alignItemWithTrigger` is set. The alternative escape is a per-request nonce, which a statically
  served SPA cannot mint, so disabling the style elements is the only route — and those components
  must then be styled by class. Good news that removes a worry: `style-src-attr` governs style
  attributes parsed from prerendered HTML and **does not apply to client-side JavaScript that sets
  styles**, and React applies the `style` prop through the CSSOM, so Floating UI's dynamic positioning
  needs no CSP relaxation at all.
- **`build.assetsInlineLimit: 0`**, so Vite emits no `data:` URIs. This keeps `img-src 'self' blob:`
  and `font-src 'self'` narrow instead of widening both for inlined assets.
- **`build.chunkImportMap` stays false.** It emits an inline `<script type="importmap">`, which CSP
  treats as inline script and which would force a nonce we cannot provide.
- **Confirm the built `index.html` contains no inline script.** If one appears,
  `build.modulePreload.polyfill: false` removes it. The whole `script-src 'self'` posture — and the
  argument for having no nonce — rests on this holding. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-002. Amend the table by ID, not this list.*

The policy itself, and the delivery mechanism, are settled: a templated
`<meta http-equiv="Content-Security-Policy" content="%VITE_CSP%">` plus a real header from Vite
`server.headers` / `preview.headers`, with `.env.development` relaxing **only** `script-src` and
`style-src` for Vite's own injected code. The prototype should verify with `curl -I`, and the CSP
assertion proper runs against **`vite preview`**, never `vite dev`. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-003. Amend the table by ID, not this list.*

Also inherited, for the QR screen: `img-src` allows `blob:` and not `data:`, so the TOTP enrolment
image must stay an object URL — which collides with ticket 22's finding that the prescribed object-URL
cleanup breaks under React 19 StrictMode. Resolve that here rather than switching to a `data:` URI,
which would require changing the policy.

## What to decide

**Auth state discovery.** The session cookie is HttpOnly, so the SPA cannot read it and has no
client-side way to know whether it is logged in. Decide the mechanism: call the self-read endpoint
from "Decide the admin module" on app load and treat 401 as logged-out. Then decide the
consequences — what renders during that first in-flight request (a splash? a skeleton? nothing?), and
how a hard refresh on a protected route avoids a visible flash of the login screen.

**CSRF handling.** Where the token is fetched, where it is held (in memory, never `localStorage`),
how it is attached to state-changing requests, and the retry policy on a 403 from a stale token —
which "Decide session management and the CSRF contract" settles; implement whatever it decided.
Include the pre-login bootstrap ordering.

**The logout edge case.** Logout keeps CSRF protection and so returns 401/403 on an already-expired
session. The standard is explicit that the SPA must catch this via a global interceptor, clear local
state, and redirect to login **without showing the user an error**. Design that interceptor.

**Routing and guards.** Route structure, the protected-route wrapper, the admin-only route guard,
and where an unauthorised user lands. Client-side guards are UX only — server-side checks are the
real control, and the design should make that obvious so nobody later mistakes the guard for
security.

**Error presentation.** How the error envelope from "Decide the API error envelope" renders. The hard
part: generic auth errors must stay generic in the UI too. A helpful "that account is locked"
message in the frontend leaks exactly what the backend worked to hide. Decide the copy for the
generic failure, and for the registration and reset flows where the response is deliberately
uninformative — the user needs to know what to do next without being told whether the account exists.

**Forms.** Zod schemas mirroring the server-side password policy, and the rule that client validation
is convenience only and never authoritative. Decide how the two stay in sync, since a drifting
client policy produces confusing rejections.

**Accessibility.** What Base UI gives for free and what still needs doing: labels, error association
via `aria-describedby`, focus management on error, live-region announcements for async failures, and
keyboard paths through the admin table and its confirm dialogs.

**Screens to mock.** Login, register, verify-email landing, forgot password, reset password, the
hello-world greeting, and the admin user list with its enable/disable, role-change, and delete
confirmations.

## Done when

A rough mock exists and is linked from this ticket, the auth-state and interceptor behaviours are
decided, the generic-error copy is written, and the component and route structure is recorded.

## Prototype

[Auth state &amp; interceptor behaviour, driveable](../prototypes/14-auth-interceptor-state-machine.prototype.html)
— single self-contained HTML file, opens by double-click, no build. Throwaway; the only part
intended to survive is the pure module in `#pure-module` (`classify` / `reduce` / `screenFor` /
`factorState`), which lifts into the real client intact.

Four switches, each one a decision set the way we intend to build it and flippable to the
obvious-but-wrong alternative: lazy vs eager token bootstrap, proactive vs reactive token refresh,
terminal vs generic sign-out, and the data-fetching library's default three retries. Three
counters — requests, audit rows, throwaway sessions — make the cost of each wrong setting visible
rather than argued.

Ten walkthroughs, chosen for the cases that are hard to reason about on paper: verification
expiring mid-action, two actions blocked at once, a fresh deployment's three gates in order, the
form token after login, sign-out on a dead session, concurrent eviction, a stale enrolment belief,
ten wrong codes, the library's default retry, and an ordinary user hitting an admin URL.

The pure module is exercised headlessly (all 15 `code` identifiers map to exactly one action;
sign-out is terminal ahead of every other branch; the stale-token retry is capped at one; the
three gates order correctly; the step-up queue drains on one success; forgetting is total).

## Answer

**One rule for where the app may be, one rule for what it does when refused, a queue the server
cannot keep, and a word for a state that had none.** Navigation runs off belief and belief comes from
`GET /api/profile`; the envelope `code` is the authority and outranks belief every time. Everything
else here follows from those two sentences, including the one genuinely new member of ticket 06's
closed enum.

External facts are verified in
[the frontend stack and browser platform asset](../research/frontend-stack-and-browser-platform-verification.md)
and cited by section below, not restated here. That asset's §13 records what it does **not** support,
including one claim an earlier draft of this ticket rested on.

### 1. Belief drives navigation; the code is the authority

`GET /api/profile` is the only auth-state discovery mechanism, called on load, and 401 means signed
out. Three state axes arrive in that one response — `role`, `forcePasswordChange`, and ticket 06's
`factors` object — so routing costs no extra round trip, which is the reason ticket 06 put factor
state there rather than inventing an endpoint.

The rule that generalises across all three axes, and the only one worth memorising: **the self-read
tells the app where it may go; the envelope `code` tells it where it actually stands, and wins.**
Never the reverse. This is not a preference — ticket 23 lines 406–411 built
`422 FACTOR_ENROLMENT_REQUIRED` precisely for the case where belief has gone stale (an admin resets
the factor after page load), so the server's own design assumes the client treats replies as
authoritative over its cache. A client that trusted `factors.enrolled` over the 422 would make that
reply dead code, which is the same sentence ticket 23 used to justify building it.

**During the first in-flight self-read the app renders nothing** — not a splash, not a skeleton, and
above all not the sign-in screen. A hard refresh on a protected route therefore cannot flash sign-in
at somebody who is signed in. The cost is a blank frame for one round trip; the alternative is a
false statement about auth state rendered as UI.

### 2. The form token: fetched late, refreshed early, retried once as a backstop

Bootstrap is **lazy** — `GET /api/csrf` on first need, never on load. The endpoint is a write: it
mints a session to hold the token, which is why ticket 09 raised its budget to 30/min and why ticket
08 counts roughly 450 live anonymous rows per source against the 15-minute idle window. Fetching on
load charges a session row to every visitor who never signs in, and to every reload.

Refresh is **proactive**, on login, sign-out, TOTP verification and enrolment confirmation success —
each of which rotates the session id and the token. Ticket 06's single silent re-bootstrap-and-retry
stays as the **backstop, not the primary path**, per ticket 08 §8, and the reason is worth keeping in
front of whoever builds it: leaning on the retry manufactures a 403 on the first mutation of every
session, and §6 asks operators to watch for CSRF-403 spikes. A design that guarantees one per session
drowns its own signal. *Consolidated into the ADR routing (ticket 34): ADR-040. Amend by ID, not this list.*

Anonymous mutations are not exempt. Registration, activation and both reset endpoints each need a
`GET /api/csrf` round trip first, per ticket 10.

`Accept: application/json, application/problem+json`, and **never** `X-Requested-With`. Asset §12
verifies `fetch` defaults to `*/*`, which is permissive enough to defeat ticket 06's 406 trap but may
also satisfy the framework's browser-request matcher that ticket 23 found broken. An explicit
two-type `Accept` avoids both without depending on how that matcher treats `*/*` — a Spring Security
source fact this ticket deliberately routes around rather than resolves. **Consequence worth naming:
the client's `Accept` header is now part of the server's security configuration.** Per the map's
mechanism/constants seam rule the server owns the matcher, the client owns the header, and one test
binds them. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUTH-015. Amend the table by ID, not this list.*

### 3. Sign-out is terminal — on two expiry paths, not three

Any outcome ends the session locally: clear everything, route to sign-in, never interpret, never
retry. Ticket 06 §14's blanket single-retry on `CSRF_TOKEN_INVALID`, applied here, re-bootstraps —
**minting a fresh anonymous session in order to sign out of it** — and then reports success. Nobody
wrote that interaction down.

**Narrower than an earlier draft of this section claimed.** Ticket 08 §12 registers the
absolute-lifetime filter before `CsrfFilter` specifically so an absolutely-expired session gets
`401 AUTHENTICATION_FAILED` rather than a misleading 403. So sign-out on an absolutely-expired
session already lands harmlessly on the clear-and-sign-in path. **The 403-then-mint-a-session path is
idle expiry and concurrent eviction only.** The asymmetry is itself worth recording, because ticket
08's filter ordering fixes one of the three expiry cases and not the other two, and nothing said so.

**A second thing the interceptor owes here.** Ticket 06 §10's table decides the *status* — 403 — and
assigns **no `code`**. Inferring `CSRF_TOKEN_INVALID` from filter order is exactly the kind of
remembered fact the map's verification rule exists to catch, so the code assignment is owed as an
amendment rather than assumed.

Local clearing is **unconditional, on every sign-out and every 401**, regardless of
`Clear-Site-Data`. Ticket 08 established why — the header's `cache` and `storage` directives are
scoped to the API origin, whose storage is empty, so they never touch the SPA's. Asset §11 adds two
reasons it is weaker still: `cache` is the one directive carrying a `partial_implementation` flag in
current Chrome with a documented seconds-long-hang bug, and it was absent from Firefox between 94 and
138. **No service worker**, as a negative assertion with a test: the spec requires the header be
ignored on service-worker-served responses, and a PWA plugin added later would also cache
authenticated responses. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-E2E-001. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): REJ-051. Amend by ID, not this list.*

### 4. The step-up queue, which the server cannot keep

An admin mutation more than ten minutes after verification returns `412 MISSING_FACTOR` with
`factor: "TOTP"` and `reason: MISSING | EXPIRED`. **Ticket 23 never writes the sentence that the
client must re-issue the blocked request**, and it follows from two of its own decisions:
`NullRequestCache` means there is no saved request, and `NoContentAuthenticationSuccessHandler` means
no redirect to resume. So the queue, the prompt and the replay all belong here.

Three properties, in the order they bite:

- **Single-flight.** One prompt regardless of how many requests are blocked. Two mutations fired
  before the prompt is dealt with join one queue and both replay on one success.
- **Re-bootstrap before replay, not after.** Verification rotates the session id and the token
  (ticket 23's minimal strategy includes `CsrfAuthenticationStrategy`), so replaying with the held
  token yields 403 and leans on the backstop. That would manufacture a 403 per step-up — the same
  detection-signal poisoning §2 refuses for login, arriving through a second door.
- **Drop the queue on a terminal refusal.** See §5: there is one branch where replay can never
  succeed, and holding the queue there is how a dialog becomes a trap.

### 5. The two-tier factor lockout, which had no client contract at all

Ticket 23 §5 defines tier 1 as 10 consecutive failures in a 20-minute window → 20-minute lock with
automatic lift, and tier 2 at line 494 as **100 cumulative failures → the factor disabled, cleared
only by `DELETE /api/admin/users/{uuid}/totp`**, with line 617 confirming the unlock endpoint "does
not clear tier 2, which is reserved for rebinding." Neither tier is assigned a status or a `code`
anywhere on the map. **This is an unnoticed gap, not an acknowledged one** — ticket 23 contains no
sentence conceding it, and an earlier draft of this section wrongly credited it with the catch.

Ticket 22 §10 had already warned what the default costs: the corpus maps `AccountLockedException` to
401 alongside an invalid code, which our SPA would read as "session expired" and turn into a
re-login loop.

**Tier 1 — `429 TOO_MANY_REQUESTS`, `Retry-After` from `locked_until`, no new identifier.** The
specificity is licensed by ticket 06 §11's carve-out, quoted rather than re-derived: every MFA
response goes to an already-authenticated session about its own account, so it may be specific
without opening an enumeration channel. That is the same authority that licenses 412's specificity,
and it is the reason ticket 09's refusal of `Retry-After` on the password axis does not transfer —
that refusal was about putting account state on the wire *before* authentication.

Two costs, both recorded rather than waved through:

- **`reason: "LOCKED"` is a new member of a sealed vocabulary.** Ticket 06's amendment formalised the
  reason values as a sealed hierarchy per family, serialised by an explicit `code()`, because they
  become saved-query targets and a rename silently breaks dashboards. Adding `LOCKED` to the factor
  family alongside ticket 23's `MISSING | EXPIRED` is cheap and correct, and it is an amendment, not
  a free reuse.
- **The discriminator crosses an envelope-producer boundary.** Ticket 09 already throttles
  `POST /api/mfa/totp/verification` at 20 burst / 1 per 3 s per source IP, with `Retry-After` almost
  always 1, written by an **early filter registered before `SecurityContextHolderFilter`**. The
  factor-lock 429 comes from the verification filter's failure handler. So "branch on the presence of
  `factor`" is a negative assertion about a *different* producer: ticket 09's 429 must never carry
  that member. Ticket 06 tracks its six producers deliberately; this is a seventh relationship
  between two of them, and it needs the test named or a later change to the early filter collapses
  the discriminator into ambiguity. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-006. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): ADR-033. Amend by ID, not this list.*

**Tier 2 — `423 FACTOR_DISABLED`, on three surfaces, taking the enum to 15 rows / 16 identifiers.**
The denominator is stated rather than inherited, because ticket 06 is internally inconsistent on it
(line 394 takes it to 14, line 436 still says "13-code"); before this ticket it is 14 rows / 15
identifiers, the 412 row carrying two.

The reason a code is unavoidable is stronger than "a delay cannot express an admin action", and it is
a loop:

> Ticket 12 §8 is unambiguous — "TOTP enrolment state is derived from `totp_user_details` row
> existence. There is no `totp_enrolled` flag on `users`." Tier 2 lives in a `cumulative_failures`
> column on that row, and only deleting the row clears it. So a tier-2 admin is **row-present and
> permanently unverifiable**: `factors.enrolled` reads true, admin routes return 412, the prompt
> opens, a **correct** code is refused, and nothing in the vocabulary lets the client stop offering
> it. Because ticket 19 made the challenge eager, the admin never reaches an admin route at all —
> they are pinned on the code screen from login onward. *Consolidated into the register (ticket 33): R-MFA-006. Amend the table by ID, not this list.*

The trap was **encoded in this ticket's own prototype**, in `screenFor`: `factorEnrolled` stays true
so the set-up branch is never taken, and `factorHeldSince` can never become non-null so the app is
never let past. The module is fixed, the new test reproduces the loop with the fix removed, and the
ordering matters — the terminal test must precede *both* factor tests. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-FE-001. Amend the table by ID, not this list.*

"One response, the hundredth failure" does not survive ticket 23's own amendment §4, which routes the
next login through ticket 11's forced-change flag: that path leads to a password change, after which
the row still exists and the loop resumes with no hundredth-failure response available. **There is no
path from tier-2-disabled to enrolment that does not go through an admin deleting the row.** Hence
three surfaces:

1. **`GET /api/profile`'s `factors` object gains a fourth member, `rebindRequired`.** Without it the
   client cannot render the terminal state at all, only discover it by refusal — and §1's rule is
   that belief drives navigation. **The tempting wrong fix is `enrolled: false`**, which would route
   to enrolment, where provisioning returns `409 FACTOR_ALREADY_ENROLLED` off row existence: a second
   dead end, and a self-service path around a control that exists to force admin rebinding.
   **This has to be reconciled with ticket 06's Q23a**, which keeps lock state off this endpoint
   because lockout timing helps an attacker tune brute force. `rebindRequired` is **factor state, not
   lock state**: tier 1 stays off the endpoint precisely because it is a timed lock, and tier 2 ships
   because it is terminal and its remedy is out-of-band. Written down because a reviewer will
   otherwise read the fourth member as a Q23a violation.
2. **The authorization entry point's row-read gains a third branch.** Ticket 23 lines 406–411 has it
   read the principal's TOTP row and branch two ways — absent → 422, present → 412. **This is the
   load-bearing amendment**: without it the server keeps inviting a challenge that cannot succeed,
   and no amount of client work fixes that.
3. **The verification endpoint, on every refusal while tier 2 is tripped** — not only the hundredth.

On the status: 409 was the instinct, reusing ticket 23's own `FACTOR_ALREADY_ENROLLED` precedent and
avoiding a third off-label status. It does not survive surface 2 — **a 409 on `GET /api/admin/users`
is a misuse, because the request conflicts with nothing.** 423 is the pick, and the honest argument
is that this surface already carries deliberate off-label statuses: ticket 23 §12 records 412 as
off-label with RFC 9470 as the road not taken, and 422 likewise. So **§12 becomes three recorded
deviations, not two**, and 423 is consistent with an existing recorded deviation rather than a new
kind of misuse. *Consolidated into the ADR routing (ticket 34): register (REJ-073). Amend by ID, not this list.*

Session termination on the automatic trip is ticket 23's amendment §2, cited rather than asserted:
"The automatic tier-2 trip is a different producer and inherits nothing", owed on ASVS **7.4.2 (L1)**,
and non-atomic — session rows only after commit, never inside the lock. *Consolidated into the ADR routing (ticket 34): REJ-049. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-MFA-006. Amend the table by ID, not this list.*

### 6. The guard's two refusals, which had no codes either

Ticket 11's `AdminActionGuard` holds actor ≠ subject and the two-admin invariant, and **neither has a
status or a code anywhere on the map.** Decided: self-action → `403 ACCESS_DENIED`, since it is an
authorization outcome; the invariant → `409`, on ticket 23 §12's own reasoning that a conflict with
current resource state is 409's job.

The copy carries **three** conditions, not two, because ticket 10 tightened the predicate to
`activated_at IS NOT NULL` **and** TOTP-enrolled: the invitee must redeem the invite, then be
promoted, then finish enrolment. Naming two of the three produces the same support call one stage
earlier. Ticket 11 already records the operational shape for ticket 25 — at exactly two admins
neither can be removed until a third exists — and a refusal with no next step turns an invariant
working correctly into a bug report. *Consolidated into the ADR routing (ticket 34): REJ-050. Amend by ID, not this list.*

### 7. Routing, and making "the guard is not the control" structural

Route tree: public (sign-in, register, activate, forgot, reset), authenticated (hello, profile,
change password), admin (`/admin/users`, detail), and `/settings/mfa` — ADMIN-only, and deliberately
outside `/api/admin/**` so an unenrolled admin can reach it, per ticket 23.

Gate order, fixed and matching the fresh-deploy sequence: forced password change → tier-2 terminal →
enrolment → challenge → app. The five-path forced-change allowlist is exhaustive —
`POST /api/login`, `GET /api/csrf`, `GET /api/profile`, `POST /api/logout`,
`PATCH /api/profile/password` — with `/api/mfa/**` deliberately off it.

Client guards are UX only, and that is made **structural rather than commented**: a test deletes the
guard and asserts the admin data still cannot render, because the server refuses it. A comment saying
"this is not security" ages badly; a test that fails if it ever becomes security does not. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-FE-002. Amend the table by ID, not this list.*

### 8. Error presentation

Branch on `code`, never on `type`, `title`, `detail` or status alone. The SPA owns all copy; no server
English reaches the UI. `traceId` is surfaced on 500 only and carried silently on 4xx.

The generic-failure copy is the load-bearing text: **"Please check your details and try again."** —
no mention of accounts, locks, or existence. Ticket 06 makes wrong password, unknown user, locked,
disabled, credential-expired and both session-expiry paths one indistinguishable 401, so the UI has
nothing to explain and must not invent it. Ticket 11's 30-day credential expiry lands here too, as
does concurrent eviction: the displaced admin gets "Please sign in", never "you signed in elsewhere".

For the deliberately uninformative flows the copy has to tell the user what to do next without
confirming anything. Self-registration's uniform 202: **"If that address can be registered, we've
sent it a link. Check your inbox."** Forgot-password, same shape. Username collision is the one
specific rejection, rendered as a field error on the username input, because ticket 10 evaluates the
username axis first precisely so it can be specific.

### 9. Forms

Zod schemas mirror ticket 07's policy, with the client convenience-only and the server authoritative.
The `PASSWORD_REJECTED` rule enum is closed at six — `MIN_LENGTH`, `MAX_BYTES`, `BLOCKLISTED`,
`CONTEXT_TERM`, `TOO_WEAK`, `HISTORY_REUSE` — and each gets SPA copy, which is **how NIST §3.1.1.2's
guidance SHALL is discharged**, not a nicety.

**Count bytes, not characters**: 15 code points to 72 UTF-8 bytes, measured on the NFC-normalised
value via `TextEncoder`. Normalisation is idempotent so client and server agree whichever form is
sent. `MAX_BYTES` is the rejection nobody expects and needs the most copy; `CONTEXT_TERM` is the only
directly actionable one.

zxcvbn-ts on the client, **lazy-imported on the password screens only**, pinned to 4.x. Asset §7
gives the registry figures — 1,891,187 and 5,444,769 bytes unpacked — and withdraws the
minified+gzipped figures an earlier draft used, which came from an aggregator. **Dropping
`language-en` to halve the payload is rejected on compliance, not size**: IM8 **as-5** asks the
reviewer to verify frontend and backend rules are consistent, and thinning the client dictionaries
widens the divergence from the server's `com.nulab-inc:zxcvbn`. The meter is indicative; the server
decides; ticket 16 asserts backend behaviour, never score equality. The 4.x pin matters because 3.x
ships uncompressed dictionaries.

Show-password toggle built, with the reviewer note pre-written: IM8 **as-6** greps for
`type="password"` and a toggle flips it, the same false-negative shape ticket 01 found with
`im8-review`'s Boot 3.4 spellings. `autocomplete` is `current-password` on sign-in and change-current,
`new-password` on activation and change-new, never `off`. Nothing is hashed client-side.
Confirm-the-password-twice on activation and reset. Tokens are read from the URL **fragment** and
POSTed in the body. *Consolidated into the register (ticket 33): R-FE-001. Amend the table by ID, not this list.*

Ticket 09's budgets make two form behaviours dangerous: **no resend button** on forgot-password
(3/hour per identifier, refilling 1 per 20 minutes — the tightest budget in the app by two orders of
magnitude), and the change-password request takes roughly two seconds for five BCrypt-12 operations,
so the timeout must clear it comfortably and a retry there burns a source-IP budget shared across an
office.

### 10. The document header set

Ticket 20's ten CSP directives are adopted unchanged, and its four constraints are **verified rather
than inherited** (asset §2, §8). Three carry corrections:

- `CSPProvider` with `disableStyleElements` confirmed, and the components named are exactly right —
  but the docs state `disableStyleElements` **does not cover inline `<script>`**, which components opt
  into individually and which needs a nonce we can never mint. Ticket 20 verified the `<style>` half
  only. **Owed: a negative assertion that nothing mounted emits an inline script.** *Consolidated into the register (ticket 33): R-HDR-001. Amend the table by ID, not this list.*
- `build.assetsInlineLimit: 0` is a real change from the default of 4096. `build.chunkImportMap`
  defaults to **false**, so "keep it false" is an assertion, not a change; its inline-importmap
  rationale is strongly evidenced and not formally confirmed. `modulePreload.polyfill` injects into
  the HTML entry's proxy module, so ticket 20's named remedy is speculative and **the real control is
  the check** — grep the built `index.html`. *Consolidated into the register (ticket 33): R-BLD-004. Amend the table by ID, not this list.*
- Ticket 14's body claims `%VITE_X%` HTML replacement "since 4.2". **No version is stated in Vite's
  docs and no changelog entry was found**; the feature is verified, the version is not, and the claim
  must not be repeated. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-006, T-BLD-003, T-BLD-002. Amend the table by ID, not this list.*

`style-src` is **not** split into `style-src-elem`/`style-src-attr`. Base UI's docs confirm the split
would say what we mean, and it is declined because `as-9` greps for the literal directive and the
precision buys nothing pointed at a threat. Considered-and-declined, with the compliance reason, not
left open. *Consolidated into the ADR routing (ticket 34): REJ-054. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-HDR-002. Amend the table by ID, not this list.*

**`Referrer-Policy` cannot be per-page in a single-document SPA.** Ticket 10 line 633 asks for
`no-referrer` "on both pages"; there is one document. It becomes document-wide
`<meta name="referrer" content="no-referrer">` — asset §10 verifies it as spec-supported and Baseline
since January 2020 — placed **first in `<head>`**, because a late tag leaves earlier subresource
requests on the default, and static, because dynamic insertion is documented as unpredictable. Ticket
10 line 640 already offered ticket 20 the header route, so the meta-plus-header shape is compatible
with what is recorded rather than a departure. **Name the split:** ticket 05 line 744 sets
`ReferrerPolicy.SAME_ORIGIN` on the API origin, so a reviewer grepping for the header finds two
values for two documents on two origins, legitimately. And the fragment-borne token already means no
token reaches `Referer` at all, so this is belt-and-braces over the real control. *Consolidated into the ADR routing (ticket 34): REJ-053. Amend by ID, not this list.*

### 11. The QR stays a Blob, and the effect becomes symmetric

**Ticket 23's server-rendered PNG is kept.** An earlier draft of this ticket recommended switching to
client-side SVG on three arguments; ticket 23 lines 174–178 weighed two of them in those words — the
SVG option "deletes object URLs entirely and with them the React 19 StrictMode revoke bug … and needs
no `blob:`" — and declined on a ground the draft never addressed: it moves a prescribed server
responsibility to the client on the MFA happy path, the most visible place on this map to deviate.
Its escape clause is conditional on "ticket 14 finds the Blob reconstruction fights Base UI", **a
precondition that does not hold.** Keeping the PNG also leaves ticket 19 line 308's "enrolment screen
with QR from an object URL" intact, so the amendment surface here is **zero**.

The one new fact is asset §1: React 19.3 double-invokes effects after **Fast Refresh**, so ticket 22's
revoke defect now fires on every save while someone works on this screen. That raises the cost of
getting one effect right; it is not an argument for relocating server work.

The remedy is structural, and the ADR should say why it works. The prescribed effect is
cleanup-only — it revokes and never creates — so under mount/unmount/remount the first cleanup
revokes a URL the second mount renders, and the effect body has nothing to re-create it with.
**One effect owns both halves: create the object URL from the Blob in the body, revoke it in the
cleanup, keyed on the Blob.** The cycle is then symmetric by construction, and ticket 22's two
separate leaks — no revoke on the error path, none when `isVerified` flips — stop being separate
cases, because they are the same cleanup firing. Ticket 16's assertion is **revoke-count equals
create-count across a simulated remount**, which is what actually pins it; asserting the image
renders does not. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-FE-003. Amend the table by ID, not this list.*

`img-src 'self' blob:` survives unchanged, and `data:` stays out of the policy entirely. *Consolidated into the ADR routing (ticket 34): ADR-025. Amend by ID, not this list.*

### 12. Data fetching

**The retry predicate retries only when there is no HTTP status at all.** Asset §6 verifies that
TanStack Query defaults queries to three retries, mutations to zero, and that the retry fires on the
query function throwing — so a wrapper that throws on `!res.ok` turns one 401 into four requests and
four audit rows. A predicate keyed on the absence of a status is strictly better than a global
`retry: false` and equally cheap: genuine network flakiness still recovers, while every 4xx resolves
on the first reply.

**An earlier draft called `retry: false` "forced by ticket 09's budgets and ticket 21's disk sizing".
That was unsupported and is withdrawn.** Ticket 09's ten-row table has no row for `GET /api/profile`
— its only profile row is `PATCH /api/profile/password`, keyed on source IP — and ticket 21's sizing
is symbolic (`90 × daily`, `daily × lead_days`) with no bytes-per-event or events-per-day constant
anywhere. Neither cited authority reaches the endpoint.

**The real finding is larger than the retry setting and survives whatever the client does.** Ticket 11
line 219 attaches `event.action: profile-read` to every `GET /api/profile`, and that endpoint has no
rate-limit row. An endpoint audited on every call, throttled on none, and read by every guard and
every route is an **unbounded input to ticket 21's `daily`**. Handed back to tickets 09 and 21 as a
named owed input. *Consolidated into the ADR routing (ticket 34): REJ-052. Amend by ID, not this list.*

Profile data lives **in memory, is invalidated on the five rotation events, and is never persisted**.
Ticket 11 line 422 lists `Cache-Control: no-store` as a response header in the endpoint's field list;
an earlier draft of this ticket attributed to it an instruction about client-side caching that **does
not exist anywhere in that ticket**, and built a three-option tension around the invented sentence.
Deleted. There is no conflict to interpret.

### 13. Accessibility

Base UI supplies focus management and ARIA for the dialog primitives; the corpus supplies one rule
across two files, `role="alert"` on the invalid-code message, which its own reference code omits.
Everything else is ours: labels, `aria-describedby` on every field error, focus moved to the first
error on submit failure, focus returned on dialog close, a live region for async failures, and
keyboard paths through the admin table and its confirm dialogs.

On the code entry specifically, ticket 22 found the prescribed component has no autofocus, no paste
handling and no auto-submit, and clears inconsistently. Decided: autofocus slot 0 when the prompt
opens, paste of a six-digit string fills all slots, **no auto-submit** (an affirmative click, so a
mistyped code is correctable before it burns a tier-1 attempt), clear on every Verify click per
§7.3, and `role="alert"` present. `setOpen` is wired to `onOpenChange` so Esc and overlay dismissal
work — and dismissal must drop the step-up queue, not leave it parked.

Ticket 22's fail-open mount checks are inverted: a failed status probe is treated as unknown and
disables the control, never as "not enrolled". And `409 FACTOR_ALREADY_ENROLLED` is surfaced
distinctly rather than as a generic generation failure, because ticket 23 §7 makes that reply the
compromise-detection signal — an admin told they already have an authenticator when they know they
do not has been handed evidence. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-FE-004, T-FE-005, T-FE-006, T-FE-007, T-FE-008, T-FE-009, T-FE-010, T-FE-011, T-FE-012, T-FE-013, T-FE-014, T-FE-015, T-FE-016. Amend the table by ID, not this list.*

The conformance target and how keyboard and screen-reader behaviour get verified stays fog, narrowed:
the functional blocker is closed (ticket 23 ships `otpauthUri` and `secretBase32`, so manual entry
exists), leaving the target level and the verification method. *Consolidated into the register (ticket 33): R-FE-005. Amend the table by ID, not this list.*

### 14. Screens

Ticket 14's own list is short, and **three of the additions were already on the map** — ticket 19
line 308 hands over the enrolment screen, the code-entry dialog and the eager post-login challenge.
They are missing from this ticket's list, not from the map. Genuinely unlisted anywhere: the forced-
password-change screen, the admin create-user form, and the **one-time token display**.

That last one is the most security-load-bearing screen in the application and it appears in no
ticket's screen list: an admin invite token or admin-issued reset token, returned once in a response
body, shown once, never logged, never cached, never re-fetchable, with the warning that it will not be *Consolidated into the register (ticket 33): R-FE-007. Amend the table by ID, not this list.*
shown again and a copy affordance that does not route through anything persistent.

**Staleness to fix before it is built:** ticket 11 line 324 still describes admin-created users
receiving "a 20-character generated password … returned to the admin exactly once", which ticket 10
superseded when admin-create became invite-token issuance. The screen displays a token; the ticket
describing what it displays still says password.

### 15. Corrections to this ticket's own premises

Recorded so they are not re-derived, and because four of the five run in the same direction — they
made a chosen answer look harder-won than it was.

1. **Ticket 22's stack mismatch is wrong on the OTP half and narrower than stated on the dialog
   half.** shadcn/ui made Base UI the default in July 2026, stable, and ships Input OTP built on
   `input-otp` plus Dialog and Alert Dialog (asset §4). But the corpus imports `AlertDialog` straight
   from Radix and the changelog flags prop-shape changes (`asChild` → `render`) with a migration skill
   shipped because behaviour differs, so **re-hosting the dialog is real work**. What is false is
   "with zero corpus guidance" — guidance now exists.
2. **The `input-otp` CSP interaction is silent, not warned.** `safeInsertRule`'s `console.warn` covers
   rule-level rejection only; if CSP blocks the `<style>` element, `sheet` is null, the block is
   skipped and the helper is never reached (asset §5). So an assertion of the form "no `input-otp`
   warning" **passes in exactly the blocked case** and cannot detect what it exists to detect. The
   assertion is positive: the injected sheet exists and carries rules. And "renders unstyled" was an
   overstatement — the sheet is cosmetic (selection, autofill, an iOS caret fix, PWM badge
   `pointer-events`) while layout rides on inline `style` through the CSSOM. The degradation is visual
   integrity and possibly badge clickability, not a broken field.
3. **The Q6 caching conflict never existed.** See §12.
4. **`retry: false` was not forced.** See §12.
5. **The tier-2 gap is unacknowledged, not acknowledged**, and the earlier "one response, the
   hundredth failure" scope was wrong. See §5. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-003. Amend the table by ID, not this list.*

One further non-finding, recorded because it looked like a defect for an hour: ticket 10's "§4:401
leaves no slot for the PRD's reset *request* endpoint" is about the **Standard** having no prescribed
shape, not about our architecture. `POST /api/password-reset/request` is real and lives in ticket 09's
budget table. The only thing owed is a cross-reference, because a builder reading ticket 10's flow
section alone never learns the path.

### 16. What this owes

**ADRs (nine).** `423 FACTOR_DISABLED` and the three surfaces, with the loop as its justification;
tier 1 as `429` plus the two-member discriminator and the enumeration carve-out it rests on;
`409` for the two-admin invariant and `403` for self-action; sign-out terminal, with the
idle/eviction-only narrowing; the lazy token bootstrap against the session-row cost; the retry
predicate and the audited-but-unbudgeted self-read; keeping ticket 23's server PNG with the symmetric
effect as the remedy; document-wide `Referrer-Policy` and the two-origin split; `style-src` left
unsplit on the `as-9` grep reason.

**Glossary (six).** Belief versus authority; step-up queue; terminal factor state; rebinding;
one-time secret display; visual-integrity degradation.

**Deferral register (four).** The accessibility conformance target, still fog. The inline-`<script>` *Consolidated into the register (ticket 33): R-FE-005. Amend the table by ID, not this list.*
negative assertion as an assumption until asserted. `build.chunkImportMap`'s rationale as *Consolidated into the register (ticket 33): R-HDR-001. Amend the table by ID, not this list.*
strongly-evidenced-not-confirmed. `Clear-Site-Data` on `http://localhost` as untested rather than *Consolidated into the register (ticket 33): R-BLD-004. Amend the table by ID, not this list.*
observed.

**Test delta (eleven).** Revoke-count equals create-count across a simulated remount; the OTP field's
injected sheet exists and carries rules under `vite preview` with the production policy; no inline
`<script>` in the built `index.html`, and none emitted by any mounted component; the client sends the
two-type `Accept` and never `X-Requested-With`, bound to the server's entry-point matcher in one test;
ticket 09's per-IP 429 never carries a `factor` member; a tier-2 account reaches the terminal screen
and the prompt is never offered; deleting the admin route guard leaves admin data unrenderable;
no service worker registered; sign-out clears local state on 204, 401 and 403 alike; the six
`PASSWORD_REJECTED` rules each render distinct copy; a 30-character multibyte password over 72 bytes
is rejected client-side with `MAX_BYTES` copy. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-FE-003, T-HDR-003, T-BLD-002, T-HDR-006, T-AUTH-015, T-MFA-006, T-FE-001, T-FE-002, T-E2E-001, T-FE-017, T-FE-018, T-FE-019. Amend the table by ID, not this list.*

**Amendments.** Ticket 06 — `FACTOR_DISABLED` at 423 (enum to 15 rows / 16 identifiers), `LOCKED`
added to the factor reason family, the logout-403 code assigned, and the producer relationship between
ticket 09's early filter and the verification filter's handler. Ticket 11 — `factors` gains
`rebindRequired`, with the Q23a reconciliation; line 324's generated-password text superseded. Ticket
23 — the entry point's row-read gains a third branch, and §12 becomes three recorded deviations.
Ticket 20 — its four constraints verified with three corrections. Ticket 10 — a cross-reference to
ticket 09's table for the reset-request path. Ticket 19 — untouched, which is the point of §11.

**Handbacks.** Tickets 09 and 21: `GET /api/profile` is audited per call and has no budget row.
Ticket 08: of the three directives in its `Clear-Site-Data` header, `cache` is the one carrying a
partial implementation with a documented hang bug and a 44-version Firefox hole, and it buys least
given the SPA clears its own state unconditionally. Ticket 25: the two-admin three-condition copy,
and the one-time-token handling rules.

---

## Amendment from ticket 16 (test plan)

- **Deferral-register entry 4 (14:625, "`Clear-Site-Data` on `http://localhost` as untested rather than observed") is reversed.** Ticket 16 puts Playwright in scope (Chromium and Firefox) and owns the observation at level E. The entry leaves the register. It was never a contradiction with ticket 08 (08:372–380 already said the header is testable locally); it recorded a test not yet run.
- **The `input-otp` CSP check (14:590–594)** counts `securitypolicyviolation` events under `vite preview` with the production policy and asserts **zero**. A "no warning" form passes in exactly the blocked case.
- **Frontend levels.** Vitest + Testing Library + MSW in jsdom. MSW fixtures are validated against the backend's enum schema (ticket 06's amendment from ticket 16).
  - The eleven tests at 14:621–630 are transcribed by ticket 32.
  - Deliberately not tested at level F: vendored shadcn and Base UI internals, zxcvbn score parity, visual styling, and anything CSP- or cookie-dependent, which goes to level E. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-005, T-HDR-003, T-AUTH-012. Amend the table by ID, not this list.*
