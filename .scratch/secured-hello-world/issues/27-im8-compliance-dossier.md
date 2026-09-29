# 27: IM8 control mapping dossier and accepted deviations

**What to build:** The security deliverable a reviewer actually reads. Two halves, and the second
matters more than the first: a **mapping** from every requirement to where it is enforced and
which test proves it, and an honest register of **accepted deviations** with their risk rationale.

A compliance document that claims full coverage is less credible than one that names its gaps
precisely. This application has real gaps — local HTTP, no MFA, no penetration test, a stubbed
email service, point-in-time dependency scanning — every one of them a deliberate consequence of
the PRD's out-of-scope list. Recording them as accepted deviations with rationale is what turns
them from undisclosed weaknesses into governed risk.

The mapping is not derived here. The authoritative per-control mapping already exists in
[`spec.md`](../spec.md) under **Control mapping** — all 36 IM8 Reform controls by real ID, each
with a level, a spec verdict and the satisfying ticket(s). The dossier **reproduces** that table
and cites the spec as its source; it does not re-derive verdicts, because two independently
derived mappings that disagree is a worse outcome than one mapping with a single owner. What the
dossier adds is **evidence references** — file and line — once code exists.

A waiver is not the same as non-applicability, and conflating the two is the most common way a
compliance document flatters itself. `ac-2`, `ac-12` and `ac-8` all **apply** to an application
of this shape; they are absent because the PRD excludes them, which makes them waived, not N/A.
An unrecorded waiver is itself an audit finding, so the waiver register is a required section
rather than an appendix.

The same discipline runs the other way. Controls that genuinely do not apply need an explicit
**N/A determination with rationale**, not silence — a reviewer cannot tell an inapplicable
control from an overlooked one unless the document says which it is.

**Blocked by:** 23, 25, 26.

**Status:** ready-for-agent

**IM8 controls:** `pm-6` System Documentation; `st-3` Public Vulnerability Disclosure Programme;
and the full 36-control IM8 Reform catalogue as the dossier's subject matter. *ASVS: V1
Architecture Design and Threat Modeling, V14 Configuration.*

- [ ] A mapping table covers every PRD user story and every non-functional/security requirement,
      with columns for: the requirement, the IM8 control family, the ASVS chapter, where it is
      enforced, and the **test that proves it**
- [ ] A second table covers **all 36 IM8 Reform controls by real control ID**, each with its
      verdict and the ticket(s) satisfying it — no control is omitted, including the ones that
      pass trivially, because completeness is what makes the absence of a row meaningful
- [ ] That table **reproduces** the "Control mapping" section of `spec.md`, citing it as the
      authoritative source; the verdicts are not re-derived here, and any disagreement with the
      spec is resolved by correcting one of them rather than shipping both
- [ ] Each row carries an **evidence reference** — file and line — added once the code exists, so
      a reviewer can go from control to implementation without searching
- [ ] The **ARC Framework** is recorded as N/A in full, all 88 controls, with the rationale stated
      once rather than per control: every ARC control presupposes an LLM, an MCP server, an agent
      system prompt, agent memory, inter-agent messaging, or agent-generated code, and this
      application has none of them, so there is no component for any ARC control to attach to
- [ ] Explicit **N/A determinations with rationale** are recorded — silence is not a
      determination — for at minimum: `as-12` Malware Scanning of Uploads (no upload capability
      anywhere in the application), `as-15`, `ac-7`, `dp-8`, `lm-18`, `ck-1`, `ck-2`, `ck-4`, and
      `ga-8`
- [ ] The `ck-1`/`ck-2`/`ck-4` determinations carry the note that all three would become **live**
      if the JWT appendix were ever built, since a signing key would then need establishment,
      rotation and storage — their N/A depends on an architectural choice, not on the
      application's nature
- [ ] Every row's test reference points at a test that exists and passes — an unevidenced row is
      a gap, and is recorded as one rather than left looking satisfied
- [ ] The PRD's Testing Requirements list is reconciled item by item, since it names the minimum
      required coverage explicitly: login paths, lockout and IP throttling, logout cookie reuse,
      reset token single-use and expiry and session invalidation, the admin self-action guards,
      and the `USER`-gets-403 role check
- [ ] The **IM8 caveat is stated prominently**: the control-family names are descriptive and have
      not been verified against the IM8 source text, no clause identifiers are cited, and pinning
      real control IDs requires the agency control matrix
