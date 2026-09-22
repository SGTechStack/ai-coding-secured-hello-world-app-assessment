# Critical Transaction MFA — Reimplementation Recipes

> Extends [Unified MFA Reimplementation Recipes](../MFA_Core/Base_Standalone_Reimplementation_Recipes.md) with AOP enforcement recipes for gating critical transaction operations behind a second factor, as described in the Critical Transaction MFA Standard.
>
> All code targets JDK 17+ with Spring Boot, Spring AOP, and Spring Security.

---

## Recipe CT1: AOP Enforcer Aspect

**Goal**: Intercept methods annotated with `@MultiFactorAuthentication` and execute the fixed enforcement sequence: pre-auth logic → (optional) pre-authorize → authenticate → post-auth logic → log → proceed.

**How it works in the reference**: `MultiFactorAuthenticationAspect` is an `@Aspect` component with a single `@Around` advice. It delegates role checking to `RoleAuthorizer` (optional) and factor verification to the provider resolved from `MFATypeContainer`. Pre and post-authentication logic runs directly within the aspect method.

### Step 1: Marker annotation

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MultiFactorAuthentication {
    String mfaType();
    String[] privileges() default {};
}
```

### Step 2: Aspect implementation

```java
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class MultiFactorAuthenticationAspect {
    private final MFARequestContext mfaRequestContext;
    private final MFATypeContainer mfaTypeContainer;
    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    private RoleAuthorizer roleAuthorizer;   // optional — omit bean from context when role check not required

    /**
     * Scans all Spring beans at startup and validates that every mfaType value declared
     * on @MultiFactorAuthentication annotations is registered in MFATypeContainer.
     *
     * Catches annotation typos (e.g. mfaType = "PILN" when only "PIN" is registered)
     * before the application begins serving traffic, rather than at the first runtime
     * invocation of the affected method.
     *
     * ClassUtils.getUserClass() unwraps CGLIB-proxied bean types so that annotations
     * on the real class are visible.
     */
    @PostConstruct
    public void validateAnnotationMfaTypes() {
        List<String> errors = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) continue;
            Class<?> targetClass = ClassUtils.getUserClass(beanType);
            for (Method method : targetClass.getMethods()) {
                MultiFactorAuthentication annotation = method.getAnnotation(MultiFactorAuthentication.class);
                if (annotation == null) continue;
                String mfaType = annotation.mfaType();
                if (mfaTypeContainer.getAuthenticationProvider(mfaType) == null) {
                    errors.add(String.format(
                        "  %s.%s() — mfaType=\"%s\" is not registered in MFATypeContainer (registered: %s)",
                        targetClass.getSimpleName(), method.getName(), mfaType,
                        mfaTypeContainer.registeredTypes()));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "[MultiFactorAuthenticationAspect] @MultiFactorAuthentication annotation validation failed.\n" +
                "The following methods reference an unregistered mfaType — check for typos or missing " +
                "provider registrations in MFATypeContainerConfig:\n" +
                String.join("\n", errors));
        }
    }

    @Around("@annotation(org.example.mfa.MultiFactorAuthentication)")
    public Object performMFAAuthentication(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        MultiFactorAuthentication annotation = method.getAnnotation(MultiFactorAuthentication.class);

        // Pre-authentication logic — add any custom pre-verification behaviour here

        if (roleAuthorizer != null &&
                !roleAuthorizer.preAuthorize(new ArrayList<>(Arrays.asList(annotation.privileges())))) {
            throw new InsufficientPrivilegeException();
        }

        MultiFactorAuthenticationProvider provider =
            mfaTypeContainer.getAuthenticationProvider(annotation.mfaType());
        provider.authenticate();

        // Post-authentication logic — add any custom post-verification behaviour here

        log.info("Critical Transaction - {} [{}] at {} (factorType: {})", method.getName(),
            mfaRequestContext.getUsername(), Instant.now(), annotation.mfaType());

        return joinPoint.proceed();
    }
}
```

### Validation Rules

*   The aspect MUST be annotated with `@Order(Ordered.HIGHEST_PRECEDENCE)` to ensure it executes **outside** the `@Transactional` boundary. Without explicit ordering, the MFA aspect and `TransactionInterceptor` have undefined relative execution order. If the transaction wraps the MFA aspect, a failure in the business method rolls back the factor provider's state changes (e.g., `lastUsedCounter`), causing valid codes to appear replayed on retry.
*   `validateAnnotationMfaTypes()` runs at startup (via `@PostConstruct`) before any request is served. It uses `applicationContext.getBeanDefinitionNames()` + `ClassUtils.getUserClass()` (to unwrap CGLIB proxies) to scan all beans for `@MultiFactorAuthentication` annotations. If any `mfaType` value is not registered in `MFATypeContainer`, startup fails with an `IllegalStateException` naming the offending method and the unregistered value.
*   `ApplicationContext` is constructor-injected via `@RequiredArgsConstructor` — Spring automatically provides it as a first-class injectable dependency.
*   `registeredTypes()` must be exposed on `MFATypeContainer` — see Recipe 13 in the base recipes for the implementation.
*   Pre-authentication logic executes before role check (if configured). If it throws, the entire enforcement chain aborts.
*   The `log.info` critical transaction line fires unconditionally after post-authentication logic, before `proceed()`.
*   Role pre-authorization via `RoleAuthorizer` is optional. When not required, omit the bean from the application context — Spring will inject `null` via `@Autowired(required = false)`.
*   Because `validateAnnotationMfaTypes()` catches all `mfaType` mismatches at startup, `provider.authenticate()` in the `@Around` advice will never receive a `null` provider at runtime.

---

## Recipe CT2: InsufficientPrivilegeException and HTTP Mapping

**Goal**: Define the `InsufficientPrivilegeException` thrown by the AOP enforcer and add its `403` handler to the MFA exception handler (see base `MFAExceptionHandler` in Unified recipes).

### Exception

```java
public class InsufficientPrivilegeException extends RuntimeException {
    public InsufficientPrivilegeException() { super("Insufficient privileges for critical transaction"); }
}
```

### Handler addition (add to `MFAExceptionHandler`)

```java
@ExceptionHandler(InsufficientPrivilegeException.class)
public ProblemDetail handlePrivilege(InsufficientPrivilegeException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    pd.setTitle("Insufficient Privileges");
    pd.setDetail(ex.getMessage());
    return pd;
}
```

### Validation Rules

*   Insufficient privilege → `403 Forbidden`.
*   This exception is thrown only by `RoleAuthorizer` inside the AOP aspect — it is never thrown by factor providers.

---

## Recipe CT3: MFA Type Container Registration

**Goal**: Populate the `MFATypeContainer` at startup by mapping each `mfaType` string key to its corresponding provider bean. The enforcer resolves the provider from this map at runtime using the `mfaType` value declared on the annotation.

**How it works in the reference**: A `@Configuration` class injects all provider beans and constructs the container with an immutable map.

```java
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import java.util.Set;

