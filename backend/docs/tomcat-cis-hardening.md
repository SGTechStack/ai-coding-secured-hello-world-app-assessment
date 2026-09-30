# CIS Apache Tomcat 11 Hardening

This document maps every applicable control from the **CIS Apache Tomcat 10.1 Benchmark v1.1.0** to its equivalent Spring Boot `application.properties` setting. Tomcat 10.1 and Tomcat 11 share the same hardening surface; no CIS control changed meaning between versions.

Controls that require OS-level permissions, filesystem layout, or standalone Tomcat configuration files (`server.xml`, `web.xml`, `context.xml`) are noted as **N/A for embedded Tomcat** – they must be enforced through the container image, Kubernetes security context, or infrastructure policy instead.

## Profile mapping

Controls are split across two property files:

| File | Scope |
|----|----|
| `application.properties` | Base defaults active in every profile. |
| `application-feat-https.properties` | HTTPS-only controls: session-cookie `Secure` flag, TLS protocol allowlist, cipher suite allowlist. Activated by the `feat-https` feature flag, which is included in the `local`, `dev`, `qa`, and `prod` profile groups. |
| `application-feat-https.properties` + `SelfSignedSslConfig.java` | Runtime self-signed TLS certificate. Activated by the `feat-https` feature flag, which is included in all profile groups (`local`, `dev`, `qa`, `prod`). Generates a 2048-bit RSA certificate via `keytool` at startup, valid for `localhost` / `127.0.0.1` with a randomly generated one-time keystore password. In cloud deployments (dev, qa, prod) an AWS ALB terminates TLS with a CA-signed certificate and forwards to Spring Boot over HTTPS; ALB accepts the self-signed cert without additional trust configuration. |

## Summary

| Section | CIS | Control | Profile |
|----|----|----|----|
| 2 – Minimize Tomcat Platform Information Leakage | 2.1–2.3 | Suppress `server.info`, `server.number`, and `server.built` strings to prevent Tomcat version disclosure in the `Server` response header, error-page footers, and JMX attributes. | All |
| 2 – Minimize Tomcat Platform Information Leakage | 2.4 | Remove the `Server` HTTP response header and suppress `X-Powered-By`. | All |
| 2 – Minimize Tomcat Platform Information Leakage | 2.5 | Do not expose stack traces, exception class names, or error messages in HTTP responses. | All |
| 2 – Minimize Tomcat Platform Information Leakage | 2.6 | Disable the HTTP `TRACE` method to prevent cross-site tracing (XST). | All |
| 3 – Protect the Shutdown Port | 3.1–3.2 | Set a non-deterministic shutdown command or disable the shutdown port entirely. | N/A |
| 4 – Logging | 4.1 | Enable application-specific access logging so every request is auditable. | All |
| 4 – Logging | 4.2–4.3 | Configure the `FileHandler` class in `logging.properties`. | N/A |
| 4 – Logging | 4.4 | Write log files to a directory outside the Tomcat installation root. | All |
| 4 – Logging | 4.5 | Configure log file retention / size limits; rotate daily. | All |
| 5 – SSL / TLS | 5.1 | Use a secure realm (avoid `MemoryRealm`, etc.). | N/A |
| 5 – SSL / TLS | 5.2 | Enable `SSLEnabled` on the connector for sensitive traffic. | `feat-https` (all) |
| 5 – SSL / TLS | 5.3–5.4 | Set `scheme=https` and `secure=true` on the connector. | `feat-https` (all) |
| 5 – SSL / TLS | 5.5–5.6 | Disable SSLv2, SSLv3, TLSv1, TLSv1.1; ensure TLSv1.2 or TLSv1.3 only. | `feat-https` (all) |
| 5 – SSL / TLS | 5.7 | Use FIPS-aligned, forward-secret cipher suites; exclude RC4, DES, 3DES, CBC-only, and non-FIPS suites (including ChaCha20-Poly1305). | `feat-https` (all) |
| 6 – Connectors | 6.1 | Establish client-certificate authentication where required. | Not required (deployment-specific) |
| 6 – Connectors | 6.2 | Set a `connectionTimeout` to release idle or slow-loris connections quickly. | All |
| 7 – Establish and Protect the Tomcat Account | 7.1–7.3 | Run Tomcat under a dedicated low-privilege OS account; set file ownership and permissions. | IaC required |
| 8 – Limit Server Requests | 8.1 | Cap `maxConnections` to prevent connection-flood denial-of-service. | All |
| 8 – Limit Server Requests | 8.2 | Cap `acceptCount` (backlog queue) to bound memory pressure during traffic spikes. | All |
| 8 – Limit Server Requests | 8.3 | Cap `maxParameterCount` to prevent parameter-pollution and resource-exhaustion. | All |
| 8 – Limit Server Requests | 8.4 | Cap `maxSwallowSize` to limit how much of a rejected body Tomcat reads (slow-body protection). | All |
| 8 – Limit Server Requests | 8.5 | Cap `maxHttpFormPostSize` for `multipart/form-data` requests. | All |
| 8 – Limit Server Requests | 8.6 | Cap `maxKeepAliveRequests` to bound pipelined requests per connection. | All |
| 8 – Limit Server Requests | 8.7 | Cap inbound HTTP request header size to prevent header-injection and memory exhaustion. | All |
| 8 – Limit Server Requests | 8.8 | Cap outbound HTTP response header size. | All |
| 9 – Configure Application Context Files | 9.1 | Disable cross-context access (`crossContext=false`). | N/A |
| 9 – Configure Application Context Files | 9.2–9.3 | Disable auto-deployment and symbolic link following. | N/A |
| 10 – Application Deployment | 10.1–10.3 | Do not deploy the Manager, Host Manager, or examples applications. | N/A |
| 11 – Protecting Sensitive Information (Session) | 11.1 | Set the `HttpOnly` flag on session cookies so JavaScript cannot read them. | All |
| 11 – Protecting Sensitive Information (Session) | 11.2 | Set the `Secure` flag on session cookies so they are never sent over plain HTTP. | `feat-https` (all) |
| 11 – Protecting Sensitive Information (Session) | 11.3 | Set `SameSite=Strict` on session cookies to prevent CSRF via cookie leakage. | All |
| 11 – Protecting Sensitive Information (Session) | 11.4 | Use cookie-only session tracking; disable URL-based session IDs (prevents session fixation). | All |
| 11 – Protecting Sensitive Information (Session) | 11.5 | Set a short session idle timeout to reduce the exposure window of a stolen session. | All |
| 12 – Miscellaneous Configuration Settings | 12.1 | Disable Tomcat’s JMX / MBean registry to shrink the local JMX attack surface. | All |
| 12 – Miscellaneous Configuration Settings | 12.2 | Disable the default servlet to prevent unintended static-file serving and directory listing. | All |
| 12 – Miscellaneous Configuration Settings | 12.3 | Use the `LockOutRealm` to throttle failed authentication attempts. | N/A (Java config) |
| 12 – Miscellaneous Configuration Settings | 12.4 | Enable the memory-leak-prevention listener. | N/A |

