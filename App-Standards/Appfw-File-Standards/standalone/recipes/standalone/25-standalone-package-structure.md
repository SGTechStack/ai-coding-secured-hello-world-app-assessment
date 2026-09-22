# 25. Package Structure (Standalone)

See [25. Code Organization & Package Structure](../shared/25-code-organization-package-structure.md) for shared packages, DDL, and entity fields.

**Standalone-specific packages**:

| Package | Purpose | Key Classes |
|:---|:---|:---|
| `adapter.outbound.standalone` | Standalone profile implementations | `StandaloneScanWorkflow`, `LocalStorageQuotaChecker`, `DirectRecordProcessor`, `StandaloneFileScanEventPublisher`, `DatabaseBlobStorage`, `LocalFilesystemBlobStorage` |
| `config` (Standalone) | Standalone-specific Spring configuration | `StandaloneConfig`, `StandaloneFileProperties` |
