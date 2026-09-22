# MPDS Retrieval - Reimplementation Recipes

> **These recipes are canonical.** The AI agent MUST reproduce them exactly. Deviations from class names, method signatures, package placement, or serialization logic are defects.

These recipes show how to implement the MPDS retrieval behaviour. The target design emphasizes:

* deterministic multiple-UUID serialization (manual string building, no ObjectMapper)
* explicit request validation (including max batch size)
* explicit timeout handling
* resilience (bulkhead + circuit breaker) as a decorator
* one gateway boundary instead of command objects leaking everywhere
* immutable response model with defensive null checks
* default structured-logging observation recorder

## Recipe 1: Define the MPDS Boundary and Configuration Contract

This recipe creates an application-facing contract so the rest of the codebase does not need to handle the `queryParamValues` encoding rules.

### Step 1: Bind the MPDS endpoint, timeout, and batch limit

```java
@ConfigurationProperties(prefix = "spring.security.eds.mcc.mpds")
public record MpdsProperties(
        URI url,
        Duration requestTimeout,
        int maxBatchSize
) {
}
```

### Step 2: Bind the resilience configuration

```java
@ConfigurationProperties(prefix = "spring.security.eds.mcc.mpds.resilience")
public record MpdsResilienceProperties(
        int bulkheadMaxConcurrentCalls,
        int circuitBreakerFailureRateThreshold,
        int circuitBreakerMinimumNumberOfCalls,
        Duration circuitBreakerWaitDurationInOpenState,
        int circuitBreakerSlidingWindowSize,
        int circuitBreakerPermittedCallsInHalfOpen
) {
}
```

### Step 3: Declare property keys in the base `application.yml`

Base `application.yml` declares keys only — every environment-specific value is injected at runtime, so no literal endpoint appears here (framework-neutral tuning defaults such as timeouts and resilience thresholds are not environment-specific and stay literal):

```yaml
spring:
  security:
    eds:
      mcc:
        mpds:
          url: ${MPDS_URL}
          request-timeout: 10s
          max-batch-size: 100
          resilience:
            bulkhead-max-concurrent-calls: 10
            circuit-breaker-failure-rate-threshold: 50
            circuit-breaker-minimum-number-of-calls: 5
            circuit-breaker-wait-duration-in-open-state: 30s
            circuit-breaker-sliding-window-size: 10
            circuit-breaker-permitted-calls-in-half-open: 3
```

