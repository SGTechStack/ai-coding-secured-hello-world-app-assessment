# Data Patterns

## Entity Design

- Extend `BaseAuditableEntity` for audit columns
- Use `GenerationType.IDENTITY` for MS SQL auto-increment
- `@Version` for optimistic locking on any concurrently-modified entity
- Domain methods enforce invariants — no public setters
- Protected no-arg constructor for JPA

```java
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

    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Version
    private Long version;

    public void addItem(Product product, int quantity) {
        if (this.status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Cannot add items to a non-draft order");
        }
        this.items.add(new OrderItem(this, product, quantity));
    }
}
```

---

## Repository

Extend `JpaRepository` + `JpaSpecificationExecutor` for dynamic queries. Do not add `@Repository` — extending a Spring Data interface registers the proxy bean automatically, so the annotation is redundant:

```java
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findWithItemsById(Long id);

    Page<OrderSummary> findByCustomerId(Long customerId, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.customer WHERE o.status = :status")
    List<Order> findByStatusWithCustomer(@Param("status") OrderStatus status);
}
```

---

## Specifications (Dynamic Filters)

```java
public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from != null && to != null) return cb.between(root.get("createdAt"), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            return cb.lessThanOrEqualTo(root.get("createdAt"), to);
        };
    }

    public static Specification<Order> belongsToCustomer(Long customerId) {
        return (root, query, cb) -> customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }
}
```

Usage in service:

```java
public Page<Order> search(OrderSearchCriteria criteria, Pageable pageable) {
    var spec = Specification.where(hasStatus(criteria.status()))
            .and(createdBetween(criteria.from(), criteria.to()))
            .and(belongsToCustomer(criteria.customerId()));
    return orderRepository.findAll(spec, pageable);
}
```

---

## Transactions

- Class-level `@Transactional(readOnly = true)` for services
- Override with `@Transactional` on write methods
- Never wrap external API calls in a transaction

```java
@Service
@Transactional(readOnly = true)
public class OrderService {

    @Transactional
    public Order submitOrder(Long orderId) {
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.submit();
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuditEvent(AuditEvent event) {
        // Separate transaction so audit is saved even if parent rolls back
        auditRepository.save(event);
    }
}
```

---

## Projections

**Interface projection (read-only, Spring Data generates impl):**

```java
public interface OrderSummary {
    Long getId();
    String getStatus();
    Instant getCreatedAt();
    String getCreatedByUserName();
}
```

**Record projection (via JPQL constructor expression):**

```java
@Query("""
    SELECT new org.eds.demo.order.api.OrderListItem(o.id, o.status, o.createdAt, c.displayName)
    FROM Order o JOIN o.customer c
    WHERE o.status = :status
    """)
Page<OrderListItem> findListItems(@Param("status") OrderStatus status, Pageable pageable);
```

---

## Soft Delete (optional pattern)

If entities should not be physically deleted:

```java
@Column(name = "deleted", nullable = false)
private boolean deleted = false;

@Column(name = "deleted_at")
private Instant deletedAt;

public void markDeleted() {
    this.deleted = true;
    this.deletedAt = Instant.now();
}
```

Add `@Where(clause = "deleted = false")` or use Specifications to filter.
