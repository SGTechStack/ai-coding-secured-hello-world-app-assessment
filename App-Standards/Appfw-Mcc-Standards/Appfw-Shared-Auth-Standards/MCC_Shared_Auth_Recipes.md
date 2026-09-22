# MCC Shared Auth Foundation - Reimplementation Recipes

These recipes show how to implement the shared MCC auth foundation. The target design emphasizes:

* key source selection is explicit instead of implicit
* public-only JWKS publication is guaranteed
* generated-key persistence is protected instead of merely encoded
* request-context and background clients stay behind one provider boundary
* fixed-key deployments can start without generated-key infrastructure

## Reference Implementation Map

The tutorial below uses these concrete building blocks.

| Component | Responsibility | Required in fixed-key mode | Required in generated-key mode |
|:---|:---|:---|:---|
| `MccClientKeyProperties` | Binds MCC-specific key fields under the OAuth2 registration. | Yes | Yes |
| `MccRuntimeProperties` | Binds generated-mode toggle, overlap duration, and rotation settings. | Yes | Yes |
| `MccRotationProperties` | Runtime view of cron, zone, and overlap settings. | No | Yes |
| `ActiveSigningKeyService` | Owns startup key selection, active key state, and rotation. | Yes | Yes |
| `PublishedJwksView` | Represents the exact public keys exposed over HTTP. | Yes | Yes |
| `GeneratedSigningKeyStore` | Loads and saves protected generated-key records. | No | Yes |
| `JwkMaterialProtector` | Encrypts and decrypts generated JWK JSON before persistence. | No | Yes |
| `BackgroundAccessTokenSupplier` | Obtains bearer tokens for background flows without servlet request state. | Yes | Yes |
| `SingleFlightTokenManager` | Deduplicates concurrent token acquisition calls so exactly one call reaches the token endpoint. | Yes | Yes |
| `MccAuthenticatedClientProvider` | Exposes the request-context and background clients to downstream adapters. | Yes | Yes |
| `MccKeyRotator` | Drives scheduled generated-key rotation. | No | Yes |

The tutorial is split into two slices:

* fixed-key mode: configured `kid`, `publicKey`, and `privateKey`; no generated-key store, rotator, or shared lock required
* generated-key mode: protected key history, explicit rollover overlap, and scheduled rotation

## Recipe 0: Ensure the Application Entry Point Exists

This foundation requires the `@SpringBootApplication` entry point defined by the [MCC Project Bootstrap Application Standard](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md). If it does not already exist in the project, run [Bootstrap Recipe Step 4](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Recipes.md) to create it before proceeding.

## Recipe 1: Define Configuration and Mode Selection

This recipe defines the configuration model for MCC client-credentials auth and validates key completeness at startup. 

### Step 1: Keep OAuth2 metadata separate from MCC key metadata

**Base `application.yml` (the production profile)** — keys only; every environment-specific and secret value is injected at runtime (framework constants such as the grant type and client-authentication method stay literal):

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          mcc-sso-client-credentials:
            token-uri: ${MCC_M2M_TOKEN_URI}
        registration:
          mcc-sso-client-credentials:
            client-id: ${MCC_CLIENT_ID_M2M}
            authorization-grant-type: client_credentials
            client-authentication-method: private_key_jwt
            scope: mcns
    eds:
      oauth2:
        client:
          registration:
            mcc-sso-client-credentials:
              kid: ${MCC_KEY_ID}
              public-key: ${MCC_PUBLIC_KEY}
              private-key: ${MCC_PRIVATE_KEY}
          mcc:
            generated-key-mode-enabled: false
            tls:
              bundle: mcc
  ssl:
    bundle:
      pem:
        mcc:
          truststore:
            certificate: ${MCC_TRUST_CERT}
```

**Dev profile (`application-dev-mcc.yml`)** — committed defaults point at the local mock-m-sso Keycloak:

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          mcc-sso-client-credentials:
            token-uri: https://localhost:9080/realms/SSO/protocol/openid-connect/token
        registration:
          mcc-sso-client-credentials:
            client-id: svc-appfw
            authorization-grant-type: client_credentials
            client-authentication-method: private_key_jwt
            scope: mcns,mds,ssft
    eds:
      oauth2:
        client:
          registration:
            mcc-sso-client-credentials:
              # kid must match the key registered in mock-m-sso Keycloak realm for this client.
              # Do not invent a new kid — use the value from the realm's client key configuration.
              kid: mcc-dev-ec521
              # Key format: base64-encode the ENTIRE PEM file content (including
              # -----BEGIN PUBLIC KEY----- and -----END PUBLIC KEY----- headers).
              # Example: cat mcc-dev-ec521.pub | base64 -w0
              # Do NOT use the raw key bytes (the inner base64 from the PEM) — use
              # base64(entire_PEM_string). The implementation must detect and unwrap
              # the PEM headers before decoding the inner key material.
              public-key: LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlHYk1CQUdCeXFHU000OUFnRUdCU3VCQkFBakE0R0dBQVFBbEpzRFhKVVlKU3Y0Zk9saTR2dG5Ec01BYVRaTgp5SXUwY01UY1ZZanRTUS9iSUVCK2ViZmRSZjZKY3cwZGhYN1FieWcvTkxXL1U4bVEySE51WlF5dVdNVUJacVU4CmhMR20zcE1HTTduaXhWTDNyWWhvTFJYTEdkczB5VFpUS2p0bUM0MStZRjloRHRyTVFJcy9LYk01Mk9FRW05UzEKZi9kOWhLUTIzV2trenQ2QU56QT0KLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0tCg==
              private-key: LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSHVBZ0VBTUJBR0J5cUdTTTQ5QWdFR0JTdUJCQUFqQklIV01JSFRBZ0VCQkVJQjlPOGgvR3ZXaG9WVG9sVFgKdEJaWWVHTkhwb3ArbVhMTnRVLzlpeXNwK0o1V2x1SzdzTHdBSmlkSGdmY2hmdnZCTDE1M2lMZlIxVkdVOGJQSwo1bnE1R25laGdZa0RnWVlBQkFDVW13TmNsUmdsSy9oODZXTGkrMmNPd3dCcE5rM0lpN1J3eE54VmlPMUpEOXNnClFINTV0OTFGL29sekRSMkZmdEJ2S0Q4MHRiOVR5WkRZYzI1bERLNVl4UUZtcFR5RXNhYmVrd1l6dWVMRlV2ZXQKaUdndEZjc1oyelRKTmxNcU8yWUxqWDVnWDJFTzJzeEFpejhwc3puWTRRU2IxTFYvOTMyRXBEYmRhU1RPM29BMwpNQT09Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K
          mcc:
            generated-key-mode-enabled: false
            jwks-overlap-duration: PT24H
            key-rotation-duration: "0 0 0 1 1 *"
            key-rotation-zone: Asia/Singapore
```

**SIT profile (`application-sit.yml`)** — deployed-environment configuration (the real SIT onboarded values, the `.env.sit.example` file, and the TLS trust note) is broken out into its own recipe so it doesn't compete for attention with the dev-profile setup above. See [Recipe 9: Deployed Environment Profile Configuration (SIT)](#recipe-9-deployed-environment-profile-configuration-sit).

### Step 2: Bind the MCC-specific properties

```java
@ConfigurationProperties(prefix = "spring.security.eds.oauth2.client")
public record MccClientKeyProperties(Map<String, Registration> registration) {

    public record Registration(
            String kid,
            String publicKey,
            String privateKey
    ) {
        boolean hasAnyKeyMaterial() {
            return hasText(kid) || hasText(publicKey) || hasText(privateKey);
        }

        boolean hasCompleteKeyMaterial() {
            return hasText(kid) && hasText(publicKey) && hasText(privateKey);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
```

