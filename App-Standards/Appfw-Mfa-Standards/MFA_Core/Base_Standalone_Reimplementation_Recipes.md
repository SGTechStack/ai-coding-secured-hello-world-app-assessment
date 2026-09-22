# Unified MFA — Reimplementation Recipes

> Recipes show how to reimplement the base MFA factor behaviors (PIN and TOTP) described in the Unified MFA Application Standard using public libraries. They use Spring Boot, Spring Data JPA, and `javax.crypto`. All code targets JDK 17+.
>
> Cloud-specific recipes (OTP factor, KMS integration) are in [MCC_MFA_Reimplementation_Recipes.md](../MFA_MCC/MCC_MFA_Reimplementation_Recipes.md).
> Critical transaction enforcement recipes (AOP interceptor, role checks) are in [Critical_Transaction_MFA_Reimplementation_Recipes.md](../MFA_Critical_Transaction/Critical_Transaction_MFA_Reimplementation_Recipes.md).

---

## Recipe 1: PIN Verification with Bcrypt Storage

**Goal**: Verify a user-supplied PIN against a bcrypt-hashed PIN stored in `PIN_USER_DETAILS`.

**How it works in the reference**: `PINAuthenticationProvider.authenticate()` reads the `X-PIN` header value from `PINRequestContext`, looks up the stored hash, and calls `PasswordEncoder.matches()`.

### Step 1: Entity

```java
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "PIN_USER_DETAILS")
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PINUserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USERNAME", unique = true, nullable = false)
    private String username;

    @Column(name = "SSO_ID", unique = true)
    private String ssoId;

    @Column(name = "PIN")
    private String pin;  // bcrypt hash; null after admin removal

    @Column(name = "FAILED_ATTEMPTS", nullable = false)
    private int failedAttempts;

    @Column(name = "LAST_FAILED_ATTEMPT_AT")
    private Instant lastFailedAttemptAt;

    @Column(name = "LOCKED_AT")
    private Instant lockedAt;
}
```

### Step 2: Repository

```java
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;

public interface PINUserDetailsRepository extends ListCrudRepository<PINUserDetails, Long> {
    Optional<PINUserDetails> findByUsername(String username);
}
```

### Step 3: Provider

```java
import io.micrometer.common.util.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class PINAuthenticationProvider extends MultiFactorAuthenticationProvider {
    private final PINUserDetailsRepository pinRepository;
    private final MFATypeContainer mfaTypeContainer;

    public PINAuthenticationProvider(ObjectProvider<PasswordEncoder> passwordEncoder,
                                     PINRequestContext mfaRequestContext,
                                     PINUserDetailsRepository pinRepository,
                                     MFATypeContainer mfaTypeContainer) {
        super(passwordEncoder, mfaRequestContext);
        this.pinRepository = pinRepository;
        this.mfaTypeContainer = mfaTypeContainer;
    }

    @Override
    @Transactional
    public void authenticate() {
        String pin = ((PINRequestContext) mfaRequestContext).getRequestPin();

        if (StringUtils.isEmpty(pin)) {
            throw new MFACodeNotFoundException(mfaTypeContainer.getRequestKey("PIN"));
        }

        PINUserDetails userDetails = pinRepository.findByUsername(mfaRequestContext.getUsername())
            .orElseThrow(InvalidPinException::new);

        if (userDetails.getLockedAt() != null) {
            throw new AccountLockedException();
        }

        if (userDetails.getPin() == null || !passwordEncoder.getIfAvailable().matches(pin, userDetails.getPin())) {
            recordFailure(userDetails);
            throw new InvalidPinException();
        }

        resetFailureCount(userDetails);
    }

    private void recordFailure(PINUserDetails userDetails) {
        Instant now = Instant.now();
        // Reset window if last failure was more than 1 hour ago
        if (userDetails.getLastFailedAttemptAt() == null
                || userDetails.getLastFailedAttemptAt().isBefore(now.minus(1, ChronoUnit.HOURS))) {
            userDetails.setFailedAttempts(0);
        }
        userDetails.setFailedAttempts(userDetails.getFailedAttempts() + 1);
        userDetails.setLastFailedAttemptAt(now);
        if (userDetails.getFailedAttempts() >= 10) {
            userDetails.setLockedAt(now);
        }
        pinRepository.save(userDetails);
    }

    private void resetFailureCount(PINUserDetails userDetails) {
        userDetails.setFailedAttempts(0);
        userDetails.setLastFailedAttemptAt(null);
        pinRepository.save(userDetails);
    }
}
```

### Validation Rules

