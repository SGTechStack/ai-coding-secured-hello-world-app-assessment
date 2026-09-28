# 22 — Extract the MFA_Core and MFA_Frontend/Standalone recipes at implementation fidelity

Type: research
Status: resolved
Blocked by: —

## Question

What exactly do the recipes prescribe for the parts of the MFA feature we are keeping, at the level of
detail an implementer can follow without re-reading the corpus?

[Resolve the MFA scope conflict raised by IM8 ac-2](19-mfa-scope-conflict.md) settled the shape:
`MFA_Core` is followed as prescribed, `MFA_Frontend/Standalone` is followed minus the advisory
`MFAPrompt`, and `MFA_Critical_Transaction` is **not** followed at all. That ticket read the standards
at survey level. This one reads the recipes at code level, for the retained scope only.

## Scope

Read, under `App-Standards/Appfw-Mfa-Standards/`:

- `MFA_Core/Base_Standalone_Reimplementation_Recipes.md` — Recipes 1–13, excluding the PIN-only ones
  (we are TOTP-only, so `PIN_USER_DETAILS`, PIN setup, PIN removal, and the PIN-scoped admin unlock are
  out of our scope; note them as skipped rather than extracting them).
- `MFA_Core/Base_Standalone_Application_Standard.md` §2.2, §2.2a, §2.5, §3.4, §4.2, §7.
- `MFA_Core/Base_Standalone_Application_Standard_Questions.md` — the open questions the standard itself
  leaves to the integrator.
- `MFA_Frontend/Standalone/MFA_Frontend_Standalone_Standard.md` and its recipe file.

Deliberately **out of scope**: everything under `MFA_Critical_Transaction/`, `MFA_MCC/`, and
`MFA_Frontend/MCC/`.

## What to extract

- **Recipe 8 configuration properties.** The `spring.eds.mfa.totp` prefix, `issuer`, `period`, `digits`,
  and whatever else the `@ConfigurationProperties` class carries. Exact property names.
- **QR provisioning.** The `otpauth://` URI construction, Base32 encoding library, the QR library and
  its invocation, the PNG response shape, and whether the `PENDING_TOTP` row is written before or after
  the image is produced.
- **Recipe 10, the transactional promotion.** `PENDING_TOTP` → confirmed row, the single `baseCounter`
  computation, the ±1 skew loop, the constant-time compare, and the `lastUsedCounter` initialisation.
- **Recipe 12, verification.** The `REQUIRES_NEW` propagation and why, the persisted failure counters,
  and the exception types thrown. Note that we are re-homing this logic into a Spring Security
  `AuthenticationProvider` that grants `FactorGrantedAuthority.withFactor("TOTP")` — extract what the
  recipe does, and flag any part that assumes it runs inside the AOP aspect we are not building.
- **The `EncryptionService` shaped hole.** Confirm what the recipes expect of it (method signatures,
  where it is injected) so our AES-GCM implementation drops in cleanly. Its design is already decided in
  ticket 19; this is about the seam, not the primitive.
- **Entity and table naming drift.** The standard says `TOTP_USER_DETAILS`; the recipes promote into
  `OTPUserDetails` / `OTP_USER_DETAILS` and inject `OTPRequestContext` and
  `PendingOTPUserDetailsRepository`. Report which names the recipes actually use, since ticket 12 has to
  pick one set and justify it.
- **Frontend recipes** for the enrolment screen and the code-entry dialog: the object-URL lifecycle
  (creation and revocation), the digit-slot input, the form library usage, and the prescribed Vitest
  cases. Report the internal contradiction on whether the Generate-QR button is enabled when a key
  already exists (§3.2 says always enabled, §5 says disabled) and which reading the backend constraint
  supports.
- **Anything the recipes assume about the enforcement layer we replaced**, so ticket 23 knows what it is
  designing rather than inheriting.

## Done when

An implementer can build enrolment, confirmation, and verification from the extraction alone, and every
place our chosen design departs from a recipe is named rather than discovered later.

## Answer

**The recipes do not compile as printed, and the one recipe this ticket most needed does not exist.**
Everything else — the algorithm, the schema, the promotion, the verification loop — extracts cleanly and
is genuinely followable. But the extraction turns up three structural facts that change what tickets 12,
14, 16 and 23 are doing:

1. **There is no provisioning recipe.** `MFA_Core` gives `generateSecretKey()` and `generateQRCode(...)`
   as static utilities with **no caller**: no command, no controller, no endpoint, no status code, no
   content type, no `PENDING_TOTP` write. Recipe 5 L325 defers it — "see cloud recipes for the full
   provisioning flow with KMS encryption" — to the MCC corpus we are not following. So this ticket's
   question *"is the `PENDING_TOTP` row written before or after the image is produced?"* **has no answer
   in the corpus.** It is a decision for ticket 23, not an extraction. *Consolidated into the register (ticket 33): R-STD-036. Amend the table by ID, not this list.*
2. **The enforcement-layer re-homing is licensed, not a deviation — but a different deviation is now
   exposed.** `MFA_Core` contains no AOP requirement at all, and `Appfw-Mfa-Standards/index.md` routes
   "MFA TOTP Standalone on Login" through exactly `MFA_Core` → `MFA_Frontend/Standalone`. CT was never on
   our path. The recipes' own "Implementation Notes" concede the request-context and command patterns are
   "not mandated" and "Implementors may use any equivalent mechanism". **What is not licensed** is §4.1's
   enforced constraint that the enforcement layer re-verifies **on each request** — our session-scoped
   `FactorGrantedAuthority` converts per-request possession proof into per-session. That is the material
   deviation, and ticket 19 named the wrong one.
3. **A TOTP-locked account cannot be unlocked.** §4.2 mandates `lockedAt` "Cleared by admin"; the standard
   defines no unlock endpoint; Recipe 11's `UnlockAccountActionCommand` touches `PINUserDetailsRepository`
   only. TOTP-only + admin-only MFA means a locked sole admin is unrecoverable without a DB edit. *Consolidated into the register (ticket 33): R-STD-037. Amend the table by ID, not this list.*

---

### 1. Recipe inventory (Recipes 1–13)

File order is non-monotonic: 1–8, **10** (L617), **9** (L716), **12** (L755), **11** (L865), 13. Cite line
numbers, not recipe numbers.

**Skipped — PIN factor:** Recipe 1 (PIN verification, bcrypt, `PIN_USER_DETAILS`), Recipe 2 (PIN
self-service setup, `^\d{6}$`), Recipe 3 (admin PIN removal), Recipe 4 (PIN setup-prompt detection),
Recipe 11 (admin unlock — PIN-scoped). Recipe 1 stays load-bearing by reference: Recipe 12's lockout rule
says "same 1-hour sliding window as PIN (see Recipe 1)", and Recipe 1 L19–49 is the **only** place the
lockout column names appear.

**Skipped — enforcement layer:** Recipe 13 (`MFATypeContainer` + startup validation). Two consequences
worth recording: its `@Bean` signature **hard-requires a `PINAuthenticationProvider` argument**, so a
TOTP-only context cannot satisfy it; and the container it declares exposes **no `getRequestKey(String)`**
despite Recipes 1, 10 and 12 calling exactly that, five times. *Consolidated into the register (ticket 33): R-STD-038. Amend the table by ID, not this list.*

**In scope:** Recipe 5 (TOTP utilities: secret generation, QR, both entities, both repositories), Recipe 6
(RFC 6238 computation), Recipe 7 (exception hierarchy + RFC 9457 mapping), Recipe 8 (config properties),
Recipe 10 (transactional promotion), Recipe 9 (deterministic test vector), Recipe 12 (verification +
persisted lockout).

