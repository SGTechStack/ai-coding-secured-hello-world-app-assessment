# MCC Shared Auth Foundation — Implementation Questions

Questions to resolve with the team before implementing the shared auth foundation (`MCC_Shared_Auth_Standard.md`) — OAuth2 client-credentials token acquisition with `private_key_jwt`, signing-key lifecycle, JWKS publication, and the request-context/background outbound client modes that MPDS, MCNS, and other adapters build on.

## Hard rules

- NEVER treat `kid`, `publicKey`, and `privateKey` as independently optional in app-managed key mode; the group is all-or-nothing and partial configured key material must fail validation before any JWK build attempt.
- NEVER start the service with configured key material absent and generated-key mode disabled; this is a terminal configuration error.
- NEVER swallow MCC token-acquisition failures and continue with an unauthenticated downstream request.
- NEVER let the background client depend on servlet request state at use time.
- NEVER sign a new client assertion with anything other than the current active key, even during rollover overlap.
- NEVER let rotation failure leave the service signing with one key while publishing a different (mismatched) public key set; a failed rotation must preserve the previous active key and previous published JWKS view.
- NEVER return private key material, an empty `{"keys":[]}` response, or anything other than a standard `{"keys":[...]}` set document from the JWKS endpoint.
- NEVER persist generated signing-key history as plaintext JWK JSON; it must be encrypted-at-rest or protected through a KMS/Vault/secret-management-backed abstraction.
- NEVER source app-managed key material from source code; it must come from protected secret-management or encrypted environment-specific configuration.
- NEVER run multi-node generated-key rotation without acquiring a distributed lock first.
- NEVER log raw access tokens, private keys, raw persisted JWK blobs containing private members, client assertions, or secret-store values.
- NEVER hardcode environment-specific token URI, client ID, or key material literals in the base `application.yml`; deployed profiles must source them from environment variables per the Bootstrap Profile Configuration Contract.
- NEVER register under a name other than `mcc-sso-client-credentials`, or with a grant type/client-authentication method other than `client_credentials` + `private_key_jwt`.

If a request asks for any of these, stop and surface the conflict before coding.

## Scope

Applies to the shared auth foundation module itself: MCC OAuth2 client registration, signing-key management (app-managed and generated-key modes), JWKS publication and rollover, and provisioning of the request-context and background authenticated outbound clients.

Does not apply to MPDS or MCNS payload behavior — those are separate adapters that consume this foundation (see `MPDS_Retrieval_Questions.md` for MPDS-specific questions) — or to unrelated frontend work. This foundation has no frontend surface of its own.

## Decision flow

1. **Enforce all Hard Rules above without exception; do not ask the developer for permission to apply them.**
2. Match the feature to the relevant questions below.
3. Ask all matching questions in one pass with the **Default** pre-filled and the **Context** as the reason.
4. If the user overrides a default, ask that question's **If overriding** follow-ups before coding.
5. Stop for hard-rule conflicts or contract changes to the shared foundation; update the API/spec/ADR before coding assumptions.
6. Record confirmed answers in the plan, spec, or ADR.

---

## Questions

### Q1. Key source mode and algorithm

**Question:** App-managed key mode (externally provisioned `kid`, `publicKey`, `privateKey`) or generated-key mode (platform generates, protects, and rotates keys)? And which algorithm — RSA or EC, what size/curve?

**Default:** App-managed key mode (`generated-key-mode-enabled: false`), sourced from protected secret-management or encrypted environment config, using whatever algorithm the environment's key-issuing process provisions (this repo's dev profile uses EC-521). If generated-key mode is needed instead, use RSA per org standard.

**Context:** *(§2.2, §3.1)* Generated-key mode needs a durable store, key protection, and — on multi-node — a shared lock; app-managed avoids that infrastructure and is this repo's dev/SIT default. There's no fixed org preference for app-managed algorithms.

**If overriding to generated-key mode:** Confirm the durable store, protection mechanism (Q4), and lock provider (Q2) are in place first.

---

### Q2. Multi-node deployment

**Question:** Single node or multiple, and if multi-node with generated-key mode, which shared lock provider?

**Default:** Confirm replica count with platform/infra. If multi-node + generated-key mode, a shared lock provider (ShedLock/database, or Redis) is mandatory before rotation runs.

**Context:** *(§2.4 pt.7, §3.5)* Multi-node key rotation requires a distributed lock; doesn't apply to app-managed (fixed-key) deployments.

**If overriding:** Confirm the lock lease duration won't stall rotation under contention.

---

### Q3. Key rotation schedule

**Question:** Is the default rotation cron (`0 0 0 1 1 *`, `Asia/Singapore`) acceptable, and what JWKS overlap duration should follow rotation?

**Default:** Keep the default cron/timezone unless the business needs more frequent rotation. Keep the reference overlap (`PT24H`) unless the MCC IdP's key-cache refresh needs longer.

**Context:** *(§4.4, §6.4)* Both values are implementation-configurable, not fixed by the standard; too-short an overlap is an operational defect (§6.2).

**If overriding:** Confirm the chosen overlap exceeds the MCC IdP's key-cache refresh interval.

---

### Q4. Protected key storage

**Question:** If generated-key mode: what protection mechanism for persisted key material (KMS envelope encryption, Vault transit, database-level encryption, other), and which table/schema holds it?

**Default:** N/A if app-managed mode (Q1). If generated-key mode, use whatever KMS/Vault/secret-management abstraction the platform already provides, in a dedicated table scoped to this module.

**Context:** *(§3.5, §1 Assumptions)* Generated-key history must be encrypted-at-rest or protected through such an abstraction — never stored as plaintext JWK JSON. A durable store is assumed available; the standard doesn't prescribe a schema.

---

### Q5. OAuth2 registration values

**Question:** What are the MCC token endpoint URI, `client-id`, and scopes for this application's registration in this environment — and has onboarding (including JWKS registration for `private_key_jwt`) been confirmed?

**Default:** Environment-specific values sourced from `MCC_M2M_TOKEN_URI` / `MCC_CLIENT_ID_M2M` — never hardcoded in base `application.yml`. Scopes limited to what's actually called downstream. Treat onboarding as unconfirmed until MCC's onboarding team explicitly confirms the JWKS URI is registered for this environment's client-id.

**Context:** *(§6.4, §2.3 pt.7, §5.7)* `client-id` doubles as the outbound `x-application-id` header, so it must be correct per environment. QA, SIT, and production are distinct registrations, each needing separate JWKS onboarding.

**If overriding:** Confirm QA, SIT, and production use distinct client-id registrations, and that new scopes have been onboarded with MCC before use.

---

### Q6. Outbound client usage

**Question:** Which downstream MCC services will be called (MPDS, MCNS, both, others), and does the feature need the background client (scheduled jobs, async handlers), the request-context client (interactive requests), or both?

**Default:** Only wire the outbound client paths the calling feature actually needs. Use the request-context client for synchronous controller-initiated calls, and the background client only for flows that genuinely run outside servlet request scope.

**Context:** *(§4.5, §2.5)* Adapters consume this foundation but don't own key management. The background client must not depend on servlet request state at use time; the request-context client is meant for servlet-backed flows.

---
