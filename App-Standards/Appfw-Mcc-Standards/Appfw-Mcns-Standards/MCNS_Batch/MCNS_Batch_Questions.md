# MCNS Batch Retry — Implementation Questions

Questions to resolve with the team before starting implementation. Resolve the core integration questions in the [MCNS Core Questions](../MCNS_Core/MCNS_Core_Questions.md) first — the batch retry pipeline requires the core integration to already be configured.

---

### Tag IDs and scheduling

1. How many distinct notification types require batch retry? Each type needs its own tag ID, cron expression, and item reader bean.
2. For each tag ID: what is the cron expression (retry frequency), and what is the expected volume of pending records per run?
   > Higher volumes may require tuning `chunkSize` and executor pool size. Lower frequencies reduce load on executor pool but increase re-queue lag and the window in which a failed notification goes undelivered.
3. Are all tag IDs known at design time, or will new ones be added at runtime?
   > Tag IDs are registered statically in `RepositoryMap`. Adding a new tag ID requires a code change and restart — there is no dynamic registration path.

---

### Domain record lifecycle

4. After a notification is sent successfully, should the domain record be **retained** (`isProcessed = true`) or **deleted outright**? *(See Batch Standard §4.2 design choice: Domain record fate after successful send)*
   > Retaining the record preserves a local audit trail and allows manual re-queue by resetting `isProcessed`. Deletion reduces storage overhead but removes the payload permanently from the domain table.

5. When `ATTEMPTS_EXCEEDED` is reached, should the domain record be **deleted** or **moved to a dead letter table**? *(See Batch Standard §4.2 design choice: Handling max attempts exceeded)*
   > Dead Letter Queue preserves the original payload for operator review and potential manual re-queue. Deletion keeps the domain table clean but makes post-incident investigation harder. If a quarantine table is chosen, who is responsible for reviewing and clearing it?

6. What is the appropriate `maxAttempts` value for each tag ID? The default is 5. Consider the MCNS service's typical recovery window — `maxAttempts` × cron interval determines how long the pipeline will keep retrying before giving up and raising an operational alert.

---

### Delivery semantics

7. Are duplicate notifications acceptable for this use case? *(See Batch Standard §4.2 design choice: Delivery semantics)*
   > The default pipeline provides **at-least-once delivery** — if a node crashes after MCNS accepts the notification but before `isProcessed = true` is written, the record will be re-sent on the next cron run. If duplicates are not acceptable, the MCNS recipient template must be idempotent on `referenceId`, or a pre-send deduplication check must be implemented.

---

### Concurrency and executor sizing

8. What `chunkSize` is appropriate for each tag ID? The default is 1000. A larger chunk size means more items in-flight before state is committed at the chunk boundary.
9. What `executor.maxPoolSize` is appropriate? Must be ≤ `chunkSize`. The default is 20. This caps how many MCNS calls run concurrently within a chunk.
    > Effective concurrency per chunk is `min(chunkSize, maxPoolSize)`. Size both together — a pool larger than `chunkSize` adds threads that can never all be used within a single chunk. *(See Batch Standard §4.4)*
10. What `executor.queueCapacity` is appropriate? The default is 100. Tasks are queued when all pool threads are busy. If the queue fills, submissions block — size it to handle the difference between `chunkSize` and `maxPoolSize`.

### Operational alerts and monitoring

11. Who receives operational alerts when `ATTEMPTS_EXCEEDED` is emitted? This event requires manual intervention — no further automatic retries will occur. Is there an existing alerting channel (e.g. Splunk) to route it to?
12. What is the process for manually re-queuing a record after `ATTEMPTS_EXCEEDED`? Is there an admin endpoint, a database script, or a quarantine table review process?
13. Who is the on-call owner for batch job failures (`BATCH_JOB_EXECUTION` exit status `FAILED`) in production?
14. Is there a monitoring alert for domain records with `isProcessed = false` that have aged beyond the expected SLA? This can indicate a stuck job, a missing tag ID in `RepositoryMap`, or a listener registration failure.
