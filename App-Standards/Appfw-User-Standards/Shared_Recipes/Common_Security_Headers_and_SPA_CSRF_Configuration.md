# Security Headers and SPA CSRF Configuration

## 1. Introduction

This guide defines the standards for **Browser-Defense Security**, covering HTTP security headers, cookie attributes, and the specific configuration required to support **Single Page Applications (SPAs)** in Spring Security 7. These controls ensure protection against clickjacking, MIME-sniffing, XSS, and CSRF while enabling seamless integration with frontend frameworks like React or Angular.

**Key Design Principles:**
- **Explicit Header Enforcement:** Configures modern browser-native protections (HSTS, CSP, Permissions-Policy).
- **Session-Based CSRF (Best Practice):** Aligns with OWASP recommendations by storing CSRF tokens in the server-side session, preventing exposure via cookies.
- **CSRF Bootstrap Pattern:** Uses a dedicated endpoint to provide SPAs with the initial session-bound token, resolving "Deferred Token" issues without compromising security.
- **Secure-by-Default Cookies:** Enforces `HttpOnly`, `Secure`, and `SameSite` attributes for all cookies (Session and CSRF).
- **Generic Error Handling:** Relies on Spring Boot's secure defaults to prevent information leakage during authentication failures, complying with OWASP and IM8 guidelines.

## 2. Prerequisites

- Spring Boot 4.x (Java 21/25)
- Spring Security 7.x

## 3. Implementation

### Step 1: Configure Security Filter Chains & Headers

Industry best practice for Spring Security 7 is to use separate filter chains to allow specific paths (like H2 Console) to have different security postures without compromising the main application.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    @Order(1)
    @Profile("dev")
    public SecurityFilterChain devToolsFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(PathRequest.toH2Console())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    public SecurityFilterChain appFilterChain(HttpSecurity http) throws Exception {
        // Implementation details continued in Step 2...
        return http.build();
    }
}
```

### Step 2: SPA-Friendly Session-Based CSRF & Security Headers

Storing the CSRF token in the `HttpSession` is the most secure approach for applications with server-side sessions. It ensures the token is never accessible to JavaScript via cookies and remains tightly bound to the authenticated session.

#### 1. Security Defaults
Spring Security 7 enables several protections by default. You **do not need** to explicitly configure the following unless you are overriding them:
- **Session-Based CSRF Storage:** `HttpSessionCsrfTokenRepository` is the default repository.
- **BREACH Protection:** `XorCsrfTokenRequestAttributeHandler` (XOR encoding) is the default CSRF handler.
- **Clickjacking Protection:** `X-Frame-Options: DENY` is active by default.
- **MIME-Sniffing Protection:** `X-Content-Type-Options: nosniff` is active by default.
- **HSTS:** `Strict-Transport-Security` is active by default for HTTPS requests.
- **Secure Error Handling:** Unhandled security exceptions automatically result in generic 401/403 responses via Spring Boot's `BasicErrorController`, preventing stack trace leakage.

#### 2. Configure application.yml
Enforce strict security for the session cookie.

```yaml
server:
  servlet:
    session:
      cookie:
        http-only: true   # Prevent JS access
        secure: true      # HTTPS only
        same-site: lax    # Standard SPA posture
```

#### 3. Configure the Security Filter Chain
We rely on Spring's defaults for CSRF and exception handling, focusing configuration entirely on modern security headers.

```java
@Bean
public SecurityFilterChain appFilterChain(HttpSecurity http) throws Exception {
    http
        // 1. Rely on default Session-based CSRF and XOR encoding
        .csrf(Customizer.withDefaults())
            
        .headers(headers -> headers
            // 2. Configure modern headers
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'; object-src 'none';"))
            .permissionsPolicy(permissions -> permissions
                .policy("geolocation=(), microphone=(), camera=()"))
            
            // Customize HSTS (Overrides default 1-year max-age)
            .httpStrictTransportSecurity(hsts -> hsts
                .includeSubDomains(true).preload(true).maxAgeInSeconds(31536000))
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("${api.base-path}/csrf").permitAll() // Whitelist bootstrap endpoint
            .anyRequest().authenticated());

    return http.build();
}
```

### Step 3: Implement the CSRF Bootstrap Endpoint

Because we use session-based storage, SPAs cannot read the token from a cookie. Instead, they must call a "Bootstrap" endpoint to retrieve the token as a JSON response.

```java
@RestController
@RequestMapping("${api.base-path}")
public class CsrfController {
    @GetMapping("/csrf")
    public CsrfToken getCsrfToken(CsrfToken token) {
        // Spring Security automatically resolves the session-bound token
        return token;
    }
}
```

> **Note: Why Session-Based?**
> While "Double Submit Cookies" were common for SPAs, storing tokens in the `HttpSession` (with a bootstrap endpoint) is now the preferred security standard. It eliminates the risk of token leakage via the `Set-Cookie` header and ensures tokens are cleared precisely when the session ends.

---

### Strictly Prohibited: Cookie-Based CSRF

The **Double Submit Cookie** pattern (e.g., Spring Security's `CookieCsrfTokenRepository`) is **strictly prohibited** in this architecture. Because the application is stateful and relies on server-side sessions, falling back to a stateless, cookie-based token weakens security and violates the standard. CSRF tokens must always be anchored to the HTTP Session and distributed exclusively via the JSON Bootstrap Endpoint.

---

### Step 4: Implement Explicit CORS Policy

Because the CSRF token is exposed via the `/csrf` JSON endpoint, its security relies entirely on the browser's Same-Origin Policy. **You must configure a strict CORS whitelist.** Never use wildcards (`*`) or dynamically reflect unverified origins, as this would allow malicious sites to steal the token.

```java
@Bean
public CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
    CorsConfiguration config = new CorsConfiguration();
    // CRITICAL: Must be a strict whitelist of trusted SPA origins. Do not use "*".
    config.setAllowedOrigins(props.getAllowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("${api.base-path}/**", config);
    return source;
}
```

## 4. Verification

1. **Header Check:** Use `curl -I` to verify all five headers (Frame, Content-Type, HSTS, CSP, Permissions) are present.
2. **CSRF JSON:** Call `GET /csrf` and verify a valid JSON token is returned.
3. **Cookie Audit:** Call `GET /` and verify **no** `XSRF-TOKEN` cookie is present (confirming session storage).
4. **Error Masking:** Trigger an auth failure; verify the response is generic (e.g., via Spring Boot's default `/error` response) and does not leak internal exceptions or system details.

## 5. Conclusion

By prioritizing **Session-Based CSRF** and strict browser headers, this architecture provides a defense-in-depth posture. Leveraging Spring Security 7 defaults simplifies configuration, using the framework's native error handling prevents information leakage, and the use of the "Bootstrap" endpoint ensures modern frontend frameworks can securely retrieve tokens without exposing them in unencrypted cookies.

## 6. References

- [OWASP: CSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [Spring Security 7: CSRF Protection](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
