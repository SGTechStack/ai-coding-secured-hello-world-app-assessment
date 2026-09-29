# 09 — HTTP security: CSRF bootstrap, CORS and headers

Type: grilling
Status: open
Blocked by: 01
Map: [Secured Login App](../map.md)

## Question

How are CSRF protection, CORS and security headers configured for a cross-origin React SPA using cookie-based sessions?

Answer `Q25` (frontend type), `Q26` (external-domain resources, which drives CSP) and `Q27` (CORS origin allowlist and credential policy), against [`Common_Security_Headers_and_SPA_CSRF_Configuration.md`](../../../App-Standards/Appfw-User-Standards/Shared_Recipes/Common_Security_Headers_and_SPA_CSRF_Configuration.md) and [`Standalone_Session_Login_with_CSRF_Bootstrap.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Session_Login_with_CSRF_Bootstrap.md).

The PRD's NFRs make this load-bearing rather than boilerplate: CSRF enabled for **all** state-changing endpoints precisely because auth is cookie-based; an explicit origin allowlist with `Access-Control-Allow-Credentials: true`; `HttpOnly`, `Secure` (prod) and `SameSite` cookie attributes; session-fixation protection (`prd/assessment-prd.md:117`).

The hard part is the **CSRF bootstrap**: a cross-origin SPA cannot read an `HttpOnly` CSRF cookie, and it needs a token before its *first* state-changing call — which includes login and registration, both unauthenticated. Settle how the SPA obtains its first token, which cookie/header names are used, and what `SameSite` value permits the cookie to travel from `localhost:3000` to `localhost:8080` at all. Note the tension to resolve explicitly: `SameSite=Strict` would break the cross-origin flow, while `SameSite=None` requires `Secure`, which local HTTP cannot provide — and local HTTPS is out of scope. State how dev and prod differ, and record the accepted dev gap.

Also decide the security-header set (HSTS, CSP, `X-Content-Type-Options`, frame options) and whether HSTS is configured despite local HTTP.

Blocked on 01 (origins and path shape).
