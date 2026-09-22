# 02: Registration

**What to build:** A visitor can register an account from the React app with a username, email, and password. The backend validates uniqueness and password strength, stores the password as a BCrypt hash, and creates the account with role `USER` and `enabled = true`. Invalid or duplicate submissions are rejected with clear validation errors and no account is created.

**Blocked by:** 01 (needs both origins wired and persistence available)

**Status:** done

- [x] Visitor submits a unique username, unique email, and a password meeting the minimum strength policy (length ≥ 12) → account created with role `USER`, `enabled = true`, password stored as a BCrypt hash.
- [x] Visitor submits a username or email already registered → request rejected with a clear validation error (username/email conflict), no account created.
- [x] Visitor submits a password failing the strength policy → request rejected with a validation error, no account created.
- [x] Plaintext password is never logged or stored, on any registration attempt (success or failure).
- [x] React app has a working registration form wired to the endpoint, showing success/error states.

## Implementation notes

- Backend: `com.sgtechstack.helloworldauthapp.user` (`User` entity, `Role` enum, `UserRepository`) and `com.sgtechstack.helloworldauthapp.auth` (`RegistrationRequest`/`RegistrationResponse` DTOs, `PasswordPolicy` (min length 12), `RegistrationService`, `AuthController` at `POST /api/auth/register`, `AuthExceptionHandler` for uniform `{message, details}` error responses: 400 for validation/weak-password, 409 for username/email conflicts).
- Added `spring-boot-starter-security` (for `BCryptPasswordEncoder`) and `spring-boot-starter-validation` (for bean validation) to `backend/pom.xml`.
- New `SecurityConfig`: CSRF left disabled for now (no session-cookie auth exists yet, so there's no ambient credential for CSRF to protect); stateless session policy; `/api/auth/register` and `/api/health` are `permitAll()`, everything else requires authentication by default (least privilege) — ticket 03 will build on this rather than replace it.
- Note: since no custom `UserDetailsService` bean exists yet, Spring Boot auto-configures a default in-memory user and logs a generated password at startup. Harmless right now since nothing authenticates against it, but ticket 03 (login) will supersede this.
- Verified no log statement anywhere touches the raw password (grepped `main/`), and confirmed via test that the stored value is a BCrypt hash, not the plaintext.
- Frontend: `RegistrationForm.tsx` (username/email/password fields, client-side `minLength`/`type=email` hints plus server-side validation as the source of truth), `api/client.ts` gained `register()` and an `ApiError` class that carries the backend's `message` + `details[]`.
- Backend tests: `AuthControllerRegistrationTest` — success (201, BCrypt hash verified via `PasswordEncoder.matches`), duplicate username (409, no account created), duplicate email (409), weak password (400, no account created), blank/invalid field validation (400). `mvn clean verify`: 6/6 tests pass, BUILD SUCCESS.
- Verified end to end in a real browser (chrome-devtools MCP): registered a new account (201, success message rendered, form cleared), then re-submitted the same username (409, error alert rendered with "Username is already taken"). No CORS errors.
