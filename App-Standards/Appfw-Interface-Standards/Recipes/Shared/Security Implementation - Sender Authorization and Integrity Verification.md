# Security Implementation: Sender Authorization and Integrity Verification

## 1. Introduction

The starter provides two built-in security mechanisms for inbound files: **integrity verification** via SHA-256 hash and RSA digital signature validation, and **access control** via `@MAGEntity` sender classification and security/sensitivity labels. Together they enforce the standard's requirement that every inbound batch is authenticated, its integrity confirmed, and its handling governed by its classification before any processing begins.

By the end of this recipe, you will:
- Enable and configure hash and signature validation for inbound files
- Understand how the starter maps integrity failures to specific ACK codes and statuses
- Classify files with `SecurityClassification` and `SensitivityClassification`
- Understand what sender authorization the starter enforces and what you must implement at the transport layer
- Store credentials and keys securely using an external secrets manager

## 2. Prerequisites

- `interface-management-inbound-starter` on the classpath
- An RSA key pair (if digital signature validation is required)
- Access to an external secrets manager (AWS Secrets Manager, HashiCorp Vault, or Azure Key Vault) for key storage
- A registered `@InboundMAGEntity` DTO (see [Inbound Batch Reception and Validation](../Inbound/Inbound%20Batch%20Reception%20and%20Validation.md))

---

## 3. Steps

### Step 1: Understand the Two Layers of Integrity Verification

The starter supports two complementary integrity checks, both controlled by `app.interface.signature.*` properties:

**Layer 1 — Hash verification (SHA-256):**
- The sender generates a SHA-256 hash of the data file and writes it to a companion file with extension `.sha3` (configurable)
- On receipt, the starter recomputes the hash of the received data file and compares it against the companion file
- A mismatch sets status `INVALID_HASH_FILE` and generates ACK `460 CHECKSUM_EXCEPTION`

> [!IMPORTANT]
> **Memory-Efficient Stream-Based Hashing (OWASP & OOM Prevention):**
> When implementing custom integrity verification utilities or running on platforms with large file limits (e.g. 5GB), never load the entire file into memory (e.g., avoid `Files.readAllBytes()`). Always use stream-based chunk buffers (e.g. 8KB) to compute cryptographic digests, ensuring minimal heap utilization:
> ```java
> MessageDigest digest = MessageDigest.getInstance("SHA-256");
> try (InputStream fis = Files.newInputStream(filePath)) {
>     byte[] buffer = new byte[8192];
>     int n;
>     while ((n = fis.read(buffer)) != -1) {
>         digest.update(buffer, 0, n);
>     }
> }
> byte[] hashBytes = digest.digest();
> String hexHash = bytesToHex(hashBytes);
> ```

**Layer 2 — Digital signature verification (RSA):**
- The sender signs the data file using their private key and writes the signature to a companion file with extension `.signed` (configurable)
- On receipt, the starter verifies the signature using the sender's public key
- A failed verification sets status `INVALID_SIGNATURE_FILE` and generates ACK `461 DIGITAL_SIGNATURE_EXCEPTION`

Both layers are gated behind `app.interface.signature.enabled`. When disabled, neither check runs and files proceed directly to processing.

---

### Step 2: Enable and Configure Signature Validation

```yaml
# File: src/main/resources/application.yml
app:
  interface:
    signature:
      enabled: true

      # Directory containing sender public keys for signature verification
      # Each key file should be named after the sender or interface prefix
      publicKeyDirectoryPath: /etc/interface/keys/public

      # Directory containing this system's private key (used for outbound signing, if applicable)
      privateKeyDirectoryPath: /etc/interface/keys/private

      # Companion file extensions (must match what the sender appends)
      hashExtension: .sha3
      signatureExtension: .signed

      # Algorithms — defaults match the standard's recommendations
      hashAlgorithm: SHA-256
      signatureAlgorithm: SHA256withRSA

      # Keystore password (if keys are stored in a JKS keystore)
      # Prefer fetching this from secrets manager at runtime rather than hardcoding here
      keystorePassword: ${KEYSTORE_PASSWORD}
```

