# JPA Patterns — Spring Data JPA + MS SQL Server

Reference for JPA/Hibernate patterns in this project. Covers entity design, auditing, query optimization, Liquibase migrations, and MS SQL Server specifics.

---

## Lombok Best Practices for JPA Entities

### DO use

| Annotation                                           | Where                  | Why                                                                      |
| ---------------------------------------------------- | ---------------------- | ------------------------------------------------------------------------ |
| `@Getter`                                            | Class level            | Safe — generates all getters                                             |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | Class level            | JPA requires no-arg constructor, `protected` prevents misuse             |
| `@Setter`                                            | Individual fields only | Only on fields that need external mutation (e.g., audit listener fields) |
| `@Slf4j`                                             | Class level            | Logging                                                                  |

### DO NOT use

| Annotation              | Why                                                                                                            |
| ----------------------- | -------------------------------------------------------------------------------------------------------------- |
| `@Setter` (class level) | Breaks encapsulation — use domain methods to enforce invariants                                                |
| `@ToString`             | Can trigger lazy loading on associations → `LazyInitializationException`                                       |
| `@EqualsAndHashCode`    | Entity identity is based on ID, not all fields. Lombok's default uses all fields which breaks with JPA proxies |
| `@Data`                 | Combines `@Setter`, `@ToString`, `@EqualsAndHashCode` — all problematic for entities                           |
| `@Builder`              | Bypasses domain invariants enforced in constructors                                                            |
| `@AllArgsConstructor`   | Exposes internal structure; use explicit constructors with business meaning                                    |

### Entity template

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "app_order")
public class Order extends BaseAuditableEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", updatable = false, nullable = false)
    @Getter(AccessLevel.NONE)
    private UUID id;

    // Typed getter — wraps raw UUID in the domain value type.
    // The @Id field stays UUID because Hibernate 7.2 forbids AttributeConverter on @Id.
    public OrderId getId() {
        return id == null ? null : new OrderId(id);
    }

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    public Order(Customer customer) {
        // Business constructor enforcing invariants
    }

    public void submit() {
        // Domain method — not a setter
    }
}
```

**Typed ID records** — one per aggregate, in the domain layer:

```java
public record OrderId(UUID value) implements TypedId, Serializable {
    public OrderId { Objects.requireNonNull(value); }
}
```

All typed ID records implement `TypedId` (from `common`) so the shared `UuidWrapperConverter<T extends TypedId>` base class covers non-PK column conversions without duplication.

**Boundary rules:**

- `@Id` field: raw `UUID` (Hibernate constraint) — exposed via typed getter
- Non-PK FK/audit columns that hold another aggregate's ID: typed field + `@Convert(converter = XIdConverter.class)`
- Repository ID type param: `UUID` (matches the `@Id` field type)
- Service/controller method signatures: typed IDs throughout
- DTOs and JSON wire: raw `UUID` — unwrap with `.value()` in mappers

---

## Auditing Base Entity

All entities extend `BaseAuditableEntity` which provides denormalized audit columns to avoid joins for common "who created/modified this" queries.

```java
@Getter
@MappedSuperclass
@EntityListeners({AuditingEntityListener.class, AuditUserListener.class})
public abstract class BaseAuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // UserId is a typed ID record — stored via AttributeConverter (works on non-PK columns).
    @Setter
    @Convert(converter = UserIdConverter.class)
    @Column(name = "created_by_user_id", updatable = false)
    private UserId createdByUserId;

    @Setter
    @Convert(converter = UserIdConverter.class)
    @Column(name = "updated_by_user_id")
    private UserId updatedByUserId;

    @Setter
    @Column(name = "created_by_user_display_name", updatable = false)
    private String createdByUserDisplayName;

    @Setter
    @Column(name = "updated_by_user_display_name")
    private String updatedByUserDisplayName;
}
```

**Do not use `@CreatedBy` / `@LastUpdatedBy`.** Spring's `AuditorAware` returns a single value; we need both user ID and display name, so a `@PrePersist`/`@PreUpdate` listener pulls both from `SecurityContextHolder` directly:

```java
public class AuditUserListener {

