# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Existing codebase: React 19 + Vite + TypeScript frontend (own origin, e.g. `localhost:3000`); Spring Boot 3.3 + Spring Security + Spring Session + Spring Data JPA (H2 dev / Postgres-portable) backend (own origin, e.g. `localhost:8080`), REST API. Not a user decision for this project — the stack was fixed by the PRD.

## Users

- **Visitor** — unauthenticated, arrives to register or log in.
- **User** — authenticated account holder (`USER` role), views a personalized greeting after login.
- **Admin** — authenticated account holder (`ADMIN` role), manages other users' accounts (list, enable/disable, role change, delete).

Assumed evaluation context: this is a coding-assessment reference/demo app. The realistic "user" reading and clicking through the UI is a technical reviewer verifying that the security behavior specified in the PRD (lockout, generic error messages, session handling, admin guardrails, etc.) is correctly implemented and clearly demonstrable — not a mass consumer audience. Default assumption per user instruction; not separately confirmed.

## Product Purpose

A production-grade security baseline for username/password authentication — demonstrating cookie-based session auth, account lockout/IP throttling, password reset via single-use tokens, and role-based admin user management — built as a reference implementation rather than a shortcut demo.

## Positioning

Not a commercial product; a reference/demo app. Its differentiator is doing the unglamorous security plumbing correctly (BCrypt, lockout, enumeration resistance, CSRF/CORS, session invalidation, audit logging) rather than visual differentiation.

## Operating Context

- Frontend and backend run on separate origins locally; CORS is configured with credentials enabled so the session cookie travels cross-origin.
- Auth is server-side session via secure HttpOnly cookie (Spring Session), not JWT (JWT is documented as an alternative in the PRD appendix, not built).
- Local dev runs over HTTP; `Secure` cookie / HTTPS is a documented production assumption, not present locally.
- No real email delivery — password reset uses a stub `EmailService` that logs the reset link instead of sending mail.
- Six screens/views exist today, currently built with plain semantic HTML and no visual styling applied (Vite-starter global CSS only): Login, Register, Forgot Password, Reset Password, Hello (post-login greeting), and Admin Users (table with per-row enable/disable, role toggle, delete).

## Capabilities and Constraints

- Registration requires unique username, unique email, password length ≥ 12.
- Login is generic on failure (never reveals whether a username exists); account lockout after N failed attempts; IP-level throttling independent of per-account lockout.
- Logout invalidates the server-side session; replayed cookies post-logout are rejected.
- Password reset: single-use hashed token, 15–30 min expiry, resets invalidate all existing sessions for that user.
- Admin endpoints are role-gated server-side (`403` for non-admins); an admin cannot disable, demote, or delete their own account via the admin endpoints.
- An initial admin account is seeded on first startup from configuration if no `ADMIN` exists yet.
- Out of scope (explicitly, per PRD): JWT implementation, MFA/2FA, real SMTP, containerization/CI/CD/hosting infra, local HTTPS, granular per-resource authorization beyond the USER/ADMIN check.

## Brand Commitments

None. No name, logo, or identity constraints exist beyond the working title "Secured Hello World App." Visual direction is open — treat as a from-scratch visual world for an internal security-tooling / admin-console-style surface, not a customer-facing brand.

## Evidence on Hand

None. No real user testimonials, case studies, or production data exist; this is a synthetic reference app. Do not fabricate any.

## Product Principles

1. Correctness and clarity of security behavior outrank visual expression — a reviewer must be able to see lockout, generic errors, and role gating working, not just look at pretty screens.
2. Every screen must stay fully functional and accessible (labeled fields, `role="alert"` error surfacing, keyboard operability) — polish is additive, never a replacement for working semantics already in the code.
3. No invented branding, testimonials, or claims — this is an unbranded reference implementation.
4. Treat Login/Register/Forgot/Reset as a cohesive Operate-mode auth flow family; treat Admin Users as an internal console/table surface with its own density-appropriate conventions.

## Accessibility & Inclusion

No project-specific requirement beyond standard web accessibility: existing forms already use `<label htmlFor>` associations, `autoComplete` hints, and `role="alert"` for error messaging — preserve and extend these patterns rather than replacing them.
