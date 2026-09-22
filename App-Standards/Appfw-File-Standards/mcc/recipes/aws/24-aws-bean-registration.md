# 24. AWS Bean Registration

**Goal**: Wire AWS SFS adapters, authenticated outbound client access, scheduler ownership, and clean-store backend selection.

```java
@Configuration
@Profile("aws")
@EnableConfigurationProperties({AwsScannerProperties.class, AwsFileProperties.class})
public class AwsConfig {

    @Bean
    public BlobStorage blobStorage(AwsFileProperties properties,
                                   DirtyFileBlobRepository dirtyRepo,
                                   FileBlobRepository cleanRepo,
                                   S3Client s3Client,
                                   EntityManager entityManager) {
        if (properties.cleanStorageBackend() == CleanStorageBackend.S3) {
            return new AwsS3CleanStoreBlobStorage(dirtyRepo, s3Client, properties);
        }
        return new DatabaseBlobStorage(dirtyRepo, cleanRepo, entityManager);
    }

    @Bean
    @ConditionalOnProperty(name = "file.scanner.enabled", havingValue = "true", matchIfMissing = true)
    public ScanWorkflow awsScanWorkflow(SfsScannerClient sfsScannerClient,
                                        BlobStorage blobStorage,
                                        StateMachineService stateMachine) {
        return new AwsScanWorkflow(sfsScannerClient, blobStorage, stateMachine);
    }

    @Bean
    @ConditionalOnProperty(name = "file.scanner.enabled", havingValue = "false")
    public ScanWorkflow awsScannerBypassWorkflow(BlobStorage blobStorage,
                                                 StateMachineService stateMachine) {
        return new AwsScannerBypassWorkflow(blobStorage, stateMachine);
    }
}
```

**AWS wiring notes**:

* Inject the MCC-authenticated outbound client into the scanner adapter via `MccAuthenticatedClientProvider`.
* Scheduled lifecycle work must use `MccAuthenticatedClientProvider.backgroundClient()` so scanner jobs do not depend on servlet request state.
* Keep MCC authentication and JWKS concerns in the shared MCC module.
* Use ShedLock only when scheduled lifecycle ownership is shared across AWS nodes.
