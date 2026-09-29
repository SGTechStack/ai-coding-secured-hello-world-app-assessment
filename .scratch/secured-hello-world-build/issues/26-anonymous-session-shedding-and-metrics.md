# 26: Anonymous session shedding and metrics

**What to build:** A flood of token fetches can't fill the database, and operators can monitor the app without it revealing anything.

- **Shedding.** New anonymous sessions from `GET /api/csrf` are shed when the session-row count or free disk crosses its limit (ADR-041). The shed response uses the envelope.
- **Health.** `ping`, `diskspace` and a shared-count `h2Data` indicator. `db` health and probes are off (REJ-063).
- **Metrics.** Pushed over OTLP, off by default. A profile that enables export must make the URL required (ADR-061). The authenticable-admins gauge is exported.

**Blocked by:** 09, 11

**Status:** done

- [x] Past the row limit, `GET /api/csrf` sheds while signed-in traffic continues.
- [x] A low-disk condition sheds anonymous sessions.
- [x] `/actuator/health` stays detail-free and reflects `h2Data`.
- [x] Enabling OTLP export without a URL stops startup.
