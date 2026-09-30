# Ticket 33 dedup (merge agent output)

## Map
| key | action | target | reason |
|---|---|---|---|
| SA-001 | keep |  |  |
| SA-002 | merge | SG1-001 | scheduled account-hygiene jobs not built, one deferral |
| SA-003 | merge | SC-020 | IM8 ac-4 scheduled compare-and-revoke deferral, one fact |
| SA-004 | keep |  |  |
| SA-005 | merge | SC-021 | population-declaration N/A set, carried per IM8 control |
| SA-006 | keep |  |  |
| SA-007 | keep |  |  |
| SA-008 | keep |  |  |
| SA-009 | merge | SB-001 | periodic password expiry N/A by prohibition, one fact |
| SA-010 | merge | SB-003 | no pepper or keyed pre-hash, one declined SHOULD |
| SA-011 | merge | SE1-001 | SameSite=Strict instead of Lax, one deviation |
| SA-012 | merge | SE1-002 | unexercised production configuration, one limitation row |
| SA-013 | keep |  |  |
| SA-014 | keep |  |  |
| SA-015 | keep |  |  |
| SA-016 | keep |  |  |
| SA-017 | keep |  |  |
| SA-018 | keep |  |  |
| SA-019 | keep |  |  |
| SA-020 | keep |  |  |
| SA-021 | keep |  |  |
| SA-022 | merge | SD1-003 | platform-side 90-day audit retention, one obligation |
| SA-023 | keep |  |  |
| SA-024 | keep |  |  |
| SA-025 | merge | SD1-010 | user.id on failure rows reveals account existence, one fact |
| SA-026 | merge | SG2-004 | a needed correlation field, one trigger |
| SA-027 | merge | SG2-002 | the SPA sends trace headers, one trigger |
| SA-028 | keep |  |  |
| SA-029 | keep |  |  |
| SA-030 | keep |  |  |
| SA-031 | merge | SC-032 | mixed authority conventions, one defect |
| SA-032 | keep |  |  |
| SA-033 | keep |  |  |
| SA-034 | merge | SC-028 | incompatible forced-change allowlists, one defect |
| SA-035 | keep |  |  |
| SA-036 | merge | SE1-003 | static-host document CSP and API-side CSP as defence in depth, one row |
| SA-037 | keep |  |  |
| SA-038 | keep |  |  |
| SA-039 | merge | SG3-027 | Std §5:473 N/A by construction, one fact |
| SB-001 | keep |  |  |
| SB-002 | keep |  |  |
| SB-003 | keep |  |  |
| SB-004 | keep |  |  |
| SB-005 | keep |  |  |
| SB-006 | keep |  |  |
| SB-007 | keep |  |  |
| SB-008 | keep |  |  |
| SB-009 | keep |  |  |
| SB-010 | keep |  |  |
| SB-011 | keep |  |  |
| SB-012 | keep |  |  |
| SB-013 | keep |  |  |
| SB-014 | merge | SG1-026 | single-instance limiters, multi-instance consistency unsupported, one fact |
| SB-015 | keep |  |  |
| SB-016 | keep |  |  |
| SB-017 | keep |  |  |
| SB-018 | keep |  |  |
| SB-019 | keep |  |  |
| SB-020 | keep |  |  |
| SB-021 | merge | SG2-017 | runner operator vacancy, one role |
| SB-022 | keep |  |  |
| SB-023 | keep |  |  |
| SB-024 | merge | SG2-015 | red-rehearsal trigger, one event |
| SB-025 | merge | SD1-022 | credential-expired audit-reason oracle: a fired trigger kept as one open design defect (residual, fail) owed back to the lazy-expiry decision |
| SC-001 | keep |  |  |
| SC-002 | keep |  |  |
| SC-003 | keep |  |  |
| SC-004 | keep |  |  |
| SC-005 | merge | SB-016 | per-account redemption limit unknowable before lookup, one defect |
| SC-006 | keep |  |  |
| SC-007 | keep |  |  |
| SC-008 | keep |  |  |
| SC-009 | keep |  |  |
| SC-010 | keep |  |  |
| SC-011 | keep |  |  |
| SC-012 | keep |  |  |
| SC-013 | keep |  |  |
| SC-014 | keep |  |  |
| SC-015 | merge | SG1-011 | dev stub logs reset links, one fact |
| SC-016 | keep |  |  |
| SC-017 | keep |  |  |
| SC-018 | merge | SE2-024 | authentication pathway inventory, one fact |
| SC-019 | keep |  |  |
| SC-020 | keep |  |  |
| SC-021 | keep |  |  |
| SC-022 | keep |  |  |
| SC-023 | keep |  |  |
| SC-024 | keep |  |  |
| SC-025 | keep |  |  |
| SC-026 | keep |  |  |
| SC-027 | keep |  |  |
| SC-028 | keep |  |  |
| SC-029 | keep |  |  |
| SC-030 | keep |  |  |
| SC-031 | keep |  |  |
| SC-032 | keep |  |  |
| SC-033 | keep |  |  |
| SC-034 | keep |  |  |
| SC-035 | keep |  |  |
| SC-036 | keep |  |  |
| SC-037 | keep |  |  |
| SC-038 | merge | SD1-022 | credential-expired audit-reason oracle: a fired trigger kept as one open design defect (residual, fail) owed back to the lazy-expiry decision |
| SC-039 | merge | SE2-030 | seeded admin authenticator binding and first-enroller race, one fact |
| SC-040 | merge | SD2-006 | TM-08 admin-on-admin takeover, one accepted risk |
| SC-041 | merge | SD2-007 | TM-09 tombstone without role, one accepted risk |
| SC-042 | keep |  |  |
| SC-043 | merge | SG3-027 | Std §5:473 N/A by construction, one fact |
| SC-044 | keep |  |  |
| SC-045 | keep |  |  |
| SC-046 | keep |  |  |
| SC-047 | keep |  |  |
| SC-048 | keep |  |  |
| SC-049 | keep |  |  |
| SC-050 | keep |  |  |
| SC-051 | keep |  |  |
| SC-052 | keep |  |  |
| SC-053 | keep |  |  |
| SC-054 | keep |  |  |
| SC-055 | keep |  |  |
| SC-056 | merge | SG3-029 | PIN factor not built, one N/A |
| SD1-001 | keep |  |  |
| SD1-002 | keep |  |  |
| SD1-003 | keep |  |  |
| SD1-004 | keep |  |  |
| SD1-005 | merge | SA-021 | three-field schema amendment request, one obligation |
| SD1-006 | keep |  |  |
| SD1-007 | merge | SF1-023 | use-jackson2-defaults excluded from the prohibited list, one fact |
| SD1-008 | keep |  |  |
| SD1-009 | keep |  |  |
| SD1-010 | keep |  |  |
| SD1-011 | keep |  |  |
| SD1-012 | keep |  |  |
| SD1-013 | keep |  |  |
| SD1-014 | merge | SG2-013 | runner stdout as a documented log destination, one fact |
| SD1-015 | keep |  |  |
| SD1-016 | keep |  |  |
| SD1-017 | keep |  |  |
| SD1-018 | keep |  |  |
| SD1-019 | keep |  |  |
| SD1-020 | keep |  |  |
| SD1-021 | keep |  |  |
| SD1-022 | keep |  |  |
| SD1-023 | keep |  |  |
| SD1-024 | keep |  |  |
| SD1-025 | keep |  |  |
| SD1-026 | merge | SF2-015 | mail-transport trigger, one event |
| SD1-027 | merge | SC-050 | im8-review MFA-flag false negative, one fact |
| SD1-028 | keep |  |  |
| SD1-029 | merge | SC-019 | self-service TOTP enrolment reopens pending registration, one trigger |
| SD2-001 | merge | SE1-007 | who terminates TLS, one worked row |
| SD2-002 | merge | SG1-011 | stubbed email dev log-read takeover; its outside-dev half keeps its own row |
| SD2-003 | merge | SD1-002 | no separate audit store, one forwarder; the read-access and tamper-proofing half is the audit-log read-access row |
| SD2-004 | merge | SG1-026 | single-instance limiters, multi-instance consistency unsupported, one fact |
| SD2-005 | merge | SC-008 | username availability disclosed at registration, one ASVS 6.3.8 fact |
| SD2-006 | keep |  |  |
| SD2-007 | keep |  |  |
| SD2-008 | merge | SF2-030 | audit truncation alerting, one obligation; its sizing half is the threshold-recompute row |
| SD2-009 | merge | SG1-015 | no global bulkhead and aggregate capacity (TM-07), one residual |
| SD2-010 | keep |  |  |
| SD2-011 | merge | SG1-024 | production-database locking, types, collation and DDL portability unexercised, one fidelity item |
| SD2-012 | merge | SG1-024 | production-database locking, types, collation and DDL portability unexercised, one fidelity item |
| SD2-013 | merge | SG1-024 | production-database locking, types, collation and DDL portability unexercised, one fidelity item |
| SD2-014 | merge | SE1-002 | unexercised production configuration, one limitation row |
| SD2-015 | merge | SG1-027 | deployer launch command unexercised by the harness, one fidelity item |
| SD2-016 | merge | SG1-028 | ASVS 6.3.8 verified by call count, not wall clock, one fact |
| SD2-017 | merge | SG1-029 | WebKit and Safari not exercised, one fidelity item |
| SD2-018 | merge | SG1-001 | scheduled account-hygiene jobs not built, one deferral |
| SD2-019 | merge | SG1-014 | durable 90-day audit retention not built beyond the host, one deferral |
| SD2-020 | merge | SG1-022 | notification SHALL failed for want of a transport, one fact |
| SD2-021 | merge | SG1-023 | Dependency-Check bound to verify rather than a CI gate, one deferral |
| SD2-022 | merge | SG1-030 | Surefire and Failsafe pin with two bypass flags, one residual |
| SD2-023 | merge | SB-013 | per-account throttle unreachable by pure-failure traffic, one defect |
| SD2-024 | keep |  |  |
| SD2-025 | keep |  |  |
| SD2-026 | merge | SG3-027 | Std §5:473 N/A by construction, one fact |
| SD2-027 | merge | SG3-028 | batch password reset N/A, one fact |
| SD2-028 | keep |  |  |
| SD2-029 | merge | SF2-015 | mail-transport trigger, one event |
| SD2-030 | merge | SG3-016 | change to k, one trigger; the cron half keeps its own row |
| SD2-031 | keep |  |  |
| SD2-032 | merge | SE2-023 | ASVS 6.3.3 MFA on the admin surface only, one grade |
| SD2-033 | merge | SE2-029 | no notification when an authenticator is bound, one SHALL |
| SD2-034 | merge | SF2-016 | recovery-codes deferral reversed, one trigger |
| SE1-001 | keep |  |  |
| SE1-002 | keep |  |  |
| SE1-003 | keep |  |  |
| SE1-004 | keep |  |  |
| SE1-005 | keep |  |  |
| SE1-006 | keep |  |  |
| SE1-007 | keep |  |  |
| SE1-008 | keep |  |  |
| SE1-009 | keep |  |  |
| SE1-010 | keep |  |  |
| SE1-011 | keep |  |  |
| SE1-012 | keep |  |  |
| SE1-013 | keep |  |  |
| SE1-014 | keep |  |  |
| SE1-015 | merge | SD1-002 | no separate audit store, one forwarder; the read-access and tamper-proofing half is the audit-log read-access row |
| SE1-016 | merge | SF2-029 | disk-space threshold recompute, one obligation |
| SE1-017 | merge | SF2-017 | git or build-info plugin added, one trigger |
| SE1-018 | keep |  |  |
| SE1-019 | keep |  |  |
| SE1-020 | keep |  |  |
| SE2-001 | keep |  |  |
| SE2-002 | keep |  |  |
| SE2-003 | keep |  |  |
| SE2-004 | keep |  |  |
| SE2-005 | keep |  |  |
| SE2-006 | keep |  |  |
| SE2-007 | keep |  |  |
| SE2-008 | keep |  |  |
| SE2-009 | keep |  |  |
| SE2-010 | keep |  |  |
| SE2-011 | keep |  |  |
| SE2-012 | keep |  |  |
| SE2-013 | keep |  |  |
| SE2-014 | keep |  |  |
| SE2-015 | keep |  |  |
| SE2-016 | keep |  |  |
| SE2-017 | keep |  |  |
| SE2-018 | keep |  |  |
| SE2-019 | keep |  |  |
| SE2-020 | keep |  |  |
| SE2-021 | keep |  |  |
| SE2-022 | merge | SB-023 | break-glass access restoration, one worked row |
| SE2-023 | keep |  |  |
| SE2-024 | keep |  |  |
| SE2-025 | merge | SB-018 | one ASVS 6.1.1 malicious-lockout documentation verdict |
| SE2-026 | keep |  |  |
| SE2-027 | keep |  |  |
| SE2-028 | keep |  |  |
| SE2-029 | keep |  |  |
| SE2-030 | keep |  |  |
| SE2-031 | keep |  |  |
| SE2-032 | merge | SA-033 | one app.* property prefix, TOTP keys included |
| SE2-033 | merge | SA-038 | 422 for factor enrolment required, one off-label status |
| SE2-034 | keep |  |  |
| SE2-035 | merge | SB-023 | break-glass access restoration, one worked row |
| SE2-036 | keep |  |  |
| SE2-037 | keep |  |  |
| SF1-001 | keep |  |  |
| SF1-002 | keep |  |  |
| SF1-003 | keep |  |  |
| SF1-004 | keep |  |  |
| SF1-005 | keep |  |  |
| SF1-006 | keep |  |  |
| SF1-007 | keep |  |  |
| SF1-008 | keep |  |  |
| SF1-009 | keep |  |  |
| SF1-010 | keep |  |  |
| SF1-011 | keep |  |  |
| SF1-012 | keep |  |  |
| SF1-013 | keep |  |  |
| SF1-014 | keep |  |  |
| SF1-015 | keep |  |  |
| SF1-016 | keep |  |  |
| SF1-017 | keep |  |  |
| SF1-018 | keep |  |  |
| SF1-019 | keep |  |  |
| SF1-020 | keep |  |  |
| SF1-021 | merge | SC-053 | bounded tombstone retention makes old HMAC key versions retirable, one trigger |
| SF1-022 | merge | SD1-006 | Jackson source-inclusion pin lives outside the validator, one fact |
| SF1-023 | keep |  |  |
| SF1-024 | keep |  |  |
| SF1-025 | keep |  |  |
| SF1-026 | keep |  |  |
| SF1-027 | merge | SG1-009 | ASVS citation hygiene, one note |
| SF1-028 | keep |  |  |
| SF2-001 | merge | SG1-022 | notification SHALL failed for want of a transport, one fact |
| SF2-002 | keep |  |  |
| SF2-003 | keep |  |  |
| SF2-004 | merge | SF1-014 | no vault, KMS or HSM, one fact |
| SF2-005 | merge | SD1-002 | no separate audit store, one forwarder; the read-access and tamper-proofing half is the audit-log read-access row |
| SF2-006 | merge | SF1-008 | H2 datasource runs as administrator with an unchanging password, one fact |
| SF2-007 | merge | SF1-004 | key randomness rests on the documented generation command, one fact |
| SF2-008 | keep |  |  |
| SF2-009 | merge | SE1-009 | IM8 lm-16 alerting half, one fact |
| SF2-010 | merge | SG1-010 | drift family note with its companions, one row |
| SF2-011 | merge | SG1-008 | adopted Reading B note, one row |
| SF2-012 | merge | SB-018 | one ASVS 6.1.1 malicious-lockout documentation verdict |
| SF2-013 | keep |  |  |
| SF2-014 | keep |  |  |
| SF2-015 | keep |  |  |
| SF2-016 | keep |  |  |
| SF2-017 | keep |  |  |
| SF2-018 | merge | SG2-015 | red-rehearsal trigger, one event |
| SF2-019 | keep |  |  |
| SF2-020 | keep |  |  |
| SF2-021 | keep |  |  |
| SF2-022 | keep |  |  |
| SF2-023 | keep |  |  |
| SF2-024 | keep |  |  |
| SF2-025 | keep |  |  |
| SF2-026 | keep |  |  |
| SF2-027 | keep |  |  |
| SF2-028 | keep |  |  |
| SF2-029 | keep |  |  |
| SF2-030 | keep |  |  |
| SF2-031 | keep |  |  |
| SG1-001 | keep |  |  |
| SG1-002 | keep |  |  |
| SG1-003 | keep |  |  |
| SG1-004 | keep |  |  |
| SG1-005 | keep |  |  |
| SG1-006 | merge | SE1-007 | who terminates TLS, one worked row |
| SG1-007 | keep |  |  |
| SG1-008 | keep |  |  |
| SG1-009 | keep |  |  |
| SG1-010 | keep |  |  |
| SG1-011 | keep |  |  |
| SG1-012 | keep |  |  |
| SG1-013 | merge | SD1-002 | no separate audit store, one forwarder; the read-access and tamper-proofing half is the audit-log read-access row |
| SG1-014 | keep |  |  |
| SG1-015 | keep |  |  |
| SG1-016 | merge | SC-008 | username availability disclosed at registration, one ASVS 6.3.8 fact |
| SG1-017 | merge | SD2-006 | TM-08 admin-on-admin takeover, one accepted risk |
| SG1-018 | merge | SD2-007 | TM-09 tombstone without role, one accepted risk |
| SG1-019 | merge | SB-022 | mass permanent-lockout primitive, one residual |
| SG1-020 | merge | SF2-015 | mail-transport trigger, one event |
| SG1-021 | merge | SE1-004 | inline script in the production document, one trigger |
| SG1-022 | keep |  |  |
| SG1-023 | keep |  |  |
| SG1-024 | keep |  |  |
| SG1-025 | merge | SE1-002 | unexercised production configuration, one limitation row |
| SG1-026 | keep |  |  |
| SG1-027 | keep |  |  |
| SG1-028 | keep |  |  |
| SG1-029 | keep |  |  |
| SG1-030 | keep |  |  |
| SG1-031 | merge | SD2-028 | Surefire or Failsafe version change, one trigger |
| SG2-001 | keep |  |  |
| SG2-002 | keep |  |  |
| SG2-003 | keep |  |  |
| SG2-004 | keep |  |  |
| SG2-005 | keep |  |  |
| SG2-006 | keep |  |  |
| SG2-007 | keep |  |  |
| SG2-008 | keep |  |  |
| SG2-009 | keep |  |  |
| SG2-010 | keep |  |  |
| SG2-011 | keep |  |  |
| SG2-012 | keep |  |  |
| SG2-013 | keep |  |  |
| SG2-014 | keep |  |  |
| SG2-015 | keep |  |  |
| SG2-016 | merge | SF2-015 | mail-transport trigger, one event |
| SG2-017 | keep |  |  |
| SG2-018 | keep |  |  |
| SG2-019 | keep |  |  |
| SG2-020 | merge | SB-023 | break-glass access restoration, one worked row |
| SG2-021 | keep |  |  |
| SG2-022 | keep |  |  |
| SG2-023 | keep |  |  |
| SG2-024 | keep |  |  |
| SG2-025 | keep |  |  |
| SG2-026 | keep |  |  |
| SG2-027 | keep |  |  |
| SG2-028 | keep |  |  |
| SG2-029 | keep |  |  |
| SG2-030 | keep |  |  |
| SG2-031 | keep |  |  |
| SG2-032 | keep |  |  |
| SG2-033 | keep |  |  |
| SG3-001 | keep |  |  |
| SG3-002 | keep |  |  |
| SG3-003 | merge | SE1-019 | authenticable-admins gauge export and alert below two, one obligation |
| SG3-004 | keep |  |  |
| SG3-005 | keep |  |  |
| SG3-006 | keep |  |  |
| SG3-007 | keep |  |  |
| SG3-008 | keep |  |  |
| SG3-009 | keep |  |  |
| SG3-010 | keep |  |  |
| SG3-011 | keep |  |  |
| SG3-012 | keep |  |  |
| SG3-013 | merge | SB-020 | cardinality-axis refusal of legitimate sources, one residual |
| SG3-014 | keep |  |  |
| SG3-015 | keep |  |  |
| SG3-016 | keep |  |  |
| SG3-017 | keep |  |  |
| SG3-018 | keep |  |  |
| SG3-019 | merge | SF2-015 | mail-transport trigger, one event |
| SG3-020 | keep |  |  |
| SG3-021 | merge | SF2-019 | edge per-source limit, one obligation that IPv6 prefix aggregation tightens |
| SG3-022 | keep |  |  |
| SG3-023 | keep |  |  |
| SG3-024 | keep |  |  |
| SG3-025 | merge | SB-016 | per-account redemption limit unknowable before lookup, one defect |
| SG3-026 | keep |  |  |
| SG3-027 | keep |  |  |
| SG3-028 | keep |  |  |
| SG3-029 | keep |  |  |
| SG3-030 | keep |  |  |
| SG3-031 | keep |  |  |
| SG3-032 | keep |  |  |
| SG3-033 | keep |  |  |
| SG3-034 | keep |  |  |
| SG3-035 | merge | SG2-003 | an outbound call is added, one event with two dependents |
| SH-001 | keep |  |  |
| SH-002 | keep |  |  |
| SH-003 | keep |  |  |
| SH-004 | keep |  |  |
| SH-005 | keep |  |  |
| SH-006 | keep |  |  |
| SH-007 | keep |  |  |
| SH-008 | keep |  |  |
| SH-009 | keep |  |  |
| SH-010 | keep |  |  |
| SH-011 | keep |  |  |
| SH-012 | keep |  |  |
| SH-013 | keep |  |  |
| SH-014 | keep |  |  |
| SH-015 | keep |  |  |
| SH-016 | keep |  |  |
| SH-017 | keep |  |  |
| SH-018 | keep |  |  |
| SH-019 | keep |  |  |
| SH-020 | keep |  |  |
| SH-021 | keep |  |  |
| SH-022 | keep |  |  |
| SH-023 | keep |  |  |
| SH-024 | keep |  |  |
| SH-025 | keep |  |  |
| SH-026 | keep |  |  |

