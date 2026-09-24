# 19 — Resolve the MFA scope conflict raised by IM8 ac-2

Type: grilling
Status: open
Blocked by: —

## Question

IM8 mandates MFA for privileged access. The PRD declares MFA out of scope. Which gives, and what
exactly do we build?

This ticket exists because the map was wrong. "MFA" sat in Out of scope on the grounds that
`Appfw-Mfa-Standards/index.md` reads as a feature router rather than a blanket mandate. That reading
holds for the App Standard, but IM8 is an independent and unconditional source, and the map's own
entry made the exclusion conditional: *if IM8 forces MFA for privileged access, this returns.* It does.

## What the research found

From "Extract the IM8 and ARC controls that bind this app":

- `im8-reform-app-policy.md` → `## Access Control (ac)` → **ac-2** requires MFA for privileged
  account logins and privileged actions, including step-up authentication.
- `im8-review/SKILL.md` → `### ac-2` defines privileged accounts to include `ROLE_ADMIN` or any role
  that can manage users. Our ADMIN role exists for exactly that (PRD Stories 8–11).
- **ac-2 has no N/A branch.** Controls that can be ruled out — ac-6, ac-7, ac-8, as-12, ck-1/2/4,
  ga-8, lm-18, st-3, dp-8 — all carry an explicit not-applicable clause. ac-2's applicability offers
  only "privileged accounts only" versus "all accounts". The sole escape is having no privileged
  accounts, and the admin module is the PRD's own requirement.
- The PRD as written fails every ac-2 backend check: no MFA fields, no second-factor endpoint, no
  two-step login, no step-up gate on admin mutations.

A second finding compounds this, from "Verify the App Standard's controls are still current
practice": NIST SP 800-63B-4 §3.1.1.2 sets a **15-character minimum** for a password used as
single-factor authentication, permitting 8 only within a multi-factor process. Without MFA we are
single-factor, so the PRD's 12-character minimum is below the NIST floor. **Adding MFA and keeping a
shorter password are the same decision viewed from two directions**, which is why they should be
decided together rather than in separate tickets.

## Ruled out before the grill: email OTP via the stubbed `EmailService`

Asked and answered, so it does not get re-litigated mid-session. Reusing the PRD's stubbed
`EmailService` as an MFA delivery channel **is not viable**, for four independent reasons:

1. **NIST prohibits it.** [SP 800-63B-4 §3.1.3.1](https://pages.nist.gov/800-63-4/sp800-63b/authenticators/)
   states email shall not be used for out-of-band authentication — reachable with only a password,
   interceptable in transit and at intermediate mail servers, and vulnerable to DNS-spoofing reroutes.
   **Narrow carve-out that matters to us:** confirmation codes sent to *validate an email address*, or
   issued as recovery codes, are not authentication processes and are explicitly unaffected. So the
   registration email verification in ticket 10 is fine; the same mechanism as a login factor is not.
2. **The stub makes it self-defeating.** `EmailService` logs instead of sending, so the second factor
   lands in the application log. Anyone with log access authenticates as an admin.
3. **It would breach the logging contract.** App Standard §3.4 "What NOT to Log" names OTP values and
   authentication challenge responses explicitly.
4. **Email is already the account-recovery path**, so it cannot also be the second factor — one
   mailbox compromise yields both factors. Two factors sharing a single point of failure are one factor.

**Tension worth knowing:** `im8-review` → `### ac-2` lists "OTP via SMS or email" among acceptable
"something you have" factors, so email OTP would pass the automated check — but the same section's
manual review flag sets the real bar at "at least one 'something you have' factor — TOTP or hardware
token". The automated check is more permissive than the guidance behind it. Also note the
`Appfw-Mfa-Standards` router has **no standalone OTP path**: OTP appears only in the MCC variants,
delivered via the MCNS platform service, so it is not a sanctioned option for a standalone app.

**Also ruled out: the PIN factor.** `MFA_Core` offers a 6-digit bcrypt-hashed PIN and it looks cheap,
but a PIN is "something you know" — the same category as the password — so it is not a second factor
and fails ac-2's bar.

## What TOTP already gives us, prescribed rather than designed

`MFA_Core/Base_Standalone_Application_Standard.md` specifies TOTP as enforced constraints, so option 1
below is recipe-following, not fresh design: 20-byte random secret; encrypted at rest with a
configured key; `otpauth://totp/...` QR provisioning; a `PENDING_TOTP` record promoted to
`TOTP_USER_DETAILS` only after a confirmation code succeeds, inside `@Transactional`; verification
across three time windows (`counter-1`, `counter`, `counter+1`) with constant-time comparison; and
**`lastUsedCounter` replay prevention** rejecting any matched counter ≤ the stored value, so an
intercepted code cannot be replayed inside the 90-second skew window. Factor value arrives in an
`X-TOTP` header. RFC 6238 conformance (HMAC-SHA1) is mandatory — deviation breaks standard
authenticator apps.

Nothing is transmitted to the user, which is precisely why TOTP avoids every weakness that rules out
email.

## Recommendation going into the grill

**TOTP step-up on admin mutations only, plus a 15-character password floor for everyone.** Rationale:
closes ac-2 at its narrowest scope; satisfies the NIST single-factor floor; leaves the PRD's login flow
and Stories 1–5 untouched; follows a prescriptive recipe.

On the password floor specifically — take 15 even with MFA, because step-up does not make *login*
multi-factor (the second factor is demanded later, at the action, so the login boundary is still
single-factor), and regular USER accounts have no MFA at all under this option so 15 binds for them
regardless. Two minimums by role is complexity that buys nothing. NIST also requires permitting ≥64
characters, paste, and password managers, so 15 is not a usability problem. Note the resulting band is
15 characters to 72 *bytes*, which narrows for non-ASCII input.

## Options

1. **TOTP step-up on `/api/admin/**` mutations only** — not at login. `im8-review` accepts
   step-up-at-action for ac-2. Scope: one encrypted `totpSecret` column (ac-2 requires encryption at
   rest), an enrol endpoint, a verify endpoint, one guard. Smallest change that closes ac-2.
2. **TOTP at login for ADMIN accounts only** — two-step login with a partial-authentication state
   that cannot reach protected resources.
3. **TOTP at login for all accounts** — also drops the password floor from 15 to 8 and satisfies the
   strongest reading, at the highest cost.
4. **Accept the FAIL and register it as a deferral.** Defensible for a reference app, but it will
   surface as a top-band ac-2 FAIL in every future assessment, and `spec-compliance` Step 4 requires
   explicit user sign-off on FAILs before decomposition.

## What to decide

- Which option, and the consequence for the password minimum (15 if single-factor, 8 permitted if MFA).
- If MFA is built: which `Appfw-Mfa-Standards` path applies. The index routes "MFA TOTP Standalone on
  Login" through `MFA_Core` then `MFA_Frontend/Standalone`, and "MFA PIN/TOTP Standalone on Critical
  Transaction" through `MFA_Core` then `MFA_Critical_Transaction` then `MFA_Frontend/Standalone` —
  option 1 above maps to the critical-transaction path, not the login path. A follow-up research
  ticket to extract those recipes will be needed, which is real added scope.
- Where the TOTP secret's encryption key lives, given secrets management for non-local environments
  is still unspecified on the map.
- Whether this pushes MFA out of Out of scope entirely, or leaves a narrowed version there.

## Done when

An option is chosen, the password-minimum consequence is settled with it, and either MFA is moved out
of the map's Out of scope section with its follow-up tickets created, or the deferral is written with
the user's explicit sign-off recorded.
