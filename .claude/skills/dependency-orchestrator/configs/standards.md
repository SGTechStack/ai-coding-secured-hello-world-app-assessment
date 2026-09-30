# Appfw Standards Reference

This file defines which standards exist, their hard dependencies, and which nodes they apply to. Teams can extend this file with their own standards.

## How Standards Drive the DAG

1. **Backwards mapping** derives nodes from stories using the `{domain}_{layer_suffix}` convention
2. Each node is checked against the **Node to Standard Mapping** below — if a match is found, set the node's `appfw_standard` field
3. If the matched standard has **hard dependencies** (see Dependencies Between Standards), create new nodes named after each prerequisite standard and add them to the chain as predecessors
4. Each prerequisite node also gets its own `appfw_standard` set to the standard it implements

**Example:** Backwards mapping derives `be_notification_service`. Concept mapping matches it to `MCNS_Core`. `MCNS_Core` depends on `Shared-Auth`. So:
- `be_notification_service` gets `appfw_standard: "Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core"`
- A new node `shared_auth` is created with `appfw_standard: "Appfw-Mcc-Standards/Appfw-Shared-Auth-Standards"`
- Edge: `shared_auth → be_notification_service`
- The story now references both standards

If no standard matches a node, omit the `appfw_standard` field. Not every node needs a standard.

---

## Standards Catalog

### User Standards (`Appfw-User-Standards`)

| Sub-Standard | Path | Description |
|---|---|---|
| **Shared_Recipes** | `Appfw-User-Standards/Shared_Recipes` | RBAC, secure self-read endpoint, security headers (used by both SSO and Standalone) |
| **User_SSO** | `Appfw-User-Standards/User_SSO` | OAuth2/OIDC, PKCE, back-channel logout, refresh token rotation, auto-provisioning |
| **User_Standalone** | `Appfw-User-Standards/User_Standalone` | Username/password, password reset, account lockout, session login, CSRF |

### MFA Standards (`Appfw-Mfa-Standards`)

| Sub-Standard | Path | Description |
|---|---|---|
| **MFA_Core** | `Appfw-Mfa-Standards/MFA_Core` | Unified PIN + TOTP factors (standalone base) |
| **MFA_MCC** | `Appfw-Mfa-Standards/MFA_MCC` | Cloud deployment: OTP via MCNS, AWS KMS for TOTP secret encryption |
| **MFA_Critical_Transaction** | `Appfw-Mfa-Standards/MFA_Critical_Transaction` | AOP-based enforcement gating critical transactions behind a second factor |
| **MFA_Frontend/Standalone** | `Appfw-Mfa-Standards/MFA_Frontend/Standalone` | React module for standalone MFA (PIN, TOTP) |
| **MFA_Frontend/MCC** | `Appfw-Mfa-Standards/MFA_Frontend/MCC` | React module for cloud MFA (OTP, KMS-backed TOTP) |

### MCC Standards (`Appfw-Mcc-Standards`)

| Sub-Standard | Path | Description |
|---|---|---|
| **Shared-Auth** | `Appfw-Mcc-Standards/Appfw-Shared-Auth-Standards` | `private_key_jwt` signing, JWKS publication, OAuth2 client credentials |
| **MCNS_Core** | `Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core` | Single SMS/email delivery, rate limiting, retry policies |
| **MCNS_Batch** | `Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Batch` | Batch notification retry pipeline, failed notification reprocessing |
| **MPDS** | `Appfw-Mcc-Standards/Appfw-Mpds-Standards` | Personnel data retrieval, query/response models |

### Report Standards (`Appfw-Report-Standards`)

| Sub-Standard | Path | Description |
|---|---|---|
| **Report_Core** | `Appfw-Report-Standards/Report_Core` | JasperReports, JRXML templates, fixed-schema reporting |
| **Report_Programmatic** | `Appfw-Report-Standards/Report_Programmatic` | Dynamic columns, JasperDesign, variable column schemas |

### File Standards (`Appfw-File-Standards`)

| Sub-Standard | Path | Description |
|---|---|---|
| **File MCC** | `Appfw-File-Standards/mcc` | AWS deployment: S3 clean store, SFS scanner, virus scanning, ShedLock, zombie cleanup |
| **File Standalone** | `Appfw-File-Standards/standalone` | Local deployment: local storage, storage quota, local promotion, virus scan bypass |

### Interface Standards (`Appfw-Interface-Standards`)

Single standard (no sub-components). Path: `Appfw-Interface-Standards`.

### Logging Standards (`Appfw-Logging-Standards`)

Single standard (no sub-components). Path: `Appfw-Logging-Standards`.

---

## Dependencies Between Standards

