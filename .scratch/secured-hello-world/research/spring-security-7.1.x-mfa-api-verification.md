# Spring Security 7.1.x MFA API surface — primary-source verification

Scope: only Spring Security reference docs, Spring Security javadoc, Spring Security GitHub source/releases, and spring.io blog were used. Where a claim could not be established from those sources it is flagged explicitly.

Canonical source used for code claims: the `7.1.x` maintenance branch of `spring-projects/spring-security` (branch `gradle.properties` = `7.1.2-SNAPSHOT`, i.e. the line that produced 7.1.1 GA). `docs.spring.io/.../api/` "current" javadoc reports itself as **7.0.0**, so branch source was preferred over "current" javadoc for 7.1-only API.

Primary references used throughout:

- Reference doc: <https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html>
- Official blog: <https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7>
- Branch version marker: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/gradle.properties>

---

## 1. `AuthorizationManagerFactory` — interface, default impl, bean registration, `requireFactors` / `setAdditionalAuthorization`

### 1a. The interface

`org.springframework.security.authorization.AuthorizationManagerFactory` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authorization/AuthorizationManagerFactory.java>

```java
public interface AuthorizationManagerFactory<T extends @Nullable Object> {
	default AuthorizationManager<T> permitAll()
	default AuthorizationManager<T> denyAll()
	default AuthorizationManager<T> hasRole(String role)
	default AuthorizationManager<T> hasAnyRole(String... roles)
	default AuthorizationManager<T> hasAllRoles(String... roles)
	default AuthorizationManager<T> hasAuthority(String authority)
	default AuthorizationManager<T> hasAnyAuthority(String... authorities)
	default AuthorizationManager<T> hasAllAuthorities(String... authorities)
	default AuthorizationManager<T> authenticated()
	default AuthorizationManager<T> fullyAuthenticated()
	default AuthorizationManager<T> rememberMe()
	default AuthorizationManager<T> anonymous()
}
```

`@since 7.0`, author Steve Riesenberg. Every method is a `default` method; there are **no abstract methods**.

**Correction to a common assumption: `requireFactors(...)` is *not* on this interface.** It lives on a builder (see 1c).

### 1b. Default implementation

`org.springframework.security.authorization.DefaultAuthorizationManagerFactory<T>` — `public final class ... implements AuthorizationManagerFactory<T>`. Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authorization/DefaultAuthorizationManagerFactory.java>

Configuration surface (all JavaBean setters, no fluent API):

```java
public void setTrustResolver(AuthenticationTrustResolver trustResolver)     // default AuthenticationTrustResolverImpl
public void setRoleHierarchy(RoleHierarchy roleHierarchy)                   // default NullRoleHierarchy
public void setRolePrefix(String rolePrefix)                                // default "ROLE_"
public void setAdditionalAuthorization(@Nullable AuthorizationManager<T> additionalAuthorization) // default null
```

How `setAdditionalAuthorization` composes (verbatim from the private helper):

```java
private AuthorizationManager<T> withAdditionalAuthorization(AuthorizationManager<T> manager) {
	if (this.additionalAuthorization == null) {
		return manager;
	}
	return AuthorizationManagers.allOf(new AuthorizationDecision(false), this.additionalAuthorization, manager);
}
```

Note the argument order: the additional authorization is evaluated **before** the role/authority manager, so its `AuthorizationResult` (a `FactorAuthorizationDecision`, see §5) is the one that surfaces on denial. The `allOf` "deny" fallback result is `new AuthorizationDecision(false)`.

The class javadoc for `setAdditionalAuthorization` lists the affected methods as `hasRole`, `hasAnyRole`, `hasAllRoles`, `hasAuthority`, `hasAnyAuthority`, `hasAllAuthorities`, `authenticated`, `fullyAuthenticated`, `rememberMe`, and states it "does not affect `anonymous`, `permitAll`, or `denyAll`".

> ⚠️ **Source/javadoc discrepancy (observed, not documented):** in the 7.1.x source, `anonymous()` *is* overridden as `return createManager(AuthenticatedAuthorizationManager.anonymous());`, and `createManager` calls `withAdditionalAuthorization(...)`. So on this branch the additional factor requirement does appear to be applied to `anonymous()`, contradicting the javadoc. `permitAll()` / `denyAll()` are not overridden, so those genuinely are unaffected. I could not find a primary source resolving which behaviour is intended.

### 1c. Where `requireFactors(...)` actually lives

