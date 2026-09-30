# Web Patterns

## REST Controller Structure

```java
@RestController
@RequestMapping("/api/{resource}")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService service;

    @GetMapping
    public Page<ResourceResponse> list(Pageable pageable) {
        return service.findAll(pageable).map(ResourceResponse::from);
    }

    @GetMapping("/{id}")
    public ResourceResponse get(@PathVariable Long id) {
        return ResourceResponse.from(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
        return ResourceResponse.from(service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public ResourceResponse update(@PathVariable Long id, @Valid @RequestBody UpdateResourceRequest request) {
        return ResourceResponse.from(service.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

---

## Request Validation

Use Jakarta Validation on request DTOs:

```java
// spotless:off
public record CreateResourceRequest(
    @NotBlank
    @Size(max = 255)
    String name,

    @NotNull
    @Email
    String email,

    @Size(max = 1000)
    String description,

    @NotNull
    @Positive
    Long categoryId
) {
// spotless:on
    public CreateResourceCommand toCommand() {
        return new CreateResourceCommand(name.trim(), email, description, categoryId);
    }
}
```

### Custom Validators

```java
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Constraint(validatedBy = NoProfanityValidator.class)
public @interface NoProfanity {
    String message() default "Contains prohibited content";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class NoProfanityValidator implements ConstraintValidator<NoProfanity, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true; // let @NotNull handle nulls
        return !ProfanityFilter.contains(value);
    }
}
```

---

## Error Responses — ProblemDetail (RFC 9457)

All error responses use Spring's `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request body contains invalid fields",
  "instance": "/api/orders",
  "errors": [{ "field": "email", "message": "must not be blank" }]
}
```

Handled centrally via `@RestControllerAdvice` (see main SKILL.md template).

---

## Pagination

Always return `Page<T>` for list endpoints. Spring auto-resolves `Pageable` from query params:

```
GET /api/orders?page=0&size=20&sort=createdAt,desc
```

Default page size configured in properties:

```properties
spring.data.web.pageable.default-page-size=20
spring.data.web.pageable.max-page-size=100
```

---

## CORS

Configured centrally (not per-controller):

```java
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173") // frontend dev server
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowCredentials(true);
    }
}
```

In production, CORS is unnecessary since the backend serves the frontend as static files (same origin).