`url` has no default — see [Recipe 10: Deployed Environment Profile Configuration (SIT)](#recipe-10-deployed-environment-profile-configuration-sit) for the `application-sit.yml` value and local dev-mcc fallback.

Template IDs and shared-auth settings are also required runtime inputs, but they are owned outside this MPDS adapter slice:

* single-UUID query template ID registered on MTM
* multiple-UUID query template ID registered on MTM
* shared MCC auth foundation OAuth2 registration and signing-key configuration

### Step 4: Expose one gateway interface

```java
public interface MpdsGateway {
    MpdsResponse querySingleUuid(String queryTemplateId, String uuid);
    MpdsResponse queryMultipleUuids(String queryTemplateId, Collection<String> uuids);
}
```

### Step 5: Validate requests before transport

```java
@Component
@RequiredArgsConstructor
public class MpdsRequestValidator {

    private final MpdsProperties properties;

    public void validateSingle(String queryTemplateId, String uuid) {
        requireText(queryTemplateId, "queryTemplateId is required.");
        requireText(uuid, "uuid is required.");
    }

    public void validateMultiple(String queryTemplateId, Collection<String> uuids) {
        requireText(queryTemplateId, "queryTemplateId is required.");
        if (uuids == null || uuids.isEmpty()) {
            throw new IllegalArgumentException("At least one uuid is required.");
        }
        if (uuids.size() > properties.maxBatchSize()) {
            throw new IllegalArgumentException(
                "UUID batch exceeds maximum of " + properties.maxBatchSize() + ".");
        }
        uuids.forEach(uuid -> requireText(uuid, "uuid entries must be non-blank."));
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
```

## Recipe 2: Implement the Single-UUID Request Contract

This recipe preserves the exact single-UUID wire shape from the legacy starter.

### Step 1: Model the request as a value object that preserves the exact payload contract

```java
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MpdsSingleUuidRequest {

    @NotBlank
    private String queryTemplateId;

    @JsonProperty(value = "queryParamValues", access = JsonProperty.Access.READ_ONLY)
    private String getQueryParamValues() {
        return "{\"uuid\": \"" + uuid + "\"}";
    }

    @NotBlank
    @JsonIgnore
    private String uuid;

    public static MpdsSingleUuidRequest of(String queryTemplateId, String uuid) {
        return new MpdsSingleUuidRequest(queryTemplateId, uuid);
    }
}
```

### Step 2: Protect the contract with an exact serialization test

```java
@Test
void serializesExactSingleUuidPayload() throws Exception {
    String json = new ObjectMapper().writeValueAsString(
            MpdsSingleUuidRequest.of(
                    "it0002_main-89931c36-9816-45fd-9a74-6afbdb34fc3e",
                    "dd29623a8a0b44fd925d3753db195444"));

    assertThat(json).isEqualTo(
            "{\"queryTemplateId\":\"it0002_main-89931c36-9816-45fd-9a74-6afbdb34fc3e\"," +
            "\"queryParamValues\":\"{\\\"uuid\\\": \\\"dd29623a8a0b44fd925d3753db195444\\\"}\"}");
}
```

### Step 3: Keep this request model isolated

Do not let other layers hand-build this JSON string. The request model keeps this serialization rule in one place. Place this class in the `client` sub-package.

## Recipe 3: Implement the Multiple-UUID Request Contract with Deterministic Ordering

This recipe preserves the nested-string MPDS payload while fixing the determinism problem that comes from serializing an unordered `Set`. It uses manual string building instead of Jackson `ObjectMapper` for the inner UUID array.

### Step 1: Model the multiple-UUID request with manual serialization

```java
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MpdsMultipleUuidRequest {

    @NotBlank
    private String queryTemplateId;

    @JsonProperty(value = "queryParamValues", access = JsonProperty.Access.READ_ONLY)
    private String getQueryParamValues() {
        String serializedArray = "[" + uuids.stream()
                .map(u -> "\"" + u + "\"")
                .collect(Collectors.joining(",")) + "]";
        return "{\"uuids\": \"" + serializedArray + "\"}";
    }

    @JsonIgnore
    private List<String> uuids;

    public static MpdsMultipleUuidRequest of(String queryTemplateId, List<String> uuids) {
        return new MpdsMultipleUuidRequest(queryTemplateId, List.copyOf(uuids));
    }
}
```

**Why manual string building:** UUIDs are hex strings (`[0-9a-f-]`) — no characters require JSON escaping. This eliminates the ObjectMapper dependency, removes the `JsonProcessingException` path, and makes serialization fully transparent.

### Step 2: Normalize UUIDs before constructing the request

```java
private List<String> normalizeUuids(Collection<String> uuids) {
    return uuids.stream().distinct().sorted().toList();
}
```

Sorting gives stable contract tests, stable logs, and reproducible behavior across JVMs and nodes.

### Step 3: Lock the nested-string contract down with an exact test

```java
@Test
void serializesExactMultipleUuidPayload() throws Exception {
    String json = new ObjectMapper().writeValueAsString(
            MpdsMultipleUuidRequest.of(
                    "it0002_main-89931c36-9816-45fd-9a74-6afbdb34fc3e",
                    List.of(
                            "08a03b9b2f0246d7a2456c6910697678",
                            "dd29623a8a0b44fd925d3753db195444")));

    assertThat(json).isEqualTo(
            "{\"queryTemplateId\":\"it0002_main-89931c36-9816-45fd-9a74-6afbdb34fc3e\"," +
            "\"queryParamValues\":\"{\\\"uuids\\\": \\\"[\\\"08a03b9b2f0246d7a2456c6910697678\\\",\\\"dd29623a8a0b44fd925d3753db195444\\\"]\\\"}\"}");
}
```

This assertion matches the legacy `MPDSMulitpleUUIDRequestTest` exactly.

## Recipe 4: Execute MPDS Calls Through the Shared Request-Context Client

This recipe implements MPDS transport: validate input, build the exact request model, then use the request-context client from the shared auth foundation.

### Step 1: Define the exception hierarchy

```java
public class MpdsRetrievalException extends RuntimeException {
    public MpdsRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class MpdsCircuitOpenException extends MpdsRetrievalException {
    public MpdsCircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class MpdsBulkheadFullException extends MpdsRetrievalException {
    public MpdsBulkheadFullException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### Step 2: Implement the gateway over the shared client provider

```java
@Service
@RequiredArgsConstructor
public class DefaultMpdsGateway implements MpdsGateway {

    private final MccAuthenticatedClientProvider clientProvider;
    private final MpdsProperties properties;
    private final MpdsRequestValidator validator;
    private final MpdsObservationRecorder observationRecorder;

    @Override
    public MpdsResponse querySingleUuid(String queryTemplateId, String uuid) {
        validator.validateSingle(queryTemplateId, uuid);

        MpdsSingleUuidRequest request = MpdsSingleUuidRequest.of(queryTemplateId, uuid);
        MpdsObservationContext context = new MpdsObservationContext(
                null, null, queryTemplateId, "single", 1);

        return execute(request, context);
    }

    @Override
    public MpdsResponse queryMultipleUuids(String queryTemplateId, Collection<String> uuids) {
        validator.validateMultiple(queryTemplateId, uuids);

        List<String> normalized = normalizeUuids(uuids);
        MpdsMultipleUuidRequest request = MpdsMultipleUuidRequest.of(queryTemplateId, normalized);
        MpdsObservationContext context = new MpdsObservationContext(
                null, null, queryTemplateId, "multiple", normalized.size());

        return execute(request, context);
    }

    private MpdsResponse execute(Object requestBody, MpdsObservationContext context) {
        Instant startedAt = Instant.now();
        observationRecorder.recordStart(context);

        try {
            MpdsResponse response = clientProvider.requestContextClient()
                    .post()
                    .uri(properties.url())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(MpdsResponse.class)
                    .timeout(properties.requestTimeout())
                    .block();

            observationRecorder.recordSuccess(context, Duration.between(startedAt, Instant.now()));
            return response;
        } catch (WebClientResponseException | WebClientRequestException ex) {
            Duration duration = Duration.between(startedAt, Instant.now());
            observationRecorder.recordFailure(context, duration, "transport");
            throw new MpdsRetrievalException("MPDS transport failed.", ex);
        } catch (CodecException ex) {
            Duration duration = Duration.between(startedAt, Instant.now());
            observationRecorder.recordFailure(context, duration, "decoding");
            throw new MpdsRetrievalException("MPDS response decoding failed.", ex);
        } catch (RuntimeException ex) {
            Throwable unwrapped = Exceptions.unwrap(ex);
            Duration duration = Duration.between(startedAt, Instant.now());
            if (unwrapped instanceof TimeoutException) {
                observationRecorder.recordFailure(context, duration, "timeout");
                throw new MpdsRetrievalException("MPDS retrieval timed out.", ex);
            }
            observationRecorder.recordFailure(context, duration, "unknown");
            throw new MpdsRetrievalException("MPDS retrieval failed.", ex);
        }
    }

    private List<String> normalizeUuids(Collection<String> uuids) {
        return uuids.stream().distinct().sorted().toList();
    }
}
```

### Step 3: Map failures by category

The gateway MUST report:

* `IllegalArgumentException` for validation rejection before transport
* `MpdsRetrievalException("MPDS retrieval timed out.", cause)` for timeout failure
* `MpdsRetrievalException("MPDS transport failed.", cause)` for downstream HTTP or network failure
* `MpdsRetrievalException("MPDS response decoding failed.", cause)` for response-decoding failure

## Recipe 5: Implement the Resilient Gateway Decorator

This recipe wraps `DefaultMpdsGateway` with bulkhead and circuit breaker using Resilience4j.

### Step 1: Implement the resilient decorator

```java
@Primary
@Service
@RequiredArgsConstructor
public class ResilientMpdsGateway implements MpdsGateway {

    private final DefaultMpdsGateway delegate;
    private final Bulkhead bulkhead;
    private final CircuitBreaker circuitBreaker;

    @Override
    public MpdsResponse querySingleUuid(String queryTemplateId, String uuid) {
        return executeWithResilience(() -> delegate.querySingleUuid(queryTemplateId, uuid));
    }

    @Override
    public MpdsResponse queryMultipleUuids(String queryTemplateId, Collection<String> uuids) {
        return executeWithResilience(() -> delegate.queryMultipleUuids(queryTemplateId, uuids));
    }

    private MpdsResponse executeWithResilience(Supplier<MpdsResponse> supplier) {
        try {
            return Decorators.ofSupplier(supplier)
                    .withBulkhead(bulkhead)
                    .withCircuitBreaker(circuitBreaker)
                    .get();
        } catch (BulkheadFullException ex) {
            throw new MpdsBulkheadFullException(
                    "MPDS bulkhead is full. Maximum concurrent calls reached.", ex);
        } catch (CallNotPermittedException ex) {
            throw new MpdsCircuitOpenException(
                    "MPDS circuit breaker is open. Dependency is degraded.", ex);
        }
    }
}
```

### Step 2: Wire resilience beans from configuration

```java
@Configuration
@EnableConfigurationProperties({MpdsProperties.class, MpdsResilienceProperties.class})
public class MpdsAdapterConfiguration {

    @Bean
    Bulkhead mpdsBulkhead(MpdsResilienceProperties props) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(props.bulkheadMaxConcurrentCalls())
                .maxWaitDuration(Duration.ZERO)
                .build();
        return Bulkhead.of("mpds", config);
    }

    @Bean
    CircuitBreaker mpdsCircuitBreaker(MpdsResilienceProperties props) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(props.circuitBreakerFailureRateThreshold())
                .minimumNumberOfCalls(props.circuitBreakerMinimumNumberOfCalls())
                .waitDurationInOpenState(props.circuitBreakerWaitDurationInOpenState())
                .slidingWindowSize(props.circuitBreakerSlidingWindowSize())
                .permittedNumberOfCallsInHalfOpenState(props.circuitBreakerPermittedCallsInHalfOpen())
                .build();
        return CircuitBreaker.of("mpds", config);
    }
}
```

### Step 3: Verify resilience behavior in tests

```java
@Test
void bulkheadRejectsWhenFull() {
    Bulkhead bulkhead = Bulkhead.of("test", BulkheadConfig.custom()
            .maxConcurrentCalls(0).maxWaitDuration(Duration.ZERO).build());
    CircuitBreaker breaker = CircuitBreaker.ofDefaults("test");

    ResilientMpdsGateway gateway = new ResilientMpdsGateway(delegate, bulkhead, breaker);

    assertThatThrownBy(() -> gateway.querySingleUuid("template", "uuid"))
            .isInstanceOf(MpdsBulkheadFullException.class)
            .hasMessageContaining("bulkhead is full");
}