*   `PasswordEncoder.matches()` MUST be used — never raw equality comparison.
*   An empty `X-PIN` header → `MFACodeNotFoundException` with the header key as the message.
*   Missing user record → `InvalidPinException`.
*   A `null` PIN (after removal) → `InvalidPinException`. A null guard MUST precede the `matches()` call — `BCryptPasswordEncoder.matches(x, null)` throws `IllegalArgumentException` rather than returning false.
*   `lockedAt` non-null → `AccountLockedException` before PIN check. The lock is cleared only by admin action.
*   The failure window resets automatically when `lastFailedAttemptAt` is older than 1 hour; the counter resets to 0 before incrementing in that case.
*   All counter and lock mutations MUST be persisted within the same transaction as the authentication attempt.

---

## Recipe 2: PIN Setup (Self-Service Enrollment)

**Goal**: Allow a user to set their initial PIN (6 digits, no existing PIN) via `POST /mfa/resetPin`.

**How it works in the reference**: `ResetPinActionCommand` validates the PIN format and absence of an existing PIN, then bcrypt-hashes and persists.

### Implementation

```java
import java.util.Optional;
import java.util.regex.Pattern;

public class ResetPinActionCommand implements MFAActionCommand<Void> {
    public static final Pattern PIN_PATTERN = Pattern.compile("^\\d{6}$");

    private final String newPin;

    // injected via @Autowired setters or constructor
    private MFARequestContext mfaRequestContext;
    private PasswordEncoder passwordEncoder;
    private PINUserDetailsRepository pinRepository;

    public ResetPinActionCommand(String newPin) {
        this.newPin = newPin;
    }

    @Override
    public void validate() {
        if (StringUtils.isEmpty(newPin) || !PIN_PATTERN.matcher(newPin).matches()) {
            throw new UnableToResetPinException();
        }
        Optional<PINUserDetails> existing = pinRepository.findByUsername(mfaRequestContext.getUsername());
        if (existing.isPresent() && existing.get().getPin() != null) {
            throw new UnableToResetPinException();  // Already has a PIN — admin must remove first
        }
    }

    @Override
    public Void execute() {
        validate();
        String hashed = passwordEncoder.encode(newPin);

        Optional<PINUserDetails> existing = pinRepository.findByUsername(mfaRequestContext.getUsername());
        PINUserDetails record = existing.orElseGet(() ->
            PINUserDetails.builder().username(mfaRequestContext.getUsername()).build());
        record.setPin(hashed);
        pinRepository.save(record);
        return null;
    }
}
```

### Controller endpoint

```java
@PostMapping("/resetPin")
public ResponseEntity<Void> resetPin(@RequestHeader("X-PIN") String newPin) {
    ResetPinActionCommand cmd = new ResetPinActionCommand(newPin);
    commandExecutorService.executeCommand(cmd);
    return ResponseEntity.ok().build();
}
```

### Validation Rules

*   PIN must match `^\d{6}$` exactly — 6 numeric digits only.
*   Self-service setup is only allowed when the user has no PIN (`null` in DB).
*   Never log or return the hashed or plaintext PIN in the response.

---

## Recipe 3: PIN Removal (Admin Operation)

**Goal**: Allow an administrator to remove a user's PIN, permitting them to re-enroll. Requires `ROLE_USERS_UPDATE`.

**How it works in the reference**: `RemovePinActionCommand` finds the record and sets `pin = null`.

### Implementation

```java
public class RemovePinActionCommand implements MFAActionCommand<Void> {
    private final String username;
    private PINUserDetailsRepository pinRepository;

    public RemovePinActionCommand(String username) {
        this.username = username;
    }

    @Override
    public void validate() {}  // No validation; no-op

    @Override
    public Void execute() {
        pinRepository.findByUsername(username).ifPresent(record -> {
            record.setPin(null);
            pinRepository.save(record);
        });
        return null;
    }
}
```

### Controller endpoint

```java
@PatchMapping("/removePin")
@PreAuthorize("hasRole('USERS_UPDATE')")
public ResponseEntity<Void> removePin(@RequestParam String username) {
    RemovePinActionCommand cmd = new RemovePinActionCommand(username);
    commandExecutorService.executeCommand(cmd);
    return ResponseEntity.ok().build();
}
```

### Validation Rules

*   Endpoint MUST be guarded by `@PreAuthorize("hasRole('USERS_UPDATE')")`.
*   Setting PIN to `null` effectively disables it — `pin == null` allows setup again afterward.
*   If no record exists for the username, the command silently does nothing.

---

## Recipe 4: PIN Setup Prompt Detection

**Goal**: Return `true` if the current user should be prompted to set up a PIN. The base check is purely whether the user has no PIN configured — no role requirements are imposed at this level.

