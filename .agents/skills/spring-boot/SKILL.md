# Spring Boot — Project Skill

Core development skill for the demo-app backend. Provides workflow, templates, constraints, and references for building features in this Spring Boot 4 application.

---

## Lombok conventions

Use Lombok everywhere to eliminate boilerplate. Quick reference:

| Where                                                                | Annotations                                                                                                        |
| -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `@Service`, `@Component`, `@RestController`, `@RestControllerAdvice` | `@RequiredArgsConstructor` (replaces constructor injection boilerplate)                                            |
| JPA entity                                                           | `@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` (note: `@Builder.Default` on collection fields) |
| Response record / DTO record                                         | `@Builder` on the record                                                                                           |
| Command / value object (non-record)                                  | `@Builder` + `@Value` (immutable) or `@Getter`                                                                     |
| Logging                                                              | `@Slf4j`                                                                                                           |

**Record formatting — always wrap record field declarations with `// spotless:off` / `// spotless:on`:**
Spotless reformats record parameters in a way that mangles per-field annotations and removes blank lines. Suppress it around every record declaration. Rules inside the guard:

- Single annotation → keep on the same line as the type: `@NotNull String name`
- Multiple annotations → each annotation on its own line, type on the next line
- Blank line between each parameter

```java
// spotless:off
@Builder
public record MyRequest(
    @NotNull String name,

    @NotNull
    @Positive
    BigDecimal amount,

    String description
) {
// spotless:on
    // body...
}
```

This applies to **all** records — request DTOs, response DTOs, and internal application records — whether or not they have per-field annotations.

**Builder rule — always use named builder, never positional constructor:**
Positional calls on records/classes with same-typed fields (e.g. two `UUID`s) compile silently but produce wrong data if fields are reordered.

**Entity `@Builder` goes at the class level.** Pair it with `@AllArgsConstructor` (required by Lombok's class-level builder) and keep the domain constructor or static factory for invariant-enforcing creation where needed.

---

## Workflow

When implementing a new feature or fixing a bug:

1. **Analyze** — Understand the requirement. Identify which domain this belongs to.
2. **Design** — Decide on the package structure, API contract, and data model.
3. **Implement** — Follow the DDD layer structure: `domain` → `application` → `api`.
4. **Secure** — Apply authentication/authorization rules.
5. **Test** — Unit tests for domain/service logic, integration tests for API endpoints.
6. **Format** — Run `./mvnw spotless:apply` before committing.

---

## Package Structure

New features follow this DDD-layered structure:

```
src/main/java/org/eds/demo/{feature}/
├── api/                    # Controllers + Response DTOs
│   ├── {Feature}Controller.java
│   └── {Feature}Response.java
├── application/            # Use-cases / Services
│   ├── {Feature}Service.java
│   └── commands/           # Input command objects (optional)
└── domain/                 # Entities, Value Objects, Repository interfaces
    ├── {Feature}.java      # JPA Entity
    └── {Feature}Repository.java
```

**Dependency rules:**

- `api` → `application` → `domain` (never the reverse, never skip a layer)
- Controllers MUST call services; controllers MUST NOT call repositories directly
- `domain` has zero Spring dependencies (except JPA annotations)
- `api` layer handles HTTP concerns (validation, status codes, response mapping)
- `application` layer handles business logic, transactions, orchestration
- Even for simple CRUD, always go through a service — no "pass-through" shortcuts

---

## Quick-Start Templates

### Entity

```java
package org.eds.demo.order.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.eds.demo.common.BaseAuditableEntity;

@Entity
@Table(name = "app_order")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Version
    private Long version;

    public void submit() {
        if (this.status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Only draft orders can be submitted");
        }
        this.status = OrderStatus.SUBMITTED;
    }
}
```

### Repository

```java
package org.eds.demo.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
}
```

### Service

```java
package org.eds.demo.order.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eds.demo.order.domain.Order;
import org.eds.demo.order.domain.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        var order = Order.builder()
            .customer(command.customer())
            .build();
        return orderRepository.save(order);
    }

    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
```

### Controller

```java
package org.eds.demo.order.api;

import lombok.RequiredArgsConstructor;
import org.eds.demo.order.application.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        var order = orderService.createOrder(request.toCommand());
        return OrderResponse.from(order);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        var order = orderService.getOrder(id);
        return OrderResponse.from(order);
    }
}
```

### Response DTO

Always annotate response records with `@Builder` and use the named builder in `from()` — never the positional constructor. Positional calls on records with same-typed fields (e.g. two `UUID`s) compile silently but produce wrong data if fields are reordered.

```java
package org.eds.demo.order.api;

import lombok.Builder;
import org.eds.demo.order.domain.Order;
import java.time.Instant;

// spotless:off
@Builder
public record OrderResponse(
    Long id,

    String status,

    Instant createdAt,

    String createdByUserName
) {
// spotless:on
    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .createdByUserName(order.getCreatedByUserName())
                .build();
    }
}
```

### Request DTO

```java
package org.eds.demo.order.api;

import jakarta.validation.constraints.NotNull;
import org.eds.demo.order.application.CreateOrderCommand;

// spotless:off
public record CreateOrderRequest(
    @NotNull Long customerId
) {
// spotless:on
    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(customerId);
    }
}
```

### Global Exception Handler

```java
package org.eds.demo.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", ex.getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConflict(OptimisticLockingFailureException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setDetail("Resource was modified by another user. Please retry.");
        return problem;
    }
}
```

---

## Constraints

### MUST DO

- `@RequiredArgsConstructor` on all Spring-managed classes — replaces manual constructor injection
- `@Slf4j` on any class that logs
- `@Getter` + `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` on JPA entities
- `@Builder` on all response/DTO records; call `.builder()...build()` in `from()` — never positional constructors
- Wrap all record declarations with `// spotless:off` / `// spotless:on`; single annotation → same line as type; multiple annotations → each on its own line; blank line between each parameter
- `@Transactional` on service methods (never controllers or repositories)
- Use records for DTOs
- Validate input with Jakarta Validation annotations
- Return `ProblemDetail` (RFC 9457) for error responses
- Run `./mvnw spotless:apply` before committing
- Use `FetchType.LAZY` for all JPA associations
- Extend `BaseAuditableEntity` for all entities
- No magic numbers or strings — extract non-trivial literals into named constants (inline comment for purpose/context): an **enum** for a closed set of interchangeable named variants (esp. with associated data/behavior), a plain **constant** for a single fixed literal used in one place, a **configurable property** (`application*.properties`) only when the value should vary by profile/environment or be tunable without a code change

### MUST NOT DO

- Use `@Autowired` on fields — always `@RequiredArgsConstructor`
- Annotate Spring Data repository interfaces with `@Repository` — the proxy bean is created automatically when extending `JpaRepository` (or any `Repository`); the annotation is redundant
- Use positional constructors for records/classes with 3+ fields — use `.builder()...build()`
- Skip layers — controllers must never inject or call repositories directly
- Expose JPA entities in API responses
- Put business logic in controllers
- Use `CascadeType.ALL` without deliberate justification
- Commit credentials or secrets
- Use `FetchType.EAGER`
- Return `null` from methods — use `Optional` for single values, empty collections for lists

---

## References

- [Web Patterns](references/web.md) — REST controllers, validation, exception handling, CORS
- [Data Patterns](references/data.md) — JPA entities, repositories, specifications, transactions
- [Security Patterns](references/security.md) — Session-based auth, CSRF, authorization
- [Testing Patterns](references/testing.md) — Unit tests, integration tests, MockMvc, security testing
