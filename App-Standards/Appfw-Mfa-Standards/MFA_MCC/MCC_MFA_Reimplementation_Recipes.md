# Cloud MFA — Reimplementation Recipes

> Extends [Unified MFA Reimplementation Recipes](../MFA_Core/Base_Standalone_Reimplementation_Recipes.md) with cloud-specific recipes: the OTP factor (server-generated one-time password with MCNS delivery) and AWS KMS integration for TOTP secret encryption and decryption, as described in the Cloud MFA Application Standard.
>
> All code targets JDK 17+ with Spring Boot, AWS SDK v2, and `javax.crypto`.

---

## Recipe C1: KMS Service (Encrypt/Decrypt)

**Goal**: Wrap AWS KMS encrypt/decrypt operations using a single configured KMS key ARN.

**How it works in the reference**: `encryptData()` and `decryptData()` delegate to the AWS SDK v2 `KmsClient` using the key ARN from `KMSProperties`.

```java
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KMSService {
    private final KmsClient kmsClient;
    private final KMSProperties kmsProperties;

    public SdkBytes encryptData(SdkBytes plaintext) {
        try {
            return kmsClient.encrypt(EncryptRequest.builder()
                .keyId(kmsProperties.getKeyArn()).plaintext(plaintext).build()).ciphertextBlob();
        } catch (KmsException e) {
            throw new InternalSystemException(e.getMessage(), e);
        }
    }

    public SdkBytes decryptData(SdkBytes ciphertext) {
        try {
            return kmsClient.decrypt(DecryptRequest.builder()
                .ciphertextBlob(ciphertext).keyId(kmsProperties.getKeyArn()).build()).plaintext();
        } catch (KmsException e) {
            throw new InternalSystemException(e.getMessage(), e);
        }
    }
}
```

### Validation Rules

*   All `KmsException`s are wrapped as `InternalSystemException` — no internal retry.

---

## Recipe C2: TOTP Provisioning (Generate Secret → KMS Encrypt → QR Code)

**Goal**: Generate a 20-byte TOTP secret, encrypt it with AWS KMS, store the ciphertext, and return a QR code PNG for the user to scan.

**How it works in the reference**: `GenerateSecretKeyActionCommand.execute()` generates random bytes using `TotpUtilities.generateSecretKey()` (see base recipes), calls `KMSService.encryptData()`, stores the ciphertext, then calls `TotpUtilities.generateQRCode()` for the ZXing PNG.

### Provisioning command

```java
public class GenerateSecretKeyActionCommand implements ActionCommand<byte[]> {
    // injected: OTPRequestContext, PendingOTPUserDetailsRepository, TOTPProperties, KMSService

    @Override
    public byte[] execute() {
        validate();  // null-check issuer, period, digits, username

        byte[] secret = TotpUtilities.generateSecretKey();

        // Encrypt and persist to staging table — NOT the confirmed TOTP table
        byte[] encrypted = kmsService.encryptData(SdkBytes.fromByteArray(secret))
            .asByteArray();

        PendingOTPUserDetails pending = pendingOtpRepository.findByUsername(context.getUsername())
            .orElse(PendingOTPUserDetails.builder().username(context.getUsername()).build());
        pending.setTotpKey(encrypted);
        pendingOtpRepository.save(pending);

        // Generate and return QR code PNG
        try {
            return TotpUtilities.generateQRCode(secret, totpProperties.getIssuer(),
                totpProperties.getPeriod(), totpProperties.getDigits(), context.getUsername());
        } catch (Exception e) {
            throw new UnableToGenerateSecretKeyException();
        }
    }
}
```

### Controller endpoint

```java
@GetMapping("/generateTotpQrCode")
public ResponseEntity<byte[]> generateTotpQrCode() {
    byte[] qrCode = commandExecutorService.executeCommand(new GenerateSecretKeyActionCommand());
    return ResponseEntity.ok().body(qrCode);
}
```

### Validation Rules

*   KMS encryption MUST be called before any persistence of the secret.
*   The encrypted secret MUST be stored in `PENDING_TOTP`, not in `OTP_USER_DETAILS`. The confirmed table is only written to after successful setup confirmation (Recipe 10 in the Unified Recipes).
*   The plaintext secret bytes are used only for QR code generation — they are never stored or logged.