```java
@ConfigurationProperties(prefix = "spring.security.eds.oauth2.client.mcc")
public record MccRuntimeProperties(
        boolean generatedKeyModeEnabled,
        Duration jwksOverlapDuration,
        String keyRotationDuration,
        String keyRotationZone,
        Tls tls,
        Duration connectTimeout,
        Duration readTimeout
) {
    public MccRuntimeProperties {
        if (tls == null) tls = new Tls(null);
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(10);
    }

    public record Tls(String bundle) {
        public String getBundle() { return bundle; }
    }

    public Tls getTls() { return tls; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }

    public MccRotationProperties toRotationProperties() {
        String cron = (keyRotationDuration == null || keyRotationDuration.isBlank())
                ? "0 0 0 1 1 *"
                : keyRotationDuration;
        ZoneId zone = (keyRotationZone == null || keyRotationZone.isBlank())
                ? ZoneId.of("Asia/Singapore")
                : ZoneId.of(keyRotationZone);
        Duration overlap = jwksOverlapDuration == null ? Duration.ofHours(24) : jwksOverlapDuration;
        return new MccRotationProperties(cron, zone, overlap);
    }
}

public record MccRotationProperties(
        String cronExpression,
        ZoneId zoneId,
        Duration jwksOverlapDuration
) {}
```

### Step 3: Validate registration and mode selection up front

```java
@Component
@RequiredArgsConstructor
public class MccSharedAuthConfigurationValidator {

    public static final String REGISTRATION_ID = "mcc-sso-client-credentials";

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final MccClientKeyProperties keyProperties;
    private final MccRuntimeProperties runtimeProperties;

    @PostConstruct
    void validate() {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        if (registration == null) {
            throw new IllegalStateException("Missing OAuth2 registration " + REGISTRATION_ID);
        }
        if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(registration.getAuthorizationGrantType())) {
            throw new IllegalStateException("MCC shared auth requires client_credentials.");
        }
        if (!ClientAuthenticationMethod.PRIVATE_KEY_JWT.equals(registration.getClientAuthenticationMethod())) {
            throw new IllegalStateException("MCC shared auth requires private_key_jwt.");
        }

        MccClientKeyProperties.Registration keyRegistration =
                keyProperties.registration().get(REGISTRATION_ID);
        if (keyRegistration == null) {
            throw new IllegalStateException("Missing MCC key registration " + REGISTRATION_ID);
        }
        if (keyRegistration.hasAnyKeyMaterial() && !keyRegistration.hasCompleteKeyMaterial()) {
            throw new IllegalStateException(
                    "Configured key mode requires kid, publicKey, and privateKey together.");
        }
        if (!keyRegistration.hasCompleteKeyMaterial() && !runtimeProperties.generatedKeyModeEnabled()) {
            throw new IllegalStateException(
                    "Generated-key mode must be enabled when configured key material is absent.");
        }
    }
}
```


## Recipe 2: Build the Runtime Key Model Once and Reuse It Everywhere

This recipe consolidates active signing, JWKS publication, and rollover overlap into one runtime service that provides both the signing key and the public JWKS view.

### Step 1: Define the runtime records and interfaces

```java
public enum SigningKeySource {
    CONFIGURED,
    GENERATED
}

public record PublishedJwksView(List<JWK> publicKeys) {
    public Map<String, Object> toPayload() {
        return new JWKSet(publicKeys).toJSONObject();
    }
}

public record ActiveSigningKeySnapshot(
        SigningKeySource source,
        JWK activeSigningKey,
        JWK previousPublicKey,
        Instant previousPublicKeyExpiresAt,
        Instant activatedAt
) {
    public PublishedJwksView publishedJwksView(Clock clock) {
        List<JWK> keys = new ArrayList<>();
        keys.add(activeSigningKey.toPublicJWK());
        if (previousPublicKey != null
                && previousPublicKeyExpiresAt != null
                && Instant.now(clock).isBefore(previousPublicKeyExpiresAt)) {
            keys.add(previousPublicKey);
        }
        return new PublishedJwksView(keys);
    }
}

public interface ActiveSigningKeyService {
    ActiveSigningKeySnapshot current();
    ActiveSigningKeySnapshot initialize();
    ActiveSigningKeySnapshot rotate();
}

public interface JwkMaterialProtector {
    String encrypt(String jwkJson);
    String decrypt(String ciphertext);
}

public record ProtectedGeneratedSigningKeyRecord(
        UUID id,
        String kid,
        String encryptedJwk,
        boolean activeForSigning,
        Instant createdAt,
        Instant activatedAt,
        Instant publishPublicUntil
) {}

public interface GeneratedSigningKeyStore {
    Optional<ProtectedGeneratedSigningKeyRecord> loadActiveSigningKey();
    Optional<ProtectedGeneratedSigningKeyRecord> loadStillPublishedPreviousKey(Instant now, String activeKid);
    void saveInitial(ProtectedGeneratedSigningKeyRecord record);
    void rotateTo(ProtectedGeneratedSigningKeyRecord newActive, Instant previousPublicUntil);
    void retireExpiredPublications(Instant now);
}
```

### Step 2: Implement one active-key service

```java
@RequiredArgsConstructor
public class DefaultActiveSigningKeyService implements ActiveSigningKeyService {

    public static final String REGISTRATION_ID = "mcc-sso-client-credentials";

    private final AtomicReference<ActiveSigningKeySnapshot> snapshotRef = new AtomicReference<>();
    private final MccClientKeyProperties keyProperties;
    private final MccRuntimeProperties runtimeProperties;
    private final ObjectProvider<GeneratedSigningKeyStore> generatedSigningKeyStoreProvider;
    private final ObjectProvider<JwkMaterialProtector> jwkMaterialProtectorProvider;
    private final Clock clock;

    @Override
    public ActiveSigningKeySnapshot current() {
        ActiveSigningKeySnapshot snapshot = snapshotRef.get();
        if (snapshot == null) {
            throw new IllegalStateException("No active MCC signing key is available.");
        }
        return snapshot;
    }

    @Override
    public ActiveSigningKeySnapshot initialize() {
        MccClientKeyProperties.Registration registration =
                keyProperties.registration().get(REGISTRATION_ID);

        ActiveSigningKeySnapshot snapshot = registration.hasCompleteKeyMaterial()
                ? initializeConfiguredKey(registration)
                : initializeGeneratedKey();

        snapshotRef.set(snapshot);
        return snapshot;
    }

    @Override
    public ActiveSigningKeySnapshot rotate() {
        if (!runtimeProperties.generatedKeyModeEnabled()) {
            throw new IllegalStateException("Rotation is only valid in generated-key mode.");
        }

        ActiveSigningKeySnapshot current = current();
        Instant now = Instant.now(clock);
        Instant publishPreviousUntil = now.plus(runtimeProperties.toRotationProperties().jwksOverlapDuration());

        JWK newActiveJwk = generateSigningKey();
        ProtectedGeneratedSigningKeyRecord newRecord = protect(newActiveJwk, true, now, now, null);

        GeneratedSigningKeyStore store = requiredGeneratedSigningKeyStore();
        store.rotateTo(newRecord, publishPreviousUntil);
        store.retireExpiredPublications(now);

        ActiveSigningKeySnapshot next = new ActiveSigningKeySnapshot(
                SigningKeySource.GENERATED,
                newActiveJwk,
                current.activeSigningKey().toPublicJWK(),
                publishPreviousUntil,
                now
        );
        snapshotRef.set(next);
        return next;
    }

    private ActiveSigningKeySnapshot initializeConfiguredKey(MccClientKeyProperties.Registration registration) {
        JWK jwk = JWKSUtil.buildJWK(registration.kid(), registration.publicKey(), registration.privateKey());
        return new ActiveSigningKeySnapshot(
                SigningKeySource.CONFIGURED,
                jwk,
                null,
                null,
                Instant.now(clock)
        );
    }

    private ActiveSigningKeySnapshot initializeGeneratedKey() {
        GeneratedSigningKeyStore store = requiredGeneratedSigningKeyStore();
        JwkMaterialProtector protector = requiredJwkMaterialProtector();
        Instant now = Instant.now(clock);

        Optional<ProtectedGeneratedSigningKeyRecord> activeRecord = store.loadActiveSigningKey();
        if (activeRecord.isPresent()) {
            JWK activeJwk = parseProtectedRecord(activeRecord.get(), protector);

            Optional<ProtectedGeneratedSigningKeyRecord> previousRecord =
                    store.loadStillPublishedPreviousKey(now, activeJwk.getKeyID());

            JWK previousPublicKey = previousRecord
                    .map(record -> parseProtectedRecord(record, protector).toPublicJWK())
                    .orElse(null);
            Instant previousPublicUntil = previousRecord
                    .map(ProtectedGeneratedSigningKeyRecord::publishPublicUntil)
                    .orElse(null);

            return new ActiveSigningKeySnapshot(
                    SigningKeySource.GENERATED,
                    activeJwk,
                    previousPublicKey,
                    previousPublicUntil,
                    activeRecord.get().activatedAt()
            );
        }

        JWK initialJwk = generateSigningKey();
        ProtectedGeneratedSigningKeyRecord record = protect(initialJwk, true, now, now, null);
        store.saveInitial(record);
        return new ActiveSigningKeySnapshot(SigningKeySource.GENERATED, initialJwk, null, null, now);
    }

    private ProtectedGeneratedSigningKeyRecord protect(
            JWK jwk,
            boolean activeForSigning,
            Instant createdAt,
            Instant activatedAt,
            Instant publishPublicUntil
    ) {
        String ciphertext = requiredJwkMaterialProtector().encrypt(jwk.toJSONString());
        return new ProtectedGeneratedSigningKeyRecord(
                UUID.randomUUID(),
                jwk.getKeyID(),
                ciphertext,
                activeForSigning,
                createdAt,
                activatedAt,
                publishPublicUntil
        );
    }

    private GeneratedSigningKeyStore requiredGeneratedSigningKeyStore() {
        GeneratedSigningKeyStore store = generatedSigningKeyStoreProvider.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException("Generated-key mode requires GeneratedSigningKeyStore.");
        }
        return store;
    }

    private JwkMaterialProtector requiredJwkMaterialProtector() {
        JwkMaterialProtector protector = jwkMaterialProtectorProvider.getIfAvailable();
        if (protector == null) {
            throw new IllegalStateException("Generated-key mode requires JwkMaterialProtector.");
        }
        return protector;
    }

    private JWK parseProtectedRecord(
            ProtectedGeneratedSigningKeyRecord record,
            JwkMaterialProtector protector
    ) {
        try {
            return JWK.parse(protector.decrypt(record.encryptedJwk()));
        } catch (ParseException ex) {
            throw new IllegalStateException("Unable to parse protected generated JWK.", ex);
        }
    }

    private JWK generateSigningKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Unable to generate MCC signing key.", ex);
        }
    }
}
```

