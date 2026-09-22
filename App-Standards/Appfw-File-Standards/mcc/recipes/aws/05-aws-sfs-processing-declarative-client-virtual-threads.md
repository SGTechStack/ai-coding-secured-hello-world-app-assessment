# 05. AWS SFS Processing (Declarative Client + Virtual Threads)

**Goal**: Define the target AWS scanner adapter contract. A thin, replaceable adapter keeps SFS transport details outside the core domain (per §4.8 of the [AWS Standard](../../standards/file_management_standards_aws_core.md)) and ensures scanner operation failures are diagnosable by phase (per §4.7).

## Enforced Constraints

1. **Virtual Threads are mandatory.** All projects must enable `spring.threads.virtual.enabled: true` in the base `application.yml`. This makes all request-handling threads, scheduler threads, and blocking I/O operations run on virtual threads automatically.

2. **`RestClient` is the only permitted outbound HTTP client.** `WebClient` (reactive) must not be used for outbound calls. With virtual threads enabled, blocking I/O is free — the reactive stack (`Mono`, `.block()`, `WebClient`) becomes unnecessary complexity. Use `RestClient` for all synchronous outbound HTTP calls (SFS, MPDS, MCNS, OAuth token acquisition).

3. **`spring-boot-starter-webflux` must not be added as a dependency** unless there is a genuine non-HTTP reactive streaming requirement. The presence of WebFlux on the classpath causes Spring Boot to auto-configure a Netty-based server instead of Tomcat, and encourages reactive patterns that conflict with the virtual-thread-first approach.

**Required configuration:**

```yaml
# application.yml (base)
spring:
  threads:
    virtual:
      enabled: true
```

**Rationale:** Virtual threads make blocking I/O free (no platform thread pinning), so the reactive programming model provides no throughput benefit while adding substantial complexity (reactive operators, subscription management, unreadable stack traces, `.block()` calls everywhere). `RestClient` with virtual threads gives the same concurrency characteristics as `WebClient` with simpler, debuggable, synchronous code.

**Implementation**: Use a `RestClient`-backed `SfsScannerClient` adapter. Return typed outcome records (`UploadHandshake`, `PollOutcome`) and throw typed exceptions (`SfsUploadException`, `SfsPollingException`, `SfsDownloadException`) that carry `fileId`, HTTP status, and a truncated response body.

**Suggested Scanner Adapter Shape**:

```java
public interface SfsScannerClient {

    UploadHandshake requestUpload(String fileName, String mimeType);

    void uploadBytes(URI presignedUploadUrl, Map<String, String> headers, byte[] bytes);

    PollOutcome poll(String scannerUuid, String fileName, String mimeType);

    byte[] downloadClean(URI downloadUri);
}
```

**Suggested Declarative Client Split**:

```java
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface SfsControlClient {

    @PostExchange("/put")
    SfsPutResponse requestUpload(@RequestBody SfsPutRequest request);

    @PostExchange("/get")
    SfsGetResponse checkStatus(@RequestBody SfsGetRequest request);
}
```

**SFS Request/Response DTOs**:

```java
// Request for /put
record SfsPutRequest(String name, String type, String action) {
    public SfsPutRequest(String name, String type) {
        this(name, type, "put");
    }
}

// Request for /get
record SfsGetRequest(String uuid, String name, String type, String action) {
    public SfsGetRequest(String uuid, String name, String type) {
        this(uuid, name, type, "get");
    }
}

// Shared nested presigned info (present in both /put and /get responses)
record PreSignedInfo(String preSignedUrl, Map<String, String> preSignedHeaders) {}

// Response from /put
record SfsPutResponse(String uuid, String processingStatus, PreSignedInfo preSignedInfo, String message, String fileStatus) {}

// Response from /get (same structure)
record SfsGetResponse(String uuid, String processingStatus, PreSignedInfo preSignedInfo, String message, String fileStatus) {}
```

**SFS API Contract**:

| Operation | Method | URL | Payload | Response |
|:---|:---|:---|:---|:---|
| Request Upload | POST | `${sfs-endpoint}/put` | `{ name, type, action: "put" }` | `{ uuid, processingStatus, preSignedInfo: { preSignedUrl, preSignedHeaders }, message?, fileStatus? }` |
| Upload Binary | PUT | `preSignedInfo.preSignedUrl` | Raw bytes | `2xx` |
| Check Status | POST | `${sfs-endpoint}/get` | `{ uuid, name, type, action: "get" }` | `{ uuid, processingStatus, preSignedInfo: { preSignedUrl, preSignedHeaders }?, message?, fileStatus? }` |
| Download Clean | GET | `preSignedInfo.preSignedUrl` | none | Raw bytes |

