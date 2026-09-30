## Lists
| list | file | heading or first words | first line | last line | n tests | keys |
| --- | --- | --- | --- | --- | --- | --- |
| L24-a | 24 | §3 "No secret's property name may appear in any committed properties or YAML file" | 214 | 219 | 1 | S24-01 |
| L24-b | 24 | §6 "the §3 name-absence assertion is extended to `.env.example`" | 325 | 327 | 1 (≡ S24-01, folded) | S24-01 |
| L24-c | 24 | §4 "that specific fact gets a one-line assertion" | 295 | 296 | 1 | S24-02 |
| L24-d | 24 | §8.2 cookie name / secure rows "Asserted by test, non-dev never executed" | 504 | 505 | 2 | S24-03, S24-04 |
| L24-e | 24 | §9 prohibited-configuration table + "a test asserting the validator bean is present, and one assertion per entry" | 534 | 554 | 10 | S24-05 – S24-14 |
| L24-f | 24 | §11 "Two assertions, not a regex" | 644 | 648 | 2 | S24-15, S24-16 |
| L24-g | 24 | §15 "Made verifiable rather than procedural" (post-build index.html) | 793 | 797 | 1 | S24-17 |
| L24-h | 24 | Amendment from 13 §2 "A prohibited configuration that cannot join the refresh-phase validator" (mapper test) | 884 | 899 | 1 | S24-18 |
| L24-i | 24 | Amendment from 13 "One confirmation" (observed length only) | 919 | 921 | 1 | S24-19 |
| L24-j | 24 | Amendment from 21 §1 "A tenth prohibited configuration" | 931 | 938 | 1 | S24-20 |
| L24-k | 24 | Amendment from 21 "Any assertion that telemetry goes where we said must … be on absent beans" | 974 | 977 | 1 (≡ S24-20) | S24-20 |
| L24-l | 24 | Amendment from 09 §R "The entry: the token prints to `System.out`" (eleventh entry) | 990 | 1000 | 0 | — (Not transcribed) |
| L24-m | 24 | Amendment from 25 §3 "Both filenames go onto the 13.4.1 jar-content test" | 1035 | 1041 | 1 (≡ S25-01) | S25-01 |
| L24-n | 24 | Amendment from 28 §1 "The eleventh prohibited-configuration entry becomes vacuous, and is replaced" | 1058 | 1061 | 0 | — (Not transcribed) |
| L24-o | 24 | Amendment from 28 §2 "Three new refresh-phase validator entries" | 1063 | 1070 | 3 | S24-21 – S24-23 |
| L24-p | 24 | Amendment from 28 §3 "Four prohibited implementations on the runner, each tested by ticket 16" | 1075 | 1080 | 4 | S24-24 – S24-27 |
| L25-a | 25 | Amendment from 09 item 3 "startup check that fails if a clustering-related property is present" | 140 | 145 | 1 (≡ S24-10) | S24-10 |
| L25-b | 25 | Amendment from 24 item 5 "A startup check converts that into a failure" (datasource) | 264 | 272 | 1 (≡ S24-12) | S24-12 |
| L25-c | 25 | Amendment from 24 item 6 "A post-build assertion covers the riskiest step" | 279 | 281 | 1 (≡ S24-17) | S24-17 |
| L25-d | 25 | Amendment from 24 "And one requirement this ticket is asked to adopt … ASVS 13.4.1 (L1)" | 283 | 288 | 2 | S25-01, S25-02 |
| L25-e | 25 | 09 §R §1 item 4 "The reserved-name note … Enforced by a denylist" | 415 | 416 | 1 | S25-03 |
| L25-f | 25 | 09 §R §4 "the output warning is enforceable … the reserved-name note is enforceable" | 438 | 442 | 2 (≡ S25-03; ≡ S25-06 restated) | S25-03, S25-06 |
| L25-g | 25 | Answer §2 "extractor runs in the Maven `verify` phase and fails when the committed renderings differ" | 491 | 494 | 1 | S25-04 |
| L25-h | 25 | Answer §7 "Adopted here … ASVS 13.4.1 (L1) … two assertions" | 664 | 670 | 2 (≡ S25-01, S25-02) | S25-01, S25-02 |
| L25-i | 25 | Answer §8 "The `git.properties` exposure path does not exist … both filenames join its list" | 698 | 704 | 1 (≡ S25-01) | S25-01 |
| L25-j | 25 | Amendment from 15 §1 "the recovery-code route and the recovery-address confirmation must be structurally unavailable" | 870 | 874 | 1 | S25-05 |
| L25-k | 25 | Amendment from 15 §2 "The row's grading — closed … The named test: run the runner with a known password" | 897 | 905 | 1 | S25-06 |
| L25-l | 25 | Amendment from ticket 16 (drift gate one JUnit test; TM-13 constraint) | 964 | 971 | 2 (≡ S25-04, S25-05) | S25-04, S25-05 |

