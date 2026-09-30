# Testing Patterns

## Conventions

- **Naming:** Behavioral specification in camelCase — `submittingDraftOrderChangesStatusToSubmitted`
- **Assertions:** AssertJ exclusively (`assertThat`) — no JUnit assertions
- **Profiles:** Always declare `@ActiveProfiles` on integration tests
- **Layer testing:** Unit tests for domain/service logic, integration tests for API endpoints
- **No mocking repositories in service tests** unless the test is specifically about service orchestration logic — prefer `@DataJpaTest` with H2

---

## Unit Test — Domain Entity

```java
class OrderTest {

    @Test
    void submittingDraftOrderChangesStatusToSubmitted() {
        var order = Order.builder().customer(testCustomer()).build();

        order.submit();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SUBMITTED);
    }

    @Test
    void submittingNonDraftOrderThrowsException() {
        var order = Order.builder().customer(testCustomer()).build();
        order.submit(); // now SUBMITTED

        assertThatThrownBy(order::submit)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("draft");
    }

    @Test
    void addingItemToDraftOrderSucceeds() {
        var order = Order.builder().customer(testCustomer()).build();

        order.addItem(testProduct(), 2);

        assertThat(order.getItems()).hasSize(1);
    }

    private Customer testCustomer() {
        return Customer.builder().name("Ada Lovelace").email("ada@example.com").build();
    }

    private Product testProduct() {
        return Product.builder().name("Widget").price(BigDecimal.valueOf(9.99)).build();
    }
}
```

---

## Unit Test — Service (with Mockito)

Use when testing orchestration logic or when the service coordinates multiple dependencies:

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void submitOrderNotifiesCustomer() {
        var order = Order.builder().customer(testCustomer()).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.submitOrder(1L);

        verify(notificationService).notifyOrderSubmitted(order);
    }

    @Test
    void submitOrderThrowsWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.submitOrder(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
```

---

## Integration Test — Controller (MockMvc)

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void cleanup() {
        orderRepository.deleteAll();
    }

    @Test
    void createOrderReturns201WithOrderResponse() throws Exception {
        var request = """
                { "customerId": 1 }
                """;

        mockMvc.perform(post("/api/orders")
                        .with(user("ada").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.createdByUserName").exists());
    }

    @Test
    void createOrderWithInvalidBodyReturns400() throws Exception {
        var request = """
                { "customerId": null }
                """;

        mockMvc.perform(post("/api/orders")
                        .with(user("ada").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutAdminRoleCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/orders/1")
                        .with(user("ada").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
```

---

## Integration Test — Repository (@DataJpaTest)

```java
@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryIT {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByStatusReturnsMatchingOrders() {
        var customer = entityManager.persist(Customer.builder().name("Ada").email("ada@test.com").build());
        var draft = entityManager.persist(Order.builder().customer(customer).build());
        var submitted = entityManager.persist(Order.builder().customer(customer).build());
        submitted.submit();
        entityManager.persist(submitted);
        entityManager.flush();

        var results = orderRepository.findAll(OrderSpecifications.hasStatus(OrderStatus.DRAFT));

        assertThat(results).containsExactly(draft);
    }

    @Test
    void projectionReturnsOnlyRequestedFields() {
        var customer = entityManager.persist(Customer.builder().name("Ada").email("ada@test.com").build());
        entityManager.persist(Order.builder().customer(customer).build());
        entityManager.flush();

        var summaries = orderRepository.findByCustomerId(customer.getId(), Pageable.unpaged());

        assertThat(summaries.getContent()).hasSize(1);
        assertThat(summaries.getContent().get(0).getStatus()).isEqualTo("DRAFT");
    }
}
```

---

## Security Boundary Tests

Always test both the happy path AND adversarial cases:

```java
@Nested
class SecurityBoundary {

    @Test
    void pathTraversalInUrlReturns400() throws Exception {
        mockMvc.perform(get("/api/orders/..%2f..%2fetc%2fpasswd")
                        .with(user("ada").roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingCsrfOnMutatingEndpointReturns403() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(user("ada").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().isNotFound());
    }
}
```

---

## Test Configuration

```properties
# application-test.properties
spring.jpa.hibernate.ddl-auto=create-drop
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.session.store-type=none
spring.liquibase.enabled=false
```

For integration tests that need Liquibase schema validation:
```properties
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml
```

---

## Test Utilities

Create shared test fixtures in a `test/support` package:

```java
public final class TestFixtures {

    private TestFixtures() {}

    public static Customer customer() {
        return Customer.builder().name("Ada Lovelace").email("ada@example.com").build();
    }

    public static Customer customer(String name) {
        return Customer.builder()
                .name(name)
                .email(name.toLowerCase().replace(" ", ".") + "@example.com")
                .build();
    }

    public static Order draftOrder() {
        return Order.builder().customer(customer()).build();
    }

    public static Order submittedOrder() {
        var order = draftOrder();
        order.submit();
        return order;
    }
}
```

---

## What NOT to Test

- Trivial getters/setters with no logic
- Spring framework behavior (e.g., "does `@Autowired` work?")
- Private methods — test through public API
- Empty `contextLoads()` tests — every other IT already proves this
