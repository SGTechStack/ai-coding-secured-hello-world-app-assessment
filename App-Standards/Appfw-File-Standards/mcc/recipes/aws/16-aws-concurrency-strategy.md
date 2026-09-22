# 16. Concurrency Strategy (AWS)

**Goal**: Prevent double-processing and race conditions across multi-node AWS deployments.

> **Standalone note**: Standalone runs on a single node with no external scan submission phase, so no distributed locking is needed.

**PESSIMISTIC_WRITE (AWS Default Scheduled Mode)**:

```java
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT fm FROM FileMetadata fm WHERE fm.status = :status ORDER BY fm.uploadedOn ASC")
    List<FileMetadata> findByStatusWithLock(@Param("status") FileStatus status, Pageable pageable);
}
```

**Concurrency Comparison**:

| Aspect | Standalone | AWS |
|:---|:---|:---|
| Coordinator | Single process | Dedicated batch job server, or ShedLock-backed shared scheduler |
| Lock Mechanism | None (single-node) | `ShedLock` for scheduler ownership plus `PESSIMISTIC_WRITE` when multiple nodes may contend for the same work |
| Lock Timeout | N/A | Immediate `LockTimeoutException` when DB locking is enabled |
| Parallelization | Optional local parallelism only; no external scan submission phase | Virtual Threads per file, capped by the remaining SFS budget |
| Ordering | Sequential per request | Per-file FIFO within transaction |
| Node Count | 1 | 2 or more application nodes, with an optional dedicated batch job server |

**Requirements**:

* The system must not use `SKIP LOCKED` or `READPAST`; a locked record must cause an immediate exception.
* `spring.threads.virtual.enabled=true` is the preferred AWS parallelization model.
* AWS Phase 1 parallelism is bounded by the remaining in-flight SFS budget after subtracting files already in `PENDING_SCAN_RESPONSE`.