**How it works in the reference**: `QueryPinSetupAllowedCommand` checks for an absent or null PIN record. `PINQuerySetupPromptCommand` delegates to `QueryPinSetupAllowedCommand` — the prompt fires whenever the required factor is not yet configured. Extending standards (e.g. Critical Transaction) may add further conditions such as role checks.

### QueryPinSetupAllowedCommand (no PIN check only)

```java
public class QueryPinSetupAllowedCommand implements MFAQueryCommand<Boolean> {
    private MFARequestContext mfaRequestContext;
    private PINUserDetailsRepository pinRepository;

    @Override
    public Boolean execute() {
        Optional<PINUserDetails> record = pinRepository.findByUsername(mfaRequestContext.getUsername());
        return record.isEmpty() || record.get().getPin() == null;
    }
}
```

### PINQuerySetupPromptCommand (no PIN check)

```java
public class PINQuerySetupPromptCommand implements MFAQueryCommand<Boolean> {
    private EDSCommandPatternExecutorService executorService;

    @Override
    public Boolean execute() {
        return executorService.executeCommand(new QueryPinSetupAllowedCommand());
    }
}
```

### Controller endpoints

```java
@GetMapping("/setupAllowed")
public ResponseEntity<Boolean> setupAllowed() {
    return ResponseEntity.ok(commandExecutorService.executeCommand(new QueryPinSetupAllowedCommand()));
}

@GetMapping("/requireSetupPin")
public ResponseEntity<Boolean> requireSetupPin() {
    return ResponseEntity.ok(commandExecutorService.executeCommand(new PINQuerySetupPromptCommand()));
}
```

### Validation Rules

*   `setupAllowed` returns `true` whenever no PIN exists — it imposes no role requirement.
*   `requireSetupPin` fires whenever the required factor is not yet configured — no additional conditions are imposed at the base level.
*   Extending standards may wrap or replace `QueryPinSetupAllowedCommand` to add role or TOTP enrollment checks.

---

## Recipe 5: TOTP Utilities (Secret Generation + QR Code)

**Goal**: Generate a 20-byte TOTP secret and produce a QR code PNG from the `otpauth://` URI.

**How it works in the reference**: `TotpUtilities` provides static helpers for secret generation and QR code encoding. These are used by the provisioning command (see cloud recipes for the full provisioning flow with KMS encryption).

### TOTP User Details entity and repository

This entity holds **confirmed** TOTP secrets. It is only written to after the user successfully completes setup confirmation. It is extended with OTP columns (`otp`, `otpTtl`) in the Cloud standard.

```java
@Entity
@Table(name = "OTP_USER_DETAILS")
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class OTPUserDetails {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) @Column(name = "ID")
    private Long id;

    @Column(name = "USERNAME", unique = true, nullable = false)
    private String username;

    @Column(name = "SSO_ID", unique = true)
    private String ssoId;

    @Column(name = "TOTP_KEY")
    private byte[] totpKey;   // encrypted secret; null if TOTP not provisioned

    @Column(name = "LAST_USED_COUNTER", nullable = false)
    private long lastUsedCounter = -1L;  // -1 = no code accepted yet; updated on each successful TOTP verification
}

public interface OTPUserDetailsRepository extends ListCrudRepository<OTPUserDetails, Long> {
    Optional<OTPUserDetails> findByUsername(String username);
}
```

### Pending TOTP entity and repository

Temporary staging table. Written during TOTP provisioning; deleted and promoted to `OTP_USER_DETAILS` on successful setup confirmation.

```java
@Entity
@Table(name = "PENDING_TOTP")
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class PendingOTPUserDetails {
    @Id @GeneratedValue(strategy = GenerationType.AUTO) @Column(name = "ID")
    private Long id;

    @Column(name = "USERNAME", unique = true, nullable = false)
    private String username;

    @Column(name = "SSO_ID", unique = true)
    private String ssoId;

    @Column(name = "TOTP_KEY", nullable = false)
    private byte[] totpKey;   // encrypted secret pending confirmation
}

public interface PendingOTPUserDetailsRepository extends ListCrudRepository<PendingOTPUserDetails, Long> {
    Optional<PendingOTPUserDetails> findByUsername(String username);
    void deleteByUsername(String username);
}
```

### TotpUtilities

```java
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.commons.codec.binary.Base32;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class TotpUtilities {
    /** 20 bytes (160 bits) per RFC 6238 */
    public static byte[] generateSecretKey() {
        byte[] bytes = new byte[20];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /** otpauth:// URI per Google Authenticator Key URI Format */
    public static byte[] generateQRCode(byte[] key, String issuer, int period, int digits, String account)
            throws Exception {
        String base32Key = new Base32().encodeToString(key);
        String encIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encAccount = URLEncoder.encode(account, StandardCharsets.UTF_8).replace("+", "%20");
        String url = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&period=%d&digits=%d",
            encIssuer, encAccount, base32Key, encIssuer, period, digits);

        BitMatrix matrix = new MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        return out.toByteArray();
    }
}
```