@Test
void circuitBreakerRejectsWhenOpen() {
    Bulkhead bulkhead = Bulkhead.ofDefaults("test");
    CircuitBreaker breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .minimumNumberOfCalls(1).failureRateThreshold(100).build());
    breaker.transitionToOpenState();

    ResilientMpdsGateway gateway = new ResilientMpdsGateway(delegate, bulkhead, breaker);

    assertThatThrownBy(() -> gateway.querySingleUuid("template", "uuid"))
            .isInstanceOf(MpdsCircuitOpenException.class)
            .hasMessageContaining("circuit breaker is open");
}
```

## Recipe 6: Preserve the Raw Response and Add Field Helpers

This recipe preserves the raw response while adding field accessors. The response model is immutable.

### Step 1: Model the raw response with immutable design

```java
@Getter
@Builder
@JsonDeserialize(builder = MpdsResponse.MpdsResponseBuilder.class)
public class MpdsResponse {

    private final JsonNode data;
    private final String message;
    private final String uuid;
    private final String timestamp;

    @JsonPOJOBuilder(withPrefix = "")
    public static class MpdsResponseBuilder {
    }

    public String getNric() {
        return getFirstField("it0002_main", "nric");
    }

    public String getFullName() {
        return getFirstField("it0002_main", "zzpad_cname");
    }