---

## Recipe C3: TOTP Verification Provider (KMS Decrypt → RFC 6238 Compute → Compare)

**Goal**: Decrypt the user's stored TOTP secret via KMS, compute the TOTP using RFC 6238, and compare against the submitted header value with clock-skew tolerance.

**How it works in the reference**: `TOTPAuthenticationProvider.authenticate()` decrypts the stored secret via `KMSService`, then checks three time windows (±1 period) using constant-time comparison.

```java
import java.security.MessageDigest;

@Component
public class TOTPAuthenticationProvider extends MultiFactorAuthenticationProvider {
    private final OTPUserDetailsRepository otpRepository;
    private final TOTPProperties totpProperties;
    private final KMSService kmsService;
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

        // Decrypt secret via KMS
        byte[] secret = kmsService.decryptData(SdkBytes.fromByteArray(userDetails.getTotpKey()))
            .asByteArray();

        int period = totpProperties.getPeriod();
        int digits = totpProperties.getDigits();

        // Compute base counter once to avoid time drift across the loop.
        long baseCounter = (long) Math.floor((double) Instant.now().getEpochSecond() / period);

        // Check current window and adjacent windows (±1 period) for clock-skew tolerance.
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

    /** Constant-time string comparison — prevents timing attacks */
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

*   The `authenticate()` method MUST use `@Transactional(propagation = Propagation.REQUIRES_NEW)`. This ensures the TOTP counter update (`lastUsedCounter`, failure counters) commits independently of any outer transaction wrapping the business operation. Without `REQUIRES_NEW`, if the business method fails (e.g., a database constraint violation during patient save), the entire transaction rolls back — including the counter update — making a valid TOTP code appear replayable on retry, or worse, consuming the code without the business operation succeeding.
*   Comparison MUST be constant-time (`MessageDigest.isEqual()`) — not `String.equals()`.
*   Accept ±1 period to tolerate clock drift between server and authenticator app.
*   `lockedAt` non-null → `AccountLockedException` before KMS decryption or comparison.
*   After a window match, check `matchedCounter <= userDetails.getLastUsedCounter()` — if so, record failure and throw `InvalidTotpException` (RFC 6238 §5.2 replay prevention).
*   On successful verification, `lastUsedCounter` is persisted via `resetFailureCount` so the same time-step cannot be reused.
*   Failure counter uses the same 1-hour sliding window as PIN (see base Recipe 1).
*   All counter, lock, and `lastUsedCounter` mutations MUST be within the `REQUIRES_NEW` transactional boundary of the `authenticate()` method.

---

## Recipe C4: KMS and OTP Configuration Properties

**Goal**: Define the `@ConfigurationProperties` classes for AWS KMS (TOTP secret encryption) and OTP delivery, and configure them per environment.

```java
@ConfigurationProperties(prefix = "spring.security.eds.mcc.mfa.kms")
public class KMSProperties {
    @Setter private String region = "ap-southeast-1";
    @Getter private Region awsRegion = Region.of(region);
    @Getter @Setter private String keyArn;
    @Getter @Setter private String endpointOverride;   // set for LocalStack (dev); null = real AWS
}
```

```java
@ConfigurationProperties(prefix = "spring.security.eds.mcc.mfa.otp")
public record OtpProperties(
    String templateKey,  // MCNS logical template key; MCNS resolves it to the environment's onboarded id
    String issuer         // display name rendered as the OTP message "issuer"
) {}
```

### Configuration

**Dev profile (`application-dev-mcc.yml`)** — committed defaults point at the local LocalStack and mock services:

```yaml
# File: backend/src/main/resources/application-dev-mcc.yml   (MFA slice)
spring:
  security:
    eds:
      mcc:
        mfa:
          otp:
            template-key: otp-email     # references spring.security.eds.mcc.mcns.templates.otp-email
            issuer: MyApp
          kms:
            # TOTP secret encryption. Dev: LocalStack :4566 with the alias provisioned by Recipe C5.
            key-arn: arn:aws:kms:us-east-1:000000000000:alias/myapp-dev-key
            region: us-east-1
            endpoint-override: http://localhost:4566
