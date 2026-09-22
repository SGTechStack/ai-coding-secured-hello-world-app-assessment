# 29. LocalStack Dev Bucket Provisioning (AWS)

**Goal**: Provision the local S3 dirty/clean stores this profile depends on for development, without pushing bucket naming or lifecycle into the bootstrap standard.

**Ownership**: The [MCC Project Bootstrap Application Standard](../../../../Appfw-Project-Bootstrap/Mcc/Mcc_Project_Bootstrap_Application_Standard.md) owns the LocalStack **container** and the **init-script hook** (`scripts/localstack-init/` mounted into `/etc/localstack/init/ready.d/`). This standard owns **provisioning its own buckets** by dropping an init script into that hook. Bootstrap does not create or name the file buckets.

## When this applies

Only when the AWS profile's blob-store backend is set to S3 (`file.aws.storage-backend=S3`). If `DATABASE` is selected, no LocalStack buckets are needed.

## Step 1: Add the bucket-creation init script

Drop a numbered script into the bootstrap-provided hook so it runs after `01-create-resources.sh` (bootstrap's KMS block). Use the bucket names chosen in backend question **B10**.

```bash
#!/usr/bin/env bash
# File: scripts/localstack-init/02-create-file-buckets.sh
# Provisions the File Management dirty/clean S3 stores in LocalStack (dev only).
# Bucket names come from File Standards backend question B10.
set -euo pipefail

DIRTY_BUCKET="${FILE_DIRTY_BUCKET:-myapp-dirty-dev}"
CLEAN_BUCKET="${FILE_CLEAN_BUCKET:-myapp-clean-dev}"

echo "Creating file-management S3 buckets: ${DIRTY_BUCKET}, ${CLEAN_BUCKET}"
awslocal s3 mb "s3://${DIRTY_BUCKET}"
awslocal s3 mb "s3://${CLEAN_BUCKET}"

# Dirty/clean separation is enforced by using distinct buckets; keep them private.
# LocalStack does not enforce IAM or SSE, but keep the names/prefixes aligned with the
# deployed configuration so the S3BlobStorage adapter behaves identically across environments.
echo "File-management bucket provisioning complete."
```

## Step 2: Align the names with profile config

The same bucket names must appear in `application-dev-mcc.yml` under `file.aws.clean-bucket` (and the dirty store locator), so the `S3BlobStorage` adapter targets the buckets this script creates. Keep the script's defaults and the profile values in lockstep.

## Step 3: Deployed environments

In SIT/UAT/PROD the buckets are provisioned by the platform (Terraform/CloudFormation/console) with private IAM access and server-side encryption per the [Core standard §3.5 S3 Variant Guidance](../../standards/file_management_standards_aws_core.md). This recipe is local-development only — do not run `awslocal` against real AWS.

## Verification

```bash
# Inside the localstack container / with awslocal configured
awslocal s3 ls
# Expect the dirty and clean buckets from B10 to be listed.
```

> The bucket names are mock, dev-only values decided in B10. This recipe keeps their creation and naming with the standard that consumes them, so bootstrap stays agnostic to file-management specifics.
