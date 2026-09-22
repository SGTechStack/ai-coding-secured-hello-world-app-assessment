# 08. Transactional State Transitions (StateMachineService)

**Goal**: Keep lifecycle state changes, storage cleanup, and event publication consistent across profiles.

**Shared rules**:

* Remote I/O and expensive parsing stay outside the transition transaction.
* Metadata updates, dirty/clean storage actions, and `FileScanEvent` publication happen in one domain-service transaction.
* Dirty cleanup is idempotent; deleting an already-absent dirty artifact is not an error.
* A file is visible for download only after the clean artifact is written and metadata reaches `DOWNLOADED`.
* Every lifecycle transition that occurs in the selected profile publishes `FileScanEvent(fileId, status, eventTimestamp)`.

**Shared service shape**:

```java
@Service
@Transactional
public class StateMachineService {

    private final FileMetadataRepository metadataRepo;
    private final BlobStorage blobStorage;
    private final FileScanEventPublisher eventPublisher;

    public void transitionToDownloaded(UUID fileId, InputStream cleanContent,
                                       String contentType) {
        FileMetadata file = metadataRepo.findById(fileId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        blobStorage.storeClean(fileId, cleanContent, contentType);
        blobStorage.deleteDirty(fileId);

        file.setStatus(FileStatus.DOWNLOADED);
        metadataRepo.save(file);

        eventPublisher.publish(new FileScanEvent(fileId, FileStatus.DOWNLOADED, Instant.now()));
    }

    public void transitionToTerminalFailure(UUID fileId, FileStatus targetStatus,
                                            String diagnosticMessage) {
        if (!targetStatus.isTerminal()) {
            throw new IllegalArgumentException("Target must be terminal");
        }

        FileMetadata file = metadataRepo.findById(fileId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        file.setStatus(targetStatus);
        file.setDiagnosticMessage(diagnosticMessage);
        metadataRepo.save(file);

        blobStorage.deleteDirty(fileId);
        eventPublisher.publish(new FileScanEvent(fileId, targetStatus, Instant.now()));
    }
}
```

**Profile-specific transition recipes**:

* Standalone local promotion belongs in [08. Standalone Local Promotion Transitions](../standalone/08-standalone-local-promotion-transitions.md).
* AWS scanner lifecycle transitions belong in [08. AWS Scanner State Transitions](../aws/08-aws-scanner-state-transitions.md).
