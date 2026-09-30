# Error contract

<!-- Generated from sg.securedhello.error.ErrorCode by ErrorContractDriftIT. Do not edit by hand: change the enum and run `mvn -f backend/pom.xml verify -Derror-contract.regenerate=true`. -->

Every error the API returns is an RFC 9457 `application/problem+json` body with a `code` extension, written only by `ProblemDetailWriter` (ADR-031). The machine-readable form of this contract is [`error-contract.schema.json`](error-contract.schema.json).

## Envelope

| Member | Type | Meaning |
|---|---|---|
| `type` | string | Derived from `code` for RFC 9457 conformance. Clients never read it. |
| `title` | string | Constant per code. |
| `status` | integer | The HTTP status; each code has exactly one. |
| `detail` | string | Constant per code; never an exception message. |
| `instance` | string | The request path that failed. |
| `traceId` | string | 32 lowercase hex digits identifying the request. |
| `code` | string | One value from the closed enum below. The only member a client branches on. |

No other member is allowed until the contract declares it, as an extension member below.

## Codes

| `code` | Status | Title | Detail | Used for |
|---|---|---|---|---|
| `AUTHENTICATION_FAILED` | 401 | Authentication failed | Authentication is required, or the credentials were not accepted. | every password-axis sign-in failure, an unauthenticated request, and absolute expiry |
| `PASSWORD_CHANGE_REQUIRED` | 403 | Password change required | The password must be changed before this request can be made. | a forced-change credential used outside the allowlist |
| `CSRF_TOKEN_INVALID` | 403 | CSRF token invalid | The CSRF token is missing, invalid or no longer current. | a missing, wrong or superseded CSRF token, including logout on a dead session |
| `ACCESS_DENIED` | 403 | Access denied | The request is not permitted. | insufficient role, an unmatched route for a signed-in caller, and a self-action refusal |
| `VALIDATION_FAILED` | 400 | Validation failed | The request was not valid. | format and length rejections, the body size cap, and rule USERNAME_UNAVAILABLE |
| `PASSWORD_REJECTED` | 400 | Password rejected | The password does not meet the password policy. | password policy, with a rule member |
| `RESET_TOKEN_INVALID` | 400 | Token invalid | The token is invalid or has expired. | any failed redemption of an activation or reset token |
| `USER_EXISTS` | 400 | User exists | A user with that username or email address already exists. | admin-initiated creation only, including tombstone hits |
| `TOO_MANY_REQUESTS` | 429 | Too many requests | Too many requests. Try again later. | every throttle and a tier-1 factor lock; always carries an integer Retry-After |
| `MISSING_FACTOR` | 412 | Second factor required | A current second-factor verification is required. | second factor absent or expired |
| `INVALID_FACTOR` | 412 | Second factor invalid | The second-factor code was not accepted. | wrong second-factor code |
| `FACTOR_ENROLMENT_REQUIRED` | 422 | Second factor enrolment required | A second factor must be enrolled before this request can be made. | an unenrolled admin on the admin surface |
| `FACTOR_ALREADY_ENROLLED` | 409 | Second factor already enrolled | A second factor is already enrolled. | provisioning when a confirmed factor exists |
| `FACTOR_DISABLED` | 423 | Second factor disabled | The second factor is disabled. Contact an administrator. | tier-2 disable, on the self-read, the admin entry point and verification |
| `TWO_ADMIN_INVARIANT` | 409 | Two-admin minimum | The change would leave fewer than two enrolled administrators. | the two-admin invariant's refusal of a disable, demote or delete |
| `SERVICE_BUSY` | 503 | Service busy | The service is busy. Try again shortly. | a guarded admin change whose locks other changes held past the lock timeout; always carries an integer Retry-After |
| `INTERNAL_ERROR` | 500 | Internal error | An unexpected error occurred. | anything unhandled |

## Extension members

Each is allowed only on the codes listed for it, with that code's values, and absent from every other.

| Member | Code | Required | Values | Meaning |
|---|---|---|---|---|
| `rule` | `VALIDATION_FAILED` | no | `USERNAME_UNAVAILABLE` | Present only when the failure is a property of the submitted value the caller can act on: a taken username at registration (ADR-032). A format or length rejection carries none. |
| `rule` | `PASSWORD_REJECTED` | yes | `MIN_LENGTH`, `MAX_BYTES`, `BLOCKLISTED`, `CONTEXT_TERM`, `TOO_WEAK`, `HISTORY_REUSE` | The first password-policy rule the password failed, in the order the rules run (ADR-005). |
| `factor` | `TOO_MANY_REQUESTS` | no | `TOTP` | Present only on a tier-1 factor lock, the factor that is locked; a source or identifier throttle never carries it (ADR-033). |
| `reason` | `TOO_MANY_REQUESTS` | no | `LOCKED` | `LOCKED`, only with `factor` (ADR-027). |
| `factor` | `MISSING_FACTOR` | yes | `TOTP` | The factor the admin surface requires (R-MFA-001). |
| `reason` | `MISSING_FACTOR` | yes | `MISSING`, `EXPIRED` | `MISSING` when the session does not hold the factor; `EXPIRED` when it holds one older than the rule accepts (ADR-021). |

## Rules

- Clients branch on `code` and on nothing else (REJ-092).
- `type` is `tag:securedhello.sg,2026:problem:` followed by the code in lower case, with hyphens for underscores.
- A status the producer knows without a code maps as: 400, 406, 413 and 415 to `VALIDATION_FAILED`; 401 to `AUTHENTICATION_FAILED`; 403, 404 and 405 to `ACCESS_DENIED`; 429 to `TOO_MANY_REQUESTS`; anything else to `INTERNAL_ERROR`.
- 401 responses carry no `WWW-Authenticate` challenge (R-AUTH-005).
- `/actuator/**` is exempt: its health body is Actuator's own format (REJ-066).
