# 12. Record-Level Ingestion & Processing (Post-Retrieval Flow)

**Goal**: Process tabular data after successful scan using iterator patterns with batch persistence.


**Java Implementation (Profile-Agnostic IngestionService)**:

```java
@Service
public class IngestionService {

    private final BlobStorage blobStorage;
    private final IngestionRecordRepository ingestionRepo;
    private final FileMetadataRepository metadataRepo;

    @Transactional(timeout = 1800) // 30 min — transaction-timeout-ingestion-ms
    public void processFile(UUID fileId, String mimeType) {
        FileMetadata meta = metadataRepo.findById(fileId)
            .orElseThrow(() -> new FileNotFoundException(fileId));

        meta.setIngestionStatus(IngestionStatus.IN_PROGRESS);
        metadataRepo.save(meta);

        try (InputStream content = blobStorage.openCleanStream(fileId);
             RowIterator rows = parserFactory.create(mimeType, content)) {
            long rowNum = 0;
            List<IngestionRecord> batch = new ArrayList<>(500);

            while (rows.hasNext()) {
                rowNum++;
                Row row = rows.next();
                try {
                    processRow(row);
                    batch.add(IngestionRecord.success(fileId, rowNum));
                } catch (RowProcessingException e) {
                    batch.add(IngestionRecord.failed(fileId, rowNum, e.getMessage()));
                    log.error("[ROW_PARSE_ERROR] Row {} parsing failed in fileId={}. Error: {}",
                        rowNum, fileId, e.getMessage());
                }

                if (batch.size() >= 500) { // ingestion-batch-flush-size
                    ingestionRepo.saveAll(batch);
                    meta.setProcessedRows(rowNum);
                    metadataRepo.save(meta);
                    batch.clear();
                }
            }

            // Flush remaining
            if (!batch.isEmpty()) ingestionRepo.saveAll(batch);

            meta.setTotalRows(rowNum);
            meta.setProcessedRows(rowNum);
            meta.setIngestionStatus(determineOutcome(fileId));
            metadataRepo.save(meta);

            // Clean artifact deletion on the ingest-and-delete path
            if (meta.isIngestAndDeletePath()) {
                blobStorage.deleteClean(fileId);
                log.info("[CLEAN_STORE_CLEANUP] Deleted clean artifact for fileId={} (Reason: ingest-and-delete path)", fileId);
            }

        } catch (Exception e) {
            meta.setIngestionStatus(IngestionStatus.FAILED);
            meta.setIngestionErrorMessage(e.getMessage());
            meta.setIngestionAttempts(meta.getIngestionAttempts() + 1);
            metadataRepo.save(meta);
            throw e; // re-throw for adapter retry handling
        }
    }
}
```

**Trigger Condition**:
```
FileScanEvent { fileId, status = DOWNLOADED } received
  AND FileMetadata.rowIngestionRequired = true
  AND FileMetadata.fileType IN rowIngestionMimeTypes
```

**Profile-Specific Implementations**:

| Profile | Implementation | Trigger | Coordination |
|:---|:---|:---|:---|
| **standalone** | `DirectRecordProcessor` | `@EventListener` on FileScanEvent(DOWNLOADED) | In-process event-driven |
| **aws** | `DirectRecordProcessor` | `@EventListener` on FileScanEvent(DOWNLOADED) | In-process event-driven |

> **Requirement**: Record-level ingestion must start only after `DOWNLOADED`, must read the file from the Clean Store through a streaming API rather than fully materializing the file in memory, and must delete the clean file artifact afterward when the selected business path is ingest-and-delete.

