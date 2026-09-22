# File Management Standards — AWS Profile

Two post-scan integration modes (mutually exclusive per upload component):

* **Retain-for-download** — Clean artifact stays in the Clean Store; downloadable by the owning principal.
* **Ingest-and-delete** — After reaching `DOWNLOADED`, the file is read record-by-record, processed, and the artifact deleted. Not downloadable after ingestion.

## Standards

* [Core Lifecycle](standards/file_management_standards_aws_core.md) — upload, validation, SFS lifecycle, FSM, events, security, errors, persistence, concurrency, runbook, definitions
* [Retain-for-Download](standards/file_management_standards_aws_retain.md) — retrieval API, bulk download, owner-scope enforcement
* [Ingest-and-Delete](standards/file_management_standards_aws_ingest.md) — ingestion trigger, record processing, idempotency, completion guard, artifact deletion

## Questions

### Backend
* [AWS](questions/backend/file_management_questions_aws_backend.md) — always
* [Retain](questions/backend/file_management_questions_aws_backend_retain.md) — retain-for-download mode only
* [Ingest](questions/backend/file_management_questions_aws_backend_ingest.md) — ingest-and-delete mode only

### Frontend
* [AWS](questions/frontend/file_management_questions_aws_frontend.md) — always
* [Retain](questions/frontend/file_management_questions_aws_frontend_retain.md) — retain-for-download mode only
* [Ingest](questions/frontend/file_management_questions_aws_frontend_ingest.md) — ingest-and-delete mode only

## Recipes

* [Shared](recipes/shared/) — UUID generation, magic bytes, state transitions, error handling, logging
* [AWS](recipes/aws/) — SFS processing, scheduled phases, zombie cleanup, concurrency, ShedLock, LocalStack dev bucket provisioning (29)
