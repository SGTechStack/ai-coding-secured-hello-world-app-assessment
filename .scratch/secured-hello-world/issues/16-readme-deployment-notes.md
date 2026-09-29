# 16: README deployment notes

**What to build:** An operator preparing a real deployment reads the README and learns everything they must add around the app, and every Standard or IM8 deviation they are accepting. It documents the app as built, which is why it comes last. See the spec's Further Notes deployment checklist, "Security configuration" (TLS, secrets, metrics), and ADR 0001's acknowledged and accepted deviations.

**Blocked by:** 07 (IP-hash HMAC key), 13 (Bootstrap Admin's required first-login change), 14 (management port)

**Status:** ready-for-agent

- [ ] The README covers:
  - TLS 1.2+ outside `dev`, including to the database.
  - The single-instance requirement.
  - A secrets manager for the Bootstrap Admin password, HMAC key and datasource credentials, with HMAC key rotation.
  - The Bootstrap Admin's first-login change, and that the `dev` fallback must never be used outside local development.
  - Forwarding the audit file to central logging with at least 90 days' retention, write-once storage, restricted access, and alerting when audit events stop.
  - The headers the SPA host must send, and setting `VITE_SECURITY_CONTACT`.
  - Never routing the management port publicly.
  - The production CORS allowlist, and reverse-proxy and NAT caveats for the IP Throttle.
  - The IM8 deviations accepted for this reference implementation.
- [ ] The Postgres and MySQL Flyway migrations (`db/migration/postgresql`, `db/migration/mysql`) are run against real Postgres and MySQL databases, with Hibernate `ddl-auto=validate` passing. Until now only H2 has been tested (reviewer decision "Non-H2 migrations", issue 03).
- [ ] Every configuration property the README names matches the property the app actually reads, and following the README from a clean checkout starts both applications in `dev`.
