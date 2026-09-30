# Runtime Profiles

This project keeps profile-specific configuration small so environment deltas stay easy to review.

## Profile Matrix

| Profile | Includes | Config source | Redis sessions | Purpose |
|----|----|----|----|----|
| `local` | `local` | Local property files | Disabled | Run the service on a developer machine without Redis or AWS dependencies. |
| `test` | `test` | Test property files | Disabled | Run automated tests with deterministic local infrastructure. |
| `dev` | `dev`, `aws-secrets` | AWS Secrets Manager | Enabled | AWS development environment. |
| `qa` | `qa`, `aws-secrets` | AWS Secrets Manager | Enabled | AWS QA environment. |
| `prod` | `prod`, `aws-secrets` | AWS Secrets Manager | Enabled | AWS production environment. |

## Configuration Layers

`application.properties`  
Common defaults and profile group definitions.

`application-local.properties`  
Local-only overrides. This profile must not require Redis or AWS credentials.

`application-test.properties`  
Test-only overrides. Tests should not depend on Redis or AWS services.

`application-aws-secrets.properties`  
Shared AWS Secrets Manager import used by production-like profiles.

`application-dev.properties`, `application-qa.properties`, `application-prod.properties`  
Optional environment-specific overrides. Keep these files empty or minimal unless an environment has a real delta.

## Comparing Environment Deltas

Treat `application-aws-secrets.properties` as the shared cloud layer for `dev`, `qa`, and `prod`.

Only put a property in `application-dev.properties`, `application-qa.properties`, or `application-prod.properties` when that environment intentionally differs from the shared cloud behavior. If these files are empty, there is no code-level delta between the environments.

Environment-specific values such as Redis hosts, credentials, and service endpoints should usually live in AWS Secrets Manager under each environment’s secret name rather than being duplicated in the repository.

## Runtime Inputs

Production-like environments should set:

```shell
SPRING_PROFILES_ACTIVE=dev
AWS_REGION=ap-southeast-1
DEMO_CONFIG_SECRET_NAME=/demo-backend/dev
```

Use the matching profile and secret name for `qa` and `prod`.