## Control mapping

### Section 2 – Minimize Tomcat Platform Information Leakage

#### CIS 2.1–2.3

**Description:** Suppress `server.info`, `server.number`, and `server.built` strings to prevent Tomcat version disclosure in the `Server` response header, error-page footers, and JMX attributes.

**Property**

Shadow `org/apache/catalina/util/ServerInfo.properties` from `catalina.jar` by placing a file at the same path in `src/main/resources`. Spring Boot’s fat-jar classloader resolves application resources before embedded jar entries, so Tomcat picks up the overriding (empty) values at startup — no Java code required.

```properties
# src/main/resources/org/apache/catalina/util/ServerInfo.properties
server.info=
server.number=
server.built=
```

**Profile:** All

#### CIS 2.4

**Description:** Remove the `Server` HTTP response header and suppress `X-Powered-By`.

**Property:** `server.server-header=` (empty value omits the header)

**Profile:** All

#### CIS 2.5

**Description:** Do not expose stack traces, exception class names, or error messages in HTTP responses.

**Property**

```properties
server.error.include-stacktrace=never
server.error.include-exception=false
server.error.include-message=never
spring.web.error.include-binding-errors=never
```

**Profile:** All

#### CIS 2.6

**Description:** Disable the HTTP `TRACE` method to prevent cross-site tracing (XST).

**Property:** `spring.mvc.dispatch-trace-request=false`

**Profile:** All

### Section 3 – Protect the Shutdown Port

| CIS Control | Description | Property | Profile |
|----|----|----|----|
| 3.1–3.2 | Set a non-deterministic shutdown command or disable the shutdown port entirely. | Embedded Tomcat has no shutdown port. N/A. | N/A |

### Section 4 – Logging

#### CIS 4.1

**Description:** Enable application-specific access logging so every request is auditable.

**Property**

```properties
server.tomcat.accesslog.enabled=true
server.tomcat.accesslog.pattern=%h %l %u %t "%r" %s %b %D "%{Referer}i" "%{User-Agent}i"
server.tomcat.accesslog.buffered=false
```

**Profile:** All

#### CIS 4.2–4.3

**Description:** Configure the `FileHandler` class in `logging.properties`.

**Property:** Handled by Spring Boot’s Logback / ECS logging configuration. N/A.

