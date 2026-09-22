# 27. Status, Download, and Retrieval API Contract

**Goal**: Define the shared REST controller shape for file status, single download, and bulk download, including profile-specific download guard logic and owner-scope enforcement.

## Step 1: Define the shared controller contract

```java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileRetrievalController {

    private final FileMetadataRepository metadataRepo;
    private final BlobStorage blobStorage;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String tags,
            @AuthenticationPrincipal UserDetails principal) {
        FileUploadResponse response = fileUploadService.upload(file, tags, principal.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{fileId}/status")
    @Transactional(readOnly = true)
    public ResponseEntity<FileStatusResponse> status(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal UserDetails principal) {
        FileMetadata meta = findOwnedFileOrNotFound(fileId, principal.getUsername());
        return ResponseEntity.ok(new FileStatusResponse(
                meta.getId(),
                meta.getStatus(),
                meta.getIngestionStatus(),
                meta.getProcessedRows(),
                meta.getTotalRows()
        ));
    }

    @GetMapping("/{fileId}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal UserDetails principal) {
        FileMetadata meta = findOwnedFileOrNotFound(fileId, principal.getUsername());

        if (meta.getStatus() != FileStatus.DOWNLOADED) {
            throw new FileNotReadyException(fileId, meta.getStatus());
        }

        InputStream content = blobStorage.openCleanStream(fileId);
        InputStreamResource resource = new InputStreamResource(content);

        String safeFilename = sanitizeHeaderValue(meta.getDisplayName());
        String safeContentType = sanitizeHeaderValue(meta.getFileType());

        var responseBuilder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(safeContentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeFilename + "\"")
                .contentLength(meta.getFileSize());

        // AWS profile: signal sanitized content via response header (Retain §2.3).
        // originalHash is non-null only when the scanner verdict was Sanitized.
        if (meta.getOriginalHash() != null) {
            responseBuilder.header("X-File-Sanitized", "true");
        }

        return responseBuilder.body(resource);
    }

    private FileMetadata findOwnedFileOrNotFound(UUID fileId, String principal) {
        return metadataRepo.findByIdAndUploadedBy(fileId, principal)
                .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    private String sanitizeHeaderValue(String value) {
        if (value == null) return "";
        return value.replaceAll("[\\r\\n]", "");
    }
}
```

## Step 2: Define the response DTOs

```java
public record FileUploadResponse(UUID id, FileStatus status) {}

public record FileStatusResponse(
        UUID id,
        FileStatus status,
        IngestionStatus ingestionStatus,
        long processedRows,
        Long totalRows
) {}
```

## Step 3: Understand profile-specific download guard differences

The controller shape is shared, but the timing of when `DOWNLOADED` is reached differs by profile:

| Profile | Path to `DOWNLOADED` | Download available after |
|:---|:---|:---|
| Standalone | `PENDING_SCAN` → immediate local promotion → `DOWNLOADED` | Upload completes |
| AWS | `PENDING_SCAN` → SFS submit → poll → retrieve → `DOWNLOADED` | SFS flow completes |

The download endpoint rejects requests before `DOWNLOADED` regardless of profile. On the ingest-and-delete path, the clean artifact is removed after ingestion, so the download endpoint must also handle the case where the clean blob no longer exists. Per the standard, both cases return `403 Forbidden` — the file is not in a retained downloadable state:

```java
@GetMapping("/{fileId}/download")
@Transactional(readOnly = true)
public ResponseEntity<Resource> download(
        @PathVariable UUID fileId,
        @AuthenticationPrincipal UserDetails principal) {
    FileMetadata meta = findOwnedFileOrNotFound(fileId, principal.getUsername());

    if (meta.getStatus() != FileStatus.DOWNLOADED) {
        throw new FileNotReadyException(fileId, meta.getStatus());
    }

    try {
        InputStream content = blobStorage.openCleanStream(fileId);
        // ... build response as above
    } catch (BlobNotFoundException ex) {
        // Clean artifact was deleted after ingestion — file is no longer in a retained downloadable state
        throw new FileNotReadyException(fileId, meta.getStatus());
    }
}
```

## Step 4: Add bulk download support

```java
@PostMapping("/bulk-download")
@Transactional(readOnly = true)
public ResponseEntity<StreamingResponseBody> bulkDownload(
        @RequestBody List<UUID> fileIds,
        @AuthenticationPrincipal UserDetails principal) {

    List<FileMetadata> files = fileIds.stream()
            .map(id -> findOwnedFileOrNotFound(id, principal.getUsername()))
            .toList();

    // Every file must be in DOWNLOADED state and on the retain-for-download path
    for (FileMetadata meta : files) {
        if (meta.getStatus() != FileStatus.DOWNLOADED) {
            throw new FileNotReadyException(meta.getId(), meta.getStatus());
        }
    }

    StreamingResponseBody body = outputStream -> {
        try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (FileMetadata meta : files) {
                zip.putNextEntry(new ZipEntry(meta.getDisplayName()));
                try (InputStream content = blobStorage.openCleanStream(meta.getId())) {
                    content.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    };

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"files.zip\"")
            .body(body);
}
```

**Bulk download rules**:
* Every requested file must belong to the current principal.
* Every file must already be in the retained `DOWNLOADED` state.
* Do not return a partial archive — reject the entire request if any file is ineligible.

## Step 5: Map error responses to RFC 9457

```java
@RestControllerAdvice
public class FileRetrievalExceptionHandler {

    @ExceptionHandler(FileNotFoundException.class)
    public ProblemDetail handleNotFound(FileNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(FileNotReadyException.class)
    public ProblemDetail handleNotReady(FileNotReadyException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "File is not available for download. Current status: " + ex.getStatus());
        pd.setTitle("File Not Available");
        return pd;
    }
}
```

## Step 6: Owner-scope enforcement

The owner-scope rule is the same across supported profiles:

* `findByIdAndUploadedBy` ensures only the uploading principal can access the file.
* If the file exists but belongs to a different principal, return 404 — do not disclose that the file exists.
* This applies to status, single download, bulk download, and removal operations.

```java
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    Optional<FileMetadata> findByIdAndUploadedBy(UUID id, String uploadedBy);

    List<FileMetadata> findByUploadedByAndStatus(String uploadedBy, FileStatus status);
}
```
