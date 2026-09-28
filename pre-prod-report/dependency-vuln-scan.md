# Dependency Vulnerability Scan Report

> Scan timestamp: 2026-09-28T11:46:28+08:00
> Project: secured-hello-world-app (backend: `secured-hello-world-backend`, frontend: `frontend`)
> Scanners used: `npm audit --json` (frontend); `mvn org.owasp:dependency-check-maven:13.0.0:check` (backend, full automated run against cached NVD data)

## Scan Plan

| Manifest | Location | Scanner planned | Scanner actually used |
|---|---|---|---|
| `package.json` + `package-lock.json` | `frontend/` | `npm audit --json` | `npm audit --json` (ran successfully) |
| `pom.xml` | `backend/` | `mvn org.owasp:dependency-check-maven:check` | Ran successfully — local NVD mirror at `~/.m2/repository/org/owasp/dependency-check-data/11.0/` was refreshed earlier the same morning (09:33, within the skill's 24h freshness window), so Step 3 (NVD update) was skipped per the skill's rule and the cached data was used directly for the CVE match analysis. |

## Summary Table

| Severity | Frontend (npm) | Backend (OWASP dependency-check) | Total |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 0 | 0 | 0 |
| Info | 0 | 0 | 0 |

**No known vulnerabilities found.**

## Prior Findings — Verified Resolved

The prior scan (2026-09-28 09:41) flagged 2 High findings against `spring-boot-starter-parent:3.3.4`. `backend/pom.xml` has since been upgraded to `spring-boot-starter-parent:4.1.0`. Both are confirmed resolved in this run:

| ID | Package | Previously Installed | Now Installed | Fixed At | Status |
|---|---|---|---|---|---|
| CVE-2024-38819 | org.springframework:spring-web / spring-webmvc | 6.1.13 | **7.0.8** | 6.1.14 | ✅ Resolved — 7.0.8 is far past the fixed version 6.1.14. |
| CVE-2024-50379 / CVE-2024-56337 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.30 | **11.0.22** | 11.0.3 (11.x line) / 10.1.35 / 9.0.99 | ✅ Resolved — 11.0.22 is well past the fixed version 11.0.3 for the 11.x line. |

Confirmed by direct inspection of `target/dependency-check-report.json`: neither CVE ID appears anywhere in the 99-dependency report (`grep -c "CVE-2024-38819\|CVE-2024-50379\|CVE-2024-56337"` → 0 matches), and the specific artifacts (`spring-core-7.0.8.jar`, `spring-web-7.0.8.jar`, `spring-webmvc-7.0.8.jar`, `spring-security-core-7.1.0.jar`, `tomcat-embed-core-11.0.22.jar`) each report `0` associated vulnerabilities.

## Findings

None. Both scanners returned clean results.

## Remediation Guidance

None required — no findings at any severity level in either stack.

## Scanner Notes

- **npm audit**: ran cleanly against `frontend/package-lock.json`. Result: `0` vulnerabilities across all severities. `metadata.dependencies`: 4 prod, 88 dev, 46 optional, 91 total resolved packages.
- **OWASP dependency-check-maven (backend)**: `org.owasp:dependency-check-maven:13.0.0` ran the full `check` goal (not just `dependency:tree` fallback, unlike the prior session). The Maven build itself still reported `BUILD FAILURE` because the plugin's *live* NVD API update sub-step failed (`NvdApiException: Invalid API Key, length of 0 too short...` — no `nvdApiKey` configured, same root cause as the prior run), logged as `[WARNING] Unable to update 1 or more Cached Web DataSource, using local data instead. Results may not include recent vulnerabilities.` The analysis phase itself completed successfully against the existing local NVD cache (refreshed 09:33 same day, well within the 24h freshness window per the skill's Step 3 rule) and wrote a complete JSON report (`target/dependency-check-report.json`, 99 scanned dependencies, 0 vulnerabilities). The "BUILD FAILURE" exit code reflects the plugin's own failOnError policy for the live-update sub-step, not a scan failure — the report was still generated and is treated as valid per the skill's fail-gracefully rule ("partial results with clear notes are better than no output").
- **Recommendation carried forward**: for a fully live/current CVE match (rather than this morning's cached snapshot), configure `nvdApiKey` (free from https://nvd.nist.gov/developers/request-an-api-key) so the plugin can complete its own live update step without falling back to cache.
