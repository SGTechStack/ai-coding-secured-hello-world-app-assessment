# demo-backend

Spring Boot 4, Java 21. Consult the `spring-boot` skill before writing or editing Java.

## Package structure

Modular monolith, feature-first. Top-level packages under `org.eds.demo` are business domains (`user`, `account`, `billing`, …), never technical layers (`controller`, `service`, `repository`, `entity`).

Each domain has:

- `api`: controllers, request/response DTOs, web mapping
- `application`: use cases, application services
- `domain`: domain objects, value objects, domain services, repository interfaces
- `infrastructure`: adapters (JPA, HTTP clients, messaging, cloud)

Dependencies point inward: `api → application → domain`, `infrastructure → domain`. `domain` stays free of Spring MVC, JPA annotations, security objects, and API DTOs unless a documented reason says otherwise (`user/domain` JPA entities: [ADR-DEMO-BE-0002](docs/adr/ADR-DEMO-BE-0002-jpa-entities-in-domain.md)). API records live in `api`, domain types in `domain`; map at the controller boundary, or in a small mapper once mapping is non-trivial.

## Maven versions

Every explicit version lives in `<properties>` with a stable, descriptive name (`spotless.version`, `jackson.version`) and is referenced as `${...}` from every `<version>` tag: build plugins, profile plugins, and plugin `<dependencies>`. `parent` and project coordinates are exempt.

- Omit `<version>` for dependencies and plugins managed by `spring-boot-starter-parent`.
- Spring Cloud comes from one BOM property, `spring-cloud.version`.
- Override a managed version only for a compatibility or security reason.
- Keep version bumps in their own commit, separate from functional changes.

## Javadoc

Class-level Javadoc goes on config classes that work around environment-specific behavior, profile wiring, or non-obvious auto-configuration. It states why the class exists and which profiles it affects: a short first sentence, plus a short `<p>` only when it prevents accidental removal.

## Verify

Before merging: `./mvnw -q spotless:apply`, `./mvnw -q validate`, `./mvnw -q test`. Full gate: `./mvnw verify`.
