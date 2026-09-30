# Developer Report — Secured Hello World Auth App

> Hand-derived from `prd/assessment-prd.stories.yaml` using the dependency-orchestrator
> backwards-mapping rules and the web-frontend / backend-api foundation templates.
> The Python driver was not run (the authoring session had no shell access), so
> durations are the `duration-defaults.md` sizes with the 1.3× testing overhead
> applied, and scheduling assumes **team size = 1**.

## Summary

| Metric | Value |
| --- | --- |
| Stories | 17 (5 infrastructure + 12 feature) |
| Dev task nodes | 47 |
| Total effort (with 1.3× testing overhead) | ≈ 30 working days for one developer |
| Critical path | INFRA-FE-02 → INFRA-FE-03 → 1 → 2 → 8 → 9/10/11 |
| Max useful developers | 3 (waves 1, 3 and 4 have three or more independent stories) |

## Story-level dependency graph

```
INFRA-BE-01 ─┐
INFRA-BE-02 ─┼─► 1 (register) ─► 2 (login) ─► 3 (lockout)
INFRA-FE-01 ─┤        │              ├──────► 4 (logout)
INFRA-FE-02 ─┼► INFRA-FE-03          ├──────► 5 (hello)
             │        │              └──────► 8 (admin list) ─► 9, 10, 11
             │        └──► 6 (reset request) ─► 7 (reset confirm)
             └────────────► 12 (admin bootstrap) ─► 8
```

## Delivery waves (implementation order)

| Wave | Stories | Why they are unblocked |
| --- | --- | --- |
| 1 | INFRA-BE-01, INFRA-BE-02, INFRA-FE-01, INFRA-FE-02 | Foundations, no predecessors |
| 2 | INFRA-FE-03, 1 | Need only wave-1 foundations |
| 3 | 2, 6, 12 | Need the users schema from story 1 |
| 4 | 3, 4, 5, 7, 8 | Need login (2), reset request (6) or bootstrap (12) |
| 5 | 9, 10, 11 | Need the admin user service and page from story 8 |

## Story schedule (team size 1, working days)

| Order | Story | Category | Effort (days) | Float | Owned nodes |
| --- | --- | --- | --- | --- | --- |
| 1 | INFRA-BE-01 | infrastructure | 2.0 | 0.5 | logging, exception_mapping, openapi_docs, be_arch_tests |
| 2 | INFRA-BE-02 | infrastructure | 1.3 | 0.5 | auth_service |
| 3 | INFRA-FE-01 | infrastructure | 2.3 | 0.5 | project_setup, routing_setup, state_management, api_client, fe_arch_tests |
| 4 | INFRA-FE-02 | infrastructure | 1.3 | Critical | design_tokens, component_lib |
| 5 | INFRA-FE-03 | infrastructure | 0.7 | Critical | auth_ui |
| 6 | 1 | feature | 3.6 | Critical | be_user_db_schema, be_user_data_access, be_user_registration_service, be_user_registration_routes, fe_register_form |
| 7 | 2 | feature | 2.3 | Critical | be_auth_login_service, be_auth_login_routes, fe_login_form |
| 8 | 12 | feature | 1.0 | 1.0 | be_user_admin_config, be_user_admin_bootstrap_job |
| 9 | 6 | feature | 3.9 | 2.0 | be_password_reset_db_schema, be_password_reset_data_access, be_notification_integration, be_password_reset_request_service, be_password_reset_routes, fe_password_reset_request_form |
| 10 | 3 | feature | 2.0 | 3.0 | be_auth_lockout_service, be_auth_throttle_middleware |
| 11 | 4 | feature | 0.7 | 3.0 | be_auth_logout_routes, fe_logout_widget |
| 12 | 5 | feature | 1.0 | 3.0 | be_hello_routes, fe_hello_page |
| 13 | 7 | feature | 2.0 | 2.0 | be_password_reset_confirm_service, fe_password_reset_confirm_form |
| 14 | 8 | feature | 2.3 | Critical | be_user_admin_service, be_user_admin_routes, fe_user_admin_page, fe_user_admin_list |
| 15 | 9 | feature | 1.0 | Critical | be_user_status_service, fe_user_status_widget |
| 16 | 10 | feature | 1.0 | 0.3 | be_user_role_service, fe_user_role_widget |
| 17 | 11 | feature | 1.3 | 0.0 | be_user_delete_service, fe_user_delete_modal |

## Dev task chains per story

- **INFRA-BE-01**: logging → (exception_mapping, openapi_docs, be_arch_tests in parallel)
- **INFRA-BE-02**: auth_service (filter chain, session repository, cookie serializer, CSRF, CORS, 401/403 handlers)
- **INFRA-FE-01**: project_setup → routing_setup, state_management, api_client, fe_arch_tests
- **INFRA-FE-02**: design_tokens → component_lib
- **INFRA-FE-03**: auth_ui (session query, AuthProvider, RequireAuth / RequireAdmin guards)
- **1**: be_user_db_schema → be_user_data_access → be_user_registration_service → be_user_registration_routes → fe_register_form
- **2**: be_auth_login_service → be_auth_login_routes → fe_login_form
- **3**: be_auth_lockout_service → be_auth_throttle_middleware
- **4**: be_auth_logout_routes → fe_logout_widget
- **5**: be_hello_routes → fe_hello_page
- **6**: be_password_reset_db_schema → be_password_reset_data_access → (be_notification_integration) → be_password_reset_request_service → be_password_reset_routes → fe_password_reset_request_form
- **7**: be_password_reset_confirm_service → (be_password_reset_routes) → fe_password_reset_confirm_form
- **8**: be_user_admin_service → be_user_admin_routes → fe_user_admin_list → fe_user_admin_page
- **9**: be_user_status_service → fe_user_status_widget
- **10**: be_user_role_service → fe_user_role_widget
- **11**: be_user_delete_service → fe_user_delete_modal
- **12**: be_user_admin_config → be_user_admin_bootstrap_job

## Risk notes

- Everything funnels through story 1 and story 2; a slip there delays every feature.
- Story 7 (session invalidation on reset) and story 4 (logout replay rejection) both depend on the session store chosen in INFRA-BE-02 exposing "find sessions by principal" and "delete by id". The in-memory Spring Session repository was written with that interface so it can be swapped for JDBC/Redis later.
- Admin stories 9–11 share the same service and page; implement 8 fully before starting them.