### Step 3: Bootstrap the runtime state once

```java
@Component
@RequiredArgsConstructor
public class MccKeyBootstrap {

    private final ActiveSigningKeyService activeSigningKeyService;

    @PostConstruct
    void initialize() {
        activeSigningKeyService.initialize();
    }
}
```

In fixed-key mode, the service never asks for `GeneratedSigningKeyStore` or `JwkMaterialProtector`. That is the deliberate boundary that keeps fixed-key deployments lightweight.

## Recipe 3: Publish a Public-Only JWKS Endpoint and Define the Rollover Lifecycle

### Step 1: Build the public-only controller

```java
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final ActiveSigningKeyService activeSigningKeyService;
    private final Clock clock;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        PublishedJwksView jwksView = activeSigningKeyService.current().publishedJwksView(clock);
        if (jwksView.publicKeys().isEmpty()) {
            throw new IllegalStateException("No public keys are available for JWKS publication.");
        }
        return jwksView.toPayload();
    }
}
```

### Step 1a: Permit the JWKS endpoint without authentication

The JWKS endpoint must be publicly accessible so that the MCC IdP can fetch public keys for assertion validation. Permit it in the Spring Security filter chain:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/.well-known/jwks.json").permitAll()
            // ... other matchers
            .anyRequest().authenticated()
        )
        .build();
}
```

Without this, a default-deny security configuration will return 401/403 for JWKS requests, breaking IdP key discovery.

### Step 2: Reference rollover sequence

1. Generate the new signing key.
2. Protect the new JWK JSON through `JwkMaterialProtector`.
3. Persist the new key as active for signing.
4. Mark the old active key as non-signing but still published until `now + jwksOverlapDuration`.
5. Switch new client assertions to the new active key immediately.
6. Publish `previous + current` public keys during the overlap window.
7. Retire expired previous-key publications after the overlap window.

Keep the old public key available for a configurable period after switching to a new signing key. Do not hardcode this duration.

### Step 3: Make the publication state visible in generated-key storage

```sql
create table mcc_signing_key_history (
    id uuid primary key,
    kid varchar(120) not null unique,
    encrypted_jwk clob not null,
    active_for_signing boolean not null,
    created_at timestamp not null,
    activated_at timestamp not null,
    publish_public_until timestamp null
);

create index idx_mcc_signing_key_history_active
    on mcc_signing_key_history (active_for_signing);

create index idx_mcc_signing_key_history_publish_public_until
    on mcc_signing_key_history (publish_public_until);
```

The column is named `encrypted_jwk` to signal that the content is protected, not merely encoded. Do not store raw or Base64-encoded private JWK JSON.

### Step 4: Example store adapter shape

```java
@Entity
@Getter
@Setter
@Table(name = "mcc_signing_key_history")
public class GeneratedSigningKeyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String kid;

    @Column(name = "encrypted_jwk", nullable = false)
    private String encryptedJwk;

    @Column(name = "active_for_signing", nullable = false)
    private boolean activeForSigning;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "publish_public_until")
    private Instant publishPublicUntil;

    public ProtectedGeneratedSigningKeyRecord toRecord() {
        return new ProtectedGeneratedSigningKeyRecord(
                id,
                kid,
                encryptedJwk,
                activeForSigning,
                createdAt,
                activatedAt,
                publishPublicUntil
        );
    }

    public static GeneratedSigningKeyEntity from(ProtectedGeneratedSigningKeyRecord record) {
        GeneratedSigningKeyEntity entity = new GeneratedSigningKeyEntity();
        entity.id = record.id();
        entity.kid = record.kid();
        entity.encryptedJwk = record.encryptedJwk();
        entity.activeForSigning = record.activeForSigning();
        entity.createdAt = record.createdAt();
        entity.activatedAt = record.activatedAt();
        entity.publishPublicUntil = record.publishPublicUntil();
        return entity;
    }
}
```

```java
public interface GeneratedSigningKeyJpaRepository extends JpaRepository<GeneratedSigningKeyEntity, UUID> {
    Optional<GeneratedSigningKeyEntity> findFirstByActiveForSigningTrueOrderByActivatedAtDesc();
    Optional<GeneratedSigningKeyEntity> findFirstByActiveForSigningFalseAndPublishPublicUntilAfterAndKidNotOrderByPublishPublicUntilDesc(
            Instant now,
            String kid
    );
}
```

```java
@Service
@Transactional
@RequiredArgsConstructor
public class JpaGeneratedSigningKeyStore implements GeneratedSigningKeyStore {

    private final GeneratedSigningKeyJpaRepository repository;

    @Override
    public Optional<ProtectedGeneratedSigningKeyRecord> loadActiveSigningKey() {
        return repository.findFirstByActiveForSigningTrueOrderByActivatedAtDesc().map(GeneratedSigningKeyEntity::toRecord);
    }

    @Override
    public Optional<ProtectedGeneratedSigningKeyRecord> loadStillPublishedPreviousKey(Instant now, String activeKid) {
        return repository
                .findFirstByActiveForSigningFalseAndPublishPublicUntilAfterAndKidNotOrderByPublishPublicUntilDesc(now, activeKid)
                .map(GeneratedSigningKeyEntity::toRecord);
    }