- [ ] Accepted deviations are recorded, each with what is absent, why it is out of scope, the
      residual risk, and what would close it:
  - [ ] Local development over HTTP with no TLS — the `Secure` cookie attribute and HSTS both
        depend on it, and any real deployment must sit behind HTTPS
  - [ ] No MFA, leaving a single authentication factor as the only barrier
  - [ ] JWT documented in the appendix but not built
  - [ ] Stubbed email service that **logs reset links in plaintext**, which would be a serious
        finding in any non-development environment
  - [ ] No container, host, or pipeline hardening, since that infrastructure is out of scope
  - [ ] Point-in-time dependency scanning rather than continuous
  - [ ] No penetration test or independent security assessment
  - [ ] Authorization no finer than the USER/ADMIN check
  - [ ] Registration deliberately discloses that a username or email is taken, a bounded
        exception to enumeration resistance (see ticket 05)
- [ ] A **waiver register** is a required section of the dossier, distinct from the N/A
      determinations, and it states explicitly that these are controls which **would otherwise
      apply**: a waiver is a decision to accept a gap, not a finding that the control is
      irrelevant, and an unrecorded waiver is itself an audit finding
- [ ] Every waiver entry carries three things: the **PRD clause** that waives it, the **residual
      risk**, and the **compensating controls** with the ticket numbers that deliver them
- [ ] The register **reproduces the six-row waiver register in [`spec.md`](../spec.md)**, which is
      keyed to the PRD's Out of Scope list so that every scope exclusion has a traceable control
      disposition. As with the control mapping, the dossier reproduces rather than re-derives it, and
      a disagreement is resolved by correcting one of the two rather than shipping both
- [ ] The register therefore covers all six: `ac-2` MFA Enforcement; `ac-12` SSO for Internal
      Services together with `ac-8` Automated Account Lifecycle; the dev-over-HTTP `dp-3` Data in
      Transit Encryption gap; the stubbed `EmailService` against `lm-19`; the no-CI/CD consequences
      for `pm-6` continuous scanning and `lm-16` alert routing; and the USER/ADMIN granularity
      ceiling on `ac-1` and `ac-4`
- [ ] The JWT exclusion is recorded even though it waives no live control, because the
      `ck-1`/`ck-2`/`ck-4` N/A determinations are **contingent** on it
- [ ] The `EmailService` entry names it a **deployment blocker** rather than a cosmetic stub,
      because it logs a live reset token — the residual risk is full account takeover from log
      read access, and the compensating control is that it exists only under the dev profile
- [ ] The `dp-3` entry records that it is held as DEFERRED rather than waived outright, since the
      prod-profile TLS requirement, the TLS 1.2 protocol floor, and the profile-conditional
      `Secure` cookie all remain binding
- [ ] Secrets handling is documented: what must be externally supplied (the admin bootstrap
      credential above all), that nothing sensitive is committed, and how it is expected to be
      provided in a real deployment
- [ ] The three documentation artefacts `pm-6` requires that do not exist yet are produced here,
      since architecture and the data model are already covered by the PRD and spec: an **OpenAPI
      3 specification** of the API, a **data-flow diagram**, and the **SBOM** from ticket 26
- [ ] The OpenAPI 3 specification is exposed **outside production only**, per `as-13`, so the
      document that enumerates every endpoint is not itself an attack aid in prod
- [ ] The data-flow diagram shows the **trust boundaries** between browser, API and database —
      which data crosses which boundary and under what protection — rather than being a component
      box diagram with arrows
- [ ] `st-3` Public Vulnerability Disclosure Programme is carried as a **deployment checklist
      item**, not a code change: a `/.well-known/security.txt` disclosure channel presupposes a
      public deployment and hosting is out of scope. The dossier says this explicitly, so it is a
      recorded decision with an owner rather than an omission a reviewer has to notice
- [ ] A short threat-model note records the attacks this design is built against — credential
      brute force, session replay, CSRF on a cookie-based session, privilege escalation, account
      enumeration, reset-token replay — and which ticket answers each
- [ ] The PRD's session-versus-JWT trade-off is carried into the dossier as the recorded
      architectural decision, including why the JWT blacklist requirement undercuts its main
      claimed advantage
- [ ] The dossier states plainly that it is a **spec-phase** mapping: a PASS means the spec
      mandates the control, **not** that code implements it. Treating a spec-phase PASS as
      implementation evidence is the single most misleading thing this document could do
- [ ] It records that the `im8-review` skill performs the code-level audit later under a stricter
      evidence standard, where a control delegated to a framework counts as **WARN rather than
      PASS** unless the module is both imported and configured in the application's own code — so
      some spec-phase PASS verdicts are expected to downgrade
- [ ] It recommends being **re-verified against that code-level audit before sign-off**, naming
      that as the gate rather than leaving the spec-phase verdicts as the final word
- [ ] The dossier states its own shelf life: it describes a specific commit, and re-verification
      is required before any deployment