`org.springframework.security.authorization.AuthorizationManagerFactories` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authorization/AuthorizationManagerFactories.java> (javadoc: <https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/authorization/AuthorizationManagerFactories.html>)

```java
public final class AuthorizationManagerFactories {
	public static <T> AdditionalRequiredFactorsBuilder<T> multiFactor()

	public static final class AdditionalRequiredFactorsBuilder<T> {
		public AdditionalRequiredFactorsBuilder<T> when(Predicate<Authentication> condition)          // @since 7.1
		public AdditionalRequiredFactorsBuilder<T> withWhen(
			Function<@Nullable Predicate<Authentication>, @Nullable Predicate<Authentication>> condition) // @since 7.1
		public AdditionalRequiredFactorsBuilder<T> requireFactors(String... additionalAuthorities)
		public AdditionalRequiredFactorsBuilder<T> requireFactors(
			Consumer<AllRequiredFactorsAuthorizationManager.Builder<T>> factors)
		public AdditionalRequiredFactorsBuilder<T> requireFactor(Consumer<RequiredFactor.Builder> factor)
		public DefaultAuthorizationManagerFactory<T> build()
	}
}
```

- `requireFactors(String...)` delegates to `requireFactor((factor) -> factor.authority(authority))` per string — i.e. authority-match only, **no** `validDuration`.
- `build()` constructs a `DefaultAuthorizationManagerFactory<T>`, builds an `AllRequiredFactorsAuthorizationManager` from the accumulated factors, wraps it in `ConditionalAuthorizationManager.when(whenCondition).whenTrue(...)` if `when(...)`/`withWhen(...)` was used, and calls `setAdditionalAuthorization(...)` with the result.

### 1d. Bean registration

`AuthorizationManagerFactoryConfiguration` (package-private, `org.springframework.security.config.annotation.authorization`, `implements ImportAware`) — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/authorization/AuthorizationManagerFactoryConfiguration.java>

```java
@Bean
DefaultAuthorizationManagerFactory authorizationManagerFactory(
		List<Customizer<AdditionalRequiredFactorsBuilder<Object>>> additionalRequiredFactorsCustomizers) {
	AdditionalRequiredFactorsBuilder<Object> builder = AuthorizationManagerFactories.multiFactor()
		.requireFactors(this.authorities);
	for (Customizer<AdditionalRequiredFactorsBuilder<Object>> customizer : additionalRequiredFactorsCustomizers) {
		customizer.customize(builder);
	}
	return builder.build();
}
```

So: the bean name is `authorizationManagerFactory`, its type is `DefaultAuthorizationManagerFactory`, its authorities come from `@EnableMultiFactorAuthentication#authorities()`, and any number of `Customizer<AdditionalRequiredFactorsBuilder<Object>>` beans can further mutate the builder. This configuration class is only imported when `authorities()` is non-empty (§3).

---

## 2. `FactorGrantedAuthority`

Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/core/authority/FactorGrantedAuthority.java>

- **Package:** `org.springframework.security.core.authority` (note: `core`, *not* `web`).
- **Declaration:** `public final class FactorGrantedAuthority implements GrantedAuthority`, `@since 7.0`, authors Yoobin Yoon / Rob Winch.
- **Serializable:** yes, transitively — `GrantedAuthority extends Serializable`, and the class declares `private static final long serialVersionUID = 1998010439847123984L;`. It does **not** declare `implements Serializable` directly.
- **Constructor:** private. Instances come from factories/builder only.

### Authority string constants

Constant name (field) → value:

| Constant | Value |
| --- | --- |
| `AUTHORIZATION_CODE_AUTHORITY` | `FACTOR_AUTHORIZATION_CODE` |
| `BEARER_AUTHORITY` | `FACTOR_BEARER` |
| `CAS_AUTHORITY` | `FACTOR_CAS` |
| `OTT_AUTHORITY` | `FACTOR_OTT` |
| `PASSWORD_AUTHORITY` | `FACTOR_PASSWORD` |
| `SAML_RESPONSE_AUTHORITY` | `FACTOR_SAML_RESPONSE` |
| `WEBAUTHN_AUTHORITY` | `FACTOR_WEBAUTHN` |
| `X509_AUTHORITY` | `FACTOR_X509` |

**There is no TOTP or generic OTP constant.** The naming pattern is `<X>_AUTHORITY` with value `FACTOR_<X>`; there is no constant literally named `FACTOR_PASSWORD_AUTHORITY` or `FACTOR_OTT_AUTHORITY` (some javadoc examples inside the codebase refer to a non-existent `GrantedAuthorities.FACTOR_OTT_AUTHORITY` — stale javadoc, see §11).

