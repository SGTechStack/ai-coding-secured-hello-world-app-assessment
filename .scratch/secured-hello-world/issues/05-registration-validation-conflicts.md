# 05: Registration validation and conflict handling

**What to build:** Registration now rejects what it should reject, and says so clearly enough
that a visitor can fix it. Two rejection families: an already-taken username or email, and a
password that fails the strength policy. Both must leave **no account behind**.

Also establishes the password-strength policy as one reusable rule, because ticket 14 (password
reset confirmation) has to apply the identical policy — two divergent copies of a password rule
is a security defect waiting to happen.

Covers PRD Story 1, rejection paths.

**Blocked by:** 04.

**Status:** ready-for-agent

**IM8 controls:** `as-1` Input Validation; `as-5` Password Requirements; `as-3` Output
Sanitisation. *ASVS: V2.1 Password Security, V5 Validation Sanitization and Encoding,
V7 Error Handling.*

- [ ] Every registration request DTO declares its own validation contract through Bean
      Validation — `@NotBlank`, `@Email`, `@Size` on the fields — and the controller parameter
      carries `@Valid`, so the rules are **declarative and auditable** in one place rather than
      scattered through service code where a reviewer has to go looking for them
- [ ] The email must be a well-formed address, rejected by the declared constraint before any
      persistence lookup runs
- [ ] The username has a declared length range and a declared character **allow-list** —
      alphanumerics plus a small set of separators — and anything outside that set is rejected
      rather than silently escaped or stripped
- [ ] The username allow-list doubles as an **output-sanitisation** control, not just an input
      one: ticket 07 reflects the stored username straight back as `Hello, <username>`, so
      constraining the character set at the point of entry is the cheaper of the two defences
- [ ] The password strength policy — minimum length 12 per the PRD — lives in **one** reusable
      validator that both registration and password reset call
- [ ] The policy resolves the PRD's silence on complexity toward **NIST SP 800-63B**: keep the
      length floor, add a check against a breached-password denylist, and explicitly **reject**
      character-composition rules as a substitute — composition rules push users toward
      predictable substitutions, while a length floor plus a breach list blocks the passwords
      that actually get guessed
- [ ] An upper length bound (128) exists purely to bound the work BCrypt is asked to do on a
      single request, not as a policy statement, and is documented as such
- [ ] BCrypt's own truncation of input past **72 bytes** is documented next to the policy, so
      nobody is surprised by it later or assumes length beyond that adds strength
- [ ] A password failing the policy is rejected with a validation error that tells the visitor
      what the requirement is, and no account is created
- [ ] An already-registered username is rejected with a clear conflict error, and no account is
      created
- [ ] An already-registered email is rejected with a clear conflict error, and no account is
      created
- [ ] The database uniqueness constraint violation is translated into the same clean conflict
      error rather than surfacing as a sanitised server error
- [ ] Validation errors identify which field failed so the form can mark it, without leaking
      persistence or framework detail
- [ ] The frontend registration form renders field-level errors returned by the backend
- [ ] Test: registering a duplicate username creates no second account and returns a conflict
- [ ] Test: registering a duplicate email creates no second account and returns a conflict
- [ ] Test: a password shorter than the policy minimum creates no account and returns a
      validation error
- [ ] Test: a registration carrying an oversized username, a malformed email address, or a
      disallowed character is rejected with a **field-level** validation error and creates no
      account
- [ ] Test: two concurrent registrations of the same username result in exactly one account

**Note on enumeration:** registration conflict errors intentionally *do* reveal that a username
or email is taken — the PRD requires a clear validation error here, and a registration form
cannot function otherwise. This is a deliberate, bounded exception to the enumeration-resistance
requirement that governs login and password reset. Record it as such in ticket 27's
deviation register.
