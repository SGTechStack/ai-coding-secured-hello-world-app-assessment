# Security Patterns — Session-Based Authentication

This project uses Spring Security with session-based authentication. Sessions are stored in-memory (local/test) or Redis (dev/qa/prod).

---

## Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // spotless:off to preserve formatting of fluent API
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(authSuccessHandler())
                        .failureHandler(authFailureHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler())
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                )
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                )
                .build();
        // spotless:on
    }
}
```

---

## CSRF for SPA

Since the frontend is a React SPA, CSRF uses cookie-based token delivery:

1. Backend sets `XSRF-TOKEN` cookie (readable by JS since `httpOnly=false`)
2. Frontend reads the cookie and sends it back as `X-XSRF-TOKEN` header
3. Backend validates header matches cookie

The `SpaCsrfTokenRequestHandler` resolves the token from the header for AJAX requests.

---

## Session Store Switching

Profile-based session configuration:

```properties
# application.properties (prod default)
spring.session.store-type=redis

# application-local.properties
spring.session.store-type=none

# application-test.properties
spring.session.store-type=none
```

The `feat-redis` profile enables Redis session store with configuration:

```properties
spring.session.store-type=redis
spring.data.redis.repositories.enabled=true
```

---

## Authentication Handlers

Return JSON (not redirects) since the frontend is an SPA:

```java
@Slf4j
@Component
public class JsonAuthSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"status": "authenticated"}
                """);
    }
}

@Component
public class JsonAuthFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error": "Invalid credentials"}
                """);
    }
}

@Component
public class JsonUnauthorizedEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
                {"error": "Authentication required"}
                """);
    }
}
```

---

## Method-Level Security

Use `@PreAuthorize` for fine-grained access control:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.principal.id")
    public Page<Order> getOrdersForCustomer(Long customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(Long orderId) {
        orderRepository.deleteById(orderId);
    }
}
```

---

## Accessing Current User

```java
public class SecurityUtils {

    public static Optional<CustomUserDetails> getCurrentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(CustomUserDetails.class::isInstance)
                .map(CustomUserDetails.class::cast);
    }

    public static String getCurrentUserId() {
        return getCurrentUser()
                .map(CustomUserDetails::getId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }
}
```

---

## Security Testing

Testing authenticated endpoints with MockMvc:

```java
// Authenticated user
mockMvc.perform(get("/api/orders")
        .with(user("ada").roles("USER")))
        .andExpect(status().isOk());

// Admin access
mockMvc.perform(delete("/api/orders/1")
        .with(user("admin").roles("ADMIN")))
        .andExpect(status().isNoContent());

// Unauthenticated — expect 401
mockMvc.perform(get("/api/orders"))
        .andExpect(status().isUnauthorized());

// Wrong role — expect 403
mockMvc.perform(delete("/api/orders/1")
        .with(user("ada").roles("USER")))
        .andExpect(status().isForbidden());

// CSRF on mutating endpoints
mockMvc.perform(post("/api/orders")
        .with(user("ada").roles("USER"))
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestBody))
        .andExpect(status().isCreated());
```

---

## Security Checklist

- Never return different error messages for "user not found" vs "wrong password" (timing attack)
- Session fixation: Spring regenerates session ID on login by default — don't disable
- Set `SameSite=Lax` on session cookie (Spring Boot default)
- Rate limit `/api/auth/login` to prevent brute force
- Log authentication failures (but not passwords)
- Never expose internal user IDs in URLs if they're sequential — use UUIDs for external-facing IDs