    @PrePersist
    public void onPrePersist(BaseAuditableEntity entity) {
        entity.setCreatedByUserDisplayName(getCurrentDisplayName());
        entity.setUpdatedByUserDisplayName(getCurrentDisplayName());
        entity.setCreatedByUserId(getCurrentUserId());
        entity.setUpdatedByUserId(getCurrentUserId());
    }

    @PreUpdate
    public void onPreUpdate(BaseAuditableEntity entity) {
        entity.setUpdatedByUserDisplayName(getCurrentDisplayName());
        entity.setUpdatedByUserId(getCurrentUserId());
    }

    private UserId getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof AppUserDetails details) return details.getUserId();
        return null;
    }

    private String getCurrentDisplayName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        if (auth.getPrincipal() instanceof AppUserDetails details) return details.getDisplayName();
        return auth.getName();
    }
}
```

---

## N+1 Problem

### Detection

Enable SQL logging in `unsafe-debug` profile:

```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql=TRACE
```

Look for repeated SELECT statements when loading a collection.

### Solutions

**JOIN FETCH in JPQL:**

```java
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.customer.id = :customerId")
List<Order> findByCustomerIdWithItems(@Param("customerId") Long customerId);
```

**@EntityGraph:**

```java
@EntityGraph(attributePaths = {"items", "items.product"})
List<Order> findByCustomerId(Long customerId);
```

**@BatchSize (on the entity relationship):**

```java
@OneToMany(mappedBy = "order")
@BatchSize(size = 25)
private List<OrderItem> items;
```

### When to Use Each

| Approach     | Use When                                                       |
| ------------ | -------------------------------------------------------------- |
| JOIN FETCH   | You always need the association for this query                 |
| @EntityGraph | Same query used with/without association in different contexts |
| @BatchSize   | Loading many parent entities, each with a lazy collection      |

---

## Fetch Strategy

- **Default to `FetchType.LAZY`** for all associations (`@OneToMany`, `@ManyToOne`, `@ManyToMany`)
- Spring Data JPA defaults: `@OneToMany` = LAZY, `@ManyToOne` = EAGER — override `@ManyToOne` to LAZY explicitly
- Never use `FetchType.EAGER` — it removes your ability to optimize later

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
private Customer customer;
```

---

## Projections

Use projections when you don't need the full entity:

**Interface projection (read-only, efficient):**

```java
public interface OrderSummary {
    Long getId();
    String getStatus();
    Instant getCreatedAt();
    String getCreatedByUserName();
}

List<OrderSummary> findByStatus(String status);
```

**Record projection (for DTOs):**

```java
@Query("SELECT new org.eds.demo.order.api.OrderSummaryDto(o.id, o.status, o.createdAt) FROM Order o WHERE o.status = :status")
List<OrderSummaryDto> findSummariesByStatus(@Param("status") String status);
```

---

## Pagination

Always paginate list endpoints:

```java
Page<OrderSummary> findByCustomerId(Long customerId, Pageable pageable);
```

Never load unbounded collections. If you need streaming for batch processing:

```java
@QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "50"))
@Query("SELECT o FROM Order o WHERE o.status = :status")
Stream<Order> streamByStatus(@Param("status") String status);
```

---

## Specifications (Dynamic Queries)

For complex search/filter endpoints:

```java
public class OrderSpecifications {

    public static Specification<Order> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdAfter(Instant after) {
        return (root, query, cb) -> after == null ? null : cb.greaterThan(root.get("createdAt"), after);
    }
}
```

Repository extends `JpaSpecificationExecutor<Order>`:

```java
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {}
```

---

## Transaction Management

- `@Transactional` on service methods only
- `@Transactional(readOnly = true)` for queries — enables Hibernate flush mode MANUAL and allows DB read replicas
- Keep transactions short — no external API calls inside a transaction
- Use `REQUIRES_NEW` sparingly (audit logging is a valid case)

```java
@Service
@Transactional(readOnly = true)
public class OrderService {

    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        // write operation
    }

    public OrderSummary getOrder(Long id) {
        // read-only, inherits class-level annotation
    }
}
```

