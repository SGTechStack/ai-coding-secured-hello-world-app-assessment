# MCC SSO Provider Hint and Private Key JWT

## 1. Introduction

This guide shows how to implement the MCC-specific parts of the SSO flow: adding an identity-provider hint to the outbound authorization request, using private key JWT client authentication during the authorization-code token exchange, configuring stable principal resolution from nested token claims, and configuring integration with the Profile Data Service. It includes security validation, error handling, and comprehensive audit logging.

This matters because the SSO starter contains explicit MCC-specific behavior beyond generic OAuth2 login. By the end, the application will customize the authorization request for MCC SSO (which strictly mandates PKCE using the S256 challenge method), sign the token request with a client assertion, resolve identities consistently using framework-native configuration, and integrate with profile attributes.

## 2. Prerequisites

- Spring Boot 4.x with Spring Security 7.x
- `spring-boot-starter-oauth2-client`
- JWK material (RSA private key) available to the application
- Secure storage for JWK (e.g., environment variables or secret management)
- An MCC SSO client registration such as `mcc-sso-login`
- Lombok (for @Slf4j logging)

## 3. Steps

### 1. Define the MCC registration metadata securely

Use a dedicated registration so the application can apply issuer-specific behavior. The registration ID is conventionally `mcc-sso-login` to activate MCC-specific handlers. If you choose to change this registration ID, ensure the new value is updated across all MCC-specific handlers and that the corresponding redirect URI is correctly onboarded via MTM.

```yaml
# File: src/main/resources/application.yml
spring:
  security:
    sso:
      oauth2:
        client:
          registration:
            mcc-sso-login:
              preferred-identity-provider: "singpass"
              # JWK properties for signing client assertions (Strictly for local testing/development).
              # In higher environments, the public key should be exposed via an application JWKS URI (infra side) for the IdP to fetch.
              kid: ${MCC_SSO_LOGIN_DEV_JWK_KID:""}
              public-key: ${MCC_SSO_LOGIN_DEV_JWK_PUBLIC_KEY:""}
              private-key: ${MCC_SSO_LOGIN_DEV_JWK_PRIVATE_KEY:""}
            mcc-sso-client-credentials:
              # JWK properties for signing client assertions (Strictly for local testing/development).
              # In higher environments, the public key should be exposed via an application JWKS URI (infra side) for the IdP to fetch.
              kid: ${MCC_SSO_CC_DEV_JWK_KID:""}
              public-key: ${MCC_SSO_CC_DEV_JWK_PUBLIC_KEY:""}
              private-key: ${MCC_SSO_CC_DEV_JWK_PRIVATE_KEY:""}
        # Profile Data Service template configuration
        mpds:
          templateId: ${MCC_MPDS_TEMPLATE_ID:""}
      # Profile Data Service endpoints configuration
      mcc:
        mpds:
          clientId: ${MCC_MPDS_CLIENT_ID:""}
          url: "https://${MCC_MPDS_DOMAIN:sit.mds-ecs.defcloud.gov.sg}/retrieve"

    oauth2:
      client:
        provider:
          mcc-sso-login:
            issuer-uri: "https://${MCC_SSO_DOMAIN:sit.auth.defcloud.gov.sg}/auth/realms/SSO"
            authorization-uri: "https://${MCC_SSO_DOMAIN:sit.auth.defcloud.gov.sg}/auth/realms/SSO/protocol/openid-connect/auth"
            jwk-set-uri: "https://${MCC_SSO_DOMAIN:sit.auth.defcloud.gov.sg}/auth/realms/SSO/protocol/openid-connect/certs"
            token-uri: "https://${MCC_SSO_DOMAIN:sit.auth.defcloud.gov.sg}/auth/realms/SSO/protocol/openid-connect/token"
          mcc-sso-client-credentials:
            token-uri: "https://${MCC_SSO_ECS_DOMAIN:sit.auth-ecs.defcloud.gov.sg}/auth/realms/SSO/protocol/openid-connect/token"
        registration:
          mcc-sso-login:
            client-id: ${MCC_SSO_LOGIN_CLIENT_ID:""}
            authorization-grant-type: "authorization_code"
            client-authentication-method: "private_key_jwt"
            scope: "openid, afcas"
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
          mcc-sso-client-credentials:
            client-id: ${MCC_SSO_CC_CLIENT_ID:""}
            authorization-grant-type: "client_credentials"
            client-authentication-method: "private_key_jwt"
            scope: "mds, mcns, ssft"
```

### 2. Implement JWK validation and management

Create configuration and resolver beans to load the JWK properties.

