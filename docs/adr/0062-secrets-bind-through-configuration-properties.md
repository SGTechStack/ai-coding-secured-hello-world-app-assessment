---
status: accepted
---

# ADR-062: Secrets bind through `@Validated @ConfigurationProperties`, not `@Value`

Every secret the application defines binds through a `@Validated @ConfigurationProperties` class. No secret is read
with `@Value`. The `im8-review` as-8 check looks for `@Value("${...}")` as its sign of good practice, so a maintainer
chasing a clean scan would convert the bindings back. This choice has one cost that is easy to miss: on an
unresolved `${VAR}` placeholder, `@Value` fails and the binder does not.

## Context

- The application's secrets are the TOTP encryption key, the two HMAC keys and the bootstrap admin credentials. Each
  is mandatory in every profile, has no default, and its property name appears in no committed file (T-CFG-020).
- Absence must stop the application during context refresh. Boot starts the web server during refresh, so a check
  that runs later runs after the port is bound.
- IM8 as-8 itself says only "Securely store secrets in appropriate solutions (e.g., AWS Secrets Manager, HashiCorp
  Vault)". It names no annotation. `@Value("${...}")` appears in the `im8-review` skill's automated check, which
  treats it as the good pattern against hardcoded values. The store question is separate, and is recorded as
  R-CFG-013.
- The Spring Boot 4.1 reference calls `@Value` "a core container feature" that "does not provide the same features
  as type-safe configuration properties". It rates `@Value`'s relaxed binding as limited, and recommends grouping a
  component's own keys in a `@ConfigurationProperties` class.
- A `@Validated` violation is reported by Boot's `BindValidationFailureAnalyzer` with `Property:`, `Value:` and
  `Origin:` lines. A constraint that checks a key's content would print the key to stdout.
- **The binder is lenient on placeholders.** Boot's `PropertySourcesPlaceholdersResolver` builds its placeholder
  helper with unresolvable placeholders ignored, so `${VAR}` with `VAR` unset binds as the literal string, and
  `@NotBlank` accepts it. `@Value` is resolved by Spring's `PropertySourcesPlaceholderConfigurer`, a
  `PlaceholderConfigurerSupport`, which throws by default.
  The Boot reference does not document this difference. It was confirmed in the 4.1.x source.
- `ConfigurablePropertyResolver#setRequiredProperties` is checked by `validateRequiredProperties()` in
  `prepareRefresh()`, before any bean is created. It fails on absence, and on an unresolvable placeholder because it
  reads through the strict property path.

## Considered options

- **`@Value("${...}")` on each secret.** It fails fast on an unresolved placeholder and satisfies the scan. It has no
  metadata, limited relaxed binding, and no single class to validate or to type the key material. Each secret's
  presence and shape would be checked at its own injection point.
- **`Environment#getRequiredProperty` in each `@Bean` factory.** Also strict, but it spreads secret reads across the
  code base, and it runs whenever the bean is created, not at one fixed point.
- **`@Validated @ConfigurationProperties` for presence, value checks in the `@Bean` factory (chosen).**

## Decision

- Each secret binds as a `String` field (the admin password as `byte[]`) on a `@Validated @ConfigurationProperties`
  class, annotated for presence only, for example `@NotBlank`. A missing key binds `null` and fails refresh.
- Every value-dependent check runs in the `@Bean` factory that builds the encryptor or MAC: strict Base64 decoding,
  an exact 32-byte length, and the content checks. A failure reports the observed length only, never the value or
  its origin. The typed holder is built after validation, so it never takes part in binding.
- No `@Value` on any secret path.
- Where a required value must also refuse an unresolved placeholder, it is registered with `setRequiredProperties`
  from an `EnvironmentPostProcessor`. Today that is the OTLP URL on a profile that enables metrics export (ADR-061).

## Consequences

- **The `im8-review` as-8 check can report a false negative.** A reviewer reads its output beside R-CFG-007.
- **Unresolved placeholders are a residual (R-CFG-019).** Key material still fails, because `$`, `{` and `}` fail
  strict Base64 decoding (T-CFG-022). Presence-only properties such as the origins, the key versions and the TOTP
  issuer would bind the literal. No test covers that case yet. The committed configuration cannot carry such a
  placeholder, because the names are absent from it (T-CFG-020). The exposure is deployer-supplied configuration.
- T-CFG-023 pins absence for the keys, the origins and the key versions, and blank for the TOTP issuer. T-ADM-023
  pins the bootstrap admin credentials. T-CFG-022 pins key shape.
- **The OTLP export header is a conditional fourth secret outside this rule.** `management.otlp.metrics.export.headers.*`
  binds through Boot's own `OtlpMetricsProperties`, which the application does not validate. Micrometer also reads
  `OTEL_EXPORTER_OTLP_HEADERS` and `OTEL_EXPORTER_OTLP_METRICS_HEADERS` straight from the process environment, with no
  Spring binding at all. It exists only if a deployer enables export. T-CFG-020 keeps its map namespace out of
  committed files (R-CFG-020).
- Any future claim that a `@ConfigurationProperties` presence check "fails fast" must be read against the placeholder
  leniency above.

## Reopening triggers

- A Boot upgrade makes the configuration-properties binder strict on unresolved placeholders. The residual then
  closes and `setRequiredProperties` may no longer be needed.
- `im8-review` changes its as-8 check to recognise `@ConfigurationProperties` binding.
- A new secret is added. It follows this rule and joins T-CFG-020 and T-CFG-023.

## Sources

- Spring Boot 4.1 reference, Externalized Configuration: "@ConfigurationProperties Validation" and
  "@ConfigurationProperties vs. @Value".
- Spring Boot 4.1.x source: `PropertySourcesPlaceholdersResolver` (constructor), `ConfigurationPropertiesBinder`
  (`getBinder()`), `BindValidationFailureAnalyzer` (`appendFieldError`), `OtlpMetricsPropertiesConfigAdapter`
  (`headers()`).
- Spring Framework source: `PlaceholderConfigurerSupport` (`ignoreUnresolvablePlaceholders` defaults to false),
  `AbstractPropertyResolver` (`setRequiredProperties`, `validateRequiredProperties`).
- Micrometer `micrometer-registry-otlp` source, `OtlpConfig#headers()` (main branch).
- IM8 as-8 (Secrets Management), and the `im8-review` as-8 automated check text.
- OWASP ASVS 5.0: 13.3.1 (L2) secrets kept out of source code and build artefacts.
