# 20 — Decide the origin topology: two origins, or one behind a reverse proxy

Type: grilling
Status: resolved
Blocked by: —

## Question

Do the SPA and the API stay on separate origins as the PRD specifies, or sit behind a single origin
via a reverse proxy?

This was never a ticket because the PRD appeared to settle it. Two independent findings from the
research tier have turned it back into a real decision, and it blocks both the session/CSRF ticket
and the frontend ticket.

## Why it reopened

**Finding 1 — the inherited cookie posture is wrong for two origins.** From "Verify the App
Standard's controls are still current practice": OWASP now names `SameSite=Strict` preferred over
`Lax`, and recommends the `__Host-` cookie prefix for session IDs. `SameSite` is scoped to the
registrable domain rather than the origin, so on our topology `Strict` costs nothing and is free
protection we are currently declining. But the App Standard mandates `Lax` (confirmed in two places
by "Inventory the prescribed recipes": the headers recipe §3 Step 2, and
`Standalone_Session_Login_with_CSRF_Bootstrap.md` line 40), and its recipes appear to assume a single
origin serves both.

**Finding 2 — the CSP obligation lands where it does nothing.** From "Inventory the prescribed
recipes": `Common_Security_Headers_and_SPA_CSRF_Configuration.md` sets
`default-src 'self'; object-src 'none';` inside the Spring Security filter chain, so the header ships
on **API** responses. The API returns JSON, never a document, so the policy governs no browsing
context, and `'self'` resolves to the API origin rather than where the SPA's scripts live. Worse, App
Standard §3.5 requires the headers "at the application level" and calls infrastructure-level
injection "not a substitute" — pointing the control at the one origin where it has no effect while
ruling out the one place it would work. The standard's own question inventory (Q26) only asks which
external domains to whitelist; it has no slot for *which origin the policy protects*. Meanwhile IM8
**as-9** FAILs outright if no CSP is found.

Both problems dissolve under a single origin. That is two independent arguments pointing the same
way, which is why they belong in one decision rather than being patched separately in tickets 08 and 14.

## Corrections to the framing above (made while resolving — read these first)

Four premises in this ticket's own body are wrong. They are left in place above so the reasoning is
traceable, but do not act on them.

1. **"Its recipes appear to assume a single origin serves both" — false.**
   `Common_Security_Headers_and_SPA_CSRF_Configuration.md` Step 4 is an explicit CORS policy
   (`setAllowedOrigins(props.getAllowedOrigins())`, `allowCredentials=true`, `X-CSRF-TOKEN` in allowed
   headers), justified by "Because the CSRF token is exposed via the `/csrf` JSON endpoint, its security
   relies entirely on the browser's Same-Origin Policy." And Q27 of the question inventory offers
   "CORS enabled, credentials allowed — Required for cookie-based session auth from different origin"
   as a first-class branch. The corpus supports two origins deliberately.
2. **"Both problems dissolve under a single origin" — half false, and it was the load-bearing claim.**
   Finding 1 was never a problem: `SameSite` is scoped to the registrable domain, so `localhost:5173`
   and `localhost:8080` are already same-site and `Strict` already works under two origins (ticket 19
   had this right). Nothing dissolves. The "two independent arguments pointing the same way" is one
   argument (CSP), and the cookie question is independent of topology.
3. **`localhost:3000` — not binding.** PRD line 9 reads "React, its own origin (e.g. `localhost:3000`)";
   `5173` and `vite` appear nowhere in the PRD. The ports are illustrative. What *is* stated twice is
   "its own origin", and CORS-with-credentials sits in the requirements block that line 114 calls
   binding on every story. Settled below as **5173**, matching ticket 19.
4. **IM8 as-9 does not require a header.** The `im8-review` as-9 check reads `index.html` for a
   `<meta http-equiv="Content-Security-Policy">` tag as an accepted delivery mechanism and FAILs only
   when CSP is absent from *both* backend config and the meta tag. The as-9 FAIL is closable without
   touching the topology.

## What to decide