> **Never hardcode `keystorePassword` in `application.yml` committed to source control.** Use an environment variable backed by your secrets manager (see Step 5).

When `enabled: true`, the scheduler expects three files to arrive together for every inbound batch:

| File | Example | Purpose |
|---|---|---|
| Data file | `PAYMENT_TXN_20260327_001.csv` | The actual batch payload |
| Hash companion | `PAYMENT_TXN_20260327_001.csv.sha3` | SHA-256 digest of the data file |
| Signature companion | `PAYMENT_TXN_20260327_001.csv.signed` | RSA signature of the data file |

If only the companion files arrive first (no data file), the starter creates a `MISSING_PAYLOAD` record and waits. When the data file arrives, the status is promoted to `RECEIVED` and the full validation pipeline runs.

---

### Step 3: Prepare Keys and Place Them Correctly

**Generating a sender key pair (example using OpenSSL):**

```bash
# Generate sender private key (sender holds this — never share)
openssl genrsa -out sender_private.pem 2048

# Derive the public key (provide this to the receiving system — your application)
openssl rsa -in sender_private.pem -pubout -out sender_public.pem
```

Place `sender_public.pem` into the directory configured at `app.interface.signature.publicKeyDirectoryPath`. The `FileSignatureService` (auto-configured by `CoreAutoConfiguration`) loads keys from this directory at startup.

**File permissions:** Key files must be readable only by the application user. Set permissions to `600` or `400` on Unix systems:

```bash
chmod 400 /etc/interface/keys/public/sender_public.pem
chmod 400 /etc/interface/keys/private/app_private.pem
```

**For senders to generate companion files:**

```bash
# Sender generates the SHA-256 hash companion
sha256sum PAYMENT_TXN_20260327_001.csv > PAYMENT_TXN_20260327_001.csv.sha3

# Sender generates the RSA signature companion
openssl dgst -sha256 -sign sender_private.pem \
    -out PAYMENT_TXN_20260327_001.csv.signed \
    PAYMENT_TXN_20260327_001.csv
```

Provide this procedure to the sending system's integration team so they generate companions in the expected format.

---

### Step 4: Classify Files with Security and Sensitivity Labels

Every `@MAGEntity` carries two classification annotations that define how the file and its contents must be handled:

```java
// File: src/main/java/com/example/dto/PaymentFileDTO.java
import com.example.interface.core.annotation.MAGEntity;
import com.example.interface.core.enums.FileType;
import com.example.interface.core.enums.SequenceType;
import com.example.interface.core.enums.SecurityClassification;
import com.example.interface.core.enums.SensitivityClassification;

@MAGEntity(
    filenamePrefix = "PAYMENT_TXN",
    fileType = FileType.CSV,
    sequenceType = SequenceType.DAILY_SEQUENCE,
    securityClassification = SecurityClassification.RESTRICTED,
    sensitivityClassification = SensitivityClassification.SENSITIVE_HIGH
)
public class PaymentFileDTO extends InboundFileContentDTO {
    // fields
}
```

**`SecurityClassification` values:**

| Value | Meaning | Typical Use |
|---|---|---|
| `OFFICIAL_OPEN` | No access restriction | Reference data, public catalogs |
| `OFFICIAL_CLOSED` | Internal use only | Internal transaction files |
| `RESTRICTED` | Limited distribution, need-to-know | Payroll, customer records |
| `CONFIDENTIAL` | Strict access control, audit required | Regulated financial data, PII |

**`SensitivityClassification` values:**

| Value | Meaning | Logging Implication |
|---|---|---|
| `NON_SENSITIVE` | No PII or sensitive data | Full record content may be logged at DEBUG |
| `SENSITIVE_NORMAL` | Contains PII or business-sensitive data (default) | Log only file ID and record count; never log record content |
| `SENSITIVE_HIGH` | Highly sensitive — regulated data (e.g., payment card, health records) | Log only file ID; no counts or metadata beyond what is required for audit |