**Critical implementation notes:**

1. **`action` field is mandatory** — the `/put` endpoint requires `action: "put"` and the `/get` endpoint requires `action: "get"`. Omitting this field results in 400 BAD_REQUEST with no body.
2. **Response structure is nested** — the presigned URL and headers live inside a `preSignedInfo` object (`preSignedInfo.preSignedUrl`, `preSignedInfo.preSignedHeaders`), not as flat top-level fields.
3. **`fileStatus` is the verdict** — when `processingStatus` is `"Allowed"` or `"Blocked"`, the scan verdict is in `fileStatus` (e.g., `"Sanitized"`, `"Unchanged"`, `"Quarantined"`).
4. **All SFS calls require authentication** — including the presigned URL upload (PUT) and download (GET). The mock-SFS enforces OAuth2 resource server security on all endpoints. Use the MCC authenticated background client for all SFS interactions, not a plain unauthenticated HTTP client. In real SIT/prod, presigned URLs are truly self-authenticating (S3-signed), but the mock requires the bearer token on every call.
5. **`processingStatus` values** — `"Processing"` (still scanning), `"Allowed"` (scan complete, file safe), `"Blocked"` (quarantined/malware). The transition from `Processing` to `Allowed`/`Blocked` happens after `SFS_SCAN_DELAY_MS` (configurable on the mock).

**SIT Reference Endpoints**:

| Purpose | Method | URL / Source |
|:---|:---|:---|
| MCC SSO token acquisition | POST | `https://sit.auth-ecs.defcloud.gov.sg/auth/realms/SSO/protocol/openid-connect/token` |
| SFS service base endpoint | N/A | `https://sit.sft-ecs.defcloud.gov.sg` |
| Request dirty-file upload URL | POST | `https://sit.sft-ecs.defcloud.gov.sg/put` (body: `{name, type, action:"put"}`) |
| Poll scan result | POST | `https://sit.sft-ecs.defcloud.gov.sg/get` (body: `{uuid, name, type, action:"get"}`) |
| Upload dirty file bytes | PUT | `preSignedInfo.preSignedUrl` from `/put` response |
| Download scanned file bytes | GET | `preSignedInfo.preSignedUrl` from `/get` response |

The scanner must not depend on a fixed S3 bucket URL for dirty-file submission. SFS returns short-lived presigned URLs and any required headers for object-store upload/download. Treat presigned URLs as credentials: do not log or persist them beyond the scanner metadata required to complete the lifecycle.

**Metadata Written By Phase**:

| Phase | Required Metadata |
|:---|:---|
| Request upload succeeded | Scanner UUID |
| Upload handoff confirmed | `sentToScannedOn` submission timestamp |
| Poll returned allowed | Download reference / presigned URL |
| Poll returned blocked or error | Scanner verdict + scanner error message |

**MCC Authentication Prerequisites**:

* AWS/MCC scanner integration depends on the shared MCC authenticated outbound client boundary.
* Consume the authenticated outbound client through DI, using `MccAuthenticatedClientProvider`.
* Scheduled scanner jobs must use the background client execution mode via `MccAuthenticatedClientProvider.backgroundClient()` so lifecycle work does not depend on servlet request state at use time.
* Required config:
  * registration ID `mcc-sso-client-credentials`
  * token URI
  * client ID
  * `authorization-grant-type=client_credentials`
  * `client-authentication-method=private_key_jwt`
  * MCC `kid`, `publicKey`, `privateKey`
  * onboarded SFS scope alias
* Reuse the shared MCC module for OAuth token acquisition, `x-application-id`, JWKS/public-key exposure, and key rotation.

**Storage Backend Selection**:

| Condition | Recommended Backend |
|:---|:---|
| Low-to-moderate file volume, small files, controlled retention, prefer simpler transactional persistence | Database-backed |
| High retained-file volume, longer retention, or operational separation from the metadata database is a concern | S3-backed (default) |

When the S3 variant is enabled, use a dedicated bucket or prefix for clean artifacts, private IAM access, and server-side encryption.

> Constraints for capacity throttling, hash mismatch handling, and the 24-hour retention window are defined in §3.5 and §4.7 of the [AWS Standard](../../standards/file_management_standards_aws_core.md).