## Edits
| key | column | value | reason |
|---|---|---|---|
| SD1-001 | requirement | ASVS 16.1.1; ASVS 16.4.2 | worked row: audit-log read access; separation stays on its own row |
| SD1-001 | level | L2; L2 | levels follow requirement |
| SD1-001 | subject | Audit-log read access: log neither access-controlled nor tamper-proof | worked-row name |
| SE1-007 | level | IM8 (MUST, sev 2/2); —; PRD | one level per requirement |
| SB-023 | kind | obligation | worked row carries a conditional pass, not a deviation |
| SB-023 | subject | Break-glass access restoration through the offline rebinding runner | worked-row name |
| SB-023 | requirement | NIST SP 800-63B-4 §4.2.2.2; ASVS 6.4.4; ASVS 6.5.6; ASVS 6.4.1 | worked-row IDs; the §4.2.3 notification SHALL keeps its own failed row |
| SB-023 | level | SHALL; L2; L3; L1 | levels follow requirement |
| SB-023 | verdict | conditional-pass | worked-row grade: mail transport is the single named prerequisite |
| SB-023 | sequence | 7 | worked-row anchor |
| SB-023 | decision | Access restoration after a lost or disabled authenticator runs through the offline rebinding runner, which takes an operator-set password and emits no secret. The invalidation half is satisfied under NIST §4.3 and §4.5; the access-restoration half is a conditional pass with mail transport as its single named prerequisite, and until a transport exists the shell runner is an accepted failure with that same single cause. | worked-row statement; the runner now emits no secret |
| SB-023 | rationale | NIST SP 800-63B-4 §4.2.2.2 option 2 is one recovery code plus a bound single-factor authenticator, and §3.1.1 makes a password one; §3.1.3.1 permits emailed recovery codes with a 24-hour cap. Under the adopted Reading B an application-specific method must still land inside a §4.2.2 class, so the §4.2.1 risk analysis is the interim compensating artefact, not the compliance route. ASVS 6.4.4 (L2) is met by parity with enrolment, ASVS 6.5.6 (L3) by in-app factor revocation, and ASVS 6.4.1 (L1) attaches to the operator-set password, which forces a change at first login. | standalone rationale with primary citations |
| SB-023 | residual | Until a mail transport exists, restoration needs deploy-level shell access and a planned outage, and the §4.2.3 notification is not sent. | worked-row interim |
| SB-023 | acceptance | The recovery rehearsal on the deployed topology passes (runner rebinding, a tier-2 factor disable cleared through the break-glass path, the output warning confirmed), plus the documented §4.2.1 risk analysis covering the interim. | worked-row acceptance |
| SD1-022 | kind | residual | ruling: open design defect, not a pending trigger |
| SD1-022 | requirement | Std §3.4 (no passwords in logs) | the reason confirms password correctness in the audit stream |
| SD1-022 | verdict | fail | ruling |
| SD1-022 | decision | Open design defect owed back to the lazy 30-day expiry decision: the expiry runs in the post-authentication checks, so its audit reason is written only when the submitted password matched. The check must move to the pre-authentication checks, or its reason must be made indistinguishable from a wrong-password failure. | ruling wording |
| SB-018 | verdict | pass-with-note | its decision cell and the sole-admin premise grade it pass-with-note pending first rehearsal |
| SD2-007 | verdict | partial | IM8 ac-7 partial, as the owning admin-module row grades it |
| SG1-022 | responsibility | shared | delivery rests on the deployer mail transport |
| SG1-022 | priority | blocking | mail transport gates real accounts |
| SG1-022 | sequence | 5 | mail transport step |
| SG1-022 | acceptance | A real mail transport is configured and a rehearsed recovery or credential event delivers a notification to the owner address; until then the row reads fail. | better twin statement |
| SB-003 | kind | deviation | a declined SHOULD with a stated reason |
| SB-003 | verdict | pass-with-note | declined SHOULD documented on non-rotatability; the source names it a declined SHOULD, not a failure |
| SC-016 | pillar | STD | register-wide note, same home as the other register-wide notes |
| SC-016 | requirement | ASVS 5.0 (declared target level) | the note binds every ASVS row, not 6.3.3 |
| SC-016 | level | L1 | declared target |
| SC-008 | requirement | ASVS 6.3.8 | the PRD Story 1 AC2 half is its own PRD-deviation row |
| SC-008 | level | L3 | level follows requirement |
| SF2-015 | requirement | Break-glass conditional pass; the notification SHALLs; dev-only reset-link confinement and recovery containment; the tier-2 distinct-user audit cap; runner recovery designed around no transport; the lockout warning-window runbook | every reopened decision on the one event |
| SF2-015 | decision | When a real mail transport enters scope: the break-glass conditional pass closes; the three notification SHALLs resolve together; log-leak containment for administrators (TM-13) inverts from partial to total, because a dev-log reader holding a reset link and an issued recovery code holds NIST §4.2.2.2 option 2 in full, so the recovery-code route and recovery-address confirmation stay structurally unavailable while the transport is the stub, gated on a declared transport property and never on the `EmailService` bean type; the tier-2 audit key space must be re-argued against an attacker-growable population; both runner paths, batch recovery included, converge on the normal reset flow; and the owner reset becomes real outside dev, so the warning-window runbook is rewritten. | ruling 2: every consequence in one decision |
| SF2-015 | acceptance | T-CRED-023 passes today; when the transport lands, each consequence is re-graded, the absence test gains a pinned HTTP status per path and principal, the mass-lockout recovery row is regraded, and the batch and warning-window procedures are rewritten to rely on resets. | union of twin acceptances |
| SB-016 | decision | Token-redemption endpoints (password-reset confirm, registration activate) cannot carry the mandated per-account limit, because the account is unknowable before the token lookup; they are limited per source key only, and the per-source budget is recorded in its place. | carries the redemption-half deviation merged in |
| SG2-003 | requirement | Inbound trace context restarted at the application boundary; outbound correlation-ID injection not applicable while no outbound call exists | one event, two dependents |