Hard dependencies — a standard's prerequisites must be implemented before it. When a node references a standard, all prerequisite standards in the chain must also exist as nodes in the DAG.

### User Standards

SSO and Standalone are **mutually exclusive** deployment profiles. Both require Shared_Recipes.
- SSO Login: `Shared_Recipes` → `User_SSO`
- Standalone Login: `Shared_Recipes` → `User_Standalone`

### MFA Standards (8 build paths)

MFA requires a User standard (`User_SSO` or `User_Standalone`) to be built first — authentication must exist before adding a second factor.

1. PIN Standalone on Login: `User_Standalone` → `MFA_Core` → `MFA_Frontend/Standalone`
2. TOTP Standalone on Login: `User_Standalone` → `MFA_Core` → `MFA_Frontend/Standalone`
3. TOTP MCC on Login: `User_SSO` → `MFA_Core` → `MFA_MCC` → `MFA_Frontend/MCC`
4. OTP MCC on Login: `User_SSO` → `MFA_Core` → `MCNS_Core` → `MFA_MCC` → `MFA_Frontend/MCC`
5. PIN Standalone on Critical Transaction: `User_Standalone` → `MFA_Core` → `MFA_Critical_Transaction` → `MFA_Frontend/Standalone`
6. TOTP Standalone on Critical Transaction: `User_Standalone` → `MFA_Core` → `MFA_Critical_Transaction` → `MFA_Frontend/Standalone`
7. TOTP MCC on Critical Transaction: `User_SSO` → `MFA_Core` → `MFA_MCC` → `MFA_Critical_Transaction` → `MFA_Frontend/MCC`
8. OTP MCC on Critical Transaction: `User_SSO` → `MFA_Core` → `MCNS_Core` → `MFA_MCC` → `MFA_Critical_Transaction` → `MFA_Frontend/MCC`

### MCC Standards

`Shared-Auth` is the prerequisite for all MCC sub-standards.
- Notification (single): `Shared-Auth` → `MCNS_Core`
- Notification (batch): `Shared-Auth` → `MCNS_Core` → `MCNS_Batch`
- Personnel data: `Shared-Auth` → `MPDS`

### Report Standards

- Fixed-schema report: `Report_Core`
- Programmatic report (dynamic columns): `Report_Core` → `Report_Programmatic`

### File Standards

MCC and Standalone are **mutually exclusive** deployment profiles. No inter-standard dependencies.
- AWS file management: `Appfw-File-Standards/mcc`
- Local file management: `Appfw-File-Standards/standalone`

---

## Node to Standard Mapping

Rules for determining which standard(s) apply to a node. If no rule matches, the node has no standard — omit the `appfw_standard` field.

### Foundation nodes

| Node ID | Standard(s) | Resolution |
|---|---|---|
| `logging` | `Appfw-Logging-Standards` | Always |
| `auth_service` | `["Appfw-User-Standards/User_SSO", "Appfw-User-Standards/Shared_Recipes"]` or `["Appfw-User-Standards/User_Standalone", "Appfw-User-Standards/Shared_Recipes"]` | Resolve based on whether the project uses SSO or standalone login. Both always include Shared_Recipes. |
| `exception_mapping` | — | No standard |
| `openapi_docs` | — | No standard |
| `arch_tests` | — | No standard |

### Concept-based mapping

When a backwards-mapped node's primary responsibility matches a concept below, apply the corresponding standard. The node keeps its natural `{domain}_{layer_suffix}` name. If the matched standard has hard dependencies (see above), create prerequisite nodes and add them to the chain.

| Concept / keywords in story | Standard to apply | Disambiguation |
|---|---|---|
| file upload, file download, document upload, attachment, file storage | `Appfw-File-Standards/mcc` (AWS/cloud) or `Appfw-File-Standards/standalone` (local/on-prem) | Default to MCC for cloud projects, standalone for on-prem |
| send email, send SMS, push notification | `Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core` | — |
| batch notifications, scheduled notifications | `Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Batch` | — |
| MFA, two-factor, 2FA, OTP (standalone) | `Appfw-Mfa-Standards/MFA_Core` | — |
| MFA, two-factor, 2FA, OTP (cloud/MCC) | `Appfw-Mfa-Standards/MFA_MCC` | — |
| MFA critical transaction, step-up auth | `Appfw-Mfa-Standards/MFA_Critical_Transaction` | — |
| report, generate PDF, generate XLSX, export CSV | `Appfw-Report-Standards/Report_Core` | — |
| dynamic report, programmatic report, custom columns | `Appfw-Report-Standards/Report_Programmatic` | — |
| batch file interface, file ingestion, file trigger | `Appfw-Interface-Standards` | — |

### All other nodes

Nodes that don't match any rule above have no standard. Omit the `appfw_standard` field.
