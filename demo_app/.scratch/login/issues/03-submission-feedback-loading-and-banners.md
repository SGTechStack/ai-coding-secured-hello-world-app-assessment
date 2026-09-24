# 03: Submission feedback — loading state and failure banners

**What to build:** Once a validly formatted login is submitted, the form shows a deliberate loading state for at least 400ms, then reports the outcome. Wrong credentials show a generic, non-leaking banner; a server error or network outage shows a distinct "can't connect" banner. In both cases the form is re-enabled, and typing in either field dismisses the banner. See spec §Login form state machine and §API contract.

**Blocked by:** 01

**Status:** done

**Spec scenarios:** Story 1 · Scenarios 5, 6, 7, 8

- [x] While the request is in flight: username input, password input, and submit button are disabled; button text is "Logging in..." with a CSS-animated ellipsis
- [x] The disabled/loading state lasts at least 400ms from submit, even if the response arrives sooner (fake-timer test)
- [x] Backend returns `401 {"message":"Invalid username or password"}` for both unknown username and wrong password — identical status and body (MockMvc tests for both cases)
- [x] On `401`, a banner above the form (`role="alert"`) reads "Invalid username or password"; nothing in the UI indicates which field was wrong
- [x] On `5xx` or network failure, the banner reads "Unable to connect to the server. Please try again later."
- [x] After either failure, inputs and button are re-enabled and the button reads "Log in"
- [x] Typing in either field dismisses any banner immediately
- [x] All gates from ticket 01 still pass

## Comments

- Implementer (03): the banner renders inside the `<form>`, between the "Log in" heading and the fields, rather than before the form element. It still sits above every input, and it keeps the heading at the top of the page. Failed logins are rendered by `auth/LoginFailureHandler` (a `@RestControllerAdvice` scoped to `AuthController`), so the security entry point still returns `401 {"message":"Unauthorized"}` for other unauthenticated API calls. The banner text is fixed in the UI and keyed on `ApiError.kind`, not taken from the server's `message`.
- Fake-timer testing notes (for later tickets): RTL's async wrapper hangs under Vitest fake timers, so tests type with real timers, then fake only `setTimeout`/`clearTimeout` and submit with `fireEvent`. React Query's `notifyManager` scheduler is switched to `queueMicrotask` for those tests because sinon turns a mid-tick `setTimeout(0)` into 1ms. See `LoginPage.test.tsx` › "/login submission feedback".
- Code review (fix/code-review): the heading and banner now render outside and before the `<form>` element, as the spec says. Failed logins are rendered by the global `web/ApiExceptionHandler`, and the client's error kinds are now `unauthorized` / `rejected` / `unavailable`; a non-401 4xx on login is retried once with a fresh CSRF token before the "Unable to connect..." banner shows.
- Deliberate accessibility choice: under `prefers-reduced-motion: reduce` the "Logging in..." ellipsis stops animating (the dots stay visible, the button text and disabled state are unchanged). The spec asks for a CSS-animated ellipsis; honouring the user's reduced-motion setting takes precedence, and the loading state is still conveyed by the text and the disabled controls.
