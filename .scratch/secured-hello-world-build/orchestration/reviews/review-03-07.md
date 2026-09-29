# Independent review: tickets 03-07 (`806135c..2184851`)

Branch `zacharylim`. Range: 23 commits (merges of tickets 03 build gates, 04 schema, 05 error contract, 06 config
validator and secrets, 07 source-key resolver). HEAD at the time of review is `18a7654` (ticket 16). The "HEAD"
column says whether each finding still applies there.

Method: the `code-review` skill (two parallel sub-agents, Standards and Spec), plus a direct read by the reviewer of
every security-relevant main class and the proving tests. The sub-agent reports are reproduced below, lightly
cleaned. The reviewer's own findings come first because they are the ones that matter. The two sub-agents found no
hard defects. Every finding below is the reviewer's own, and each was checked against the source.

Counts (reviewer findings): **Critical 0, High 1, Medium 6, Low 7**.

---

## Reviewer findings

### High

**H1. Forwarded-header trust can be installed outside the single `client-ip` input. `native` and direct
`server.tomcat.remoteip.*` settings are not refused.** Still applies at HEAD.

- `backend/src/main/java/sg/securedhello/config/ProhibitedConfigurationValidator.java:58` refuses only
  `server.forward-headers-strategy=framework`.
- `backend/src/test/java/sg/securedhello/config/ProhibitedConfigurationValidatorTest.java:43-48`
  (`otherForwardHeadersStrategiesAreNotThisValidatorsConcern`) pins `native` as allowed.
- `application.yml:6` sets `forward-headers-strategy: none`, but any higher-precedence source overrides that value,
  for example `SERVER_FORWARDHEADERSSTRATEGY=native` in the environment or a deployer's `application-prod.yml`. Boot
  then adds its own `RemoteIpValve`. That valve uses Tomcat's default `internalProxies`: 10/8, 172.16/12, 192.168/16,
  169.254/16, 127/8, 100.64/10, ::1, fe80::/10 and fc00::/7. From then on, any peer on a private network can pick its
  own `getRemoteAddr()`, and so its source key. That defeats the per-source rate limiter, the lockout cardinality
  axis and `source.ip_hash` (ASVS 15.3.4). The same happens when `server.tomcat.remoteip.internal-proxies`,
  `trusted-proxies` or `remote-ip-header` is set directly while the strategy is `native`. With `source=proxy`, two
  valves are installed.
- Why this is a defect: register row R-RL-007 (`docs/register/register.md:112`) says Tomcat's `internal-proxies`
  "and `forward-headers-strategy` are both derived from that one input" and that "setting it directly is also
  refused". T-CFG-026 ("Setting `server.tomcat.remoteip.internal-proxies` directly makes the validator refuse
  startup") is still on the pending ledger at HEAD, and no ticket file names it. The shipped control therefore does
  less than the register claims, and the unit test locks that gap in.
- Fix: refuse any `server.forward-headers-strategy` value other than `none`, and any
  `server.tomcat.remoteip.*` property. Use the relaxed-name scan already used at HEAD for the clustering prefixes.
  Replace the `native` "not our concern" test with a refusal test, and prove T-CFG-026.

### Medium

**M1. `FILE_LOCK=NO` is "prohibited" but nothing refuses it.** Still applies at HEAD.
Spec §Data (`docs/spec.md:578`) and ticket 04 both say "`FILE_LOCK=NO` is prohibited". The only trace of this in code
is a comment (`application.yml:12`). `ProhibitedConfigurationValidator.datasourceViolation` checks for `mem:` and
the dev file prefix only, so a deployer URL with `;FILE_LOCK=NO` starts. With file locking off, two instances can
open the same H2 file, which also undermines the single-instance assumption (REJ-018). `LOCK_TIMEOUT=1000` is
likewise not asserted on the resolved URL. Fix: add both checks to the validator, with a U-level test.

**M2. The traceability gate accepts citations that never execute.** Still applies at HEAD (the file is unchanged).
- `backend/src/test/java/sg/securedhello/build/Citations.java:32-34`: `TEST_NAME` deliberately matches
  `it.skip(...)`, `test.todo(...)`, `describe(...)` and similar. An `it.todo('T-XYZ-001 ...')` with no body therefore
  satisfies "a row has no test" (ADR-068). A `describe('T-…')` around an empty suite does too.
- `Citations.java:52-65`: Java citations come from every test-tree class, including `@Disabled` classes and methods.
  They also come from classes whose names match neither the Surefire includes (`*Test`, `Test*`, `*Tests`,
  `*TestCase`) nor the Failsafe ones (`*IT`), and from abstract bases and helper methods. None of these run.
- ADR-068 does accept that the gate proves existence and not assertions. A skipped or never-run test is not a test,
  though, so the ledger can shrink with no test having run. Fix: ignore `.skip`, `.todo` and `.fixme` names and
  `describe` (or require `it`/`test` with a body). Reject `@Proves` on `@Disabled` or abstract classes and methods,
  and on classes outside the runner include patterns.