**Profile:** N/A

#### CIS 4.4

**Description:** Write log files to a directory outside the Tomcat installation root.

**Property:** `server.tomcat.accesslog.directory=./logs`

**Profile:** All

#### CIS 4.5

**Description:** Configure log file retention / size limits; rotate daily.

**Property**

```properties
server.tomcat.accesslog.rotate=true
server.tomcat.accesslog.max-days=90
```

**Profile:** All

### Section 5 – SSL / TLS

#### CIS 5.1

**Description:** Use a secure realm (avoid `MemoryRealm`, etc.).

**Property:** Managed by Spring Security. N/A.

**Profile:** N/A

#### CIS 5.2

**Description:** Enable `SSLEnabled` on the connector for sensitive traffic.

**Property:** `SelfSignedSslConfig.java` calls `ssl.setEnabled(true)` via Spring Boot’s `Ssl` API, which wires the connector scheme, `SSLEnabled`, and the default SSL host config automatically.

**Profile:** `feat-https` (all)

#### CIS 5.3–5.4

**Description:** Set `scheme=https` and `secure=true` on the connector.

**Property**

```properties
server.servlet.session.cookie.secure=true
```

Spring Boot’s `Ssl` API sets `scheme=https` and `secure=true` on the connector automatically when `ssl.setEnabled(true)` is called in `SelfSignedSslConfig.java`. (Proxy header forwarding via `server.forward-headers-strategy=native` when behind an LB.)

**Profile:** `feat-https` (all)

#### CIS 5.5–5.6

**Description:** Disable SSLv2, SSLv3, TLSv1, TLSv1.1; ensure TLSv1.2 or TLSv1.3 only.

**Property:** `SelfSignedSslConfig.java` calls `ssl.setEnabledProtocols("TLSv1.2", "TLSv1.3")` programmatically. Java 21 also disables TLSv1 and TLSv1.1 at the JVM level via `jdk.tls.disabledAlgorithms`.

**Profile:** `feat-https` (all)

#### CIS 5.7

**Description:** Use FIPS-aligned, forward-secret cipher suites; exclude RC4, DES, 3DES, CBC-only, and non-FIPS suites (including ChaCha20-Poly1305).

**Property**

`SelfSignedSslConfig.java` calls `ssl.setCiphers(CIPHERS)` with the following FIPS 140-2/140-3 approved, forward-secret suites (NIST SP 800-52 Rev 2):

```java
"TLS_AES_256_GCM_SHA384",        // TLS 1.3
"TLS_AES_128_GCM_SHA256",        // TLS 1.3
"TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",  // TLS 1.2
"TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",    // TLS 1.2
"TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",  // TLS 1.2
"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"     // TLS 1.2
```

Note: `TLS_CHACHA20_POLY1305_SHA256` and the two ECDHE-ChaCha20 variants are intentionally excluded — ChaCha20-Poly1305 is not FIPS 140-2/140-3 approved.

**Profile:** `feat-https` (all)

### Section 6 – Connectors

#### CIS 6.1

**Description:** Establish client-certificate authentication where required.

**Property:** Configured via `server.ssl.client-auth` and trust-store properties. Not required for this app.

**Profile:** Not required (deployment-specific)

#### CIS 6.2

**Description:** Set a `connectionTimeout` to release idle or slow-loris connections quickly.

**Property**

```properties
server.tomcat.connection-timeout=20s
server.tomcat.keep-alive-timeout=20s
```

**Profile:** All

### Section 7 – Establish and Protect the Tomcat Account

| CIS Control | Description | Property | Profile |
|----|----|----|----|
| 7.1–7.3 | Run Tomcat under a dedicated low-privilege OS account; set file ownership and permissions. | OS / container image concern. Enforce via Dockerfile `USER` directive or Kubernetes `securityContext`. Required, but enforced by IaC, not by this repo. | IaC required |

### Section 8 – Limit Server Requests

| CIS Control | Description | Property | Profile |
|----|----|----|----|
| 8.1 | Cap `maxConnections` to prevent connection-flood denial-of-service. | `server.tomcat.max-connections=8192` | All |
| 8.2 | Cap `acceptCount` (backlog queue) to bound memory pressure during traffic spikes. | `server.tomcat.accept-count=100` | All |
| 8.3 | Cap `maxParameterCount` to prevent parameter-pollution and resource-exhaustion. | `server.tomcat.max-parameter-count=1000` | All |
| 8.4 | Cap `maxSwallowSize` to limit how much of a rejected body Tomcat reads (slow-body protection). | `server.tomcat.max-swallow-size=2MB` | All |
| 8.5 | Cap `maxHttpFormPostSize` for `multipart/form-data` requests. | `server.tomcat.max-http-form-post-size=2MB` | All |
| 8.6 | Cap `maxKeepAliveRequests` to bound pipelined requests per connection. | `server.tomcat.max-keep-alive-requests=100` | All |
| 8.7 | Cap inbound HTTP request header size to prevent header-injection and memory exhaustion. | `server.max-http-request-header-size=8KB` | All |
| 8.8 | Cap outbound HTTP response header size. | `server.tomcat.max-http-response-header-size=8KB` | All |

