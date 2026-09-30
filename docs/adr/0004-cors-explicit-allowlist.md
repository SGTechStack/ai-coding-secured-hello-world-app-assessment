# ADR-0004: CORS via explicit origin allow-list and minimal method/header set

## Status

Accepted

## Context

The frontend and backend run on separate origins, and the session cookie has to travel with
cross-origin requests. CORS therefore needs `Access-Control-Allow-Credentials: true`, and
browsers reject a wildcard origin when credentials are enabled.

## Decision

```java
configuration.setAllowedOrigins(List.of(allowedOrigins));   // ${app.cors.allowed-origins}
configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-Correlation-ID"));
configuration.setExposedHeaders(List.of("Retry-After", "X-Correlation-ID"));
configuration.setAllowCredentials(true);
```

- Origins come from per-profile configuration: `http://localhost:3000` in dev, and
  `${CORS_ALLOWED_ORIGINS}` with no default in prod, so startup fails if it's unset.
- Methods and headers are the minimal set the SPA actually uses:
  - `PATCH` for admin updates
  - `X-CSRF-TOKEN` (ADR-0003)
  - `X-Correlation-ID` (ADR-0011)
- Only `Retry-After` and `X-Correlation-ID` are exposed to the SPA's JavaScript.
- Preflight `OPTIONS` requests are `permitAll()`, placed ahead of the deny-by-default rule.

## Consequences

- A new frontend header or HTTP verb requires a change here. That friction is deliberate
  (`CorsConfigurationTest` asserts the exact lists).
- Prod must set `CORS_ALLOWED_ORIGINS`. Don't "fix" a missing value with a permissive default.
