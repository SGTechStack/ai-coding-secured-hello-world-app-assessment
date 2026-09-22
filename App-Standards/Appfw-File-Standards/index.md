> **All directories must be considered.** For each business feature, do not skip any listed directory — each one contains standards or context required for a correct implementation.

> **Build order matters.** For each business feature, all directories listed must be visited in order — each layer depends on the one above it, so build them top-down.

### File Upload AWS (MCC)
File upload on AWS, S3 clean store, SFS scanner, virus scanning, scheduled scan phases, ShedLock, zombie file cleanup, batch job, concurrency strategy
**Build order (read in sequence):**
1. [mcc](mcc/)

### File Upload Standalone
File upload standalone, local file storage, storage quota enforcement, local promotion, local virus scanning bypass
**Build order (read in sequence):**
1. [standalone](standalone/)