**M3. T-AUTH-001 is proven only by a hand-built error dispatch.** Still applies at HEAD (the only citation).
`backend/src/test/java/sg/securedhello/error/ErrorEnvelopeTest.java:117-125,195-203` sends a MockMvc request to
`/error`. It sets `DispatcherType.ERROR`, `ERROR_EXCEPTION` and `ERROR_STATUS_CODE` by hand. That shows
`ProblemErrorController` renders an escaped exception correctly. The row asks for more: "An exception escaping the
advice and the security handlers **reaches** the `/error` dispatch." Nothing shows that a real escape, such as an
exception thrown in a servlet filter on a real port, is routed by Tomcat through the security chain's
ERROR-dispatch `permitAll` to this controller rather than to Tomcat's HTML error page. `ErrorEnvelopePortTest`
covers unmatched routes and firewall rejections only. Fix: add a `ctx-port` case with a test-only filter that throws.

**M4. T-CFG-023 passes on any startup failure.**
`backend/src/test/java/sg/securedhello/config/StartupRefusalTest.java:39-51` asserts only `failure != null` and
`!portOpened`. It never checks that the failure names the removed property, so any unrelated startup failure, such
as an H2 lock or a bean cycle, also passes. Ticket 06's criterion ("with a message naming the property but not its
value") is asserted for malformed keys (T-CFG-022) but not for missing ones. The positive control
`theTestSecretsBootTheApplication` only partly mitigates this. Fix: assert that `failureMessages()` contains the
property name, or that the root cause is a `BindValidationException` for that property.

**M5. The T-CFG-038 test is parameterised from the implementation's own list, so it cannot catch an omission.**
Still applies at HEAD (`REQUIRED` is unchanged).
`StartupRefusalTest.java:76-85` uses `@FieldSource("…RequiredPropertiesPostProcessor#REQUIRED")`. A property left out
of `REQUIRED` is silently untested. The test-plan row (`docs/test-plan/test-plan.md:342`) names a fixed list that
also contains `app.mfa.totp.issuer` and the OTLP URL under the export profile. Those may belong to later tickets, but
nothing will flag them when they arrive. Fix: parameterise over a list written out in the test, taken from the row.

**M6. The V7 blob-hash check is pinned to a hard-coded upstream hash, not the jar on the classpath.** Still applies
at HEAD.
`backend/src/test/java/sg/securedhello/persistence/SpringSessionSchemaTest.java:25-35` compares V7 with a literal
hash of spring-session-jdbc 4.1.1's `schema-h2.sql`. `initialize-schema: never` is set, and Hibernate does not
validate the session tables. So if a Spring Session upgrade changes the DDL, V7 goes stale, this test stays green,
and the failure first appears at runtime on the first session write. Fix: hash
`org/springframework/session/jdbc/schema-h2.sql` from the classpath and compare the two, keeping the literal as a
second assertion if wanted.

### Low

- **L1.** `backend/src/main/java/sg/securedhello/config/AdminSeedProperties.java:17` binds the admin password as a
  `String`. ADR-062 §Decision says "the admin password as `byte[]`". It still applies at HEAD.
- **L2.** An unresolved `${…}` in `app.admin.username` or `app.admin.password` binds as a literal that passes
  `@NotBlank` (`RequiredPropertiesPostProcessor.java:18-22`). ADR-062 records this as a residual, and T-CFG-020 keeps
  those names out of committed config. It is worth adding both names to `REQUIRED` anyway: it costs nothing and
  closes a seed-password-equals-placeholder footgun.
- **L3.** `backend/src/test/java/sg/securedhello/architecture/ArchitectureRules.java:74-78,108-114` (T-RL-028) bans
  `getRemoteAddr` and `WebAuthenticationDetails.getRemoteAddress` only. `getRemoteHost()` (the client address after
  the valve, and a DNS lookup when `enableLookups` is on) and reading `X-Forwarded-For` directly with `getHeader`
  are also raw-address paths under ADR-020. The rule matches its row, but the ban has gaps.
- **L4.** `backend/src/test/java/sg/securedhello/security/source/SourceKeyNoDnsTest.java` (T-RL-025). The JVM's
  `InetAddress` positive and negative caches can answer a regressed `getByName` without calling the provider.
  `localhost` is resolved by other contexts in the same reused fork, and `bad.cafe` is negatively cached by
  `theTrapCatchesARealLookup`. The other tokens still catch a regression, so this is a weakening, not a hole. Add
  never-seen random hostnames.
- **L5.** `ProhibitedConfigurationValidator.java:69` checks `spring.datasource.url` only. A
  `spring.datasource.hikari.jdbc-url=jdbc:h2:mem:x` is bound onto the pool after it and wins. T-CFG-031 says "a
  resolved JDBC URL". This takes a deliberate misconfiguration.
- **L6.** `backend/src/test/java/sg/securedhello/persistence/SchemaValidationGateTest.java:108-116` runs
  `restart`-context rows (T-CFG-002 to 006) on an `ApplicationContextRunner` with three auto-configurations. That is
  neither the restart harness nor a named context (ADR-065). The javadoc justifies it, and it is reasonable for a
  schema gate. Note, though, that T-ARCH-006's slice rule cannot see partial `ApplicationContextRunner` contexts, so
  the same pattern on a real security test would pass the architecture gate.