    public String getEmail() {
        return getSubtypeField("it0105_main", "usrid_long", "0010");
    }

    public String getMobileNumber() {
        return getSubtypeField("it0105_main", "usrid", "9001");
    }

    private String getFirstField(String templateId, String fieldName) {
        JsonNode templateNode = data != null ? data.get(templateId) : null;
        if (templateNode != null && templateNode.isArray() && !templateNode.isEmpty()) {
            JsonNode fieldNode = templateNode.get(0).get(fieldName);
            return fieldNode != null ? fieldNode.asText() : null;
        }
        return null;
    }

    private String getSubtypeField(String templateId, String fieldName, String subtype) {
        JsonNode templateNode = data != null ? data.get(templateId) : null;
        if (templateNode == null || !templateNode.isArray()) {
            return null;
        }

        for (JsonNode node : templateNode) {
            JsonNode subtypeNode = node.get("subty");
            if (subtypeNode != null && subtype.equals(subtypeNode.asText())) {
                JsonNode fieldNode = node.get(fieldName);
                return fieldNode != null ? fieldNode.asText() : null;
            }
        }
        return null;
    }
}
```

Key differences from legacy:
* **Immutable** — no setters, `@JsonDeserialize` with builder for Jackson.
* **Null-safe** — `fieldNode != null` check in `getSubtypeField` fixes a latent NPE in the legacy.
* **No Lombok `@Data`** — only `@Getter` to prevent mutation.

### Step 2: Add tests for fully populated and sparse responses

```java
private static final class TestMpdsResponses {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static MpdsResponse fullRecord() {
        return fromJson("""
                {
                  "data": {
                    "it0002_main": [
                      { "nric": "T0000000A", "zzpad_cname": "Synthetic Person" }
                    ],
                    "it0105_main": [
                      { "subty": "0010", "usrid_long": "person@example.test" },
                      { "subty": "9001", "usrid": "80000000" }
                    ]
                  },
                  "message": "ok",
                  "uuid": "uuid-1",
                  "timestamp": "2026-04-10T10:15:30Z"
                }
                """);
    }

