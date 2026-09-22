# 24. Standalone Bean Registration

**Goal**: Wire standalone local storage, local promotion, quota enforcement, and in-process events.

```java
@Configuration
@Profile("standalone")
@EnableConfigurationProperties(StandaloneFileProperties.class)
public class StandaloneConfig {

    @Bean
    public BlobStorage blobStorage(StandaloneFileProperties properties,
                                   DirtyFileBlobRepository dirtyRepo,
                                   FileBlobRepository cleanRepo,
                                   EntityManager entityManager) {
        if (properties.storageBackend() == StorageBackend.DATABASE) {
            return new DatabaseBlobStorage(dirtyRepo, cleanRepo, entityManager);
        }
        return new LocalFilesystemBlobStorage(properties.dirtyDir(), properties.cleanDir());
    }

    @Bean
    public ScanWorkflow scanWorkflow(BlobStorage blobStorage,
                                     StateMachineService stateMachine) {
        return new StandaloneScanWorkflow(blobStorage, stateMachine);
    }

    @Bean
    public StorageQuotaChecker storageQuotaChecker(FileMetadataRepository repository,
                                                   StandaloneFileProperties properties) {
        return new LocalStorageQuotaChecker(repository, properties.storageQuotaBytes());
    }
}
```

Standalone does not register SFS clients, scanner schedulers, MCC outbound clients, or ShedLock.
