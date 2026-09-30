# 08: Audit emitter and stream

**What to build:** The audit stream that every later security event goes to (ADR-055):
- one `AuditEvent` enum and one `emit`, with typed context records;
- unknown keys are rejected, and `emit` takes no throwable;
- reasons come from sealed families and serialise by `code()`;
- `emit` fails soft at runtime and hard in tests.

Output is ECS through a custom structured encoder that redacts at source, with no masking decorator (REJ-001). It goes to a dedicated daily-rolled file with 90 archives and no total-size cap, plus a stdout copy in every profile (ADR-056; R-AUD-037). `source.ip_hash` and `session.hash` are keyed HMACs under the log key, and no address is ever logged (ADR-054). `url.path` is the matched route pattern. The raw URI appears only on pre-handler rows, capped at 256 characters (REJ-081). Inbound trace context is restarted at the boundary, baggage is off and `correlation.id` is dropped (ADR-063; REJ-087; REJ-088). The ASVS 16.1.1 log inventory is generated from the enum as a snapshot (R-AUD-027). A suite-wide *canary secret* scan fails any test run in which a known secret reaches any appender.

Emit the startup rows here. Later tickets add their events to the enum.

**Blocked by:** 06, 07

**Status:** done

- [x] An unknown context key fails in tests and degrades softly at runtime (T-AUD-043).
- [x] Rows carry `source.ip_hash` and `session.hash` as keyed hashes (T-AUD-042), and no raw address appears anywhere.
- [x] The file appender and the stdout copy both receive rows (T-AUD-044).
- [x] Reason codes serialise to their pinned values (T-AUD-045).
- [x] The catalogue snapshot regenerates from the enum, and drift fails the build.
- [x] An inbound `traceparent` is not continued, and baggage is not propagated.
- [x] The canary-secret scan runs across the suite.
- [x] Emitter key validation is in the PIT scope at 85%.
