# Profiles, config and audit logging

Type: grilling
Status: resolved
Blocked by: —

## Question

Settle the configuration layout:

- **Profiles:** `dev` vs `prod` property split — cookie `Secure` flag, H2 (file vs in-memory) + console exposure, CORS origin list source (property, not hardcoded), session timeout value.
- **Tunables as config:** lockout threshold/window/cooldown, IP-throttle threshold/window, password min length — which become `app.*` properties vs constants?
- **Admin seed creds:** env-var/property names (`app.admin.username`, `app.admin.password`); dev default convenience vs prod fail-fast when absent — how strict is "documented, never hardcoded" in a demo?
- **Structured audit logging:** format (SLF4J key=value args vs JSON encoder like logstash-logback), required fields per the PRD's audit list (login success/failure, lockout, reset requested/completed, admin actor+target actions), and confirmation that passwords/tokens never appear.

## Answer

Decided:

- **Profiles:** `dev` + `prod`. dev: in-memory H2 + console enabled, `Secure=false`, localhost CORS. prod: `Secure=true`, H2 console off, CORS origins env-driven.
- **Tunables:** all externalized as grouped `app.*` properties — lockout threshold/window/cooldown, IP-throttle values, password min length, session timeout, admin seed creds.
- **Admin seed creds:** `app.admin.username`/`app.admin.password` env-backed with a documented dev default; prod profile fails fast when absent.
- **Audit logging:** JSON via logstash-logback encoder — genuinely machine-parseable. Required events per PRD: login success/failure, lockout triggered, reset requested/completed, admin actor+target actions (role change, enable/disable, delete). Passwords and tokens never logged.