    @Override
    public void saveInitial(ProtectedGeneratedSigningKeyRecord record) {
        repository.save(GeneratedSigningKeyEntity.from(record));
    }

    @Override
    public void rotateTo(ProtectedGeneratedSigningKeyRecord newActive, Instant previousPublicUntil) {
        GeneratedSigningKeyEntity previous = repository.findFirstByActiveForSigningTrueOrderByActivatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("No active generated signing key found for rotation."));
        previous.setActiveForSigning(false);
        previous.setPublishPublicUntil(previousPublicUntil);
        repository.save(previous);
        repository.save(GeneratedSigningKeyEntity.from(newActive));
    }

    @Override
    public void retireExpiredPublications(Instant now) {
        repository.findAll().stream()
                .filter(entity -> !entity.isActiveForSigning())
                .filter(entity -> entity.getPublishPublicUntil() != null)
                .filter(entity -> entity.getPublishPublicUntil().isBefore(now))
                .forEach(entity -> {
                    entity.setPublishPublicUntil(now);
                    repository.save(entity);
                });
    }
}
```

## Recipe 4: Expose Request-Context and Background Authenticated Clients

This recipe keeps downstream adapters decoupled from OAuth2 token acquisition. Request-driven flows use `ServletOAuth2AuthorizedClientExchangeFilterFunction` for token lifecycle, while schedulers and listeners acquire tokens through `AuthorizedClientServiceOAuth2AuthorizedClientManager` with no servlet state involved.

> **Signing algorithm auto-detection:** The JWS algorithm for client assertions MUST be resolved from the active signing key's EC curve — never hardcoded. P-256 → ES256, P-384 → ES384, P-521 → ES512. The dev bootstrap generates a P-521 key, but onboarded SIT/prod keys may use P-256 (the MCC onboarding team issues EC P-256 keys). If the algorithm is hardcoded to ES512, signing will fail at runtime when a P-256 key is loaded. Detect the curve via `key.getParams().getOrder().bitLength()` (256 → ES256, 384 → ES384, 521 → ES512).
>
> **Critical: set the `algorithm` field on the JWK.** When using `NimbusJwtClientAuthenticationParametersConverter`, the converter reads the JWS algorithm from the JWK's `alg` property — NOT from the key's curve. If `alg` is not set on the `ECKey`, the converter defaults to ES256 regardless of the actual curve, causing a `JOSEException: The ES256 algorithm is not allowed or supported by the JWS signer` error at runtime with P-521 keys. Always build the ECKey with `.algorithm(detectedAlgorithm)`:
>
> ```java
> new ECKey.Builder(curve, ecPublicKey)
>     .privateKey(ecPrivateKey)
>     .keyID(kid)
>     .algorithm(detectedAlgorithm)  // REQUIRED — without this, NimbusJwtClientAuthenticationParametersConverter defaults to ES256
>     .build();
> ```

### Step 1: Expose the same provider boundary

```java
public interface MccAuthenticatedClientProvider {
    WebClient requestContextClient();
    WebClient backgroundClient();
}

@RequiredArgsConstructor
public class DefaultMccAuthenticatedClientProvider implements MccAuthenticatedClientProvider {

    private final WebClient requestContextClient;
    private final WebClient backgroundClient;

    @Override
    public WebClient requestContextClient() {
        return requestContextClient;
    }

    @Override
    public WebClient backgroundClient() {
        return backgroundClient;
    }
}
```

### Step 2: Configure the shared `private_key_jwt` converter once

```java
@Bean
NimbusJwtClientAuthenticationParametersConverter<OAuth2ClientCredentialsGrantRequest>
clientAssertionConverter(ActiveSigningKeyService activeSigningKeyService) {
    return new NimbusJwtClientAuthenticationParametersConverter<>(
            clientRegistration -> activeSigningKeyService.current().activeSigningKey());
}
```

### Step 3: Keep request-context and background token managers separate

The token response client uses `RestClientClientCredentialsTokenResponseClient` with a custom `RestClient` that applies per-client TLS trust from the Spring SSL bundle. This eliminates the need for JVM-level truststore overrides.

> **Critical: explicit message converters on the token RestClient.** The `RestClient` used by `RestClientClientCredentialsTokenResponseClient` MUST have `FormHttpMessageConverter` and `OAuth2AccessTokenResponseHttpMessageConverter` explicitly configured. Without them, the RestClient cannot serialize the `application/x-www-form-urlencoded` token request or deserialize the `application/json` token response — the call appears to succeed but returns a null access token (`accessToken cannot be null` at runtime). This is required because the default `RestClient.builder()` message converters do not include the OAuth2-specific response converter.

```java
/**
 * Builds an isolated RestClient for the MCC token endpoint.
 * Applies bounded connect and read timeouts and optional per-client TLS trust
 * from the configured SSL bundle. Falls back to the JVM default trust store
 * when no bundle is configured.
 */
private RestClient buildTokenEndpointRestClient(
        MccRuntimeProperties runtimeProperties, Optional<SslBundles> sslBundles) {

    SSLContext sslContext = null;
    String bundleName = runtimeProperties.getTls().getBundle();
    if (bundleName != null && !bundleName.isBlank() && sslBundles.isPresent()) {
        try {
            SslBundle bundle = sslBundles.get().getBundle(bundleName);
            sslContext = bundle.createSslContext();
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Failed to apply MCC TLS bundle '" + bundleName + "': " + ex.getMessage(), ex);
        }
    }

    HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
            .connectTimeout(runtimeProperties.getConnectTimeout());
    if (sslContext != null) {
        httpClientBuilder.sslContext(sslContext);
    }

    JdkClientHttpRequestFactory factory =
            new JdkClientHttpRequestFactory(httpClientBuilder.build());
    factory.setReadTimeout(runtimeProperties.getReadTimeout());

    return RestClient.builder()
            .requestFactory(factory)
            .messageConverters(converters -> {
                converters.clear();
                converters.add(new FormHttpMessageConverter());
                converters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
            })
            .build();
}
```

```java
@Bean
RestClientClientCredentialsTokenResponseClient mccTokenResponseClient(
        ActiveSigningKeyService activeSigningKeyService,
        MccRuntimeProperties runtimeProperties,
        Optional<SslBundles> sslBundles) {

    RestClient restClient = buildTokenEndpointRestClient(runtimeProperties, sslBundles);

    Function<ClientRegistration, JWK> jwkResolver =
            reg -> activeSigningKeyService.current().activeSigningKey();

    var converter = new NimbusJwtClientAuthenticationParametersConverter<
            OAuth2ClientCredentialsGrantRequest>(jwkResolver);

    RestClientClientCredentialsTokenResponseClient client =
            new RestClientClientCredentialsTokenResponseClient();
    client.setRestClient(restClient);
    client.addParametersConverter(converter);
    return client;
}

@Bean
OAuth2AuthorizedClientManager requestContextClientManager(
        ClientRegistrationRepository registrations,
        OAuth2AuthorizedClientRepository authorizedClientRepository,
        RestClientClientCredentialsTokenResponseClient tokenResponseClient
) {
    OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(configurer -> configurer.accessTokenResponseClient(tokenResponseClient))
            .build();

    DefaultOAuth2AuthorizedClientManager manager =
            new DefaultOAuth2AuthorizedClientManager(registrations, authorizedClientRepository);
    manager.setAuthorizedClientProvider(provider);
    return manager;
}

@Bean
OAuth2AuthorizedClientManager backgroundClientManager(
        ClientRegistrationRepository registrations,
        OAuth2AuthorizedClientService authorizedClientService,
        RestClientClientCredentialsTokenResponseClient tokenResponseClient
) {
    OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(configurer -> configurer.accessTokenResponseClient(tokenResponseClient))
            .build();

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
            new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClientService);
    manager.setAuthorizedClientProvider(provider);
    return manager;
}
```

### Step 4: Make the background token supplier concrete

```java
public interface BackgroundAccessTokenSupplier {
    String getAccessTokenValue();
}

