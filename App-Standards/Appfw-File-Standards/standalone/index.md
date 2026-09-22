# File Management Standards — Standalone Profile

Two post-promotion integration modes (mutually exclusive per upload component):

* **Retain-for-download** — Clean artifact stays in the Clean Store; downloadable by the owning principal.
* **Ingest-and-delete** — After reaching `DOWNLOADED`, the file is read record-by-record, processed, and the artifact deleted. Not downloadable after ingestion.

## Standards

* [Core Lifecycle](standards/file_management_standards_standalone_core.md) — upload, validation, local promotion, FSM, events, security, errors, persistence, storage quota, runbook, definitions
* [Retain-for-Download](standards/file_management_standards_standalone_retain.md) — retrieval API, bulk download, owner-scope enforcement
* [Ingest-and-Delete](standards/file_management_standards_standalone_ingest.md) — ingestion trigger, record processing, clean artifact deletion

## Questions

### Backend
* [Standalone](questions/backend/file_management_questions_standalone_backend.md) — always

### Frontend
* [Standalone](questions/frontend/file_management_questions_standalone_frontend.md) — always

## Recipes

* [Shared](recipes/shared/) — UUID generation, magic bytes, state transitions, error handling, logging
* [Standalone](recipes/standalone/) — local promotion transitions, storage quota enforcement, standalone verification tests
