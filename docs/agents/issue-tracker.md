# Issue tracker (local)

GitHub issues were **not** created (the `/stories-to-issues` script needs the `gh`
CLI, which could not be run in the authoring session). Each story instead has a
local issue file under `artifacts/issues/`, written in the same layout the
script produces. To publish them later, run from the repo root:

```bash
python3 <SKILL_DIR>/stories-to-issues/issue-creator.py artifacts/processor-output.json --team-size 1 --repo <owner>/<repo> --label user-story --label auto-generated --dry-run
```

(`processor-output.json` will exist once `dependency-orchestrator/orchestrator.py`
has been run against `artifacts/run-config.json`.)

## Selection rule

Work issues top-to-bottom; an issue is unblocked when every issue in its
"Blocked by" column is `done`.

| # | Issue file | Story | Wave | Blocked by | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | `01-infra-be-01-backend-foundations.md` | INFRA-BE-01 | 1 | — | done |
| 2 | `02-infra-be-02-session-auth-foundation.md` | INFRA-BE-02 | 1 | — | done |
| 3 | `03-infra-fe-01-frontend-data-routing.md` | INFRA-FE-01 | 1 | — | done |
| 4 | `04-infra-fe-02-design-system.md` | INFRA-FE-02 | 1 | — | done |
| 5 | `05-infra-fe-03-frontend-auth-ui.md` | INFRA-FE-03 | 2 | 3, 4 | done |
| 6 | `06-story-1-registration.md` | 1 | 2 | 1, 2, 3, 4 | done |
| 7 | `07-story-2-login.md` | 2 | 3 | 5, 6 | done |
| 8 | `08-story-12-admin-bootstrap.md` | 12 | 3 | 6 | done |
| 9 | `09-story-6-password-reset-request.md` | 6 | 3 | 6 | done |
| 10 | `10-story-3-lockout-and-throttling.md` | 3 | 4 | 7 | done |
| 11 | `11-story-4-logout.md` | 4 | 4 | 7 | done |
| 12 | `12-story-5-hello.md` | 5 | 4 | 7 | done |
| 13 | `13-story-7-password-reset-confirm.md` | 7 | 4 | 9, 2 | done |
| 14 | `14-story-8-admin-list-users.md` | 8 | 4 | 7, 8 | done |
| 15 | `15-story-9-admin-enable-disable.md` | 9 | 5 | 14 | done |
| 16 | `16-story-10-admin-change-role.md` | 10 | 5 | 14 | done |
| 17 | `17-story-11-admin-delete.md` | 11 | 5 | 14 | done |

`done` here means the code and its tests were written. **Build, test execution and
commit have not been run** — see `docs/RUNNING.md` for the commands.
