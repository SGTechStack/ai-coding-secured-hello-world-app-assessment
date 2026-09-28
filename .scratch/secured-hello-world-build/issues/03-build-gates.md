# 03: Build gates

**What to build:** From this ticket on, `mvn verify` enforces the mandatory build gates.

1. **The `@Proves` traceability gate (ADR-068).** `verify` fails when:
   - a test cites an unknown T-ID;
   - the test-plan table is missing, unreadable or empty, or has the wrong header (T-BLD-007; T-BLD-008);
   - a T-row has no test *and* is not on the **pending ledger**.

   The pending ledger is a checked-in list of T-IDs that don't have tests yet. It starts full. Each later ticket removes the rows it proves, and ticket 28 deletes the ledger. The gate also fails when a ledger entry already has a test, so the ledger can only shrink. It covers Java (`@Proves`) and Vitest/Playwright (the T-ID in the test name).
2. **The register drift gate.** A single Failsafe test regenerates both register renderings, the compliance rendering and the operational handover document, and fails when a committed rendering differs (T-BLD-006; R-BLD-006; ADR-069). The spec's register schema governs both renderings.
3. **Register level expansion.** This is the first build item that touches the register. It expands the `level` column to one entry per requirement on R-ADM-008, R-AUTH-002, R-CFG-005, R-CRED-020, R-CRED-021, R-CRED-024, R-CRED-025, R-MFA-016, R-SES-007 and R-STD-049.
4. **Dependency scanning.** The Maven-bound OWASP Dependency-Check runs in `verify` with its CVSS failure threshold (R-BLD-007; R-BLD-010).
5. **Mutation testing.** A `-Pmutation` PIT profile at threshold 85 is scoped to the security-decision classes. They don't exist yet, so the profile lists their packages and later tickets fill them.

**Blocked by:** 01, 02

**Status:** done (Dependency-Check wiring verified; no full NVD run without an API key)

- [x] A test citing an unknown T-ID fails `verify`.
- [x] Removing a ledger entry whose row has no test fails `verify`, and so does leaving an entry whose row has a test.
- [x] Hand-editing either rendering fails `verify`. Regenerating it passes.
- [x] The ten rows carry one level per requirement, and the renderings are regenerated.
- [x] Dependency-Check runs in `verify` and fails on the configured CVSS threshold. Bound to `verify` at CVSS 7; wiring checked with `help:effective-pom` and a bounded run. A full NVD run needs `NVD_API_KEY`.
- [x] `mvn -Pmutation` runs PIT with `--threshold=85` over the configured scope.
- [x] Neither `-DskipITs` nor `-Dmaven.test.skip` is used by any documented release command (R-BLD-009).
