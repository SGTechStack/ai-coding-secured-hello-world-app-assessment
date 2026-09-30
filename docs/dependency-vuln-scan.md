# Dependency vulnerability scan

| | |
|---|---|
| **Scanned** | 2026-09-30T10:08:20Z |
| **Project** | Secured Login App (`backend/` Maven + `frontend/` npm) |
| **Scanners** | `npm audit` (npm 11.16.0) · **OSV.dev** query API (Maven) |
| **Coverage** | 123 distinct Maven artifacts (compile/runtime/provided) · 346 npm packages (34 prod, 313 dev) |

> **Point-in-time.** Re-run to refresh — a clean report today says nothing about tomorrow.

## Why OSV rather than `dependency-check-maven`

The plan names `dependency-check-maven` with `failBuildOnCVSS=7` (spec.md S1, S12), and that plugin needs
an **NVD API key** we do not have. Its own documentation sets the inter-request delay to `3500`ms with a
key versus `8000`ms without, across a 399,572-record download; a four-minute attempt did not finish and
left a 4.7 MB partial cache.

**OSV.dev** was used instead: the same class of data (it aggregates GitHub Security Advisories, which is
where these CVEs are published), a public API, no key. What is sent is package coordinates and versions
— all public open-source identifiers, no project code.

This is a **deviation from the plan, not a discharge of its gate**. `dependency-check-maven` remains
item 6 of definition-of-done list B and remains unrun. The two differ in mechanism — dependency-check
also fingerprints bundled JARs and can catch a shaded dependency that a coordinate lookup misses.

## Summary

| Severity | Maven | npm | Total |
|---|---:|---:|---:|
| Critical | 3 | 0 | **3** |
| High | 2 | 0 | **2** |
| Moderate | 7 | 1 | **8** |
| Low / info | 0 | 0 | 0 |

### The project would fail its own gate

**Four findings compute to CVSS v3.1 ≥ 7.0, and a fifth is rated High on a v4.0 vector.** Against
`failBuildOnCVSS=7`, that is a failing gate. Base scores below are calculated from each advisory's
published vector using the CVSS v3.1 formula, not taken from a qualitative label.

## Critical

| Score | ID | CVE | Artifact | Installed | Fixed in |
|---:|---|---|---|---|---|
| **9.8** | GHSA-9xv2-5v5q-p794 | CVE-2026-65905 | `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.22 | **11.0.25** |
| **9.1** | GHSA-gcx9-497g-6cp6 | CVE-2026-65182 | `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.22 | **11.0.25** |
| **9.1** | GHSA-h3x4-894j-xpx5 | CVE-2026-68525 | `org.apache.tomcat.embed:tomcat-embed-core` | 11.0.22 | **11.0.25** |

All three are in the embedded servlet container, and all three are **authentication or authorization
defects**:

- CVE-2026-65905 — DIGEST authenticator: authentication bypass by capture-replay
- CVE-2026-65182 — improper access control / incorrect authorization
- CVE-2026-68525 — FORM authentication: incorrect authorization

**Relevance to this application, stated honestly.** This app uses neither DIGEST nor Tomcat's FORM
authenticator — authentication is a custom JSON filter on Spring Security's chain, and
`formLogin` and `httpBasic` are both explicitly disabled in `SecurityConfig`. So the *specific* attack
paths for the DIGEST and FORM advisories are not obviously reachable here. That is a reason to be
calmer about the exploitability, **not** a reason to leave a 9.8 in an authentication service: the
access-control advisory is not authenticator-specific, exploitability analysis is not something this
scan can settle, and the fix is a one-line version bump.

## High

| Score | ID | CVE | Artifact | Installed | Fixed in |
|---:|---|---|---|---|---|
| **7.5** | GHSA-q4xh-88c3-wmh7 | CVE-2026-68497 | `tools.jackson.core:jackson-databind` | 3.1.4 | **3.1.6** |
| v4.0 (High) | GHSA-j92g-9f8w-j867 | CVE-2026-54291 | `org.postgresql:postgresql` | 42.7.11 | **42.7.12** |

- **jackson-databind** — `Duration`/`XMLGregorianCalendar` unbounded number parse, denial of service.
  Directly reachable: every request body on this API is deserialized by Jackson.
- **postgresql** — silent channel-binding authentication downgrade via unsupported certificate
  algorithms. Its vector is CVSS v4.0, which this report does not compute (the v4.0 formula is
  substantially more involved than v3.1 and hand-implementing it would be a worse answer than saying
  so); GitHub rates it **High**. Scope note: the driver is `runtime` scope and PostgreSQL is the
  production target, so this is a deployment-path finding, not a test-only one.

## Moderate