    static MpdsResponse sparseRecord() {
        return fromJson("""
                {
                  "data": {
                    "it0002_main": [ {} ],
                    "it0105_main": []
                  },
                  "message": "ok",
                  "uuid": "uuid-2",
                  "timestamp": "2026-04-10T10:15:30Z"
                }
                """);
    }

    private static MpdsResponse fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MpdsResponse.class);
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }
}

@Test
void returnsPersonnelFieldsFromKnownTemplates() {
    MpdsResponse response = TestMpdsResponses.fullRecord();

    assertThat(response.getNric()).isEqualTo("T0000000A");
    assertThat(response.getFullName()).isEqualTo("Synthetic Person");
    assertThat(response.getEmail()).isEqualTo("person@example.test");
    assertThat(response.getMobileNumber()).isEqualTo("80000000");
}

@Test
void returnsNullWhenHelperFieldsAreMissing() {
    MpdsResponse response = TestMpdsResponses.sparseRecord();

    assertThat(response.getNric()).isNull();
    assertThat(response.getEmail()).isNull();
    assertThat(response.getMobileNumber()).isNull();
}
```

## Recipe 7: Implement the Default Observation Recorder

This recipe provides the shipped default `Slf4jMpdsObservationRecorder` that satisfies both audit and logging contracts.

### Step 1: Define the observation port

```java
public interface MpdsObservationRecorder {
    void recordStart(MpdsObservationContext context);
    void recordSuccess(MpdsObservationContext context, Duration duration);
    void recordFailure(MpdsObservationContext context, Duration duration, String errorCategory);
}

