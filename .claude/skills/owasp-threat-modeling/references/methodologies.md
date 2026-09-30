# Threat Modeling Methodologies

## STRIDE

| Category | Threat Type | Description | Example |
|----------|-------------|-------------|---------|
| S | Spoofing | Impersonating a user or system | Stolen session tokens |
| T | Tampering | Modifying data in transit or at rest | SQL injection altering records |
| R | Repudiation | Denying an action occurred | Missing audit logs |
| I | Information Disclosure | Exposing sensitive data | API returning excessive fields |
| D | Denial of Service | Making a service unavailable | Resource exhaustion attack |
| E | Elevation of Privilege | Gaining unauthorized access | Broken access control |

## LINDDUN (Privacy-Focused)

Use LINDDUN when the feature handles personal data or has privacy compliance requirements (GDPR, PDPA, HIPAA).

| Category | Threat Type | Description |
|----------|-------------|-------------|
| L | Linkability | Associating data items across contexts |
| I | Identifiability | Identifying an individual from data |
| N | Non-repudiation | Inability to deny an action (privacy risk) |
| D | Detectability | Determining if data about a subject exists |
| D | Disclosure | Exposing personal information |
| U | Unawareness | User unaware of data collection |
| N | Non-compliance | Violating privacy regulations |