| Score | ID | Artifact | Installed | Fixed in | Note |
|---:|---|---|---|---|---|
| 6.5 | GHSA-5gvw-p9qm-jgwh | `jackson-databind` | 3.1.4 | 3.1.5 | `@JsonView` bypassed for `@JsonUnwrapped` containers |
| 5.6 | GHSA-gx83-3vf8-gh7j | `jackson-databind` | 3.1.4 | 3.1.6 | incomplete polymorphic-typing fix |
| 5.3 | GHSA-vvgp-rfg2-7rr6 | `jackson-databind` | 3.1.4 | 3.1.5 | eager DNS resolution (SSRF), incomplete prior fix |
| 5.3 | GHSA-wjgm-6hv5-3cvf | `jackson-databind` | 3.1.4 | 3.1.6 | `Path` deserialization, missing scheme allowlist |
| 5.3 | GHSA-rcgg-9c38-7xpx | `io.opentelemetry:opentelemetry-api` | 1.55.0 | 1.62.0 | unbounded memory in W3C baggage propagation |
| 5.3 | GHSA-rcgg-9c38-7xpx | `io.opentelemetry:opentelemetry-extension-trace-propagators` | 1.55.0 | 1.62.0 | same advisory, second artifact |
| n/a (v4.0) | GHSA-qv9r-c865-cp47 | `org.apache.logging.log4j:log4j-api` | 2.25.4 | 2.25.5 | non-finite float encoding in `MapMessage` JSON |

### npm — 1 moderate, dev-only

| Score | ID | Package | Installed range | Fixed in |
|---:|---|---|---|---|
| 5.9 | GHSA-82fw-gwwq-j7x9 | `vitest` / `@vitest/mocker` | `>=2.1.0 <4.1.11` | 4.1.11 |

Path traversal / arbitrary file read via the mocker's redirect-mock feature. **`devDependencies` only** —
Vitest is not in the shipped bundle, so this is a developer-workstation exposure, not a production one.
`npm audit` proposes `vitest@5.0.2`, a semver-major jump from the pinned `^3`; `4.1.11` is the minimum
that closes it. Either needs a compatibility check — Vitest 3 was itself required to match Vite 6.

**The 34 production npm packages have no known vulnerabilities.**

## Remediation

### The four-line fix for everything at or above the gate

Spring Boot **4.0.8** is released and stays inside spec.md S1's `4.0.x` pin. It carries
`postgresql 42.7.13` and `log4j 2.25.5` — closing two findings outright — but pins
`tomcat 11.0.24`, one patch short of the 11.0.25 that fixes the three criticals. So the bump alone is
not enough, and the container version has to be overridden explicitly.

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.8</version>          <!-- was 4.0.7 -->
</parent>

<properties>
  <!-- Boot 4.0.8 pins 11.0.24; 11.0.25 is the fix for CVE-2026-65905/65182/68525. -->
  <tomcat.version>11.0.25</tomcat.version>
  <!-- Boot's managed Jackson is 3.1.4; 3.1.6 closes CVE-2026-68497 and three moderates. -->
  <jackson.version>3.1.6</jackson.version>
  <!-- Boot pins 1.55.0 even at 4.0.8; 1.62.0 closes CVE-2026-45292. -->
  <opentelemetry.version>1.62.0</opentelemetry.version>
</properties>
```

All five target versions were confirmed published on Maven Central at scan time.

Overriding a Boot-managed property is supported but it **unpins** that library from the platform's
tested set, so `mvn verify` has to pass afterwards — which for this project means the full suite twice,
on H2 and on Testcontainers PostgreSQL. Treat a green run as the condition for accepting these bumps.

### Frontend

```bash
cd frontend && npm install --save-dev vitest@^4.1.11   # or @^5 if Vite is also upgraded
```

Lower priority: dev-only, below the CVSS 7 gate, and it has a real compatibility risk against Vite 6.

## Scanner notes

- **`dependency-check-maven` was not run.** Needs `NVD_API_KEY`. The partial cache at
  `~/.m2/repository/org/owasp/dependency-check-data/` (4.7 MB, from an aborted run) is not usable data.
  The plan's gate is therefore **still open** — this report does not close it.
- **CVSS v4.0 vectors are not scored here.** Two findings (`log4j-api`, `postgresql`) publish v4.0
  vectors only. Their GitHub ratings are reported instead, and the postgresql one is flagged High on
  that basis rather than on a computed score.
- **Scope filter**: Maven results cover `compile`, `runtime` and `provided`. Test-scope artifacts
  (ArchUnit, Testcontainers, spring-boot-starter-test) were excluded — they do not ship.
- **Transitive-only coverage.** OSV matches on coordinates. A shaded or repackaged dependency carrying a
  vulnerable class under a different coordinate would be missed; `dependency-check`'s JAR fingerprinting
  is what catches that class of problem, and it has not run.
- `npm audit` exited 1, which is its normal signal that findings exist, not an error.
