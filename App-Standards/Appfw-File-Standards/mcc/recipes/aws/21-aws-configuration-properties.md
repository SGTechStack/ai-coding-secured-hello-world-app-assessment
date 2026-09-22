/# 21. AWS Configuration Properties

**Goal**: Configure AWS SFS lifecycle, scheduler timing, blob-store backend selection, and MCC authenticated outbound client prerequisites.

**AWS scanner properties** (`file.scanner.*`):

```java
@ConfigurationProperties(prefix = "file.scanner")
@Validated
public record AwsScannerProperties(
    boolean enabled,
    @NotBlank String pollingIntervalCron,
    @Positive int scanDurationTimeout,
    @Positive int scannerFilesLimit,
    @Positive int pendingScanDatabaseClearIntervalInDays,
    @Positive int jobRetryLimit,
    @Positive long retryDelayMilliseconds,
    long transactionTimeoutBatchCycleMs,
    long transactionTimeoutPhase1Ms,
    long transactionTimeoutPhase2Ms,
    long transactionTimeoutPhase3Ms,
    @NotBlank String lockAtMostFor,
    @NotBlank String lockAtLeastFor
) {}
```

**AWS endpoint and storage properties** (`file.aws.*`):

```java
@ConfigurationProperties(prefix = "file.aws")
@Validated
public record AwsFileProperties(
    @NotBlank String sfsEndpoint,
    StorageBackend storageBackend,   // S3 or DATABASE — governs both dirty and clean blob persistence
    String dirtyBucket,              // Required when storageBackend = S3
    String cleanBucket,              // Required when storageBackend = S3
    // S3 client settings (endpoint-override for LocalStack in dev; unset in real AWS to use IAM/role creds)
    String s3EndpointOverride,
    String s3Region,
    String s3AccessKeyId,
    String s3SecretAccessKey
) {}
```

`storage-backend` selects the single `BlobStorage` implementation for all blob operations:

| Value | Dirty blobs | Clean blobs |
|:------|:------------|:------------|
| `S3` (default for deployed envs) | `dirty-bucket` / file-id key | `clean-bucket` / file-id key, SSE AES-256 |
| `DATABASE` (default for local dev) | `FILE_CONTENT_DIRTY` table | `FILE_CONTENT_CLEANED` table |

When `DATABASE` is selected, the S3 bucket/credential properties are unused.

**MCC SSO prerequisites**:

The AWS scanner profile depends on the shared MCC authenticated outbound client configuration:

* `spring.security.oauth2.client.registration.mcc-sso-client-credentials.client-id`
* `spring.security.oauth2.client.registration.mcc-sso-client-credentials.authorization-grant-type=client_credentials`
* `spring.security.oauth2.client.registration.mcc-sso-client-credentials.client-authentication-method=private_key_jwt`
* `spring.security.oauth2.client.registration.mcc-sso-client-credentials.scope`
* `spring.security.oauth2.client.provider.mcc-sso-client-credentials.token-uri`
* `spring.security.eds.oauth2.client.registration.mcc-sso-client-credentials.kid`
* `spring.security.eds.oauth2.client.registration.mcc-sso-client-credentials.public-key`
* `spring.security.eds.oauth2.client.registration.mcc-sso-client-credentials.private-key`

The MCC SSO registration itself (token-uri, client-id, scopes, and signing-key metadata) is owned by the [MCC Shared Auth Foundation](../../../../Appfw-Mcc-Standards/Appfw-Shared-Auth-Standards/MCC_Shared_Auth_Recipes.md) — do not duplicate those values here. This recipe configures only `file.scanner.*` and `file.aws.*`.

## Configuration (`application.yml`)

Environment-invariant structural constants — same values regardless of profile. These belong in the base `application.yml`:

```yaml
# File: backend/src/main/resources/application.yml   (file-upload slice)
file:
  scanner:
    enabled: true
    polling-interval-cron: "0/30 * * * * *"
    scan-duration-timeout: 30
    scanner-files-limit: 10
    pending-scan-database-clear-interval-in-days: 1
    job-retry-limit: 3
    retry-delay-milliseconds: 0
    transaction-timeout-batch-cycle-ms: 600000
    transaction-timeout-phase1-ms: 60000
    transaction-timeout-phase2-ms: 60000
    transaction-timeout-phase3-ms: 60000
    lock-at-most-for: 10m
    lock-at-least-for: 30s
  aws:
    storage-backend: S3
```

## Configuration (`application-dev-mcc.yml`)

Committed defaults are the local dev values: mock-SFS from the bootstrap compose stack on `:9083`, and LocalStack S3 provisioned by [recipe 29](29-aws-localstack-dev-bucket-provisioning.md). The M2M auth this scanner uses resolves through the shared MCC auth foundation's `mcc-sso-client-credentials` registration (mock-m-sso Keycloak in dev via `application-dev-mcc.yml`, real SIT IdP via `application-sit.yml`). Switching targets is done purely by selecting the Spring profile (`SPRING_PROFILES_ACTIVE=dev-mcc` or `sit`) — no env-var overrides needed for target selection.