public record MpdsObservationContext(
        String correlationId,
        String actor,
        String queryTemplateId,
        String queryCardinality,
        int queryCount
) {
}
```

The observation context MUST use query count and cardinality rather than full UUID lists. Audit records MAY identify the target dependency as `MPDS`; subject identifiers MUST stay masked or omitted unless the application has explicit approval to record them.

### Step 2: Implement the default structured-logging recorder

```java
@Component
@Slf4j
public class Slf4jMpdsObservationRecorder implements MpdsObservationRecorder {

    @Override
    public void recordStart(MpdsObservationContext context) {
        log.info("MPDS retrieval started. template={} cardinality={} count={}",
                context.queryTemplateId(),
                context.queryCardinality(),
                context.queryCount());
    }

    @Override
    public void recordSuccess(MpdsObservationContext context, Duration duration) {
        log.info("MPDS retrieval succeeded. template={} cardinality={} count={} duration_ms={}",
                context.queryTemplateId(),
                context.queryCardinality(),
                context.queryCount(),
                duration.toMillis());
    }

    @Override
    public void recordFailure(MpdsObservationContext context, Duration duration, String errorCategory) {
        log.warn("MPDS retrieval failed. template={} cardinality={} count={} duration_ms={} error_category={}",
                context.queryTemplateId(),
                context.queryCardinality(),
                context.queryCount(),
                duration.toMillis(),
                errorCategory);
    }
}
```

This implementation:
* Logs at `INFO` on start and success.
* Logs at `WARN` on failure (transient dependency issues).
* Uses structured key-value pairs for log aggregation.
* DOES NOT log UUIDs, bearer tokens, or raw response data.

## Recipe 8: Assemble the Full Spring Reference Slice

This recipe pulls the earlier pieces together so teams can see the whole adapter in one Spring-shaped slice.

### Step 1: The complete dependency chain

```
Controller/Application Service
  → MpdsGateway (interface)
    → ResilientMpdsGateway (decorator: bulkhead + circuit breaker)
      → DefaultMpdsGateway (transport: validation, request building, execution)
        → MccAuthenticatedClientProvider.requestContextClient()
          → MPDS endpoint
```

### Step 2: Example application service (NOT part of the adapter)

`PERSON_CONTACT_TEMPLATE` below is a placeholder literal for illustration only — it is exempt from the "reproduce exactly" rule at the top of this document. How a real application resolves a `queryTemplateId` (a fixed constant, an env-var/config-bound property, or a database-backed template registry looked up per tenant/request) is an application-specific decision this recipe does not prescribe.

```java
@Service
@RequiredArgsConstructor
public class PersonnelLookupService {

    private static final String PERSON_CONTACT_TEMPLATE = "it0002_main-contact-template";

    private final MpdsGateway mpdsGateway;

    public PersonContactSnapshot lookupContact(String uuid) {
        MpdsResponse response = mpdsGateway.querySingleUuid(PERSON_CONTACT_TEMPLATE, uuid);
        return new PersonContactSnapshot(
                response.getUuid(),
                response.getFullName(),
                response.getEmail(),
                response.getMobileNumber());
    }
}

public record PersonContactSnapshot(
        String responseUuid,
        String fullName,
        String email,
        String mobileNumber
) {
}
```

### Step 3: Example controller with purpose-built DTO (NOT part of the adapter)

```java
@RestController
@RequiredArgsConstructor
@RequestMapping("/personnel")
public class PersonnelLookupController {

    private final PersonnelLookupService personnelLookupService;

