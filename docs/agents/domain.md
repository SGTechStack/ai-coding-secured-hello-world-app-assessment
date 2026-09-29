# Domain docs

Read these before changing auth, sessions, or admin behavior.

## Start here

- `CONTEXT.md` at the repo root is the glossary. Use those terms.
- `prd/assessment-prd.md` is the behavior the app has to meet.
- `docs/spec/secured-hello-world-auth.md` is the contract this branch implements.
- `docs/adr/` records why sessions, reset tokens, and the password rule look the way they do.

This repo is one application, split into `backend/` and `frontend/`. There is no per-module `CONTEXT.md`.

## Layout

```
/
├── AGENTS.md
├── CONTEXT.md
├── prd/assessment-prd.md
├── docs/
│   ├── adr/
│   ├── agents/
│   └── spec/
├── backend/
└── frontend/
```

## Words to keep

Use the spec's names in issues, tests, and reviews. An account is the `users` row. A session is the Spring Session row behind `HELLOSESSION`. A reset token is the secret in the link; the database holds only its hash. Lockout is per account. Throttling is per IP. Do not rename those to synonyms in the same change.

## When a change fights an ADR

Say so in the notes for the change. Do not quietly switch the session store, hash the reset token with BCrypt, or add character-class password rules without a new ADR.