### Factory / builder API

```java
public static Builder withAuthority(String authority)              // exact authority string
public static Builder withFactor(String factor)                    // prefixes "FACTOR_"; rejects input already starting with "FACTOR_"
public static FactorGrantedAuthority fromAuthority(String authority) // withAuthority(authority).build()
public static FactorGrantedAuthority fromFactor(String factor)       // withFactor(factor).build()
public String getAuthority()
public Instant getIssuedAt()
public static final class Builder { ... }
```

### `getIssuedAt()`

Yes, it exists. Signature `public Instant getIssuedAt()`, type `java.time.Instant`, non-null (the private constructor asserts `issuedAt` is not null). It participates in `equals`/`hashCode`/`toString` alongside `authority`.

---

## 3. `@EnableMultiFactorAuthentication`

Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/authorization/EnableMultiFactorAuthentication.java>

- **Package:** `org.springframework.security.config.annotation.authorization`. (It is *not* under `...config.annotation.web.configuration` — that path 404s on 7.1.x.)
- **Meta-annotations:** `@Retention(RUNTIME)`, `@Target(TYPE)`, `@Documented`, `@Import(MultiFactorAuthenticationSelector.class)`.

### Attributes

```java
String[] authorities();                            // required (no default)
MultiFactorCondition[] when() default {};          // @since 7.1
```

`authorities()` has no default, so it must always be supplied (an empty array is legal). `when()` defaults to empty, which per the javadoc means MFA is required unconditionally; multiple conditions are ANDed.

### What it actually does

`MultiFactorAuthenticationSelector` (package-private `ImportSelector`) — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/authorization/MultiFactorAuthenticationSelector.java>

1. Validates: if `when()` contains `MultiFactorCondition.WEBAUTHN_REGISTERED` but `authorities()` does not contain `FactorGrantedAuthority.WEBAUTHN_AUTHORITY`, it throws `IllegalArgumentException` during configuration processing.
2. If `authorities().length > 0`, imports `AuthorizationManagerFactoryConfiguration` → registers the `DefaultAuthorizationManagerFactory` bean described in §1d. This is the application-wide effect: because a single `AuthorizationManagerFactory` bean backs the authorization rule DSL, **every** `hasRole` / `hasAuthority` / `authenticated` (etc.) rule gets the factor requirement ANDed in, in both `authorizeHttpRequests` and method security.
3. If `WEBAUTHN_REGISTERED` is present, additionally imports `WhenWebAuthnRegisteredMfaConfiguration`.
4. **Always** imports `EnableMfaFiltersConfiguration` → a `BeanPostProcessor` that calls `setMfaEnabled(true)` on authentication filters (see §7).

Annotation javadoc states the configuration is picked up by both `@EnableWebSecurity` and `@EnableMethodSecurity`, and that **reactive applications do not support MFA at this time**.

---

## 4. `validDuration`

### Where it lives

`org.springframework.security.authorization.RequiredFactor` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authorization/RequiredFactor.java>

```java
public final class RequiredFactor implements Serializable {
	public String getAuthority()
	public @Nullable Duration getValidDuration()
	public static Builder withAuthority(String authority)
	public static Builder builder()

	public static class Builder {
		public Builder authority(@Nullable String authority)
		public Builder validDuration(@Nullable Duration validDuration)
		public RequiredFactor build()
		// convenience authority setters:
		public Builder authorizationCodeAuthority()
		public Builder bearerTokenAuthority()
		public Builder casAuthority()
		public Builder passwordAuthority()
		public Builder ottAuthority()
		public Builder samlAuthority()
		public Builder webauthnAuthority()
		public Builder x509Authority()
	}
}
```

`validDuration` is a `java.time.Duration`, nullable; when null only the authority string must match.

### Semantics when the factor authority is older than the duration

From `AllRequiredFactorsAuthorizationManager.requiredFactorError(...)` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authorization/AllRequiredFactorsAuthorizationManager.java>

```java
Instant now = this.clock.instant();
Instant expiresAt = factorAuthority.getIssuedAt().plus(requiredFactor.getValidDuration());
if (now.isBefore(expiresAt)) {
	return null;   // granted
}
// denied (expired or no issuedAt to compare)
return RequiredFactorError.createExpired(requiredFactor);
```

Details worth noting:

- Only the **first** authority whose `getAuthority()` matches is considered (`findFirst()`); if it is expired, later matching authorities are not tried.
- If the matching authority is a plain `GrantedAuthority` (not a `FactorGrantedAuthority`, hence no `issuedAt`) **and** `validDuration` is non-null, the result is `createExpired(...)` — i.e. treated as expired, not granted.
- Clock is injectable: `public void setClock(Clock clock)` (default `Clock.systemUTC()`).
- The authorization result is always a `FactorAuthorizationDecision` (§5), with one `RequiredFactorError` per unsatisfied factor; `isGranted()` is `factorErrors.isEmpty()`.
- `RequiredFactorError` distinguishes the two cases: `isMissing()` vs `isExpired()` (source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authorization/RequiredFactorError.java>). Constructing an `EXPIRED` error with a null `validDuration` throws `IllegalArgumentException`.

### Resulting HTTP behaviour

There is no dedicated status code in the authorization layer. The denial travels as an `AuthorizationDeniedException` carrying the `FactorAuthorizationDecision`, and the HTTP outcome is decided by the `AccessDeniedHandler`:

- With MFA wired up (form login and/or one-time-token login configured), the handler is `DelegatingMissingAuthorityAccessDeniedHandler`, which saves the request and invokes the `AuthenticationEntryPoint` registered for the missing authority. For the default form-login entry point (`LoginUrlAuthenticationEntryPoint`) that is a **302 redirect to the login page**, not a 401/403.
- If no entry point is registered for any of the missing authorities, the handler falls through to its `defaultAccessDeniedHandler`, which `ExceptionHandlingConfigurer` sets to `AccessDeniedHandlerImpl` → **403**.

> ⚠️ **Partially verified:** I read `DelegatingMissingAuthorityAccessDeniedHandler` and `ExceptionHandlingConfigurer` directly, which establishes the handler selection and both branches. I did **not** re-read `ExceptionTranslationFilter` on 7.1.x in this pass, so the step "authenticated user + `AccessDeniedException` → `AccessDeniedHandler`" is asserted from the handler contract rather than from freshly verified filter source.

---

## 5. Missing-factor handling — is there a "which factor is missing, and where do I send the user" mechanism?

**Yes.** Three cooperating pieces.

### 5a. The decision object

`org.springframework.security.authorization.FactorAuthorizationDecision` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authorization/FactorAuthorizationDecision.java>

```java
public class FactorAuthorizationDecision implements AuthorizationResult {
	public FactorAuthorizationDecision(List<RequiredFactorError> factorErrors)
	public List<RequiredFactorError> getFactorErrors()
	@Override public boolean isGranted()   // factorErrors.isEmpty()
}
```

Note it implements `AuthorizationResult`, **not** `AuthorityAuthorizationDecision`.

Also present: `AllRequiredFactorsAuthorizationManager.anyOf(AllRequiredFactorsAuthorizationManager<T>...)` (`@since 7.1`) which grants if any manager grants, and otherwise collects the union (`LinkedHashSet`) of `RequiredFactorError`s from all of them.

### 5b. The servlet adapter

There is **no** class named `MissingAuthorityAuthorizationManager`. The class is:

`org.springframework.security.web.access.DelegatingMissingAuthorityAccessDeniedHandler` — `public final class ... implements AccessDeniedHandler`, `@since 7.0`, author Josh Cummings. Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/web/src/main/java/org/springframework/security/web/access/DelegatingMissingAuthorityAccessDeniedHandler.java>

```java
public static Builder builder()
public void setDefaultAccessDeniedHandler(AccessDeniedHandler defaultAccessDeniedHandler)  // default AccessDeniedHandlerImpl
public void setRequestCache(RequestCache requestCache)                                     // default NullRequestCache
public void handle(HttpServletRequest, HttpServletResponse, AccessDeniedException)

public static final class Builder {
	public Builder addEntryPointFor(AuthenticationEntryPoint entryPoint, String missingAuthority)
	public Builder addEntryPointFor(Consumer<DelegatingAuthenticationEntryPoint.Builder> entryPoint, String missingAuthority)
	public DelegatingMissingAuthorityAccessDeniedHandler build()
}
```

What `handle(...)` does, per the source:

1. Unwraps the cause chain (`ThrowableAnalyzer`) to find the `AuthorizationDeniedException` and reads `getAuthorizationResult()`.
2. If the result is a `FactorAuthorizationDecision`, maps each `RequiredFactorError` to `error.getRequiredFactor().getAuthority()`. If the result is an `AuthorityAuthorizationDecision`, it maps each authority, synthesising a *missing* `RequiredFactorError` for authorities whose name starts with `"FACTOR_"`.
3. For the first missing authority that has a registered entry point: saves the request via the `RequestCache`, sets request attribute `WebAttributes.REQUIRED_FACTOR_ERRORS` to the `List<RequiredFactorError>`, wraps the denial in `new InsufficientAuthenticationException("Missing Authorities " + requiredAuthority, denied)`, and calls `entryPoint.commence(request, response, ex)`.
4. If nothing matched, delegates to the default `AccessDeniedHandler`.

So the "entry point that knows which factor is missing and where to send the user" is exactly this: a map from authority string → `AuthenticationEntryPoint`, plus the `REQUIRED_FACTOR_ERRORS` request attribute so the login page can distinguish *missing* from *expired*.

### 5c. Wiring (and what is automatic)

`ExceptionHandlingConfigurer` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/ExceptionHandlingConfigurer.java>

```java
public ExceptionHandlingConfigurer<H> defaultDeniedHandlerForMissingAuthority(
		AuthenticationEntryPoint entryPoint, String authority)                                   // @since 7.0
public ExceptionHandlingConfigurer<H> defaultDeniedHandlerForMissingAuthority(
		Consumer<DelegatingAuthenticationEntryPoint.Builder> entryPoint, String authority)       // @since 7.0
```

In `createDefaultDeniedHandler(H http)`, if any missing-authority entry point was registered, it builds the `DelegatingMissingAuthorityAccessDeniedHandler`, sets its `RequestCache` from the shared `RequestCache`, and sets its default handler to the normal computed handler.

Registration happens automatically from the login configurers:

- `FormLoginConfigurer.init(H)` registers its entry point for `FactorGrantedAuthority.PASSWORD_AUTHORITY` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/FormLoginConfigurer.java>
- `OneTimeTokenLoginConfigurer.init(H)` registers its entry point for `FactorGrantedAuthority.OTT_AUTHORITY` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/ott/OneTimeTokenLoginConfigurer.java>

Both use the `Consumer<DelegatingAuthenticationEntryPoint.Builder>` overload with `getAuthenticationEntryPointMatcher(http)`, so the entry point only applies to browser-style requests (HTML/XHTML/image/plain accept types, and not `X-Requested-With: XMLHttpRequest`).

**Implication for a custom second factor:** a custom factor authority gets no automatic entry point. You must call `defaultDeniedHandlerForMissingAuthority(yourEntryPoint, "FACTOR_YOURS")` yourself, otherwise missing-factor denials for it produce a 403 instead of a redirect.

---

## 6. Built-in TOTP support in 7.x — confirmation

**Confirmed: there is no first-class TOTP / authenticator-app support in Spring Security 7.1.x, and `OneTimeTokenAuthenticationProvider` is not TOTP.**

`org.springframework.security.authentication.ott.OneTimeTokenAuthenticationProvider` — source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authentication/ott/OneTimeTokenAuthenticationProvider.java>

- `@since 6.4`, author Marcus da Coregio. Class javadoc: authenticates users based on one-time tokens, using an `OneTimeTokenService` to *consume* tokens and a `UserDetailsService` to fetch authorities.
- `authenticate(...)` casts to `OneTimeTokenAuthenticationToken`, calls `this.oneTimeTokenService.consume(otpAuthenticationToken)`, throws `InvalidOneTimeTokenException("Invalid token")` if the service returns null, loads the user, then:

```java
private static final String AUTHORITY = FactorGrantedAuthority.OTT_AUTHORITY;
...
Collection<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
authorities.add(FactorGrantedAuthority.fromAuthority(AUTHORITY));
OneTimeTokenAuthentication authenticated = new OneTimeTokenAuthentication(user, authorities);
```

This is server-generated, server-stored, single-use token consumption (magic-link / emailed-code style, paired with `GenerateOneTimeTokenFilter` and `OneTimeTokenGenerationSuccessHandler` in `OneTimeTokenLoginConfigurer`). It is **not** RFC 6238 time-based OTP: there is no shared secret, no time step, no drift window, no `Totp*` type anywhere in the flow.

Evidence for absence of TOTP:

1. Recursive tree listing of the `7.1.x` branch contains no path matching `Totp`: <https://api.github.com/repos/spring-projects/spring-security/git/trees/7.1.x?recursive=1>
2. `FactorGrantedAuthority` defines no TOTP/OTP constant (§2).
3. Spring Boot 4.1's managed-dependency coordinates list no `spring-security-totp`-style artifact: <https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html>
4. The official MFA blog post contains no occurrence of "TOTP": <https://spring.io/blog/2025/10/21/multi-factor-authentication-in-spring-security-7>

