# 06. AWS Default Orchestration (Scheduled Phases)

**Goal**: In the AWS profile, run the async submit -> poll -> clean-file retrieval lifecycle with explicit phase ownership and SFS-aware throttling.

**Default AWS Flow**:

```text
Upload -> status = PENDING_SCAN
  -> Phase 0 cleanup runs first (terminates upload zombies, retry-exceeded files, and scanner response timeouts so the in-flight count used in Phase 1 reflects only genuinely pending SFS work)
  -> Phase 1 selects only the remaining SFS capacity
  -> request upload metadata -> PUT bytes to presigned URL
  -> status = PENDING_SCAN_RESPONSE
  -> Phase 2 polls by scanner UUID and file identity
  -> status = PENDING_DOWNLOAD or terminal failure
  -> Phase 3 downloads clean bytes from SFS
  -> verify downloaded SHA-256 against original hash
  -> if verdict is Unchanged AND hash matches: store in configured Clean Store
  -> if verdict is Unchanged AND hash matches: status = DOWNLOADED and emit FileScanEvent(DOWNLOADED)
  -> if verdict is Sanitized: store sanitized content in Clean Store, status = DOWNLOADED, emit FileScanEvent(DOWNLOADED) + audit record (original hash, sanitized hash)
  -> if verdict is Unchanged AND hash differs: status = DOWNLOADED_FILE_MISMATCH (genuine integrity failure)
  -> if DOWNLOADED: retain for download OR ingest records and delete clean artifact
```

**AWS Batch Lifecycle Sequence**:

```mermaid
sequenceDiagram
    participant Scheduler as ScanBatchScheduler
    participant SM as StateMachineService
    participant DB as FileMetadataRepository
    participant Blob as BlobStorage
    participant SFS as SFS Scanner API

    Scheduler->>DB: Phase 0a — find PENDING_SCAN past timeout
    DB-->>Scheduler: Timed-out files
    Scheduler->>SM: transitionToTerminalFailure(PENDING_SCAN_TIMEOUT)
    SM->>Blob: deleteDirty(fileId)

    Scheduler->>DB: Phase 0b — find PENDING_SCAN with jobAttempts >= limit
    DB-->>Scheduler: Retry-exceeded files
    Scheduler->>SM: transitionToTerminalFailure(PENDING_SCAN_RETRY_EXCEEDED)
    SM->>Blob: deleteDirty(fileId)

    Scheduler->>DB: Phase 0c — find PENDING_SCAN_RESPONSE past scan timeout
    DB-->>Scheduler: Scanner-response timed-out files
    Scheduler->>SM: transitionToTerminalFailure(PENDING_SCAN_RESPONSE_TIMEOUT)
    SM->>Blob: deleteDirty(fileId)

    Scheduler->>DB: Count PENDING_SCAN_RESPONSE (in-flight)
    DB-->>Scheduler: inFlightCount
    Note over Scheduler: capacity = max(0, sfsLimit - inFlightCount)

    Scheduler->>DB: Phase 1 — select PENDING_SCAN (up to capacity)
    DB-->>Scheduler: Batch of files

    loop Each file in batch
        Scheduler->>SFS: requestUpload(fileName, mimeType)
        SFS-->>Scheduler: UploadHandshake(uuid, presignedUrl)
        Scheduler->>SFS: PUT bytes to presignedUrl
        Scheduler->>SM: transitionToPendingScanResponse(fileId, uuid)
        SM->>Blob: deleteDirty(fileId)
    end

    Scheduler->>DB: Phase 2 — select PENDING_SCAN_RESPONSE
    loop Each file
        Scheduler->>SFS: poll(uuid, fileName, mimeType)
        alt Allowed
            Scheduler->>SM: transition to PENDING_DOWNLOAD
        else Blocked / Error
            Scheduler->>SM: transitionToTerminalFailure(BAD_RESULT)
            SM->>Blob: deleteDirty(fileId)
        end
    end

    Scheduler->>DB: Phase 3 — select PENDING_DOWNLOAD
    loop Each file
        Scheduler->>SFS: downloadClean(presignedUrl)
        SFS-->>Scheduler: Clean bytes
        Scheduler->>Scheduler: Verify SHA-256 hash
        alt Verdict = Unchanged AND hash matches original
            Scheduler->>SM: transitionToDownloaded(fileId, cleanBytes)
            SM->>Blob: storeClean
        else Verdict = Sanitized (hash mismatch expected)
            Scheduler->>SM: transitionToDownloaded(fileId, cleanBytes) + audit record
            SM->>Blob: storeClean
        else Verdict = Unchanged AND hash differs
            Scheduler->>SM: transitionToTerminalFailure(DOWNLOADED_FILE_MISMATCH)
        end
    end
```

> Rules for event publication, hash mismatch handling, SFS capacity throttling, metadata persistence, and lock coordination are defined in §4.4 and §4.5 of the [AWS Standard](../../standards/file_management_standards_aws_core.md).
