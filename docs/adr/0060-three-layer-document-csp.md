---
status: accepted
---

# ADR-060: Document CSP is delivered in three layers, with a templated meta tag

The SPA document's content security policy is delivered three ways. A `<meta http-equiv="Content-Security-Policy">`
tag in `index.html` takes its value from the Vite mode's environment file. Vite's dev and preview servers send the
policy as a response header. Whoever serves the bundle in a real deployment must send the same header. Because
browsers enforce every active policy, the layers look redundant. Each covers a gap the others leave, and removing
any one of them leaves a case with no protection.

## Context

- A CSP only acts in the document context: the browsing context that loads the SPA's HTML and scripts. The API
  returns JSON, so its CSP governs nothing and is kept only as labelled defence in depth (REJ-062). The document
  comes from a static host on its own origin (ADR-059), so Spring's header writer never touches it.
- The standard's §3.5 wants headers set "at the application level" and calls infrastructure-level injection
  "complementary but not a substitute". A header sent by a static host is infrastructure.
- CSP Level 3 §3.3 allows a policy in a `<meta>` element but ignores `frame-ancestors`, `report-uri` and `sandbox`
  there, and does not support report-only. A meta policy also does not apply to content that comes before it.
- CSP Level 3 §8.1: when several policies apply, a resource must pass all of them, so extra policies "can only
  further restrict". A fixed meta tag cannot be an environment-independent floor. If its `connect-src` lacks the
  environment's API origin, every API call is blocked whatever the header allows. The meta tag must carry each
  environment's own policy, which is why it is templated.
- Vite's HTML constant replacement substitutes `%NAME%` with values from `import.meta.env`, including `VITE_`
  variables from `.env.[mode]`. An unknown name is left in place, not replaced. A missing variable therefore ships
  the literal `%VITE_CSP%` as the policy, which restricts nothing. That failure is open, so it needs a build check.
- IM8 as-9 reads "Set minimally permissive CSP response headers", which asks for a header. The `im8-review` as-9
  check also accepts a meta tag in `index.html`, and fails only when neither a backend policy nor the meta tag is
  found. The meta tag satisfies the tool. Only the header satisfies the control's text.
- ASVS 5.0 3.4.6 (L2) wants `frame-ancestors` on every response, and calls `X-Frame-Options` obsolete. For the
  document that can only be a header. ASVS 5.0 3.4.3 (L2) wants `object-src 'none'` and `base-uri 'none'` as a
  minimum.
- In dev, `@vitejs/plugin-react` injects the React Refresh preamble as an inline module script, and HMR injects
  `<style>` elements. The dev policy adds `'unsafe-inline'` to `script-src` and `style-src` for them (REJ-060).

## Considered options

- **Header only.** A host that sends no header leaves the document with no policy at all. The whole control then
  rests on infrastructure, which §3.5 says is not a substitute.
- **A fixed meta tag as a floor, plus the header.** Impossible under intersection: one fixed `connect-src` cannot
  name every environment's API origin.
- **A templated meta tag only.** No `frame-ancestors`, so the document has no clickjacking protection, and IM8 as-9
  asks for a response header.
- **Three layers: templated meta tag, Vite-server header, host header obligation (chosen).**

## Decision

1. **Meta tag.** `index.html` carries `<meta http-equiv="Content-Security-Policy" content="%VITE_CSP%">` in `<head>`,
   before every `<script>` and `<link>` element. `VITE_CSP` comes from `.env.production` or `.env.development`. It
   never contains `frame-ancestors`. The production value is `default-src 'self'; script-src 'self'; style-src 'self';
   connect-src 'self' <api-origin>; img-src 'self' blob:; font-src 'self'; object-src 'none'; base-uri 'none';
   form-action 'none'`. The development value differs only in `connect-src` and in the two `'unsafe-inline'` tokens.
2. **Vite-server header.** `server.headers` (dev) and `preview.headers` (preview) send the same policy plus
   `frame-ancestors 'none'`. `vite preview` of the built bundle is the verification surface for the production
   policy. `vite dev` and the API origin are never evidence for it.
3. **Host header.** In a real deployment the static host sends the preview header set, including
   `frame-ancestors 'none'`. This is a deployer obligation (R-HDR-005).

`style-src` stays one directive (REJ-054). The referrer meta tag (REJ-053) may sit above the CSP meta tag, because
it fetches nothing.

## Consequences

- T-HDR-004 pins layers 1 and 2 on `vite preview`: the header with `frame-ancestors`, the meta tag without it, and
  the API policy refused as evidence. T-HDR-003 runs the app under the production policy with zero violations.
  T-HDR-002 keeps the API policy distinct. T-BLD-009 fails a build whose `index.html` still holds a `%VITE_`
  placeholder or names `localhost` in `connect-src`.
- **Until the host sends the header, the document has no `frame-ancestors`.** R-HDR-005 carries this, and nothing in
  the application can check it.
- Where the host sends the header, part of the control is infrastructure-delivered. R-HDR-004 records that as a
  known argument surface under §3.5, with the meta tag as the compensating application artefact.
- The API origin appears in `connect-src` and in the CORS allow-list. The two must agree in every environment.
- Removing the meta tag makes a misconfigured host fail open. Removing the Vite header loses `frame-ancestors` from
  every run. Removing the host obligation loses it from production. None of these removals breaks the app.
- **Open:** a preview run that exercises the API must use a page origin the CORS allow-list admits, and a
  `connect-src` that names the local API. Vite's default preview port is 4173, not 5173, and the production
  `connect-src` names the real API origin. The preview configuration behind T-HDR-003 is not yet stated.

## Sources

- Standalone User Access Control Application Standard §3.5 Security Contract (application-level headers).
- W3C Content Security Policy Level 3: §3.3 The `<meta>` element; §8.1 The effect of multiple policies.
- Vite 8.3.1 documentation: Env Variables and Modes, "HTML Constant Replacement"; Server Options, `server.headers`;
  Preview Options, `preview.headers` and `preview.port` (default 4173).
- `@vitejs/plugin-react` source (`transformIndexHtml` preamble injection).
- IM8 as-9 Content Security Policy (control text as given in the organisation's IM8 application policy reference;
  `im8-review` as-9 automated checks).
- OWASP ASVS 5.0: 3.4.3 (L2), 3.4.6 (L2).