### Validation Rules

*   Secret MUST be 20 bytes and generated with `SecureRandom`.
*   QR URI MUST use the `otpauth://totp/` scheme with Base32-encoded secret.
*   The plaintext secret bytes passed to `generateQRCode` are used only for QR generation — they must not be stored. Store only the encrypted form (see cloud recipes).

---

## Recipe 6: TOTP Computation (RFC 6238 HMAC-SHA1)

**Goal**: Compute a TOTP value from a decrypted secret byte array using the exact RFC 6238 algorithm.

**How it works in the reference**: `TotpUtilities.generateTotpFromSecretKey()` performs HMAC-SHA1 keyed hash → dynamic truncation → decimal modulo → zero-pad. The `TOTPAuthenticationProvider` (see cloud recipes) calls this after decrypting the stored secret.

### TOTP computation

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;

public class TotpUtilities {
    /**
     * Compute TOTP for the current time window. Delegates to generateTotpFromCounter.
     */
    public static String generateTotpFromSecretKey(byte[] key, int period, int digits) {
        long counter = (long) Math.floor((double) Instant.now().getEpochSecond() / period);
        return generateTotpFromCounter(key, counter, digits);
    }

    /**
     * Compute TOTP for an explicit counter value (RFC 6238 §5.3).
     * Used by providers to check adjacent time windows (counter ± 1) for clock-skew tolerance,
     * and by unit tests to produce deterministic output from a fixed counter.
     */
    public static String generateTotpFromCounter(byte[] key, long counter, int digits) {
        try {
            byte[] counterBytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                counterBytes[i] = (byte) (counter & 0xFF);
                counter >>= 8;
            }

            // HMAC-SHA1
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            // Dynamic truncation (RFC 6238 §5.3)
            int offset = hash[20 - 1] & 0xF;
            long truncated = 0;
            for (int i = 0; i < 4; i++) {
                truncated <<= 8;
                truncated |= (hash[offset + i] & 0xFF);
            }
            truncated &= 0x7FFFFFFF;
            truncated %= (long) Math.pow(10, digits);

            return String.format("%0" + digits + "d", truncated);
        } catch (Exception e) {
            throw new InternalSystemException(e.getMessage(), e);
        }
    }
}
```

### Validation Rules

*   TOTP computation MUST use the exact RFC 6238 algorithm: HMAC-SHA1 keyed hash → dynamic truncation → decimal modulo → zero-pad. Any deviation (e.g., HMAC-SHA256, non-standard truncation) will produce codes incompatible with standard authenticator apps.
*   Comparison MUST be constant-time (`MessageDigest.isEqual()`) — not `String.equals()`.
*   TOTP period MUST match the period configured in the QR code; mismatches cause persistent failures.
*   Accept ±1 period to tolerate clock drift between server and authenticator app.

---

## Recipe 7: Exception Hierarchy and HTTP Mapping

**Goal**: Define a typed exception hierarchy for MFA factor errors and map them to HTTP responses via `@RestControllerAdvice`.

### Exception hierarchy

```java
// Base MFA authentication exception
public abstract class MFAAuthenticationException extends RuntimeException {
    private final String code;
    public MFAAuthenticationException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}

public class InvalidPinException extends MFAAuthenticationException {
    public InvalidPinException() { super("INVALID_PIN", "PIN verification failed"); }
}
public class InvalidTotpException extends MFAAuthenticationException {
    public InvalidTotpException() { super("INVALID_TOTP", "TOTP verification failed"); }
}
public class MFACodeNotFoundException extends MFAAuthenticationException {
    public MFACodeNotFoundException(String headerKey) {
        super("CODE_NOT_FOUND", "Required MFA header missing: " + headerKey);
    }
}
public class AccountLockedException extends MFAAuthenticationException {
    public AccountLockedException() { super("ACCOUNT_LOCKED", "Account locked due to too many failed attempts"); }
}
```

### RFC 9457 ProblemDetail HTTP mapping

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class MFAExceptionHandler {

    @ExceptionHandler(MFAAuthenticationException.class)
    public ProblemDetail handleMFAException(MFAAuthenticationException ex) {
        HttpStatus status = ex instanceof MFACodeNotFoundException ? HttpStatus.UNPROCESSABLE_ENTITY
            : HttpStatus.UNAUTHORIZED;
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle("MFA Verification Failed");
        pd.setDetail(ex.getMessage());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler({UnableToResetPinException.class, UnableToGenerateSecretKeyException.class})
    public ProblemDetail handleBusiness(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("MFA Business Error");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(InternalSystemException.class)
    public ProblemDetail handleSystem(InternalSystemException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("MFA System Error");
        pd.setDetail("An internal error occurred. Please try again later.");
        return pd;
    }
}
```