> ⚠️ **Caveat on the absence claim:** the GitHub trees API can truncate responses for large repositories, and authenticated GitHub code search was not available in this environment (`gh` CLI unusable). The four independent signals above make the conclusion strong, but it rests on absence-of-evidence rather than an explicit "TOTP is not supported" statement from Spring.

**Consequence:** the application must supply its own `AuthenticationProvider` (+ `Authentication` token and processing filter), and have it add a factor authority — e.g. `FactorGrantedAuthority.fromFactor("TOTP")`, yielding the authority string `FACTOR_TOTP` — so the authorization side (`requireFactors("FACTOR_TOTP")`) can see it.

---

## 7. Wiring a second `AbstractAuthenticationProcessingFilter` for a second factor

This is the most important finding for implementation: **the authority merge is built into `AbstractAuthenticationProcessingFilter` itself in 7.x.** You do not write it, and there is no separate public "merge" helper class.

Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/web/src/main/java/org/springframework/security/web/authentication/AbstractAuthenticationProcessingFilter.java>

Inside `doFilter`, after `attemptAuthentication` returns a non-null result:

```java
Authentication current = this.securityContextHolderStrategy.getContext().getAuthentication();
if (shouldPerformMfa(current, authenticationResult)) {
	authenticationResult = authenticationResult.toBuilder()
		.authorities((a) -> {
			Set<String> newAuthorities = a.stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toUnmodifiableSet());
			for (GrantedAuthority currentAuthority : current.getAuthorities()) {
				if (!newAuthorities.contains(currentAuthority.getAuthority())) {
					a.add(currentAuthority);
				}
			}
		})
		.build();
}
this.sessionStrategy.onAuthentication(authenticationResult, request, response);
if (this.continueChainBeforeSuccessfulAuthentication) {
	chain.doFilter(request, response);
}
successfulAuthentication(request, response, chain, authenticationResult);
```

The gate:

```java
@Contract("null, _ -> false")
private boolean shouldPerformMfa(@Nullable Authentication current, Authentication authenticationResult) {
	if (!this.mfaEnabled) {
		return false;
	}
	if (current == null || !current.isAuthenticated()) {
		return false;
	}
	if (!declaresToBuilder(authenticationResult)) {
		return false;
	}
	return current.getName().equals(authenticationResult.getName());
}
```

`declaresToBuilder(...)` uses reflection to check that the *concrete* `Authentication` class declares a no-arg `toBuilder()`.

Four requirements for a custom second-factor filter to merge correctly:

1. `setMfaEnabled(true)` must be on. Signature: `public void setMfaEnabled(boolean mfaEnabled)`; field default is `false`. It is set automatically by `EnableMfaFiltersConfiguration`'s `BeanPostProcessor` (source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/authorization/EnableMfaFiltersConfiguration.java>) for beans of type `AbstractAuthenticationProcessingFilter`, `AuthenticationFilter`, `AbstractPreAuthenticatedProcessingFilter`, and `BasicAuthenticationFilter`. **Because it is a `BeanPostProcessor`, a filter instantiated with `new` inside a `SecurityFilterChain` `@Bean` method — rather than registered as its own bean — will not be post-processed and will silently not merge.**
2. There must already be an authenticated `Authentication` in the `SecurityContext` when the second filter runs.
3. The second factor's `Authentication` implementation must declare a public no-arg `toBuilder()`.
4. `getName()` must match between the first and second authentications.

Replacement (not merging) of the context is then done by `successfulAuthentication`, which is unchanged from earlier versions:

```java
SecurityContext context = this.securityContextHolderStrategy.createEmptyContext();
context.setAuthentication(authResult);
this.securityContextHolderStrategy.setContext(context);
this.securityContextRepository.saveContext(context, request, response);
this.rememberMeServices.loginSuccess(request, response, authResult);
if (this.eventPublisher != null) {
	this.eventPublisher.publishEvent(new InteractiveAuthenticationSuccessEvent(authResult, this.getClass()));
}
this.successHandler.onAuthenticationSuccess(request, response, authResult);
```

So it *is* a wholesale replacement of the `Authentication` in a fresh `SecurityContext` — the merge happened earlier, in `doFilter`.

---

## 8. Events published by such a filter