```

The OTP factor is delivered over **MCNS**. MFA does **not** own the onboarded template id — it references the MCNS **logical template key** (`otp-email`) and supplies the `issuer` template value. The environment-specific onboarded id and its `templateValues` allowlist live in the MCNS template policy (`spring.security.eds.mcc.mcns.templates.otp-email`, injected per environment); MCNS transport config (`spring.security.eds.mcc.mcns.url` / `sender-id`) and outbound M2M auth are owned by the [MCNS standard](../../Appfw-Mcc-Standards/Appfw-Mcns-Standards/MCNS_Core/MCNS_Core_Standard.md) and the Shared Auth Foundation respectively.

**SIT profile (`application-sit.yml`)** — deployed-environment configuration (the SIT YAML, the SIT environment variables, and `.env.sit.example`) is broken out into its own recipe. See [Recipe C8: Deployed Environment Profile Configuration (SIT and Beyond)](#recipe-c8-deployed-environment-profile-configuration-sit-and-beyond).

### KmsClient bean configuration

The `KmsClient` bean must supply static dummy credentials when `endpointOverride` is set (LocalStack), because the AWS SDK v2 default credential chain fails in local dev (no IAM role, no `~/.aws/credentials`). In SIT/prod, `endpointOverride` is null so the default credential chain resolves normally (IAM role from the platform).

```java
@Configuration
@EnableConfigurationProperties(KMSProperties.class)
public class KmsConfiguration {

    @Bean
    public KmsClient kmsClient(KMSProperties kmsProperties) {
        KmsClientBuilder builder = KmsClient.builder()
                .region(Region.of(kmsProperties.getRegion()));

        if (kmsProperties.getEndpointOverride() != null && !kmsProperties.getEndpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(kmsProperties.getEndpointOverride()));
            // LocalStack accepts any credentials — provide dummy values so the SDK
            // does not fail during credential resolution in local dev.
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")));
        }

        return builder.build();
    }
}
```

> **Why?** Without explicit credentials, the AWS SDK's `DefaultCredentialsProvider` searches `~/.aws/credentials`, env vars, and IMDS — all absent in local dev. LocalStack doesn't validate credentials, but the SDK still requires them to be *present*. The static dummy credentials are only set when `endpointOverride` points at LocalStack; in SIT/prod (no override) the standard IAM credential chain is used.

---

## Recipe C5: OTP Generation (Generate → Hash → Store with TTL)

**Goal**: Generate a short cryptographically random numeric OTP, hash it, persist with a TTL, and deliver it via the cloud messaging service (MCNS).

**How it works in the reference**: `GenerateOTPActionCommand.execute()` uses `SecureRandom.nextInt(10^digits)` formatted with `String.format("%0" + digits + "d", ...)`. TTL is `Instant.now().plus(ttl, MINUTES)`. The OTP is delivered via MCNS after being stored.

### Full OTPUserDetails entity (base + OTP columns)

The base entity defined in Unified Recipe 5 contains only `totpKey`. This cloud extension adds the `otp` and `otpTtl` columns to the same physical table.

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

    @Column(name = "OTP")
    private String otp;       // bcrypt hash; null after successful verification

    @Column(name = "OTP_TTL")
    private Timestamp otpTtl; // expiry; null after successful verification

    @Column(name = "OTP_ATTEMPT_COUNT", nullable = false)
    private int otpAttemptCount;  // failed hash-match attempts against current OTP; reset on new OTP or success

    @Column(name = "FAILED_ATTEMPTS", nullable = false)
    private int failedAttempts;   // consecutive failed TOTP verifications in current 1-hour window

    @Column(name = "LAST_FAILED_ATTEMPT_AT")
    private Instant lastFailedAttemptAt;

    @Column(name = "LOCKED_AT")
    private Instant lockedAt;     // non-null means account locked; cleared by admin
}
```

### Generation command

