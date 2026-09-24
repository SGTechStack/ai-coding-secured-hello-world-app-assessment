# Do-Work TDD Wrapper

Use [../../tdd/SKILL.md](../../tdd/SKILL.md) as the TDD source of truth. This wrapper takes precedence for do-work execution; if it conflicts with `/tdd`, follow this wrapper.

- You MUST treat the implementer's `Test seams used` as the already-agreed seams.
- You MUST NOT prompt the user to confirm seams during implementation.
- If the stated seams are missing, contradictory, or too vague to implement safely, you must return `Status: blocked` and name the missing decision.