- **L7.** `backend/README.md:52-54` forbids `-DskipITs`, `-Dmaven.test.skip` and `-Ddependency-check.skip`, and notes
  that `-DskipTests` no longer skips Failsafe (verified: Failsafe 3.6.0 no longer binds the `skipTests` user
  property). But `-DskipTests` still skips every Surefire-level gate: the ArchUnit bans (T-RL-028, the `sendError`
  ban), `TraceabilityGateTest` and the V7 hash check. The README wording reads as if `-DskipTests` were harmless for
  a release build. It should forbid it explicitly.

### Checked and found sound

The following were checked and found sound:

- The IPv6 masking arithmetic.
- IPv4-mapped addresses collapsing to IPv4 (the JDK returns `Inet4Address`).
- The literal-shape gating, which keeps `InetAddress.getByName` off DNS: a token containing a colon throws rather
  than resolving.
- The `internalProxies` host masks, not `trustedProxies`, proven on a real port for both the untrusted and the named
  peer.
- Log-token escaping.
- `KeyMaterial`: strict padded Base64, no value echoed, a constant-time distinctness check, and a domain-separated
  fingerprint.
- The single writer (ADR-031): no `sendError`, constant `detail`, and security exceptions rethrown to
  `ExceptionTranslationFilter`.
- The matrix order: whitelist, then the roles `denyAll`, then the role guards, then `anyRequest().denyAll()`.
  T-ADM-019 and T-ADM-020 cover every method and both roles in `ErrorEnvelopeTest`.
- The schema negative tests: each asserts a `SchemaManagementException` with the specific message.
- Dependency-Check is bound to `verify` at CVSS 7 and fails closed.

---

## Standards (sub-agent report, lightly cleaned)

Reviewed the diff against `CONTEXT.md`, `backend/README.md` and ADR-031, 050, 051, 062, 065, 067, 068 and 069.

**Hard violations: none found.**
- ADR-031: one writer (`ProblemDetailWriter`), no `sendError`, `instance` set explicitly, a closed `ErrorCode` enum.
- ADR-062: secrets bind through `@ConfigurationProperties` with no `@Value`, and `setRequiredProperties` closes the
  placeholder gap for the listed properties.
- ADR-050: `@UuidGenerator` on the generated keys, and `DeletedUser` and `PendingTotp` reuse `user_id`.
- ADR-051: invariants are asserted outside the persistence context through raw JDBC.
- ADR-065 and ADR-067: no `@WebMvcTest`, `@MockitoBean`, `application-test` or `@ActiveProfiles("test")` in the
  new tests. `SourceKeyResolverTest` is a level-U pure-logic test.

**@Proves sample** (`ErrorEnvelopeTest`, `MatrixAndAdviceTest`, `StartupRefusalTest`, `SourceKeyResolverTest`,
`TraceabilityGateTest`): the sampled assertions match their T-IDs. *Reviewer note: see M3 to M5 for the exceptions
found on a closer read.*

**Judgement calls:**
- `ErrorCode.forStatus` (`ErrorCode.java:106-114`): a possible Repeated Switches smell if a second status switch
  appears. For now it is speculative.
- `ClientIpConfig.hostMask` dereferences `parseLiteral` without a null check. It is guarded in practice, because
  `ClientIpProperties.isTrustedProxiesLiteral` validates the list before the customizer runs.
- `ProhibitedConfigurationValidator.violations` is a growing flat list of unrelated checks (possible Divergent
  Change). Split it by concern if it grows further. It has already grown at HEAD.

## Spec (sub-agent report, lightly cleaned)

- **(a) Missing or partial:** none outright. The ticket 03 to 07 checklists are backed by code and tests.
  `ApplicationKeys` enforces distinct material for the three keys.
- **(b) Scope creep:** none significant. The register renderings are what ticket 03 item 2 requires.
- **(c) Implemented but possibly wrong:**
  - The admin seed placeholder leniency is an accepted residual in ADR-062 (see L2).
  - XFF trust, the IPv6 /64 derivation, the 48 to 128 prefix bounds (T-RL-027: 47, 129, 0 and -64 are refused),
    the envelope's constant `detail`, and the whitelist-first default-deny order all match ADR-020, 031 and 043.
  - No incorrect `@Proves` citations were found in the areas sampled.
  - *Reviewer note: the sub-agent checked `framework` refusal only. H1 (`native` and direct `remoteip.*`) and M1
    (`FILE_LOCK=NO`) are spec gaps it did not catch.*

---

**Summary.**
- Reviewer: 14 findings (0 Critical, 1 High, 6 Medium, 7 Low). The worst is H1: forwarded-header trust can be
  enabled with Tomcat's RFC 1918 default, bypassing the named-proxy control.
- Standards axis: 0 hard violations and 3 judgement calls. The worst is the growing flat `violations` method.
- Spec axis: 0 findings from the sub-agent. Its most important blind spot is H1.
