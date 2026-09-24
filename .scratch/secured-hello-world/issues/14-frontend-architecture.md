# 14 — Design the frontend architecture

Type: prototype
Status: open
Blocked by: 05, 06, 08, 20

## Question

How is the SPA structured, and how does it behave around auth state, CSRF, and errors — when the
session cookie is HttpOnly and therefore invisible to JavaScript?

This is a prototype ticket rather than pure discussion: the screens and the interceptor behaviour are
far easier to judge from a rough, concrete artifact than from prose. Build cheap mocks, react to
them, then decide. Prototype code is throwaway and does not become the build.

## Settled going in

React 19.3 + Vite + TypeScript strict + shadcn/ui on Base UI + React Hook Form + Zod + TanStack
Query. Base UI for real focus management and ARIA. Separate origin from the API, CORS with
credentials.

## What to decide

**Auth state discovery.** The session cookie is HttpOnly, so the SPA cannot read it and has no
client-side way to know whether it is logged in. Decide the mechanism: call the self-read endpoint
from "Decide the admin module" on app load and treat 401 as logged-out. Then decide the
consequences — what renders during that first in-flight request (a splash? a skeleton? nothing?), and
how a hard refresh on a protected route avoids a visible flash of the login screen.

**CSRF handling.** Where the token is fetched, where it is held (in memory, never `localStorage`),
how it is attached to state-changing requests, and the retry policy on a 403 from a stale token —
which "Decide session management and the CSRF contract" settles; implement whatever it decided.
Include the pre-login bootstrap ordering.

**The logout edge case.** Logout keeps CSRF protection and so returns 401/403 on an already-expired
session. The standard is explicit that the SPA must catch this via a global interceptor, clear local
state, and redirect to login **without showing the user an error**. Design that interceptor.

**Routing and guards.** Route structure, the protected-route wrapper, the admin-only route guard,
and where an unauthorised user lands. Client-side guards are UX only — server-side checks are the
real control, and the design should make that obvious so nobody later mistakes the guard for
security.

**Error presentation.** How the error envelope from "Decide the API error envelope" renders. The hard
part: generic auth errors must stay generic in the UI too. A helpful "that account is locked"
message in the frontend leaks exactly what the backend worked to hide. Decide the copy for the
generic failure, and for the registration and reset flows where the response is deliberately
uninformative — the user needs to know what to do next without being told whether the account exists.

**Forms.** Zod schemas mirroring the server-side password policy, and the rule that client validation
is convenience only and never authoritative. Decide how the two stay in sync, since a drifting
client policy produces confusing rejections.

**Accessibility.** What Base UI gives for free and what still needs doing: labels, error association
via `aria-describedby`, focus management on error, live-region announcements for async failures, and
keyboard paths through the admin table and its confirm dialogs.

**Screens to mock.** Login, register, verify-email landing, forgot password, reset password, the
hello-world greeting, and the admin user list with its enable/disable, role-change, and delete
confirmations.

## Done when

A rough mock exists and is linked from this ticket, the auth-state and interceptor behaviours are
decided, the generic-error copy is written, and the component and route structure is recorded.
