# Map: Secured Login App

Label: `wayfinder:map`
Effort: `secured-login-app`
Tickets: [`issues/`](issues/)

## Destination

A **locked plan** for the PRD's username/password auth app (React SPA + Spring Boot, session-cookie auth): every non-obvious decision resolved, so `/do-work` can build it to Appfw Standalone-Login conformance without stopping to decide anything. The map is done when no ticket remains and [17 — Handoff to delivery pipeline](issues/17-handoff-to-delivery-pipeline.md) has handed the plan to `stories-to-issues` → `/do-work`.

Planning only. This map resolves decisions; it does not build the app.

## Notes

**Domain.** Secure username/password login: registration, login, lockout, IP throttling, logout, protected greeting, password reset, admin user management, admin bootstrap. See [`prd/assessment-prd.md`](../../prd/assessment-prd.md) for the 12 stories, NFRs, data model and testing requirements.

**Authority order.** Settled while charting:

1. The Appfw standards in [`App-Standards/`](../../App-Standards/) **win wherever both they and the PRD speak.** Consequences already known: roles become `USER`/`USER_MANAGER` (not `ADMIN`) — **settled in 03**, together with single-role-per-user and a validated `String` in place of the PRD's Java `enum`; hard delete becomes a tombstone with a retention period; password history enters scope.
2. The PRD's **explicit** exclusions hold: JWT, MFA, real SMTP, containerization/CI-CD/hosting, local HTTPS, fine-grained per-resource authz. The last is compatible with the standard, which permits omitting privileges (`Standalone_User_Access_Control_Application_Standard_Questions.md:485`).
3. Standard-only **subsystems** that serve no PRD story are out of scope — see [Out of scope](#out-of-scope).

**Binding standards set.**

- All of [`Appfw-User-Standards/User_Standalone/`](../../App-Standards/Appfw-User-Standards/User_Standalone/) — the 670-line standard, its 760-line question set, and all four Standalone recipes except scheduled hygiene jobs.
- [`Appfw-User-Standards/Shared_Recipes/`](../../App-Standards/Appfw-User-Standards/Shared_Recipes/): Security Headers & SPA CSRF Configuration, Role-Based Access Control Configuration, Secure Self-Read User Endpoint. **Not** Automatic DB Role Synchronization & Deleted Role Backups.
- All of [`Appfw-Logging-Standards/`](../../App-Standards/Appfw-Logging-Standards/) (7,169 lines). **05 read all of it**; its Answer is the de-duplicated applicable set, and the two recipes 05's brief did not name — `Custom_Structured_Log_Encoder.md` and `Logging_Exceptions_With_Enhanced_Details.md` — were covered too. The vacuity call on `Recipes/Logging_Batch_And_Scheduled_Jobs.md` is **confirmed** (its gates are Spring Batch on the classpath and `@Scheduled`; neither holds), but note how cheaply it trips: `@Scheduled` needs no dependency at all, so adding an expired-token sweep or a session-cleanup task would activate all 1,254 lines. Treat that as a scope change, not an implementation detail.
- **The recipes are guides; the standard is the clause list.** 05's most consequential finding, and it governs how every remaining logging ticket reads its sources: normative force lives in `Structured_Logging_Application_Standard.md` (`MUST`, `[Enforced Constraint]`), not in the recipes. So a requirement with no recipe template is still mandatory, and a recipe's enthusiasm is not a mandate.

**Nuance, recorded so it isn't re-litigated.** `Questions.md:431` ("Roles cannot be created or modified through API endpoints") constrains *defining* roles, not *assigning* them. PRD Story 10 (admin changes a user's role) survives intact.

**Skills every session should consult.** `grilling` and `domain-modeling` by default. `policies` / `spec-compliance` when checking a decision against Appfw or IM8. The Appfw question set is a pre-written decision frontier — consult it rather than re-deriving questions.

**Branch.** `taniakoh`. Per [`README.md:33`](../../README.md) nothing may be committed to `main`.

## Decisions so far

<!-- one line per closed ticket: gist + link. Zoom the link for the detail. -->

- [01 — Context, topology and API surface](issues/01-context-topology-and-api-surface.md): Internal-enterprise, single-instance (JDBC session store regardless); base path `/api/v1` externalized as `api.base-path`; **JSON-only login** at `POST /api/v1/auth/login` via a custom filter subclassing `UsernamePasswordAuthenticationFilter`; admin endpoints are **resource-oriented** `/api/v1/users/**` guarded by role, so the PRD's `/api/admin/**` prefix is gone; an `/auth` segment holds login, logout and the endpoints the standard names no path for (register, password-reset); full endpoint inventory in the ticket.

- [03 — Role model reconciliation](issues/03-role-model-reconciliation.md): `ADMIN` becomes **`USER_MANAGER`** (a pure rename) and no third role exists; **one role per user**, a scalar `NOT NULL` `users.role`, with `role-hierarchy: "ROLE_USER_MANAGER > ROLE_USER"` supplying an admin's baseline user access; registration hard-codes `USER` while `POST /users` lets a manager choose; role definitions live in `application.yml` **and** a minimal read-only `roles` table keyed by name; guards key on role authorities with **no privilege layer**; `GET /api/v1/roles` is added to 01's inventory; every self-action on `/users/{userId}` is rejected `403` and the app must always retain at least one enabled `USER_MANAGER` (`409` otherwise).

