---
status: accepted
---

# ADR-058: `SameSite=Strict`, not the standard's `Lax`

The session cookie is set with `SameSite=Strict` in every profile. The governing standard mandates `Lax`, and its
own test list prescribes a test that asserts `Lax`, so this implementation fails that prescribed test on purpose. A
maintainer holding the standard would plausibly "fix" the value back to `Lax`. Everything would keep working, and
the session cookie would again travel on cross-site top-level navigations to the API.

## Context

- The standard says `Lax` in four places of its own text. §3.5 Security Contract, Session Management: "All session
  and CSRF cookies must be explicitly configured with" `HttpOnly=true`, `Secure=true` and `SameSite=Lax`. The §2
  sequence diagram shows the login response setting `SameSite=Lax`. §5 Security Headers and CORS Tests prescribes
  "Session and CSRF cookies have the Secure, HttpOnly, and SameSite=Lax attributes set." §6 Required Runtime
  Configuration lists `SameSite=Lax`. The standard's question inventory (Q27) and two of its recipes (Standalone
  Session Login with CSRF Bootstrap; Common Security Headers and SPA CSRF Configuration) repeat it.
- The PRD fixes no value. Its Non-Functional & Security Requirements ask only for "`SameSite` cookie attributes".
- The standard gives `Lax` this purpose: it "prevents the browser from sending cookies with cross-site requests".
  That describes `Strict`. Under RFC 6265bis §4.1.2.7, a `Strict` cookie is sent only with same-site requests, while
  a `Lax` cookie is also sent with cross-site top-level navigations (§5.6.7.1).
- RFC 6265bis §5.2 takes "same site" from the HTML Standard. A site is the scheme plus the host's registrable
  domain, or the host itself when it has none. Ports are ignored. `http://localhost:5173` and
  `http://localhost:8080` are therefore cross-origin but same-site (see "same-site" in the glossary).
- For a document in a top-level window, the "site for cookies" is that window's origin (RFC 6265bis §5.2.1). So the
  SPA's `fetch` calls to the API are same-site however the user reached the SPA, including from an emailed link.
- So `Lax` and `Strict` behave the same for all of the SPA's API traffic. They differ only when a hostile page
  navigates the top-level window straight to an API URL: `Lax` attaches the session cookie to that GET, `Strict`
  does not. The attacker cannot read the response, so this is hardening, not a hole. It costs nothing to close.
- The SPA document needs no cookie. It is a static file on its own origin, and sign-in state is discovered by
  `fetch` after load. Reset and activation links in email therefore work under `Strict`.
- ASVS 5.0 3.3.2 (L2) asks that each cookie's `SameSite` value be "set according to the purpose of the cookie".
- Only one cookie exists. The CSRF token is session-bound and never set as a cookie (ADR-036), so the "CSRF cookies"
  half of the standard's clause has no subject. REJ-009 reinterprets it.

## Considered options

- **`Lax`, the standard's value.** It passes the prescribed test as printed. It sends the session cookie on
  cross-site top-level GET navigations to the API, and buys nothing in return on this topology.
- **`None`.** It is sometimes assumed necessary for cross-origin cookies. It is not, because the two origins are
  same-site. It would send the cookie on every cross-site request and make the browser's `SameSite` layer disappear.
- **`Strict` (chosen).** It matches the purpose the standard itself states. It changes no behaviour the SPA uses.

## Decision

The session cookie carries `SameSite=Strict` in every profile, together with `HttpOnly`, `Path=/` and no `Domain`
attribute. The standard's §5 cookie test is implemented, but it asserts `Strict`. It carries a comment naming this
ADR, so the deliberate failure of the prescribed `Lax` test is visible at the exact point a reviewer checks it.

## Consequences

- **A prescribed test fails by design.** A checklist-driven assessor reads it as failed. R-SES-004 records the
  deviation. T-SES-002 asserts `Strict` with the comment, T-SES-011 asserts it under the non-dev profile with the
  `__Host-` name (REJ-061), and T-E2E-002 shows a browser sending the cookie from the SPA origin to the API origin.
- **Reverting to `Lax` fails T-SES-002.** That is the intended tripwire. The revert would otherwise pass the
  standard's checklist.
- **The posture needs the SPA and API to stay same-site.** A different registrable domain, or a different scheme
  (an `https` SPA calling an `http` API), makes every SPA request cross-site. The cookie is then withheld, the CSRF
  bootstrap fails, and nobody can sign in (ADR-036). R-OPS-002 hands this to the deployer.
- `Lax` would not rescue such a deployment. It also withholds the cookie from cross-site `fetch`. Only `None` would,
  and that would reopen ADR-036's CSRF analysis. So the same-site constraint does not come from choosing `Strict`.
- The value is set explicitly, so browser defaults for cookies without `SameSite` never apply.

## Sources

- Standalone User Access Control Application Standard §2 Sequence Diagram (login response), §3.5 Security Contract
  (Session Management), §5 Security Headers and CORS Tests, §6 Required Runtime Configuration (HTTP Security and
  CORS); its question inventory Q27; the recipes Standalone Session Login with CSRF Bootstrap and Common Security
  Headers and SPA CSRF Configuration.
- PRD, Non-Functional & Security Requirements ("Session security").
- draft-ietf-httpbis-rfc6265bis (Internet-Draft): §4.1.2.7 The SameSite Attribute, §5.2 "Same-site" and
  "cross-site" Requests, §5.2.1 Document-based requests, §5.6.7 The SameSite Attribute.
- WHATWG HTML Living Standard §7.1.1.1 Sites ("same site"; port ignored).
- OWASP ASVS 5.0: 3.3.2 (L2).
