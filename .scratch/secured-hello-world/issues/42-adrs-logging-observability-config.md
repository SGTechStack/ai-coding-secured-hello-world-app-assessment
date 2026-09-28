# 42 — Write the platform ADRs: logging, observability, configuration and topology

Type: task
Status: resolved
Blocked by: 34
Blocks: 18

## Question

Write the ten ADRs that [ticket 34](34-adr-candidate-list.md) routed to this ticket. They go to `docs/adr/`, in
the `domain-modeling` skill's ADR format: **ADR-054 to ADR-063**. That covers the following:

- keyed log hashes, the `AuditEvent` enum and console duplication;
- the dev-only reset-link logger;
- `SameSite=Strict`, two origins and the three-layer CSP;
- Actuator exposure;
- secrets binding;
- trace restart.

*Split from [36](36-adrs-platform.md) by ticket 34.* It is owner 36's work, by 17 Answer §2's source split.

ADR-063 must carry its reopening triggers in its own text (17:285–291).

The ID, title, merged sources, attached amendments and filter answer for each ADR are in
[`adr-routing/routing.md`](../adr-routing/routing.md) §2.

## Rules

Same as [35](35-adrs-authentication.md) §Rules.

## Done when

- ADR-054 to ADR-063 exist, and their `docs/adr/README.md` index rows move from `reserved`.
- The grep in 35's Done-when returns nothing for these files.

---

## Answer

**All ten are written, `accepted`, and linked from the index.** Files are `docs/adr/0054-…` to `0063-…`. None
regroups 34's routing. ADR-059 and ADR-063 carry `## Reopening triggers` sections. ADR-061 and ADR-062 also gained
short ones.

**Done-when check.**
- Index rows ADR-054 to ADR-063 moved from `reserved` to `accepted`, with links (targeted row edits only; the index
  is shared with 35 and 39–41).
- The ticket 35 grep (`\b\d{2}:\d+`, `ticket \d`, `\d{2} ADR`, `.scratch`) plus a bare `\bticket\b` returns **0**
  matches across the ten files.
- Every `ADR-`, `REJ-`, `R-` and `T-` ID they cite (109 distinct) resolves in the index, `docs/register/register.md` or
  `docs/test-plan/test-plan.md`.
- ADR-063 carries all three triggers named at 17:285–291 (upstream gateway, SPA `traceparent` with the
  `allowedHeaders` tripwire, outbound traced call), plus the correlation-field and re-enable triggers. The glossary's
  **Trace restart** term already exists in `CONTEXT.md`.

### 1. External facts checked at source for this ticket

Checked fresh against the standards in the repo, ASVS 5.0 on GitHub, W3C CSP3, W3C Trace Context, RFC 6265bis, the
HTML Standard, Vite 8.3.1 docs, `@vitejs/plugin-react` source, the Spring Boot 4.1 reference and 4.1.x source, the
Spring Framework source, Micrometer's `OtlpConfig`, the ECS reference and the Logback manual. Each ADR's `## Sources`
lists what it rests on.

### 2. What the checking changed

None of these reverses a decision. Each changes what an ADR says.

1. **ADR-062's filter sentence was half wrong.** IM8 as-8's own text names no annotation. It says to store secrets
   in appropriate solutions. `@Value("${...}")` is only in the `im8-review` skill's check. Boot's docs also do not
   say to avoid `@Value`. They recommend `@ConfigurationProperties` for grouped keys. The ADR says both.
2. **ADR-061's filter sentence overstated.** `health`-only exposure is already Boot's default, and Prometheus
   scraping is a documented pattern, not a default. Micrometer's OTLP URL also falls back through
   `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` and `OTEL_EXPORTER_OTLP_ENDPOINT` before `localhost:4318`.
3. **ADR-062's fourth secret sits outside its rule.** `management.otlp.metrics.export.headers.*` binds through Boot's
   own unvalidated `OtlpMetricsProperties`, and Micrometer reads `OTEL_EXPORTER_OTLP_*HEADERS` straight from the
   process environment.
4. **ADR-059's reopening trigger was wrong.** "A static host cannot nonce it, so `'unsafe-inline'` is the only
   alternative" is false: a CSP hash source allows a fixed inline script with no server. Only inline script whose
   content varies per response reopens the topology. Any `script-src` change, hashes included, still reopens the
   enrolment re-auth decline, which has no ADR and lives only in R-HDR-003.