```java
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class GenerateOTPActionCommand implements ActionCommand<Void> {
    // injected dependencies: OTPRequestContext, OTPUserDetailsRepository, OTPProperties, PasswordEncoder

    @Override
    public Void execute() {
        validate();  // checks digits, ttl, and username are non-null

        int digits = otpProperties.getDigits();
        int ttl = otpProperties.getTtl();

        SecureRandom rng = new SecureRandom();
        String otp = String.format("%0" + digits + "d",
            rng.nextInt((int) Math.pow(10, digits)));

        String hashed = passwordEncoder.getIfAvailable().encode(otp);
        Timestamp expiry = Timestamp.from(Instant.now().plus(ttl, ChronoUnit.MINUTES));

        OTPUserDetails record = otpRepository.findByUsername(context.getUsername())
            .orElse(OTPUserDetails.builder().username(context.getUsername()).build());
        record.setOtp(hashed);
        record.setOtpTtl(expiry);
        record.setOtpAttemptCount(0);  // reset on new OTP — gives a fresh 3-attempt window
        otpRepository.save(record);

        // Deliver plaintext OTP via MCNS — it must not be returned in the response body. Refer to the MCC Notification and Communication Service (MCNS) — Core Integration Standard on how to integrate MCNS messaging service.
        mcnsService.send(context.getUsername(), otp);
        return null;
    }
}
```

### Controller endpoint

```java
@GetMapping("/generateOtp")
public ResponseEntity<Void> generateOtp() {
    commandExecutorService.executeCommand(new GenerateOTPActionCommand());
    return ResponseEntity.ok().build();
}
```

### Validation Rules

*   `digits` and `ttl` must be non-null at execution time (validated in `validate()`).
*   `SecureRandom` MUST be used — `Math.random()` is not cryptographically secure.
*   The zero-padded format (`%0Nd`) ensures OTPs with leading zeros are preserved as strings.
*   The plaintext OTP must be delivered to the user via MCNS — the command must not return it in a response body.

---

## Recipe C6: OTP Verification with TTL

**Goal**: Verify a submitted OTP against the stored hash and TTL. Auto-regenerate a new OTP only if the stored OTP is expired. Reject immediately if the header is missing. Clear storage on success.

**How it works in the reference**: `OTPAuthenticationProvider.authenticate()` — throws if empty header; checks expired flag; if `expired` generates a new OTP then throws; verifies hash; on success deletes.

### Exception (add to cloud exception handler)

```java
public class InvalidOtpException extends MFAAuthenticationException {
    public InvalidOtpException() { super("INVALID_OTP", "OTP verification failed"); }
}
```

Add to `MFAExceptionHandler` (alongside `UnableToGenerateSecretKeyException`):

```java
@ExceptionHandler({UnableToResetPinException.class, UnableToGenerateOTPException.class,
                    UnableToGenerateSecretKeyException.class})
public ProblemDetail handleBusiness(RuntimeException ex) { /* ... */ }
```

### Provider implementation

```java
import org.apache.commons.lang3.StringUtils;
import java.time.Instant;

@Component
public class OTPAuthenticationProvider extends MultiFactorAuthenticationProvider {
    private final OTPUserDetailsRepository otpRepository;
    private final OTPProperties otpProperties;
    private final MFATypeContainer mfaTypeContainer;
    private final EDSCommandPatternExecutorService executorService;

    @Override
    @Transactional
    public void authenticate() {
        String username = mfaRequestContext.getUsername();

        OTPUserDetails userDetails = otpRepository.findByUsername(username)
            .orElseThrow(() -> new MFACodeNotFoundException(mfaTypeContainer.getRequestKey("OTP")));

        String submittedOtp = ((OTPRequestContext) mfaRequestContext).getRequestOtp();

        // Reject immediately if no header was provided — no OTP generation
        if (StringUtils.isEmpty(submittedOtp)) {
            throw new MFACodeNotFoundException(mfaTypeContainer.getRequestKey("OTP"));
        }

        boolean expired = userDetails.getOtpTtl() != null
            && Instant.now().isAfter(userDetails.getOtpTtl().toInstant());

        // Auto-generate a new OTP if expired (side effect); resets otpAttemptCount
        if (expired) {
            executorService.executeCommand(new GenerateOTPActionCommand());
        }

        // Reject if expired
        if (expired) {
            throw new InvalidOtpException();
        }

        // Reject if attempt limit reached — invalidate OTP so user must request a new one
        if (userDetails.getOtpAttemptCount() >= 3) {
            userDetails.setOtp(null);
            userDetails.setOtpTtl(null);
            userDetails.setOtpAttemptCount(0);
            otpRepository.save(userDetails);
            throw new InvalidOtpException();
        }

        // Reject if hash mismatch — increment attempt counter
        if (!passwordEncoder.getIfAvailable().matches(submittedOtp, userDetails.getOtp())) {
            userDetails.setOtpAttemptCount(userDetails.getOtpAttemptCount() + 1);
            otpRepository.save(userDetails);
            throw new InvalidOtpException();
        }

        // Success: clear OTP — single-use
        userDetails.setOtp(null);
        userDetails.setOtpTtl(null);
        userDetails.setOtpAttemptCount(0);
        otpRepository.save(userDetails);
    }
}
```

