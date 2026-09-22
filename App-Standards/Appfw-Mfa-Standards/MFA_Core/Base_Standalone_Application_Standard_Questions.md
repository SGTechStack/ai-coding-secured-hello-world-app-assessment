# Base Standalone Application Standard — Implementation Questions

Questions to resolve with the team or product owner before starting implementation.

---

### Deployment profile

1. What is the deployment profile for this service?
   - **Standalone (Base Standard only)**: PIN and/or TOTP factors, local encryption key, no cloud messaging dependency.
   - **Cloud / MCC**: Adds OTP factor delivered via MCNS, AWS KMS for TOTP secret encryption. Requires MCNS onboarding and a KMS key ARN.
   - **Critical Transaction (AOP enforcement)**: Adds a method-level AOP interceptor to gate specific operations behind a second factor. Can be combined with Standalone or MCC.

   > Identifying the profile early determines which extending standards apply and which additional questions below are relevant.

2. What is the primary purpose of MFA in this service?
   - **Login / session upgrade**: MFA is performed as part of or immediately after primary authentication to establish a higher-assurance session.
   - **Critical transaction guard**: MFA is enforced per-operation on sensitive methods (e.g., fund transfers, account changes) via the AOP enforcer.
   - **Phone/contact verification**: OTP is used to prove possession of a phone number or email address before storing it — the delivery target is not yet in the database at the time of the OTP request.
   - **Multiple purposes**: Describe each use case — different purposes may require different factor types or enforcement patterns.

---

### Factor selection

3. Which factors does this service need to support — PIN, TOTP, OTP, or a combination?

   > **Recommendation**: TOTP is the strongest factor available in this standard — it is time-bound, requires no messaging infrastructure, and is compatible with standard authenticator apps (Google Authenticator, Microsoft Authenticator). PIN is simpler but is a static secret and weaker against replay if intercepted. OTP (via MCNS) proves possession of a phone/email but adds a dependency on MCNS and introduces delivery latency; it is well-suited for contact verification flows or as a fallback when the user cannot use an authenticator app. If the deployment profile is Standalone, OTP is not available. Use TOTP as the default unless there is a specific reason to prefer PIN or OTP.

4. Must a user enroll in all factors before MFA registration is considered complete, or is either factor sufficient on its own?
5. If multiple factors are registered, must all pass on every request, or is satisfying any one factor sufficient? Does the factor type chosen vary based on the request?

---

### User identity

6. Which field identifies a user within the MFA store — SSO ID, employee number, email address, or another identifier? This value is used as the lookup key for all factor records. *(See Base Standard §1 design choice: User Identity)*

---

### Registration and enrollment

7. When is MFA enrollment triggered — self-service during the user's first authenticated session, or admin-initiated before first login? *(See Base Standard §2 design choice: Registration timing)*
8. If self-service: how is the user directed to complete enrollment — a setup-required prompt, a redirect, or a separate onboarding flow?
9. If admin-initiated: who provisions the initial credential, and does the user reset it on first use?
10. Who is permitted to remove a user's PIN or TOTP key — any admin, a specific role, or only a designated security administrator? This determines the required role on the PIN/TOTP removal endpoints.

---

### TOTP configuration *(if TOTP is used)*

11. What issuer name should appear in the QR code and authenticator app — the application name, the organisation name, or a branded label?
12. Are the default TOTP period (30 s) and digit count (6) acceptable, or does the deployment have specific requirements? The digit count cannot be changed without breaking compatibility with standard authenticator apps.

---

### Encryption key management

13. How is the TOTP encryption key provisioned and stored — in the application database, in a secrets manager (e.g., Vault), or via another mechanism? *(Standalone profile only — Cloud profile uses AWS KMS)*
14. What is the key rotation schedule? The standard mandates at minimum yearly rotation. Is there an existing process for re-encrypting TOTP secrets under a new key, or does one need to be designed?

---

### Account lockout and rate limiting

15. Is the 10-failure account lockout threshold appropriate for this service's user base and risk profile, or does it need to be adjusted?
16. What happens to a locked account — does it self-unlock after a fixed period, or does it require administrator action?
17. What backoff strategy should be applied between failed verification attempts — exponential (e.g., 1 s → 2 s → 4 s, capped at 60 s), linear, or flat delay? *(See Base Standard §3.4 design choice: Exponential Backoff)*
18. Is global IP-level rate limiting required? If so, will it be enforced at the API gateway or the application layer, and what is the acceptable threshold? *(See Base Standard §3.4 design choice: Global / IP-Level Throttling)*

---

### Operational

19. What is the expected MFA verification volume — requests per minute and peak load?
20. Is there a latency SLA for factor verification?
