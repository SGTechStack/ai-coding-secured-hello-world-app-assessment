# Frontend skeleton

Type: grilling
Status: resolved
Blocked by: 01

## Question

Pin the React skeleton so implementation can start:

- **UI library:** which one (shadcn/ui, MUI, Ant, other)? Stack is already fixed at Vite + TypeScript; demo bar is "credible, not designed".
- **Page inventory:** login, register, forgot-password, reset-password (token link target), protected `hello`, admin user list (+ enable/disable, role change, delete controls). Anything else?
- **Router** (react-router assumed — confirm) and route guards.
- **Auth-state detection:** how the SPA learns it's logged in on load — `GET /api/hello` probe vs a dedicated `/api/auth/me`-style endpoint (if the latter, feeds ticket 07).
- **Fetch conventions:** `credentials: 'include'` wrapper, base-URL config for the `localhost:8080` origin.
- **CSRF client handling:** how the SPA obtains and presents the token — **depends on ticket 01's answer**, which is why this is blocked.
- **Styling/polish bar:** how far past "functional" should the demo UI go?

## Answer

Decided:

- **UI library:** shadcn/ui + Tailwind CSS (Vite + TypeScript already pinned).
- **Page inventory (all six):** login, register, forgot-password, reset-password, protected `hello`, admin user-management panel (user table + enable/disable, role change, delete controls). react-router assumed; route guards gate protected + admin routes.
- **Auth-state detection:** dedicated `GET /api/auth/me` — returns current principal or 401; called on app load (added to ticket 07's endpoint list).
- **CSRF client handling (from ticket 01):** call `GET /api/auth/csrf` at app start and after login/logout; read `XSRF-TOKEN` cookie, send it as `X-XSRF-TOKEN` header on every mutating request; `credentials: 'include'` on all calls to `localhost:8080`.
- **Polish bar:** design-taste polish — the build session invokes the `design-taste-frontend` skill for the SPA.