### OTP configuration properties

```java
@Data
@ConfigurationProperties(prefix = "spring.eds.mfa.otp")
public class OTPProperties {
    private Integer digits = 6;
    /** OTP lifetime in minutes */
    private Integer ttl = 10;
}
```

```yaml
spring:
  eds:
    mfa:
      otp:
        digits: 6
        ttl: 10
```

### Validation Rules

*   `PasswordEncoder.matches()` MUST be used for comparison (bcrypt).
*   On success, `otp`, `otpTtl`, and `otpAttemptCount` MUST be cleared and saved — OTP is single-use.
*   When the header is absent AND the stored OTP is expired, both conditions trigger auto-generation independently (the reference checks both as an OR).
*   The auto-generated OTP is a side effect — it is stored and delivered via MCNS, not returned in the response.
*   The attempt limit check (≥ 3) MUST occur before `PasswordEncoder.matches()`. On the 4th attempt, the OTP is invalidated and `otpAttemptCount` reset to 0; the user must request a new OTP.
*   Expiry rejection does NOT increment `otpAttemptCount` — expiry is independent of hash-match failures.
*   `otpAttemptCount` is reset to 0 by `GenerateOTPActionCommand` when a new OTP is issued, so a fresh OTP always starts with a clean counter.

---

## Implementation Notes: Additional Detail Beyond the Standard

The following patterns appear in these recipes but are not defined by the Cloud MFA Application Standard. They are implementation choices made in the reference codebase.

**`UnableToGenerateSecretKeyException` and `UnableToGenerateOTPException`**
These exception classes are referenced in the exception handler stubs but are not formally named or scoped by the cloud standard. The standard requires that QR code and OTP generation failures surface as appropriate HTTP error responses; the specific exception names are an implementation detail.

**Combined `OTP_USER_DETAILS` table for TOTP and OTP**
The cloud standard (§4.2) places OTP state in `OTP_USER_DETAILS` and states the Cloud MFA Standard extends the base TOTP table with `otp`/`otpTtl` columns. These recipes reflect that by using a single `OTPUserDetails` entity for both TOTP key and OTP columns. This is the intended cloud schema; services that implement TOTP only (no OTP factor) may omit the `otp` and `otpTtl` columns.

## Recipe C7: LocalStack Dev KMS Key Provisioning

**Goal**: Provision the KMS key this standard depends on for local development, without pushing key naming or lifecycle into the bootstrap standard.

**Ownership**: The [MCC Project Bootstrap Application Standard](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md) owns the LocalStack **container** and the **init-script hook** (`scripts/localstack-init/` mounted into `/etc/localstack/init/ready.d/`). This standard owns **provisioning its own KMS key** by dropping an init script into that hook. Bootstrap does not create or name the key.

## When this applies

Only when the Cloud MFA profile encrypts TOTP secrets with AWS KMS and runs locally against LocalStack.

## Step 1: Add the key-creation init script

Drop a numbered script into the bootstrap-provided hook. Use the alias chosen in question **Q11a**.

