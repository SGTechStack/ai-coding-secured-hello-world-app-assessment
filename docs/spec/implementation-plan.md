# Implementation plan

This is the order the `junwen` branch was built. The contract is [secured-hello-world-auth.md](./secured-hello-world-auth.md). Each slice was finished before the next one started. The security tests live in `backend/src/test/java/hello/desk/SecurityRequirementTests.java` and were run with the API.

| Slice | What landed | Done when |
| --- | --- | --- |
| 1 | Maven module, Spring Boot 3.4, Java 21, H2, JPA, security filter, CORS, CSRF cookie, session cookie serializer | App starts and an anonymous `GET /api/hello` is 401 |
| 2 | `UserAccount`, `PasswordResetToken`, repositories, `PublicUser` | Schema matches the PRD tables and the API type never includes `passwordHash` |
| 3 | Register and the length policy, BCrypt, conflict errors | Short password creates no row; duplicates return 409; stored value starts with `$2a$` |
| 4 | Login, logout, `/api/auth/me`, `/api/hello`, session id change on login, dummy BCrypt for unknown users | Success and failure messages match the spec; old cookie fails after logout |
| 5 | `IpThrottle` and account lockout in `AuthService` | Five failures lock the row; a later success clears it; the 21st hit from one IP is 429 and does not lock an untouched account |
| 6 | Reset request and confirm, `TokenHasher`, `LoggingEmailService`, `SessionService.invalidateUsername` | Token in the log is not the stored hash; second use and expiry fail; the pre-reset cookie is 401 |
| 7 | Admin list, enable, role, delete, self guard, `AdminSeeder` | User gets 403; admin self actions get 409; disable blocks the next login; restart does not insert a second admin |
| 8 | Vite React app: register, login, forgot, reset, greeting, directory | Browser run against the local API, including a reset link copied from the API log |

ADRs 0001–0003 were written with the slices that introduced Spring Session JDBC, SHA-256 reset tokens, and the length-only password rule.

Not in this plan: a shared store for the IP counter, character-class password rules, RFC 9457 problem responses, and a non-dev fail-fast on the sample admin password. Those would be new ADRs, not silent edits to the slices above.
