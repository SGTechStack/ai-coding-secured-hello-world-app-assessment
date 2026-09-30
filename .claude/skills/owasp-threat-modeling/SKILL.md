---
name: owasp-threat-modeling
description: Produce a threat model for a feature or system using OWASP Threat Dragon. Creates data flow diagrams, identifies threats via STRIDE or LINDDUN, and generates a findings report. Use when designing a new feature, conducting a threat risk assessment, or preparing for a security design review.
---

# OWASP Threat Modeling

Run a structured threat modeling session using OWASP Threat Dragon to surface security and privacy threats before or during implementation.

## Prerequisites

- OWASP Threat Dragon — desktop app or Docker instance
- Architecture documentation or PRD for the feature/system in scope

**Install (Docker):**
```bash
docker run -p 3000:3000 \
  -e ENCRYPTION_JWT_SIGNING_KEY=$(openssl rand -hex 32) \
  -e ENCRYPTION_JWT_REFRESH_SIGNING_KEY=$(openssl rand -hex 32) \
  -e ENCRYPTION_KEYS='[{"isPrimary":true,"id":0,"value":"'$(openssl rand -hex 16)'"}]' \
  -e NODE_ENV=production \
  owasp/threat-dragon:latest
```

Or download the desktop app from the [Threat Dragon releases page](https://github.com/OWASP/threat-dragon/releases).

## Workflow

1. **Define scope** — document system name, assets being protected, external dependencies, compliance requirements, and trust boundaries
2. **Create DFDs** — model Processes, Data Stores, External Entities, Data Flows, and Trust Boundaries in Threat Dragon (see [references/dfd-elements.md](./references/dfd-elements.md))
3. **Identify threats** — use Threat Dragon's rule engine as a starting point; apply STRIDE per element type from the applicability matrix
4. **Triage** — mark each threat as Mitigated, Not Applicable, or Open; assign priority and owner to Open items
5. **Define mitigations** — for each Open threat, document control strategy, specific technical controls, owner, and timeline
6. **Generate report** — export PDF from Threat Dragon; store the `.json` model file in version control alongside code
7. **Update** — revisit when architecture changes; reference findings in issue acceptance criteria

See [references/methodologies.md](./references/methodologies.md) for STRIDE and LINDDUN reference tables.

## Rules

- Start with a Level 0 DFD before decomposing into detailed diagrams
- Time-box initial sessions to 90 minutes; iterate in follow-ups
- Treat the threat model as a living document — store it in version control alongside code
- Privacy-sensitive features: supplement STRIDE with LINDDUN