### Validation Rules

*   Missing factor header → `412 Precondition Failed`.
*   Invalid/mismatched factor → `412 Precondition Failed`.
*   Business constraint violation (e.g., PIN already set) → `422 Unprocessable Entity`.
*   System failure → `500 Internal Server Error`. Do not expose internal details to the caller.
*   `InvalidOtpException` and `UnableToGenerateOTPException` live in the cloud recipes.
*   `InsufficientPrivilegeException` and its `403` mapping live in the critical transaction recipes.

---

## Recipe 8: Configuration Properties

**Goal**: Define type-safe `@ConfigurationProperties` classes for TOTP settings.

```java
@Data
@ConfigurationProperties(prefix = "spring.eds.mfa.totp")
public class TOTPProperties {
    private String issuer;
    private Integer period = 30;
    private Integer digits = 6;

    @PostConstruct
    private void validate() {
        if (issuer == null || issuer.isBlank()) {
            throw new InternalSystemException("spring.eds.mfa.totp.issuer must be configured — it cannot be absent or blank.",
                new IllegalArgumentException());
        }
        if (digits != 6) {
            throw new InternalSystemException("TOTP digit count must be exactly 6.",
                new IllegalArgumentException());
        }
    }
}
```

### Example `application.yaml`

```yaml
spring:
  eds:
    mfa:
      totp:
        issuer: "MyOrganisation"
        period: 30
        digits: 6
```

---

## Recipe 10: TOTP Setup Confirmation (Promote Pending to Confirmed)

**Goal**: Verify the user's first TOTP code against the pending secret. On success, promote the record from `PENDING_TOTP` to `OTP_USER_DETAILS` and delete the staging entry.

**How it works in the reference**: `ConfirmTotpSetupActionCommand.execute()` loads the pending record, decrypts the secret, runs the same 3-window RFC 6238 check as `TOTPAuthenticationProvider`, then performs the atomic promote-and-delete.

### Command

```java
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class ConfirmTotpSetupActionCommand implements ActionCommand<Void> {
    // injected: OTPRequestContext, PendingOTPUserDetailsRepository,
    //           OTPUserDetailsRepository, TOTPProperties, EncryptionService

    @Override
    @Transactional
    public Void execute() {
        String username = context.getUsername();
        String submittedTotp = context.getRequestTotp();

        if (StringUtils.isEmpty(submittedTotp)) {
            throw new MFACodeNotFoundException(mfaTypeContainer.getRequestKey("TOTP"));
        }

        PendingOTPUserDetails pending = pendingOtpRepository.findByUsername(username)
            .orElseThrow(() -> new MFACodeNotFoundException(mfaTypeContainer.getRequestKey("TOTP")));

        // Decrypt the pending secret
        byte[] secret = encryptionService.decrypt(pending.getTotpKey());

        int period = totpProperties.getPeriod();
        int digits = totpProperties.getDigits();

        // Verify against ±1 window using constant-time comparison.
        // Compute the base counter once to avoid time drift across the loop.
        long baseCounter = (long) Math.floor((double) Instant.now().getEpochSecond() / period);
        boolean valid = false;
        long matchedCounter = Long.MIN_VALUE;
        for (int skew = -1; skew <= 1; skew++) {
            String computed = TotpUtilities.generateTotpFromCounter(secret, baseCounter + skew, digits);
            if (constantTimeEquals(submittedTotp, computed)) {
                valid = true;
                matchedCounter = baseCounter + skew;
                break;
            }
        }

        if (!valid) {
            throw new InvalidTotpException();
        }

        // Promote: write confirmed record to main table.
        // Initialise lastUsedCounter to the matched counter so the confirmation code
        // cannot be immediately replayed as an authentication attempt (RFC 6238 §5.2).
        OTPUserDetails confirmed = otpRepository.findByUsername(username)
            .orElse(OTPUserDetails.builder().username(username).build());
        confirmed.setTotpKey(pending.getTotpKey());
        confirmed.setLastUsedCounter(matchedCounter);
        otpRepository.save(confirmed);

        // Delete staging record
        pendingOtpRepository.deleteByUsername(username);

        return null;
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
```

### Controller endpoint

```java
@PostMapping("/confirmTotpSetup")
public ResponseEntity<Void> confirmTotpSetup() {
    commandExecutorService.executeCommand(new ConfirmTotpSetupActionCommand());
    return ResponseEntity.ok().build();
}
```

### Validation Rules