```yaml
# File: backend/src/main/resources/application-dev-mcc.yml   (file-upload slice)
# Defaults are local dev / LocalStack values, safe for version control.
file:
  scanner:
    enabled: true                 # set false to bypass SFS entirely for local dev
    polling-interval-cron: "0/30 * * * * *"
    scan-duration-timeout: 30      # minutes
    scanner-files-limit: 10
    pending-scan-database-clear-interval-in-days: 1
    job-retry-limit: 3
    retry-delay-milliseconds: 0
    transaction-timeout-batch-cycle-ms: 600000
    transaction-timeout-phase1-ms: 60000
    transaction-timeout-phase2-ms: 60000
    transaction-timeout-phase3-ms: 60000
    lock-at-most-for: 10m
    lock-at-least-for: 30s
  aws:
    sfs-endpoint: http://localhost:9083
    storage-backend: S3              # S3 or DATABASE (governs both dirty + clean)
    dirty-bucket: myapp-dirty-dev
    clean-bucket: myapp-clean-dev
    s3-endpoint-override: http://localhost:4566
    s3-region: us-east-1
    s3-access-key-id: test
    s3-secret-access-key: test
```

## Deployed Environment Profile Configuration (SIT)

### Step 1: Declare Profile Overrides in `application-sit.yml`

Per the [Bootstrap Profile Configuration Contract](../../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md#36-profile-configuration-contract), graft these env var keys onto the `file.aws.*` structure above, falling back to its dev-mcc defaults. Base keys that are the same across all profiles (scanner timings, limits, storage-backend) belong in `application.yml` and are not repeated here.

| Env var | Purpose |
|:---|:---|
| `SFS_BASE_URL` | SFS endpoint for scan handshake |
| `S3_DIRTY_BUCKET` | S3 bucket for unscanned uploads |
| `S3_CLEAN_BUCKET` | S3 bucket for scanned clean files |
| `S3_REGION` | AWS region |
| `S3_ENDPOINT_OVERRIDE` | LocalStack endpoint override — leave unset in SIT/real AWS so the SDK uses IAM role credentials |
| `S3_ACCESS_KEY` | S3 access key — leave unset in SIT/real AWS so the SDK uses IAM role credentials |
| `S3_SECRET_ACCESS_KEY` | S3 secret key — leave unset in SIT/real AWS so the SDK uses IAM role credentials |

```yaml
# File: backend/src/main/resources/application-sit.yml   (file-upload slice)
file:
  aws:
    sfs-endpoint: ${SFS_BASE_URL:http://localhost:9083}
    dirty-bucket: ${S3_DIRTY_BUCKET:myapp-dirty-dev}
    clean-bucket: ${S3_CLEAN_BUCKET:myapp-clean-dev}
    s3-endpoint-override: ${S3_ENDPOINT_OVERRIDE:http://localhost:4566}
    s3-region: ${S3_REGION:us-east-1}
    s3-access-key-id: ${S3_ACCESS_KEY:test}
    s3-secret-access-key: ${S3_SECRET_ACCESS_KEY:test}
```

> **Note:** In SIT, `S3_ENDPOINT_OVERRIDE`, `S3_ACCESS_KEY`, and `S3_SECRET_ACCESS_KEY` are left empty/unset so the SDK uses real AWS S3 with IAM role credentials. The dev-mcc defaults above point at LocalStack with test credentials.

`.env.sit` is loaded automatically by the bootstrap's `ProfileDotenvPostProcessor` — no `spring.config.import` declaration is needed in `application-sit.yml`.

### Step 2: Create `.env.sit.example`

Provide a `.env.sit.example` template at the repository root to guide deployment engineers. The developer copies this to `.env.sit` (which is gitignored) for local verification.

```env
# File Upload SIT Configuration
SFS_BASE_URL=https://sit.sft-ecs.defcloud.gov.sg
S3_DIRTY_BUCKET=<s3-dirty-bucket-from-onboarding>
S3_CLEAN_BUCKET=<s3-clean-bucket-from-onboarding>
S3_REGION=ap-southeast-1
```

### SIT SFS Endpoint Reference

| Purpose | Value |
|:---|:---|
| SFS base endpoint | `https://sit.sft-ecs.defcloud.gov.sg` |
| SFS upload handshake | `POST https://sit.sft-ecs.defcloud.gov.sg/put` (body: `{name, type, action:"put"}`) |
| SFS status polling | `POST https://sit.sft-ecs.defcloud.gov.sg/get` (body: `{uuid, name, type, action:"get"}`) |
| Dirty file byte upload | `PUT` to `preSignedInfo.preSignedUrl` returned by `/put` |
| Clean file download | `GET` from `preSignedInfo.preSignedUrl` returned by `/get` |

All SFS calls (including presigned URL upload/download) must carry the MCC bearer token via the authenticated background client. In local dev, the mock-SFS enforces OAuth2 resource server security on every endpoint. In real SIT/prod, the presigned URLs returned by the real SFS are self-authenticating (S3-signed), but the control endpoints (`/put`, `/get`) still require the bearer token.

`thread-count-for-upload` is deprecated; prefer Virtual Threads plus backlog-aware SFS throttling.
