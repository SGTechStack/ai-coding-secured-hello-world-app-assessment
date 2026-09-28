# ADR-0004: CORS via explicit origin allow-list and minimal method/header set

## Status

Accepted

## Context

The frontend and backend run on separate origins and the session cookie
must travel cross-origin, so CORS needs `Access-Control-Allow-Credentials:
true`. Browsers reject a wildcard `Access-Control-Allow-Origin` whenever
credentials are enabled, so an explicit origin allow-list is required, not
optional. `SecurityConfig`'s CORS configuration:

```java
configuration.setAllowedOrigins(List.of(allowedOrigins));   // ${app.cors.allowed-origins}
configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
configuration.setAllowCredentials(true);
```

- Origins come from external config per profile: `http://localhost:3000` in
  dev, `${CORS_ALLOWED_ORIGINS}` with no default in prod (fails fast if
  unset, rather than silently allowing nothing or something unintended).
- The allowed-methods and allowed-headers lists are a deliberate minimal
  set matching the app's actual verbs (`PATCH` for admin role/status
  updates) and the one custom header the frontend sends
  (`X-XSRF-TOKEN`) — not a wildcard, and not "whatever happened to make an
  error go away" during development.
- CORS preflight (`OPTIONS`) is explicitly `permitAll()`'d ahead of the
  deny-by-default authorization rule, since a preflight request never
  carries cookies or the CSRF header anyway and would otherwise be rejected
  before the browser gets a chance to see the real CORS headers.

## Decision

Keep the allow-list explicit and minimal rather than defaulting to a
wildcard or a broad allow-everything CORS policy, even though it means
every new frontend-consumed header or HTTP verb requires a config change
here.

## Consequences

- Adding a new custom request header or HTTP method on the frontend
  requires updating `allowedHeaders`/`allowedMethods` in `SecurityConfig` —
  this is a deliberate friction point, not a bug, to prevent CORS scope
  from silently widening.
- Prod deployment must set `CORS_ALLOWED_ORIGINS` or the app fails to start
  — this is intentional fail-fast behavior and should not be "fixed" with a
  permissive default.
