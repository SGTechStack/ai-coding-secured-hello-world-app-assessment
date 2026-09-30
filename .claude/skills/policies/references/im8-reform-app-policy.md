# IM8 Reform Control Catalog — Application Code Review

## Summary of Controls

| Section | Count |
| :--- | :---: |
| Application Security (as) | 15 |
| Access Control (ac) | 8 |
| Data Protection (dp) | 2 |
| Logging and Monitoring (lm) | 5 |
| Cryptography (ck) | 3 |
| Generative AI (ga) | 1 |
| Project Management (pm) | 1 |
| Security Testing (st) | 1 |
| **Total** | **36** |

## Application Security (as)

- **as-1: Input Validation**: Validate all application inputs to ensure that they match the expected type, structure, or format.
- **as-2: Parameterised Interfaces**: Use parameterised interfaces for database queries or system commands.
- **as-3: Output Sanitisation**: Sanitise all application outputs that will be used to render a HTML document.
- **as-4: Authentication Mechanism Rate-Limiting**: Apply rate-limiting on all authentication mechanisms to deter brute-force attacks.
- **as-5: Password Requirements**: Verify password length and complexity where SSO/passwordless is not supported.
- **as-6: Password Salting and Hashing**: Store passwords as salted hashes using resistant schemes (e.g., Argon2, scrypt).
- **as-7: Access Control Check Enforcement**: Perform access control checks on all authenticated requests.
- **as-8: Secrets Management**: Securely store secrets in appropriate solutions (e.g., AWS Secrets Manager, HashiCorp Vault).
- **as-9: Content Security Policy (CSP)**: Set minimally permissive CSP response headers.
- **as-10: HTTP Strict Transport Security (HSTS)**: Set HSTS response headers with at least 1 year max-age.
- **as-11: Session Management**: Require re-authentication or termination after session exceeds defined hours.
- **as-12: Malware Scanning of Uploaded Files**: Scan file uploads for malware before processing.
- **as-13: Exposure of Internal System Details**: Prevent disclosure of internal details (debug info, stack traces) to end users.
- **as-14: Secure Cryptographic Libraries**: Use reputable and secure cryptographic libraries (e.g., OpenSSL).
- **as-15: Password Change**: Enforce password change upon suspected account compromise.

## Access Control (ac)

- **ac-1: Principle of Least Privilege**: Enforce URL-role mapping with default-deny, method-level security annotations, and ownership/tenancy checks on data access.
- **ac-2: Multi-Factor Authentication (MFA) Enforcement**: Require MFA for privileged account logins and privileged actions (step-up authentication).
- **ac-3: Inactive and Expired Accounts**: Disable accounts within 5 days of last authorised use or after 90 days of inactivity.
- **ac-4: Access Review**: Periodically review and revoke excessive privileges against a declared per-account permission baseline.
- **ac-6: Default Credentials**: Require password change on first login after admin-issued or temporary credentials.
- **ac-7: Singpass/Corppass for Public Users**: Require Singpass/Corppass step-up for high-risk transactions (public users); WOG AAD for internal/agency users.
- **ac-8: Automated Account Lifecycle Management**: Automate provisioning and deprovisioning via SCIM, equivalent push protocol, or SSO JIT provisioning.
- **ac-12: Single Sign-On (SSO) for Internal Services**: Internal users must authenticate via organisational IdP (e.g., WOG AAD), not local credentials.

## Data Protection (dp)

- **dp-3: Data in Transit Encryption**: Enforce TLS for all connections. No deprecated protocols (SSLv3, TLSv1.0, TLSv1.1). No disabled certificate/hostname validation.
- **dp-8: Data Classification Disclosure**: Display security and sensitivity classification labels adjacent to all input fields (internal applications only).

## Logging and Monitoring (lm)

- **lm-4: Audit Logging**: Log management and audit events (authentication, authorization failures, data access).
- **lm-15: Structured Log Formatting**: Use consistent schemas (ECS, JSON) for log output.
- **lm-16: Key Signals Monitoring**: Track latency, traffic, errors, and saturation (RED/USE metrics).
- **lm-18: Whole of Government Application Analytics (WOGAA)**: Public-facing government digital services must embed the WOGAA tracking script (internal-only applications are N/A).
- **lm-19: Log Sanitisation**: Mask or tokenise sensitive data in logs.

## Cryptography (ck)

- **ck-1: Cryptographic Key Establishment**: Use industry-standard schemes (NIST SP 800-56).
- **ck-2: Cryptographic Key Rotation**: Regularly rotate keys (e.g., via KMS).
- **ck-4: Cryptographic Key Storage**: Securely store cryptographic keys and implement strict access controls based on the principle of least privilege.

## Generative AI (ga)

- **ga-8: Inform Users about GenAI Risks and Limitations**: Require users to explicitly acknowledge the risk of inaccurate or fabricated outputs (hallucinations) before accessing GenAI features.

## Project Management (pm)

- **pm-6: System Documentation**: Maintain architecture documentation, software/hardware inventory, API specifications, network topology, and data flow diagrams.

## Security Testing (st)

- **st-3: Public Vulnerability Disclosure Programme**: Provide a discoverable public channel (security.txt or site-wide link) for security researchers to report vulnerabilities.