- [05 — Which logging standards actually bind a two-process app](issues/05-logging-standards-applicability.md): The recipes are guides; `Structured_Logging_Application_Standard.md` is the clause list, so **a mandatory event with no recipe template is still mandatory** and the audit surface is ~2.5× the PRD's list. Ten `[Enforced Constraint]`s bind (`:319-330`) — including **Micrometer Tracing on the classpath** (`:321`), which settles this ticket's key question: `trace.id`/`span.id` are required on **every** line even with one service, but **no exporter, collector or trace platform is needed** (`Trace:14`), so the feared collision with the hosting exclusion does not arise and the SPA need send nothing. Also mandatory: an MDC filter clearing in `finally`, a global exception handler, boundary masking (three permitted mechanisms), and a **separate audit appender**. A typed audit module is **recommended, not mandatory**; "plaintext password never logged" is satisfied by **prevention, not masking**; retention TTL is the integrator's, not the app's. The batch recipe (1,254 lines) is **confirmed vacuous**. Three hard schema gaps land on 12: **no target/resource field exists**, `event.action`'s closed enum has **no** role-change or status-change value (invalidating one of 01's stated rationales, not its decision), and an unknown-username failure has **`trace.id` as its only permitted subject field**. Two decisions split off as 18 and 19; five tickets amended. Full cites in the ticket; detail in [`research/`](research/).

## Not yet specified

<!-- in-scope fog: real, but not yet sharp enough to ticket. Graduates as the frontier advances. -->

- **React screen and route inventory** — which screens exist, what each shows. Waits on the authorization matrix (08) and HTTP security (09) to know what the SPA can call.
- **SPA auth-context shape** — how the frontend holds session state and learns its own role. Waits on the self-read path/payload ruling in 10. 01 already fixed the bootstrap sequence: `GET /csrf` → `POST /auth/login` (200, empty body) → `GET /currentUser`; 03 fixed the role vocabulary it will hold (`USER` / `USER_MANAGER`, exactly one) and gave the role-change control a source in `GET /api/v1/roles`.
- **Architecture test rules** — `arch-tests-plan` rows enforcing the standard's §4 Separation of Concerns. Waits on module structure (15). 05 adds two candidate rows: audit logging routed through a single owner rather than scattered `LoggerFactory` calls, and no `System.out.println` / `e.printStackTrace()` anywhere (`Structured_Logging_Application_Standard.md:330`, an `[Enforced Constraint]` that a static rule enforces better than review).
- **Configuration, secrets and the HTTPS deployment note** — what is externalised, where secrets live, how the accepted local-HTTP gap is documented. Waits on 15.
- **Handoff definition of done** — whether `im8-review` and/or `pre-prod-check` must run clean before 17 closes. Waits on the test plan (14). 05 narrowed it: logging conformance needs **no** external log platform, collector or trace exporter (`Trace:14`, `Trace:10`, `Structured_Logging_Application_Standard.md:287` note), so it is fully assertable in-repo and cannot be deferred to an integrator.
- **Error contract shape** — the concrete response body, error codes and status mapping under the standard's §3.2. Waits on 08 (01 is resolved; it fixed the login endpoint's status codes but not the body shape). 05 adds two constraints it must satisfy: the binding set contradicts itself on the log level for Bean Validation failures (`Structured_Logging_Application_Standard.md:97` says `ERROR`, `Structured_Logging_Application_Standard_Questions.md:298` says `WARN`), and under ECS an `error_code` on a **non-exception** `ERROR` event is silently dropped (`Custom_Structured_Log_Encoder.md:408` against `Structured_Logging_Application_Standard.md:186`) — so the error contract and 19's encoder decision are coupled.

## Out of scope

<!-- ruled beyond the destination. Never graduates; returns only as a fresh effort. -->

- **JWT implementation** — PRD: design documented in its appendix only.
- **MFA / 2FA** — PRD exclusion; `App-Standards/Appfw-Mfa-Standards/` therefore does not apply.
- **Real SMTP / email delivery** — PRD: stubbed `EmailService` that logs.
- **Containerization, CI/CD, hosting infra** — PRD exclusion.
- **Local HTTPS setup** — PRD: documented deployment assumption; local dev over HTTP.
- **Fine-grained privileges model** — PRD excludes granular per-resource authz; the standard permits roles without privileges (`Questions.md:485`). **03 confirmed the consequence:** `url-guards` key directly on `ROLE_USER_MANAGER` / `ROLE_USER`, not on synthesized authorities like `USER_READ`.
- **Scheduled account-hygiene batch jobs** — `Standalone_Scheduled_Account_Hygiene_Jobs.md`; a whole subsystem serving no PRD story.
- **Remember-me** — `Questions.md:238`; no PRD story.
- **Admin-created-user activation workflow** — `Questions.md:535`; no PRD story (registration is self-service).
- **Automatic DB role synchronization and deleted-role backups** — `Common_Automatic_Database_Role_Synchronization_and_Deleted_Role_Backups.md`; same shape as the hygiene jobs. **Narrowed by 03:** what is out is the *archive-on-removal* (`DeletedRole`) machinery and the `sync-db-users` reset flag. A minimal read-only `roles` table seeded from config at startup **is in scope** — the standard makes persisting role definitions an enforced constraint (`Standalone_User_Access_Control_Application_Standard.md:407`, `:415`).
- **SSO login** — `Appfw-User-Standards/User_SSO/`; this app is Standalone.
- **Appfw File, Interface/Batch, Report and Mcc standards** — no applicable surface in this app shape.
