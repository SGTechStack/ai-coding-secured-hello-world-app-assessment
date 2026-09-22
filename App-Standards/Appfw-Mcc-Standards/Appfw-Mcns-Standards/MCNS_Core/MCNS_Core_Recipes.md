# MCNS — Core Implementation Recipes

> These recipes cover how to implement the core MCNS library functionality: rate limiting, retry, the HTTP send, and constructing the outbound message. All code targets Spring Boot 3.x and JDK 17+.

---

## Recipe 1: Implementing Rate Limiting

**Goal**: Enforce a configurable cap on outbound MCNS requests within a sliding time window using Resilience4j.

```java
@Configuration
public class MCNSRateLimiterConfiguration {

    @Bean
    public RateLimiter mcnsRateLimiter(MCNSProperties properties) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(properties.getRateLimitPerPeriod())
            .limitRefreshPeriod(Duration.ofSeconds(properties.getLimitRefreshPeriod()))
            .timeoutDuration(Duration.ZERO) // Reject immediately if limit exceeded
            .build();

        return RateLimiter.of("mcns-rate-limiter", config);
    }
}
```

Apply at the point of dispatch — before any network call is made:

```java
rateLimiter.executeCallable(() -> sendToMCNS(requestBody));
```

If the window is exhausted, Resilience4j throws `RequestNotPermitted` immediately. No network call is made.

---

## Recipe 2: Implementing Retry

**Goal**: Automatically re-attempt failed MCNS sends up to `maxRetries` times, with a fixed wait between attempts, using Resilience4j.

```java
@Configuration
public class MCNSRetryConfiguration {

    @Bean
    public Retry mcnsRetry(MCNSProperties properties) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(properties.getMaxRetries())
            .waitDuration(Duration.ofSeconds(properties.getRetryWaitDuration()))
            .retryOnException(e -> !(e instanceof InvalidMCNSRequestException))
            .build();

        return Retry.of("mcns-retry", config);
    }
}
```

Key points:
- `InvalidMCNSRequestException` is explicitly excluded — validation failures are non-retryable.
- Once `maxRetries` is exhausted, Resilience4j wraps the last exception in `MCNSRequestFailedException`.
- Callers must not wrap the send call in their own retry logic.

Decorate the send call:

```java
Callable<MCNSResponse> send = Retry.decorateCallable(mcnsRetry, () -> sendToMCNS(requestBody));
MCNSResponse response = send.call();
```

---

## Recipe 3: Implementing the HTTP Send

**Goal**: POST the notification request to MCNS via the MCC-authenticated WebClient.

The endpoint is `{url}/{channel}` — the channel value is appended to the base URL directly.

```java
@Component
public class MCNSSender {

    private final MccAuthenticatedClientProvider clientProvider;
    private final MCNSProperties properties;

    public MCNSResponse send(MCNSRequestBody requestBody) {
        String endpoint = properties.getUrl() + requestBody.getChannel();

        return clientProvider.requestContextClient()
            .post()
            .uri(endpoint)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(MCNSResponse.class)
            .timeout(Duration.ofMinutes(properties.getRequestTimeout()))
            .block();
    }
}
```

