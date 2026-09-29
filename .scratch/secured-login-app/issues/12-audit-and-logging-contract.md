# 12 — Audit and logging contract

Type: grilling
Status: open
Blocked by: 05
Map: [Secured Login App](../map.md)

## Question

What exactly does this application log, in what shape, and what is retained?

Take the applicable requirement list produced by [05](05-logging-standards-applicability.md) and turn it into this application's concrete audit and logging contract, satisfying the standard's §3.3 Audit Contract and §3.4 Logging Contract (`Standalone_User_Access_Control_Application_Standard.md:269,294`).

Answer `Q28` (audit event retention period) and `Q30` (notification strategy for security events) of the standard's question set. On `Q30`, note the PRD stubs email entirely and MCNS notification standards are out of scope — so the answer may be "log only", but say so deliberately.

Settle:

- The event list, starting from the PRD's audit NFR (`prd/assessment-prd.md:123`): login success, login failure, lockout triggered, password reset requested, password reset completed, role change, enable/disable, delete — each with **actor and target**. Reconcile against the standard's own required event set from 05; where the standard demands more events, the standard wins.
- The field/key schema per `Log_Schema.md`, including how actor and target are represented, and what identifies an anonymous actor on a failed login (username attempted? IP? both — and does recording an attempted username at INFO create an enumeration risk in the logs themselves?).
- Masking rules: passwords never logged (PRD, Story 1), plus reset tokens/links from 11, session ids, and email addresses if the privacy policy requires it.
- Whether a typed audit module is required (per 05's finding on `Centralising_Audit_Logging_With_A_Typed_Module.md`) and where it sits relative to the service layer under §4 Separation of Concerns.
- Retention: `Q28` for audit events, and its relationship to 10's tombstone retention — if an audit row names a deleted user, the two retention periods interact.
- Where audit output goes: the PRD says no dedicated table is required, structured log lines suffice.

Blocked on 05, which establishes which of the 7,169 binding lines actually have surface here.
