# Handoff

## State — 2026-09-29

All twelve PRD stories are implemented on **jingshun**. The React/TypeScript UI
supports registration, login, reset request/confirmation, protected greetings and
admin account management. The Java 21/Spring Boot API uses BCrypt, CSRF, exact
credentialed CORS, JDBC Spring Session, JPA/H2, Flyway and structured auditing.

The repository uses conventional commits. Main was updated from origin before
branching; implementation stays on jingshun. No production deployment is included.

## Verification

- `backend/mvnw.cmd verify`: **18 tests passed, zero failures/errors/skips**;
  Spotless formatting check and executable JAR packaging passed.
- `frontend/npm test`: **11 tests passed**.
- `npm run lint`, `npm run format:check`, `npm run build`: all passed.
  The build includes strict TypeScript checking.
- `npm audit --omit=dev --audit-level=moderate`: zero reported vulnerabilities.
- A packaged-JAR HTTP smoke test against port 18080 exercised registration,
  login, greeting, USER role denial, generic reset request, reset single-use and
  session revocation, admin role/status/deletion, and logout. The Vite frontend
  served successfully on port 3000. Ports 8080/8081 were already occupied by
  unrelated services and were left untouched.
- A fresh independent code review found a throttle-window boundary bypass and a
  Unicode audit sanitization error. Both have regression tests and are fixed.
  Additional tests cover concurrent throttling, production cookie flags,
  bootstrap idempotency, session expiry and signed-in password reset navigation.
- Live visual browser verification was unavailable: the browser tool reported
  no available browser. UI behavior was verified with React Testing Library;
  actual browser layout and mobile appearance still merit a manual check.
- Temporary application servers were stopped after verification. No secrets,
  development databases, node_modules, build outputs or logs are committed.

## Running and reviewing

Follow the root README. Set your own initial administrator password; none is
provided by the application. Start backend with the `dev` profile and frontend
with `npm run dev`, then visit http://localhost:3000.

Use `docs/requirements.md` for the story/test mapping and `docs/security.md` for
deployment assumptions and the JWT alternative. The next optional review step is
to open the app in a browser and inspect desktop/mobile presentation. HTTPS,
SMTP, an external production database and a shared deployment-scale limiter are
deployment work outside this assessment's scope.