### 2. Recipe 8 — configuration properties

```java
@Data
@ConfigurationProperties(prefix = "spring.eds.mfa.totp")
public class TOTPProperties {
    private String issuer;              // no default; blank/null → InternalSystemException
    private Integer period = 30;
    private Integer digits = 6;         // digits != 6 → InternalSystemException
    @PostConstruct private void validate() { ... }
}
```

YAML keys verbatim: `spring.eds.mfa.totp.issuer` (`"MyOrganisation"` in the example),
`spring.eds.mfa.totp.period: 30`, `spring.eds.mfa.totp.digits: 6`.

**Not present, and we must supply:** no `algorithm` property (`"HmacSHA1"` is hardcoded in
`TotpUtilities`), no secret-length property (20 bytes hardcoded), no QR-size, skew-window,
lockout-threshold or lockout-window properties (200×200, `-1..1`, `>= 10`, `1 HOURS` are all literals), no
encryption-key property, and no `@EnableConfigurationProperties` / `@ConfigurationPropertiesScan`
registration. `period` is never validated — a `0` or negative period divides by zero in the counter
computation. An explicitly-blank `digits:` in YAML NPEs inside `@PostConstruct`.

### 3. QR provisioning

Signature: `public static byte[] generateQRCode(byte[] key, String issuer, int period, int digits, String account) throws Exception`

```java
String base32Key = new Base32().encodeToString(key);
String encIssuer  = URLEncoder.encode(issuer,  StandardCharsets.UTF_8).replace("+", "%20");
String encAccount = URLEncoder.encode(account, StandardCharsets.UTF_8).replace("+", "%20");
String url = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&period=%d&digits=%d",
    encIssuer, encAccount, base32Key, encIssuer, period, digits);
BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 200, 200);
ByteArrayOutputStream out = new ByteArrayOutputStream();
MatrixToImageWriter.writeToStream(matrix, "PNG", out);
return out.toByteArray();
```

- Label is `encIssuer:encAccount`. Query order **`secret`, `issuer`, `period`, `digits`**. **No
  `algorithm` parameter** — so an authenticator defaulting to anything but SHA1 fails persistently with no
  diagnostic, and Recipe 6 forbids us deviating from SHA1 to match.
- Base32: `org.apache.commons.codec.binary.Base32`, instance method. QR: ZXing `MultiFormatWriter` +
  `MatrixToImageWriter` — note the latter lives in the separate **`zxing:javase`** artifact, not
  `zxing:core`.
- **No Maven coordinates anywhere in the file** — zero `<dependency>` blocks. We pin versions for
  `com.google.zxing:core`, `com.google.zxing:javase`, `commons-codec:commons-codec`.
- Two latent URI defects: `base32Key` is **not URL-encoded** and Commons Codec emits `=` padding, which
  most authenticators tolerate but the Key URI Format does not prescribe. `.replace("+", "%20")` is
  applied to issuer and account only.
- `throws Exception` — an unmapped checked throwable; Recipe 7 has no handler for it, so it surfaces as a
  bare 500.

**Response shape and write ordering: absent.** No status, no `MediaType.IMAGE_PNG`, no `byte[]` vs
`ResponseEntity<byte[]>`, no cache headers. The only ordering prose is directional, not sequenced:
`PENDING_TOTP` is "written during TOTP provisioning" (L359) and "The plaintext secret bytes passed to
`generateQRCode` are used only for QR generation — they must not be stored" (L427). Since
`PENDING_TOTP.TOTP_KEY` is `nullable = false` and holds **ciphertext**, the row cannot precede encryption
— but write-vs-render order is ours to decide.

**Ticket 23 also inherits a protocol decision here.** §2.2 step 1 mandates a **GET** for provisioning and
step 3 mandates a create-or-update write. A state-mutating GET is unsafe per RFC 9110 §9.2.1, is
CSRF-exempt under Spring Security's default safe-method allowlist, is prefetch-hazardous, and is the one
MFA call that would *not* trigger a CORS preflight from our SPA origin. Recommendation: **POST, recorded as
a deviation.** *Consolidated into the register (ticket 33): R-MFA-010. Amend the table by ID, not this list.*

### 4. Recipe 10 — transactional promotion

`ConfirmTotpSetupActionCommand implements ActionCommand<Void>` (siblings use `MFAActionCommand`;
`ActionCommand` appears nowhere else in the corpus). Dependencies are a **comment, not fields**.

```java
// Compute the base counter once to avoid time drift across the loop.
long baseCounter = (long) Math.floor((double) Instant.now().getEpochSecond() / period);

boolean valid = false;  long matchedCounter = Long.MIN_VALUE;
for (int skew = -1; skew <= 1; skew++) {
    String computed = TotpUtilities.generateTotpFromCounter(secret, baseCounter + skew, digits);
    if (constantTimeEquals(submittedTotp, computed)) { valid = true; matchedCounter = baseCounter + skew; break; }
}
if (!valid) { throw new InvalidTotpException(); }

OTPUserDetails confirmed = otpRepository.findByUsername(username)
    .orElse(OTPUserDetails.builder().username(username).build());
confirmed.setTotpKey(pending.getTotpKey());
confirmed.setLastUsedCounter(matchedCounter);
otpRepository.save(confirmed);
pendingOtpRepository.deleteByUsername(username);
```

- Loop is 3 windows, **oldest-first**, `break` on first match. Annotation is plain **`@Transactional`
  (`REQUIRED`)** on `execute()`.
- Constant-time compare is `MessageDigest.isEqual()` on UTF-8 bytes behind a length pre-check — a
  byte-identical private method duplicated in Recipe 12, so make it one shared utility.
- **`lastUsedCounter` initialises to `matchedCounter`**, not `-1`, not `baseCounter`. Rule L712: this is
  what stops the confirmation code being replayed as a login (RFC 6238 §5.2).
- The ciphertext is copied **as-is** (`setTotpKey(pending.getTotpKey())`) with no re-encryption, so both
  tables must share one key and one envelope format.
- Promotion is **upsert-shaped** — an existing confirmed row is silently overwritten, with no guard against
  re-enrolling an already-enrolled user. §3.4's "Setup self-service guard" ("A user may only set up TOTP
  when none exists. Overwriting requires admin removal first") is not enforced by this code.
- Pending row: deleted in-transaction on success; preserved on any failure, by rule, "so the user can
  re-scan and retry without data loss".
- Endpoint `@PostMapping("/confirmTotpSetup")` takes **no body and no parameters** — the code arrives via
  `X-TOTP` through `OTPRequestContext`. No `@PreAuthorize`, no rate limit, no CSRF note.
- **No failure counting on this path.** Enrolment confirmation is brute-forceable indefinitely; only
  Recipe 12 counts. Ticket 09 inherits that hole.

### 5. Recipe 12 — verification

`@Transactional(propagation = Propagation.REQUIRES_NEW)` on `public void authenticate()`. The stated reason
(L855) is entirely aspect-derived: without it, "if the Critical Transaction aspect and `@Transactional`
share the same transaction, a business-method failure rolls back factor state". **In an
`AuthenticationProvider` there is no outer business transaction, so the annotation must be re-justified on
its own terms — failure counters must survive a rejected authentication — or dropped. Do not copy it
blindly.**

