# 01. Generate UUIDs and Hash on Upload

**Goal**: Ensure every file has a unique identifier, integrity check, and validated metadata before storage.


**Java Implementation (Spring Boot 4 / JDK 21)**:

```java
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.util.DigestUtils;

@Service
@Transactional
public class FileUploadService {

    private final FileMetadataRepository repo;
    private final BlobStorage blobStorage;
    private final AcceptedFileValidator validator;
    private final StorageQuotaChecker quotaChecker;
    private final FileScanEventPublisher eventPublisher;

    public FileUploadResponse upload(MultipartFile file, String tags, String uploadedBy) {
        // 1. Size check before materializing bytes
        validateSize(file.getSize());
        quotaChecker.assertCapacity(file.getSize()); // Standalone only

        // 2. Materialize once — safe because size is bounded by the enforced upload limit
        byte[] bytes = file.getBytes();

        // 3. Validate (display name, MIME, magic bytes, encrypted PDF) — all from the same byte[]
        validator.validate(bytes, file.getContentType(), file.getOriginalFilename());

        // 4. Hash and store from the same byte[]
        String sha256Hex = DigestUtils.sha256Hex(bytes);

        // 5. Create FileMetadata
        FileMetadata metadata = new FileMetadata();
        metadata.setId(UUID.randomUUID());   // @GeneratedValue(strategy = UUID) in entity
        metadata.setHash(sha256Hex);
        metadata.setStatus(FileStatus.PENDING_SCAN);
        metadata.setFileSize(file.getSize());
        metadata.setFileType(file.getContentType());       // MIME type
        metadata.setFileExtension(extractExtension(file));  // e.g. ".pdf"
        metadata.setDisplayName(file.getOriginalFilename());
        metadata.setTags(tags);
        metadata.setUploadedBy(uploadedBy);
        metadata.setUploadedOn(Instant.now());
        metadata.setJobAttempts(0);

        // 6. Persist (Transactional — metadata + dirty blob atomic)
        repo.save(metadata);
        blobStorage.storeDirty(metadata.getId(), bytes);

        // 7. Publish Event
        eventPublisher.publish(new FileScanEvent(metadata.getId(), FileStatus.PENDING_SCAN, Instant.now()));

        return new FileUploadResponse(metadata.getId(), FileStatus.PENDING_SCAN);
    }
}
```

**HTTP Response**: `200 OK` → `{ id: UUID, status: "PENDING_SCAN" }`.