### Section 9 – Configure Application Context Files

| CIS Control | Description | Property | Profile |
|----|----|----|----|
| 9.1 | Disable cross-context access (`crossContext=false`). | Default behaviour in embedded Tomcat. N/A. | N/A |
| 9.2–9.3 | Disable auto-deployment and symbolic link following. | Embedded Tomcat has no hot-deployment directory. N/A. | N/A |

### Section 10 – Application Deployment

| CIS Control | Description | Property | Profile |
|----|----|----|----|
| 10.1–10.3 | Do not deploy the Manager, Host Manager, or examples applications. | Embedded Tomcat does not bundle these. N/A. | N/A |

### Section 11 – Protecting Sensitive Information (Session)

| CIS Control | Description | Property | Profile |
|----|----|----|----|
| 11.1 | Set the `HttpOnly` flag on session cookies so JavaScript cannot read them. | `server.servlet.session.cookie.http-only=true` | All |
| 11.2 | Set the `Secure` flag on session cookies so they are never sent over plain HTTP. | `server.servlet.session.cookie.secure=true` | `feat-https` (all) |
| 11.3 | Set `SameSite=Strict` on session cookies to prevent CSRF via cookie leakage. | `server.servlet.session.cookie.same-site=strict` | All |
| 11.4 | Use cookie-only session tracking; disable URL-based session IDs (prevents session fixation). | `server.servlet.session.tracking-modes=cookie` | All |
| 11.5 | Set a short session idle timeout to reduce the exposure window of a stolen session. | `server.servlet.session.timeout=15m` | All |

### Section 12 – Miscellaneous Configuration Settings

| CIS Control | Description | Property | Profile |
|----|----|----|----|
| 12.1 | Disable Tomcat’s JMX / MBean registry to shrink the local JMX attack surface. | `server.tomcat.mbeanregistry.enabled=false` | All |
| 12.2 | Disable the default servlet to prevent unintended static-file serving and directory listing. | `server.servlet.register-default-servlet=false` | All |
| 12.3 | Use the `LockOutRealm` to throttle failed authentication attempts. | Handled by Spring Security (`HttpSecurity` rate-limiting or `AccountStatusUserDetailsChecker`). N/A for properties. | N/A (Java config) |
| 12.4 | Enable the memory-leak-prevention listener. | Registered automatically by embedded Tomcat. N/A. | N/A |

## Controls handled by Spring Security (not application.properties)

The following CIS controls are satisfied by `SecurityConfiguration.java` or require explicit Java configuration:

- **2.6 TRACE method** – additionally enforced via `http.authorizeHttpRequests()` deny-all for unrecognised methods.

- **CSRF protection** – Spring Security’s `CsrfFilter` handles this by default for state-changing requests.

- **HTTP → HTTPS redirect** – configure `http.requiresChannel().anyRequest().requiresSecure()` in `SecurityConfiguration.java` for environments where the application itself terminates TLS.

- **Security response headers** (HSTS, X-Frame-Options, X-Content-Type-Options) – provided automatically by `HeadersConfigurer` in Spring Security; customise via `http.headers()` if stricter values are needed.

## N/A controls summary

The following CIS controls require OS, container, or infrastructure action rather than application-level properties:

| Section | Remediation approach |
|----|----|
| 3.1–3.2 (shutdown port) | No shutdown port exists in embedded Tomcat. |
| 4.2–4.3 (FileHandler) | Managed by Spring Boot’s structured ECS logging (see `logging.structured.format.file=ecs`). |
| 5.1 (realm type) | Managed by Spring Security’s `UserDetailsService`. |
| 7.x (OS account) | Required, but enforced by the deployment setup (not in this repo). Typical setup: Dockerfile `USER nonroot` + Kubernetes `runAsNonRoot: true` / `readOnlyRootFilesystem: true`. |
| 9.x (crossContext, symlinks) | Not applicable to embedded Tomcat’s single-application model. |
| 10.x (Manager / examples) | Not bundled in the Spring Boot fat-jar. |
| 12.3 (LockOutRealm) | Implement via Spring Security account locking (`UserDetails.isAccountNonLocked()`). |
| 12.4 (memory-leak listener) | Auto-registered by embedded Tomcat. |