    @GetMapping("/{uuid}/contact")
    public PersonContactSnapshot getContact(@PathVariable String uuid) {
        return personnelLookupService.lookupContact(uuid);
    }
}
```

**MUST NOT** return `MpdsResponse` directly. The `PersonContactSnapshot` DTO controls which fields reach the client.

### Step 4: Keep the shared-auth dependency at the adapter edge

The application service knows `MpdsGateway`. Only the gateway implementation knows `MccAuthenticatedClientProvider`. Only the auth foundation knows about OAuth2 tokens, client assertions, and signing keys.

## Recipe 9: Cover the Retrieval Path with Transport and Contract Tests

This recipe adds tests around the retrieval path so refactors do not change the contract by accident.

### Step 1: Verify the gateway uses the request-context authenticated client

```java
@Test
void singleUuidGatewayUsesRequestContextClient() {
    gateway.querySingleUuid("template-id", "uuid-1");

    verify(clientProvider).requestContextClient();
    verify(clientProvider, never()).backgroundClient();
}
```

### Step 2: Verify exact payload shapes for both query modes

```java
@Test
void payloadShapesStayStable() throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    String singleJson = mapper.writeValueAsString(
            MpdsSingleUuidRequest.of("template-id", "uuid-1"));
    assertThat(singleJson).contains("\"queryParamValues\":\"{\\\"uuid\\\":");

    String multiJson = mapper.writeValueAsString(
            MpdsMultipleUuidRequest.of("template-id", List.of("uuid-1", "uuid-2")));
    assertThat(multiJson).contains("\"queryParamValues\":\"{\\\"uuids\\\":");
}
```

### Step 3: Verify timeout and failure handling

```java
@Test
void timeoutMapsToMpdsTimeoutFailure() {
    MccAuthenticatedClientProvider clientProvider = mock(MccAuthenticatedClientProvider.class);
    WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> Mono.never())
            .build();
    when(clientProvider.requestContextClient()).thenReturn(webClient);

    DefaultMpdsGateway gateway = new DefaultMpdsGateway(
            clientProvider,
            new MpdsProperties(URI.create("https://example.test/retrieve"), Duration.ofMillis(50), 100),
            new MpdsRequestValidator(new MpdsProperties(URI.create("https://example.test/retrieve"), Duration.ofMillis(50), 100)),
            new Slf4jMpdsObservationRecorder());

    assertThatThrownBy(() -> gateway.querySingleUuid("template-id", "uuid-1"))
            .isInstanceOf(MpdsRetrievalException.class)
            .hasMessageContaining("MPDS retrieval timed out");
}