```java
// File: src/main/java/com/example/sso/SsoClientProperties.java
@Data
@ConfigurationProperties(prefix = "spring.security.sso.oauth2.client")
public class SsoClientProperties {
    private Map<String, Registration> registration = new HashMap<>();

    @Data
    public static class Registration {
        private String preferredIdentityProvider;
        private String kid;
        private String publicKey;
        private String privateKey;
    }
}

// File: src/main/java/com/example/sso/SsoJwkResolver.java
@Component
public class SsoJwkResolver {

    private final SsoClientProperties ssoClientProperties;

    public SsoJwkResolver(SsoClientProperties ssoClientProperties) {
        this.ssoClientProperties = ssoClientProperties;
    }

    public JWK resolveJwk(ClientRegistration clientRegistration) {
        String registrationId = clientRegistration.getRegistrationId();
        SsoClientProperties.Registration reg = ssoClientProperties.getRegistration().get(registrationId);

        if (reg == null || reg.getPrivateKey() == null) {
            throw new IllegalStateException("No signing key configured for registration: " + registrationId);
        }

        try {
            RSAPublicKey publicKey = parsePublicKey(reg.getPublicKey());
            RSAPrivateKey privateKey = parsePrivateKey(reg.getPrivateKey());

            return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(reg.getKid())
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JWK for registration: " + registrationId, e);
        }
    }

    private RSAPublicKey parsePublicKey(String key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s+", ""));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(spec);
    }

    private RSAPrivateKey parsePrivateKey(String key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", ""));
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }
}
```

### 3. Customize the authorization request

Add the identity provider hint parameter (e.g., `kc_idp_hint=singpass`) only when the request targets the `mcc-sso-login` registration.

```java
// File: src/main/java/com/example/sso/SsoAuthorizationRequestResolver.java
@Component
public class SsoAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final SsoClientProperties ssoClientProperties;
    private final String mccRegistrationId = "mcc-sso-login";

    public SsoAuthorizationRequestResolver(
            ClientRegistrationRepository registrations,
            SsoClientProperties ssoClientProperties) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, "/oauth2/authorization");
        this.ssoClientProperties = ssoClientProperties;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest original = delegate.resolve(request);
        return customize(original, resolveRegistrationId(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        OAuth2AuthorizationRequest original = delegate.resolve(request, registrationId);
        return customize(original, registrationId);
    }

    private OAuth2AuthorizationRequest customize(OAuth2AuthorizationRequest original, String registrationId) {
        if (original == null || !mccRegistrationId.equals(registrationId)) {
            return original;
        }

        SsoClientProperties.Registration reg = ssoClientProperties.getRegistration().get(registrationId);
        if (reg == null || reg.getPreferredIdentityProvider() == null) {
            return original;
        }

        Map<String, Object> additionalParameters = new LinkedHashMap<>(original.getAdditionalParameters());
        additionalParameters.put("kc_idp_hint", reg.getPreferredIdentityProvider());

        return OAuth2AuthorizationRequest.from(original)
                .additionalParameters(additionalParameters)
                .build();
    }

    private String resolveRegistrationId(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && path.contains("/oauth2/authorization/")) {
            return path.substring(path.lastIndexOf("/") + 1);
        }
        return null;
    }
}
```

### 4. Configure private key JWT client authentication

Override the token response client to sign assertions using the custom JWK resolver.

```java
// File: src/main/java/com/example/sso/SsoPrivateKeyJwtTokenClient.java
@Configuration
public class SsoPrivateKeyJwtTokenClient {

    @Bean
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> ssoTokenClient(
            SsoJwkResolver jwkResolver) {

        RestClientAuthorizationCodeTokenResponseClient client =
            new RestClientAuthorizationCodeTokenResponseClient();

        // Injects the client assertion into the token request automatically
        client.addParametersConverter(
            new NimbusJwtClientAuthenticationParametersConverter<>(clientRegistration -> {
                try {
                    JWK jwk = jwkResolver.resolveJwk(clientRegistration);
                    return new ImmutableJWKSet<>(new JWKSet(jwk));
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to resolve JWK for authentication", e);
                }
            })
        );

        return client;
    }
}
```

### 5. Configure stable principal resolution

Because the stable UUID is nested within the `afcas` claim (i.e., `afcas.uuid`), override `getName()` using a custom OidcUser wrapper, and swap the authentication token principal during login success.

