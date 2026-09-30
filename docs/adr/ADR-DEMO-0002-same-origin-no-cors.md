---
status: proposed
---

# Serve SPA and API same-origin; no CORS

The SPA and API stay same-origin: Spring serves the SPA under `/app` in prod, and the Vite dev server proxies `/api`, `/admin/api`, `/login` and `/logout` to the backend. No CORS configuration is added, cookies stay `SameSite=strict`, and CSRF stays enabled on all state-changing endpoints. Cross-origin with credentials would widen the CSRF and cookie attack surface the template deliberately avoids.

## Considered Options

- **Separate origins with CORS and credentials** — rejected: needs `SameSite` relaxation, an allow-list to maintain, and absolute API URLs in the SPA, for no functional gain.
