# 25. Package Structure (AWS)

See [25. Code Organization & Package Structure](../shared/25-code-organization-package-structure.md) for shared packages, DDL, and entity fields.

**AWS-specific packages**:

| Package | Purpose | Key Classes |
|:---|:---|:---|
| `adapter.outbound.aws` | AWS profile implementations | `AwsScanWorkflow`, `S3BlobStorage`, optional `DatabaseBlobStorage` |
| `adapter.outbound.sfs` | SFS HTTP client & models | `SfsScannerClient`, `SfsControlClient`, `SfsClientConfig`, `SfsPutRequest/Response`, `SfsGetRequest/Response`, `UploadHandshake`, `PollOutcome`, `SfsUploadException`, `SfsPollingException`, `SfsDownloadException` |
| `config` (AWS) | AWS-specific Spring configuration | `AwsConfig`, `AwsScannerProperties`, `AwsFileProperties` |

**Note**: SFS interaction depends on the shared MCC authentication module. See [05. AWS SFS Processing](05-aws-sfs-processing-declarative-client-virtual-threads.md) for authentication and prerequisite details.

**Database Resources (AWS-only)**:

| Resource | Location | Purpose |
|:---|:---|:---|
| `005-create-shedlock.yaml` | `src/main/resources/db/changelog/changes/` | `SHEDLOCK` table for distributed scheduler ownership |

**Reference DDL**:

```sql
-- SHEDLOCK (aws only - when scheduler ownership is shared)
create table SHEDLOCK (
    name                varchar(64) primary key,
    lock_until          timestamp not null,
    locked_at           timestamp not null,
    locked_by           varchar(255) not null
);
```
