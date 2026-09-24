# 02: Audit log for login and logout

**What to build:** An operator can see who logged in, who failed and who logged out, from which IP, as structured log lines on a dedicated `AUDIT` logger. The component is built so that later tickets only add events to it. See spec §Implementation Decisions › Audit log.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] An `AuditLog` component logs to the `AUDIT` logger through the SLF4J key-value API with fields `event`, `actor` (username or `anonymous`), `ip` and `outcome`, plus event-specific fields.
- [ ] The event vocabulary is fixed (an enum or constant set covering the spec's full list), so later tickets can't invent ad-hoc names.
- [ ] `LOGIN_SUCCESS`, `LOGIN_FAILURE` and `LOGOUT` are emitted at the right points.
- [ ] Every user-supplied value has CR/LF and other control characters replaced. A username containing `\r\n` is logged on one line.
- [ ] Passwords, CSRF tokens and session IDs never appear in any captured log output.
- [ ] Outside the `dev` profile, Spring Boot's structured console logging (ECS JSON) is on.
- [ ] Tests assert on captured log output at the HTTP seam, not on mocks. `mvn verify` passes.
