# ADR naming convention

ADRs are prefixed by the context they belong to, each with its own independent number sequence, so a reference like "ADR-DEMO-FE-0002" is unambiguous when quoted out of context and the two contexts can add ADRs without coordinating a shared counter.

## Convention

| Prefix             | Scope                                  | Directory            |
| ------------------ | -------------------------------------- | -------------------- |
| `ADR-DEMO-NNNN`    | System-wide (spans backend + frontend) | `docs/adr/` (root)   |
| `ADR-DEMO-BE-NNNN` | Backend only                           | `backend/docs/adr/`  |
| `ADR-DEMO-FE-NNNN` | Frontend only                          | `frontend/docs/adr/` |

- Each prefix has its own sequence starting at `0001`. `ADR-DEMO-BE-0001` and `ADR-DEMO-FE-0001` coexist by design.
- Filenames are `<PREFIX>-NNNN-kebab-slug.md`.
- To number a new ADR, scan the target directory for the highest existing number under that prefix and increment by one.
- This convention mirrors the context structure: root `CONTEXT.md` (shared) ↔ `docs/adr/`, `backend/CONTEXT.md` ↔ `backend/docs/adr/`, frontend ↔ `frontend/docs/adr/`.

## Considered Options

- **One global counter across all ADRs** — rejected: forces two independently-evolving areas to coordinate numbering, and offers nothing the prefix doesn't already disambiguate.
