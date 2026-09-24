# 02 — Verify the App Standard's controls are still current practice

Type: research
Status: resolved
Blocked by: —

## Question

The App Standard is binding, but it may be dated. For each security control it prescribes, is that
control still current best practice against primary sources — or has guidance moved on?

Where the standard is behind current practice, say so with a citation, so the design ticket that
consumes it can make an informed call instead of inheriting a stale rule.

## Suspected stale items — check these first

The standard cites NIST SP 800-63B-4 (2025) yet prescribes several things recent guidance has moved
away from. These are concrete, not speculative:

1. **Password history (default 3).** Recent NIST guidance discourages forced rotation and history
   requirements, favouring breached-password screening instead. Does 800-63B-4 still endorse
   history? (Constrains "Decide the password policy and hashing parameters".)
2. **Composition rules.** The standard requires admin-generated passwords to contain lowercase,
   uppercase, digit, and special character. NIST has moved away from composition rules. Check.
3. **Account lockout as primary brute-force control.** NIST and OWASP increasingly favour
   throttling and rate limiting over hard lockout, because lockout is itself a denial-of-service
   vector — exactly the tension between PRD Story 3 and the standard's per-account counters.
   (Constrains "Decide lockout and dual rate limiting semantics".)
4. **Breached-password screening.** NIST recommends checking new passwords against known-compromised
   lists; the standard omits it entirely. Spring Security ships a `CompromisedPasswordChecker`
   backed by the Pwned Passwords k-anonymity API. Is this a MUST under current guidance, and does
   calling an external API conflict with IM8?
5. **BCrypt specifics.** Current OWASP Password Storage guidance on minimum work factor, and on
   BCrypt's 72-byte input truncation — what is the recommended handling for long passphrases
   (pre-hash? enforce a max length? something else)? The PRD sets a 12-character minimum with no
   stated maximum, so this is live.
6. **`SameSite=Lax` vs `Strict`** for a session cookie on an SPA whose API is a different origin but
   the same site. Current OWASP session management guidance.
7. **Argon2id vs BCrypt.** BCrypt is already the decision, but record what current guidance says so
   the ADR can state the trade-off honestly rather than pretending there isn't one.

## Method

Primary sources only: NIST SP 800-63B-4, the OWASP cheat sheets the standard itself cites
(Authentication, Password Storage, Session Management, CSRF, Forgot Password, Credential Stuffing),
OWASP Top 10:2025, and official Spring Security documentation. Not blog posts.

## Done when

Each of the seven items above is marked "standard is current" or "standard is behind, current
guidance says X" with a citation, and any item where the standard is behind is flagged to the
design ticket that consumes it.
## Answer