@RequiredArgsConstructor
public class OAuth2BackgroundAccessTokenSupplier implements BackgroundAccessTokenSupplier {

    public static final String REGISTRATION_ID = "mcc-sso-client-credentials";

    private final OAuth2AuthorizedClientManager backgroundClientManager;

    @Override
    public String getAccessTokenValue() {
        Authentication principal = new AnonymousAuthenticationToken(
                "mcc-background",
                "mcc-background",
                AuthorityUtils.createAuthorityList("ROLE_MCC_BACKGROUND"));

        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId(REGISTRATION_ID)
                .principal(principal)
                .build();

        OAuth2AuthorizedClient authorizedClient = backgroundClientManager.authorize(authorizeRequest);
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new IllegalStateException("Unable to acquire MCC background access token.");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }
}
```

### Step 5: Build the two `WebClient` variants

The standard requires single-flight token acquisition: when multiple concurrent callers request a token and the cache is empty or expired, exactly one call must be made to the MCC token endpoint with the result shared to all waiters. Wrap the token supplier with a single-flight guard:

```java
/**
 * Single-flight wrapper that deduplicates concurrent token acquisition calls.
 * When the cache is empty or expired, exactly one call reaches the token endpoint
 * and all concurrent waiters receive the same result.
 */
public class SingleFlightTokenManager {

    private final AtomicReference<CompletableFuture<String>> inflightRef = new AtomicReference<>();
    private final BackgroundAccessTokenSupplier delegate;

    public SingleFlightTokenManager(BackgroundAccessTokenSupplier delegate) {
        this.delegate = delegate;
    }

    public String acquire() {
        CompletableFuture<String> existing = inflightRef.get();
        if (existing != null) {
            return existing.join();
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        if (inflightRef.compareAndSet(null, future)) {
            try {
                String token = delegate.getAccessTokenValue();
                future.complete(token);
                return token;
            } catch (Exception ex) {
                future.completeExceptionally(ex);
                throw ex;
            } finally {
                inflightRef.set(null);
            }
        } else {
            return inflightRef.get().join();
        }
    }
}
```

Use the `SingleFlightTokenManager` in the bearer filter for both WebClients to satisfy the standard's single-flight requirement (Section 2.3 step 5).

```java
@Configuration
public class MccWebClientConfiguration {

    public static final String REGISTRATION_ID = "mcc-sso-client-credentials";

    @Bean
    WebClient requestContextWebClient(
            OAuth2AuthorizedClientManager requestContextClientManager,
            ClientRegistrationRepository registrations
    ) {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(requestContextClientManager);
        oauth.setDefaultClientRegistrationId(REGISTRATION_ID);

        String clientId = registrations.findByRegistrationId(REGISTRATION_ID).getClientId();

        return WebClient.builder()
                .filter(oauth)
                .defaultHeaders(headers -> {
                    headers.set("x-application-id", clientId);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                })
                .build();
    }

    @Bean
    BackgroundAccessTokenSupplier backgroundAccessTokenSupplier(
            OAuth2AuthorizedClientManager backgroundClientManager
    ) {
        return new OAuth2BackgroundAccessTokenSupplier(backgroundClientManager);
    }

    @Bean
    SingleFlightTokenManager singleFlightTokenManager(
            BackgroundAccessTokenSupplier tokenSupplier
    ) {
        return new SingleFlightTokenManager(tokenSupplier);
    }

    @Bean
    WebClient backgroundWebClient(
            SingleFlightTokenManager singleFlightTokenManager,
            ClientRegistrationRepository registrations
    ) {
        String clientId = registrations.findByRegistrationId(REGISTRATION_ID).getClientId();

        ExchangeFilterFunction bearerFilter = (request, next) -> {
            ClientRequest authenticatedRequest = ClientRequest.from(request)
                    .headers(headers -> {
                        headers.setBearerAuth(singleFlightTokenManager.acquire());
                        headers.set("x-application-id", clientId);
                        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                            headers.setContentType(MediaType.APPLICATION_JSON);
                        }
                    })
                    .build();
            return next.exchange(authenticatedRequest);
        };

        return WebClient.builder()
                .filter(bearerFilter)
                .build();
    }

    @Bean
    MccAuthenticatedClientProvider mccAuthenticatedClientProvider(
            @Qualifier("requestContextWebClient") WebClient requestContextWebClient,
            @Qualifier("backgroundWebClient") WebClient backgroundWebClient
    ) {
        return new DefaultMccAuthenticatedClientProvider(requestContextWebClient, backgroundWebClient);
    }
}
```

### Step 6: Document downstream adapter usage

| Client | Use when | Depends on | What it adds |
|:---|:---|:---|:---|
| `requestContextClient()` | Interactive request flows such as MPDS lookups | Servlet request state | OAuth2 bearer auth, `x-application-id`, JSON content type |
| `backgroundClient()` | Schedulers, async handlers, event listeners, MCNS sends | None (servlet-free) | OAuth2 bearer auth via background token supplier, `x-application-id`, JSON content type |

### Step 7: Configure per-client TLS trust for MCC outbound connections

The standard requires configurable per-client TLS trust scoped to MCC connections only (Section 3.5, Section 4.1). When MCC endpoints use non-public certificate authorities (internal CAs, self-signed certificates in SIT/UAT), configure a Spring Boot SSL bundle and apply it to the MCC WebClient connector without modifying the JVM default trust store.

```yaml
spring:
  ssl:
    bundle:
      pem:
        mcc:
          truststore:
            certificate: classpath:certs/mcc-internal-ca.pem
  security:
    eds:
      oauth2:
        client:
          mcc:
            tls:
              bundle: mcc
```

> **Canonical bundle name:** The TLS bundle name used in code to look up the trust store is read from `spring.security.eds.oauth2.client.mcc.tls.bundle`. The default value is `"mcc"`, matching the `spring.ssl.bundle.pem.mcc` definition above. If the bundle is named differently in your YAML (e.g. `mcc-trust`), update the `tls.bundle` property to match. The implementation must use this property value to look up the bundle — never hardcode the bundle name in Java code.

```java
/**
 * Builds the HTTP client connector with optional per-client TLS trust.
 * When mcc.tls.bundle is set, the MCC WebClient trusts only the specified bundle's CA chain.
 * When absent, the JVM default trust store applies.
 */
private ClientHttpConnector buildMccConnector(
        MccRuntimeProperties runtimeProperties, Optional<SslBundles> sslBundles) {

    String bundleName = runtimeProperties.getTls().getBundle();
    if (bundleName != null && !bundleName.isBlank() && sslBundles.isPresent()) {
        SslBundle bundle = sslBundles.get().getBundle(bundleName);
        TrustManagerFactory tmf = bundle.getManagers().getTrustManagerFactory();
        SslContext nettySslContext = SslContextBuilder.forClient()
                .trustManager(tmf)
                .build();
        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(nettySslContext));
        return new ReactorClientHttpConnector(httpClient);
    }
    return new ReactorClientHttpConnector();
}
```

Apply this connector when building both WebClient variants:

```java
return WebClient.builder()
        .clientConnector(buildMccConnector(runtimeProperties, sslBundles))
        .filter(oauth)
        .defaultHeaders(headers -> {
            headers.set("x-application-id", clientId);
            headers.setContentType(MediaType.APPLICATION_JSON);
        })
        .build();