> **The starter does not enforce logging redaction automatically based on these values.** They are declarative labels for your team's use. You are responsible for ensuring `BatchJobCommand.process()` does not log record content for files classified as `SENSITIVE_NORMAL` or above. Follow the App Standard's [Prohibited Audit Content](../Interface_And_Batch_Application_Standard.md#33-audit-contract) rules.

---

### Step 5: Store Credentials in an External Secrets Manager

Never store SFTP credentials, API keys, keystore passwords, or private key material in `application.yml` or source code. Retrieve them at application startup from your secrets manager.

**AWS Secrets Manager example (Spring Cloud AWS):**

```yaml
# File: src/main/resources/application.yml
spring:
  cloud:
    aws:
      secretsmanager:
        enabled: true

# Reference secrets by ARN or name — Spring Cloud AWS resolves them at startup
app:
  interface:
    signature:
      keystorePassword: ${/prod/interface/keystore-password}

# SFTP credentials for outbound transmission
sftp:
  username: ${/prod/interface/sftp-username}
  privateKeyPath: /etc/secrets/sftp_key   # file provisioned by secrets manager sidecar
```

**HashiCorp Vault example (Spring Cloud Vault):**

```yaml
# File: src/main/resources/application.yml
spring:
  cloud:
    vault:
      enabled: true
      uri: https://vault.internal:8200
      authentication: KUBERNETES
      kubernetes:
        role: interface-app

# Vault KV secret at secret/interface/credentials
app:
  interface:
    signature:
      keystorePassword: ${vault.keystore-password}
```

**Runtime secret injection via environment variable (simplest approach):**

```bash
# In your deployment manifest (Kubernetes Secret → env var)
KEYSTORE_PASSWORD=<value-from-secret>
SFTP_PRIVATE_KEY_PATH=/etc/secrets/sftp_key
```

```yaml
app:
  interface:
    signature:
      keystorePassword: ${KEYSTORE_PASSWORD}
```

> **Key rotation:** When a sender rotates their signing key, place the new public key in `publicKeyDirectoryPath` before they begin sending files with the new private key. Keep the old public key in place until all in-flight files signed with the old key have been processed and archived.

---

### Step 6: Understand What Sender Authorization the Starter Enforces

The starter enforces **filename-prefix authorization** implicitly: only files whose filename prefix matches a registered `@MAGEntity.filenamePrefix` are accepted for processing. Files with unrecognised prefixes receive status `UNRECOGNIZED` and are archived without generating an ACK.

This means each registered `@MAGEntity` defines an authorised file type. If a sender transmits a file type that is not registered, it is silently quarantined.

**What the starter does NOT enforce automatically:**
- Per-sender authentication at the transport layer (SFTP user, TLS client certificate, API key) — this is your deployment infrastructure's responsibility
- Restricting which senders may submit which file types — if two senders use the same `filenamePrefix` and deposit files in the same inbox, both will be processed

**If you need per-sender access control at the file level**, implement it in `BatchJobCommand.process()` using a sender ID embedded in the file content or filename, and reject records from unauthorised senders by throwing `FileContentValidationException`:

```java
// File: src/main/java/com/example/batch/command/PaymentBatchCommand.java
@Override
public PaymentRecord process(PaymentFileDTO dto, boolean isContentValid, String validationErrors)
        throws FileContentValidationException {

    if (!isContentValid) {
        return null;
    }

    // Enforce sender authorization at record level
    if (!authorisedSenders.contains(dto.getSenderId())) {
        throw new FileContentValidationException(
            "Unauthorised sender ID: " + dto.getSenderId()
        );
    }

    return mapToRecord(dto);
}
```

For transport-level authorization (recommended for production), configure your SFTP server or API gateway to authenticate senders before files reach the inbox, separate from the starter's processing pipeline.

---

### Step 7: Verify Integrity Validation Is Working

Use these checks to confirm the configuration is active:

```sql
-- Files that failed hash verification (ACK 460 sent)
SELECT name, datetime_created
FROM INBOUND_FILE
WHERE status = 'INVALID_HASH_FILE'
ORDER BY datetime_created DESC;

-- Files that failed signature verification (ACK 461 sent)
SELECT name, datetime_created
FROM INBOUND_FILE
WHERE status = 'INVALID_SIGNATURE_FILE'
ORDER BY datetime_created DESC;
```

To test the hash validation path end-to-end:
1. Create a valid CSV file and its correct `.sha3` companion.
2. Corrupt the `.sha3` file (change one character).
3. Drop both into the FSX inbox.
4. After the next scheduler cycle, confirm status is `INVALID_HASH_FILE` and an `ack_460_*` file exists in the ACK output directory.

---

## 4. Examples

### Example: File Set for a Signature-Enabled Interface

```
# Files deposited by sender in FSX inbox:
PAYMENT_TXN_20260327_001.csv          # data file
PAYMENT_TXN_20260327_001.csv.sha3     # SHA-256 hash
PAYMENT_TXN_20260327_001.csv.signed   # RSA signature

# After successful validation and processing:
INBOUND_FILE.status = PROCESSED
ACK_FILE.status_code = SUCCESS (200)
ACK_FILE.name = PAYMENT_TXN_20260327_001.csv.ack_200_20260327T143045
```

### Example: Hash Mismatch

```
# Files deposited:
PAYMENT_TXN_20260327_002.csv          # data file (content modified after hash was computed)
PAYMENT_TXN_20260327_002.csv.sha3     # SHA-256 hash (now stale — does not match data file)

# Processing result:
INBOUND_FILE.status = INVALID_HASH_FILE
ACK_FILE.name = PAYMENT_TXN_20260327_002.csv.ack_460_20260327T143100
# Sender must recompute hash and resubmit both files
```

---

## 5. Verification

1. Enable `app.interface.signature.enabled: true` and place valid key files at the configured paths.
2. Submit a complete file set (data + `.sha3` + `.signed`) — confirm status `PROCESSED` and ACK `200`.
3. Submit a file with a corrupted `.sha3` — confirm status `INVALID_HASH_FILE` and ACK `460`.
4. Submit a file with a corrupted `.signed` — confirm status `INVALID_SIGNATURE_FILE` and ACK `461`.
5. Submit a file with an unrecognised prefix — confirm status `UNRECOGNIZED` and no ACK generated.
6. Confirm key files have restrictive permissions (`ls -la /etc/interface/keys/`) and are not readable by other users.

---

## 6. Conclusion

The starter's integrity verification pipeline automatically handles hash and signature validation before any business logic runs, with distinct failure statuses and ACK codes that communicate the exact failure mode to senders. Classification via `@MAGEntity` labels (`SecurityClassification`, `SensitivityClassification`) provides a declarative contract for how your team handles file content in logs and storage. Credential and key management must be handled outside the starter using an external secrets manager — the starter provides the configuration hooks; you provide the values at runtime.

## 7. References

- `com.example.interface.core.config.FileSignatureProperties` — all signature configuration properties
- `com.example.interface.inbound.service.InboundFileValidationService` — `isValidHash()`, `isValidSignature()`, `validateFileHashAndSignature()`
- `com.example.interface.core.util.FileValidator` — `isFileHashValid()`, `isFileZeroByte()`
- `com.example.interface.core.annotation.MAGEntity` — `securityClassification`, `sensitivityClassification`
- `com.example.interface.core.enums.SecurityClassification` — `OFFICIAL_OPEN`, `OFFICIAL_CLOSED`, `RESTRICTED`, `CONFIDENTIAL`
- `com.example.interface.core.enums.SensitivityClassification` — `NON_SENSITIVE`, `SENSITIVE_NORMAL`, `SENSITIVE_HIGH`
- `com.example.interface.inbound.entity.InboundFileStatus` — `INVALID_HASH_FILE`, `INVALID_SIGNATURE_FILE`
- `com.example.interface.inbound.entity.AckFileStatusCode` — `CHECKSUM_EXCEPTION (460)`, `DIGITAL_SIGNATURE_EXCEPTION (461)`
- [App Standard §3.5 Security Contract](../Interface_And_Batch_Application_Standard.md#35-security-contract)
- [Error Handling, Retry, and Recovery](../Inbound/Error%20Handling%2C%20Retry%2C%20and%20Recovery.md)
