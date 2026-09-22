# Critical Transaction MFA Standard — Implementation Questions

Questions to resolve with the team before starting implementation. These questions are specific to the AOP-based enforcement layer. Resolve the base factor and enrollment questions in the [Base Standalone Application Standard Questions](../MFA_Core/Base_Standalone_Application_Standard_Questions.md) first.

---

### Enforcement scope

1. Which operations (methods) need to be gated behind a second factor? List each method with its expected MFA types (e.g. PIN or TOTP).
---

### Factor type and provider dispatch

2. Does the service need to support multiple factor types across different annotated methods (e.g., one method uses PIN, another uses TOTP), or is the same single factor type used on every protected method? *(See Critical Transaction Standard §4.2 design choice: MFA Type Container vs. direct invocation)*
   > If only one factor type is ever used, the aspect can invoke the provider directly without a container — simpler to configure and no registry indirection. Use the MFA Type Container when different methods may specify different `mfaType` values via the annotation.

---

### Role pre-authorisation

3. Should a Spring Security role check be enforced before factor verification on annotated methods? *(See Critical Transaction Standard §1 design choice: Critical Transaction Role)*
   > Role pre-authorisation is optional. If enabled, the caller must hold the declared role(s) before factor verification is even attempted. If not needed, omit the `RoleAuthorizer` bean entirely and the aspect skips the role check.
4. If role pre-authorisation is enabled: which roles are required — `ROLE_CRITICAL_TRANSACTION`, a custom application role, or a combination? Must every annotated method use the same role, or does it vary per method?
5. If role pre-authorisation is enabled: should the role check block all callers who lack the role (hard gate), or are there any callers who should bypass it (e.g., internal service accounts)?

---

### Pre- and post-authentication logic

6. Is there any logic that must run before factor verification on each protected method — for example, session state validation, request enrichment, or an early audit stamp? *(See Critical Transaction Standard §4.1 design choice: Pre-authentication logic)*
7. Is there any logic that must run after successful factor verification but before the method executes — for example, publishing an event? *(See Critical Transaction Standard §4.1 design choice: Post-authentication logic)*
8. If pre- or post-authentication logic is needed: is it the same for every annotated method, or does it vary per method? Varying logic may require a more flexible hook point than a single shared aspect.

---

### Operational

9. Who is the on-call owner for failures on critical transaction MFA enforcement in production?
