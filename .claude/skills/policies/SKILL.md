---
name: policies
description: Loads and applies organisational policy references (IM8, ARC) to audit a document for security and governance compliance. Use when the user asks about compliance, IM8, ARC, policies, or organisational security standards.
---

# Policies

## Quick start

Load the policy reference files relevant to the document being audited. For a full compliance review, load all.

## Workflows

1. Triage — identify which policy domains the document touches. Load only relevant references below.
2. Audit — for each applicable control, classify as PASS, AUTO-FIX, FAIL, DEFERRED, or N/A.
3. Fix — apply minimum changes for AUTO-FIX items (add scenarios, edge cases, tighten "should" to "MUST").
4. Append summary comment to the document, print findings.

**Never auto-fix:** scope decisions, security boundaries, permission models — mark FAIL.

## References

- [im8-reform-app-policy.md](./references/im8-reform-app-policy.md) — IM8 security controls: authentication, data protection, audit logging, session management, cryptography, input validation
- [arc-framework-policy.md](./references/arc-framework-policy.md) — ARC framework: agentic AI governance, risk classification, capability boundaries