Verified against primary sources only, September 2026. Sources used: [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html)
(final, 2025), the OWASP cheat sheets the standard itself cites, [OWASP Top 10:2025](https://owasp.org/Top10/2025/),
and official Spring Security 7.0 reference/API docs plus the Spring security advisories. No blog posts, no SEO content.
Content from external sources was rephrased for compliance with licensing restrictions; verbatim quotes are kept
under 25 words and attributed inline.

**Sections of the standard verified:** §3.5 Security Contract (Session Management, Password Policy, Account Lockout,
Rate Limiting, HTTP Security), §6 Required Runtime Configuration, plus §1 Scope line on "expiring stale credentials".

### Verdict summary

| # | Control in the standard | Verdict | Consumed by |
|---|---|---|---|
| 1 | Password history default `3` | Permitted but not endorsed — wrong control to lean on | 07, 12, 17 |
| 2 | Composition rules on admin-generated passwords | Behind in spirit; harmless here, harmful if it leaks to user-chosen | 07, 10, 11 |
| 3 | Account lockout as primary brute-force control | **Behind** — throttling is primary, lockout is the backstop | 09, 15, 16 |
| 4 | Breached-password screening (absent) | **Behind — normative SHALL, and the standard omits it** | 07, 01, 10, 12 |
| 5 | BCrypt work factor + 72-byte truncation | Silent — must be decided; two 2025 CVEs are load-bearing here | 07, 06, 16, 17 |
| 6 | `SameSite=Lax` on session cookie | **Behind** — `Strict` is now preferred and costs nothing here | 08, 14, 15 |
| 7 | Argon2id vs BCrypt | Standard's preference is current; the PRD's BCrypt is the weaker option | 07, 17, 12 |

---

### 1. Password history (default `3`) — standard is permitted, but not endorsed, and it is not the control it looks like

**Verdict: the standard is not contradicted, but NIST does not endorse history as a control, and relying on it in place
of breached-password screening is the actual mistake.**

NIST SP 800-63B-4 §3.1.1.2 (Password Verifiers) contains **no** password-history requirement. What it does contain is
the opposite-direction rule — [NIST SP 800-63B-4 §3.1.1.2](https://pages.nist.gov/800-63-4/sp800-63b.html): "Verifiers
and CSPs SHALL NOT require subscribers to change passwords periodically." Forced change is permitted only on evidence
of compromise. Reuse rejection appears only once, in the *informative* usability section (§8.1.2.2), which suggests
giving clear feedback when a chosen password is rejected — listing "has been used previously" alongside blocklist hits
as example reasons. That is permission, not a mandate.

So: keeping `password_history = 3` does not put us out of compliance. It is cheap and it is a binding requirement of
the standard, so we keep it. Two things to record honestly:

- It buys very little. NIST's answer to "don't let users pick a bad password" is the blocklist (item 4), not history.
  Three prior hashes stop `Password1!` → `Password2!` only if the user cycles within three; they do not stop a password
  that was breached elsewhere.
- It has a cost: three additional bcrypt hashes retained per user, each an offline-attack target and a data-retention
  obligation. Ticket 12 needs to decide the table, and whether history rows are purged on account deletion or follow
  the tombstone.

**Separate conflict found while checking this.** The standard's §1 Scope commits to scheduled jobs "expiring stale
credentials". If that is ever implemented as periodic password expiry it is a direct `SHALL NOT` violation of §3.1.1.2.
Note also OWASP Top 10:2025 A07 lists rotation and complexity requirements as practices organisations are advised to
stop ([A07:2025 Authentication Failures](https://owasp.org/Top10/2025/A07_2025-Authentication_Failures/)). The account
hygiene jobs are already out of scope on the map, so this is currently moot — but the deferral note in ticket 17 should
say *we are not implementing credential expiry, and would not, because NIST prohibits it*, rather than implying it is
merely deferred work someone should pick up later.

**Flagged to:** 07 (Decide the password policy and hashing parameters) for the policy call; 12 (data model) for the
history table; 17 (deferral register) for the expiry wording.

---

### 2. Composition rules on admin-generated passwords — behind in spirit, mostly harmless as written

**Verdict: the standard is behind current guidance in spirit. As scoped to *admin-generated random* passwords it is not
a NIST violation, but it is pointless, and the real risk is these four rules leaking into the user-chosen password
validator.**

NIST is unambiguous about user-chosen passwords —
[NIST SP 800-63B-4 §3.1.1.2](https://pages.nist.gov/800-63-4/sp800-63b.html): "Verifiers and CSPs SHALL NOT impose
other composition rules (e.g., requiring mixtures of different character types) for passwords." §3.1.1.1 adds that if
a password is rejected for being on a blocklist the subscriber must choose another, and that other composition
requirements SHALL NOT be imposed. Appendix A.3 explains why: users respond to composition rules predictably
(`password` → `Password1` → `Password1!`), so the rules cost memorability without buying strength. OWASP agrees — the
[Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) states
there should be no composition rules limiting permitted character types, and no requirement for case, digits, or
special characters.

The nuance that saves the standard: §3.1.1.1 permits passwords "assigned randomly by the CSP", and the `SHALL NOT`
targets rules imposed on *subscriber choice*. An admin-generated 12-character password is machine-generated, so
requiring one of each class does not frustrate a human. But it is still the wrong shape:

- Constraining a CSPRNG draw to contain one of each class *reduces* the effective keyspace (rejection sampling), it
  does not increase it. A uniform draw from the full printable-ASCII alphabet is strictly stronger per character.
- These passwords are single-use by design — the standard already flags every admin-created account for mandatory
  change on first login. Making them pretty is wasted effort.
- The live risk is contamination: if the same `PasswordPolicy` component validates both admin-generated and
  user-chosen passwords, the composition rules become a `SHALL NOT` violation the moment they apply to a user's choice.

**Recommendation for ticket 07:** keep the standard's 12-character admin-generated password but generate it as a
uniform draw over the full printable-ASCII set with no class quotas (which satisfies the letter of the standard with
overwhelming probability at 12 characters, and exceeds it in strength). Keep the user-chosen validator to length,
blocklist, and the 72-byte ceiling — nothing else. If ticket 11's admin bootstrap reuses the generator, it inherits
this. If the compliance reviewer insists on literal class quotas, that is an ADR: cite Appendix A.3 and note the
entropy cost.

**Flagged to:** 07; 10 (credential flows) for the shared validator boundary; 11 (admin bootstrap) for the generator.

---

### 3. Account lockout as primary brute-force control — standard is behind

**Verdict: the standard is behind. Current guidance says the primary control is rate limiting / progressive throttling,
with hard lockout as a backstop, precisely because lockout is itself a denial-of-service vector. The standard's
numbers (5 / 20 min) are still *permitted*, but the framing is stale — and the standard's own two counters are
internally inconsistent.**

NIST no longer calls this control "lockout" at all. §3.2.2 is titled **Rate Limiting (Throttling)** and requires the
verifier to limit consecutive failed attempts on a single account to no more than 100 by disabling that authenticator
— an *upper* bound, with agencies free to go lower. It then recommends techniques specifically to stop an attacker
locking out a legitimate user: a bot-detection challenge before authentication, an **increasing** wait after each
failure (the worked example runs 30 seconds up to an hour), and risk-based signals (IP, geolocation, request timing,
browser metadata). Appendix A.2 names the threat plainly: an attacker, or a claimant with poor typing, inflicting a
denial-of-service on the subscriber through incorrect guesses. It also confirms the standard's reset rule — on
successful authentication the verifier should disregard prior failed attempts.
([NIST SP 800-63B-4 §3.2.2](https://pages.nist.gov/800-63-4/sp800-63b.html))

OWASP Top 10:2025 A07 prevention guidance is the same shape:
["Limit or increasingly delay failed login attempts but be careful not to create a denial of service scenario."](https://owasp.org/Top10/2025/A07_2025-Authentication_Failures/)
It pairs that with logging every failure and alerting on suspected credential stuffing.

Where the standard is *right* and should be kept: the
[OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
still describes account lockout as the most common protection, and explicitly says the failed-login counter belongs to
the account rather than the source IP, so an attacker cannot spread attempts across many IPs. That directly vindicates
the standard's per-account counter and the map's dual-limiter decision — per-account lockout and per-IP throttling
defend different attacks and neither substitutes for the other. The same cheat sheet names the three parameters to
tune (threshold, observation window, lockout duration), suggests exponential rather than fixed duration, and warns
that lockout must not become a DoS — noting that allowing forgotten-password to work on a locked account is one way out.

**Two concrete defects for ticket 09 to resolve:**

1. **The standard has no observation window.** It says "5 *consecutive* failed logins" with a reset on success, but
   never bounds the window. A user who mistypes twice on Monday, twice on Wednesday and once on Friday is locked out.
   OWASP names the observation window as a required parameter. Ticket 09 must add one.
2. **The two counters cannot both bite.** §3.5 sets lockout at 5 consecutive failures *and* a login rate limit of 10
   attempts per account per minute. The account is locked at attempt 5, so the per-account 10/min limiter is
   unreachable and the `429` + `Retry-After` path in Failure Path 3 is dead code on the per-account axis. Either the
   rate limit must be below the lockout threshold to act as the *first* line (which is what NIST's progressive-delay
   model wants), or the 10/min budget belongs on the per-IP limiter from PRD Story 3. My read: make per-IP the 10/min
   limiter and introduce a progressive per-account delay *before* the hard lock at 5 — that gets us NIST's throttling
   posture while still satisfying the standard's letter on threshold and duration.

The standard's fixed 20-minute auto-lift is defensible and worth keeping as the DoS release valve — it is explicitly
justified in Failure Path 2 to prevent mass-lockout DoS, which is the current concern, correctly handled.

**Flagged to:** 09 (Decide lockout and dual rate limiting semantics) — this is the ticket the item names; 15 (threat
model) for lockout-as-DoS; 16 (test plan) for the observation-window and 429-reachability tests.

---

### 4. Breached-password screening — standard is behind, and this is the largest gap found

**Verdict: the standard is behind. Blocklist screening against known-compromised passwords is a normative `SHALL` in
NIST SP 800-63B-4 and the standard omits it entirely. This is the one item where the standard is not merely
old-fashioned but non-compliant with the guideline it claims to align with.**

[NIST SP 800-63B-4 §3.1.1.2](https://pages.nist.gov/800-63-4/sp800-63b.html) requires that when processing a request to
establish or change a password, verifiers "compare the prospective secret against a blocklist that contains known
commonly used, expected, or compromised passwords". The whole password is compared, not substrings. The list may draw
on previous breach corpuses, dictionary words, and context-specific words such as the service name, the username, and
derivatives. On a hit the CSP SHALL require a different secret **and SHALL provide the reason for rejection**. Verifiers
SHALL also offer guidance to help the subscriber choose a strong password, which matters most right after a rejection.

Crucially for scoping, NIST also bounds the effort: the blocklist should be large enough that an attacker is unlikely
to guess a permitted password before the throttling limit bites, and excessively large lists give little incremental
benefit because online attacks are already rate-limited. Appendix A.3 repeats this — an over-large blocklist mostly
frustrates users.

Corroborating sources: the [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
says to block common and previously breached passwords and names Pwned Passwords (API or self-hosted download). The
[Credential Stuffing Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Credential_Stuffing_Prevention_Cheat_Sheet.html)
makes "identifying leaked passwords" a named defence. OWASP Top 10:2025 A07 lists both a weak-password check against a
worst-passwords list *and* validation against known breached credentials at account creation and password change; it
also lists "allows users to create new accounts with already known-breached credentials" as a defining weakness of the
category.

**Spring Security support (confirmed, primary source).** The
[Spring Security reference](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
documents the `CompromisedPasswordChecker` API and `HaveIBeenPwnedRestApiPasswordChecker`. Registering a
`CompromisedPasswordChecker` bean is picked up automatically when authenticating through `DaoAuthenticationProvider`,
which raises `CompromisedPasswordException` — and the docs warn about exactly the UX trap: a bare 401 confuses a user
whose password *was* correct, so they show handling the exception in an `AuthenticationFailureHandler` to route the
user to a reset. Note the shape of that: the built-in integration screens **at login**, not only at password set. That
is a design decision, not a given.

**Is calling an external API an IM8 problem?** Weaker than it first looks, but not zero:

- The Pwned Passwords range API is k-anonymity based. The client sends only the first five characters of the SHA-1
  hash of the candidate and filters the returned suffix list locally — the password and its full hash never leave the
  application ([Have I Been Pwned, NIST-compliant password checking](https://haveibeenpwned.com/NIST)). So the
  confidentiality objection largely dissolves; there is no credential egress.
- What remains is real: outbound internet egress from the credential-change path, a third-party availability
  dependency on password set/reset/change (what happens on timeout — fail open or fail closed?), TLS trust and
  possibly proxy configuration, and a supplier/data-transfer assessment that IM8 will want regardless of k-anonymity.

**Recommendation for ticket 07.** Implement screening — it is a `SHALL` and we cannot skip it — but implement it as a
**local bounded blocklist**, not a live API call:

1. Ship a curated list (top ~10k–100k breached/common passwords, filtered to those ≥ our minimum length, since shorter
   entries can never be chosen anyway) as a resource, checked in and version-pinned. NIST explicitly blesses this size
   bound.
2. Add the context-specific terms NIST names: the service name and its derivatives, and the username itself — the
   latter must be checked per-request, not baked into the list.
3. Implement it as a `CompromisedPasswordChecker` bean so the wiring is the framework's, and the API-backed checker
   remains a one-line swap if egress is ever approved.
4. Screen on **set/change/reset** (the NIST trigger). Decide separately and deliberately whether to also screen at
   login — screening at login is what the Spring default does, costs a check per authentication, and turns a valid
   login into a forced reset.
5. Error contract: NIST requires telling the user *why* the password was rejected. That interacts with ticket 06 —
   this is a legitimate specific error, and it does not leak account existence because it describes the submitted
   password, not the account. Standard §3.5 Failure Path 18 already requires a specific rule-violation error, so this
   is consistent.

**Flagged to:** 07 (primary — this ticket decides the policy); 01 (IM8/ARC controls — record the egress decision and
why k-anonymity does not fully dispose of it); 10 (credential flows — which of set/reset/change/login screens);
12 (data model — blocklist is a resource, not a table); 06 (error envelope — rejection reason).

---

### 5. BCrypt work factor and the 72-byte input problem — standard is silent; current guidance is specific, and two 2025 CVEs sit directly on this

**Verdict: the standard is silent on both (it externalises "password hashing algorithm" and "minimum strength
requirements" to configuration without values), so there is nothing to contradict — but current guidance is precise,
and Spring Security's own history here is load-bearing. Recommended handling: enforce a hard 72-**byte** maximum,
reject explicitly, and do not pre-hash.**

**Work factor.** [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html):
"The work factor should be as large as verification server performance will allow, with a minimum of 10." Its general
rule is that a hash should take under a second; it also notes that a work factor set too high becomes its own DoS
vector, since an attacker can exhaust CPU with login attempts. The Spring Security reference says the same — tune the
work factor to roughly one second on your system, and treat the framework default as a starting point, not an answer.
`BCryptPasswordEncoder` in [Spring Security 7.0](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/crypto/bcrypt/BCryptPasswordEncoder.html)
defaults to strength **10**, accepts 4–31, and takes an optional version (`$2a`, `$2b`, `$2y`) and `SecureRandom`.

NIST is deliberately algorithm-agnostic but imposes storage requirements we must meet regardless: salt at least 32
bits, both salt and hash stored per password, cost factor as high as practical without hurting verifier performance and
increased over time, and — directly relevant to our `DelegatingPasswordEncoder` choice — a stored **reference to the
scheme and cost factor** per password so we can migrate later. The `{bcrypt}$2a$10$…` prefix format satisfies that.

**The 72-byte problem.** OWASP is blunt: bcrypt's input limit is 72 bytes in most implementations, so
["enforce a maximum password length of 72 bytes"](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
(or less, if the implementation's limit is lower). On pre-hashing, it says the naive `bcrypt(H(password))` construction
is **dangerous** for two reasons: original bcrypt expects a NUL-terminated string, so a hash output containing a zero
byte truncates the input there; and password shucking means that if the inner hash `H` of the same password is known to
an attacker from another breach, cracking collapses to breaking `H`. `bcrypt(base64(sha512(password)))` is called out
as no better than plain SHA-512. The only construction it sanctions, if pre-hashing is unavoidable, is
`bcrypt(base64(hmac-sha384(password, pepper)))` with the pepper stored outside the database.

Both NIST and OWASP also forbid the lazy option: NIST §3.1.1.2 requires the verifier to request the password in full
and verify the entire submission without truncating it; the OWASP Authentication Cheat Sheet says do not silently
truncate.

**Spring Security's two 2025 CVEs on exactly this.** Primary sources:

- [CVE-2025-22228](https://spring.io/security/cve-2025-22228) — `BCryptPasswordEncoder.matches()` returned `true` for
  passwords over 72 characters whenever the first 72 matched. Any password sharing our user's first 72 bytes
  authenticated. Fixed in 6.3.8 / 6.4.4 by enforcing the maximum length.
- [CVE-2025-22234](https://spring.io/security/cve-2025-22234) — that fix inadvertently broke
  `DaoAuthenticationProvider`'s timing-attack mitigation, reintroducing a timing side channel. Fixed in 6.3.9 / 6.4.5.

Spring Boot 4.1 / Spring Security 7.0 is well past both, so the encoder now **rejects** over-long input rather than
silently truncating it. That is the right behaviour but it changes our error contract: an unvalidated 100-character
password reaches the encoder and produces a framework exception rather than our validation error. The second CVE is
also a standing reminder for ticket 06 and the map's response-time normalisation note — timing equality on the auth
path is fragile and has been broken by a security fix before.

**Recommendation for ticket 07:**

1. Validate a maximum of **72 bytes** (UTF-8), not 72 characters, in our own validator, before the encoder ever sees
   the input. A 64-character passphrase with non-ASCII characters can exceed 72 bytes, so the limit must be expressed
   and surfaced in bytes, and the SPA must count the same way (ticket 14).
2. Reject with a specific error naming the rule (consistent with §3.5 Failure Path 18 and ticket 06). Never truncate.
3. **Do not pre-hash.** The only safe construction needs a pepper in a secrets store, and secrets handling for
   non-local environments is explicitly "not yet specified" on the map. Adding a pepper dependency to buy support for
   passphrases over 72 bytes is a bad trade for this app.
4. Set the work factor by measurement on the target hardware, floor of 10, target ≤ 1s per verify. Record the measured
   number and the machine in the ADR, because the number is meaningless without them. Externalise it as configuration,
   which the standard already requires.

**Supplementary finding, and it is a live conflict.** NIST SP 800-63B-4 §3.1.1.2 requires **15 characters minimum**
for a password used as single-factor authentication; 8 is permitted only when the password is part of a multi-factor
process. MFA is out of scope for this app, so we are single-factor, and the PRD's 12-character minimum is below the
NIST floor. The OWASP Authentication Cheat Sheet restates the same threshold — under 15 characters is weak without
MFA. NIST additionally says verifiers SHOULD permit at least 64 characters, SHOULD accept all printing ASCII plus
space, SHOULD accept Unicode (counting each code point as one character, NFC-normalised before hashing), and SHALL
allow password managers and paste. Ticket 07 must either raise the minimum to 15 or write an ADR accepting 12 with the
citation stated — and note the interaction: a 15-character floor with a 72-byte ceiling is a narrower band than it
looks for non-ASCII input.

**Flagged to:** 07 (work factor, max length, minimum length); 06 (error envelope for the rejection, and the timing
side-channel precedent); 14 (frontend must count bytes, allow paste, allow all characters); 16 (test plan: a >72-byte
password must be rejected by *our* validator, not by the encoder); 17 (ADR if 12 characters is retained).

---

### 6. `SameSite=Lax` vs `Strict` for the session cookie — standard is behind

**Verdict: the standard is behind. `Lax` is still explicitly permitted, but current OWASP guidance names `Strict` as
preferred — and in our specific topology (SPA and API on different origins but the same site) `Strict` costs us
nothing, so there is no reason to take the weaker option.**

[OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html):
"Session cookies must explicitly set SameSite=Strict (preferred) or SameSite=Lax." It adds three things the standard
does not say — never use `SameSite=None` without `Secure`, never rely on the browser default (it varies by browser and
version, which is why the standard is right to require the attribute explicitly), and treat `SameSite` as defence in
depth rather than a CSRF-token replacement. The same cheat sheet now recommends the **`__Host-` cookie name prefix**
for session IDs: it forces `Secure`, forbids a `Domain` attribute, and requires `Path=/`, which blocks subdomain
forgery and HTTPS downgrade. Its worked example is `__Host-SessionID=…; Secure; HttpOnly; SameSite=Strict; Path=/`.

**Why `Strict` is free for us.** The decisive fact is that `SameSite` is scoped to the **registrable domain, not the
origin** — the [CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
spells this out under "Limitations of SameSite". So the SPA calling the API across origins on the same site is
**same-site** for cookie purposes, and `Strict` does not withhold the cookie on those XHR calls. The usual argument for
`Lax` — that `Strict` breaks a user arriving from an external link and appearing logged out — does not apply either:
the session cookie is set by the API, host-only (no `Domain` attribute, per the standard's own narrow-scope rule and
the `__Host-` prefix), so no inbound top-level navigation ever needs it. The SPA's own document load carries no session
cookie under any `SameSite` value. `Lax` buys us nothing here and gives up the protection `Strict` provides against
top-level-navigation and `window.open` tricks.

**Why this does not reduce our CSRF work.** The same registrable-domain scoping cuts the other way: any sibling host
on our parent domain — including a dangling subdomain someone takes over — is treated as same-site, and its requests
will carry our cookie. The cheat sheet lists the conditions under which `SameSite` alone could suffice (sole control of
the registrable domain, no state-changing GET endpoints, `Strict` or `Lax` plus `__Host-`, plus origin verification)
and says anything short of all of them means `SameSite` is defence in depth beside a token. We are not in a position to
claim all of them, so the standard's synchronizer-token requirement stays mandatory — which it already is. Two
corollaries worth carrying into ticket 08: audit that **no** GET endpoint mutates state (the cheat sheet calls this the
most common way SameSite defences fail), and consider `Origin`/`Sec-Fetch-Site` verification as an additional layer,
noting the CSRF cheat sheet's caveat that proxies sometimes strip those headers.

Also confirmed correct in the standard and worth keeping: `HttpOnly` and `Secure` are both described as mandatory for
session cookies; non-persistent session cookies are preferred over `Max-Age`/`Expires`; the `Domain` attribute should
be left unset; session ID rotation on login to defeat fixation is required (and OWASP Top 10:2025 A07 lists reusing
the same session identifier after login as a weakness). The standard's CORS rules — explicit allowlist,
`allowCredentials=true` only for listed origins, no wildcard — match the CSRF cheat sheet's guidance for a
cookie-authenticated cross-origin API exactly, including its warning against allowing subdomains by regex.

**Recommendation for ticket 08:** upgrade session and CSRF cookies to `SameSite=Strict`, add the `__Host-` prefix, keep
`HttpOnly` + `Secure` + no `Domain` + `Path=/`, keep the CSRF token. Record it as a deviation-stricter-than-standard,
which needs no ADR justification beyond this citation. Note the local-dev friction: `__Host-` and `Secure` require
HTTPS, and the PRD concedes local dev is HTTP — so the cookie name and `Secure` flag must be profile-driven, and that
belongs in the HTTPS/HSTS handover notes the map already lists.

**Flagged to:** 08 (Decide session management and the CSRF contract); 14 (frontend architecture — CORS credentials and
the cross-origin call shape); 15 (threat model — sibling-subdomain and subdomain-takeover path); 16 (cookie attribute
assertions).

---

### 7. Argon2id vs BCrypt — the standard's stated preference is current; the PRD's BCrypt is the weaker option, and the ADR should say so

**Verdict: the standard's preference ordering is current guidance. What is *not* current is applying its BCrypt
carve-out to this app — the carve-out is for existing systems, and this is a new one. BCrypt stays (PRD mandate, user
decision), but the ADR must state the trade-off rather than imply parity.**

[OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
ranks them:

- **Argon2id** first, minimum `m=19456` (19 MiB), `t=2`, `p=1`; several equivalent settings are listed trading CPU
  against RAM (`m=47104, t=1, p=1` through `m=7168, t=5, p=1`).
- **scrypt** when Argon2id is unavailable: `N=2^17`, `r=8`, `p=1` minimum.
- **bcrypt** — it "should only be used for password storage in legacy systems where Argon2 and scrypt are not
  available", work factor ≥ 10, with the 72-byte input limit enforced.
- **PBKDF2** (600,000 iterations, HMAC-SHA-256) when FIPS-140 validation is required.

It also notes newer algorithms should be chosen for new applications, with older ones acceptable for legacy systems
given appropriate configuration. Read against that, the App Standard's own wording — prefer Argon2id or scrypt, BCrypt
acceptable *for existing systems* — is faithful to current guidance. The standard is not stale on item 7. We are the
ones taking the exception, and the standard's text does not actually cover our case.

NIST does not pick a winner: §3.1.1.2 requires a suitable password hashing scheme with salt ≥ 32 bits, a cost factor as
high as practical and raised over time, and a stored reference to the scheme and cost factor. It also *recommends*
(SHOULD) an additional keyed hash or encryption pass with a verifier-only secret key held in an HSM or TEE — a pepper,
in OWASP's vocabulary, which OWASP likewise offers as optional defence in depth. Both are SHOULDs; we are not taking
them, and the reason is on the map: secrets handling for non-local environments is unresolved. Worth a line in the
deferral register rather than silence.

**What makes the trade-off bounded.** Spring Security ships `Argon2PasswordEncoder` and `SCryptPasswordEncoder`
alongside `BCryptPasswordEncoder`, and `DelegatingPasswordEncoder` stores the algorithm id in the hash itself
(`{bcrypt}$2a$10$…`, `{argon2}…`), matching on the stored id while encoding new passwords with `idForEncode`
([Spring Security reference](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)).
Migration is therefore re-hash-on-next-login, not a data migration — and the cheat sheet describes exactly that
upgrade pattern. Two implications for other tickets: use `DelegatingPasswordEncoder` with `idForEncode = "bcrypt"`
rather than a bare `BCryptPasswordEncoder` (cheap now, and the only thing that makes migration cheap later), and size
the credential column for the prefixed format plus room for a longer Argon2 hash (ticket 12).

**Honest ADR wording for ticket 17** — the trade-off, stated plainly:

- BCrypt is **not** the recommended algorithm for a new system under current OWASP guidance; it is the legacy option.
- The reason we use it is the PRD mandate and the recorded stack decision, not a security argument.
- The concrete cost is memory-hardness. BCrypt's ~4 KiB working set does not meaningfully penalise GPU or ASIC
  attackers; Argon2id's 19 MiB floor is the entire point of the algorithm. Offline cracking of a stolen hash file is
  materially cheaper with BCrypt at equal verification latency.
- The second cost is functional: the 72-byte input ceiling (item 5) is a BCrypt artefact. Argon2id has no such limit,
  so choosing BCrypt is what forces the maximum-length rule and the passphrase restriction on our users.
- Mitigations we take: work factor ≥ 10 tuned by measurement, `DelegatingPasswordEncoder` so the hash records its own
  algorithm and cost, a 72-byte cap enforced in our own validator, and breached-password screening (item 4) which
  reduces the population of crackable hashes far more than the algorithm choice does.
- Not taken: pepper / keyed second pass (NIST SHOULD, OWASP optional) — blocked on unresolved secrets handling.

**Flagged to:** 07 (parameters); 17 (the ADR — this item exists to feed it); 12 (credential column width and the `{id}`
prefix).

---

### Things checked that came back clean

Worth recording so the next reader does not re-verify them:

- **Session ID rotation on login**, server-side session state, invalidation on logout, and idle plus absolute timeouts
  — all current (OWASP Session Management; OWASP Top 10:2025 A07 names session-ID reuse after login and missing
  invalidation as weaknesses). The standard's 15-minute idle / 8-hour absolute defaults are *stricter* than NIST's
  AAL1 recommendation (30-day overall, no inactivity timeout required) and roughly align with AAL2 (24-hour overall,
  1-hour inactivity). No conflict.
- **Per-account failed-login counters rather than per-IP** — explicitly endorsed by the OWASP Authentication Cheat
  Sheet. The map's dual-limiter decision is correct, not redundant.
- **Generic identical responses across auth outcomes, including response timing** — current (OWASP Authentication
  Cheat Sheet's enumeration section, with the "quick exit" worked example; OWASP Top 10:2025 A07 recommends identical
  messages for all outcomes). CVE-2025-22234 above is a live example of how easily the timing half breaks.
- **Current password required for self-service change** — current (OWASP Authentication Cheat Sheet's change-password
  section, with the shared-computer abuse case; CWE-620 is mapped under A07:2025).
- **CSRF token from a dedicated endpoint, session-bound, required on all state-changing requests, kept on logout** —
  current. The synchronizer token pattern is still OWASP's primary recommendation for stateful applications, and
  custom-header transmission (our `X-CSRF-TOKEN`) is described as more secure than a hidden form field. The standard's
  instruction not to exempt logout from CSRF is consistent with that guidance. One addition available if ticket 08
  wants it: the cheat sheet now describes **Fetch Metadata** (`Sec-Fetch-Site`) as a straightforward modern layer with
  ~98% browser coverage, usable alongside the token.
- **Reset tokens: `SecureRandom`, SHA-256 stored rather than a slow hash, single-use, 30-minute expiry, all sessions
  invalidated on reset** — consistent with current guidance; a high-entropy random token does not need an adaptive
  hash. Re-authentication after a reset is called for by the OWASP Authentication Cheat Sheet's risk-events section,
  which the standard's session invalidation satisfies.