*   A `PENDING_TOTP` record MUST exist for the user — if absent, throw a missing-code exception.
*   The submitted `X-TOTP` header MUST be non-empty — if absent, throw a missing-code exception.
*   Comparison MUST be constant-time (`MessageDigest.isEqual()`) — not `String.equals()`.
*   The `execute()` method MUST be annotated with `@Transactional`. If either the `OTP_USER_DETAILS` write or the `PENDING_TOTP` delete fails, both operations are rolled back — `PENDING_TOTP` is preserved and `TOTP_USER_DETAILS` is unchanged so the user can re-scan and retry.
*   `lastUsedCounter` MUST be set to the matched counter when promoting the record. This prevents the confirmation code from being replayed as a subsequent authentication attempt (RFC 6238 §5.2).
*   The plaintext secret is used only in memory during verification — it is never written to either table.

---

## Recipe 9: Deterministic TOTP Computation (Testing)

**Goal**: Write a unit test that validates TOTP computation by fixing the timestamp.

```java
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TotpUtilitiesTest {
    @Test
    void testTotpComputationWithKnownValues() {
        // RFC 6238 Appendix B test vector: secret = "12345678901234567890" (ASCII bytes), t=59, expected="94287082"
        // This vector uses 8 digits to match the RFC spec. generateTotpFromCounter accepts any digit width
        // for algorithm correctness testing; TOTPProperties enforces exactly 6 digits in production.
        byte[] secret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        int period = 30;
        int digits = 8;
        // counter = floor(59 / 30) = 1

        String result = TotpUtilities.generateTotpFromCounter(secret, 1L, digits);
        assertThat(result).isEqualTo("94287082");
    }

    @Test
    void testTotpRejectsDigitsNotEqualToSix() {
        // TOTPProperties @PostConstruct enforces exactly 6 digits (standard §3.4)
        TOTPProperties props = new TOTPProperties();
        props.setDigits(5);
        assertThatThrownBy(props::checkMinimumDigits)
            .isInstanceOf(InternalSystemException.class);
        props.setDigits(8);
        assertThatThrownBy(props::checkMinimumDigits)
            .isInstanceOf(InternalSystemException.class);
    }
}
```

---

## Recipe 12: TOTP Verification Provider (EncryptionService + Persisted Lockout)

**Goal**: Verify a submitted TOTP against the stored encrypted secret using the DB encryption key, with a persisted per-account lockout counter.

**How it works**: Decrypt the stored secret with `EncryptionService`, compute ±1 time windows, constant-time compare. Track failures in `OTPUserDetails`; lock after 10 within a 1-hour window.

### Provider

```java
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class TOTPAuthenticationProvider extends MultiFactorAuthenticationProvider {
    private final OTPUserDetailsRepository otpRepository;
    private final TOTPProperties totpProperties;
    private final EncryptionService encryptionService;
    private final MFATypeContainer mfaTypeContainer;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void authenticate() {
        OTPUserDetails userDetails = otpRepository.findByUsername(mfaRequestContext.getUsername())
            .orElseThrow(() -> new MFACodeNotFoundException(mfaTypeContainer.getRequestKey("TOTP")));

        String requestTotp = ((OTPRequestContext) mfaRequestContext).getRequestTotp();

        if (userDetails.getTotpKey() == null || requestTotp == null) {
            throw new MFACodeNotFoundException(mfaTypeContainer.getRequestKey("TOTP"));
        }

        if (userDetails.getLockedAt() != null) {
            throw new AccountLockedException();
        }

        byte[] secret = encryptionService.decrypt(userDetails.getTotpKey());
        int period = totpProperties.getPeriod();
        int digits = totpProperties.getDigits();
        long baseCounter = (long) Math.floor((double) Instant.now().getEpochSecond() / period);

        boolean valid = false;
        long matchedCounter = Long.MIN_VALUE;
        for (int skew = -1; skew <= 1; skew++) {
            String computed = TotpUtilities.generateTotpFromCounter(secret, baseCounter + skew, digits);
            if (constantTimeEquals(requestTotp, computed)) {
                valid = true;
                matchedCounter = baseCounter + skew;
                break;
            }
        }

        if (!valid) {
            recordFailure(userDetails);
            throw new InvalidTotpException();
        }

        // RFC 6238 §5.2 replay prevention: reject if this time-step was already consumed.
        if (matchedCounter <= userDetails.getLastUsedCounter()) {
            recordFailure(userDetails);
            throw new InvalidTotpException();
        }

        resetFailureCount(userDetails, matchedCounter);
    }

    private void recordFailure(OTPUserDetails userDetails) {
        Instant now = Instant.now();
        if (userDetails.getLastFailedAttemptAt() == null
                || userDetails.getLastFailedAttemptAt().isBefore(now.minus(1, ChronoUnit.HOURS))) {
            userDetails.setFailedAttempts(0);
        }
        userDetails.setFailedAttempts(userDetails.getFailedAttempts() + 1);
        userDetails.setLastFailedAttemptAt(now);
        if (userDetails.getFailedAttempts() >= 10) {
            userDetails.setLockedAt(now);
        }
        otpRepository.save(userDetails);
    }

    private void resetFailureCount(OTPUserDetails userDetails, long matchedCounter) {
        userDetails.setFailedAttempts(0);
        userDetails.setLastFailedAttemptAt(null);
        userDetails.setLastUsedCounter(matchedCounter);
        otpRepository.save(userDetails);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
```