- **`InteractiveAuthenticationSuccessEvent`** — published from `AbstractAuthenticationProcessingFilter.successfulAuthentication(...)` as `new InteractiveAuthenticationSuccessEvent(authResult, this.getClass())`, only if `this.eventPublisher != null` (the filter implements `ApplicationEventPublisherAware`). Import is `org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent`. The class javadoc confirms: no events are published on failure, "because this would generally be recorded via an `AuthenticationManager`-specific application event". Source as in §7.
- **`AuthenticationSuccessEvent`** — published by `ProviderManager`, not the filter, via its `AuthenticationEventPublisher`: `this.eventPublisher.publishAuthenticationSuccess(result)`. Two conditions from the source: it is skipped when the *parent* `AuthenticationManager` produced the result (`parentResult != null`) to avoid duplicates, and `ProviderManager`'s own default publisher is `NullEventPublisher` — "defaults to a null implementation which doesn't publish events, so if you are configuring the bean yourself you must inject a publisher bean if you want to receive events". The standard implementation is `DefaultAuthenticationEventPublisher`. Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/core/src/main/java/org/springframework/security/authentication/ProviderManager.java>
- Failures: `ProviderManager.prepareException(...)` calls `this.eventPublisher.publishAuthenticationFailure(ex, auth)` (suppressed when the parent already published).

Net effect for a second-factor filter built on `AbstractAuthenticationProcessingFilter` with the standard `HttpSecurity` setup: **two** success events per second-factor login — one `AuthenticationSuccessEvent` from `ProviderManager`, one `InteractiveAuthenticationSuccessEvent` from the filter. Importantly, the `InteractiveAuthenticationSuccessEvent` carries the **merged** `Authentication` (it is created in `successfulAuthentication` after the merge), whereas the `AuthenticationSuccessEvent` from `ProviderManager` carries the **unmerged** provider result — so audit logging that wants the full factor set should listen to the interactive event.

> ⚠️ **Not verified:** I did not confirm from primary source which component sets the `AuthenticationEventPublisher` on the `ProviderManager` built by `HttpSecurity`/Boot auto-configuration. Treat "`AuthenticationSuccessEvent` is actually published in a default Boot app" as likely but unverified here.

---

## 9. Session fixation on the second factor

**Yes, the framework invokes the session strategy on the second-factor authentication, in its own flows.**

- `AbstractAuthenticationProcessingFilter.doFilter` calls `this.sessionStrategy.onAuthentication(authenticationResult, request, response)` on **every** successful authentication attempt, positioned *after* the MFA authority merge and *before* `successfulAuthentication`. There is no MFA-specific bypass. Source as in §7.
- The filter's own field default is `NullAuthenticatedSessionStrategy` (`setSessionAuthenticationStrategy` javadoc: "If not set a null implementation is used").
- However, `AbstractAuthenticationFilterConfigurer.configure(B http)` injects the shared strategy into every filter built through it — including `OneTimeTokenLoginConfigurer`, which extends it:

```java
SessionAuthenticationStrategy sessionAuthenticationStrategy = http.getSharedObject(SessionAuthenticationStrategy.class);
if (sessionAuthenticationStrategy != null) {
	this.authFilter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
}
```

Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/AbstractAuthenticationFilterConfigurer.java>

- `SessionManagementConfigurer`'s default session-fixation strategy is `ChangeSessionIdAuthenticationStrategy`: `private static SessionAuthenticationStrategy createDefaultSessionFixationProtectionStrategy() { return new ChangeSessionIdAuthenticationStrategy(); }`, and `sessionAuthenticationStrategy(...)`'s javadoc states "The default is to use `ChangeSessionIdAuthenticationStrategy`". Source: <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/SessionManagementConfigurer.java>

So in a standard `formLogin()` + `oneTimeTokenLogin()` MFA app, `HttpServletRequest#changeSessionId()` is called **twice** — once on the password step and again on the OTT step. A custom second-factor filter registered via `AbstractAuthenticationFilterConfigurer` inherits this; one registered as a bare filter with `addFilter*` and no explicit strategy would keep `NullAuthenticatedSessionStrategy` and **not** rotate the session id.

> ⚠️ **Not verified:** I did not locate or read a framework MFA/OTT *sample application* under the 7.1.x repo, so "what the framework's own sample does about session fixation on the second step" is answered only at the configurer/filter level above, not from sample code.

---

## 10. Current 7.1.x release and Spring Boot 4.1.x pairing