```java
Instant now = Instant.now();
if (userDetails.getLastFailedAttemptAt() == null
        || userDetails.getLastFailedAttemptAt().isBefore(now.minus(1, ChronoUnit.HOURS))) {
    userDetails.setFailedAttempts(0);
}
userDetails.setFailedAttempts(userDetails.getFailedAttempts() + 1);
userDetails.setLastFailedAttemptAt(now);
if (userDetails.getFailedAttempts() >= 10) { userDetails.setLockedAt(now); }
otpRepository.save(userDetails);
```

Reset on success: `failedAttempts = 0`, `lastFailedAttemptAt = null`, `lastUsedCounter = matchedCounter`.
Increment points: window mismatch **and** replay rejection.

Exceptions and order:

| Case | Throws |
| --- | --- |
| No row for username | `MFACodeNotFoundException(getRequestKey("TOTP"))` |
| `totpKey == null` or `requestTotp == null` (not enrolled, or header absent) | `MFACodeNotFoundException(...)` |
| `lockedAt != null` | `AccountLockedException()` |
| No match across 3 windows | `recordFailure(...)` then `InvalidTotpException()` |
| `matchedCounter <= lastUsedCounter` (replay) | `recordFailure(...)` then `InvalidTotpException()` |
| Crypto/HMAC failure | `InternalSystemException(e.getMessage(), e)` |

Stated ordering rules: lockout check precedes decryption and comparison; the missing-key/header check
precedes the lockout check, "as there is nothing to verify" — so a locked-but-unenrolled account reports
`CODE_NOT_FOUND`, not `ACCOUNT_LOCKED`.

#### Every seam that assumes the aspect we are not building

This is the part ticket 23 must replace, one for one:

1. **`extends MultiFactorAuthenticationProvider`** — a superclass **never defined in the corpus**, supplying
   a protected `mfaRequestContext` and an `ObjectProvider<PasswordEncoder>`. It is the aspect's dispatch
   contract, and it is **interface-incompatible with `org.springframework.security.authentication.AuthenticationProvider`**
   despite the near-identical name. Flag this in the ADR so a reviewer does not read "AuthenticationProvider"
   in both documents and assume conformance.
2. **`public void authenticate()` — no arguments, `void` return.** No `supports(Class<?>)`, no
   `Authentication` token, and **no authority is ever granted**: the corpus contains zero occurrences of
   `FactorGrantedAuthority`, `GrantedAuthority`, `AuthenticationManager`, `ProviderManager`, or any
   `*AuthenticationToken`. Success is signalled purely by not throwing. **The entire success-signal path is
   ours.**
3. **`mfaRequestContext.getUsername()`** — identity from a request-scoped bean, not from the `Authentication`
   principal.
4. **`((OTPRequestContext) mfaRequestContext).getRequestTotp()`** — a downcast of a request-scoped bean,
   sourced from the `X-TOTP` header on the business request. Must become the token's credentials.
5. **`OTPRequestContext` / `MFARequestContext` / request scope itself** — the tail notes explicitly release
   us from these.
6. **`MFATypeContainer` + three `getRequestKey("TOTP")` call sites**, used solely to put a header-key string
   into `MFACodeNotFoundException`. With no header, that exception message must be redesigned — and
   `getRequestKey` was never declared anyway.
7. **`MFATypeContainerConfig` registration**, keyed by aspect `mfaType` strings, with `registeredTypes()`
   existing expressly "for annotation validation in the Critical Transaction aspect". Replaced by
   `ProviderManager` wiring — but §4.1's startup-validation constraint survives in spirit: assert at startup
   that the TOTP provider bean and the authorization rule set both exist and are non-empty.
8. **`@Transactional(REQUIRES_NEW)`'s rationale** — see above.
9. **Exception-as-control-flow into `@RestControllerAdvice`.** `MFAAuthenticationException extends
   RuntimeException`, **not `AuthenticationException`**. Thrown from inside a security filter these will not
   reach `MFAExceptionHandler` (the advice only sees what reaches `DispatcherServlet`) and `ProviderManager`
   will not treat them as authentication failures. Either subclass `AuthenticationException` or translate at
   an `AuthenticationFailureHandler` / `AuthenticationEntryPoint`. The corpus never mentions
   `AuthenticationEntryPoint` or `SecurityFilterChain`.
10. **Recipe 10 carries the same seams into enrolment** — `context.getUsername()`,
    `context.getRequestTotp()`, `getRequestKey("TOTP")`, the `ActionCommand` + `EDSCommandPatternExecutorService`
    dispatch, and a no-parameter controller method. Confirmation is a plain endpoint, not a provider, so it
    needs its own decision about where the submitted code arrives from.
11. **No step-up or session-elevation model exists.** Nothing describes how a successful verification is
    remembered, because in the aspect model it isn't. Our authority-granting design has no counterpart to
    inherit.
