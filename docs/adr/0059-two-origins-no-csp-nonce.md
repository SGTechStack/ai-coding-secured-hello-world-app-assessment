---
status: accepted
---

# ADR-059: Two origins, not Boot serving the bundle, and therefore no CSP nonce

The SPA is a static bundle served from its own origin, and the API is a second origin: `http://localhost:5173` and
`http://localhost:8080` in development. The strongest single-origin alternative is Spring Boot serving the built
bundle itself. It is declined, and with it the only topology here that could put a per-response CSP nonce on the
document. No nonce is used anywhere. That is safe only while the built document contains no inline script, so this
ADR carries its own reopening triggers.

## Context

- The PRD's Overview gives the frontend and the backend each "its own origin", with CORS "to allow the frontend
  origin, with credentials". Its Non-Functional & Security Requirements, declared binding on every story, require an
  "Explicit allow-list of the frontend origin(s); `Access-Control-Allow-Credentials: true`". The ports it names are
  examples. `5173` is Vite's default dev-server port.
- The standard's §5 Security Headers and CORS Tests prescribes two CORS tests: a preflight from an allowed origin
  succeeds, and a request from a non-allowlisted origin is rejected. On one origin there is no cross-origin request
  to test.
- The standard's §3.5 wants security headers "at the application level using the framework's built-in security
  configuration" and calls infrastructure-level injection "not a substitute". If Boot served the bundle, Spring's
  headers would land on the document exactly as the standard writes them. That is the real pull of the option.
- A nonce must be fresh and unguessable on every response. Vite's `html.cspNonce` adds a `nonce` attribute to script,
  style and stylesheet or modulepreload link tags, and injects a `<meta property="csp-nonce">` placeholder. Vite's
  documentation warns that the placeholder must be replaced with a unique value for each request. A static host
  serves the same bytes every time, so it cannot do that.
- A server in the document path could. Spring Security's CSP support writes a fixed `policyDirectives` string and
  does not generate nonces, so this would be custom code: a filter that mints the value and rewrites `index.html`.
- The built document has no inline script to permit. A Vite production build references external module scripts
  and stylesheets. The React Refresh preamble, an inline `<script type="module">`, is injected by
  `@vitejs/plugin-react` only when Fast Refresh is on, which it is not in a production build. So `script-src 'self'`
  is already as strict as a nonce policy would be.
- A nonce is not the only way to allow an inline script. A CSP hash source (`'sha256-…'`) allows one inline script
  with fixed content, and needs no server. Only inline script whose content changes per response needs a nonce.
- ASVS 5.0 3.4.3 (L2) accepts a global policy that "defines either an allowlist or uses nonces or hashes". Only L3
  requires a per-response policy with nonces or hashes.

## Considered options

- **One origin behind a reverse proxy.** Hosting infrastructure is out of scope in the PRD, and it offers nothing
  the Boot option does not.
- **Spring Boot serves the bundle.** Spring's headers reach the document, CORS disappears and a nonce becomes
  possible. But it removes a binding PRD control, leaves the two prescribed CORS tests with no subject, and couples
  the Node build into the Maven build. Day-to-day work would still run on the Vite dev server, which does not emit
  Spring's headers, so the gain would appear only in a configuration nobody runs.
- **Two origins in development, one in production.** Cookie and CSP behaviour would then differ between what is
  tested and what ships.
- **Two origins, no nonce (chosen).**

## Decision

The SPA and the API stay on separate origins, with CORS allowing credentials for the SPA origin only. Production is
described as configuration and asserted by tests, never run (R-CFG-004). No nonce is generated or configured:
`html.cspNonce` stays unset, and no static nonce appears in any file. The production document policy uses
`script-src 'self'`. The built `index.html` must contain no inline script, and no component may insert one.

## Consequences

- The CORS configuration and both prescribed CORS tests stay alive: T-HDR-007, T-HDR-008. The production allow-list
  value is a deployer input (R-HDR-006).
- The cookie posture depends on the two origins staying same-site (ADR-058, ADR-036, R-OPS-002).
- The document's CSP cannot come from Spring. ADR-060 decides how it is delivered, and R-HDR-005 puts the header on
  the static host.
- "No inline script" is enforced by T-BLD-002 (the built `index.html`), T-HDR-006 (mounted components) and T-BLD-003
  (`build.chunkImportMap` false, `build.assetsInlineLimit` 0).
- A static nonce would look strict to a scanner while being trivially bypassable, which is why none is wired in.
- R-HDR-003 records the triggers below.

## Reopening triggers

- **The production document needs inline script whose content varies per response**, such as server state or
  per-user bootstrap data. Reopen this topology decision. A static host cannot mint a nonce, so the choices are a
  server in the document path (the Boot option above) or `'unsafe-inline'`. An inline script with fixed content does
  not reopen the topology, because a hash source can allow it. It still changes `script-src`, so the next trigger
  fires.
- **Any change to the production `script-src`**: a hash or nonce, `'unsafe-inline'` outside the dev-server
  carve-out (REJ-060), or `data:`. Each also reopens the decision not to require password re-entry on TOTP
  provisioning and confirmation. That decision has no ADR. R-HDR-003 records its two reasons. First, a
  session-riding XSS must defeat `script-src 'self'` with no inline script and no inlined assets. Second, NIST
  SP 800-63B-4 §4.1.2.1 binds at the lower of the available and target AAL. A relaxed `script-src` removes the
  first reason.

## Sources

- PRD, Overview; Out of Scope ("Containerization / CI/CD / hosting infra"); Non-Functional & Security Requirements
  ("CORS").
- Standalone User Access Control Application Standard §3.5 Security Contract, §5 Security Headers and CORS Tests.
- W3C Content Security Policy Level 3: §2.3.1 Source Lists (nonce and hash sources), §6.7.3.3 Does element match
  source list for type and source? (hash of an inline element's content).
- Vite 8.3.1 documentation: Features, Content Security Policy (`'nonce-{RANDOM}'` and its warning; `data:`); Shared
  Options, `html.cspNonce`.
- `@vitejs/plugin-react` source (`vite:react-refresh`, `transformIndexHtml` injecting the preamble only when Fast
  Refresh is not skipped).
- Spring Security reference, Servlet, Security HTTP Response Headers, Content Security Policy (`policyDirectives`).
- OWASP ASVS 5.0: 3.4.3 (L2; L3 clause on per-response nonces or hashes).
