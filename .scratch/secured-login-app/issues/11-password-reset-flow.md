# 11 — Password reset flow

Type: grilling
Status: open
Blocked by: 04
Map: [Secured Login App](../map.md)

## Question

What is the password reset workflow and token delivery strategy?

Answer `Q14` (password reset workflow and token delivery, `Questions.md:335`) against [`Standalone_Privileged_User_Administration_and_Password_Reset.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Privileged_User_Administration_and_Password_Reset.md).

The PRD's Stories 6 and 7 fix the required behaviour; this ticket settles the mechanism:

- Token generation: source of randomness and length; the **hash** is stored, never the plaintext (`password_reset_tokens.token_hash`) — decide the hash algorithm, and note it is a lookup key, so a salted-per-row BCrypt hash would make lookup impossible. This is a real design constraint, not a detail.
- Expiry (PRD allows 15–30 min) and single-use enforcement via `used_at`.
- Whether an outstanding token is invalidated when a new one is requested, and whether requests are rate-limited (unthrottled, this endpoint is an email-enumeration and mail-flood vector even with a generic response).
- Enumeration resistance: the response is a generic success **regardless** of whether the email is registered (Story 6), which constrains timing as well as body — decide whether timing equalisation is in scope.
- The stubbed `EmailService.sendPasswordResetEmail(...)` that logs the link instead of sending: what it logs, and how that interacts with the logging standard's masking rules — a reset link in a log line is a live credential, so 12 must cover it.
- On confirm: password updated, token marked used, and **all existing sessions for that user invalidated** (Story 7) — this is the criterion that depends on 02's session-store choice supporting find-by-principal.
- Whether the new password is checked against history, per 04's ruling.

Blocked on 04 (password policy and history apply to the new password).

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** Two path facts and one scoping item from 01's endpoint inventory:

- The PRD's self-service reset endpoints have **no standard path** (the standard's reset is admin-initiated), so 01 placed them under the `/auth` provenance rule: `POST /api/v1/auth/password-reset/request` and `POST /api/v1/auth/password-reset/confirm`.
- **`PATCH /api/v1/users/{userId}/resetPassword`** (`Standalone_Privileged_User_Administration_and_Password_Reset.md:391`) is the standard's *admin-initiated* reset. It serves no PRD story and 01 assigned ownership of it here: rule it in or out, and if out, it belongs on the map's **Out of scope**, not **Decisions so far**.
- Whether a successful reset sets `requirePasswordChange` is ticket 04's to rule on (see its amendment); coordinate rather than duplicate.