---

## Optimistic Locking

Use `@Version` for entities that may be concurrently modified:

```java
@Version
@Column(name = "version")
private Long version;
```

Handle `OptimisticLockException` at the service layer — translate to a 409 Conflict response.

---

## MS SQL Server Specifics

### Primary Key Strategy

Choose based on sensitivity:

**Long ID (non-sensitive entities)** — simpler, faster joins, smaller index:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

**UUIDv7 stored as `binary(16)` (sensitive entities)** — prevents enumeration attacks and keeps the clustered index append-only:

```java
@Id
@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
@JdbcTypeCode(SqlTypes.BINARY)                        // binary(16) column — NOT uniqueidentifier
@Column(name = "id", updatable = false, nullable = false)
@Getter(AccessLevel.NONE)
private UUID id;

public OrderId getId() {                              // typed getter wraps the raw UUID
    return id == null ? null : new OrderId(id);
}
```

Why `binary(16)` and not `uniqueidentifier`: SQL Server sorts `uniqueidentifier` on its _last_ 6 bytes, so UUIDv7 stored there is not sequential. `binary(16)` sorts byte-by-byte left-to-right; UUIDv7's leading 48 bits are a Unix-millis timestamp, so each generated key is greater than the last — rows append onto the rightmost clustered index leaf (low fragmentation). The column must be hand-written as `binary(16)` in the migration script; `ddl-auto=none` means Hibernate never emits it.

Why `@UuidGenerator(VERSION_7)` and not `@GeneratedValue(UUID)`: Hibernate 7.2's `@GeneratedValue(UUID)` strategy produces UUIDv4 (random). `@UuidGenerator(VERSION_7)` is Hibernate's native RFC 9562 UUIDv7 generator. Do not confuse `Style.VERSION_7` with `Style.TIME` — the latter is Hibernate's legacy time-based layout and does not give byte-ordered storage.

Why the `@Id` field stays raw `UUID`: Hibernate 7.2 forbids `AttributeConverter` and `@JavaType` on `@Id` fields. The typed getter is the correct interim until app-assigned IDs (`Persistable<TypedId>`) are adopted.

| Use Long                          | Use UUIDv7 + binary(16)                 |
| --------------------------------- | --------------------------------------- |
| Lookup tables, categories, config | Users, orders, payments, documents      |
| Internal-only entities            | Anything exposed in URLs or APIs        |
| High-volume join targets          | Entities where sequential IDs leak info |

Do NOT use `GenerationType.SEQUENCE` unless you explicitly create a sequence (MS SQL supports sequences but IDENTITY is the convention for Long IDs).

### Pagination

MS SQL uses `OFFSET ... FETCH NEXT` (supported by Hibernate dialect automatically via `Pageable`). No special handling needed.

### String Types

- `VARCHAR` for ASCII-only columns
- `NVARCHAR` for Unicode (use this for user-facing text, names, etc.)

In entity mapping:

```java
@Column(name = "display_name", columnDefinition = "NVARCHAR(255)")
private String displayName;
```

### Datetime

Use `DATETIME2` (not `DATETIME`) — higher precision, larger range. Maps to `Instant` or `LocalDateTime` in Java.

### Deadlock Awareness

MS SQL is more prone to deadlocks than PostgreSQL. Mitigations:

- Keep transactions short
- Access tables in a consistent order across transactions
- Use `READ_COMMITTED_SNAPSHOT` isolation (configured at DB level)
- Add retry logic for `@Transactional` methods that may deadlock

---

## Anti-Patterns to Avoid

- **Open Session in View** — already disabled (`spring.jpa.open-in-view=false`). Never re-enable.
- **Exposing entities in API responses** — always use DTOs/records.
- **`CascadeType.ALL`** — be explicit about which cascades you need. `REMOVE` cascade is dangerous.
- **Bidirectional relationships without a clear owner** — always set `mappedBy` on the non-owning side.
- **`toString()` on entities with lazy associations** — will trigger loads or throw `LazyInitializationException`.
- **Mutable entity setters exposed broadly** — use domain methods that enforce invariants.
