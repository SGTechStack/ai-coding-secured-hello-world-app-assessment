# Cloud MFA (MCC) Application Standard — Implementation Questions

Questions to resolve with the team before starting implementation. These questions are specific to the Cloud / MCC deployment profile (OTP factor via MCNS and AWS KMS for TOTP secret encryption). Resolve the base factor and enrollment questions in the [Base Standalone Application Standard Questions](../MFA_Core/Base_Standalone_Application_Standard_Questions.md) first.

---

### OTP factor

1. How is the OTP delivery target resolved at generation time — looked up from the user database, or supplied as a request parameter by the caller? *(See Cloud Standard §4.4 design choice: OTP delivery target resolution)*
   > Choose the parameter option when the delivery target itself is being verified before being stored (e.g., a phone number verification flow where the number is not yet persisted). Choose the database option for established users with a stored contact.

2. What OTP digit count and TTL are required? The recommended TTL is 10 minutes to accommodate MCNS delivery latency — SMS and email can take several minutes. A TTL shorter than expected delivery latency will cause OTPs to expire before they arrive.

3. Should OTP verification be required before a user can set up a PIN or provision a TOTP key — i.e., use OTP as a setup guard? *(See Cloud Standard §4.4 design choice: OTP as setup guard)*
   > Use this when proving phone/email possession before credential setup is a business requirement. If not used, PIN/TOTP setup is protected only by session authentication.

4. When a submitted OTP is absent or the stored OTP has expired, should the service auto-generate a fresh OTP as a side effect, or return a TTL-expired error and let the frontend prompt the user to request a new one explicitly? *(See Cloud Standard §2.4 design choice: On absent or expired OTP)*
   > Auto-generation is convenient but can confuse users if a new code arrives while the original is still in transit. The explicit-error option gives the user more control and is generally preferred when MCNS delivery latency is noticeable.

5. Should OTP regeneration be unrestricted, or should a cooldown period be enforced between requests? *(See Cloud Standard §3.4 design choice: OTP regeneration throttling)*
   > A cooldown slightly longer than typical MCNS delivery latency (e.g., 90 s if MCNS typically takes 60 s) reduces impatient re-requests without blocking users who genuinely need a new code.

---

### MCNS onboarding and delivery

6. Has the service been onboarded onto MCNS with an approved message template for OTP delivery? MCNS will reject requests from services without an active approved template.
7. Has the MCNS sender identity (SMS originator or email sender) been registered with the relevant telecommunications or email registry? Unregistered senders appear as "Likely SCAM" on recipient devices.
8. What is the expected MCNS delivery channel — SMS, email, or both? If both, is the channel selected per user or per request?
9. What MCNS retry behaviour is required on transient delivery failure — rely on the MCNS library's built-in retry policy, or implement application-level retry? *(See Cloud Standard §4.3 design choice: MCNS retry strategy)*

---

### AWS KMS

10. Should the KMS key be an AWS Managed Key (auto-rotated annually, no additional configuration) or a Customer Managed Key (implementor controls rotation schedule and key policy)? *(See Cloud Standard §4.4 design choice: KMS key type)*
    > AWS Managed Keys are recommended unless compliance requirements mandate custom key policy, audit controls, or a specific rotation schedule.
11. Which AWS region will the KMS client target?
12. Is there an existing KMS key already provisioned for this service, or does one need to be created? If it is already provisioned, what is the KMS key ARN?
13. What is the key rotation schedule for Customer Managed Keys? The standard mandates at minimum yearly rotation for the encryption key — is there an existing process for re-encrypting TOTP secrets under a new key, or does one need to be designed?

---

### Operational

14. Who is the on-call owner for OTP delivery failures and KMS access errors in production?
15. Is there a health check or monitoring alert configured for KMS reachability and MCNS connectivity? These are the two external dependencies that, if unavailable, prevent all OTP and TOTP operations respectively.
