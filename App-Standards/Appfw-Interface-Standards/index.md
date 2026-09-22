> **All directories must be considered.** For each business feature, do not skip any listed directory — each one contains standards or context required for a correct implementation.

> **Build order matters.** For each business feature, all directories listed must be visited in order — each layer depends on the one above it, so build them top-down.

### Inbound Interface and Batch
File reception, format validation, SHA-256 hash integrity, RSA digital signature validation, idempotency, duplicate detection, retry policies, error recovery, sender authorization, transaction state machine, entity modeling, cron orchestration, and structured logging correlation.
**Build order (read in sequence):**
1. [Interface and Batch Application Standard](Interface_And_Batch_Application_Standard.md)
2. [Interface and Batch Application Standard Questions](Interface_And_Batch_Application_Standard_Questions.md)
3. [Shared Recipes](Recipes/Shared/index.md)
4. [Inbound Recipes](Recipes/Inbound/index.md)

### Outbound Interface and Batch
Outbound batch file generation, multi-transport transmission (SFTP, HTTP, local FS), ItemReader paging, JobFactory wiring, acknowledgment handling, cron orchestration, concurrency control, transaction state machine, entity modeling, and structured logging correlation.
**Build order (read in sequence):**
1. [Interface and Batch Application Standard](Interface_And_Batch_Application_Standard.md)
2. [Interface and Batch Application Standard Questions](Interface_And_Batch_Application_Standard_Questions.md)
3. [Shared Recipes](Recipes/Shared/index.md)
4. [Outbound Recipes](Recipes/Outbound/index.md)