```bash
#!/usr/bin/env bash
# File: scripts/localstack-init/03-create-mfa-kms-key.sh
# Provisions the Cloud MFA TOTP-encryption KMS key in LocalStack (dev only).
# Key alias comes from MFA MCC question Q11a.
set -euo pipefail

KEY_ALIAS="${MFA_KMS_ALIAS:-alias/myapp-dev-key}"

echo "Creating KMS key for TOTP secret encryption: ${KEY_ALIAS}"
KEY_ID=$(awslocal kms create-key --description "myapp-dev-key" \
  --query 'KeyMetadata.KeyId' --output text)
awslocal kms create-alias --alias-name "${KEY_ALIAS}" --target-key-id "$KEY_ID"

echo "MFA KMS key provisioning complete."
```

## Step 2: Point the KMS client at LocalStack in dev

For local development the AWS KMS client must target the LocalStack endpoint and resolve the key by its alias. Keep the alias in the script and the profile config in lockstep (`spring.eds.aws`), so `KMSService` (Recipe C1) targets the key this script creates.

## Step 3: Deployed environments

In SIT/UAT/PROD the KMS key is provisioned by the platform (Terraform/CloudFormation/console) with the appropriate key policy, least-privilege access, and the rotation schedule from Q13. This recipe is local-development only — do not run `awslocal` against real AWS.

## Verification

```bash
# Inside the localstack container / with awslocal configured
awslocal kms list-aliases
# Expect the alias from Q11a to be listed.
```

> The key alias is a mock, dev-only value decided in Q11a. This recipe keeps its creation and naming with the standard that consumes it, so bootstrap stays agnostic to MFA specifics.

---

## Recipe C8: Deployed Environment Profile Configuration (SIT and Beyond)

**Goal**: Configure profile-specific environment variables for deployed environments (SIT/UAT/Prod) using `.env.sit` and `application-sit.yml`.

Per the [Bootstrap Profile Configuration Contract](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md#36-profile-configuration-contract), `keyArn`, `region`, and the OTP `issuer` are environment-specific and MUST NOT live as hardcoded literals in `application.yml`.

### Step 1: Declare Profile Overrides in `application-sit.yml`

```yaml
# File: backend/src/main/resources/application-sit.yml   (MFA slice)
# Activated via: SPRING_PROFILES_ACTIVE=sit
# Secrets use Spring default syntax: ${ENV_VAR:default}
# Real deployments override via platform-injected env vars.
spring:
  security:
    eds:
      mcc:
        mfa:
          otp:
            # The onboarded template id is owned by the MCNS template policy
            # (templates.otp-email.template-id, injected via MCNS_OTP_TEMPLATE_ID) — not configured here.
            issuer: ${MFA_OTP_ISSUER:MyApp}
          kms:
            key-arn: ${KMS_KEY_ARN:arn:aws:kms:ap-southeast-1:000000000000:key/mfa-totp-key}
            region: ${KMS_REGION:ap-southeast-1}
```

| Env var | Purpose | Example value |
|:---|:---|:---|
| `MFA_OTP_ISSUER` | OTP issuer name | `MyApp` |
| `KMS_KEY_ARN` | Platform-provisioned KMS key ARN | *(from platform)* |
| `KMS_REGION` | AWS region for KMS | `ap-southeast-1` |

> The onboarded OTP template id is **not** an MFA env var. It is owned by the MCNS template policy (`spring.security.eds.mcc.mcns.templates.otp-email.template-id`) and injected via `MCNS_OTP_TEMPLATE_ID` — MFA only references the logical key `otp-email`.

Switching between local dev and SIT is done purely by selecting the Spring profile (`SPRING_PROFILES_ACTIVE=dev-mcc` or `sit`); no additional per-target selection step is needed.

### Step 2: Create `.env.sit.example`

Provide a `.env.sit.example` template at the repository root to guide deployment engineers. The developer copies this to `.env.sit` (which is gitignored) for local verification.

```env
# Cloud MFA SIT Configuration
MFA_OTP_ISSUER=MyApp
KMS_KEY_ARN=<platform-provisioned KMS key ARN>
KMS_REGION=ap-southeast-1
```