5. **ADR-060: IM8 as-9's text does ask for a response header.** "as-9 accepts a meta tag" holds for the
   `im8-review` tool only. The meta tag satisfies the tool; the host header satisfies the control.
6. **ADR-058: same-site includes the scheme.** An `https` SPA calling an `http` API is cross-site. `Lax` would not
   rescue a cross-site deployment either, since it also withholds the cookie from cross-site `fetch`. The standard's
   own stated purpose for `Lax` describes `Strict`, and the ADR says so.
7. **ADR-054's filter sentence corrected.** `Log_Schema.md` defines `session.hash` as SHA-256 with no key named. It
   does not "prescribe plain SHA-256" against a keyed alternative.
8. **ADR-056's framing corrected.** stdout comes from the logging standard's §3.1 emission requirement, which is not
   labelled an Enforced Constraint, and it is not the only route to ASVS 16.4.3 (L2): §3.5 prefers a forwarding agent
   on the rolling file. The ADR frames the choice as §3.1 against §4.
9. **ADR-057: environment-variable log levels do not reach class-named loggers.** Boot's relaxed binding lowercases
   them. T-CFG-010 passes vacuously unless the logger's name is all lowercase or the test sets the level through
   `SPRING_APPLICATION_JSON`.

### 3. Owed elsewhere

- **Test plan** (amend the table by ID; ticket 38 owns `docs/test-plan/`):
  - `session.hash` must not equal the unkeyed SHA-256 of a known session id (ADR-054).
  - `emit` with a key outside the event's allowed set produces the degraded row (ADR-055).
  - Serialised reason codes match a committed list, so a renamed enum constant fails (ADR-055).
  - An audit row reaches stdout as well as the audit file; `additivity="false"` passes today (ADR-056).
  - T-CFG-010's override path must actually reach the logger (ADR-057, §2 item 9).
  - The CSP meta tag precedes every `<script>` and `<link>` in the built `index.html` (ADR-060).
  - T-HDR-003's preview setup is unstated and unworkable as written: preview's default port is 4173, not on the CORS
    allow-list, and the production `connect-src` names the real API origin (ADR-060).
  - R-CFG-019 has no test: set each presence-only property to an unresolved `${…}` and assert refresh fails (ADR-062).
  - T-AUD-014 cites only the governing standard's §3.3, but two of its three N/A events come from the logging
    standard's §3.4.
- **Register, handed to ticket 18** (ticket 33 is resolved; amend `docs/register/register.md` by R-ID):
  - R-CFG-007 and R-CFG-012: correct the as-8 wording (§2 item 1).
  - R-CFG-020: add the `OTEL_*` header fallback. R-OBS-007 or R-OBS-008: add the `OTEL_*` endpoint fallback.
  - R-HDR-003: add the hash-source nuance (§2 item 4).
  - R-SES-004: its title says "session and CSRF cookies", but no CSRF cookie exists (ADR-036, REJ-009).
  - New inputs: the logging standard §4 separate-destination deviation and ASVS 16.2.3 (L2) by documentation
    (R-AUD-013 cites ADR-056 but is the 16.4.3 row); ASVS 3.3.2 (L2), 3.4.3 (L2, with its unmet L3 clause) and
    3.4.6 (L2).
- **Routing** (`adr-routing/routing.md` §2): the ADR-054, 061 and 062 filter sentences are superseded by the ADR
  text. Not edited here; it dies with `.scratch/`.
- **Ticket 18 (compliance gate):** §2 items 1, 4 and 5 are where a reviewer grading IM8 as-8 and as-9 should look.
- **Unconfirmed, and stated as such in the ADRs:** whether an empty `management.tracing.propagation.consume` disables
  extraction (ADR-063); the exact Micrometer version Boot 4.1 manages (ADR-061).

### Handover items (ticket 25)

No new items. Each deployer obligation these ADRs point at is already a register/handover row: R-OPS-002 (same-site,
one scheme), R-HDR-005 (static host sends the preview header set), R-HDR-006 (production CORS origin), R-OBS-007
(OTLP export and header secret), R-OBS-015 (never re-enable trace continuation), R-AUD-032 (trace headers out of the
Tomcat access-log pattern), R-AUD-013 (forward the audit stream) and R-CRED-020 (no real account under `dev`).

Status: resolved.