```

This ensures TLS trust is scoped to MCC outbound calls only — other WebClients in the application remain unaffected.

## Recipe 5: Add Protected Generated-Key Persistence and Rotation

This recipe makes durable key history and scheduled rotation available only when needed. Fixed-key apps avoid unnecessary database, scheduler, and lock dependencies.

### Step 1: Require protected storage for generated JWK material

Reuse `JwkMaterialProtector` from Recipe 2. The backing implementation — KMS, Vault, database encryption, or equivalent — is up to the platform. Raw JWK JSON must never be stored as plaintext.

### Step 2: Schedule rotation from a dedicated runtime component

```java
@Component
@ConditionalOnProperty(
        prefix = "spring.security.eds.oauth2.client.mcc",
        name = "generated-key-mode-enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class MccKeyRotator {

    private final ActiveSigningKeyService activeSigningKeyService;

    @Scheduled(
            cron = "${spring.security.eds.oauth2.client.mcc.key-rotation-duration:0 0 0 1 1 *}",
            zone = "${spring.security.eds.oauth2.client.mcc.key-rotation-zone:Asia/Singapore}"
    )
    @SchedulerLock(name = "mccSharedAuthKeyRotation")
    public void rotate() {
        activeSigningKeyService.rotate();
    }
}
```

This component is only created when generated-key mode is enabled. Fixed-key mode needs neither the scheduler nor the shared lock table.

### Step 3: Provide generated-mode beans conditionally

```java
@Configuration
@EnableConfigurationProperties({MccClientKeyProperties.class, MccRuntimeProperties.class})
public class MccSharedAuthConfiguration {

    @Bean
    MccRotationProperties mccRotationProperties(MccRuntimeProperties runtimeProperties) {
        return runtimeProperties.toRotationProperties();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ActiveSigningKeyService activeSigningKeyService(
            MccClientKeyProperties keyProperties,
            MccRuntimeProperties runtimeProperties,
            ObjectProvider<GeneratedSigningKeyStore> generatedSigningKeyStoreProvider,
            ObjectProvider<JwkMaterialProtector> jwkMaterialProtectorProvider,
            Clock clock
    ) {
        return new DefaultActiveSigningKeyService(
                keyProperties,
                runtimeProperties,
                generatedSigningKeyStoreProvider,
                jwkMaterialProtectorProvider,
                clock
        );
    }
}
```

If the app supplies configured key material and disables generated mode, it can skip all of these:

* `GeneratedSigningKeyStore`
* `JwkMaterialProtector`
* `MccKeyRotator`
* `@SchedulerLock` infrastructure

## Recipe 6: End-to-End Spring Reference

This is the end-to-end shape for a Spring service that wants one shared-auth module.

### Fixed-Key Mode

1. Provide `MccClientKeyProperties` with `kid`, `publicKey`, and `privateKey`.
2. Set `generated-key-mode-enabled: false`.
3. Start `MccSharedAuthConfigurationValidator`.
4. Bootstrap `ActiveSigningKeyService`.
5. Publish `/.well-known/jwks.json` from `JwksController`.
6. Inject `MccAuthenticatedClientProvider` into MPDS, MCNS, or other outbound adapters.

In this slice there is no generated-key store, no key protector, no scheduler, and no distributed lock.

### Generated-Key Mode

1. Provide `MccClientKeyProperties` with no configured key material.
2. Set `generated-key-mode-enabled: true`.
3. Provide `GeneratedSigningKeyStore`, `JwkMaterialProtector`, and shared lock infrastructure.
4. Bootstrap `ActiveSigningKeyService`.
5. On first startup, generate a key, protect it, and save it as the active record.
6. Publish one public key through `JwksController` in steady state.
7. On rotation, persist the new active key, keep the previous public key published through the overlap window, then retire it.
8. Use the same `MccAuthenticatedClientProvider` boundary for MPDS, MCNS, schedulers, and listeners.

### Runtime Interaction

```mermaid
sequenceDiagram
    participant App as App / Adapter
    participant Auth as Shared MCC Auth
    participant KeySvc as ActiveSigningKeyService
    participant Jwks as JwksController / PublishedJwksView
    participant IdP as MCC Token Endpoint / IdP
    participant API as MPDS / MCNS

    App->>Auth: Need MCC-authenticated outbound call
    Auth->>KeySvc: Resolve active key for mcc-sso-client-credentials
    KeySvc-->>Auth: Active signing key
    Auth->>IdP: client_credentials + private_key_jwt assertion
    IdP-->>Auth: Access token
    Auth->>API: HTTP request + Bearer token + x-application-id
    API-->>Auth: Response
    Auth-->>App: Response