| Fact | Value | Source |
| --- | --- | --- |
| Latest Spring Security 7.1.x GA | **7.1.1** | <https://api.github.com/repos/spring-projects/spring-security/releases?per_page=15> |
| `7.1.x` branch development version | `7.1.2-SNAPSHOT` | <https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/gradle.properties> |
| Latest Spring Boot 4.1.x GA | **4.1.1**, published 2026-08-20 | <https://api.github.com/repos/spring-projects/spring-boot/releases?per_page=10> |
| Spring Security managed by Boot 4.1.1 | **7.1.1** — release notes entry "Upgrade to Spring Security 7.1.1" (#51261) | same releases API response |
| Spring Security managed by Boot 4.1.0 | 7.1.0 — "Upgrade to Spring Security 7.1.0" (#50573) | same releases API response |
| Boot 4.1 documented managed versions | `spring-security-*` = `7.1.1`; property is `spring-security.version` | <https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html>, <https://docs.spring.io/spring-boot/appendix/dependency-versions/properties.html> |
| Next Boot line (not 4.1.x) | 4.2.0-M2 moves to Spring Security 7.2.0-M2 | same releases API response |

**Bottom line:** Spring Boot 4.1.1 + Spring Security 7.1.1 is the correct, self-consistent pairing; no version override is needed to get the 7.1 MFA API on Boot 4.1.x. Also note Boot 4.1.x pairs with Spring Framework 7.0.9 (4.1.1), i.e. Framework 7.1 is not yet in the 4.1.x line.

> Note on "as of late 2026": the newest GA releases visible in the GitHub releases API at the time of this check were Boot 4.1.1 / 4.0.8 / 3.5.16 (all published 2026-08-20) with 4.2.0-M2 on 2026-09-24. If a Boot 4.1.2 has shipped since, it would likely carry a 7.1.2.

---

## 11. Incidental source-quality observations (worth knowing before copying javadoc examples)

These are observations from reading the 7.1.x source; they are not Spring statements of intent.

- **Stale javadoc class name.** `@EnableMultiFactorAuthentication`'s example uses `GrantedAuthorities.FACTOR_OTT` / `GrantedAuthorities.FACTOR_PASSWORD`, and `DelegatingMissingAuthorityAccessDeniedHandler`'s example uses `GrantedAuthorities.FACTOR_OTT_AUTHORITY`. Neither the class `GrantedAuthorities` nor those constant names match the actual API — use `FactorGrantedAuthority.OTT_AUTHORITY` / `FactorGrantedAuthority.PASSWORD_AUTHORITY` (§2).
- **`anonymous()` vs its javadoc** in `DefaultAuthorizationManagerFactory` (§1b).
- **Typo in an assertion message:** `DelegatingMissingAuthorityAccessDeniedHandler.setRequestCache` asserts with `"requestCachgrantedaue cannot be null"`.
- **`AbstractAuthenticationProcessingFilter.setSecurityContextRepository` javadoc** says "The default action is not to save the `SecurityContext`", while the field default is `new RequestAttributeSecurityContextRepository()`.
- `DelegatingMissingAuthorityAccessDeniedHandler`'s default `RequestCache` is `NullRequestCache`; the redirect-back-after-second-factor behaviour depends on `ExceptionHandlingConfigurer` replacing it with the shared cache (`HttpSessionRequestCache`). A hand-built handler that forgets `setRequestCache(...)` loses the saved request.

---

## Summary of unverified / flagged items

1. §4 — the `ExceptionTranslationFilter` link in the chain (`AccessDeniedException` → `AccessDeniedHandler`) was not re-read on 7.1.x.
2. §6 — TOTP absence is established by four converging negative signals, not an explicit Spring statement; GitHub trees API truncation and lack of authenticated code search mean it is not absolute.
3. §8 — which component installs the `AuthenticationEventPublisher` on the `HttpSecurity`-built `ProviderManager` was not verified, so real-world emission of `AuthenticationSuccessEvent` is inferred.
4. §9 — no framework MFA/OTT sample application was located; session-fixation behaviour is answered from configurer + filter source only.
5. Throughout — `docs.spring.io/.../api/` "current" javadoc self-reports as 7.0.0, so 7.1-only members (`when`, `withWhen`, `AllRequiredFactorsAuthorizationManager.anyOf`, `MultiFactorCondition`) are verified from 7.1.x branch source rather than published javadoc.
6. `MultiFactorCondition` and `WhenWebAuthnRegisteredMfaConfiguration` were confirmed only by reference from `MultiFactorAuthenticationSelector` / `EnableMultiFactorAuthentication`; their own sources were not read, so `MultiFactorCondition`'s full constant set beyond `WEBAUTHN_REGISTERED` is unknown.
