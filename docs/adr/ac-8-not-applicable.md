# IM8 ac-8: Automated Account Lifecycle Management — N/A Determination

**Control:** ac-8 requires internal user accounts to be provisioned and
deprovisioned via an automated mechanism (SCIM/equivalent push
provisioning, or SSO JIT provisioning). External/public user accounts are
explicitly out of scope for this control.

**Determination: N/A**

**Justification:**

This application has no internal user account population that the
control's automation requirement applies to:

- The account model (see `PRODUCT.md`) is **open self-service
  registration** — any visitor can create an account via
  `POST /api/register` with a unique username/email. This is the
  "external/public user accounts" category the control explicitly
  excludes, not an internal-staff directory.
- There is no external IdP, HR system, or directory service feeding
  account lifecycle events into this application — accounts are created
  directly by end users through the registration endpoint, or (for the
  single ADMIN account) seeded once at first boot by
  `AdminBootstrapRunner`.
- The "internal" framing that does appear in `PRODUCT.md` (e.g. "internal
  security-tooling / admin-console-style surface") describes the
  application's **visual design language** — it is styled like an
  internal admin console — not its user population. The actual users
  (including the admin) are managed the same way as any self-registered
  account, not provisioned/deprovisioned by an external system.

**What would change this determination:** if this application is later
integrated behind an organisational SSO/IdP for admin accounts
specifically (e.g. requiring staff to authenticate via a corporate
identity provider rather than local username/password), ac-8's JIT
provisioning pattern would become applicable to that admin population at
that time, and should be revisited.

**Related, already-implemented controls (do not confuse with ac-8):**

- **ac-3** (inactivity-based disabling) — implemented via
  `DormantAccountDisablingJob`.
- **ac-4** (periodic access review against a declared baseline) —
  implemented via `AccessReviewJob`.
- **ac-6** (forced change of default/temporary credentials) — implemented
  via `ForcePasswordChangeFilter` / `PasswordChangeService`.

These three controls together already cover the account lifecycle risks
that are actually present in this application's architecture (self-
registered accounts with local credentials); ac-8 covers a different,
inapplicable risk (unmanaged internal/employee account sprawl via a
directory service this application does not have).
