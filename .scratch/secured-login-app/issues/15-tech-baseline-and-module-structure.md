# 15 — Tech baseline and module structure

Type: grilling
Status: open
Blocked by: 01, 02
Map: [Secured Login App](../map.md)

## Question

What are the pinned technology versions, and how is the code organised?

Two halves, both of which the delivery pipeline needs before it can generate anything.

**Baseline.** Java version, Spring Boot version, build tool (Maven versus Gradle), and the Spring modules in play (Web, Security, Data JPA, Session, Validation, Actuator). On the frontend: React version, build tooling (Vite versus CRA versus Next), TypeScript or not, router, and HTTP client. Check whether `App-Standards/` pins any of these — if the standards or their recipes assume particular versions, those pins are binding, and finding that out is part of this ticket rather than a guess.

**Structure.** The standard's §4 Architectural Design and its Separation of Concerns section (`Standalone_User_Access_Control_Application_Standard.md:393,411`) prescribes how authentication, persistence and web layers are divided. Produce the concrete package layout for this application, and the frontend's directory layout. Decide whether it is one repository with two top-level directories, and where configuration lives.

**Also settle here**, since both depend on the layout:

- Configuration and secrets handling: what is externalised, how `app.admin.password` (Story 12) is supplied without landing in version control, and the documented HTTPS deployment assumption the PRD requires (`prd/assessment-prd.md:120`). This graduates the map's config/secrets fog.
- Whether architecture tests enforce the layering — `arch-tests-plan` exists in this repo's skills and its rows need real package names, so this ticket is what unblocks that fog patch.

Blocked on 01 (API surface shapes the web layer) and 02 (persistence choice shapes the data layer and the dependency set).
