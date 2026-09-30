# 05: Security headers and dev-only H2 console

**What to build:** Every API response carries the security headers that block clickjacking, MIME sniffing and cross-origin leaks. The H2 console exists only in the dev profile. Tests confirm that only the frontend origins on the allow-list can make credentialed cross-origin calls.

**Blocked by:** 01 (Walking skeleton)

**Status:** ready-for-agent

- [ ] Every response carries HSTS (1 year, includeSubDomains), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'self'; object-src 'none'`, and a Permissions-Policy denying geolocation, microphone and camera.
- [ ] The headers are present on successful, error and anonymous responses alike.
- [ ] The `/csrf` response is not cacheable.
- [ ] Preflight from an allowed origin succeeds with credentials allowed, and preflight from any other origin is refused.
- [ ] The H2 console is reachable only in the dev profile, through its own dev-only security filter chain. In any other profile it doesn't exist.
- [ ] URL security rules use `PathPatternRequestMatcher` only.