## Rows
| key | pillar | control | assertion | level | context | isolation | clause | source | polarity | dup | note |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S24-01 | CFG | Secret property names absent from committed config | For every secret (`app.mfa.totp.encryption.key`, `app.security.hmac.tombstone.key`, `app.security.hmac.log.key`, `app.admin.password`, `app.admin.username`, and the `management.otlp.metrics.export.headers.*` map namespace) one normalising regex permitting dot, dash, underscore or camel-hump segment joins finds no match in any committed properties or YAML file under `src/main/resources` or `src/test/resources`; for `app.admin.password` the scan also covers `.env.example`. Parameterised over secrets. | U | none | none | IM8 as-8; ASVS 13.3.1 (L2) compensating; 24 §3; 24 §6 | 24:214; 24:325; 24:969 | neg |  | One parameterised test. The `.env.example` extension (24:325) is folded in because 24 frames it as an extension of the same assertion, not a separate test. OTLP headers map namespace added per 24:969 (conditional fourth secret). |
| S24-02 | CFG | Mounted secret file beats `.env` import | With the same key supplied by both `optional:file:./.env[.properties]` and `optional:configtree:/run/secrets/` (temp dirs), the bound value is the configtree one, pinning the relative order of the two sibling imports in one document. | U | none | none | 24 §4; ASVS 13.3.1 (L2) compensating | 24:295 | pos |  | "One-line assertion", not the whole precedence chain. Needs Boot config-data processing (SpringApplication without web), so U is nominal. |
| S24-03 | SES | Per-profile session cookie name | Dev binds `server.servlet.session.cookie.name=SESSION`; non-dev binds `__Host-SESSION`, asserted without executing the prod profile. | C | ctx-nondev | none | 24 §8.2; 05; 20 | 24:504 | pos | ≡? 20 | Owned by 05/20. 24:573 requires prod-only values be asserted without refreshing a context (ApplicationContextRunner or binding assertion); conflicts with ctx-nondev as a full context. Merge to resolve. |
| S24-04 | SES | Per-profile session cookie Secure flag | Dev binds `server.servlet.session.cookie.secure=false`; non-dev binds `true`, asserted without executing the prod profile. | C | ctx-nondev | none | 24 §8.2; 05; 20 | 24:505 | pos | ≡? 05 | Same context conflict as S24-03 (24:573). |
| S24-05 | CFG | Prohibited: `spring.mvc.servlet.path` | Setting `spring.mvc.servlet.path` to any value makes the refresh-phase prohibited-configuration validator refuse startup. | U | none | none | 24 §9 (CVE-2026-22753); 11; 23 | 24:536 | neg |  | Per-entry assertion required by 24:552. Refresh expected to fail (ApplicationContextRunner). |
| S24-06 | CFG | Prohibited: forward-headers-strategy framework | Setting `server.forward-headers-strategy=framework` makes the validator refuse startup. | U | none | none | 24 §9; 09 | 24:537 | neg |  | Per-entry assertion (24:552). |
| S24-07 | CFG | Prohibited: direct Tomcat internal-proxies | Setting `server.tomcat.remoteip.internal-proxies` directly makes the validator refuse startup. | U | none | none | 24 §9; 09 | 24:538 | neg |  | Per-entry assertion (24:552). |
| S24-08 | CFG | Prohibited: session schema initialize always | Setting `spring.session.jdbc.initialize-schema=always` makes the validator refuse startup. | U | none | none | 24 §9; 05 | 24:539 | neg |  | Per-entry assertion (24:552). |
| S24-09 | CFG | Prohibited: actuator show-values loosened | Setting `management.endpoint.env.show-values` or `management.endpoint.configprops.show-values` to any value other than `never` makes the validator refuse startup; parameterised over both keys. | U | none | none | ASVS 13.4.5 (L2); 24 §9 | 24:540 | neg |  | Per-entry assertion (24:552); one entry covering two keys. |
| S24-10 | CFG | Prohibited: clustering-related property | Presence of any clustering-related property makes the validator refuse startup (rate limiter is in-memory, single-instance). | U | none | none | 24 §9; 09; Std §5:453 | 24:541; 25:140 | neg |  | Per-entry assertion (24:552). 25:140 names the same startup check. Neither source enumerates which properties count as clustering-related. |
| S24-11 | CFG | Prohibited: secret supplied from committed file | A secret property whose value originates from a committed classpath properties or YAML file makes the validator refuse startup. | U | none | none | IM8 as-8; 24 §9; 24 §3 | 24:542 | neg |  | Per-entry assertion (24:552). Runtime twin of S24-01's static scan. 24 does not specify how the validator detects a committed origin. |
| S24-12 | CFG | Prohibited: in-memory or non-file H2 datasource | A resolved JDBC URL that is H2 in-memory (including the `jdbc:h2:mem:` fallback Boot resolves when `spring.datasource.url` is unset) refuses startup; with the dev profile active, a URL that is not `jdbc:h2:file` refuses startup; a non-dev non-H2 URL is not refused. | U | none | none | 24 §9; 20; ASVS 13.2.1 (L2) | 24:543; 25:264 | neg |  | Narrowed entry per 24:571–572. 25:264 states the same startup check. Runner full-refresh requirement (24:1082) is tested by 28/16 (16:261). |
| S24-13 | CFG | Prohibited: session cookie max-age | Setting `server.servlet.session.cookie.max-age` makes the validator refuse startup. | U | none | none | 24 §9; 08 | 24:544 | neg |  | Per-entry assertion (24:552). |
| S24-14 | CFG | Prohibited-configuration validator present | The application context contains the refresh-phase prohibited-configuration validator bean. | C | ctx-default | none | 24 §9 | 24:552 | pos | ≡? 16:261 | Guards against single-point deletion. If realised as `configurationPropertiesValidator` its `@Bean` must be `static` (24:556). 16:261 is the runner-context variant. |
| S24-15 | CFG | `.env.production` key allowlist | The set of keys in committed `.env.production` is exactly `{VITE_CSP, VITE_API_ORIGIN}`. | U | none | none | IM8 as-8; 24 §11 | 24:644 | neg | ≡? 20 | Allowlist, not a secret-shaped regex. Level could be F if placed in the frontend suite. |
| S24-16 | CFG | Committed API origin still a placeholder | The committed `VITE_API_ORIGIN` value in `.env.production` is still the placeholder, not a real hostname. | U | none | none | IM8 as-8; 24 §11 | 24:647 | neg | ≡? 20 | Complementary to S24-15. Level could be F. |
| S24-17 | BLD | Post-build frontend substitution check | The emitted `index.html` after `npm run build` contains no `%VITE_` placeholder and no `localhost` in the `connect-src` directive. | B | build | none | 24 §15; 20 | 24:794; 25:279 | neg | ≡? 20 | 25:279 restates the same assertion. |
| S24-18 | AUD | Request body never echoed into error/audit fields | A real malformed credential-bearing request body yields an error message (and any `error.message` / `error.stack_trace`) containing `REDACTED` and not the submitted password; asserts the outcome, not the `INCLUDE_SOURCE_IN_LOCATION` flag. | C | ctx-default | keyed | 13; 24 amendment from 13 §2 | 24:884; 24:895 | neg | ≡? 13 | Owned by ticket 13's negative-assertion set; cannot live in the refresh-phase validator. |
| S24-19 | AUD | Key validation failure never echoes the value | A present but malformed or wrong-length key refuses startup with our exception carrying only the observed length; the value and its origin appear in no log line, appender, stdout/stderr or throwable at any level. | U | none | none | 24 §7; 13 | 24:919; 24:360 | neg | ≡? 13 | Adopted verbatim into ticket 13's negative-assertion set and extended to all rejected configuration values. |
| S24-20 | CFG | OTLP metrics export disabled by default | The effective `management.otlp.metrics.export.enabled` is `false`, and the `OtlpMeterRegistry`, `OtlpConfig` and `OtlpMetricsConnectionDetails` beans are absent (assert absent beans, never the resolved URL). | C | ctx-default | none | 24 amendment from 21 §1; 21 | 24:931; 24:975 | neg | ≡? 21 | Tenth prohibited configuration; an assert-a-value-is-present entry outside the validator and outside the name-absence rule. |
| S24-21 | CFG | Prohibited: `AUTO_SERVER=TRUE` | A resolved JDBC URL containing `AUTO_SERVER=TRUE` makes the validator refuse startup. | U | none | none | 28; 24 §9 | 24:1068 | neg | ≡? 28 test 12 | Per 16 §9, 28 test 12 duplicates 24's validator entries; count once. |
| S24-22 | CFG | Prohibited: `FILE_LOCK=NO` | A resolved JDBC URL containing `FILE_LOCK=NO` makes the validator refuse startup. | U | none | none | 28; 24 §9 | 24:1069 | neg | ≡? 28 test 12 | Per 16 §9 duplicate; count once. |
| S24-23 | CFG | Prohibited: H2 TCP server bean | A context defining any H2 TCP server bean (`org.h2.tools.Server`) is refused by the validator at startup. | U | none | none | 28; 24 §9 | 24:1070 | neg | ≡? 28 test 12 | Per 16 §9 duplicate; count once. `h2.bindAddress` cannot be validated and is moot (24:1072). |
| S24-24 | RUN | No `System.console()` nullness mode selection | No main code uses `System.console()` nullness to choose a mode or detect a terminal. | A | archunit | none | 28 | 24:1076 | neg | ≡? 28:299 | "Each tested by ticket 16" (24:1075). Level chosen here; 28 may specify a behavioural form. |
| S24-25 | RUN | No command line on runner audit rows | No audit row written by the runner carries `process.command_line` or `process.args`. | R | runner | own-DB | 28 | 24:1078 | neg | ≡? 28 | "Each tested by ticket 16" (24:1075). |
| S24-26 | RUN | No `user.name` identity fallback | No main code calls `System.getProperty("user.name")` as an identity fallback. | A | archunit | none | 28 | 24:1079 | neg | ≡? 28:309 | "Each tested by ticket 16" (24:1075). |
| S24-27 | RUN | No secret-bearing runner argument | The runner accepts no secret-bearing argument (for example `--password`); the password is read only from stdin or a prompt. | R | runner | own-DB | 28; CWE-214 | 24:1080 | neg | ≡? 28:311 | "Each tested by ticket 16" (24:1075). |
| S25-01 | BLD | No source-control metadata in the jar | The built jar contains no `.git` or `.svn` entries and no `git.properties` or `build-info.properties`. | B | build | none | ASVS 13.4.1 (L1); ASVS 13.4.6 (L3); ASVS 13.4.5 (L2) | 25:283; 25:664; 25:698; 24:1035 | neg | ≡? 16:173; ≡? 16:180 | Takes the first branch of disjunctive 13.4.1. The two filenames are 13.4.6 + 13.4.5 scope, not 13.4.1. Counted once under 25. |
| S25-02 | BLD | No source-control metadata in the static bundle | The emitted static bundle directory contains no `.git` or `.svn`. | B | build | none | ASVS 13.4.1 (L1) | 25:283; 25:664 | neg | ≡? 16:173 | 25:664 says "two assertions"; split jar/bundle accordingly. |
| S25-03 | ADM | Reserved bootstrap admin username refused | The refresh-phase validator refuses startup when `app.admin.username` matches the reserved-name denylist. | U | none | none | 09 §R.3; 11 | 25:415; 25:438 | neg | ≡? 16:209; ≡? 11 | The reserved-name half of 25:438 stands; only its output-warning half is superseded (see S25-06). |
| S25-04 | BLD | Handover/register drift gate | One JUnit test under Failsafe 3.6.0 regenerates both renderings from the single extracted table and fails when the committed renderings differ. | B | build | none | ASVS 6.3.1 (L1); ASVS 6.1.2 (L2); ASVS 6.2.11 (L2); ASVS 16.2.3 (L2); ASVS 16.3.3 (L2) | 25:491; 25:964 | neg | ≡? 16:188 | Restated by 25:964 and 16 §8: one test, not also a plugin execution. Accepted bypasses `-DskipITs`, `-Dmaven.test.skip`. |
| S25-05 | MFA | Recovery routes structurally absent (TM-13) | In ctx-default, no handler in `getHandlerMethods()` issues or redeems the issued-recovery-code token type, and ticket 26's three-registry disposition test contains no recovery entry. | C | ctx-default | none | TM-13; 16 §7 | 25:870; 25:966 | neg | ≡? 16:239 | Assertion taken from 16 §7: no HTTP status is pinned yet. When mail transport enters scope the gate keys on a declared transport property, never the `EmailService` bean type, and the test gains a status per path and principal. |
| S25-06 | AUD | Runner emits no secret (TM-12) | Run the runner with a known password; that string appears in none of stdout, stderr or the audit file. | R | runner | own-DB | TM-12; 28; ASVS 6.1.1 (L1) | 25:897; 25:438 | neg | ≡? 28:377; ≡? 16:207 | Restated. Replaces 25:438's "output warning is enforceable" and 16:207's "no logger on the token path" per the TM-12 in-place correction (25:897–905); per 16 §9, 28 test 9 is the content test. |

