# 40 — Write the authentication ADRs: the MFA factor

Type: task
Status: resolved
Blocked by: 34
Blocks: 18

## Question

Write the eight ADRs that [ticket 34](34-adr-candidate-list.md) routed to this ticket. They go to `docs/adr/`, in
the `domain-modeling` skill's ADR format: **ADR-021 to ADR-028**. That covers the following:

- session-scoped factor authority;
- the TOTP key location and the context prefix;
- the admin-only MFA scope and the recovery-codes deferral;
- the provisioning envelope;
- hand-composed factor rules;
- the two-tier TOTP lockout.

*Split from [35](35-adrs-authentication.md) by ticket 34.* It is owner 35's work, by 17 Answer §2's source split.

The ID, title, merged sources, attached amendments and filter answer for each ADR are in
[`adr-routing/routing.md`](../adr-routing/routing.md) §2.

## Rules

Same as [35](35-adrs-authentication.md) §Rules.

## Done when

- ADR-021 to ADR-028 exist, and their `docs/adr/README.md` index rows move from `reserved`.
- The grep in 35's Done-when returns nothing for these files.

## Answer

**ADR-021 to ADR-028 are written in `docs/adr/` and marked `accepted` in the index, as routed, with no regrouping.**
Every attached amendment in routing §2 is folded into its ADR's text. The Done-when grep (`\b\d{2}:\d+`, `ticket \d`,
`\d{2} ADR`, `.scratch`, plus `L\d{2,3}` and inventory keys) returns nothing for the eight files. All 44 `R-`, `T-`
and `REJ-` IDs they cite resolve in `docs/register/register.md`, `docs/test-plan/test-plan.md` or the rejection log.

| ADR | File |
|---|---|
| ADR-021 | `0021-session-scoped-factor-authority.md` |
| ADR-022 | `0022-totp-key-outside-the-database.md` |
| ADR-023 | `0023-totp-for-administrators-only.md` |
| ADR-024 | `0024-recovery-codes-deferred.md` |
| ADR-025 | `0025-json-provisioning-envelope.md` |
| ADR-026 | `0026-hand-composed-factor-rules.md` |
| ADR-027 | `0027-two-tier-totp-lockout.md` |
| ADR-028 | `0028-context-prefix-instead-of-aad.md` |

### Verification (map Notes rule)

Re-checked at primary source this session, not taken from the tickets:

- **Spring Security 7.1.x source** (`7.1.x` branch): `AuthorizationManagers.allOf(managers...)` defaults to
  `new AuthorizationDecision(true)` on all-abstain (fail-open); `AllRequiredFactorsAuthorizationManager`'s
  `getFactorGrantedAuthorities` returns **all** authorities despite its javadoc, a null `validDuration` grants on a
  string match, and a non-`FactorGrantedAuthority` under a non-null duration yields `createExpired`.
  `DefaultAuthorizationManagerFactory` composition was already verified in
  `research/spring-security-7.1.x-mfa-api-verification.md` §1b.
- **`AesGcmBytesEncryptor`** (7.1.x source): 16-byte random IV, 128-bit tag, IV prepended, `withSecretKey` builder,
  `BytesEncryptor` surface only, private `Cipher` fields. So 69 bytes and "AAD unreachable" both hold.
- **CVE-2026-47842** (HeroDevs migration guide): only the null-IV CBC path is exposed; `Encryptors.standard()`,
  `stronger()`, `text()` and `delux()` are not exposed but **are deprecated and password-derived**.
- **MFA_Core standard text**: §4.1 Provider Dispatch "On each request…" (enforced), §3.4 "is stored in the database"
  (enforced), Q13's "via another mechanism", §3.4's "10 cumulative failures within a 1-hour window … until
  administrative review", §2.2 step 5 "PNG bytes"; `MFA_Frontend/Standalone` §1.2 and the `MFAPrompt` row; the PRD's
  Out of scope MFA line.
- **NIST SP 800-63B-4 §3.2.2** quoted from `research/boot-4.1-actuator-observability-and-nist-throttling-verification.md`.

### What the checking changed

1. **ADR-024's routing filter sentence was too broad.** "Adding them naively inverts containment while email is
   stubbed" holds for *issued* (emailed) codes, not for *saved* codes shown in-session at enrolment, which is what
   ADR-024 defers. The ADR says so; ADR-070 carries the inversion.
2. **`Encryptors.stronger()` is "not exposed", not "fine".** The ticket 11 amendment said it was not implicated. True,
   but the same patch deprecates it and it is password-derived, so ADR-022 still excludes it, on those grounds.
3. **Ticket 19's `@Value` key binding is superseded** by ADR-062's `@ConfigurationProperties`; ADR-022 cites ADR-062.
4. **Tier 2 is not cleared "only by `DELETE …/totp`"**, as ticket 23 §5 says. The offline runner's `totp` scope also
   clears it (ADR-072). ADR-027 names both.
5. **C:19-M-x2 has two halves.** The 69-byte half is folded into ADR-028. The MFA-flag false-negative half already
   lives in ADR-053 and R-MFA-005, so it was not folded here. No routing amendment is needed, because nothing moves.

### Handover items (ticket 25)

None new. Every operator obligation these ADRs touch already has a row: yearly key rotation (R-CFG-005, MFA_Core
§3.4 and Q14), the context-prefix alarm (R-MFA-020),
and break-glass (R-RUN-001).