```java
// File: src/main/java/com/example/sso/SsoOidcUser.java
public class SsoOidcUser extends DefaultOidcUser {

    public SsoOidcUser(Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken, OidcUserInfo userInfo) {
        super(authorities, idToken, userInfo);
    }

    @Override
    public String getName() {
        // Extract the nested 'afcas.uuid' claim from the ID token for stable principal resolution
        Object afcasObj = getAttributes().get("afcas");
        if (afcasObj instanceof Map<?, ?> afcasMap && afcasMap.get("uuid") instanceof String uuid) {
            return uuid;
        }

        // Fallback for flattened claim
        if (getAttributes().get("afcas.uuid") instanceof String flatUuid) {
            return flatUuid;
        }
        return super.getName();
    }
}

// File: src/main/java/com/example/sso/SsoLoginSuccessHandler.java
@Component
public class SsoLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final String mccRegistrationId = "mcc-sso-login";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        
        if (authentication instanceof OAuth2AuthenticationToken token && 
                mccRegistrationId.equals(token.getAuthorizedClientRegistrationId())) {
            
            DefaultOidcUser oidcUser = (DefaultOidcUser) token.getPrincipal();
            SsoOidcUser ssoOidcUser = new SsoOidcUser(
                    oidcUser.getAuthorities(),
                    oidcUser.getIdToken(),
                    oidcUser.getUserInfo()
            );

            OAuth2AuthenticationToken updatedToken = new OAuth2AuthenticationToken(
                    ssoOidcUser,
                    token.getAuthorities(),
                    token.getAuthorizedClientRegistrationId()
            );
            updatedToken.setDetails(token.getDetails());
            SecurityContextHolder.getContext().setAuthentication(updatedToken);
            authentication = updatedToken;
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
```

### 6. Wire components into OAuth2 login

```java
// File: src/main/java/com/example/security/SsoSecurityConfig.java
@Configuration
@EnableWebSecurity
public class SsoSecurityConfig {

    @Bean
    SecurityFilterChain appSecurity(
            HttpSecurity http,
            SsoAuthorizationRequestResolver ssoResolver,
            SsoLoginSuccessHandler successHandler,
            OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> ssoTokenClient) throws Exception {

        http.oauth2Login(oauth2 -> oauth2
            .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(ssoResolver))
            .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(ssoTokenClient))
            .successHandler(successHandler)
        );

        return http.build();
    }
}
```

### 7. Configure Profile Data Service integration (Optional)

Configure properties to connect and fetch user attributes securely from the Profile Data Service using the templates registration.

```java
// File: src/main/java/com/example/sso/SsoMpdsProperties.java
@Data
@ConfigurationProperties(prefix = "spring.security.sso")
public class SsoMpdsProperties {
    private Mcc mcc = new Mcc();
    private Oauth2 oauth2 = new Oauth2();

    @Data
    public static class Mcc {
        private Mpds mpds = new Mpds();
    }

    @Data
    public static class Mpds {
        private String clientId;
        private String url;
    }

    @Data
    public static class Oauth2 {
        private MpdsTemplate mpds = new MpdsTemplate();
    }

    @Data
    public static class MpdsTemplate {
        private String templateId;
    }
}

// File: src/main/java/com/example/sso/SsoProfileService.java
@Service
@Slf4j
public class SsoProfileService {

    private final SsoMpdsProperties mpdsProperties;
    private final RestClient restClient;

    public SsoProfileService(SsoMpdsProperties mpdsProperties, RestClient.Builder restClientBuilder) {
        this.mpdsProperties = mpdsProperties;
        this.restClient = restClientBuilder.build();
    }

    public Map<String, Object> retrieveProfile(String uuid) {
        String url = mpdsProperties.getMcc().getMpds().getUrl();
        String clientId = mpdsProperties.getMcc().getMpds().getClientId();
        String templateId = mpdsProperties.getOauth2().getMpds().getTemplateId();

        log.atInfo()
           .setMessage("Retrieving profile from user data service")
           .addKeyValue("user.id", uuid)
           .addKeyValue("query.template.id", templateId)
           .addKeyValue("client.id", clientId)
           .addKeyValue("event.action", "profile-read")
           .log();
        // Execute request to MPDS using templateId and clientId parameters
        return Map.of("uuid", uuid, "status", "success");
    }
}
```

## 4. Examples

### MCC SSO authorization request

```
GET /oauth2/authorization/mcc-sso-login HTTP/1.1

# Redirects to IdP with:
# kc_idp_hint=singpass
```

### MCC SSO token request with private key JWT

```bash
# Token request to IdP includes signed assertion:
# client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
# client_assertion=<signed-jwt>
```

## 5. Verification

Confirm that:

- `/oauth2/authorization/mcc-sso-login` includes `kc_idp_hint=singpass`;
- the callback uses a private key JWT client assertion during token exchange;
- the local `Principal.getName()` returns the value of the nested `afcas.uuid` claim (verify via `/api/v1/profile`);
- non-MCC registrations continue to use their own `user-name-attribute` defaults (which resolves to `sub` for standard OIDC);
- configuration validation fails fast on missing or invalid JWK material;
- the Profile Data Service fetches attributes correctly using the configured `template-id` and endpoints.

## 6. Conclusion

By leveraging custom `OidcUser` principal name mapping, a dynamic JWK resolver, and custom Profile Data Service configurations, the application implements MCC-specific security requirements with clean, modular code. This approach ensures maximum compatibility with standard OpenID Connect authorization code grant flows.

## 7. References

- [RFC 7523: JWT Profile for Client Authentication](https://tools.ietf.org/html/rfc7523)
- [Spring Security: OAuth2 Client Authentication](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html#oauth2Client-client-authentication)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

