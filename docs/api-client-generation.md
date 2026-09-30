# API client generation

Two OpenAPI groups, one per channel (see `OpenApiGroupConfiguration`). Regenerate in this order.

| Group      | Paths           | Spec                     | Client project (`orval.config.ts`) |
| ---------- | --------------- | ------------------------ | ---------------------------------- |
| `frontend` | `/api/**`, `/login` | `docs/openapi.json`       | `demo` → `src/api/generated/`       |
| `admin`    | `/admin/api/**` | `docs/openapi-admin.json` | `admin` → `src/api/generated-admin/` |

Admin operations stay out of the public spec and client: `/admin/api/**` is outside `/api/**`, and the admin chain admits only `Role.ADMIN`.

## 1. Regenerate specs

The `/v3/api-docs/**` endpoints exist only under `unsafe-openapi`.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local,unsafe-openapi
```

Once the log shows `Started Application`, run at the repo root:

```bash
curl -s http://localhost:8080/v3/api-docs/frontend > docs/openapi.json         # /api/**
curl -s http://localhost:8080/v3/api-docs/admin    > docs/openapi-admin.json   # /admin/api/**
```

Stop the server.

## 2. Regenerate the frontend clients

```bash
cd frontend
npm run generate:api          # docs/openapi.json       → src/api/generated/
npm run generate:api:admin    # docs/openapi-admin.json → src/api/generated-admin/
```

## 3. Commit

Specs and generated clients are committed. Put them in their own commit, apart from hand-written changes:
`chore(api): update generated OpenAPI spec`.
