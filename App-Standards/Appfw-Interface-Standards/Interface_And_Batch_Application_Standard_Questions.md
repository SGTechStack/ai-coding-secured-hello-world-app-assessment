# App Standard: Interface and Batch Flow — Decision Questionnaire

Answer these questions with your team and product owner before implementation to determine the configuration and architectural parameters of your batch application.

---

## 1. Interface Context

### Q1: What type of interface flow does your application implement?

**Select all that apply:**

- [ ] **Inbound Interface** - Receives and processes batch files from external systems
- [ ] **Outbound Interface** - Generates and sends batch files to external systems
- [ ] **Bidirectional Interface** - Both inbound and outbound processing

> **Why this matters:** Determines which sections of the questionnaire apply to your implementation. Questions Q2–Q13 address inbound configurations, Q14–Q19 address outbound configurations, and Q20–Q27 apply to both.

---

## 2. Inbound Interface Configuration

### Q2: What file formats will your inbound interface receive?

**Select all that apply:**

- [ ] **CSV** - Delimited text files (RFC 4180)
- [ ] **XML** - Structured XML documents
- [ ] **ZIP** - Compressed archives containing payload files
- [ ] **Fixed-width** - Positional format files
- [ ] **Other**: _________________

---

### Q3: Does your inbound interface require integrity verification?

**Select all that apply:**

- [ ] **Hash validation** - Companion hash file (e.g., SHA-256 checksum)
- [ ] **Digital signature** - Companion signature file for non-repudiation
- [ ] **File size validation** - Expected file size check
- [ ] **None** - No integrity verification required

---

### Q4: How should your application handle duplicate batch files?

- [ ] **Idempotent processing (Recommended)** - Return cached ACK without re-processing
- [ ] **Reject duplicates** - Return error for any duplicate submission
- [ ] **Allow reprocessing** - Process every submission regardless of history

**Deduplication window:**
- [ ] **90 days** (default)
- [ ] **Custom duration**: _________________

---

### Q5: What error handling strategy will your application use for record-level failures?

**Select one:**

- [ ] **Halt on first error** - Stop processing immediately when any record fails
- [ ] **Continue with skip limit** - Process remaining records up to configured skip threshold
- [ ] **Process all records** - Continue processing entire batch regardless of individual failures

**Skip limit (if continuing past errors):**
- [ ] **10 records** (recommended for small batches < 1,000 records)
- [ ] **100 records** (recommended for medium batches 1,000–10,000 records)
- [ ] **Custom limit**: _________________

---

### Q6: What transaction rollback behavior should be applied when the skip limit is exceeded mid-chunk?

**Select one:**

- [ ] **Rollback chunk (Option A - Recommended)** - Discard all changes in the current chunk and fail the batch immediately. Previous chunks remain committed.
- [ ] **Complete chunk (Option B)** - Process all remaining records in the current chunk, commit the chunk, and then fail the batch.

---

### Q7: Does your application support checkpoint and restart for large batches?

- [ ] **Yes** - Batch jobs must be restartable from the last committed chunk (Required for batches > 1,000 records)
- [ ] **No** - Batch jobs process the entire file in a single transaction

**Chunk size:**
- [ ] **100 records** (recommended for small records < 1KB each)
- [ ] **500 records** (recommended for medium records 1–10KB each)
- [ ] **1,000 records** (recommended for large records > 10KB each)
- [ ] **Custom size**: _________________

---

### Q8: Will your application run in a multi-instance deployment?

- [ ] **Yes** - Multiple application instances running concurrently (Enforces distributed locking)
- [ ] **No** - Single instance only

---

### Q9: How should your application handle unrecognized files?

- [ ] **Quarantine and alert (Recommended)** - Move to quarantine, log warning, trigger alert for operator review
- [ ] **Reject and delete** - Delete file immediately without processing
- [ ] **Ignore silently** - No action taken

---

### Q10: How should your application handle stale files?

**Stale file:** A file received and persisted but not processed within the configured cutoff window.

- [ ] **Mark stale and alert (Recommended)** - Transition to `STALE` state and trigger alert
- [ ] **Auto-process regardless** - Process file regardless of age
- [ ] **Auto-delete** - Delete unprocessed files after cutoff

**Stale file cutoff:**
- [ ] **24 hours** (default)
- [ ] **Custom duration**: _________________

---

### Q11: How should your application handle zero-byte files?

- [ ] **Reject and notify sender (Recommended)** - Mark status as `ZERO_BYTE_FILE`, generate rejection ACK, and alert
- [ ] **Accept as empty batch** - Mark as successful with 0 records processed
- [ ] **Ignore silently** - No action taken

---

### Q12: What acknowledgment (ACK) format will your application generate?

- [ ] **Same format as inbound** - If inbound is CSV, ACK is CSV-like
- [ ] **Fixed format (e.g., XML)** - Regardless of inbound format
- [ ] **Custom format**: _________________

---

### Q13: Where will your application send ACK files?

- [ ] **Outbound file system directory** - ACK stored for sender pickup
- [ ] **SFTP server** - ACK pushed to sender's SFTP location
- [ ] **HTTP API** - ACK sent via REST/SOAP endpoint
- [ ] **Other**: _________________

---

## 3. Outbound Interface Configuration

### Q14: What file formats will your outbound interface generate?

**Select all that apply:**