@Test
void transportFailureMapsToMpdsTransportFailure() {
    MccAuthenticatedClientProvider clientProvider = mock(MccAuthenticatedClientProvider.class);
    WebClient webClient = WebClient.builder()
            .exchangeFunction(request -> Mono.error(new WebClientRequestException(
                    new IOException("connection reset"),
                    HttpMethod.POST,
                    URI.create("https://example.test/retrieve"),
                    HttpHeaders.EMPTY)))
            .build();
    when(clientProvider.requestContextClient()).thenReturn(webClient);

    DefaultMpdsGateway gateway = new DefaultMpdsGateway(
            clientProvider,
            new MpdsProperties(URI.create("https://example.test/retrieve"), Duration.ofSeconds(2), 100),
            new MpdsRequestValidator(new MpdsProperties(URI.create("https://example.test/retrieve"), Duration.ofSeconds(2), 100)),
            new Slf4jMpdsObservationRecorder());

    assertThatThrownBy(() -> gateway.querySingleUuid("template-id", "uuid-1"))
            .isInstanceOf(MpdsRetrievalException.class)
            .hasMessageContaining("MPDS transport failed");
}
```

### Step 4: Verify validation rejects bad input before transport

```java
@Test
void rejectsBlankTemplateId() {
    assertThatThrownBy(() -> gateway.querySingleUuid("", "uuid-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("queryTemplateId is required");
}

@Test
void rejectsEmptyUuidCollection() {
    assertThatThrownBy(() -> gateway.queryMultipleUuids("template-id", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one uuid is required");
}

@Test
void rejectsBatchExceedingMaxSize() {
    List<String> oversized = IntStream.range(0, 101)
            .mapToObj(i -> "uuid-" + i)
            .toList();

    assertThatThrownBy(() -> gateway.queryMultipleUuids("template-id", oversized))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds maximum");
}
```

## Package Structure Reference

The MPDS adapter MUST be placed in the following package structure, aligned with the MCNS adapter conventions:

```
<base-package>.shared.mpds/
├── client/
│   ├── DefaultMpdsGateway.java
│   ├── ResilientMpdsGateway.java
│   ├── MpdsSingleUuidRequest.java
│   └── MpdsMultipleUuidRequest.java
├── config/
│   ├── MpdsAdapterConfiguration.java
│   ├── MpdsProperties.java
│   └── MpdsResilienceProperties.java
├── exception/
│   ├── MpdsRetrievalException.java
│   ├── MpdsCircuitOpenException.java
│   └── MpdsBulkheadFullException.java
├── logging/
│   ├── MpdsObservationRecorder.java
│   └── Slf4jMpdsObservationRecorder.java
├── validation/
│   └── MpdsRequestValidator.java
├── MpdsGateway.java
├── MpdsResponse.java
└── MpdsObservationContext.java
```

Application services MUST import from `shared.mpds` (the public API: `MpdsGateway`, `MpdsResponse`, `MpdsObservationContext`).
Application services MUST NOT import from `shared.mpds.client`.

## Required Test Classes

```
<base-package>.shared.mpds/ (test source root)
├── client/
│   ├── MpdsSingleUuidRequestTest.java
│   ├── MpdsMultipleUuidRequestTest.java
│   ├── DefaultMpdsGatewayTest.java
│   └── ResilientMpdsGatewayTest.java
├── logging/
│   └── Slf4jMpdsObservationRecorderTest.java
├── validation/
│   └── MpdsRequestValidatorTest.java
└── MpdsResponseTest.java
```

---

## Recipe 10: Deployed Environment Profile Configuration (SIT)

**Goal**: Configure profile-specific environment variables for deployed environments (SIT/UAT/Prod) using `.env.sit` and `application-sit.yml`.

Per the [Bootstrap Profile Configuration Contract](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md#36-profile-configuration-contract), `url` is environment-specific and MUST NOT live as a hardcoded literal in `application.yml`.

### Step 1: Declare Profile Overrides in `application-sit.yml`

Only `url` varies per environment; `request-timeout`, `max-batch-size`, and the `resilience.*` thresholds are environment-invariant and already declared in the base `application.yml` ([Recipe 1, Step 3](#step-3-declare-property-keys-in-the-base-applicationyml)) — they are not repeated here.

```yaml
spring:
  security:
    eds:
      mcc:
        mpds:
          url: ${MPDS_URL:http://localhost:9081/retrieve}
```

`.env.sit` is loaded automatically by the bootstrap's `ProfileDotenvPostProcessor` — no `spring.config.import` declaration is needed in `application-sit.yml`.

The dev-mcc fallback (`http://localhost:9081/retrieve`) targets the local `mock-mpds` container from the [MCC Project Bootstrap](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md). Switching between local dev and SIT is done purely by selecting the Spring profile (`SPRING_PROFILES_ACTIVE=dev-mcc` or `sit`); no additional per-target selection step is needed. Without a `.env.sit` file, the SIT profile falls back to this dev-mcc default and works against the local Docker mock stack.

### Step 2: Create `.env.sit.example`

Provide a `.env.sit.example` template at the repository root to guide deployment engineers. The developer copies this to `.env.sit` (which is gitignored) for local verification.

```env
# MPDS Retrieval SIT Configuration
MPDS_URL=https://sit.mds-ecs.defcloud.gov.sg/retrieve
```