    IdP->>Jwks: GET /.well-known/jwks.json
    Jwks->>KeySvc: Resolve published public keys
    KeySvc-->>Jwks: PublishedJwksView
    Jwks-->>IdP: JWKS payload
```

### Wiring Diagram

```mermaid
flowchart TD
    Props[MccClientKeyProperties + MccRuntimeProperties] --> Validator[MccSharedAuthConfigurationValidator]
    Validator --> KeySvc[ActiveSigningKeyService]
    Store[GeneratedSigningKeyStore] --> KeySvc
    Protector[JwkMaterialProtector] --> KeySvc
    KeySvc --> Jwks[JwksController]
    KeySvc --> Assertion[Nimbus private_key_jwt converter]
    Assertion --> ReqMgr[Request-context authorized client manager]
    Assertion --> BgMgr[Background authorized client manager]
    BgMgr --> BgSupplier[BackgroundAccessTokenSupplier]
    BgSupplier --> SFlight[SingleFlightTokenManager]
    ReqMgr --> ReqClient[requestContextWebClient]
    SFlight --> BgClient[backgroundWebClient]
    TLS[TLS Bundle - optional] --> ReqClient
    TLS --> BgClient
    ReqClient --> Provider[MccAuthenticatedClientProvider]
    BgClient --> Provider
    Rotator[MccKeyRotator] --> KeySvc
```

## Recipe 7: Verify the Foundation with Tests

This recipe defines the minimum tests for the shared-auth behavior. Keep fast unit tests around key and mode logic, component tests around Spring wiring and HTTP-facing behavior, and integration tests around token exchange, downstream calls, and multi-node rotation safety.

### Step 1: Verify configured key material and startup validation

```java
@Test
void buildsConfiguredEcKey() {
    JWK jwk = JWKSUtil.buildJWK(kid, ecPublicKey, ecPrivateKey);
    assertThat(jwk.getKeyID()).isEqualTo(kid);
}

@Test
void buildsConfiguredRsaKey() {
    JWK jwk = JWKSUtil.buildJWK(kid, rsaPublicKey, rsaPrivateKey);
    assertThat(jwk.getKeyID()).isEqualTo(kid);
}

@Test
void rejectsUnknownKeyType() {
    assertThatThrownBy(() -> JWKSUtil.buildJWK("kid", "bad", "bad"))
            .isInstanceOf(UnknownKeyTypeException.class);
}

@Test
void rejectsPartialConfiguredKeyMaterialBeforeJwkBuild() {
    MccClientKeyProperties properties = keyPropertiesWith("kid", ecPublicKey, "");
    MccSharedAuthConfigurationValidator validator = validatorFor(properties, generatedMode(false));

    assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("kid, publicKey, and privateKey");
}

@Test
void rejectsMissingConfiguredKeyMaterialWhenGeneratedModeIsDisabled() {
    MccSharedAuthConfigurationValidator validator =
            validatorFor(emptyKeyProperties(), generatedMode(false));

    assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Generated-key mode must be enabled");
}
```

### Step 2: Verify OAuth2 registration requirements

```java
@Test
void missingMccRegistrationFailsStartup() {
    MccSharedAuthConfigurationValidator validator =
            validatorWithRegistration(null, configuredKeyProperties, generatedMode(false));

    assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing OAuth2 registration");
}

@Test
void nonClientCredentialsGrantFailsStartup() {
    ClientRegistration registration = registrationWithGrant(AuthorizationGrantType.AUTHORIZATION_CODE);
    MccSharedAuthConfigurationValidator validator =
            validatorWithRegistration(registration, configuredKeyProperties, generatedMode(false));

    assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("client_credentials");
}

@Test
void nonPrivateKeyJwtAuthenticationFailsStartup() {
    ClientRegistration registration =
            registrationWithClientAuthentication(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    MccSharedAuthConfigurationValidator validator =
            validatorWithRegistration(registration, configuredKeyProperties, generatedMode(false));

    assertThatThrownBy(validator::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("private_key_jwt");
}
```

### Step 3: Verify fixed-key mode has no generated-key dependency

These tests use the tiny `StaticObjectProvider` helper shown in Step 4.

```java
@Test
void fixedKeyModeInitializesWithoutGeneratedKeyInfrastructure() {
    MccRuntimeProperties runtimeProperties = new MccRuntimeProperties(false, Duration.ofHours(24), null, null);
    ActiveSigningKeyService service = new DefaultActiveSigningKeyService(
            configuredKeyProperties,
            runtimeProperties,
            new StaticObjectProvider<>(null),
            new StaticObjectProvider<>(null),
            Clock.systemUTC()
    );

    ActiveSigningKeySnapshot snapshot = service.initialize();

    assertThat(snapshot.source()).isEqualTo(SigningKeySource.CONFIGURED);
}
```

### Step 4: Verify generated mode dependencies and protected persistence

```java
private static final class StaticObjectProvider<T> implements ObjectProvider<T> {
    private final T value;

    private StaticObjectProvider(T value) {
        this.value = value;
    }

    @Override
    public T getObject(Object... args) {
        return value;
    }

    @Override
    public T getObject() {
        return value;
    }

    @Override
    public T getIfAvailable() {
        return value;
    }

    @Override
    public T getIfUnique() {
        return value;
    }

    @Override
    public Iterator<T> iterator() {
        return value == null ? Collections.emptyIterator() : Collections.singleton(value).iterator();
    }
}

@Test
void generatedModeRequiresProtectedPersistence() {
    MccRuntimeProperties runtimeProperties = new MccRuntimeProperties(true, Duration.ofHours(24), null, null);
    ActiveSigningKeyService service = new DefaultActiveSigningKeyService(
            emptyKeyProperties,
            runtimeProperties,
            new StaticObjectProvider<>(null),
            new StaticObjectProvider<>(jwkMaterialProtector),
            Clock.systemUTC()
    );

    assertThatThrownBy(service::initialize)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("GeneratedSigningKeyStore");
}

@Test
void generatedModeRequiresJwkMaterialProtector() {
    MccRuntimeProperties runtimeProperties = new MccRuntimeProperties(true, Duration.ofHours(24), null, null);
    ActiveSigningKeyService service = new DefaultActiveSigningKeyService(
            emptyKeyProperties,
            runtimeProperties,
            new StaticObjectProvider<>(generatedSigningKeyStore),
            new StaticObjectProvider<>(null),
            Clock.systemUTC()
    );

    assertThatThrownBy(service::initialize)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JwkMaterialProtector");
}

@Test
void generatedKeyHistoryStoresCiphertextInsteadOfRawJwkJson() {
    activeSigningKeyService.initialize();

    ProtectedGeneratedSigningKeyRecord record = generatedSigningKeyStore.loadActiveSigningKey().orElseThrow();

    assertThat(record.encryptedJwk()).isNotBlank();
    assertThat(record.encryptedJwk()).doesNotContain("\"d\"");
    assertThat(record.encryptedJwk()).isNotEqualTo(activeSigningKeyService.current().activeSigningKey().toJSONString());
}
```

### Step 5: Verify generated-key rotation and rollover safety

```java
@Test
void rotationPublishesPreviousAndCurrentPublicKeysDuringOverlap() {
    ActiveSigningKeySnapshot rotated = activeSigningKeyService.rotate();

    PublishedJwksView jwksView = rotated.publishedJwksView(fixedClockBeforeOverlapExpiry);

    assertThat(jwksView.publicKeys()).hasSize(2);
    assertThat(jwksView.publicKeys()).extracting(JWK::getKeyID)
            .contains(rotated.activeSigningKey().getKeyID(), rotated.previousPublicKey().getKeyID());
}

@Test
void overlapExpiryRemovesPreviousPublicKey() {
    ActiveSigningKeySnapshot rotated = activeSigningKeyService.rotate();

    PublishedJwksView beforeExpiry = rotated.publishedJwksView(fixedClockBeforeOverlapExpiry);
    PublishedJwksView atExpiry = rotated.publishedJwksView(fixedClockAtOverlapExpiry);
    PublishedJwksView afterExpiry = rotated.publishedJwksView(fixedClockAfterOverlapExpiry);

    assertThat(beforeExpiry.publicKeys()).hasSize(2);
    assertThat(atExpiry.publicKeys()).hasSize(1);
    assertThat(afterExpiry.publicKeys()).hasSize(1);
}

@Test
void rotationFailureKeepsPreviousSigningAndPublicationState() {
    ActiveSigningKeySnapshot before = activeSigningKeyService.current();
    given(generatedSigningKeyStore.rotateTo(any(), any())).willThrow(new IllegalStateException("store down"));

    assertThatThrownBy(activeSigningKeyService::rotate)
            .isInstanceOf(IllegalStateException.class);

    ActiveSigningKeySnapshot after = activeSigningKeyService.current();
    assertThat(after.activeSigningKey().getKeyID()).isEqualTo(before.activeSigningKey().getKeyID());
    assertThat(after.publishedJwksView(clock).publicKeys()).extracting(JWK::getKeyID)
            .containsExactlyElementsOf(before.publishedJwksView(clock).publicKeys()
                    .stream()
                    .map(JWK::getKeyID)
                    .toList());
}

@Test
void newAssertionsUseOnlyCurrentKeyDuringOverlap() {
    ActiveSigningKeySnapshot rotated = activeSigningKeyService.rotate();

    JWK signingKey = activeSigningKeyService.current().activeSigningKey();

    assertThat(signingKey.getKeyID()).isEqualTo(rotated.activeSigningKey().getKeyID());
    assertThat(signingKey.getKeyID()).isNotEqualTo(rotated.previousPublicKey().getKeyID());
}
```

### Step 6: Verify public-only JWKS publication and readiness

```java
@Test
void jwksSteadyStatePublishesOnePublicKeyOnly() throws Exception {
    String body = objectMapper.writeValueAsString(controller.keys());

    assertThat(body).contains("\"keys\"");
    assertThat(body).doesNotContain("\"d\"");
    assertThat(body).doesNotContain("\"p\"");
    assertThat(body).doesNotContain("\"q\"");
}

@Test
void jwksEndpointFailsWhenNoPublicKeyIsAvailable() {
    given(activeSigningKeyService.current()).willThrow(new IllegalStateException("No active key"));

    assertThatThrownBy(controller::keys)
            .isInstanceOf(IllegalStateException.class);
}

@Test
void readinessFailsWhenNoPublicKeyCanBePublished() {
    given(activeSigningKeyService.current()).willThrow(new IllegalStateException("No active key"));

    Health health = readinessIndicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
}

@Test
void readinessSucceedsWhenPublicJwksViewExists() {
    Health health = readinessIndicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
}
```

### Step 7: Verify the outbound client contract in both execution modes

```java
private MockWebServer server;

@BeforeEach
void startServer() throws IOException {
    server = new MockWebServer();
    server.start();
}

@AfterEach
void stopServer() throws IOException {
    server.shutdown();
}

private RecordedRequest executeRecordedCall(WebClient client, MockWebServer server) {
    server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody("{\"ok\":true}"));

    client.post()
            .uri(server.url("/mcc").uri())
            .bodyValue(Map.of("hello", "world"))
            .retrieve()
            .bodyToMono(String.class)
            .block();

    return server.takeRequest();
}

@Test
void requestContextClientAddsHeadersAndBearerToken() {
    RecordedRequest request = executeRecordedCall(provider.requestContextClient(), server);

    assertThat(request.getHeader("x-application-id")).isEqualTo("app-marketplace-dev");
    assertThat(request.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).startsWith("Bearer ");
}

@Test
void backgroundClientWorksWithoutServletRequestState() {
    RecordedRequest request = executeRecordedCall(provider.backgroundClient(), server);

    assertThat(request.getHeader("x-application-id")).isEqualTo("app-marketplace-dev");
    assertThat(request.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).startsWith("Bearer ");
}
```

### Step 8: Verify token acquisition and downstream blocking

```java
@Test
void tokenEndpointRequiresPrivateKeyJwt() {
    tokenServer.enqueue(assertingTokenResponse(request -> {
        assertThat(request.formParameter("grant_type")).isEqualTo("client_credentials");
        assertThat(request.formParameter("client_assertion_type")).contains("jwt-bearer");
        assertThat(request.formParameter("client_assertion")).isNotBlank();
    }));
    mccApiServer.enqueue(new MockResponse().setBody("{\"ok\":true}"));

    provider.backgroundClient().get()
            .uri(mccApiServer.url("/mcc").uri())
            .retrieve()
            .bodyToMono(String.class)
            .block();

    assertThat(mccApiServer.getRequestCount()).isEqualTo(1);
}

@Test
void tokenFailurePreventsDownstreamRequest() {
    tokenServer.enqueue(new MockResponse().setResponseCode(401));
    mccApiServer.enqueue(new MockResponse().setBody("{\"shouldNotBeCalled\":true}"));

    assertThatThrownBy(() -> provider.backgroundClient().get()
            .uri(mccApiServer.url("/mcc").uri())
            .retrieve()
            .bodyToMono(String.class)
            .block())
            .isInstanceOf(RuntimeException.class);

    assertThat(mccApiServer.getRequestCount()).isZero();
}
```

### Step 9: Verify multi-node rotation coordination

Use this as an integration test with the real shared lock provider used by the application environment.

```text
Given two application instances share the same generated-key store and lock provider
When both instances trigger generated-key rotation at the same time
Then only one instance acquires the lock and switches the active signing key
And the generated-key store has one new active key
And the JWKS view contains the new current public key plus the previous public key during overlap
And the instance that did not acquire the lock keeps serving with a valid active key state
```

### Step 10: Verify startup audit or logs record selected key source

```java
@Test
void startupRecordsSelectedKeySource() {
    ActiveSigningKeySnapshot snapshot = activeSigningKeyService.initialize();

    assertThat(snapshot.source()).isIn(SigningKeySource.CONFIGURED, SigningKeySource.GENERATED);
}
```


## Recipe 8: Logging & Observability

The shared auth module must provide structured audit logging for security-relevant operations. This is an IM8 requirement — the code review will flag its absence as a HIGH finding.

### Required log events

| Event | Level | What to log | What NEVER to log |
|:---|:---|:---|:---|
| Key source selected at startup | INFO | Source (`CONFIGURED` / `GENERATED`), `kid` | Private key material |
| Token acquisition success | DEBUG | Registration ID, token expiry timestamp | Token value |
| Token acquisition failure | WARN | Registration ID, error message, stack trace | Token value, credentials |
| Client assertion signing failure | ERROR | Client ID, `kid`, error message | Private key, assertion JWT |
| Key rotation success | INFO | Old `kid`, new `kid`, overlap expiry | Key material |
| Key rotation failure | ERROR | Error message, stack trace | Key material |
| TLS bundle resolution failure | WARN | Bundle name, error message | Certificate content |

### Implementation requirements

1. **Use SLF4J** (`LoggerFactory.getLogger(...)`) — no `System.out`, no framework-specific logging.
2. **Include MDC context** where available (`trace.id`, `correlation.id`) so auth events can be correlated with request traces.
3. **Never log token values, private keys, or credentials** at any level including DEBUG/TRACE.
4. **Log at the point of failure** — the class that catches the exception should log it, not a caller further up the stack. Silent `catch (Exception ignored)` blocks are not acceptable in production auth code.
5. **Token acquisition logging** should be at DEBUG for success (high volume) and WARN for failure (actionable). Do not log every successful acquisition at INFO — it generates noise.

### Metrics (optional, recommended)

If Micrometer is on the classpath, expose:
- `mcc_token_acquisition_total` (counter, tagged `outcome=success|failure`)
- `mcc_token_acquisition_duration_seconds` (timer)
- `mcc_key_rotation_total` (counter, tagged `outcome=success|failure`)

These enable alerting on repeated token failures or rotation issues without parsing logs.


## Recipe 9: Deployed Environment Profile Configuration (SIT)

**Goal**: Configure profile-specific environment variables for deployed environments (SIT/UAT/Prod) using `.env.sit` and `application-sit.yml`.

Per the [Bootstrap Profile Configuration Contract](../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md#36-profile-configuration-contract), token URI, client ID, and signing key material are environment-specific and MUST NOT live as hardcoded literals in `application.yml`. The SIT YAML uses `${VAR:dev-mcc-default}` for every property (defaults copied from the dev-mcc profile in [Recipe 1](#recipe-1-define-configuration-and-mode-selection)). Real SIT onboarded values are stored in `.env.sit` (gitignored, loaded by the bootstrap `ProfileDotenvPostProcessor`) and override the defaults at runtime.

### Step 1: Declare Profile Overrides in `application-sit.yml`

Add these env var keys to `application-sit.yml`, falling back to the dev-mcc defaults from Recipe 1:

| Env var | Purpose |
|:---|:---|
| `MCC_M2M_TOKEN_URI` | MCC client-credentials token endpoint |
| `MCC_CLIENT_ID_M2M` | OAuth2 client id (also the outbound `x-application-id`) |
| `MCC_SCOPE` | OAuth2 scopes (comma-separated, project-specific — include each downstream service scope: `mcns`, `mds`, etc.) |
| `MCC_KEY_ID` | Signing key `kid` |
| `MCC_PUBLIC_KEY` | Base64-encoded public key PEM (i.e. `base64(entire_PEM_file)` including `-----BEGIN PUBLIC KEY-----` headers) |
| `MCC_PRIVATE_KEY` | Base64-encoded private key PEM (i.e. `base64(entire_PEM_file)` including `-----BEGIN PRIVATE KEY-----` headers) |
| `MCC_TLS_BUNDLE` | Spring SSL bundle name |
| `MCC_TRUST_CERT` | Path to the SIT CA trust PEM |

Actual SIT onboarded values are given in the `.env.sit.example` template in Step 2.

Switching between local dev and SIT is done purely by selecting the Spring profile (`SPRING_PROFILES_ACTIVE=dev-mcc` or `sit`); no additional per-target selection step is needed. Without a `.env.sit` file, the SIT profile falls back to dev-mcc defaults and works against the local Docker mock stack.

> **TLS trust for token acquisition:** The `private_key_jwt` token acquisition uses Spring Security's `RestClientClientCredentialsTokenResponseClient`, which is `RestClient`-based. A custom `RestClient` is injected with an `SSLContext` built from the Spring Boot SSL bundle (`spring.ssl.bundle.pem.mcc` or the bundle named in `mcc.tls.bundle`). This means both token acquisition and downstream WebClient calls trust the same CA chain — no JVM-level truststore override (`-Djavax.net.ssl.trustStore`) or `dev-truststore.jks` is required. When no TLS bundle is configured (e.g. the IdP uses a publicly-trusted CA), the JVM default trust store applies.

### Step 2: Create `.env.sit.example`

Provide a `.env.sit.example` template at the repository root to guide deployment engineers. The developer copies this to `.env.sit` (which is gitignored) for local verification.

Dev-only generated sample keys may be committed for the `dev-mcc` profile, but SIT/UAT/prod
key material must not be committed in this recipe or in `.env.sit.example`. Use placeholders
in deployed-environment documentation and load real values from the environment's secret store.

```env
# MCC Shared Auth Foundation SIT Configuration
MCC_M2M_TOKEN_URI=https://sit.auth-ecs.defcloud.gov.sg/auth/realms/SSO/protocol/openid-connect/token
MCC_CLIENT_ID_M2M=<mcc-client-id-m2m-from-onboarding>
MCC_SCOPE=mcns
MCC_KEY_ID=<mcc-key-id-from-onboarding>
MCC_PUBLIC_KEY=<base64-public-key-pem-from-secret-store>
MCC_PRIVATE_KEY=<base64-private-key-pem-from-secret-store>
MCC_TLS_BUNDLE=mcc
MCC_TRUST_CERT=classpath:combined-trust.pem
```