- **Two origins (PRD as written)** — keeps `localhost:3000` / `localhost:8080`, CORS with
  credentials, and accepts that: the SPA origin needs its own CSP delivered by whatever serves the
  static bundle (outside every recipe's scope, so we design it ourselves), `X-Frame-Options` on the
  API does not protect the SPA document so `frame-ancestors` must cover it, and the `SameSite` choice
  must be re-argued against the standard's `Lax` mandate.
- **One origin behind a reverse proxy** — the SPA and `/api/**` served from a single host. Closes the
  CSP gap (the recipe's CSP then lands on the SPA document as written), makes `Strict` trivially
  correct, keeps the standard's `Lax` mandate satisfiable, and removes CORS entirely. Costs: it
  contradicts the PRD's explicit "Cross-origin: CORS configured on the backend" premise and its
  stated `localhost:3000` / `localhost:8080` split, and infra is out of scope so the proxy is either
  a Vite dev proxy (dev only, papering over the production question) or a documented deployment
  requirement.
- **A hybrid** — separate origins in dev, single origin in production. Tempting, and dangerous: the
  cookie and CSP behaviour then differs between the environment we test and the one we ship, which is
  how `SameSite` and CSP bugs reach production undetected.

Whichever wins, decide and record:

- The `SameSite` value, and whether `__Host-` is used. Note from "Pin down the Spring Security 7
  config surface" that `__Host-` works with Spring Session but forces a **per-profile cookie name**,
  because browsers reject the prefix over plain HTTP.
- Which host emits which CSP, and the full directive set for the document context —
  `connect-src` must name the API origin, plus `frame-ancestors`, `form-action`, `base-uri`,
  `script-src`, `style-src`.
- Whether the API-side CSP is retained as defence in depth. Worth keeping (it hardens Boot's `/error`
  page) but it must be recorded as defence in depth, so a later reviewer does not read a passing
  `curl -I` against the API as the obligation being met.
- The deployment note that follows, since `SameSite` protection depends on the SPA and API remaining
  same-site. If they ever aren't, the cookie posture breaks — a deployment-blocking fact.

## Done when

The topology is chosen, the `SameSite` and `__Host-` decisions follow from it, the CSP owner and
directive set are written down for the document-serving origin, and the deployment constraint is
recorded for whoever ships this.
## Answer

**Two origins, as the PRD specifies — `http://localhost:5173` (SPA) and `http://localhost:8080` (API) —
with `SameSite=Strict`, `__Host-SESSION` outside dev, and a document CSP we own and deliver in three
layers. Production exists as a documented deployment requirement only, not as a configuration we run.**

The reverse-proxy option is dropped outright: it buys nothing that the Boot-serves-the-bundle variant
below doesn't, and infrastructure is out of scope.

### 1. Production is a document, not an environment

The PRD requires TLS for "any real deployment" (line 121) while putting containerization, CI/CD and
hosting infra out of scope (line 21) and local HTTPS out of scope (line 22), so **no component is
nominated to terminate TLS and no production origin is ever named**, yet `Secure` is scoped `(prod)` *Consolidated into the register (ticket 33): R-OPS-003. Amend the table by ID, not this list.*
(line 117). Settled: we build, run and test exactly one configuration — two localhost origins over
HTTP. Every prod-only value (`Secure`, HSTS, `__Host-` prefix, real origins, the strict CSP) is
expressed as profile config **asserted by a test but never executed against a real deployment**, and
that limitation is stated plainly rather than implied. This is what keeps "dev is the shape we ship"
an honest claim instead of a pretence that a production topology was validated. *Consolidated into the register (ticket 33): R-CFG-004. Amend the table by ID, not this list.*

### 2. Why two origins won, including the option this ticket missed

A third shape exists that the ticket didn't consider: **Spring Boot serves the built SPA bundle from
its own static resources** — one origin, no proxy, no nginx, no container. That is the strongest
single-origin option and it was evaluated on its merits:

For it: CSP and `X-Frame-Options: DENY` land on the real document exactly as the recipe writes them;
§3.5's "the framework's built-in security configuration" is satisfied literally; CORS disappears into
Q27's "CORS disabled" branch; a reviewer runs one command; and — the advantage that nearly changed the
outcome — **Spring could mint a per-request CSP nonce**, which a static file server structurally cannot.

Against it, decisively:

- It deletes a **binding** PRD requirement. Line 119 ("Explicit allow-list of the frontend origin(s);
  `Access-Control-Allow-Credentials: true`") sits in the block line 114 declares binding on every story,
  and lines 9–11 assert two origins three times over. For a plan that is formally assessed against the
  PRD, removing a named control reads as non-compliance, not as an improvement.
- It makes **two prescribed tests unimplementable**. The standard's test list requires "CORS preflight
  requests from allowed origins succeed with appropriate headers" and "CORS requests from
  non-allowlisted origins are rejected, and wildcard origins are not permitted". Under one origin there
  is no preflight to test.
- Its security advantage largely evaporates in practice, because its dev story is still the Vite dev
  server, which does not emit Spring's headers. The CSP would be real only in the configuration nobody
  runs day to day — the same divergence, relocated.
- It couples Node into the Maven build (`frontend-maven-plugin` or a copy step), adding a supply-chain
  surface the PRD never asked for.
- The nonce capability, its one unique advantage, is **an advantage we have no use for** — see §5. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-007, T-HDR-008. Amend the table by ID, not this list.*

The hybrid (two origins in dev, one in prod) stays rejected for the reason the ticket gave, and is now
moot anyway: with production as a document, there is only one topology.

### 3. Cookie posture

**`SameSite=Strict`**, deviating from the standard's `Lax` mandate — which appears in four places
(§3 "must be explicitly configured", the login sequence diagram at line 158, the CSRF bootstrap
recipe's `application.yml`, and Q27). The cost is sharper than "needs an ADR": the standard's own test
list at line 499 asserts "Session and CSRF cookies have the Secure, HttpOnly, and `SameSite=Lax` *Consolidated into the register (ticket 33): R-SES-004. Amend the table by ID, not this list.*
attributes set", so a checklist-driven assessor reads `Strict` as a failed prescribed test. Mitigation
is to assert `Strict` in our test with a comment pointing at the ADR, so the deviation is visible at
the exact point a reviewer checks it. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-002. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-SES-004. Amend the table by ID, not this list.*

Why `Strict` is nonetheless right, and why it is **independent of the topology**: the two origins are
cross-*origin* but same-*site* (same registrable domain, same scheme), so the session cookie travels on
same-site XHR under either value — `Lax` and `Strict` are behaviourally identical for our API traffic.
They differ only on cross-site top-level navigation: `Lax` sends the session cookie when a hostile page
links directly to an API GET endpoint. The response is unreadable cross-origin, so this is hardening
rather than a hole, but it costs nothing to close. The document load needs no cookie (auth state is
discovered by XHR), so `Strict` has no UX consequence, including for emailed reset and activation links.

**`__Host-` prefix adopted**: `SESSION` in dev, `__Host-SESSION` in the non-dev profile, with a
profile-scoped test asserting the name. The prefix requires `Secure`, `Path=/` and no `Domain`, so
browsers reject it over plain HTTP and a per-profile name is unavoidable (as ticket 05 flagged). The
concrete justification is cookie-tossing: the deployment shape recommended in §7 puts the SPA and API
on sibling subdomains, which is exactly the case the prefix defends. No conflict with that shape — the
cookie is host-locked to the API host and the SPA's XHR still carries it. Recorded honestly as config *Consolidated into the register (ticket 33): R-CFG-004. Amend the table by ID, not this list.*
we never exercise against real HTTPS. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-SES-011. Amend the table by ID, not this list.*

### 4. The document CSP — three layers, and who owns each

Q26's answer is recorded as **no external domains**: no CDN, no Google Fonts, no analytics. The policy
is closed.

**Production document policy** (`.env.production`):

```
default-src 'self'; script-src 'self'; style-src 'self';
connect-src 'self' https://<api-origin>; img-src 'self' blob:; font-src 'self';
object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'
```

Three directives are deliberately tighter than the recipe's `default-src 'self'; object-src 'none';`.
`form-action 'none'` is correct because the SPA submits via fetch and never an HTML form, so an injected
credential-harvesting form is dead. `base-uri 'none'` kills `<base>` hijacking of relative script URLs.
`img-src 'self' blob:` exists for exactly one feature — the TOTP enrolment QR, which ticket 22 found is
fetched and turned into an object URL. **If ticket 23 delivers the QR as a base64 `data:` URI instead,
this directive changes with it.** No `report-to`: there is no collector, and inventing one would paper
over the monitoring gap IM8 `lm-16` already FAILs on.

**Development document policy** (`.env.development`): identical except `script-src 'self' 'unsafe-inline'`
and `style-src 'self' 'unsafe-inline'`, with `connect-src 'self' http://localhost:8080`. The two
relaxations exist solely for the Vite dev server's own injected code — `@vitejs/plugin-react` injects the
React Refresh preamble as an inline `<script type="module">`, and HMR injects CSS as `<style>` elements.
Neither appears in `dist/`. No `'unsafe-eval'`: Vite dev transforms via native ESM and does not need it.
The other seven directives stay identical to production **on purpose**, so that the violations ordinary
feature work introduces — a Google Font, a second API origin, a `data:` image — are caught while
developing. The dev policy is therefore a **development-time lint for the production policy, not a
security control**, and must be described that way wherever it is documented. `as-9` flags
`unsafe-inline` only in `script-src`, so the entire flagged exposure is one token in one dev-only file.

Three delivery layers, because no single one suffices:

1. **`<meta http-equiv="Content-Security-Policy" content="%VITE_CSP%">` in `index.html`**, substituted
   per mode. Vite has supported `%VITE_X%` replacement in HTML since 4.2. This is *not* an
   environment-invariant floor, and cannot be: **multiple CSP policies intersect** — a resource must
   satisfy every active policy — so a meta tag carrying `default-src 'self'` without the API origin in
   `connect-src` blocks every API call no matter how permissive the header is. Templating is what makes
   the meta tag possible at all; its actual job is to be the fail-safe when a static host sends no header.
2. **A real header via Vite `server.headers` (dev) and `preview.headers` (preview)**, carrying the full
   policy including `frame-ancestors`, which the CSP spec ignores in a meta tag along with `report-uri`
   and `sandbox`. Verifiable with `curl -I` against the serving port.
3. **The same header set as a documented obligation** on whoever serves the static bundle in a real
   deployment — see §7.

**`vite preview` is the CSP verification surface.** It serves the built artifact under
`preview.headers`, so the document is byte-identical to what a static host would serve and the policy
under test is the production one. Compliance evidence for `as-9` points at `.env.production` and the
preview run, never at `.env.development` or at `vite dev`. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-HDR-004. Amend the table by ID, not this list.*

### 5. No nonce anywhere — and the condition that reopens this

A nonce exists to permit *specific* inline scripts while blocking injected ones. A Vite production build
emits external module scripts and an external stylesheet, so **there is no inline script to permit** and
`script-src 'self'` is already exactly as strict as `script-src 'nonce-…'` would be. `'strict-dynamic'`
would buy nothing either, since our allowlist is just `'self'`. Nonces earn their complexity when inline
code is unavoidable — SSR hydration state, analytics snippets — and we have neither.

Vite does offer first-class nonce support (`html.cspNonce` adds the nonce to `<script>` and `<style>`
tags and to stylesheet and modulepreload `<link>` tags, and injects a `<meta property="csp-nonce">` whose
value Vite reuses in dev and after build). It is declined for a specific reason: Vite's own documentation
warns the placeholder must be replaced with a unique value per request or the policy is trivially
bypassed, and **nothing in a static-hosting topology rewrites it per request**. A nonce committed to
`vite.config.ts` is knowingly bypassable while appearing strict to a scanner, and wiring the mechanism
in at all invites a later reader to reuse it in production without a rewriter.

Per-request nonce generation *is* possible — but only by putting a server in the document path, i.e. the
Boot-serves-the-bundle option in §2. An edge function or nginx `sub_filter` is out-of-scope
infrastructure, and a handover note demanding per-request rewriting that the deployer skips leaves a
static placeholder in production, which is worse than having no nonce at all.

**Reopening trigger, recorded deliberately:** if the production document ever needs an inline script,
this topology decision must be reopened, because a static host cannot nonce it and `'unsafe-inline'`
would be the only alternative. Two consequences follow immediately and are listed in §6. *Consolidated into the register (ticket 33): R-HDR-003. Amend the table by ID, not this list.*

### 6. The API-side CSP is retained as defence in depth — and labelled as such

The recipe's `default-src 'self'; object-src 'none';` stays **verbatim** so recipe-conformance checks
pass unchanged, plus `frame-ancestors 'none'`, plus the framework's default `X-Frame-Options: DENY`. It
governs no browsing context on a JSON-only origin, but it hardens Boot's `/error` page and anything that
accidentally returns HTML.

**The label is the deliverable.** It must be recorded as defence in depth in three places — a comment at
the config site, the ADR, and the compliance evidence itself — because the headers recipe's own
verification step says to use `curl -I` to confirm all five headers, and against `:8080` that passes
while protecting nothing. Without the label, a later reviewer reads a green `curl -I` against the API as
§3.5 being satisfied, which is the precise misreading this ticket exists to prevent.

§3.5's "Infrastructure-level header injection is complementary but not a substitute" is satisfied on the
API by the Spring filter chain. For the SPA document it is satisfied by the templated meta tag plus the
Vite-emitted header, which are application artefacts rather than infrastructure. Where a real deployment
puts the policy on the static host, that is recorded as a **known argument surface**: the document's CSP
is then infrastructure-delivered, and §3.5 can be read as prohibiting that. The compensating position is
that the meta tag ships inside the application artefact and applies regardless. *Consolidated into the register (ticket 33): R-HDR-004. Amend the table by ID, not this list.*

### 7. Deployment constraints for whoever ships this

- **The cookie posture holds only while the SPA and API share a registrable domain.** Sibling subdomains
  (`app.example.com` + `api.example.com`) are the recommended shape: still cross-origin, still same-site,
  so `Strict` survives, CORS stays as configured, and `__Host-` remains correct. **If they ever land on
  different registrable domains, `SameSite=Strict` breaks the session outright** — deployment-blocking,
  not a tuning detail.
- The static host must emit the production header set, including `frame-ancestors 'none'`, which the meta *Consolidated into the register (ticket 33): R-OPS-002. Amend the table by ID, not this list.*
  tag cannot carry.
- Someone must terminate TLS. The PRD requires it and nominates nobody. *Consolidated into the register (ticket 33): R-HDR-005, R-OPS-003. Amend the table by ID, not this list.*
- Never allow `localhost` origins in the production CORS allow-list — the standard's Q27 says so
  explicitly, and our dev allow-list value is `http://localhost:5173`. *Consolidated into the register (ticket 33): R-HDR-006. Amend the table by ID, not this list.*

These three plus the four contents already owed from other tickets are why the handover document
graduated from fog into **ticket 25**.

### 8. Constraints handed to other tickets

- **Ticket 08 (session and CSRF):** `SameSite=Strict`, cookie name `SESSION` / `__Host-SESSION` by
  profile, the CORS allow-list value `http://localhost:5173`, and the same-site deployment constraint are
  all settled here. Do not re-decide them; its "Cross-origin specifics" and "Per-profile cookie config"
  sections are closed.
- **Ticket 14 (frontend architecture):** four hard constraints. Wrap the app in Base UI's `CSPProvider`
  with **`disableStyleElements`** — Base UI injects inline `<style>` elements in `ScrollArea.Viewport` and
  in `Select.Popup`/`Select.List` under `alignItemWithTrigger`, and since we have no server rendering the
  document we cannot mint a per-request nonce, so disabling them is the only available route. Set
  **`build.assetsInlineLimit: 0`** so no `data:` URIs are emitted, keeping `img-src` and `font-src`
  narrow. Keep **`build.chunkImportMap` false** — it emits an inline `<script type="importmap">`, which
  CSP treats as inline script. And **confirm the built `index.html` contains no inline script**; if one
  appears, `build.modulePreload.polyfill: false` removes it. Good news from Base UI's docs: `style-src-attr`
  governs style attributes parsed from server-prerendered HTML and does not affect client-side JavaScript
  that sets styles, and React applies the `style` prop through the CSSOM — so Floating UI's dynamic
  positioning needs no `'unsafe-inline'` at all.
- **Ticket 16 (test plan):** the CSP assertion runs against **`vite preview`**, not `vite dev`. Also owed:
  a profile-scoped assertion on the cookie name, a `SameSite=Strict` assertion carrying the ADR comment,
  and the two prescribed CORS tests (allowed origin preflight succeeds, non-allowlisted origin rejected)
  which the chosen topology keeps alive.
- **Ticket 23 (TOTP flows):** `img-src 'self' blob:` assumes the QR arrives as a blob. Changing to a
  base64 `data:` URI requires changing this directive.
- **Ticket 24 (secrets and config):** the CSP is environment-specific and lives in `.env.development` /
  `.env.production`, which are build-time client config, not secrets. The API origin appears in
  `connect-src` and in the CORS allow-list — two places that must agree per environment. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-BLD-002, T-HDR-007, T-HDR-008, T-SES-002, T-SES-011, T-HDR-004. Amend the table by ID, not this list.*

### 9. ADRs owed

1. `SameSite=Strict` instead of the standard's mandated `Lax`, naming the four places the standard says
   `Lax` and the prescribed test at line 499 that our implementation will fail by design. *Consolidated into the ADR routing (ticket 34): ADR-058. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-SES-004. Amend the table by ID, not this list.*
2. Two origins retained over the Boot-serves-the-bundle single origin, naming the nonce capability *Consolidated into the register (ticket 33): R-SES-004. Amend the table by ID, not this list.*
   forgone and the reopening trigger in §5. *Consolidated into the ADR routing (ticket 34): ADR-059. Amend by ID, not this list.*
3. The document CSP's three-layer delivery, the templated meta tag, and why CSP intersection semantics
   make an invariant meta floor impossible. *Consolidated into the ADR routing (ticket 34): ADR-060. Amend by ID, not this list.*
4. No nonce, justified by the bundle having no inline scripts rather than by the static nonce being weak. *Consolidated into the ADR routing (ticket 34): ADR-059. Amend by ID, not this list.*
5. The dev-only `'unsafe-inline'` relaxation, scoped to two directives, with `vite preview` as the
   verification surface. *Consolidated into the ADR routing (ticket 34): REJ-060. Amend by ID, not this list.*
6. `__Host-` adopted with a per-profile cookie name, including the admission that the non-dev profile is
   never exercised. *Consolidated into the ADR routing (ticket 34): REJ-061. Amend by ID, not this list.*
7. The API-side CSP retained as defence in depth, with the §3.5 argument surface in §6 recorded. *Consolidated into the ADR routing (ticket 34): REJ-062. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-HDR-004. Amend the table by ID, not this list.*

Also owed to ticket 17, as glossary terms rather than ADRs: **same-site vs cross-origin** has now been
re-derived independently in tickets 08, 19 and 20, and a research pass got it wrong (asserting
`SameSite=None` was required). It needs writing down once. Same for **document context** versus API
origin, which is the distinction the whole CSP half of this ticket turns on.

### 10. Questionnaire answers recorded

- **Q25** — SPA (React), CSRF token in the `X-CSRF-TOKEN` header, token endpoint at the default `/csrf`.
- **Q26** — no external domains. CSP whitelist empty; the policy in §4 is closed.
- **Q27** — CORS enabled, credentials allowed. Dev origin `http://localhost:5173`. Production origin
  undetermined by design (production is a document); the allow-list is a required handover input. *Consolidated into the register (ticket 33): R-HDR-006. Amend the table by ID, not this list.*

---

## Amendment from ticket 23 (TOTP enrolment, step-up, and factor-reset flows)

**Your CSP survives untouched, and it has acquired a dependent.**

**1. `img-src 'self' blob:` with no `data:` holds, and your constraint changed the design rather than the policy.**
Ticket 23's provisioning endpoint returns JSON carrying the QR PNG as **base64**, which the SPA turns into a Blob and
then an object URL — a `blob:` URL, already permitted. So `data:` stays out of the policy entirely, ticket 14's
directives do not change, and `assetsInlineLimit: 0` keeps its rationale. The option you flagged as "not a free
choice" was not taken.

**2. A CSP decision is now load-bearing for an MFA decision, which you should know about.** Ticket 23 declined
requiring fresh password re-entry on TOTP provisioning and confirmation. The threat that control would address is a
session-riding XSS silently binding an authenticator, and one of the two stated reasons for declining is that
**`script-src 'self'` with no inline script and `assetsInlineLimit: 0` means such an XSS must first defeat a policy we
own**. (The other reason is NIST SP 800-63B-4 §4.1.2.1, which requires binding at the *lower* of available and target
AAL and therefore does not compel re-authentication.)

Consequence for your reopening trigger: it currently fires on **any inline script in the production document**, on the
grounds that it would force the topology back open. It now has a second effect — relaxing `script-src`, adding
`'unsafe-inline'` outside the dev-server carve-out, or admitting `data:` to `script-src` also **invalidates ticket
23's rationale for declining re-authentication on enrolment**, and that decision would need reopening alongside the
topology. Worth adding to the trigger's text so the dependency is not discovered by whoever relaxes the policy. *Consolidated into the register (ticket 33): R-HDR-003. Amend the table by ID, not this list.*

**3. No new directive is needed.** The `otpauth://` URI never reaches an `<a href>`, an `<img src>`, the address bar or
history — it is rendered as text for manual entry only — so no scheme lands in any directive. Ticket 23 records it as
being as sensitive as the Base32 secret and therefore excluded from logging, telemetry and error-reporter breadcrumbs.

## Amendment from ticket 31 (IPv6 source keying)

This ticket never mentions IPv6, and nothing here is changed. For the record: whether the per-source limiters' IPv6
key does anything depends on whether the **public hostname publishes AAAA**, not on the origin. In proxy mode a
dual-stack edge forwards native IPv6 addresses to an IPv4-only origin. Without AAAA, IPv6-only clients arrive
through NAT64 as shared IPv4. Neither case is mandated; the deployer declares which applies
([ticket 31](31-ipv6-source-keying.md) §7 and its first handover item).