Key points:
- Always source the WebClient from `MccAuthenticatedClientProvider` (see [MCC Shared Auth Foundation Recipes, Recipe 4](../../Appfw-Shared-Auth-Standards/MCC_Shared_Auth_Recipes.md#recipe-4-expose-request-context-and-background-authenticated-clients)) — raw unauthenticated `WebClient` instances are not permitted. This synchronous send flow runs inside a live request, so it uses `requestContextClient()`; a caller invoking MCNS from outside servlet request state (e.g. a scheduler) must use `backgroundClient()` instead.
- The `timeout` is applied at the reactive layer. Callers in reactive contexts must apply their own external timeout wrapper instead.
- A null or error response should propagate as `MCNSRequestFailedException` after retries are exhausted.

---

## Recipe 4: Defining and Constructing the Outbound Message

**Goal**: Define the request classes the application owns, and build a valid request body with sender identity injected, ready for dispatch.

The application defines `MCNSMessage` and `MCNSRequestBody` as its own POJOs. These are serialised to JSON and POSTed to the MCNS endpoint.

**`MCNSMessage`** — define with the following fields:

| Field | Type | Required | Description |
|:---|:---|:---|:---|
| `receiver` | `String` | Yes | Recipient address or phone number. Must match the channel format. |
| `sender` | `String` | Yes | Always set from `senderId` configuration. Never accept this from the caller. |
| `templateId` | `String` | Yes | The MCNS-onboarded template id **resolved from the caller's logical template key** via the template policy (never the logical key itself, and never accepted directly from the caller). |
| `templateValues` | `String` | No | JSON string of key-value pairs passed to the template (e.g. `{"otp":"123456"}`). Do not include PII in plain text. |

**`MCNSRequestBody`** — define with the following fields:

| Field | Type | Required | Description |
|:---|:---|:---|:---|
| `channel` | `String` | Yes | `"email"` or `"sms"`. Drives the endpoint path: `{url}/{channel}`. |
| `messages` | `List<MCNSMessage>` | Yes | Must be non-empty. All messages in a single body share the same channel. |

```java
// Application-owned POJOs
@Data
@Builder
public class MCNSMessage {
    private String receiver;
    private String sender;
    private String templateId;
    private String templateValues;
}

@Data
@Builder
public class MCNSRequestBody {
    private String channel;
    private List<MCNSMessage> messages;
}
```

The caller never supplies a raw MCNS id. It supplies a **logical template key**; the send service resolves the environment's onboarded `template-id` from the template policy and validates `templateValues` against that policy before building the request. Bind the policy on `MCNSProperties`:

```java
@ConfigurationProperties(prefix = "spring.security.eds.mcc.mcns")
@Data
public class MCNSProperties {
    private String url;
    private String senderId;
    // ... requestTimeout, maxRetries, retryWaitDuration, rateLimitPerPeriod, limitRefreshPeriod ...

    /** Logical template key -> policy. See Core Standard §6.1. */
    private Map<String, TemplatePolicy> templates = new HashMap<>();

    @Data
    public static class TemplatePolicy {
        private String templateId;                       // environment-specific onboarded id (injected)
        private List<String> allowedKeys;                // variable contract (env-invariant)
        private Map<String, String> valuePatterns;       // optional per-key regex
    }
}
```

Resolve the logical key and enforce the allowlist gate (fail-fast, pre-network):

```java
MCNSProperties.TemplatePolicy policy = properties.getTemplates().get(templateKey);
if (policy == null || !StringUtils.hasText(policy.getTemplateId())) {
    throw new InvalidMCNSRequestException("No MCNS template configured for key: " + templateKey);
}
// templateValues supplied as a Map<String,String> so keys/values can be validated
templateValues.forEach((k, v) -> {
    if (!policy.getAllowedKeys().contains(k)) {
        throw new InvalidMCNSRequestException("templateValues key not allowed for " + templateKey + ": " + k);
    }
    String pattern = policy.getValuePatterns() == null ? null : policy.getValuePatterns().get(k);
    if (pattern != null && !v.matches(pattern)) {
        throw new InvalidMCNSRequestException("templateValues['" + k + "'] fails pattern for " + templateKey);
    }
});
```

Build the request with the **resolved** `template-id` and `senderId` from configuration:

```java
MCNSMessage message = MCNSMessage.builder()
    .receiver(receiver)
    .sender(properties.getSenderId())        // always from config; never from caller input
    .templateId(policy.getTemplateId())      // resolved onboarded id, never the logical key
    .templateValues(toJson(templateValues))  // serialise validated map to JSON string; null if empty
    .build();

MCNSRequestBody body = MCNSRequestBody.builder()
    .channel(channel)                        // "email" or "sms"
    .messages(List.of(message))
    .build();
```

**`MCNSResponse`** — the response returned by the send service after a successful dispatch:

| Field | Type | Description |
|:---|:---|:---|
| `uuid` | `UUID` | Correlation ID assigned by MCNS for the overall request |
| `message` | `String` | Status message returned by MCNS |
| `messageDetails` | `List<MessageDetailsResponse>` | Per-recipient delivery details |

**`MessageDetailsResponse`** — one entry per recipient:

| Field | Type | Description |
|:---|:---|:---|
| `receiver` | `String` | The recipient address or phone number |
| `msgId` | `String` | Per-recipient message ID assigned by MCNS; use for delivery tracking |

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MCNSResponse {
    private String message;
    private UUID uuid;

    @JsonProperty("messageDetails")
    private List<MessageDetailsResponse> messageDetails;
}

@Data
public class MessageDetailsResponse {
    @JsonProperty("receiver")
    private String receiver;

    @JsonProperty("msgId")
    private String msgId;
}
```

Valid channel values and receiver format requirements:

| Channel | Receiver Format |
|:---|:---|
| `email` | `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$` |
| `sms` | `^\+[1-9]{1}[0-9]{1,14}$` or `^\+[1-9]{1}[0-9]{1,3} [0-9]{4,14}(?:x.+)?$` |

Validate all fields before passing to the send flow. Any violation should throw `InvalidMCNSRequestException` — no network call should be made.

---

## Recipe 5: Full Send Flow

**Goal**: Combine message construction, rate limiting, retry, and HTTP send into a single end-to-end dispatch method.

```java
@Service
public class MCNSSendService {

    private final MccAuthenticatedClientProvider clientProvider;
    private final RateLimiter mcnsRateLimiter;
    private final Retry mcnsRetry;
    private final MCNSProperties properties;

    public MCNSResponse send(String channel, String receiver,
                             String templateKey, Map<String, String> templateValues) {

        // Step 1: Resolve the logical template key to the environment's onboarded id
        //         and enforce the allowlist gate before building anything.
        MCNSProperties.TemplatePolicy policy = properties.getTemplates().get(templateKey);
        if (policy == null || !StringUtils.hasText(policy.getTemplateId())) {
            throw new InvalidMCNSRequestException("No MCNS template configured for key: " + templateKey);
        }
        templateValues.forEach((k, v) -> {
            if (!policy.getAllowedKeys().contains(k)) {
                throw new InvalidMCNSRequestException("templateValues key not allowed for " + templateKey + ": " + k);
            }
            String pattern = policy.getValuePatterns() == null ? null : policy.getValuePatterns().get(k);
            if (pattern != null && !v.matches(pattern)) {
                throw new InvalidMCNSRequestException("templateValues['" + k + "'] fails pattern for " + templateKey);
            }
        });

        // Step 2: Construct the outbound message with the resolved onboarded id
        MCNSMessage message = MCNSMessage.builder()
            .receiver(receiver)
            .sender(properties.getSenderId())        // always from config; never from caller input
            .templateId(policy.getTemplateId())      // resolved onboarded id, never the logical key
            .templateValues(toJson(templateValues))
            .build();

        MCNSRequestBody body = MCNSRequestBody.builder()
            .channel(channel)
            .messages(List.of(message))
            .build();

        // Step 3: Wrap the HTTP send with retry, then rate limiter
        // Rate limiter is outermost — rejects immediately if window is exceeded, no retry consumed
        // Retry is inner — re-attempts the HTTP call on transient failures
        Callable<MCNSResponse> sendWithRetry = Retry.decorateCallable(
            mcnsRetry,
            () -> doSend(body)
        );

        return RateLimiter.decorateCallable(mcnsRateLimiter, sendWithRetry).call();
    }

    private MCNSResponse doSend(MCNSRequestBody body) {
        String endpoint = properties.getUrl() + body.getChannel();

        return clientProvider.requestContextClient()
            .post()
            .uri(endpoint)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(MCNSResponse.class)
            .timeout(Duration.ofMinutes(properties.getRequestTimeout()))
            .block();
    }
}
```

---

## Recipe 6: Configure the Dev-MCC Profile

**Goal**: Provide committed, safe-to-commit defaults for local development against the mock-MCNS stack.

### Step 1: Declare committed defaults in `application-dev-mcc.yml`

**Dev profile (`application-dev-mcc.yml`)** — committed defaults point at the local mock-MCNS stack:

```yaml
spring:
  security:
    eds:
      mcc:
        mcns:
          url: http://localhost:9082/
          sender-id: no-reply.app-fw
          request-timeout: 30s
          retry-wait-duration: 2s
          max-retries: 3
          limit-refresh-period: 60s
          rate-limit-per-period: 100
          templates:
            otp-email:
              template-id: dev-otp-template-001
              allowed-keys: [code, expiry, issuer]
              value-patterns:
                code: '\d{6,8}'
                expiry: '[1-9]|[1-5][0-9]|60'
                issuer: '[a-zA-Z0-9]+'
```

`otp-email` is illustrative, not a fixed key — declare one entry under `templates.<key>` per logical template your app actually onboards with MCNS (see Core Standard [§6.1](MCNS_Core_Standard.md#61-configuration-reference)); the key name and how many you need are app-specific.

`request-timeout`, `retry-wait-duration`, `max-retries`, `limit-refresh-period`, `rate-limit-per-period`, and the template's `allowed-keys`/`value-patterns` are environment-invariant, so the SIT profile in [Recipe 7](#recipe-7-deployed-environment-profile-configuration-sit) does not repeat them — only `url`, `sender-id`, and `template-id` vary per environment.

---

## Recipe 7: Deployed Environment Profile Configuration (SIT)

**Goal**: Configure profile-specific environment variables for deployed environments (SIT/UAT/Prod) using `.env.sit` and `application-sit.yml`.

Per the [Bootstrap Profile Configuration Contract](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md#36-profile-configuration-contract), `url` and `senderId` are environment-specific and MUST NOT live as hardcoded literals in `application.yml`. 

### Step 1: Declare Profile Overrides in `application-sit.yml`

Graft these env var keys onto the `spring.security.eds.mcc.mcns.*` structure already shown in [Recipe 6](#recipe-6-configure-the-dev-mcc-profile), falling back to its dev-mcc defaults:

| Env var | Purpose |
|:---|:---|
| `MCNS_URL` | MCNS base URL (`{url}/{channel}` endpoint) |
| `MCNS_SENDER_ID` | Sender id sent as `sender` on every outbound message |
| `MCNS_OTP_TEMPLATE_ID` | Onboarded template id for the `otp-email` logical template key |

`MCNS_OTP_TEMPLATE_ID` follows Recipe 6's illustrative `otp-email` key — declare one `MCNS_<KEY>_TEMPLATE_ID`-style env var per logical template your app actually onboards; the key name and count are app-specific.

`.env.sit` is loaded automatically by the bootstrap's `ProfileDotenvPostProcessor` — no `spring.config.import` declaration is needed in `application-sit.yml`.

Actual SIT onboarded values are given in the `.env.sit.example` template in Step 2.

### Step 2: Create `.env.sit.example`

Provide a `.env.sit.example` template at the repository root to guide deployment engineers. The developer copies this to `.env.sit` (which is gitignored) for local verification.

```env
# MCNS Integration SIT Configuration
MCNS_URL=https://sit.mcns-ecs.defcloud.gov.sg/
MCNS_SENDER_ID=<mcns-sender-id-from-onboarding>
MCNS_OTP_TEMPLATE_ID=<mcns-otp-template-id-from-onboarding>
```
