# 01: Backend skeleton and security configuration baseline

**What to build:** A Spring Boot backend that starts up, serves one unauthenticated endpoint,
and has its security posture correct from the first commit rather than retrofitted later. Nobody
can use it yet — this is deliberate **prefactoring** so that every ticket after it inherits CORS,
CSRF, security headers, sanitised errors and correct cookie attributes for free instead of
bolting them on nine tickets deep.

The one visible behaviour: a caller can hit an unauthenticated liveness endpoint and get a
successful JSON response.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

**IM8 controls:** `as-2` Parameterised Interfaces; `as-7` Access Control Check Enforcement;
`as-9` Content Security Policy; `as-10` HSTS; `as-11` Session Management; `as-13` Exposure of
Internal System Details; `as-14` Secure Cryptographic Libraries; `dp-3` Data in Transit
Encryption. *ASVS: V14 Configuration, V7 Error Handling, V3.4 Cookie-based Session
Management, V9 Communication, V13 API.*

- [ ] Spring Boot app starts on its own origin with Spring Security and Spring Session on the
      classpath and active
- [ ] Spring Data JPA configured against H2 under a `dev` profile, with the schema written so
      it ports to Postgres/MySQL without rework
- [ ] All repository access goes through Spring Data derived queries or **bound parameters**
      (`?1`, `:name`); concatenating or interpolating user input into a JPQL string or into a
      `@Query(nativeQuery = true)` string is prohibited. Stated explicitly because it is the
      rule an auditor needs a citable line for, not because the happy path is likely to break it
- [ ] CORS is an explicit **allow-list** of the frontend origin — never a wildcard — with
      `Access-Control-Allow-Credentials: true` so the session cookie travels cross-origin
- [ ] CSRF protection is enabled and enforced on every state-changing request, in a form a
      cross-origin JavaScript client can actually satisfy
- [ ] The filter chain is **default-deny**: the last matcher is `anyRequest().authenticated()`,
      so an endpoint added nine tickets later and forgotten in the matcher list fails closed
      rather than open. The public matchers are a closed allow-list — liveness, register, login,
      password-reset request and password-reset confirm — and nothing else
- [ ] Session cookie attributes are profile-conditional: `HttpOnly` always on, `Secure` on in
      the production profile and off in `dev` (local HTTP is the accepted gap), `SameSite` set
      explicitly rather than left to the container default
- [ ] The production profile configures TLS (`server.ssl.*` or an SSL bundle) with a **TLS 1.2
      floor** — SSLv3, TLS 1.0 and TLS 1.1 are rejected. No `http://` URL is hardcoded anywhere
      in the source, so the deployment target is a configuration decision
- [ ] No accept-all `X509TrustManager` and no `HostnameVerifier` that returns `true`
      unconditionally exists in application code **or test code** — a trust-everything verifier
      written to make an integration test pass is the usual way this ships to production
- [ ] Exactly one `PasswordEncoder` bean is the only hashing path in the app, and
      `java.security.SecureRandom` is the only source of randomness for anything
      security-relevant (tokens, salts, identifiers). `java.util.Random` and `Math.random()` are
      prohibited there, and no algorithm is hand-rolled — the library does the cryptography
- [ ] Security response headers are set (at minimum: content-type options, frame options,
      referrer policy, and a content security policy)
- [ ] The **content security policy is named and minimally permissive**, not merely present:
      `default-src 'self'`, `frame-ancestors 'none'`, `object-src 'none'`, `base-uri 'self'`,
      and neither `unsafe-inline` nor `unsafe-eval` appears anywhere in the production policy
- [ ] `Strict-Transport-Security` is set in the production profile with at least
      `max-age=31536000; includeSubDomains`. It is profile-conditional rather than absent
      because the header is meaningless over plain HTTP in `dev` — a browser ignores it — while
      shipping prod without it leaves the first request downgradeable
- [ ] A global error handler returns sanitised JSON for every unhandled exception: no stack
      traces, no framework internals, no database detail, no class names reach the client
- [ ] Information disclosure is closed off by configuration as well as by the handler:
      `server.error.include-stacktrace=never` and `server.error.include-message=never`, the H2
      console disabled outside `dev`, and no stack trace, SQL fragment, framework class name or
      library version string in any response body on any path
- [ ] An unauthenticated liveness endpoint returns success and is reachable without a session
- [ ] Test: a cross-origin preflight from the configured frontend origin is permitted, and one
      from an unconfigured origin is not
- [ ] Test: a state-changing request without a valid CSRF token is rejected
- [ ] Test: under the production profile the response carries `Content-Security-Policy` and
      `Strict-Transport-Security` with the expected directive and `max-age` values — asserting
      the values, not just that the headers exist
- [ ] Test: a request to an endpoint absent from the public allow-list returns 401, not 200,
      which is what proves default-deny is actually wired rather than assumed
- [ ] Test: an endpoint that throws returns a sanitised body containing no stack trace