- [ ] **CSV** - Delimited text files (RFC 4180)
- [ ] **XML** - Structured XML documents
- [ ] **ZIP** - Compressed archives containing payload files
- [ ] **Fixed-width** - Positional format files
- [ ] **Other**: _________________

---

### Q15: Does your outbound interface require integrity controls?

**Select all that apply:**

- [ ] **Generate hash file** - Companion hash file (SHA-256 checksum)
- [ ] **Generate digital signature** - Companion signature file for non-repudiation
- [ ] **None** - No integrity controls required

---

### Q16: What retry strategy will your application use for outbound transmission failures?

- [ ] **Immediate retry (Pattern A)** - Apply backoff immediately (Recommended for request-based transport like HTTP/SFTP)
- [ ] **Scheduled retry (Pattern B)** - Archive file and let a scheduler retry later (Recommended for batch window systems)
- [ ] **No retry** - Fail immediately and alert operator

**Retry limits:**
- Maximum attempts: [ ] **3** [ ] **5** [ ] **Custom**: _______
- Backoff strategy: [ ] **Exponential (1s, 2s, 4s, 8s, 16s)** [ ] **Linear (30s, 60s, 120s)**

---

### Q17: How long will your application wait for acknowledgment from recipient?

- [ ] **24 hours**
- [ ] **48 hours (Recommended)**
- [ ] **Custom duration**: _________________

---

### Q18: How should your application handle ACK rejection from recipient?

**ACK rejection:** Recipient returns ACK with status="rejected" or "partial".

- [ ] **Log and alert (Recommended)** - Mark as FAILED and route for manual operator review (no auto-retry)
- [ ] **Auto-retry** - Re-transmit batch after remediation
- [ ] **Discard** - Mark as failed and take no further action

---

### Q19: Will operators need to manually discard outbound files?

- [ ] **Yes** - Provide operator discard capability (State transitions to `DISCARDED`)
- [ ] **No** - All files must be transmitted or reach retry limit

---

## 4. Operational & Security Configuration (Applies to Both)

### Q20: What data retention policy will your application enforce?

**Inbound files:**
- [ ] **90 days** (default)
- [ ] **Custom duration**: _________________

**Outbound files:**
- [ ] **Until ACK received + 30 days** (default)
- [ ] **Custom duration**: _________________

**Audit records:**
- [ ] **7 years** (for financial systems)
- [ ] **90 days** (minimum for non-financial)
- [ ] **Custom duration**: _________________

---

### Q21: Where will your archived batch files be stored, and how will they be secured at rest?

**Select all that apply:**

- [ ] **Local storage only** - Files stored in a local directory path on the container or local volume
- [ ] **Cloud storage only (e.g., AWS S3)** - Files uploaded directly to a cloud bucket
- [ ] **Dual storage (Local + S3 in parallel)** - Files archived to both local directory and S3 bucket simultaneously

---

### Q22: How will domain data records associated with purged batches be cleaned up?

- [ ] **Framework-driven content cleanup callback** - Implement and register `ContentCleanupCallback` to delete records from business/domain tables when the framework cleans up metadata.
- [ ] **Independent integrator cleanup** - Manage purging of domain content separately (e.g., via partitioned tables, database ETLs, or independent batch jobs).

**If framework-driven callback selected, should startup validation fail-fast when a callback is not registered?**
- [ ] **Fail-fast on startup (Recommended)** - Refuse to start if content cleanup is enabled but no callback is registered (`failFastIfNoCallback=true`)
- [ ] **Allow startup** - Allow starting up without registered callbacks (`failFastIfNoCallback=false`)

---

### Q23: What security classification level applies to your batch files?

**Select IM8 double-dimension classifications:**

**Security Classification:**
- [ ] **UNCLASSIFIED**
- [ ] **CONFIDENTIAL**
- [ ] **RESTRICTED**

**Sensitivity Classification:**
- [ ] **NON_SENSITIVE**
- [ ] **SENSITIVE_NORMAL**
- [ ] **SENSITIVE_HIGH**

---

### Q23b: How will credentials and encryption keys be managed and rotated?

**Select external secrets manager:**

- [ ] **AWS Secrets Manager**
- [ ] **HashiCorp Vault**
- [ ] **Azure Key Vault**
- [ ] **Other external store**: _________________

---

### Q24: Does your application require sender authorization for inbound files?

- [ ] **Yes** - Only authorized senders may submit files
- [ ] **No** - All files accepted regardless of sender

---

### Q25: How will your application handle execution concurrency and sequencing for multiple files?

- [ ] **Sequential processing (FIFO / Strict sequence)** - Files must be processed in chronological order or by sequence numbers, one at a time.
- [ ] **Parallel processing (Concurrent execution)** - Multiple files can be processed concurrently by different threads or instances.

---

### Q26: What throttling and backpressure limits will your application enforce to protect system resources?

- [ ] **Bounded thread pool limits** - Limit maximum concurrent batch job executions.
- [ ] **Staggered polling execution** - Throttle poller to check and pull a maximum number of files per run.
- [ ] **None** - Process all files as they arrive without limits.

---

### Q27: What disaster recovery and manual re-run options are required for batch jobs?

- [ ] **Forced Reprocessing (`force_reprocess`)** - Bypass duplicate checking for specific files.
- [ ] **Reset Checkpoint (`reset_checkpoint`)** - Reset chunk-level progress to re-process a file from scratch.
- [ ] **None** - Standard restart from the last committed chunk is sufficient.
