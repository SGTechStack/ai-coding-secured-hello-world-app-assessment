# 24. Profile-Specific Bean Registration & Configuration

**Goal**: Activate the correct adapters per deployment profile while keeping profile-specific implementation details in profile recipes.

**Shared pattern**:

```java
@Configuration
@EnableConfigurationProperties({
    FileValidationProperties.class,
    FileProcessingProperties.class
})
public class FileManagementSharedConfig {

    @Bean
    public FileScanEventPublisher fileScanEventPublisher(
            ApplicationEventPublisher applicationEventPublisher) {
        return new SpringFileScanEventPublisher(applicationEventPublisher);
    }
}
```

**Rules**:

* Controllers and listeners depend on ports and domain services, not concrete storage or scanner adapters.
* Profile-specific `@Configuration` classes own adapter selection.
* Standalone is local promotion, not scanner bypass.
* AWS scanner bypass is an AWS development option and belongs in the AWS wiring recipe.

**Profile-specific wiring recipes**:

* Standalone wiring belongs in [24. Standalone Bean Registration](../standalone/24-standalone-bean-registration.md).
* AWS wiring belongs in [24. AWS Bean Registration](../aws/24-aws-bean-registration.md).