@Configuration
public class MFATypeContainerConfig {

    @Bean
    public MFATypeContainer mfaTypeContainer(PINAuthenticationProvider pin,
                                              TOTPAuthenticationProvider totp) {
        return new MFATypeContainer(Map.of("PIN", pin, "TOTP", totp));
    }

    /**
     * Validates the container at startup: no blank keys, no null providers, at least one entry.
     * Catches registration-side mistakes (e.g. a blank key in Map.of(), a null provider bean).
     * Annotation-side typos (e.g. mfaType = "PILN" on a method) are caught by the
     * @PostConstruct in MultiFactorAuthenticationAspect (Recipe CT1).
     *
     * @Configuration CGLIB proxy ensures mfaTypeContainer() returns the singleton bean.
     */
    @PostConstruct
    public void validateMfaTypeContainer() {
        Set<String> keys = mfaTypeContainer().registeredTypes();
        if (keys.isEmpty()) {
            throw new IllegalStateException(
                "[MFATypeContainerConfig] No mfaType providers registered. " +
                "At least one provider must be present at startup.");
        }
        keys.forEach(key -> {
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                    "[MFATypeContainerConfig] Blank mfaType key detected. " +
                    "Verify all Map.of() keys in MFATypeContainerConfig are non-blank strings.");
            }
            if (mfaTypeContainer().getAuthenticationProvider(key) == null) {
                throw new IllegalStateException(
                    "[MFATypeContainerConfig] Null provider for mfaType '" + key + "'. " +
                    "Verify the provider bean for this key is defined and not null.");
            }
        });
    }
}
```

### Validation Rules

*   Every `mfaType` string used in any `@MultiFactorAuthentication` annotation MUST have a corresponding entry in this map. Annotation-side mismatches are caught at startup by the `@PostConstruct` validation in `MultiFactorAuthenticationAspect` (Recipe CT1) — not at runtime.
*   Key strings must exactly match the `mfaType` annotation attribute values (case-sensitive).
*   The `@PostConstruct` here catches registration mistakes in this config class (blank keys, null providers). The aspect's `@PostConstruct` catches consumption mistakes (annotation `mfaType` values that don't match any registered key). Both are required.
*   `registeredTypes()` is part of the `MFATypeContainer` contract — see Recipe 13 in the base recipes for the implementation.
*   If only one factor type is used, the aspect may invoke the provider directly without a container — see §4.2 of the Critical Transaction MFA Standard for the design choice.