### Validation Rules

*   The `authenticate()` method MUST use `@Transactional(propagation = Propagation.REQUIRES_NEW)`. This guarantees the factor verification state (counter updates, failure counts, lockout) commits independently from the outer business transaction. Without `REQUIRES_NEW`, if the Critical Transaction aspect and `@Transactional` share the same transaction, a business-method failure rolls back factor state — making valid codes appear replayable or consuming codes without the business operation succeeding.
*   `lockedAt` non-null → `AccountLockedException` before decryption or comparison.
*   A missing TOTP key or missing header → `MFACodeNotFoundException` (checked before the lockout check, as there is nothing to verify).
*   After a window match, check `matchedCounter <= userDetails.getLastUsedCounter()` — if so, record failure and throw `InvalidTotpException` (RFC 6238 §5.2 replay prevention).
*   On successful verification, `lastUsedCounter` is persisted via `resetFailureCount` so the same time-step cannot be reused.
*   Failure counter uses the same 1-hour sliding window as PIN (see Recipe 1).
*   All counter, lock, and `lastUsedCounter` mutations MUST be within the `REQUIRES_NEW` transactional boundary of the `authenticate()` method.
*   The MCC variant of this provider (Recipe C3) follows the same lockout pattern but uses KMS for decryption.

---

## Recipe 11: Admin Account Unlock

**Goal**: Allow an administrator to clear a locked account so the user may attempt verification again.

**How it works**: Clears `lockedAt`, `failedAttempts`, and `lastFailedAttemptAt` on the `PIN_USER_DETAILS` record.

### Implementation

```java
public class UnlockAccountActionCommand implements MFAActionCommand<Void> {
    private final String username;
    private final PINUserDetailsRepository pinRepository;

    @Override
    public void validate() {}  // No validation; no-op

    @Override
    public Void execute() {
        pinRepository.findByUsername(username).ifPresent(record -> {
            record.setLockedAt(null);
            record.setFailedAttempts(0);
            record.setLastFailedAttemptAt(null);
            pinRepository.save(record);
        });
        return null;
    }
}
```

### Controller endpoint

```java
@PostMapping("/mfa/admin/unlock/{username}")
@PreAuthorize("hasRole('USERS_UPDATE')")
public ResponseEntity<Void> unlockAccount(@PathVariable String username) {
    executorService.executeCommand(new UnlockAccountActionCommand(username, pinRepository));
    return ResponseEntity.ok().build();
}
```

### Validation Rules

*   Endpoint MUST be guarded by `@PreAuthorize("hasRole('USERS_UPDATE')")`.
*   If no record exists for the username, the command silently does nothing.
*   All three fields (`lockedAt`, `failedAttempts`, `lastFailedAttemptAt`) MUST be cleared together — partial resets leave the account in an inconsistent state.

---

## Recipe 13: MFA Type Container — Implementation and Startup Validation

**Goal**: Provide the `MFATypeContainer` implementation (with a `registeredTypes()` accessor needed for startup validation) and a `@PostConstruct` method in the configuration class that verifies the container's registered entries are non-blank and non-null before the application begins serving traffic.

**How it works**: `MFATypeContainerConfig` constructs the container in a `@Bean` method, then validates it in a `@PostConstruct`. Because `@Configuration` classes are CGLIB-proxied by Spring, calling `mfaTypeContainer()` from within `@PostConstruct` returns the already-constructed singleton bean.

### MFATypeContainer implementation

```java
import java.util.Map;
import java.util.Set;

public class MFATypeContainer {
    private final Map<String, MultiFactorAuthenticationProvider> providers;

    public MFATypeContainer(Map<String, MultiFactorAuthenticationProvider> providers) {
        this.providers = Map.copyOf(providers);
    }

    /** Returns the provider for the given mfaType, or null if not registered. */
    public MultiFactorAuthenticationProvider getAuthenticationProvider(String mfaType) {
        return providers.get(mfaType);
    }

    /** Returns all registered mfaType keys. Used by startup validation and error messages. */
    public Set<String> registeredTypes() {
        return providers.keySet();
    }
}
```

