# 05 — Which logging standards actually bind a two-process app

Type: research
Status: open
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

All 7,169 lines of [`Appfw-Logging-Standards/`](../../../App-Standards/Appfw-Logging-Standards/) are binding on this effort. Produce the concrete, de-duplicated list of requirements that actually apply to a two-process application (React SPA + one Spring Boot service, no batch jobs, no distributed hops).

Read and report on, at minimum:

- `Structured_Logging_Application_Standard.md` (437) and `Log_Schema.md` (352) — the mandatory key/field set, levels, and privacy policy.
- `Recipes/Sensitive_Data_Masking_For_Logs.md` (260) — non-negotiable given the PRD's "plaintext password is never logged" criterion.
- `Recipes/Logging_AuthN_And_AuthZ_Events.md` (516) — maps almost directly onto the PRD's audit list (login success/failure, lockout, reset requested/completed, role change, enable/disable/delete, actor + target).
- `Recipes/Centralising_Audit_Logging_With_A_Typed_Module.md` (343) — whether a typed audit module is required or merely recommended.
- `Recipes/Enriching_Logs_With_MDC.md` (669) — whether the AuthN/AuthZ recipe depends on MDC enrichment, or it stands alone.
- `Recipes/Understanding_OpenTelemetry_And_Micrometer_Tracing.md` (577) and `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md` (1,163) — **the key finding to establish**: what trace correlation means when there is one service and no downstream hop. Is a trace/correlation id still mandatory on every log line and propagated from the SPA, or does the requirement have no surface here?
- `Recipes/Logging_Application_Lifecycle_Events.md` (337) — startup/shutdown event requirements.
- `Recipes/Logging_Batch_And_Scheduled_Jobs.md` (1,254) — confirm this is vacuous (no scheduled jobs remain in scope) rather than silently skipped.
- `Structured_Logging_Application_Standard_Questions.md` (482) — surface any question here that needs a human decision; those become their own tickets.

Deliver a Markdown findings file in the repo, cite standard + line for every requirement, and separate **mandatory** from **recommended**. Distinguish what the standard states from what you infer.

This ticket gates the audit and logging contract (12).