## Minted
| key | inv | source | pillar | kind | requirement | level | verdict | subject | decision | rationale | residual | responsibility | status | priority | sequence | acceptance | refs | dup | note |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Self-check

- Keys in Map: 424 (unique 424); every staged key appears exactly once.
- Every merge target is a `keep` row (asserted by the generator); no merge targets another merge.
- keep 324 / merge 100 / retire 0 / minted 0.
- Checklist: every routing §5 ADR and REJ ID sits on a kept or merged row (refs union into the kept row); every routing §6 PRD line has a row naming its story and AC; the nine rows owed by the test-plan transcription, the §6a fidelity and §6b not-built rows, the runner (seven), anonymous-session (five) and sole-admin (TM-08 widened) inputs, and the handover document's nine register entries are all present, so nothing was minted.

Judgement calls for the resolver:

- Audit store: the read-access worked row (16.1.1, 16.4.2) and the separation row (16.4.3) stay apart per ruling 8; the four twins that state both merge into the separation row.
- TLS worked row keeps verdict partial (IM8 as-10 WARN/FAIL); two twins said conditional-pass and the worked row gives none.
- Break-glass worked row absorbs the 6.4.4/6.5.6 parity note, the operator-set-password handover and the tier-2 break-glass runbook; the §4.2.3 notification stays a separate failed row.
- Notifications: the not-built owner-notification row absorbs the §4.2.3 recovery-notification entry and becomes shared, blocking, step 5.
- Pepper decline regraded from fail to pass-with-note, kind deviation (declined SHOULD with a stated reason).
- ASVS 6.3.3 graded partial (admin surface only) over fail; the whole-application-L2-not-claimable row merges into it.
- ASVS 6.1.1 verdict set to pass-with-note (the row said conditional-pass while its decision said pass-with-note); the factor-axis 6.1.1 note merges into it.
- The redemption-half deviation owed by the test-plan transcription merges into the per-account-limit defect per ruling 5.
- 90-day retention kept as two rows: the not-built durable store and the platform TTL obligation.
- Per-control IM8 N/A rows kept; the four-control population-declaration row merges into the SSO row.
- Multi-instance limiter rows collapse onto the fidelity row (n/a, none); the aggregate-capacity (TM-07) half joins the no-bulkhead residual.
- The k-or-cron trigger merges into the k trigger; the cron trigger keeps its own row.
- The GCM IV note is kept although one source withdrew it, because a later source lists it among register entries.
- The credential-expired oracle keeps pillar AUD and cites Std §3.4; that requirement is a judgement.