### MFATypeContainerConfig with startup validation

```java
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import java.util.Set;

@Configuration
public class MFATypeContainerConfig {

    @Bean
    public MFATypeContainer mfaTypeContainer(PINAuthenticationProvider pin,
                                              TOTPAuthenticationProvider totp) {
        return new MFATypeContainer(Map.of("PIN", pin, "TOTP", totp));
    }

    /**
     * Validates the MFATypeContainer at startup. Fails fast if any registered mfaType key is
     * blank or maps to a null provider — misconfiguration that would otherwise only surface as
     * a NullPointerException on the first runtime invocation.
     *
     * Calling mfaTypeContainer() here is safe: @Configuration CGLIB proxy ensures this returns
     * the singleton bean already constructed by the @Bean method above.
     */
    @PostConstruct
    public void validateMfaTypeContainer() {
        Set<String> keys = mfaTypeContainer().registeredTypes();
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                "[MFATypeContainerConfig] No mfaType providers registered. " +
                "At least one provider must be present at startup.");
        }
        keys.forEach(key -> {
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                    "[MFATypeContainerConfig] Blank mfaType key detected. " +
                    "Verify all Map.of() keys in MFATypeContainerConfig are non-blank strings.");
            }
            if (mfaTypeContainer().getAuthenticationProvider(key) == null) {
                throw new IllegalStateException(
                    "[MFATypeContainerConfig] Null provider for mfaType '" + key + "'. " +
                    "Verify the provider bean for this key is defined and not null.");
            }
        });
    }
}
```

### Validation Rules

*   `registeredTypes()` MUST be exposed on `MFATypeContainer` — it is required both for startup validation here and for annotation validation in the Critical Transaction aspect (see Recipe CT1).
*   The `@PostConstruct` in the config class catches registration-time mistakes (blank key in `Map.of(...)`, null provider bean). It does NOT catch annotation-side typos (e.g. `mfaType = "PILN"` on a method annotation) — that check is in the aspect (Recipe CT1).
*   `Map.copyOf()` in the constructor makes the container immutable; providers cannot be added or removed after startup.

---

## Implementation Notes: Additional Detail Beyond the Standard

The following patterns appear in these recipes but are not defined by the Base Standalone Application Standard. They are implementation choices made in the reference codebase.

**Request context abstraction (`MFARequestContext`, `PINRequestContext`, `OTPRequestContext`)**
The recipes wrap inbound request data (username, header values) in a context object per factor type. The standard specifies what data must be read (e.g., the `X-PIN` header) but does not prescribe this abstraction. Implementors may use any equivalent mechanism to pass header and identity data into providers and commands.

**Command pattern framework (`MFAActionCommand`, `MFAQueryCommand`, `EDSCommandPatternExecutorService`)**
PIN setup, removal, TOTP confirmation, and setup-prompt queries are expressed as command objects dispatched through a command executor service. The standard defines the required operations and their contracts; the command pattern is an architectural choice that is not mandated.

**`ssoId` column on all entities**
Every entity in these recipes carries both a `username` (mapped to the standard's `userId`) and an `ssoId` column. The standard's §4.2 schema defines only one `userId` identity field (design choice). The `ssoId` is a secondary lookup key for SSO-federated identity. Implementors who do not use SSO federation need not include this column.

**`MFATypeContainer` in base providers**
`PINAuthenticationProvider` and other base providers reference `MFATypeContainer` to resolve the expected header key string (e.g., `"X-PIN"`). The `MFATypeContainer` as a provider registry is defined in the Critical Transaction Standard (§4.2). The base standard only requires that a missing-code exception carry the expected header key — the mechanism for obtaining that key is an implementation detail.

**`InternalUserRepository` (Recipe 4)**
`PINQuerySetupPromptCommand` no longer reads a `firstLogin` flag or depends on a user-management domain. The setup prompt fires whenever the required factor is not yet configured — no external state is consulted.

**`UnableToResetPinException` (Recipe 2)**
The recipe raises this single exception for both PIN format validation failure and PIN-already-set. The base standard §2.4 distinguishes these as separate exception types (validation exception vs. PIN-already-set exception). Services that need to return different HTTP status codes or error codes for these two cases should define separate exception classes.

**Table naming: `OTP_USER_DETAILS` vs `TOTP_USER_DETAILS`**
The base standard §4.2 describes the confirmed TOTP entity as "TOTP User Details" without specifying a table name. These recipes use `OTP_USER_DETAILS` for this table from the base onwards, preemptively adopting the cloud standard's combined table name (which adds `otp`/`otpTtl` columns). Implementations that do not extend to the cloud standard may prefer to name this table `TOTP_USER_DETAILS` to match the base standard's terminology.
