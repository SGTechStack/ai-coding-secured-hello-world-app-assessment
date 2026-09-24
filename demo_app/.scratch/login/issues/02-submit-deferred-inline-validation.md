# 02: Submit-deferred inline validation

**What to build:** When the user clicks "Log in" with a blank username and/or password, inline errors appear beneath the empty fields and nothing is sent to the server. Errors never appear while typing before the first submit, and a field's inline error disappears the moment the user types in that field. The backend also rejects blank fields with `400` as defence in depth. See spec §Login form state machine.

**Blocked by:** 01

**Status:** done

**Spec scenarios:** Story 1 · Scenarios 2, 3, 4

- [x] Blank username on submit → "Username is required" beneath the username field
- [x] Blank password on submit → "Password is required" beneath the password field
- [x] Both blank → both messages shown
- [x] No request to `/api/v1/auth/login` is sent when either field is blank (asserted at the MSW boundary)
- [x] Typing (including invalid characters) before the first submit shows no inline errors and no banner
- [x] Typing any character into a field whose inline error is showing dismisses that error immediately (username and password symmetric); the other field's error is untouched
- [x] Inline errors are linked via `aria-describedby` and the input gets `aria-invalid="true"`
- [x] `POST /api/v1/auth/login` with a blank/missing field returns `400 {"message": ...}` (MockMvc test)
- [x] All gates from ticket 01 still pass

## Comments

- Implementer (02): whitespace-only input counts as blank on both sides (frontend trims for the check but sends the raw value; backend uses `@NotBlank`), so the UI never sends a request the API would reject. Backend `400` body is a single generic `{"message": "Username and password are required"}` for any blank/missing field (added `spring-boot-starter-validation`; handler `LoginRequestValidationHandler` is scoped to `AuthController`). Frontend tests live in `LoginPage.validation.test.tsx` (separate file to keep merges with ticket 03 clean). Inline errors are plain `<p>` elements, not `role="alert"`, so the banner stays the only alert.