12. **`X-TOTP`-on-business-request is in the rules text, not just the code** (L709: "The submitted `X-TOTP`
    header MUST be non-empty"). Rewriting that rule is unavoidable.

### 6. The `EncryptionService` seam

Five occurrences, total. `private final EncryptionService encryptionService;` and two calls:
`encryptionService.decrypt(pending.getTotpKey())`, `encryptionService.decrypt(userDetails.getTotpKey())`.

- **No package, no `interface` declaration, no javadoc anywhere.**
- **Only `byte[] decrypt(byte[])` is ever used.** No `encrypt` method is named or called — because the
  provisioning recipe that would encrypt does not exist. Even the encryption half of the signature is
  unstated.
- **Bespoke `byte[] → byte[]`, not Spring Security Crypto.** No mention of `TextEncryptor`, `BytesEncryptor`,
  `Encryptors`, `AesBytesEncryptor`, or `KeyGenerators`. It is however signature-identical to
  `org.springframework.security.crypto.encrypt.BytesEncryptor`, which makes that the natural substitution —
  and confirms ticket 19's choice drops in with **no wrapper**, since `totpKey` is already a byte column.
- Injection is `private final` with **no constructor shown** in Recipe 12; in Recipe 10 it is a comment. The
  corpus is deliberately vague ("injected via `@Autowired` setters or constructor").
- **No implementation, no algorithm, no mode, no IV/nonce handling, no envelope format, no rotation story.
  AES-GCM is never mentioned.** Ticket 19's design therefore has zero conflict and zero inherited guidance.
- **Key source is never stated** in the recipes — only indirect pointers ("the DB encryption key", "the MCC
  variant uses KMS"). The standard is explicit, and is the clause ticket 19 overrides: §3.4 "Encryption Key
  Rotation" — *"The encryption key used to protect TOTP secrets **is stored in the database** and MUST be
  rotated at minimum once yearly."* Corroborated at §4.1 ("including retrieval of the encryption key"), §6.3
  (operator action: "Verify the encryption key is present in the database"), and §8 Glossary.
- **Two facts that strengthen ticket 19's override.** First, **Q13 of the standard's own Questions file
  treats key provisioning as an open integrator decision** — "in the application database, in a secrets
  manager (e.g., Vault), or **via another mechanism**" — while §3.4 tags DB storage as an enforced
  constraint. The corpus contradicts itself on whether this is a mandate or a choice. Second, **the yearly *Consolidated into the ADR routing (ticket 34): ADR-022 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-STD-049. Amend the table by ID, not this list.*
  rotation MUST is unimplementable as written**: re-encrypting every record under a new key requires knowing
  which key each ciphertext used, and §4.2 provides **no key-version column**. Ticket 19 added one; it turns
  out to fix a real defect rather than to add belt-and-braces. *Consolidated into the register (ticket 33): R-STD-039. Amend the table by ID, not this list.*
- Envelope sizing: `TOTP_KEY` must hold the GCM envelope, not the 20-byte plaintext — typically 12 + 20 + 16
  = 48 bytes — and the format must be identical across both tables, since Recipe 10 copies the blob verbatim.

### 7. Naming and schema drift — what ticket 12 must pick

The recipes use, verbatim: entity `OTPUserDetails` → `@Table(name = "OTP_USER_DETAILS")`;
`OTPUserDetailsRepository extends ListCrudRepository<OTPUserDetails, Long>`; entity
`PendingOTPUserDetails` → `@Table(name = "PENDING_TOTP")`; `PendingOTPUserDetailsRepository`;
`OTPRequestContext`; `TOTPAuthenticationProvider`; `TOTPProperties`; `TotpUtilities`;
`ConfirmTotpSetupActionCommand`. All three names this ticket suspected are confirmed.

**The file licenses our preference.** Its own tail note: the recipes use `OTP_USER_DETAILS` "preemptively
adopting the cloud standard's combined table name (which adds `otp`/`otpTtl` columns)", and
"Implementations that do not extend to the cloud standard **may prefer to name this table
`TOTP_USER_DETAILS`** to match the base standard's terminology." We are standalone and TOTP-only with no OTP
factor, so **take `TOTP_USER_DETAILS` and drop the `OTP`-prefixed class names** — the standard's name, with
the recipes' explicit blessing. Recipe 10's own rule text already mixes both names in a single bullet.

Two further drifts: **`ssoId`** is on every recipe entity and is conceded to be a design choice
("Implementors who do not use SSO federation need not include this column") — drop it. And the standard's
identity field is **`userId`** while every recipe method is `findByUsername`; Q6 leaves the identity key
open. Ticket 12 must pick one and be consistent in Flyway.

Columns as written:

- `OTP_USER_DETAILS`: `ID` `Long` (`@GeneratedValue(AUTO)`), `USERNAME` `String` (`unique`, `nullable=false`),
  `SSO_ID` `String` (`unique`), `TOTP_KEY` `byte[]` (nullable — ciphertext), `LAST_USED_COUNTER` `long`
  (`nullable=false`, field initialiser `-1L`).
- `PENDING_TOTP`: `ID`, `USERNAME` (`unique`, `nullable=false`), `SSO_ID` (`unique`), `TOTP_KEY` `byte[]`
  **`nullable=false`**.
- Standard §4.2 adds to the TOTP table, as enforced constraints: `failedAttempts` integer NOT NULL default 0,
  `lastFailedAttemptAt` timestamp nullable, `lockedAt` timestamp nullable, `lastUsedCounter` long NOT NULL
  **default −1**.

**The highest-value defect for ticket 12: `OTPUserDetails` as defined in Recipe 5 has no
`FAILED_ATTEMPTS`, `LAST_FAILED_ATTEMPT_AT` or `LOCKED_AT` — yet Recipe 12 calls getters and setters for all
three. Recipe 12 cannot compile against Recipe 5's entity.** The SQL names exist only on Recipe 1's
`PIN_USER_DETAILS` (`FAILED_ATTEMPTS` `int nullable=false`, `LAST_FAILED_ATTEMPT_AT` `Instant`, `LOCKED_AT`
`Instant`); we borrow them, on our own authority. Also: no `@Column` length on `TOTP_KEY` (defaults to *Consolidated into the register (ticket 33): R-STD-040. Amend the table by ID, not this list.*
`varbinary(255)` — size it for the GCM envelope), no `@Version`, no audit columns, and **no expiry column on
`PENDING_TOTP`** — there is no TTL, no reaper and no re-provision-overwrite rule anywhere in the corpus,
while `USERNAME` is `unique`, so a re-provision must delete or overwrite first.

One live bug to avoid inheriting: `lastUsedCounter`'s `-1L` field initialiser is **defeated by Lombok
`@Builder` with no `@Builder.Default`** — `OTPUserDetails.builder().username(u).build()` yields `0L`. Benign
here (real counters are ~5.8e7 and Recipe 10 overwrites immediately) but the documented "−1 means no code
accepted yet" invariant is silently false.

### 8. The algorithm — clean, and worth following exactly

Recipe 6, with §2.5 and §3.4 as enforced constraints: `counter = floor(epochSeconds / period)`, big-endian
8-byte counter, `Mac.getInstance("HmacSHA1")` with `new SecretKeySpec(key, "HmacSHA1")`, dynamic truncation
(`offset = hash[19] & 0xF`, 4 bytes, `& 0x7FFFFFFF`), `mod 10^digits`, zero-padded. Secret is 20 bytes from
`SecureRandom` (160 bits, RFC 6238). Comparison MUST be constant-time — `MessageDigest.isEqual()`, "not
`String.equals()`". Skew is ±1 period only: "Do NOT extend the window beyond ±1 period."

Recipe 9's only conformance vector is RFC 6238 Appendix B: secret `"12345678901234567890"` (US-ASCII bytes),
`generateTotpFromCounter(secret, 1L, 8) == "94287082"`. **It uses 8 digits while production is locked to 6**,
so the corpus ships **no 6-digit vector** — we write one. The same test also calls
`props::checkMinimumDigits`, a method that does not exist (Recipe 8 has `private void validate()`), and
`@PostConstruct` does not fire on a `new TOTPProperties()` anyway. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-012, T-MFA-013. Amend the table by ID, not this list.*

**A real behavioural defect the standard does not acknowledge.** §3.4 claims ±1 skew gives "±30 seconds of
tolerance". But §2.5's replay rule rejects any matched counter ≤ `lastUsedCounter`, so **after the first
successful verification the `counter-1` and `counter` windows are permanently dead for that principal** —
effective tolerance is **+1 window only**. An admin whose device runs ~25 s behind the server succeeds once
and then fails every attempt until server time catches up. Ticket 23 should state this plainly; it is a
support-call generator, not a theoretical edge. TOTP depends on the **server's** clock. *Consolidated into the register (ticket 33): R-STD-041. Amend the table by ID, not this list.*

### 9. Lockout — the third defective control this map has found

The standard binds: **10 failures**, **1-hour window**, tracked **per principal and per factor type**, in the
**database** ("In-memory counters are insufficient"), consequence **kicked out of the current session and
account locked until administrative review**, counter reset on successful verification. Backoff
(1→2→4→8…60 s) and IP throttling (20 req/min) are explicitly design choices, so ticket 09 is unconstrained
there — except for one knock-on MUST: §3.2 requires `429` responses to carry `Retry-After` in integer
seconds.

Defects ticket 09 and ticket 11 inherit:

- **"1-hour sliding window" is not sliding.** The code resets the counter only if the **single most recent**
  failure is older than an hour. An attacker pacing one attempt every 59 minutes accumulates 10 failures
  over ~9 hours and still locks the account; a legitimate user at 9 failures is still at 9 an hour later.
  Combined with "locked until administrative review", no self-unlock, and keying on username only with no IP
  dimension, **this is a denial-of-service lever against a known admin username.** This is the third
  standards defect on this map, after the lockout control with no observation window and the unreachable
  per-account rate limit. *Consolidated into the register (ticket 33): R-STD-042. Amend the table by ID, not this list.*
- **Empty-string amplifies it.** Recipe 12 checks only `requestTotp == null`, not `isEmpty()` (Recipe 10 uses
  `StringUtils.isEmpty`). An `X-TOTP: ""` falls through to the compare loop, fails the length pre-check, and
  **is counted as a failed attempt.** *Consolidated into the register (ticket 33): R-STD-043. Amend the table by ID, not this list.*
- **Three readings of the same counter:** "cumulative" (§3.4), "consecutive" (§3.3, §4.2),
  consecutive-since-last-success-or-1h-gap (the code). Pick one. *Consolidated into the register (ticket 33): R-STD-044. Amend the table by ID, not this list.*
- **"Kicked out of the current session" has no prescribed mechanism.** `MFA_Core` never mentions sessions —
  zero hits. With Spring Session JDBC this is ours:
  `FindByIndexNameSessionRepository.findByPrincipalName(...)` + delete, or the
  `SpringSessionBackedSessionRegistry` we already pinned for password reset. Ticket 08 inherits it.
- **No unlock path for TOTP**, as stated at the top. Ticket 11's ADMIN-only audited reset endpoint must also
  clear `lockedAt`, `failedAttempts` and `lastFailedAttemptAt` — the standard requires all three cleared
  together, "partial resets leave the account in an inconsistent state." *Consolidated into the register (ticket 33): R-STD-037. Amend the table by ID, not this list.*
- **Two locks, no precedence.** The factor lockout lives on the factor table but its consequence is
  account-level. So a password-lockout flag on the user table and `TOTP_USER_DETAILS.lockedAt` can both
  exist, with no prescribed precedence, no shared counter and no shared unlock. Ticket 09 must decide:
  single authoritative account-level lock that the TOTP provider writes to, or two locks with an explicit
  precedence rule and one admin unlock that clears both.
- **Enrolment confirmation is not rate limited at all** (Recipe 10 counts nothing).

### 10. Error contract — ticket 06 was right, and was prescribed

**Ticket 06's RFC 9457 + SCREAMING_SNAKE `code` decision is not an invention; it is what the recipes
already do.** Recipe 7 is headed "RFC 9457 ProblemDetail HTTP mapping" and does
`pd.setProperty("code", ex.getCode())` with exactly SCREAMING_SNAKE values:

| Exception | `code` | message → `detail` |
| --- | --- | --- |
| `InvalidPinException` | `INVALID_PIN` | `"PIN verification failed"` |
| `InvalidTotpException` | `INVALID_TOTP` | `"TOTP verification failed"` |
| `MFACodeNotFoundException(headerKey)` | `CODE_NOT_FOUND` | `"Required MFA header missing: " + headerKey` |
| `AccountLockedException` | `ACCOUNT_LOCKED` | `"Account locked due to too many failed attempts"` |

Titles: `"MFA Verification Failed"` / `"MFA Business Error"` / `"MFA System Error"`. Note the standard itself
never names `problem+json` — that vocabulary is recipe-only, which is why ticket 06 found zero hits in the
user pillar.

Four corrections to what this map believed:

1. **`412` is prescribed in prose and produced by no code in `MFA_Core`.** The handler is
   `ex instanceof MFACodeNotFoundException ? UNPROCESSABLE_ENTITY : UNAUTHORIZED` — so missing-header and
   not-enrolled → **422**, invalid code and account-locked → **401**. Eleven lines below, the Validation
   Rules demand **412** for both missing header and invalid factor. This code-versus-prose contradiction is
   the root of the corpus-wide confusion, and the frontend's `412 = invalid code` branch is built on the
   prose the reference implementation ignores. *Consolidated into the register (ticket 33): R-STD-045. Amend the table by ID, not this list.*
2. **The not-enrolled contradiction is two-way, not three-way.** §2.4 and the §2.6 flowchart say **412**
   ("TOTP key is null → missing-code exception"); §3.2's Org Standard says **422** with a mandated body.
   **`403` and `SETUP_REQUIRED` do not appear anywhere in `MFA_Core`** — the only `403` is
   `InsufficientPrivilegeException`, owned by `MFA_Critical_Transaction`, and it means insufficient
   privileges. This confirms ticket 06's finding and corrects ticket 19's "three contradictory statuses" to
   two plus a misattribution.
3. **The mandated English string is unproducible.** §3.2 requires that a 422 for incomplete enrolment carry
   `"detail": "User Details not found."`, and states "The frontend relies on this exact string." **No
   exception in `MFA_Core` ever yields it** — the not-enrolled path yields `"Required MFA header missing:
   X-TOTP"`. Ticket 19's decision to drop the string dependency is not just better practice; the corpus
   cannot satisfy its own rule. *Consolidated into the register (ticket 33): R-STD-046. Amend the table by ID, not this list.*
4. **`AccountLockedException` has no prescribed status** — §3.2 has no account-locked row, and the handler
   silently maps it to **401**, which our SPA would read as "session expired" and could turn into a
   re-login loop against a locked account. Ticket 06's enum needs a distinct code and status here.

Also note §3.2's claim that "Callers can distinguish these cases by HTTP status alone" is **false on its own
table**: 412 covers two causes and 422 covers two. The `code` extension is the only reliable discriminator —
which is precisely ticket 06's single-branch-point decision. *Consolidated into the register (ticket 33): R-STD-047. Amend the table by ID, not this list.*

### 11. Frontend — `MFA_Frontend/Standalone`

**Retained:** `MFATotpForm` + `useMFATotpForm` (the enrolment screen), `MFADialog` (repurposed as the
post-login challenge / confirmation code entry), the `InputOTP`/`InputOTPGroup`/`InputOTPSlot` primitives,
and `SetupMFA` as the `/settings/mfa` route, role-guarded to ADMIN. **Skipped:** everything PIN
(`MFAPinForm`, `useMFAPinForm`, Recipe 1), `MFAPrompt` + `useMfaPrompt`, and `useMFA` — which is the
critical-transaction orchestrator, so the hook that drives `MFADialog` is ours to write.

**Props, verbatim.** `MFATotpFormProps`: `showSpinner`, `imageSrc`, `handleSubmit`, `codeToVerify`,
`setCodeToVerify`, `isVerified`, `verifyTotp`, `keyExists`. `MFADialogProps`: `callback`, `mfaType`,
`description?`, `open`, `setOpen`, `onCancel`, `showSpinner`.

**The Generate-QR contradiction: confirmed, and resolved against §3.2.** §3.2 says the button "is always
enabled — re-provisioning does not require an OTP gate in standalone." §5 says "If `keyExists` is `true`, the
Generate QR Code button is disabled — re-provisioning is not permitted in standalone." Three witnesses side
with §5: Recipe 5's code (`disabled={keyExists}`), Recipe 5's validation rules, and the §2.3 sequence diagram
(enable only on "false — no key yet"). **§5 is correct; §3.2 is wrong**, and the root cause is visible in its
wording — "does not require an OTP gate" is the MCC-versus-standalone distinction mis-transcribed into a claim
about enablement. **The backend compels §5's reading**: §3.4's setup self-service guard forbids overwriting an
existing key without admin removal, so an always-enabled button fires a request the backend must reject, and
the only error handling is a toast saying `"Failed to generate QR code."` — misreporting an authorization
refusal as a generation failure. *Consolidated into the register (ticket 33): R-STD-048. Amend the table by ID, not this list.*

Consequence for ticket 14: with `disabled={keyExists}`, the `"Regenerate QR Code"` label and the warning
*"generates a new TOTP for you. The old TOTP can no longer be used."* are reachable **only** between a
successful generate and a successful verify — i.e. re-rolling an *unconfirmed* key. Both strings promise
post-enrolment re-provisioning the code forbids. **Do not carry them.**

**Object-URL lifecycle.** `URL.createObjectURL` **never appears in code** — it is prose only, located inside
`generateQrCode` in `lib/services`, whose body the corpus never shows. Response type is `arraybuffer`.
`revokeObjectURL` *is* shown, as a cleanup-only `useEffect` keyed `[imageSrc]`:

```ts
useEffect(() => { return () => { if (imageSrc) URL.revokeObjectURL(imageSrc); }; }, [imageSrc]);
```

The closure captures the *previous* `imageSrc`, so regenerate revokes the old URL after the new one commits.
**This breaks under React 19 StrictMode**: the synthetic unmount fires the cleanup and revokes the URL the
`<img>` still points at, with no re-creation (the effect body is empty). The QR renders broken in dev only.
The corpus targets "React 18+" and never mentions StrictMode. Ticket 14 should hold the `Blob` in state and
derive-plus-revoke in one effect with a re-create body. Two smaller leaks: no revoke on the error path, and
none when `isVerified` flips — so a live provisioning blob survives in memory after enrolment completes.

**Form library: the corpus does not use RHF+Zod where we keep it.** `MFADialog` and `MFATotpForm` both use
bare `useState` with no `<form>` element and no schema; RHF + `zodResolver` appear **only** in the PIN form we
drop. The recipes explicitly leave the choice open ("the choice of form library is up to the implementor").
Consequence: §7.1 prescribes a `codeToVerify` validation row — "Exactly 6 digits… On submit only", error
message **"Incorrect Code"** — that **no code in either file ever renders**, because there is no form and no
`FormMessage`. Moving to RHF+Zod *adds* the field-error surface the standard describes and the corpus never
built.

**Digit input.** `input-otp` with `maxLength={6}`, `pattern={REGEXP_ONLY_DIGITS}`, six `InputOTPSlot`s. Slot
count is hardcoded twice per instance plus `< 6` in each Verify gate — no constant. **Not prescribed at all:
autofocus, paste handling, auto-submit on completion.** Verify is always an affirmative click, disabled until
length 6. `MFADialog` clears its value on both Verify and Cancel; `MFATotpForm` never clears, on success or
failure — which contradicts §7.3's "Factor codes must be cleared from dialog local state on every Verify
click".

**Stack mismatch ticket 14 owns:** the snippets import `AlertDialog` from `@radix-ui/react-alert-dialog`, and
shadcn-on-Base-UI has no `input-otp`-based OTP primitive in that shape. **Both the OTP input and the dialog
need re-hosting, with zero corpus guidance.** Also `MFADialog` declares `setOpen`, threads it, and never uses
it — no `onOpenChange`, so no Esc or overlay dismissal, and no test covers it.

**Tests: this ticket's premise was wrong.** The recipes file contains **no test cases at all** — no Vitest, no
`describe`/`it`. Everything is in STD §8.1, and the distribution is the problem: 7 cases for `MFADialog` (one
vacuous — it asserts the absence of `ResendOtpButton`, a component defined nowhere, an MCC leak), 8 for
`MFAPinForm`, 11 for `useMFA`, 5 for `useMFAPinForm` — and **zero for `MFATotpForm`, zero for
`useMFATotpForm`, zero for `MFAPrompt`, zero for `SetupMFA`, zero for the service functions.** Of 31
prescribed cases, 24 sit on code we are not building, and the two components we *are* keeping have none. **So
ticket 16 inherits 6 usable cases and owns essentially the whole MFA frontend test surface** — including QR
generation, the object-URL lifecycle, the `keyExists` disable, and verify success/failure. Reusable fixtures
exist (`mockQrPngBytes = new Uint8Array([137, 80, 78, 71])`, `mockMfaCode = '123456'`, `mock412Error`,
`mock422MfaError`, `mock429Error`), but every fixture is flat `{ status, data }` while `handleMFAError` reads
`error.response.status` / `.data.detail` / `.headers["retry-after"]` — **every prescribed error test fails
against its own fixtures.** *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-FE-020, T-FE-021, T-FE-013, T-FE-022, T-FE-023, T-FE-024, T-FE-025, T-FE-003, T-FE-026, T-FE-027. Amend the table by ID, not this list.*

**API contract the SPA assumes** (paths from §6; the recipes give only method names, so these are assumptions
to reconcile, not facts): `GET /mfa/queryTotpKeyExists` → bare `boolean`; `GET /mfa/generateTotpQrCode` → PNG
byte array; `POST /mfa/confirmTotpSetup` → bare `boolean`. **`confirmTotpSetup` has three incompatible
transports** — §6 says JSON `{ code: string }`, §2.3's diagram says header `X-TOTP: code`, the recipes call
`TotpControllerService.verifyTotp(codeToVerify)` with no path or transport. Method name and endpoint name also
disagree. There are **three mutually inconsistent client layers**: §7.3 mandates all MFA calls go through
`@/lib/api`, which never appears in the recipes; the recipes use generated OpenAPI services, hand-written
`lib/services` functions, and a bare `apiClient`. Our TanStack Query layer replaces all three.

**Fail-open on every mount check.** Three unguarded `await`s with no try/catch. For TOTP, a failed
`queryTotpKeyExists` leaves `keyExists === false`, so **the Generate button stays enabled** — a failed status
probe presents an enabled provisioning control. §6 explicitly wants the opposite ("leave isSetupAllowed as
false" on error).

**No enrolled-state UI exists.** When `isVerified` flips, the QR and input simply disappear, leaving the
placeholder icon and a disabled button. No success panel, no "TOTP is enrolled" text, no enrolment date. And a
user arriving with `keyExists === true` sees a placeholder, a disabled button, and **zero explanatory text** —
a dead-end screen. Ticket 14 supplies all of it.

**Accessibility: the corpus prescribes essentially nothing.** A search across both files for `aria`, `role=`,
`focus`, `autoFocus`, `keyboard`, `tabIndex` returns **one** substantive hit — §7.2's `role="alert"` on the
invalid-code message, which the reference code then omits. Absent and therefore ours: focus-on-open, autofocus
to slot 0, focus return on close, focus handling for the Generate button that *unmounts* rather than disables
during the spinner, any label or `aria-describedby` on the OTP group, and any live region — every TOTP outcome
is toast-only, and toasts are declared out of scope, so a screen-reader user gets no in-page confirmation that
enrolment succeeded. **The functional gap matters most: `alt="QR Code"` is the only text alternative, and the
corpus never exposes the `otpauth://` URI or the Base32 secret, so a user who cannot scan a QR has no
prescribed path to enrol at all.** That is a blocker for an admin-only factor, not a nice-to-have.

**What the frontend corpus declines to own:** §1.2 — "Session management and auth-guard redirect are out of
scope." There is no login flow, no session-state model, and no concept of "which factors is this session
missing" anywhere. The whole model is that codes ride on business requests as `X-PIN`/`X-TOTP` and the dialog
exists only to harvest a code for a retry. **There is no standalone verification endpoint in the corpus** —
nothing validates a factor independently of a transaction. So ticket 19's eager post-login challenge, the
factor-state endpoint, and the verification endpoint are all net-new surface with no antecedent. Also net-new:
the ADMIN-only route guard — the corpus assumes `/settings/mfa` is reachable by any authenticated user, which
is exactly what made `MFAPrompt` pointless under our scope. The transferable part is §7.3's storage
prohibitions: factor codes never in `localStorage`/`sessionStorage`, no MFA state beyond the interaction, no
`console.log` of a code, and raw API error messages never surfaced — the last sitting awkwardly beside the
prescribed practice of branching on a raw `detail` string.

### 12. Logging — feeds ticket 13

Logging is in §3.3, not §3.4 (this ticket cited the wrong section). Required events, with fields verbatim:

| Event | Fields | Level |
| --- | --- | --- |
| Factor verification failure | `user.id`, `error.message`: `{no. attempts}` | `WARN` |
| Account locked (10+ in 1h) | `event.action`: `ATTEMPTS_EXCEEDED`, `user.id`, `error.message`: `{no. attempts}` | `ERROR` |
| TOTP setup confirmed | `event.action`: `TOTP_SETUP`, `user.id` | `INFO` |
| Access denied | `event.action`: `ACCESS_DENIED`, `error.message`: `{no. attempts}`, `user.id` | `ERROR` |

In-scope action values: `ATTEMPTS_EXCEEDED`, `TOTP_SETUP`, `ACCESS_DENIED`. `PIN_CREATED` and
`event.reason: PIN_ALREADY_EXISTS` are skipped.

Prohibited: raw PINs, plaintext TOTP secrets or decrypted key material, encryption keys or key ciphertext,
raw usernames or PII. **Note what is *not* prohibited: a submitted `X-TOTP` code value.** Ticket 19 asserted
that §3.4's "What NOT to Log" names OTP values and challenge responses explicitly — in `MFA_Core` it does
not. The prohibition covers *secrets*, not *submitted codes*. Since a submitted TOTP is single-use and
counter-bound after the replay rule, that silence is defensible, but we should close it by policy rather
than cite it.

Four gaps for ticket 13: **no success-path verification event** exists, yet §5.4 requires testing that audit
events carry *actor, timestamp, factor type, target resource, outcome* on **both success and failure paths* —
and §3.3 defines none of those fields. **No provisioning event** — the moment a secret is minted is unlogged.
**No admin-action events** for TOTP removal or unlock. And `error.message` is used to carry `{no. attempts}`
in three rows, stuffing a count into a string error field, with **no numeric attempt field defined anywhere**
— which collides directly with ticket 03's closed-enum schema work. For `ACCESS_DENIED`, an attempt count is
meaningless.

### 13. What the corpus sanctions, and the one deviation it does not

Better news than expected on four of ticket 19's six owed ADRs:

- **TOTP-only is explicitly sanctioned.** §4.1: "Which factor providers are active (PIN, TOTP, or both) is a
  **deployment decision** — not all providers need to be registered." And Q3 recommends "TOTP as the default
  unless there is a specific reason to prefer PIN or OTP." The PIN skip needs no ADR, just a citation. *Dropped in the ADR routing (ticket 34): superseded; see its routing §4.*
- **Skipping `MFA_Critical_Transaction` needs no justification.** `Appfw-Mfa-Standards/index.md` routes "MFA
  TOTP Standalone on Login" through exactly two directories, `MFA_Core` then `MFA_Frontend/Standalone`. CT is
  a different feature row. And **`MFA_Core` contains no AOP or annotation-interception requirement at all.**
  So ticket 19's ADR 1 is mis-framed: we are not rejecting a prescribed enforcement layer, we are building
  the login-path enforcement the router prescribes and the corpus never specifies. *Consolidated into the ADR routing (ticket 34): ADR-021 (attached amendment). Amend by ID, not this list.*
- **The `AuthenticationProvider` re-homing is licensed.** The recipes' own Implementation Notes: the request
  context and command patterns are "not mandated" and "Implementors may use any equivalent mechanism to pass
  header and identity data into providers and commands." *Consolidated into the ADR routing (ticket 34): ADR-021 (attached amendment). Amend by ID, not this list.*
- **The key-location override has cover from inside the corpus** — Q13 versus §3.4, above.

**The deviation that does need an ADR, and that ticket 19 missed.** §4.1 enforced constraint: "**On each
request** the enforcement layer iterates through all present providers and calls `authenticate()` on each in
sequence. A request is accepted only if every registered provider passes." That is a **per-request
possession-proof model**. Our session-scoped `FactorGrantedAuthority` is per-session, which is a weaker
assurance posture — and it is the deviation a security reviewer finds first. Ticket 19's mutation rule bounds
it at 10 minutes via `validDuration`, but its **admin-read rule is explicitly unbounded**, so admin reads sit
furthest from the standard. Ticket 23 should decide whether to bound the read rule too, and ticket 17 owes an
ADR naming §4.1 and arguing the trade. *Consolidated into the ADR routing (ticket 34): ADR-021. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-MFA-012. Amend the table by ID, not this list.*

### 14. Open integrator questions that survive our decisions

Of the 20 questions in `Base_Standalone_Application_Standard_Questions.md`, most are answered by the stack
baseline or ticket 19. Genuinely open and owned:

- **Q6 — which field identifies a user in the MFA store.** Ticket 12. Note the standard says `userId` and
  every recipe method says `findByUsername`.
- **Q10 — who may remove a TOTP key.** Ticket 11. The standard says "User Update Role"; the recipes use
  `hasRole('USERS_UPDATE')`. Compounded by the self-lockout case: the remover is themselves an MFA-gated
  admin, so a TOTP-locked sole admin has no path. This is what makes ticket 19's break-glass runbook line
  load-bearing rather than advisory.
- **Q11 — the issuer name.** Required with no default; §6.2: "application fails to start if absent or
  blank." Ticket 24, as configuration.
- **Q14 — key rotation schedule and the re-encryption procedure.** Still open. Our override removed the DB
  key but not the yearly-rotation MUST; ticket 19's key-version column makes rotation *possible* but nobody
  has written the procedure. Ticket 24 or a formal deferral in 17.
- **Q15 — is 10 the right threshold.** §3.4 tags 10 as enforced while Q15 invites us to change it; we cannot
  lower it without deviating. Ticket 09.
- **Q17 / Q18 — backoff strategy and IP-level throttling.** Pure design choices. Ticket 09, with no gateway
  in our topology so app-layer if adopted.

Three governance contradictions worth recording once: §3.4 tags the DB-resident key, the 10-failure
threshold, and lock-until-administrative-review as **enforced constraints**, while Q13, Q15 and Q16 present
all three as **integrator decisions**. Either the tags or the questions are wrong, and we cite whichever
supports the decision we have reasons for. *Consolidated into the register (ticket 33): R-STD-049. Amend the table by ID, not this list.*

### 15. Defects in the standards, for the deferral register

Beyond those above: §7 "Negative Requirements Summary" is not a usable checklist — **`NEG-REQ-03` does not
exist** (the sequence is 01, 02, 04, 05), **NEG-REQ-04 cites §2.4, which contains no such rule**, and §7 *Consolidated into the register (ticket 33): R-STD-050, R-STD-051. Amend the table by ID, not this list.*
omits the standard's own highest-severity negatives (storing a plaintext secret, logging key material,
in-memory lockout counters, verifying against an unconfirmed `PENDING_TOTP` secret). **`CON-06` is referenced *Consolidated into the register (ticket 33): R-STD-052. Amend the table by ID, not this list.*
in §8 and never defined** — the only `CON-` identifier in the file is `CON-01`. Digit length is stated three *Consolidated into the register (ticket 33): R-STD-053. Amend the table by ID, not this list.*
inconsistent ways: "exactly 6" (§3.4), "at least 6" (§8), "enforced at startup" (§3.1) versus configurable
with default 6 (§6.2), with no startup validation of `digits` specified anywhere. And `TotpUtilities` is *Consolidated into the register (ticket 33): R-STD-054. Amend the table by ID, not this list.*
declared **twice**, as two `public class` blocks with disjoint members — neither is complete, and the
implementer must merge them. *Consolidated into the register (ticket 33): R-STD-055. Amend the table by ID, not this list.*

### 16. Constraints handed on

- **06 (error envelope):** the four recipe `code` values (`INVALID_TOTP`, `CODE_NOT_FOUND`, `ACCOUNT_LOCKED`,
  plus `INTERNAL`) are prescribed SCREAMING_SNAKE and should be absorbed into the closed enum rather than
  re-invented; `ACCOUNT_LOCKED` needs a distinct status because the recipes map it to 401 alongside invalid
  code; the not-enrolled contradiction is 412-vs-422 only; the `"User Details not found."` string is *Consolidated into the register (ticket 33): R-STD-045. Amend the table by ID, not this list.*
  unproducible from the corpus, confirming the decision to drop it; `429` MUST carry `Retry-After` in integer *Consolidated into the register (ticket 33): R-STD-046. Amend the table by ID, not this list.*
  seconds.
- **08 (session and CSRF):** the mandated "kick the user out of the current session" on factor lockout has no
  prescribed mechanism — it lands on the `SpringSessionBackedSessionRegistry` already pinned for password
  reset. And §2.2's state-mutating **GET** for provisioning is CSRF-exempt under Spring Security's default
  safe-method allowlist; recommend POST as a recorded deviation.
- **09 (lockout and rate limiting):** threshold 10 / window 1 hour / DB-persisted / per-principal-per-factor
  are binding; the window is **not sliding** and is a DoS lever against a known admin username; empty-string *Consolidated into the register (ticket 33): R-STD-042. Amend the table by ID, not this list.*
  `X-TOTP` counts as a failure; enrolment confirmation counts nothing; "cumulative" vs "consecutive" must be *Consolidated into the register (ticket 33): R-STD-043. Amend the table by ID, not this list.*
  picked; two lock flags need a precedence rule and one unlock; backoff and IP throttling are free choices. *Consolidated into the register (ticket 33): R-STD-044. Amend the table by ID, not this list.*
- **11 (admin module):** the TOTP reset endpoint must also clear `lockedAt`, `failedAttempts` and
  `lastFailedAttemptAt` **together**; role is `USERS_UPDATE` per the corpus (Q10 leaves it open); there is no
  unlock path in the corpus at all, so this endpoint is the whole recovery story alongside the
  two-enrolled-admins invariant.
- **12 (data model):** take `TOTP_USER_DETAILS` over the recipes' `OTP_USER_DETAILS` — explicitly licensed for
  non-cloud implementations; drop `ssoId`; settle `userId` vs `username`; **add the three lockout columns the *Consolidated into the register (ticket 33): R-STD-040. Amend the table by ID, not this list.*
  recipes' own entity omits**; size `TOTP_KEY` for the AES-GCM envelope (~48 bytes, not the 20-byte
  plaintext); keep `lastUsedCounter NOT NULL DEFAULT -1` and use `@Builder.Default`; decide `PENDING_TOTP`
  expiry, since the corpus has no TTL, no reaper, and a `unique` username that forces overwrite-or-delete on
  re-provision.
- **13 (audit events):** `ATTEMPTS_EXCEEDED`, `TOTP_SETUP`, `ACCESS_DENIED` are the prescribed
  `event.action` values; there is **no success-verification event, no provisioning event, and no admin-action
  event** despite §5.4 requiring tests for success paths and for fields §3.3 never defines; `error.message`
  is misused to carry an attempt count with no numeric field defined — reconcile against ticket 03's closed
  enum; submitted TOTP codes are **not** on the prohibited list, so prohibit them ourselves.
- **14 (frontend):** §5/Recipe 5 wins the Generate-QR contradiction (`disabled={keyExists}`), and the
  "Regenerate" label plus the old-TOTP warning must not be carried; the prescribed object-URL cleanup breaks *Consolidated into the register (ticket 33): R-STD-048. Amend the table by ID, not this list.*
  under React 19 StrictMode; code entry is bare `useState` in the corpus, so RHF+Zod is ours and adds the
  "Incorrect Code" surface §7.1 describes but nothing renders; `input-otp` and Radix `AlertDialog` both need
  re-hosting on Base UI with no guidance; every mount check fails open; there is **no enrolled-state UI at
  all**; and **no manual-entry secret fallback exists**, which is a functional accessibility blocker for an
  admin-only factor.
- **16 (test plan):** 24 of the corpus's 31 prescribed frontend cases sit on code we are not building, the two
  components we keep have **zero**, one retained case is vacuous, and every error-path fixture is shaped
  wrong for the parser it feeds. The only backend conformance vector is RFC 6238 Appendix B at **8 digits**,
  so a 6-digit vector is ours. Add: the +1-window-only skew behaviour, the empty-string lockout lever, and *Consolidated into the register (ticket 33): R-STD-041. Amend the table by ID, not this list.*
  `PENDING_TOTP` preservation on failed confirmation.
- **17 (ADRs):** ADR 1 is mis-framed and should be rewritten — the router never put `MFA_Critical_Transaction`
  on our path and `MFA_Core` mandates no AOP. **Add an ADR for the per-request → per-session assurance change
  against §4.1**, which is the reviewable deviation. Drop any ADR for the PIN skip (§4.1 and Q3 sanction it).
  Note the Q13-versus-§3.4 governance contradiction as supporting material for the key-location ADR.
- **23 (enrolment, step-up, reset flows):** scope grows materially. The corpus has **no provisioning recipe**,
  so the endpoint, its method (GET per §2.2 versus POST for CSRF safety), status, content type, response
  shape, and the `PENDING_TOTP`-write ordering are all decisions, not extractions. Also ours: where the *Consolidated into the register (ticket 33): R-STD-036. Amend the table by ID, not this list.*
  submitted code arrives from on the confirmation endpoint, whether the admin-read rule gets a
  `validDuration` bound, the self-service overwrite guard Recipe 10's upsert does not enforce, and the
  +1-window-only skew behaviour as a user-facing fact.
- **24 (secrets and config):** `spring.eds.mfa.totp.issuer` is required with no default and fails startup if
  blank; we own the encryption-key property and the rotation procedure (Q14), which the standard mandates
  yearly and makes unimplementable by omitting a key-version column. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-014, T-MFA-015, T-MFA-016. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-STD-039. Amend the table by ID, not this list.*

### 17. Done-when check

An implementer can build enrolment confirmation and verification from §4, §5 and §7 above plus the algorithm
in §8. **They cannot build provisioning from this extraction, because the corpus does not contain it** — that
is the honest answer to the ticket, and it is now ticket 23's explicit scope rather than a surprise during
implementation. Every departure from a recipe is named in §16. *Consolidated into the register (ticket 33): R-STD-036. Amend the table by ID, not this list.*

## Amendment from ticket 10 (credential flows)

- This ticket's finding that the TOTP lockout has **no unlock path anywhere in the corpus** (Recipe 11 clears
  `PIN_USER_DETAILS` only) is why ticket 10 had to pin *which* counter a password reset clears. Redemption clears
  `failed_login_attempts`, `last_failed_at` and `locked_until` only, and never any TOTP counter or enrolment
  state — ASVS 6.4.3 (L2). A shared counter would have turned an email-gated reset into an MFA-lockout lift, and
  combined with the log leak would have let a log reader retry TOTP against an admin freely.
- Recorded as a positive assertion rather than an absence: redemption does not clear, reset, or re-issue TOTP
  enrolment. Ticket 19's admin-resets-admin path remains the only route, and the break-glass runbook line owed to
  ticket 25 is unchanged. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-LCK-017. Amend the table by ID, not this list.*