## Not transcribed
| file:line | item | reason |
| --- | --- | --- |
| 24:990 | Eleventh prohibited-configuration entry: runner `System.out` token never on a logging channel (duplicated on 13's negative list) | Superseded by 28 test 9 (16 §9; 24:1058). Restated assertion is S25-06. |
| 24:1058 | "replaced by the content test in ticket 16 (a known password appears in no stream)" | Pointer to the superseding test; transcribed once as S25-06 (≡? 28:377). |
| 24:356–361; 24:391 | Strict Base64 decode, exactly 32 decoded bytes, reject all-printable-ASCII and all-identical keys, one fingerprint INFO line per key | Stated as enforced controls. 24 names no test for them. Only the failure-message rule is test-owned (S24-19). Flagged for merge. |
| 24:412–416; 24:497–499; 24:509 | Refuse-to-start on absence of each secret, `app.origins.api` CORS cross-check, key-version properties, blank `issuer` | Absence behaviour stated in the inventory tables. 24 names no test. Flagged for merge (ticket 11/23 may own some). |
| 24:573 | Ticket 20's prod-only values asserted without refreshing a context | Constraint on test form, not a test. Applied as a note on S24-03/S24-04. |
| 24:657 | History scan and pre-commit secret scanner | External tooling, not a test. The pre-commit hook is local and bypassable (24:667–673). |
| 24:915 | `ApplicationReadyEvent` effective-logger-level check; `/actuator/loggers` absent or read-only outside `dev` | Controls owned by ticket 13. 24 names no test for them. |
| 24:958 | `setRequiredProperties` from an `EnvironmentPostProcessor` for the export-enabling profile | Mechanism recommendation for a future profile. No test owed. |
| 24:1072 | `h2.bindAddress` system property | Validator cannot see it; moot once the TCP server bean is prohibited. |
| 24:1082 | Runner mode passes full refresh; appends `IFEXISTS=TRUE` | Requirement on the runner. Tests are 28/16 runner tests (16:261, 16:263). |
| 25:122 | Trusted-proxy `@ConfigurationProperties` fails refresh when `source=proxy` with no list | Enforcement description. Test owned by ticket 09. |
| 25:195; 25:225 | "assert no row remains on a retired version" in the TOTP key rotation procedure | Operator procedure step, not an automated test. |
| 25:329 | Reset-link logger three enforcement points, incl. "a prohibited-configuration entry in ticket 24's refresh-phase validator" | Owned by ticket 13. 24 §9 lists no reset-link entry (24:912–916 says the validator cannot see `LOGGING_LEVEL_…`). Contradiction flagged. |
| 25:348–375 | Ticket 21 section acceptance checks (alert rules, absence-of-logs alert, probes 200, OTLP inverted, re-scan report) | Deployer acceptance checks, not tests. |
| 25:403; 25:621 | Runner "mints a single-use token … prints it once" (single-use token, 6.4.1) | Superseded in 25 itself (25:956 → TM-12 correction 25:897–905): the runner reads the password from stdin and mints nothing. 16:205's single-use-token row is likely vacuous too. Flagged. |
| 25:563 | Unexercised production config row (`Secure`, HSTS, `__Host-SESSION`, strict CSP, prod CORS) "asserted-by-test" | Cites tests owned by 05/20. No new test. |
| 25:583; 25:906; 25:965 | Step-7 recovery rehearsal | Acceptance check, not a test (25:965; 16:197). |
| 25:655 | NIST §4.2.3 account-recovery notification SHALL | Out of scope: no transport outside `dev` (16:212; 16 §6(b)). |
| 25:669 | 13.1.1 third limb "discharged by ticket 10's three negative assertions" | Tests owned by ticket 10. |
| 25:736 | Two new event families for ticket 13 (input-validation rejections, business-logic violations) | Catalogue amendment to 13. No test stated here. |
| 25:965 | "Ticket 16 automates what surrounds it (ticket 28's runner tests 2, 6 and 7 run both ways)" | Tests defined in ticket 28. No assertion given here, so not re-transcribed. |

## Counts
| source ticket/section | n tests | keys |
| --- | --- | --- |
| 24 §3 (also 24:325 §6, 24:969) | 1 | S24-01 |
| 24 §4 | 1 | S24-02 |
| 24 §8.2 | 2 | S24-03, S24-04 |
| 24 §9 (S24-10 also 25:140; S24-12 also 25:264) | 10 | S24-05 – S24-14 |
| 24 §11 | 2 | S24-15, S24-16 |
| 24 §15 (also 25:279) | 1 | S24-17 |
| 24 amendment from 13 | 2 | S24-18, S24-19 |
| 24 amendment from 21 | 1 | S24-20 |
| 24 amendment from 28 §2 | 3 | S24-21 – S24-23 |
| 24 amendment from 28 §3 | 4 | S24-24 – S24-27 |
| 25 amendment from 24 / 13.4.1 (also 25:664, 25:698, 24:1035) | 2 | S25-01, S25-02 |
| 25 09 §R §1 item 4 (also 25:438) | 1 | S25-03 |
| 25 Answer §2 (also 25:964) | 1 | S25-04 |
| 25 amendment from 15 §1, TM-13 (also 25:966) | 1 | S25-05 |
| 25 amendment from 15 §2, TM-12 (also 25:438) | 1 | S25-06 |
| **total** | **33** | 27 × S24, 6 × S25 |
