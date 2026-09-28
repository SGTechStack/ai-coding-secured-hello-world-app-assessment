# Backend agent notes

Spring Boot 4.1.0 (Spring Framework 7 / Spring Security 7) on Java 21, Maven. See root `ARCHITECTURE.md` for module boundaries and data flows, and root `AGENTS.md` for repo-wide conventions — both apply here.

## Build & test

- Build: `mvn clean verify` (compiles, runs tests). Package structure and dependencies live in `pom.xml` — read it rather than assuming versions.
- Single test class: `mvn test -Dtest=ClassName`.

## Conventions

- Package-per-feature under `com.assessment.securedhelloworld` (`auth`, `registration`, `passwordreset`, `admin`, `lifecycle`, `logging`, `config`, `bootstrap`, `user`, `web`). New features get their own package rather than growing an existing one.
- Authorization is centralized in `config/SecurityConfig.java`'s filter chain (URL-rule based); `@PreAuthorize` is used additionally on admin endpoints as defense in depth. Check both when changing access rules.
- Log through `logging/LogSanitizer` (or equivalent existing helper) for any user-controlled value — do not interpolate raw user input into `log.*` calls. Structured/ECS logging is configured in `application.yml`; keep new log statements consistent with it.
- Never log secrets, tokens, or reset links — see `passwordreset/LoggingEmailService.java` for the pattern to follow (log that an action happened, not the sensitive value itself).
- Test with `@SpringBootTest`/`MockMvc` integration tests under `src/test/java`, mirroring the main package layout. New endpoints and scheduled jobs need integration test coverage, not just unit tests.
